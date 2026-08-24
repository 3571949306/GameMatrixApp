package com.gamecenter.app.settings

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.gamecenter.app.cloudsync.CloudSyncManager
import com.gamecenter.app.core.common.DataConnectionCenter
import com.gamecenter.app.core.common.DownloadRecord
import com.gamecenter.app.core.common.LocalDataSummary
import com.gamecenter.app.core.common.NetworkModuleInfo
import com.gamecenter.app.core.common.PermissionInfo
import com.gamecenter.app.core.common.SyncInfo
import com.gamecenter.app.modules.ModuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据与连接中心数据聚合器（#23.2）。
 *
 * 从以下数据源聚合用户设备上与数据/网络相关的状态：
 * 1. PackageManager — 已授权的运行时权限
 * 2. ModuleManager — 已安装模块及其 PrivacyCard（云端/网络行为、本地数据）
 * 3. CloudSyncManager — 最近一次云同步状态
 * 4. ModuleDownloadManager — 模块下载记录（活跃 + 历史）
 * 5. 应用缓存目录 — 可清理的缓存大小
 *
 * 同时提供：
 * - [#23.4] 一键导出用户数据为 JSON
 * - [#23.5] 一键删除缓存/本地数据（不删除模块本身）
 *
 * 所有聚合方法在 IO 调度器上执行，UI 层应通过 ViewModel 调用。
 */
class DataConnectionCenterProvider(private val context: Context) {

    companion object {
        private const val TAG = "DataConnectionCenter"
        private const val EXPORT_DIR = "data_center_exports"
        private const val EXPORT_FILENAME = "gamematrix_data.json"

        /** Android 危险权限集合（用于 PermissionInfo.isDangerous 标记） */
        private val DANGEROUS_PERMISSIONS: Set<String> = setOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    /**
     * 聚合数据与连接中心的全量状态。
     * 任意子聚合失败不影响其他项，最终返回已收集到的部分数据。
     */
    suspend fun aggregate(): DataConnectionCenter = withContext(Dispatchers.IO) {
        val grantedPermissions = safeAggregate({ aggregateGrantedPermissions() }, "permissions", emptyList())
        val installedManifests = safeAggregate({ loadInstalledModuleManifests() }, "manifests", emptyList())
        val networkModules = safeAggregate({ aggregateNetworkModules(installedManifests) }, "networkModules", emptyList())
        val lastSync = safeAggregate({ aggregateSyncInfo() }, "sync", SyncInfo())
        val downloadRecords = safeAggregate({ aggregateDownloadRecords() }, "downloads", emptyList())
        val cacheSizeBytes = safeAggregate({ aggregateCacheSize() }, "cache", 0L)
        val localDataSummary = safeAggregate({ aggregateLocalData(installedManifests) }, "localData", emptyList())

        DataConnectionCenter(
            grantedPermissions = grantedPermissions,
            networkModules = networkModules,
            lastSync = lastSync,
            downloadRecords = downloadRecords,
            cacheSizeBytes = cacheSizeBytes,
            localDataSummary = localDataSummary
        )
    }

    /**
     * [#23.4] 导出用户数据为 JSON 文件。
     *
     * 导出内容包括：
     * - 已授权权限列表
     * - 已安装模块的隐私卡（本地数据/云端数据/网络域）
     * - 最近同步状态
     * - 下载记录
     * - 缓存大小
     *
     * @return 导出文件，已写入 app-private 外部存储
     */
    suspend fun exportToJson(): File = withContext(Dispatchers.IO) {
        val snapshot = aggregate()
        val json = buildExportJson(snapshot)
        val exportDir = File(context.getExternalFilesDir(null), EXPORT_DIR).apply {
            if (!exists()) mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, EXPORT_FILENAME.replace(".json", "_$timestamp.json"))
        file.writeText(json.toString(2), Charsets.UTF_8)
        Log.i(TAG, "导出用户数据：${file.absolutePath}（${file.length()} 字节）")
        file
    }

    /**
     * [#23.5] 清除缓存/本地数据（不删除模块本身）。
     *
     * 清理范围：
     * - 应用外部缓存目录（context.externalCacheDir）
     * - 应用内部缓存目录（context.cacheDir）
     * - 模块下载临时文件
     *
     * 不清理：
     * - 已安装模块本体（filesDir/dynamic_modules）
     * - 模块数据库（Room modules 表）
     * - 用户偏好设置
     *
     * @return 实际清理的字节数
     */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        var cleared = 0L
        // 外部缓存
        context.externalCacheDir?.let { dir ->
            cleared += cleanDir(dir)
        }
        // 内部缓存（保留 data_center_exports 导出目录）
        context.cacheDir?.let { dir ->
            dir.listFiles()?.forEach { child ->
                if (child.name != EXPORT_DIR && child.isDirectory) {
                    cleared += cleanDir(child)
                } else if (child.isFile && child.name != EXPORT_FILENAME) {
                    cleared += child.length()
                    child.delete()
                }
            }
        }
        Log.i(TAG, "清理缓存：$cleared 字节")
        cleared
    }

    // ============ 子聚合实现 ============

    /** 聚合已授权的运行时权限 */
    private fun aggregateGrantedPermissions(): List<PermissionInfo> {
        val declaredPermissions = try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions?.toList() ?: emptyList()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "无法读取声明的权限", e)
            emptyList()
        }

        return declaredPermissions.mapNotNull { perm ->
            val granted = ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return@mapNotNull null

            val (label, description) = resolvePermissionMetadata(perm)
            PermissionInfo(
                permission = perm,
                label = label,
                description = description,
                isDangerous = perm in DANGEROUS_PERMISSIONS
            )
        }.sortedWith(compareByDescending<PermissionInfo> { it.isDangerous }.thenBy { it.permission })
    }

    /** 解析权限的可读标签与描述 */
    private fun resolvePermissionMetadata(perm: String): Pair<String, String> {
        return try {
            val info = context.packageManager.getPermissionInfo(perm, 0)
            val label = info.loadLabel(context.packageManager).toString()
            val desc = info.loadDescription(context.packageManager)?.toString() ?: ""
            label to desc
        } catch (e: PackageManager.NameNotFoundException) {
            // 未知权限（如自定义权限），使用权限名末尾作为标签
            val shortName = perm.substringAfterLast('.').replace('_', ' ')
            shortName to ""
        }
    }

    /** 加载已安装模块的 manifest 列表（含 PrivacyCard） */
    private fun loadInstalledModuleManifests(): List<com.gamecenter.app.core.common.ModuleManifest> {
        val installedIds = ModuleManager.getInstalledModuleIds(context)
        if (installedIds.isEmpty()) return emptyList()
        val manifests = ModuleManager.getManifests()
        return installedIds.mapNotNull { id -> manifests[id] }
    }

    /** 聚合涉及云端/网络的已安装模块 */
    private fun aggregateNetworkModules(
        manifests: List<com.gamecenter.app.core.common.ModuleManifest>
    ): List<NetworkModuleInfo> {
        return manifests
            .filter { it.privacy?.involvesCloud == true }
            .map { manifest ->
                val privacy = manifest.privacy!!
                NetworkModuleInfo(
                    moduleId = manifest.id,
                    moduleName = manifest.name,
                    networkDomains = privacy.networkDomains,
                    cloudData = privacy.cloudData,
                    syncLocation = privacy.syncLocation
                )
            }
            .sortedBy { it.moduleName }
    }

    /** 聚合最近一次云同步状态 */
    private fun aggregateSyncInfo(): SyncInfo {
        val provider = CloudSyncManager.getProvider(context)
        return SyncInfo(
            lastSyncTime = CloudSyncManager.getLastSyncTime(context),
            lastSyncStatus = CloudSyncManager.getLastSyncStatus(context),
            isConfigured = provider.isConfigured(),
            autoSyncEnabled = CloudSyncManager.isAutoSyncEnabled(context)
        )
    }

    /** 聚合模块下载记录 */
    private fun aggregateDownloadRecords(): List<DownloadRecord> {
        val manifests = ModuleManager.getManifests()
        val installedIds = ModuleManager.getInstalledModuleIds(context)
        // 活跃下载来自 ModuleDownloadManager
        val activeDownloads = try {
            com.gamecenter.app.modulestore.ModuleDownloadManager.getInstance(context)
                .getActiveDownloads()
        } catch (e: Exception) {
            Log.w(TAG, "无法获取活跃下载列表", e)
            emptyList()
        }

        val records = mutableListOf<DownloadRecord>()

        // 活跃下载
        for (moduleId in activeDownloads) {
            val manifest = manifests[moduleId]
            records.add(
                DownloadRecord(
                    moduleId = moduleId,
                    moduleName = manifest?.name ?: moduleId,
                    state = "DOWNLOADING",
                    totalSize = manifest?.fileSize ?: 0L,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 已安装模块的下载历史（简化：只记录已安装状态）
        for (moduleId in installedIds) {
            if (records.any { it.moduleId == moduleId }) continue
            val manifest = manifests[moduleId] ?: continue
            records.add(
                DownloadRecord(
                    moduleId = moduleId,
                    moduleName = manifest.name,
                    state = "LOADED",
                    downloadedSize = manifest.fileSize,
                    totalSize = manifest.fileSize,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return records.sortedByDescending { it.updatedAt }
    }

    /** 聚合缓存大小（仅外部 + 内部缓存，不含已安装模块本体） */
    private fun aggregateCacheSize(): Long {
        var size = 0L
        context.externalCacheDir?.let { size += calculateDirSize(it) }
        context.cacheDir?.let { size += calculateDirSize(it) }
        return size
    }

    /** 聚合各模块本地数据声明 */
    private fun aggregateLocalData(
        manifests: List<com.gamecenter.app.core.common.ModuleManifest>
    ): List<LocalDataSummary> {
        return manifests
            .filter { it.privacy?.localData?.isNotEmpty() == true }
            .map { manifest ->
                val privacy = manifest.privacy!!
                LocalDataSummary(
                    moduleId = manifest.id,
                    moduleName = manifest.name,
                    localData = privacy.localData,
                    retentionPeriod = privacy.retentionPeriod,
                    deletionMethod = privacy.deletionMethod
                )
            }
            .sortedBy { it.moduleName }
    }

    // ============ 工具方法 ============

    /** 安全聚合：子聚合失败时返回默认值，不影响其他项 */
    private inline fun <T> safeAggregate(
        crossinline block: () -> T,
        tag: String,
        default: T
    ): T = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "聚合 $tag 失败：${e.message}", e)
        default
    }

    /** 递归计算目录大小 */
    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    /** 递归清理目录（保留目录结构） */
    private fun cleanDir(dir: File): Long {
        if (!dir.exists()) return 0L
        var cleared = 0L
        dir.listFiles()?.forEach { child ->
            cleared += if (child.isDirectory) {
                val sub = cleanDir(child)
                child.delete()
                sub
            } else {
                val len = child.length()
                child.delete()
                len
            }
        }
        return cleared
    }

    /** 构建导出 JSON */
    private fun buildExportJson(snapshot: DataConnectionCenter): JSONObject {
        return JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("exportedAtHuman", SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
            ).format(Date()))
            put("appVersion", runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("unknown"))

            put("grantedPermissions", JSONArray().apply {
                snapshot.grantedPermissions.forEach { p ->
                    put(JSONObject().apply {
                        put("permission", p.permission)
                        put("label", p.label)
                        put("isDangerous", p.isDangerous)
                    })
                }
            })

            put("networkModules", JSONArray().apply {
                snapshot.networkModules.forEach { m ->
                    put(JSONObject().apply {
                        put("moduleId", m.moduleId)
                        put("moduleName", m.moduleName)
                        put("networkDomains", JSONArray(m.networkDomains))
                        put("cloudData", m.cloudData)
                        put("syncLocation", m.syncLocation)
                    })
                }
            })

            put("lastSync", JSONObject().apply {
                put("lastSyncTime", snapshot.lastSync.lastSyncTime)
                put("lastSyncTimeHuman", CloudSyncManager.formatTime(snapshot.lastSync.lastSyncTime))
                put("lastSyncStatus", snapshot.lastSync.lastSyncStatus)
                put("isConfigured", snapshot.lastSync.isConfigured)
                put("autoSyncEnabled", snapshot.lastSync.autoSyncEnabled)
            })

            put("downloadRecords", JSONArray().apply {
                snapshot.downloadRecords.forEach { r ->
                    put(JSONObject().apply {
                        put("moduleId", r.moduleId)
                        put("moduleName", r.moduleName)
                        put("state", r.state)
                        put("downloadedSize", r.downloadedSize)
                        put("totalSize", r.totalSize)
                    })
                }
            })

            put("cacheSizeBytes", snapshot.cacheSizeBytes)

            put("localDataSummary", JSONArray().apply {
                snapshot.localDataSummary.forEach { d ->
                    put(JSONObject().apply {
                        put("moduleId", d.moduleId)
                        put("moduleName", d.moduleName)
                        put("localData", d.localData)
                        put("retentionPeriod", d.retentionPeriod)
                        put("deletionMethod", d.deletionMethod)
                    })
                }
            })
        }
    }
}

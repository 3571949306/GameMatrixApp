package com.gamecenter.app.cloudsync

import android.content.Context
import android.util.Log
import com.gamecenter.app.ui.DataBackupHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P3-13 (CLOUD_SAVE_SYNC): 云存档同步管理器。
 *
 * 职责：
 * 1. 协调本地存档与云端存档的上传/下载
 * 2. 冲突解决（基于时间戳，新者胜；可选强制覆盖）
 * 3. 记录上次同步时间，供 UI 展示
 *
 * 数据流：
 * - 上传：DataBackupHelper.exportToJson → WebDavSyncProvider.upload
 * - 下载：WebDavSyncProvider.download → DataBackupHelper.importFromJson
 *
 * 远程存档文件名固定为 `gamematrix_save.json`，存放在 WebDAV 子目录下。
 *
 * 冲突检测策略：
 * - 上传前获取远程 meta，若远程 lastModified > localLastSyncTime 且 localTimestamp > localLastSyncTime，
 *   则视为冲突（两端都有更新），返回 Conflict 由用户决策
 * - localLastSyncTime 记录在 SharedPreferences，表示"上次同步完成时的时间戳"
 */
object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    private const val REMOTE_FILENAME = "gamematrix_save.json"
    private const val PREFS_NAME = "cloud_sync_prefs"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_AUTO_SYNC = "auto_sync_enabled"
    private const val KEY_LAST_SYNC_STATUS = "last_sync_status"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    /** 同步结果 */
    sealed class SyncResult {
        object Success : SyncResult()
        data class Conflict(val remoteTimestamp: Long, val localTimestamp: Long) : SyncResult()
        data class Failure(val message: String) : SyncResult()
        object NotConfigured : SyncResult()
        object NoLocalData : SyncResult()
    }

    /** 当前默认提供者（目前仅 WebDAV） */
    @JvmStatic
    fun getProvider(context: Context): CloudSyncProvider {
        return WebDavSyncProvider(context)
    }

    /** 上次同步时间（0 = 从未同步） */
    @JvmStatic
    fun getLastSyncTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_TIME, 0)
    }

    /** 上次同步状态（"success" / "conflict" / "failure" / ""） */
    @JvmStatic
    fun getLastSyncStatus(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SYNC_STATUS, "") ?: ""
    }

    /** 是否启用自动同步（App 启动时触发） */
    @JvmStatic
    fun isAutoSyncEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SYNC, false)
    }

    @JvmStatic
    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    /**
     * 执行上传同步：将本地存档推送到云端。
     * 冲突时返回 [SyncResult.Conflict]，由调用方决定是否 [forceUpload]。
     */
    @JvmStatic
    fun upload(context: Context, force: Boolean = false, callback: (SyncResult) -> Unit) {
        // P0 内存泄漏修复：强制使用 applicationContext，避免 scope.launch 协程体
        // 捕获 Activity context 导致 Activity 在协程挂起期间无法回收。
        val appContext = context.applicationContext
        val provider = getProvider(appContext)
        if (!provider.isConfigured()) {
            callback(SyncResult.NotConfigured)
            return
        }
        scope.launch {
            val result = syncMutex.withLock { doUpload(appContext, provider, force) }
            callback(result)
        }
    }

    /**
     * 执行下载同步：从云端拉取存档覆盖本地。
     */
    @JvmStatic
    fun download(context: Context, callback: (SyncResult) -> Unit) {
        val appContext = context.applicationContext
        val provider = getProvider(appContext)
        if (!provider.isConfigured()) {
            callback(SyncResult.NotConfigured)
            return
        }
        scope.launch {
            val result = syncMutex.withLock { doDownload(appContext, provider) }
            callback(result)
        }
    }

    /**
     * 双向同步（智能合并）：
     * 1. 获取远程 meta
     * 2. 若远程较新 → 下载覆盖本地
     * 3. 若本地较新 → 上传覆盖远程
     * 4. 若都较新（冲突）→ 返回 Conflict
     */
    @JvmStatic
    fun sync(context: Context, callback: (SyncResult) -> Unit) {
        val appContext = context.applicationContext
        val provider = getProvider(appContext)
        if (!provider.isConfigured()) {
            callback(SyncResult.NotConfigured)
            return
        }
        scope.launch {
            val result = syncMutex.withLock { doSync(appContext, provider) }
            recordSyncResult(appContext, result)
            callback(result)
        }
    }

    /**
     * P0 内存泄漏修复：取消单例 CoroutineScope，释放 SupervisorJob 及其子协程。
     * 供 App.onTerminate 或测试用例调用；调用后此单例不再可用（需重新创建进程）。
     */
    @JvmStatic
    fun shutdown() {
        scope.cancel()
    }

    // ============ 内部实现 ============

    private fun doUpload(context: Context, provider: CloudSyncProvider, force: Boolean): SyncResult {
        return try {
            val localTs = System.currentTimeMillis()
            // 冲突检测：非强制时检查远程是否较新
            if (!force) {
                val remoteMeta = provider.getRemoteMeta(REMOTE_FILENAME)
                val lastSync = getLastSyncTime(context)
                if (remoteMeta != null && remoteMeta.lastModified > lastSync && localTs > lastSync) {
                    // 两端都有更新 → 冲突
                    return SyncResult.Conflict(remoteMeta.lastModified, localTs)
                }
            }
            // 序列化本地数据
            val out = ByteArrayOutputStream()
            val bytes = DataBackupHelper.exportToJson(context, out)
            val data = out.toByteArray()
            Log.d(TAG, "上传存档：${data.size} 字节")
            when (val r = provider.upload(REMOTE_FILENAME, data, localTs)) {
                is CloudSyncProvider.UploadResult.Success -> {
                    recordSyncTime(context, localTs)
                    SyncResult.Success
                }
                is CloudSyncProvider.UploadResult.Conflict -> SyncResult.Conflict(r.remoteTimestamp, localTs)
                is CloudSyncProvider.UploadResult.Failure -> SyncResult.Failure(r.message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "doUpload 异常: ${e.message}")
            SyncResult.Failure("上传失败：${e.message}")
        }
    }

    private fun doDownload(context: Context, provider: CloudSyncProvider): SyncResult {
        return try {
            when (val r = provider.download(REMOTE_FILENAME)) {
                is CloudSyncProvider.DownloadResult.Success -> {
                    val count = DataBackupHelper.importFromJson(context, ByteArrayInputStream(r.data))
                    Log.i(TAG, "下载存档成功，导入 $count 个 key")
                    recordSyncTime(context, r.remoteTimestamp)
                    SyncResult.Success
                }
                CloudSyncProvider.DownloadResult.NotFound -> SyncResult.Failure("云端无存档")
                is CloudSyncProvider.DownloadResult.Failure -> SyncResult.Failure(r.message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "doDownload 异常: ${e.message}")
            SyncResult.Failure("下载失败：${e.message}")
        }
    }

    private fun doSync(context: Context, provider: CloudSyncProvider): SyncResult {
        return try {
            val localTs = System.currentTimeMillis()
            val lastSync = getLastSyncTime(context)
            val remoteMeta = provider.getRemoteMeta(REMOTE_FILENAME)

            val remoteNewer = remoteMeta != null && remoteMeta.lastModified > lastSync
            val localNewer = localTs > lastSync

            when {
                remoteMeta == null -> {
                    // 远程无存档，直接上传
                    Log.d(TAG, "远程无存档，执行上传")
                    doUpload(context, provider, force = true)
                }
                remoteNewer && !localNewer -> {
                    // 仅远程有更新 → 下载
                    Log.d(TAG, "远程较新，执行下载")
                    doDownload(context, provider)
                }
                !remoteNewer && localNewer -> {
                    // 仅本地有更新 → 上传
                    Log.d(TAG, "本地较新，执行上传")
                    doUpload(context, provider, force = true)
                }
                remoteNewer && localNewer -> {
                    // 两端都有更新 → 冲突
                    Log.w(TAG, "检测到冲突：远程(${remoteMeta.lastModified}) vs 本地($localTs)")
                    SyncResult.Conflict(remoteMeta.lastModified, localTs)
                }
                else -> {
                    // 两端均无更新
                    Log.d(TAG, "两端均为最新，无需同步")
                    SyncResult.Success
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "doSync 异常: ${e.message}")
            SyncResult.Failure("同步失败：${e.message}")
        }
    }

    private fun recordSyncTime(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .apply()
    }

    private fun recordSyncResult(context: Context, result: SyncResult) {
        val status = when (result) {
            SyncResult.Success -> "success"
            is SyncResult.Conflict -> "conflict"
            is SyncResult.Failure -> "failure"
            SyncResult.NotConfigured -> "not_configured"
            SyncResult.NoLocalData -> "no_data"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SYNC_STATUS, status)
            .apply()
    }

    /** 格式化时间戳为可读字符串 */
    @JvmStatic
    fun formatTime(ts: Long): String {
        if (ts <= 0) return "从未"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}

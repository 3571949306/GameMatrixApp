package com.gamecenter.app.games

import android.content.Context
import android.util.Log
import com.gamecenter.app.interfaces.IModuleStore.DownloadCallback
import com.gamecenter.app.models.ModuleInfo
import com.gamecenter.app.models.ModuleVersion
import com.gamecenter.app.models.UpdatePolicy
import com.gamecenter.app.modulestore.ModuleDownloadManager
import com.gamecenter.app.modules.ModuleDownloader
import com.gamecenter.app.modules.ModuleManager
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Date
import java.util.HashMap
import java.util.Scanner
import org.json.JSONArray
import org.json.JSONObject

/**
 * 内置模块更新器（app 层，单加载器版本）。
 *
 * 取代原 core/modulestore 的 BuiltInModuleUpdater：原实现经 ModuleLoaderV2/ModuleInstaller
 * 硬编码错误的入口类名，永远无法加载真实模块（静默失败）。本版本：
 * - 检查更新：读取 assets/files 下的 modules.json（同原逻辑）。
 * - 下载更新：沿用活链路 ModuleDownloadManager（core，live）。
 * - 应用更新 / 回退：改接 ModuleManager（app，单加载器），经 ModuleLoader 加载，
 *   自动先卸载内置版本再加载外置版本，保持全应用单一权威加载器。
 *
 * 仅被 app 内 ModuleAdapter 使用，故置于 app 层以便直接调用 ModuleManager。
 *
 * @author GameMatrixApp 隔离改造 (Phase 1)
 */
class BuiltInModuleUpdater private constructor(private val context: Context) {

    private val TAG = "BuiltInModuleUpdater"

    private val downloadManager = ModuleDownloadManager.getInstance(context)
    private val builtInModules = HashMap<String, ModuleInfo>()
    private val updatePolicies = HashMap<String, UpdatePolicy>()

    private val BUILT_IN_VERSION_CODE = 1
    private val BUILT_IN_VERSION_NAME = "1.0.0"

    companion object {
        @Volatile
        private var instance: BuiltInModuleUpdater? = null

        @JvmStatic
        fun getInstance(context: Context): BuiltInModuleUpdater {
            val appCtx = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: BuiltInModuleUpdater(appCtx).also { instance = it }
            }
        }
    }

    fun registerBuiltInModule(moduleInfo: ModuleInfo) {
        val moduleId = moduleInfo.moduleId
        builtInModules[moduleId] = moduleInfo

        val policy = UpdatePolicy(moduleId)
        policy.isAutoUpdate = true
        policy.isAllowPrerelease = false
        policy.updateChannel = "stable"
        updatePolicies[moduleId] = policy

        Log.d(TAG, "已注册内置模块: $moduleId")
    }

    fun checkBuiltInUpdate(moduleId: String): ModuleVersion? {
        if (moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空")
            return null
        }
        Log.d(TAG, "检查内置模块更新: $moduleId")

        val latestVersion = readLatestVersionFromJson(moduleId) ?: return null

        val builtInInfo = builtInModules[moduleId]
        if (builtInInfo != null && latestVersion.versionCode <= builtInInfo.versionCode) {
            Log.d(TAG, "内置模块已是最新版本: $moduleId")
            return null
        }

        Log.i(TAG, "发现更新: $moduleId -> v${latestVersion.versionName}")
        return latestVersion
    }

    private fun readLatestVersionFromJson(moduleId: String): ModuleVersion? {
        try {
            var `is`: InputStream? = null
            try {
                `is` = context.assets.open("modules.json")
            } catch (e: Exception) {
                val jsonFile = File(context.filesDir, "modules.json")
                if (jsonFile.exists()) `is` = FileInputStream(jsonFile)
            }

            if (`is` == null) {
                Log.w(TAG, "modules.json 未找到")
                return null
            }

            val scanner = Scanner(`is`, "UTF-8")
            val jsonStr = scanner.useDelimiter("\\A").next()
            scanner.close()
            `is`.close()

            val root = JSONObject(jsonStr)
            val modules = root.optJSONArray("modules") ?: run {
                Log.w(TAG, "modules.json 格式错误：缺少 modules 数组")
                return null
            }

            for (i in 0 until modules.length()) {
                val m = modules.getJSONObject(i)
                val id = m.optString("id", "")
                if (moduleId == id) {
                    val version = ModuleVersion()
                    version.versionName = m.optString("versionName", "1.0.0")
                    version.versionCode = m.optInt("versionCode", 1)
                    version.changelog = m.optString("description", "")
                    version.downloadUrl = m.optString("downloadUrl", "")
                    version.fileSize = m.optLong("fileSize", 0)
                    version.sha256 = m.optString("sha256", "")
                    version.releaseDate = Date()
                    version.minFrameworkVersion = m.optInt("minAppVersion", 1)
                    return version
                }
            }

            Log.d(TAG, "modules.json 中未找到模块: $moduleId")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "读取 modules.json 失败", e)
            return null
        }
    }

    fun downloadUpdate(moduleId: String, callback: DownloadCallback) {
        if (moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空")
            callback.onError(moduleId, 1009, "模块 ID 为空")
            return
        }

        val updateVersion = checkBuiltInUpdate(moduleId)
        if (updateVersion == null) {
            Log.w(TAG, "无可用更新: $moduleId")
            callback.onError(moduleId, 1009, "无可用更新")
            return
        }

        val tempInfo = ModuleInfo()
        tempInfo.moduleId = moduleId
        tempInfo.moduleName = builtInModules[moduleId]?.moduleName ?: moduleId
        tempInfo.versionName = updateVersion.versionName
        tempInfo.versionCode = updateVersion.versionCode
        tempInfo.type = "game"
        tempInfo.isBuiltIn = false
        tempInfo.downloadUrl = updateVersion.downloadUrl
        tempInfo.fileSize = updateVersion.fileSize
        tempInfo.sha256 = updateVersion.sha256

        Log.d(TAG, "开始下载内置模块更新: $moduleId v${updateVersion.versionName}")
        downloadManager.downloadModule(tempInfo, callback)
    }

    /**
     * 应用内置模块更新（下载完成后调用），改接单加载器 ModuleManager。
     *
     * @return 应用成功返回 true，否则回退到内置版本并返回 false。
     */
    fun applyUpdate(moduleId: String, apkFilePath: String): Boolean {
        if (moduleId.isEmpty() || apkFilePath.isEmpty()) {
            Log.e(TAG, "applyUpdate 参数无效")
            return false
        }
        val apkFile = File(apkFilePath)
        if (!apkFile.exists() || !apkFile.isFile) {
            Log.e(TAG, "applyUpdate: APK 不存在 $apkFilePath")
            return false
        }

        val update = checkBuiltInUpdate(moduleId)
        val versionCode = update?.versionCode ?: 0
        if (versionCode <= 0) {
            Log.w(TAG, "applyUpdate: 无法确定更新版本号 $moduleId")
            return false
        }

        return try {
            val applied = ModuleManager.applyExternalUpdate(context, moduleId, apkFile, versionCode)
            if (!applied) {
                Log.w(TAG, "applyUpdate: 应用失败，尝试回退 $moduleId")
                rollbackToBuiltIn(moduleId)
            }
            applied
        } catch (e: Exception) {
            Log.e(TAG, "applyUpdate 异常: $moduleId", e)
            rollbackToBuiltIn(moduleId)
            false
        }
    }

    /**
     * 回退到内置版本（经单加载器 ModuleManager）。
     */
    fun rollbackToBuiltIn(moduleId: String): Boolean {
        if (moduleId.isEmpty()) {
            Log.e(TAG, "rollbackToBuiltIn: moduleId 为空")
            return false
        }
        return try {
            Log.d(TAG, "回退到内置版本: $moduleId")
            ModuleManager.unloadModule(context, moduleId)

            val manifest = ModuleManager.getModuleManifest(moduleId)
            if (manifest != null) {
                val file = ModuleDownloader.getModuleFileCompat(context, manifest)
                if (file.exists()) file.delete()
            }

            val loaded = ModuleManager.loadModule(context, moduleId)
            loaded != null
        } catch (e: Exception) {
            Log.e(TAG, "回退失败: $moduleId", e)
            false
        }
    }

    fun getBuiltInModuleInfo(moduleId: String): ModuleInfo? = builtInModules[moduleId]

    fun getRegisteredBuiltInModules(): List<String> = ArrayList(builtInModules.keys)

    fun hasDownloadedUpdate(moduleId: String): Boolean {
        if (moduleId.isEmpty()) return false
        val modulesDir = File(context.filesDir, "modules")
        if (!modulesDir.exists()) return false
        val files = modulesDir.listFiles { _, name -> name.startsWith(moduleId) && name.endsWith(".apk") }
        return files != null && files.isNotEmpty()
    }

    fun setUpdatePolicy(moduleId: String, policy: UpdatePolicy) {
        if (moduleId.isEmpty()) return
        updatePolicies[moduleId] = policy
        Log.d(TAG, "更新策略已设置: $moduleId")
    }

    fun getUpdatePolicy(moduleId: String): UpdatePolicy {
        var policy = updatePolicies[moduleId]
        if (policy == null) {
            policy = UpdatePolicy(moduleId)
            updatePolicies[moduleId] = policy
        }
        return policy
    }

    fun release() {
        builtInModules.clear()
        updatePolicies.clear()
        downloadManager.release()
        Log.d(TAG, "BuiltInModuleUpdater 已释放所有资源")
    }
}

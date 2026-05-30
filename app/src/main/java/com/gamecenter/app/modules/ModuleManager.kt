package com.gamecenter.app.modules

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.DynamicGameActivity
import com.gamecenter.app.R
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.VpnDelegate
import com.gamecenter.app.core.security.SecureOkHttpFactory
import com.gamecenter.app.games.GameRegistry
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ModuleManager {

    private const val TAG = "ModuleManager"
    private const val PREFS_NAME = "module_manager_prefs"
    private const val KEY_INSTALLED_MODULES = "installed_modules"
    private const val KEY_MODULE_VERSION_PREFIX = "module_version_"
    private const val KEY_MODULES_LIST_VERSION = "modules_list_version"
    private const val KEY_MODULES_LIST_JSON = "modules_list_json"

    private val MODULES_URL: String get() = BuildConfig.MODULES_URL

    private val manifests = ConcurrentHashMap<String, ModuleManifest>()
    private val downloadCallbacks = ConcurrentHashMap<String, ModuleDownloader.Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== 模块列表获取（本地缓存 + 后台版本对比） =====

    /**
     * 加载模块列表 — 本地优先策略：
     * 1. 先注入本地内置兜底（无条件覆盖而非 containsKey 判断，防止缓存脏数据）
     * 2. 有缓存则立即返回本地缓存
     * 3. 后台从 VPS 获取最新列表，内存中的 manifests 始终用远程数据刷新
     * 4. 版本对比：相同仅走内存刷新不写盘，不同则合并并写盘
     */
    fun loadModuleList(context: Context, callback: (List<ModuleManifest>, error: String?) -> Unit) {
        val appContext = context.applicationContext

        registerLocalFallbackIfNeeded(appContext)

        val cachedJson = prefs(appContext).getString(KEY_MODULES_LIST_JSON, null)
        var hasCache = false
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val cachedModules = parseModulesArray(cachedJson)
                for (m in cachedModules) manifests[m.id] = m
                registerLocalFallbackIfNeeded(appContext)
                callback(getAvailableModules(), null)
                hasCache = true
            } catch (_: Exception) { /* fall through to remote */ }
        }

        Thread {
            val (modules, error) = fetchRemoteModulesInternal(appContext)
            if (error != null) {
                if (!hasCache) mainHandler.post { callback(emptyList(), error) }
                return@Thread
            }
            val result = getAvailableModules()
            mainHandler.post {
                try {
                    callback(result, null)
                } catch (e: Exception) {
                    Log.w(TAG, "loadModuleList callback error: ${e.message}")
                }
            }
        }.start()
    }

    /** 仅从远程获取模块列表（不读缓存、不回调两次）。内部使用。 */
    private fun fetchRemoteModulesInternal(context: Context): Pair<List<ModuleManifest>?, String?> {
        return try {
            val client = SecureOkHttpFactory.buildModuleClient()
            val request = Request.Builder()
                .url(MODULES_URL)
                .build()

            val response = client.newCall(request).execute()
            val responseCode = response.code
            if (!response.isSuccessful) {
                response.close()
                return Pair(null, "HTTP $responseCode")
            }

            val body = response.body?.string() ?: run {
                response.close()
                return Pair(null, "响应体为空")
            }
            response.close()

            // 解析新格式 { version: N, modules: [...] }
            val json = JSONObject(body)
            val remoteVersion = json.getInt("version")
            val localVersion = prefs(context).getInt(KEY_MODULES_LIST_VERSION, 0)

            if (remoteVersion < localVersion) {
                Log.d(TAG, "远程模块列表版本较旧($remoteVersion < $localVersion)，保留本地清单")
                return Pair(getAvailableModules(), null)
            }

            if (remoteVersion == localVersion && localVersion > 0) {
                Log.d(TAG, "模块列表版本一致 ($remoteVersion)，无需写盘，但仍刷新内存")
                val fresh = parseModulesArray(body)
                val newMap = ConcurrentHashMap<String, ModuleManifest>()
                for (m in fresh) newMap[m.id] = m
                manifests.clear()
                manifests.putAll(newMap)
                registerLocalFallbackIfNeeded(context)
                return Pair(getAvailableModules(), null)
            }

            Log.d(TAG, "模块列表版本变更: $localVersion → $remoteVersion，更新本地缓存")
            val modules = parseModulesArray(body)
            val newMap = ConcurrentHashMap<String, ModuleManifest>()
            for (m in modules) newMap[m.id] = m
            manifests.clear()
            manifests.putAll(newMap)
            registerLocalFallbackIfNeeded(context)

            prefs(context).edit()
                .putInt(KEY_MODULES_LIST_VERSION, remoteVersion)
                .putString(KEY_MODULES_LIST_JSON, body)
                .apply()

            Pair(getAvailableModules(), null)
        } catch (e: Exception) {
            Log.w(TAG, "获取远程模块列表失败: ${e.message}")
            Pair(null, e.message)
        }
    }

    /** 兼容新旧 JSON 格式的解析 */
    private fun parseModulesArray(jsonStr: String): List<ModuleManifest> {
        return try {
            // 新格式 { version: N, modules: [...] }
            val json = JSONObject(jsonStr)
            val arr = json.getJSONArray("modules")
            ModuleManifest.fromJsonArray(arr.toString())
        } catch (_: Exception) {
            // 旧格式 [...]（向后兼容）
            ModuleManifest.fromJsonArray(jsonStr)
        }
    }

    private fun parseModuleListVersion(jsonStr: String): Int {
        return try {
            JSONObject(jsonStr).optInt("version", 0)
        } catch (_: Exception) {
            0
        }
    }

    // ===== 模块下载、加载、管理 =====

    fun downloadModule(context: Context, moduleId: String, callback: ModuleDownloader.Callback?) {
        Log.d(TAG, "downloadModule() called for $moduleId, callback=$callback")
        val manifest = manifests[moduleId]
        if (manifest == null) {
            Log.e(TAG, "downloadModule: module not found: $moduleId, manifests keys=${manifests.keys}")
            callback?.onError(moduleId, "模块不存在: $moduleId")
            return
        }

        if (isModuleInstalled(context, moduleId)) {
            val installedVersion = getInstalledVersionCode(context, moduleId)
            Log.d(TAG, "downloadModule: $moduleId installedVersion=$installedVersion, manifestVersion=${manifest.versionCode}")
            if (installedVersion >= manifest.versionCode && manifest.fileName.isNotEmpty()) {
                val existingFile = ModuleDownloader.getModuleFile(context, manifest)
                if (existingFile.exists() && ModuleVerifier.verifySha256(existingFile, manifest.sha256)) {
                    Log.d(TAG, "downloadModule: $moduleId is already up to date and verified")
                    callback?.onComplete(moduleId, existingFile)
                    return
                }
                Log.d(TAG, "downloadModule: $moduleId file missing or corrupted, re-downloading")
            }
        }

        Log.d(TAG, "downloadModule: start $moduleId, url=${manifest.downloadUrl}")
        if (callback != null) downloadCallbacks[moduleId] = callback

        ModuleDownloader.downloadModule(context, manifest, object : ModuleDownloader.Callback {
            override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
                Log.d(TAG, "onProgress: $moduleId downloaded=$downloaded total=$total speed=$speedKbps")
                downloadCallbacks[moduleId]?.onProgress(moduleId, downloaded, total, speedKbps)
            }

            override fun onComplete(moduleId: String, file: File) {
                Log.d(TAG, "onComplete: $moduleId file=${file.absolutePath}")
                ModuleLoader.unloadModule(moduleId)
                markModuleInstalled(context, manifest)
                if (manifest.type == "game") {
                    registerInstalledGameModules(context)
                }
                downloadCallbacks[moduleId]?.onComplete(moduleId, file)
                downloadCallbacks.remove(moduleId)
            }

            override fun onError(moduleId: String, message: String) {
                Log.e(TAG, "onError: $moduleId message=$message")
                downloadCallbacks[moduleId]?.onError(moduleId, message)
                downloadCallbacks.remove(moduleId)
            }

            override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) {
                Log.d(TAG, "onSourceSwitch: $moduleId sourceIndex=$sourceIndex url=$url")
                downloadCallbacks[moduleId]?.onSourceSwitch(moduleId, sourceIndex, url)
            }
        })
    }

    fun loadModule(context: Context, moduleId: String): ModuleInterface? {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val manifest = manifests[moduleId] ?: return null
        return ModuleLoader.loadModule(context, manifest)
    }

    fun getModuleResources(moduleId: String): com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources? {
        return ModuleLoader.getModuleResources(moduleId)
    }

    fun startModule(context: Context, moduleId: String): Boolean = ModuleLoader.startModule(context, moduleId)

    fun unloadModule(context: Context, moduleId: String) = ModuleLoader.unloadModule(moduleId)

    fun uninstallModule(context: Context, moduleId: String) {
        ModuleLoader.unloadModule(moduleId)
        val manifest = manifests[moduleId] ?: return
        val file = ModuleDownloader.getModuleFile(context, manifest)
        if (file.exists()) file.delete()
        removeInstalledModule(context, moduleId)
        if (manifest.type == "game") {
            GameRegistry.unregister(manifest.gameId.ifEmpty { manifest.id })
        }
        Log.d(TAG, "模块 $moduleId 已卸载")
    }

    fun isModuleInstalled(context: Context, moduleId: String): Boolean {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val installed = prefs(context).getStringSet(KEY_INSTALLED_MODULES, emptySet()) ?: emptySet()
        if (installed.contains(moduleId)) return true
        val manifest = manifests[moduleId] ?: return false
        if (manifest.builtIn) return true
        if (manifest.fileName.isNotEmpty()) {
            val file = ModuleDownloader.getModuleFile(context, manifest)
            if (file.exists()) return true
        }
        return false
    }

    fun isModuleLoaded(moduleId: String): Boolean = ModuleLoader.isModuleLoaded(moduleId)

    fun getLoadedFeature(context: Context, moduleId: String): FeatureModule? {
        if (!isModuleInstalled(context, moduleId)) return null
        loadModule(context, moduleId)
        return ModuleLoader.getLoadedInstance(moduleId) as? FeatureModule
    }

    fun getLoadedVpnDelegate(context: Context): VpnDelegate? {
        if (!isModuleInstalled(context, "vpn")) return null
        loadModule(context, "vpn")
        return ModuleLoader.getLoadedInstance("vpn") as? VpnDelegate
    }

    fun getInstalledVersionCode(context: Context, moduleId: String): Int {
        val installedVersion = prefs(context).getInt(KEY_MODULE_VERSION_PREFIX + moduleId, 0)
        if (installedVersion > 0) return installedVersion
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val manifest = manifests[moduleId] ?: return 0
        return if (manifest.builtIn) {
            if (manifest.builtInVersionCode > 0) manifest.builtInVersionCode else manifest.versionCode
        } else {
            0
        }
    }

    fun getRemoteVersionCode(moduleId: String): Int {
        val manifest = getModuleManifest(moduleId) ?: return 0
        return manifest.versionCode
    }

    fun getRemoteVersionName(moduleId: String): String {
        val manifest = getModuleManifest(moduleId) ?: return "1.0.0"
        return manifest.versionName
    }

    fun getAvailableModules(): List<ModuleManifest> = manifests.values.toList()

    fun getModuleManifest(moduleId: String): ModuleManifest? {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded()
        return manifests[moduleId]
    }

    fun getInstalledModuleIds(context: Context): Set<String> {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val installed = prefs(context).getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        for ((id, manifest) in manifests) {
            if (manifest.builtIn) {
                installed.add(id)
                continue
            }
            if (!installed.contains(id) && manifest.fileName.isNotEmpty()) {
                val file = ModuleDownloader.getModuleFile(context, manifest)
                if (file.exists()) installed.add(id)
            }
        }
        return installed
    }

    /** 本地内置模块兜底 — 无条件覆盖（不检查 containsKey），确保非内置模块的关键字段（sha256 等）不被缓存脏数据覆盖 */
    fun registerLocalFallbackIfNeeded(context: Context? = null) {
        if (context != null && registerBundledModuleList(context.applicationContext)) {
            return
        }

        val localModules = listOf(
            ModuleManifest(
                id = "vpn",
                name = "科学上网",
                description = "多协议科学上网工具，支持 VMess/VLESS/Trojan/Shadowsocks 节点管理与 VPN 连接。",
                versionName = "1.0.0", versionCode = 100,
                entryClass = "com.gamecenter.app.vpn.VpnModuleEntryPoint",
                fileName = "feature_vpn_v100_v2.apk",
                fileSize = 661544,
                sha256 = "222b57edf262c23dd71752ba8ba52933c2ffe78cb1035fab48b00ce56d207bae",
                downloadUrl = BuildConfig.DOWNLOAD_BASE_URL + "feature_vpn_v100_v2.apk",
                type = "nav", storeCategory = "vpn",
                builtIn = false, isBaseFramework = false, iconUrl = ""
            ),
            ModuleManifest(
                id = "game_2048",
                name = "2048",
                description = "经典数字合并游戏，滑动方块使相同数字合并，目标达到2048！",
                versionName = "1.0.0", versionCode = 100,
                entryClass = "com.gamecenter.app.game2048.Game2048ModuleEntryPoint",
                fileName = "feature_game2048_v100.apk",
                fileSize = 0,
                sha256 = "",
                downloadUrl = BuildConfig.DOWNLOAD_BASE_URL + "feature_game2048_v100.apk",
                type = "game", storeCategory = "game",
                gameId = "game_2048", gameCategory = "puzzle",
                gameDesc = "经典数字合并游戏",
                builtIn = false, isBaseFramework = false, iconUrl = ""
            )
        )
        for (m in localModules) manifests[m.id] = m
    }

    private fun registerBundledModuleList(context: Context): Boolean {
        return try {
            val body = context.assets.open("modules.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val bundledVersion = parseModuleListVersion(body)
            val bundledModules = parseModulesArray(body)
            for (m in bundledModules) manifests[m.id] = m
            val storedVersion = prefs(context).getInt(KEY_MODULES_LIST_VERSION, 0)
            if (bundledVersion > storedVersion) {
                prefs(context).edit()
                    .putInt(KEY_MODULES_LIST_VERSION, bundledVersion)
                    .putString(KEY_MODULES_LIST_JSON, body)
                    .apply()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "读取内置模块清单失败，使用硬编码兜底: ${e.message}")
            false
        }
    }

    private fun markModuleInstalled(context: Context, manifest: ModuleManifest) {
        val p = prefs(context)
        val installed = p.getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        installed.add(manifest.id)
        p.edit()
            .putStringSet(KEY_INSTALLED_MODULES, installed)
            .putInt(KEY_MODULE_VERSION_PREFIX + manifest.id, manifest.versionCode)
            .apply()
    }

    private fun removeInstalledModule(context: Context, moduleId: String) {
        val p = prefs(context)
        val installed = p.getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        installed.remove(moduleId)
        p.edit()
            .putStringSet(KEY_INSTALLED_MODULES, installed)
            .remove(KEY_MODULE_VERSION_PREFIX + moduleId)
            .apply()
    }

    fun cancelDownload(moduleId: String) = ModuleDownloader.cancel(moduleId)

    fun registerInstalledGameModules(context: Context) {
        val installedIds = getInstalledModuleIds(context)
        for (id in installedIds) {
            val manifest = manifests[id] ?: continue
            if (isLaunchableGameManifest(manifest)) {
                registerGameFromManifest(context, manifest)
            }
        }
    }

    fun enableBuiltInModule(context: Context, manifest: ModuleManifest) {
        markModuleInstalled(context, manifest)
        if (isLaunchableGameManifest(manifest)) {
            registerGameFromManifest(context, manifest)
        }
    }


    private fun isLaunchableGameManifest(manifest: ModuleManifest): Boolean {
        if (manifest.type != "game") return false
        if (manifest.entryClass.isNotEmpty()) return true
        return manifest.builtIn && manifest.activityClass.isNotEmpty()
    }

    private fun getGameIconRes(gameId: String): Int {
        return when (gameId) {
            "gomoku" -> R.drawable.ic_gomoku
            "doudizhu" -> R.drawable.ic_doudizhu
            "chinesechess" -> R.drawable.ic_chinesechess
            "go" -> R.drawable.ic_go
            "checkers" -> R.drawable.ic_checkers
            "blackjack" -> R.drawable.ic_blackjack
            "rock" -> R.drawable.ic_rock
            "game_2048" -> R.drawable.ic_game_2048
            "sudoku" -> R.drawable.ic_sudoku
            "sokoban" -> R.drawable.ic_sokoban
            "pipeline" -> R.drawable.ic_pipeline
            "klotski" -> R.drawable.ic_klotski
            "minesweeper" -> R.drawable.ic_minesweeper
            "tetris" -> R.drawable.ic_tetris
            "tic" -> R.drawable.ic_tic
            "snake" -> R.drawable.ic_snake
            "brotato" -> R.drawable.ic_brotato
            "breakout" -> R.drawable.ic_breakout
            "whack" -> R.drawable.ic_whack
            "match" -> R.drawable.ic_match
            "flappy" -> R.drawable.ic_flappy
            "tiles" -> R.drawable.ic_tiles
            "plane" -> R.drawable.ic_plane
            "reaction" -> R.drawable.ic_reaction
            "memory" -> R.drawable.ic_memory
            "guess" -> R.drawable.ic_guess
            "dice" -> R.drawable.ic_dice
            else -> R.drawable.ic_game
        }
    }

    private fun registerGameFromManifest(context: Context, manifest: ModuleManifest) {
        try {
            val gameId = manifest.gameId.ifEmpty { manifest.id }
            if (gameId == "gomoku" || gameId == "doudizhu") {
                return
            }
            val activityClass = if (manifest.builtIn && manifest.activityClass.isNotEmpty()) {
                Class.forName(manifest.activityClass)
            } else {
                DynamicGameActivity::class.java
            }
            val categoryKey = manifest.gameCategory.ifEmpty { "casual" }
            val categoryLabel = when (categoryKey) {
                "classics" -> context.getString(R.string.category_classics)
                "puzzle" -> context.getString(R.string.category_puzzle)
                else -> context.getString(R.string.category_casual)
            }
            GameRegistry.register(GameRegistry.Entry(
                gameId,
                getGameIconRes(gameId), manifest.name,
                manifest.gameDesc.ifEmpty { manifest.description },
                activityClass, categoryLabel, categoryKey
            ))
            Log.d(TAG, "动态注册游戏: ${manifest.name} -> $categoryKey")
        } catch (e: Exception) {
            Log.w(TAG, "注册游戏失败 ${manifest.id}: ${e.message}")
        }
    }
}

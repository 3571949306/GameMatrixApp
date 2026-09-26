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
import com.gamecenter.app.core.common.ModuleDetail
import com.gamecenter.app.core.common.VpnDelegate
import com.gamecenter.app.core.security.SecureOkHttpFactory
import com.gamecenter.app.games.GameRegistry
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

object ModuleManager {

    private const val TAG = "ModuleManager"
    private const val PREFS_NAME = "module_manager_prefs"
    private const val KEY_INSTALLED_MODULES = "installed_modules"
    private const val KEY_MODULE_VERSION_PREFIX = "module_version_"
    private const val KEY_LAST_GOOD_VERSION_PREFIX = "module_last_good_version_"
    private const val KEY_CURRENT_MANIFEST_PREFIX = "module_current_manifest_"
    private const val KEY_LAST_GOOD_MANIFEST_PREFIX = "module_last_good_manifest_"
    private const val KEY_LEGACY_QUARANTINE_PREFIX = "module_legacy_quarantine_"
    private const val KEY_LEGACY_VERSION_PREFIX = "module_legacy_version_"
    private const val KEY_DISABLED_MODULES = "disabled_modules"
    private const val KEY_MODULES_LIST_VERSION = "modules_list_version"
    private const val KEY_MODULES_LIST_JSON = "modules_list_json"
    /** Batch 21: ETag 缓存协商 — 服务端返回 304 时跳过全量下载 */
    private const val KEY_MODULES_LIST_ETAG = "modules_list_etag"
    private const val HTTP_NOT_MODIFIED = 304

    private val MODULES_URL: String get() = BuildConfig.MODULES_URL

    private val manifests = ConcurrentHashMap<String, ModuleManifest>()
    private val downloadCallbacks = ConcurrentHashMap<String, ModuleDownloader.Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Download completion is delivered on main; transaction install/load must not run there. */
    private val installExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ModuleInstall")
    }

    /**
     * 冒烟缺陷修复（Games 大厅 Classics 首次为空）：出厂预装安装完成监听器。
     *
     * 背景：首启预装（installBundledModulesIfNeeded）是重 IO，可能晚于 GamesFragment
     * 的首次 onResume；底部导航 add/hide/show 切换不触发 onResume，导致大厅一直
     * 显示 "No games available"，需手动进一次商店才刷新。预装完成后在此通知
     * 已注册的监听者（主线程回调），UI 侧据此重载游戏列表。
     */
    private val bundledInstallListeners = CopyOnWriteArrayList<Runnable>()

    /** 出厂预装安装是否已在本进程跑完（区别于标记"已启动"的 [bundledInstallDone]）。 */
    @Volatile
    private var bundledInstallCompleted = false

    /**
     * 完成事件的整表派发消息是否已在主线程执行。与补偿回调互斥的判据：
     * 派发执行时先置位再遍历。监听器注册若发生在派发之前（消息还在队列中），
     * 整表派发必然会遍历到它，补偿必须跳过，否则同一监听器会被回调两次
     * （M1 整表派发一次 + M2 补偿一次的竞争时序）。
     */
    @Volatile
    private var bundledInstallDispatchDone = false

    /** 注册预装安装完成监听器（主线程回调）。若预装已完成则立即回调一次。 */
    fun addBundledInstallListener(listener: Runnable) {
        bundledInstallListeners.add(listener)
        if (bundledInstallCompleted && bundledInstallDispatchDone) {
            // 仅当整表派发已经跑过才需要补偿：新注册者错过了派发窗口。
            // 若派发消息尚在主线程队列中（dispatchDone==false），它会遍历到本
            // 监听器，此处再补偿就会双回调。补偿回调定向派发给新注册者（避免
            // 整表重派导致老监听器重复回调），执行前复查其仍在列表中：注册后、
            // 执行前注销的监听器不会收到回调。
            mainHandler.post {
                if (listener in bundledInstallListeners) {
                    try {
                        listener.run()
                    } catch (e: Exception) {
                        Log.w(TAG, "预装完成监听器回调失败: ${e.message}")
                    }
                }
            }
        }
    }

    /** 注销监听器。 */
    fun removeBundledInstallListener(listener: Runnable) {
        bundledInstallListeners.remove(listener)
    }

    /**
     * 标记出厂预装安装完成并派发监听器（主线程）。
     *
     * internal 开放给单元测试（BundledInstallListenerTest）：真实安装链路含 31 个
     * APK 的 SHA/签名校验与 dex 装载，无法在 Robolectric 下运行，测试经本接缝
     * 直接驱动完成事件。幂等：重复调用只派发一次。
     */
    internal fun notifyBundledInstallCompleted() {
        if (bundledInstallCompleted) return
        bundledInstallCompleted = true
        mainHandler.post { dispatchBundledInstallCompleted() }
    }

    /** 主线程统一派发：遍历监听器快照逐个回调，单个异常不阻断其他监听器。 */
    private fun dispatchBundledInstallCompleted() {
        // 先置位再遍历：此后注册的监听器由 addBundledInstallListener 据
        // dispatchDone 补偿一次，本派发覆盖此刻已在列表中的所有监听器，
        // 两条路径对任一监听器互斥，合计恰好一次。
        bundledInstallDispatchDone = true
        for (listener in bundledInstallListeners) {
            try {
                listener.run()
            } catch (e: Exception) {
                Log.w(TAG, "预装完成监听器回调失败: ${e.message}")
            }
        }
    }

    // MODULE_STORE_PERF_OPT: 内存级缓存，消除主线程 N+1 文件 IO
    @Volatile private var installedIdsCache: MutableSet<String>? = null
    @Volatile private var installedVersionCache: MutableMap<String, Int>? = null

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
                val cachedModules = parseModulesArray(cachedJson) ?: emptyList()
                for (m in cachedModules) manifests[m.id] = m
                registerLocalFallbackIfNeeded(appContext)
                callback(getAvailableModules(), null)
                hasCache = true
            } catch (e: Exception) { Log.w(TAG, "本地加载失败，回退到远程", e) }
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
            // Batch 21: ETag 缓存协商 — 上次响应携带 ETag 时发送 If-None-Match
            val cachedEtag = prefs(context).getString(KEY_MODULES_LIST_ETAG, null)
            val requestBuilder = Request.Builder().url(MODULES_URL)
            if (!cachedEtag.isNullOrEmpty()) {
                requestBuilder.header("If-None-Match", cachedEtag)
                Log.d(TAG, "ETag 缓存协商: 发送 If-None-Match=$cachedEtag")
            }
            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            val responseCode = response.code

            // Batch 21: 304 Not Modified — 远程清单未变化，跳过解析，直接返回本地缓存
            if (responseCode == HTTP_NOT_MODIFIED) {
                Log.d(TAG, "远程模块列表未修改 (304 Not Modified)，使用本地缓存")
                response.close()
                val cachedJson = prefs(context).getString(KEY_MODULES_LIST_JSON, null)
                if (!cachedJson.isNullOrEmpty()) {
                    val cached = parseModulesArray(cachedJson) ?: emptyList()
                    val newMap = ConcurrentHashMap<String, ModuleManifest>()
                    for (m in cached) newMap[m.id] = m
                    manifests.clear()
                    manifests.putAll(newMap)
                    registerLocalFallbackIfNeeded(context)
                }
                return Pair(getAvailableModules(), null)
            }

            if (!response.isSuccessful) {
                response.close()
                return Pair(null, "HTTP $responseCode")
            }

            // Batch 21: 提取并缓存 ETag（如果服务端返回）
            val serverEtag = response.header("ETag")
            if (!serverEtag.isNullOrEmpty()) {
                Log.d(TAG, "服务端返回 ETag: $serverEtag")
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

            // Batch 21: 版本一致 + ETag 一致时仅刷新内存不写盘
            val etagUnchanged = !serverEtag.isNullOrEmpty() && serverEtag == cachedEtag
            if (remoteVersion == localVersion && localVersion > 0) {
                Log.d(TAG, "模块列表版本一致 ($remoteVersion, etagUnchanged=$etagUnchanged)，无需写盘，但仍刷新内存")
                val fresh = parseModulesArray(body) ?: emptyList()
                val newMap = ConcurrentHashMap<String, ModuleManifest>()
                for (m in fresh) newMap[m.id] = m
                manifests.clear()
                manifests.putAll(newMap)
                registerLocalFallbackIfNeeded(context)
                return Pair(getAvailableModules(), null)
            }

            Log.d(TAG, "模块列表版本变更: $localVersion → $remoteVersion，更新本地缓存")
            val modules = parseModulesArray(body) ?: emptyList()
            val newMap = ConcurrentHashMap<String, ModuleManifest>()
            for (m in modules) newMap[m.id] = m
            manifests.clear()
            manifests.putAll(newMap)
            registerLocalFallbackIfNeeded(context)

            prefs(context).edit()
                .putInt(KEY_MODULES_LIST_VERSION, remoteVersion)
                .putString(KEY_MODULES_LIST_JSON, body)
                .apply()
            // Batch 21: 持久化 ETag 供下次请求使用
            if (!serverEtag.isNullOrEmpty()) {
                prefs(context).edit().putString(KEY_MODULES_LIST_ETAG, serverEtag).apply()
            }

            Pair(getAvailableModules(), null)
        } catch (e: Exception) {
            Log.w(TAG, "获取远程模块列表失败: ${e.message}")
            Pair(null, e.message)
        }
    }

    /** 兼容新旧 JSON 格式的解析。internal 开放给 §六 接缝契约测试（BUG_LEDGER BL-001 守卫） */
    internal fun parseModulesArray(jsonStr: String): List<ModuleManifest> {
        return try {
            // 新格式 { version: N, modules: [...] }
            val json = JSONObject(jsonStr)
            val arr = json.getJSONArray("modules")
            ModuleManifest.fromJsonArray(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "模块操作失败", e)
            // 旧格式 [...]（向后兼容）
            ModuleManifest.fromJsonArray(jsonStr)
        }
    }

    private fun parseModuleListVersion(jsonStr: String): Int {
        return try {
            JSONObject(jsonStr).optInt("version", 0)
        } catch (e: Exception) {
            Log.w(TAG, "模块操作失败", e)
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
                // P3: 使用兼容方法检查已安装的模块
                val existingFile = ModuleDownloader.getModuleFileCompat(context, manifest)
                if (existingFile.exists() && ModuleVerifier.verifySha256(existingFile, manifest.sha256, allowEmpty = manifest.builtIn)) {
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
                installExecutor.execute {
                    try {
                        installDownloadedModule(context.applicationContext, moduleId, manifest, file)
                    } catch (e: Exception) {
                        Log.e(TAG, "下载完成后的模块安装异常: $moduleId", e)
                        val callback = downloadCallbacks.remove(moduleId)
                        if (callback != null) {
                            mainHandler.post {
                                callback.onError(moduleId, "安装异常: ${e.message ?: "未知错误"}")
                            }
                        }
                    }
                }
            }

            override fun onError(moduleId: String, message: String) {
                Log.e(TAG, "onError: $moduleId message=$message")
                downloadCallbacks[moduleId]?.onError(moduleId, message)
                downloadCallbacks.remove(moduleId)
            }

            override fun onStateChanged(moduleId: String, state: String) {
                downloadCallbacks[moduleId]?.onStateChanged(moduleId, state)
            }

            override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) {
                Log.d(TAG, "onSourceSwitch: $moduleId sourceIndex=$sourceIndex url=$url")
                downloadCallbacks[moduleId]?.onSourceSwitch(moduleId, sourceIndex, url)
            }
        })
    }

    /**
     * Handles the post-download transaction off the main thread. Only the final callback
     * delivery is posted back to main because store/UI observers expect that thread.
     */
    private fun installDownloadedModule(
        context: Context,
        moduleId: String,
        manifest: ModuleManifest,
        file: File
    ) {
        val callback = downloadCallbacks[moduleId]
        fun deliver(action: (ModuleDownloader.Callback) -> Unit) {
            callback ?: return
            mainHandler.post { action(callback) }
        }

        Log.d(TAG, "onComplete: $moduleId file=${file.absolutePath}")
        deliver { it.onStateChanged(moduleId, "installing") }
        // 先读取真正已安装版本的清单快照。不能用本次待安装的 manifest
        // 充当旧版本，否则 fileName/SHA 等元数据会与 last_good 文件错配。
        val wasInstalledBefore = isModuleInstalled(context, moduleId)
        if (wasInstalledBefore && !migrateLegacyCurrentFile(context, moduleId, manifest)) {
            Log.e(TAG, "legacy 模块文件迁移失败，拒绝覆盖 current: $moduleId")
            downloadCallbacks.remove(moduleId)
            deliver { it.onError(moduleId, "旧模块文件迁移失败") }
            return
        }
        val previousVersion = getInstalledVersionCode(context, moduleId)
        val previousManifest = prepareInstalledManifest(context, manifest)
        val allowUntrackedCurrent = wasInstalledBefore && previousManifest == null

        // P3: 事务性安装 - 将文件从 staging 移动到 current
        val installResult = com.gamecenter.app.modules.store.TransactionInstaller.install(
            context, manifest, file, previousManifest, allowUntrackedCurrent
        )
        synchronizeLastGoodManifest(context, moduleId, previousManifest, installResult)

        if (!installResult.isSuccess) {
            val reason = (installResult as? com.gamecenter.app.modules.store.TransactionInstaller.InstallResult.Failure)?.reason ?: "未知原因"
            Log.e(TAG, "事务安装失败: $moduleId, $reason")
            downloadCallbacks.remove(moduleId)
            deliver { it.onError(moduleId, "安装失败: $reason") }
            return
        }
        val quarantinedPrevious =
            (installResult as? com.gamecenter.app.modules.store.TransactionInstaller.InstallResult.Success)
                ?.quarantinedPrevious
        if (quarantinedPrevious != null) {
            rememberLegacyRecovery(context, moduleId, quarantinedPrevious, previousVersion)
        } else {
            clearLegacyRecovery(context, moduleId)
        }

        // 获取安装后的 current 文件
        val installedFile = ModuleDownloader.getInstalledModuleFile(context, manifest)
        Log.d(TAG, "事务安装成功: $moduleId -> ${installedFile.absolutePath}")

        ModuleLoader.unloadModule(moduleId)
        markModuleInstalled(context, manifest)
        if (manifest.type == "game") {
            // 游戏安装后先做一次真实装载探测，再注册大厅卡片；否则坏包会
            // 先生成可见幻影，直到用户点击时才暴露，且可能错过 last_good 回滚。
            val loaded = ModuleLoader.loadModule(context, manifest)
            if (loaded == null) {
                val restored = restoreFailedExternalUpdate(
                    context = context,
                    moduleId = moduleId,
                    manifest = manifest,
                    previousManifest = previousManifest,
                    wasInstalled = wasInstalledBefore
                )
                if (!restored) {
                    Log.e(TAG, "游戏模块装载失败且补偿恢复未完成: $moduleId")
                }
                downloadCallbacks.remove(moduleId)
                deliver { it.onError(moduleId, "模块装载失败") }
                return
            }
            ModuleLoader.unloadModule(moduleId)
            clearLegacyRecovery(context, moduleId)
            registerInstalledGameModules(context)
        } else if (BuildConfig.PRELOAD_INSTALLED_TOOL_MODULES && manifest.category == "tool") {
            // 工具模块下载后立即 load 进内存，使其 TOOLS_GRID 贡献可被 DynamicToolsFragment 收集
            // ModuleLoader.loadModule 是幂等的（内部有 loadedModules 缓存）
            val loaded = runCatching { ModuleLoader.loadModule(context, manifest) }
                .onFailure { e ->
                    Log.e(TAG, "工具模块加载失败: ${manifest.id}", e)
                }
                .getOrNull()
            if (loaded != null) {
                clearLegacyRecovery(context, moduleId)
                Log.d(TAG, "工具模块已加载: ${manifest.id}")
            } else {
                val restored = restoreFailedExternalUpdate(
                    context = context,
                    moduleId = moduleId,
                    manifest = manifest,
                    previousManifest = previousManifest,
                    wasInstalled = wasInstalledBefore
                )
                if (!restored) {
                    Log.e(TAG, "工具模块装载失败且补偿恢复未完成: $moduleId")
                }
                downloadCallbacks.remove(moduleId)
                deliver { it.onError(moduleId, "模块装载失败") }
                return
            }
        }
        downloadCallbacks.remove(moduleId)
        deliver { it.onComplete(moduleId, installedFile) }
    }

    fun loadModule(context: Context, moduleId: String): ModuleInterface? {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val catalogManifest = manifests[moduleId] ?: return null
        // 目录清单代表可用更新；实际装载必须优先使用已安装快照，避免冷启动时
        // 用远程 v2 清单校验回滚后仍在 current 的 v1 文件。
        val manifest = installedManifestOr(context, moduleId, catalogManifest)
        val loaded = ModuleLoader.loadModule(context, manifest)
        if (loaded != null) clearLegacyRecovery(context, moduleId)
        return loaded
    }

    /**
     * 获取已加载模块的独立资源。
     *
     * 返回模块依赖链可见的 [com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources]
     * 表示（app-classes.jar 提供，动态模块编译期可见）；实际资源加载由统一加载器
     * `core:module-host` 完成（含 addAssetPath 探测与降级），此处仅做表示转换。
     */
    fun getModuleResources(moduleId: String): com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources? {
        val core = ModuleLoader.getModuleResources(moduleId)
            ?: return null
        return com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources(
            moduleId = core.moduleId,
            resources = core.resources,
            assetManager = core.assetManager,
            packageName = core.packageName
        )
    }

    fun startModule(context: Context, moduleId: String): Boolean = ModuleLoader.startModule(context, moduleId)

    fun unloadModule(context: Context, moduleId: String) = ModuleLoader.unloadModule(moduleId)

    /**
     * 将已下载的外部更新 APK 应用到模块（内置模块热更新到外置版本）。
     * 单加载器路径：旧 manifest/文件快照 → staging → TransactionInstaller 事务安装
     * （SHA/签名校验）→ markModuleInstalled → ModuleLoader 加载。markModuleInstalled
     * 会先记录待验证的 current 路径，供统一加载失败回调定位新文件；失败时由幂等补偿
     * 恢复旧状态，不会把该待验证版本当作最终成功更新。
     * 取代原 Java BuiltInModuleUpdater 经 ModuleLoaderV2/ModuleInstaller 的失效加载链路。
     */
    fun applyExternalUpdate(context: Context, moduleId: String, apkFile: File, versionCode: Int): Boolean {
        val catalogManifest = manifests[moduleId] ?: run {
            Log.e(TAG, "applyExternalUpdate: manifest 未找到 $moduleId")
            return false
        }
        if (!apkFile.exists() || !apkFile.isFile) {
            Log.e(TAG, "applyExternalUpdate: APK 不存在 $apkFile")
            return false
        }

        // versionCode 是旧 API 的独立参数；其余完整性/签名元数据仍必须来自
        // 已信任 catalog manifest，不能为了兼容调用方而放宽 SHA/证书校验。
        val manifest = if (versionCode > 0 && versionCode != catalogManifest.versionCode) {
            catalogManifest.copy(versionCode = versionCode)
        } else {
            catalogManifest
        }
        val appCtx = context.applicationContext
        val wasInstalled = isModuleInstalled(appCtx, moduleId)
        if (wasInstalled && !migrateLegacyCurrentFile(appCtx, moduleId, manifest)) {
            Log.e(TAG, "legacy 模块文件迁移失败，拒绝覆盖 current: $moduleId")
            return false
        }
        val previousVersion = getInstalledVersionCode(appCtx, moduleId)
        // 已安装状态若缺少可验证的 current manifest，不能伪造 last_good；
        // 事务安装会先隔离同名未知 current，再执行一次性旧安装迁移。
        val previousManifest = prepareInstalledManifest(appCtx, manifest)
        val allowUntrackedCurrent = wasInstalled && previousManifest == null

        val stagingFile = com.gamecenter.app.modules.store.TransactionInstaller
            .getStagingFile(appCtx, manifest)
        return try {
            // 外部输入先复制到 staging，避免事务安装把调用方持有的文件 rename
            // 到 current；真正的移动、SHA、签名、旧版本备份统一由 installer 完成。
            if (apkFile.canonicalFile != stagingFile.canonicalFile) {
                stagingFile.parentFile?.mkdirs()
                apkFile.copyTo(stagingFile, overwrite = true)
            }
            val installResult = com.gamecenter.app.modules.store.TransactionInstaller.install(
                appCtx,
                manifest,
                stagingFile,
                previousManifest,
                allowUntrackedCurrent
            )
            synchronizeLastGoodManifest(appCtx, moduleId, previousManifest, installResult)
            if (!installResult.isSuccess) {
                Log.e(TAG, "applyExternalUpdate 事务安装失败: $moduleId, $installResult")
                return false
            }
            val quarantinedPrevious =
                (installResult as? com.gamecenter.app.modules.store.TransactionInstaller.InstallResult.Success)
                    ?.quarantinedPrevious

            if (quarantinedPrevious != null) {
                // legacy 迁移没有可信 last_good，先持久化隔离文件名，再允许加载新版本；
                // 进程在加载失败前退出时，下一次启动仍能找到隔离记录并安全清理失败包。
                rememberLegacyRecovery(appCtx, moduleId, quarantinedPrevious, previousVersion)
            } else {
                clearLegacyRecovery(appCtx, moduleId)
            }

            // 先记录待验证版本，使 CoreModuleLoader 的失败回调能够按新 fileName
            // 隔离坏 current；restoreFailedExternalUpdate 会幂等地提交旧状态。
            ModuleLoader.unloadModule(moduleId)
            markModuleInstalled(appCtx, manifest)
            val loaded = ModuleLoader.loadModule(appCtx, manifest)
            if (loaded == null) {
                // 正常情况下 core loader 的失败回调已尝试回滚；这里覆盖未注入
                // 回调的测试/早期初始化路径，避免成功提交后静默遗留坏 current。
                // legacy 无快照时只能隔离失败包并撤销本次安装状态，不能恢复未验证旧包。
                val restored = restoreFailedExternalUpdate(
                    context = appCtx,
                    moduleId = moduleId,
                    manifest = manifest,
                    previousManifest = previousManifest,
                    wasInstalled = wasInstalled
                )
                if (!restored) {
                    Log.e(TAG, "外部更新加载失败且补偿恢复未完成: $moduleId")
                }
                return false
            }
            clearLegacyRecovery(appCtx, moduleId)
            // 冒烟缺陷修复：与下载路径（downloadModule.onComplete）对齐，
            // 预装/热更安装游戏模块且装载成功后立即注册到 GameRegistry。此前仅
            // 下载路径注册，首启预装晚于 GamesFragment 首次加载时大厅不显示卡片。
            // 仅在装载成功后注册：装载失败时 onVerifyFailure 已清理安装状态，
            // 不应残留无法启动的幻影卡片。
            // 注册判据与 registerInstalledGameModules 统一为 isLaunchableGameManifest：
            // type=="game" 且入口可用（entryClass 非空，或 builtIn+activityClass），
            // 防止无入口类的 game 清单注册出无法启动的幻影卡片。
            if (isLaunchableGameManifest(manifest)) {
                registerGameFromManifest(appCtx, manifest)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "applyExternalUpdate 失败: $moduleId", e)
            false
        } finally {
            // install 成功时 staging 已被移动；失败时只清理本次复制的 staging，
            // 不触碰外部源文件，也不触碰 current/last_good/quarantine。
            if (stagingFile.canonicalFile != apkFile.canonicalFile && stagingFile.exists()) {
                stagingFile.delete()
            }
        }
    }

    fun uninstallModule(context: Context, moduleId: String) {
        ModuleLoader.unloadModule(moduleId)
        val catalogManifest = manifests[moduleId] ?: return
        val manifest = installedManifestOr(context, moduleId, catalogManifest)
        // Capture only the verified backup belonging to this module before removing its
        // metadata. Untracked files and other modules' backups are not uninstall targets.
        val lastGoodFile = validatedLastGoodManifest(context, moduleId)?.let {
            com.gamecenter.app.modules.store.TransactionInstaller.getLastGoodFile(context, it)
        }
        // P3: 使用兼容方法获取模块文件路径
        val file = ModuleDownloader.getModuleFileCompat(context, manifest)
        if (file.exists()) {
            file.setWritable(true, false)
            file.delete()
        }
        lastGoodFile?.let {
            it.setWritable(true, false)
            it.delete()
        }
        removeInstalledModule(context, moduleId)
        if (manifest.type == "game") {
            GameRegistry.unregister(manifest.gameId.ifEmpty { manifest.id })
        }
        Log.d(TAG, "模块 $moduleId 已卸载")
    }

    /** 出厂版本号缓存：与本宿主 APK 同批打包的 assets/modules.json 中各模块的 versionCode。 */
    @Volatile
    private var bundledVersionCodes: Map<String, Int>? = null

    /** 分发架构 v2：出厂预装安装是否已在本进程执行。 */
    @Volatile
    private var bundledInstallDone = false

    /**
     * 初始包自带模块的首次安装/升级（“初始自带全部模块”标准，docs/游戏中心主页面重做执行计划 §1.4）。
     *
     * 遍历与本宿主 APK 同批打包的 assets/modules.json，凡出厂版本高于当前已安装版本
     * （或从未安装）的非内置模块，从 assets 提取 APK 并走 [applyExternalUpdate] 正式事务
     * （SHA/签名校验、落位、SP 标记、dex 加载、游戏注册）。
     *
     * IO 较重（全模块 APK 拷贝），必须在后台线程调用；每进程执行一次。
     */
    fun installBundledModulesIfNeeded(context: Context) {
        if (bundledInstallDone) return
        synchronized(this) {
            if (bundledInstallDone) return
            bundledInstallDone = true
        }
        val appCtx = context.applicationContext
        // manifests 表是懒加载的（loadModuleList 之后才有值）；Splash 阶段可能只含
        // registerLocalFallbackIfNeeded 的本地回退条目。这里**始终**用出厂
        // assets/modules.json（Catalog V2 对象格式）补全缺失条目（不覆盖已有），
        // 保证初始包自带模块全部进入安装视野。
        runCatching {
            appCtx.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use { body ->
                val arr = org.json.JSONObject(body.readText()).optJSONArray("modules")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        runCatching {
                            com.gamecenter.app.core.common.ModuleManifest
                                .fromJson(arr.getJSONObject(i))
                                ?.let { manifests.putIfAbsent(it.id, it) }
                        }
                    }
                }
            }
        }
        Log.i(TAG, "出厂预装扫描: manifests=${manifests.size}")
        var installedCount = 0
        // 本次实际尝试过安装/提取的模块数（未被 alreadyApplied 跳过）。
        // 为 0 说明全部已达标（非首启快路径），本进程未发生任何安装状态变化，
        // 结尾无需广播完成事件（省一次大厅无谓 loadGames）。
        var attemptedCount = 0
        for (m in manifests.values) {
            if (m.fileName.isBlank()) continue
            val current = try {
                getInstalledVersionCode(appCtx, m.id)
            } catch (e: Exception) {
                0
            }
            // 版本已达标且文件已落位 → 跳过；builtIn 模块版本被 SP 自动标记为
            // 出厂版本，但 assets 中的 APK 尚未提取到数据目录时仍需提取
            val appliedKey = "bundled_applied_${m.versionCode}"
            val fileReady = ModuleDownloader.getModuleFileCompat(appCtx, m).exists()
            val alreadyApplied = current >= m.versionCode &&
                    fileReady &&
                    prefs(appCtx).getBoolean(appliedKey, false)
            Log.i(TAG, "预装检查 ${m.id}: current=$current bundled=${m.versionCode} fileReady=$fileReady applied=${alreadyApplied}")
            if (alreadyApplied) continue
            attemptedCount++
            try {
                val tmp = java.io.File(appCtx.cacheDir, "bundled_${m.fileName}")
                appCtx.assets.open("modules/${m.fileName}").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                val ok = applyExternalUpdate(appCtx, m.id, tmp, m.versionCode)
                tmp.delete()
                if (ok) {
                    installedCount++
                    prefs(appCtx).edit().putBoolean(appliedKey, true).apply()
                }
                Log.i(TAG, "出厂预装模块 ${m.id}: ${if (ok) "成功" else "失败"}")
            } catch (e: Exception) {
                Log.w(TAG, "出厂预装模块失败 ${m.id}: ${e.message}")
            }
        }
        Log.i(TAG, "出厂预装完成: 新装/升级 $installedCount 个模块")
        // 冒烟缺陷修复：无论本次是否有新装（幂等场景也要通知，首启竞态下
        // GamesFragment 可能在预装进行中就已加载过空列表），统一广播完成事件，
        // 让已打开的游戏大厅等 UI 自动重载。例外：本次一个模块都没尝试过
        // （attemptedCount==0，非首启全部 alreadyApplied 跳过）说明本进程没有
        // 任何安装状态变化，跳过广播，省去大厅一次无谓 loadGames；只要尝试过
        // （即使失败）仍广播，保证大厅重扫。
        if (attemptedCount > 0) {
            notifyBundledInstallCompleted()
        } else {
            Log.i(TAG, "预装无待处理模块，跳过完成广播")
        }
    }

    /**
     * 读取模块的出厂版本号（assets/modules.json 中的 versionCode）。
     *
     * 预装 APK 与该清单由同一构建产出，版本天然一致；版本号补种必须以此为准，
     * 而不能用可能已被远程清单刷新的 [manifests]，否则会把"有更新"判定遮蔽。
     * 解析失败返回 0（不补种，行为退回现状）。
     */
    private fun bundledVersionCodeOf(context: Context, moduleId: String): Int {
        bundledVersionCodes?.let { return it[moduleId] ?: 0 }
        val map = try {
            val body = context.assets.open("modules.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseModulesArray(body).associate { it.id to it.versionCode }
        } catch (e: Exception) {
            Log.w(TAG, "读取出厂清单失败，跳过版本号补种: ${e.message}")
            emptyMap()
        }
        bundledVersionCodes = map
        return map[moduleId] ?: 0
    }

    /**
     * MODULE_STORE_PERF_OPT: 确保安装状态缓存已初始化（线程安全）。
     * 首次调用时在当前线程做一次全量扫描（建议在 IO 线程或 Activity.onCreate 调用），
     * 后续所有 isModuleInstalled / getInstalledVersionCode / getInstalledModuleIds 均走内存。
     * 关闭 flag 时退化为原逻辑（每次主线程文件 IO）。
     */
    fun ensureInstalledCache(context: Context) {
        if (!BuildConfig.MODULE_STORE_PERF_OPT) return
        if (installedIdsCache != null && installedVersionCache != null) return
        synchronized(this) {
            if (installedIdsCache != null && installedVersionCache != null) return
            if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
            val appContext = context.applicationContext
            val p = prefs(appContext)
            val installed = p.getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
            val versions = mutableMapOf<String, Int>()
            // BUG-007 修复：收集"SP 标记已安装但实际 APK 文件缺失"的模块 id，循环结束后回写 SP 清理脏数据。
            // 之前只在 !installed.contains(id) 时校验文件存在性（只能加不能减），
            // 导致历史安装过的模块被外部删除文件后，SP 仍记录为已安装，统计栏显示"6 已安装"但实际只有 3 个 APK。
            val staleIds = mutableListOf<String>()
            val seededVersions = mutableMapOf<String, Int>()
            for ((id, manifest) in manifests) {
                if (manifest.builtIn) {
                    installed.add(id)
                    val vc = if (manifest.builtInVersionCode > 0) manifest.builtInVersionCode else manifest.versionCode
                    versions[id] = vc
                    continue
                }
                val savedV = p.getInt(KEY_MODULE_VERSION_PREFIX + id, 0)
                val installedManifest = installedManifestOr(appContext, id, manifest)
                val fileExists = if (installedManifest.fileName.isNotEmpty()) {
                    ModuleDownloader.getModuleFileCompat(appContext, installedManifest).exists()
                } else {
                    false
                }
                if (installed.contains(id)) {
                    // SP 已标记为已安装：文件缺失且没有可恢复来源才视为脏数据；
                    // 有 last_good/quarantine 时必须保留状态，给加载器机会完成恢复。
                    if (!fileExists && !hasRecoverableInstalledState(appContext, id)) {
                        installed.remove(id)
                        versions.remove(id)
                        staleIds.add(id)
                        Log.w(TAG, "缓存清理: 模块 $id 标记为已安装但 APK 文件不存在，已从缓存移除")
                    } else if (savedV > 0) {
                        versions[id] = savedV
                    }
                } else {
                    // SP 未标记：检查文件是否存在以补全缓存（原逻辑）
                    if (fileExists) {
                        installed.add(id)
                        if (savedV > 0) {
                            versions[id] = savedV
                        } else {
                            // 预装补种：APK 文件在但从未走过下载流程（SP 无版本记录）。
                            // 商店"有更新"判定要求 installedVersion > 0，不补种则预装模块永远收不到更新。
                            val bundledV = bundledVersionCodeOf(appContext, id)
                            if (bundledV > 0) {
                                versions[id] = bundledV
                                seededVersions[id] = bundledV
                            }
                        }
                    }
                }
        }
        // 回写清理后的 SP（仅当确有脏数据时才写入，避免无谓 IO）
        if (staleIds.isNotEmpty() || seededVersions.isNotEmpty()) {
            val editor = p.edit()
            editor.putStringSet(KEY_INSTALLED_MODULES, installed)
            for (id in staleIds) {
                editor.remove(KEY_MODULE_VERSION_PREFIX + id)
                editor.remove(KEY_LAST_GOOD_VERSION_PREFIX + id)
                editor.remove(KEY_CURRENT_MANIFEST_PREFIX + id)
                editor.remove(KEY_LAST_GOOD_MANIFEST_PREFIX + id)
                editor.remove(KEY_LEGACY_QUARANTINE_PREFIX + id)
                editor.remove(KEY_LEGACY_VERSION_PREFIX + id)
            }
            for ((seedId, seedV) in seededVersions) {
                editor.putInt(KEY_MODULE_VERSION_PREFIX + seedId, seedV)
            }
            editor.apply()
            if (staleIds.isNotEmpty()) {
                Log.d(TAG, "已清理 ${staleIds.size} 个失效模块的安装状态缓存: $staleIds")
            }
            if (seededVersions.isNotEmpty()) {
                Log.i(TAG, "已为 ${seededVersions.size} 个预装模块补种安装版本号: $seededVersions")
            }
        }
            installedIdsCache = installed
            installedVersionCache = versions
            Log.d(TAG, "安装状态缓存已初始化: ${installed.size} 个已安装模块")
        }
    }

    /** MODULE_STORE_PERF_OPT: 失效缓存（安装/卸载/回滚后调用） */
    fun invalidateInstalledCache() {
        if (!BuildConfig.MODULE_STORE_PERF_OPT) return
        installedIdsCache = null
        installedVersionCache = null
    }

    fun isModuleInstalled(context: Context, moduleId: String): Boolean {
        if (BuildConfig.MODULE_STORE_PERF_OPT) {
            val cache = installedIdsCache
            if (cache != null) return cache.contains(moduleId)
            // 缓存未初始化，走 ensure（首次访问兜底）
            ensureInstalledCache(context)
            return installedIdsCache?.contains(moduleId) ?: false
        }
        // 原逻辑（flag 关闭时）
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val installed = prefs(context).getStringSet(KEY_INSTALLED_MODULES, emptySet()) ?: emptySet()
        if (installed.contains(moduleId)) return true
        val manifest = manifests[moduleId] ?: return false
        if (manifest.builtIn) return true
        val installedManifest = installedManifestOr(context, moduleId, manifest)
        if (installedManifest.fileName.isNotEmpty()) {
            // P3: 使用兼容方法检查模块文件
            val file = ModuleDownloader.getModuleFileCompat(context, installedManifest)
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
        if (BuildConfig.MODULE_STORE_PERF_OPT) {
            val cache = installedVersionCache
            if (cache != null) return cache[moduleId] ?: 0
            ensureInstalledCache(context)
            return installedVersionCache?.get(moduleId) ?: 0
        }
        // 原逻辑（flag 关闭时）
        val installedVersion = prefs(context).getInt(KEY_MODULE_VERSION_PREFIX + moduleId, 0)
        if (installedVersion > 0) return installedVersion
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val manifest = manifests[moduleId] ?: return 0
        val installedManifest = installedManifestOr(context, moduleId, manifest)
        return if (manifest.builtIn) {
            if (manifest.builtInVersionCode > 0) manifest.builtInVersionCode else manifest.versionCode
        } else if (installedManifest.fileName.isNotEmpty() &&
            ModuleDownloader.getModuleFileCompat(context, installedManifest).exists()
        ) {
            // 预装补种（与 ensureInstalledCache 同源）：文件在而 SP 无版本时按出厂清单版本回退并持久化
            val bundledV = bundledVersionCodeOf(context, moduleId)
            if (bundledV > 0) {
                prefs(context).edit().putInt(KEY_MODULE_VERSION_PREFIX + moduleId, bundledV).apply()
            }
            bundledV
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

    /** 返回所有已加载模块清单映射表（ID → Manifest） */
    fun getManifests(): Map<String, ModuleManifest> = HashMap(manifests)

    /**
     * 将已经过商店目录信任链校验的清单注册到运行时索引。
     *
     * 底部导航在冷启动时需要从上次成功的 Catalog 缓存恢复远程模块元数据，
     * 但不能因此触发网络请求或绕过现有下载/安装器。这里只更新内存索引；
     * APK 是否已安装、签名与 SHA-256 是否有效，仍由现有 ModuleManager/ModuleLoader
     * 权威链路判断。
     */
    fun registerAvailableManifests(available: Collection<ModuleManifest>?) {
        if (available.isNullOrEmpty()) {
            registerLocalFallbackIfNeeded()
            return
        }
        for (manifest in available) {
            manifests[manifest.id] = manifest
        }
        // Catalog V2 已提供可信清单时，只补齐缺失的本地 VPN 兜底；不能再用
        // 旧兜底覆盖其版本、哈希和下载地址。
        if (!manifests.containsKey("vpn")) {
            registerLocalFallbackIfNeeded()
        }
    }

    fun getAvailableModules(): List<ModuleManifest> = manifests.values.toList()

    /**
     * P3-12 (MODULE_STORE_ENHANCE): 获取与指定模块相似的其他模块。
     *
     * 相似度策略：
     * 1. 同 storeCategory 优先；
     * 2. 排除自身、base framework、内置兜底模块；
     * 3. 取最多 [limit] 个。
     */
    fun getSimilarModules(moduleId: String, limit: Int = 6): List<ModuleManifest> {
        val target = manifests[moduleId] ?: return emptyList()
        val category = target.storeCategory
        return manifests.values
            .asSequence()
            .filter { it.id != moduleId }
            .filter { !it.isBaseFramework }
            .sortedWith(compareByDescending<ModuleManifest> { if (it.storeCategory == category) 1 else 0 }
                .thenByDescending { it.versionCode })
            .take(limit)
            .toList()
    }

    fun getModuleManifest(moduleId: String): ModuleManifest? {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded()
        return manifests[moduleId]
    }

    fun getInstalledModuleIds(context: Context): Set<String> {
        if (BuildConfig.MODULE_STORE_PERF_OPT) {
            ensureInstalledCache(context)
            return installedIdsCache?.toSet() ?: emptySet()
        }
        // 原逻辑（flag 关闭时）
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded(context)
        val installed = prefs(context).getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        for ((id, manifest) in manifests) {
            if (manifest.builtIn) {
                installed.add(id)
                continue
            }
            val installedManifest = installedManifestOr(context, id, manifest)
            if (!installed.contains(id) && installedManifest.fileName.isNotEmpty()) {
                // P3: 使用兼容方法检查模块文件
                val file = ModuleDownloader.getModuleFileCompat(context, installedManifest)
                if (file.exists()) installed.add(id)
            }
        }
        return installed
    }

    fun isModuleEnabled(context: Context, moduleId: String): Boolean {
        val disabled = prefs(context).getStringSet(KEY_DISABLED_MODULES, emptySet()) ?: emptySet()
        return !disabled.contains(moduleId)
    }

    fun setModuleEnabled(context: Context, moduleId: String, enabled: Boolean): Boolean {
        val manifest = getModuleManifest(moduleId) ?: return false
        if (!enabled && (manifest.required || manifest.isBaseFramework)) return false
        val disabled = prefs(context).getStringSet(KEY_DISABLED_MODULES, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        if (enabled) disabled.remove(moduleId) else disabled.add(moduleId)
        prefs(context).edit().putStringSet(KEY_DISABLED_MODULES, disabled).apply()
        if (!enabled) ModuleLoader.unloadModule(moduleId)
        return true
    }

    fun hasRollback(context: Context, moduleId: String): Boolean {
        val manifest = getModuleManifest(moduleId) ?: return false
        if (!manifest.rollbackAllowed) return false
        if (validatedLastGoodManifest(context, moduleId) == null) return false
        // rollbackModule also requires a parseable current snapshot. Without it, reporting
        // rollback availability produces a button that can never succeed.
        return readManifestSnapshot(
            context,
            KEY_CURRENT_MANIFEST_PREFIX + moduleId,
            moduleId,
            "current"
        ) != null
    }

    fun rollbackModule(context: Context, moduleId: String): Boolean {
        val manifest = getModuleManifest(moduleId) ?: return false
        if (!manifest.rollbackAllowed) return false
        val lastGoodManifest = validatedLastGoodManifest(context, moduleId) ?: return false
        val currentInstalledManifest = readManifestSnapshot(
            context,
            KEY_CURRENT_MANIFEST_PREFIX + moduleId,
            moduleId,
            "current"
        ) ?: return false
        ModuleLoader.unloadModule(moduleId)
        val rolledBack = com.gamecenter.app.modules.store.TransactionInstaller.rollback(
            context,
            currentInstalledManifest,
            lastGoodManifest
        )
        if (!rolledBack) return false
        val restoredFile = com.gamecenter.app.modules.store.TransactionInstaller
            .getCurrentFile(context, lastGoodManifest)
        if (!com.gamecenter.app.core.security.ModuleVerifier
                .verify(restoredFile, lastGoodManifest.sha256, lastGoodManifest.fileSize).isSuccess
        ) {
            Log.w(TAG, "模块 $moduleId 回滚后完整性校验失败，拒绝提交状态")
            return false
        }
        // 文件恢复成功后，清单索引、已安装清单和缓存必须一起切回旧版本。
        // last_good 文件仍由同一份快照描述，不能把版本号单独交换成当前坏版本。
        commitInstalledManifestState(context, moduleId, lastGoodManifest)
        return true
    }

    /**
     * 统一加载失败后的内部恢复入口。只有带可信 last_good 的状态才属于公开回滚；
     * 无快照的 legacy 更新只能隔离失败文件并清除安装状态，不能向用户伪造回滚成功。
     */
    fun recoverFailedModuleLoad(context: Context, moduleId: String): Boolean {
        val manifest = getModuleManifest(moduleId) ?: return false
        if (!manifest.rollbackAllowed) return false
        return if (validatedLastGoodManifest(context, moduleId) != null) {
            rollbackModule(context, moduleId)
        } else {
            restoreLegacyRecovery(context, moduleId, manifest)
        }
    }

    private fun restoreLegacyRecovery(
        context: Context,
        moduleId: String,
        manifest: ModuleManifest
    ): Boolean {
        val p = prefs(context)
        val quarantineName = p.getString(KEY_LEGACY_QUARANTINE_PREFIX + moduleId, null)
            ?: return false
        if (com.gamecenter.app.modules.store.TransactionInstaller
                .findQuarantineFile(context, quarantineName) == null
        ) return false
        ModuleLoader.unloadModule(moduleId)
        // The old package has no trusted manifest/SHA. Keep it quarantined rather than
        // restoring it as current, because the unified loader would otherwise validate it
        // against the newer catalog manifest on the next cold start.
        if (!com.gamecenter.app.modules.store.TransactionInstaller.restoreUntrackedCurrent(
                context,
                manifest,
                null
            )
        ) return false
        val previousVersion = p.getInt(KEY_LEGACY_VERSION_PREFIX + moduleId, 0)
        removeInstalledModule(context, moduleId)
        Log.w(TAG, "模块 $moduleId 的 legacy 旧包无法验证，已隔离并清除安装状态 v$previousVersion")
        return true
    }

    private fun restoreFailedExternalUpdate(
        context: Context,
        moduleId: String,
        manifest: ModuleManifest,
        previousManifest: ModuleManifest?,
        wasInstalled: Boolean
    ): Boolean {
        val alreadyRestored = if (previousManifest != null) {
            val currentSnapshot = readManifestSnapshot(
                context,
                KEY_CURRENT_MANIFEST_PREFIX + moduleId,
                moduleId,
                "current"
            )
            currentSnapshot == previousManifest && isVerifiedManifestFile(
                com.gamecenter.app.modules.store.TransactionInstaller
                    .getCurrentFile(context, previousManifest),
                previousManifest
            )
        } else {
            wasInstalled && !isModuleInstalled(context, moduleId) &&
                !com.gamecenter.app.modules.store.TransactionInstaller
                    .getCurrentFile(context, manifest).exists()
        }
        if (alreadyRestored) {
            if (previousManifest != null) {
                commitInstalledManifestState(context, moduleId, previousManifest)
            } else {
                removeInstalledModule(context, moduleId)
            }
            return true
        }

        val restored = if (previousManifest != null) {
            val rolledBack = com.gamecenter.app.modules.store.TransactionInstaller.rollback(
                context,
                manifest,
                previousManifest
            )
            rolledBack && isVerifiedManifestFile(
                com.gamecenter.app.modules.store.TransactionInstaller
                    .getCurrentFile(context, previousManifest),
                previousManifest
            )
        } else {
            com.gamecenter.app.modules.store.TransactionInstaller.restoreUntrackedCurrent(
                context,
                manifest,
                null
            )
        }
        if (!restored) return false

        if (previousManifest != null) {
            commitInstalledManifestState(context, moduleId, previousManifest)
        } else {
            // 没有隔离到旧 current 时无法证明旧安装仍可恢复（例如旧 fileName
            // 与新清单不同）；不能把新文件移走后仍标记成已安装旧版本。
            removeInstalledModule(context, moduleId)
        }
        return true
    }

    private fun commitInstalledManifestState(
        context: Context,
        moduleId: String,
        manifest: ModuleManifest
    ) {
        manifests[moduleId] = manifest
        val p = prefs(context)
        val installed = p.getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        installed.add(moduleId)
        p.edit()
            .putStringSet(KEY_INSTALLED_MODULES, installed)
            .putInt(KEY_MODULE_VERSION_PREFIX + moduleId, manifest.versionCode)
            .putString(KEY_CURRENT_MANIFEST_PREFIX + moduleId, manifest.toJson().toString())
            // A successful recovery consumes last_good; the next update will create
            // a fresh snapshot from this verified current manifest.
            .remove(KEY_LAST_GOOD_VERSION_PREFIX + moduleId)
            .remove(KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId)
            .remove(KEY_LEGACY_QUARANTINE_PREFIX + moduleId)
            .remove(KEY_LEGACY_VERSION_PREFIX + moduleId)
            .apply()
        installedIdsCache?.add(moduleId)
        installedVersionCache?.put(moduleId, manifest.versionCode)
    }

    /**
     * 安装前只读取并验证 current 快照，不能提前覆盖仍有效的 last_good 元数据。
     * 实际备份结果由 synchronizeLastGoodManifest 在事务返回后核对。
     */
    private fun prepareInstalledManifest(
        context: Context,
        newManifest: ModuleManifest
    ): ModuleManifest? {
        if (!isModuleInstalled(context, newManifest.id)) {
            return null
        }

        val previous = readManifestSnapshot(
            context,
            KEY_CURRENT_MANIFEST_PREFIX + newManifest.id,
            newManifest.id,
            "current"
        ) ?: run {
            Log.w(TAG, "模块 ${newManifest.id} 缺少可解析的 current manifest，兼容旧安装：本次不建立回滚快照")
            return null
        }
        val previousFile = com.gamecenter.app.modules.store.TransactionInstaller
            .getCurrentFile(context, previous)
        if (!isVerifiedManifestFile(previousFile, previous)) {
            Log.w(TAG, "模块 ${newManifest.id} 的 current 文件校验失败，拒绝建立回滚快照")
            return null
        }
        return previous
    }

    /**
     * Match last_good metadata to files after the transaction, including failed moves.
     * Preflight rejection leaves the existing verified snapshot intact. If backup already
     * replaced the file before a later failure, only the verified previous-current snapshot
     * may describe that new backup; the superseded metadata must not survive.
     */
    private fun synchronizeLastGoodManifest(
        context: Context,
        moduleId: String,
        previousManifest: ModuleManifest?,
        installResult: com.gamecenter.app.modules.store.TransactionInstaller.InstallResult
    ) {
        val previousBackup = previousManifest?.takeIf {
            isVerifiedManifestFile(
                com.gamecenter.app.modules.store.TransactionInstaller.getLastGoodFile(context, it),
                it
            )
        }
        val snapshot = if (installResult.isSuccess) {
            // Legacy updates without a trusted current snapshot do not invent rollback.
            previousBackup
        } else {
            val existing = readManifestSnapshot(
                context,
                KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId,
                moduleId,
                "last-good"
            )?.takeIf {
                isVerifiedManifestFile(
                    com.gamecenter.app.modules.store.TransactionInstaller.getLastGoodFile(context, it),
                    it
                )
            }
            if (existing != null) {
                // A failed transaction did not invalidate this backup. Preserve the
                // original snapshot verbatim, including absent optional/unknown fields.
                val p = prefs(context)
                if (p.getInt(KEY_LAST_GOOD_VERSION_PREFIX + moduleId, 0) != existing.versionCode) {
                    p.edit().putInt(KEY_LAST_GOOD_VERSION_PREFIX + moduleId, existing.versionCode).apply()
                }
                return
            }
            previousBackup
        }
        if (snapshot == null) {
            clearLastGoodMetadata(context, moduleId)
            return
        }
        prefs(context).edit()
            .putString(KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId, snapshot.toJson().toString())
            .putInt(KEY_LAST_GOOD_VERSION_PREFIX + moduleId, snapshot.versionCode)
            .apply()
    }

    /**
     * 将旧版扁平 modules/ 目录中的已安装包迁移到事务 current/ 目录。
     *
     * 已有 current 快照时按旧清单文件名定位；没有快照时只接受精确文件名、模块
     * 默认名或唯一的 moduleId_ 前缀文件，避免把同目录的未知包误认成当前模块。
     */
    private fun migrateLegacyCurrentFile(
        context: Context,
        moduleId: String,
        manifest: ModuleManifest
    ): Boolean {
        val previous = readManifestSnapshot(
            context,
            KEY_CURRENT_MANIFEST_PREFIX + moduleId,
            moduleId,
            "current"
        )
        val targetManifest = previous ?: manifest
        val target = com.gamecenter.app.modules.store.TransactionInstaller
            .getCurrentFile(context, targetManifest)
        if (target.exists()) return true

        val legacyDir = File(context.filesDir, "modules")
        val configuredName = File(targetManifest.fileName).name
        val exactCandidates = linkedSetOf<String>()
        if (configuredName.isNotEmpty() && configuredName == targetManifest.fileName) {
            exactCandidates.add(configuredName)
        }
        exactCandidates.add("$moduleId.apk")
        val exact = exactCandidates.asSequence()
            .map { File(legacyDir, it) }
            .firstOrNull { it.isFile }
        val source = exact ?: legacyDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("${moduleId}_") }
            ?.takeIf { it.size == 1 }
            ?.single()
            ?: return true

        return try {
            target.parentFile?.mkdirs()
            source.setWritable(true, false)
            if (!source.renameTo(target)) {
                Log.e(TAG, "legacy 模块文件无法迁移: ${source.absolutePath} -> ${target.absolutePath}")
                false
            } else {
                Log.i(TAG, "已迁移 legacy 模块文件: $moduleId (${source.name})")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "legacy 模块文件迁移异常: $moduleId", e)
            false
        }
    }

    /** Runtime backups must come from the same verified package as outer rollback. */
    internal fun getVerifiedLastGoodManifest(context: Context, moduleId: String): ModuleManifest? =
        validatedLastGoodManifest(context, moduleId)

    /**
     * 读取并验证 last_good 的完整快照。旧的独立版本号不是可信来源：快照缺失、
     * 损坏、路径不安全或文件 SHA/size 不匹配时，清掉旧数字，避免 UI 假报可回滚。
     * 清理只涉及状态元数据，不删除 last_good/quarantine 中的用户文件。
     */
    private fun validatedLastGoodManifest(context: Context, moduleId: String): ModuleManifest? {
        val snapshot = readManifestSnapshot(
            context,
            KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId,
            moduleId,
            "last-good"
        ) ?: run {
            clearLastGoodMetadata(context, moduleId)
            return null
        }
        val file = com.gamecenter.app.modules.store.TransactionInstaller
            .getLastGoodFile(context, snapshot)
        if (!isVerifiedManifestFile(file, snapshot)) {
            Log.w(TAG, "模块 $moduleId 的 last-good 文件校验失败，保守禁用回滚")
            clearLastGoodMetadata(context, moduleId)
            return null
        }
        return snapshot
    }

    private fun isVerifiedManifestFile(file: File, manifest: ModuleManifest): Boolean {
        if (manifest.fileName.isBlank() || File(manifest.fileName).name != manifest.fileName) return false
        return com.gamecenter.app.core.security.ModuleVerifier
            .verify(file, manifest.sha256, manifest.fileSize).isSuccess
    }

    /** 保留仍有可恢复来源的安装标记，避免缓存初始化抢在加载器回滚前清掉状态。 */
    private fun hasRecoverableInstalledState(context: Context, moduleId: String): Boolean {
        val lastGood = readManifestSnapshot(
            context,
            KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId,
            moduleId,
            "last-good"
        )
        if (lastGood != null && isVerifiedManifestFile(
                com.gamecenter.app.modules.store.TransactionInstaller.getLastGoodFile(context, lastGood),
                lastGood
            )
        ) {
            return true
        }
        val quarantineName = prefs(context).getString(KEY_LEGACY_QUARANTINE_PREFIX + moduleId, null)
        return quarantineName != null && com.gamecenter.app.modules.store.TransactionInstaller
            .findQuarantineFile(context, quarantineName) != null
    }

    private fun clearLastGoodMetadata(context: Context, moduleId: String) {
        prefs(context).edit()
            .remove(KEY_LAST_GOOD_VERSION_PREFIX + moduleId)
            .remove(KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId)
            .apply()
    }

    private fun rememberLegacyRecovery(
        context: Context,
        moduleId: String,
        quarantinedPrevious: File,
        previousVersion: Int
    ) {
        val persisted = prefs(context).edit()
            .putString(KEY_LEGACY_QUARANTINE_PREFIX + moduleId, quarantinedPrevious.name)
            .putInt(KEY_LEGACY_VERSION_PREFIX + moduleId, previousVersion)
            .commit()
        if (!persisted) {
            Log.e(TAG, "无法持久化 legacy recovery token: $moduleId")
        }
    }

    private fun clearLegacyRecovery(context: Context, moduleId: String) {
        prefs(context).edit()
            .remove(KEY_LEGACY_QUARANTINE_PREFIX + moduleId)
            .remove(KEY_LEGACY_VERSION_PREFIX + moduleId)
            .apply()
    }

    private fun readManifestSnapshot(
        context: Context,
        key: String,
        moduleId: String,
        label: String
    ): ModuleManifest? {
        val raw = prefs(context).getString(key, null)
        if (raw.isNullOrBlank()) {
            Log.w(TAG, "模块 $moduleId 缺少 $label manifest 快照")
            return null
        }
        return try {
            ModuleManifest.fromJson(JSONObject(raw)).also {
                require(it.id == moduleId) { "manifest id 不匹配" }
                require(it.fileName.isBlank() || File(it.fileName).name == it.fileName) {
                    "manifest fileName 不安全"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "模块 $moduleId 的 $label manifest 快照损坏: ${e.message}")
            null
        }
    }

    /** 读取已安装清单供文件定位/装载使用；缺失时回退到目录清单（旧安装兼容）。 */
    private fun installedManifestOr(
        context: Context,
        moduleId: String,
        fallback: ModuleManifest
    ): ModuleManifest {
        val raw = prefs(context).getString(KEY_CURRENT_MANIFEST_PREFIX + moduleId, null)
            ?: return fallback
        return try {
            ModuleManifest.fromJson(JSONObject(raw)).also {
                require(it.id == moduleId) { "manifest id 不匹配" }
                require(it.fileName.isBlank() || File(it.fileName).name == it.fileName) {
                    "manifest fileName 不安全"
                }
            }
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * 本地 VPN 兜底。Catalog V2 恢复路径和直接 null 调用都不能覆盖已有清单。
     */
    fun registerLocalFallbackIfNeeded(context: Context? = null) {
        if (context != null && registerBundledModuleList(context.applicationContext)) {
            return
        }
        // null 入口也可能由后台导航恢复调用；已有清单（尤其是可信 Catalog
        // 清单）优先，避免 fallback 的旧元数据覆盖版本、哈希和下载地址。
        if (manifests.containsKey("vpn")) return

        // Batch 21 修复：vpn 兜底的文件元数据与 assets/modules.json 保持一致
        // （sha256/fileName/fallbackUrl/githubUrl 完全对齐）；下载地址跟随当前
        // BuildConfig，避免导航恢复路径把已更新的分发域名改回历史死域。
        // 避免 assets 读取失败时，使用与 modules.json 不一致的 sha256 导致下载后校验失败
        // 注意：无 Context 调用无法读取 assets，必须保持此处的文件元数据与
        // assets/modules.json 中的 vpn 条目完全一致。
        val localModules = listOf(
            ModuleManifest(
                id = "vpn",
                name = "VPN",
                description = "仅远程使用的 VPN 模块，支持代理配置管理与连接控制。",
                versionName = "1.0.0", versionCode = 100,
                entryClass = "com.gamecenter.app.vpn.VpnModuleEntryPoint",
                fileName = "vpn-release.apk",
                fileSize = 1134494,
                sha256 = "a973532064cbc0e455e41e785600d5f1357eb8ef82d79e48c2df3d7ac797cf11",
                downloadUrl = BuildConfig.DOWNLOAD_BASE_URL + "vpn-release.apk",
                fallbackUrl = "",
                githubUrl = "",
                type = "nav", storeCategory = "device_network", category = "tool",
                minAppVersionCode = 492,
                details = ModuleDetail(
                    valueDescription = "提供远程代理连接能力，支持配置管理与连接状态控制。",
                    audience = "需要通过代理访问网络或保护网络隐私的用户。",
                    offlineCapability = "需联网建立 VPN 隧道；配置管理界面离线可用。",
                    updateImpact = "更新保留已保存的 VPN 配置。",
                    uninstallImpact = "卸载将清除所有 VPN 配置与连接凭证。",
                    highlights = listOf("代理配置管理", "连接状态监控", "多配置切换", "启动即连接"),
                    limitations = listOf("仅支持远程配置的代理协议", "连接质量依赖服务器与网络环境")
                ),
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
            val bundledModules = parseModulesArray(body) ?: emptyList()
            for (m in bundledModules) {
                val existing = manifests[m.id]
                // 2026-08-29 热更修复：按版本号择优合并。此前无条件覆盖会以出厂清单
                // 降盖远程清单的新版本，导致热更永远不可见。版本相同或更高时仍以
                // 出厂清单为准（保留"防止缓存脏数据"的原有意图）。
                if (existing == null || m.versionCode >= existing.versionCode) {
                    manifests[m.id] = m
                }
            }
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
            .putString(KEY_CURRENT_MANIFEST_PREFIX + manifest.id, manifest.toJson().toString())
            .apply()
        // MODULE_STORE_PERF_OPT: 同步更新内存缓存
        installedIdsCache?.add(manifest.id)
        installedVersionCache?.put(manifest.id, manifest.versionCode)
    }

    private fun removeInstalledModule(context: Context, moduleId: String) {
        val p = prefs(context)
        val installed = p.getStringSet(KEY_INSTALLED_MODULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        installed.remove(moduleId)
        p.edit()
            .putStringSet(KEY_INSTALLED_MODULES, installed)
            .remove(KEY_MODULE_VERSION_PREFIX + moduleId)
            .remove(KEY_LAST_GOOD_VERSION_PREFIX + moduleId)
            .remove(KEY_CURRENT_MANIFEST_PREFIX + moduleId)
            .remove(KEY_LAST_GOOD_MANIFEST_PREFIX + moduleId)
            .remove(KEY_LEGACY_QUARANTINE_PREFIX + moduleId)
            .remove(KEY_LEGACY_VERSION_PREFIX + moduleId)
            .apply()
        setModuleEnabled(context, moduleId, true)
        // MODULE_STORE_PERF_OPT: 同步更新内存缓存
        installedIdsCache?.remove(moduleId)
        installedVersionCache?.remove(moduleId)
    }

    /** 供 [ModuleLoader] 在确认无可恢复来源后清理 SP 与内存缓存中的安装状态。 */
    fun removeInstalledModulePublic(context: Context, moduleId: String) {
        removeInstalledModule(context, moduleId)
    }

    fun cancelDownload(moduleId: String) = ModuleDownloader.cancel(moduleId)

    fun registerInstalledGameModules(context: Context) {
        val installedIds = getInstalledModuleIds(context)
        for (id in installedIds) {
            val catalogManifest = manifests[id] ?: continue
            val manifest = installedManifestOr(context, id, catalogManifest)
            if (isLaunchableGameManifest(manifest)) {
                registerGameFromManifest(context, manifest)
            }
        }
    }

    /**
     * 根据游戏 ID 获取宿主 Activity 类名。
     *
     * 查找顺序：
     * 1. manifests 中有匹配的 builtIn 模块且指定了 activityClass → 返回该类名
     * 2. manifests 中有匹配的游戏模块 → 返回 DynamicGameActivity 类名（由宿主统一承载）
     * 3. 未找到 → 返回 null
     *
     * @param gameId 游戏 ID（通常为 manifest.gameId 或 manifest.id）
     * @return Activity 完整类名，未匹配时返回 null
     */
    fun getHostGameActivityClassName(gameId: String): String? {
        if (manifests.isEmpty()) registerLocalFallbackIfNeeded()
        for ((_, manifest) in manifests) {
            val mid = manifest.gameId.ifEmpty { manifest.id }
            if (mid != gameId) continue
            if (manifest.builtIn && manifest.activityClass.isNotEmpty()) {
                return manifest.activityClass
            }
            // Non-builtIn modules (downloaded from store) handle their own Activity
            // via entryClass, so return null to avoid DynamicGameActivity infinite loop
            return null
        }
        return null
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
            // 2026-08-29 模块热更改造：所有游戏统一注册 DynamicGameActivity（模块加载器路径），
            // 启动后由 tryLoadModuleGame 按清单加载外置模块 APK，支持经商店单独热更。
            // 数据回退兼容：若清单把某游戏翻回 builtIn=true + activityClass，
            // getHostGameActivityClassName 仍会在 tryLoadModuleGame 中接管宿主直启。
            val activityClass = DynamicGameActivity::class.java
            val categoryKey = manifest.gameCategory.ifEmpty { "casual" }
            val categoryLabel = when (categoryKey) {
                "classics" -> context.getString(R.string.category_classics)
                "puzzle" -> context.getString(R.string.category_puzzle)
                else -> context.getString(R.string.category_casual)
            }
            // 优先使用 catalog.json 中的中文名称和描述（本地化方案A），
            // 模块 APK 内 manifest 的 name/desc 可能仍是英文
            val catalogModule = try {
                com.gamecenter.app.modules.store.DefaultStoreCatalogRepository
                    .getInstance(context).getCachedCatalog()
                    ?.modules?.find { it.id == manifest.id || it.id == gameId }
            } catch (e: Exception) {
                Log.w(TAG, "读取 catalog 查找模块 ${manifest.id} 失败: ${e.message}")
                null
            }
            val displayName = catalogModule?.name?.takeIf { it.isNotEmpty() } ?: manifest.name
            val displayDesc = catalogModule?.gameDesc?.takeIf { it.isNotEmpty() }
                ?: catalogModule?.description?.takeIf { it.isNotEmpty() }
                ?: manifest.gameDesc.ifEmpty { manifest.description }
            GameRegistry.register(GameRegistry.Entry(
                gameId,
                getGameIconRes(gameId), displayName,
                displayDesc,
                activityClass, categoryLabel, categoryKey
            ))
            Log.d(TAG, "动态注册游戏: $displayName -> $categoryKey")
        } catch (e: Exception) {
            Log.w(TAG, "注册游戏失败 ${manifest.id}: ${e.message}")
        }
    }
}

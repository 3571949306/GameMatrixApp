package com.gamecenter.app.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.games.GameUsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * P3-14 (OFFLINE_MODULE_PRELOAD): 离线模块预下载管理器。
 *
 * 在 WiFi 环境下自动预下载用户可能需要的模块 APK，提升离线体验。
 *
 * 触发时机：
 * 1. WiFi 连接时（通过 [ConnectivityManager.NetworkCallback]）
 * 2. App 启动时（如已在 WiFi 环境）
 *
 * 优先级策略（综合评分，降序）：
 * - 收藏游戏对应的模块（+100）
 * - 最近 7 天内游玩过的模块（+50）
 * - 按历史启动次数（每次 +5，上限 50）
 * - 精选模块（+10）
 *
 * 限制：
 * - 仅在 WiFi 下触发（可通过 [setAllowOnCellular] 放宽，默认 false）
 * - 单次预下载不超过 [MAX_PRELOAD_PER_RUN] 个模块
 * - 已安装/已下载的模块自动跳过（由 ModuleDownloadManager 内部短路）
 * - 跳过内置/baseFramework/必装模块（已预装）
 * - 跳过体积超过 [MAX_FILE_SIZE_BYTES] 的模块（避免占用过多空间）
 */
object ModulePreDownloadManager {

    private const val TAG = "ModulePreDownload"

    /** 单次预下载最大模块数 */
    private const val MAX_PRELOAD_PER_RUN = 5

    /** 单模块体积上限：50 MB（避免预下载超大模块） */
    private const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024

    /** 评分权重 */
    private const val SCORE_FAVORITE = 100
    private const val SCORE_RECENT = 50
    private const val SCORE_PLAY_COUNT_PER = 5
    private const val SCORE_PLAY_COUNT_MAX = 50
    private const val SCORE_FEATURED = 10

    /** 最近游玩判定窗口：7 天 */
    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private const val PREFS_NAME = "module_preload_prefs"
    private const val KEY_ENABLED = "preload_enabled"
    private const val KEY_ALLOW_CELLULAR = "preload_allow_cellular"
    private const val KEY_LAST_RUN_TIME = "last_preload_run_time"
    /** 最小触发间隔：30 分钟（避免频繁触发） */
    private const val MIN_INTERVAL_MS = 30L * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runMutex = Mutex()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 初始化：注册 WiFi 网络回调。应在 App.onCreate() 中调用。
     */
    @JvmStatic
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) {
            Log.d(TAG, "预下载未启用，跳过初始化")
            return
        }
        registerNetworkCallback(appContext)
        // 启动时如已在 WiFi 环境，触发一次
        if (isOnWifi(appContext)) {
            Log.d(TAG, "App 启动时已在 WiFi 环境，触发预下载")
            maybePreload(appContext)
        }
    }

    /** 启用/禁用预下载功能 */
    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            registerNetworkCallback(appContext)
        } else {
            unregisterNetworkCallback(appContext)
        }
    }

    /** 是否启用预下载 */
    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    /** 是否允许蜂窝网络下预下载（默认仅 WiFi） */
    @JvmStatic
    fun setAllowOnCellular(context: Context, allow: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ALLOW_CELLULAR, allow).apply()
    }

    @JvmStatic
    fun isAllowOnCellular(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_CELLULAR, false)
    }

    /** 上次预下载运行时间（0 表示从未运行） */
    @JvmStatic
    fun getLastRunTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_RUN_TIME, 0)
    }

    /**
     * 判断当前是否在 WiFi 环境。
     */
    @JvmStatic
    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 触发预下载（带节流和 WiFi 检查）。
     * 内部用 Mutex 保证同一时刻只有一个预下载任务在运行。
     */
    @JvmStatic
    fun maybePreload(context: Context) {
        // P0 内存泄漏修复：在 scope.launch 前转为 applicationContext，
        // 避免协程体捕获 Activity context 导致 Activity 在等待 mutex 期间泄漏。
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return
        // 节流：30 分钟内不重复触发
        val now = System.currentTimeMillis()
        val last = getLastRunTime(appContext)
        if (now - last < MIN_INTERVAL_MS) {
            Log.d(TAG, "距上次预下载不足 30 分钟，跳过")
            return
        }
        // 网络检查
        val onWifi = isOnWifi(appContext)
        if (!onWifi && !isAllowOnCellular(appContext)) {
            Log.d(TAG, "当前非 WiFi 且未允许蜂窝，跳过预下载")
            return
        }
        scope.launch {
            runMutex.withLock {
                runPreloadInternal(appContext)
            }
        }
    }

    private fun runPreloadInternal(context: Context) {
        val appContext = context.applicationContext
        // 更新运行时间
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_RUN_TIME, System.currentTimeMillis()).apply()

        val usageStore = GameUsageStore(appContext)
        val candidates = scoreAndRankModules(appContext, usageStore)
        if (candidates.isEmpty()) {
            Log.d(TAG, "无可预下载的候选模块")
            return
        }

        val toPreload = candidates.take(MAX_PRELOAD_PER_RUN)
        Log.i(TAG, "开始预下载 ${toPreload.size} 个模块: ${toPreload.map { it.first.id }}")

        for ((manifest, _) in toPreload) {
            // 再次确认未安装
            if (ModuleManager.isModuleInstalled(appContext, manifest.id)) continue
            // 检查是否已在下载（由 ModuleDownloader 内部 activeDownloads 保护，这里只是日志）
            Log.d(TAG, "预下载模块: ${manifest.id} (${manifest.name})")

            ModuleDownloadManager.downloadModule(appContext, manifest, object : ModuleDownloader.Callback {
                override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
                    // 预下载不展示进度，仅 debug 日志
                    Log.d(TAG, "预下载进度: $moduleId $downloaded/$total @${speedKbps}KB/s")
                }

                override fun onComplete(moduleId: String, file: java.io.File) {
                    Log.i(TAG, "预下载完成: $moduleId -> ${file.absolutePath}")
                }

                override fun onError(moduleId: String, message: String) {
                    Log.w(TAG, "预下载失败: $moduleId, $message")
                }

                override fun onError(moduleId: String, errorCode: Int, message: String) {
                    Log.w(TAG, "预下载失败: $moduleId (code=$errorCode), $message")
                }

                override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) {
                    Log.d(TAG, "预下载切换源: $moduleId -> #$sourceIndex $url")
                }
            })
        }
    }

    /**
     * 对所有可用模块评分并排序，返回候选列表（已排除已安装/内置/超限模块）。
     */
    private fun scoreAndRankModules(
        context: Context,
        usageStore: GameUsageStore
    ): List<Pair<ModuleManifest, Int>> {
        val now = System.currentTimeMillis()
        val favorites = usageStore.favoriteIds ?: emptySet()
        val recentIds = usageStore.getRecentIds(12).toSet()

        return ModuleManager.getAvailableModules()
            .asSequence()
            .filter { !it.builtIn }                       // 跳过内置
            .filter { !it.isBaseFramework }               // 跳过基础框架
            .filter { it.downloadUrl.isNotEmpty() }       // 必须有下载地址
            .filter { it.fileSize in 1..MAX_FILE_SIZE_BYTES } // 体积在合理范围
            .filter { !ModuleManager.isModuleInstalled(context, it.id) } // 未安装
            .filter { !ModuleDownloadManager.isModuleDownloaded(context, it) } // 未下载
            .map { manifest ->
                var score = 0
                val gameId = manifest.gameId.ifEmpty { manifest.id }
                // 1. 收藏加成
                if (favorites.contains(gameId)) score += SCORE_FAVORITE
                // 2. 最近游玩加成
                if (gameId in recentIds) score += SCORE_RECENT
                // 3. 启动次数加成
                val playCount = usageStore.getPlayCount(gameId)
                score += minOf(playCount * SCORE_PLAY_COUNT_PER, SCORE_PLAY_COUNT_MAX)
                // 4. 最近 7 天内游玩额外加成（时间衰减）
                val lastPlayed = usageStore.getLastPlayedAt(gameId)
                if (lastPlayed > 0 && now - lastPlayed < RECENT_WINDOW_MS) {
                    // 已通过 recentIds 加成的不再重复
                }
                // 5. 精选模块加成
                if (manifest.featured) score += SCORE_FEATURED
                manifest to score
            }
            .filter { it.second > 0 } // 只预下载有评分的（即用户感兴趣的）
            .sortedByDescending { it.second }
            .toList()
    }

    private fun registerNetworkCallback(context: Context) {
        if (networkCallback != null) return
        // P0 内存泄漏修复：保存 applicationContext 用于 shutdown 时注销回调
        val appContext = context.applicationContext
        moduleStoreAppContext = appContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.d(TAG, "WiFi 已连接，触发预下载")
                    // 回调在主线程，切到 IO 线程；使用 appContext 避免捕获 Activity
                    mainHandler.post { maybePreload(appContext) }
                }
            }
        }
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                callback
            )
            networkCallback = callback
            Log.d(TAG, "WiFi 网络回调已注册")
        } catch (e: Exception) {
            Log.w(TAG, "注册网络回调失败: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback(context: Context) {
        val cb = networkCallback ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.unregisterNetworkCallback(cb)
        networkCallback = null
        Log.d(TAG, "WiFi 网络回调已注销")
    }

    /**
     * P0 内存泄漏修复：取消单例 CoroutineScope 并注销网络回调。
     * 供 App.onTerminate 或测试用例调用。
     */
    @JvmStatic
    fun shutdown() {
        scope.cancel()
        networkCallback?.let { cb ->
            try {
                // networkCallback 注册时使用 applicationContext，这里也用 applicationContext 注销
                val cm = moduleStoreAppContext?.getSystemService(Context.CONNECTIVITY_SERVICE)
                        as? ConnectivityManager
                cm?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "shutdown 注销网络回调失败: ${e.message}")
            }
            networkCallback = null
        }
    }

    /** shutdown 时用于注销 NetworkCallback 的 applicationContext（在 registerNetworkCallback 时记录） */
    private var moduleStoreAppContext: Context? = null
}

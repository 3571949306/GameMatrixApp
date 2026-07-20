package com.gamecenter.app.modules.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.SecureOkHttpFactory
import com.gamecenter.app.modules.store.model.StoreUiConfig
import okhttp3.Request
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * 商店 UI 配置仓库接口（P2.3）。
 *
 * 职责：
 * - 读取上次成功缓存的 store-ui.json（立即返回，不阻塞）
 * - 后台请求远程 store-ui.json
 * - 支持 ETag 协商
 * - 校验 schemaVersion=1
 * - 校验 minHostVersionCode <= BuildConfig.VERSION_CODE
 * - 原子写入新缓存
 * - 通知观察者
 *
 * 降级优先级（不得在网络请求开始时删除旧缓存）：
 * 1. 最新有效远程配置（满足 minHostVersionCode）
 * 2. 上次成功缓存（file，满足 minHostVersionCode）
 * 3. assets/store-ui.json（满足 minHostVersionCode）
 * 4. StoreUiConfig.defaultConfig()（最小硬编码默认布局）
 *
 * 严禁：
 * - 从服务器动态下载并执行任意代码 / 脚本
 * - 反射任意类
 * - 解析非白名单的动作类型
 */
interface StoreUiConfigRepository {

    /** 立即返回缓存配置（可能为 null，调用方需降级到 defaultConfig） */
    fun getCachedConfig(): StoreUiConfig?

    /**
     * 触发远程刷新。callback 在主线程回调。
     * 失败时若存在旧缓存，仍通过 Result.success 返回旧缓存（保证 UI 可用）；
     * 仅当既无远程又无任何缓存时才 Result.failure（调用方应使用 defaultConfig）。
     */
    fun refresh(callback: ((Result<StoreUiConfig>) -> Unit)? = null)

    /** 注册配置观察者，配置更新时回调（主线程） */
    fun addObserver(observer: (StoreUiConfig) -> Unit)

    /** 注销观察者 */
    fun removeObserver(observer: (StoreUiConfig) -> Unit)
}

/**
 * 默认实现：基于 OkHttp + 文件缓存 + ETag。
 *
 * 缓存路径：filesDir/store/store-ui.json
 * 临时文件：filesDir/store/store-ui.json.tmp
 * ETag 存储：SharedPreferences "store_ui_prefs"
 *
 * Feature flag STORE_REMOTE_UI 关闭时，仅使用 assets + 文件缓存，不发起网络请求。
 */
class DefaultStoreUiConfigRepository private constructor(
    private val appContext: Context
) : StoreUiConfigRepository {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArrayList<(StoreUiConfig) -> Unit>()
    private val cachedRef = AtomicReference<StoreUiConfig?>(null)

    private val cacheDir: File by lazy { File(appContext.filesDir, "store").apply { mkdirs() } }
    private val cacheFile: File by lazy { File(cacheDir, "store-ui.json") }
    private val tmpFile: File by lazy { File(cacheDir, "store-ui.json.tmp") }

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** store-ui.json URL — 由 MODULES_URL 推导，与 catalog.json 同源 */
    private val configUrl: String by lazy {
        val modulesUrl = BuildConfig.MODULES_URL
        if (modulesUrl.endsWith("/modules.json")) {
            modulesUrl.removeSuffix("modules.json") + "store-ui.json"
        } else {
            val base = modulesUrl.substringBeforeLast('/')
            "$base/store-ui.json"
        }
    }

    init {
        // 初始化时加载缓存到内存（缓存 > assets > default）
        cachedRef.set(loadFromCacheFile() ?: loadFromAssets() ?: StoreUiConfig.defaultConfig())
    }

    override fun getCachedConfig(): StoreUiConfig? = cachedRef.get()

    override fun refresh(callback: ((Result<StoreUiConfig>) -> Unit)?) {
        Thread {
            val result = refreshInternal()
            mainHandler.post {
                result.onSuccess { config -> notifyObservers(config) }
                callback?.invoke(result)
            }
        }.start()
    }

    private fun refreshInternal(): Result<StoreUiConfig> {
        // Feature flag 关闭：仅使用 assets/缓存，不发起网络请求
        if (!BuildConfig.STORE_REMOTE_UI) {
            val config = cachedRef.get()
                ?: loadFromCacheFile()
                ?: loadFromAssets()
                ?: StoreUiConfig.defaultConfig()
            return Result.success(config)
        }

        return try {
            val client = SecureOkHttpFactory.buildModuleClient()
            val cachedEtag = prefs.getString(KEY_ETAG, null)
            val requestBuilder = Request.Builder().url(configUrl)
            if (!cachedEtag.isNullOrEmpty()) {
                requestBuilder.header("If-None-Match", cachedEtag)
                Log.d(TAG, "ETag 协商: 发送 If-None-Match=$cachedEtag")
            }
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            val responseCode = response.code

            // 304 Not Modified
            if (responseCode == HTTP_NOT_MODIFIED) {
                Log.d(TAG, "远程 UI 配置未修改 (304)，使用本地缓存")
                response.close()
                val cached = cachedRef.get()
                    ?: loadFromCacheFile()
                    ?: loadFromAssets()
                    ?: StoreUiConfig.defaultConfig()
                return Result.success(cached)
            }

            if (!response.isSuccessful) {
                response.close()
                Log.w(TAG, "远程 UI 配置请求失败: HTTP $responseCode，降级使用缓存")
                return degradeToCacheOrFailure(RuntimeException("HTTP $responseCode"))
            }

            val serverEtag = response.header("ETag")
            val body = response.body?.string() ?: run {
                response.close()
                return degradeToCacheOrFailure(RuntimeException("响应体为空"))
            }
            response.close()

            // 1. 写入 tmp
            tmpFile.writeText(body, Charsets.UTF_8)

            // 2. 解析验证
            val config = try {
                StoreUiConfig.fromJson(body)
            } catch (e: Exception) {
                Log.w(TAG, "UI 配置解析失败: ${e.message}，删除 tmp 保留旧缓存")
                tmpFile.delete()
                return degradeToCacheOrFailure(e)
            }

            // 3. 检查 minHostVersionCode（不满足时降级到默认布局，不使用该配置）
            if (config.minHostVersionCode > BuildConfig.VERSION_CODE) {
                Log.w(
                    TAG,
                    "UI 配置 minHostVersionCode=${config.minHostVersionCode} > 当前 VERSION_CODE=${BuildConfig.VERSION_CODE}，" +
                        "降级使用默认布局"
                )
                tmpFile.delete()
                return Result.success(StoreUiConfig.defaultConfig())
            }

            // 4. 原子替换
            if (cacheFile.exists() && !cacheFile.delete()) {
                Log.w(TAG, "无法删除旧缓存文件，将尝试覆盖")
            }
            if (!tmpFile.renameTo(cacheFile)) {
                Log.w(TAG, "renameTo 失败，尝试直接写入")
                cacheFile.writeText(body, Charsets.UTF_8)
                tmpFile.delete()
            }

            // 5. 持久化 ETag
            if (!serverEtag.isNullOrEmpty()) {
                prefs.edit().putString(KEY_ETAG, serverEtag).apply()
            }

            // 6. 更新内存并通知
            cachedRef.set(config)
            Log.d(
                TAG,
                "UI 配置刷新成功: schemaV=${config.schemaVersion} pageV=${config.pageVersion} " +
                    "pages=${config.pages.size} sections=${config.pages["store_home"]?.sections?.size ?: 0}"
            )
            Result.success(config)
        } catch (e: Exception) {
            Log.w(TAG, "刷新远程 UI 配置失败: ${e.message}，降级使用缓存")
            degradeToCacheOrFailure(e)
        }
    }

    /** 降级到缓存；若缓存也不可用则返回 failure（调用方应使用 defaultConfig） */
    private fun degradeToCacheOrFailure(error: Throwable): Result<StoreUiConfig> {
        val cached = cachedRef.get()
            ?: loadFromCacheFile()
            ?: loadFromAssets()
        return if (cached != null) Result.success(cached) else Result.failure(error)
    }

    private fun loadFromCacheFile(): StoreUiConfig? {
        return try {
            if (!cacheFile.exists()) return null
            val body = cacheFile.readText(Charsets.UTF_8)
            val config = StoreUiConfig.fromJson(body)
            // 缓存文件也需校验 minHostVersionCode（防止旧版本配置污染）
            if (config.minHostVersionCode > BuildConfig.VERSION_CODE) {
                Log.w(TAG, "缓存 minHostVersionCode=${config.minHostVersionCode} 不满足，使用默认布局")
                return StoreUiConfig.defaultConfig()
            }
            cachedRef.set(config)
            Log.d(TAG, "从缓存文件加载 UI 配置: pages=${config.pages.size}")
            config
        } catch (e: Exception) {
            Log.w(TAG, "缓存文件解析失败: ${e.message}")
            null
        }
    }

    /** 从 assets/store-ui.json 加载；失败时返回 null，调用方降级到 defaultConfig */
    private fun loadFromAssets(): StoreUiConfig? {
        return try {
            val body = appContext.assets.open("store-ui.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val config = StoreUiConfig.fromJson(body)
            if (config.minHostVersionCode > BuildConfig.VERSION_CODE) {
                Log.w(TAG, "assets minHostVersionCode=${config.minHostVersionCode} 不满足，使用默认布局")
                return StoreUiConfig.defaultConfig()
            }
            Log.d(TAG, "从 assets/store-ui.json 加载 UI 配置: pages=${config.pages.size}")
            config
        } catch (e: Exception) {
            Log.w(TAG, "assets/store-ui.json 加载失败: ${e.message}")
            null
        }
    }

    private fun notifyObservers(config: StoreUiConfig) {
        for (observer in observers) {
            try {
                observer(config)
            } catch (e: Exception) {
                Log.w(TAG, "观察者回调失败: ${e.message}")
            }
        }
    }

    override fun addObserver(observer: (StoreUiConfig) -> Unit) {
        observers.addIfAbsent(observer)
    }

    override fun removeObserver(observer: (StoreUiConfig) -> Unit) {
        observers.remove(observer)
    }

    companion object {
        private const val TAG = "StoreUiConfigRepo"
        private const val PREFS_NAME = "store_ui_prefs"
        private const val KEY_ETAG = "store_ui_etag"
        private const val HTTP_NOT_MODIFIED = 304

        @Volatile private var instance: DefaultStoreUiConfigRepository? = null

        fun getInstance(context: Context): DefaultStoreUiConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: DefaultStoreUiConfigRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

package com.gamecenter.app.modules.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.SecureOkHttpFactory
import com.gamecenter.app.modules.store.model.StoreCatalog
import okhttp3.Request
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * 商店目录仓库接口（P1.6）。
 *
 * 职责：
 * - 读取上次成功缓存（立即返回，不阻塞）
 * - 后台请求远程目录
 * - 支持 ETag 协商
 * - 复用 SecureOkHttpFactory 与 ModuleManager 一致的安全策略
 * - 验证 JSON 结构
 * - 原子写入新缓存
 * - 通知观察者
 *
 * 降级优先级（不得在网络请求开始时删除旧缓存）：
 * 1. 最新有效远程目录
 * 2. 上次成功缓存（file）
 * 3. assets/catalog.json（v2 格式）
 * 4. assets/modules.json（v1 格式，转换）
 * 5. StoreCatalog.rescueCatalog()（最小硬编码）
 */
interface StoreCatalogRepository {

    /** 立即返回缓存目录（可能为 null，调用方需降级到 assets） */
    fun getCachedCatalog(): StoreCatalog?

    /**
     * 触发远程刷新。callback 在主线程回调。
     * 失败时若存在旧缓存，仍通过 Result.success 返回旧缓存（保证 UI 可用）；
     * 仅当既无远程又无任何缓存时才 Result.failure。
     */
    fun refresh(callback: ((Result<StoreCatalog>) -> Unit)? = null)

    /** 注册目录观察者，目录更新时回调（主线程） */
    fun addObserver(observer: (StoreCatalog) -> Unit)

    /** 注销观察者 */
    fun removeObserver(observer: (StoreCatalog) -> Unit)
}

/**
 * 默认实现：基于 OkHttp + 文件缓存 + ETag。
 *
 * 缓存路径：filesDir/store/catalog.json
 * 临时文件：filesDir/store/catalog.json.tmp
 * ETag 存储：SharedPreferences "store_catalog_prefs"
 *
 * 原子替换流程：
 * 1. 下载到 catalog.json.tmp
 * 2. 解析验证（失败则删除 tmp，保留旧 catalog.json）
 * 3. 检查 schemaVersion 和 catalogVersion
 * 4. catalog.json.tmp → catalog.json (renameTo)
 * 5. 持久化 ETag
 * 6. 更新内存引用并通知观察者
 *
 * Feature flag STORE_REMOTE_CATALOG 关闭时，仅使用 assets + 文件缓存，不发起网络请求。
 */
class DefaultStoreCatalogRepository private constructor(
    private val appContext: Context
) : StoreCatalogRepository {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArrayList<(StoreCatalog) -> Unit>()
    private val cachedRef = AtomicReference<StoreCatalog?>(null)

    private val cacheDir: File by lazy { File(appContext.filesDir, "store").apply { mkdirs() } }
    private val cacheFile: File by lazy { File(cacheDir, "catalog.json") }
    private val tmpFile: File by lazy { File(cacheDir, "catalog.json.tmp") }

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** catalog.json URL — 由 MODULES_URL 推导，保持同源 */
    private val catalogUrl: String by lazy {
        val modulesUrl = BuildConfig.MODULES_URL
        if (modulesUrl.endsWith("/modules.json")) {
            modulesUrl.removeSuffix("modules.json") + "catalog.json"
        } else {
            val base = modulesUrl.substringBeforeLast('/')
            "$base/catalog.json"
        }
    }

    init {
        // 初始化时加载缓存到内存（缓存 > assets > rescue）
        cachedRef.set(loadFromCacheFile() ?: loadFromAssets())
    }

    override fun getCachedCatalog(): StoreCatalog? = cachedRef.get()

    override fun refresh(callback: ((Result<StoreCatalog>) -> Unit)?) {
        Thread {
            val result = refreshInternal()
            mainHandler.post {
                result.onSuccess { catalog -> notifyObservers(catalog) }
                callback?.invoke(result)
            }
        }.start()
    }

    private fun refreshInternal(): Result<StoreCatalog> {
        // Feature flag 关闭：仅使用 assets/缓存，不发起网络请求
        if (!BuildConfig.STORE_REMOTE_CATALOG) {
            val catalog = cachedRef.get()
                ?: loadFromCacheFile()
                ?: loadFromAssets()
                ?: StoreCatalog.rescueCatalog()
            return Result.success(catalog)
        }

        return try {
            val client = SecureOkHttpFactory.buildModuleClient()
            val cachedEtag = prefs.getString(KEY_ETAG, null)
            val requestBuilder = Request.Builder().url(catalogUrl)
            if (!cachedEtag.isNullOrEmpty()) {
                requestBuilder.header("If-None-Match", cachedEtag)
                Log.d(TAG, "ETag 协商: 发送 If-None-Match=$cachedEtag")
            }
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            val responseCode = response.code

            // 304 Not Modified
            if (responseCode == HTTP_NOT_MODIFIED) {
                Log.d(TAG, "远程目录未修改 (304)，使用本地缓存")
                response.close()
                val cached = cachedRef.get()
                    ?: loadFromCacheFile()
                    ?: loadFromAssets()
                    ?: StoreCatalog.rescueCatalog()
                return Result.success(cached)
            }

            if (!response.isSuccessful) {
                response.close()
                Log.w(TAG, "远程目录请求失败: HTTP $responseCode，降级使用缓存")
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
            val catalog = try {
                StoreCatalog.fromJson(body)
            } catch (e: Exception) {
                Log.w(TAG, "目录解析失败: ${e.message}，删除 tmp 保留旧缓存")
                tmpFile.delete()
                return degradeToCacheOrFailure(e)
            }

            // 3. 检查 schemaVersion
            if (catalog.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
                Log.w(TAG, "不支持的 schemaVersion: ${catalog.schemaVersion}，删除 tmp 保留旧缓存")
                tmpFile.delete()
                return degradeToCacheOrFailure(RuntimeException("不支持的 schemaVersion=${catalog.schemaVersion}"))
            }

            // 4. 原子替换（先删旧文件再 rename，兼容部分设备 renameTo 不能覆盖）
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
            cachedRef.set(catalog)
            Log.d(
                TAG,
                "目录刷新成功: schemaV=${catalog.schemaVersion} catalogV=${catalog.catalogVersion} " +
                    "modules=${catalog.modules.size} categories=${catalog.categories.size} banners=${catalog.heroBanners.size}"
            )
            Result.success(catalog)
        } catch (e: Exception) {
            Log.w(TAG, "刷新远程目录失败: ${e.message}，降级使用缓存")
            degradeToCacheOrFailure(e)
        }
    }

    /** 降级到缓存；若缓存也不可用则返回 failure（保证不丢失错误信息） */
    private fun degradeToCacheOrFailure(error: Throwable): Result<StoreCatalog> {
        val cached = cachedRef.get()
            ?: loadFromCacheFile()
            ?: loadFromAssets()
        return if (cached != null) Result.success(cached) else Result.failure(error)
    }

    private fun loadFromCacheFile(): StoreCatalog? {
        return try {
            if (!cacheFile.exists()) return null
            val body = cacheFile.readText(Charsets.UTF_8)
            val catalog = StoreCatalog.fromJson(body)
            cachedRef.set(catalog)
            Log.d(TAG, "从缓存文件加载目录: ${catalog.modules.size} modules")
            catalog
        } catch (e: Exception) {
            Log.w(TAG, "缓存文件解析失败: ${e.message}")
            null
        }
    }

    /**
     * 从 assets 加载目录。优先 catalog.json (v2)，失败再加载 modules.json (v1)。
     * 兼容旧服务器格式，不让旧缓存或旧服务器格式导致商店闪退。
     */
    private fun loadFromAssets(): StoreCatalog? {
        return try {
            val body = appContext.assets.open("catalog.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val catalog = StoreCatalog.fromJson(body)
            Log.d(TAG, "从 assets/catalog.json 加载目录: ${catalog.modules.size} modules")
            catalog
        } catch (_: Exception) {
            try {
                val body = appContext.assets.open("modules.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val catalog = StoreCatalog.fromJson(body)
                Log.d(TAG, "从 assets/modules.json 兼容加载: ${catalog.modules.size} modules")
                catalog
            } catch (e: Exception) {
                Log.w(TAG, "assets 加载失败: ${e.message}")
                null
            }
        }
    }

    private fun notifyObservers(catalog: StoreCatalog) {
        for (observer in observers) {
            try {
                observer(catalog)
            } catch (e: Exception) {
                Log.w(TAG, "观察者回调失败: ${e.message}")
            }
        }
    }

    override fun addObserver(observer: (StoreCatalog) -> Unit) {
        observers.addIfAbsent(observer)
    }

    override fun removeObserver(observer: (StoreCatalog) -> Unit) {
        observers.remove(observer)
    }

    companion object {
        private const val TAG = "StoreCatalogRepo"
        private const val PREFS_NAME = "store_catalog_prefs"
        private const val KEY_ETAG = "catalog_etag"
        private const val HTTP_NOT_MODIFIED = 304
        private val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2)

        @Volatile private var instance: DefaultStoreCatalogRepository? = null

        fun getInstance(context: Context): DefaultStoreCatalogRepository {
            return instance ?: synchronized(this) {
                instance ?: DefaultStoreCatalogRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

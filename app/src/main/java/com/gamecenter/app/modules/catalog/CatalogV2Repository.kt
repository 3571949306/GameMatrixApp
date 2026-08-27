package com.gamecenter.app.modules.catalog

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.store.DefaultStoreCatalogRepository
import com.gamecenter.app.core.common.ModuleManifest
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * V2 read model over the existing authoritative ModuleManager state.
 * No second install database or catalog cache is created here.
 */
class CatalogV2Repository private constructor(private val context: Context) {
    private val catalogExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "catalog-v2-loader")
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCatalog(callback: (Result<CatalogV2>) -> Unit) {
        loadFromModuleManager(callback)
    }

    fun refresh(callback: (Result<CatalogV2>) -> Unit) {
        DefaultStoreCatalogRepository.getInstance(context).refresh { refreshResult ->
            loadFromModuleManager(callback, refreshResult.exceptionOrNull())
        }
    }

    private fun loadFromModuleManager(
        callback: (Result<CatalogV2>) -> Unit,
        refreshError: Throwable? = null
    ) {
        val delivered = AtomicBoolean(false)
        catalogExecutor.execute {
            ModuleManager.loadModuleList(context) { manifests, error ->
                if (!delivered.compareAndSet(false, true)) return@loadModuleList
                catalogExecutor.execute {
                    val result = buildCatalogResult(manifests, error, refreshError)
                    mainHandler.post { callback(result) }
                }
            }
        }
    }

    private fun buildCatalogResult(
        manifests: List<ModuleManifest>?,
        error: String?,
        refreshError: Throwable?
    ): Result<CatalogV2> {
        // 冷启动 NPE 修复：loadModuleList 回调可能从 Java 侧传入 null，
        // 此处兜底为 emptyList，避免后续遍历触发 Iterator.next() on null。
        val safeManifests = manifests ?: emptyList()
        if (safeManifests.isEmpty() && error != null) {
            return Result.failure(IllegalStateException(error))
        }
        return runCatching {
            val parsed = loadValidatedCatalog()
            val catalog = if (parsed != null) mergeAuthority(parsed, safeManifests) else CatalogV2(
                catalogVersion = 0,
                source = "module_manager_fallback",
                offline = true,
                modules = safeManifests.map(CatalogModuleMapper::fromManifest)
            )
            catalog.copy(
                offline = catalog.offline || error != null || refreshError != null,
                modules = catalog.modules?.sortedWith(
                    compareBy<CatalogModule> { it.sortOrder }.thenBy { it.name }
                ) ?: emptyList()
            )
        }
    }

    /** Reads the already signature-checked store cache; this creates no second cache. */
    private fun loadValidatedCatalog(): CatalogV2? {
        // 目录双轨已收敛：仅接受签名/内置的正式 V2 目录，旧版 assets/modules.json 不再作为目录来源。
        val candidates = listOf(
            Triple(File(context.filesDir, "store/catalog.json"), "signed_cache", true),
            Triple(null, "asset_catalog", true)
        )
        return candidates.firstNotNullOfOrNull { (file, source) ->
            val raw = runCatching {
                if (file != null) {
                    if (!file.isFile) return@runCatching null
                    file.readText(Charsets.UTF_8)
                } else {
                    context.assets.open("catalog.json")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }.getOrNull() ?: return@firstNotNullOfOrNull null
            runCatching { CatalogV2Parser.parse(raw, source) }.getOrNull()
        }
    }

    private fun mergeAuthority(catalog: CatalogV2, manifests: List<ModuleManifest>?): CatalogV2 {
        // 冷启动 NPE 修复：catalog.modules / manifests 在异常路径下可能为 null，
        // 兜底为 emptyList 避免遍历崩溃。
        val safeManifests = manifests ?: emptyList()
        val catalogModules = catalog.modules ?: emptyList()
        val authority = safeManifests.associateBy { it.id }
        val merged = catalogModules.map { module ->
            authority[module.id]
                ?.takeIf { CatalogAuthorityMatcher.matches(module, it) }
                ?.let { module.copy(legacyManifest = it) }
                ?: module
        }.toMutableList()
        val known = merged.mapTo(mutableSetOf()) { it.id }
        safeManifests.filterNot { it.id in known }
            .mapTo(merged, CatalogModuleMapper::fromManifest)
        CatalogPackageTrustRegistry.replace(merged)
        return catalog.copy(modules = merged)
    }

    companion object {
        @Volatile private var instance: CatalogV2Repository? = null

        fun getInstance(context: Context): CatalogV2Repository =
            instance ?: synchronized(this) {
                instance ?: CatalogV2Repository(context.applicationContext).also { instance = it }
            }
    }
}

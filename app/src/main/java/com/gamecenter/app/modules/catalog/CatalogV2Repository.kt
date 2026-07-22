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
        manifests: List<ModuleManifest>,
        error: String?,
        refreshError: Throwable?
    ): Result<CatalogV2> {
        if (manifests.isEmpty() && error != null) {
            return Result.failure(IllegalStateException(error))
        }
        return runCatching {
            val parsed = loadValidatedCatalog()
            val catalog = if (parsed != null) mergeAuthority(parsed, manifests) else CatalogV2(
                catalogVersion = 0,
                source = "module_manager_fallback",
                offline = true,
                modules = manifests.map(LegacyCatalogAdapter::fromManifest)
            )
            catalog.copy(
                offline = catalog.offline || error != null || refreshError != null,
                modules = catalog.modules.sortedWith(
                    compareBy<CatalogModule> { it.sortOrder }.thenBy { it.name }
                )
            )
        }
    }

    /** Reads the already signature-checked store cache; this creates no second cache. */
    private fun loadValidatedCatalog(): CatalogV2? {
        val candidates = listOf(
            Triple(File(context.filesDir, "store/catalog.json"), "signed_cache", true),
            Triple(null, "asset_catalog", true),
            Triple(null, "legacy_assets", true)
        )
        return candidates.firstNotNullOfOrNull { (file, source, _) ->
            val raw = runCatching {
                if (file != null) {
                    if (!file.isFile) return@runCatching null
                    file.readText(Charsets.UTF_8)
                } else {
                    val assetName = if (source == "asset_catalog") "catalog.json" else "modules.json"
                    context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }.getOrNull() ?: return@firstNotNullOfOrNull null
            runCatching { CatalogV2Parser.parse(raw, source) }.getOrNull()
        }
    }

    private fun mergeAuthority(catalog: CatalogV2, manifests: List<ModuleManifest>): CatalogV2 {
        val authority = manifests.associateBy { it.id }
        val merged = catalog.modules.map { module ->
            authority[module.id]
                ?.takeIf { CatalogAuthorityMatcher.matches(module, it) }
                ?.let { module.copy(legacyManifest = it) }
                ?: module
        }.toMutableList()
        val known = merged.mapTo(mutableSetOf()) { it.id }
        manifests.filterNot { it.id in known }
            .mapTo(merged, LegacyCatalogAdapter::fromManifest)
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

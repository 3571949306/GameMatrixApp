package com.gamecenter.app.modules.catalog

import com.gamecenter.app.core.common.ModuleManifest
import java.util.concurrent.ConcurrentHashMap

/** Binds the legacy downloader record to the exact package authenticated by Catalog V2. */
object CatalogAuthorityMatcher {
    fun matches(module: CatalogModule, manifest: ModuleManifest): Boolean {
        if (module.id != manifest.id || module.versionCode != manifest.versionCode) return false
        if (module.deliveryType == DeliveryType.BUILTIN) return manifest.builtIn
        val inferred = CatalogModuleMapper.fromManifest(manifest)
        if (module.runtimeType != inferred.runtimeType || module.deliveryType != inferred.deliveryType) return false
        val pkg = module.packageInfo
        return pkg.fileName == manifest.fileName &&
            pkg.fileSize == manifest.fileSize &&
            pkg.sha256.equals(manifest.sha256, ignoreCase = true) &&
            pkg.downloadUrl == manifest.downloadUrl &&
            pkg.fallbackUrl == manifest.fallbackUrl &&
            pkg.githubUrl == manifest.githubUrl &&
            module.minHostVersionCode == manifest.minAppVersionCode &&
            module.maxHostVersionCode == manifest.maxAppVersionCode
    }
}

object CatalogPackageTrustRegistry {
    private data class Binding(
        val versionCode: Int,
        val fileName: String,
        val fileSize: Long,
        val sha256: String,
        val downloadUrl: String,
        val fallbackUrl: String,
        val githubUrl: String
    )

    private val bindings = ConcurrentHashMap<String, Binding>()

    fun replace(modules: Collection<CatalogModule>) {
        bindings.clear()
        modules.filter { module ->
            module.deliveryType != DeliveryType.BUILTIN &&
                !module.packageInfo.fileName.endsWith(".apk", ignoreCase = true) &&
                module.legacyManifest != null
        }.forEach { module ->
            val pkg = module.packageInfo
            bindings[module.id] = Binding(
                versionCode = module.versionCode,
                fileName = pkg.fileName,
                fileSize = pkg.fileSize,
                sha256 = pkg.sha256.lowercase(),
                downloadUrl = pkg.downloadUrl,
                fallbackUrl = pkg.fallbackUrl,
                githubUrl = pkg.githubUrl
            )
        }
    }

    fun isTrusted(manifest: ModuleManifest): Boolean {
        val binding = bindings[manifest.id] ?: return false
        return binding.versionCode == manifest.versionCode &&
            binding.fileName == manifest.fileName &&
            binding.fileSize == manifest.fileSize &&
            binding.sha256 == manifest.sha256.lowercase() &&
            binding.downloadUrl == manifest.downloadUrl &&
            binding.fallbackUrl == manifest.fallbackUrl &&
            binding.githubUrl == manifest.githubUrl
    }

    internal fun clearForTest() = bindings.clear()
}

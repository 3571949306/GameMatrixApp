package com.gamecenter.app.modules.catalog

import com.gamecenter.app.core.common.ModuleManifest
import org.json.JSONObject

/** Converts the deployed legacy modules.json contract into the single V2 model. */
object LegacyCatalogAdapter {

    fun adapt(rawJson: String, source: String = "legacy"): CatalogV2 {
        val root = JSONObject(rawJson)
        val array = root.optJSONArray("modules")
            ?: throw IllegalArgumentException("Legacy catalog does not contain a modules array")
        val modules = buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(fromManifest(ModuleManifest.fromJson(json)))
            }
        }
        return CatalogV2(
            catalogVersion = root.optInt("version", root.optInt("catalogVersion", 0)),
            generatedAt = root.optString("generatedAt", ""),
            source = source,
            offline = source !in setOf("remote", "signed_cache"),
            modules = modules
        )
    }

    fun fromManifest(manifest: ModuleManifest): CatalogModule {
        val runtime = inferRuntime(manifest)
        val delivery = inferDelivery(manifest, runtime)
        return CatalogModule(
            id = manifest.id,
            name = manifest.name,
            shortDescription = manifest.shortDescription.ifEmpty { manifest.gameDesc },
            description = manifest.description,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            runtimeType = runtime,
            deliveryType = delivery,
            route = if (runtime == RuntimeType.FLUTTER) "/${manifest.id}" else "",
            entryClass = manifest.entryClass,
            entry = if (runtime == RuntimeType.WEB) "index.html" else "",
            serviceType = if (runtime == RuntimeType.NATIVE_SERVICE) manifest.id else "",
            launcherId = if (runtime == RuntimeType.UNITY) manifest.id else "",
            enabled = manifest.enabled,
            required = manifest.required,
            featured = manifest.featured,
            sortOrder = manifest.sortOrder,
            minHostVersionCode = manifest.minAppVersionCode,
            maxHostVersionCode = manifest.maxAppVersionCode,
            category = manifest.storeCategory.ifEmpty { manifest.category },
            permissions = manifest.permissions.mapIndexed { index, id ->
                CatalogPermission(id, manifest.permissionsDescription.getOrNull(index).orEmpty())
            },
            dependencies = manifest.depends,
            tags = manifest.tags,
            screenshots = manifest.screenshots,
            changelog = manifest.changelog.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
            navigationContribution = manifest.navigationContribution,
            packageInfo = CatalogPackage(
                fileName = manifest.fileName,
                fileSize = manifest.fileSize,
                downloadUrl = manifest.downloadUrl,
                fallbackUrl = manifest.fallbackUrl,
                githubUrl = manifest.githubUrl,
                sha256 = manifest.sha256
            ),
            legacyManifest = manifest
        )
    }

    private fun inferRuntime(manifest: ModuleManifest): RuntimeType {
        val kind = manifest.kind.lowercase()
        return when {
            kind.startsWith("unity") || manifest.type.equals("unity", true) -> RuntimeType.UNITY
            kind == "web" || manifest.fileName.endsWith(".html", true) -> RuntimeType.WEB
            kind in setOf("asset", "config-pack", "content-pack") -> RuntimeType.ASSET
            manifest.id == "vpn" || kind == "native-service" -> RuntimeType.NATIVE_SERVICE
            kind == "flutter" -> RuntimeType.FLUTTER
            else -> RuntimeType.ANDROID
        }
    }

    private fun inferDelivery(manifest: ModuleManifest, runtimeType: RuntimeType): DeliveryType {
        if (manifest.builtIn || manifest.fileName.isEmpty()) return DeliveryType.BUILTIN
        return when {
            runtimeType == RuntimeType.UNITY -> DeliveryType.CONTENT
            manifest.fileName.endsWith(".apk", true) -> DeliveryType.APK
            else -> DeliveryType.ZIP
        }
    }
}

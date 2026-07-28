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
            storeCategory = mapLegacyStoreCategory(manifest.storeCategory.ifEmpty { manifest.category }, manifest.id, manifest.type),
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
            legacyManifest = manifest,
            details = manifest.details,
            privacy = manifest.privacy
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

    /**
     * 将旧版自由格式 storeCategory 字符串映射到新的标准枚举。
     * 旧值包括 "game", "tools", "browser", "ai", "vpn" 等。
     */
    private fun mapLegacyStoreCategory(legacyValue: String, moduleId: String, moduleType: String): StoreCategory? {
        // 先尝试直接匹配新枚举的 wireValue
        StoreCategory.fromWire(legacyValue)?.let { return it }
        // 再按旧值映射
        return when (legacyValue.lowercase().trim()) {
            "game", "games" -> StoreCategory.ENTERTAINMENT_VERSUS
            "browser" -> StoreCategory.READING_BROWSING
            "tools", "tool" -> StoreCategory.DEVICE_NETWORK
            "ai" -> StoreCategory.TEXT_CREATION
            "vpn" -> StoreCategory.DEVICE_NETWORK
            "wrongbook", "wrong_book" -> StoreCategory.LEARNING_ORGANIZATION
            "tts", "tts_voice", "voice" -> StoreCategory.TEXT_CREATION
            else -> when {
                moduleType.equals("game", true) -> StoreCategory.ENTERTAINMENT_VERSUS
                moduleId == "browser" -> StoreCategory.READING_BROWSING
                moduleId == "wrongbook" -> StoreCategory.LEARNING_ORGANIZATION
                moduleId == "ai" -> StoreCategory.TEXT_CREATION
                moduleId == "vpn" -> StoreCategory.DEVICE_NETWORK
                moduleId == "tools" -> StoreCategory.DEVICE_NETWORK
                else -> null
            }
        }
    }
}

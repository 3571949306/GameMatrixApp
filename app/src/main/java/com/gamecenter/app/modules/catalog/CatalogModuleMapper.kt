package com.gamecenter.app.modules.catalog

import com.gamecenter.app.core.common.ModuleManifest

/**
 * 模块清单 → 目录展示模型映射（统一真源）。
 *
 * 目录解析已收敛到纯 Catalog V2（[CatalogV2Parser]）；本对象只负责把
 * ModuleManager 的权威清单（manifest）映射为展示用 [CatalogModule]，用于
 * 目录-清单握手（[CatalogAuthorityMatcher]）与商店展示，不具备 V1 目录适配能力。
 */
object CatalogModuleMapper {

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
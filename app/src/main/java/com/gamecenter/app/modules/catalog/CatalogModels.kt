package com.gamecenter.app.modules.catalog

import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.common.NavigationContribution

enum class RuntimeType(val wireValue: String) {
    FLUTTER("flutter"),
    WEB("web"),
    ASSET("asset"),
    ANDROID("android"),
    NATIVE_SERVICE("native_service"),
    UNITY("unity");

    companion object {
        fun fromWire(value: String): RuntimeType? = entries.firstOrNull {
            it.wireValue.equals(value.trim(), ignoreCase = true)
        }
    }
}

enum class DeliveryType(val wireValue: String) {
    BUILTIN("builtin"),
    APK("apk"),
    ZIP("zip"),
    CONTENT("content");

    companion object {
        fun fromWire(value: String): DeliveryType? = entries.firstOrNull {
            it.wireValue.equals(value.trim(), ignoreCase = true)
        }
    }
}

data class CatalogPackage(
    val fileName: String = "",
    val fileSize: Long = 0,
    val downloadUrl: String = "",
    val fallbackUrl: String = "",
    val githubUrl: String = "",
    val sha256: String = "",
    val signature: String = ""
)

data class CatalogAssets(
    val url: String = "",
    val sha256: String = "",
    val signature: String = ""
)

data class CatalogPermission(
    val id: String,
    val description: String = ""
)

data class CatalogModule(
    val id: String,
    val name: String,
    val shortDescription: String = "",
    val description: String = "",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val runtimeType: RuntimeType,
    val deliveryType: DeliveryType,
    val route: String = "",
    val entryClass: String = "",
    val entry: String = "",
    val serviceType: String = "",
    val launcherId: String = "",
    val enabled: Boolean = true,
    val required: Boolean = false,
    val featured: Boolean = false,
    val sortOrder: Int = 0,
    val minHostVersionCode: Int = 0,
    val maxHostVersionCode: Int = 0,
    val category: String = "other",
    val permissions: List<CatalogPermission> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val changelog: List<String> = emptyList(),
    val navigationContribution: NavigationContribution? = null,
    val packageInfo: CatalogPackage = CatalogPackage(),
    val assets: CatalogAssets = CatalogAssets(),
    val legacyManifest: ModuleManifest? = null
) {
    fun isCompatibleWithHost(hostVersionCode: Int): Boolean {
        if (minHostVersionCode > 0 && hostVersionCode < minHostVersionCode) return false
        if (maxHostVersionCode > 0 && hostVersionCode > maxHostVersionCode) return false
        return true
    }
}

data class CatalogV2(
    val schemaVersion: Int = 2,
    val catalogVersion: Int,
    val generatedAt: String = "",
    val source: String,
    val offline: Boolean,
    val modules: List<CatalogModule>
)

data class CatalogValidationIssue(
    val path: String,
    val code: String,
    val message: String
)

data class CatalogValidationResult(
    val issues: List<CatalogValidationIssue>
) {
    val isValid: Boolean get() = issues.isEmpty()
}

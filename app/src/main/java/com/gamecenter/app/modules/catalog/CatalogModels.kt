package com.gamecenter.app.modules.catalog

import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.common.NavigationContribution
import com.gamecenter.app.core.common.ModuleDetail
import com.gamecenter.app.core.common.PrivacyCard

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

/**
 * 商店结果分类：按用户完成的任务类型组织模块，而非技术交付类型。
 * 用于 Flutter 商店与原生商店的分组展示。
 */
enum class StoreCategory(val wireValue: String, val displayName: String) {
    ENTERTAINMENT_VERSUS("entertainment_versus", "娱乐与对战"),
    LEARNING_ORGANIZATION("learning_organization", "学习与整理"),
    READING_BROWSING("reading_browsing", "阅读与浏览"),
    TEXT_CREATION("text_creation", "文本与创作"),
    DEVICE_NETWORK("device_network", "设备与网络"),
    PERSONALIZATION("personalization", "个性化");

    companion object {
        fun fromWire(value: String?): StoreCategory? = value?.let { v ->
            entries.firstOrNull { it.wireValue.equals(v.trim(), ignoreCase = true) }
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
    val storeCategory: StoreCategory? = null,
    val permissions: List<CatalogPermission> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val changelog: List<String> = emptyList(),
    val navigationContribution: NavigationContribution? = null,
    val packageInfo: CatalogPackage = CatalogPackage(),
    val assets: CatalogAssets = CatalogAssets(),
    val legacyManifest: ModuleManifest? = null,
    /** 模块详情（#11.1）：价值描述、受众、离线能力、更新/卸载影响等 */
    val details: ModuleDetail? = null,
    /** 隐私卡（#11.2）：本地/云端数据、网络域、同步位置、保存期限、删除方式 */
    val privacy: PrivacyCard? = null
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

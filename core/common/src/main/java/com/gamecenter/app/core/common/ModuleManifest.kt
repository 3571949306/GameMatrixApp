package com.gamecenter.app.core.common

import org.json.JSONArray
import org.json.JSONObject

/**
 * 模块清单数据类（ModuleManifest v3）。
 *
 * P0 核心协议：这是整个项目中唯一的规范 ModuleManifest 模型。
 * - app/modules/ModuleManifest 是此类的 typealias/兼容包装
 * - core/module-host/ModuleManifest 是此类的 typealias/兼容包装
 * - StoreModule 仅用于目录序列化，最终应转换为此类
 *
 * v3 新增字段：
 * - navigationContribution: 模块导航贡献声明
 * - permissions: 模块所需权限列表
 * - kind: 更细粒度的模块类型（feature-apk, game-apk, unity-launcher, unity-content, config-pack）
 * - channel: stable/beta/alpha
 * - rollout: 灰度发布配置
 */
data class ModuleManifest(
    /** 模块唯一标识 */
    val id: String,
    /** 显示名称 */
    val name: String,
    /** 功能描述 */
    val description: String = "",
    /** 版本名称 */
    val versionName: String = "1.0.0",
    /** 版本号 */
    val versionCode: Int = 1,
    /** 入口类完整路径 */
    val entryClass: String = "",
    /** APK 文件名 */
    val fileName: String = "",
    /** 预期文件大小（字节） */
    val fileSize: Long = 0,
    /** SHA-256 哈希 */
    val sha256: String = "",
    /** 主下载地址 */
    val downloadUrl: String = "",
    /** 备用下载地址 */
    val fallbackUrl: String = "",
    /** GitHub 下载地址 */
    val githubUrl: String = "",
    /** 图标 URL */
    val iconUrl: String = "",
    /** 模块分类 */
    val category: String = "other",
    /** 商店分类 */
    val storeCategory: String = "other",
    /** 细粒度模块类型 */
    val kind: String = "feature-apk",
    /** 发布渠道 */
    val channel: String = "stable",
    /** 最低 App 版本号 */
    val minAppVersionCode: Int = 0,
    /** 最大 App 版本号，0 表示无限制 */
    val maxAppVersionCode: Int = 0,
    /** 依赖模块 ID 列表 */
    val depends: List<String> = emptyList(),
    /** 是否必装 */
    val required: Boolean = false,
    /** 是否为基础框架模块（不允许卸载） */
    val isBaseFramework: Boolean = false,
    /** 是否预装模块 */
    val builtIn: Boolean = false,
    /** 预装版本号 */
    val builtInVersionCode: Int = 0,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 是否推荐/精选 */
    val featured: Boolean = false,
    /** 排序权重 */
    val sortOrder: Int = 0,
    /** 模块类型（兼容旧字段） */
    val type: String = "module",
    /** 游戏 ID */
    val gameId: String = "",
    /** 游戏分类 */
    val gameCategory: String = "",
    /** 游戏描述 */
    val gameDesc: String = "",
    /** 内置 Activity 类名 */
    val activityClass: String = "",
    /** 简短描述 */
    val shortDescription: String = "",
    /** 截图 URL 列表 */
    val screenshots: List<String> = emptyList(),
    /** 更新日志 */
    val changelog: String = "",
    /** 权限说明 */
    val permissionsDescription: List<String> = emptyList(),
    /** 标签 */
    val tags: List<String> = emptyList(),
    /** 导航贡献声明 */
    val navigationContribution: NavigationContribution? = null,
    /** 所需权限列表 */
    val permissions: List<String> = emptyList(),
    /** 灰度发布百分比（0-100） */
    val rolloutPercent: Int = 100,
    /** 是否需要重启 */
    val restartRequired: Boolean = false,
    /** 是否允许回滚 */
    val rollbackAllowed: Boolean = true
) {

    /**
     * 获取所有可用的下载地址列表。
     */
    fun getAllDownloadUrls(): List<String> {
        return listOfNotNull(
            downloadUrl.takeIf { it.isNotEmpty() },
            fallbackUrl.takeIf { it.isNotEmpty() && it != downloadUrl },
            githubUrl.takeIf { it.isNotEmpty() && it != downloadUrl && it != fallbackUrl }
        )
    }

    /**
     * 判断当前模块是否应在当前 App 版本上可用。
     */
    fun isCompatibleWithHost(hostVersionCode: Int): Boolean {
        if (minAppVersionCode > 0 && hostVersionCode < minAppVersionCode) return false
        if (maxAppVersionCode > 0 && hostVersionCode > maxAppVersionCode) return false
        return true
    }

    /**
     * 判断当前用户是否符合灰度条件。
     * 简单实现：使用用户 ID 哈希对 100 取模。
     */
    fun isRolloutEnabledForUser(userHash: Int): Boolean {
        if (rolloutPercent >= 100) return true
        if (rolloutPercent <= 0) return false
        return (userHash % 100) < rolloutPercent
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("entryClass", entryClass)
        put("fileName", fileName)
        put("fileSize", fileSize)
        put("sha256", sha256)
        put("downloadUrl", downloadUrl)
        put("fallbackUrl", fallbackUrl)
        put("githubUrl", githubUrl)
        put("iconUrl", iconUrl)
        put("category", category)
        put("storeCategory", storeCategory)
        put("kind", kind)
        put("channel", channel)
        put("minAppVersionCode", minAppVersionCode)
        put("maxAppVersionCode", maxAppVersionCode)
        put("depends", JSONArray(depends))
        put("required", required)
        put("isBaseFramework", isBaseFramework)
        put("builtIn", builtIn)
        put("builtInVersionCode", builtInVersionCode)
        put("enabled", enabled)
        put("featured", featured)
        put("sortOrder", sortOrder)
        put("type", type)
        put("gameId", gameId)
        put("gameCategory", gameCategory)
        put("gameDesc", gameDesc)
        put("activityClass", activityClass)
        put("shortDescription", shortDescription)
        put("screenshots", JSONArray(screenshots))
        put("changelog", changelog)
        put("permissionsDescription", JSONArray(permissionsDescription))
        put("tags", JSONArray(tags))
        navigationContribution?.let { put("navigationContribution", it.toJson()) }
        put("permissions", JSONArray(permissions))
        put("rolloutPercent", rolloutPercent)
        put("restartRequired", restartRequired)
        put("rollbackAllowed", rollbackAllowed)
    }

    companion object {

        fun fromJson(json: JSONObject): ModuleManifest {
            val navJson = json.optJSONObject("navigationContribution")
            val navContribution = navJson?.let { NavigationContribution.fromJson(it) }

            return ModuleManifest(
                id = json.getString("id"),
                name = json.optString("name", ""),
                description = json.optString("description", ""),
                versionName = json.optString("versionName", "1.0.0"),
                versionCode = json.optInt("versionCode", 1),
                entryClass = json.optString("entryClass", ""),
                fileName = json.optString("fileName", ""),
                fileSize = json.optLong("fileSize", 0),
                sha256 = json.optString("sha256", ""),
                downloadUrl = json.optString("downloadUrl", ""),
                fallbackUrl = json.optString("fallbackUrl", ""),
                githubUrl = json.optString("githubUrl", ""),
                iconUrl = json.optString("iconUrl", ""),
                category = json.optString("category", "other"),
                storeCategory = json.optString("storeCategory", "other"),
                kind = json.optString("kind", "feature-apk"),
                channel = json.optString("channel", "stable"),
                minAppVersionCode = json.optInt("minAppVersionCode", json.optInt("minAppVersion", 0)),
                maxAppVersionCode = json.optInt("maxAppVersionCode", 0),
                depends = parseStringArray(json, "depends"),
                required = json.optBoolean("required", false),
                isBaseFramework = json.optBoolean("isBaseFramework", false),
                builtIn = json.optBoolean("builtIn", false),
                builtInVersionCode = json.optInt("builtInVersionCode", 0),
                enabled = json.optBoolean("enabled", true),
                featured = json.optBoolean("featured", false),
                sortOrder = json.optInt("sortOrder", 0),
                type = json.optString("type", "module"),
                gameId = json.optString("gameId", ""),
                gameCategory = json.optString("gameCategory", ""),
                gameDesc = json.optString("gameDesc", ""),
                activityClass = json.optString("activityClass", ""),
                shortDescription = json.optString("shortDescription", ""),
                screenshots = parseStringArray(json, "screenshots"),
                changelog = json.optString("changelog", ""),
                permissionsDescription = parseStringArray(json, "permissionsDescription"),
                tags = parseStringArray(json, "tags"),
                navigationContribution = navContribution,
                permissions = parseStringArray(json, "permissions"),
                rolloutPercent = json.optInt("rolloutPercent", 100),
                restartRequired = json.optBoolean("restartRequired", false),
                rollbackAllowed = json.optBoolean("rollbackAllowed", true)
            )
        }

        fun fromJsonArray(jsonStr: String): List<ModuleManifest> {
            val array = JSONArray(jsonStr)
            return buildList {
                for (i in 0 until array.length()) {
                    try {
                        add(fromJson(array.getJSONObject(i)))
                    } catch (_: Exception) {
                        // 跳过格式错误的单个模块条目
                    }
                }
            }
        }

        private fun parseStringArray(json: JSONObject, key: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i, "")
                    if (item.isNotEmpty()) add(item)
                }
            }
        }
    }
}

/**
 * 导航贡献声明（可序列化到 modules.json）。
 */
data class NavigationContribution(
    val slot: String = "bottom_nav",
    val title: String = "",
    val icon: String = "",
    val order: Int = 100,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("slot", slot)
        put("title", title)
        put("icon", icon)
        put("order", order)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): NavigationContribution = NavigationContribution(
            slot = json.optString("slot", "bottom_nav"),
            title = json.optString("title", ""),
            icon = json.optString("icon", ""),
            order = json.optInt("order", 100),
            enabled = json.optBoolean("enabled", true)
        )
    }
}

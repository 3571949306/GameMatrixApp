package com.gamecenter.app.modules.store.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 商店模块（schemaVersion=2 的模块条目）。
 *
 * 与主用 `ModuleManifest` 的关系：
 * - StoreModule 是远程目录条目的完整表示，包含商店展示所需的全部扩展字段
 * - ModuleManifest 保持向后兼容，字段集是 StoreModule 的子集
 * - StoreCatalogRepository 解析出 StoreModule 列表后，由 ModuleStoreActivity 决定
 *   是直接使用 StoreModule 还是转换为 ModuleManifest
 *
 * 新增字段（相比 ModuleManifest）：
 * - shortDescription：列表卡片简短描述
 * - screenshots：截图 URL 列表
 * - changelog：更新日志文本
 * - permissionsDescription：权限说明文本列表
 * - tags：标签列表
 * - sortOrder：商店内排序权重
 * - featured：是否为推荐模块（影响 Hero Banner 选择）
 * - enabled：是否在商店展示（false 表示下架但已安装用户仍可在已安装管理中看到）
 * - minAppVersionCode：兼容的最低宿主 versionCode
 * - required：是否为必需模块
 */
data class StoreModule(
    val id: String,
    val name: String,
    val description: String,
    val versionName: String,
    val versionCode: Int,
    val entryClass: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val downloadUrl: String,
    val fallbackUrl: String = "",
    val githubUrl: String = "",
    val iconUrl: String = "",
    val category: String = "other",
    val storeCategory: String = "game",
    val type: String = "module",
    val activityClass: String = "",
    val gameId: String = "",
    val gameCategory: String = "",
    val gameDesc: String = "",
    val builtIn: Boolean = false,
    val isBaseFramework: Boolean = false,
    val builtInVersionCode: Int = 0,
    val minAppVersion: Int = 0,
    val minAppVersionCode: Int = 0,
    val depends: List<String> = emptyList(),
    val required: Boolean = false,
    // P1 新增字段
    val shortDescription: String = "",
    val screenshots: List<String> = emptyList(),
    val changelog: String = "",
    val permissionsDescription: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val featured: Boolean = false,
    val enabled: Boolean = true,
    // P0 v3 新增字段
    val kind: String = "feature-apk",
    val channel: String = "stable",
    val maxAppVersionCode: Int = 0,
    val navigationContribution: com.gamecenter.app.core.common.NavigationContribution? = null,
    val permissions: List<String> = emptyList(),
    val rolloutPercent: Int = 100,
    val restartRequired: Boolean = false,
    val rollbackAllowed: Boolean = true
) {

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
        if (fallbackUrl.isNotEmpty()) put("fallbackUrl", fallbackUrl)
        if (githubUrl.isNotEmpty()) put("githubUrl", githubUrl)
        if (iconUrl.isNotEmpty()) put("iconUrl", iconUrl)
        put("category", category)
        put("storeCategory", storeCategory)
        put("type", type)
        if (activityClass.isNotEmpty()) put("activityClass", activityClass)
        if (gameId.isNotEmpty()) put("gameId", gameId)
        if (gameCategory.isNotEmpty()) put("gameCategory", gameCategory)
        if (gameDesc.isNotEmpty()) put("gameDesc", gameDesc)
        put("builtIn", builtIn)
        put("isBaseFramework", isBaseFramework)
        put("builtInVersionCode", builtInVersionCode)
        if (minAppVersion > 0) put("minAppVersion", minAppVersion)
        if (minAppVersionCode > 0) put("minAppVersionCode", minAppVersionCode)
        if (depends.isNotEmpty()) put("depends", JSONArray(depends))
        put("required", required)
        if (shortDescription.isNotEmpty()) put("shortDescription", shortDescription)
        if (screenshots.isNotEmpty()) put("screenshots", JSONArray(screenshots))
        if (changelog.isNotEmpty()) put("changelog", changelog)
        if (permissionsDescription.isNotEmpty()) put("permissionsDescription", JSONArray(permissionsDescription))
        if (tags.isNotEmpty()) put("tags", JSONArray(tags))
        put("sortOrder", sortOrder)
        put("featured", featured)
        put("enabled", enabled)
        put("kind", kind)
        put("channel", channel)
        put("maxAppVersionCode", maxAppVersionCode)
        navigationContribution?.let { put("navigationContribution", it.toJson()) }
        if (permissions.isNotEmpty()) put("permissions", JSONArray(permissions))
        put("rolloutPercent", rolloutPercent)
        put("restartRequired", restartRequired)
        put("rollbackAllowed", rollbackAllowed)
    }

    /**
     * 转换为规范 ModuleManifest（P0 v3）。
     */
    fun toModuleManifest(): com.gamecenter.app.core.common.ModuleManifest = com.gamecenter.app.core.common.ModuleManifest(
        id = id,
        name = name,
        description = description,
        versionName = versionName,
        versionCode = versionCode,
        entryClass = entryClass,
        fileName = fileName,
        fileSize = fileSize,
        sha256 = sha256,
        downloadUrl = downloadUrl,
        fallbackUrl = fallbackUrl,
        githubUrl = githubUrl,
        iconUrl = iconUrl,
        category = category,
        storeCategory = storeCategory,
        kind = kind,
        channel = channel,
        minAppVersionCode = minAppVersionCode,
        maxAppVersionCode = maxAppVersionCode,
        depends = depends,
        required = required,
        isBaseFramework = isBaseFramework,
        builtIn = builtIn,
        builtInVersionCode = builtInVersionCode,
        enabled = enabled,
        featured = featured,
        sortOrder = sortOrder,
        type = type,
        gameId = gameId,
        gameCategory = gameCategory,
        gameDesc = gameDesc,
        activityClass = activityClass,
        shortDescription = shortDescription,
        screenshots = screenshots,
        changelog = changelog,
        permissionsDescription = permissionsDescription,
        tags = tags,
        navigationContribution = navigationContribution,
        permissions = permissions,
        rolloutPercent = rolloutPercent,
        restartRequired = restartRequired,
        rollbackAllowed = rollbackAllowed
    )

    companion object {
        fun fromJson(json: JSONObject): StoreModule? {
            val id = json.optString("id", "").ifEmpty { return null }
            val packageJson = json.optJSONObject("package")
            val dependsList = mutableListOf<String>()
            (json.optJSONArray("depends") ?: json.optJSONArray("dependencies"))?.let { arr ->
                for (i in 0 until arr.length()) {
                    val dep = arr.optString(i, "")
                    if (dep.isNotEmpty()) dependsList.add(dep)
                }
            }
            val screenshotsList = mutableListOf<String>()
            json.optJSONArray("screenshots")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val url = arr.optString(i, "")
                    if (url.isNotEmpty()) screenshotsList.add(url)
                }
            }
            val permissionsList = mutableListOf<String>()
            json.optJSONArray("permissionsDescription")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val perm = arr.optString(i, "")
                    if (perm.isNotEmpty()) permissionsList.add(perm)
                }
            }
            val tagsList = mutableListOf<String>()
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val tag = arr.optString(i, "")
                    if (tag.isNotEmpty()) tagsList.add(tag)
                }
            }
            val runtimePermissionsList = mutableListOf<String>()
            json.optJSONArray("permissions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val perm = arr.optString(i, "")
                    if (perm.isNotEmpty()) runtimePermissionsList.add(perm)
                }
            }
            val navJson = json.optJSONObject("navigationContribution")
            val navContribution = navJson?.let { com.gamecenter.app.core.common.NavigationContribution.fromJson(it) }
            return StoreModule(
                id = id,
                name = json.optString("name", id),
                description = json.optString("description", ""),
                versionName = json.optString("versionName", "1.0.0"),
                versionCode = json.optInt("versionCode", 0),
                entryClass = json.optString("entryClass", ""),
                fileName = json.optString("fileName", "").ifEmpty {
                    packageJson?.optString("fileName", "").orEmpty()
                },
                fileSize = json.optLong("fileSize", packageJson?.optLong("fileSize", 0L) ?: 0L),
                sha256 = json.optString("sha256", "").ifEmpty {
                    packageJson?.optString("sha256", "").orEmpty()
                },
                downloadUrl = json.optString("downloadUrl", "").ifEmpty {
                    packageJson?.optString("downloadUrl", "").orEmpty()
                },
                fallbackUrl = json.optString("fallbackUrl", "").ifEmpty {
                    packageJson?.optString("fallbackUrl", "").orEmpty()
                },
                githubUrl = json.optString("githubUrl", "").ifEmpty {
                    packageJson?.optString("githubUrl", "").orEmpty()
                },
                iconUrl = json.optString("iconUrl", ""),
                category = json.optString("category", "other"),
                storeCategory = json.optString("storeCategory", "game"),
                type = json.optString("type", "module"),
                activityClass = json.optString("activityClass", ""),
                gameId = json.optString("gameId", ""),
                gameCategory = json.optString("gameCategory", ""),
                gameDesc = json.optString("gameDesc", ""),
                builtIn = json.optBoolean(
                    "builtIn",
                    json.optString("deliveryType", "").equals("builtin", ignoreCase = true)
                ),
                isBaseFramework = json.optBoolean("isBaseFramework", false),
                builtInVersionCode = json.optInt("builtInVersionCode", 0),
                minAppVersion = json.optInt("minAppVersion", 0),
                minAppVersionCode = json.optInt("minAppVersionCode", json.optInt("minHostVersionCode", 0)),
                depends = dependsList,
                required = json.optBoolean("required", false),
                shortDescription = json.optString("shortDescription", ""),
                screenshots = screenshotsList,
                changelog = json.optString("changelog", ""),
                permissionsDescription = permissionsList,
                tags = tagsList,
                sortOrder = json.optInt("sortOrder", 0),
                featured = json.optBoolean("featured", false),
                enabled = json.optBoolean("enabled", true),
                kind = json.optString("kind", json.optString("runtimeType", "feature-apk")),
                channel = json.optString("channel", "stable"),
                maxAppVersionCode = json.optInt("maxAppVersionCode", json.optInt("maxHostVersionCode", 0)),
                navigationContribution = navContribution,
                permissions = runtimePermissionsList,
                rolloutPercent = json.optInt("rolloutPercent", 100),
                restartRequired = json.optBoolean("restartRequired", false),
                rollbackAllowed = json.optBoolean("rollbackAllowed", true)
            )
        }
    }
}

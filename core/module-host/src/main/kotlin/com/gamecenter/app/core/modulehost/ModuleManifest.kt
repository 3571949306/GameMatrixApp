package com.gamecenter.app.core.modulehost

import org.json.JSONObject

/**
 * 模块清单数据类（ModuleManifest v2）。
 *
 * 此类是 :core:module-host 模块的核心数据模型，
 * 定义了模块商店中每个模块的元数据结构。
 *
 * 与原版 (com.gamecenter.app.modules.ModuleManifest) 的区别：
 * - 新增 `minAppVersionCode` 字段（原为 minAppVersion，语义更明确）
 * - 新增 `required` 字段（标记必装模块）
 * - sha256 解析失败时不再静默忽略，而是保留为空并让 ModuleVerifier 处理
 */
data class ModuleManifest(
    /** 模块唯一标识，如 "vpn", "game_2048", "games-hall" */
    val id: String,
    /** 模块显示名称 */
    val name: String,
    /** 模块功能描述 */
    val description: String,
    /** 版本名称，如 "1.2.3" */
    val versionName: String,
    /** 版本号（整数，用于比较） */
    val versionCode: Int,
    /** 模块入口类完整路径，如 "com.gamecenter.vpn.VpnModuleEntryPoint" */
    val entryClass: String,
    /** APK 文件名，如 "feature_vpn_v100.apk" */
    val fileName: String,
    /** 预期文件大小（字节），0 表示跳过大小校验 */
    val fileSize: Long,
    /** SHA-256 哈希（小写十六进制），空字符串时 ModuleVerifier 会强制拒绝 */
    val sha256: String,
    /** 主下载地址（香港 VPS） */
    val downloadUrl: String,
    /** 备用下载地址（可选） */
    val fallbackUrl: String = "",
    /** 图标 URL（存放在 VPS 上的 PNG/WebP 图标） */
    val iconUrl: String = "",
    /** 模块类型："game", "nav", "core", "tool" */
    val type: String = "module",
    /** 商店分类：用于商店页面分组显示 */
    val storeCategory: String = "other",
    /** 是否为内置模块（代码在主 APK DEX 中，无需 DexClassLoader） */
    val builtIn: Boolean = false,
    /** 是否为必装模块（启动时强制安装最新版） */
    val required: Boolean = false,
    /** 安装此模块所需的最低 App 版本号，0 表示无限制 */
    val minAppVersionCode: Int = 0,
    /** 依赖的其他模块 ID 列表 */
    val depends: List<String> = emptyList(),
    // ===== 游戏模块专用字段 =====
    /** 游戏唯一 ID（type="game" 时有效） */
    val gameId: String = "",
    /** 游戏分类："classics", "puzzle", "casual" */
    val gameCategory: String = "",
    /** 游戏简短描述（显示在游戏大厅卡片） */
    val gameDesc: String = "",
    /** 内置游戏的 Activity 类名（builtIn=true 时使用） */
    val activityClass: String = "",
    /** 是否为基础框架模块（不允许卸载） */
    val isBaseFramework: Boolean = false
) {

    /**
     * 获取所有可用的下载地址列表，主地址在前，备用在后。
     */
    fun getAllDownloadUrls(): List<String> {
        val urls = mutableListOf(downloadUrl)
        if (fallbackUrl.isNotEmpty() && fallbackUrl != downloadUrl) {
            urls.add(fallbackUrl)
        }
        return urls.filter { it.isNotEmpty() }
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
        put("iconUrl", iconUrl)
        put("type", type)
        put("storeCategory", storeCategory)
        put("builtIn", builtIn)
        put("required", required)
        put("minAppVersionCode", minAppVersionCode)
        put("gameId", gameId)
        put("gameCategory", gameCategory)
        put("gameDesc", gameDesc)
        put("activityClass", activityClass)
        put("isBaseFramework", isBaseFramework)
    }

    companion object {

        fun fromJson(json: JSONObject): ModuleManifest {
            val dependsArray = json.optJSONArray("depends")
            val dependsList = buildList {
                if (dependsArray != null) {
                    for (i in 0 until dependsArray.length()) {
                        add(dependsArray.getString(i))
                    }
                }
            }
            return ModuleManifest(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description", ""),
                versionName = json.optString("versionName", "1.0.0"),
                versionCode = json.optInt("versionCode", 1),
                entryClass = json.optString("entryClass", ""),
                fileName = json.optString("fileName", ""),
                fileSize = json.optLong("fileSize", 0),
                sha256 = json.optString("sha256", ""),
                downloadUrl = json.optString("downloadUrl", ""),
                fallbackUrl = json.optString("fallbackUrl", ""),
                iconUrl = json.optString("iconUrl", ""),
                type = json.optString("type", "module"),
                storeCategory = json.optString("storeCategory", "other"),
                builtIn = json.optBoolean("builtIn", false),
                required = json.optBoolean("required", false),
                minAppVersionCode = json.optInt("minAppVersionCode",
                    json.optInt("minAppVersion", 0)),  // 兼容旧字段名
                depends = dependsList,
                gameId = json.optString("gameId", ""),
                gameCategory = json.optString("gameCategory", ""),
                gameDesc = json.optString("gameDesc", ""),
                activityClass = json.optString("activityClass", ""),
                isBaseFramework = json.optBoolean("isBaseFramework", false)
            )
        }

        fun fromJsonArray(jsonStr: String): List<ModuleManifest> {
            val array = org.json.JSONArray(jsonStr)
            return buildList {
                for (i in 0 until array.length()) {
                    try {
                        add(fromJson(array.getJSONObject(i)))
                    } catch (_: Exception) {
                        // 跳过格式错误的单个模块条目，不影响其他模块
                    }
                }
            }
        }
    }
}

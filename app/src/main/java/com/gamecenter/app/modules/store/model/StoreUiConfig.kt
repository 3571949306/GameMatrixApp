package com.gamecenter.app.modules.store.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 商店 UI 配置（schemaVersion=1）。
 *
 * 服务器通过 store-ui.json 控制商店页面已有区块的：
 * - 显示顺序（order）
 * - 是否启用（enabled）
 * - 列数（columns，仅对 module_grid 生效）
 * - 区块参数（params，区块特定的可选配置）
 *
 * 严格禁止：
 * - 任意 Intent URI
 * - 任意类名
 * - 任意 Shell 命令
 * - 任意文件路径
 * - 任意 JavaScript / 表达式
 *
 * 未知 type 仅记录日志并跳过，不导致整个配置失效。
 * 单个 section 解析失败仅跳过该 section。
 */
data class StoreUiConfig(
    val schemaVersion: Int,
    val pageVersion: Int,
    val minHostVersionCode: Int,
    val generatedAt: String,
    val pages: Map<String, StorePage>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("pageVersion", pageVersion)
        put("minHostVersionCode", minHostVersionCode)
        if (generatedAt.isNotEmpty()) put("generatedAt", generatedAt)
        val pagesObj = JSONObject()
        pages.forEach { (id, page) -> pagesObj.put(id, page.toJson()) }
        put("pages", pagesObj)
    }

    companion object {
        private const val TAG = "StoreUiConfig"

        /**
         * 解析 store-ui.json。容错策略：
         * 1. schemaVersion != 1 → 抛 IllegalArgumentException，调用方降级
         * 2. 单个 section 解析失败 → 跳过该 section
         * 3. pages 字段缺失 → 返回空 map，调用方走默认布局
         */
        fun fromJson(jsonStr: String): StoreUiConfig {
            val json = JSONObject(jsonStr)
            val schemaVersion = json.optInt("schemaVersion", 0)
            if (schemaVersion != 1) {
                throw IllegalArgumentException("Unsupported store-ui schemaVersion: $schemaVersion")
            }
            val pageVersion = json.optInt("pageVersion", 1)
            val minHostVersionCode = json.optInt("minHostVersionCode", 0)
            val generatedAt = json.optString("generatedAt", "")

            val pagesMap = mutableMapOf<String, StorePage>()
            json.optJSONObject("pages")?.let { pagesObj ->
                val keys = pagesObj.keys()
                while (keys.hasNext()) {
                    val pageId = keys.next()
                    try {
                        val pageJson = pagesObj.optJSONObject(pageId) ?: continue
                        StorePage.fromJson(pageJson)?.let { pagesMap[pageId] = it }
                    } catch (_: Exception) {
                        /* 跳过单页解析失败 */
                    }
                }
            }

            return StoreUiConfig(
                schemaVersion = schemaVersion,
                pageVersion = pageVersion,
                minHostVersionCode = minHostVersionCode,
                generatedAt = generatedAt,
                pages = pagesMap
            )
        }

        /**
         * 默认布局：所有 9 种区块按固定顺序启用。
         * 当服务器配置不可用、损坏或 minHostVersionCode 不满足时使用。
         */
        fun defaultConfig(): StoreUiConfig = StoreUiConfig(
            schemaVersion = 1,
            pageVersion = 1,
            minHostVersionCode = 0,
            generatedAt = "",
            pages = mapOf(
                "store_home" to StorePage(
                    id = "store_home",
                    sections = listOf(
                        StoreSection(id = "hero", type = "hero_banner", enabled = true, order = 10, columns = 0, params = emptyMap()),
                        StoreSection(id = "search", type = "search_bar", enabled = true, order = 20, columns = 0, params = emptyMap()),
                        StoreSection(id = "categories", type = "category_tabs", enabled = true, order = 30, columns = 0, params = emptyMap()),
                        StoreSection(id = "modules", type = "module_grid", enabled = true, order = 50, columns = 2, params = emptyMap()),
                        StoreSection(id = "updates", type = "update_section", enabled = true, order = 60, columns = 0, params = emptyMap()),
                        StoreSection(id = "installed", type = "installed_section", enabled = true, order = 70, columns = 0, params = emptyMap())
                    )
                )
            )
        )
    }
}

/**
 * 商店页面（一个页面包含多个 section）。
 *
 * 当前只支持 store_home 一个页面，但协议层保留多页扩展能力。
 */
data class StorePage(
    val id: String,
    val sections: List<StoreSection>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sections", JSONArray().apply { sections.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): StorePage? {
            val id = json.optString("id", "")
            val sections = mutableListOf<StoreSection>()
            json.optJSONArray("sections")?.let { arr ->
                for (i in 0 until arr.length()) {
                    try {
                        val item = arr.optJSONObject(i) ?: continue
                        StoreSection.fromJson(item)?.let { sections.add(it) }
                    } catch (_: Exception) {
                        /* 跳过单条 section 解析失败 */
                    }
                }
            }
            if (id.isEmpty() && sections.isEmpty()) return null
            return StorePage(id = id.ifEmpty { "store_home" }, sections = sections)
        }
    }
}

/**
 * 商店页面区块。
 *
 * columns 仅对 module_grid 类型有效，合法范围 [1, 4]；非法值由调用方回退到 2。
 * params 为区块特定的可选配置，由各 Renderer 自行解析（如 notice 的 text、update_section 的 maxItems）。
 *
 * 严禁在 params 中放入动作类指令（如 Intent、类名、Shell 命令、JavaScript）。
 * 所有动作必须通过 StoreActionRouter 的白名单（见 P2.5）。
 */
data class StoreSection(
    val id: String,
    val type: String,
    val enabled: Boolean,
    val order: Int,
    val columns: Int,
    val params: Map<String, String>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("enabled", enabled)
        put("order", order)
        if (columns > 0) put("columns", columns)
        if (params.isNotEmpty()) {
            val paramsObj = JSONObject()
            params.forEach { (k, v) -> paramsObj.put(k, v) }
            put("params", paramsObj)
        }
    }

    companion object {
        /**
         * 第一版支持的区块类型白名单（见 P2.2）。
         * 未知 type 仍会被解析（保留 id 与 order），由 RendererRegistry 在渲染时跳过并记录日志。
         */
        val SUPPORTED_TYPES = setOf(
            "hero_banner",
            "search_bar",
            "notice",
            "category_tabs",
            "section_title",
            "module_list",
            "module_grid",
            "update_section",
            "installed_section"
        )

        /** columns 合法范围（仅对 module_grid 生效） */
        const val MIN_COLUMNS = 1
        const val MAX_COLUMNS = 4
        const val DEFAULT_COLUMNS = 2

        fun fromJson(json: JSONObject): StoreSection? {
            val id = json.optString("id", "")
            val type = json.optString("type", "")
            if (id.isEmpty() && type.isEmpty()) return null

            val rawColumns = json.optInt("columns", 0)
            // columns 合法范围校验：超出 [1, 4] 时回退到 0（表示不指定，由 Renderer 用默认 2）
            val columns = if (rawColumns in MIN_COLUMNS..MAX_COLUMNS) rawColumns else 0

            val paramsMap = mutableMapOf<String, String>()
            json.optJSONObject("params")?.let { paramsObj ->
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    // 仅保留字符串类型参数，其他类型转为字符串
                    paramsMap[key] = paramsObj.optString(key, "")
                }
            }

            return StoreSection(
                id = id.ifEmpty { type },
                type = type,
                enabled = json.optBoolean("enabled", true),
                order = json.optInt("order", 0),
                columns = columns,
                params = paramsMap
            )
        }
    }
}

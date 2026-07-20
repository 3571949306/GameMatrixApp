package com.gamecenter.app.modules.store.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 商店目录顶层对象（schemaVersion=2）。
 *
 * 兼容旧格式 `{ "version": N, "modules": [...] }`：
 * - 缺少 `schemaVersion` 字段时按 v1 解析，仅 modules 字段有效
 * - categories / heroBanners 缺失时返回空列表，由调用方走 fallback
 *
 * 不在此处抛异常，单个分类 / Banner / 模块解析失败仅跳过该条目，
 * 避免服务器返回的单点错误导致整个目录失效。
 */
data class StoreCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val generatedAt: String,
    val categories: List<StoreCategory>,
    val heroBanners: List<StoreHeroBanner>,
    val modules: List<StoreModule>
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("catalogVersion", catalogVersion)
        put("generatedAt", generatedAt)
        put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
        put("heroBanners", JSONArray().apply { heroBanners.forEach { put(it.toJson()) } })
        put("modules", JSONArray().apply { modules.forEach { put(it.toJson()) } })
    }

    companion object {
        private const val TAG = "StoreCatalog"

        /**
         * 解析目录 JSON。容错策略：
         * 1. 顶层 schemaVersion 缺失 → 视为 v1，仅解析 modules
         * 2. 单个条目解析失败 → 跳过该条目，不抛异常
         * 3. modules 字段缺失 → 返回空列表
         * 4. JSON 格式错误 → 抛 IllegalArgumentException（调用方降级到缓存）
         */
        fun fromJson(jsonStr: String): StoreCatalog {
            val json = JSONObject(jsonStr)
            val schemaVersion = json.optInt("schemaVersion", 1)
            val catalogVersion = if (schemaVersion >= 2) {
                json.optInt("catalogVersion", 1)
            } else {
                // v1 兼容：用顶层 version 字段作为 catalogVersion
                json.optInt("version", 0)
            }
            val generatedAt = json.optString("generatedAt", "")

            val categories = parseCategoryList(json)
            val heroBanners = parseHeroBannerList(json)
            val modules = parseModuleList(json)

            return StoreCatalog(
                schemaVersion = schemaVersion,
                catalogVersion = catalogVersion,
                generatedAt = generatedAt,
                categories = categories,
                heroBanners = heroBanners,
                modules = modules
            )
        }

        private fun parseCategoryList(json: JSONObject): List<StoreCategory> {
            val arr = json.optJSONArray("categories") ?: return emptyList()
            val result = mutableListOf<StoreCategory>()
            for (i in 0 until arr.length()) {
                try {
                    val item = arr.optJSONObject(i) ?: continue
                    StoreCategory.fromJson(item)?.let { result.add(it) }
                } catch (_: Exception) { /* 跳过单条错误 */ }
            }
            return result
        }

        private fun parseHeroBannerList(json: JSONObject): List<StoreHeroBanner> {
            val arr = json.optJSONArray("heroBanners") ?: return emptyList()
            val result = mutableListOf<StoreHeroBanner>()
            for (i in 0 until arr.length()) {
                try {
                    val item = arr.optJSONObject(i) ?: continue
                    StoreHeroBanner.fromJson(item)?.let { result.add(it) }
                } catch (_: Exception) { /* 跳过单条错误 */ }
            }
            return result
        }

        private fun parseModuleList(json: JSONObject): List<StoreModule> {
            val arr = json.optJSONArray("modules") ?: return emptyList()
            val result = mutableListOf<StoreModule>()
            for (i in 0 until arr.length()) {
                try {
                    val item = arr.optJSONObject(i) ?: continue
                    StoreModule.fromJson(item)?.let { result.add(it) }
                } catch (_: Exception) { /* 跳过单条错误 */ }
            }
            return result
        }

        /**
         * 最小硬编码救援目录 — 所有外部数据源都失败时的最后兜底。
         * 仅包含 games_hall 一个条目，保证应用不致商店完全空白。
         */
        fun rescueCatalog(): StoreCatalog = StoreCatalog(
            schemaVersion = 2,
            catalogVersion = 1,
            generatedAt = "",
            categories = emptyList(),
            heroBanners = emptyList(),
            modules = listOf(
                StoreModule(
                    id = "games_hall",
                    name = "游戏大厅",
                    description = "聚合宿主游戏与已下载游戏模块的内置入口。",
                    versionName = "1.0.0",
                    versionCode = 100,
                    entryClass = "com.gamecenter.app.features.BuiltInGamesHallModuleEntryPoint",
                    fileName = "", fileSize = 0, sha256 = "",
                    downloadUrl = "", fallbackUrl = "", githubUrl = "", iconUrl = "",
                    category = "game", storeCategory = "game", type = "nav",
                    builtIn = true, isBaseFramework = true, builtInVersionCode = 100
                )
            )
        )
    }
}

package com.gamecenter.app.modules.store.model

import org.json.JSONObject

/**
 * 商店 Hero Banner（远程化）。
 *
 * 服务器控制 Banner 的显示、隐藏、排序、文案、图片和目标模块。
 * 图片加载失败时由 Adapter 显示本地占位图，不崩溃。
 * moduleId 无效时由 Adapter 跳过点击或显示 Toast，不崩溃。
 */
data class StoreHeroBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val moduleId: String,
    val imageUrl: String,
    val order: Int,
    val enabled: Boolean
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("subtitle", subtitle)
        put("moduleId", moduleId)
        if (imageUrl.isNotEmpty()) put("imageUrl", imageUrl)
        put("order", order)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): StoreHeroBanner? {
            val id = json.optString("id", "").ifEmpty { return null }
            return StoreHeroBanner(
                id = id,
                title = json.optString("title", ""),
                subtitle = json.optString("subtitle", ""),
                moduleId = json.optString("moduleId", ""),
                imageUrl = json.optString("imageUrl", ""),
                order = json.optInt("order", 0),
                enabled = json.optBoolean("enabled", true)
            )
        }
    }
}

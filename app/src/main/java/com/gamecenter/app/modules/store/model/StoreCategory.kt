package com.gamecenter.app.modules.store.model

import org.json.JSONObject

/**
 * 商店分类（远程化）。
 *
 * 服务器控制分类的显示顺序、是否启用、显示名称。
 * 未知分类仍能显示在"其他"分类，由调用方处理。
 */
data class StoreCategory(
    val id: String,
    val name: String,
    val order: Int,
    val enabled: Boolean,
    val icon: String = ""
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("order", order)
        put("enabled", enabled)
        if (icon.isNotEmpty()) put("icon", icon)
    }

    companion object {
        fun fromJson(json: JSONObject): StoreCategory? {
            val id = json.optString("id", "").ifEmpty { return null }
            return StoreCategory(
                id = id,
                name = json.optString("name", id),
                order = json.optInt("order", 0),
                enabled = json.optBoolean("enabled", true),
                icon = json.optString("icon", "")
            )
        }
    }
}

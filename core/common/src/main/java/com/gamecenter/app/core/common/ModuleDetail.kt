package com.gamecenter.app.core.common

import org.json.JSONObject

/**
 * 模块详情数据模型（#11.1）。
 *
 * 覆盖 7 项必填字段，供商店详情页在安装前向用户完整披露模块信息。
 * 字段全部可选——未填写时回退到 CatalogModule 的基础信息或通用文案。
 *
 * @property valueDescription 价值描述：模块解决什么问题、给用户带来什么结果
 * @property audience 目标受众：如学生、开发者、游戏玩家
 * @property offlineCapability 离线能力：是否可离线使用，哪些功能需要联网
 * @property updateImpact 更新影响：更新时会改动什么（数据/配置/缓存）
 * @property uninstallImpact 卸载影响：卸载后会遗留什么（数据/配置）
 * @property highlights 亮点列表：3-5 条核心功能要点
 * @property limitations 已知限制：如性能、兼容性、使用约束
 */
data class ModuleDetail(
    val valueDescription: String = "",
    val audience: String = "",
    val offlineCapability: String = "",
    val updateImpact: String = "",
    val uninstallImpact: String = "",
    val highlights: List<String> = emptyList(),
    val limitations: List<String> = emptyList()
) {
    /** 是否有任何详情内容 */
    val hasContent: Boolean get() = valueDescription.isNotEmpty() ||
        audience.isNotEmpty() ||
        offlineCapability.isNotEmpty() ||
        updateImpact.isNotEmpty() ||
        uninstallImpact.isNotEmpty() ||
        highlights.isNotEmpty() ||
        limitations.isNotEmpty()

    companion object {
        fun fromJson(json: JSONObject?): ModuleDetail {
            if (json == null) return ModuleDetail()
            return ModuleDetail(
                valueDescription = json.optString("valueDescription", ""),
                audience = json.optString("audience", ""),
                offlineCapability = json.optString("offlineCapability", ""),
                updateImpact = json.optString("updateImpact", ""),
                uninstallImpact = json.optString("uninstallImpact", ""),
                highlights = stringList(json.optJSONArray("highlights")),
                limitations = stringList(json.optJSONArray("limitations"))
            )
        }

        private fun stringList(array: org.json.JSONArray?): List<String> {
            if (array == null) return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("valueDescription", valueDescription)
        put("audience", audience)
        put("offlineCapability", offlineCapability)
        put("updateImpact", updateImpact)
        put("uninstallImpact", uninstallImpact)
        put("highlights", org.json.JSONArray(highlights))
        put("limitations", org.json.JSONArray(limitations))
    }
}

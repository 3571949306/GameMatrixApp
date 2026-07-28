package com.gamecenter.app.core.common

import org.json.JSONObject

/**
 * 隐私卡数据模型（#11.2，与 #23 数据与连接中心共用）。
 *
 * 覆盖 6 项必填字段，在安装前向用户明示模块的数据行为。
 * 所有字段可选——未填写时 UI 回退为"未声明"提示，鼓励开发者补全。
 *
 * @property localData 本地数据：模块在设备上存储了什么（文件/数据库/SharedPreferences）
 * @property cloudData 云端数据：模块会向云端发送什么内容
 * @property networkDomains 网络域：模块连接的域名/IP 列表
 * @property syncLocation 同步位置：数据同步到哪（如 WebDAV 服务器、厂商云、无）
 * @property retentionPeriod 保存期限：数据保留多久（如 "会话级"/"30 天"/"直到卸载"）
 * @property deletionMethod 删除方式：如何删除数据（如 "卸载即清除"/"设置内一键清除"）
 */
data class PrivacyCard(
    val localData: String = "",
    val cloudData: String = "",
    val networkDomains: List<String> = emptyList(),
    val syncLocation: String = "",
    val retentionPeriod: String = "",
    val deletionMethod: String = ""
) {
    /** 是否有任何隐私卡内容 */
    val hasContent: Boolean get() = localData.isNotEmpty() ||
        cloudData.isNotEmpty() ||
        networkDomains.isNotEmpty() ||
        syncLocation.isNotEmpty() ||
        retentionPeriod.isNotEmpty() ||
        deletionMethod.isNotEmpty()

    /** 是否声明了任何云端/网络行为（用于判断是否需要显示 consent） */
    val involvesCloud: Boolean get() = cloudData.isNotEmpty() ||
        networkDomains.isNotEmpty() ||
        syncLocation.isNotEmpty()

    companion object {
        fun fromJson(json: JSONObject?): PrivacyCard {
            if (json == null) return PrivacyCard()
            return PrivacyCard(
                localData = json.optString("localData", ""),
                cloudData = json.optString("cloudData", ""),
                networkDomains = stringList(json.optJSONArray("networkDomains")),
                syncLocation = json.optString("syncLocation", ""),
                retentionPeriod = json.optString("retentionPeriod", ""),
                deletionMethod = json.optString("deletionMethod", "")
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
        put("localData", localData)
        put("cloudData", cloudData)
        put("networkDomains", org.json.JSONArray(networkDomains))
        put("syncLocation", syncLocation)
        put("retentionPeriod", retentionPeriod)
        put("deletionMethod", deletionMethod)
    }
}

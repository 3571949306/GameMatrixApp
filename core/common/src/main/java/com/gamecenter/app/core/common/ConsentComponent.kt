package com.gamecenter.app.core.common

/**
 * 云端调用前统一明示组件（#24.1）。
 *
 * 任何模块在发起云端调用前，必须构建此组件并通过 ConsentDialog 向用户明示：
 * 1. 发送什么 — 数据内容描述（如"用户输入的文本"、"图片的 Base64 编码"、"游戏存档 JSON"）
 * 2. 为什么 — 用途说明（如"调用 AI 模型生成回复"、"OCR 识别图片文字"、"同步到 WebDAV 服务器"）
 * 3. 可否本地 — 是否有本地替代方案（空字符串表示无本地替代，必须走云端）
 * 4. 费用/网络 — 网络消耗与费用提示（如"需要联网，可能消耗流量"、"免费额度内不收费"）
 * 5. 如何取消 — 取消方式（如"点击取消即可中止"、"可在设置中关闭云同步"）
 *
 * 设计原则：
 * - 文案面向非技术用户，避免 jargon
 * - 有本地替代时必须明示，让用户可选择"改用本地"
 * - consent 结果按 scope 独立缓存到 SharedPreferences，避免每次都弹
 * - 条款更新后通过 versionCode 递增强制重新确认
 *
 * @property scope 调用域标识，用于独立缓存同意状态（如 "ai_cloud"、"ocr_cloud"、"webdav_sync"）
 * @property versionCode 条款版本，递增时强制重新确认
 * @property title 弹窗标题
 * @property sendData 发送什么：数据内容描述
 * @property purpose 为什么：用途说明
 * @property localAlternative 可否本地：本地替代方案描述，空字符串表示无本地替代
 * @property costAndNetwork 费用/网络：网络消耗与费用提示
 * @property cancelHint 如何取消：取消方式
 * @property providerInfo 服务提供方（如"DeepSeek API"、"百度 OCR"、"WebDAV 服务器"）
 * @property dataRetention 数据留存策略（如"服务端不留存"、"留存 30 天"）
 */
data class ConsentComponent(
    val scope: String,
    val versionCode: Int = 1,
    val title: String = "",
    val sendData: String = "",
    val purpose: String = "",
    val localAlternative: String = "",
    val costAndNetwork: String = "",
    val cancelHint: String = "",
    val providerInfo: String = "",
    val dataRetention: String = ""
) {
    /** 是否有本地替代方案 */
    val hasLocalAlternative: Boolean get() = localAlternative.isNotEmpty()

    /** 是否有完整必填信息（5 项必填字段均非空） */
    val isComplete: Boolean get() = sendData.isNotEmpty() &&
        purpose.isNotEmpty() &&
        costAndNetwork.isNotEmpty() &&
        cancelHint.isNotEmpty()

    companion object {
        /** consent 缓存 SharedPreferences 文件名 */
        const val PREFS_NAME = "cloud_consent_prefs"

        /** consent 缓存 key 前缀：{scope}_consent_version */
        private fun consentKey(scope: String) = "${scope}_consent_version"

        /** consent "不再提示" key 前缀：{scope}_dont_ask_again */
        private fun dontAskKey(scope: String) = "${scope}_dont_ask_again"

        /**
         * 检查是否已获得过此版本的有效同意。
         * @param context 任意 Context
         * @param scope 调用域
         * @param versionCode 当前条款版本
         * @return true 表示用户已同意过此版本，可跳过弹窗
         */
        @JvmStatic
        fun hasValidConsent(
            context: android.content.Context,
            scope: String,
            versionCode: Int
        ): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val agreedVersion = prefs.getInt(consentKey(scope), 0)
            return agreedVersion >= versionCode
        }

        /**
         * 检查用户是否选择了"不再提示"。
         * 注意：即使选择了"不再提示"，条款版本更新后仍应重新询问。
         */
        @JvmStatic
        fun isDontAskAgain(context: android.content.Context, scope: String): Boolean {
            return context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(dontAskKey(scope), false)
        }

        /**
         * 记录用户的同意决定。
         * @param context 任意 Context
         * @param scope 调用域
         * @param versionCode 同意的条款版本
         * @param dontAskAgain 是否选择"不再提示"
         */
        @JvmStatic
        fun recordConsent(
            context: android.content.Context,
            scope: String,
            versionCode: Int,
            dontAskAgain: Boolean
        ) {
            context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt(consentKey(scope), versionCode)
                .putBoolean(dontAskKey(scope), dontAskAgain)
                .apply()
        }

        /**
         * 撤销同意（用于设置页"重置云调用同意"功能）。
         */
        @JvmStatic
        fun revokeConsent(context: android.content.Context, scope: String) {
            context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .remove(consentKey(scope))
                .remove(dontAskKey(scope))
                .apply()
        }
    }
}

/** Consent 决策结果 */
enum class ConsentDecision {
    /** 同意走云端 */
    AGREE_CLOUD,
    /** 拒绝（取消操作） */
    REFUSE,
    /** 改用本地路径 */
    USE_LOCAL
}

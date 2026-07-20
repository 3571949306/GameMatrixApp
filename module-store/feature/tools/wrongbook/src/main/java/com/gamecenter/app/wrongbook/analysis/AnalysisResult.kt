package com.gamecenter.app.wrongbook.analysis

/**
 * AI 解析结果。
 *
 * 后端 /api/wrongbook/solve-text 和 /api/wrongbook/solve-image 返回的结构化字段。
 * cloud 模式（直连 DeepSeek 等）仅填充 subject/difficulty/knowledgePoints/analysis。
 *
 * @param success 是否成功
 * @param subject 科目
 * @param difficulty 难度 1-5
 * @param knowledgePoints 知识点列表
 * @param analysis 解析文本
 * @param message 附加消息（错误提示等）
 * @param questionType 题型：single_choice/multiple_choice/judge/fill_blank/short_answer/unknown
 * @param question 题干（AI 提取）
 * @param options 选项列表
 * @param answer 正确答案
 * @param wrongReason 错因分析
 * @param reviewSuggestion 复习建议
 * @param confidence AI 置信度 0.0-1.0
 */
data class AnalysisResult(
    val success: Boolean,
    val subject: String = "",
    val difficulty: Int = 3,
    val knowledgePoints: List<String> = emptyList(),
    val analysis: String = "",
    val message: String = "",
    val questionType: String = "unknown",
    val question: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    val wrongReason: String = "",
    val reviewSuggestion: String = "",
    val confidence: Double = 0.0
)

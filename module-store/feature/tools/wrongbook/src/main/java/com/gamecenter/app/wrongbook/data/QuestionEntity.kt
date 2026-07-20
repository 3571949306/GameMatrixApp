package com.gamecenter.app.wrongbook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 错题实体。
 *
 * 第三阶段（v3）扩展字段：
 * - AI 解析返回的题型、清洗题干、选项、答案、错因、复习建议、置信度
 * - OCR 原始文本与用户校正文本分离
 * - 来源类型与 OCR/AI 引擎溯源
 *
 * @param id 主键，自增
 * @param rawText 用户确认后的题目文本（兼容旧版本字段）
 * @param subject 科目名称
 * @param difficulty 难度 1-5
 * @param analysis AI 解析结果
 * @param knowledgePoints 知识点列表，JSON 数组字符串
 * @param imagePath 原始图片本地路径，可为空
 * @param createdAt 创建时间戳
 * @param updatedAt 最后更新时间戳
 * @param mastery 掌握度 0-100
 * @param isFavorite 是否收藏
 * @param sortOrder 排序权重
 * @param tags 自定义标签
 * @param questionType 题型枚举：single_choice/multiple_choice/judge/fill_blank/short_answer/unknown
 * @param question AI 清洗后的题干文本（可能与 rawText 一致，也可能被规范化）
 * @param optionsJson 选项 JSON 数组字符串
 * @param answer 正确答案
 * @param wrongReason 易错原因
 * @param reviewSuggestion 复习建议
 * @param confidence AI 置信度 0.0-1.0
 * @param ocrText OCR 引擎原始输出（未经过用户编辑）
 * @param correctedText 用户校正后的文本（与 rawText 同步）
 * @param sourceType 题目来源：photo/album/manual
 * @param ocrProvider OCR 提供方：local/scnet/baidu
 * @param aiProvider AI 提供方：cloud/backend_proxy/local
 * @param aiModel AI 模型名称
 */
@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawText: String,
    val subject: String,
    val difficulty: Int = 3,
    val analysis: String = "",
    val knowledgePoints: String = "[]",
    val imagePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val mastery: Int = 0,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
    val tags: String = "",
    // ===== 第三阶段扩展字段（v3） =====
    val questionType: String = "unknown",
    val question: String = "",
    val optionsJson: String = "[]",
    val answer: String = "",
    val wrongReason: String = "",
    val reviewSuggestion: String = "",
    val confidence: Double = 0.0,
    val ocrText: String = "",
    val correctedText: String = "",
    val sourceType: String = "manual",
    val ocrProvider: String = "",
    val aiProvider: String = "",
    val aiModel: String = ""
)

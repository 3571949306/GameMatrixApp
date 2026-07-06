package com.gamecenter.app.wrongbook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 错题实体。
 *
 * @param id 主键，自增
 * @param rawText OCR 识别或手动输入的原始题目文本
 * @param subject 科目名称
 * @param difficulty 难度 1-5
 * @param analysis AI 解析结果
 * @param knowledgePoints 知识点列表，JSON 数组字符串
 * @param imagePath 原始图片本地路径，可为空
 * @param createdAt 创建时间戳
 * @param updatedAt 最后更新时间戳
 * @param mastery 掌握度 0-100
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
    val tags: String = ""
)

package com.gamecenter.app.wrongbook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 复习计划实体。
 *
 * 基于艾宾浩斯遗忘曲线，为每道错题生成多轮复习提醒。
 *
 * @param id 主键
 * @param questionId 关联错题 ID
 * @param stage 复习阶段 1-8
 * @param scheduledAt 计划复习时间戳
 * @param completedAt 实际完成时间戳，0 表示未完成
 * @param status 状态：pending / completed / skipped
 */
@Entity(
    tableName = "review_plans",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["questionId"]), Index(value = ["scheduledAt"])]
)
data class ReviewPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionId: Long,
    val stage: Int,
    val scheduledAt: Long,
    val completedAt: Long = 0,
    val status: String = "pending"
)

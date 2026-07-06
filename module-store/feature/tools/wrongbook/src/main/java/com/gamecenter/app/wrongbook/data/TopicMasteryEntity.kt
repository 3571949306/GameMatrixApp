package com.gamecenter.app.wrongbook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识点掌握度实体。
 *
 * 按科目 + 知识点聚合，计算平均掌握度。
 *
 * @param id 主键
 * @param subject 科目
 * @param topic 知识点名称
 * @param mastery 平均掌握度 0-100
 * @param questionCount 关联错题数量
 */
@Entity(
    tableName = "topic_mastery",
    indices = [Index(value = ["subject", "topic"], unique = true)]
)
data class TopicMasteryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val topic: String,
    val mastery: Int = 0,
    val questionCount: Int = 0
)

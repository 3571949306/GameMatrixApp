package com.gamecenter.app.wrongbook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 科目实体。
 *
 * @param id 主键
 * @param name 科目名称，唯一
 * @param color 科目颜色（ARGB 整型），用于 UI 区分
 * @param sortOrder 排序权重
 */
@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int = 0,
    val sortOrder: Int = 0
)

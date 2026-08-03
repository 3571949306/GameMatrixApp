package com.gamecenter.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val achievementId: String,
    val gameId: String = "",
    val unlocked: Boolean = false,
    val progress: Int = 0,
    val maxProgress: Int = 0,
    val unlockedAt: Long = 0L,
    val title: String = "",
    val description: String = ""
)

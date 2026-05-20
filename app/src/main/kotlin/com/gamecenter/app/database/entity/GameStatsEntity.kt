package com.gamecenter.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_stats")
data class GameStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String,
    val result: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

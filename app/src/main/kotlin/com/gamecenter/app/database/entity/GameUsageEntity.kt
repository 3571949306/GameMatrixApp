package com.gamecenter.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_usage")
data class GameUsageEntity(
    @PrimaryKey
    val gameId: String,
    val playCount: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val totalPlayTimeMs: Long = 0L,
    val highScore: Long = 0L,
    val userRating: Int = 0,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long = 0L
)

package com.gamecenter.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamecenter.app.database.entity.GameStatsEntity

@Dao
interface GameStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stats: GameStatsEntity): Long

    @Query("SELECT * FROM game_stats WHERE gameType = :gameType ORDER BY timestamp DESC")
    fun getByGameType(gameType: String): List<GameStatsEntity>

    @Query("SELECT COUNT(*) FROM game_stats WHERE gameType = :gameType AND result = :result")
    fun countByGameTypeAndResult(gameType: String, result: String): Int

    @Query("DELETE FROM game_stats WHERE timestamp < :cutoffTime")
    fun deleteOlderThan(cutoffTime: Long): Int
}

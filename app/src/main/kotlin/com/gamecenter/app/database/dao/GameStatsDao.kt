package com.gamecenter.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamecenter.app.database.entity.GameStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: GameStatsEntity): Long

    @Query("SELECT * FROM game_stats WHERE gameType = :gameType ORDER BY timestamp DESC")
    fun getByGameType(gameType: String): Flow<List<GameStatsEntity>>

    @Query("SELECT * FROM game_stats WHERE gameType = :gameType ORDER BY timestamp DESC")
    suspend fun getByGameTypeSync(gameType: String): List<GameStatsEntity>

    @Query("SELECT COUNT(*) FROM game_stats WHERE gameType = :gameType AND result = :result")
    suspend fun countByGameTypeAndResult(gameType: String, result: String): Int

    @Query("DELETE FROM game_stats WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int
}

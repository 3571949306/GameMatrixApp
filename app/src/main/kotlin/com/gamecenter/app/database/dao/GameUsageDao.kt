package com.gamecenter.app.database.dao

import androidx.room.*
import com.gamecenter.app.database.entity.GameUsageEntity

@Dao
interface GameUsageDao {
    @Query("SELECT * FROM game_usage")
    suspend fun getAll(): List<GameUsageEntity>

    @Query("SELECT * FROM game_usage WHERE gameId = :gameId")
    suspend fun getById(gameId: String): GameUsageEntity?

    @Query("SELECT * FROM game_usage WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC")
    suspend fun getFavorites(): List<GameUsageEntity>

    @Query("SELECT * FROM game_usage ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<GameUsageEntity>

    @Query("SELECT * FROM game_usage ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int): List<GameUsageEntity>

    @Upsert
    suspend fun upsert(usage: GameUsageEntity)

    @Upsert
    suspend fun upsertAll(usages: List<GameUsageEntity>)

    @Query("UPDATE game_usage SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE gameId = :gameId")
    suspend fun incrementPlayCount(gameId: String, timestamp: Long)

    @Query("UPDATE game_usage SET wins = wins + 1 WHERE gameId = :gameId")
    suspend fun incrementWin(gameId: String)

    @Query("UPDATE game_usage SET losses = losses + 1 WHERE gameId = :gameId")
    suspend fun incrementLoss(gameId: String)

    @Query("UPDATE game_usage SET totalPlayTimeMs = totalPlayTimeMs + :additionalMs WHERE gameId = :gameId")
    suspend fun addPlayTime(gameId: String, additionalMs: Long)

    @Query("UPDATE game_usage SET highScore = :score WHERE gameId = :gameId AND highScore < :score")
    suspend fun updateHighScore(gameId: String, score: Long)

    @Query("UPDATE game_usage SET userRating = :rating WHERE gameId = :gameId")
    suspend fun updateRating(gameId: String, rating: Int)

    @Query("UPDATE game_usage SET isFavorite = :favorite WHERE gameId = :gameId")
    suspend fun setFavorite(gameId: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM game_usage WHERE isFavorite = 1")
    suspend fun getFavoriteCount(): Int

    @Query("DELETE FROM game_usage WHERE gameId = :gameId")
    suspend fun delete(gameId: String)

    // ====== 非 suspend 同步版本（供 Java/同步调用方使用，在调用线程执行） ======

    @Query("SELECT * FROM game_usage WHERE gameId = :gameId")
    fun getByIdSync(gameId: String): GameUsageEntity?

    @Query("SELECT * FROM game_usage")
    fun getAllSync(): List<GameUsageEntity>

    @Query("SELECT * FROM game_usage WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC")
    fun getFavoritesSync(): List<GameUsageEntity>

    @Query("SELECT * FROM game_usage ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayedSync(limit: Int): List<GameUsageEntity>

    @Upsert
    fun upsertSync(usage: GameUsageEntity)

    @Query("UPDATE game_usage SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE gameId = :gameId")
    fun incrementPlayCountSync(gameId: String, timestamp: Long)

    @Query("UPDATE game_usage SET wins = wins + 1 WHERE gameId = :gameId")
    fun incrementWinSync(gameId: String)

    @Query("UPDATE game_usage SET losses = losses + 1 WHERE gameId = :gameId")
    fun incrementLossSync(gameId: String)

    @Query("UPDATE game_usage SET totalPlayTimeMs = totalPlayTimeMs + :additionalMs WHERE gameId = :gameId")
    fun addPlayTimeSync(gameId: String, additionalMs: Long)

    @Query("UPDATE game_usage SET highScore = :score WHERE gameId = :gameId AND highScore < :score")
    fun updateHighScoreSync(gameId: String, score: Long)

    @Query("UPDATE game_usage SET userRating = :rating WHERE gameId = :gameId")
    fun updateRatingSync(gameId: String, rating: Int)

    @Query("UPDATE game_usage SET isFavorite = :favorite WHERE gameId = :gameId")
    fun setFavoriteSync(gameId: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM game_usage WHERE isFavorite = 1")
    fun getFavoriteCountSync(): Int

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM game_usage")
    fun getTotalPlayCountSync(): Int

    @Query("SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM game_usage")
    fun getTotalPlayTimeSync(): Long
}

package com.gamecenter.app.database.dao

import androidx.room.*
import com.gamecenter.app.database.entity.AchievementEntity

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE achievementId = :id")
    suspend fun getById(id: String): AchievementEntity?

    @Query("SELECT * FROM achievements WHERE gameId = :gameId")
    suspend fun getByGameId(gameId: String): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE unlocked = 1 ORDER BY unlockedAt DESC")
    suspend fun getUnlocked(): List<AchievementEntity>

    @Upsert
    suspend fun upsert(achievement: AchievementEntity)

    @Upsert
    suspend fun upsertAll(achievements: List<AchievementEntity>)

    @Query("UPDATE achievements SET progress = :progress WHERE achievementId = :id")
    suspend fun updateProgress(id: String, progress: Int)

    @Query("UPDATE achievements SET unlocked = 1, unlockedAt = :timestamp WHERE achievementId = :id")
    suspend fun unlock(id: String, timestamp: Long)

    @Query("DELETE FROM achievements WHERE achievementId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM achievements WHERE unlocked = 1")
    suspend fun getUnlockedCount(): Int

    // ====== 非 suspend 同步版本（供 Java/同步调用方使用，在调用线程执行） ======

    @Query("SELECT * FROM achievements WHERE achievementId = :id")
    fun getByIdSync(id: String): AchievementEntity?

    @Query("SELECT * FROM achievements WHERE unlocked = 1 ORDER BY unlockedAt DESC")
    fun getUnlockedSync(): List<AchievementEntity>

    @Query("SELECT COUNT(*) FROM achievements WHERE unlocked = 1")
    fun getUnlockedCountSync(): Int

    @Query("SELECT * FROM achievements WHERE gameId = :gameId")
    fun getByGameIdSync(gameId: String): List<AchievementEntity>

    @Upsert
    fun upsertSync(achievement: AchievementEntity)

    @Query("UPDATE achievements SET progress = :progress WHERE achievementId = :id")
    fun updateProgressSync(id: String, progress: Int)

    @Query("UPDATE achievements SET unlocked = 1, unlockedAt = :timestamp WHERE achievementId = :id")
    fun unlockSync(id: String, timestamp: Long)
}

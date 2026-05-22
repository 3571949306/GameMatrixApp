package com.gamecenter.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamecenter.app.database.entity.AiMessageEntity

@Dao
interface AiMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: AiMessageEntity): Long

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: String): List<AiMessageEntity>

    @Query("DELETE FROM ai_messages WHERE sessionId = :sessionId")
    fun deleteBySessionId(sessionId: String): Int

    @Query("DELETE FROM ai_messages WHERE timestamp < :cutoffTime")
    fun deleteOlderThan(cutoffTime: Long): Int
}

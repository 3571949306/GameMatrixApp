package com.gamecenter.app.modular

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleDao {

    @Query("SELECT * FROM modules ORDER BY updatedAt DESC")
    fun getAllModules(): Flow<List<ModuleEntity>>

    @Query("SELECT * FROM modules WHERE moduleId = :moduleId")
    suspend fun getModuleById(moduleId: String): ModuleEntity?

    @Query("SELECT * FROM modules WHERE state = :state")
    suspend fun getModulesByState(state: String): List<ModuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(module: ModuleEntity)

    @Update
    suspend fun update(module: ModuleEntity)

    @Query("DELETE FROM modules WHERE moduleId = :moduleId")
    suspend fun deleteById(moduleId: String)

    @Query("UPDATE modules SET state = :state, updatedAt = :timestamp WHERE moduleId = :moduleId")
    suspend fun updateState(moduleId: String, state: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE modules SET downloadedSize = :size, updatedAt = :timestamp WHERE moduleId = :moduleId")
    suspend fun updateDownloadedSize(moduleId: String, size: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE modules SET state = :state, localPath = :path, updatedAt = :timestamp WHERE moduleId = :moduleId")
    suspend fun updateStateAndPath(moduleId: String, state: String, path: String, timestamp: Long = System.currentTimeMillis())
}

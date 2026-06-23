package com.gamecenter.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gamecenter.app.database.dao.GameStatsDao
import com.gamecenter.app.database.entity.GameStatsEntity

@Database(
    entities = [GameStatsEntity::class],
    version = 1,
    exportSchema = true   // Phase 2.2: 启用 schema 导出 (JSON 在 app/schemas/)
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameStatsDao(): GameStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "GameMatrix_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

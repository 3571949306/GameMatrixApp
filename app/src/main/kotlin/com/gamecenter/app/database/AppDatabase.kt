package com.gamecenter.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gamecenter.app.database.dao.AiMessageDao
import com.gamecenter.app.database.dao.GameStatsDao
import com.gamecenter.app.database.entity.AiMessageEntity
import com.gamecenter.app.database.entity.GameStatsEntity

@Database(
    entities = [AiMessageEntity::class, GameStatsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun gameStatsDao(): GameStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gamecenter_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

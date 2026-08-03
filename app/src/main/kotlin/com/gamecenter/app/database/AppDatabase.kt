package com.gamecenter.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gamecenter.app.database.dao.AchievementDao
import com.gamecenter.app.database.dao.GameStatsDao
import com.gamecenter.app.database.dao.GameUsageDao
import com.gamecenter.app.database.entity.AchievementEntity
import com.gamecenter.app.database.entity.GameStatsEntity
import com.gamecenter.app.database.entity.GameUsageEntity

@Database(
    entities = [
        GameStatsEntity::class,
        AchievementEntity::class,
        GameUsageEntity::class
    ],
    version = 2,
    exportSchema = true   // Phase 2.2: 启用 schema 导出 (JSON 在 app/schemas/)
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameStatsDao(): GameStatsDao
    abstract fun achievementDao(): AchievementDao
    abstract fun gameUsageDao(): GameUsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "GameMatrix_database"
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    // 注意：以下两行因保持 SP→Room 迁移的同步 API 行为而启用。
                    // 原 SP 实现允许主线程调用，因此非 suspend DAO 方法（*Sync）也允许主线程调用。
                    // 如需后续严格异步访问，可分阶段改造 Store 类为 suspend。
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

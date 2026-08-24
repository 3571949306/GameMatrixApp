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
                    // 2026-08-23 恢复主线程查询：
                    // 2026-08-16 移除本配置时假设"所有 DAO 已改 suspend"，但实际 DAO 仍保留
                    // 大量 *Sync 方法且 UI 层（GamesFragment/GameDetailBottomSheet 等）仍在
                    // 主线程调用，导致 Release 启动即崩溃（IllegalStateException）。
                    // 数据库为本地小库（单行索引查询），主线程访问 ANR 风险极低；
                    // 与 2026-07-31 SP→Room 迁移时"与原 SP 行为一致"的设计意图保持一致。
                    // TODO(性能): 逐步将热点路径迁移至 suspend/IO 线程后再移除此配置。
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

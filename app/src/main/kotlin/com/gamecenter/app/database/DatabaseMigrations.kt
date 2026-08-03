package com.gamecenter.app.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移注册中心。
 *
 * 当 Entity 或 @Database version 发生变化时，在此添加对应的 [Migration] 对象，
 * 并通过 [ALL_MIGRATIONS] 数组注入到 Room.databaseBuilder()。
 *
 * <p>迁移测试见 `app/src/androidTest/java/com/gamecenter/app/database/AppDatabaseMigrationTest.kt`。
 *
 * <h3>添加新迁移的步骤：</h3>
 * <ol>
 *   <li>修改 Entity（新增/删除字段或表）；</li>
 *   <li>将 @Database version 从 N 升到 N+1；</li>
 *   <li>在此文件添加 `val MIGRATION_N_N1 = object : Migration(N, N+1) { ... }`；</li>
 *   <li>将其加入 [ALL_MIGRATIONS] 数组；</li>
 *   <li>在 `AppDatabaseMigrationTest` 中添加对应的验证测试；</li>
 *   <li>运行 `./gradlew :app:connectedDebugAndroidTest` 验证。</li>
 * </ol>
 */
object DatabaseMigrations {

    /**
     * Migration 1→2: P0 SharedPreferences → Room 迁移。
     *
     * 新增两张表承载原由 SharedPreferences 持久化的结构化数据：
     * <ul>
     *   <li>`achievements`：原 `game_achievements` / `achievements` SP（成就解锁状态）</li>
     *   <li>`game_usage`：原 `game_usage` SP（游戏次数、胜负、时长、评分、收藏）</li>
     * </ul>
     *
     * 注意：此迁移仅创建表结构；将旧 SP 数据搬运到 Room 的工作由调用方按需进行
     * （DataBackupHelper 的导入路径负责旧备份文件的兼容写入）。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS achievements (
                    achievementId TEXT NOT NULL PRIMARY KEY,
                    gameId TEXT NOT NULL DEFAULT '',
                    unlocked INTEGER NOT NULL DEFAULT 0,
                    progress INTEGER NOT NULL DEFAULT 0,
                    maxProgress INTEGER NOT NULL DEFAULT 0,
                    unlockedAt INTEGER NOT NULL DEFAULT 0,
                    title TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS game_usage (
                    gameId TEXT NOT NULL PRIMARY KEY,
                    playCount INTEGER NOT NULL DEFAULT 0,
                    wins INTEGER NOT NULL DEFAULT 0,
                    losses INTEGER NOT NULL DEFAULT 0,
                    totalPlayTimeMs INTEGER NOT NULL DEFAULT 0,
                    highScore INTEGER NOT NULL DEFAULT 0,
                    userRating INTEGER NOT NULL DEFAULT 0,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    lastPlayedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    /**
     * 所有已注册的迁移。当前 version=2。
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
}

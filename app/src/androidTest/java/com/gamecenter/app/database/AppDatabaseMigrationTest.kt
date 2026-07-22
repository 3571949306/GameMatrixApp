package com.gamecenter.app.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/**
 * Phase 2.2: Room 迁移测试模板
 *
 * 当你修改 Entity 或 @Database version 时, 这个测试会验证:
 * 1. 老 schema + Migration 路径 -> 新 schema 不会丢数据
 * 2. Migration 的 SQL 是合法可执行的
 * 3. 跨多个版本连续 migration 也 work
 *
 * 详细文档: https://developer.android.com/training/data-storage/room/migrating-db-versions
 *
 * <p>当前 schema (v1): 表 `game_stats`，列: id, gameType, result, durationMs, timestamp。
 * 当需要升级到 v2 时，在 [DatabaseMigrations] 中添加 MIGRATION_1_2 并取消下方测试注释。</p>
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "GameMatrix_database"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /**
     * 验证 v1 数据库可以正常创建并插入数据。
     *
     * 这是当前唯一活跃的测试——确保 v1 schema 与 Entity 定义一致。
     * 当 @Database version 升到 2 时，取消下方 migration_1_2_works 的注释并实现 MIGRATION_1_2。
     */
    @Test
    fun v1_schema_creates_and_inserts_correctly() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("""
                INSERT INTO game_stats (gameType, result, durationMs, timestamp)
                VALUES ('gomoku', 'WIN', 120000, 1000)
            """.trimIndent())
        }

        helper.runMigrationsAndValidate(TEST_DB, 1, true).use { db ->
            val cursor = db.query("SELECT gameType, result FROM game_stats WHERE gameType = 'gomoku'")
            cursor.moveToFirst()
            assertEquals("gomoku", cursor.getString(0))
            assertEquals("WIN", cursor.getString(1))
        }
    }

    /*
     * === 模板：当 schema 从 v1 升级到 v2 时取消注释 ===
     *
     * 步骤：
     * 1. 在 DatabaseMigrations.kt 中定义 MIGRATION_1_2
     * 2. 将 @Database version 改为 2
     * 3. 取消下方注释并调整 SQL
     *
    @Test
    fun migration_1_2_works() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("""
                INSERT INTO game_stats (gameType, result, durationMs, timestamp)
                VALUES ('gomoku', 'WIN', 120000, 1000)
            """.trimIndent())
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, DatabaseMigrations.MIGRATION_1_2).use { db ->
            val cursor = db.query("SELECT gameType FROM game_stats WHERE gameType = 'gomoku'")
            cursor.moveToFirst()
            assertEquals("gomoku", cursor.getString(0))
        }
    }
    */
}

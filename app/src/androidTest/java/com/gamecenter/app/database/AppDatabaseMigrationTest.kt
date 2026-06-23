package com.gamecenter.app.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
     * 示例: 验证 v1 直接打开 (还没升级过)
     * 1. 在 schemas/ 下放 v1.json (KSP 自动生成, 在 build/ 目录)
     * 2. 拷贝到 app/schemas/com.gamecenter.app.database.AppDatabase/1.json
     * 3. 跑这个测试
     */
    @Test
    fun migration_1_2_works() {
        // 创建一个 v1 的 db
        helper.createDatabase(TEST_DB, 1).use { db ->
            // 插点测试数据
            db.execSQL("""
                INSERT INTO GameStatsEntity (gameId, wins, losses, lastPlayedAt)
                VALUES ('gomoku', 5, 3, 1000)
            """.trimIndent())
        }

        // 用 Migration 升级到 v2
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            // 验证数据没丢
            val cursor = db.query("SELECT wins FROM GameStatsEntity WHERE gameId = 'gomoku'")
            cursor.moveToFirst()
            assertEquals(5, cursor.getInt(0))
        }
    }

    /**
     * 占位的 Migration - 实际用时按你的 schema diff 写
     */
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 例: 加一个新列
                // db.execSQL("ALTER TABLE GameStatsEntity ADD COLUMN draws INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

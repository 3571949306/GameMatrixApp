package com.gamecenter.app.database

import androidx.room.migration.Migration

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
     * 所有已注册的迁移。当前 version=1，无迁移。
     *
     * 未来添加迁移示例：
     * ```kotlin
     * val MIGRATION_1_2 = object : Migration(1, 2) {
     *     override fun migrate(db: SupportSQLiteDatabase) {
     *         db.execSQL("ALTER TABLE game_stats ADD COLUMN difficulty INTEGER NOT NULL DEFAULT 0")
     *     }
     * }
     * ```
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf()
}

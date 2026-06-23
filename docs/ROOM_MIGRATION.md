# Room 数据库迁移

## Phase 2.2 状态

✅ 两个 Database 都启用 `exportSchema = true`:
- `AppDatabase` (game stats, 1 entity, version 1)
- `ModuleDatabase` (module store, 1 entity, version 1)

✅ KSP 配 `room.schemaLocation = $projectDir/schemas`
✅ Migration 测试模板放好 `app/src/androidTest/java/.../AppDatabaseMigrationTest.kt`

## Schema 文件位置

每次 build 完会生成：
- `app/schemas/com.gamecenter.app.database.AppDatabase/1.json`
- `app/schemas/com.gamecenter.app.modular.ModuleDatabase/1.json`

**这些 JSON 必须 commit 到 git**——它们是 migration 测试的"已知好 schema"基线。

## 怎么加一条 Migration

例: 给 `GameStatsEntity` 加一列 `draws`

### 1. 改 Entity
```kotlin
@Entity
data class GameStatsEntity(
    @PrimaryKey val gameId: String,
    val wins: Int,
    val losses: Int,
    val draws: Int = 0,  // 新列
    val lastPlayedAt: Long
)
```

### 2. 改 Database version
```kotlin
@Database(
    entities = [GameStatsEntity::class],
    version = 2,  // 1 -> 2
    exportSchema = true
)
```

### 3. 写 Migration
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE GameStatsEntity ADD COLUMN draws INTEGER NOT NULL DEFAULT 0")
    }
}
```

### 4. 在 AppDatabase 注册
```kotlin
.addMigrations(MIGRATION_1_2)
```

### 5. 跑测试验证
```bash
./gradlew :app:connectedAndroidTest
# 或
./gradlew :app:testDebugUnitTest (instrumented test)
```

### 6. Commit 新 schema JSON
git status 看 `app/schemas/.../2.json`，提交。

## 自动生成的 Schema 检测

Phase 2.2 起的 CI 检查（待 Phase 1.5+ 加 lint check）:
- 如果 `@Database(version=N)` 改了但 `schemas/.../N.json` 没更新 → 编译错误
- 强制开发者每次升 version 都生成 schema

## 多个 Database 共享版本号

`AppDatabase` 和 `ModuleDatabase` 各自维护自己的 version。互相不影响。

## 跨版本 Migration

```kotlin
val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 手动执行 1 -> 2 -> 3 的所有 SQL
        db.execSQL("ALTER TABLE ...")
        db.execSQL("ALTER TABLE ...")
    }
}
```

或者推荐做法：注册多个小 migration（`MIGRATION_1_2`, `MIGRATION_2_3`），Room 会自动连跑。

## 测试小贴士

- Migration 测试跑在 `androidTest` 不是 `test`（需要 SQLite framework）
- `helper.createDatabase(N)` 用 schemas/N.json 创建空库
- `helper.runMigrationsAndValidate()` 跑 migration 链
- 想看真实的 schema diff：`./gradlew :app:exportSchema`（Phase 2+）

## 注意事项

- **不要删旧 schema JSON**——已上线的用户要靠它升级
- **不要改 schema JSON 的格式**——Room 解析失败会编译错
- **migration 必须幂等**（重跑不会爆）——避免 `CREATE TABLE` 失败的情况用 `CREATE TABLE IF NOT EXISTS`
- **大表加列要分步**：先加 nullable 列，再 backfill 数据，最后 NOT NULL（避免一次性大表锁）

## 参考

- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper

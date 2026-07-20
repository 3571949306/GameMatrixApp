package com.gamecenter.app.wrongbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 错题本 Room 数据库。
 *
 * 数据库存放在宿主 App 的私有目录，避免模块卸载后数据丢失。
 */
@Database(
    entities = [
        QuestionEntity::class,
        ReviewPlanEntity::class,
        TopicMasteryEntity::class,
        SubjectEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WrongBookDatabase : RoomDatabase() {

    abstract fun wrongBookDao(): WrongBookDao

    companion object {
        private const val DATABASE_NAME = "wrongbook.db"

        @Volatile
        private var instance: WrongBookDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 第三阶段扩展：增加 AI 解析返回字段、OCR/AI 溯源字段。
         *
         * 注意：SQLite 单条 ALTER TABLE 仅能加一列，因此拆成多条语句。
         * confidence 用 REAL 存储；其余文本字段默认空串；sourceType 默认 manual。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN questionType TEXT NOT NULL DEFAULT 'unknown'")
                db.execSQL("ALTER TABLE questions ADD COLUMN question TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN optionsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE questions ADD COLUMN answer TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN wrongReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN reviewSuggestion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE questions ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN correctedText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE questions ADD COLUMN ocrProvider TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN aiProvider TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN aiModel TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): WrongBookDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrongBookDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

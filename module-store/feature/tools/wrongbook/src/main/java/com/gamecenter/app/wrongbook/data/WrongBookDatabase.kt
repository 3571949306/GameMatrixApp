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
    version = 2,
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

        fun getInstance(context: Context): WrongBookDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrongBookDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

package com.gamecenter.app.wrongbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = false
)
abstract class WrongBookDatabase : RoomDatabase() {

    abstract fun wrongBookDao(): WrongBookDao

    companion object {
        private const val DATABASE_NAME = "wrongbook.db"

        @Volatile
        private var instance: WrongBookDatabase? = null

        fun getInstance(context: Context): WrongBookDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrongBookDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

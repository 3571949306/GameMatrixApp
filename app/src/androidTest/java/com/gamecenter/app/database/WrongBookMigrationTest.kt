package com.gamecenter.app.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamecenter.app.wrongbook.data.WrongBookDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WrongBook 数据库 V1 -> V2 迁移自动化测试类。
 */
@RunWith(AndroidJUnit4::class)
class WrongBookMigrationTest {

    @Test
    fun testMigration1To2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "wrongbook_migration_test.db"
        context.deleteDatabase(dbName)

        // Step 1: Create V1 database manually using SupportSQLiteOpenHelper
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `questions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `answer` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = factory.create(config)
        val v1Db = openHelper.writableDatabase

        // Insert V1 mock data
        v1Db.execSQL("""
            INSERT INTO questions (title, content, answer, createdAt)
            VALUES ('Math Question 1', 'Solve 1+1', '2', 1700000000000)
        """.trimIndent())
        v1Db.close()

        // Step 2: Load with Room and execute MIGRATION_1_2
        val wrongBookDb = Room.databaseBuilder(
            context,
            WrongBookDatabase::class.java,
            dbName
        )
            .addMigrations(WrongBookDatabase.MIGRATION_1_2)
            .build()

        // Verify schema updates and data preservation
        val cursor = wrongBookDb.openHelper.readableDatabase.query("SELECT * FROM questions")
        assertEquals(true, cursor.moveToFirst())

        val titleIndex = cursor.getColumnIndex("title")
        assertEquals("Math Question 1", cursor.getString(titleIndex))

        val isFavoriteIndex = cursor.getColumnIndex("isFavorite")
        val sortOrderIndex = cursor.getColumnIndex("sortOrder")
        val tagsIndex = cursor.getColumnIndex("tags")

        assertEquals(0, cursor.getInt(isFavoriteIndex))
        assertEquals(0, cursor.getInt(sortOrderIndex))
        assertEquals("", cursor.getString(tagsIndex))

        cursor.close()
        wrongBookDb.close()
        context.deleteDatabase(dbName)
    }
}

package com.gamecenter.app.wrongbook.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WrongBookMigrationTest {

    @Test
    fun migration1To2PreservesDataAndAddsColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "wrongbook_migration_test.db"
        context.deleteDatabase(dbName)

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE questions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            content TEXT NOT NULL,
                            answer TEXT NOT NULL,
                            createdAt INTEGER NOT NULL
                        )""".trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val database = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        database.execSQL(
            "INSERT INTO questions (title, content, answer, createdAt) " +
                "VALUES ('Math Question 1', 'Solve 1+1', '2', 1700000000000)"
        )

        WrongBookDatabase.MIGRATION_1_2.migrate(database)

        database.query("SELECT title, isFavorite, sortOrder, tags FROM questions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Math Question 1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals("", cursor.getString(3))
        }

        database.close()
        context.deleteDatabase(dbName)
    }
}

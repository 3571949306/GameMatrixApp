package com.gamecenter.app.browser.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.gamecenter.app.browser.data.dao.BrowserBookmarkDao;
import com.gamecenter.app.browser.data.dao.BrowserDownloadDao;
import com.gamecenter.app.browser.data.dao.BrowserHistoryDao;
import com.gamecenter.app.browser.data.dao.SearchHistoryDao;
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.data.entity.SearchHistoryEntity;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        BrowserHistoryEntity.class,
        BrowserBookmarkEntity.class,
        BrowserDownloadEntity.class,
        SearchHistoryEntity.class
    },
    version = 2,
    exportSchema = false
)
public abstract class BrowserDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "browser_database";

    public abstract BrowserHistoryDao historyDao();
    public abstract BrowserBookmarkDao bookmarkDao();
    public abstract BrowserDownloadDao downloadDao();
    public abstract SearchHistoryDao searchHistoryDao();

    private static volatile BrowserDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE browser_download ADD COLUMN systemDownloadId INTEGER NOT NULL DEFAULT -1");
        }
    };

    public static BrowserDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (BrowserDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            BrowserDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return INSTANCE;
    }
}

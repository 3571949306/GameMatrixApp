package com.gamecenter.app.browser.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.gamecenter.app.browser.data.dao.BrowserBookmarkDao;
import com.gamecenter.app.browser.data.dao.BrowserDownloadDao;
import com.gamecenter.app.browser.data.dao.BrowserHistoryDao;
import com.gamecenter.app.browser.data.dao.BrowserReadingListDao;
import com.gamecenter.app.browser.data.dao.SearchHistoryDao;
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.data.entity.BrowserReadingListEntity;
import com.gamecenter.app.browser.data.entity.SearchHistoryEntity;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        BrowserHistoryEntity.class,
        BrowserBookmarkEntity.class,
        BrowserDownloadEntity.class,
        SearchHistoryEntity.class,
        BrowserReadingListEntity.class
    },
    version = 4,
    exportSchema = false
)
public abstract class BrowserDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "browser_database";

    public abstract BrowserHistoryDao historyDao();
    public abstract BrowserBookmarkDao bookmarkDao();
    public abstract BrowserDownloadDao downloadDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract BrowserReadingListDao readingListDao();

    private static volatile BrowserDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE browser_download ADD COLUMN systemDownloadId INTEGER NOT NULL DEFAULT -1");
        }
    };

    /** v2 → v3：新增 browser_reading_list 表（P1-3 阅读列表） */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS browser_reading_list (" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "url TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "summary TEXT NOT NULL DEFAULT '', " +
                            "host TEXT NOT NULL DEFAULT '', " +
                            "savedAt INTEGER NOT NULL DEFAULT 0, " +
                            "read INTEGER NOT NULL DEFAULT 0)"
            );
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_browser_reading_list_url ON browser_reading_list(url)"
            );
        }
    };

    /** v3 → v4：新增 browser_download.dangerous 字段（D4 危险文件标记） */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Phase 3: 为下载表添加 dangerous 字段，标记危险文件（APK/EXE 等）
            database.execSQL("ALTER TABLE browser_download ADD COLUMN dangerous INTEGER NOT NULL DEFAULT 0");
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
                    )
                    // Phase 3: 安全回退 - 当迁移缺失时允许破坏性迁移（避免旧用户升级失败）
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}

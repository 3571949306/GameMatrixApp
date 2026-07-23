package com.gamecenter.app.games.chinesechess.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.gamecenter.app.games.chinesechess.data.dao.GameRecordDao;
import com.gamecenter.app.games.chinesechess.data.entity.GameRecordEntity;

/**
 * 对局记录 Room Database。
 */
@Database(
    entities = {GameRecordEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class GameRecordDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "game_record_database";

    public abstract GameRecordDao gameRecordDao();

    private static volatile GameRecordDatabase INSTANCE;

    public static GameRecordDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (GameRecordDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            GameRecordDatabase.class,
                            DATABASE_NAME
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}

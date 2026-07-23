package com.gamecenter.app.games.chinesechess.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.gamecenter.app.games.chinesechess.data.entity.GameRecordEntity;

import java.util.List;

/**
 * 对局记录 Room DAO。
 */
@Dao
public interface GameRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GameRecordEntity entity);

    @Update
    void update(GameRecordEntity entity);

    @Delete
    void delete(GameRecordEntity entity);

    @Query("SELECT * FROM game_record WHERE game_id = :gameId")
    GameRecordEntity getById(String gameId);

    @Query("SELECT * FROM game_record ORDER BY start_time DESC")
    List<GameRecordEntity> getAll();

    @Query("SELECT * FROM game_record ORDER BY start_time DESC LIMIT :limit")
    List<GameRecordEntity> getRecent(int limit);

    @Query("SELECT * FROM game_record WHERE difficulty = :difficulty ORDER BY start_time DESC")
    List<GameRecordEntity> getByDifficulty(int difficulty);

    @Query("SELECT * FROM game_record WHERE result = :result ORDER BY start_time DESC")
    List<GameRecordEntity> getByResult(String result);

    @Query("SELECT COUNT(*) FROM game_record")
    int getCount();

    @Query("DELETE FROM game_record")
    void deleteAll();
}

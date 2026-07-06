package com.gamecenter.app.browser.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;

import java.util.List;

@Dao
public interface BrowserHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BrowserHistoryEntity history);

    @Query("SELECT * FROM browser_history WHERE isDeleted = 0 ORDER BY lastVisitTime DESC")
    List<BrowserHistoryEntity> getAllHistory();

    @Query("SELECT * FROM browser_history WHERE isDeleted = 0 AND (title LIKE '%' || :keyword || '%' OR url LIKE '%' || :keyword || '%') ORDER BY lastVisitTime DESC")
    List<BrowserHistoryEntity> searchHistory(String keyword);

    @Query("DELETE FROM browser_history WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM browser_history")
    void deleteAll();

    @Query("UPDATE browser_history SET visitCount = visitCount + 1, lastVisitTime = :lastVisitTime WHERE id = :id")
    void updateVisitCount(long id, long lastVisitTime);

    @Query("SELECT * FROM browser_history WHERE url = :url AND isDeleted = 0 LIMIT 1")
    BrowserHistoryEntity getByUrl(String url);

    @Query("UPDATE browser_history SET title = :title, visitCount = visitCount + 1, lastVisitTime = :lastVisitTime WHERE url = :url AND isDeleted = 0")
    void updateByUrl(String url, String title, long lastVisitTime);
}

package com.gamecenter.app.browser.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gamecenter.app.browser.data.entity.BrowserReadingListEntity;

import java.util.List;

/**
 * 阅读列表 Dao（P1-3）。
 */
@Dao
public interface BrowserReadingListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BrowserReadingListEntity item);

    @Query("DELETE FROM browser_reading_list WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM browser_reading_list WHERE url = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM browser_reading_list")
    void deleteAll();

    @Query("UPDATE browser_reading_list SET read = :read WHERE id = :id")
    void updateRead(long id, int read);

    /** 未读优先，未读中按 savedAt 倒序；已读置底 */
    @Query("SELECT * FROM browser_reading_list ORDER BY read ASC, savedAt DESC")
    List<BrowserReadingListEntity> getAll();

    @Query("SELECT * FROM browser_reading_list WHERE title LIKE '%' || :keyword || '%' OR url LIKE '%' || :keyword || '%' ORDER BY read ASC, savedAt DESC")
    List<BrowserReadingListEntity> search(String keyword);

    @Query("SELECT COUNT(*) FROM browser_reading_list WHERE url = :url")
    int countByUrl(String url);

    @Query("SELECT * FROM browser_reading_list WHERE url = :url LIMIT 1")
    BrowserReadingListEntity getByUrl(String url);

    @Query("SELECT COUNT(*) FROM browser_reading_list WHERE read = 0")
    int countUnread();
}

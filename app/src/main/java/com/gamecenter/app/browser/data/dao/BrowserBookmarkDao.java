package com.gamecenter.app.browser.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;

import java.util.List;

@Dao
public interface BrowserBookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BrowserBookmarkEntity bookmark);

    @Query("DELETE FROM browser_bookmark WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM browser_bookmark ORDER BY sortOrder ASC, createTime DESC")
    List<BrowserBookmarkEntity> getAllBookmarks();

    @Query("SELECT * FROM browser_bookmark WHERE title LIKE '%' || :keyword || '%' OR url LIKE '%' || :keyword || '%' ORDER BY sortOrder ASC, createTime DESC")
    List<BrowserBookmarkEntity> searchBookmarks(String keyword);

    @Query("SELECT COUNT(*) FROM browser_bookmark WHERE url = :url")
    int countByUrl(String url);

    @Query("DELETE FROM browser_bookmark WHERE url = :url")
    void deleteByUrl(String url);

    @Query("DELETE FROM browser_bookmark")
    void deleteAll();

    @Query("SELECT * FROM browser_bookmark WHERE url = :url LIMIT 1")
    BrowserBookmarkEntity getByUrl(String url);
}

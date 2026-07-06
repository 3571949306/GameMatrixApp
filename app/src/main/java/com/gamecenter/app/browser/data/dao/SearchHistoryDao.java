package com.gamecenter.app.browser.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.gamecenter.app.browser.data.entity.SearchHistoryEntity;

import java.util.List;

@Dao
public interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SearchHistoryEntity searchHistory);

    @Query("SELECT * FROM browser_search_history ORDER BY createTime DESC LIMIT :limit")
    List<SearchHistoryEntity> getRecentSearches(int limit);

    @Query("DELETE FROM browser_search_history WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM browser_search_history")
    void deleteAll();

    @Query("SELECT * FROM browser_search_history WHERE keyword = :keyword LIMIT 1")
    SearchHistoryEntity getByKeyword(String keyword);

    @Update
    void update(SearchHistoryEntity searchHistory);
}

package com.gamecenter.app.browser.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;

import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.entity.SearchHistoryEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 搜索历史 Repository。
 */
public class SearchHistoryRepository {

    private final BrowserDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SearchHistoryRepository(@NonNull Application application) {
        database = BrowserDatabase.getInstance(application);
    }

    /**
     * 保存搜索关键词，同一关键词更新时间和计数。
     */
    public void saveSearchHistory(@NonNull String keyword, @NonNull String searchEngine) {
        if (keyword.trim().isEmpty()) return;
        executor.execute(() -> {
            try {
                SearchHistoryEntity existing = database.searchHistoryDao().getByKeyword(keyword);
                long now = System.currentTimeMillis();
                if (existing != null) {
                    existing.setCount(existing.getCount() + 1);
                    existing.setCreateTime(now);
                    existing.setSearchEngine(searchEngine);
                    database.searchHistoryDao().update(existing);
                } else {
                    SearchHistoryEntity entity = new SearchHistoryEntity();
                    entity.setKeyword(keyword);
                    entity.setSearchEngine(searchEngine);
                    entity.setCreateTime(now);
                    entity.setCount(1);
                    database.searchHistoryDao().insert(entity);
                }
            } catch (Exception ignored) {}
        });
    }

    public void getRecentSearches(int limit, @NonNull SearchListCallback callback) {
        executor.execute(() -> {
            try {
                List<SearchHistoryEntity> list = database.searchHistoryDao().getRecentSearches(limit);
                callback.onResult(list);
            } catch (Exception e) {
                callback.onResult(new java.util.ArrayList<>());
            }
        });
    }

    public void deleteAll() {
        executor.execute(() -> {
            try {
                database.searchHistoryDao().deleteAll();
            } catch (Exception ignored) {}
        });
    }

    public interface SearchListCallback {
        void onResult(List<SearchHistoryEntity> list);
    }
}

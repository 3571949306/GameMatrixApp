package com.gamecenter.app.browser.data.repository;

import android.app.Application;
import android.util.Log;

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
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private synchronized ExecutorService getExecutor() {
        if (executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        return executor;
    }

    public void shutdown() {
        if (!getExecutor().isShutdown()) {
            getExecutor().shutdown();
        }
    }

    public SearchHistoryRepository(@NonNull Application application) {
        database = BrowserDatabase.getInstance(application);
    }

    /**
     * 保存搜索关键词，同一关键词更新时间和计数。
     */
    public void saveSearchHistory(@NonNull String keyword, @NonNull String searchEngine) {
        if (keyword.trim().isEmpty()) return;
        getExecutor().execute(() -> {
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
            } catch (Exception e) { Log.w("SearchHistoryRepo", "saveSearchHistory failed", e); }
        });
    }

    public void getRecentSearches(int limit, @NonNull SearchListCallback callback) {
        getExecutor().execute(() -> {
            try {
                List<SearchHistoryEntity> list = database.searchHistoryDao().getRecentSearches(limit);
                callback.onResult(list);
            } catch (Exception e) {
                callback.onResult(new java.util.ArrayList<>());
            }
        });
    }

    public void deleteAll() {
        getExecutor().execute(() -> {
            try {
                database.searchHistoryDao().deleteAll();
            } catch (Exception e) { Log.w("SearchHistoryRepo", "saveSearchHistory failed", e); }
        });
    }

    public interface SearchListCallback {
        void onResult(List<SearchHistoryEntity> list);
    }
}

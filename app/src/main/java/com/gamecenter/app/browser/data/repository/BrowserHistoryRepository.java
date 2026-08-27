package com.gamecenter.app.browser.data.repository;

import android.app.Application;

import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.dao.BrowserHistoryDao;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BrowserHistoryRepository {

    private final BrowserHistoryDao historyDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public BrowserHistoryRepository(Application application) {
        BrowserDatabase db = BrowserDatabase.getInstance(application);
        historyDao = db.historyDao();
    }

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

    public void insert(BrowserHistoryEntity history) {
        getExecutor().execute(() -> historyDao.insert(history));
    }

    public void getAllHistory(HistoryListCallback callback) {
        getExecutor().execute(() -> {
            List<BrowserHistoryEntity> list = historyDao.getAllHistory();
            callback.onResult(list);
        });
    }

    public void searchHistory(String keyword, HistoryListCallback callback) {
        getExecutor().execute(() -> {
            List<BrowserHistoryEntity> list = historyDao.searchHistory(keyword);
            callback.onResult(list);
        });
    }

    public void deleteById(long id) {
        getExecutor().execute(() -> historyDao.deleteById(id));
    }

    public void deleteAll() {
        getExecutor().execute(() -> historyDao.deleteAll());
    }

    public void updateVisitCount(long id, long lastVisitTime) {
        getExecutor().execute(() -> historyDao.updateVisitCount(id, lastVisitTime));
    }

    public interface HistoryListCallback {
        void onResult(List<BrowserHistoryEntity> list);
    }
}

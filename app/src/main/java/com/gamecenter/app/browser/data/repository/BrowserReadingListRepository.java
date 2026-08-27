package com.gamecenter.app.browser.data.repository;

import android.app.Application;

import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.dao.BrowserReadingListDao;
import com.gamecenter.app.browser.data.entity.BrowserReadingListEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阅读列表 Repository（P1-3）。
 */
public class BrowserReadingListRepository {

    private final BrowserReadingListDao dao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public BrowserReadingListRepository(Application application) {
        BrowserDatabase db = BrowserDatabase.getInstance(application);
        dao = db.readingListDao();
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

    public void insert(BrowserReadingListEntity item) {
        getExecutor().execute(() -> dao.insert(item));
    }

    public void insert(BrowserReadingListEntity item, InsertCallback callback) {
        getExecutor().execute(() -> {
            long id = dao.insert(item);
            if (callback != null) callback.onResult(id);
        });
    }

    public void deleteById(long id) {
        getExecutor().execute(() -> dao.deleteById(id));
    }

    public void deleteByUrl(String url) {
        getExecutor().execute(() -> dao.deleteByUrl(url));
    }

    public void deleteAll() {
        getExecutor().execute(dao::deleteAll);
    }

    public void markRead(long id, boolean read) {
        getExecutor().execute(() -> dao.updateRead(id, read ? 1 : 0));
    }

    public void getAll(ListCallback callback) {
        getExecutor().execute(() -> {
            List<BrowserReadingListEntity> list = dao.getAll();
            callback.onResult(list);
        });
    }

    public void search(String keyword, ListCallback callback) {
        getExecutor().execute(() -> {
            List<BrowserReadingListEntity> list = dao.search(keyword);
            callback.onResult(list);
        });
    }

    public void exists(String url, ExistsCallback callback) {
        getExecutor().execute(() -> {
            int count = dao.countByUrl(url);
            callback.onResult(count > 0);
        });
    }

    public void countUnread(UnreadCountCallback callback) {
        getExecutor().execute(() -> callback.onResult(dao.countUnread()));
    }

    public interface ListCallback { void onResult(List<BrowserReadingListEntity> list); }
    public interface ExistsCallback { void onResult(boolean exists); }
    public interface UnreadCountCallback { void onResult(int count); }
    public interface InsertCallback { void onResult(long id); }
}

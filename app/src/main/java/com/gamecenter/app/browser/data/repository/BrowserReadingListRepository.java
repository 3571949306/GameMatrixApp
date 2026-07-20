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
    private final ExecutorService executor;

    public BrowserReadingListRepository(Application application) {
        BrowserDatabase db = BrowserDatabase.getInstance(application);
        dao = db.readingListDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public void insert(BrowserReadingListEntity item) {
        executor.execute(() -> dao.insert(item));
    }

    public void insert(BrowserReadingListEntity item, InsertCallback callback) {
        executor.execute(() -> {
            long id = dao.insert(item);
            if (callback != null) callback.onResult(id);
        });
    }

    public void deleteById(long id) {
        executor.execute(() -> dao.deleteById(id));
    }

    public void deleteByUrl(String url) {
        executor.execute(() -> dao.deleteByUrl(url));
    }

    public void deleteAll() {
        executor.execute(dao::deleteAll);
    }

    public void markRead(long id, boolean read) {
        executor.execute(() -> dao.updateRead(id, read ? 1 : 0));
    }

    public void getAll(ListCallback callback) {
        executor.execute(() -> {
            List<BrowserReadingListEntity> list = dao.getAll();
            callback.onResult(list);
        });
    }

    public void search(String keyword, ListCallback callback) {
        executor.execute(() -> {
            List<BrowserReadingListEntity> list = dao.search(keyword);
            callback.onResult(list);
        });
    }

    public void exists(String url, ExistsCallback callback) {
        executor.execute(() -> {
            int count = dao.countByUrl(url);
            callback.onResult(count > 0);
        });
    }

    public void countUnread(UnreadCountCallback callback) {
        executor.execute(() -> callback.onResult(dao.countUnread()));
    }

    public interface ListCallback { void onResult(List<BrowserReadingListEntity> list); }
    public interface ExistsCallback { void onResult(boolean exists); }
    public interface UnreadCountCallback { void onResult(int count); }
    public interface InsertCallback { void onResult(long id); }
}

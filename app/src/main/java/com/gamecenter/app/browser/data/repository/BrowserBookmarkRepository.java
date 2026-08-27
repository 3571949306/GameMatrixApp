package com.gamecenter.app.browser.data.repository;

import android.app.Application;

import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.dao.BrowserBookmarkDao;
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BrowserBookmarkRepository {

    private final BrowserBookmarkDao bookmarkDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public BrowserBookmarkRepository(Application application) {
        BrowserDatabase db = BrowserDatabase.getInstance(application);
        bookmarkDao = db.bookmarkDao();
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

    public void insert(BrowserBookmarkEntity bookmark) {
        getExecutor().execute(() -> bookmarkDao.insert(bookmark));
    }

    public void deleteById(long id) {
        getExecutor().execute(() -> bookmarkDao.deleteById(id));
    }

    public void deleteAll() {
        getExecutor().execute(() -> bookmarkDao.deleteAll());
    }

    public void getAllBookmarks(BookmarkListCallback callback) {
        getExecutor().execute(() -> {
            List<BrowserBookmarkEntity> list = bookmarkDao.getAllBookmarks();
            callback.onResult(list);
        });
    }

    public void searchBookmarks(String keyword, BookmarkListCallback callback) {
        getExecutor().execute(() -> {
            List<BrowserBookmarkEntity> list = bookmarkDao.searchBookmarks(keyword);
            callback.onResult(list);
        });
    }

    public void isBookmarked(String url, BookmarkCountCallback callback) {
        getExecutor().execute(() -> {
            int count = bookmarkDao.countByUrl(url);
            callback.onResult(count > 0);
        });
    }

    public interface BookmarkListCallback {
        void onResult(List<BrowserBookmarkEntity> list);
    }

    public interface BookmarkCountCallback {
        void onResult(boolean isBookmarked);
    }
}

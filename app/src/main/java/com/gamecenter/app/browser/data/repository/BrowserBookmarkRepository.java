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
    private final ExecutorService executor;

    public BrowserBookmarkRepository(Application application) {
        BrowserDatabase db = BrowserDatabase.getInstance(application);
        bookmarkDao = db.bookmarkDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public void insert(BrowserBookmarkEntity bookmark) {
        executor.execute(() -> bookmarkDao.insert(bookmark));
    }

    public void deleteById(long id) {
        executor.execute(() -> bookmarkDao.deleteById(id));
    }

    public void deleteAll() {
        executor.execute(() -> bookmarkDao.deleteAll());
    }

    public void getAllBookmarks(BookmarkListCallback callback) {
        executor.execute(() -> {
            List<BrowserBookmarkEntity> list = bookmarkDao.getAllBookmarks();
            callback.onResult(list);
        });
    }

    public void searchBookmarks(String keyword, BookmarkListCallback callback) {
        executor.execute(() -> {
            List<BrowserBookmarkEntity> list = bookmarkDao.searchBookmarks(keyword);
            callback.onResult(list);
        });
    }

    public void isBookmarked(String url, BookmarkCountCallback callback) {
        executor.execute(() -> {
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

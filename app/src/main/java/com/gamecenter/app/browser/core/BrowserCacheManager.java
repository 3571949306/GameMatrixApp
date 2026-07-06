package com.gamecenter.app.browser.core;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 浏览器缓存管理器。
 * 负责清理 WebView 缓存、WebStorage、Cookie 和应用缓存目录。
 * 不会删除 Room 数据库（历史记录、收藏夹、下载记录）。
 */
public class BrowserCacheManager {

    /**
     * 清理 WebView 缓存（不删除数据库）。
     */
    public static void clearWebViewCache(@NonNull Context context) {
        try {
            WebView webView = new WebView(context);
            webView.clearCache(true);
            webView.destroy();
        } catch (Exception ignored) {}
    }

    /**
     * 清理 WebStorage 数据。
     */
    public static void clearWebStorage() {
        try {
            WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {}
    }

    /**
     * 清理 Cookie。
     */
    public static void clearCookies() {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } catch (Exception ignored) {}
    }

    /**
     * 清理应用缓存目录下的浏览器缓存文件。
     */
    public static void clearAppBrowserCache(@NonNull Context context) {
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDir(cacheDir);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 清理所有浏览数据（缓存 + Cookie + WebStorage），不删除数据库。
     */
    public static void clearAllBrowsingData(@NonNull Context context) {
        clearWebViewCache(context);
        clearWebStorage();
        clearCookies();
        clearAppBrowserCache(context);
    }

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
        }
        return dir != null && dir.delete();
    }
}

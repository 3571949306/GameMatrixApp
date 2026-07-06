package com.gamecenter.app.browser.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.browser.security.AdBlocker;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 浏览器设置中心。
 *
 * <p>负责读取/写入 SharedPreferences 并应用到 WebSettings。
 */
public class BrowserSettingsManager {

    public static final String PREFS_NAME = "browser_settings";

    public static final String KEY_JAVASCRIPT = "javascript_enabled";
    public static final String KEY_COOKIE = "cookie_enabled";
    public static final String KEY_THIRD_PARTY_COOKIE = "third_party_cookie";
    public static final String KEY_AD_BLOCK = "ad_block_enabled";
    public static final String KEY_SAFE_BROWSING = "safe_browsing";
    public static final String KEY_DOM_STORAGE = "dom_storage_enabled";
    public static final String KEY_WEBVIEW_DEBUGGING = "webview_debugging_enabled";
    public static final String KEY_LOAD_IMAGES = "load_images_enabled";
    public static final String KEY_AUTO_PLAY_MEDIA = "auto_play_media_enabled";
    public static final String KEY_HOME_URL = "home_url";
    public static final String KEY_SEARCH_ENGINE = "search_engine";

    private static final boolean DEFAULT_JAVASCRIPT = true;
    private static final boolean DEFAULT_COOKIE = true;
    private static final boolean DEFAULT_THIRD_PARTY_COOKIE = false;
    private static final boolean DEFAULT_AD_BLOCK = true;
    private static final boolean DEFAULT_SAFE_BROWSING = true;
    private static final boolean DEFAULT_DOM_STORAGE = true;
    private static final boolean DEFAULT_WEBVIEW_DEBUGGING = false;
    private static final boolean DEFAULT_LOAD_IMAGES = true;
    private static final boolean DEFAULT_AUTO_PLAY_MEDIA = false;
    public static final String DEFAULT_HOME_URL = "https://www.baidu.com";
    private static final String DEFAULT_SEARCH_ENGINE = "baidu";

    public static final int RELOAD_NONE = 0;
    public static final int RELOAD_REQUIRED = 1;

    public interface OnSettingsChangeListener {
        void onSettingsChanged(int reloadRequired);
    }

    private static volatile BrowserSettingsManager instance;
    private final SharedPreferences prefs;
    private final CopyOnWriteArrayList<OnSettingsChangeListener> listeners =
            new CopyOnWriteArrayList<>();

    private BrowserSettingsManager(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static BrowserSettingsManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (BrowserSettingsManager.class) {
                if (instance == null) instance = new BrowserSettingsManager(context);
            }
        }
        return instance;
    }

    // ===== Getters =====

    public boolean isJavaScriptEnabled() {
        return prefs.getBoolean(KEY_JAVASCRIPT, DEFAULT_JAVASCRIPT);
    }

    public boolean isCookieEnabled() {
        return prefs.getBoolean(KEY_COOKIE, DEFAULT_COOKIE);
    }

    public boolean isThirdPartyCookieEnabled() {
        return prefs.getBoolean(KEY_THIRD_PARTY_COOKIE, DEFAULT_THIRD_PARTY_COOKIE);
    }

    public boolean isAdBlockEnabled() {
        return prefs.getBoolean(KEY_AD_BLOCK, DEFAULT_AD_BLOCK);
    }

    public boolean isSafeBrowsingEnabled() {
        return prefs.getBoolean(KEY_SAFE_BROWSING, DEFAULT_SAFE_BROWSING);
    }

    public boolean isDomStorageEnabled() {
        return prefs.getBoolean(KEY_DOM_STORAGE, DEFAULT_DOM_STORAGE);
    }

    public boolean isWebViewDebuggingEnabled() {
        return prefs.getBoolean(KEY_WEBVIEW_DEBUGGING, DEFAULT_WEBVIEW_DEBUGGING);
    }

    public boolean isLoadImagesEnabled() {
        return prefs.getBoolean(KEY_LOAD_IMAGES, DEFAULT_LOAD_IMAGES);
    }

    public boolean isAutoPlayMediaEnabled() {
        return prefs.getBoolean(KEY_AUTO_PLAY_MEDIA, DEFAULT_AUTO_PLAY_MEDIA);
    }

    @NonNull
    public String getHomeUrl() {
        String url = prefs.getString(KEY_HOME_URL, DEFAULT_HOME_URL);
        return url != null && !url.isEmpty() ? url : DEFAULT_HOME_URL;
    }

    @NonNull
    public String getSearchEngine() {
        String engine = prefs.getString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE);
        return engine != null && !engine.isEmpty() ? engine : DEFAULT_SEARCH_ENGINE;
    }

    // ===== Setters =====

    public void setJavaScriptEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_JAVASCRIPT, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setCookieEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_COOKIE, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setThirdPartyCookieEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_THIRD_PARTY_COOKIE, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setAdBlockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AD_BLOCK, enabled).apply();
        AdBlocker.getInstance().setEnabled(enabled);
        notifyListeners(RELOAD_NONE);
    }

    public void setSafeBrowsingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAFE_BROWSING, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setDomStorageEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOM_STORAGE, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setWebViewDebuggingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WEBVIEW_DEBUGGING, enabled).apply();
        notifyListeners(RELOAD_NONE);
    }

    public void setLoadImagesEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOAD_IMAGES, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setAutoPlayMediaEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_MEDIA, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    public void setHomeUrl(@Nullable String url) {
        prefs.edit().putString(KEY_HOME_URL, url != null ? url : DEFAULT_HOME_URL).apply();
        notifyListeners(RELOAD_NONE);
    }

    public void setSearchEngine(@Nullable String engine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine != null ? engine : DEFAULT_SEARCH_ENGINE).apply();
        notifyListeners(RELOAD_NONE);
    }

    /**
     * 恢复所有浏览器设置为默认值。
     */
    public void resetToDefaults() {
        prefs.edit()
                .putBoolean(KEY_JAVASCRIPT, DEFAULT_JAVASCRIPT)
                .putBoolean(KEY_COOKIE, DEFAULT_COOKIE)
                .putBoolean(KEY_THIRD_PARTY_COOKIE, DEFAULT_THIRD_PARTY_COOKIE)
                .putBoolean(KEY_AD_BLOCK, DEFAULT_AD_BLOCK)
                .putBoolean(KEY_SAFE_BROWSING, DEFAULT_SAFE_BROWSING)
                .putBoolean(KEY_DOM_STORAGE, DEFAULT_DOM_STORAGE)
                .putBoolean(KEY_WEBVIEW_DEBUGGING, DEFAULT_WEBVIEW_DEBUGGING)
                .putBoolean(KEY_LOAD_IMAGES, DEFAULT_LOAD_IMAGES)
                .putBoolean(KEY_AUTO_PLAY_MEDIA, DEFAULT_AUTO_PLAY_MEDIA)
                .putString(KEY_HOME_URL, DEFAULT_HOME_URL)
                .putString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE)
                .apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    // ===== Listener =====

    public void addListener(@NonNull OnSettingsChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(@NonNull OnSettingsChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(int reloadRequired) {
        for (OnSettingsChangeListener l : listeners) {
            try { l.onSettingsChanged(reloadRequired); } catch (Exception ignore) {}
        }
    }

    // ===== Apply to WebView =====

    @SuppressWarnings("deprecation")
    public void applyToWebView(@NonNull WebView webView) {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(isJavaScriptEnabled());
        s.setDomStorageEnabled(isDomStorageEnabled());
        s.setLoadsImagesAutomatically(isLoadImagesEnabled());
        try { s.setMediaPlaybackRequiresUserGesture(!isAutoPlayMediaEnabled()); } catch (Throwable ignored) {}

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(isCookieEnabled());
        try { cm.setAcceptThirdPartyCookies(webView, isThirdPartyCookieEnabled()); } catch (Throwable ignored) {}

        AdBlocker.getInstance().setEnabled(isAdBlockEnabled());

        try { s.setSafeBrowsingEnabled(isSafeBrowsingEnabled()); } catch (Throwable ignored) {}
    }
}

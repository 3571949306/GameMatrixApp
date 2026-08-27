package com.gamecenter.app.browser.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
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
    /** P1-2 追踪保护开关 */
    public static final String KEY_TRACKER_PROTECTION = "tracker_protection_enabled";
    public static final String KEY_DOM_STORAGE = "dom_storage_enabled";
    public static final String KEY_WEBVIEW_DEBUGGING = "webview_debugging_enabled";
    public static final String KEY_LOAD_IMAGES = "load_images_enabled";
    public static final String KEY_AUTO_PLAY_MEDIA = "auto_play_media_enabled";
    public static final String KEY_HOME_URL = "home_url";
    public static final String KEY_SEARCH_ENGINE = "search_engine";
    public static final String KEY_HOME_PAGE_STYLE = "home_page_style";
    public static final String KEY_FORCE_DARK_MODE = "force_dark_mode";
    /** P2-4 数据节省模式：拦截图片/字体资源 */
    public static final String KEY_DATA_SAVER = "data_saver_enabled";
    /** P2-2 音量键滚动 */
    public static final String KEY_VOLUME_SCROLL = "volume_scroll_enabled";
    /** P2-1 智能双指缩放 */
    public static final String KEY_SMART_ZOOM = "smart_zoom_enabled";

    /** P0-3 手势导航增强：双击 WebView 前进、长按 WebView 显示历史 */
    public static final String KEY_GESTURE_DOUBLE_TAP_FORWARD = "gesture_double_tap_forward";
    public static final String KEY_GESTURE_LONG_PRESS_HISTORY = "gesture_long_press_history";

    /** 起始页风格：宫格、卡片流、极简 */
    public static final String HOME_PAGE_STYLE_GRID = "grid";
    public static final String HOME_PAGE_STYLE_CARDS = "cards";
    public static final String HOME_PAGE_STYLE_MINIMAL = "minimal";

    /** 夜间模式：自动（跟随系统）、强制开、强制关 */
    public static final int DARK_MODE_AUTO = 0;
    public static final int DARK_MODE_FORCE_ON = 1;
    public static final int DARK_MODE_FORCE_OFF = 2;

    private static final boolean DEFAULT_JAVASCRIPT = true;
    private static final boolean DEFAULT_COOKIE = true;
    private static final boolean DEFAULT_THIRD_PARTY_COOKIE = false;
    private static final boolean DEFAULT_AD_BLOCK = true;
    private static final boolean DEFAULT_SAFE_BROWSING = true;
    private static final boolean DEFAULT_TRACKER_PROTECTION = true;
    private static final boolean DEFAULT_DOM_STORAGE = true;
    private static final boolean DEFAULT_WEBVIEW_DEBUGGING = false;
    private static final boolean DEFAULT_LOAD_IMAGES = true;
    private static final boolean DEFAULT_AUTO_PLAY_MEDIA = false;
    public static final String DEFAULT_HOME_URL = "https://www.baidu.com";
    private static final String DEFAULT_SEARCH_ENGINE = "baidu";
    private static final String DEFAULT_HOME_PAGE_STYLE = HOME_PAGE_STYLE_GRID;
    private static final int DEFAULT_DARK_MODE = DARK_MODE_AUTO;
    private static final boolean DEFAULT_DATA_SAVER = false;
    private static final boolean DEFAULT_VOLUME_SCROLL = false;
    private static final boolean DEFAULT_SMART_ZOOM = true;
    private static final boolean DEFAULT_GESTURE_DOUBLE_TAP_FORWARD = true;
    private static final boolean DEFAULT_GESTURE_LONG_PRESS_HISTORY = true;

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

    /** P1-2 追踪保护开关 */
    public boolean isTrackerProtectionEnabled() {
        return prefs.getBoolean(KEY_TRACKER_PROTECTION, DEFAULT_TRACKER_PROTECTION);
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

    @NonNull
    public String getHomePageStyle() {
        String style = prefs.getString(KEY_HOME_PAGE_STYLE, DEFAULT_HOME_PAGE_STYLE);
        return style != null && !style.isEmpty() ? style : DEFAULT_HOME_PAGE_STYLE;
    }

    public int getForceDarkMode() {
        return prefs.getInt(KEY_FORCE_DARK_MODE, DEFAULT_DARK_MODE);
    }

    /** P2-4 数据节省模式开关 */
    public boolean isDataSaverEnabled() {
        return prefs.getBoolean(KEY_DATA_SAVER, DEFAULT_DATA_SAVER);
    }

    /** P2-2 音量键滚动开关 */
    public boolean isVolumeScrollEnabled() {
        return prefs.getBoolean(KEY_VOLUME_SCROLL, DEFAULT_VOLUME_SCROLL);
    }

    /** P2-1 智能双指缩放开关 */
    public boolean isSmartZoomEnabled() {
        return prefs.getBoolean(KEY_SMART_ZOOM, DEFAULT_SMART_ZOOM);
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

    /** P1-2 追踪保护开关 */
    public void setTrackerProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRACKER_PROTECTION, enabled).apply();
        com.gamecenter.app.browser.security.BrowserTrackerBlocker.getInstance().setEnabled(enabled);
        notifyListeners(RELOAD_NONE);
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

    public void setHomePageStyle(@Nullable String style) {
        prefs.edit().putString(KEY_HOME_PAGE_STYLE, style != null ? style : DEFAULT_HOME_PAGE_STYLE).apply();
        notifyListeners(RELOAD_NONE);
    }

    public void setForceDarkMode(int mode) {
        prefs.edit().putInt(KEY_FORCE_DARK_MODE, mode).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    /** P2-4 数据节省模式开关 */
    public void setDataSaverEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DATA_SAVER, enabled).apply();
        notifyListeners(RELOAD_REQUIRED);
    }

    /** P2-2 音量键滚动开关 */
    public void setVolumeScrollEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOLUME_SCROLL, enabled).apply();
        notifyListeners(RELOAD_NONE);
    }

    /** P2-1 智能双指缩放开关 */
    public void setSmartZoomEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SMART_ZOOM, enabled).apply();
        notifyListeners(RELOAD_NONE);
    }

    // ===== P0-3 手势导航增强 =====

    public boolean isDoubleTapForwardEnabled() {
        return prefs.getBoolean(KEY_GESTURE_DOUBLE_TAP_FORWARD, DEFAULT_GESTURE_DOUBLE_TAP_FORWARD);
    }

    public void setDoubleTapForwardEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GESTURE_DOUBLE_TAP_FORWARD, enabled).apply();
        // 手势设置变更不需要 reload WebView
    }

    public boolean isLongPressHistoryEnabled() {
        return prefs.getBoolean(KEY_GESTURE_LONG_PRESS_HISTORY, DEFAULT_GESTURE_LONG_PRESS_HISTORY);
    }

    public void setLongPressHistoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GESTURE_LONG_PRESS_HISTORY, enabled).apply();
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
                .putBoolean(KEY_TRACKER_PROTECTION, DEFAULT_TRACKER_PROTECTION)
                .putBoolean(KEY_DOM_STORAGE, DEFAULT_DOM_STORAGE)
                .putBoolean(KEY_WEBVIEW_DEBUGGING, DEFAULT_WEBVIEW_DEBUGGING)
                .putBoolean(KEY_LOAD_IMAGES, DEFAULT_LOAD_IMAGES)
                .putBoolean(KEY_AUTO_PLAY_MEDIA, DEFAULT_AUTO_PLAY_MEDIA)
                .putString(KEY_HOME_URL, DEFAULT_HOME_URL)
                .putString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE)
                .putString(KEY_HOME_PAGE_STYLE, DEFAULT_HOME_PAGE_STYLE)
                .putInt(KEY_FORCE_DARK_MODE, DEFAULT_DARK_MODE)
                .putBoolean(KEY_DATA_SAVER, DEFAULT_DATA_SAVER)
                .putBoolean(KEY_VOLUME_SCROLL, DEFAULT_VOLUME_SCROLL)
                .putBoolean(KEY_SMART_ZOOM, DEFAULT_SMART_ZOOM)
                .putBoolean(KEY_GESTURE_DOUBLE_TAP_FORWARD, DEFAULT_GESTURE_DOUBLE_TAP_FORWARD)
                .putBoolean(KEY_GESTURE_LONG_PRESS_HISTORY, DEFAULT_GESTURE_LONG_PRESS_HISTORY)
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

    /**
     * Phase 3: 统一通用 WebSettings 配置，消除 BrowserController 和 BrowserWebViewPool 中的重复代码。
     * 这些是静态配置（不依赖用户设置），因此使用静态方法。
     */
    @SuppressWarnings("SetJavaScriptEnabled")
    public static void applyCommonSettings(@NonNull WebView webView) {
        WebSettings s = webView.getSettings();
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccess(false);
        // S5: 禁止混合内容
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    }

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
        // P1-2 追踪保护：将开关同步到拦截器
        com.gamecenter.app.browser.security.BrowserTrackerBlocker.getInstance().setEnabled(isTrackerProtectionEnabled());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                s.setSafeBrowsingEnabled(isSafeBrowsingEnabled());
            }
        } catch (Throwable ignored) {}

        // P1-1 夜间模式三档策略：自动（跟随系统）/ 强制开 / 强制关
        applyDarkMode(webView);
    }

    /**
     * P1-1：应用夜间模式到 WebView。
     *
     * <p>三档策略：
     * <ul>
     *   <li>{@link #DARK_MODE_AUTO}：跟随系统夜间模式（UiMode = NIGHT_YES 时强制 WebView 暗化）</li>
     *   <li>{@link #DARK_MODE_FORCE_ON}：强制 WebView 暗化</li>
     *   <li>{@link #DARK_MODE_FORCE_OFF}：强制 WebView 不暗化</li>
     * </ul>
     *
     * <p>API 33+ 推荐使用 {@link WebSettings#setAlgorithmicDarkeningAllowed(boolean)}，
     * 但为兼容低版本及保证行为一致，这里使用 {@link WebSettings#setForceDark(int)}（API 29+，
     * 已在 API 33 标记 deprecated 但仍可用）+ 手动判断系统夜间模式。
     *
     * @param webView 目标 WebView
     */
    @SuppressWarnings("deprecation")
    private void applyDarkMode(@NonNull WebView webView) {
        int mode = getForceDarkMode();
        WebSettings s = webView.getSettings();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int forceDarkMode;
                boolean isSystemDark;
                if (mode == DARK_MODE_AUTO) {
                    int nightMode = webView.getContext().getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK;
                    isSystemDark = (nightMode == Configuration.UI_MODE_NIGHT_YES);
                    forceDarkMode = isSystemDark
                            ? WebSettings.FORCE_DARK_ON
                            : WebSettings.FORCE_DARK_OFF;
                } else if (mode == DARK_MODE_FORCE_ON) {
                    forceDarkMode = WebSettings.FORCE_DARK_ON;
                } else {
                    forceDarkMode = WebSettings.FORCE_DARK_OFF;
                }
                s.setForceDark(forceDarkMode);
                Log.d("BrowserSettings", "applyDarkMode: mode=" + mode
                        + " forceDark=" + forceDarkMode);
            }
        } catch (Throwable t) {
            Log.w("BrowserSettings", "applyDarkMode failed", t);
        }
    }
}

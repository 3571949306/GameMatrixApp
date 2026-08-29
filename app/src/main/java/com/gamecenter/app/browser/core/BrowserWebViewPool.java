package com.gamecenter.app.browser.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.DownloadListener;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.browser.core.incognito.IncognitoProfileManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 浏览器 WebView 池（P0-1 真·多 Tab 架构）。
 *
 * <p>每个 Tab 对应一个 WebView 实例，按需创建。切换 Tab 时仅切换 visibility，
 * 保留所有 Tab 的页面状态（滚动位置、JS 上下文、登录态等）。
 *
 * <p>内存压力策略：当池大小超过 {@link #MAX_ACTIVE_WEBVIEWS} 时，
 * 自动释放最久未活跃的 WebView（先 {@link WebView#saveState} 后 {@link WebView#destroy}），
 * 释放后再次切换到该 Tab 会通过 {@link WebView#restoreState} 恢复。
 *
 * <p>所有 WebView 都挂载到同一个 {@link FrameLayout} 容器（R.id.webview_container），
 * 通过 visibility 控制显示。
 */
public class BrowserWebViewPool {

    private static final String TAG = "BrowserWebViewPool";

    /** 同时活跃的 WebView 最大数量。超过时自动释放最久未用的。 */
    public static final int MAX_ACTIVE_WEBVIEWS = 5;

    private final Context context;
    private final FrameLayout container;
    private final BrowserWebViewClient.PageLoadCallback pageLoadCallback;
    private final BrowserChromeClient.PageInfoCallback pageInfoCallback;
    @Nullable private final BrowserWebViewClient.ExternalUrlHandler externalUrlHandler;

    /** A4: 池操作的全局锁，确保复合操作的原子性。 */
    private final ReentrantLock poolLock = new ReentrantLock();

    /** tabId → WebView 实例（活跃池，可见或可快速恢复） — A4: ConcurrentHashMap 替代 HashMap */
    private final Map<String, WebView> activePool = new ConcurrentHashMap<>();
    /** tabId → 已释放 WebView 前保存的状态（restoreState 用） — A4: ConcurrentHashMap 替代 HashMap */
    private final Map<String, Bundle> releasedStates = new ConcurrentHashMap<>();
    /** tabId → 创建时间戳，用于 LRU 释放策略 — A4: 包装为线程安全 map */
    private final Map<String, Long> lastAccessMap = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile String activeTabId;

    /** A2: 为每个新建 WebView 的 ChromeClient 应用宿主回调（文件上传/全屏/权限）。 */
    @Nullable private java.util.function.Consumer<BrowserChromeClient> chromeClientConfigurator;
    /** Applied before the first fallback navigation of every lazily-created tab. */
    @Nullable private DownloadListener downloadListener;

    public BrowserWebViewPool(@NonNull Context context,
                              @NonNull FrameLayout container,
                              @NonNull BrowserWebViewClient.PageLoadCallback pageLoadCallback,
                              @NonNull BrowserChromeClient.PageInfoCallback pageInfoCallback,
                              @Nullable BrowserWebViewClient.ExternalUrlHandler externalUrlHandler) {
        this.context = context.getApplicationContext();
        this.container = container;
        this.pageLoadCallback = pageLoadCallback;
        this.pageInfoCallback = pageInfoCallback;
        this.externalUrlHandler = externalUrlHandler;
    }

    /** A2: 注册 ChromeClient 配置器，应用于此后池内每个新建 WebView 的 ChromeClient。 */
    public void setChromeClientConfigurator(@Nullable java.util.function.Consumer<BrowserChromeClient> configurator) {
        this.chromeClientConfigurator = configurator;
    }

    /**
     * Register downloads once for the pool, including WebViews that are created in
     * the future. Installing this from BrowserController after acquireWebView is
     * too late for a page that starts a download during its first navigation.
     */
    public void setDownloadListener(@Nullable DownloadListener listener) {
        this.downloadListener = listener;
        for (WebView webView : activePool.values()) {
            try {
                webView.setDownloadListener(listener);
            } catch (Throwable ignored) {
                // A failed listener assignment must not make a tab unusable.
            }
        }
    }

    /** 获取或创建指定 Tab 的 WebView，并将其设为可见。其他 WebView 设为 GONE。 */
    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    public WebView acquireWebView(@NonNull String tabId, @Nullable String fallbackUrl) {
        return acquireWebView(tabId, fallbackUrl, null);
    }

    /**
     * Acquire a tab WebView and apply its profile before a fallback navigation can
     * begin. This ordering is important for incognito tabs: configuring after
     * {@link WebView#loadUrl(String)} leaves the first page outside the intended
     * no-form/no-cache policy.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    public WebView acquireWebView(@NonNull String tabId, @Nullable String fallbackUrl,
                                  @Nullable BrowserTabManager.Tab tab) {
        String safeFallbackUrl = BrowserController.isHttpUrl(fallbackUrl) ? fallbackUrl : null;
        poolLock.lock();
        try {
            WebView webView = activePool.get(tabId);
            if (webView == null) {
                // 已释放过 → 从 savedState 恢复
                Bundle savedState = releasedStates.remove(tabId);
                webView = createWebView(context);
                configureWebView(webView, tabId);
                IncognitoProfileManager.applyProfile(webView, tab);
                if (savedState != null) {
                    try {
                        webView.restoreState(savedState);
                        Log.d(TAG, "acquireWebView: restored tab=" + tabId);
                    } catch (Throwable t) {
                        Log.w(TAG, "restoreState failed for tab=" + tabId, t);
                        if (safeFallbackUrl != null) webView.loadUrl(safeFallbackUrl);
                    }
                } else if (safeFallbackUrl != null) {
                    webView.loadUrl(safeFallbackUrl);
                }
                // Every WebView belongs below the static overlay layers. This remains
                // true when a new Tab is created after the player overlay already exists.
                container.addView(webView, 0);
                activePool.put(tabId, webView);
            }
            // 切换可见性
            for (Map.Entry<String, WebView> entry : activePool.entrySet()) {
                entry.getValue().setVisibility(entry.getKey().equals(tabId) ? View.VISIBLE : View.GONE);
            }
            activeTabId = tabId;
            touchAccess(tabId);
        } finally {
            poolLock.unlock();
        }
        trimIfNeeded();
        return activePool.get(tabId);
    }

    /** 仅获取当前 active WebView（不创建）。 */
    @Nullable
    public WebView getActiveWebView() {
        return activeTabId != null ? activePool.get(activeTabId) : null;
    }

    /** 保存指定 Tab 的 WebView 状态（不释放）。用于切换前的快照。 */
    public void saveTabState(@NonNull String tabId) {
        poolLock.lock();
        try {
            WebView webView = activePool.get(tabId);
            if (webView == null) return;
            try {
                Bundle state = new Bundle();
                webView.saveState(state);
                releasedStates.put(tabId, state);
                Log.d(TAG, "saveTabState: tab=" + tabId);
            } catch (Throwable t) {
                Log.w(TAG, "saveTabState failed for tab=" + tabId, t);
            }
        } finally {
            poolLock.unlock();
        }
    }

    /**
     * A1: 保存所有活跃 Tab 的 WebView 状态快照到 releasedStates，
     * 供配置变更（旋转）后重建 pool 时通过 {@link #restoreStateMap} 恢复。
     */
    public void saveAllStates() {
        poolLock.lock();
        try {
            for (Map.Entry<String, WebView> entry : activePool.entrySet()) {
                try {
                    Bundle state = new Bundle();
                    entry.getValue().saveState(state);
                    releasedStates.put(entry.getKey(), state);
                    Log.d(TAG, "saveAllStates: tab=" + entry.getKey());
                } catch (Throwable t) {
                    Log.w(TAG, "saveAllStates failed for tab=" + entry.getKey(), t);
                }
            }
        } finally {
            poolLock.unlock();
        }
    }

    /** A1: 收集当前所有已保存状态到外层 Bundle，用于跨 Fragment 重建传递。 */
    @NonNull
    public Bundle collectStateMap() {
        Bundle map = new Bundle();
        for (Map.Entry<String, Bundle> entry : releasedStates.entrySet()) {
            map.putBundle(entry.getKey(), entry.getValue());
        }
        return map;
    }

    /** A1: 从外层 Bundle 恢复已保存状态（须在首次 switchToTab 之前调用）。 */
    public void restoreStateMap(@Nullable Bundle map) {
        if (map == null) return;
        try {
            for (String key : map.keySet()) {
                Bundle state = map.getBundle(key);
                if (state != null) releasedStates.put(key, state);
            }
        } catch (Throwable t) {
            Log.w(TAG, "restoreStateMap failed", t);
        }
    }

    /** 销毁指定 Tab 的 WebView 并清理所有引用（关闭 Tab 时调用）。 */
    public void releaseTab(@NonNull String tabId) {
        poolLock.lock();
        try {
            WebView webView = activePool.remove(tabId);
            if (webView != null) {
                try {
                    container.removeView(webView);
                    webView.stopLoading();
                    webView.removeAllViews();
                    webView.destroy();
                } catch (Throwable t) {
                    Log.w(TAG, "releaseTab destroy failed for tab=" + tabId, t);
                }
            }
            releasedStates.remove(tabId);
            lastAccessMap.remove(tabId);
            if (tabId.equals(activeTabId)) activeTabId = null;
        } finally {
            poolLock.unlock();
        }
    }

    /** 销毁所有 WebView（onDestroyView 调用）。 */
    public void releaseAll() {
        poolLock.lock();
        try {
            for (Map.Entry<String, WebView> entry : activePool.entrySet()) {
                WebView webView = entry.getValue();
                try {
                    container.removeView(webView);
                    webView.stopLoading();
                    webView.removeAllViews();
                    webView.destroy();
                } catch (Throwable t) {
                    Log.w(TAG, "releaseAll destroy failed for tab=" + entry.getKey(), t);
                }
            }
            activePool.clear();
            releasedStates.clear();
            lastAccessMap.clear();
            activeTabId = null;
        } finally {
            poolLock.unlock();
        }
    }

    /** 对所有 WebView 调用 onPause。 */
    public void onPause() {
        for (WebView webView : activePool.values()) {
            try { webView.onPause(); } catch (Throwable ignored) {}
        }
    }

    /** 对 active WebView 调用 onResume。 */
    public void onResume(@NonNull Context ctx) {
        WebView active = getActiveWebView();
        if (active != null) {
            try {
                active.onResume();
                BrowserSettingsManager.getInstance(ctx).applyToWebView(active);
            } catch (Throwable ignored) {}
        }
    }

    /** 对所有 WebView 应用设置（设置变更时调用）。 */
    public void applySettingsToAll(@NonNull Context ctx) {
        BrowserSettingsManager mgr = BrowserSettingsManager.getInstance(ctx);
        for (WebView webView : activePool.values()) {
            try { mgr.applyToWebView(webView); } catch (Throwable ignored) {}
        }
    }

    /** 销毁并移除非 active 的 WebView（用于 onTrimMemory 或主动降内存）。 */
    public void trimToActiveOnly() {
        poolLock.lock();
        try {
            String keep = activeTabId;
            if (keep == null) return;
            // 先 saveState 再 destroy
            for (Map.Entry<String, WebView> entry : new HashMap<>(activePool).entrySet()) {
                String tabId = entry.getKey();
                if (tabId.equals(keep)) continue;
                WebView webView = entry.getValue();
                try {
                    Bundle state = new Bundle();
                    webView.saveState(state);
                    releasedStates.put(tabId, state);
                    container.removeView(webView);
                    webView.stopLoading();
                    webView.removeAllViews();
                    webView.destroy();
                } catch (Throwable t) {
                    Log.w(TAG, "trimToActiveOnly failed for tab=" + tabId, t);
                }
                activePool.remove(tabId);
                lastAccessMap.remove(tabId);
            }
            Log.d(TAG, "trimToActiveOnly: kept=" + keep + ", released=" + releasedStates.size());
        } finally {
            poolLock.unlock();
        }
    }

    public int getActiveCount() { return activePool.size(); }
    public int getReleasedCount() { return releasedStates.size(); }

    @NonNull
    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(@NonNull Context ctx) {
        // 使用容器对应的 Context（带 AppCompat 主题），避免 LayoutInflater InflateException
        return new WebView(container.getContext());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(@NonNull WebView webView, @Nullable String tabId) {
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        BrowserSettingsManager settingsMgr = BrowserSettingsManager.getInstance(context);
        settingsMgr.applyToWebView(webView);

        // Phase 3: 使用统一的通用 WebSettings 配置（消除重复代码）
        BrowserSettingsManager.applyCommonSettings(webView);

        String defaultUA = webView.getSettings().getUserAgentString();
        webView.getSettings().setUserAgentString(defaultUA + " GameMatrixBrowser/1.0");

        BrowserWebViewClient client = new BrowserWebViewClient(context, pageLoadCallback, tabId);
        client.setExternalUrlHandler(externalUrlHandler);
        BrowserChromeClient chrome = new BrowserChromeClient(pageInfoCallback, tabId);
        // A2: 新建 WebView 的 ChromeClient 应用宿主回调（文件上传/全屏/权限）
        if (chromeClientConfigurator != null) {
            try {
                chromeClientConfigurator.accept(chrome);
            } catch (Throwable ignored) {}
        }
        webView.setWebViewClient(client);
        webView.setWebChromeClient(chrome);
        if (downloadListener != null) {
            try {
                webView.setDownloadListener(downloadListener);
            } catch (Throwable ignored) {
                // Keep page navigation available even if the platform rejects it.
            }
        }
    }

    private void touchAccess(@NonNull String tabId) {
        lastAccessMap.remove(tabId);
        lastAccessMap.put(tabId, System.currentTimeMillis());
    }

    /** 池大小超过 MAX 时，按 LRU 释放最久未活跃的 WebView。 */
    private void trimIfNeeded() {
        poolLock.lock();
        try {
            while (activePool.size() > MAX_ACTIVE_WEBVIEWS) {
                String oldest = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, Long> entry : lastAccessMap.entrySet()) {
                    if (entry.getKey().equals(activeTabId)) continue;
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldest = entry.getKey();
                    }
                }
                if (oldest == null) break;
                WebView webView = activePool.remove(oldest);
                if (webView != null) {
                    try {
                        Bundle state = new Bundle();
                        webView.saveState(state);
                        releasedStates.put(oldest, state);
                        container.removeView(webView);
                        webView.stopLoading();
                        webView.removeAllViews();
                        webView.destroy();
                        Log.d(TAG, "trimIfNeeded: LRU released tab=" + oldest);
                    } catch (Throwable t) {
                        Log.w(TAG, "trimIfNeeded release failed for tab=" + oldest, t);
                    }
                }
                lastAccessMap.remove(oldest);
            }
        } finally {
            poolLock.unlock();
        }
    }
}

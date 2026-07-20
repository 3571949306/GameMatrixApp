package com.gamecenter.app.browser.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.BuildConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** tabId → WebView 实例（活跃池，可见或可快速恢复） */
    private final Map<String, WebView> activePool = new HashMap<>();
    /** tabId → 已释放 WebView 前保存的状态（restoreState 用） */
    private final Map<String, Bundle> releasedStates = new HashMap<>();
    /** tabId → 创建时间戳，用于 LRU 释放策略 */
    private final Map<String, Long> lastAccessMap = new LinkedHashMap<>();
    private String activeTabId;

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

    /** 获取或创建指定 Tab 的 WebView，并将其设为可见。其他 WebView 设为 GONE。 */
    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    public WebView acquireWebView(@NonNull String tabId, @Nullable String fallbackUrl) {
        WebView webView = activePool.get(tabId);
        if (webView == null) {
            // 已释放过 → 从 savedState 恢复
            Bundle savedState = releasedStates.remove(tabId);
            webView = createWebView(context);
            configureWebView(webView);
            if (savedState != null) {
                try {
                    webView.restoreState(savedState);
                    Log.d(TAG, "acquireWebView: restored tab=" + tabId);
                } catch (Throwable t) {
                    Log.w(TAG, "restoreState failed for tab=" + tabId, t);
                    if (fallbackUrl != null) webView.loadUrl(fallbackUrl);
                }
            } else if (fallbackUrl != null) {
                webView.loadUrl(fallbackUrl);
            }
            container.addView(webView);
            activePool.put(tabId, webView);
        }
        // 切换可见性
        for (Map.Entry<String, WebView> entry : activePool.entrySet()) {
            entry.getValue().setVisibility(entry.getKey().equals(tabId) ? View.VISIBLE : View.GONE);
        }
        activeTabId = tabId;
        touchAccess(tabId);
        trimIfNeeded();
        return webView;
    }

    /** 仅获取当前 active WebView（不创建）。 */
    @Nullable
    public WebView getActiveWebView() {
        return activeTabId != null ? activePool.get(activeTabId) : null;
    }

    /** 保存指定 Tab 的 WebView 状态（不释放）。用于切换前的快照。 */
    public void saveTabState(@NonNull String tabId) {
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
    }

    /** 销毁指定 Tab 的 WebView 并清理所有引用（关闭 Tab 时调用）。 */
    public void releaseTab(@NonNull String tabId) {
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
    }

    /** 销毁所有 WebView（onDestroyView 调用）。 */
    public void releaseAll() {
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
    private void configureWebView(@NonNull WebView webView) {
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        BrowserSettingsManager settingsMgr = BrowserSettingsManager.getInstance(context);
        settingsMgr.applyToWebView(webView);

        if (BuildConfig.BROWSER_WEBVIEW_DEBUG && settingsMgr.isWebViewDebuggingEnabled()) {
            try {
                WebView.setWebContentsDebuggingEnabled(true);
            } catch (Throwable ignored) {}
        }

        WebSettings settings = webView.getSettings();
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);

        String defaultUA = settings.getUserAgentString();
        settings.setUserAgentString(defaultUA + " GameMatrixBrowser/1.0");

        BrowserWebViewClient client = new BrowserWebViewClient(pageLoadCallback);
        client.setExternalUrlHandler(externalUrlHandler);
        BrowserChromeClient chrome = new BrowserChromeClient(pageInfoCallback);
        webView.setWebViewClient(client);
        webView.setWebChromeClient(chrome);
    }

    private void touchAccess(@NonNull String tabId) {
        lastAccessMap.remove(tabId);
        lastAccessMap.put(tabId, System.currentTimeMillis());
    }

    /** 池大小超过 MAX 时，按 LRU 释放最久未活跃的 WebView。 */
    private void trimIfNeeded() {
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
    }
}

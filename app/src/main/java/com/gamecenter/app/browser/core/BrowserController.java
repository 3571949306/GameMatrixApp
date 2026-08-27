package com.gamecenter.app.browser.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.browser.bridge.BrowserJsBridge;
import com.gamecenter.app.browser.security.JsBridgePolicy;
import com.gamecenter.app.browser.util.UrlUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Browser core controller, manages WebView operations.
 *
 * <p>P0-1 多 Tab 架构：当 {@link BuildConfig#BROWSER_REAL_MULTI_TAB} 为 true 时，
 * 通过 {@link BrowserWebViewPool} 管理多个 WebView 实例；为 false 时保留原单 WebView 行为。
 */
public class BrowserController {

    private static final String DEFAULT_HOME_URL = "https://www.baidu.com";
    private static final String DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private String mobileUserAgent;

    // ===== 单 WebView 模式字段（BROWSER_REAL_MULTI_TAB=false） =====
    @Nullable private WebView webView;
    @Nullable private FrameLayout webViewContainer;
    @Nullable private BrowserWebViewClient webViewClient;
    @Nullable private BrowserChromeClient chromeClient;
    // A5: 按 tabId 管理 JS Bridge 注入状态，支持多 Tab 独立追踪
    private final Map<String, Boolean> jsBridgeInjectedTabs = new HashMap<>();

    // ===== 多 Tab 模式字段（BROWSER_REAL_MULTI_TAB=true） =====
    @Nullable private BrowserWebViewPool pool;
    @Nullable private FrameLayout poolContainer;
    @Nullable private BrowserWebViewClient.PageLoadCallback poolPageCallback;
    @Nullable private BrowserChromeClient.PageInfoCallback poolPageInfoCallback;
    @Nullable private BrowserWebViewClient.ExternalUrlHandler poolExternalHandler;
    @Nullable private String activeTabId;

    @SuppressLint("SetJavaScriptEnabled")
    public void initWebView(@NonNull Context context,
                            @NonNull FrameLayout container,
                            @NonNull BrowserWebViewClient.PageLoadCallback pageLoadCallback,
                            @NonNull BrowserChromeClient.PageInfoCallback pageInfoCallback) {
        initWebView(context, container, pageLoadCallback, pageInfoCallback, null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initWebView(@NonNull Context context,
                            @NonNull FrameLayout container,
                            @NonNull BrowserWebViewClient.PageLoadCallback pageLoadCallback,
                            @NonNull BrowserChromeClient.PageInfoCallback pageInfoCallback,
                            @Nullable BrowserWebViewClient.ExternalUrlHandler externalUrlHandler) {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            // 多 Tab 模式：仅保存引用，具体 WebView 由 pool 按需创建
            this.poolContainer = container;
            this.poolPageCallback = pageLoadCallback;
            this.poolPageInfoCallback = pageInfoCallback;
            this.poolExternalHandler = externalUrlHandler;
            this.pool = new BrowserWebViewPool(context, container, pageLoadCallback,
                    pageInfoCallback, externalUrlHandler);
            return;
        }
        // 单 WebView 模式：保留原行为
        destroy();
        webView = new WebView(context);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        this.webViewContainer = container;
        container.addView(webView);

        BrowserSettingsManager settingsMgr = BrowserSettingsManager.getInstance(context);
        settingsMgr.applyToWebView(webView);

        if (BuildConfig.BROWSER_WEBVIEW_DEBUG && settingsMgr.isWebViewDebuggingEnabled()) {
            try {
                WebView.setWebContentsDebuggingEnabled(true);
            } catch (Throwable ignored) {}
        }

        // Phase 3: 使用统一的通用 WebSettings 配置（消除重复代码）
        BrowserSettingsManager.applyCommonSettings(webView);

        String defaultUA = webView.getSettings().getUserAgentString();
        mobileUserAgent = defaultUA;
        webView.getSettings().setUserAgentString(defaultUA + " GameMatrixBrowser/1.0");

        webViewClient = new BrowserWebViewClient(pageLoadCallback);
        webViewClient.setExternalUrlHandler(externalUrlHandler);
        chromeClient = new BrowserChromeClient(pageInfoCallback);
        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(chromeClient);
    }

    // ===== 多 Tab API（仅 BROWSER_REAL_MULTI_TAB=true 时可用） =====

    /**
     * 切换到指定 Tab。若该 Tab 的 WebView 不存在则按需创建（含 restoreState 恢复）。
     * 其他 Tab 的 WebView 设为 GONE。
     *
     * @param tabId 目标 Tab id
     * @param fallbackUrl 若 WebView 需重建且无 savedState，加载此 URL
     * @return 切换后的 active WebView，失败返回 null
     */
    @Nullable
    public WebView switchToTab(@NonNull String tabId, @Nullable String fallbackUrl) {
        if (!BuildConfig.BROWSER_REAL_MULTI_TAB || pool == null) return null;
        WebView wv = pool.acquireWebView(tabId, fallbackUrl);
        if (wv != null) {
            activeTabId = tabId;
            // 重新绑定下载监听（新创建的 WebView 需要）
            if (downloadListener != null) {
                try { wv.setDownloadListener(downloadListener); } catch (Throwable ignored) {}
            }
        }
        return wv;
    }

    /** 关闭指定 Tab 的 WebView 并清理（关闭 Tab 时调用）。 */
    public void closeTabWebView(@NonNull String tabId) {
        if (pool == null) return;
        pool.releaseTab(tabId);
        if (tabId.equals(activeTabId)) activeTabId = null;
    }

    /** 主动释放非 active 的 WebView（用于 onTrimMemory）。 */
    public void trimToActiveOnly() {
        if (pool != null) pool.trimToActiveOnly();
    }

    public int getPoolActiveCount() {
        return pool != null ? pool.getActiveCount() : 0;
    }

    public int getPoolReleasedCount() {
        return pool != null ? pool.getReleasedCount() : 0;
    }

    /** A2: 注册 ChromeClient 配置器（多 Tab 模式转发给 pool，应用于每个新建 WebView 的 ChromeClient）。 */
    public void setChromeClientConfigurator(@Nullable java.util.function.Consumer<BrowserChromeClient> configurator) {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB && pool != null) {
            pool.setChromeClientConfigurator(configurator);
        }
    }

    /** A1: 保存所有活跃 Tab 的 WebView 状态（配置变更前调用）。 */
    public void saveAllTabStates() {
        if (pool != null) pool.saveAllStates();
    }

    /** #8: 单 WebView 模式下保存页面状态（多 Tab 模式走 saveAllTabStates）。 */
    @Nullable
    public Bundle saveSingleWebViewState() {
        if (webView == null) return null;
        try {
            Bundle state = new Bundle();
            webView.saveState(state);
            return state;
        } catch (Throwable t) {
            return null;
        }
    }

    /** #8: 单 WebView 模式下恢复页面状态。返回是否恢复成功（成功后调用方不再加载初始页）。 */
    public boolean restoreSingleWebViewState(@Nullable Bundle state) {
        if (webView == null || state == null) return false;
        try {
            return webView.restoreState(state) != null;
        } catch (Throwable t) {
            return false;
        }
    }
    // 注意：saveAllTabStates / collectTabStateMap / restoreTabStateMap 仅在多 Tab 模式生效

    /** A1: 收集所有 Tab 的已保存状态到 Bundle。 */
    @NonNull
    public Bundle collectTabStateMap() {
        return pool != null ? pool.collectStateMap() : new Bundle();
    }

    /** A1: 恢复 Tab 状态映射（重建 pool 后、首次切换 Tab 前调用）。 */
    public void restoreTabStateMap(@Nullable Bundle map) {
        if (pool != null) pool.restoreStateMap(map);
    }

    @Nullable
    public String getActiveTabId() { return activeTabId; }

    // ===== 通用 API（两种模式共用） =====

    public void loadUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return;
        WebView wv = getActiveWebView();
        if (wv != null) wv.loadUrl(url);
    }

    public void loadInput(@NonNull String input) {
        String url = UrlUtils.processInput(input);
        loadUrl(url);
    }

    public boolean goBack() {
        WebView wv = getActiveWebView();
        if (wv != null && wv.canGoBack()) { wv.goBack(); return true; }
        return false;
    }

    public boolean goForward() {
        WebView wv = getActiveWebView();
        if (wv != null && wv.canGoForward()) { wv.goForward(); return true; }
        return false;
    }

    public void reload() {
        WebView wv = getActiveWebView();
        if (wv != null) wv.reload();
    }

    public void stopLoading() {
        WebView wv = getActiveWebView();
        if (wv != null) wv.stopLoading();
    }

    @NonNull
    public String getCurrentUrl() {
        WebView wv = getActiveWebView();
        if (wv != null && wv.getUrl() != null) return wv.getUrl();
        return "";
    }

    @NonNull
    public String getTitle() {
        WebView wv = getActiveWebView();
        if (wv != null && wv.getTitle() != null) return wv.getTitle();
        return "";
    }

    public boolean canGoBack() {
        WebView wv = getActiveWebView();
        return wv != null && wv.canGoBack();
    }

    public boolean canGoForward() {
        WebView wv = getActiveWebView();
        return wv != null && wv.canGoForward();
    }

    /** Set custom User-Agent string for desktop/mobile mode toggle. */
    public void setUserAgent(@Nullable String userAgent) {
        WebView wv = getActiveWebView();
        if (wv != null && userAgent != null) wv.getSettings().setUserAgentString(userAgent);
    }

    @Nullable
    private android.webkit.DownloadListener downloadListener;
    public void setDownloadListener(@Nullable android.webkit.DownloadListener listener) {
        this.downloadListener = listener;
        WebView wv = getActiveWebView();
        if (wv != null) wv.setDownloadListener(listener);
    }

    public void setDesktopMode(boolean enabled) {
        WebView wv = getActiveWebView();
        if (wv == null) return;
        String baseUA = enabled ? DESKTOP_USER_AGENT : mobileUserAgent;
        if (baseUA == null) baseUA = wv.getSettings().getUserAgentString();
        String suffix = baseUA.contains("GameMatrixBrowser/1.0") ? "" : " GameMatrixBrowser/1.0";
        wv.getSettings().setUserAgentString(baseUA + suffix);
        wv.reload();
    }

    @Nullable
    public WebView getWebView() {
        return getActiveWebView();
    }

    /** 获取当前活跃 WebView（两种模式统一入口）。 */
    @Nullable
    private WebView getActiveWebView() {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            return pool != null ? pool.getActiveWebView() : null;
        }
        return webView;
    }

    @Nullable
    public BrowserChromeClient getChromeClient() {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            // 多 Tab 模式下 chromeClient 由 pool 内部持有，不对外暴露
            return null;
        }
        return chromeClient;
    }

    @NonNull
    public String getDefaultHomeUrl() { return DEFAULT_HOME_URL; }

    /** Clear WebView history, cache and form data (for incognito cleanup). S6: 增强清理。 */
    public void clearWebViewData() {
        WebView wv = getActiveWebView();
        if (wv != null) {
            wv.clearHistory();
            wv.clearCache(true);
            wv.clearFormData();
            // S6: 增加 Cookie 和 WebStorage 清理
            try {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {}
            try {
                android.webkit.WebStorage.getInstance().deleteAllData();
            } catch (Exception ignored) {}
        }
    }

    /**
     * S6: 无痕模式切换时的完整数据清理。
     * 清除当前 WebView 的所有浏览数据，包括 Cookie、存储、历史等。
     */
    public void clearAllBrowsingDataForIncognito() {
        WebView wv = getActiveWebView();
        if (wv != null) {
            wv.clearHistory();
            wv.clearCache(true);
            wv.clearFormData();
        }
        // 清除全局 Cookie（S6: 注意这影响所有 Tab，仅在真正退出无痕时调用）
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } catch (Exception ignored) {}
        try {
            android.webkit.WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {}
        // 清除所有 Tab 的 Bridge 注入状态
        jsBridgeInjectedTabs.clear();
    }

    /**
     * 根据当前 URL 决定是否注入/移除 JSBridge。
     * 仅在可信 HTTPS 域名下注入，页面离开可信域时立即移除。
     * A5: 按 tabId 追踪注入状态，支持多 Tab 独立管理。
     */
    public void injectJsBridge(@NonNull Context context, @Nullable String url) {
        WebView wv = getActiveWebView();
        if (wv == null || !BuildConfig.BROWSER_JS_BRIDGE_ENABLED) return;
        JsBridgePolicy policy = JsBridgePolicy.getInstance();
        boolean trusted = url != null && policy.canUseJsBridge(url);
        String tabId = activeTabId != null ? activeTabId : "__single__";
        boolean injected = Boolean.TRUE.equals(jsBridgeInjectedTabs.get(tabId));

        if (trusted) {
            if (!injected) {
                wv.addJavascriptInterface(new BrowserJsBridge(context.getApplicationContext()), "GameMatrixBridge");
                jsBridgeInjectedTabs.put(tabId, true);
            }
        } else {
            if (injected) {
                wv.removeJavascriptInterface("GameMatrixBridge");
                jsBridgeInjectedTabs.put(tabId, false);
            }
        }
    }

    /** 强制移除 JSBridge，用于页面开始加载或不可信域。 A5: 移除当前 Tab 的注入状态。 */
    public void removeJsBridge() {
        WebView wv = getActiveWebView();
        if (wv != null) {
            String tabId = activeTabId != null ? activeTabId : "__single__";
            if (Boolean.TRUE.equals(jsBridgeInjectedTabs.get(tabId))) {
                wv.removeJavascriptInterface("GameMatrixBridge");
                jsBridgeInjectedTabs.put(tabId, false);
            }
        }
    }

    /** A5: 关闭 Tab 时清理该 Tab 的 JS Bridge 状态。 */
    public void clearJsBridgeForTab(@NonNull String tabId) {
        WebView wv = pool != null ? pool.getActiveWebView() : null;
        // 移除对应 Tab 的注入标记
        jsBridgeInjectedTabs.remove(tabId);
    }

    public void onResume(Context context) {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            if (pool != null) pool.onResume(context);
            return;
        }
        if (webView != null) {
            webView.onResume();
            applySettings(context);
        }
    }

    public void applySettings(@NonNull Context context) {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            if (pool != null) pool.applySettingsToAll(context);
            return;
        }
        if (webView != null) {
            BrowserSettingsManager.getInstance(context).applyToWebView(webView);
        }
    }

    public void onPause() {
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            if (pool != null) pool.onPause();
            return;
        }
        if (webView != null) webView.onPause();
    }

    public void destroy() {
        // A5: 清理所有 Tab 的 JS Bridge 状态
        jsBridgeInjectedTabs.clear();

        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            if (pool != null) {
                pool.releaseAll();
                pool = null;
            }
            activeTabId = null;
            return;
        }
        if (webView != null) {
            // P0 内存泄漏修复：必须先从父容器移除 WebView，再调用 destroy()。
            // 否则 WebView 仍挂在 ViewGroup 上，Chromium 渲染进程、JS 引擎、
            // native 组件无法被 GC 回收，单次泄漏可达 30-80MB。
            // 对比 BrowserWebViewPool 多 Tab 路径已正确执行 removeView。
            if (webViewContainer != null) {
                try {
                    webViewContainer.removeView(webView);
                } catch (Throwable ignored) {}
            }
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        webViewContainer = null;
        webViewClient = null;
        chromeClient = null;
    }
}

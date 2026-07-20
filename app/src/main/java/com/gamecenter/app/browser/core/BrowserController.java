package com.gamecenter.app.browser.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.browser.bridge.BrowserJsBridge;
import com.gamecenter.app.browser.security.JsBridgePolicy;
import com.gamecenter.app.browser.util.UrlUtils;

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
    @Nullable private BrowserWebViewClient webViewClient;
    @Nullable private BrowserChromeClient chromeClient;
    private boolean jsBridgeInjected = false;

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
        container.addView(webView);

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
        mobileUserAgent = defaultUA;
        settings.setUserAgentString(defaultUA + " GameMatrixBrowser/1.0");

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

    /** Clear WebView history, cache and form data (for incognito cleanup). */
    public void clearWebViewData() {
        WebView wv = getActiveWebView();
        if (wv != null) {
            wv.clearHistory();
            wv.clearCache(true);
            wv.clearFormData();
        }
    }

    /**
     * 根据当前 URL 决定是否注入/移除 JSBridge。
     * 仅在可信 HTTPS 域名下注入，页面离开可信域时立即移除。
     */
    public void injectJsBridge(@NonNull Context context, @Nullable String url) {
        WebView wv = getActiveWebView();
        if (wv == null || !BuildConfig.BROWSER_JS_BRIDGE_ENABLED) return;
        JsBridgePolicy policy = JsBridgePolicy.getInstance();
        boolean trusted = url != null && policy.canUseJsBridge(url);
        if (trusted) {
            if (!jsBridgeInjected) {
                wv.addJavascriptInterface(new BrowserJsBridge(context.getApplicationContext()), "GameMatrixBridge");
                jsBridgeInjected = true;
            }
        } else {
            if (jsBridgeInjected) {
                wv.removeJavascriptInterface("GameMatrixBridge");
                jsBridgeInjected = false;
            }
        }
    }

    /** 强制移除 JSBridge，用于页面开始加载或不可信域。 */
    public void removeJsBridge() {
        WebView wv = getActiveWebView();
        if (wv != null && jsBridgeInjected) {
            wv.removeJavascriptInterface("GameMatrixBridge");
            jsBridgeInjected = false;
        }
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
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            if (pool != null) {
                pool.releaseAll();
                pool = null;
            }
            activeTabId = null;
            return;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        webViewClient = null;
        chromeClient = null;
    }
}

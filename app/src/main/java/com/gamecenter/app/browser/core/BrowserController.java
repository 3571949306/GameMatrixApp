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
 */
public class BrowserController {

    private static final String DEFAULT_HOME_URL = "https://www.baidu.com";
    private static final String DESKTOP_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private String mobileUserAgent;

    @Nullable private WebView webView;
    @Nullable private BrowserWebViewClient webViewClient;
    @Nullable private BrowserChromeClient chromeClient;
    private boolean jsBridgeInjected = false;

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
        destroy();

        webView = new WebView(context);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(webView);

        // 应用 BrowserSettingsManager 设置
        BrowserSettingsManager settingsMgr = BrowserSettingsManager.getInstance(context);
        settingsMgr.applyToWebView(webView);

        // Debug 包且用户开启调试开关时启用 WebView 远程调试
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

    public void loadUrl(@Nullable String url) {
        if (url == null || url.isEmpty() || webView == null) return;
        webView.loadUrl(url);
    }

    public void loadInput(@NonNull String input) {
        String url = UrlUtils.processInput(input);
        loadUrl(url);
    }

    public boolean goBack() {
        if (webView != null && webView.canGoBack()) { webView.goBack(); return true; }
        return false;
    }

    public boolean goForward() {
        if (webView != null && webView.canGoForward()) { webView.goForward(); return true; }
        return false;
    }

    public void reload() { if (webView != null) webView.reload(); }
    public void stopLoading() { if (webView != null) webView.stopLoading(); }

    @NonNull
    public String getCurrentUrl() {
        if (webView != null && webView.getUrl() != null) return webView.getUrl();
        return "";
    }

    @NonNull
    public String getTitle() {
        if (webView != null && webView.getTitle() != null) return webView.getTitle();
        return "";
    }

    public boolean canGoBack() { return webView != null && webView.canGoBack(); }
    public boolean canGoForward() { return webView != null && webView.canGoForward(); }

    /** Set custom User-Agent string for desktop/mobile mode toggle. */
    public void setUserAgent(@Nullable String userAgent) {
        if (webView != null && userAgent != null) webView.getSettings().setUserAgentString(userAgent);
    }

    public void setDownloadListener(@Nullable android.webkit.DownloadListener listener) {
        if (webView != null) webView.setDownloadListener(listener);
    }

    public void setDesktopMode(boolean enabled) {
        if (webView == null) return;
        String baseUA = enabled ? DESKTOP_USER_AGENT : mobileUserAgent;
        if (baseUA == null) baseUA = webView.getSettings().getUserAgentString();
        String suffix = baseUA.contains("GameMatrixBrowser/1.0") ? "" : " GameMatrixBrowser/1.0";
        webView.getSettings().setUserAgentString(baseUA + suffix);
        webView.reload();
    }

    @Nullable
    public WebView getWebView() { return webView; }

    @Nullable
    public BrowserChromeClient getChromeClient() { return chromeClient; }

    @NonNull
    public String getDefaultHomeUrl() { return DEFAULT_HOME_URL; }

    /** Clear WebView history, cache and form data (for incognito cleanup). */
    public void clearWebViewData() {
        if (webView != null) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
        }
    }

    /**
     * 根据当前 URL 决定是否注入/移除 JSBridge。
     * 仅在可信 HTTPS 域名下注入，页面离开可信域时立即移除。
     */
    public void injectJsBridge(@NonNull Context context, @Nullable String url) {
        if (webView == null || !BuildConfig.BROWSER_JS_BRIDGE_ENABLED) return;
        JsBridgePolicy policy = JsBridgePolicy.getInstance();
        boolean trusted = url != null && policy.canUseJsBridge(url);
        if (trusted) {
            if (!jsBridgeInjected) {
                webView.addJavascriptInterface(new BrowserJsBridge(context.getApplicationContext()), "GameMatrixBridge");
                jsBridgeInjected = true;
            }
        } else {
            if (jsBridgeInjected) {
                webView.removeJavascriptInterface("GameMatrixBridge");
                jsBridgeInjected = false;
            }
        }
    }

    /** 强制移除 JSBridge，用于页面开始加载或不可信域。 */
    public void removeJsBridge() {
        if (webView != null && jsBridgeInjected) {
            webView.removeJavascriptInterface("GameMatrixBridge");
            jsBridgeInjected = false;
        }
    }

    public void onResume(Context context) {
        if (webView != null) {
            webView.onResume();
            applySettings(context);
        }
    }

    public void applySettings(@NonNull Context context) {
        if (webView != null) {
            BrowserSettingsManager.getInstance(context).applyToWebView(webView);
        }
    }

    public void onPause() { if (webView != null) webView.onPause(); }

    public void destroy() {
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

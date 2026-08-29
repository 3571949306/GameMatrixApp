package com.gamecenter.app.browser.core;

import android.graphics.Bitmap;
import android.content.Context;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.browser.security.BrowserSecurityPolicy;
import com.gamecenter.app.browser.security.AdBlocker;
import com.gamecenter.app.browser.security.BrowserTrackerBlocker;
import com.gamecenter.app.browser.security.BrowserTrackerStats;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Browser WebViewClient handling page navigation, errors, security, and ad blocking.
 */
public class BrowserWebViewClient extends WebViewClient {

    public interface PageLoadCallback {
        /** @param tabId 产生事件的 Tab id；单 WebView 模式下为 null */
        void onPageStarted(@Nullable String tabId, String url, Bitmap favicon);
        void onPageFinished(@Nullable String tabId, String url);
        void onPageError(@Nullable String tabId, String url, String description);
        void onReceivedSslError(@Nullable String tabId, String url);
    }

    /** 外部 URL 处理回调：由 BrowserFragment 实现弹窗确认 */
    public interface ExternalUrlHandler {
        /**
         * 当 WebView 遇到非 http/https 链接时调用。
         * @param url 外部链接
         * @return true 表示已由宿主处理，WebView 不再加载
         */
        boolean onExternalUrlRequested(@NonNull String url);
    }

    private final PageLoadCallback callback;
    /** A2: 回调所属 Tab；由 WebView 池按 tab 绑定，防止后台 Tab 事件被当作前台页处理。 */
    @Nullable private final String tabId;
    /** A-4：一次性获取的设置中心，拦截回调内只读取 volatile 内存快照。 */
    @Nullable private final BrowserSettingsManager requestSettings;
    @Nullable private ExternalUrlHandler externalUrlHandler;
    private final AdBlocker adBlocker = AdBlocker.getInstance();
    private final BrowserTrackerBlocker trackerBlocker = BrowserTrackerBlocker.getInstance();
    /**
     * Best-effort process-wide stats queue. A client is created for every pooled Tab;
     * keeping one executor per client leaked one thread per WebView and there was no
     * reliable client-destroy callback. A zero-core pool also lets the worker expire
     * after a quiet period, while dropped stats never affect navigation correctness.
     */
    private static final ExecutorService STATS_EXECUTOR = new ThreadPoolExecutor(
            0, 1, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadPoolExecutor.DiscardPolicy());

    public BrowserWebViewClient(@NonNull PageLoadCallback callback) {
        this(null, callback, null);
    }

    public BrowserWebViewClient(@NonNull PageLoadCallback callback, @Nullable String tabId) {
        this(null, callback, tabId);
    }

    public BrowserWebViewClient(@Nullable Context context, @NonNull PageLoadCallback callback) {
        this(context, callback, null);
    }

    public BrowserWebViewClient(@Nullable Context context, @NonNull PageLoadCallback callback,
                                @Nullable String tabId) {
        this.callback = callback;
        this.tabId = tabId;
        requestSettings = context != null ? BrowserSettingsManager.getInstance(context) : null;
    }

    public void setExternalUrlHandler(@Nullable ExternalUrlHandler handler) {
        this.externalUrlHandler = handler;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        if (callback != null) callback.onPageStarted(tabId, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (callback != null) callback.onPageFinished(tabId, url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request.isForMainFrame() && callback != null) {
            callback.onPageError(
                tabId,
                request.getUrl().toString(),
                error.getDescription() != null ? error.getDescription().toString() : "未知错误"
            );
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        if (callback != null) callback.onReceivedSslError(tabId, error.getUrl());
        handler.cancel();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // Only top-level navigations may leave the WebView. Subframe requests are
        // common for embeds and must not trigger an external-app confirmation or
        // accidentally blank an otherwise valid page.
        if (request == null || !request.isForMainFrame()) return false;
        String url = request.getUrl().toString();
        BrowserSecurityPolicy.UrlPolicy policy = BrowserSecurityPolicy.getInstance().checkUrlPolicy(url);
        switch (policy) {
            case ALLOW_INTERNAL:
                return false;
            case CONFIRM_EXTERNAL:
                if (externalUrlHandler != null && externalUrlHandler.onExternalUrlRequested(url)) {
                    return true;
                }
                return true;
            case BLOCK:
            default:
                return true;
        }
    }

    // ===== Stage 12: Ad Blocking =====

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        if ((requestSettings == null || requestSettings.isAdBlockEnabled())
                && adBlocker.shouldBlock(url)) {
            return emptyResponse();
        }

        if ((requestSettings == null || requestSettings.isTrackerProtectionEnabled())
                && trackerBlocker.shouldBlock(url)) {
            if (view != null && view.getContext() != null) {
                Context appContext = view.getContext().getApplicationContext();
                if (appContext != null) {
                    STATS_EXECUTOR.execute(() ->
                            BrowserTrackerStats.getInstance(appContext).recordBlock(url));
                }
            }
            return emptyResponse();
        }

        // 数据节省模式：拦截图片/字体资源（仅非主框架）
        // Legacy constructor callers have no settings snapshot; fail closed for
        // data-saver rather than reintroducing a per-resource SharedPreferences read.
        boolean dataSaverEnabled = requestSettings != null
                && requestSettings.isDataSaverEnabled();
        if (!request.isForMainFrame() && dataSaverEnabled) {
            String lower = url.toLowerCase(java.util.Locale.ROOT);
            if (isImageOrFont(lower)) {
                return emptyResponse();
            }
        }
        return super.shouldInterceptRequest(view, request);
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
            new ByteArrayInputStream(new byte[0]));
    }

    private static boolean isImageOrFont(String lower) {
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".webp") || lower.endsWith(".bmp")
                || lower.endsWith(".svg") || lower.contains(".jpg?")
                || lower.contains(".png?") || lower.contains(".webp?")
                || lower.endsWith(".woff") || lower.endsWith(".woff2")
                || lower.endsWith(".ttf") || lower.endsWith(".otf")
                || lower.endsWith(".eot");
    }
}

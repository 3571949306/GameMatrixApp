package com.gamecenter.app.browser.core;

import android.graphics.Bitmap;
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
import java.util.concurrent.Executors;

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
    @Nullable private ExternalUrlHandler externalUrlHandler;
    private final AdBlocker adBlocker = AdBlocker.getInstance();
    private final BrowserTrackerBlocker trackerBlocker = BrowserTrackerBlocker.getInstance();
    private final ExecutorService statsExecutor = Executors.newSingleThreadExecutor();

    public BrowserWebViewClient(@NonNull PageLoadCallback callback) {
        this(callback, null);
    }

    public BrowserWebViewClient(@NonNull PageLoadCallback callback, @Nullable String tabId) {
        this.callback = callback;
        this.tabId = tabId;
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

        if (adBlocker.shouldBlock(url)) {
            return emptyResponse();
        }

        if (trackerBlocker.shouldBlock(url)) {
            if (view != null && view.getContext() != null) {
                statsExecutor.execute(() ->
                    BrowserTrackerStats.getInstance(view.getContext()).recordBlock(url)
                );
            }
            return emptyResponse();
        }

        // 数据节省模式：拦截图片/字体资源（仅非主框架）
        if (view != null && view.getContext() != null
                && !request.isForMainFrame()
                && BrowserSettingsManager.getInstance(view.getContext()).isDataSaverEnabled()) {
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

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

import java.io.ByteArrayInputStream;

/**
 * Browser WebViewClient handling page navigation, errors, security, and ad blocking.
 */
public class BrowserWebViewClient extends WebViewClient {

    public interface PageLoadCallback {
        void onPageStarted(String url, Bitmap favicon);
        void onPageFinished(String url);
        void onPageError(String url, String description);
        void onReceivedSslError(String url);
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
    @Nullable private ExternalUrlHandler externalUrlHandler;

    public BrowserWebViewClient(@NonNull PageLoadCallback callback) {
        this.callback = callback;
    }

    public void setExternalUrlHandler(@Nullable ExternalUrlHandler handler) {
        this.externalUrlHandler = handler;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        if (callback != null) callback.onPageStarted(url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (callback != null) callback.onPageFinished(url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request.isForMainFrame() && callback != null) {
            callback.onPageError(
                request.getUrl().toString(),
                error.getDescription() != null ? error.getDescription().toString() : "\u672a\u77e5\u9519\u8bef"
            );
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        if (callback != null) callback.onReceivedSslError(error.getUrl());
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
        AdBlocker adBlocker = AdBlocker.getInstance();
        if (adBlocker.shouldBlock(url)) {
            return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
        }
        return super.shouldInterceptRequest(view, request);
    }
}

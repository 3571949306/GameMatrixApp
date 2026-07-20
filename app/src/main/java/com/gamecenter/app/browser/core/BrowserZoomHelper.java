package com.gamecenter.app.browser.core;

import android.content.Context;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 智能双指缩放辅助类（P2-1）。
 *
 * <p>在 WebView 双指缩放时，同步调整文字缩放比例并持久化（按 host 维度记忆），
 * 实现"字号记忆"——同一站点下次访问自动应用上次的字号。
 *
 * <p>仅当 {@code BrowserSettingsManager.isSmartZoomEnabled()} 返回 true 时启用。
 */
public class BrowserZoomHelper {

    private static final int MIN_TEXT_ZOOM = 50;
    private static final int MAX_TEXT_ZOOM = 200;
    private static final int DEFAULT_TEXT_ZOOM = 100;
    private static final int STEP = 10;

    private final Context context;
    private final BrowserSettingsManager settings;
    private ScaleGestureDetector scaleDetector;

    public BrowserZoomHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.settings = BrowserSettingsManager.getInstance(this.context);
    }

    /** 为指定 WebView 应用持久化的字号（按 host） */
    public void applySavedTextZoom(@NonNull WebView webView, @Nullable String url) {
        if (!settings.isSmartZoomEnabled()) return;
        int zoom = getSavedZoomForUrl(url);
        try {
            WebSettings s = webView.getSettings();
            s.setTextZoom(zoom);
        } catch (Throwable ignored) {}
    }

    /** 增大字号 */
    public void increaseTextZoom(@NonNull WebView webView) {
        adjustTextZoom(webView, STEP);
    }

    /** 减小字号 */
    public void decreaseTextZoom(@NonNull WebView webView) {
        adjustTextZoom(webView, -STEP);
    }

    /** 重置为 100% */
    public void resetTextZoom(@NonNull WebView webView) {
        try {
            webView.getSettings().setTextZoom(DEFAULT_TEXT_ZOOM);
            persistZoomForCurrent(webView, DEFAULT_TEXT_ZOOM);
        } catch (Throwable ignored) {}
    }

    private void adjustTextZoom(@NonNull WebView webView, int delta) {
        if (!settings.isSmartZoomEnabled()) return;
        try {
            int current = webView.getSettings().getTextZoom();
            int next = Math.max(MIN_TEXT_ZOOM, Math.min(MAX_TEXT_ZOOM, current + delta));
            webView.getSettings().setTextZoom(next);
            persistZoomForCurrent(webView, next);
        } catch (Throwable ignored) {}
    }

    private void persistZoomForCurrent(@NonNull WebView webView, int zoom) {
        String url = webView.getUrl();
        if (url == null || url.isEmpty()) return;
        String host = extractHost(url);
        if (host.isEmpty()) return;
        context.getSharedPreferences("browser_text_zoom", Context.MODE_PRIVATE)
                .edit().putInt(host, zoom).apply();
    }

    private int getSavedZoomForUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return DEFAULT_TEXT_ZOOM;
        String host = extractHost(url);
        if (host.isEmpty()) return DEFAULT_TEXT_ZOOM;
        return context.getSharedPreferences("browser_text_zoom", Context.MODE_PRIVATE)
                .getInt(host, DEFAULT_TEXT_ZOOM);
    }

    private String extractHost(@NonNull String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() != null ? uri.getHost().toLowerCase(java.util.Locale.ROOT) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** ScaleGestureDetector 回调（如需接入双指缩放细化可扩展） */
    public static class ZoomListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            return false; // 由 WebView 内置缩放接管
        }
    }
}

package com.gamecenter.app.browser.core;

import android.net.Uri;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Browser WebChromeClient handling title, progress, file upload, fullscreen video, and permissions.
 */
public class BrowserChromeClient extends WebChromeClient {

    /** Page info callback */
    public interface PageInfoCallback {
        /** @param tabId 产生事件的 Tab id；单 WebView 模式下为 null */
        void onTitleChanged(@Nullable String tabId, String title);
        void onProgressChanged(@Nullable String tabId, int progress);
        void onReceivedIcon(@Nullable String tabId, android.graphics.Bitmap icon);
    }

    /** File chooser callback for <input type="file"> */
    public interface FileChooserCallback {
        void onShowFileChooser(ValueCallback<Uri[]> callback, FileChooserParams params);
    }

    /** Fullscreen video callback for HTML5 video */
    public interface FullscreenCallback {
        void onShowCustomView(View view, CustomViewCallback callback);
        void onHideCustomView();
        boolean isCustomViewShowing();
    }

    /** Permission request callback for geolocation, camera, microphone */
    public interface PermissionCallback {
        void onGeolocationPermissionRequest(String origin, GeolocationPermissions.Callback callback);
        void onPermissionRequest(PermissionRequest request);
    }

    private final PageInfoCallback callback;
    /** A2: 回调所属 Tab；由 WebView 池按 tab 绑定，防止后台 Tab 事件被当作前台页处理。 */
    @Nullable private final String tabId;
    @Nullable private FileChooserCallback fileChooserCallback;
    @Nullable private FullscreenCallback fullscreenCallback;
    @Nullable private PermissionCallback permissionCallback;

    public BrowserChromeClient(@NonNull PageInfoCallback callback) {
        this(callback, null);
    }

    public BrowserChromeClient(@NonNull PageInfoCallback callback, @Nullable String tabId) {
        this.callback = callback;
        this.tabId = tabId;
    }

    public void setFileChooserCallback(@Nullable FileChooserCallback cb) { this.fileChooserCallback = cb; }
    public void setFullscreenCallback(@Nullable FullscreenCallback cb) { this.fullscreenCallback = cb; }
    public void setPermissionCallback(@Nullable PermissionCallback cb) { this.permissionCallback = cb; }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        if (callback != null) callback.onTitleChanged(tabId, title);
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (callback != null) callback.onProgressChanged(tabId, newProgress);
    }

    @Override
    public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {
        super.onReceivedIcon(view, icon);
        if (callback != null) callback.onReceivedIcon(tabId, icon);
    }

    // ===== Stage 10: File Upload =====

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams) {
        if (fileChooserCallback != null) {
            fileChooserCallback.onShowFileChooser(filePathCallback, fileChooserParams);
            return true;
        }
        filePathCallback.onReceiveValue(null);
        return false;
    }

    // ===== Stage 10: Permissions =====

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback geoCallback) {
        if (permissionCallback != null) {
            permissionCallback.onGeolocationPermissionRequest(origin, geoCallback);
        } else {
            geoCallback.invoke(origin, false, false);
        }
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        if (permissionCallback != null) {
            permissionCallback.onPermissionRequest(request);
        } else {
            request.deny();
        }
    }

    // ===== Stage 11: Fullscreen Video =====

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fullscreenCallback != null) {
            fullscreenCallback.onShowCustomView(view, callback);
        } else {
            callback.onCustomViewHidden();
        }
    }

    @Override
    public void onHideCustomView() {
        if (fullscreenCallback != null) {
            fullscreenCallback.onHideCustomView();
        }
    }
}

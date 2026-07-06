package com.gamecenter.app.browser.bridge;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.gamecenter.app.BuildConfig;

import java.lang.ref.WeakReference;

/**
 * 浏览器 JSBridge - 仅对可信域名开放。
 */
public class BrowserJsBridge {

    private final WeakReference<Context> contextRef;

    public BrowserJsBridge(Context context) {
        this.contextRef = new WeakReference<>(context);
    }

    @JavascriptInterface
    public String getAppVersion() { return BuildConfig.VERSION_NAME; }

    @JavascriptInterface
    public void showToast(String message) {
        Context ctx = contextRef.get();
        if (ctx != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show());
        }
    }

    @JavascriptInterface
    public void shareContent(String title, String text) {
        Context ctx = contextRef.get();
        if (ctx != null) {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
            intent.putExtra(android.content.Intent.EXTRA_TEXT, text);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }
}

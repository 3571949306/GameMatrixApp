package com.gamecenter.app.browser.core;

import android.webkit.WebView;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;

/**
 * 媒体嗅探（P2-3）。
 *
 * <p>通过 evaluateJavascript 注入脚本，提取页面中的视频源（&lt;video&gt; src/source）
 * 和 PDF 链接（&lt;a href=&quot;*.pdf&quot;&gt;、&lt;embed&gt;），返回 JSON 数组字符串。</p>
 */
public class BrowserMediaSniffer {

    private BrowserMediaSniffer() {}

    /** 提取脚本：返回 JSON 数组 [{type, url, label}] */
    private static final String SCRIPT =
            "(function(){var results=[];" +
            "document.querySelectorAll('video').forEach(function(v){" +
            "  if(v.src) results.push({type:'video',url:v.src,label:'video source'});" +
            "  v.querySelectorAll('source').forEach(function(s){ if(s.src) results.push({type:'video',url:s.src,label:'source tag'}); });" +
            "});" +
            "document.querySelectorAll('a[href]').forEach(function(a){" +
            "  var h=a.href||'';" +
            "  if(h.toLowerCase().match(/\\.pdf(\\?|$)/)) results.push({type:'pdf',url:h,label:a.textContent||a.href});" +
            "});" +
            "document.querySelectorAll('embed[src]').forEach(function(e){" +
            "  var s=e.src||'';" +
            "  if(s.toLowerCase().match(/\\.pdf(\\?|$)/)) results.push({type:'pdf',url:s,label:'embed pdf'});" +
            "});" +
            "return JSON.stringify(results);})();";

    public interface Callback {
        void onResult(@NonNull String jsonResult);
    }

    /** 异步嗅探页面媒体资源 */
    public static void sniff(@NonNull WebView webView, @NonNull final Callback callback) {
        webView.evaluateJavascript(SCRIPT, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || value.equals("null") || value.equals("undefined")) {
                    callback.onResult("[]");
                    return;
                }
                // evaluateJavascript 返回的是 JSON 字符串字面量（带引号），需反转义
                callback.onResult(unescape(value));
            }
        });
    }

    private static String unescape(@NonNull String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            return inner.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\/", "/")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t");
        }
        return value;
    }
}

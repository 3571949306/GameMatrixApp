package com.gamecenter.app.browser.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;

/**
 * 浏览器阅读模式辅助类。
 *
 * <p>通过 evaluateJavascript 注入 JS 脚本：
 * <ul>
 *   <li>提取页面标题</li>
 *   <li>提取 <article> 或最大文本密度 <div>/<section> 的 innerHTML</li>
 *   <li>移除 nav/footer/aside/script/style</li>
 *   <li>用美化后的 HTML 模板替换 WebView 内容</li>
 * </ul>
 *
 * <p>注意：阅读模式是"尽力提取"，对于 SPA / 富交互页面可能效果有限。
 */
public class BrowserReaderModeHelper {

    public interface ReaderModeCallback {
        void onReaderModeEntered();
        void onReaderModeExited();
    }

    /** JS 提取脚本，输出 JSON 字符串：{"title": "...", "content": "..."} */
    private static final String EXTRACT_JS =
        "(function(){" +
        "  function pickMain(){" +
        "    var candidates = [];" +
        "    var tags = ['article','main','div','section'];" +
        "    for (var t=0; t<tags.length; t++){" +
        "      var nodes = document.getElementsByTagName(tags[t]);" +
        "      for (var i=0; i<nodes.length; i++){" +
        "        var n = nodes[i];" +
        "        var text = n.innerText || '';" +
        "        var len = text.trim().length;" +
        "        if (len > 200) candidates.push({node:n, len:len});" +
        "      }" +
        "    }" +
        "    candidates.sort(function(a,b){return b.len-a.len;});" +
        "    return candidates.length > 0 ? candidates[0].node : document.body;" +
        "  }" +
        "  // 移除干扰元素" +
        "  var removes = document.querySelectorAll('script,style,nav,footer,aside,iframe,header,form,button,svg,canvas');" +
        "  for (var r=0; r<removes.length; r++){ removes[r].parentNode && removes[r].parentNode.removeChild(removes[r]); }" +
        "  var main = pickMain();" +
        "  var title = document.title || '';" +
        "  var content = main ? (main.innerHTML || '') : '';" +
        "  return JSON.stringify({title:title, content:content});" +
        "})();";

    @Nullable private WebView webView;
    @Nullable private ReaderModeCallback callback;
    private boolean readerModeActive = false;
    @Nullable private String originalUrl;
    @Nullable private String readerTitle;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void bind(@NonNull WebView webView, @Nullable ReaderModeCallback callback) {
        this.webView = webView;
        this.callback = callback;
    }

    public boolean isActive() {
        return readerModeActive;
    }

    /** 进入阅读模式：注入 JS 提取正文 → 渲染美化 HTML */
    @SuppressLint("SetJavaScriptEnabled")
    public void enterReaderMode(@NonNull final Context context) {
        if (webView == null) return;
        if (readerModeActive) {
            Toast.makeText(context, R.string.browser_reader_already_active, Toast.LENGTH_SHORT).show();
            return;
        }
        originalUrl = webView.getUrl();
        try {
            webView.evaluateJavascript(EXTRACT_JS, value -> {
                if (value == null || value.equals("null") || value.equals("\"\"")) {
                    mainHandler.post(() -> Toast.makeText(context,
                            R.string.browser_reader_extract_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                // JS 返回的是被引号包裹的 JSON 字符串
                String jsonStr = value;
                if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                    // 去掉外层引号并反转义
                    jsonStr = unescapeJsString(jsonStr.substring(1, jsonStr.length() - 1));
                }
                final String title;
                final String content;
                try {
                    org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                    title = obj.optString("title", "");
                    content = obj.optString("content", "");
                } catch (Exception e) {
                    mainHandler.post(() -> Toast.makeText(context,
                            R.string.browser_reader_extract_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                if (content == null || content.trim().isEmpty()) {
                    mainHandler.post(() -> Toast.makeText(context,
                            R.string.browser_reader_extract_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                readerTitle = title;
                final String html = buildReaderHtml(title, content);
                mainHandler.post(() -> {
                    if (webView == null) return;
                    readerModeActive = true;
                    // 用 loadDataWithBaseURL 避免影响历史栈
                    webView.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null);
                    if (callback != null) callback.onReaderModeEntered();
                });
            });
        } catch (Throwable t) {
            Toast.makeText(context, R.string.browser_reader_extract_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 退出阅读模式：恢复原始 URL */
    public void exitReaderMode() {
        if (!readerModeActive || webView == null) return;
        readerModeActive = false;
        String url = originalUrl;
        originalUrl = null;
        readerTitle = null;
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }
        if (callback != null) callback.onReaderModeExited();
    }

    public void toggle(@NonNull Context context) {
        if (readerModeActive) exitReaderMode();
        else enterReaderMode(context);
    }

    @Nullable
    public String getReaderTitle() {
        return readerTitle;
    }

    /** 生成阅读模式 HTML：浅色卡片背景 + 字体放大 + 行高放松，自动跟随 prefers-color-scheme */
    private String buildReaderHtml(@NonNull String title, @NonNull String content) {
        // 转义标题中可能的特殊字符
        String safeTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no'>"
            + "<style>"
            + "  :root { color-scheme: light dark; }"
            + "  body { font-family: -apple-system, 'Helvetica Neue', 'Noto Sans SC', sans-serif;"
            + "         max-width: 720px; margin: 0 auto; padding: 24px 16px 80px;"
            + "         line-height: 1.75; font-size: 17px;"
            + "         background: #ffffff; color: #1a1a1a; }"
            + "  @media (prefers-color-scheme: dark) {"
            + "    body { background: #121212; color: #e4e4e4; }"
            + "    a { color: #8ab4f8; }"
            + "    img { opacity: 0.85; }"
            + "  }"
            + "  h1.title { font-size: 26px; font-weight: 600; line-height: 1.3;"
            + "             margin: 0 0 24px; padding-bottom: 16px;"
            + "             border-bottom: 1px solid rgba(128,128,128,0.2); }"
            + "  p { margin: 0 0 16px; }"
            + "  img { max-width: 100%; height: auto; border-radius: 8px; margin: 16px 0; }"
            + "  a { color: #1a73e8; text-decoration: none; }"
            + "  blockquote { margin: 16px 0; padding: 8px 16px;"
            + "               border-left: 3px solid rgba(128,128,128,0.4);"
            + "               background: rgba(128,128,128,0.05); color: inherit; }"
            + "  pre, code { font-family: 'JetBrains Mono', monospace; font-size: 14px; }"
            + "  pre { background: rgba(128,128,128,0.1); padding: 12px; border-radius: 6px; overflow-x: auto; }"
            + "</style></head><body>"
            + "<h1 class='title'>" + safeTitle + "</h1>"
            + content
            + "</body></html>";
    }

    /** 反转义 JS 字符串字面量（处理 \\n \\t \\" \\\\ 等） */
    private static String unescapeJsString(@NonNull String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '/': sb.append('/'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int code = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) code);
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public void destroy() {
        webView = null;
        callback = null;
        originalUrl = null;
        readerTitle = null;
        readerModeActive = false;
    }
}

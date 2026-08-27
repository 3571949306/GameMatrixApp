package com.gamecenter.app.browser.core;

import android.content.Context;
import android.webkit.WebView;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 离线缓存（P1-4）。
 *
 * <p>LRU 缓存最近 10 个页面的 URL + 标题 + 提取的 HTML 正文快照。
 * 持久化到应用私有文件目录（filesDir）的 JSON 文件。
 *
 * <p>安全说明（P2 加固）：旧版把整页 outerHTML 明文写入 SharedPreferences，快照含
 * 页面内可能出现的登录态/token 且 shared_prefs 位于标准的 xml 明文目录；现改为落
 * 应用私有文件，避免与其它 SP 数据混存。若页面含敏感数据，页面本身的快照语义仍由
 * 用户决定是否开启该功能。</p>
 *
 * <p>LRU 策略：访问/插入时移到末尾，超出容量时移除头部。</p>
 */
public class BrowserOfflineCache {

    private static volatile BrowserOfflineCache instance;
    private static final String CACHE_FILE_NAME = "browser_offline_cache.json";
    private static final int MAX_ENTRIES = 10;

    private final File cacheFile;
    /** LinkedHashMap accessOrder=true 实现 LRU；末尾为最近访问 */
    private final LinkedHashMap<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    private BrowserOfflineCache(@NonNull Context context) {
        cacheFile = new File(context.getApplicationContext().getFilesDir(), CACHE_FILE_NAME);
        loadFromDisk();
    }

    public static BrowserOfflineCache getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (BrowserOfflineCache.class) {
                if (instance == null) instance = new BrowserOfflineCache(context);
            }
        }
        return instance;
    }

    /** 缓存条目 */
    public static class CacheEntry {
        public final String url;
        public final String title;
        public final String htmlSnapshot;
        public final long savedAt;

        public CacheEntry(String url, String title, String htmlSnapshot, long savedAt) {
            this.url = url;
            this.title = title;
            this.htmlSnapshot = htmlSnapshot;
            this.savedAt = savedAt;
        }
    }

    /**
     * 异步提取页面 HTML 并缓存。
     * 通过 evaluateJavascript 注入脚本获取 document.documentElement.outerHTML。
     */
    public void captureAsync(@NonNull WebView webView, @NonNull final String url, @Nullable final String titleHint) {
        webView.evaluateJavascript(
                "(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})();",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (value == null || value.equals("null") || value.equals("undefined")) return;
                        String html = unquoteJs(value);
                        if (html.isEmpty()) return;
                        String title = titleHint != null ? titleHint : extractTitle(html);
                        put(url, title, html);
                    }
                });
    }

    /** 同步放入缓存（自动 LRU 淘汰 + 持久化） */
    public synchronized void put(@NonNull String url, @NonNull String title, @NonNull String html) {
        CacheEntry entry = new CacheEntry(url, title, html, System.currentTimeMillis());
        cache.put(url, entry);
        trimToSize();
        persist();
    }

    @Nullable
    public synchronized CacheEntry get(@NonNull String url) {
        return cache.get(url);
    }

    public synchronized boolean contains(@NonNull String url) {
        return cache.containsKey(url);
    }

    public synchronized void remove(@NonNull String url) {
        cache.remove(url);
        persist();
    }

    public synchronized void clear() {
        cache.clear();
        persist();
    }

    /** 按最近访问倒序返回（最最近访问在前） */
    @NonNull
    public synchronized List<CacheEntry> getAll() {
        List<CacheEntry> list = new ArrayList<>(cache.values());
        java.util.Collections.reverse(list);
        return list;
    }

    public synchronized int size() {
        return cache.size();
    }

    private void trimToSize() {
        while (cache.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            } else break;
        }
    }

    private void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (CacheEntry e : cache.values()) {
                JSONObject o = new JSONObject();
                o.put("url", e.url);
                o.put("title", e.title);
                o.put("html", e.htmlSnapshot);
                o.put("savedAt", e.savedAt);
                arr.put(o);
            }
            byte[] payload = arr.toString().getBytes(StandardCharsets.UTF_8);
            File tmp = new File(cacheFile.getParentFile(), CACHE_FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(payload);
            }
            if (!tmp.renameTo(cacheFile)) {
                try (FileOutputStream out = new FileOutputStream(cacheFile)) {
                    out.write(payload);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void loadFromDisk() {
        try {
            if (!cacheFile.isFile() || cacheFile.length() <= 0) return;
            byte[] bytes = new byte[(int) cacheFile.length()];
            try (FileInputStream in = new FileInputStream(cacheFile)) {
                int off = 0, n;
                while (off < bytes.length && (n = in.read(bytes, off, bytes.length - off)) > 0) off += n;
            }
            String json = new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
            if (json.isEmpty()) return;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CacheEntry e = new CacheEntry(
                        o.optString("url", ""),
                        o.optString("title", ""),
                        o.optString("html", ""),
                        o.optLong("savedAt", 0L));
                if (!e.url.isEmpty()) cache.put(e.url, e);
            }
            trimToSize();
        } catch (Throwable ignored) {}
    }

    private String extractTitle(@NonNull String html) {
        try {
            int s = html.toLowerCase(Locale.ROOT).indexOf("<title>");
            if (s < 0) return "";
            int e = html.toLowerCase(Locale.ROOT).indexOf("</title>", s);
            if (e < 0) return "";
            return html.substring(s + 7, e).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 反转义 evaluateJavascript 返回的 JSON 字符串 */
    private String unquoteJs(@NonNull String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            StringBuilder sb = new StringBuilder(inner.length());
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '\\' && i + 1 < inner.length()) {
                    char next = inner.charAt(++i);
                    switch (next) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 < inner.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(inner.substring(i + 1, i + 5), 16));
                                    i += 4;
                                } catch (Throwable t) {
                                    sb.append(next);
                                }
                            } else sb.append(next);
                            break;
                        default: sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return value;
    }
}

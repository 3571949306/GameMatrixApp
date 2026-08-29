package com.gamecenter.app.browser.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-tab manager.
 * Max 10 tabs. Incognito tabs excluded from persistence.
 */
public class BrowserTabManager {

    public static final int MAX_TABS = 10;
    private static final String PREFS_NAME = "browser_tabs";
    private static final String KEY_TABS = "tab_list";
    private static final String KEY_ACTIVE_ID = "active_tab_id";

    private static volatile BrowserTabManager instance;
    private final SharedPreferences prefs;
    // A8: 使用线程安全的 CopyOnWriteArrayList 替代 ArrayList
    private final List<Tab> tabs = new CopyOnWriteArrayList<>();
    /**
     * System.currentTimeMillis() alone collides when the tab switcher creates two
     * tabs in one clock tick. Keep a monotonic in-process sequence while retaining
     * the timestamp-shaped ids used by already-persisted normal tabs.
     */
    private final AtomicLong tabIdSequence = new AtomicLong();
    private String activeTabId;
    // A8: LruCache 管理 favicon 内存，最多缓存 100 个（约 10 Tab * 10 倍冗余）
    private final LruCache<String, Bitmap> faviconCache = new LruCache<>(100);

    public static class Tab {
        private String id;
        private String title;
        private String url;
        private boolean isIncognito;
        private long lastActiveTime;

        public Tab(String id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.lastActiveTime = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public boolean isIncognito() { return isIncognito; }
        public void setIncognito(boolean incognito) { isIncognito = incognito; }
        public long getLastActiveTime() { return lastActiveTime; }
        public void setLastActiveTime(long time) { this.lastActiveTime = time; }
    }

    private BrowserTabManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadTabs();
        if (tabs.isEmpty()) createTab("https://www.baidu.com");
    }

    public static BrowserTabManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BrowserTabManager.class) {
                if (instance == null) instance = new BrowserTabManager(context);
            }
        }
        return instance;
    }

    public Tab createTab(@Nullable String url) {
        if (tabs.size() >= MAX_TABS) return null;
        Tab tab = new Tab(nextTabId(false),
            "\u65b0\u6807\u7b7e\u9875", url != null ? url : "https://www.baidu.com");
        tabs.add(tab);
        activeTabId = tab.getId();
        saveTabs();
        return tab;
    }

    public Tab createIncognitoTab(@Nullable String url) {
        if (tabs.size() >= MAX_TABS) return null;
        Tab tab = new Tab(nextTabId(true),
            "\u65e0\u75d5\u6807\u7b7e", url != null ? url : "https://www.baidu.com");
        tab.setIncognito(true);
        tabs.add(tab);
        activeTabId = tab.getId();
        return tab;
    }

    public void closeTab(String tabId) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(tabId)) {
                tabs.remove(i);
                removeTabFavicon(tabId);  // A8: 清理 favicon 缓存
                if (tabs.isEmpty()) {
                    createTab("https://www.baidu.com");
                } else if (activeTabId != null && activeTabId.equals(tabId)) {
                    activeTabId = tabs.get(Math.min(i, tabs.size() - 1)).getId();
                }
                saveTabs();
                return;
            }
        }
    }

    public void closeAllTabs() {
        tabs.clear();
        activeTabId = null;
        faviconCache.evictAll();  // A8: 清理所有 favicon 缓存
        prefs.edit().remove(KEY_TABS).remove(KEY_ACTIVE_ID).apply();
        createTab("https://www.baidu.com");
    }

    public void switchTab(String tabId) {
        for (Tab tab : tabs) {
            if (tab.getId().equals(tabId)) {
                activeTabId = tabId;
                tab.setLastActiveTime(System.currentTimeMillis());
                saveTabs();
                return;
            }
        }
    }

    @Nullable
    public Tab getCurrentTab() {
        for (Tab tab : tabs) { if (tab.getId().equals(activeTabId)) return tab; }
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    public List<Tab> getTabList() { return new ArrayList<>(tabs); }

    /** A8: 清理 favicon 缓存（供内存管理调用）。 */
    public void clearFaviconCache() {
        faviconCache.evictAll();
    }
    public int getTabCount() { return tabs.size(); }
    public String getActiveTabId() { return activeTabId; }

    public boolean hasIncognitoTabs() {
        for (Tab tab : tabs) { if (tab.isIncognito()) return true; }
        return false;
    }

    public void updateTabInfo(String tabId, String title, String url) {
        for (Tab tab : tabs) {
            if (tab.getId().equals(tabId)) {
                if (title != null && !title.isEmpty()) tab.setTitle(title);
                if (url != null && !url.isEmpty()) tab.setUrl(url);
                tab.setLastActiveTime(System.currentTimeMillis());
                if (!tab.isIncognito()) saveTabs();
                return;
            }
        }
    }

    // A8: favicon 改由 LruCache 管理，Tab 不再持有 Bitmap
    public void updateTabFavicon(String tabId, Bitmap favicon) {
        if (tabId != null && favicon != null) {
            faviconCache.put(tabId, favicon);
        }
    }

    /** A8: 从 LruCache 获取指定 Tab 的 favicon。 */
    @Nullable
    public Bitmap getTabFavicon(String tabId) {
        return tabId != null ? faviconCache.get(tabId) : null;
    }

    /** A8: 移除指定 Tab 的 favicon 缓存。 */
    public void removeTabFavicon(String tabId) {
        if (tabId != null) {
            faviconCache.remove(tabId);
        }
    }

    private void loadTabs() {
        tabs.clear();
        String json = prefs.getString(KEY_TABS, null);
        activeTabId = prefs.getString(KEY_ACTIVE_ID, null);
        if (json == null) return;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.optBoolean("isIncognito", false)) continue;
                Tab tab = new Tab(obj.getString("id"), obj.optString("title", ""), obj.optString("url", ""));
                tab.setLastActiveTime(obj.optLong("lastActiveTime", System.currentTimeMillis()));
                tabs.add(tab);
            }
        } catch (JSONException e) { /* ignore */ }
        if (!hasTab(activeTabId) && !tabs.isEmpty()) activeTabId = tabs.get(0).getId();
    }

    private void saveTabs() {
        JSONArray array = new JSONArray();
        for (Tab tab : tabs) {
            if (tab.isIncognito()) continue;
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", tab.getId());
                obj.put("title", tab.getTitle());
                obj.put("url", tab.getUrl());
                obj.put("lastActiveTime", tab.getLastActiveTime());
                obj.put("isIncognito", false);
                array.put(obj);
            } catch (JSONException e) { /* skip */ }
        }
        // Incognito tabs are intentionally memory-only. Never persist an incognito id
        // as the active normal tab, otherwise the next process start has no matching
        // tab and can route the first page incorrectly.
        String persistedActiveId = null;
        if (activeTabId != null) {
            for (Tab tab : tabs) {
                if (!tab.isIncognito() && tab.getId().equals(activeTabId)) {
                    persistedActiveId = activeTabId;
                    break;
                }
            }
        }
        if (persistedActiveId == null) {
            for (Tab tab : tabs) {
                if (!tab.isIncognito()) {
                    persistedActiveId = tab.getId();
                    break;
                }
            }
        }
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_TABS, array.toString());
        if (persistedActiveId == null) editor.remove(KEY_ACTIVE_ID);
        else editor.putString(KEY_ACTIVE_ID, persistedActiveId);
        editor.apply();
    }

    private boolean hasTab(@Nullable String tabId) {
        if (tabId == null) return false;
        for (Tab tab : tabs) {
            if (tabId.equals(tab.getId())) return true;
        }
        return false;
    }

    @NonNull
    private String nextTabId(boolean incognito) {
        final String prefix = incognito ? "inc_" : "";
        while (true) {
            final long now = System.currentTimeMillis();
            final long sequence = tabIdSequence.updateAndGet(previous ->
                    Math.max(now, previous + 1));
            final String candidate = prefix + sequence;
            if (!hasTab(candidate)) return candidate;
        }
    }
}

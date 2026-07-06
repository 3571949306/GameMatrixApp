package com.gamecenter.app.browser.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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
    private final List<Tab> tabs = new ArrayList<>();
    private String activeTabId;
    private TabChangeListener listener;

    public static class Tab {
        private String id;
        private String title;
        private String url;
        private boolean isIncognito;
        private long lastActiveTime;
        private transient Bitmap favicon;

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
        public Bitmap getFavicon() { return favicon; }
        public void setFavicon(Bitmap favicon) { this.favicon = favicon; }
    }

    public interface TabChangeListener {
        void onTabChanged(Tab activeTab);
        void onTabListChanged(List<Tab> tabs);
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

    public void setTabChangeListener(TabChangeListener listener) { this.listener = listener; }

    public Tab createTab(@Nullable String url) {
        if (tabs.size() >= MAX_TABS) return null;
        Tab tab = new Tab(String.valueOf(System.currentTimeMillis()),
            "\u65b0\u6807\u7b7e\u9875", url != null ? url : "https://www.baidu.com");
        tabs.add(tab);
        activeTabId = tab.getId();
        saveTabs();
        notifyListeners(tab);
        return tab;
    }

    public Tab createIncognitoTab(@Nullable String url) {
        if (tabs.size() >= MAX_TABS) return null;
        Tab tab = new Tab("inc_" + System.currentTimeMillis(),
            "\u65e0\u75d5\u6807\u7b7e", url != null ? url : "https://www.baidu.com");
        tab.setIncognito(true);
        tabs.add(tab);
        activeTabId = tab.getId();
        if (listener != null) {
            listener.onTabListChanged(new ArrayList<>(tabs));
            listener.onTabChanged(tab);
        }
        return tab;
    }

    public void closeTab(String tabId) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(tabId)) {
                tabs.remove(i);
                if (tabs.isEmpty()) {
                    createTab("https://www.baidu.com");
                } else if (activeTabId.equals(tabId)) {
                    activeTabId = tabs.get(Math.min(i, tabs.size() - 1)).getId();
                }
                saveTabs();
                if (listener != null) {
                    listener.onTabListChanged(new ArrayList<>(tabs));
                    listener.onTabChanged(getCurrentTab());
                }
                return;
            }
        }
    }

    public void closeAllTabs() {
        tabs.clear();
        activeTabId = null;
        prefs.edit().remove(KEY_TABS).remove(KEY_ACTIVE_ID).apply();
        createTab("https://www.baidu.com");
    }

    public void switchTab(String tabId) {
        for (Tab tab : tabs) {
            if (tab.getId().equals(tabId)) {
                activeTabId = tabId;
                tab.setLastActiveTime(System.currentTimeMillis());
                saveTabs();
                if (listener != null) listener.onTabChanged(tab);
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

    public void updateTabFavicon(String tabId, Bitmap favicon) {
        for (Tab tab : tabs) {
            if (tab.getId().equals(tabId)) { tab.setFavicon(favicon); return; }
        }
    }

    private void notifyListeners(Tab tab) {
        if (listener != null) {
            listener.onTabListChanged(new ArrayList<>(tabs));
            listener.onTabChanged(tab);
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
        if (activeTabId == null && !tabs.isEmpty()) activeTabId = tabs.get(0).getId();
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
        prefs.edit().putString(KEY_TABS, array.toString()).putString(KEY_ACTIVE_ID, activeTabId).apply();
    }
}

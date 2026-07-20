package com.gamecenter.app.browser.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 追踪拦截统计（P1-2）。
 * <p>记录拦截总数 / 按域名分布 / 按日期分布；持久化到 SharedPreferences。</p>
 */
public class BrowserTrackerStats {

    private static volatile BrowserTrackerStats instance;
    private static final String PREFS_NAME = "browser_tracker_stats";
    private static final String KEY_TOTAL_BLOCKED = "total_blocked";
    private static final String KEY_DOMAINS_PREFIX = "domain_";
    private static final String KEY_SESSION_BLOCKED = "session_blocked";

    private final SharedPreferences prefs;
    private int sessionBlocked = 0;

    private BrowserTrackerStats(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static BrowserTrackerStats getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (BrowserTrackerStats.class) {
                if (instance == null) instance = new BrowserTrackerStats(context);
            }
        }
        return instance;
    }

    /** 记录一次拦截，并更新域名统计 */
    public void recordBlock(@NonNull String url) {
        sessionBlocked++;
        prefs.edit()
                .putInt(KEY_TOTAL_BLOCKED, getTotalBlocked() + 1)
                .putInt(KEY_SESSION_BLOCKED, prefs.getInt(KEY_SESSION_BLOCKED, 0) + 1)
                .apply();
        String host = extractHost(url);
        if (!host.isEmpty()) {
            int cur = prefs.getInt(KEY_DOMAINS_PREFIX + host, 0);
            prefs.edit().putInt(KEY_DOMAINS_PREFIX + host, cur + 1).apply();
        }
    }

    public int getTotalBlocked() {
        return prefs.getInt(KEY_TOTAL_BLOCKED, 0);
    }

    public int getSessionBlocked() {
        return sessionBlocked;
    }

    /** 获取按拦截次数倒序排序的 Top N 域名 */
    @NonNull
    public List<Map.Entry<String, Integer>> getTopDomains(int topN) {
        Map<String, Integer> all = getAllDomains();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(all.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue(), a.getValue());
            }
        });
        if (list.size() > topN) return new ArrayList<>(list.subList(0, topN));
        return list;
    }

    @NonNull
    public Map<String, Integer> getAllDomains() {
        Map<String, Integer> map = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(KEY_DOMAINS_PREFIX)) {
                String host = key.substring(KEY_DOMAINS_PREFIX.length());
                Object v = entry.getValue();
                if (v instanceof Integer) map.put(host, (Integer) v);
            }
        }
        return map;
    }

    /** 清除所有统计 */
    public void reset() {
        sessionBlocked = 0;
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key != null && (key.startsWith(KEY_DOMAINS_PREFIX) || key.equals(KEY_TOTAL_BLOCKED) || key.equals(KEY_SESSION_BLOCKED))) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    @Nullable
    private String extractHost(@NonNull String url) {
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase(Locale.ROOT) : "";
        } catch (Throwable t) {
            return "";
        }
    }
}

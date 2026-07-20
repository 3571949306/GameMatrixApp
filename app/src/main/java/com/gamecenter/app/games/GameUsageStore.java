package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GameUsageStore {
    private static final String PREFS_NAME = "game_usage";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENT_IDS = "recent_ids";
    private static final String KEY_PLAY_COUNT_PREFIX = "play_count_";
    private static final String KEY_LAST_PLAYED_PREFIX = "last_played_";
    private static final String KEY_WIN_PREFIX = "win_count_";
    private static final String KEY_LOSS_PREFIX = "loss_count_";
    private static final String KEY_PLAY_TIME_PREFIX = "play_time_";
    private static final String KEY_DAILY_PLAY_TIME_PREFIX = "daily_play_time_";

    private final SharedPreferences prefs;

    public GameUsageStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void recordLaunch(String gameId) {
        long now = System.currentTimeMillis();
        prefs.edit()
                .putInt(KEY_PLAY_COUNT_PREFIX + gameId, getPlayCount(gameId) + 1)
                .putLong(KEY_LAST_PLAYED_PREFIX + gameId, now)
                .putString(KEY_RECENT_IDS, updateRecent(gameId))
                .apply();
    }

    public void recordWin(String gameId) {
        prefs.edit().putInt(KEY_WIN_PREFIX + gameId, prefs.getInt(KEY_WIN_PREFIX + gameId, 0) + 1).apply();
    }

    public void recordLoss(String gameId) {
        prefs.edit().putInt(KEY_LOSS_PREFIX + gameId, prefs.getInt(KEY_LOSS_PREFIX + gameId, 0) + 1).apply();
    }

    public void recordPlayTime(String gameId, long durationMs) {
        long totalMs = prefs.getLong(KEY_PLAY_TIME_PREFIX + gameId, 0L) + durationMs;
        // Batch 10-1 (HOME_QUICK_STATS_BAR): 同步累计今日时长（key 带日期后缀，每天独立）
        String dailyKey = KEY_DAILY_PLAY_TIME_PREFIX + todayDateKey();
        long dailyMs = prefs.getLong(dailyKey, 0L) + durationMs;
        prefs.edit()
                .putLong(KEY_PLAY_TIME_PREFIX + gameId, totalMs)
                .putLong(dailyKey, dailyMs)
                .apply();
    }

    public void recordScore(String gameId, int score) {
        int currentHigh = getHighScore(gameId);
        if (score > currentHigh) {
            prefs.edit().putInt("high_score_" + gameId, score).apply();
        }
    }

    public int getHighScore(String gameId) {
        return prefs.getInt("high_score_" + gameId, 0);
    }

    public long getTotalPlayTimeMs(String gameId) {
        return prefs.getLong(KEY_PLAY_TIME_PREFIX + gameId, 0L);
    }

    /**
     * Batch 10-1 (HOME_QUICK_STATS_BAR): 获取今日总游玩时长（毫秒）。
     * 内部使用 "daily_play_time_yyyy-MM-dd" 作为 key，每天独立累计。
     */
    public long getTodayPlayTimeMs() {
        return prefs.getLong(KEY_DAILY_PLAY_TIME_PREFIX + todayDateKey(), 0L);
    }

    /**
     * Batch 10-1 (HOME_QUICK_STATS_BAR): 获取所有游戏的累计总时长（毫秒）。
     */
    public long getAllTotalPlayTimeMs() {
        long total = 0L;
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(KEY_PLAY_TIME_PREFIX) && entry.getValue() instanceof Long) {
                total += (Long) entry.getValue();
            }
        }
        return total;
    }

    private String todayDateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public int getPlayCount(String gameId) {
        return prefs.getInt(KEY_PLAY_COUNT_PREFIX + gameId, 0);
    }

    public long getLastPlayedAt(String gameId) {
        return prefs.getLong(KEY_LAST_PLAYED_PREFIX + gameId, 0L);
    }

    public Set<String> getFavoriteIds() {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_FAVORITES, new LinkedHashSet<>()));
    }

    public boolean isFavorite(String gameId) {
        return getFavoriteIds().contains(gameId);
    }

    public void toggleFavorite(String gameId) {
        Set<String> favorites = getFavoriteIds();
        if (favorites.contains(gameId)) {
            favorites.remove(gameId);
        } else {
            favorites.add(gameId);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public int getWinCount(String gameId) {
        return prefs.getInt(KEY_WIN_PREFIX + gameId, 0);
    }

    public int getLossCount(String gameId) {
        return prefs.getInt(KEY_LOSS_PREFIX + gameId, 0);
    }

    public List<String> getRecentIds(int limit) {
        String stored = prefs.getString(KEY_RECENT_IDS, "");
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String id : stored.split(",")) {
            if (!id.isEmpty()) {
                result.add(id);
                if (result.size() >= limit) break;
            }
        }
        return result;
    }

    private String updateRecent(String gameId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(gameId);
        ids.addAll(getRecentIds(12));
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String id : ids) {
            if (count++ >= 12) break;
            if (builder.length() > 0) builder.append(',');
            builder.append(id);
        }
        return builder.toString();
    }
}

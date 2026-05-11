package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GameUsageStore {

    private static final String PREFS_NAME = "game_usage";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENT_IDS = "recent_ids";
    private static final String KEY_PLAY_COUNT_PREFIX = "play_count_";
    private static final String KEY_LAST_PLAYED_PREFIX = "last_played_";
    private static final String KEY_STATS_PREFIX = "stats_";
    private static final int MAX_RECENT_COUNT = 12;

    private final SharedPreferences prefs;

    public GameUsageStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void recordLaunch(String gameId) {
        int playCount = getPlayCount(gameId) + 1;
        List<String> recentIds = getRecentIds(MAX_RECENT_COUNT);
        recentIds.remove(gameId);
        recentIds.add(0, gameId);
        while (recentIds.size() > MAX_RECENT_COUNT) {
            recentIds.remove(recentIds.size() - 1);
        }

        prefs.edit()
                .putInt(KEY_PLAY_COUNT_PREFIX + gameId, playCount)
                .putLong(KEY_LAST_PLAYED_PREFIX + gameId, System.currentTimeMillis())
                .putString(KEY_RECENT_IDS, encodeIds(recentIds))
                .apply();
    }

    public boolean toggleFavorite(String gameId) {
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        boolean nowFavorite;
        if (favorites.contains(gameId)) {
            favorites.remove(gameId);
            nowFavorite = false;
        } else {
            favorites.add(gameId);
            nowFavorite = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
        return nowFavorite;
    }

    public boolean isFavorite(String gameId) {
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(gameId);
    }

    public int getPlayCount(String gameId) {
        return prefs.getInt(KEY_PLAY_COUNT_PREFIX + gameId, 0);
    }

    public long getLastPlayedAt(String gameId) {
        return prefs.getLong(KEY_LAST_PLAYED_PREFIX + gameId, 0L);
    }

    public List<String> getRecentIds(int limit) {
        List<String> ids = decodeIds(prefs.getString(KEY_RECENT_IDS, ""));
        if (ids.size() <= limit) {
            return ids;
        }
        return new ArrayList<>(ids.subList(0, limit));
    }

    public Set<String> getFavoriteIds() {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public void recordScore(String gameId, int score) {
        GameStats stats = getStats(gameId);
        if (score > stats.highScore) {
            stats.highScore = score;
            saveStats(gameId, stats);
        }
    }

    public void recordWin(String gameId) {
        GameStats stats = getStats(gameId);
        stats.totalWins++;
        stats.totalPlays++;
        stats.lastPlayedAt = System.currentTimeMillis();
        saveStats(gameId, stats);
    }

    public void recordLoss(String gameId) {
        GameStats stats = getStats(gameId);
        stats.totalLosses++;
        stats.totalPlays++;
        stats.lastPlayedAt = System.currentTimeMillis();
        saveStats(gameId, stats);
    }

    public void recordPlayTime(String gameId, long durationMs) {
        GameStats stats = getStats(gameId);
        stats.totalPlayTimeMs += durationMs;
        if (stats.bestTimeMs <= 0 || durationMs < stats.bestTimeMs) {
            stats.bestTimeMs = durationMs;
        }
        stats.lastPlayedAt = System.currentTimeMillis();
        saveStats(gameId, stats);
    }

    public GameStats getStats(String gameId) {
        String json = prefs.getString(KEY_STATS_PREFIX + gameId, null);
        if (json == null || json.isEmpty()) {
            return new GameStats(gameId);
        }
        try {
            return parseStatsJson(gameId, json);
        } catch (Exception e) {
            return new GameStats(gameId);
        }
    }

    public List<GameStats> getAllStats() {
        List<GameStats> allStats = new ArrayList<>();
        Set<String> allKeys = prefs.getAll().keySet();
        Set<String> processedGameIds = new HashSet<>();

        for (String key : allKeys) {
            if (key.startsWith(KEY_STATS_PREFIX) && !key.endsWith("_ids")) {
                String gameId = key.substring(KEY_STATS_PREFIX.length());
                if (!processedGameIds.contains(gameId)) {
                    processedGameIds.add(gameId);
                    GameStats stats = getStats(gameId);
                    if (stats.totalPlays > 0 || stats.highScore > 0 ||
                            stats.totalWins > 0 || stats.totalLosses > 0 ||
                            stats.totalPlayTimeMs > 0) {
                        allStats.add(stats);
                    }
                }
            }
        }

        return allStats;
    }

    public long getTotalPlayTimeMs() {
        long total = 0;
        for (GameStats stats : getAllStats()) {
            total += stats.totalPlayTimeMs;
        }
        return total;
    }

    public int getTotalWins() {
        int total = 0;
        for (GameStats stats : getAllStats()) {
            total += stats.totalWins;
        }
        return total;
    }

    public int getTotalPlays() {
        int total = 0;
        for (GameStats stats : getAllStats()) {
            total += stats.totalPlays;
        }
        return total;
    }

    private void saveStats(String gameId, GameStats stats) {
        prefs.edit().putString(KEY_STATS_PREFIX + gameId, statsToJson(stats)).apply();
    }

    private String statsToJson(GameStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"highScore\":").append(stats.highScore).append(",");
        sb.append("\"totalWins\":").append(stats.totalWins).append(",");
        sb.append("\"totalLosses\":").append(stats.totalLosses).append(",");
        sb.append("\"totalPlays\":").append(stats.totalPlays).append(",");
        sb.append("\"bestTimeMs\":").append(stats.bestTimeMs).append(",");
        sb.append("\"totalPlayTimeMs\":").append(stats.totalPlayTimeMs).append(",");
        sb.append("\"lastPlayedAt\":").append(stats.lastPlayedAt);
        sb.append("}");
        return sb.toString();
    }

    private GameStats parseStatsJson(String gameId, String json) {
        GameStats stats = new GameStats(gameId);
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim();
                    try {
                        switch (key) {
                            case "highScore": stats.highScore = Integer.parseInt(value); break;
                            case "totalWins": stats.totalWins = Integer.parseInt(value); break;
                            case "totalLosses": stats.totalLosses = Integer.parseInt(value); break;
                            case "totalPlays": stats.totalPlays = Integer.parseInt(value); break;
                            case "bestTimeMs": stats.bestTimeMs = Long.parseLong(value); break;
                            case "totalPlayTimeMs": stats.totalPlayTimeMs = Long.parseLong(value); break;
                            case "lastPlayedAt": stats.lastPlayedAt = Long.parseLong(value); break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return stats;
    }

    private static String encodeIds(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private static List<String> decodeIds(String raw) {
        List<String> ids = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String id = part.trim();
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}

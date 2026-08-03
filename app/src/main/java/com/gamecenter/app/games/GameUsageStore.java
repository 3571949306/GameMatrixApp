package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gamecenter.app.database.AppDatabase;
import com.gamecenter.app.database.dao.GameUsageDao;
import com.gamecenter.app.database.entity.GameUsageEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GameUsageStore {
    private static final String PREFS_NAME = "game_usage";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_FAVORITES_STR = "favorites_str";
    private static final String KEY_RECENT_IDS = "recent_ids";
    private static final String KEY_DAILY_PLAY_TIME_PREFIX = "daily_play_time_";

    private final SharedPreferences prefs;
    private final GameUsageDao gameUsageDao;

    public GameUsageStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gameUsageDao = AppDatabase.getDatabase(context.getApplicationContext()).gameUsageDao();
    }

    /**
     * 确保 Room 中存在指定 gameId 的行，便于后续 UPDATE 语句生效。
     * <p>Room 的 {@code incrementXxxSync} 等 UPDATE 语句在行不存在时不会创建行，
     * 因此所有 {@code record*} 方法开头需先调用本方法。</p>
     */
    private void ensureRowExists(String gameId) {
        if (gameUsageDao.getByIdSync(gameId) == null) {
            gameUsageDao.upsertSync(new GameUsageEntity(
                    gameId, 0, 0, 0, 0L, 0L, 0, false, 0L));
        }
    }

    public void recordLaunch(String gameId) {
        long now = System.currentTimeMillis();
        ensureRowExists(gameId);
        gameUsageDao.incrementPlayCountSync(gameId, now);
        // recent_ids 仍是逗号分隔字符串，保留在 SP
        prefs.edit()
                .putString(KEY_RECENT_IDS, updateRecent(gameId))
                .apply();
    }

    public void recordWin(String gameId) {
        ensureRowExists(gameId);
        gameUsageDao.incrementWinSync(gameId);
    }

    public void recordLoss(String gameId) {
        ensureRowExists(gameId);
        gameUsageDao.incrementLossSync(gameId);
    }

    public void recordPlayTime(String gameId, long durationMs) {
        ensureRowExists(gameId);
        gameUsageDao.addPlayTimeSync(gameId, durationMs);
        // Batch 10-1 (HOME_QUICK_STATS_BAR): 同步累计今日时长（key 带日期后缀，每天独立）
        // daily_play_time_* 带日期后缀，不适合放入简单 Entity，保留在 SP
        String dailyKey = KEY_DAILY_PLAY_TIME_PREFIX + todayDateKey();
        long dailyMs = prefs.getLong(dailyKey, 0L) + durationMs;
        prefs.edit()
                .putLong(dailyKey, dailyMs)
                .apply();
    }

    public void recordScore(String gameId, int score) {
        ensureRowExists(gameId);
        // updateHighScoreSync 仅在 highScore < score 时更新（SQL 内置条件）
        gameUsageDao.updateHighScoreSync(gameId, (long) score);
    }

    public int getHighScore(String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? (int) entity.getHighScore() : 0;
    }

    public long getTotalPlayTimeMs(String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? entity.getTotalPlayTimeMs() : 0L;
    }

    /**
     * Batch 10-1 (HOME_QUICK_STATS_BAR): 获取今日总游玩时长（毫秒）。
     * 内部使用 "daily_play_time_yyyy-MM-dd" 作为 key，每天独立累计。
     */
    public long getTodayPlayTimeMs() {
        return prefs.getLong(KEY_DAILY_PLAY_TIME_PREFIX + todayDateKey(), 0L);
    }

    /**
     * P2-8 (STATS_VISUALIZATION): 获取指定日期的累计游玩时长（毫秒）。
     * @param dateKey 形如 "yyyy-MM-dd" 的日期字符串
     */
    public long getDailyPlayTimeMs(@NonNull String dateKey) {
        return prefs.getLong(KEY_DAILY_PLAY_TIME_PREFIX + dateKey, 0L);
    }

    /**
     * P2-8: 返回最近 N 天（含今天）的日期 key 列表，下标 0 = N 天前，最后一位 = 今天。
     */
    @NonNull
    public List<String> getRecentDateKeys(int days) {
        List<String> keys = new ArrayList<>();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = days - 1; i >= 0; i--) {
            java.util.Calendar c = (java.util.Calendar) cal.clone();
            c.add(java.util.Calendar.DAY_OF_YEAR, -i);
            keys.add(sdf.format(c.getTime()));
        }
        return keys;
    }

    /**
     * Batch 10-1 (HOME_QUICK_STATS_BAR): 获取所有游戏的累计总时长（毫秒）。
     */
    public long getAllTotalPlayTimeMs() {
        return gameUsageDao.getTotalPlayTimeSync();
    }

    /**
     * BUG-005 修复：获取所有游戏的累计总对局数。
     * <p>由 Room {@code game_usage} 表 SUM(playCount) 求和，与 {@link #getPlayCount(String)}
     * 单游戏读取使用同一数据源，保证 GameDetailBottomSheet / ProfileFragment / StatsActivity 三处显示一致。</p>
     * <p>之前 ProfileFragment 与 StatsActivity 使用 {@code StreakTracker.totalGames}，
     * 但 {@code StreakTracker.recordGamePlayed()} 从未被调用，导致总对局永远为 0，
     * 与 GameDetailBottomSheet 的 {@code getPlayCount()} 不一致。</p>
     */
    public int getAllTotalPlayCount() {
        return gameUsageDao.getTotalPlayCountSync();
    }

    private String todayDateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public int getPlayCount(String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? entity.getPlayCount() : 0;
    }

    public long getLastPlayedAt(String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? entity.getLastPlayedAt() : 0L;
    }

    public Set<String> getFavoriteIds() {
        // BUG-008 根因修复3：SharedPreferences 的 StringSet 类型存在严重的跨实例缓存陷阱。
        // 即使使用 prefs.getAll().get(KEY_FAVORITES) 绕过 getStringSet，实测在 BottomSheet 中
        // 调用 toggleFavorite 后，切到个人中心 onResume 读取的 favorites 仍是旧值（空集→仍读到1个）。
        // 而同期的 Int/Long/String 类型（play_count、play_time、recent_ids）都能正确实时刷新。
        // 根因：SharedPreferencesImpl 对 StringSet 使用特殊的缓存对象引用，即使 remove+putStringSet
        // 新副本，getAll 返回的 map 中该 key 对应的 Set 引用仍可能指向旧对象。
        // 最终方案：彻底放弃 StringSet，改用逗号分隔的 String 存储 favorites（与 recent_ids 一致）。
        // 兼容：首次读取时迁移旧的 StringSet 数据到新 String 格式。
        String stored = prefs.getString(KEY_FAVORITES_STR, null);
        Log.d("GameUsageStore", "getFavoriteIds: favorites_str=" + stored + " prefs=" + System.identityHashCode(prefs));
        if (stored == null) {
            // 一次性迁移旧的 StringSet 数据
            Object legacy = prefs.getAll().get(KEY_FAVORITES);
            if (legacy instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> set = (Set<String>) legacy;
                StringBuilder sb = new StringBuilder();
                for (String id : set) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(id);
                }
                stored = sb.toString();
                prefs.edit().putString(KEY_FAVORITES_STR, stored).apply();
            } else {
                stored = "";
            }
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (stored.isEmpty()) return result;
        for (String id : stored.split(",")) {
            if (!id.isEmpty()) result.add(id);
        }
        return result;
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
        // BUG-008 修复3：改用 String 存储，彻底绕过 StringSet 缓存陷阱。
        // 与 recent_ids 的存储方式一致（逗号分隔字符串），已验证该方式可实时刷新。
        StringBuilder sb = new StringBuilder();
        for (String id : favorites) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        String newValue = sb.toString();
        prefs.edit().putString(KEY_FAVORITES_STR, newValue).apply();
        Log.d("GameUsageStore", "toggleFavorite: " + gameId + " newFavoritesStr=" + newValue + " prefs=" + System.identityHashCode(prefs));
    }

    public int getWinCount(String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? entity.getWins() : 0;
    }

    public int getLossCount(String gameId) {
        GameUsageEntity entity = gameUsageDao.getByIdSync(gameId);
        return entity != null ? entity.getLosses() : 0;
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

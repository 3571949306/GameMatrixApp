package com.gamecenter.app.games.achievement;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 连胜统计器（Feature A / ACHIEVEMENT_V2）。
 *
 * <p>记录两类连胜：</p>
 * <ul>
 *   <li><b>每日活跃连胜</b>：连续 N 天有玩过任意游戏（无中断）</li>
 *   <li><b>单游戏连胜</b>：按 gameId 维度记录连胜次数（任意时间窗）</li>
 * </ul>
 *
 * <p>同时记录累计对局总数，供成就中心顶部"连胜卡片"展示。</p>
 *
 * <p>所有状态写入 SharedPreferences，无数据库依赖。</p>
 */
public class StreakTracker {

    private static final String PREF_NAME = "streak_tracker";
    private static final String KEY_LAST_PLAY_DATE = "last_play_date";
    private static final String KEY_CURRENT_STREAK = "current_streak";
    private static final String KEY_BEST_STREAK = "best_streak";
    private static final String KEY_TOTAL_GAMES = "total_games";
    private static final String KEY_PREFIX_GAME_STREAK = "game_streak_";

    private static volatile StreakTracker instance;

    private final SharedPreferences prefs;

    private StreakTracker(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 获取单例。 */
    @NonNull
    public static StreakTracker getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (StreakTracker.class) {
                if (instance == null) {
                    instance = new StreakTracker(context);
                }
            }
        }
        return instance;
    }

    /** 今日日期字符串（yyyy-MM-dd）。 */
    @NonNull
    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /**
     * 记录一次游戏结束。应在每局游戏结束时调用。
     *
     * @param gameId 游戏 ID
     * @param won   本局是否获胜
     */
    public void recordGamePlayed(@NonNull String gameId, boolean won) {
        // 1. 累计总对局数
        int total = prefs.getInt(KEY_TOTAL_GAMES, 0) + 1;
        prefs.edit().putInt(KEY_TOTAL_GAMES, total).apply();

        // 2. 更新每日活跃连胜
        updateDailyStreak();

        // 3. 更新单游戏连胜
        String key = KEY_PREFIX_GAME_STREAK + gameId;
        int cur = prefs.getInt(key, 0);
        if (won) {
            prefs.edit().putInt(key, cur + 1).apply();
        } else {
            prefs.edit().putInt(key, 0).apply();
        }
    }

    /**
     * 仅记录"今日有活跃"（不增加对局数、不影响单游戏连胜）。
     *
     * <p>用于"用户进入游戏大厅时"或"用户点击启动游戏时"的活跃度统计，
     * 让每日活跃连胜可以反映真实使用情况，而无需等到一局完整对局结束。</p>
     */
    public void recordActivity() {
        updateDailyStreak();
    }

    /** 当前每日活跃连胜（天数）。 */
    public int getCurrentStreak() {
        return prefs.getInt(KEY_CURRENT_STREAK, 0);
    }

    /** 最佳每日活跃连胜（天数）。 */
    public int getBestStreak() {
        return prefs.getInt(KEY_BEST_STREAK, 0);
    }

    /**
     * 累计对局总数。
     * @deprecated 该值由 {@link #recordGamePlayed} 累加，但历史调用方已迁移至
     *             {@link com.gamecenter.app.games.GameUsageStore#getAllTotalPlayCount()}（Room 数据源），
     *             两套数据源可能不一致。新代码请使用 GameUsageStore。
     */
    @Deprecated
    public int getTotalGames() {
        return prefs.getInt(KEY_TOTAL_GAMES, 0);
    }

    /** 单游戏连胜次数。 */
    public int getGameStreak(@NonNull String gameId) {
        return prefs.getInt(KEY_PREFIX_GAME_STREAK + gameId, 0);
    }

    /** 重置单游戏连胜（游戏重启时调用）。 */
    public void resetGameStreak(@NonNull String gameId) {
        prefs.edit().remove(KEY_PREFIX_GAME_STREAK + gameId).apply();
    }

    /** 仅用于测试/调试：清空所有连胜数据。 */
    public void clearAll() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            editor.remove(key);
        }
        editor.apply();
    }

    // ==================== 内部 ====================

    private void updateDailyStreak() {
        String today = todayKey();
        String last = prefs.getString(KEY_LAST_PLAY_DATE, "");

        int current = prefs.getInt(KEY_CURRENT_STREAK, 0);
        int best = prefs.getInt(KEY_BEST_STREAK, 0);

        if (today.equals(last)) {
            // 今天已记录过，不重复增加
            return;
        }

        // 判断是否连续（昨天 → 今天）
        if (!last.isEmpty() && isYesterday(last, today)) {
            current += 1;
        } else {
            // 中断或首次，重置为 1
            current = 1;
        }

        if (current > best) {
            best = current;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LAST_PLAY_DATE, today);
        editor.putInt(KEY_CURRENT_STREAK, current);
        editor.putInt(KEY_BEST_STREAK, best);
        editor.apply();
    }

    /** 判断 lhs 是否是 rhs 的前一天。 */
    private boolean isYesterday(@NonNull String lhs, @NonNull String rhs) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d1 = fmt.parse(lhs);
            Date d2 = fmt.parse(rhs);
            if (d1 == null || d2 == null) return false;
            long diff = d2.getTime() - d1.getTime();
            long oneDay = 24L * 60 * 60 * 1000;
            return diff > 0 && diff <= oneDay;
        } catch (Exception e) {
            return false;
        }
    }
}

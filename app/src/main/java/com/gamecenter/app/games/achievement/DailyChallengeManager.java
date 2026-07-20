package com.gamecenter.app.games.achievement;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.games.GameRegistry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 每日挑战管理器（Feature A / ACHIEVEMENT_V2）。
 *
 * <p>每天根据日期种子选 1 个内置游戏 + 1 个挑战目标，
 * 供 {@link AchievementCenterActivity} 顶部"每日挑战卡片"展示与推进。</p>
 *
 * <p>挑战类型：</p>
 * <ul>
 *   <li>{@link #TYPE_PLAY_ROUNDS} —— 玩 N 局指定游戏</li>
 *   <li>{@link #TYPE_WIN_ROUNDS} —— 赢 N 局指定游戏</li>
 *   <li>{@link #TYPE_UNLOCK_ACHIEVEMENTS} —— 解锁 N 个新成就</li>
 * </ul>
 *
 * <p>所有状态写入 SharedPreferences（异步 apply），不依赖数据库，
 * 因此即使升级也不会丢失已完成的当日挑战记录。</p>
 */
public class DailyChallengeManager {

    /** 挑战类型：玩 N 局 */
    public static final int TYPE_PLAY_ROUNDS = 0;
    /** 挑战类型：赢 N 局 */
    public static final int TYPE_WIN_ROUNDS = 1;
    /** 挑战类型：解锁 N 个新成就 */
    public static final int TYPE_UNLOCK_ACHIEVEMENTS = 2;

    private static final String PREF_NAME = "daily_challenge";
    private static final String KEY_DATE = "challenge_date";
    private static final String KEY_GAME_ID = "challenge_game_id";
    private static final String KEY_GAME_NAME = "challenge_game_name";
    private static final String KEY_TYPE = "challenge_type";
    private static final String KEY_TARGET = "challenge_target";
    private static final String KEY_PROGRESS = "challenge_progress";
    private static final String KEY_COMPLETED = "challenge_completed";

    private static volatile DailyChallengeManager instance;

    private final SharedPreferences prefs;
    private final Context appContext;

    private DailyChallengeManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 获取单例。 */
    @NonNull
    public static DailyChallengeManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (DailyChallengeManager.class) {
                if (instance == null) {
                    instance = new DailyChallengeManager(context);
                }
            }
        }
        return instance;
    }

    /** 今日日期字符串（yyyy-MM-dd），用作挑战轮换的种子。 */
    @NonNull
    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /**
     * 获取今日挑战（若日期变化或尚未生成，则自动重新生成）。
     */
    @NonNull
    public Challenge getTodayChallenge() {
        String today = todayKey();
        String storedDate = prefs.getString(KEY_DATE, "");
        if (TextUtils.isEmpty(storedDate) || !storedDate.equals(today)) {
            generateNewChallenge(today);
        }
        return readChallengeFromPrefs();
    }

    /**
     * 推进"玩 N 局"挑战进度。返回更新后的进度。
     */
    public int recordGamePlayed(@Nullable String gameId, boolean won) {
        Challenge c = getTodayChallenge();
        if (c.completed) return c.progress;

        boolean match = false;
        switch (c.type) {
            case TYPE_PLAY_ROUNDS:
                match = c.gameId.equals(gameId);
                break;
            case TYPE_WIN_ROUNDS:
                match = c.gameId.equals(gameId) && won;
                break;
            default:
                break;
        }
        if (!match) return c.progress;

        int newProgress = Math.min(c.progress + 1, c.target);
        prefs.edit().putInt(KEY_PROGRESS, newProgress).apply();
        if (newProgress >= c.target) {
            prefs.edit().putBoolean(KEY_COMPLETED, true).apply();
        }
        return newProgress;
    }

    /**
     * 推进"解锁 N 个新成就"挑战进度。返回更新后的进度。
     */
    public int recordAchievementUnlocked() {
        Challenge c = getTodayChallenge();
        if (c.completed || c.type != TYPE_UNLOCK_ACHIEVEMENTS) return c.progress;

        int newProgress = Math.min(c.progress + 1, c.target);
        prefs.edit().putInt(KEY_PROGRESS, newProgress).apply();
        if (newProgress >= c.target) {
            prefs.edit().putBoolean(KEY_COMPLETED, true).apply();
        }
        return newProgress;
    }

    /** 重置今日挑战进度（仅供测试或"重新生成"使用）。 */
    public void resetToday() {
        prefs.edit().clear().apply();
        generateNewChallenge(todayKey());
    }

    // ==================== 内部 ====================

    private void generateNewChallenge(@NonNull String today) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.putString(KEY_DATE, today);

        // 用日期字符串哈希作为种子，保证同一天结果一致
        long seed = today.hashCode();
        Random rnd = new Random(seed);

        // 收集所有内置游戏
        List<GameRegistry.Entry> allGames = new java.util.ArrayList<>();
        List<GameRegistry.Category> cats = GameRegistry.getCategories(appContext);
        if (cats != null) {
            for (GameRegistry.Category cat : cats) {
                if (cat != null && cat.games != null) {
                    allGames.addAll(cat.games);
                }
            }
        }

        int type = rnd.nextInt(3); // 0/1/2 三种挑战类型

        if (type == TYPE_UNLOCK_ACHIEVEMENTS || allGames.isEmpty()) {
            // 选"解锁成就"挑战，不需要绑定具体游戏
            editor.putInt(KEY_TYPE, TYPE_UNLOCK_ACHIEVEMENTS);
            editor.putString(KEY_GAME_ID, "");
            editor.putString(KEY_GAME_NAME, "");
            editor.putInt(KEY_TARGET, 1 + rnd.nextInt(2)); // 1-2 个成就
        } else {
            GameRegistry.Entry chosen = allGames.get(rnd.nextInt(allGames.size()));
            editor.putInt(KEY_TYPE, type);
            editor.putString(KEY_GAME_ID, chosen.id);
            editor.putString(KEY_GAME_NAME, chosen.name);
            editor.putInt(KEY_TARGET, 1 + rnd.nextInt(3)); // 1-3 局
        }

        editor.putInt(KEY_PROGRESS, 0);
        editor.putBoolean(KEY_COMPLETED, false);
        editor.apply();
    }

    @NonNull
    private Challenge readChallengeFromPrefs() {
        Challenge c = new Challenge();
        c.date = prefs.getString(KEY_DATE, todayKey());
        c.gameId = prefs.getString(KEY_GAME_ID, "");
        c.gameName = prefs.getString(KEY_GAME_NAME, "");
        c.type = prefs.getInt(KEY_TYPE, TYPE_PLAY_ROUNDS);
        c.target = prefs.getInt(KEY_TARGET, 1);
        c.progress = prefs.getInt(KEY_PROGRESS, 0);
        c.completed = prefs.getBoolean(KEY_COMPLETED, false);
        return c;
    }

    /** 挑战数据快照。 */
    public static class Challenge {
        public String date;
        public String gameId;
        public String gameName;
        public int type;
        public int target;
        public int progress;
        public boolean completed;
    }
}

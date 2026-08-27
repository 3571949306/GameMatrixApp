package com.gamecenter.app.td;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

/**
 * 塔防「保卫蛋蛋」存档管理器。
 *
 * <p>持久化各关卡的最高星级与解锁进度，以及各难度的最佳战绩。
 * 存储走 ModuleScopedPreferences 模块作用域 SP（mod_td__td_save），历史扁平 td_save 数据自动迁移；
 * 键仍以 td_ 前缀命名，并使用 String 存储（见项目规范：避免 StringSet 跨实例缓存问题）。
 */
public class TdSaveManager {

    /** 本模块在 catalog 中的 id，用作数据隔离作用域前缀 */
    private static final String MODULE_ID = "td";
    private static final String PREFS = "td_save"; // 旧扁平名；同时作为作用域 SP 的 baseName
    private static final String KEY_STARS_PREFIX = "td_stars_";
    private static final String KEY_UNLOCKED = "td_unlocked_levels";
    private static final String KEY_KILLS_PREFIX = "td_kills_";
    private static final String KEY_BEST_TIME_PREFIX = "td_time_";
    private static final String KEY_PLAY_COUNT = "td_play_count";
    private static final String KEY_EASY_DONE = "td_easy_done";
    private static final String KEY_HARD_DONE = "td_hard_done";

    private final SharedPreferences prefs;

    public TdSaveManager(Context context) {
        Context appContext = context.getApplicationContext();
        // 数据隔离（Phase 3 强约束）：旧扁平 td_save 若有历史数据仅迁移一次到 mod_td__td_save，
        // 之后一律走带 moduleId 前缀的作用域 SP，禁止模块间以任意文件名互读。
        ModuleScopedPreferences.migrateFrom(appContext, MODULE_ID, PREFS);
        this.prefs = ModuleScopedPreferences.get(appContext, MODULE_ID, PREFS);
    }

    /** 解锁关卡数量（index 从 0 开始，level 1 恒解锁） */
    public int getUnlockedLevelCount() {
        return Math.max(1, prefs.getInt(KEY_UNLOCKED, 1));
    }

    /** 记录通关，解锁下一关 */
    public void recordWin(int levelIndex) {
        int unlocked = getUnlockedLevelCount();
        int nextUnlocked = levelIndex + 2;
        if (nextUnlocked > unlocked) {
            prefs.edit().putInt(KEY_UNLOCKED, nextUnlocked).apply();
        }
    }

    public void recordPlay() {
        prefs.edit().putInt(KEY_PLAY_COUNT, getPlayCount() + 1).apply();
    }

    public int getPlayCount() {
        return prefs.getInt(KEY_PLAY_COUNT, 0);
    }

    /** 获取某关最高星级（0=未通过） */
    public int getBestStars(int levelIndex) {
        return prefs.getInt(KEY_STARS_PREFIX + levelIndex, 0);
    }

    /** 设定某关最高星级（只增不减） */
    public void setBestStars(int levelIndex, int stars) {
        SharedPreferences.Editor e = prefs.edit();
        if (stars > getBestStars(levelIndex)) {
            e.putInt(KEY_STARS_PREFIX + levelIndex, stars);
        }
        e.apply();
    }

    /** 累计击杀数 */
    public int getTotalKills() {
        return prefs.getInt(KEY_KILLS_PREFIX + "total", 0);
    }

    public void addKills(int n) {
        prefs.edit().putInt(KEY_KILLS_PREFIX + "total", getTotalKills() + n).apply();
    }

    /** 某关最佳战绩秒数（0 表示未记录） */
    public int getBestTimeSec(int levelIndex) {
        return prefs.getInt(KEY_BEST_TIME_PREFIX + levelIndex, 0);
    }

    public void setBestTimeSec(int levelIndex, int sec) {
        int cur = getBestTimeSec(levelIndex);
        if (cur == 0 || sec < cur) {
            prefs.edit().putInt(KEY_BEST_TIME_PREFIX + levelIndex, sec).apply();
        }
    }

    /** 简单难度是否通关过（用于成就显示） */
    public boolean isEasyCleared() { return prefs.getBoolean(KEY_EASY_DONE, false); }
    public void setEasyCleared(boolean v) { prefs.edit().putBoolean(KEY_EASY_DONE, v).apply(); }

    /** 困难难度是否通关过 */
    public boolean isHardCleared() { return prefs.getBoolean(KEY_HARD_DONE, false); }
    public void setHardCleared(boolean v) { prefs.edit().putBoolean(KEY_HARD_DONE, v).apply(); }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}

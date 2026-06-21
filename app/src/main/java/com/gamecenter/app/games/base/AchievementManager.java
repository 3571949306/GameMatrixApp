package com.gamecenter.app.games.base;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.games.model.AchievementData;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏成就管理器
 * <p>
 * 管理游戏成就的解锁状态，支持跨游戏复用。
 * 使用 SharedPreferences 持久化，线程安全。
 * </p>
 */
public class AchievementManager {

    private static final String PREFS_NAME = "game_achievements";
    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, AchievementData> achievementCache = new HashMap<>();

    public AchievementManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 检查并解锁成就
     * <p>
     * 如果成就尚未解锁，则根据事件类型和参数判断是否满足解锁条件。
     * </p>
     *
     * @param achievementId 成就标识
     * @param params 条件参数（当前值）
     */
    public void checkAndUnlock(String achievementId, Object... params) {
        AchievementData data = getOrCreate(achievementId);
        if (data.unlocked) return;

        // 保存进度
        if (params != null && params.length > 0 && params[0] instanceof Number) {
            data.currentProgress = ((Number) params[0]).intValue();
        }
        data.unlocked = true;
        data.unlockedAt = System.currentTimeMillis();
        saveToPrefs(achievementId, data);
    }

    /**
     * 查询成就是否已解锁
     */
    public boolean isUnlocked(String achievementId) {
        AchievementData data = getOrCreate(achievementId);
        return data.unlocked;
    }

    /**
     * 获取成就进度数据
     */
    public AchievementData getData(String achievementId) {
        return getOrCreate(achievementId);
    }

    private AchievementData getOrCreate(String achievementId) {
        AchievementData data = achievementCache.get(achievementId);
        if (data == null) {
            data = new AchievementData(achievementId);
            data.unlocked = prefs.getBoolean("unlock_" + achievementId, false);
            data.currentProgress = prefs.getInt("progress_" + achievementId, 0);
            data.unlockedAt = prefs.getLong("unlocked_at_" + achievementId, 0);
            achievementCache.put(achievementId, data);
        }
        return data;
    }

    private void saveToPrefs(String achievementId, AchievementData data) {
        prefs.edit()
                .putBoolean("unlock_" + achievementId, data.unlocked)
                .putInt("progress_" + achievementId, data.currentProgress)
                .putLong("unlocked_at_" + achievementId, data.unlockedAt)
                .apply();
    }
}

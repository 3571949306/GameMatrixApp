package com.gamecenter.app.games.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.games.model.AchievementData;
import com.gamecenter.app.ui.AchievementToastView;

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

    private static final String TAG = "AchievementManager";
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

        // Batch 8-3 (ACHIEVEMENT_TOAST): 解锁成功后弹出顶部浮层
        if (BuildConfig.ACHIEVEMENT_TOAST) {
            showAchievementToast(achievementId);
        }
    }

    /**
     * Batch 8-3 (ACHIEVEMENT_TOAST): 在当前 Activity 顶部弹出成就解锁浮层。
     * 解析成就标题与描述（按 achievementId 取本地化字符串，找不到时回退到通用文案）。
     * 调用方需保证 context 来自 Activity，否则浮层不显示（不抛错）。
     */
    private void showAchievementToast(@NonNull String achievementId) {
        try {
            String title = context.getString(com.gamecenter.app.R.string.achievement_toast_title);
            // 描述：尝试用 achievementId 找对应字符串，找不到时回退到 achievementId 本身
            int descResId = context.getResources().getIdentifier(
                    "achievement_desc_" + achievementId,
                    "string",
                    context.getPackageName());
            String description = descResId != 0
                    ? context.getString(descResId)
                    : achievementId;
            AchievementToastView.show(context, title, description);
        } catch (Exception e) {
            Log.w(TAG, "成就浮层显示失败: " + achievementId, e);
        }
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

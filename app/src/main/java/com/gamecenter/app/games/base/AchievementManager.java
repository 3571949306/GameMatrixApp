package com.gamecenter.app.games.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * <p>
 * 2026-07-22 修复（GAME_REVAMP_2026）：
 * <ul>
 *   <li>成就键按 gameId 隔离，避免不同游戏共用 win/score/time 等泛型 id 串扰。</li>
 *   <li>引入阈值判定：仅当 currentValue &gt;= threshold 时才解锁，修正此前无条件解锁的 bug。</li>
 *   <li>保留旧签名以向后兼容：单参数 Number 仅记录进度不自动解锁；Boolean true 仍可解锁（兼容 win 事件）。</li>
 * </ul>
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
     * 检查并解锁成就（带 gameId 隔离与阈值判定，推荐用法）。
     * <p>
     * 当 currentValue &gt;= threshold 时解锁，并始终更新进度。
     * </p>
     *
     * @param gameId         游戏唯一标识，用于隔离不同游戏的同名成就
     * @param achievementId  成就标识
     * @param currentValue   当前进度值
     * @param threshold      解锁阈值
     * @return 是否本次新解锁
     */
    public boolean checkAndUnlock(@NonNull String gameId,
                                  @NonNull String achievementId,
                                  int currentValue,
                                  int threshold) {
        String compositeKey = composeKey(gameId, achievementId);
        AchievementData data = getOrCreate(compositeKey);
        if (data.unlocked) return false;

        // 更新进度（取较大值，避免回退）
        if (currentValue > data.currentProgress) {
            data.currentProgress = currentValue;
        }

        boolean shouldUnlock = currentValue >= threshold;
        if (shouldUnlock) {
            data.unlocked = true;
            data.unlockedAt = System.currentTimeMillis();
            saveToPrefs(compositeKey, data);
            showAchievementToastIfEnabled(achievementId);
        } else {
            saveToPrefs(compositeKey, data);
        }
        return shouldUnlock;
    }

    /**
     * 直接解锁成就（带 gameId 隔离，用于 boolean 型事件如 win/game_over）。
     *
     * @param gameId        游戏唯一标识
     * @param achievementId 成就标识
     * @return 是否本次新解锁
     */
    public boolean unlock(@NonNull String gameId, @NonNull String achievementId) {
        String compositeKey = composeKey(gameId, achievementId);
        AchievementData data = getOrCreate(compositeKey);
        if (data.unlocked) return false;
        data.unlocked = true;
        data.unlockedAt = System.currentTimeMillis();
        saveToPrefs(compositeKey, data);
        showAchievementToastIfEnabled(achievementId);
        return true;
    }

    /**
     * 检查并解锁成就（旧签名，向后兼容）。
     * <p>
     * 2026-07-22 修复：不再无条件解锁。行为如下：
     * <ul>
     *   <li>params[0] 为 Boolean 且为 true → 解锁（兼容 win 事件）。</li>
     *   <li>params.length &gt;= 2 且 params[0]/params[1] 均为 Number → 阈值判定（兼容旧 checkAchievement(eventType, current, threshold)）。</li>
     *   <li>params[0] 为单个 Number → 仅记录进度，不自动解锁（修正原 bug）。</li>
     *   <li>无 params → 仅查询，不解锁。</li>
     * </ul>
     * 推荐迁移到 {@link #checkAndUnlock(String, String, int, int)} 或 {@link #unlock(String, String)}。
     * </p>
     *
     * @param achievementId 成就标识（未隔离 gameId，存在跨游戏串扰风险，仅为兼容保留）
     * @param params 条件参数
     */
    @Deprecated
    public void checkAndUnlock(@NonNull String achievementId, @Nullable Object... params) {
        AchievementData data = getOrCreate(achievementId);
        if (data.unlocked) return;

        if (params == null || params.length == 0) {
            return; // 无参数，不解锁
        }

        Object first = params[0];
        if (first instanceof Boolean) {
            // Boolean 模式：true 时解锁
            if ((Boolean) first) {
                data.unlocked = true;
                data.unlockedAt = System.currentTimeMillis();
                saveToPrefs(achievementId, data);
                showAchievementToastIfEnabled(achievementId);
            }
            return;
        }

        if (first instanceof Number) {
            int currentValue = ((Number) first).intValue();
            if (currentValue > data.currentProgress) {
                data.currentProgress = currentValue;
            }
            // 双参数阈值判定模式
            if (params.length >= 2 && params[1] instanceof Number) {
                int threshold = ((Number) params[1]).intValue();
                if (currentValue >= threshold) {
                    data.unlocked = true;
                    data.unlockedAt = System.currentTimeMillis();
                    saveToPrefs(achievementId, data);
                    showAchievementToastIfEnabled(achievementId);
                    return;
                }
            }
            // 单参数 Number：仅记录进度，不自动解锁（修正原 bug）
            saveToPrefs(achievementId, data);
        }
    }

    /**
     * Batch 8-3 (ACHIEVEMENT_TOAST): 解锁成功后弹出顶部浮层。
     */
    private void showAchievementToastIfEnabled(@NonNull String achievementId) {
        if (BuildConfig.ACHIEVEMENT_TOAST && BuildConfig.GAME_REVAMP_2026) {
            showAchievementToast(achievementId);
        } else if (BuildConfig.ACHIEVEMENT_TOAST) {
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
     * 查询成就是否已解锁（带 gameId 隔离）
     */
    public boolean isUnlocked(@NonNull String gameId, @NonNull String achievementId) {
        return getOrCreate(composeKey(gameId, achievementId)).unlocked;
    }

    /**
     * 查询成就是否已解锁（旧签名，向后兼容）
     */
    public boolean isUnlocked(@NonNull String achievementId) {
        return getOrCreate(achievementId).unlocked;
    }

    /**
     * 获取成就进度数据（带 gameId 隔离）
     */
    public AchievementData getData(@NonNull String gameId, @NonNull String achievementId) {
        return getOrCreate(composeKey(gameId, achievementId));
    }

    /**
     * 获取成就进度数据（旧签名，向后兼容）
     */
    public AchievementData getData(@NonNull String achievementId) {
        return getOrCreate(achievementId);
    }

    /** 拼接 gameId + achievementId 作为隔离键 */
    private static String composeKey(@NonNull String gameId, @NonNull String achievementId) {
        return gameId + "_" + achievementId;
    }

    private AchievementData getOrCreate(String key) {
        AchievementData data = achievementCache.get(key);
        if (data == null) {
            data = new AchievementData(key);
            data.unlocked = prefs.getBoolean("unlock_" + key, false);
            data.currentProgress = prefs.getInt("progress_" + key, 0);
            data.unlockedAt = prefs.getLong("unlocked_at_" + key, 0);
            achievementCache.put(key, data);
        }
        return data;
    }

    private void saveToPrefs(String key, AchievementData data) {
        prefs.edit()
                .putBoolean("unlock_" + key, data.unlocked)
                .putInt("progress_" + key, data.currentProgress)
                .putLong("unlocked_at_" + key, data.unlockedAt)
                .apply();
    }
}

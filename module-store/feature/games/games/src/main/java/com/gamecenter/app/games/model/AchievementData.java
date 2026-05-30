package com.gamecenter.app.games.model;

import com.gamecenter.app.games.model.enums.AchievementLevel;

/**
 * 成就进度/解锁数据
 * <p>
 * 存储某个成就的当前解锁状态和进度。
 * </p>
 */
public class AchievementData {

    /** 成就标识 */
    public String achievementId;

    /** 成就显示名称 */
    public String name;

    /** 是否已解锁 */
    public boolean unlocked = false;

    /** 当前进度值 */
    public int currentProgress = 0;

    /** 解锁时间戳（毫秒） */
    public long unlockedAt = 0;

    /** 成就等级 */
    public AchievementLevel level;

    public AchievementData() {}

    public AchievementData(String achievementId) {
        this.achievementId = achievementId;
    }
}

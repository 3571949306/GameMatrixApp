package com.gamecenter.app.games.model;

import com.gamecenter.app.games.model.enums.AchievementLevel;
import com.gamecenter.app.games.model.enums.ConditionType;

import java.util.ArrayList;
import java.util.List;

/**
 * 成就定义模型
 * <p>
 * 描述一个成就的完整定义，包括名称、等级、条件和奖励。
 * </p>
 */
public class AchievementDef {

    /** 成就唯一标识（短名，如 "first_score"） */
    public String id;

    /** 成就短键（与 id 相同，供 config 模式使用） */
    public String key;

    /** 成就显示名称 */
    public String name;

    /** 成就描述 */
    public String description;

    /** 成就等级 */
    public AchievementLevel level;

    /** 触发条件类型 */
    public ConditionType conditionType;

    /** 触发阈值 */
    public int threshold;

    /** 成就图标资源名 */
    public String icon;

    /** 是否为隐藏成就 */
    public boolean hidden;

    public AchievementDef() {
        this.key = null;
    }

    public AchievementDef(String id, String name, String description,
                          AchievementLevel level, ConditionType conditionType,
                          int threshold) {
        this.id = id;
        this.key = id;
        this.name = name;
        this.description = description;
        this.level = level;
        this.conditionType = conditionType;
        this.threshold = threshold;
        this.hidden = false;
    }

    /**
     * 返回带游戏前缀的完整成就标识
     * @param gameId 游戏唯一标识
     * @return gameId + "_" + id
     */
    public String getFullId(String gameId) {
        return gameId + "_" + (id != null ? id : key);
    }
}

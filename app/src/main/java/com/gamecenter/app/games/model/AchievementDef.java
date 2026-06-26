package com.gamecenter.app.games.model;

import com.gamecenter.app.games.model.enums.AchievementLevel;

/**
 * 成就定义（数据载体）。
 *
 * <p>P0 修复：从原 :module-store:feature:games:games 模块迁回。</p>
 */
public class AchievementDef {

    @SuppressWarnings("unused")
    public String key;
    @SuppressWarnings("unused")
    public AchievementLevel level;

    /**
     * 拼接 gameId + key 作为 SharedPreferences 存储键。
     */
    @SuppressWarnings("unused")
    public String getFullId(String gameId) {
        return gameId + "_" + (key == null ? "" : key);
    }
}
package com.gamecenter.app.games.model;

import java.util.List;

/**
 * 单个游戏的成就配置（数据载体）。
 *
 * <p>P0 修复：从原 :module-store:feature:games:games 模块迁回。
 * 当前为空数据类，GameConfigLoader 不会填充字段。</p>
 */
public class GameConfig {

    @SuppressWarnings("unused")
    public String gameId;
    @SuppressWarnings("unused")
    public String name;
    @SuppressWarnings("unused")
    public int iconResId;
    @SuppressWarnings("unused")
    public List<AchievementDef> achievements;
}
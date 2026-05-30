package com.gamecenter.app.games.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏配置模型
 * <p>
 * 描述一个游戏的完整配置，包括基本信息、成就列表和难度等级。
 * </p>
 */
public class GameConfig {

    /** 游戏唯一标识 */
    public String gameId;

    /** 游戏显示名称（配置模式下也用 name 引用） */
    public String gameName;

    /** 游戏显示名称别名（与 gameName 相同） */
    public String name;

    /** 游戏图标资源ID */
    public int iconResId;

    /** 游戏描述 */
    public String description;

    /** 游戏分类 */
    public String category;

    /** 该游戏中可用的成就定义列表 */
    public List<AchievementDef> achievements = new ArrayList<>();

    /** 游戏难度等级列表 */
    public List<DifficultyLevel> difficultyLevels = new ArrayList<>();

    public GameConfig() {}

    public GameConfig(String gameId, String gameName) {
        this.gameId = gameId;
        this.gameName = gameName;
        this.name = gameName;
    }
}

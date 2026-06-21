package com.gamecenter.app.games.model;

/**
 * 游戏难度等级模型
 * <p>
 * 描述一个游戏难度级别的配置参数。
 * 不同游戏可以使用不同的字段（如速度、AI搜索深度等）。
 * </p>
 */
public class DifficultyLevel {

    /** 难度名称（如"简单"、"普通"、"困难"） */
    public final String name;

    /** 难度等级数值（1=最简单，数值越大越难） */
    public final int level;

    /** 难度描述文字 */
    public final String description;

    /** AI搜索深度（棋类游戏使用，0表示不使用AI） */
    public final int aiSearchDepth;

    /** AI搜索预算毫秒（棋类游戏使用，0表示不限时） */
    public final long aiBudgetMs;

    /** 通用难度因子（用于调节速度、频率等，默认1.0） */
    public final float difficultyFactor;

    /** 是否为推荐难度 */
    public final boolean recommended;

    public DifficultyLevel(String name, int level, String description,
                           int aiSearchDepth, long aiBudgetMs,
                           float difficultyFactor, boolean recommended) {
        this.name = name;
        this.level = level;
        this.description = description;
        this.aiSearchDepth = aiSearchDepth;
        this.aiBudgetMs = aiBudgetMs;
        this.difficultyFactor = difficultyFactor;
        this.recommended = recommended;
    }

    /**
     * 简化构造函数（用于非 AI 游戏）
     */
    public DifficultyLevel(String name, int level, String description, boolean recommended) {
        this(name, level, description, 0, 0, 1.0f, recommended);
    }
}

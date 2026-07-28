package com.gamecenter.app.games.model.enums;

/**
 * 成就等级枚举。
 *
 * <p>P0 修复：从原 :module-store:feature:games:games 模块迁回。</p>
 *
 * <p>P1-5 (ACHIEVEMENT_POINTS)：每个等级关联固定点数：
 * <ul>
 *   <li>{@link #BRONZE} 铜 = 10 点</li>
 *   <li>{@link #SILVER} 银 = 30 点</li>
 *   <li>{@link #GOLD}   金 = 100 点</li>
 *   <li>{@link #PLATINUM} 铂金 = 300 点</li>
 * </ul>
 * </p>
 */
public enum AchievementLevel {
    BRONZE("#CD7F32", 10),
    SILVER("#C0C0C0", 30),
    GOLD("#FFD700", 100),
    PLATINUM("#E5E4E2", 300);

    private final String colorHex;
    private final int points;

    AchievementLevel(String colorHex, int points) {
        this.colorHex = colorHex;
        this.points = points;
    }

    public String getColorHex() {
        return colorHex;
    }

    /** 该等级解锁后获得的标准点数。 */
    public int getPoints() {
        return points;
    }
}

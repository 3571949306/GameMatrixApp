package com.gamecenter.app.games.model.enums;

/**
 * 成就等级枚举
 */
public enum AchievementLevel {
    /** 铜牌成就 */
    BRONZE("铜", 1, "#CD7F32"),
    /** 银牌成就 */
    SILVER("银", 2, "#C0C0C0"),
    /** 金牌成就 */
    GOLD("金", 3, "#FFD700"),
    /** 彩蛋成就 */
    EASTER_EGG("彩蛋", 4, "#FF69B4");

    private final String displayName;
    private final int priority;
    private final String colorHex;

    AchievementLevel(String displayName, int priority, String colorHex) {
        this.displayName = displayName;
        this.priority = priority;
        this.colorHex = colorHex;
    }

    public String getDisplayName() { return displayName; }
    public int getPriority() { return priority; }
    public String getColorHex() { return colorHex; }
}

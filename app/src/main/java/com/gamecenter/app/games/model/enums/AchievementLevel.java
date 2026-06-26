package com.gamecenter.app.games.model.enums;

/**
 * 成就等级枚举。
 *
 * <p>P0 修复：从原 :module-store:feature:games:games 模块迁回。</p>
 */
public enum AchievementLevel {
    BRONZE("#CD7F32"),
    SILVER("#C0C0C0"),
    GOLD("#FFD700"),
    PLATINUM("#E5E4E2");

    private final String colorHex;

    AchievementLevel(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getColorHex() {
        return colorHex;
    }
}
package com.gamecenter.app.minesweeper;

/**
 * 扫雷游戏配置与状态类（独立 APK 模块版本）。
 *
 * <p>持有难度配置与分数/胜场统计，供 Fragment 跨局追踪状态。
 * 实际游戏逻辑（地雷布局、翻开、旗帜）由 {@link MinesweeperView} 自包含处理。</p>
 */
public class MinesweeperGame {

    /** 难度等级常量 */
    public static final int DIFF_EASY = MinesweeperView.DIFF_EASY;
    public static final int DIFF_NORMAL = MinesweeperView.DIFF_NORMAL;
    public static final int DIFF_HARD = MinesweeperView.DIFF_HARD;

    /** 难度名称 */
    public static final String NAME_EASY = "简单";
    public static final String NAME_NORMAL = "普通";
    public static final String NAME_HARD = "困难";

    private int difficulty = DIFF_EASY;
    private int wins = 0;
    private int losses = 0;
    private int score = 0;

    public int getDifficulty() { return difficulty; }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getScore() { return score; }

    /** 胜利时调用，记录胜场与得分 */
    public void recordWin(int addScore) {
        wins++;
        score += addScore;
    }

    /** 失败时调用，记录败场 */
    public void recordLoss() {
        losses++;
    }

    /** 获取难度对应名称 */
    public static String getDifficultyName(int level) {
        switch (level) {
            case DIFF_EASY:   return NAME_EASY;
            case DIFF_NORMAL: return NAME_NORMAL;
            case DIFF_HARD:   return NAME_HARD;
            default:          return NAME_EASY;
        }
    }
}

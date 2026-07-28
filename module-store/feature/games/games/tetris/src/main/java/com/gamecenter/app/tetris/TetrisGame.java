package com.gamecenter.app.tetris;

/**
 * 俄罗斯方块游戏状态管理（从 TetrisActivity 提取）。
 *
 * <p>负责难度（下落速度因子）、分数/消行/等级追踪与最高分记录，
 * 不包含成就系统与音效（模块化后仅保留基本游戏功能）。</p>
 */
public class TetrisGame {

    /** 难度对应的下落速度因子：简单=0.3f，普通=0.5f，困难=0.8f */
    private static final float[] SPEED_FACTORS = {0.3f, 0.5f, 0.8f};

    /** 难度名称 */
    public static final String[] DIFFICULTY_NAMES = {"简单", "普通", "困难"};

    private int difficultyLevel = 2; // 1=简单, 2=普通, 3=困难
    private float speedFactor = SPEED_FACTORS[1];
    private int score = 0;
    private int lines = 0;
    private int level = 1;
    private int highScore = 0;

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int level) {
        if (level >= 1 && level <= SPEED_FACTORS.length) {
            difficultyLevel = level;
            speedFactor = SPEED_FACTORS[level - 1];
        }
    }

    public float getSpeedFactor() {
        return speedFactor;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getLines() {
        return lines;
    }

    public void setLines(int lines) {
        this.lines = lines;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getHighScore() {
        return highScore;
    }

    public void setHighScore(int highScore) {
        this.highScore = highScore;
    }

    /**
     * 重置一局游戏的计数状态（难度与最高分保留）。
     */
    public void resetRound() {
        score = 0;
        lines = 0;
        level = 1;
    }
}

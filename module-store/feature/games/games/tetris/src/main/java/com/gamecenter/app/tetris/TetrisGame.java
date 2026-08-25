package com.gamecenter.app.tetris;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 俄罗斯方块游戏状态管理（TetrisModuleFragment 侧的统一状态对象）。
 *
 * <p>负责难度（下落速度因子）、分数/消行/等级追踪与最高分记录，
 * 同时保存 Hold / Next 队列等可观察状态供 UI 展示。</p>
 */
public class TetrisGame {

    /** 难度对应的下落速度因子：1=慢 / 2=标准 / 3=快 / 4=极快 */
    private static final float[] SPEED_FACTORS = {0f, 1.5f, 1.0f, 0.65f, 0.45f};

    /** 难度名称 */
    public static final String[] DIFFICULTY_NAMES = {"简单", "普通", "困难", "大师"};

    private int difficultyLevel = 2; // 1=简单, 2=普通, 3=困难, 4=大师
    private float speedFactor = SPEED_FACTORS[2];
    private int score = 0;
    private int lines = 0;
    private int level = 1;
    private int highScore = 0;
    private int combo = 0;
    private boolean backToBack = false;
    private int holdPiece = -1;
    private final Deque<Integer> nextQueue = new ArrayDeque<>();

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int level) {
        if (level >= 1 && level <= SPEED_FACTORS.length - 1) {
            difficultyLevel = level;
            speedFactor = SPEED_FACTORS[level];
        }
    }

    public float getSpeedFactor() {
        return speedFactor;
    }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getLines() { return lines; }
    public void setLines(int lines) { this.lines = lines; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getHighScore() { return highScore; }
    public void setHighScore(int highScore) { this.highScore = highScore; }

    public int getCombo() { return combo; }
    public void setCombo(int combo) { this.combo = combo; }

    public boolean isBackToBack() { return backToBack; }
    public void setBackToBack(boolean backToBack) { this.backToBack = backToBack; }

    public int getHoldPiece() { return holdPiece; }
    public void setHoldPiece(int p) { this.holdPiece = p; }

    public Deque<Integer> getNextQueue() { return new ArrayDeque<>(nextQueue); }
    public void setNextQueue(Deque<Integer> q) {
        nextQueue.clear();
        if (q != null) nextQueue.addAll(q);
    }

    /**
     * 重置一局游戏的计数状态（难度与最高分保留）。
     */
    public void resetRound() {
        score = 0;
        lines = 0;
        level = 1;
        combo = 0;
        backToBack = false;
        holdPiece = -1;
        nextQueue.clear();
    }
}

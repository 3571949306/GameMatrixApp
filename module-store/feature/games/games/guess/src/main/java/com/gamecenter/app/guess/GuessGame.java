package com.gamecenter.app.guess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 猜数字核心算法与状态引擎。
 *
 * <p>特性：
 * <ul>
 *   <li>三级难度分级 (简单 0-100 / 中等 0-500 / 困难 0-1000)；</li>
 *   <li>动态二分区间收敛跟踪 [currentLow, currentHigh]；</li>
 *   <li>数学最优二分中位数提示 (Binary Search Advice)；</li>
 *   <li>历史猜测记录与撤销 (Undo) 栈；</li>
 *   <li>根据尝试次数与难度的动态计分机制。</li>
 * </ul>
 * </p>
 */
public class GuessGame {

    public static final int DIFFICULTY_EASY = 0;   // 0 - 100
    public static final int DIFFICULTY_MEDIUM = 1; // 0 - 500
    public static final int DIFFICULTY_HARD = 2;   // 0 - 1000

    public static final int[] MAX_RANGES = {100, 500, 1000};

    public enum GuessResult {
        TOO_SMALL,
        TOO_BIG,
        CORRECT,
        OUT_OF_BOUNDS
    }

    public static class GuessRecord {
        public final int guess;
        public final GuessResult result;
        public final int lowBefore;
        public final int highBefore;

        public GuessRecord(int guess, GuessResult result, int lowBefore, int highBefore) {
            this.guess = guess;
            this.result = result;
            this.lowBefore = lowBefore;
            this.highBefore = highBefore;
        }
    }

    private int difficulty = DIFFICULTY_EASY;
    private int currentMax = 100;
    private int targetNumber = 0;
    private int currentLow = 0;
    private int currentHigh = 100;

    private boolean gameActive = false;
    private boolean isWin = false;
    private int score = 0;
    private int highScore = 0;
    private int totalGames = 0;
    private int winStreak = 0;
    private int maxStreak = 0;

    private final List<GuessRecord> history = new ArrayList<>();
    private Random random = new Random();

    public GuessGame() {
        startNewRound();
    }

    public void setDifficulty(int diff) {
        if (diff < 0 || diff >= MAX_RANGES.length) diff = DIFFICULTY_EASY;
        this.difficulty = diff;
        this.currentMax = MAX_RANGES[diff];
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getCurrentMax() {
        return currentMax;
    }

    public void startNewRound() {
        targetNumber = random.nextInt(currentMax + 1);
        currentLow = 0;
        currentHigh = currentMax;
        gameActive = true;
        isWin = false;
        history.clear();
    }

    public GuessResult makeGuess(int guess) {
        if (!gameActive) return GuessResult.OUT_OF_BOUNDS;
        if (guess < 0 || guess > currentMax) return GuessResult.OUT_OF_BOUNDS;

        int lowBefore = currentLow;
        int highBefore = currentHigh;
        GuessResult res;

        if (guess == targetNumber) {
            res = GuessResult.CORRECT;
            gameActive = false;
            isWin = true;
            totalGames++;
            winStreak++;
            if (winStreak > maxStreak) maxStreak = winStreak;

            // 计分公式：难度系数 * (基础分 - 尝试递减)
            int attempts = history.size() + 1;
            int baseScore = Math.max(120 - attempts * 10, 20);
            int multiplier = (difficulty == DIFFICULTY_HARD) ? 3 : (difficulty == DIFFICULTY_MEDIUM ? 2 : 1);
            score = baseScore * multiplier;
            if (score > highScore) highScore = score;

            currentLow = guess;
            currentHigh = guess;
        } else if (guess < targetNumber) {
            res = GuessResult.TOO_SMALL;
            if (guess >= currentLow) {
                currentLow = guess + 1;
            }
        } else {
            res = GuessResult.TOO_BIG;
            if (guess <= currentHigh) {
                currentHigh = guess - 1;
            }
        }

        history.add(new GuessRecord(guess, res, lowBefore, highBefore));
        return res;
    }

    public boolean undoLastGuess() {
        if (!history.isEmpty()) {
            GuessRecord last = history.remove(history.size() - 1);
            currentLow = last.lowBefore;
            currentHigh = last.highBefore;
            gameActive = true;
            isWin = false;
            return true;
        }
        return false;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    /**
     * 获取数学最优二分中位数决策建议
     */
    public int getOptimalBinaryMid() {
        return (currentLow + currentHigh) / 2;
    }

    public String getBinarySearchHint() {
        if (!gameActive) return "对局已结束，请开启新局";
        int mid = getOptimalBinaryMid();
        int remaining = currentHigh - currentLow + 1;
        return String.format("当前可能区间: [%d ~ %d] (余 %d 个数字)\n💡 理论最优决策推荐: %d (二分折半)",
                currentLow, currentHigh, remaining, mid);
    }

    // Getters
    public int getTargetNumber() { return targetNumber; }
    public int getCurrentLow() { return currentLow; }
    public int getCurrentHigh() { return currentHigh; }
    public int getAttempts() { return history.size(); }
    public boolean isGameActive() { return gameActive; }
    public boolean isWin() { return isWin; }
    public int getScore() { return score; }
    public int getHighScore() { return highScore; }
    public void setHighScore(int hs) { this.highScore = hs; }
    public int getWinStreak() { return winStreak; }
    public int getMaxStreak() { return maxStreak; }
    public List<GuessRecord> getHistory() { return new ArrayList<>(history); }

    // Test helper
    void setTargetNumberForTest(int target) {
        this.targetNumber = target;
        this.currentLow = 0;
        this.currentHigh = currentMax;
        this.gameActive = true;
        this.isWin = false;
        this.history.clear();
    }
}

package com.gamecenter.app.games.guess;

import java.util.Random;

public class GuessGame {

    public static final int EASY = 0;
    public static final int MEDIUM = 1;
    public static final int HARD = 2;

    private static final int[] MAX_RANGES = {50, 100, 500};
    private static final String[] DIFF_NAMES = {"简单(1-50)", "中等(1-100)", "困难(1-500)"};

    private int difficulty;
    private int targetNumber;
    private int minRange;
    private int maxRange;
    private int attempts;
    private int lastGuess;
    private String lastHint;
    private boolean gameOver;
    private Random random;
    private int bestScore;

    public GuessGame() {
        random = new Random();
        difficulty = MEDIUM;
        minRange = 1;
        maxRange = MAX_RANGES[difficulty];
        bestScore = 0;
        reset();
    }

    public void setDifficulty(int diff) {
        difficulty = Math.max(0, Math.min(diff, HARD));
        maxRange = MAX_RANGES[difficulty];
        reset();
    }

    public int getDifficulty() { return difficulty; }
    public String getDifficultyName() { return DIFF_NAMES[difficulty]; }

    public void reset() {
        targetNumber = random.nextInt(maxRange - minRange + 1) + minRange;
        attempts = 0;
        lastGuess = -1;
        lastHint = "";
        gameOver = false;
    }

    public String makeGuess(int guess) {
        if (gameOver) return lastHint;
        attempts++;
        lastGuess = guess;

        if (guess < 1 || guess > maxRange) {
            lastHint = "请输入1-" + maxRange + "之间的数字";
            attempts--;
            return lastHint;
        }

        int diff = Math.abs(guess - targetNumber);
        if (guess < targetNumber) {
            if (diff <= 5) lastHint = "稍微小了一点!";
            else if (diff <= maxRange / 5) lastHint = "太小了!";
            else lastHint = "太小了...差很多!";
        } else if (guess > targetNumber) {
            if (diff <= 5) lastHint = "稍微大了一点!";
            else if (diff <= maxRange / 5) lastHint = "太大了!";
            else lastHint = "太大了...差很多!";
        } else {
            lastHint = "🎉 猜对了! 共猜" + attempts + "次";
            gameOver = true;
            if (bestScore == 0 || attempts < bestScore) bestScore = attempts;
        }
        return lastHint;
    }

    public int getTargetNumber() { return targetNumber; }
    public int getAttempts() { return attempts; }
    public int getLastGuess() { return lastGuess; }
    public String getLastHint() { return lastHint; }
    public boolean isGameOver() { return gameOver; }
    public int getMinRange() { return minRange; }
    public int getMaxRange() { return maxRange; }
    public int getBestScore() { return bestScore; }
}

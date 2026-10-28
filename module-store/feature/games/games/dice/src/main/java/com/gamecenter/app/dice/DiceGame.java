package com.gamecenter.app.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 骰子对决核心逻辑引擎，支持多档难度（骰子数与特殊番型）、概率论决策建议与加倍博弈机制。
 */
public class DiceGame {

    public static final int DIFFICULTY_EASY = 0;   // 简单：双骰对决 (2颗骰子)
    public static final int DIFFICULTY_MEDIUM = 1; // 中等：三骰对决 (3颗骰子)
    public static final int DIFFICULTY_HARD = 2;   // 困难：狂暴三骰 + 豹子/顺子 3倍暴击

    public static final String[] DICE_FACES = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};

    public enum RoundResult {
        ONGOING,
        PLAYER_WIN,
        AI_WIN,
        DRAW
    }

    private int difficulty = DIFFICULTY_EASY;
    private int diceCount = 2;

    private int[] playerDice = new int[]{1, 1};
    private int[] aiDice = new int[]{1, 1};
    private boolean isDoubleUp = false;
    private boolean roundActive = false;
    private RoundResult lastResult = RoundResult.ONGOING;

    private int totalRounds = 0;
    private int playerWins = 0;
    private int aiWins = 0;
    private int draws = 0;
    private int winStreak = 0;
    private int maxStreak = 0;
    private int currentScore = 0;
    private int highScore = 0;

    private Random random = new Random();

    // 撤销历史
    private final List<int[]> playerHistory = new ArrayList<>();
    private final List<int[]> aiHistory = new ArrayList<>();

    public DiceGame() {
        setDifficulty(DIFFICULTY_EASY);
    }

    public void setDifficulty(int diff) {
        this.difficulty = diff;
        this.diceCount = (diff == DIFFICULTY_EASY) ? 2 : 3;
        this.playerDice = new int[diceCount];
        this.aiDice = new int[diceCount];
        for (int i = 0; i < diceCount; i++) {
            playerDice[i] = 1;
            aiDice[i] = 1;
        }
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getDiceCount() {
        return diceCount;
    }

    public void roll() {
        playerHistory.add(playerDice.clone());
        aiHistory.add(aiDice.clone());

        for (int i = 0; i < diceCount; i++) {
            playerDice[i] = random.nextInt(6) + 1;
            aiDice[i] = random.nextInt(6) + 1;
        }

        totalRounds++;
        roundActive = true;
        isDoubleUp = false;
        lastResult = RoundResult.ONGOING;
    }

    public void setDoubleUp(boolean doubleUp) {
        if (!roundActive) return;
        this.isDoubleUp = doubleUp;
    }

    public boolean isDoubleUp() {
        return isDoubleUp;
    }

    public RoundResult resolveRound() {
        if (!roundActive) return lastResult;
        roundActive = false;

        int playerTotal = getPlayerTotal();
        int aiTotal = getAiTotal();
        int multiplier = isDoubleUp ? 2 : 1;

        // 特殊番型加成 (豹子: 所有骰子点数一致)
        if (difficulty == DIFFICULTY_HARD && isLeopard(playerDice)) {
            multiplier *= 3; // 豹子 3倍暴击！
        }

        if (playerTotal > aiTotal) {
            lastResult = RoundResult.PLAYER_WIN;
            playerWins++;
            winStreak++;
            if (winStreak > maxStreak) maxStreak = winStreak;
            currentScore += playerTotal * multiplier;
        } else if (playerTotal < aiTotal) {
            lastResult = RoundResult.AI_WIN;
            aiWins++;
            winStreak = 0;
            if (difficulty == DIFFICULTY_HARD && isDoubleUp) {
                // 困难模式加倍失败扣分
                currentScore = Math.max(0, currentScore - aiTotal);
            }
        } else {
            lastResult = RoundResult.DRAW;
            draws++;
            currentScore += (playerTotal / 2) * multiplier;
        }

        if (currentScore > highScore) highScore = currentScore;
        return lastResult;
    }

    public boolean undoLastRoll() {
        if (!playerHistory.isEmpty()) {
            playerDice = playerHistory.remove(playerHistory.size() - 1);
            aiDice = aiHistory.remove(aiHistory.size() - 1);
            roundActive = true;
            isDoubleUp = false;
            lastResult = RoundResult.ONGOING;
            return true;
        }
        return false;
    }

    public boolean canUndo() {
        return !playerHistory.isEmpty();
    }

    /**
     * 数学胜率与决策建议 (基于条件概率期望)
     */
    public String getProbabilityHint() {
        if (!roundActive) return "对局未激活，请先摇骰";

        int playerTotal = getPlayerTotal();
        int minSum = diceCount;
        int maxSum = diceCount * 6;
        double avgSum = (minSum + maxSum) / 2.0;

        if (playerTotal > avgSum + 1.5) {
            return String.format("我方点数 %d 点高于理论均值 (%.1f)，胜率极高，强烈建议【加倍】决胜！",
                    playerTotal, avgSum);
        } else if (playerTotal < avgSum - 1.0) {
            return String.format("我方点数 %d 点低于理论均值 (%.1f)，风险较大，不建议加倍，可直接结算。",
                    playerTotal, avgSum);
        } else {
            return String.format("我方点数 %d 点接近中位平均值，胜负各半，建议稳健平局收分。", playerTotal);
        }
    }

    public int getPlayerTotal() {
        int sum = 0;
        for (int v : playerDice) sum += v;
        return sum;
    }

    public int getAiTotal() {
        int sum = 0;
        for (int v : aiDice) sum += v;
        return sum;
    }

    public static boolean isLeopard(int[] dice) {
        if (dice == null || dice.length <= 1) return false;
        int first = dice[0];
        for (int v : dice) {
            if (v != first) return false;
        }
        return true;
    }

    // Getters and Setters
    public int[] getPlayerDice() { return playerDice.clone(); }
    public int[] getAiDice() { return aiDice.clone(); }
    public boolean isRoundActive() { return roundActive; }
    public RoundResult getLastResult() { return lastResult; }
    public int getTotalRounds() { return totalRounds; }
    public int getPlayerWins() { return playerWins; }
    public int getAiWins() { return aiWins; }
    public int getDraws() { return draws; }
    public int getWinStreak() { return winStreak; }
    public int getMaxStreak() { return maxStreak; }
    public int getCurrentScore() { return currentScore; }
    public int getHighScore() { return highScore; }
    public void setHighScore(int hs) { this.highScore = hs; }

    // Test helpers
    void setDiceForTest(int[] player, int[] ai, boolean active) {
        this.playerDice = player.clone();
        this.aiDice = ai.clone();
        this.diceCount = player.length;
        this.roundActive = active;
    }
}

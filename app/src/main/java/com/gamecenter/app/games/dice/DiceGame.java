package com.gamecenter.app.games.dice;

import java.util.Arrays;
import java.util.Random;

/**
 * 骰子对战 — 玩家 vs AI 各投3颗骰子，比牌型大小
 * 牌型: 豹子(3同) > 顺子(连续) > 对子 > 散牌
 */
public class DiceGame {

    public enum HandType { THREE_OF_A_KIND, STRAIGHT, PAIR, HIGH_CARD }

    private int[] playerDice;
    private int[] aiDice;
    private int playerRolls;
    private int aiRolls;
    private int playerWins;
    private int aiWins;
    private int draws;
    private int round;
    private boolean roundOver;
    private String resultText;
    private Random random;

    private static final int MAX_REROLLS = 2;

    public DiceGame() {
        random = new Random();
        playerDice = new int[3];
        aiDice = new int[3];
        reset();
    }

    public void reset() {
        for (int i = 0; i < 3; i++) { playerDice[i] = 1; aiDice[i] = 1; }
        playerRolls = 0;
        aiRolls = 0;
        round = 0;
        roundOver = false;
        resultText = "";
    }

    public void rollPlayer() {
        if (roundOver) return;
        for (int i = 0; i < 3; i++) playerDice[i] = random.nextInt(6) + 1;
        Arrays.sort(playerDice);
        playerRolls++;

        aiRoll();
        if (playerRolls >= MAX_REROLLS) evaluateRound();
    }

    private void aiRoll() {
        for (int i = 0; i < 3; i++) aiDice[i] = random.nextInt(6) + 1;
        Arrays.sort(aiDice);
        aiRolls++;
    }

    private void evaluateRound() {
        roundOver = true;
        round++;
        int result = compareHands(playerDice, aiDice);
        if (result > 0) { playerWins++; resultText = "你赢了!"; }
        else if (result < 0) { aiWins++; resultText = "AI赢了!"; }
        else { draws++; resultText = "平局!"; }
    }

    public void nextRound() {
        playerRolls = 0;
        aiRolls = 0;
        roundOver = false;
        resultText = "";
        for (int i = 0; i < 3; i++) { playerDice[i] = 1; aiDice[i] = 1; }
    }

    public static HandType getHandType(int[] dice) {
        int[] sorted = dice.clone();
        Arrays.sort(sorted);
        if (sorted[0] == sorted[1] && sorted[1] == sorted[2]) return HandType.THREE_OF_A_KIND;
        if (sorted[1] == sorted[0] + 1 && sorted[2] == sorted[1] + 1) return HandType.STRAIGHT;
        if (sorted[0] == sorted[1] || sorted[1] == sorted[2] || sorted[0] == sorted[2])
            return HandType.PAIR;
        return HandType.HIGH_CARD;
    }

    private int compareHands(int[] p, int[] a) {
        HandType pt = getHandType(p);
        HandType at = getHandType(a);
        if (pt.ordinal() < at.ordinal()) return 1;
        if (pt.ordinal() > at.ordinal()) return -1;

        int pSum = p[0] + p[1] + p[2];
        int aSum = a[0] + a[1] + a[2];
        return Integer.compare(pSum, aSum);
    }

    public int[] getPlayerDice() { return playerDice; }
    public int[] getAiDice() { return aiDice; }
    public int getPlayerRolls() { return playerRolls; }
    public int getAiRolls() { return aiRolls; }
    public int getPlayerWins() { return playerWins; }
    public int getAiWins() { return aiWins; }
    public int getDraws() { return draws; }
    public int getRound() { return round; }
    public boolean isRoundOver() { return roundOver; }
    public String getResultText() { return resultText; }
    public int getMaxRerolls() { return MAX_REROLLS; }
}

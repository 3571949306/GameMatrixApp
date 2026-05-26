package com.gamecenter.app.games.dice;

import java.util.Arrays;
import java.util.Random;

/**
 * 骰子对战游戏的核心逻辑类 — 玩家 vs AI 各投3颗骰子，比牌型大小。
 *
 * <p><b>游戏规则：</b></p>
 * <ul>
 *   <li>每局玩家最多可掷 {@value #MAX_REROLLS} 次骰子，每次掷骰后AI也自动掷一次</li>
 *   <li>达到最大掷骰次数后自动判定本局胜负</li>
 * </ul>
 *
 * <p><b>牌型等级（从高到低）：</b></p>
 * <ol>
 *   <li>豹子（三同）— 三颗骰子点数相同</li>
 *   <li>顺子 — 三颗骰子点数连续（如 2-3-4）</li>
 *   <li>对子 — 两颗骰子点数相同</li>
 *   <li>散牌 — 无以上组合，按点数之和比较</li>
 * </ol>
 *
 * <p><b>关键设计决策：</b>牌型比较使用枚举序号（ordinal），
 * 枚举声明顺序即为牌型大小顺序，序号越小牌型越大。</p>
 */
public class DiceGame {

    /**
     * 骰子牌型枚举，声明顺序即为牌型大小顺序（序号越小牌型越大）。
     * <ul>
     *   <li>{@code THREE_OF_A_KIND} — 豹子（三同），最强牌型</li>
     *   <li>{@code STRAIGHT} — 顺子，第二强</li>
     *   <li>{@code PAIR} — 对子，第三强</li>
     *   <li>{@code HIGH_CARD} — 散牌，最弱</li>
     * </ul>
     */
    public enum HandType { THREE_OF_A_KIND, STRAIGHT, PAIR, HIGH_CARD }

    /** 玩家的3颗骰子点数，排序后存储 */
    private int[] playerDice;
    /** AI的3颗骰子点数，排序后存储 */
    private int[] aiDice;
    /** 玩家当前局已掷骰次数 */
    private int playerRolls;
    /** AI当前局已掷骰次数 */
    private int aiRolls;
    /** 玩家累计胜场 */
    private int playerWins;
    /** AI累计胜场 */
    private int aiWins;
    /** 累计平局次数 */
    private int draws;
    /** 已完成的局数 */
    private int round;
    /** 当前局是否已结束（已判定胜负） */
    private boolean roundOver;
    /** 当前局的结果文本，如 "你赢了!"、"AI赢了!"、"平局!" */
    private String resultText;
    /** 随机数生成器，用于模拟掷骰子 */
    private Random random;

    /** 每局最大掷骰次数，达到此次数后自动判定胜负 */
    private static final int MAX_REROLLS = 2;

    /**
     * 构造方法。初始化随机数生成器、骰子数组，并调用 {@link #reset()} 重置游戏状态。
     */
    public DiceGame() {
        random = new Random();
        playerDice = new int[3];
        aiDice = new int[3];
        reset();
    }

    /**
     * 重置游戏到初始状态。将所有骰子点数归1、计数器归零、局数归零。
     * 通常在重新开始整场游戏时调用。
     */
    public void reset() {
        for (int i = 0; i < 3; i++) { playerDice[i] = 1; aiDice[i] = 1; }
        playerRolls = 0;
        aiRolls = 0;
        round = 0;
        roundOver = false;
        resultText = "";
    }

    /**
     * 玩家掷骰子。每次调用执行以下操作：
     * <ol>
     *   <li>随机生成玩家的3颗骰子点数（1-6），并排序</li>
     *   <li>AI自动掷一次骰子</li>
     *   <li>若已达到最大掷骰次数，自动调用 {@link #evaluateRound()} 判定胜负</li>
     * </ol>
     *
     * <p>若当前局已结束（{@code roundOver == true}），则直接返回不做任何操作。</p>
     */
    public void rollPlayer() {
        if (roundOver) return;
        // 随机生成1-6的骰子点数
        for (int i = 0; i < 3; i++) playerDice[i] = random.nextInt(6) + 1;
        // 排序以便后续牌型判断
        Arrays.sort(playerDice);
        playerRolls++;

        // 每次玩家掷骰后，AI也跟着掷一次
        aiRoll();
        // 达到最大掷骰次数时自动结算本局
        if (playerRolls >= MAX_REROLLS) evaluateRound();
    }

    /**
     * AI掷骰子。随机生成AI的3颗骰子点数（1-6），排序后递增AI掷骰计数。
     * 此方法由 {@link #rollPlayer()} 内部调用，不对外暴露。
     */
    private void aiRoll() {
        for (int i = 0; i < 3; i++) aiDice[i] = random.nextInt(6) + 1;
        Arrays.sort(aiDice);
        aiRolls++;
    }

    /**
     * 结算当前局的胜负。比较玩家和AI的牌型，更新胜/负/平计数和结果文本。
     * 此方法在达到最大掷骰次数后自动调用。
     */
    private void evaluateRound() {
        roundOver = true;
        round++;
        int result = compareHands(playerDice, aiDice);
        if (result > 0) { playerWins++; resultText = "你赢了!"; }
        else if (result < 0) { aiWins++; resultText = "AI赢了!"; }
        else { draws++; resultText = "平局!"; }
    }

    /**
     * 开始下一局。重置掷骰计数器和局状态，但保留累计胜/负/平记录。
     * 骰子点数归1，等待玩家再次掷骰。
     */
    public void nextRound() {
        playerRolls = 0;
        aiRolls = 0;
        roundOver = false;
        resultText = "";
        for (int i = 0; i < 3; i++) { playerDice[i] = 1; aiDice[i] = 1; }
    }

    /**
     * 根据骰子点数判断牌型。
     *
     * <p>判断逻辑（输入数组需先排序）：</p>
     * <ul>
     *   <li>三颗相同 → 豹子</li>
     *   <li>连续递增（差值均为1） → 顺子</li>
     *   <li>任意两颗相同 → 对子</li>
     *   <li>以上都不满足 → 散牌</li>
     * </ul>
     *
     * @param dice 骰子点数数组（长度为3），方法内部会克隆并排序，不影响原数组
     * @return 对应的牌型枚举值
     */
    public static HandType getHandType(int[] dice) {
        int[] sorted = dice.clone();
        Arrays.sort(sorted);
        // 三颗骰子点数完全相同 → 豹子
        if (sorted[0] == sorted[1] && sorted[1] == sorted[2]) return HandType.THREE_OF_A_KIND;
        // 排序后相邻差值均为1 → 顺子（如 1-2-3, 4-5-6）
        if (sorted[1] == sorted[0] + 1 && sorted[2] == sorted[1] + 1) return HandType.STRAIGHT;
        // 任意两颗相同 → 对子（排序后只需比较相邻或首尾）
        if (sorted[0] == sorted[1] || sorted[1] == sorted[2] || sorted[0] == sorted[2])
            return HandType.PAIR;
        // 无任何组合 → 散牌
        return HandType.HIGH_CARD;
    }

    /**
     * 比较玩家和AI的牌型大小。
     *
     * <p>比较规则：</p>
     * <ol>
     *   <li>先比较牌型等级（枚举序号越小牌型越大）</li>
     *   <li>牌型相同时，比较三颗骰子点数之和</li>
     * </ol>
     *
     * @param p 玩家骰子点数（已排序）
     * @param a AI骰子点数（已排序）
     * @return 正数表示玩家赢，负数表示AI赢，0表示平局
     */
    private int compareHands(int[] p, int[] a) {
        HandType pt = getHandType(p);
        HandType at = getHandType(a);
        // 枚举序号越小牌型越大，所以玩家序号更小时返回1（玩家赢）
        if (pt.ordinal() < at.ordinal()) return 1;
        if (pt.ordinal() > at.ordinal()) return -1;

        // 牌型相同时，按点数之和决胜
        int pSum = p[0] + p[1] + p[2];
        int aSum = a[0] + a[1] + a[2];
        return Integer.compare(pSum, aSum);
    }

    /** 获取玩家骰子点数数组 */
    public int[] getPlayerDice() { return playerDice; }
    /** 获取AI骰子点数数组 */
    public int[] getAiDice() { return aiDice; }
    /** 获取玩家当前局已掷骰次数 */
    public int getPlayerRolls() { return playerRolls; }
    /** 获取AI当前局已掷骰次数 */
    public int getAiRolls() { return aiRolls; }
    /** 获取玩家累计胜场数 */
    public int getPlayerWins() { return playerWins; }
    /** 获取AI累计胜场数 */
    public int getAiWins() { return aiWins; }
    /** 获取累计平局次数 */
    public int getDraws() { return draws; }
    /** 获取已完成的局数 */
    public int getRound() { return round; }
    /** 判断当前局是否已结束 */
    public boolean isRoundOver() { return roundOver; }
    /** 获取当前局的结果文本（如 "你赢了!"） */
    public String getResultText() { return resultText; }
    /** 获取每局最大掷骰次数 */
    public int getMaxRerolls() { return MAX_REROLLS; }
}

package com.gamecenter.app.rock;

import java.util.Random;

/**
 * 猜拳游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>从宿主 RockActivity 中提取：手势判定、AI 策略、胜负统计。</p>
 */
public class RockGame {

    public static final int ROCK = 0;
    public static final int SCISSORS = 1;
    public static final int PAPER = 2;

    public static final String[] CHOICE_EMOJI = {"✊", "✌️", "🖐"};
    public static final String[] CHOICE_NAMES = {"石头", "剪刀", "布"};

    /** 结果：0=平局, 1=玩家赢, -1=玩家输 */
    public static final int RESULT_DRAW = 0;
    public static final int RESULT_PLAYER_WIN = 1;
    public static final int RESULT_PLAYER_LOSE = -1;

    private final Random random = new Random();

    private int totalRounds = 0;
    private int playerWins = 0;
    private int aiWins = 0;
    private int draws = 0;
    private int winStreak = 0;
    private int maxWinStreak = 0;
    private final boolean[] usedChoices = new boolean[3];

    /**
     * 玩家选择后计算 AI 选择并判定。
     * @return [aiChoice, result]
     */
    public int[] play(int playerChoice) {
        usedChoices[playerChoice] = true;
        int aiChoice = calculateAiChoice();
        int result = judge(playerChoice, aiChoice);

        totalRounds++;
        if (result == RESULT_DRAW) {
            draws++;
        } else if (result == RESULT_PLAYER_WIN) {
            playerWins++;
            winStreak++;
            if (winStreak > maxWinStreak) maxWinStreak = winStreak;
        } else {
            aiWins++;
            winStreak = 0;
        }
        return new int[]{aiChoice, result};
    }

    private int calculateAiChoice() {
        if (winStreak >= 2 && totalRounds > 0) {
            if (random.nextFloat() < 0.7f) {
                return random.nextInt(3);
            }
        }
        return random.nextInt(3);
    }

    private int judge(int player, int ai) {
        if (player == ai) return RESULT_DRAW;
        if ((player == ROCK && ai == SCISSORS) ||
            (player == SCISSORS && ai == PAPER) ||
            (player == PAPER && ai == ROCK)) {
            return RESULT_PLAYER_WIN;
        }
        return RESULT_PLAYER_LOSE;
    }

    public int getTotalRounds() { return totalRounds; }
    public int getPlayerWins() { return playerWins; }
    public int getAiWins() { return aiWins; }
    public int getDraws() { return draws; }
    public int getWinStreak() { return winStreak; }
    public int getMaxWinStreak() { return maxWinStreak; }
    public boolean[] getUsedChoices() { return usedChoices; }
}

package com.gamecenter.app.rock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 猜拳游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>特性：
 * <ul>
 *   <li>包含简单、中等、困难三档 AI 决策梯度；</li>
 *   <li>简单：纯伪随机；</li>
 *   <li>中等：频率分析与惯性克制；</li>
 *   <li>困难：一阶马尔可夫链转移矩阵分析（预测玩家出拳转移规律）；</li>
 *   <li>支持局势撤销与完整胜负历史。</li>
 * </ul>
 * </p>
 */
public class RockGame {

    public static final int ROCK = 0;      // 石头 ✊
    public static final int SCISSORS = 1;  // 剪刀 ✌️
    public static final int PAPER = 2;     // 布 🖐

    public static final String[] CHOICE_EMOJI = {"✊", "✌️", "🖐"};
    public static final String[] CHOICE_NAMES = {"石头", "剪刀", "布"};

    public static final int DIFFICULTY_EASY = 0;
    public static final int DIFFICULTY_MEDIUM = 1;
    public static final int DIFFICULTY_HARD = 2;

    /** 结果：0=平局, 1=玩家赢, -1=玩家输 */
    public static final int RESULT_DRAW = 0;
    public static final int RESULT_PLAYER_WIN = 1;
    public static final int RESULT_PLAYER_LOSE = -1;

    public static class RoundHistory {
        public final int playerChoice;
        public final int aiChoice;
        public final int result;

        public RoundHistory(int playerChoice, int aiChoice, int result) {
            this.playerChoice = playerChoice;
            this.aiChoice = aiChoice;
            this.result = result;
        }
    }

    private final Random random = new Random();
    private int difficulty = DIFFICULTY_MEDIUM;

    private int totalRounds = 0;
    private int playerWins = 0;
    private int aiWins = 0;
    private int draws = 0;
    private int winStreak = 0;
    private int maxWinStreak = 0;

    private final int[] playerChoiceCount = new int[3];
    private final int[][] markovTransition = new int[3][3];
    private int lastPlayerChoice = -1;

    private final List<RoundHistory> history = new ArrayList<>();

    public void reset(int diff) {
        this.difficulty = clampDifficulty(diff);
        this.totalRounds = 0;
        this.playerWins = 0;
        this.aiWins = 0;
        this.draws = 0;
        this.winStreak = 0;
        this.maxWinStreak = 0;
        this.lastPlayerChoice = -1;
        for (int i = 0; i < 3; i++) {
            playerChoiceCount[i] = 0;
            for (int j = 0; j < 3; j++) {
                markovTransition[i][j] = 0;
            }
        }
        history.clear();
    }

    private int clampDifficulty(int diff) {
        if (diff < 0) return 0;
        if (diff > 2) return 2;
        return diff;
    }

    /**
     * 玩家选择后计算 AI 选择并判定。
     * @return [aiChoice, result]
     */
    public int[] play(int playerChoice) {
        int aiChoice = calculateAiChoice();
        int result = judge(playerChoice, aiChoice);

        // 更新马尔可夫转移矩阵
        if (lastPlayerChoice >= 0) {
            markovTransition[lastPlayerChoice][playerChoice]++;
        }
        lastPlayerChoice = playerChoice;
        playerChoiceCount[playerChoice]++;

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

        history.add(new RoundHistory(playerChoice, aiChoice, result));
        return new int[]{aiChoice, result};
    }

    /** 悔棋回退上一轮 */
    public boolean undo() {
        if (history.isEmpty()) return false;
        RoundHistory last = history.remove(history.size() - 1);
        totalRounds--;
        if (last.result == RESULT_DRAW) {
            draws--;
        } else if (last.result == RESULT_PLAYER_WIN) {
            playerWins--;
            // 重新计算连胜
            recalculateStreak();
        } else {
            aiWins--;
            recalculateStreak();
        }

        playerChoiceCount[last.playerChoice]--;
        if (!history.isEmpty()) {
            RoundHistory prev = history.get(history.size() - 1);
            markovTransition[prev.playerChoice][last.playerChoice]--;
            lastPlayerChoice = prev.playerChoice;
        } else {
            lastPlayerChoice = -1;
        }
        return true;
    }

    private void recalculateStreak() {
        winStreak = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).result == RESULT_PLAYER_WIN) {
                winStreak++;
            } else {
                break;
            }
        }
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    /** 核心 AI 出拳算法 */
    private int calculateAiChoice() {
        if (difficulty == DIFFICULTY_EASY) {
            return random.nextInt(3);
        }

        if (difficulty == DIFFICULTY_MEDIUM) {
            // 中等难度：分析玩家出拳最高频手势并尝试克制 (65% 概率克制，35% 随机)
            if (totalRounds >= 2 && random.nextFloat() < 0.65f) {
                int mostFrequent = getMostFrequentPlayerChoice();
                return getCounterChoice(mostFrequent);
            }
            return random.nextInt(3);
        }

        // 困难难度：马尔可夫链转移预判 (80% 依据转移概率克制，20% 变招)
        if (totalRounds >= 3 && lastPlayerChoice >= 0 && random.nextFloat() < 0.80f) {
            int predicted = predictNextPlayerChoice(lastPlayerChoice);
            return getCounterChoice(predicted);
        }

        return random.nextInt(3);
    }

    private int getMostFrequentPlayerChoice() {
        int best = 0;
        int max = playerChoiceCount[0];
        for (int i = 1; i < 3; i++) {
            if (playerChoiceCount[i] > max) {
                max = playerChoiceCount[i];
                best = i;
            }
        }
        return best;
    }

    private int predictNextPlayerChoice(int fromChoice) {
        int best = random.nextInt(3);
        int maxCount = -1;
        for (int to = 0; to < 3; to++) {
            if (markovTransition[fromChoice][to] > maxCount) {
                maxCount = markovTransition[fromChoice][to];
                best = to;
            }
        }
        return best;
    }

    /** 获取克制目标手势的出手选择（例如对方出石头，我出布） */
    public static int getCounterChoice(int target) {
        if (target == ROCK) return PAPER;
        if (target == SCISSORS) return ROCK;
        return SCISSORS;
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

    public int getDifficulty() { return difficulty; }
    public int getTotalRounds() { return totalRounds; }
    public int getPlayerWins() { return playerWins; }
    public int getAiWins() { return aiWins; }
    public int getDraws() { return draws; }
    public int getWinStreak() { return winStreak; }
    public int getMaxWinStreak() { return maxWinStreak; }
    public List<RoundHistory> getHistory() { return history; }
}

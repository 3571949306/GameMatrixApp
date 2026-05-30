package com.gamecenter.app.games.guess;

import java.util.Random;

/**
 * 猜数字游戏的核心逻辑类
 *
 * <p>游戏规则：系统随机生成一个目标数字，玩家通过输入猜测数字获得提示
 * （太大/太小/接近），直到猜中为止。支持三种难度级别。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>三种难度：简单(1-50)、中等(1-100)、困难(1-500)，通过MAX_RANGES数组映射</li>
 *   <li>提示分三级：差距≤5为"稍微"、差距≤范围1/5为普通、更大为"差很多"，
 *       帮助玩家逐步缩小范围</li>
 *   <li>最佳记录(bestScore)在同一游戏实例内保持，跨局累计</li>
 *   <li>越界输入不计入猜测次数</li>
 * </ul>
 */
public class GuessGame {

    /** 简单难度常量 */
    public static final int EASY = 0;
    /** 中等难度常量 */
    public static final int MEDIUM = 1;
    /** 困难难度常量 */
    public static final int HARD = 2;

    /** 各难度对应的最大范围值 */
    private static final int[] MAX_RANGES = {50, 100, 500};
    /** 各难度的显示名称 */
    private static final String[] DIFF_NAMES = {"简单(1-50)", "中等(1-100)", "困难(1-500)"};

    /** 当前难度级别 */
    private int difficulty;
    /** 目标数字（需要猜的数字） */
    private int targetNumber;
    /** 最小范围（固定为1） */
    private int minRange;
    /** 最大范围（根据难度变化） */
    private int maxRange;
    /** 当前猜测次数 */
    private int attempts;
    /** 上一次猜测的数字 */
    private int lastGuess;
    /** 上一次的提示文字 */
    private String lastHint;
    /** 游戏是否结束（猜中后为true） */
    private boolean gameOver;
    /** 随机数生成器 */
    private Random random;
    /** 最佳记录（最少猜测次数），0表示暂无记录 */
    private int bestScore;

    /**
     * 构造方法，默认中等难度
     */
    public GuessGame() {
        random = new Random();
        difficulty = MEDIUM;
        minRange = 1;
        maxRange = MAX_RANGES[difficulty];
        bestScore = 0;
        reset();
    }

    /**
     * 设置难度级别
     *
     * <p>难度值会被限制在0-2范围内，设置后自动重置游戏。</p>
     *
     * @param diff 难度级别（EASY/MEDIUM/HARD）
     */
    public void setDifficulty(int diff) {
        difficulty = Math.max(0, Math.min(diff, HARD));
        maxRange = MAX_RANGES[difficulty];
        reset();
    }

    /** 获取当前难度级别 */
    public int getDifficulty() { return difficulty; }
    /** 获取当前难度的显示名称 */
    public String getDifficultyName() { return DIFF_NAMES[difficulty]; }

    /**
     * 重置游戏，生成新的目标数字
     *
     * <p>在[minRange, maxRange]范围内随机生成目标数字，
     * 重置猜测次数和游戏状态。最佳记录不会被重置。</p>
     */
    public void reset() {
        targetNumber = random.nextInt(maxRange - minRange + 1) + minRange;
        attempts = 0;
        lastGuess = -1;
        lastHint = "";
        gameOver = false;
    }

    /**
     * 进行一次猜测
     *
     * <p>猜测逻辑：</p>
     * <ol>
     *   <li>游戏已结束时返回上一次提示</li>
     *   <li>越界输入不计入猜测次数，提示输入范围</li>
     *   <li>根据猜测值与目标值的差距给出三级提示：
     *     <ul>
     *       <li>差距≤5："稍微大/小了一点"</li>
     *       <li>差距≤范围1/5："太大了/太小了"</li>
     *       <li>更大差距："太大了/太小了...差很多"</li>
     *     </ul>
     *   </li>
     *   <li>猜中时更新最佳记录</li>
     * </ol>
     *
     * @param guess 玩家猜测的数字
     * @return 提示文字
     */
    public String makeGuess(int guess) {
        if (gameOver) return lastHint;
        attempts++;
        lastGuess = guess;

        // 越界输入不计入猜测次数
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
            // 更新最佳记录
            if (bestScore == 0 || attempts < bestScore) bestScore = attempts;
        }
        return lastHint;
    }

    /** 获取目标数字（答案） */
    public int getTargetNumber() { return targetNumber; }
    /** 获取当前猜测次数 */
    public int getAttempts() { return attempts; }
    /** 获取上一次猜测的数字 */
    public int getLastGuess() { return lastGuess; }
    /** 获取上一次的提示文字 */
    public String getLastHint() { return lastHint; }
    /** 游戏是否结束 */
    public boolean isGameOver() { return gameOver; }
    /** 获取最小范围值 */
    public int getMinRange() { return minRange; }
    /** 获取最大范围值 */
    public int getMaxRange() { return maxRange; }
    /** 获取最佳记录（最少猜测次数），0表示暂无 */
    public int getBestScore() { return bestScore; }
}

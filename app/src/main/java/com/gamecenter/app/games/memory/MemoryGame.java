package com.gamecenter.app.games.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 记忆翻牌游戏的核心逻辑类
 *
 * <p>游戏规则：4x4网格中共有8对卡牌，玩家每次翻开两张，
 * 如果匹配则保持翻开，不匹配则翻回。全部匹配后游戏结束。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>采用"两阶段确认"机制：翻牌后先判断是否匹配，再由外部调用confirmMatch/hideMismatch，
 *       使得View层可以插入翻转动画延迟</li>
 *   <li>waiting状态用于阻止玩家在两张不匹配的牌尚未翻回时继续翻牌</li>
 * </ul>
 */
public class MemoryGame {

    /** 卡牌对数，共8对即16张卡牌 */
    public static final int PAIRS = 8;
    /** 网格列数 */
    public static final int COLS = 4;
    /** 网格行数，由对数和列数计算得出 */
    public static final int ROWS = PAIRS * 2 / COLS;

    /** 卡牌值矩阵，每个值代表一种图案的编号 */
    private int[][] board;
    /** 标记卡牌是否已被翻开（当前回合可见） */
    private boolean[][] revealed;
    /** 标记卡牌是否已匹配成功（永久可见） */
    private boolean[][] matched;
    /** 当前得分 */
    private int score;
    /** 已匹配的对数 */
    private int matchCount;
    /** 游戏是否结束 */
    private boolean gameOver;
    /** 随机数生成器，用于洗牌 */
    private Random random;

    /** 第一次翻开的卡牌列坐标，-1表示尚未翻开 */
    private int firstX = -1;
    /** 第一次翻开的卡牌行坐标 */
    private int firstY = -1;
    /** 第二次翻开的卡牌列坐标 */
    private int secondX = -1;
    /** 第二次翻开的卡牌行坐标 */
    private int secondY = -1;
    /** 是否正在等待（两张不匹配的牌等待翻回） */
    private boolean waiting;
    /** 上一次翻牌是否匹配成功 */
    private boolean justMatched;

    /**
     * 构造方法，初始化随机数生成器和数组，然后重置游戏状态
     */
    public MemoryGame() {
        random = new Random();
        board = new int[ROWS][COLS];
        revealed = new boolean[ROWS][COLS];
        matched = new boolean[ROWS][COLS];
        reset();
    }

    /**
     * 重置游戏到初始状态
     *
     * <p>生成8对卡牌值并随机打乱分配到网格中，
     * 同时重置所有状态变量（分数、匹配数、坐标等）。</p>
     */
    public void reset() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < PAIRS; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values, random);

        int idx = 0;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                board[y][x] = values.get(idx++);
                revealed[y][x] = false;
                matched[y][x] = false;
            }
        }

        score = 0;
        matchCount = 0;
        gameOver = false;
        firstX = -1;
        firstY = -1;
        secondX = -1;
        secondY = -1;
        waiting = false;
        justMatched = false;
    }

    /**
     * 判断指定位置的卡牌是否可以被翻开
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true表示可以翻开，false表示不可翻开
     */
    public boolean canFlip(int x, int y) {
        if (gameOver) return false;
        if (waiting) return false;
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false;
        if (revealed[y][x]) return false;
        return true;
    }

    /**
     * 翻开指定位置的卡牌
     *
     * <p>翻牌逻辑：</p>
     * <ol>
     *   <li>如果是第一张牌，记录坐标并返回</li>
     *   <li>如果是第二张牌，比较两张牌的值：
     *     <ul>
     *       <li>匹配成功：标记justMatched，清除坐标记录</li>
     *       <li>匹配失败：进入waiting状态，等待外部调用hideMismatch</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true表示翻牌成功，false表示无法翻开
     */
    public boolean flipCard(int x, int y) {
        if (!canFlip(x, y)) return false;
        revealed[y][x] = true;
        justMatched = false;

        if (firstX < 0) {
            firstX = x;
            firstY = y;
            return true;
        }

        secondX = x;
        secondY = y;

        if (board[firstY][firstX] == board[y][x]) {
            justMatched = true;
            firstX = -1;
            firstY = -1;
            secondX = -1;
            secondY = -1;
        } else {
            waiting = true;
        }
        return true;
    }

    /**
     * 判断上一次翻牌是否匹配成功
     *
     * @return true表示匹配成功
     */
    public boolean lastMatchSuccessful() {
        return justMatched;
    }

    /**
     * 确认匹配成功，将已翻开的卡牌标记为已匹配
     *
     * <p>遍历所有卡牌，将已翻开但未标记为已匹配的卡牌标记为matched，
     * 每对匹配得10分。当所有对数都匹配完毕时，游戏结束。</p>
     */
    public void confirmMatch() {
        if (!justMatched) return;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (revealed[y][x] && !matched[y][x]) {
                    matched[y][x] = true;
                    matchCount++;
                    score += 10;
                }
            }
        }
        justMatched = false;
        if (matchCount == PAIRS) {
            gameOver = true;
        }
    }

    /**
     * 隐藏不匹配的两张卡牌（翻回）
     *
     * <p>将firstX/firstY和secondX/secondY对应的两张卡牌
     * 的revealed状态设为false，并清除坐标记录，解除waiting状态。</p>
     */
    public void hideMismatch() {
        if (firstX >= 0 && secondX >= 0) {
            revealed[firstY][firstX] = false;
            revealed[secondY][secondX] = false;
        }
        firstX = -1;
        firstY = -1;
        secondX = -1;
        secondY = -1;
        waiting = false;
    }

    /**
     * 获取指定位置卡牌的值（图案编号）
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return 卡牌值，越界返回-1
     */
    public int getCardValue(int x, int y) {
        if (y < 0 || y >= ROWS || x < 0 || x >= COLS) return -1;
        return board[y][x];
    }

    /**
     * 判断指定位置的卡牌是否可见（已翻开或已匹配）
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true表示卡牌可见，越界返回false
     */
    public boolean isRevealed(int x, int y) {
        if (y < 0 || y >= ROWS || x < 0 || x >= COLS) return false;
        return revealed[y][x] || matched[y][x];
    }

    /**
     * 判断指定位置的卡牌是否已匹配成功
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true表示已匹配，越界返回false
     */
    public boolean isMatched(int x, int y) {
        if (y < 0 || y >= ROWS || x < 0 || x >= COLS) return false;
        return matched[y][x];
    }

    /** 是否正在等待（不匹配的牌等待翻回） */
    public boolean isWaiting() { return waiting; }
    /** 获取当前得分 */
    public int getScore() { return score; }
    /** 获取已匹配的对数 */
    public int getMatched() { return matchCount; }
    /** 游戏是否结束 */
    public boolean isGameOver() { return gameOver; }
    /** 获取网格行数 */
    public int getRows() { return ROWS; }
    /** 获取网格列数 */
    public int getCols() { return COLS; }
}

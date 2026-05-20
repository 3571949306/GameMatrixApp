package com.gamecenter.app.games.tic;

import java.util.Random;

/**
 * 井字棋游戏逻辑类
 *
 * <p>职责：管理 3x3 棋盘状态、判断胜负、实现电脑AI落子策略。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>棋盘使用 board[y][x] 二维数组，0=空、1=玩家、2=电脑</li>
 *   <li>AI 采用优先级策略：先检查能否获胜 → 再检查是否需要堵截 → 占中心 → 占角 → 占边</li>
 *   <li>玩家始终先手（currentTurn 初始为 PLAYER）</li>
 * </ul>
 *
 * <p>AI策略说明：本AI并非Minimax最优解，而是基于启发式规则的快速决策，
 * 优先级依次为：获胜 > 堵截 > 中心 > 角 > 任意空位。</p>
 */
public class TicGame {

    /** 棋盘空位标记 */
    public static final int EMPTY = 0;

    /** 玩家标记（执X，先手） */
    public static final int PLAYER = 1;

    /** 电脑标记（执O，后手） */
    public static final int COMPUTER = 2;

    /** 3x3 棋盘数组，board[行y][列x] */
    private int[][] board;

    /** 当前轮次：PLAYER 或 COMPUTER */
    private int currentTurn;

    /** 游戏是否结束 */
    private boolean gameOver;

    /** 获胜方：PLAYER、COMPUTER 或 EMPTY（平局） */
    private int winner;

    /** 随机数生成器，用于AI策略中的随机选择 */
    private Random random;

    /**
     * 构造方法：初始化棋盘和随机数生成器，并重置游戏状态
     */
    public TicGame() {
        board = new int[3][3];
        random = new Random();
        reset();
    }

    /**
     * 重置游戏状态
     *
     * <p>清空棋盘所有格子，将轮次设为玩家先手，重置胜负状态。</p>
     */
    public void reset() {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                board[y][x] = EMPTY;
            }
        }
        currentTurn = PLAYER;
        gameOver = false;
        winner = EMPTY;
    }

    /**
     * 玩家在指定位置落子
     *
     * @param x 列索引（0-2）
     * @param y 行索引（0-2）
     * @return true 表示落子成功；false 表示落子失败（游戏已结束/越界/已被占用/非玩家回合）
     */
    public boolean placePiece(int x, int y) {
        if (gameOver) return false;
        if (x < 0 || x > 2 || y < 0 || y > 2) return false;
        if (board[y][x] != EMPTY) return false;
        if (currentTurn != PLAYER) return false;

        board[y][x] = PLAYER;
        checkWin();
        if (!gameOver) {
            currentTurn = COMPUTER;
        }
        return true;
    }

    /**
     * 电脑AI落子
     *
     * <p>采用启发式优先级策略：</p>
     * <ol>
     *   <li><b>获胜检查</b>：遍历所有空位，模拟电脑落子，若能立即获胜则选择该位置</li>
     *   <li><b>堵截检查</b>：遍历所有空位，模拟玩家落子，若玩家能立即获胜则堵截该位置</li>
     *   <li><b>占中心</b>：中心格(1,1)是最优战略位置，优先占据</li>
     *   <li><b>占角</b>：四个角(0,0)(2,0)(0,2)(2,2)次优</li>
     *   <li><b>占任意空位</b>：最后兜底，选择第一个空位</li>
     * </ol>
     */
    public void computerMove() {
        if (gameOver || currentTurn != COMPUTER) return;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (board[y][x] == EMPTY) {
                    board[y][x] = COMPUTER;
                    if (checkWinImmediate(COMPUTER)) {
                        checkWin();
                        currentTurn = PLAYER;
                        return;
                    }
                    board[y][x] = EMPTY;
                }
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (board[y][x] == EMPTY) {
                    board[y][x] = PLAYER;
                    if (checkWinImmediate(PLAYER)) {
                        board[y][x] = COMPUTER;
                        checkWin();
                        currentTurn = PLAYER;
                        return;
                    }
                    board[y][x] = EMPTY;
                }
            }
        }

        if (board[1][1] == EMPTY) {
            board[1][1] = COMPUTER;
        } else {
            int[][] corners = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};
            for (int[] c : corners) {
                if (board[c[1]][c[0]] == EMPTY) {
                    board[c[1]][c[0]] = COMPUTER;
                    checkWin();
                    currentTurn = PLAYER;
                    return;
                }
            }
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    if (board[y][x] == EMPTY) {
                        board[y][x] = COMPUTER;
                        checkWin();
                        currentTurn = PLAYER;
                        return;
                    }
                }
            }
        }

        checkWin();
        currentTurn = PLAYER;
    }

    /**
     * 即时胜负检查：判断指定玩家是否已经连成三子
     *
     * <p>检查所有8条可能的三连线：3行 + 3列 + 2条对角线</p>
     *
     * @param player 待检查的玩家（PLAYER 或 COMPUTER）
     * @return true 表示该玩家已连成三子
     */
    private boolean checkWinImmediate(int player) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) return true;
            if (board[0][i] == player && board[1][i] == player && board[2][i] == player) return true;
        }
        if (board[0][0] == player && board[1][1] == player && board[2][2] == player) return true;
        if (board[0][2] == player && board[1][1] == player && board[2][0] == player) return true;
        return false;
    }

    /**
     * 综合胜负检查：更新 gameOver 和 winner 状态
     *
     * <p>先检查双方是否有人获胜，若无人获胜则检查棋盘是否已满（平局）。</p>
     */
    private void checkWin() {
        for (int player : new int[]{PLAYER, COMPUTER}) {
            if (checkWinImmediate(player)) {
                gameOver = true;
                winner = player;
                return;
            }
        }
        boolean full = true;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (board[y][x] == EMPTY) {
                    full = false;
                    break;
                }
            }
        }
        if (full) {
            gameOver = true;
            winner = EMPTY;
        }
    }

    /**
     * @return 当前棋盘状态（3x3二维数组）
     */
    public int[][] getBoard() { return board; }

    /**
     * @return 当前轮次（PLAYER 或 COMPUTER）
     */
    public int getCurrentTurn() { return currentTurn; }

    /**
     * @return 游戏是否已结束
     */
    public boolean isGameOver() { return gameOver; }

    /**
     * @return 获胜方（PLAYER、COMPUTER 或 EMPTY 表示平局）
     */
    public int getWinner() { return winner; }
}

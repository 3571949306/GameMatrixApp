package com.gamecenter.app.tic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 井字棋游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>从宿主 TicTacToeActivity 中提取：棋盘状态、胜负判定、
 * AI（简单 / 中等 / 困难）。</p>
 *
 * <p>难度梯度：
 * <ul>
 *   <li>简单（0）：80% 随机 + 20% 必胜/必堵手</li>
 *   <li>中等（1）：Minimax 限制深度=2 + 启发式评估</li>
 *   <li>困难（2）：完整 Minimax 必胜或必平 + 首手开局库加权随机</li>
 * </ul>
 * </p>
 */
public class TicTacToeGame {

    public static final int BOARD_SIZE = 3;
    public static final int PLAYER_X = 1; // 人类
    public static final int PLAYER_O = 2; // AI

    /** 结果：0=进行中, 1=玩家胜, 2=AI胜, 3=平局 */
    public static final int RESULT_ONGOING = 0;
    public static final int RESULT_PLAYER_WIN = 1;
    public static final int RESULT_AI_WIN = 2;
    public static final int RESULT_DRAW = 3;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private final Random random = new Random();
    private boolean playerTurn = true;
    private boolean gameOver = false;
    private int winner = 0;
    /** AI 难度：0=简单, 1=中等, 2=困难 */
    private int aiLevel = 0;

    /** 胜利连线：[startRow, startCol, endRow, endCol]，null 表示无 */
    private int[] winLine = null;

    public void reset(int level) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = 0;
            }
        }
        aiLevel = clampLevel(level);
        playerTurn = true;
        gameOver = false;
        winner = 0;
        winLine = null;
    }

    private int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > 2) return 2;
        return level;
    }

    public int[][] getBoard() { return board; }

    public boolean isPlayerTurn() { return playerTurn; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
    public int[] getWinLine() { return winLine; }
    public int getAiLevel() { return aiLevel; }

    public boolean placePlayer(int row, int col) {
        if (gameOver || !playerTurn) return false;
        if (board[row][col] != 0) return false;
        board[row][col] = PLAYER_X;
        playerTurn = false;
        evaluate();
        return true;
    }

    /** AI 落子，返回落子坐标 [row, col]，null 表示无可落子。 */
    public int[] aiMove() {
        if (gameOver) return null;
        int[] cell = null;
        // 困难档首手开局库加权随机选择，增加开局多样性
        if (aiLevel == 2 && isBoardEmpty()) {
            cell = pickOpeningMove();
        }
        if (cell == null) {
            if (aiLevel == 0) {
                cell = easyMove();
            } else if (aiLevel == 1) {
                cell = mediumMove();
            } else {
                cell = minimaxMove(Integer.MAX_VALUE);
            }
        }
        if (cell != null) {
            board[cell[0]][cell[1]] = PLAYER_O;
        }
        playerTurn = true;
        evaluate();
        return cell;
    }

    private boolean isBoardEmpty() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != 0) return false;
            }
        }
        return true;
    }

    /**
     * 开局库加权随机选择首手位置。
     * 4 角权重3，中心权重2，4 边权重1。
     */
    private int[] pickOpeningMove() {
        int[][] points = {
            {0, 0}, {0, 2}, {2, 0}, {2, 2},  // 角
            {1, 1},                             // 中心
            {0, 1}, {1, 0}, {1, 2}, {2, 1}    // 边
        };
        int[] weights = {3, 3, 3, 3, 2, 1, 1, 1, 1};
        int total = 0;
        for (int w : weights) total += w;
        int r = random.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < points.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) return points[i];
        }
        return points[0];
    }

    /** 简单模式：80% 随机 + 20% 必胜/必堵手 */
    private int[] easyMove() {
        if (random.nextInt(10) < 2) {
            int[] win = findCriticalMove(PLAYER_O);
            if (win != null) return win;
            int[] block = findCriticalMove(PLAYER_X);
            if (block != null) return block;
        }
        return randomMove();
    }

    /** 中等模式：Minimax 限制深度=2，首手用开局库 */
    private int[] mediumMove() {
        if (isBoardEmpty()) {
            return pickOpeningMove();
        }
        return minimaxMove(2);
    }

    private int[] findCriticalMove(int player) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) {
                    board[r][c] = player;
                    int w = checkWinner(board);
                    board[r][c] = 0;
                    if (w == player) return new int[]{r, c};
                }
            }
        }
        return null;
    }

    private int[] randomMove() {
        List<int[]> empty = emptyCells();
        if (empty.isEmpty()) return null;
        return empty.get(random.nextInt(empty.size()));
    }

    private int[] minimaxMove(int maxDepth) {
        int bestScore = Integer.MIN_VALUE;
        int bestRow = -1;
        int bestCol = -1;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) {
                    board[r][c] = PLAYER_O;
                    int score = minimax(board, 0, false, maxDepth);
                    board[r][c] = 0;
                    if (score > bestScore) {
                        bestScore = score;
                        bestRow = r;
                        bestCol = c;
                    }
                }
            }
        }
        if (bestRow >= 0) return new int[]{bestRow, bestCol};
        return null;
    }

    private int minimax(int[][] b, int depth, boolean isMaximizing, int maxDepth) {
        int result = checkWinner(b);
        if (result == PLAYER_O) return 10 - depth;
        if (result == PLAYER_X) return depth - 10;
        if (isFull(b)) return 0;
        if (depth >= maxDepth) return evaluateHeuristic(b);

        if (isMaximizing) {
            int best = Integer.MIN_VALUE;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (b[r][c] == 0) {
                        b[r][c] = PLAYER_O;
                        best = Math.max(best, minimax(b, depth + 1, false, maxDepth));
                        b[r][c] = 0;
                    }
                }
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (b[r][c] == 0) {
                        b[r][c] = PLAYER_X;
                        best = Math.min(best, minimax(b, depth + 1, true, maxDepth));
                        b[r][c] = 0;
                    }
                }
            }
            return best;
        }
    }

    /** 启发式评估：双方潜在两连数差（AI 视角） */
    private int evaluateHeuristic(int[][] b) {
        return countPotentialLines(b, PLAYER_O) - countPotentialLines(b, PLAYER_X);
    }

    private int countPotentialLines(int[][] b, int player) {
        int count = 0;
        int[][] lines = {
            {0, 0, 0, 1, 0, 2}, {1, 0, 1, 1, 1, 2}, {2, 0, 2, 1, 2, 2},
            {0, 0, 1, 0, 2, 0}, {0, 1, 1, 1, 2, 1}, {0, 2, 1, 2, 2, 2},
            {0, 0, 1, 1, 2, 2}, {0, 2, 1, 1, 2, 0}
        };
        for (int[] line : lines) {
            int playerCount = 0;
            int opponentCount = 0;
            for (int i = 0; i < 3; i++) {
                int cell = b[line[i * 2]][line[i * 2 + 1]];
                if (cell == player) playerCount++;
                else if (cell != 0) opponentCount++;
            }
            if (opponentCount == 0 && playerCount == 2) count++;
        }
        return count;
    }

    private List<int[]> emptyCells() {
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) cells.add(new int[]{r, c});
            }
        }
        return cells;
    }

    private void evaluate() {
        int w = checkWinner(board);
        if (w == PLAYER_X) {
            gameOver = true;
            winner = RESULT_PLAYER_WIN;
            computeWinLine(PLAYER_X);
        } else if (w == PLAYER_O) {
            gameOver = true;
            winner = RESULT_AI_WIN;
            computeWinLine(PLAYER_O);
        } else if (isFull(board)) {
            gameOver = true;
            winner = RESULT_DRAW;
        }
    }

    private int checkWinner(int[][] b) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            if (b[r][0] != 0 && b[r][0] == b[r][1] && b[r][1] == b[r][2]) return b[r][0];
        }
        for (int c = 0; c < BOARD_SIZE; c++) {
            if (b[0][c] != 0 && b[0][c] == b[1][c] && b[1][c] == b[2][c]) return b[0][c];
        }
        if (b[0][0] != 0 && b[0][0] == b[1][1] && b[1][1] == b[2][2]) return b[0][0];
        if (b[0][2] != 0 && b[0][2] == b[1][1] && b[1][1] == b[2][0]) return b[0][2];
        return 0;
    }

    private void computeWinLine(int player) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            if (board[r][0] == player && board[r][0] == board[r][1] && board[r][1] == board[r][2]) {
                winLine = new int[]{r, 0, r, 2};
                return;
            }
        }
        for (int c = 0; c < BOARD_SIZE; c++) {
            if (board[0][c] == player && board[0][c] == board[1][c] && board[1][c] == board[2][c]) {
                winLine = new int[]{0, c, 2, c};
                return;
            }
        }
        if (board[0][0] == player && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            winLine = new int[]{0, 0, 2, 2};
            return;
        }
        if (board[0][2] == player && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            winLine = new int[]{0, 2, 2, 0};
        }
    }

    private boolean isFull(int[][] b) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (b[r][c] == 0) return false;
            }
        }
        return true;
    }
}

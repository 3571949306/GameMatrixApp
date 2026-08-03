// 同步声明：此文件的 AI 算法逻辑与 app/src/main/java/com/gamecenter/app/games/checkers/CheckersActivity.java 保持同步
// 结构差异：module-store 版 AI 逻辑在独立的 CheckersGame 类中；app 版内联在 CheckersActivity 中。修改 AI 算法时请同步修改对方文件
package com.gamecenter.app.checkers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 跳棋游戏逻辑（从 CheckersActivity 提取）。
 *
 * <p>负责棋盘状态、合法移动计算、玩家与 AI 落子、升王与胜负判定。
 * 不包含成就系统与 UI 逻辑（模块化后仅保留基本游戏功能）。</p>
 *
 * <p>难度梯度：
 * <ul>
 *   <li>简单（0）：随机走子</li>
 *   <li>中等（1）：Minimax depth=2 + α-β 剪枝</li>
 *   <li>困难（2）：Minimax depth=4 + α-β 剪枝 + 评估（棋子差+王棋加权+升王行位置）</li>
 * </ul>
 * </p>
 */
public class CheckersGame {

    public static final int BOARD_SIZE = 8;
    public static final int EMPTY = CheckersView.EMPTY;
    public static final int BLACK = CheckersView.BLACK;
    public static final int BLACK_KING = CheckersView.BLACK_KING;
    public static final int WHITE = CheckersView.WHITE;
    public static final int WHITE_KING = CheckersView.WHITE_KING;

    public static final int PLAYER_COLOR = BLACK;
    public static final int AI_COLOR = WHITE;

    /** AI 思考最大节点数（防卡顿） */
    private static final int AI_MAX_NODES = 20000;

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private boolean isPlayerTurn = true;
    private int playerCaptures = 0;
    /** AI 难度：0=简单, 1=中等, 2=困难 */
    private int aiLevel = 1;
    private boolean gameOver = false;
    private final Random random = new Random();
    /** AI 搜索节点计数 */
    private int aiNodesSearched;

    public int[][] getBoard() {
        return board;
    }

    public boolean isPlayerTurn() {
        return isPlayerTurn;
    }

    public int getPlayerCaptures() {
        return playerCaptures;
    }

    public int getAiLevel() {
        return aiLevel;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setAiLevel(int level) {
        this.aiLevel = level;
    }

    /**
     * 初始化新游戏棋盘。
     */
    public void startNewGame(int level) {
        this.aiLevel = level;
        isPlayerTurn = true;
        playerCaptures = 0;
        gameOver = false;
        initBoard();
    }

    private void initBoard() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = WHITE;
                }
            }
        }
        for (int r = 5; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = BLACK;
                }
            }
        }
    }

    /**
     * 计算指定棋子的合法移动（跳吃优先）。
     */
    public boolean[][] calculateValidMoves(int row, int col) {
        boolean[][] moves = new boolean[BOARD_SIZE][BOARD_SIZE];
        int piece = board[row][col];

        List<int[]> jumps = getJumpMoves(row, col, piece);
        if (!jumps.isEmpty()) {
            for (int[] jump : jumps) {
                moves[jump[0]][jump[1]] = true;
            }
            return moves;
        }

        int[][] directions = getDirections(piece);
        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            if (isValidPosition(newRow, newCol) && board[newRow][newCol] == EMPTY) {
                moves[newRow][newCol] = true;
            }
        }
        return moves;
    }

    private List<int[]> getJumpMoves(int row, int col, int piece) {
        List<int[]> jumps = new ArrayList<>();
        int[][] directions = getDirections(piece);
        for (int[] dir : directions) {
            int midRow = row + dir[0];
            int midCol = col + dir[1];
            int landRow = row + 2 * dir[0];
            int landCol = col + 2 * dir[1];
            if (isValidPosition(landRow, landCol) && board[landRow][landCol] == EMPTY
                    && isOpponent(board[midRow][midCol], piece)) {
                jumps.add(new int[]{landRow, landCol});
            }
        }
        return jumps;
    }

    private int[][] getDirections(int piece) {
        switch (piece) {
            case BLACK:      return new int[][]{{-1, -1}, {-1, 1}};
            case BLACK_KING: return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            case WHITE:      return new int[][]{{1, -1}, {1, 1}};
            case WHITE_KING: return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            default:         return new int[0][];
        }
    }

    private boolean isOpponent(int piece, int myPiece) {
        if (myPiece == BLACK || myPiece == BLACK_KING) {
            return piece == WHITE || piece == WHITE_KING;
        } else {
            return piece == BLACK || piece == BLACK_KING;
        }
    }

    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    /**
     * 执行玩家移动。
     *
     * @return true 表示移动成功
     */
    public boolean performPlayerMove(int fromRow, int fromCol, int toRow, int toCol) {
        int piece = board[fromRow][fromCol];
        board[fromRow][fromCol] = EMPTY;

        if (Math.abs(toRow - fromRow) == 2) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            board[midRow][midCol] = EMPTY;
            playerCaptures++;
        }

        if (piece == BLACK && toRow == 0) {
            piece = BLACK_KING;
        }
        board[toRow][toCol] = piece;
        return true;
    }

    /**
     * 获取 AI 的移动决策。返回 [fromRow, fromCol, toRow, toCol] 或 null（无棋可走）。
     */
    public int[] getAiMove() {
        List<int[]> allMoves = getAllMoves(AI_COLOR);
        if (allMoves.isEmpty()) return null;

        if (aiLevel == 0) {
            // 简单 AI：随机走
            return allMoves.get(random.nextInt(allMoves.size()));
        }

        // 中等/困难 AI：Minimax + α-β 剪枝
        int depth = (aiLevel == 1) ? 2 : 4;
        aiNodesSearched = 0;
        int[] move = findBestMoveByMinimax(AI_COLOR, depth);
        return move != null ? move : allMoves.get(0);
    }

    /**
     * Minimax + α-β 剪枝搜索最佳走法。
     * 困难档若有多条等价走法，随机选一条增加开局多样性。
     */
    private int[] findBestMoveByMinimax(int color, int depth) {
        List<int[]> moves = getAllMoves(color);
        if (moves.isEmpty()) return null;

        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();

        for (int[] move : moves) {
            int[][] saved = copyBoard(board);
            applyMove(move);
            int score = minimax(depth - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
            restoreBoard(saved);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }

        if (aiLevel == 2 && bestMoves.size() > 1) {
            return bestMoves.get(random.nextInt(bestMoves.size()));
        }
        return bestMoves.get(0);
    }

    /** Minimax 递归搜索 */
    private int minimax(int depth, boolean isMaximizing, int alpha, int beta) {
        if (++aiNodesSearched > AI_MAX_NODES) return evaluateBoard();
        if (depth == 0) return evaluateBoard();

        int color = isMaximizing ? AI_COLOR : PLAYER_COLOR;
        List<int[]> moves = getAllMoves(color);
        if (moves.isEmpty()) {
            return isMaximizing ? -10000 : 10000;
        }

        if (isMaximizing) {
            int best = Integer.MIN_VALUE;
            for (int[] move : moves) {
                int[][] saved = copyBoard(board);
                applyMove(move);
                int val = minimax(depth - 1, false, alpha, beta);
                restoreBoard(saved);
                best = Math.max(best, val);
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int[] move : moves) {
                int[][] saved = copyBoard(board);
                applyMove(move);
                int val = minimax(depth - 1, true, alpha, beta);
                restoreBoard(saved);
                best = Math.min(best, val);
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    /** 评估当前棋盘（AI 视角） */
    private int evaluateBoard() {
        int score = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                int piece = board[r][c];
                switch (piece) {
                    case WHITE:
                        score += 10 + r;
                        break;
                    case WHITE_KING:
                        score += 25;
                        break;
                    case BLACK:
                        score -= 10 + (BOARD_SIZE - 1 - r);
                        break;
                    case BLACK_KING:
                        score -= 25;
                        break;
                }
            }
        }
        return score;
    }

    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(src[r], 0, copy[r], 0, BOARD_SIZE);
        }
        return copy;
    }

    private void restoreBoard(int[][] saved) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(saved[r], 0, board[r], 0, BOARD_SIZE);
        }
    }

    /** 在棋盘上应用走法（不更新 UI） */
    private void applyMove(int[] move) {
        int piece = board[move[0]][move[1]];
        board[move[0]][move[1]] = EMPTY;
        if (Math.abs(move[2] - move[0]) == 2) {
            int midRow = (move[0] + move[2]) / 2;
            int midCol = (move[1] + move[3]) / 2;
            board[midRow][midCol] = EMPTY;
        }
        if (piece == WHITE && move[2] == BOARD_SIZE - 1) {
            piece = WHITE_KING;
        }
        if (piece == BLACK && move[2] == 0) {
            piece = BLACK_KING;
        }
        board[move[2]][move[3]] = piece;
    }

    /**
     * 执行 AI 移动。
     */
    public void performAiMove(int[] move) {
        int piece = board[move[0]][move[1]];
        board[move[0]][move[1]] = EMPTY;

        if (Math.abs(move[2] - move[0]) == 2) {
            int midRow = (move[0] + move[2]) / 2;
            int midCol = (move[1] + move[3]) / 2;
            board[midRow][midCol] = EMPTY;
        }

        if (piece == WHITE && move[2] == BOARD_SIZE - 1) {
            piece = WHITE_KING;
        }
        board[move[2]][move[3]] = piece;
    }

    private List<int[]> getAllMoves(int color) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color || board[r][c] == color + 1) {
                    int[][] directions = getDirections(board[r][c]);
                    for (int[] dir : directions) {
                        int midR = r + dir[0];
                        int midC = c + dir[1];
                        int landR = r + 2 * dir[0];
                        int landC = c + 2 * dir[1];
                        if (isValidPosition(landR, landC) && board[landR][landC] == EMPTY
                                && isOpponent(board[midR][midC], board[r][c])) {
                            moves.add(new int[]{r, c, landR, landC});
                        }
                    }
                }
            }
        }
        if (!moves.isEmpty()) return moves;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color || board[r][c] == color + 1) {
                    int[][] directions = getDirections(board[r][c]);
                    for (int[] dir : directions) {
                        int newR = r + dir[0];
                        int newC = c + dir[1];
                        if (isValidPosition(newR, newC) && board[newR][newC] == EMPTY) {
                            moves.add(new int[]{r, c, newR, newC});
                        }
                    }
                }
            }
        }
        return moves;
    }

    /**
     * 检查游戏是否结束。
     *
     * @return 1=玩家胜, -1=玩家败, 0=未结束
     */
    public int checkGameEnd() {
        int blackCount = countPieces(BLACK) + countPieces(BLACK_KING);
        int whiteCount = countPieces(WHITE) + countPieces(WHITE_KING);

        if (blackCount == 0) return -1;
        if (whiteCount == 0) return 1;
        if (isPlayerTurn && getAllMoves(PLAYER_COLOR).isEmpty()) return -1;
        if (!isPlayerTurn && getAllMoves(AI_COLOR).isEmpty()) return 1;
        return 0;
    }

    private int countPieces(int pieceType) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == pieceType) count++;
            }
        }
        return count;
    }

    public void setPlayerTurn(boolean playerTurn) {
        isPlayerTurn = playerTurn;
    }

    public void setGameOver(boolean over) {
        gameOver = over;
    }

    public boolean allWhiteEaten() {
        return countPieces(WHITE) + countPieces(WHITE_KING) == 0;
    }
}

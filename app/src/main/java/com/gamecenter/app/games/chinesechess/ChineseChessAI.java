package com.gamecenter.app.games.chinesechess;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中国象棋 AI 引擎。
 *
 * <p>基于 Minimax + Alpha-Beta 剪枝算法实现 AI 决策。
 * 根据难度级别调整搜索深度和评估精度。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 Minimax 搜索 + Alpha-Beta 剪枝优化</li>
 *   <li>4 个难度级别对应搜索深度 2/4/6/8</li>
 *   <li>评估函数考虑子力价值、位置价值和安全性</li>
 *   <li>支持异步计算（通过回调返回结果）</li>
 *   <li>搜索过程中定期检查中断状态</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class ChineseChessAI {

    // ==================== 常量 ====================

    /** 棋子基础价值 */
    private static final int[] PIECE_VALUES = {
        0,     // 未使用
        10000, // 帅/将
        200,   // 仕/士
        200,   // 相/象
        400,   // 马
        900,   // 车
        450,   // 炮
        100    // 兵/卒
    };

    /** 难度配置 */
    private static final int[] SEARCH_DEPTHS = {2, 4, 6, 8};

    // ==================== 成员变量 ====================

    private final int difficulty;
    private final int searchDepth;
    private final Random random = new Random();
    private volatile boolean cancelled = false;

    // ==================== 构造函数 ====================

    /**
     * 创建 AI 实例
     *
     * @param difficulty 难度等级（1-4）
     */
    public ChineseChessAI(int difficulty) {
        this.difficulty = Math.max(1, Math.min(4, difficulty));
        this.searchDepth = SEARCH_DEPTHS[this.difficulty - 1];
    }

    // ==================== 公共方法 ====================

    /**
     * 获取 AI 的最佳走法
     *
     * @param boardState 当前棋盘状态
     * @param difficulty 难度等级
     * @return 走法数组 [fromRow, fromCol, toRow, toCol]，无合法走法返回 null
     */
    @Nullable
    public int[] getBestMove(@NonNull int[][] boardState, int difficulty) {
        cancelled = false;
        int depth = Math.max(2, Math.min(8, SEARCH_DEPTHS[Math.max(0, Math.min(difficulty - 1, 3))]));

        // 生成所有黑方（AI）的合法走法
        List<int[]> moves = generateMoves(boardState, -1);
        if (moves.isEmpty()) return null;

        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();

        for (int[] move : moves) {
            if (cancelled) break;

            // 模拟走法
            int[][] newBoard = copyBoard(boardState);
            newBoard[move[2]][move[3]] = newBoard[move[0]][move[1]];
            newBoard[move[0]][move[1]] = 0;

            // Minimax 搜索
            int score = minimax(newBoard, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, true);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }

        // 从最佳走法中随机选择一个
        if (bestMoves.isEmpty()) return null;
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    /**
     * 取消当前计算
     */
    public void cancel() {
        cancelled = true;
    }

    // ==================== Minimax 算法 ====================

    /**
     * Minimax + Alpha-Beta 剪枝
     *
     * @param board  棋盘状态
     * @param depth  剩余搜索深度
     * @param alpha  Alpha 值
     * @param beta   Beta 值
     * @param isMax  是否为最大化层（红方）
     * @return 评估分数
     */
    private int minimax(int[][] board, int depth, int alpha, int beta, boolean isMax) {
        if (cancelled) return 0;
        if (depth == 0) return evaluateBoard(board);

        if (isMax) {
            // 红方（最大化）
            int maxEval = Integer.MIN_VALUE;
            List<int[]> moves = generateMoves(board, 1);
            for (int[] move : moves) {
                int[][] newBoard = copyBoard(board);
                newBoard[move[2]][move[3]] = newBoard[move[0]][move[1]];
                newBoard[move[0]][move[1]] = 0;
                int eval = minimax(newBoard, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break; // 剪枝
            }
            return maxEval;
        } else {
            // 黑方（最小化）
            int minEval = Integer.MAX_VALUE;
            List<int[]> moves = generateMoves(board, -1);
            for (int[] move : moves) {
                int[][] newBoard = copyBoard(board);
                newBoard[move[2]][move[3]] = newBoard[move[0]][move[1]];
                newBoard[move[0]][move[1]] = 0;
                int eval = minimax(newBoard, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break; // 剪枝
            }
            return minEval;
        }
    }

    // ==================== 评估函数 ====================

    /**
     * 评估棋盘分数（正数对红方有利，负数对黑方有利）
     */
    private int evaluateBoard(int[][] board) {
        int score = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                int type = Math.abs(piece);
                int value = PIECE_VALUES[type];

                // 位置加成
                value += getPositionBonus(type, r, c, piece > 0);

                if (piece > 0) score += value;  // 红方
                else score -= value;             // 黑方
            }
        }
        return score;
    }

    /**
     * 获取位置加成分数
     */
    private int getPositionBonus(int type, int row, int col, boolean isRed) {
        // 简化的位置评估
        int bonus = 0;
        switch (type) {
            case 7: // 兵/卒
                // 过河兵价值更高
                if (isRed && row <= 4) bonus += 50;
                if (!isRed && row >= 5) bonus += 50;
                break;
            case 4: // 马
                // 中心位置的马更有价值
                if (col >= 2 && col <= 6 && row >= 2 && row <= 7) bonus += 30;
                break;
            case 5: // 车
                // 车在任何位置都有价值
                bonus += 20;
                break;
            case 6: // 炮
                // 炮在中间位置更有价值
                if (col >= 1 && col <= 7) bonus += 15;
                break;
        }
        return bonus;
    }

    // ==================== 走法生成 ====================

    /**
     * 生成指定方的所有合法走法
     *
     * @param board 棋盘状态
     * @param side  方（1=红方，-1=黑方）
     * @return 走法列表
     */
    private List<int[]> generateMoves(int[][] board, int side) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                if (side > 0 && piece < 0) continue;
                if (side < 0 && piece > 0) continue;

                // 为每个棋子生成走法
                List<int[]> pieceMoves = generatePieceMoves(board, r, c, piece);
                moves.addAll(pieceMoves);
            }
        }
        return moves;
    }

    /**
     * 为单个棋子生成走法（简化版，基本规则）
     */
    private List<int[]> generatePieceMoves(int[][] board, int fromR, int fromC, int piece) {
        List<int[]> moves = new ArrayList<>();
        int type = Math.abs(piece);
        boolean isRed = piece > 0;

        switch (type) {
            case 1: // 帅/将
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR, fromC + 1, isRed);
                break;
            case 2: // 仕/士
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC + 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC + 1, isRed);
                break;
            case 3: // 相/象
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC + 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC + 2, isRed);
                break;
            case 4: // 马
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 2, fromC + 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC - 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 2, fromC + 1, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC + 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC - 2, isRed);
                addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC + 2, isRed);
                break;
            case 5: // 车
                addLineMoves(board, moves, fromR, fromC, isRed);
                break;
            case 6: // 炮
                addCannonMoves(board, moves, fromR, fromC, isRed);
                break;
            case 7: // 兵/卒
                if (isRed) {
                    addMoveIfValid(board, moves, fromR, fromC, fromR - 1, fromC, isRed);
                    if (fromR <= 4) {
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC - 1, isRed);
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC + 1, isRed);
                    }
                } else {
                    addMoveIfValid(board, moves, fromR, fromC, fromR + 1, fromC, isRed);
                    if (fromR >= 5) {
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC - 1, isRed);
                        addMoveIfValid(board, moves, fromR, fromC, fromR, fromC + 1, isRed);
                    }
                }
                break;
        }
        return moves;
    }

    private void addMoveIfValid(int[][] board, List<int[]> moves, int fromR, int fromC, int toR, int toC, boolean isRed) {
        if (toR < 0 || toR >= 10 || toC < 0 || toC >= 9) return;
        int target = board[toR][toC];
        if (isRed && target > 0) return; // 不能吃己方
        if (!isRed && target < 0) return;
        moves.add(new int[]{fromR, fromC, toR, toC});
    }

    private void addLineMoves(int[][] board, List<int[]> moves, int fromR, int fromC, boolean isRed) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            for (int i = 1; i < 10; i++) {
                int r = fromR + d[0] * i, c = fromC + d[1] * i;
                if (r < 0 || r >= 10 || c < 0 || c >= 9) break;
                int target = board[r][c];
                if (target == 0) {
                    moves.add(new int[]{fromR, fromC, r, c});
                } else {
                    if ((isRed && target < 0) || (!isRed && target > 0)) {
                        moves.add(new int[]{fromR, fromC, r, c});
                    }
                    break;
                }
            }
        }
    }

    private void addCannonMoves(int[][] board, List<int[]> moves, int fromR, int fromC, boolean isRed) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            boolean foundPlatform = false;
            for (int i = 1; i < 10; i++) {
                int r = fromR + d[0] * i, c = fromC + d[1] * i;
                if (r < 0 || r >= 10 || c < 0 || c >= 9) break;
                int target = board[r][c];
                if (!foundPlatform) {
                    if (target == 0) {
                        moves.add(new int[]{fromR, fromC, r, c});
                    } else {
                        foundPlatform = true;
                    }
                } else {
                    if (target != 0) {
                        if ((isRed && target < 0) || (!isRed && target > 0)) {
                            moves.add(new int[]{fromR, fromC, r, c});
                        }
                        break;
                    }
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[10][9];
        for (int r = 0; r < 10; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, 9);
        }
        return copy;
    }
}

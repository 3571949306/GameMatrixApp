// =============================================================================
// 同步说明：本文件（app 内嵌版）与 module-store 版本保持逐字同步，为单一真相源。
// 修改本文件时请同步修改：
//   module-store/feature/games/games/chinesechess/src/main/java/com/gamecenter/app/chinesechess/ChineseChessAI.java
// 两份副本因包名不同（com.gamecenter.app.games.chinesechess vs com.gamecenter.app.chinesechess）
// 无法合并为同一文件，分别服务于宿主与动态模块两个编译目标。
// =============================================================================
package com.gamecenter.app.games.chinesechess;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.core.common.GameAI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中国象棋 AI 引擎。
 *
 * <p>基于 Minimax + Alpha-Beta 剪枝算法实现 AI 决策，并叠加以下增强以提升棋力与正确性：
 * <ul>
 *   <li>5 个难度级别对应搜索深度 1/2/3/4/5；</li>
 *   <li>随机性机制：根据难度级别选择评分差异阈值内的候选走法，增加对局多样性；</li>
 *   <li>开局库：前几步使用预设走法，避免开局走法固定；</li>
 *   <li>静态搜索（Quiescence Search）：仅在搜索边界对吃子序列继续展开，消除"地平线效应"
 *       （避免 AI 在搜索末端吃掉大子却看不见随后被反吃）；</li>
 *   <li>将军延伸（Check Extension）：被将军时额外展开一层，提升战术与杀棋识别；</li>
 *   <li>MVV-LVA 走法排序：优先搜索"以大吃小"的着法，显著提升 Alpha-Beta 剪枝效率，
 *       在相同时间内达到更深的等效搜索；</li>
 *   <li>基于层数的将死距离评分：优先选择更快将死 / 更晚被将死的路线（象棋中困毙亦判负）；</li>
 *   <li>评估函数综合子力价值、位置价值（棋子价值表）与机动性；</li>
 *   <li>支持异步计算与取消（通过 {@link GameAI} 契约的 cancel/isThinking）。</li>
 * </ul>
 * </p>
 *
 * <p>约定：棋盘用 10×9 的 int 矩阵表示，正值=红子、负值=黑子，
 * 绝对值 1..7 对应 将/仕/相/马/车/炮/兵。
 * 默认执黑（side = -1），通过 {@link #getBestMove(int[][], int, int)} 可指定任意视角。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 2.0
 * @since 2026-06-19
 */
public class ChineseChessAI implements GameAI {

    // ==================== 常量 ====================

    /** 棋子基础价值（索引为棋子类型 0..7） */
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

    /** 难度配置（搜索深度） - 大师档从6降到5，避免过长时间思考 */
    private static final int[] SEARCH_DEPTHS = {1, 2, 3, 4, 5};

    /** 开局库走法 - 14种常见开局，均为红方视角的合法着法（行6~9），增加开局多样性。
     *  注意：旧版本存在坐标错误（如 {9,4,7,4} 实为帅走两格、{9,1,7,1} 实为马直行），
     *  会生成非法着法。此处全部改为真实合法开局，并由 {@link #getOpeningMove} 二次校验兜底。 */
    private static final int[][] OPENING_MOVES = {
        {7, 1, 7, 4},  // 炮二平五（中炮）
        {7, 7, 7, 4},  // 炮八平五（反手中炮）
        {7, 1, 7, 3},  // 炮二平三（兵底炮）
        {7, 7, 7, 5},  // 炮八平三（卒底炮变体）
        {9, 1, 7, 2},  // 马二进三（起马）
        {9, 7, 7, 6},  // 马八进七（起马）
        {6, 0, 5, 0},  // 兵七进一（仙人指路）
        {6, 8, 5, 8},  // 兵三进一（仙人指路）
        {6, 2, 5, 2},  // 兵七进一变体
        {6, 6, 5, 6},  // 兵三进一变体
        {9, 0, 8, 0},  // 车一进一（横车）
        {9, 8, 8, 8},  // 车九进一（横车）
        {7, 1, 7, 4},  // 炮二平五（中炮，重复以增加概率）
        {9, 1, 7, 2}   // 马二进三（起马，重复以增加概率）
    };

    /** 将死分数（远大于最大子力评估，确保对将死给予最高优先级） */
    private static final int MATE_SCORE = 1_000_000;

    /** 静态搜索最大层数（仅在吃子序列上展开，避免无限递归） */
    private static final int QSEARCH_MAX_DEPTH = 6;

    /** 允许触发将军延伸的层数上限。 */
    private static final int CHECK_EXTENSION_PLY_LIMIT = 12;

    /** 搜索的绝对层数上限；即使连续将军也不得突破，避免异常局面拖垮线程。 */
    private static final int MAX_SEARCH_PLY = 24;

    // ==================== 成员变量 ====================

    private final int difficulty;
    private final int searchDepth;
    private final Random random = new Random();
    private volatile boolean cancelled = false;
    private volatile boolean thinking = false;

    /** 外部传入的当前对局局面历史（可选），用于在根节点惩罚重复局面着法 */
    private List<Long> positionHistory = null;

    // ==================== 构造函数 ====================

    /**
     * 创建 AI 实例
     *
     * @param difficulty 难度等级（1-5）
     */
    public ChineseChessAI(int difficulty) {
        this.difficulty = Math.max(1, Math.min(5, difficulty));
        this.searchDepth = SEARCH_DEPTHS[this.difficulty - 1];
    }

    /**
     * 设置当前对局的局面历史，AI 会在根节点避免选择导致已出现局面的着法。
     * <p>传入的历史应为当前对局的局面指纹列表（由游戏逻辑层记录）。</p>
     *
     * @param history 局面指纹历史，传 null 表示不启用重复规避
     */
    public void setPositionHistory(@Nullable List<Long> history) {
        this.positionHistory = history;
    }

    // ==================== 公共方法 ====================

    /**
     * 获取 AI 的最佳走法（默认执黑）
     *
     * @param boardState 当前棋盘状态
     * @param difficulty 难度等级
     * @return 走法数组 [fromRow, fromCol, toRow, toCol]，无合法走法返回 null
     */
    @Nullable
    public int[] getBestMove(@NonNull int[][] boardState, int difficulty) {
        return getBestMove(boardState, difficulty, -1);
    }

    /**
     * 获取 AI 的最佳走法（支持指定视角）
     *
     * @param boardState 当前棋盘状态
     * @param difficulty 难度等级
     * @param aiSide     AI 执子方：1=红方，-1=黑方
     * @return 走法数组 [fromRow, fromCol, toRow, toCol]，无合法走法返回 null
     */
    @Nullable
    public int[] getBestMove(@NonNull int[][] boardState, int difficulty, int aiSide) {
        cancelled = false;
        int depth = Math.max(1, Math.min(6, SEARCH_DEPTHS[Math.max(0, Math.min(difficulty - 1, 4))]));

        // 尝试开局库
        int[] openingMove = getOpeningMove(boardState, aiSide);
        if (openingMove != null) return openingMove;

        // 生成 AI 方的合法走法（过滤送将/白脸将的着法）
        List<int[]> moves = generateLegalMoves(boardState, aiSide);
        if (moves.isEmpty()) return null;

        // 根节点同样按 MVV-LVA 排序，提升剪枝与等分时择优
        orderMovesByMvvLva(moves, boardState);

        thinking = true;
        // 注意符号约定：minimax 返回的是"红方视角"评分（越大对红方越有利）。
        // AI 执红时最大化该评分，执黑时最小化。
        boolean maximize = (aiSide == 1);
        List<int[]> allMoves = new ArrayList<>();
        List<Integer> allScores = new ArrayList<>();

        for (int[] move : moves) {
            if (cancelled) break;

            // 模拟走法
            int[][] newBoard = copyBoard(boardState);
            newBoard[move[2]][move[3]] = newBoard[move[0]][move[1]];
            newBoard[move[0]][move[1]] = 0;

            // Minimax 搜索（根走子后轮到对方，isMax = !maximize）
            int score = minimax(newBoard, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !maximize, 1);

            // 根节点重复局面惩罚：若该着法导致已经出现过的局面，
            // 对 AI 不利（鼓励 AI 打破长将/循环，而不是消极重复）。
            if (positionHistory != null) {
                // AI 走子后轮到对方；棋子编码和走棋方编码与游戏逻辑层完全一致。
                long newHash = computePositionHash(newBoard, -aiSide);
                if (countPositionInHistory(newHash) >= 1) {
                    int repetitionPenalty = 50000; // 接近一个车但小于将死分
                    if (maximize) score -= repetitionPenalty;
                    else score += repetitionPenalty;
                }
            }

            allMoves.add(move);
            allScores.add(maximize ? score : -score);
        }

        thinking = false;
        if (allMoves.isEmpty()) return null;
        // 使用随机选择策略，根据难度级别选择候选走法
        return selectMoveWithRandomness(allMoves, allScores, difficulty);
    }

    /**
     * 根据难度选择候选走法：收集所有走法和评分，然后根据难度阈值随机选择
     */
    private int[] selectMoveWithRandomness(List<int[]> moves, List<Integer> scores, int difficulty) {
        int bestScore = scores.stream().max(Integer::compareTo).orElse(0);
        int threshold = getThresholdForDifficulty(difficulty);

        List<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            if (Math.abs(scores.get(i) - bestScore) <= threshold) {
                candidates.add(moves.get(i));
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 根据难度级别返回评分差异阈值
     */
    private int getThresholdForDifficulty(int difficulty) {
        switch (difficulty) {
            case 1: return 200;  // 入门
            case 2: return 100;  // 初级
            case 3: return 50;   // 中级
            case 4: return 20;   // 高级
            case 5: return 0;    // 大师
            default: return 50;
        }
    }

    /**
     * 获取开局走法
     * <p>OPENING_MOVES 以红方视角定义（行号 6~9）。当 AI 执黑（aiSide=-1）时，
     * 需将行号按棋盘上下镜像（r -> 9 - r），否则黑方会返回红方走法导致坐标错乱。
     *
     * @param board  当前棋盘
     * @param aiSide AI 执子方：1=红方，-1=黑方
     * @return 开局走法 [fromRow, fromCol, toRow, toCol]，非开局位置返回 null
     */
    private int[] getOpeningMove(int[][] board, int aiSide) {
        if (!isOpeningPosition(board)) return null;
        int[] m = OPENING_MOVES[random.nextInt(OPENING_MOVES.length)];
        int fr = m[0], fc = m[1], tr = m[2], tc = m[3];
        if (aiSide == -1) {
            // 黑方走法：行号上下镜像，列号不变
            fr = 9 - m[0];
            tr = 9 - m[2];
        }
        // 二次校验（集中闸门之外的冗余防线）：起点必须有己方棋子，且走法形状合法、
        // 走后不送将。任何非法开局着法一律拒绝，回退到搜索，
        // 保证开局库绝不输出非法着法（如旧版的"帅走两格""马直行"）。
        int piece = board[fr][fc];
        if (piece == 0) return null;
        if ((aiSide > 0 && piece < 0) || (aiSide < 0 && piece > 0)) return null;
        if (!attacksSquare(board, fr, fc, tr, tc)) return null;
        if (!isMoveLegal(board, fr, fc, tr, tc, aiSide)) return null;
        return new int[]{fr, fc, tr, tc};
    }

    /**
     * 判断是否为开局位置
     */
    private boolean isOpeningPosition(int[][] board) {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] != getInitialPiece(r, c)) return false;
            }
        }
        return true;
    }

    /**
     * 获取初始棋盘位置的棋子
     */
    private int getInitialPiece(int row, int col) {
        // 黑方（上方）
        if (row == 0) {
            if (col == 0 || col == 8) return -5; // 车
            if (col == 1 || col == 7) return -4; // 马
            if (col == 2 || col == 6) return -3; // 相
            if (col == 3 || col == 5) return -2; // 仕
            if (col == 4) return -1; // 将
        }
        if (row == 2) {
            if (col == 1 || col == 7) return -6; // 炮
        }
        if (row == 3) {
            if (col == 0 || col == 2 || col == 4 || col == 6 || col == 8) return -7; // 卒
        }
        // 红方（下方）
        if (row == 9) {
            if (col == 0 || col == 8) return 5; // 车
            if (col == 1 || col == 7) return 4; // 马
            if (col == 2 || col == 6) return 3; // 相
            if (col == 3 || col == 5) return 2; // 仕
            if (col == 4) return 1; // 帅
        }
        if (row == 7) {
            if (col == 1 || col == 7) return 6; // 炮
        }
        if (row == 6) {
            if (col == 0 || col == 2 || col == 4 || col == 6 || col == 8) return 7; // 兵
        }
        return 0;
    }

    /**
     * 取消当前计算
     */
    public void cancel() {
        cancelled = true;
        thinking = false;
    }

    @Override
    public boolean isThinking() {
        return thinking;
    }

    // ==================== Minimax 算法 ====================

    /**
     * Minimax + Alpha-Beta 剪枝（含将军延伸、静态搜索与将死距离评分）。
     *
     * @param board  棋盘状态
     * @param depth  剩余搜索深度
     * @param alpha  Alpha 值
     * @param beta   Beta 值
     * @param isMax  是否为最大化层（红方）
     * @param ply    距根节点的层数（用于将军/将死距离评分与延伸上限）
     * @return 评估分数（红方视角，越大对红方越有利）
     */
    private int minimax(int[][] board, int depth, int alpha, int beta, boolean isMax, int ply) {
        if (cancelled) return 0;

        int sideSign = isMax ? 1 : -1;
        List<int[]> moves = generateLegalMoves(board, sideSign);
        if (moves.isEmpty()) {
            // 当前走子方无合法着法：被将死或困毙（象棋规则均判负）。
            // 扣除层数使"更快将死 / 更晚被将死"获得更高分数。
            return isMax ? -(MATE_SCORE - ply) : (MATE_SCORE - ply);
        }

        if (ply >= MAX_SEARCH_PLY) return evaluateBoard(board);

        if (depth <= 0) {
            // 到达搜索边界，转入静态搜索以稳定子力评估
            return quiescence(board, alpha, beta, QSEARCH_MAX_DEPTH, isMax, ply);
        }

        // 将军延伸：被将军时本层不递减深度，强制看清杀棋线路（受层数上限保护）
        boolean inCheck = isInCheck(board, sideSign);
        int childDepth = (inCheck && ply < CHECK_EXTENSION_PLY_LIMIT) ? depth : depth - 1;

        orderMovesByMvvLva(moves, board);

        if (isMax) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                if (cancelled) break;
                int[][] newBoard = copyBoard(board);
                newBoard[move[2]][move[3]] = newBoard[move[0]][move[1]];
                newBoard[move[0]][move[1]] = 0;
                int eval = minimax(newBoard, childDepth, alpha, beta, false, ply + 1);
                if (eval > maxEval) maxEval = eval;
                if (eval > alpha) alpha = eval;
                if (beta <= alpha) break; // 剪枝
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : moves) {
                if (cancelled) break;
                int[][] newBoard = copyBoard(board);
                newBoard[move[2]][move[3]] = newBoard[move[0]][move[1]];
                newBoard[move[0]][move[1]] = 0;
                int eval = minimax(newBoard, childDepth, alpha, beta, true, ply + 1);
                if (eval < minEval) minEval = eval;
                if (eval < beta) beta = eval;
                if (beta <= alpha) break; // 剪枝
            }
            return minEval;
        }
    }

    // ==================== 静态搜索（Quiescence Search） ====================

    /**
     * 静态搜索：在搜索边界对吃子（及被将军时的全部应着）继续展开，消除地平线效应。
     *
     * <p>非将军局面采用"stand-pat"策略——若当前局面分已足够好则直接剪枝；否则仅尝试吃子。
     * 被将军局面必须枚举全部合法应着（含非吃子逃将），否则会遗漏被将死的判定。</p>
     */
    private int quiescence(int[][] board, int alpha, int beta, int qdepth, boolean isMax, int ply) {
        if (cancelled) return 0;
        if (qdepth <= 0 || ply >= MAX_SEARCH_PLY) return evaluateBoard(board);

        int sideSign = isMax ? 1 : -1;
        boolean inCheck = isInCheck(board, sideSign);

        if (inCheck) {
            List<int[]> evasions = generateLegalMoves(board, sideSign);
            if (evasions.isEmpty()) {
                // 无应着 = 被将死
                return isMax ? -(MATE_SCORE - ply) : (MATE_SCORE - ply);
            }
            orderMovesByMvvLva(evasions, board);
            if (isMax) {
                int maxEval = Integer.MIN_VALUE;
                for (int[] m : evasions) {
                    if (cancelled) break;
                    int[][] nb = copyBoard(board);
                    nb[m[2]][m[3]] = nb[m[0]][m[1]];
                    nb[m[0]][m[1]] = 0;
                    int eval = quiescence(nb, alpha, beta, qdepth - 1, false, ply + 1);
                    if (eval > maxEval) maxEval = eval;
                    if (eval > alpha) alpha = eval;
                    if (beta <= alpha) break;
                }
                return maxEval;
            } else {
                int minEval = Integer.MAX_VALUE;
                for (int[] m : evasions) {
                    if (cancelled) break;
                    int[][] nb = copyBoard(board);
                    nb[m[2]][m[3]] = nb[m[0]][m[1]];
                    nb[m[0]][m[1]] = 0;
                    int eval = quiescence(nb, alpha, beta, qdepth - 1, true, ply + 1);
                    if (eval < minEval) minEval = eval;
                    if (eval < beta) beta = eval;
                    if (beta <= alpha) break;
                }
                return minEval;
            }
        }

        // 非将军：stand-pat
        int standPat = evaluateBoard(board);
        if (isMax) {
            if (standPat >= beta) return beta;
            if (standPat > alpha) alpha = standPat;
        } else {
            if (standPat <= alpha) return alpha;
            if (standPat < beta) beta = standPat;
        }

        List<int[]> caps = generateCaptureMoves(board, sideSign);
        orderMovesByMvvLva(caps, board);
        int oppSign = -sideSign;

        for (int[] m : caps) {
            if (cancelled) break;
            int[][] nb = copyBoard(board);
            nb[m[2]][m[3]] = nb[m[0]][m[1]];
            nb[m[0]][m[1]] = 0;

            // 若此吃子后对方无合法着法（含困毙），即构成将死，直接给将死分
            if (generateLegalMoves(nb, oppSign).isEmpty()) {
                int mate = isMax ? (MATE_SCORE - (ply + 1)) : -(MATE_SCORE - (ply + 1));
                if (isMax) {
                    if (mate > alpha) alpha = mate;
                    if (alpha >= beta) return beta;
                } else {
                    if (mate < beta) beta = mate;
                    if (beta <= alpha) return alpha;
                }
                continue;
            }

            int eval = quiescence(nb, alpha, beta, qdepth - 1, !isMax, ply + 1);
            if (isMax) {
                if (eval > alpha) alpha = eval;
                if (alpha >= beta) return beta;
            } else {
                if (eval < beta) beta = eval;
                if (beta <= alpha) return alpha;
            }
        }
        return isMax ? alpha : beta;
    }

    // ==================== 评估函数 ====================

    /**
     * 评估棋盘分数（正数对红方有利，负数对黑方有利）。
     * 综合子力价值、位置价值（棋子价值表）与机动性（可走步数之差）。
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
        // 机动性：可走步数之差（轻量近似，鼓励积极调动）
        score += (countPseudoMoves(board, 1) - countPseudoMoves(board, -1)) * 2;
        return score;
    }

    /**
     * 统计某方的伪合法着法数量（用于评估中的机动性项，不做送将过滤以保证性能）。
     */
    private int countPseudoMoves(int[][] board, int side) {
        int count = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                if (side > 0 && piece < 0) continue;
                if (side < 0 && piece > 0) continue;
                count += generatePieceMoves(board, r, c, piece).size();
            }
        }
        return count;
    }

    /**
     * 获取位置加成分数（棋子价值表 PST 的轻量实现）。
     * 以红方视角计算：row 越小越靠近敌方底线（越深入敌阵越好）。
     */
    private int getPositionBonus(int type, int row, int col, boolean isRed) {
        int centerCol = 4 - Math.abs(col - 4); // 0..4，越大越居中
        switch (type) {
            case 7: { // 兵/卒
                int bonus = 0;
                boolean crossed = isRed ? (row <= 4) : (row >= 5);
                if (crossed) bonus += 60;                 // 过河兵价值大增
                int advance = isRed ? (4 - row) : (row - 5);
                if (advance > 0) bonus += advance * 10;   // 越深入敌阵越好
                if (col >= 3 && col <= 5) bonus += 12;    // 中兵控制中线
                return bonus;
            }
            case 4: { // 马：中心强、避免边角、鼓励前压
                int bonus = centerCol * 10;
                int advance = isRed ? (4 - row) : (row - 5);
                if (advance > 0) bonus += advance * 6;
                if (col == 0 || col == 8) bonus -= 12;    // 边线马受限
                if (row == 0 || row == 9) bonus -= 6;
                return bonus;
            }
            case 5: // 车：中线与过河活跃
                return (col >= 3 && col <= 5 ? 10 : 0) + ((isRed ? (4 - row) : (row - 5)) > 0 ? 3 : 0);
            case 6: // 炮：中线与河界附近活跃
                return (col >= 2 && col <= 6 ? 10 : 0) + ((isRed ? (4 - row) : (row - 5)) > 0 ? 2 : 0);
            case 2: // 仕/士：贴身护将，居中列最佳
                return (col == 4 ? 10 : (col == 3 || col == 5 ? 4 : 0));
            case 3: // 相/象：守护己方半场，略偏好居中
                return (isRed ? (row >= 4 ? 8 : 0) : (row <= 5 ? 8 : 0)) + centerCol * 2;
            case 1: // 将/帅：居中更安全，隐藏于己方半场
                return (col == 4 ? 8 : 0) + (isRed ? (row >= 4 ? 4 : 0) : (row <= 5 ? 4 : 0));
        }
        return 0;
    }

    // ==================== 走法生成 ====================

    /**
     * 生成指定方的所有伪合法走法（不校验是否送将）。
     */
    private List<int[]> generateMoves(int[][] board, int side) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                if (side > 0 && piece < 0) continue;
                if (side < 0 && piece > 0) continue;

                List<int[]> pieceMoves = generatePieceMoves(board, r, c, piece);
                moves.addAll(pieceMoves);
            }
        }
        return moves;
    }

    /**
     * 为单个棋子生成伪合法走法（基本走子规则）。
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
                // 飞将：两将同列且中间无子时，将/帅可以直接吃掉对方将/帅。
                int[] enemyGeneral = findKing(board, isRed ? -1 : 1);
                if (enemyGeneral != null && enemyGeneral[1] == fromC
                        && pathClear(board, fromR, fromC, enemyGeneral[0], enemyGeneral[1])) {
                    moves.add(new int[]{fromR, fromC, enemyGeneral[0], enemyGeneral[1]});
                }
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
        // 校验棋子基本走法（马腿、象眼、九宫、过河、炮架等），防止 AI 生成非法着法
        if (!attacksSquare(board, fromR, fromC, toR, toC)) return;
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

    /**
     * 生成 side 方的全部合法吃子着法（过滤送将/白脸将），用于静态搜索。
     */
    private List<int[]> generateCaptureMoves(int[][] board, int side) {
        List<int[]> caps = new ArrayList<>();
        for (int[] m : generateMoves(board, side)) {
            int target = board[m[2]][m[3]];
            if (target == 0) continue; // 仅保留吃子
            if (side > 0 && target < 0) {
                // 红吃黑
            } else if (side < 0 && target > 0) {
                // 黑吃红
            } else {
                continue;
            }
            if (isMoveLegal(board, m[0], m[1], m[2], m[3], side)) caps.add(m);
        }
        return caps;
    }

    /**
     * MVV-LVA 走法排序：优先搜索"以大吃小"（高价值受害者在前、低价值攻击者在前）。
     * 安静着法（无受害者）排在最后，从而最大化 Alpha-Beta 剪枝效率。
     */
    private void orderMovesByMvvLva(List<int[]> moves, int[][] board) {
        moves.sort((a, b) -> {
            int va = victimValue(board, b); // 先按受害者价值降序
            int vb = victimValue(board, a);
            if (va != vb) return va - vb;
            int aa = attackerValue(board, a); // 同受害者时按攻击者价值升序
            int ab = attackerValue(board, b);
            return aa - ab;
        });
    }

    private int victimValue(int[][] board, int[] m) {
        int t = Math.abs(board[m[2]][m[3]]);
        return t == 0 ? 0 : PIECE_VALUES[t];
    }

    private int attackerValue(int[][] board, int[] m) {
        return PIECE_VALUES[Math.abs(board[m[0]][m[1]])];
    }

    // ==================== 工具方法 ====================

    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[10][9];
        for (int r = 0; r < 10; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, 9);
        }
        return copy;
    }

    /**
     * 计算棋盘局面指纹，与游戏逻辑层保持一致（含下一走棋方）。
     */
    private long computePositionHash(int[][] board, int sideToMove) {
        long hash = 17;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int p = board[r][c];
                if (p != 0) {
                    hash = hash * 31 + Math.abs(p);
                    hash = hash * 31 + (p > 0 ? 1 : 0);
                    hash = hash * 31 + c;
                    hash = hash * 31 + r;
                }
            }
        }
        return hash * 31 + (sideToMove > 0 ? 1 : 0);
    }

    private int countPositionInHistory(long hash) {
        if (positionHistory == null) return 0;
        int count = 0;
        for (long h : positionHistory) {
            if (h == hash) count++;
        }
        return count;
    }

    // ==================== 合法性 / 将死检测 ====================

    /** 判断 (fr,fc) 棋子能否在棋盘 b 上攻击 (tr,tc)（用于将军检测）。 */
    private static boolean attacksSquare(int[][] b, int fr, int fc, int tr, int tc) {
        if (fr == tr && fc == tc) return false;
        int piece = b[fr][fc];
        if (piece == 0) return false;
        int target = b[tr][tc];
        if (piece > 0 && target > 0) return false;
        if (piece < 0 && target < 0) return false;
        int type = Math.abs(piece);
        int dr = tr - fr, dc = tc - fc;
        switch (type) {
            case 1: { // 将/帅
                // 飞将吃将不受九宫一步限制。
                if (Math.abs(target) == 1 && target == -piece && fc == tc
                        && pathClear(b, fr, fc, tr, tc)) return true;
                boolean inPalace = piece > 0 ? (tr >= 7 && tr <= 9 && tc >= 3 && tc <= 5)
                        : (tr >= 0 && tr <= 2 && tc >= 3 && tc <= 5);
                return inPalace && Math.abs(dr) + Math.abs(dc) == 1;
            }
            case 2: { // 仕/士
                boolean inPalace = piece > 0 ? (tr >= 7 && tr <= 9 && tc >= 3 && tc <= 5)
                        : (tr >= 0 && tr <= 2 && tc >= 3 && tc <= 5);
                return inPalace && Math.abs(dr) == 1 && Math.abs(dc) == 1;
            }
            case 3: { // 相/象
                if (Math.abs(dr) != 2 || Math.abs(dc) != 2) return false;
                if (piece > 0 && tr < 5) return false;
                if (piece < 0 && tr > 4) return false;
                return b[fr + dr / 2][fc + dc / 2] == 0;
            }
            case 4: { // 马
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) return false;
                if (Math.abs(dr) == 2) return b[fr + dr / 2][fc] == 0;
                return b[fr][fc + dc / 2] == 0;
            }
            case 5: // 车
                if (dr != 0 && dc != 0) return false;
                return pathClear(b, fr, fc, tr, tc);
            case 6: { // 炮
                if (dr != 0 && dc != 0) return false;
                int cnt = piecesBetween(b, fr, fc, tr, tc);
                if (target == 0) return cnt == 0;
                return cnt == 1;
            }
            case 7: // 兵/卒
                if (piece > 0) {
                    if (fr >= 5) return dr == -1 && dc == 0;
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                } else {
                    if (fr <= 4) return dr == 1 && dc == 0;
                    return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
        }
        return false;
    }

    private static boolean pathClear(int[][] b, int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) if (b[r1][c] != 0) return false;
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) if (b[r][c1] != 0) return false;
        }
        return true;
    }

    private static int piecesBetween(int[][] b, int r1, int c1, int r2, int c2) {
        int count = 0;
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) if (b[r1][c] != 0) count++;
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) if (b[r][c1] != 0) count++;
        }
        return count;
    }

    private static int[] findKing(int[][] b, int side) {
        int target = side > 0 ? 1 : -1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (b[r][c] == target) return new int[]{r, c};
            }
        }
        return null;
    }

    /** 判断 side 方在棋盘 b 上是否被将军（含"白脸将/对脸"规则）。 */
    private static boolean isInCheck(int[][] b, int side) {
        int[] king = findKing(b, side);
        // 将/帅已被吃时视为处于不可解的被将状态，阻止搜索继续生成无王着法。
        if (king == null) return true;
        int kr = king[0], kc = king[1];
        int attacker = side > 0 ? -1 : 1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int p = b[r][c];
                if (p == 0) continue;
                if ((side > 0 && p > 0) || (side < 0 && p < 0)) continue;
                if (attacksSquare(b, r, c, kr, kc)) return true;
            }
        }
        // 白脸将（两将照面）：检测敌方将/帅是否与本方将/帅同列且中间无子
        int[] enemyKing = findKing(b, attacker);
        if (enemyKing != null && enemyKing[1] == kc) {
            boolean clear = true;
            int lo = Math.min(kr, enemyKing[0]) + 1;
            int hi = Math.max(kr, enemyKing[0]);
            for (int r = lo; r < hi; r++) {
                if (b[r][kc] != 0) { clear = false; break; }
            }
            if (clear) return true;
        }
        return false;
    }

    /** 走子后 side 方将/帅是否仍被将军（用于合法性校验）。 */
    private boolean isMoveLegal(int[][] b, int fr, int fc, int tr, int tc, int side) {
        int[][] nb = copyBoard(b);
        nb[tr][tc] = nb[fr][fc];
        nb[fr][fc] = 0;
        return !isInCheck(nb, side);
    }

    /** 生成 side 方的全部合法着法（过滤送将/白脸将的着法）。 */
    private List<int[]> generateLegalMoves(int[][] b, int side) {
        List<int[]> legal = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int p = b[r][c];
                if (p == 0) continue;
                if (side > 0 && p < 0) continue;
                if (side < 0 && p > 0) continue;
                for (int[] m : generatePieceMoves(b, r, c, p)) {
                    if (isMoveLegal(b, r, c, m[2], m[3], side)) legal.add(m);
                }
            }
        }
        return legal;
    }
}

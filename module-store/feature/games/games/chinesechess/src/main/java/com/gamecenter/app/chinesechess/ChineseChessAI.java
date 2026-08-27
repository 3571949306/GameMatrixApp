// 同步说明：此文件与 app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java 保持同步，修改时请同步修改对方文件
package com.gamecenter.app.chinesechess;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.core.common.GameAI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 难度配置（搜索深度）。高难度从原来的 3 层提升到 4 层，避免被同引擎
     * depth-4 稳定压制；大师档在子力较少时再进入 5 层，控制开中局响应时间。
     */
    private static final int[] SEARCH_DEPTHS = {1, 2, 4, 4};

    /** 大师档仅在残局启用第 5 层搜索，避免开局分支爆炸。 */
    private static final int MASTER_ENDGAME_PIECE_LIMIT = 14;

    /**
     * 各难度默认搜索时间预算（毫秒）。到时 minimax 立即返回当前已知最佳，
     * 不再继续展开新分支，确保玩家可见的等待上限可控。
     */
    private static final long[] DEFAULT_MAX_TIME_MS = {200L, 800L, 2000L, 5000L};

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

    /** 根节点第二次出现同一局面时的警告惩罚。 */
    private static final int REPEAT_WARNING_PENALTY = 1200;

    /** 安静着法立即原路返回的惩罚，降低无意义来回走子。 */
    private static final int IMMEDIATE_REVERSAL_PENALTY = 90;

    /** 单次搜索的置换表容量上限，防止长对局产生不可控内存增长。 */
    private static final int MAX_TRANSPOSITION_ENTRIES = 120_000;

    private static final int TT_EXACT = 0;
    private static final int TT_LOWER_BOUND = 1;
    private static final int TT_UPPER_BOUND = 2;

    // ==================== 成员变量 ====================

    private final int difficulty;
    private final int searchDepth;
    private final Random random = new Random();
    private volatile boolean cancelled = false;
    private volatile boolean thinking = false;

    /**
     * 单次搜索时间预算（毫秒）。棋盘就地修改后单节点成本大幅下降，
     * 该上限确保即便深度 5 也给出稳定的 UI 等待时长。
     */
    private long maxTimeMs = DEFAULT_MAX_TIME_MS[0];

    /** 本次搜索截止时间戳（System.currentTimeMillis 域）。 */
    private long searchDeadlineMs;

    /** 已搜索节点计数器，配合位与做节流式超时探测。 */
    private int nodesSearched;

    /** 外部传入的当前对局局面历史（可选），用于在根节点惩罚重复局面着法 */
    private List<Long> positionHistory = null;

    /**
     * 最近由当前 AI 一方走出的真实着法，坐标统一为 AI 格式
     * [fromRow, fromCol, toRow, toCol]。只在根节点识别立即回摆，不参与规则判定。
     */
    private List<int[]> recentMoveHistory = null;

    /** 每次 getBestMove 独立清空的置换表。 */
    private final Map<Long, TranspositionEntry> transpositionTable = new HashMap<>();

    private static final class TranspositionEntry {
        final int depth;
        final int score;
        final int flag;

        TranspositionEntry(int depth, int score, int flag) {
            this.depth = depth;
            this.score = score;
            this.flag = flag;
        }
    }

    // ==================== 构造函数 ====================

    /**
     * 创建 AI 实例
     *
     * @param difficulty 难度等级（1-4），与 UI 面板档位一一对应
     */
    public ChineseChessAI(int difficulty) {
        this(difficulty, DEFAULT_MAX_TIME_MS[Math.max(0, Math.min(DEFAULT_MAX_TIME_MS.length - 1, difficulty - 1))]);
    }

    /**
     * 创建 AI 实例，可指定搜索时间预算（毫秒）。
     */
    public ChineseChessAI(int difficulty, long maxTimeMs) {
        this.difficulty = Math.max(1, Math.min(4, difficulty));
        this.searchDepth = SEARCH_DEPTHS[this.difficulty - 1];
        this.maxTimeMs = Math.max(50L, maxTimeMs);
    }

    /**
     * 设置当前对局的局面历史，AI 会在根节点避免选择导致已出现局面的着法。
     * <p>传入的历史应为当前对局的局面指纹列表（由游戏逻辑层记录）。</p>
     *
     * @param history 局面指纹历史，传 null 表示不启用重复规避
     */
    public void setPositionHistory(@Nullable List<Long> history) {
        this.positionHistory = history == null ? null : new ArrayList<>(history);
    }

    /**
     * 设置最近真实着法。调用方必须在 UI/逻辑坐标边界完成显式转换。
     */
    public void setRecentMoveHistory(@Nullable List<int[]> history) {
        if (history == null) {
            recentMoveHistory = null;
            return;
        }
        recentMoveHistory = new ArrayList<>(history.size());
        for (int[] move : history) {
            if (move != null && move.length == 4) recentMoveHistory.add(move.clone());
        }
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
        nodesSearched = 0;
        searchDeadlineMs = System.currentTimeMillis() + maxTimeMs;
        int normalizedSide = aiSide >= 0 ? 1 : -1;
        int depth = resolveSearchDepth(difficulty, boardState);
        transpositionTable.clear();

        // 尝试开局库
        int[] openingMove = getOpeningMove(boardState, normalizedSide);
        if (openingMove != null) return openingMove;

        // 生成 AI 方的合法走法（过滤送将/白脸将的着法）
        List<int[]> moves = generateLegalMoves(boardState, normalizedSide);
        if (moves.isEmpty()) return null;

        // 根节点同样按 MVV-LVA 排序，提升剪枝与等分时择优
        orderMovesByMvvLva(moves, boardState);

        thinking = true;
        // 注意符号约定：minimax 返回的是"红方视角"评分（越大对红方越有利）。
        // AI 执红时最大化该评分，执黑时最小化。
        boolean maximize = (normalizedSide == 1);
        List<int[]> safeMoves = new ArrayList<>();
        List<Integer> safeScores = new ArrayList<>();
        List<int[]> adjudicationMoves = new ArrayList<>();
        List<Integer> adjudicationScores = new ArrayList<>();

        // 【性能关键】只做一次 copyBoard，整个根节点 + minimax + 静态搜索全部就地修改。
        // 原版每层递归都新建 10×9 数组，单步搜索会产生数百万份临时对象、GC 频繁。
        int[][] board = copyBoard(boardState);

        for (int[] move : moves) {
            if (cancelled) break;

            // 就地走子
            int fr = move[0], fc = move[1], tr = move[2], tc = move[3];
            int captured = board[tr][tc];
            board[tr][tc] = board[fr][fc];
            board[fr][fc] = 0;

            // Minimax 搜索（根走子后轮到对方，isMax = !maximize）
            int score = minimax(board, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !maximize, 1);

            // 立即把刚走出的安静着法原路退回通常是无效循环；吃子或将军属于战术例外。
            if (isImmediateReversal(move)
                    && boardState[move[2]][move[3]] == 0
                    && !isInCheck(board, -normalizedSide)) {
                score += maximize ? -IMMEDIATE_REVERSAL_PENALTY : IMMEDIATE_REVERSAL_PENALTY;
            }

            // AI 走子后轮到对方；哈希编码与游戏逻辑层完全一致。
            long newHash = computePositionHash(board, -normalizedSide);
            int previousOccurrences = countPositionInHistory(newHash);
            if (previousOccurrences == 1) {
                score += maximize ? -REPEAT_WARNING_PENALTY : REPEAT_WARNING_PENALTY;
            }

            // 就地撤销走子，保证 board 始终与外部 boardState 同步
            board[fr][fc] = board[tr][tc];
            board[tr][tc] = captured;

            int normalizedScore = maximize ? score : -score;
            if (previousOccurrences >= 2) {
                // 第三次出现会立刻触发和棋或长将判负。只要还有非重复合法着法，
                // 就绝不让普通局面分或将死幻觉覆盖真实裁判结果。
                adjudicationMoves.add(move);
                adjudicationScores.add(normalizedScore);
            } else {
                safeMoves.add(move);
                safeScores.add(normalizedScore);
            }
        }

        thinking = false;
        if (!safeMoves.isEmpty()) {
            return selectMoveWithRandomness(safeMoves, safeScores, difficulty);
        }
        if (adjudicationMoves.isEmpty()) return null;
        // 极端情况下全部合法着法都会触发裁判，仍返回其中搜索评分最高的一步，
        // 交由中央 commitMove 闸门作最终判定。
        return selectMoveWithRandomness(adjudicationMoves, adjudicationScores, difficulty);
    }

    /** 根据难度和局面规模解析本次真实搜索深度。 */
    private int resolveSearchDepth(int difficulty, int[][] board) {
        int level = Math.max(1, Math.min(4, difficulty));
        int configured = SEARCH_DEPTHS[level - 1];
        if (level == 4 && countPieces(board) <= MASTER_ENDGAME_PIECE_LIMIT) return 5;
        return configured;
    }

    private int countPieces(int[][] board) {
        int count = 0;
        for (int[] row : board) {
            for (int piece : row) if (piece != 0) count++;
        }
        return count;
    }

    private boolean isImmediateReversal(int[] candidate) {
        if (recentMoveHistory == null || recentMoveHistory.isEmpty()) return false;
        int[] previous = recentMoveHistory.get(recentMoveHistory.size() - 1);
        return candidate[0] == previous[2] && candidate[1] == previous[3]
                && candidate[2] == previous[0] && candidate[3] == previous[1];
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
            case 1: return 160;  // 低：保留多样性，但减少明显送子
            case 2: return 70;   // 中
            case 3: return 8;    // 高：只在近似等价着法间随机
            case 4: return 0;    // 大师：稳定选择最优评分
            default: return 8;
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
        nodesSearched++;
        if (cancelled) return 0;
        // 节流式超时探测：每 64 节点查一次时间，避免 System.currentTimeMillis 调用主导开销。
        // 触发超时后置 cancelled=true 让递归层快速退出。
        if ((nodesSearched & 63) == 0 && System.currentTimeMillis() > searchDeadlineMs) {
            cancelled = true;
            return evaluateBoard(board);
        }

        int sideSign = isMax ? 1 : -1;

        if (ply >= MAX_SEARCH_PLY) return evaluateBoard(board);

        if (depth <= 0) {
            // 到达搜索边界，转入静态搜索以稳定子力评估
            return quiescence(board, alpha, beta, QSEARCH_MAX_DEPTH, isMax, ply);
        }

        int originalAlpha = alpha;
        int originalBeta = beta;
        long transpositionKey = computeTranspositionKey(board, sideSign, ply);
        TranspositionEntry cached = transpositionTable.get(transpositionKey);
        if (cached != null && cached.depth >= depth) {
            if (cached.flag == TT_EXACT) return cached.score;
            if (cached.flag == TT_LOWER_BOUND) alpha = Math.max(alpha, cached.score);
            else if (cached.flag == TT_UPPER_BOUND) beta = Math.min(beta, cached.score);
            if (alpha >= beta) return cached.score;
        }

        // 将军延伸：被将军时本层不递减深度，强制看清杀棋线路（受层数上限保护）
        boolean inCheck = isInCheck(board, sideSign);
        int childDepth = (inCheck && ply < CHECK_EXTENSION_PLY_LIMIT) ? depth : depth - 1;

        // 单次生成 + MVV-LVA 排序
        List<int[]> moves = generateLegalMoves(board, sideSign);
        if (moves.isEmpty()) {
            // 当前走子方无合法着法：被将死或困毙（象棋规则均判负）。
            return isMax ? -(MATE_SCORE - ply) : (MATE_SCORE - ply);
        }
        orderMovesByMvvLva(moves, board);

        int result;
        if (isMax) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                if (cancelled) break;
                // 就地走子
                int fr = move[0], fc = move[1], tr = move[2], tc = move[3];
                int captured = board[tr][tc];
                board[tr][tc] = board[fr][fc];
                board[fr][fc] = 0;
                int eval = minimax(board, childDepth, alpha, beta, false, ply + 1);
                // 就地撤销
                board[fr][fc] = board[tr][tc];
                board[tr][tc] = captured;
                if (eval > maxEval) maxEval = eval;
                if (eval > alpha) alpha = eval;
                if (beta <= alpha) break; // 剪枝
            }
            result = maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : moves) {
                if (cancelled) break;
                int fr = move[0], fc = move[1], tr = move[2], tc = move[3];
                int captured = board[tr][tc];
                board[tr][tc] = board[fr][fc];
                board[fr][fc] = 0;
                int eval = minimax(board, childDepth, alpha, beta, true, ply + 1);
                board[fr][fc] = board[tr][tc];
                board[tr][tc] = captured;
                if (eval < minEval) minEval = eval;
                if (eval < beta) beta = eval;
                if (beta <= alpha) break; // 剪枝
            }
            result = minEval;
        }

        if (!cancelled && transpositionTable.size() < MAX_TRANSPOSITION_ENTRIES) {
            int flag = TT_EXACT;
            if (result <= originalAlpha) flag = TT_UPPER_BOUND;
            else if (result >= originalBeta) flag = TT_LOWER_BOUND;
            transpositionTable.put(transpositionKey, new TranspositionEntry(depth, result, flag));
        }
        return result;
    }

    // ==================== 静态搜索（Quiescence Search） ====================

    /**
     * 静态搜索：在搜索边界对吃子（及被将军时的全部应着）继续展开，消除地平线效应。
     *
     * <p>非将军局面采用"stand-pat"策略——若当前局面分已足够好则直接剪枝；否则仅尝试吃子。
     * 被将军局面必须枚举全部合法应着（含非吃子逃将），否则会遗漏被将死的判定。</p>
     */
    private int quiescence(int[][] board, int alpha, int beta, int qdepth, boolean isMax, int ply) {
        nodesSearched++;
        if (cancelled) return 0;
        // 静态搜索同样遵守时间预算：到点立即返回当前 stand-pat 分数
        if ((nodesSearched & 63) == 0 && System.currentTimeMillis() > searchDeadlineMs) {
            cancelled = true;
            return evaluateBoard(board);
        }
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
                    int fr = m[0], fc = m[1], tr = m[2], tc = m[3];
                    int captured = board[tr][tc];
                    board[tr][tc] = board[fr][fc];
                    board[fr][fc] = 0;
                    int eval = quiescence(board, alpha, beta, qdepth - 1, false, ply + 1);
                    board[fr][fc] = board[tr][tc];
                    board[tr][tc] = captured;
                    if (eval > maxEval) maxEval = eval;
                    if (eval > alpha) alpha = eval;
                    if (beta <= alpha) break;
                }
                return maxEval;
            } else {
                int minEval = Integer.MAX_VALUE;
                for (int[] m : evasions) {
                    if (cancelled) break;
                    int fr = m[0], fc = m[1], tr = m[2], tc = m[3];
                    int captured = board[tr][tc];
                    board[tr][tc] = board[fr][fc];
                    board[fr][fc] = 0;
                    int eval = quiescence(board, alpha, beta, qdepth - 1, true, ply + 1);
                    board[fr][fc] = board[tr][tc];
                    board[tr][tc] = captured;
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
            int fr = m[0], fc = m[1], tr = m[2], tc = m[3];
            int captured = board[tr][tc];
            board[tr][tc] = board[fr][fc];
            board[fr][fc] = 0;

            // 若此吃子后对方无合法着法（含困毙），即构成将死，直接给将死分
            if (generateLegalMoves(board, oppSign).isEmpty()) {
                int mate = isMax ? (MATE_SCORE - (ply + 1)) : -(MATE_SCORE - (ply + 1));
                // 先撤销走子再回报，保持 board 状态正确
                board[fr][fc] = board[tr][tc];
                board[tr][tc] = captured;
                if (isMax) {
                    if (mate > alpha) alpha = mate;
                    if (alpha >= beta) return beta;
                } else {
                    if (mate < beta) beta = mate;
                    if (beta <= alpha) return alpha;
                }
                continue;
            }

            int eval = quiescence(board, alpha, beta, qdepth - 1, !isMax, ply + 1);
            // 撤销走子
            board[fr][fc] = board[tr][tc];
            board[tr][tc] = captured;
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
        // 将区安全不能只由仕相的材料分表达：开放车线、炮架与贴身兵马都应及时响应。
        score += evaluateKingSafety(board, 1) - evaluateKingSafety(board, -1);
        return score;
    }

    /**
     * 评估指定一方的将区安全，返回值越高表示越安全。
     * 只检查有限的重子直线和近身威胁，避免在叶节点构造完整攻击图。
     */
    private int evaluateKingSafety(int[][] board, int side) {
        int[] king = findKing(board, side);
        if (king == null) return -MATE_SCORE / 2;

        int kr = king[0], kc = king[1];
        int safety = 0;
        int advisors = 0;
        int elephantsNearHome = 0;

        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                boolean own = side > 0 ? piece > 0 : piece < 0;
                int type = Math.abs(piece);
                if (own) {
                    if (type == 2) advisors++;
                    if (type == 3 && (side > 0 ? r >= 5 : r <= 4)) elephantsNearHome++;
                    continue;
                }

                int distance = Math.abs(r - kr) + Math.abs(c - kc);
                if (type == 7 && distance <= 2) safety -= 45;
                else if (type == 4 && distance <= 3) safety -= 28;

                if ((type == 5 || type == 6) && (r == kr || c == kc)) {
                    int blockers = piecesBetween(board, r, c, kr, kc);
                    if (type == 5) {
                        if (blockers == 0) safety -= 180;
                        else if (blockers == 1) safety -= 24;
                    } else {
                        if (blockers == 1) safety -= 165;
                        else if (blockers == 0) safety -= 22;
                    }
                }
            }
        }

        // 仕象共同在位时的防守价值高于简单相加，鼓励高难度保留完整将区结构。
        safety += advisors * 16 + elephantsNearHome * 9;
        if (advisors == 2 && elephantsNearHome >= 1) safety += 14;
        int homeRow = side > 0 ? 9 : 0;
        if (kr == homeRow && kc == 4) safety += 10;
        return safety;
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

    /**
     * 置换表键额外混入 ply。当前将死分包含距根节点层数，同一盘面在不同 ply
     * 不能直接复用，否则会破坏“更快将死、尽量延迟被杀”的排序。
     */
    private long computeTranspositionKey(int[][] board, int sideToMove, int ply) {
        long hash = computePositionHash(board, sideToMove);
        hash ^= 0x9E3779B97F4A7C15L * (ply + 1L);
        hash ^= (hash >>> 29);
        return hash;
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

    /** 走子后 side 方将/帅是否仍被将军（用于合法性校验）。就地进行走子/检查/撤销，省去 copyBoard。 */
    private static boolean isMoveLegal(int[][] b, int fr, int fc, int tr, int tc, int side) {
        int captured = b[tr][tc];
        b[tr][tc] = b[fr][fc];
        b[fr][fc] = 0;
        boolean inCheck = isInCheck(b, side);
        b[fr][fc] = b[tr][tc];
        b[tr][tc] = captured;
        return !inCheck;
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

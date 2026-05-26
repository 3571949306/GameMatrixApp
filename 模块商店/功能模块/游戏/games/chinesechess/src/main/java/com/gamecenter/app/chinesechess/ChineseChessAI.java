package com.gamecenter.app.chinesechess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 中国象棋 AI — 增强版
 *
 * 搜索技术栈：
 *   Iterative Deepening + PVS + Aspiration Window
 *   + Transposition Table + Null Move Pruning + Futility Pruning
 *   + Late Move Reductions + Quiescence Search + Killer Moves
 *
 * 评估技术栈：
 *   分段PST + 机动性 + 子力协调 + 王安全 + 阶段感知
 *
 * 4档难度：每档使用独立AI配置，同等时间内搜索更深、更准
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 Zobrist 哈希实现置换表，避免重复搜索相同局面</li>
 *   <li>迭代加深配合渴望窗口，在有限时间内优先返回较优解</li>
 *   <li>AI搜索在游戏状态的深拷贝上进行，不污染主棋盘</li>
 *   <li>使用节点计数与时间双重限制，防止搜索失控</li>
 * </ul>
 */
public class ChineseChessAI {

    private static final DifficultyProfile[] DIFFICULTY_PROFILES = {
            new DifficultyProfile("chinese_chess_low.ai", 700, 8),
            new DifficultyProfile("chinese_chess_medium.ai", 1500, 12),
            new DifficultyProfile("chinese_chess_high.ai", 3500, 18),
            new DifficultyProfile("chinese_chess_master.ai", 8000, 24)
    };

    /** 最大搜索深度上限 */
    private static final int MAX_DEPTH = 24;

    /** 每隔多少个节点检查一次超时 */
    private static final int TIME_CHECK_INTERVAL = 512;

    /** 节点搜索上限，防止极端情况下搜索时间过长 */
    private static final long NODE_LIMIT = 20_000_000L;

    /** 当前难度对应的搜索时间上限 */
    private int maxTimeMs;

    /** 当前难度对应的搜索深度上限 */
    private int searchDepthLimit;

    /** 本次搜索开始的时间戳 */
    private long searchStartMs;

    /** 是否已超时 */
    private boolean timedOut;

    /** 已搜索的节点数 */
    private int nodesSearched;

    /** 无穷大估值，用于alpha-beta搜索的初始边界 */
    private static final int INF = 99999999;

    /** 将杀分数阈值，接近INF但留有余量用于区分将杀距离 */
    private static final int WIN_SCORE = INF - 100;

    /** 将杀阈值：超过此分数视为已发现将杀路径 */
    private static final int MATE_THRESHOLD = WIN_SCORE - 1000;

    // ——— 置换表 ———
    /** 置换表大小（2的20次方 = 约100万条目） */
    private static final int TT_SIZE = 1 << 20;
    /** 置换表索引掩码 */
    private static final int TT_MASK = TT_SIZE - 1;
    /** 置换表标记：精确值 */
    private static final byte TT_EXACT = 0;
    /** 置换表标记：上界（alpha裁剪） */
    private static final byte TT_ALPHA = 1;
    /** 置换表标记：下界（beta裁剪） */
    private static final byte TT_BETA = 2;
    private long[] ttKey = new long[TT_SIZE];
    private int[] ttScore = new int[TT_SIZE];
    private short[] ttDepth = new short[TT_SIZE];
    /** 置换表存储的最佳走法，4 bits per coordinate: fx, fy, tx, ty */
    private short[] ttMove = new short[TT_SIZE];
    private byte[] ttFlag = new byte[TT_SIZE];
    /** 置换表条目的年龄标记，用于区分不同搜索轮次 */
    private long[] ttAge = new long[TT_SIZE];
    /** 当前搜索轮次的年龄值 */
    private long currentAge = 0;

    // ——— 杀手走法 ———
    /** 每个深度层级记录的杀手走法编码 */
    private int[][] killerMoves;
    /** 每个深度层级已记录的杀手走法数量 */
    private int[] killerCount;

    // ——— 历史启发 ———
    /** 历史启发表：走法编码 → 历史得分，用于走法排序 */
    private Map<Integer, Integer> historyTable;

    // ——— 搜索统计 ———
    /** 当前迭代中的最佳分数 */
    private int currentBestScore;
    /** 当前迭代中的最佳走法 [fromX, fromY, toX, toY] */
    private int[] currentBest;

    /**
     * 根节点搜索结果封装。
     * <p>包含搜索得分和对应的最佳走法。
     */
    private static class RootResult {
        final int score;
        final int[] move;

        RootResult(int score, int[] move) {
            this.score = score;
            this.move = move;
        }
    }

    // ============ 棋子权重 ============
    /**
     * 各棋子类型的基础分值，顺序与 {@link ChineseChessGame.PieceType} 枚举对应：
     * 将=10000, 士=200, 象=200, 马=400, 车=1000, 炮=450, 兵=100
     */
    private static final int[] PIECE_VALUES = {10000, 200, 200, 400, 1000, 450, 100};

    /** 将军走法额外加分 */
    private static final int CHECK_MOVE_BONUS = 320000;

    /** 吃将走法额外加分（最高优先级） */
    private static final int GENERAL_CAPTURE_BONUS = 8000000;

    // ============ 红旗位置价值表 POV=黑方(上方) ============
    // GENERAL 开/终局
    private static final int[][] PST_GENERAL_OPEN = {
        {0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},
        {1,2,2,2,2,2,2,2,1},{1,2,2,2,2,2,2,2,1},{1,2,2,2,2,2,2,2,1},
        {1,4,6,6,6,6,6,4,1},{1,4,8,8,8,8,8,4,1},{1,8,10,10,10,10,10,8,1},
        {1,8,10,15,20,15,10,8,1}};
    private static final int[][] PST_GENERAL_END = {
        {1,10,20,30,30,30,20,10,1},{1,10,20,30,30,30,20,10,1},{1,10,20,30,30,30,20,10,1},
        {1,8,16,22,22,22,16,8,1},{1,8,16,22,22,22,16,8,1},{1,8,16,22,22,22,16,8,1},
        {1,6,10,14,14,14,10,6,1},{1,4,8,10,10,10,8,4,1},{1,2,4,6,6,6,4,2,1},{1,1,2,4,4,4,2,1,1}};

    // HORSE 马
    private static final int[][] PST_HORSE = {
        {2,4,6,8,8,8,6,4,2},{2,6,10,14,14,14,10,6,2},{2,8,16,22,24,22,16,8,2},
        {4,12,22,28,30,28,22,12,4},{4,14,24,32,36,32,24,14,4},{4,14,24,32,36,32,24,14,4},
        {4,12,22,28,30,28,22,12,4},{2,8,16,22,24,22,16,8,2},{2,6,10,14,14,14,10,6,2},{2,4,6,8,8,8,6,4,2}};

    // CANNON 炮 — 远离本方阵地则大幅减值（无炮架=废子）
    private static final int[][] PST_CANNON = {
        {14,16,18,20,22,20,18,16,14},{14,16,18,22,24,22,18,16,14},{12,14,16,20,22,20,16,14,12},
        {10,12,14,18,20,18,14,12,10},{6,8,10,14,16,14,10,8,6},{-6,-2,2,6,8,6,2,-2,-6},
        {-22,-16,-10,-4,0,-4,-10,-16,-22},{-38,-30,-24,-18,-12,-18,-24,-30,-38},{-54,-46,-40,-34,-28,-34,-40,-46,-54},{-70,-62,-56,-50,-44,-50,-56,-62,-70}};

    // CHARIOT 车
    private static final int[][] PST_CHARIOT = {
        {14,14,14,18,20,18,14,14,14},{18,18,20,24,26,24,20,18,18},{16,18,20,24,26,24,20,18,16},
        {14,16,18,22,24,22,18,16,14},{12,14,16,20,22,20,16,14,12},{12,14,16,20,22,20,16,14,12},
        {10,12,14,18,20,18,14,12,10},{8,10,12,16,18,16,12,10,8},{6,8,10,14,16,14,10,8,6},{4,6,8,12,14,12,8,6,4}};

    // SOLDIER 兵/卒
    private static final int[][] PST_SOLDIER_FRONT = { // 过河后
        {0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},
        {6,12,18,32,56,32,18,12,6},{10,18,28,42,60,42,28,18,10},{14,24,38,56,70,56,38,24,14},
        {18,28,44,68,80,68,44,28,18},{20,30,50,74,90,74,50,30,20},{22,34,56,80,100,80,56,34,22}};

    private static final int[][] PST_SOLDIER_BACK = { // 过河前
        {0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0},
        {3,0,12,0,18,0,12,0,3},{3,0,14,0,22,0,14,0,3},{3,0,16,0,26,0,16,0,3},{3,0,18,0,30,0,18,0,3},
        {0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0}};

    // ——— 静态，从POV=BLACK角度不加成 ———
    private static final int[][] PST_BLANK = new int[10][9];

    /**
     * 构造AI引擎。
     *
     * @param level 难度等级（1~4），决定搜索时间和深度上限
     */
    public ChineseChessAI(int level) {
        int idx = Math.max(0, Math.min(level - 1, DIFFICULTY_PROFILES.length - 1));
        DifficultyProfile profile = DIFFICULTY_PROFILES[idx];
        this.maxTimeMs = profile.maxTimeMs;
        this.searchDepthLimit = profile.maxDepth;
        this.historyTable = new HashMap<>();
        this.killerMoves = new int[MAX_DEPTH + 1][4];
        this.killerCount = new int[MAX_DEPTH + 1];
    }

    private static class DifficultyProfile {
        final String configFile;
        final int maxTimeMs;
        final int maxDepth;

        DifficultyProfile(String configFile, int maxTimeMs, int maxDepth) {
            this.configFile = configFile;
            this.maxTimeMs = maxTimeMs;
            this.maxDepth = maxDepth;
        }
    }

    /**
     * 将走法数组打包为16位整数编码。
     * <p>格式：fromX(4bit) | fromY(4bit) | toX(4bit) | toY(4bit)
     *
     * @param move 走法数组 [fromX, fromY, toX, toY]
     * @return 打包后的整数编码
     */
    private int packMove(int[] move) {
        return (move[0] << 12) | (move[1] << 8) | (move[2] << 4) | move[3];
    }

    /**
     * 在搜索中执行一步走棋，并切换走棋方。
     *
     * @param game 游戏状态
     * @param move 走法 [fromX, fromY, toX, toY]
     * @return 走棋记录（用于后续撤销），若走法无效返回null
     */
    private ChineseChessGame.MoveRecord makeSearchMove(ChineseChessGame game, int[] move) {
        ChineseChessGame.MoveRecord record = game.makeMoveSafe(move[0], move[1], move[2], move[3]);
        if (record != null) game.switchSide();
        return record;
    }

    /**
     * 在搜索中撤销一步走棋，并恢复走棋方。
     *
     * @param game   游戏状态
     * @param record 之前 {@link #makeSearchMove} 返回的走棋记录
     */
    private void undoSearchMove(ChineseChessGame game, ChineseChessGame.MoveRecord record) {
        if (record == null) return;
        game.switchSide();
        game.undoMove(record);
    }

    /**
     * 从当前走棋方视角返回评估分数。
     * <p>评估函数始终以黑方为正方向计算，若当前走棋方为红方则取反。
     *
     * @param game 游戏状态
     * @return 从当前走棋方视角的评估分数（正值=有利）
     */
    private int evaluateForSide(ChineseChessGame game) {
        int score = evaluate(game);
        return game.getCurrentSide() == ChineseChessGame.Side.BLACK ? score : -score;
    }

    /**
     * 将上一轮迭代加深的最佳走法提升到走法列表首位。
     * <p>这确保PVS搜索中第一个走法是最有希望的，提高裁剪效率。
     *
     * @param moves    走法列表
     * @param bestMove 要提升的最佳走法
     */
    private void promoteRootMove(List<int[]> moves, int[] bestMove) {
        if (bestMove == null || moves.isEmpty()) return;
        for (int i = 0; i < moves.size(); i++) {
            int[] move = moves.get(i);
            if (move[0] == bestMove[0] && move[1] == bestMove[1]
                    && move[2] == bestMove[2] && move[3] == bestMove[3]) {
                if (i > 0) {
                    moves.remove(i);
                    moves.add(0, move);
                }
                return;
            }
        }
    }

    /**
     * 将搜索分数转换为置换表存储格式。
     * <p>将杀分数需要加上当前层数，以便在读取时还原正确的将杀距离。
     *
     * @param score 原始搜索分数
     * @param ply   当前搜索层数（距根节点的距离）
     * @return 转换后的分数
     */
    private int scoreToTT(int score, int ply) {
        if (score > MATE_THRESHOLD) return score + ply;
        if (score < -MATE_THRESHOLD) return score - ply;
        return score;
    }

    /**
     * 从置换表存储格式还原搜索分数。
     * <p>将杀分数需要减去当前层数，还原正确的将杀距离。
     *
     * @param score 置换表中的分数
     * @param ply   当前搜索层数
     * @return 还原后的分数
     */
    private int scoreFromTT(int score, int ply) {
        if (score > MATE_THRESHOLD) return score - ply;
        if (score < -MATE_THRESHOLD) return score + ply;
        return score;
    }

    // ============ 核心搜索入口 ============

    /**
     * 获取当前局面的最佳走法。
     * <p>
     * 使用迭代加深搜索：从深度1开始逐步加深，在时间限制内返回最深一层的结果。
     * 每次迭代使用上一轮的最佳走法作为首选，配合渴望窗口加速搜索。
     *
     * @param realGame 真实游戏状态（会被深拷贝，不会被修改）
     * @return 最佳走法 [fromX, fromY, toX, toY]，若无合法走法返回null
     */
    public int[] getBestMove(ChineseChessGame realGame) {
        nodesSearched = 0;
        timedOut = false;
        searchStartMs = System.currentTimeMillis();
        currentAge++;
        currentBest = null;
        currentBestScore = -INF;
        historyTable.clear();
        for (int d = 0; d <= MAX_DEPTH; d++) {
            killerCount[d] = 0;
            Arrays.fill(killerMoves[d], 0);
        }

        // 深拷贝棋盘，AI搜索不污染真实棋盘
        ChineseChessGame game = realGame.deepCopy();
        ChineseChessGame.Side aiSide = game.getCurrentSide();
        List<int[]> moves = orderRootMoves(game.getAllMoves(aiSide), game);
        if (moves.isEmpty()) return null;
        currentBest = moves.get(0);

        // 迭代加深 + 渴望窗口
        for (int depth = 1; depth <= searchDepthLimit; depth++) {
            int alpha = -INF, beta = INF;
            // 深度>=4时使用渴望窗口，以上一轮最佳分数为中心±50
            if (depth >= 4) {
                alpha = currentBestScore - 50;
                beta = currentBestScore + 50;
            }

            RootResult result = searchRootDepth(game, moves, depth, alpha, beta);

            // 窗口失败则用全窗口重搜一次，避免反复卡在同一层。
            if (!timedOut && depth >= 4 && (result.score <= alpha || result.score >= beta)) {
                result = searchRootDepth(game, moves, depth, -INF, INF);
            }

            if (!timedOut && result.move != null) {
                currentBest = result.move;
                currentBestScore = result.score;
                promoteRootMove(moves, currentBest);
            }

            // 超时或已找到将杀路径则停止迭代
            if (timedOut) break;
            if (currentBestScore > MATE_THRESHOLD) break;
        }

        if (timedOut && currentBest != null) return currentBest;
        List<int[]> fallback = game.getAllMoves(aiSide);
        if (currentBest == null && !fallback.isEmpty()) return fallback.get(0);
        return currentBest;
    }

    /**
     * 在根节点执行指定深度的搜索。
     * <p>
     * 使用PVS（主变例搜索）：第一个走法用全窗口搜索，
     * 后续走法先用零窗口搜索验证，若超过alpha再用全窗口重搜。
     *
     * @param game  游戏状态
     * @param moves 已排序的走法列表
     * @param depth 搜索深度
     * @param alpha alpha下界
     * @param beta  beta上界
     * @return 搜索结果（分数+最佳走法）
     */
    private RootResult searchRootDepth(
            ChineseChessGame game, List<int[]> moves, int depth, int alpha, int beta) {
        int bestScore = -INF;
        int[] bestMove = null;

        for (int[] move : moves) {
            if (timedOut) break;
            ChineseChessGame.MoveRecord record = makeSearchMove(game, move);
            if (record == null) continue;

            int score;
            if (bestMove == null) {
                // 第一个走法：全窗口PVS搜索
                score = -search(game, depth - 1, -beta, -alpha, true, 1);
            } else {
                // 后续走法：零窗口搜索，若超过alpha则重搜
                score = -search(game, depth - 1, -alpha - 1, -alpha, false, 1);
                if (score > alpha && score < beta) {
                    score = -search(game, depth - 1, -beta, -alpha, true, 1);
                }
            }
            undoSearchMove(game, record);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) alpha = score;
        }

        return new RootResult(bestScore, bestMove);
    }

    // ============ PVS (Principal Variation Search) ============

    /**
     * PVS搜索（主变例搜索变体）。
     * <p>
     * 包含以下优化：
     * <ul>
     *   <li>置换表探测与存储</li>
     *   <li>将军延展（被将军时加深搜索）</li>
     *   <li>空走裁剪（Null Move Pruning）</li>
     *   <li>无效裁剪（Futility Pruning）</li>
     *   <li>后期走法缩减（Late Move Reductions）</li>
     *   <li>杀手走法与历史启发</li>
     * </ul>
     *
     * @param game     游戏状态
     * @param depth    剩余搜索深度
     * @param alpha    alpha下界
     * @param beta     beta上界
     * @param isPVNode 是否为主变例节点
     * @param ply      当前距根节点的层数
     * @return 搜索分数
     */
    private int pvs(ChineseChessGame game, int depth, int alpha, int beta, boolean isPVNode) {
        nodesSearched++;
        if (nodesSearched % TIME_CHECK_INTERVAL == 0 && checkTimeout()) return evaluateForSide(game);
        if (nodesSearched > NODE_LIMIT) { timedOut = true; return evaluateForSide(game); }

        // ——— 置换表探测 ———
        long hash = computeHash(game);
        int ttIdx = (int) (hash & TT_MASK);
        if (ttKey[ttIdx] == hash) {
            long age = ttAge[ttIdx];
            int ttDepth = (int) this.ttDepth[ttIdx];
            if (age == currentAge && ttDepth >= depth) {
                int score = scoreFromTT(ttScore[ttIdx], 0);
                if (ttFlag[ttIdx] == TT_EXACT) return score;
                if (ttFlag[ttIdx] == TT_ALPHA && score <= alpha) return alpha;
                if (ttFlag[ttIdx] == TT_BETA && score >= beta) return beta;
            }
        }

        // 将军延展：被将军时加深一层搜索，避免漏算
        ChineseChessGame.Side us = game.getCurrentSide();
        boolean inCheck = game.isInCheck(us);
        if (inCheck) depth++;

        if (depth <= 0 || game.isGameOver()) return quiescence(game, alpha, beta);

        // ——— 空走裁剪 ———
        // 跳过当前走棋方，若对方仍无法超过beta，则当前局面极可能优于beta
        if (!inCheck && !isPVNode && depth >= 3 && !isEndgame(game)) {
            game.switchSide();
            int nullScore = -search(game, depth - 1 - 3, -beta, -beta + 1, false, 0);
            game.switchSide();
            if (nullScore >= beta) return beta;
        }

        List<int[]> moves = orderMoves(game.getAllMoves(us), game, hash, depth, ttIdx);

        // ——— 无合法走法 = 被将杀或困毙 ———
        if (moves.isEmpty()) return -WIN_SCORE + 1;
        int staticEval = inCheck ? -INF / 2 : evaluateForSide(game);

        int best = -INF;
        int bestMoveCode = 0;
        byte flag = TT_ALPHA;
        int legalCount = 0;

        for (int moveIdx = 0; moveIdx < moves.size(); moveIdx++) {
            if (timedOut) break;
            int[] m = moves.get(moveIdx);

            // 预检测走棋后是否将军，用于无效裁剪判断
            ChineseChessGame.MoveRecord r = makeSearchMove(game, m);
            boolean newCheck = r != null && game.isInCheck(game.getCurrentSide());
            undoSearchMove(game, r);

            // ——— 无效裁剪（Futility Pruning）———
            // 若静态评估 + 走法估值 + 容差仍不超过alpha，跳过该走法
            int futilityMargin = 80 * depth;
            if (legalCount >= 1 && !inCheck && !newCheck
                    && depth <= 3 && staticEval + evaluateMove(m, game) + futilityMargin <= alpha) {
                continue;
            }

            boolean capture = game.getBoard()[m[3]][m[2]] != null;
            r = makeSearchMove(game, m);
            if (r == null) continue;
            int score;
            boolean givesCheck = newCheck;

            if (legalCount == 0) {
                // 第一个合法走法：全窗口搜索
                score = -search(game, depth - 1, -beta, -alpha, false, 1);
            } else {
                // ——— LMR（后期走法缩减）———
                // 排序靠后、不吃子、不将军的走法，先缩减深度搜索，若超过alpha再全深度重搜
                int reduction = 0;
                if (depth >= 3 && legalCount >= 4 && !givesCheck && !inCheck && !capture) {
                    reduction = 1;
                    if (legalCount >= 8) reduction = 2;
                }
                score = -search(game, depth - 1 - reduction, -alpha - 1, -alpha, false, 0);
                if (score > alpha && (reduction > 0 || score < beta))
                    score = -search(game, depth - 1, -beta, -alpha, false, 0);
            }
            undoSearchMove(game, r);
            legalCount++;

            if (score > best) {
                best = score;
                bestMoveCode = packMove(m);
                if (score > alpha) {
                    alpha = score;
                    flag = TT_EXACT;
                    if (score >= beta) {
                        // Beta裁剪：记录杀手走法和历史启发
                        flag = TT_BETA;
                        if (depth < MAX_DEPTH) {
                            updateKiller(bestMoveCode, depth);
                            updateHistory(bestMoveCode, depth);
                        }
                        break;
                    }
                }
            }
        }

        // 无合法走法时返回当前方评估值（不应发生，因前面已处理空走法列表）
        if (legalCount == 0) best = evaluateForSide(game);

        // ——— 写入置换表 ———
        if (!timedOut) {
            ttKey[ttIdx] = hash;
            ttScore[ttIdx] = scoreToTT(best, 0);
            ttDepth[ttIdx] = (short) depth;
            ttMove[ttIdx] = (short) bestMoveCode;
            ttFlag[ttIdx] = flag;
            ttAge[ttIdx] = currentAge;
        }

        return best;
    }

    // ============ 标准搜索（带零窗口） ============

    /**
     * 标准alpha-beta搜索，支持零窗口和LMR。
     * <p>
     * 与 {@link #pvs} 类似，但使用更简化的搜索逻辑，
     * 主要用于非PV节点的搜索以减少开销。
     *
     * @param game     游戏状态
     * @param depth    剩余搜索深度
     * @param alpha    alpha下界
     * @param beta     beta上界
     * @param isPVNode 是否为主变例节点
     * @param ply      当前距根节点的层数
     * @return 搜索分数
     */
    private int search(ChineseChessGame game, int depth, int alpha, int beta, boolean isPVNode, int ply) {
        nodesSearched++;
        if (nodesSearched % TIME_CHECK_INTERVAL == 0 && checkTimeout()) return evaluateForSide(game);
        if (nodesSearched > NODE_LIMIT) { timedOut = true; return evaluateForSide(game); }

        ChineseChessGame.Side us = game.getCurrentSide();
        boolean inCheck = game.isInCheck(us);
        // 被将军时延展一层搜索
        if (inCheck) depth++;

        if (depth <= 0 || game.isGameOver()) return quiescence(game, alpha, beta);

        // ——— 置换表探测 ———
        long hash = computeHash(game);
        int ttIdx = (int) (hash & TT_MASK);
        if (ttKey[ttIdx] == hash && ttAge[ttIdx] == currentAge) {
            int ttDepth = (int) this.ttDepth[ttIdx];
            if (ttDepth >= depth) {
                int score = scoreFromTT(ttScore[ttIdx], ply);
                if (ttFlag[ttIdx] == TT_EXACT) return score;
                if (ttFlag[ttIdx] == TT_ALPHA && score <= alpha) return alpha;
                if (ttFlag[ttIdx] == TT_BETA && score >= beta) return beta;
            }
        }

        // ——— 空走裁剪（简化版）———
        if (!inCheck && !isPVNode && depth >= 3 && !isEndgame(game)) {
            game.switchSide();
            int nullScore = -search(game, depth - 4, -beta, -beta + 1, false, ply + 1);
            game.switchSide();
            if (nullScore >= beta) return beta;
        }

        List<int[]> moves = orderMoves(game.getAllMoves(us), game, hash, depth, ttIdx);
        if (moves.isEmpty()) {
            // 无合法走法 = 被将杀，分数与层数相关（越早将杀分数越高）
            return -(WIN_SCORE - ply);
        }
        int staticEval = inCheck ? -INF / 2 : evaluateForSide(game);

        int best = -INF;
        int bestMoveCode = 0;
        byte flag = TT_ALPHA;
        int legalCount = 0;

        for (int i = 0; i < moves.size(); i++) {
            if (timedOut) break;
            int[] m = moves.get(i);

            // ——— 无效裁剪 ———
            if (depth <= 3 && legalCount >= 2 && !inCheck && !isPVNode) {
                int futilityMargin = 100 * depth;
                if (staticEval + evaluateMove(m, game) + futilityMargin <= alpha) continue;
            }

            boolean capture = game.getBoard()[m[3]][m[2]] != null;
            ChineseChessGame.MoveRecord r = makeSearchMove(game, m);
            if (r == null) continue;
            boolean newCheck = game.isInCheck(game.getCurrentSide());

            int score;
            if (legalCount == 0) {
                // 第一个合法走法：全窗口搜索
                score = -search(game, depth - 1, -beta, -alpha, false, ply + 1);
            } else {
                // ——— LMR（后期走法缩减）———
                // 缩减量随走法排序位置递增，PV节点缩减量受限
                int reduction = 0;
                if (depth >= 3 && legalCount >= 4 && !newCheck && !inCheck && !capture) {
                    reduction = 1 + Math.min((legalCount - 4) / 4, 2);
                    if (isPVNode) reduction = Math.min(reduction, 1);
                }
                // 先缩减深度搜索，若超过alpha则零窗口重搜，再超则全窗口重搜
                score = -search(game, depth - 1 - reduction, -alpha - 1, -alpha, false, ply + 1);
                if (score > alpha && reduction > 0)
                    score = -search(game, depth - 1, -alpha - 1, -alpha, false, ply + 1);
                if (score > alpha && score < beta)
                    score = -search(game, depth - 1, -beta, -alpha, false, ply + 1);
            }
            undoSearchMove(game, r);
            legalCount++;

            if (score > best) {
                best = score;
                bestMoveCode = packMove(m);
                if (score > alpha) {
                    alpha = score;
                    flag = TT_EXACT;
                    if (score >= beta) {
                        // Beta裁剪
                        flag = TT_BETA;
                        if (!newCheck) updateKiller(bestMoveCode, depth);
                        updateHistory(bestMoveCode, depth);
                        break;
                    }
                }
            }
        }

        if (legalCount == 0) best = evaluateForSide(game);

        // 写入置换表
        if (!timedOut) {
            ttKey[ttIdx] = hash; ttScore[ttIdx] = scoreToTT(best, ply);
            ttDepth[ttIdx] = (short) depth; ttMove[ttIdx] = (short) bestMoveCode;
            ttFlag[ttIdx] = flag; ttAge[ttIdx] = currentAge;
        }
        return best;
    }

    // ============ 静止期搜索 ============

    /**
     * 静止期搜索（Quiescence Search）。
     * <p>
     * 在搜索深度耗尽后继续搜索吃子走法，避免水平线效应（在局面动荡时
     * 返回不准确的静态评估值）。仅搜索价值合理的吃子走法：
     * 吃子收益 >= 被吃子价值/3 且 >= 100。
     *
     * @param game  游戏状态
     * @param alpha alpha下界
     * @param beta  beta上界
     * @return 静止期搜索分数
     */
    private int quiescence(ChineseChessGame game, int alpha, int beta) {
        nodesSearched++;
        if (nodesSearched % TIME_CHECK_INTERVAL == 0 && checkTimeout()) return evaluateForSide(game);

        // 站立评估（stand pat）：不做任何走法时的评估值
        int standPat = evaluateForSide(game);
        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        ChineseChessGame.Side us = game.getCurrentSide();

        // 只搜吃子走法（将军走法在普通搜索中处理）
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = game.getBoard()[y][x];
                if (p == null || p.side != us) continue;

                for (int[] m : game.getMoves(p)) {
                    ChineseChessGame.Piece cap = game.getBoard()[m[1]][m[0]];
                    if (cap == null || cap.side == us) continue;
                    // SEE简化：过滤掉明显不划算的吃子
                    int seeGain = PIECE_VALUES[cap.type.ordinal()];
                    int seeLoss = PIECE_VALUES[p.type.ordinal()];
                    if (seeGain < seeLoss / 3 && seeGain < 100) continue;

                    ChineseChessGame.MoveRecord r = game.makeMoveSafe(x, y, m[0], m[1]);
                    if (r != null && !game.isInCheck(us)) {
                        // 走棋后自己不被将军，继续递归搜索
                        game.switchSide();
                        int score = -quiescence(game, -beta, -alpha);
                        game.switchSide();
                        game.undoMove(r);
                        if (score >= beta) return beta;
                        if (score > alpha) alpha = score;
                    } else if (r != null) {
                        // 走棋后自己被将军，撤销该走法
                        game.undoMove(r);
                    }
                }
            }
        }
        return alpha;
    }

    // ============ 走法排序 ============

    /**
     * 对根节点走法进行排序。
     * <p>根据走法评分（吃子价值、将军奖励等）降序排列。
     *
     * @param moves 待排序的走法列表
     * @param game  游戏状态
     * @return 排序后的走法列表
     */
    private List<int[]> orderRootMoves(List<int[]> moves, ChineseChessGame game) {
        List<int[]> scored = new ArrayList<>();
        for (int[] m : moves) {
            int sc = scoreMove(m, game, true);
            scored.add(new int[]{m[0], m[1], m[2], m[3], sc});
        }
        Collections.sort(scored, (a, b) -> Integer.compare(b[4], a[4]));
        List<int[]> ordered = new ArrayList<>();
        for (int[] s : scored) ordered.add(new int[]{s[0], s[1], s[2], s[3]});
        return ordered;
    }

    /**
     * 对内部节点走法进行排序。
     * <p>排序优先级：置换表走法 > 杀手走法 > MVV-LVA吃子 > 将军 > 历史启发
     *
     * @param moves 待排序的走法列表
     * @param game  游戏状态
     * @param hash  当前局面的Zobrist哈希值
     * @param depth 当前搜索深度
     * @param ttIdx 置换表索引
     * @return 排序后的走法列表
     */
    private List<int[]> orderMoves(List<int[]> moves, ChineseChessGame game, long hash, int depth, int ttIdx) {
        // TT走法优先
        int ttMoveCode = 0;
        if (ttKey[ttIdx] == hash) ttMoveCode = this.ttMove[ttIdx] & 0xFFFF;

        List<int[]> scored = new ArrayList<>();
        for (int[] m : moves) {
            int code = packMove(m);
            int sc = 0;

            if (code == ttMoveCode) { sc = 10000000; } // TT走法绝对优先
            else {
                sc = scoreMove(m, game, depth >= 2);

                // 杀手走法
                if (depth < MAX_DEPTH) {
                    for (int k = 0; k < killerCount[depth]; k++) {
                        if (killerMoves[depth][k] == code) { sc += 5000000; break; }
                    }
                }

                // 历史启发
                Integer h = historyTable.get(code);
                if (h != null) sc += Math.min(h, 100000);
            }
            scored.add(new int[]{m[0], m[1], m[2], m[3], sc});
        }
        Collections.sort(scored, (a, b) -> Integer.compare(b[4], a[4]));

        List<int[]> ordered = new ArrayList<>();
        for (int[] s : scored) ordered.add(new int[]{s[0], s[1], s[2], s[3]});
        return ordered;
    }

    /**
     * 计算走法的综合评分，用于走法排序。
     * <p>评分因素：吃子价值（MVV-LVA）、将军奖励、基础走法估值。
     *
     * @param m                  走法 [fromX, fromY, toX, toY]
     * @param game               游戏状态
     * @param includeCheckProbe  是否包含将军探测（深度>=2时开启，较耗性能）
     * @return 走法评分
     */
    private int scoreMove(int[] m, ChineseChessGame game, boolean includeCheckProbe) {
        ChineseChessGame.Piece piece = game.getBoard()[m[1]][m[0]];
        if (piece == null) return 0;

        int score = evaluateMove(m, game);
        ChineseChessGame.Piece cap = game.getBoard()[m[3]][m[2]];
        if (cap != null) {
            // MVV-LVA：吃子价值越高越好，用低价值子吃高价值子更好
            int capValue = PIECE_VALUES[cap.type.ordinal()];
            int pieceValue = PIECE_VALUES[piece.type.ordinal()];
            score += capValue * 100 - pieceValue / 10;
            if (cap.type == ChineseChessGame.PieceType.GENERAL) {
                score += GENERAL_CAPTURE_BONUS;
            }
            if (capValue >= pieceValue) {
                // 等价或有利交换额外加分
                score += capValue * 8;
            }
        }

        if (includeCheckProbe && moveGivesCheck(game, m)) {
            score += CHECK_MOVE_BONUS;
        }
        return score;
    }

    /**
     * 检测走法是否会导致将军。
     * <p>通过实际执行走法后检查对方是否被将来判断。
     *
     * @param game 游戏状态
     * @param move 走法
     * @return true 若走棋后对方被将
     */
    private boolean moveGivesCheck(ChineseChessGame game, int[] move) {
        ChineseChessGame.MoveRecord record = makeSearchMove(game, move);
        if (record == null) return false;
        boolean givesCheck = game.isInCheck(game.getCurrentSide());
        undoSearchMove(game, record);
        return givesCheck;
    }

    /**
     * 计算走法的基础估值（非吃子部分）。
     * <p>考虑因素：兵卒前进奖励、马的活动性、中心位置偏好。
     *
     * @param m    走法
     * @param game 游戏状态
     * @return 基础走法估值
     */
    private int evaluateMove(int[] m, ChineseChessGame game) {
        ChineseChessGame.Piece cap = game.getBoard()[m[3]][m[2]];
        if (cap != null) return PIECE_VALUES[cap.type.ordinal()];
        ChineseChessGame.Piece piece = game.getBoard()[m[1]][m[0]];
        if (piece == null) return 0;

        int score = 0;
        int fx = m[0], fy = m[1], tx = m[2], ty = m[3];
        boolean forward = (piece.side == ChineseChessGame.Side.BLACK) ? (ty > fy) : (ty < fy);
        if (piece.type == ChineseChessGame.PieceType.SOLDIER && forward) score += 30;
        if (piece.type == ChineseChessGame.PieceType.HORSE) score += 10;
        if (tx >= 3 && tx <= 5) score += 5; // 中心列奖励
        return score;
    }

    /**
     * 更新杀手走法表。
     * <p>在同一深度层级，记录产生beta裁剪的非吃子走法。
     * 最多保留4个杀手走法，超出时滑动替换最旧的。
     *
     * @param moveCode 走法编码
     * @param depth    搜索深度
     */
    private void updateKiller(int moveCode, int depth) {
        if (depth >= MAX_DEPTH) return;
        int km = moveCode & 0xFFFF;
        // 不重复存储
        for (int i = 0; i < killerCount[depth]; i++) {
            if (killerMoves[depth][i] == km) return;
        }
        if (killerCount[depth] < 4) {
            killerMoves[depth][killerCount[depth]++] = km;
        } else {
            // 滑动：移除最旧的
            killerMoves[depth][0] = killerMoves[depth][1];
            killerMoves[depth][1] = killerMoves[depth][2];
            killerMoves[depth][2] = killerMoves[depth][3];
            killerMoves[depth][3] = km;
        }
    }

    /**
     * 更新历史启发表。
     * <p>产生beta裁剪的走法按 depth² 累加得分，上限500000。
     *
     * @param moveCode 走法编码
     * @param depth    搜索深度
     */
    private void updateHistory(int moveCode, int depth) {
        int key = moveCode & 0xFFFF;
        int val = historyTable.getOrDefault(key, 0) + depth * depth;
        historyTable.put(key, Math.min(val, 500000));
    }

    // ============ 增强评估函数 ============

    /**
     * 计算棋盘上所有棋子的总子力值。
     *
     * @param game 游戏状态
     * @return 总子力值
     */
    private int totalMaterial(ChineseChessGame game) {
        int total = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++)
            for (int x = 0; x < ChineseChessGame.COLS; x++)
                if (board[y][x] != null) total += PIECE_VALUES[board[y][x].type.ordinal()];
        return total;
    }

    /**
     * 判断当前是否处于残局阶段。
     * <p>当棋盘上车、马、炮总数 <= 6 时判定为残局。
     * 残局阶段会影响将帅PST选择和空走裁剪策略。
     *
     * @param game 游戏状态
     * @return true 若处于残局
     */
    private boolean isEndgame(ChineseChessGame game) {
        int count = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++)
            for (int x = 0; x < ChineseChessGame.COLS; x++)
                if (board[y][x] != null && (board[y][x].type == ChineseChessGame.PieceType.CHARIOT
                        || board[y][x].type == ChineseChessGame.PieceType.CANNON
                        || board[y][x].type == ChineseChessGame.PieceType.HORSE)) count++;
        return count <= 6;
    }

    /**
     * 综合评估函数。
     * <p>从黑方视角计算分数（正值=黑方有利），评估因素包括：
     * <ul>
     *   <li>子力价值 + 位置价值表（PST）</li>
     *   <li>炮架依赖（孤立炮减值）</li>
     *   <li>孤军深入惩罚</li>
     *   <li>机动性（可走步数）</li>
     *   <li>子力协调（马腿、车炮配合）</li>
     *   <li>威胁平衡（被攻击子力压力）</li>
     *   <li>兵卒结构</li>
     *   <li>王安全</li>
     *   <li>阶段感知（非残局时整体分数放大5%）</li>
     * </ul>
     *
     * @param game 游戏状态
     * @return 评估分数（黑方视角，正值=黑方有利）
     */
    private int evaluate(ChineseChessGame game) {
        int score = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        boolean endgame = isEndgame(game);

        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null) continue;

                int val = PIECE_VALUES[p.type.ordinal()];
                int sign = (p.side == ChineseChessGame.Side.BLACK) ? 1 : -1;

                // ——— PST（位置价值表）———
                // 将棋子坐标转换为黑方视角（从上往下看）
                int py = (p.side == ChineseChessGame.Side.BLACK) ? y : (9 - y);
                int px = x;
                int pstVal = getPST(p, py, px, endgame);
                val += pstVal * 2;

                // ——— 炮架依赖：孤立炮减值 ———
                if (p.type == ChineseChessGame.PieceType.CANNON && py >= 5) {
                    int mounts = countCannonMounts(game, x, y, p.side);
                    if (mounts <= 0) val -= 120;
                    else if (mounts <= 1) val -= 60;
                }

                // ——— 孤军深入惩罚（无保护远赴敌阵） ———
                if (py >= 6) {
                    val -= (py - 4) * (py - 4) * 3;
                } else if (py >= 4 && p.type != ChineseChessGame.PieceType.SOLDIER) {
                    int friendsNearby = countNearbyFriends(game, x, y, p.side);
                    if (friendsNearby == 0) val -= 20;
                }

                // ——— 机动性：可走步数越多越好 ———
                val += game.getMoves(p).size() * 3;

                score += sign * val;
            }
        }

        // ——— 子力协调加分 ———
        score += evaluateCoordination(game);

        // ——— 被攻击子与兵卒推进 ———
        score += evaluateThreatBalance(game);
        score += evaluateSoldierStructure(game);

        // ——— 王安全 ———
        score += evaluateKingSafety(game);

        // ——— 阶段感知：非残局时整体放大5%，使中局评估差异更显著 ———
        if (!endgame) score = score * 105 / 100;

        return score;
    }

    /**
     * 获取棋子在指定位置的位置价值表（PST）分值。
     * <p>根据棋子类型、位置和游戏阶段（开/残局）返回对应PST值。
     *
     * @param p       棋子
     * @param py      黑方视角的行坐标
     * @param px      列坐标
     * @param endgame 是否为残局
     * @return PST分值
     */
    private int getPST(ChineseChessGame.Piece p, int py, int px, boolean endgame) {
        int val = 0;
        switch (p.type) {
            case GENERAL:
                val = (endgame ? PST_GENERAL_END : PST_GENERAL_OPEN)[py][px];
                break;
            case ADVISOR:
                if (py >= 7) val += 10;
                if (px >= 3 && px <= 5) val += 5;
                break;
            case ELEPHANT:
                if (py >= 5) val += 10;
                if (px >= 2 && px <= 6) val += 8;
                break;
            case HORSE:
                val = PST_HORSE[py][px];
                break;
            case CHARIOT:
                val = PST_CHARIOT[py][px];
                break;
            case CANNON:
                val = PST_CANNON[py][px];
                break;
            case SOLDIER:
                if (py >= 5) val = PST_SOLDIER_FRONT[py][px]; // 过河
                else val = PST_SOLDIER_BACK[py][px]; // 未过河
                break;
        }
        return val;
    }

    /**
     * 统计指定位置周围2格范围内的友方棋子数量。
     * <p>用于评估棋子是否有友军保护。
     *
     * @param game  游戏状态
     * @param cx    中心列
     * @param cy    中心行
     * @param side  己方阵营
     * @return 友方棋子数量
     */
    private int countNearbyFriends(ChineseChessGame game, int cx, int cy, ChineseChessGame.Side side) {
        int count = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dy == 0 && dx == 0) continue;
                int ny = cy + dy, nx = cx + dx;
                if (nx >= 0 && nx < 9 && ny >= 0 && ny < 10) {
                    if (board[ny][nx] != null && board[ny][nx].side == side) count++;
                }
            }
        }
        return count;
    }

    /**
     * 统计指定炮的炮架数量（能隔子攻击敌方棋子的方向数）。
     * <p>炮需要至少一个炮架（中间子）才能吃子，无炮架的炮价值大幅降低。
     *
     * @param game  游戏状态
     * @param cx    炮的列坐标
     * @param cy    炮的行坐标
     * @param side  炮的阵营
     * @return 有效炮架方向数
     */
    private int countCannonMounts(ChineseChessGame game, int cx, int cy, ChineseChessGame.Side side) {
        int count = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : dirs) {
            int mountDist = -1;
            int nx = cx + d[0], ny = cy + d[1];
            while (nx >= 0 && nx < 9 && ny >= 0 && ny < 10) {
                if (board[ny][nx] != null) {
                    if (mountDist < 0) {
                        // 找到第一个障碍物作为炮架
                        mountDist = Math.abs(ny - cy) + Math.abs(nx - cx);
                    } else {
                        // 炮架后第一个敌方棋子为可攻击目标
                        if (board[ny][nx].side != side && mountDist <= 2) count++;
                        break;
                    }
                }
                nx += d[0]; ny += d[1];
            }
        }
        return count;
    }

    /**
     * 评估威胁平衡：被攻击棋子的压力值。
     * <p>对每个被敌方攻击的棋子计算压力分，攻击者越多、防守者越少，
     * 压力越大。无防守者的棋子压力额外加重。
     *
     * @param game 游戏状态
     * @return 威胁平衡分数（黑方视角）
     */
    private int evaluateThreatBalance(ChineseChessGame game) {
        int score = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null || p.type == ChineseChessGame.PieceType.GENERAL) continue;

                ChineseChessGame.Side enemy = opponentOf(p.side);
                int attackers = countAttackers(game, enemy, x, y);
                if (attackers <= 0) continue;

                int defenders = countAttackers(game, p.side, x, y);
                int value = PIECE_VALUES[p.type.ordinal()];
                int pressure = Math.min(520, value / 6 + attackers * 24);
                if (defenders == 0) {
                    // 无防守：额外高压
                    pressure += Math.min(420, value / 4);
                } else if (attackers > defenders) {
                    // 攻多于守
                    pressure += Math.min(260, value / 9);
                } else {
                    // 守多于攻：压力减半
                    pressure /= 2;
                }
                score -= sideSign(p.side) * pressure;
            }
        }
        return score;
    }

    /**
     * 评估兵卒结构。
     * <p>考虑因素：
     * <ul>
     *   <li>过河兵卒的基础加分，越深入敌方阵地分值越高</li>
     *   <li>中路（3~5列）兵卒额外加分</li>
     *   <li>深入到第7行及以上的兵卒额外加分</li>
     *   <li>相邻友方兵卒的协同加分</li>
     *   <li>前方有敌方棋子的兵卒加分（有攻击目标）</li>
     * </ul>
     *
     * @param game 游戏状态
     * @return 兵卒结构分数（黑方视角）
     */
    private int evaluateSoldierStructure(ChineseChessGame game) {
        int score = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null || p.type != ChineseChessGame.PieceType.SOLDIER) continue;

                int py = (p.side == ChineseChessGame.Side.BLACK) ? y : (9 - y);
                int bonus = 0;
                if (py >= 5) {
                    // 过河兵卒基础加分
                    bonus += 24 + (py - 5) * 18;
                    if (x >= 3 && x <= 5) bonus += 18; // 中路加分
                    if (py >= 7) bonus += 28; // 深入加分
                }

                // 左右相邻友方兵卒协同加分
                int left = x - 1;
                int right = x + 1;
                if (left >= 0 && board[y][left] != null
                        && board[y][left].side == p.side
                        && board[y][left].type == ChineseChessGame.PieceType.SOLDIER) {
                    bonus += 12;
                }
                if (right < ChineseChessGame.COLS && board[y][right] != null
                        && board[y][right].side == p.side
                        && board[y][right].type == ChineseChessGame.PieceType.SOLDIER) {
                    bonus += 12;
                }

                // 前方有敌方棋子（有攻击目标）
                int forwardY = p.side == ChineseChessGame.Side.BLACK ? y + 1 : y - 1;
                if (forwardY >= 0 && forwardY < ChineseChessGame.ROWS
                        && board[forwardY][x] != null
                        && board[forwardY][x].side != p.side) {
                    bonus += 10;
                }
                score += sideSign(p.side) * bonus;
            }
        }
        return score;
    }

    /**
     * 获取对方的阵营。
     *
     * @param side 己方阵营
     * @return 对方阵营
     */
    private ChineseChessGame.Side opponentOf(ChineseChessGame.Side side) {
        return side == ChineseChessGame.Side.RED
                ? ChineseChessGame.Side.BLACK : ChineseChessGame.Side.RED;
    }

    /**
     * 获取阵营的符号（黑方=+1，红方=-1），用于评估分数计算。
     *
     * @param side 阵营
     * @return +1 或 -1
     */
    private int sideSign(ChineseChessGame.Side side) {
        return side == ChineseChessGame.Side.BLACK ? 1 : -1;
    }

    /**
     * 检查坐标是否在棋盘范围内。
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true 若在棋盘内
     */
    private boolean isInside(int x, int y) {
        return x >= 0 && x < ChineseChessGame.COLS && y >= 0 && y < ChineseChessGame.ROWS;
    }

    /**
     * 检查坐标是否在指定阵营的九宫格内。
     *
     * @param x    列坐标
     * @param y    行坐标
     * @param side 阵营
     * @return true 若在九宫格内
     */
    private boolean inPalace(int x, int y, ChineseChessGame.Side side) {
        if (side == ChineseChessGame.Side.RED) return x >= 3 && x <= 5 && y >= 7 && y <= 9;
        return x >= 3 && x <= 5 && y >= 0 && y <= 2;
    }

    /**
     * 统计指定阵营攻击目标位置的棋子数量。
     *
     * @param game  游戏状态
     * @param side  攻击方阵营
     * @param tx    目标列
     * @param ty    目标行
     * @return 攻击该位置的棋子数量
     */
    private int countAttackers(ChineseChessGame game, ChineseChessGame.Side side, int tx, int ty) {
        int count = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p != null && p.side == side && pieceAttacksSquare(p, tx, ty, board)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 判断棋子是否攻击指定位置。
     * <p>根据棋子类型实现各自的攻击规则：
     * <ul>
     *   <li>将/帅：九宫内一步直走 + 对面将帅（飞将）</li>
     *   <li>士/仕：九宫内一步斜走</li>
     *   <li>象/相：田字走法，检查象眼，不过河</li>
     *   <li>马：日字走法，检查蹩马腿</li>
     *   <li>车：直线走法，检查路径无阻挡</li>
     *   <li>炮：直线走法，需恰好一个炮架</li>
     *   <li>兵/卒：未过河只能前进，过河后可左右</li>
     * </ul>
     *
     * @param p     攻击棋子
     * @param tx    目标列
     * @param ty    目标行
     * @param board 棋盘数组
     * @return true 若该棋子攻击目标位置
     */
    private boolean pieceAttacksSquare(
            ChineseChessGame.Piece p, int tx, int ty, ChineseChessGame.Piece[][] board) {
        if (p.x == tx && p.y == ty) return false;
        int dx = tx - p.x;
        int dy = ty - p.y;
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        switch (p.type) {
            case GENERAL:
                // 飞将：两将同列且中间无子
                if (p.x == tx && board[ty][tx] != null
                        && board[ty][tx].type == ChineseChessGame.PieceType.GENERAL
                        && board[ty][tx].side != p.side
                        && countLineBlockers(board, p.x, p.y, tx, ty) == 0) return true;
                // 正常走法：九宫内一步直走
                return adx + ady == 1 && inPalace(tx, ty, p.side);
            case ADVISOR:
                return adx == 1 && ady == 1 && inPalace(tx, ty, p.side);
            case ELEPHANT:
                if (adx != 2 || ady != 2) return false;
                // 不能过河
                if (p.side == ChineseChessGame.Side.RED && ty < 5) return false;
                if (p.side == ChineseChessGame.Side.BLACK && ty > 4) return false;
                // 检查象眼
                return board[p.y + dy / 2][p.x + dx / 2] == null;
            case HORSE:
                if (!((adx == 2 && ady == 1) || (adx == 1 && ady == 2))) return false;
                // 检查蹩马腿
                int legX = p.x + (adx == 2 ? Integer.signum(dx) : 0);
                int legY = p.y + (ady == 2 ? Integer.signum(dy) : 0);
                return board[legY][legX] == null;
            case CHARIOT:
                // 直线无阻挡
                return (p.x == tx || p.y == ty) && countLineBlockers(board, p.x, p.y, tx, ty) == 0;
            case CANNON:
                // 直线恰好一个炮架
                return (p.x == tx || p.y == ty) && countLineBlockers(board, p.x, p.y, tx, ty) == 1;
            case SOLDIER:
                if (p.side == ChineseChessGame.Side.RED) {
                    if (dx == 0 && dy == -1) return true; // 前进
                    return p.y < 5 && adx == 1 && dy == 0; // 过河后左右
                } else {
                    if (dx == 0 && dy == 1) return true;
                    return p.y > 4 && adx == 1 && dy == 0;
                }
        }
        return false;
    }

    /**
     * 计算两点之间直线上的阻挡棋子数量。
     * <p>两点必须在同一行或同一列上。
     *
     * @param board 棋盘数组
     * @param fromX 起始列
     * @param fromY 起始行
     * @param toX   目标列
     * @param toY   目标行
     * @return 中间阻挡的棋子数量；若不在同一直线上返回 Integer.MAX_VALUE
     */
    private int countLineBlockers(
            ChineseChessGame.Piece[][] board, int fromX, int fromY, int toX, int toY) {
        if (fromX != toX && fromY != toY) return Integer.MAX_VALUE;
        int stepX = Integer.compare(toX, fromX);
        int stepY = Integer.compare(toY, fromY);
        int blockers = 0;
        int x = fromX + stepX;
        int y = fromY + stepY;
        while (x != toX || y != toY) {
            if (board[y][x] != null) blockers++;
            x += stepX;
            y += stepY;
        }
        return blockers;
    }

    /**
     * 评估子力协调性。
     * <p>考虑因素：
     * <ul>
     *   <li>马腿被堵的惩罚</li>
     *   <li>马攻击敌方棋子的奖励</li>
     *   <li>车炮的威胁值（可攻击敌方棋子的价值总和）</li>
     * </ul>
     *
     * @param game 游戏状态
     * @return 协调性分数（黑方视角）
     */
    private int evaluateCoordination(ChineseChessGame game) {
        int score = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        boolean blackHasChariot = false, redHasChariot = false;

        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null) continue;
                if (p.type == ChineseChessGame.PieceType.CHARIOT) {
                    if (p.side == ChineseChessGame.Side.BLACK) blackHasChariot = true;
                    else redHasChariot = true;
                }
            }
        }

        // 马腿检测 → 扣分
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null || p.type != ChineseChessGame.PieceType.HORSE) continue;

                int sign = (p.side == ChineseChessGame.Side.BLACK) ? 1 : -1;
                int blockedLegs = 0;
                // 8个马腿方向及其对应的蹩腿位置和目标位置
                int[][] legs = {{0,-1,0,-2,0,0},{0,-1,0,0,-1,0},{0,1,0,0,1,0},{0,1,0,2,0,0},
                                {-1,0,0,0,0,-1},{-1,0,-2,0,0,0},{1,0,0,0,0,1},{1,0,2,0,0,0}};
                for (int[] leg : legs) {
                    int ly = y + leg[1], lx = x + leg[0];
                    int ty = y + leg[3], tx = x + leg[2];
                    if (ly >= 0 && ly < 10 && lx >= 0 && lx < 9 && board[ly][lx] != null) blockedLegs++;
                    else if (tx >= 0 && tx < 9 && ty >= 0 && ty < 10) {
                        // 马可攻击到的位置若有敌方棋子，加分
                        ChineseChessGame.Piece tgt = board[ty][tx];
                        if (tgt != null && tgt.side != p.side) score += sign * 15;
                    }
                }
                score -= sign * blockedLegs * 25;
            }
        }

        // 车炮配合 → 加分
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null) continue;
                if (p.type == ChineseChessGame.PieceType.CHARIOT
                        || p.type == ChineseChessGame.PieceType.CANNON) {
                    int sign = (p.side == ChineseChessGame.Side.BLACK) ? 1 : -1;
                    int threats = countThreats(game, p, x, y);
                    score += sign * threats;
                }
            }
        }

        return score;
    }

    /**
     * 计算车或炮的威胁值。
     * <p>统计该棋子可攻击的敌方棋子价值，以及控制中心区域的加分。
     *
     * @param game 游戏状态
     * @param p    棋子（车或炮）
     * @param x    棋子列坐标
     * @param y    棋子行坐标
     * @return 威胁值（上限15）
     */
    private int countThreats(ChineseChessGame game, ChineseChessGame.Piece p, int x, int y) {
        int count = 0;
        for (int[] m : game.getMoves(p)) {
            ChineseChessGame.Piece tgt = game.getBoard()[m[1]][m[0]];
            if (tgt != null && tgt.side != p.side) {
                count += PIECE_VALUES[tgt.type.ordinal()] / 50;
            }
            // 控制中心区域加分
            if (m[0] >= 3 && m[0] <= 5 && m[1] >= 3 && m[1] <= 6) count += 2;
        }
        return Math.min(count, 15);
    }

    /**
     * 评估双方将帅的安全性。
     * <p>考虑因素：
     * <ul>
     *   <li>士象保护数量</li>
     *   <li>被将军的惩罚</li>
     *   <li>九宫格内敌方攻击压力</li>
     *   <li>敌方车炮接近将帅的威胁</li>
     * </ul>
     *
     * @param game 游戏状态
     * @return 王安全分数（黑方视角）
     */
    private int evaluateKingSafety(ChineseChessGame game) {
        int score = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();

        // 评估两方将帅安全
        for (ChineseChessGame.Side side : new ChineseChessGame.Side[]{
                ChineseChessGame.Side.BLACK, ChineseChessGame.Side.RED}) {
            int[] kingPos = findKing(game, side);
            if (kingPos == null) return side == ChineseChessGame.Side.BLACK ? -WIN_SCORE : WIN_SCORE;

            int kx = kingPos[0], ky = kingPos[1];
            int sign = (side == ChineseChessGame.Side.BLACK) ? 1 : -1;
            int safety = 30;

            // 士象保护
            int advisors = 0, elephants = 0;
            for (int y = 0; y < ChineseChessGame.ROWS; y++) {
                for (int x = 0; x < ChineseChessGame.COLS; x++) {
                    ChineseChessGame.Piece p = board[y][x];
                    if (p == null || p.side != side) continue;
                    if (p.type == ChineseChessGame.PieceType.ADVISOR) advisors++;
                    if (p.type == ChineseChessGame.PieceType.ELEPHANT) elephants++;
                }
            }
            safety += advisors * 20 + elephants * 15;

            // 将帅暴露惩罚
            ChineseChessGame.Side enemy = (side == ChineseChessGame.Side.BLACK)
                    ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
            if (game.isInCheck(side)) safety -= 260;

            // 九宫格内敌方攻击压力评估
            int palaceDanger = 0;
            int palaceTop = side == ChineseChessGame.Side.BLACK ? 0 : 7;
            int palaceBottom = side == ChineseChessGame.Side.BLACK ? 2 : 9;
            for (int py = palaceTop; py <= palaceBottom; py++) {
                for (int px = 3; px <= 5; px++) {
                    int enemyAttacks = countAttackers(game, enemy, px, py);
                    if (enemyAttacks <= 0) continue;
                    int guards = countAttackers(game, side, px, py);
                    int squareDanger = enemyAttacks * 28 - guards * 10;
                    // 将帅所在格额外危险
                    if (px == kx && py == ky) squareDanger += 70;
                    palaceDanger += Math.max(0, squareDanger);
                }
            }
            safety -= Math.min(360, palaceDanger);

            // 敌方车炮接近将帅的威胁
            for (int y = 0; y < ChineseChessGame.ROWS; y++) {
                for (int x = 0; x < ChineseChessGame.COLS; x++) {
                    ChineseChessGame.Piece ep = board[y][x];
                    if (ep == null || ep.side != enemy) continue;
                    if (ep.type == ChineseChessGame.PieceType.CHARIOT
                            || ep.type == ChineseChessGame.PieceType.CANNON) {
                        if (Math.abs(ep.x - kx) <= 2 && Math.abs(ep.y - ky) <= 3) safety -= 15;
                    }
                }
            }

            score += sign * safety;
        }
        return score;
    }

    /**
     * 在棋盘上查找指定阵营的将/帅位置。
     *
     * @param game 游戏状态
     * @param side 阵营
     * @return 将帅坐标 [x, y]，若未找到返回null（不应发生）
     */
    private int[] findKing(ChineseChessGame game, ChineseChessGame.Side side) {
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++)
            for (int x = 0; x < ChineseChessGame.COLS; x++)
                if (board[y][x] != null && board[y][x].type == ChineseChessGame.PieceType.GENERAL
                        && board[y][x].side == side) return new int[]{x, y};
        return null;
    }

    // ============ Zobrist哈希（简化版：仅用于置换表） ============

    /** Zobrist哈希表：棋盘位置 × 7种棋子 × 2方 的随机数 */
    private static final long[] ZOBRIST_TABLE = new long[10 * 9 * 14];
    /** 走棋方切换的Zobrist异或值 */
    private static final long ZOBRIST_SIDE = 0x9E3779B97F4A7C15L;
    static {
        java.util.Random rng = new java.util.Random(123456789L);
        for (int i = 0; i < ZOBRIST_TABLE.length; i++)
            ZOBRIST_TABLE[i] = rng.nextLong();
    }

    /**
     * 计算当前棋盘的Zobrist哈希值。
     * <p>将每个棋子的位置和类型异或对应的随机数，再根据走棋方异或侧边值。
     * 用于置换表的键值匹配。
     *
     * @param game 游戏状态
     * @return Zobrist哈希值
     */
    private long computeHash(ChineseChessGame game) {
        long h = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++) {
            for (int x = 0; x < ChineseChessGame.COLS; x++) {
                ChineseChessGame.Piece p = board[y][x];
                if (p == null) continue;
                int idx = (y * 9 + x) * 14 + p.type.ordinal() * 2
                        + (p.side == ChineseChessGame.Side.BLACK ? 0 : 1);
                h ^= ZOBRIST_TABLE[idx];
            }
        }
        // 红方走棋时异或侧边值，区分同一局面不同走棋方
        if (game.getCurrentSide() == ChineseChessGame.Side.RED) h ^= ZOBRIST_SIDE;
        return h;
    }

    // ============ 时间控制 ============

    /**
     * 检查搜索是否超时。
     *
     * @return true 若已超过搜索时间上限
     */
    private boolean checkTimeout() {
        if (System.currentTimeMillis() - searchStartMs > maxTimeMs) {
            timedOut = true;
            return true;
        }
        return false;
    }
}

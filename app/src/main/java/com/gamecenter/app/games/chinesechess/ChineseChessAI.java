package com.gamecenter.app.games.chinesechess;

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
 * 6档难度：搜索时间上限分级，同等时间内搜索更深、更准
 */
public class ChineseChessAI {

    private static final int[] LEVEL_TIME_MS = {
        1000, 2000, 3000, 4000, 5000, 10000
    };

    private static final int MAX_DEPTH = 24;
    private static final int TIME_CHECK_INTERVAL = 512;
    private static final long NODE_LIMIT = 20_000_000L;

    private int maxTimeMs;
    private long searchStartMs;
    private boolean timedOut;
    private int nodesSearched;

    private static final int INF = 99999999;
    private static final int WIN_SCORE = INF - 100;
    private static final int MATE_THRESHOLD = WIN_SCORE - 1000;

    // ——— 置换表 ———
    private static final int TT_SIZE = 1 << 20;
    private static final int TT_MASK = TT_SIZE - 1;
    private static final byte TT_EXACT = 0;
    private static final byte TT_ALPHA = 1;
    private static final byte TT_BETA = 2;
    private long[] ttKey = new long[TT_SIZE];
    private int[] ttScore = new int[TT_SIZE];
    private short[] ttDepth = new short[TT_SIZE];
    private short[] ttMove = new short[TT_SIZE]; // 4 bits per coordinate: fx, fy, tx, ty
    private byte[] ttFlag = new byte[TT_SIZE];
    private long[] ttAge = new long[TT_SIZE];
    private long currentAge = 0;

    // ——— 杀手走法 ———
    private int[][] killerMoves;
    private int[] killerCount;

    // ——— 历史启发 ———
    private Map<Integer, Integer> historyTable;

    // ——— 搜索统计 ———
    private int currentBestScore;
    private int[] currentBest;

    private static class RootResult {
        final int score;
        final int[] move;

        RootResult(int score, int[] move) {
            this.score = score;
            this.move = move;
        }
    }

    // ============ 棋子权重 ============
    private static final int[] PIECE_VALUES = {10000, 200, 200, 400, 1000, 450, 100};
    private static final int CHECK_MOVE_BONUS = 320000;
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

    public ChineseChessAI(int level) {
        int idx = Math.max(0, Math.min(level - 1, LEVEL_TIME_MS.length - 1));
        this.maxTimeMs = LEVEL_TIME_MS[idx];
        this.historyTable = new HashMap<>();
        this.killerMoves = new int[MAX_DEPTH + 1][4];
        this.killerCount = new int[MAX_DEPTH + 1];
    }

    private int packMove(int[] move) {
        return (move[0] << 12) | (move[1] << 8) | (move[2] << 4) | move[3];
    }

    private ChineseChessGame.MoveRecord makeSearchMove(ChineseChessGame game, int[] move) {
        ChineseChessGame.MoveRecord record = game.makeMoveSafe(move[0], move[1], move[2], move[3]);
        if (record != null) game.switchSide();
        return record;
    }

    private void undoSearchMove(ChineseChessGame game, ChineseChessGame.MoveRecord record) {
        if (record == null) return;
        game.switchSide();
        game.undoMove(record);
    }

    private int evaluateForSide(ChineseChessGame game) {
        int score = evaluate(game);
        return game.getCurrentSide() == ChineseChessGame.Side.BLACK ? score : -score;
    }

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

    private int scoreToTT(int score, int ply) {
        if (score > MATE_THRESHOLD) return score + ply;
        if (score < -MATE_THRESHOLD) return score - ply;
        return score;
    }

    private int scoreFromTT(int score, int ply) {
        if (score > MATE_THRESHOLD) return score - ply;
        if (score < -MATE_THRESHOLD) return score + ply;
        return score;
    }

    // ============ 核心搜索入口 ============

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

        ChineseChessGame game = realGame.deepCopy();
        ChineseChessGame.Side aiSide = game.getCurrentSide();
        List<int[]> moves = orderRootMoves(game.getAllMoves(aiSide), game);
        if (moves.isEmpty()) return null;
        currentBest = moves.get(0);

        // 迭代加深 + 渴望窗口
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            int alpha = -INF, beta = INF;
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

            if (timedOut) break;
            if (currentBestScore > MATE_THRESHOLD) break;
        }

        if (timedOut && currentBest != null) return currentBest;
        List<int[]> fallback = game.getAllMoves(aiSide);
        if (currentBest == null && !fallback.isEmpty()) return fallback.get(0);
        return currentBest;
    }

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
                score = -search(game, depth - 1, -beta, -alpha, true, 1);
            } else {
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

        // 将军延展
        ChineseChessGame.Side us = game.getCurrentSide();
        boolean inCheck = game.isInCheck(us);
        if (inCheck) depth++;

        if (depth <= 0 || game.isGameOver()) return quiescence(game, alpha, beta);

        // ——— 空走裁剪 ———
        if (!inCheck && !isPVNode && depth >= 3 && !isEndgame(game)) {
            game.switchSide();
            int nullScore = -search(game, depth - 1 - 3, -beta, -beta + 1, false, 0);
            game.switchSide();
            if (nullScore >= beta) return beta;
        }

        List<int[]> moves = orderMoves(game.getAllMoves(us), game, hash, depth, ttIdx);

        // ——— 无效裁剪 ———
        if (moves.isEmpty()) return -WIN_SCORE + 1;
        int staticEval = inCheck ? -INF / 2 : evaluateForSide(game);

        int best = -INF;
        int bestMoveCode = 0;
        byte flag = TT_ALPHA;
        int legalCount = 0;

        for (int moveIdx = 0; moveIdx < moves.size(); moveIdx++) {
            if (timedOut) break;
            int[] m = moves.get(moveIdx);

            ChineseChessGame.MoveRecord r = makeSearchMove(game, m);
            boolean newCheck = r != null && game.isInCheck(game.getCurrentSide());
            undoSearchMove(game, r);

            // ——— 无效裁剪 ———
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
                score = -search(game, depth - 1, -beta, -alpha, false, 1);
            } else {
                // ——— LMR ———
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

    private int search(ChineseChessGame game, int depth, int alpha, int beta, boolean isPVNode, int ply) {
        nodesSearched++;
        if (nodesSearched % TIME_CHECK_INTERVAL == 0 && checkTimeout()) return evaluateForSide(game);
        if (nodesSearched > NODE_LIMIT) { timedOut = true; return evaluateForSide(game); }

        ChineseChessGame.Side us = game.getCurrentSide();
        boolean inCheck = game.isInCheck(us);
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
                score = -search(game, depth - 1, -beta, -alpha, false, ply + 1);
            } else {
                // ——— LMR ———
                int reduction = 0;
                if (depth >= 3 && legalCount >= 4 && !newCheck && !inCheck && !capture) {
                    reduction = 1 + Math.min((legalCount - 4) / 4, 2);
                    if (isPVNode) reduction = Math.min(reduction, 1);
                }
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
                        flag = TT_BETA;
                        if (!newCheck) updateKiller(bestMoveCode, depth);
                        updateHistory(bestMoveCode, depth);
                        break;
                    }
                }
            }
        }

        if (legalCount == 0) best = evaluateForSide(game);

        if (!timedOut) {
            ttKey[ttIdx] = hash; ttScore[ttIdx] = scoreToTT(best, ply);
            ttDepth[ttIdx] = (short) depth; ttMove[ttIdx] = (short) bestMoveCode;
            ttFlag[ttIdx] = flag; ttAge[ttIdx] = currentAge;
        }
        return best;
    }

    // ============ 静止期搜索 ============

    private int quiescence(ChineseChessGame game, int alpha, int beta) {
        nodesSearched++;
        if (nodesSearched % TIME_CHECK_INTERVAL == 0 && checkTimeout()) return evaluateForSide(game);

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
                    int seeGain = PIECE_VALUES[cap.type.ordinal()];
                    int seeLoss = PIECE_VALUES[p.type.ordinal()];
                    if (seeGain < seeLoss / 3 && seeGain < 100) continue;

                    ChineseChessGame.MoveRecord r = game.makeMoveSafe(x, y, m[0], m[1]);
                    if (r != null && !game.isInCheck(us)) {
                        game.switchSide();
                        int score = -quiescence(game, -beta, -alpha);
                        game.switchSide();
                        game.undoMove(r);
                        if (score >= beta) return beta;
                        if (score > alpha) alpha = score;
                    } else if (r != null) {
                        game.undoMove(r);
                    }
                }
            }
        }
        return alpha;
    }

    // ============ 走法排序 ============

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

    private int scoreMove(int[] m, ChineseChessGame game, boolean includeCheckProbe) {
        ChineseChessGame.Piece piece = game.getBoard()[m[1]][m[0]];
        if (piece == null) return 0;

        int score = evaluateMove(m, game);
        ChineseChessGame.Piece cap = game.getBoard()[m[3]][m[2]];
        if (cap != null) {
            int capValue = PIECE_VALUES[cap.type.ordinal()];
            int pieceValue = PIECE_VALUES[piece.type.ordinal()];
            score += capValue * 100 - pieceValue / 10;
            if (cap.type == ChineseChessGame.PieceType.GENERAL) {
                score += GENERAL_CAPTURE_BONUS;
            }
            if (capValue >= pieceValue) {
                score += capValue * 8;
            }
        }

        if (includeCheckProbe && moveGivesCheck(game, m)) {
            score += CHECK_MOVE_BONUS;
        }
        return score;
    }

    private boolean moveGivesCheck(ChineseChessGame game, int[] move) {
        ChineseChessGame.MoveRecord record = makeSearchMove(game, move);
        if (record == null) return false;
        boolean givesCheck = game.isInCheck(game.getCurrentSide());
        undoSearchMove(game, record);
        return givesCheck;
    }

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
        if (tx >= 3 && tx <= 5) score += 5; // 中心
        return score;
    }

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

    private void updateHistory(int moveCode, int depth) {
        int key = moveCode & 0xFFFF;
        int val = historyTable.getOrDefault(key, 0) + depth * depth;
        historyTable.put(key, Math.min(val, 500000));
    }

    // ============ 增强评估函数 ============

    private int totalMaterial(ChineseChessGame game) {
        int total = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++)
            for (int x = 0; x < ChineseChessGame.COLS; x++)
                if (board[y][x] != null) total += PIECE_VALUES[board[y][x].type.ordinal()];
        return total;
    }

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

                // ——— PST ———
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

                // ——— 机动性 ———
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

        // ——— 阶段感知 ———
        if (!endgame) score = score * 105 / 100;

        return score;
    }

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
                        mountDist = Math.abs(ny - cy) + Math.abs(nx - cx);
                    } else {
                        if (board[ny][nx].side != side && mountDist <= 2) count++;
                        break;
                    }
                }
                nx += d[0]; ny += d[1];
            }
        }
        return count;
    }

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
                    pressure += Math.min(420, value / 4);
                } else if (attackers > defenders) {
                    pressure += Math.min(260, value / 9);
                } else {
                    pressure /= 2;
                }
                score -= sideSign(p.side) * pressure;
            }
        }
        return score;
    }

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
                    bonus += 24 + (py - 5) * 18;
                    if (x >= 3 && x <= 5) bonus += 18;
                    if (py >= 7) bonus += 28;
                }

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

    private ChineseChessGame.Side opponentOf(ChineseChessGame.Side side) {
        return side == ChineseChessGame.Side.RED
                ? ChineseChessGame.Side.BLACK : ChineseChessGame.Side.RED;
    }

    private int sideSign(ChineseChessGame.Side side) {
        return side == ChineseChessGame.Side.BLACK ? 1 : -1;
    }

    private boolean isInside(int x, int y) {
        return x >= 0 && x < ChineseChessGame.COLS && y >= 0 && y < ChineseChessGame.ROWS;
    }

    private boolean inPalace(int x, int y, ChineseChessGame.Side side) {
        if (side == ChineseChessGame.Side.RED) return x >= 3 && x <= 5 && y >= 7 && y <= 9;
        return x >= 3 && x <= 5 && y >= 0 && y <= 2;
    }

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

    private boolean pieceAttacksSquare(
            ChineseChessGame.Piece p, int tx, int ty, ChineseChessGame.Piece[][] board) {
        if (p.x == tx && p.y == ty) return false;
        int dx = tx - p.x;
        int dy = ty - p.y;
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        switch (p.type) {
            case GENERAL:
                if (p.x == tx && board[ty][tx] != null
                        && board[ty][tx].type == ChineseChessGame.PieceType.GENERAL
                        && board[ty][tx].side != p.side
                        && countLineBlockers(board, p.x, p.y, tx, ty) == 0) return true;
                return adx + ady == 1 && inPalace(tx, ty, p.side);
            case ADVISOR:
                return adx == 1 && ady == 1 && inPalace(tx, ty, p.side);
            case ELEPHANT:
                if (adx != 2 || ady != 2) return false;
                if (p.side == ChineseChessGame.Side.RED && ty < 5) return false;
                if (p.side == ChineseChessGame.Side.BLACK && ty > 4) return false;
                return board[p.y + dy / 2][p.x + dx / 2] == null;
            case HORSE:
                if (!((adx == 2 && ady == 1) || (adx == 1 && ady == 2))) return false;
                int legX = p.x + (adx == 2 ? Integer.signum(dx) : 0);
                int legY = p.y + (ady == 2 ? Integer.signum(dy) : 0);
                return board[legY][legX] == null;
            case CHARIOT:
                return (p.x == tx || p.y == ty) && countLineBlockers(board, p.x, p.y, tx, ty) == 0;
            case CANNON:
                return (p.x == tx || p.y == ty) && countLineBlockers(board, p.x, p.y, tx, ty) == 1;
            case SOLDIER:
                if (p.side == ChineseChessGame.Side.RED) {
                    if (dx == 0 && dy == -1) return true;
                    return p.y < 5 && adx == 1 && dy == 0;
                } else {
                    if (dx == 0 && dy == 1) return true;
                    return p.y > 4 && adx == 1 && dy == 0;
                }
        }
        return false;
    }

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
                int[][] legs = {{0,-1,0,-2,0,0},{0,-1,0,0,-1,0},{0,1,0,0,1,0},{0,1,0,2,0,0},
                                {-1,0,0,0,0,-1},{-1,0,-2,0,0,0},{1,0,0,0,0,1},{1,0,2,0,0,0}};
                for (int[] leg : legs) {
                    int ly = y + leg[1], lx = x + leg[0];
                    int ty = y + leg[3], tx = x + leg[2];
                    if (ly >= 0 && ly < 10 && lx >= 0 && lx < 9 && board[ly][lx] != null) blockedLegs++;
                    else if (tx >= 0 && tx < 9 && ty >= 0 && ty < 10) {
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

    private int countThreats(ChineseChessGame game, ChineseChessGame.Piece p, int x, int y) {
        int count = 0;
        for (int[] m : game.getMoves(p)) {
            ChineseChessGame.Piece tgt = game.getBoard()[m[1]][m[0]];
            if (tgt != null && tgt.side != p.side) {
                count += PIECE_VALUES[tgt.type.ordinal()] / 50;
            }
            if (m[0] >= 3 && m[0] <= 5 && m[1] >= 3 && m[1] <= 6) count += 2;
        }
        return Math.min(count, 15);
    }

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

            int palaceDanger = 0;
            int palaceTop = side == ChineseChessGame.Side.BLACK ? 0 : 7;
            int palaceBottom = side == ChineseChessGame.Side.BLACK ? 2 : 9;
            for (int py = palaceTop; py <= palaceBottom; py++) {
                for (int px = 3; px <= 5; px++) {
                    int enemyAttacks = countAttackers(game, enemy, px, py);
                    if (enemyAttacks <= 0) continue;
                    int guards = countAttackers(game, side, px, py);
                    int squareDanger = enemyAttacks * 28 - guards * 10;
                    if (px == kx && py == ky) squareDanger += 70;
                    palaceDanger += Math.max(0, squareDanger);
                }
            }
            safety -= Math.min(360, palaceDanger);

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

    private int[] findKing(ChineseChessGame game, ChineseChessGame.Side side) {
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int y = 0; y < ChineseChessGame.ROWS; y++)
            for (int x = 0; x < ChineseChessGame.COLS; x++)
                if (board[y][x] != null && board[y][x].type == ChineseChessGame.PieceType.GENERAL
                        && board[y][x].side == side) return new int[]{x, y};
        return null;
    }

    // ============ Zobrist哈希（简化版：仅用于置换表） ============

    private static final long[] ZOBRIST_TABLE = new long[10 * 9 * 14]; // 棋盘位置 × 7种棋子 × 2方
    private static final long ZOBRIST_SIDE = 0x9E3779B97F4A7C15L;
    static {
        java.util.Random rng = new java.util.Random(123456789L);
        for (int i = 0; i < ZOBRIST_TABLE.length; i++)
            ZOBRIST_TABLE[i] = rng.nextLong();
    }

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
        if (game.getCurrentSide() == ChineseChessGame.Side.RED) h ^= ZOBRIST_SIDE;
        return h;
    }

    // ============ 时间控制 ============

    private boolean checkTimeout() {
        if (System.currentTimeMillis() - searchStartMs > maxTimeMs) {
            timedOut = true;
            return true;
        }
        return false;
    }
}

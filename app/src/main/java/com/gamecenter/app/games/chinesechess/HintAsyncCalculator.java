package com.gamecenter.app.games.chinesechess;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 中国象棋 AI 提示异步计算器。
 *
 * <p>在后台线程执行大师级 AI 搜索，计算完成后在主线程回调结果。
 * 集成 {@link HintCache} 实现缓存加速：相同局面 + 相同难度命中缓存时
 * 直接返回，不触发 AI 搜索。</p>
 *
 * <p>线程安全：通过 {@link Future} 跟踪当前计算任务，支持取消。</p>
 *
 * @author AI Assistant
 * @since 2026-07-23
 */
public class HintAsyncCalculator {

    private static final String TAG = "HintAsyncCalculator";

    /** 提示回调接口。 */
    public interface HintCallback {
        void onHintReady(HintResult result);
        void onHintError(String error);
    }

    private final ExecutorService executor;
    private final Handler mainHandler;
    private final HintCache cache;

    /** 当前计算任务的 Future，用于取消。 */
    private volatile Future<?> currentFuture;

    /** 生成递增的计算 ID，防止旧结果覆盖新请求。 */
    private final AtomicLong generationCounter = new AtomicLong(0);

    public HintAsyncCalculator(HintCache cache) {
        this(cache, null);
    }

    public HintAsyncCalculator(HintCache cache, ExecutorService executor) {
        this.cache = cache;
        this.executor = executor;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 异步计算提示。
     *
     * <p>流程：查缓存 → 命中则主线程直接回调 → 未命中则后台线程 AI 搜索
     * → 主线程回调结果并写入缓存。</p>
     *
     * @param board      int[10][9] 棋盘数组（会深拷贝，不会被修改）
     * @param playerSide 当前走棋方（1=红方，2=黑方）
     * @param difficulty  AI 难度等级（1~4）
     * @param callback   结果回调
     */
    public void calculateHintAsync(int[][] board, int playerSide, int difficulty,
                                   HintCallback callback) {
        if (board == null || callback == null) return;

        // 深拷贝棋盘，避免后台修改
        int[][] boardCopy = copyBoard(board);
        String boardHash = cache.computeBoardHash(boardCopy);
        String cacheKey = cache.buildKey(boardHash, difficulty);

        // 查缓存
        HintResult cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Cache hit, returning cached hint");
            mainHandler.post(() -> callback.onHintReady(cached));
            return;
        }

        // 取消之前的计算
        cancelCalculation();

        long gen = generationCounter.incrementAndGet();
        // playerSide: 1=红方(玩家) → aiSide=-1(黑方/AI); 2=黑方 → aiSide=1(红方)
        int aiSide = (playerSide == 1) ? -1 : 1;

        Runnable task = () -> {
            long startMs = System.currentTimeMillis();
            try {
                ChineseChessAI ai = new ChineseChessAI(4); // 大师级提示专用
                int[] bestMove = ai.getBestMove(boardCopy, 4, aiSide);
                long elapsed = System.currentTimeMillis() - startMs;

                if (Thread.currentThread().isInterrupted()) return;

                // 生成解释文本
                String explanation = generateExplanation(boardCopy, bestMove, playerSide);

                HintResult result = new HintResult(bestMove, explanation, elapsed, difficulty);

                // 写入缓存
                cache.put(cacheKey, result);

                // 主线程回调
                mainHandler.post(() -> {
                    if (generationCounter.get() == gen) {
                        callback.onHintReady(result);
                    }
                });
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startMs;
                Log.e(TAG, "Hint calculation failed", e);
                if (Thread.currentThread().isInterrupted()) return;
                mainHandler.post(() -> {
                    if (generationCounter.get() == gen) {
                        callback.onHintError("计算失败: " + e.getMessage());
                    }
                });
            }
        };

        if (executor != null) {
            currentFuture = executor.submit(task);
        } else {
            Thread thread = new Thread(task, "HintCalc");
            thread.setDaemon(true);
            currentFuture = null;
            thread.start();
        }
    }

    /**
     * 取消当前正在进行的计算。
     */
    public void cancelCalculation() {
        generationCounter.incrementAndGet();
        if (currentFuture != null && !currentFuture.isDone()) {
            currentFuture.cancel(true);
            currentFuture = null;
        }
    }

    /**
     * 生成提示解释文本。
     */
    private String generateExplanation(int[][] board, int[] move, int playerSide) {
        if (move == null || move.length < 4) return "";

        int fromR = move[0], fromC = move[1];
        int toR = move[2], toC = move[3];

        int piece = board[fromR][fromC];
        int target = board[toR][toC];

        if (piece == 0) return "";

        boolean isRed = piece > 0;
        int type = Math.abs(piece);
        String pieceName = ChineseChessView.getPieceName(type, isRed);

        StringBuilder sb = new StringBuilder();

        if (target != 0) {
            int targetType = Math.abs(target);
            boolean targetIsRed = target > 0;
            String targetName = ChineseChessView.getPieceName(targetType, targetIsRed);
            sb.append("吃掉对方").append(targetName);
        }

        // 检查是否将军
        int[][] testBoard = copyBoard(board);
        testBoard[toR][toC] = testBoard[fromR][fromC];
        testBoard[fromR][fromC] = 0;
        int opponentSide = isRed ? 2 : 1;
        if (isInCheckOnBoard(testBoard, opponentSide)) {
            if (sb.length() > 0) sb.append("，");
            sb.append("形成将军");
        }

        if (sb.length() == 0) {
            sb.append(pieceName).append("移动到目标位置");
        }

        return sb.toString();
    }

    private boolean isInCheckOnBoard(int[][] board, int side) {
        int target = (side == 1) ? 1 : -1;
        int kingRow = -1, kingCol = -1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (Math.abs(board[r][c]) == 1) {
                    if ((side == 1 && board[r][c] > 0) || (side == 2 && board[r][c] < 0)) {
                        kingRow = r;
                        kingCol = c;
                        break;
                    }
                }
            }
            if (kingRow >= 0) break;
        }
        if (kingRow < 0) return false;

        int attackerSign = (side == 1) ? -1 : 1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == 0) continue;
                if ((attackerSign > 0 && board[r][c] > 0)
                        || (attackerSign < 0 && board[r][c] < 0)) {
                    if (canAttackSimple(board, r, c, kingRow, kingCol)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canAttackSimple(int[][] board, int fromR, int fromC, int toR, int toC) {
        int piece = board[fromR][fromC];
        if (piece == 0) return false;
        int type = Math.abs(piece);
        int dr = toR - fromR;
        int dc = toC - fromC;

        switch (type) {
            case 1: return Math.abs(dr) + Math.abs(dc) == 1;
            case 2: return Math.abs(dr) == 1 && Math.abs(dc) == 1;
            case 3: return Math.abs(dr) == 2 && Math.abs(dc) == 2;
            case 4: return (Math.abs(dr) == 2 && Math.abs(dc) == 1)
                    || (Math.abs(dr) == 1 && Math.abs(dc) == 2);
            case 5:
                if (dr != 0 && dc != 0) return false;
                return isPathClearSimple(board, fromR, fromC, toR, toC);
            case 6:
                if (dr != 0 && dc != 0) return false;
                return countPiecesBetweenSimple(board, fromR, fromC, toR, toC) == 1;
            case 7:
                if (piece > 0) {
                    if (fromR >= 5) return dr == -1 && dc == 0;
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                } else {
                    if (fromR <= 4) return dr == 1 && dc == 0;
                    return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
        }
        return false;
    }

    private boolean isPathClearSimple(int[][] board, int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) {
                if (board[r1][c] != 0) return false;
            }
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) {
                if (board[r][c1] != 0) return false;
            }
        }
        return true;
    }

    private int countPiecesBetweenSimple(int[][] board, int r1, int c1, int r2, int c2) {
        int count = 0;
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) {
                if (board[r1][c] != 0) count++;
            }
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) {
                if (board[r][c1] != 0) count++;
            }
        }
        return count;
    }

    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[src.length][];
        for (int r = 0; r < src.length; r++) {
            copy[r] = src[r].clone();
        }
        return copy;
    }
}

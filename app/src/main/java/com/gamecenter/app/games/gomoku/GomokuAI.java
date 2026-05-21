package com.gamecenter.app.games.gomoku;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 五子棋AI引擎，基于Minimax搜索 + Alpha-Beta剪枝。
 * <p>
 * 核心算法采用迭代加深搜索（Iterative Deepening），在时间限制内
 * 逐步增加搜索深度，返回当前已完成的最佳着法。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>6个难度等级对应不同的搜索时间限制（500ms~10000ms）</li>
 *   <li>使用威胁评估（{@link Threat}）进行着法排序和局面评估</li>
 *   <li>防御评分乘以1.18/1.25的权重偏置，使AI更重视防守</li>
 *   <li>候选着法仅考虑已有棋子周围2格范围内的空位，大幅减少搜索空间</li>
 * <li>强制着法检测：优先处理立即获胜、阻挡对手获胜、应对重大威胁</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是五子棋AI的"大脑"，负责决定AI下在哪里。
 * 它的思考过程就像一个棋手：
 * 1. 先看看有没有一步就能赢的棋（强制着法）
 * 2. 再看看对手有没有一步就能赢的棋（必须防守）
 * 3. 如果都没有，就用"Minimax搜索"来推演各种走法，
 *    就像棋手在脑海中模拟"如果我下这里，对手会下哪里，我再怎么下……"
 * 4. 搜索时间有限（难度越高时间越长），时间到了就选当前找到的最好的一步
 */
public class GomokuAI {

    // 各难度等级对应的搜索时间限制（毫秒）
    // 难度1只给0.5秒思考，难度6给10秒——就像从"随便下下"到"深思熟虑"
    private static final int[] LEVEL_TIME_MS = {
            500,
            1500,
            3000,
            5000,
            7000,
            10000
    };

    // 最大搜索深度：AI最多往前看10步棋
    private static final int MAX_DEPTH = 10;

    // 超时检查间隔：每搜索256个节点检查一次是否超时
    // 不是每步都检查，因为检查时间本身也有开销，就像不用每走一步都看表
    private static final int TIME_CHECK_INTERVAL = 256;

    // 获胜评分基准值：分数高到这个程度就意味着赢了
    private static final int WIN_SCORE = 10_000_000;

    /** 当前难度对应的最大搜索时间 */
    private final int maxTimeMs;

    /** 搜索开始时间戳 */
    private long searchStartMs;

    /** 是否已超时 */
    private boolean timedOut;

    /** 已搜索的节点数 */
    private int nodesSearched;

    /**
     * 构造AI引擎。
     *
     * @param level 难度等级（1~6）
     */
    public GomokuAI(int level) {
        int idx = Math.max(0, Math.min(level - 1, LEVEL_TIME_MS.length - 1));
        this.maxTimeMs = LEVEL_TIME_MS[idx];
    }

    /**
     * 检查是否超时。
     * <p>
     * 超时后设置标志位，搜索循环将逐步退出。
     *
     * @return 超时返回true
     */
    private boolean checkTimeout() {
        if (System.currentTimeMillis() - searchStartMs > maxTimeMs) {
            timedOut = true;
            return true;
        }
        return false;
    }

    /**
     * 评估指定位置对某方的威胁值。
     * <p>
     * 在四个方向上扫描所有包含该位置的5格窗口，
     * 统计窗口内的己方棋子数、空位数和开放端数，
     * 并据此计算威胁评分。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 评估方
     * @param board  棋盘数组
     * @return 威胁评估结果
     */
    private Threat evaluateMoveThreat(int x, int y, int player, int[][] board) {
        Threat threat = new Threat();
        if (player == GomokuGame.EMPTY) return threat;

        for (int[] dir : GomokuGame.DIRECTIONS) {
            // 遍历以(x,y)为基准的5个可能的5格窗口
            for (int offset = -4; offset <= 0; offset++) {
                int startX = x + dir[0] * offset;
                int startY = y + dir[1] * offset;
                int stones = 0;
                int empty = 0;
                boolean blocked = false;

                for (int i = 0; i < 5; i++) {
                    int cx = startX + dir[0] * i;
                    int cy = startY + dir[1] * i;
                    if (!isInside(cx, cy)) {
                        blocked = true;
                        break;
                    }
                    int cell = board[cy][cx];
                    if (cell == player) {
                        stones++;
                    } else if (cell == GomokuGame.EMPTY) {
                        empty++;
                    } else {
                        // 窗口内包含对方棋子，此窗口无效
                        blocked = true;
                        break;
                    }
                }

                if (blocked) continue;

                // 计算窗口两端的开放性
                int beforeX = startX - dir[0];
                int beforeY = startY - dir[1];
                int afterX = startX + dir[0] * 5;
                int afterY = startY + dir[1] * 5;
                int openEnds = (isEmpty(board, beforeX, beforeY) ? 1 : 0)
                        + (isEmpty(board, afterX, afterY) ? 1 : 0);

                addWindowScore(threat, stones, empty, openEnds);
            }
        }

        // 组合威胁加成：活四、双四、双活三
        if (threat.openFours > 0) threat.score += 1_500_000;
        if (threat.fours >= 2) threat.score += 1_200_000;
        if (threat.openThrees >= 2) threat.score += 120_000;
        return threat;
    }

    /**
     * 根据窗口内的棋子数、空位数和开放端数计算威胁评分。
     * <p>
     * 评分体系：
     * <ul>
     *   <li>五连（已赢）：10,000,000</li>
     *   <li>活四（两端开放的四）：900,000</li>
     *   <li>冲四（一端封闭的四）：180,000</li>
     *   <li>活三（两端开放的三）：35,000</li>
     *   <li>眠三（一端封闭的三）：4,000</li>
     *   <li>死三（两端封闭的三）：800</li>
     *   <li>活二：1,500 / 眠二：250</li>
     *   <li>活一：80 / 眠一：10</li>
     * </ul>
     *
     * @param threat   威胁对象（累加评分）
     * @param stones   窗口内己方棋子数
     * @param empty    窗口内空位数
     * @param openEnds 开放端数（0/1/2）
     */
    private void addWindowScore(Threat threat, int stones, int empty, int openEnds) {
        if (stones >= 5) {
            threat.wins++;
            threat.score += WIN_SCORE;
        } else if (stones == 4 && empty == 1) {
            threat.fours++;
            if (openEnds == 2) {
                threat.openFours++;
                threat.score += 900_000;
            } else {
                threat.score += 180_000;
            }
        } else if (stones == 3 && empty == 2) {
            if (openEnds == 2) {
                threat.openThrees++;
                threat.score += 35_000;
            } else if (openEnds == 1) {
                threat.score += 4_000;
            } else {
                threat.score += 800;
            }
        } else if (stones == 2 && empty == 3) {
            threat.score += openEnds == 2 ? 1_500 : 250;
        } else if (stones == 1 && empty == 4) {
            threat.score += openEnds == 2 ? 80 : 10;
        }
    }

    /**
     * 评估指定位置对某方的综合评分（含中心偏置）。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 评估方
     * @param board  棋盘数组
     * @return 综合评分
     */
    private int evaluatePosition(int x, int y, int player, int[][] board) {
        Threat threat = evaluateMoveThreat(x, y, player, board);
        return threat.score + centerBias(x, y);
    }

    /**
     * 全局局面评估函数。
     * <p>
     * 遍历棋盘上所有棋子，累加AI方评分并减去人类方评分。
     * 人类方评分乘以1.18的偏置，使评估更重视防守。
     *
     * @param board    棋盘数组
     * @param aiPlayer AI方颜色
     * @return 正值表示AI优势，负值表示AI劣势
     */
    private int evaluate(int[][] board, int aiPlayer) {
        int humanPlayer = getOpponent(aiPlayer);
        int score = 0;
        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] == aiPlayer) {
                    score += evaluatePosition(x, y, aiPlayer, board);
                } else if (board[y][x] == humanPlayer) {
                    // 防守偏置：人类方评分权重更高
                    score -= (int) (evaluatePosition(x, y, humanPlayer, board) * 1.18);
                }
            }
        }
        return score;
    }

    /**
     * 获取候选着法列表。
     * <p>
     * 仅考虑已有棋子周围2格范围内的空位，大幅减少搜索空间。
     * 使用Set去重。若棋盘无棋子，返回中心点。
     *
     * @param board 棋盘数组
     * @return 候选着法坐标列表
     */
    private List<int[]> getCandidateMoves(int[][] board) {
        List<int[]> moves = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        boolean hasPiece = false;

        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] == GomokuGame.EMPTY) continue;
                hasPiece = true;
                // 扫描周围2格范围
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (isInside(nx, ny) && board[ny][nx] == GomokuGame.EMPTY) {
                            // 使用坐标组合的long值去重
                            long key = ((long) ny << 32) | (nx & 0xFFFFFFFFL);
                            if (seen.add(key)) {
                                moves.add(new int[]{nx, ny});
                            }
                        }
                    }
                }
            }
        }

        // 棋盘为空时下天元
        if (!hasPiece) {
            moves.add(new int[]{GomokuGame.BOARD_SIZE / 2, GomokuGame.BOARD_SIZE / 2});
        }
        return moves;
    }

    /**
     * 检查指定位置是否形成五连。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 棋子颜色
     * @param board  棋盘数组
     * @return 形成五连返回true
     */
    private boolean checkWinAt(int x, int y, int player, int[][] board) {
        if (player == GomokuGame.EMPTY) return false;
        for (int[] dir : GomokuGame.DIRECTIONS) {
            int count = 1;
            // 正方向计数
            for (int step = 1; step < 5; step++) {
                int nx = x + dir[0] * step;
                int ny = y + dir[1] * step;
                if (isInside(nx, ny) && board[ny][nx] == player) {
                    count++;
                } else {
                    break;
                }
            }
            // 反方向计数
            for (int step = 1; step < 5; step++) {
                int nx = x - dir[0] * step;
                int ny = y - dir[1] * step;
                if (isInside(nx, ny) && board[ny][nx] == player) {
                    count++;
                } else {
                    break;
                }
            }
            if (count >= 5) return true;
        }
        return false;
    }

    /**
     * Minimax搜索 + Alpha-Beta剪枝。
     * <p>
     * 递归搜索到指定深度，使用Alpha-Beta剪枝减少搜索量。
     * 每隔256个节点检查一次超时。获胜时评分乘以(depth+1)，
     * 使AI偏好更快的胜利。
     *
     * 【初学者提示】Minimax是什么？
     * 想象你在下棋，你会选对自己最有利的走法（Max），
     * 但对手也会选对他最有利（对你最不利）的走法（Min）。
     * Minimax就是交替模拟"我选最好的，对手选最差的"这个过程。
     * Alpha-Beta剪枝就像"剪掉不需要看的分支"：
     * 如果已经找到一个很好的走法，那些明显更差的走法就不需要再看了，
     * 就像你不会去试明显不好的棋。
     *
     * @param board        棋盘数组（搜索中直接修改，回溯时还原）
     * @param depth        剩余搜索深度
     * @param alpha        Alpha值（最大化方当前最优）
     * @param beta         Beta值（最小化方当前最优）
     * @param isMaximizing 当前是否为最大化方（AI方）
     * @param aiPlayer     AI方颜色
     * @param lastMoveInfo 上一手信息 [x, y, player]
     * @return 评估分数
     */
    private double minimax(int[][] board, int depth, double alpha, double beta,
                           boolean isMaximizing, int aiPlayer, int[] lastMoveInfo) {
        nodesSearched++;
        // 定期检查超时
        if ((nodesSearched & (TIME_CHECK_INTERVAL - 1)) == 0 && checkTimeout()) {
            return evaluate(board, aiPlayer);
        }
        // 检查上一手是否获胜
        if (lastMoveInfo != null && checkWinAt(lastMoveInfo[0], lastMoveInfo[1], lastMoveInfo[2], board)) {
            // 胜利评分乘以深度，偏好更快获胜
            return (lastMoveInfo[2] == aiPlayer ? 1 : -1) * WIN_SCORE * (depth + 1);
        }
        // 到达搜索深度上限，返回静态评估
        if (depth == 0) return evaluate(board, aiPlayer);

        int player = isMaximizing ? aiPlayer : getOpponent(aiPlayer);
        // 深层搜索减少候选数量以加速
        int limit = depth >= 4 ? 10 : 12;
        List<int[]> topMoves = scoreAndSortMoves(getCandidateMoves(board), board, player, limit);
        if (topMoves.isEmpty()) return evaluate(board, aiPlayer);

        if (isMaximizing) {
            double maxEval = -Double.MAX_VALUE;
            for (int[] move : topMoves) {
                if (timedOut) break;
                board[move[1]][move[0]] = player;
                double eval = minimax(board, depth - 1, alpha, beta, false, aiPlayer,
                        new int[]{move[0], move[1], player});
                board[move[1]][move[0]] = GomokuGame.EMPTY;
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                // Alpha-Beta剪枝
                if (beta <= alpha || timedOut) break;
            }
            return maxEval;
        }

        double minEval = Double.MAX_VALUE;
        for (int[] move : topMoves) {
            if (timedOut) break;
            board[move[1]][move[0]] = player;
            double eval = minimax(board, depth - 1, alpha, beta, true, aiPlayer,
                    new int[]{move[0], move[1], player});
            board[move[1]][move[0]] = GomokuGame.EMPTY;
            minEval = Math.min(minEval, eval);
            beta = Math.min(beta, eval);
            if (beta <= alpha || timedOut) break;
        }
        return minEval;
    }

    /**
     * 对候选着法进行评分排序，取前limit个。
     * <p>
     * 使用 {@link #scoreMoveForPlayer} 进行快速评分，
     * 按分数降序排列，仅保留前limit个着法用于搜索。
     *
     * @param moves  候选着法列表
     * @param board  棋盘数组
     * @param player 当前执子方
     * @param limit  保留数量上限
     * @return 排序后的候选着法列表
     */
    private List<int[]> scoreAndSortMoves(List<int[]> moves, int[][] board, int player, int limit) {
        moves.sort((a, b) -> Integer.compare(
                scoreMoveForPlayer(b[0], b[1], player, board),
                scoreMoveForPlayer(a[0], a[1], player, board)));

        List<int[]> result = new ArrayList<>();
        int count = Math.min(limit, moves.size());
        for (int i = 0; i < count; i++) {
            result.add(moves.get(i));
        }
        return result;
    }

    /**
     * 评估某位置对某方的着法评分（用于着法排序）。
     * <p>
     * 同时计算进攻评分和防守评分，防守评分乘以1.25的偏置。
     * 还考虑立即获胜、阻挡对手获胜、活四/双四/双活三等威胁。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 当前执子方
     * @param board  棋盘数组
     * @return 着法评分
     */
    private int scoreMoveForPlayer(int x, int y, int player, int[][] board) {
        int opponent = getOpponent(player);

        // 评估进攻威胁
        board[y][x] = player;
        Threat attack = evaluateMoveThreat(x, y, player, board);
        boolean winsNow = checkWinAt(x, y, player, board);
        board[y][x] = GomokuGame.EMPTY;

        // 评估防守威胁
        board[y][x] = opponent;
        Threat defense = evaluateMoveThreat(x, y, opponent, board);
        boolean blocksWin = checkWinAt(x, y, opponent, board);
        board[y][x] = GomokuGame.EMPTY;

        int score = attack.score + (int) (defense.score * 1.25) + centerBias(x, y);
        if (winsNow) score += WIN_SCORE;
        if (blocksWin) score += WIN_SCORE / 2;
        if (attack.openFours > 0 || attack.fours >= 2) score += 1_000_000;
        if (defense.openFours > 0 || defense.fours >= 2) score += 1_200_000;
        if (attack.openThrees >= 2) score += 90_000;
        if (defense.openThrees >= 2) score += 110_000;
        return score;
    }

    /**
     * AI核心方法：获取当前局面下的最佳着法。
     * <p>
     * 搜索流程：
     * <ol>
     *   <li>获取候选着法</li>
     *   <li>检查强制着法（立即获胜、阻挡对手获胜、应对重大威胁）</li>
     *   <li>对候选着法评分排序</li>
     *   <li>迭代加深Minimax搜索：从深度1逐步增加到{@link #MAX_DEPTH}</li>
     *   <li>每次迭代保留最佳着法，超时后返回上一轮完成的结果</li>
     * </ol>
     *
     * @param game     五子棋游戏对象
     * @param aiPlayer AI方颜色
     * @return 最佳着法坐标 [x, y]，无候选时返回null
     */
    public int[] getBestMove(GomokuGame game, int aiPlayer) {
        nodesSearched = 0;
        timedOut = false;
        searchStartMs = System.currentTimeMillis();

        int[][] board = copyBoard(game.getBoard());
        List<int[]> moves = getCandidateMoves(board);
        if (moves.isEmpty()) return null;

        int humanPlayer = getOpponent(aiPlayer);

        // 强制着法检测：优先处理紧急情况
        int[] forcedMove = findImmediateWin(moves, board, aiPlayer);
        if (forcedMove != null) return forcedMove;

        forcedMove = findImmediateWin(moves, board, humanPlayer);
        if (forcedMove != null) return forcedMove;

        forcedMove = findMajorThreat(moves, board, humanPlayer);
        if (forcedMove != null) return forcedMove;

        forcedMove = findMajorThreat(moves, board, aiPlayer);
        if (forcedMove != null) return forcedMove;

        // 迭代加深搜索
        List<int[]> orderedMoves = scoreAndSortMoves(moves, board, aiPlayer, moves.size());
        int[] bestMove = orderedMoves.get(0);

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (timedOut || checkTimeout()) break;

            int[] depthBest = null;
            double depthBestScore = -Double.MAX_VALUE;
            // 深层搜索减少顶层候选数量
            int topCount = Math.min(depth >= 5 ? 8 : 10, orderedMoves.size());

            for (int i = 0; i < topCount; i++) {
                if (timedOut) break;
                int[] move = orderedMoves.get(i);
                board[move[1]][move[0]] = aiPlayer;
                double eval = minimax(board, depth - 1, -Double.MAX_VALUE, Double.MAX_VALUE,
                        false, aiPlayer, new int[]{move[0], move[1], aiPlayer});
                board[move[1]][move[0]] = GomokuGame.EMPTY;
                if (eval > depthBestScore) {
                    depthBestScore = eval;
                    depthBest = new int[]{move[0], move[1]};
                }
            }

            // 仅在当前深度搜索完成（未超时）时更新最佳着法
            if (depthBest != null) {
                bestMove = depthBest;
            }
        }

        return bestMove;
    }

    /**
     * 查找立即获胜的着法。
     * <p>
     * 按着法评分排序后依次检查，找到第一个能形成五连的着法即返回。
     *
     * @param moves  候选着法列表
     * @param board  棋盘数组
     * @param player 检查方颜色
     * @return 获胜着法 [x, y]，无获胜着法返回null
     */
    private int[] findImmediateWin(List<int[]> moves, int[][] board, int player) {
        for (int[] move : scoreAndSortMoves(new ArrayList<>(moves), board, player, moves.size())) {
            board[move[1]][move[0]] = player;
            boolean wins = checkWinAt(move[0], move[1], player, board);
            board[move[1]][move[0]] = GomokuGame.EMPTY;
            if (wins) {
                return new int[]{move[0], move[1]};
            }
        }
        return null;
    }

    /**
     * 查找重大威胁着法（活四、双四、双活三）。
     * <p>
     * 若某着法能产生上述威胁，返回评分最高的威胁着法。
     *
     * @param moves  候选着法列表
     * @param board  棋盘数组
     * @param player 检查方颜色
     * @return 威胁着法 [x, y]，无威胁返回null
     */
    private int[] findMajorThreat(List<int[]> moves, int[][] board, int player) {
        int[] best = null;
        int bestScore = 0;
        for (int[] move : moves) {
            board[move[1]][move[0]] = player;
            Threat threat = evaluateMoveThreat(move[0], move[1], player, board);
            board[move[1]][move[0]] = GomokuGame.EMPTY;

            boolean major = threat.openFours > 0 || threat.fours >= 2 || threat.openThrees >= 2;
            if (major && threat.score > bestScore) {
                bestScore = threat.score;
                best = move;
            }
        }
        return best == null ? null : new int[]{best[0], best[1]};
    }

    /**
     * 计算中心偏置评分。
     * <p>
     * 距离棋盘中心越近评分越高，最大40分，每格距离减3分。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 中心偏置评分（0~40）
     */
    private int centerBias(int x, int y) {
        int center = GomokuGame.BOARD_SIZE / 2;
        int distance = Math.abs(x - center) + Math.abs(y - center);
        return Math.max(0, 40 - distance * 3);
    }

    /**
     * 判断坐标是否在棋盘范围内。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 在范围内返回true
     */
    private boolean isInside(int x, int y) {
        return x >= 0 && x < GomokuGame.BOARD_SIZE && y >= 0 && y < GomokuGame.BOARD_SIZE;
    }

    /**
     * 判断指定位置是否为空位。
     *
     * @param board 棋盘数组
     * @param x     横坐标
     * @param y     纵坐标
     * @return 在范围内且为空返回true
     */
    private boolean isEmpty(int[][] board, int x, int y) {
        return isInside(x, y) && board[y][x] == GomokuGame.EMPTY;
    }

    /**
     * 获取对方颜色。
     *
     * @param player 当前颜色
     * @return 对方颜色
     */
    private int getOpponent(int player) {
        return player == GomokuGame.BLACK ? GomokuGame.WHITE : GomokuGame.BLACK;
    }

    /**
     * 深拷贝棋盘数组。
     *
     * @param board 源棋盘
     * @return 副本棋盘
     */
    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[GomokuGame.BOARD_SIZE][GomokuGame.BOARD_SIZE];
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, GomokuGame.BOARD_SIZE);
        }
        return copy;
    }

    // 威胁评估结果，用于着法评分和局面评估。
    // 可以理解为"体检报告"：记录了这个位置的各种"健康指标"
    private static class Threat {
        /** 综合威胁评分（分数越高越有利） */
        int score;
        /** 五连数（已经连成5个了，就是赢了） */
        int wins;
        /** 四子数（含活四和冲四，差一步就赢了） */
        int fours;
        /** 活四数（两端都开放的四，对手无法防守，几乎必赢） */
        int openFours;
        /** 活三数（两端开放的三，下一步可以变成活四） */
        int openThrees;
    }
}

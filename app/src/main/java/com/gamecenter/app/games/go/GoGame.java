package com.gamecenter.app.games.go;

import java.util.ArrayList;
import java.util.List;

/**
 * 围棋游戏核心逻辑类。
 * <p>
 * 负责9×9棋盘上的全部围棋规则实现，包括：
 * <ul>
 *   <li>落子合法性判断（含自杀禁手）</li>
 *   <li>提子（吃子）逻辑</li>
 *   <li>领地计算与胜负判定</li>
 *   <li>基于蒙特卡洛模拟的AI决策</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘大小固定为9×9（{@link #BOARD_SIZE}），适合移动端快速对局</li>
 *   <li>AI采用"先验评分 + 蒙特卡洛模拟"的混合策略，在有限时间内选择最优着法</li>
 *   <li>贴目采用6.5目（{@link #KOMI}），遵循中国规则惯例</li>
 *   <li>落子判断使用"试下-检测"模式：在棋盘副本上模拟落子，验证合法性后再提交</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是围棋的"裁判"，比五子棋的裁判复杂得多，因为围棋规则更复杂：
 * - 不能自杀：下了一步后如果自己的棋子没气（被围死了），这步棋就不合法
 * - 提子（吃子）：如果下棋后对方的棋子没气了，就要把对方的棋子拿走
 * - 虚手（Pass）：可以跳过不走，双方都跳过则对局结束
 * - AI使用"蒙特卡洛模拟"：就像让AI在脑海中快速下很多盘随机棋局，
 *   看哪个位置赢的概率最高就选哪个——简单但有效的方法
 */
public class GoGame {

    /** 棋盘边长（9路棋盘） */
    public static final int BOARD_SIZE = 9;

    /** 空位标识 */
    public static final int EMPTY = 0;

    /** 黑子标识 */
    public static final int BLACK = 1;

    /** 白子标识 */
    public static final int WHITE = 2;

    /** 四个方向偏移量：右、下、左、上 */
    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /** AI单次决策的最大时间限制（毫秒） */
    private static final long AI_TIME_LIMIT_MS = 1800;

    /** AI根节点最大模拟次数上限 */
    private static final int MAX_ROOT_SIMULATIONS = 22000;

    /** 随机模拟（Playout）的最大步数限制，防止无限对弈 */
    private static final int PLAYOUT_STEP_LIMIT = BOARD_SIZE * BOARD_SIZE * 2;

    /** 贴目值，白方补偿6.5目
     *  为什么白方要加6.5目？因为黑方先手有优势（先下占便宜），
     *  为了公平，白方额外加6.5目作为补偿。0.5目是为了避免平局。
     */
    private static final double KOMI = 6.5;

    /** 棋盘数据，board[y][x]，0=空 1=黑 2=白 */
    private int[][] board;

    /** 当前执子方 */
    private int currentPlayer;

    /** 对局是否结束 */
    private boolean gameOver;

    /** 黑方提子数 */
    private int blackCaptures;

    /** 白方提子数 */
    private int whiteCaptures;

    /** 连续虚手计数，双方各虚手一次即终局
     *  就像两个人都说"我不下了"，那就说明对局结束了 */
    private int passes;

    /** 落子历史记录 */
    private List<MoveRecord> moveHistory;

    /** 总落子数 */
    private int moveCount;

    /** 最后一手坐标 [x, y] */
    private int[] lastMove;

    /** 上一手是否为虚手 */
    private boolean passedLast;

    /**
     * 构造函数，初始化空棋盘和默认状态。
     */
    public GoGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        blackCaptures = 0;
        whiteCaptures = 0;
        passes = 0;
        passedLast = false;
        moveHistory = new ArrayList<>();
        moveCount = 0;
        lastMove = null;
    }

    /**
     * 获取棋盘数据。
     *
     * @return 二维棋盘数组，board[y][x]
     */
    public int[][] getBoard() { return board; }

    /**
     * 获取当前执子方。
     *
     * @return {@link #BLACK} 或 {@link #WHITE}
     */
    public int getCurrentPlayer() { return currentPlayer; }

    /**
     * 查询对局是否结束。
     *
     * @return 对局结束返回true
     */
    public boolean isGameOver() { return gameOver; }

    /**
     * 设置对局结束状态（用于联机同步）。
     *
     * @param over 是否结束
     */
    public void setGameOver(boolean over) { this.gameOver = over; }

    /**
     * 获取黑方提子数。
     *
     * @return 黑方提子数
     */
    public int getBlackCaptures() { return blackCaptures; }

    /**
     * 获取白方提子数。
     *
     * @return 白方提子数
     */
    public int getWhiteCaptures() { return whiteCaptures; }

    /**
     * 获取最后一手坐标。
     *
     * @return 坐标数组 [x, y]，无最后一手时返回null
     */
    public int[] getLastMove() { return lastMove; }

    /**
     * 设置最后一手坐标（用于联机同步）。
     *
     * @param x 横坐标
     * @param y 纵坐标
     */
    public void setLastMove(int x, int y) { this.lastMove = new int[]{x, y}; }

    /**
     * 清除最后一手标记。
     */
    public void clearLastMove() { this.lastMove = null; }

    /**
     * 获取总落子数。
     *
     * @return 落子数
     */
    public int getMoveCount() { return moveCount; }

    public ScoreResult calculateScore() {
        return calculateScore(board, blackCaptures, whiteCaptures);
    }

    public int getWinner() {
        ScoreResult result = calculateScore();
        if (result.margin > 0) return BLACK;
        if (result.margin < 0) return WHITE;
        return EMPTY;
    }

    public String getResultText() {
        ScoreResult result = calculateScore();
        String winnerText = result.margin > 0 ? "黑方胜" : (result.margin < 0 ? "白方胜" : "平局");
        return String.format(java.util.Locale.US,
                "%s  黑 %.1f / 白 %.1f  贴目 %.1f  差 %.1f",
                winnerText, result.blackScore, result.whiteScore, KOMI, Math.abs(result.margin));
    }

    /**
     * 判断指定位置是否为当前玩家的合法落子点。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 合法返回true
     */
    public boolean isValidMove(int x, int y) {
        return isLegalMoveOnBoard(x, y, currentPlayer, board);
    }

    /**
     * 在棋盘上执行落子操作。
     * <p>
     * 落子后自动提掉对方无气的棋子，并记录落子历史。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 落子记录对象，非法落子返回null
     */
    public MoveRecord makeMove(int x, int y) {
        if (!isValidMove(x, y)) return null;

        int captured = applyMoveOnBoard(board, x, y, currentPlayer);
        // 根据当前玩家累加提子数
        if (currentPlayer == BLACK) {
            blackCaptures += captured;
        } else {
            whiteCaptures += captured;
        }

        MoveRecord record = new MoveRecord(x, y, currentPlayer, captured);
        moveHistory.add(record);
        moveCount++;
        lastMove = new int[]{x, y};
        passes = 0;
        passedLast = false;
        return record;
    }

    /**
     * 执行虚手（Pass）操作。
     * <p>
     * 双方连续虚手时对局结束。
     */
    public void pass() {
        passedLast = true;
        passes++;
        lastMove = null;
        if (passes >= 2) {
            gameOver = true;
        }
    }

    /**
     * 在指定棋盘上判断落子是否合法。
     * <p>
     * 判断逻辑：位置在棋盘内且为空，在副本上试下后，
     * 若能提子或自身有气则合法（排除自杀着法）。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 落子方
     * @param b      棋盘数组
     * @return 合法返回true
     */
    private boolean isLegalMoveOnBoard(int x, int y, int player, int[][] b) {
        if (!isInside(x, y) || b[y][x] != EMPTY) return false;
        int[][] testBoard = copyBoard(b);
        int captured = applyMoveOnBoard(testBoard, x, y, player);
        // 能提子或有气则合法；两者皆无则为自杀禁手
        return captured > 0 || hasLiberty(x, y, testBoard);
    }

    /**
     * 在指定棋盘上执行落子并提子。
     *
     * @param b      棋盘数组（会被直接修改）
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 落子方
     * @return 被提掉的对方棋子数
     */
    private int applyMoveOnBoard(int[][] b, int x, int y, int player) {
        b[y][x] = player;
        return removeCaptured(player, b);
    }

    /**
     * 移除被提掉的对方无气棋组。
     * <p>
     * 遍历棋盘上所有对方棋子，收集每个棋组的信息，
     * 气数为0的棋组整组移除。
     *
     * @param player 刚落子的一方（用于确定对方颜色）
     * @param b      棋盘数组（会被直接修改）
     * @return 被提掉的棋子总数
     */
    private int removeCaptured(int player, int[][] b) {
        int total = 0;
        int opponent = opponentOf(player);
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (b[y][x] == opponent && !visited[y][x]) {
                    GroupInfo group = collectGroup(x, y, b, visited);
                    if (group.liberties == 0) {
                        total += group.stones.size();
                        for (int[] stone : group.stones) {
                            b[stone[1]][stone[0]] = EMPTY;
                        }
                    }
                }
            }
        }
        return total;
    }

    /**
     * 收集指定位置所属棋组的信息（棋子列表和气数）。
     *
     * @param x             起始横坐标
     * @param y             起始纵坐标
     * @param b             棋盘数组
     * @param visitedStones 已访问棋子标记数组
     * @return 棋组信息对象
     */
    private GroupInfo collectGroup(int x, int y, int[][] b, boolean[][] visitedStones) {
        GroupInfo info = new GroupInfo();
        if (!isInside(x, y) || b[y][x] == EMPTY) return info;
        boolean[][] visitedLiberties = new boolean[BOARD_SIZE][BOARD_SIZE];
        collectGroupDfs(x, y, b[y][x], b, visitedStones, visitedLiberties, info);
        return info;
    }

    /**
     * 深度优先搜索收集棋组信息。
     * <p>
     * 同时统计棋组包含的棋子数和气数（相邻空点数），
     * 使用两个visited数组分别标记已访问的棋子和气点，避免重复计数。
     *
     * @param x              当前横坐标
     * @param y              当前纵坐标
     * @param color          棋组颜色
     * @param b              棋盘数组
     * @param visitedStones  已访问棋子标记
     * @param visitedLiberties 已访问气点标记
     * @param info           收集结果对象
     */
    private void collectGroupDfs(int x, int y, int color, int[][] b,
                                 boolean[][] visitedStones, boolean[][] visitedLiberties,
                                 GroupInfo info) {
        if (!isInside(x, y)) return;
        if (b[y][x] == EMPTY) {
            // 发现一个气点
            if (!visitedLiberties[y][x]) {
                visitedLiberties[y][x] = true;
                info.liberties++;
            }
            return;
        }
        if (b[y][x] != color || visitedStones[y][x]) return;

        visitedStones[y][x] = true;
        info.stones.add(new int[]{x, y});
        for (int[] d : DIRS) {
            collectGroupDfs(x + d[0], y + d[1], color, b, visitedStones, visitedLiberties, info);
        }
    }

    /**
     * 计算指定位置所属棋组的气数。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @param b 棋盘数组
     * @return 气数
     */
    private int countLiberties(int x, int y, int[][] b) {
        return collectGroup(x, y, b, new boolean[BOARD_SIZE][BOARD_SIZE]).liberties;
    }

    /**
     * 判断指定位置棋组是否有气。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @param b 棋盘数组
     * @return 有气返回true
     */
    private boolean hasLiberty(int x, int y, int[][] b) {
        return countLiberties(x, y, b) > 0;
    }

    /**
     * 切换当前执子方。
     */
    public void switchPlayer() {
        currentPlayer = opponentOf(currentPlayer);
    }

    /**
     * 重置游戏到初始状态。
     */
    public void reset() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        blackCaptures = 0;
        whiteCaptures = 0;
        passes = 0;
        passedLast = false;
        moveHistory.clear();
        moveCount = 0;
        lastMove = null;
    }

    /**
     * 获取当前玩家的所有合法落子点。
     *
     * @return 合法落子坐标列表，每个元素为 [x, y]
     */
    public List<int[]> getLegalMoves() {
        return getLegalMovesOnBoard(board, currentPlayer);
    }

    /**
     * 获取指定棋盘上某方的所有合法落子点。
     *
     * @param b      棋盘数组
     * @param player 执子方
     * @return 合法落子坐标列表
     */
    private List<int[]> getLegalMovesOnBoard(int[][] b, int player) {
        List<int[]> moves = new ArrayList<>();
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (isLegalMoveOnBoard(x, y, player, b)) {
                    moves.add(new int[]{x, y});
                }
            }
        }
        return moves;
    }

    /**
     * 随机选择一个合法落子点。
     *
     * @return 随机合法坐标 [x, y]，无合法着法时返回null
     */
    public int[] getRandomMove() {
        List<int[]> moves = getLegalMoves();
        if (moves.isEmpty()) return null;
        return moves.get((int) (Math.random() * moves.size()));
    }

    /**
     * AI核心方法：获取当前局面下的最佳落子。
     * <p>
     * 采用"先验评分筛选 + 蒙特卡洛模拟"的混合策略：
     * <ol>
     *   <li>对所有合法着法进行先验评分（{@link #scoreRootMove}），按分数排序</li>
     *   <li>取前28个候选着法进入模拟阶段</li>
     *   <li>在时间限制内反复执行蒙特卡洛模拟（{@link #simulateRootMove}）</li>
     *   <li>使用UCB1式选择公式（{@link #selectRootCandidate}）平衡探索与利用</li>
     *   <li>最终根据平均胜率加先验偏置选择最优着法</li>
     * </ol>
     *
     * 【初学者提示】蒙特卡洛模拟是什么？
     * 想象你和朋友下棋，到了一个关键点不知道下哪里好。
     * 蒙特卡洛模拟的做法是：对每个候选位置，在脑海中快速下很多盘随机棋局，
     * 看哪个位置赢的次数最多就选哪个。
     * "UCB1选择公式"就像一个聪明的策略：不能只试看起来好的位置（利用），
     * 也要试试还没怎么试过的位置（探索），说不定有惊喜。
     *
     * @return 最佳落子坐标 [x, y]，无合法着法时返回null
     */
    public int[] getBestMove() {
        List<int[]> legalMoves = getLegalMoves();
        if (legalMoves.isEmpty()) return null;
        // 只有一个合法着法时直接返回
        if (legalMoves.size() == 1) return legalMoves.get(0);

        // 先验评分并排序
        List<ScoredMove> candidates = new ArrayList<>();
        for (int[] move : legalMoves) {
            candidates.add(new ScoredMove(move, scoreRootMove(move)));
        }
        candidates.sort((a, b) -> Double.compare(b.prior, a.prior));

        // 取前28个候选着法进入模拟
        int candidateCount = Math.min(28, candidates.size());
        long deadline = System.currentTimeMillis() + AI_TIME_LIMIT_MS;
        double[] values = new double[candidateCount];
        int[] visits = new int[candidateCount];
        int simulations = 0;

        // 初始阶段：对每个候选至少模拟一次
        for (int i = 0; i < candidateCount && System.currentTimeMillis() < deadline; i++) {
            values[i] += simulateRootMove(candidates.get(i).move, deadline);
            visits[i]++;
            simulations++;
        }

        // 迭代模拟阶段：使用UCB1选择公式分配模拟次数
        while (System.currentTimeMillis() < deadline && simulations < MAX_ROOT_SIMULATIONS) {
            int idx = selectRootCandidate(candidates, values, visits, candidateCount, simulations);
            values[idx] += simulateRootMove(candidates.get(idx).move, deadline);
            visits[idx]++;
            simulations++;
        }

        // 根据平均胜率 + 先验偏置选择最终着法
        int bestIdx = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < candidateCount; i++) {
            double average = visits[i] == 0 ? 0.5 : values[i] / visits[i];
            // 综合平均胜率和先验分数，先验分数权重较小仅作微调
            double finalScore = average * 100.0 + candidates.get(i).prior * 0.03;
            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestIdx = i;
            }
        }
        return candidates.get(bestIdx).move;
    }

    /**
     * UCB1式候选着法选择公式。
     * <p>
     * 综合考虑平均胜率（利用）、探索奖励和先验偏置，
     * 平衡对已知好着法的深入搜索和对未充分探索着法的尝试。
     *
     * @param candidates    候选着法列表
     * @param values        各候选累计胜率值
     * @param visits        各候选模拟次数
     * @param candidateCount 候选数量
     * @param totalVisits   总模拟次数
     * @return 被选中的候选索引
     */
    private int selectRootCandidate(List<ScoredMove> candidates, double[] values, int[] visits,
                                    int candidateCount, int totalVisits) {
        int bestIdx = 0;
        double bestValue = -Double.MAX_VALUE;
        double logVisits = Math.log(totalVisits + 1.0);

        for (int i = 0; i < candidateCount; i++) {
            // 优先选择尚未模拟的候选
            if (visits[i] == 0) return i;
            double average = values[i] / visits[i];
            // 探索项：UCB1标准探索奖励
            double exploration = 0.45 * Math.sqrt(logVisits / visits[i]);
            // 先验偏置项：基于先验分数的微调
            double priorBias = Math.tanh(candidates.get(i).prior / 14.0) * 0.08;
            double value = average + exploration + priorBias;
            if (value > bestValue) {
                bestValue = value;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * 对指定根着法执行一次蒙特卡洛模拟。
     * <p>
     * 在棋盘副本上执行该着法后，进行随机模拟对弈（Playout），
     * 返回从根玩家视角的归一化胜率值（0~1）。
     *
     * @param move     待评估的着法 [x, y]
     * @param deadline 模拟截止时间
     * @return 归一化胜率值，0表示必败，1表示必胜
     */
    private double simulateRootMove(int[] move, long deadline) {
        int rootPlayer = currentPlayer;
        int[][] simBoard = copyBoard(board);
        int simBlackCaptures = blackCaptures;
        int simWhiteCaptures = whiteCaptures;

        int captured = applyMoveOnBoard(simBoard, move[0], move[1], rootPlayer);
        if (rootPlayer == BLACK) {
            simBlackCaptures += captured;
        } else {
            simWhiteCaptures += captured;
        }

        double margin = randomPlayout(simBoard, opponentOf(rootPlayer),
                simBlackCaptures, simWhiteCaptures, deadline);
        // 将盘面差值转换为从根玩家视角的胜率
        double perspective = rootPlayer == BLACK ? margin : -margin;
        return 0.5 + Math.tanh(perspective / 10.0) * 0.5;
    }

    /**
     * 随机模拟对弈（Playout）。
     * <p>
     * 从指定局面开始，双方使用加权随机策略交替落子，
     * 直到双方连续虚手或达到步数上限或超时。
     * 返回最终盘面黑方与白方的得分差值。
     *
     * @param simBoard         模拟用棋盘（会被修改）
     * @param player           当前执子方
     * @param simBlackCaptures 模拟中的黑方提子数
     * @param simWhiteCaptures 模拟中的白方提子数
     * @param deadline         截止时间
     * @return 黑方得分 - 白方得分（含贴目）
     */
    private double randomPlayout(int[][] simBoard, int player, int simBlackCaptures,
                                 int simWhiteCaptures, long deadline) {
        int current = player;
        int consecutivePasses = 0;

        for (int step = 0; step < PLAYOUT_STEP_LIMIT; step++) {
            if (System.currentTimeMillis() > deadline) break;

            // 优先选择非眼位的着法
            List<SimMove> moves = buildPlayoutMoves(simBoard, current, false);
            if (moves.isEmpty()) {
                // 无非眼着法时允许填眼
                moves = buildPlayoutMoves(simBoard, current, true);
            }

            if (moves.isEmpty()) {
                consecutivePasses++;
                if (consecutivePasses >= 2) break;
            } else {
                consecutivePasses = 0;
                SimMove move = chooseWeightedMove(moves);
                int captured = applyMoveOnBoard(simBoard, move.x, move.y, current);
                if (current == BLACK) {
                    simBlackCaptures += captured;
                } else {
                    simWhiteCaptures += captured;
                }
            }
            current = opponentOf(current);
        }

        return scoreBoardMargin(simBoard, simBlackCaptures, simWhiteCaptures);
    }

    /**
     * 构建模拟阶段的候选着法列表（带权重）。
     * <p>
     * 权重计算考虑：提子数、邻接对方棋子数、邻接己方棋子数、
     * 开局好点加成、边线惩罚、气数过少惩罚等。
     *
     * @param simBoard  棋盘数组
     * @param player    执子方
     * @param allowEyes 是否允许填自己的眼（通常为false）
     * @return 带权重的候选着法列表
     */
    private List<SimMove> buildPlayoutMoves(int[][] simBoard, int player, boolean allowEyes) {
        List<SimMove> moves = new ArrayList<>();
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                MoveAssessment assessment = assessMoveOnBoard(x, y, player, simBoard);
                if (!assessment.legal) continue;
                // 默认不填自己的眼（除非无其他着法）
                if (!allowEyes && assessment.ownEye && assessment.captured == 0) continue;

                int weight = 1 + assessment.captured * 12;
                weight += countAdjacent(x, y, opponentOf(player), simBoard) * 3;
                weight += countAdjacent(x, y, player, simBoard);
                if (isGoodOpeningPoint(x, y)) weight += 2;
                if (isEdge(x, y)) weight = Math.max(1, weight - 1);
                // 气数过少且未提子时大幅降低权重，避免下出危险着法
                if (assessment.liberties <= 1 && assessment.captured == 0) {
                    weight = Math.max(1, weight / 4);
                }
                moves.add(new SimMove(x, y, weight));
            }
        }
        return moves;
    }

    /**
     * 按权重随机选择一个着法。
     *
     * @param moves 带权重的候选着法列表
     * @return 被选中的着法
     */
    private SimMove chooseWeightedMove(List<SimMove> moves) {
        int total = 0;
        for (SimMove move : moves) total += move.weight;
        int pick = (int) (Math.random() * total);
        for (SimMove move : moves) {
            pick -= move.weight;
            if (pick < 0) return move;
        }
        return moves.get(moves.size() - 1);
    }

    /**
     * 对根着法进行先验评分。
     * <p>
     * 评估维度包括：全局局面评估、提子奖励、邻接棋子加成、
     * 开局好点加成、角落惩罚、气数过少惩罚、多气奖励等。
     *
     * @param move 待评估着法 [x, y]
     * @return 先验分数，越高越优
     */
    private double scoreRootMove(int[] move) {
        int player = currentPlayer;
        int[][] testBoard = copyBoard(board);
        int captured = applyMoveOnBoard(testBoard, move[0], move[1], player);
        int testBlackCaptures = blackCaptures + (player == BLACK ? captured : 0);
        int testWhiteCaptures = whiteCaptures + (player == WHITE ? captured : 0);

        double score = evaluateBoardForPlayer(testBoard, testBlackCaptures, testWhiteCaptures, player);
        score += captured * 9.0;
        score += countAdjacent(move[0], move[1], opponentOf(player), board) * 2.5;
        score += countAdjacent(move[0], move[1], player, board) * 1.2;
        // 开局阶段对好点加大奖励
        if (isGoodOpeningPoint(move[0], move[1])) score += moveCount < 12 ? 5.0 : 1.5;
        // 角落通常价值低
        if (isTrueCorner(move[0], move[1])) score -= 3.0;

        int liberties = countLiberties(move[0], move[1], testBoard);
        // 仅一气且未提子，极危险
        if (liberties <= 1 && captured == 0) score -= 9.0;
        if (liberties >= 3) score += 1.5;
        return score;
    }

    /**
     * 评估指定位置着法的详细信息。
     * <p>
     * 在棋盘副本上试下，返回合法性、提子数、气数、是否为眼等评估结果。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 执子方
     * @param b      棋盘数组
     * @return 着法评估结果
     */
    private MoveAssessment assessMoveOnBoard(int x, int y, int player, int[][] b) {
        MoveAssessment assessment = new MoveAssessment();
        if (!isInside(x, y) || b[y][x] != EMPTY) return assessment;

        int[][] testBoard = copyBoard(b);
        int captured = applyMoveOnBoard(testBoard, x, y, player);
        int liberties = countLiberties(x, y, testBoard);
        // 自杀着法：未提子且无气
        if (captured == 0 && liberties == 0) return assessment;

        assessment.legal = true;
        assessment.captured = captured;
        assessment.liberties = liberties;
        assessment.ownEye = isOwnEye(x, y, player, b);
        return assessment;
    }

    /**
     * 从指定玩家视角评估全局局面。
     *
     * @param b             棋盘数组
     * @param blackCaptured 黑方提子数
     * @param whiteCaptured 白方提子数
     * @param player        评估视角方
     * @return 正值表示该方优势，负值表示劣势
     */
    private double evaluateBoardForPlayer(int[][] b, int blackCaptured, int whiteCaptured, int player) {
        double margin = scoreBoardMargin(b, blackCaptured, whiteCaptured);
        return player == BLACK ? margin : -margin;
    }

    /**
     * 计算盘面得分差值（黑方得分 - 白方得分）。
     * <p>
     * 得分 = 棋盘上的棋子数 + 提子数 + 领地数，白方额外加贴目。
     * 领地通过Flood Fill确定：空区域仅被一方棋子包围时归属该方。
     *
     * @param b             棋盘数组
     * @param blackCaptured 黑方提子数
     * @param whiteCaptured 白方提子数
     * @return 黑方得分 - 白方得分（含贴目）
     */
    private double scoreBoardMargin(int[][] b, int blackCaptured, int whiteCaptured) {
        ScoreResult result = calculateScore(b, blackCaptured, whiteCaptured);
        return result.margin;
    }

    private ScoreResult calculateScore(int[][] b, int blackCaptured, int whiteCaptured) {
        double blackScore = blackCaptured;
        double whiteScore = whiteCaptured + KOMI;
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (b[y][x] == BLACK) {
                    blackScore++;
                } else if (b[y][x] == WHITE) {
                    whiteScore++;
                } else if (!visited[y][x]) {
                    Territory territory = countTerritory(x, y, b, visited);
                    if (territory.owner == BLACK) {
                        blackScore += territory.size;
                    } else if (territory.owner == WHITE) {
                        whiteScore += territory.size;
                    }
                }
            }
        }
        return new ScoreResult(blackScore, whiteScore, blackScore - whiteScore);
    }

    /**
     * 计算指定空区域的领地归属。
     * <p>
     * 通过Flood Fill探索空区域，若仅被一方棋子包围则归属该方；
     * 被双方棋子同时包围则为中立区域。
     *
     * @param x       起始横坐标
     * @param y       起始纵坐标
     * @param b       棋盘数组
     * @param visited 已访问标记数组
     * @return 领地信息对象
     */
    private Territory countTerritory(int x, int y, int[][] b, boolean[][] visited) {
        Territory territory = new Territory();
        exploreRegion(x, y, b, visited, territory);
        // 仅接触一方棋子时才判定归属
        if (territory.touchesBlack && !territory.touchesWhite) {
            territory.owner = BLACK;
        } else if (!territory.touchesBlack && territory.touchesWhite) {
            territory.owner = WHITE;
        }
        return territory;
    }

    /**
     * 递归探索空区域，统计区域大小和接触的棋子颜色。
     *
     * @param x         当前横坐标
     * @param y         当前纵坐标
     * @param b         棋盘数组
     * @param visited   已访问标记数组
     * @param territory 领地信息收集对象
     */
    private void exploreRegion(int x, int y, int[][] b, boolean[][] visited, Territory territory) {
        if (!isInside(x, y)) return;
        if (b[y][x] == BLACK) {
            territory.touchesBlack = true;
            return;
        }
        if (b[y][x] == WHITE) {
            territory.touchesWhite = true;
            return;
        }
        if (visited[y][x]) return;

        visited[y][x] = true;
        territory.size++;
        for (int[] d : DIRS) {
            exploreRegion(x + d[0], y + d[1], b, visited, territory);
        }
    }

    /**
     * 判断指定位置是否为指定玩家的眼。
     * <p>
     * 四个相邻位置均为己方棋子时视为眼。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 玩家颜色
     * @param b      棋盘数组
     * @return 是眼返回true
     */
    private boolean isOwnEye(int x, int y, int player, int[][] b) {
        for (int[] d : DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isInside(nx, ny) && b[ny][nx] != player) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算指定位置相邻的某方棋子数。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 目标玩家颜色
     * @param b      棋盘数组
     * @return 相邻棋子数
     */
    private int countAdjacent(int x, int y, int player, int[][] b) {
        int count = 0;
        for (int[] d : DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isInside(nx, ny) && b[ny][nx] == player) count++;
        }
        return count;
    }

    /**
     * 判断是否为开局好点（天元和四三三位置）。
     * <p>
     * 在9路棋盘上，天元(4,4)和星位(2,2)(2,6)(6,2)(6,6)是开局常见着点。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 是好点返回true
     */
    private boolean isGoodOpeningPoint(int x, int y) {
        int center = BOARD_SIZE / 2;
        if (x == center && y == center) return true;
        int low = 2;
        int high = BOARD_SIZE - 3;
        return (x == low || x == high) && (y == low || y == high);
    }

    /**
     * 判断是否为棋盘真角（四个角点）。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 是角点返回true
     */
    private boolean isTrueCorner(int x, int y) {
        return (x == 0 || x == BOARD_SIZE - 1) && (y == 0 || y == BOARD_SIZE - 1);
    }

    /**
     * 判断是否在棋盘边线上。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 在边线上返回true
     */
    private boolean isEdge(int x, int y) {
        return x == 0 || y == 0 || x == BOARD_SIZE - 1 || y == BOARD_SIZE - 1;
    }

    /**
     * 判断坐标是否在棋盘范围内。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 在范围内返回true
     */
    private boolean isInside(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    /**
     * 获取对方的颜色标识。
     *
     * @param player 当前玩家颜色
     * @return 对方颜色
     */
    private int opponentOf(int player) {
        return player == BLACK ? WHITE : BLACK;
    }

    /**
     * 深拷贝棋盘数组。
     *
     * @param src 源棋盘
     * @return 副本棋盘
     */
    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(src[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    /**
     * 落子记录，用于历史回放和悔棋。
     */
    public static class MoveRecord {
        /** 落子横坐标 */
        public int x, y;
        /** 落子方颜色 */
        public int player;
        /** 本手提子数 */
        public int captured;

        public MoveRecord(int x, int y, int player, int captured) {
            this.x = x;
            this.y = y;
            this.player = player;
            this.captured = captured;
        }
    }

    public static class ScoreResult {
        public final double blackScore;
        public final double whiteScore;
        public final double margin;

        public ScoreResult(double blackScore, double whiteScore, double margin) {
            this.blackScore = blackScore;
            this.whiteScore = whiteScore;
            this.margin = margin;
        }
    }

    /**
     * 棋组信息，包含棋子列表和气数。
     */
    private static class GroupInfo {
        /** 棋组中的棋子坐标列表 */
        final List<int[]> stones = new ArrayList<>();
        /** 棋组的气数 */
        int liberties;
    }

    /**
     * 领地信息，包含区域大小和归属。
     */
    private static class Territory {
        /** 区域大小（空点数） */
        int size;
        /** 归属方（BLACK/WHITE/0=中立） */
        int owner;
        /** 是否与黑子相邻 */
        boolean touchesBlack;
        /** 是否与白子相邻 */
        boolean touchesWhite;
    }

    /**
     * 带先验分数的候选着法，用于AI根节点搜索。
     */
    private static class ScoredMove {
        /** 着法坐标 */
        final int[] move;
        /** 先验评分 */
        final double prior;

        ScoredMove(int[] move, double prior) {
            this.move = move;
            this.prior = prior;
        }
    }

    /**
     * 模拟阶段的带权重着法，用于加权随机选择。
     */
    private static class SimMove {
        /** 横坐标 */
        final int x;
        /** 纵坐标 */
        final int y;
        /** 选择权重 */
        final int weight;

        SimMove(int x, int y, int weight) {
            this.x = x;
            this.y = y;
            this.weight = weight;
        }
    }

    /**
     * 着法评估结果，用于模拟阶段的着法筛选。
     */
    private static class MoveAssessment {
        /** 是否合法 */
        boolean legal;
        /** 提子数 */
        int captured;
        /** 落子后气数 */
        int liberties;
        /** 是否为己方眼位 */
        boolean ownEye;
    }
}

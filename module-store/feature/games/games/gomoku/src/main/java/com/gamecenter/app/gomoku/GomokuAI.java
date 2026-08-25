// 同步声明：此文件与 app/src/main/java/com/gamecenter/app/games/gomoku/GomokuAI.java 保持同步，修改时请同步修改对方文件
package com.gamecenter.app.gomoku;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.gamecenter.app.core.common.GameAI;

/**
 * 五子棋AI引擎，基于Minimax搜索 + Alpha-Beta剪枝。
 * <p>
 * 核心算法采用迭代加深搜索（Iterative Deepening），在时间限制内
 * 逐步增加搜索深度，返回当前已完成的最佳着法。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>4个难度等级对应独立AI配置（低 / 中 / 高 / 大师）</li>
 *   <li>使用威胁评估（{@link Threat}）进行着法排序和局面评估</li>
 *   <li>防御评分按难度乘以1.12~1.40的权重偏置，难度越低越重防守</li>
 *   <li>候选着法仅考虑已有棋子周围2格范围内的空位，大幅减少搜索空间</li>
 * <li>强制着法检测：优先处理立即获胜、阻挡对手获胜、应对重大威胁</li>
 * <li>低难度有概率随机选择前3评分着法，新手更容易获胜</li>
 * <li>大师难度启用VCF（连续冲四算杀）浅层搜索，提升攻击力</li>
 * <li>评估函数识别"跳活三"和"跳冲四"等间隔棋型</li>
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
public class GomokuAI implements GameAI {

    private static final DifficultyProfile[] DIFFICULTY_PROFILES = {
            new DifficultyProfile("gomoku_low.ai", 200, 2),
            new DifficultyProfile("gomoku_medium.ai", 800, 4),
            new DifficultyProfile("gomoku_high.ai", 2500, 6),
            new DifficultyProfile("gomoku_master.ai", 5000, 8)
    };

    // 超时检查间隔：每搜索256个节点检查一次是否超时
    // 不是每步都检查，因为检查时间本身也有开销，就像不用每走一步都看表
    private static final int TIME_CHECK_INTERVAL = 256;

    // 获胜评分基准值：分数高到这个程度就意味着赢了
    private static final int WIN_SCORE = 10_000_000;

    // VCF算杀的最大搜索深度（6层=3个回合）
    private static final int VCF_MAX_DEPTH = 6;

    // VCF搜索占用最大搜索时间的比例（25%，降低以让主搜索有更多预算）
    private static final double VCF_TIME_RATIO = 0.25;

    // 各难度随机走子：从评分前N名中随机选
    private static final int RANDOM_TOP_N_LOW = 5;
    private static final int RANDOM_TOP_N_MEDIUM = 3;
    private static final int RANDOM_TOP_N_HIGH = 2;

    /** 当前难度对应的最大搜索时间 */
    private final int maxTimeMs;

    /** 当前难度对应的最大搜索深度 */
    private final int maxDepth;

    /** 当前难度等级（1~4） */
    private final int level;

    /** 防守偏置：人类方评分乘以该权重，难度越低越重防守 */
    private final double defenseBias;

    /** 随机数生成器，用于低难度随机走子和开局首手偏移 */
    private final Random random;

    /** VCF算杀开关（feature flag）：仅大师难度启用 */
    private final boolean vcfEnabled;

    /** 禁手规则开关（由当前对局决定，仅约束黑方） */
    private boolean applyForbiddenRule;
    private volatile boolean cancelled = false;
    private volatile boolean thinking = false;

    /** 当前对局引用，用于禁手判定 */
    private GomokuGame gameRef;

    /** 搜索开始时间戳 */
    private long searchStartMs;

    /** 是否已超时 */
    private boolean timedOut;

    /** 已搜索的节点数 */
    private int nodesSearched;

    /**
     * 构造AI引擎。
     *
     * @param level 难度等级（1~4）
     */
    public GomokuAI(int level) {
        int idx = Math.max(0, Math.min(level - 1, DIFFICULTY_PROFILES.length - 1));
        DifficultyProfile profile = DIFFICULTY_PROFILES[idx];
        this.maxTimeMs = profile.maxTimeMs;
        this.maxDepth = profile.maxDepth;
        this.level = idx + 1;
        // 防守偏置按难度递减：低难度重防守（保守易破），高难度重进攻（激进）
        this.defenseBias = defenseBiasForLevel(this.level);
        this.random = new Random();
        // VCF算杀仅大师难度启用
        this.vcfEnabled = (this.level == 4);
    }

    /**
     * 根据难度等级返回防守偏置。
     * <p>
     * 难度1（低）：1.20，略偏防守但易出错
     * 难度2（中）：1.15
     * 难度3（高）：1.10
     * 难度4（大师）：1.05，最激进，重进攻
     *
     * @param level 难度等级（1~4）
     * @return 防守偏置
     */
    private static double defenseBiasForLevel(int level) {
        switch (level) {
            case 1: return 1.20;
            case 2: return 1.15;
            case 3: return 1.10;
            case 4: return 1.05;
            default: return 1.10;
        }
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
     * <p>
     * 此外通过 {@link #evaluateGapPatterns} 额外扫描6格窗口，
     * 识别"跳活三"和"跳冲四"等间隔棋型（连续窗口扫描无法识别）。
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

        // 额外扫描6格窗口识别间隔棋型（跳活三、跳冲四）
        // 连续5格窗口扫描无法识别中间带空位的棋型，需用6格窗口补充
        evaluateGapPatterns(threat, x, y, player, board);

        // 组合威胁加成：活四、双四、双活三
        if (threat.openFours > 0) threat.score += 1_500_000;
        if (threat.fours >= 2) threat.score += 1_200_000;
        if (threat.openThrees >= 2) threat.score += 120_000;
        return threat;
    }

    /**
     * 扫描6格窗口识别间隔棋型（跳活三、跳冲四）。
     * <p>
     * 连续5格窗口扫描（{@link #addWindowScore}）只能识别紧密相连的棋型，
     * 无法识别中间带空位的"跳"棋型。本方法对每个方向扫描以 (x,y) 为基准的
     * 6格窗口（offset 从 -5 到 0），统计窗口内棋子数、空位数和首末棋子间
     * 是否存在空位（gap），据此识别：
     * <ul>
     *   <li>跳活三：6格窗口内3子且首末棋子间有空位，两端开放，评分 25,000
     *       （介于活三35,000和眠三4,000之间，因为是潜在活三）</li>
     *   <li>跳冲四：6格窗口内4子且首末棋子间有空位，一端被堵，评分 120,000
     *       （介于冲四180,000和活三35,000之间）</li>
     * </ul>
     * 注意：本方法只识别有 gap 的棋型，连续棋型由 {@link #addWindowScore} 处理，
     * 不会重复识别。
     *
     * @param threat 威胁对象（累加评分）
     * @param x      基准横坐标
     * @param y      基准纵坐标
     * @param player 评估方
     * @param board  棋盘数组
     */
    private void evaluateGapPatterns(Threat threat, int x, int y, int player, int[][] board) {
        for (int[] dir : GomokuGame.DIRECTIONS) {
            // 遍历以(x,y)为基准的6个可能的6格窗口
            for (int offset = -5; offset <= 0; offset++) {
                int startX = x + dir[0] * offset;
                int startY = y + dir[1] * offset;

                int stones = 0;
                int firstStonePos = -1;
                int lastStonePos = -1;
                boolean blocked = false;

                // 扫描6格窗口，记录棋子数及首末棋子位置
                for (int i = 0; i < 6; i++) {
                    int cx = startX + dir[0] * i;
                    int cy = startY + dir[1] * i;
                    if (!isInside(cx, cy)) {
                        blocked = true;
                        break;
                    }
                    int cell = board[cy][cx];
                    if (cell == player) {
                        stones++;
                        if (firstStonePos == -1) firstStonePos = i;
                        lastStonePos = i;
                    } else if (cell != GomokuGame.EMPTY) {
                        // 窗口内含对方棋子，无效
                        blocked = true;
                        break;
                    }
                }

                if (blocked) continue;
                // 首末棋子间至少要有一个空位才算"跳"棋型
                if (lastStonePos <= firstStonePos + 1) continue;

                // 检查首末棋子之间是否存在空位（gap）
                boolean hasGap = false;
                for (int i = firstStonePos + 1; i < lastStonePos; i++) {
                    int cx = startX + dir[0] * i;
                    int cy = startY + dir[1] * i;
                    if (board[cy][cx] == GomokuGame.EMPTY) {
                        hasGap = true;
                        break;
                    }
                }
                if (!hasGap) continue; // 连续棋型，交给 addWindowScore 处理

                // 计算窗口两端的开放性
                int beforeX = startX - dir[0];
                int beforeY = startY - dir[1];
                int afterX = startX + dir[0] * 6;
                int afterY = startY + dir[1] * 6;
                int openEnds = (isEmpty(board, beforeX, beforeY) ? 1 : 0)
                        + (isEmpty(board, afterX, afterY) ? 1 : 0);

                // 跳活三：3子，两端开放
                if (stones == 3 && openEnds == 2) {
                    threat.score += 25_000;
                }
                // 跳冲四：4子，一端被堵（openEnds == 1）
                else if (stones == 4 && openEnds == 1) {
                    threat.score += 120_000;
                }
            }
        }
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
     * 人类方评分乘以 {@link #defenseBias} 的偏置，使评估更重视防守。
     * 难度越低偏置越大（越保守），难度越高偏置越小（越激进）。
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
                    score -= (int) (evaluatePosition(x, y, humanPlayer, board) * defenseBias);
                }
            }
        }
        return score;
    }

    /**
     * 获取候选着法列表。
     * <p>
     * 仅考虑已有棋子周围2格范围内的空位，大幅减少搜索空间。
     * 使用Set去重。若棋盘无棋子，从天元及其相邻6个位置中随机选一个，
     * 增加开局多样性。
     *
     * @param board 棋盘数组
     * @return 候选着法坐标列表
     */
    private List<int[]> getCandidateMoves(int[][] board) {
        List<int[]> moves = new ArrayList<>();
        // 使用二维布尔数组去重，避免 HashSet 自动装箱的潜在 NPE 问题
        boolean[][] seen = new boolean[GomokuGame.BOARD_SIZE][GomokuGame.BOARD_SIZE];
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
                        if (isInside(nx, ny) && board[ny][nx] == GomokuGame.EMPTY && !seen[ny][nx]) {
                            seen[ny][nx] = true;
                            moves.add(new int[]{nx, ny});
                        }
                    }
                }
            }
        }

        // 棋盘为空时从天元及周围多个候选位置加权随机选一个，显著增加开局多样性
        // 13个候选位置：天元(权重4) + 一线4个(权重3) + 二线8个(权重2)
        if (!hasPiece) {
            int center = GomokuGame.BOARD_SIZE / 2;
            int[][] openCandidates = {
                    {center, center},           // 天元 (7,7)
                    {center - 1, center},       // 一线上 (6,7)
                    {center + 1, center},       // 一线下 (8,7)
                    {center, center - 1},       // 一线左 (7,6)
                    {center, center + 1},       // 一线右 (7,8)
                    {center - 1, center - 1},   // 二线左上 (6,6)
                    {center - 1, center + 1},   // 二线右上 (6,8)
                    {center + 1, center - 1},   // 二线左下 (8,6)
                    {center + 1, center + 1},   // 二线右下 (8,8)
                    {center - 2, center},       // 三线上 (5,7)
                    {center + 2, center},       // 三线下 (9,7)
                    {center, center - 2},       // 三线左 (7,5)
                    {center, center + 2}        // 三线右 (7,9)
            };
            // 加权随机：天元权重4，一线4个各权重3，二线4个各权重2，三线4个各权重1
            int[] weights = {4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1};
            int totalWeight = 0;
            for (int w : weights) totalWeight += w;
            int r = random.nextInt(totalWeight);
            int cumulative = 0;
            int pickIndex = 0;
            for (int i = 0; i < weights.length; i++) {
                cumulative += weights[i];
                if (r < cumulative) {
                    pickIndex = i;
                    break;
                }
            }
            int[] pick = openCandidates[pickIndex];
            moves.add(new int[]{pick[0], pick[1]});
        }
        return moves;
    }

    /**
     * 获取合法候选着法（含禁手过滤）。
     * <p>
     * 当启用禁手规则且当前着子方为黑方时，过滤掉会导致禁手（三三/四四/长连）的着法。
     * 若过滤后无候选着法，则返回原始候选（极端边界情况，避免无着可走）。
     *
     * @param board  棋盘数组
     * @param player 当前执子方
     * @return 候选着法坐标列表
     */
    private List<int[]> getLegalCandidateMoves(int[][] board, int player) {
        List<int[]> base = getCandidateMoves(board);
        if (!applyForbiddenRule || player != GomokuGame.BLACK || gameRef == null) return base;
        List<int[]> out = new ArrayList<>();
        for (int[] m : base) {
            if (gameRef.getForbiddenType(m[0], m[1], player, board) == GomokuGame.ForbiddenType.NONE) out.add(m);
        }
        return out.isEmpty() ? base : out;
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
     * @param lastMoveX    上一手 x（-1 表示无上一手，用于根节点）
     * @param lastMoveY    上一手 y
     * @param lastMovePlayer 上一手执子方颜色
     * @return 评估分数
     */
    private double minimax(int[][] board, int depth, double alpha, double beta,
                           boolean isMaximizing, int aiPlayer,
                           int lastMoveX, int lastMoveY, int lastMovePlayer) {
        nodesSearched++;
        if (cancelled) return evaluate(board, aiPlayer);
        // 定期检查超时
        if ((nodesSearched & (TIME_CHECK_INTERVAL - 1)) == 0 && checkTimeout()) {
            return evaluate(board, aiPlayer);
        }
        // 检查上一手是否获胜（lastMoveX/Y=-1 表示无上一手，用于根节点）
        if (lastMoveX >= 0 && checkWinAt(lastMoveX, lastMoveY, lastMovePlayer, board)) {
            // 胜利评分乘以深度，偏好更快获胜
            return (lastMovePlayer == aiPlayer ? 1 : -1) * WIN_SCORE * (depth + 1);
        }
        // 到达搜索深度上限，返回静态评估
        if (depth == 0) return evaluate(board, aiPlayer);

        int player = isMaximizing ? aiPlayer : getOpponent(aiPlayer);
        // 深层搜索减少候选数量以加速
        int limit = depth >= 4 ? 10 : 12;
        List<int[]> topMoves = scoreAndSortMoves(getLegalCandidateMoves(board, player), board, player, limit);
        if (topMoves.isEmpty()) return evaluate(board, aiPlayer);

        if (isMaximizing) {
            double maxEval = -Double.MAX_VALUE;
            for (int[] move : topMoves) {
                if (timedOut) break;
                board[move[1]][move[0]] = player;
                // 性能优化：用 3 个 int 字段传递 lastMove，避免每层递归 new int[3] 分配
                double eval = minimax(board, depth - 1, alpha, beta, false, aiPlayer,
                        move[0], move[1], player);
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
                    move[0], move[1], player);
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
     * 同时计算进攻评分和防守评分，防守评分乘以 {@link #defenseBias} 的偏置。
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

        int score = attack.score + (int) (defense.score * defenseBias) + centerBias(x, y);
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
     *   <li>大师难度启用VCF算杀：尝试连续冲四必胜路径，找到则直接返回首手</li>
     *   <li>对候选着法评分排序</li>
     *   <li>迭代加深Minimax搜索：从深度1逐步增加到当前难度配置的上限</li>
     *   <li>每次迭代保留最佳着法，超时后返回上一轮完成的结果</li>
     *   <li>低难度按概率从评分前3中随机选一个，增加新手友好度</li>
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
        this.gameRef = game;
        this.applyForbiddenRule = game.isForbiddenMovesEnabled();
        thinking = true;
        cancelled = false;
        int[] bestMove = null;
        try {
        List<int[]> moves = getLegalCandidateMoves(board, aiPlayer);
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

        // 大师难度VCF算杀：在迭代加深前尝试连续冲四必胜路径
        // VCF占用最大搜索时间的40%，超时则放弃转正常搜索
        if (vcfEnabled) {
            int[] vcfMove = findVcfMove(board, aiPlayer);
            if (vcfMove != null) return vcfMove;
            // VCF超时后重置超时标志，让后续迭代加深搜索有完整预算
            timedOut = false;
        }

        // 迭代加深搜索
        List<int[]> orderedMoves = scoreAndSortMoves(moves, board, aiPlayer, moves.size());
        bestMove = orderedMoves.get(0);

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut || checkTimeout()) break;

            int[] depthBest = null;
            double depthBestScore = -Double.MAX_VALUE;
            // 深层搜索减少顶层候选数量
            int topCount = Math.min(depth >= 5 ? 8 : 10, orderedMoves.size());

            for (int i = 0; i < topCount; i++) {
                if (timedOut) break;
                int[] move = orderedMoves.get(i);
                board[move[1]][move[0]] = aiPlayer;
                // 性能优化：lastMoveX/Y/Player 三个 int 字段代替 new int[3]，每层递归省 1 次分配
                double eval = minimax(board, depth - 1, -Double.MAX_VALUE, Double.MAX_VALUE,
                        false, aiPlayer, move[0], move[1], aiPlayer);
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

        // 低难度随机走子：按概率从评分前3中随机选一个，新手更容易获胜
        // 难度3/4始终选最优，不随机
        bestMove = maybePickRandomFromTop(bestMove, orderedMoves);

        } finally {
            thinking = false;
        }

        return bestMove;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isThinking() {
        return thinking;
    }

    /**
     * 低难度随机走子：按难度概率从评分前N名中随机选一个。
     * <p>
     * 难度1（低）：50%概率选评分前5的随机走子
     * 难度2（中）：25%概率选评分前3的随机走子
     * 难度3（高）：10%概率选评分前2的随机走子
     * 难度4（大师）：不随机，始终选最优
     * <p>
     * 这样低难度AI会频繁走次优着法，让新手有获胜机会；
     * 中等难度偶尔出错，仍有挑战性；大师档保持完美。
     *
     * @param bestMove      当前最优着法
     * @param orderedMoves  按评分降序排列的候选着法列表
     * @return 最终着法
     */
    private int[] maybePickRandomFromTop(int[] bestMove, List<int[]> orderedMoves) {
        double probability;
        int topN;
        if (level == 1) {
            probability = 0.50;
            topN = RANDOM_TOP_N_LOW;
        } else if (level == 2) {
            probability = 0.25;
            topN = RANDOM_TOP_N_MEDIUM;
        } else if (level == 3) {
            probability = 0.10;
            topN = RANDOM_TOP_N_HIGH;
        } else {
            // 难度4（大师）不随机
            return bestMove;
        }

        if (orderedMoves.size() <= 1) return bestMove;
        if (random.nextDouble() >= probability) return bestMove;

        int limit = Math.min(topN, orderedMoves.size());
        return orderedMoves.get(random.nextInt(limit));
    }

    /**
     * VCF（Victory by Continuous Four）算杀入口。
     * <p>
     * 仅大师难度启用。在迭代加深搜索前尝试寻找连续冲四必胜路径。
     * VCF搜索总时间不超过最大搜索时间的 {@link #VCF_TIME_RATIO}（40%）。
     *
     * @param board    棋盘数组
     * @param aiPlayer AI方颜色
     * @return VCF必胜路径首手 [x, y]，未找到返回null
     */
    private int[] findVcfMove(int[][] board, int aiPlayer) {
        long vcfDeadline = searchStartMs + (long) (maxTimeMs * VCF_TIME_RATIO);
        int[] bestMove = new int[]{-1, -1};
        boolean found = vcfSearch(board, aiPlayer, 0, VCF_MAX_DEPTH, vcfDeadline, bestMove);
        if (found && bestMove[0] >= 0) {
            return bestMove;
        }
        return null;
    }

    /**
     * VCF递归搜索：仅考虑能形成"冲四"的着法，寻找连续进攻必胜路径。
     * <p>
     * 每层只生成冲四着法（{@link Threat#fours} > 0 且 {@link Threat#openFours} == 0），
     * 若形成五连则胜利。对手防守时取评分前5的着法递归验证，
     * 只有对手所有防守都失败才算必胜。
     * <p>
     * 【初学者提示】VCF是什么？
     * 想象AI不断"将军"（冲四迫使对手防守），一路进攻直到五连获胜。
     * 因为每步都是冲四，对手只能被动防守，没有反击机会。
     * 如果存在这样一条必胜路径，AI就直接走第一步。
     *
     * @param board     棋盘数组（搜索中直接修改，回溯时还原）
     * @param attacker  进攻方（AI）
     * @param depth     当前搜索深度
     * @param maxDepth  最大搜索深度
     * @param deadline  VCF搜索截止时间戳
     * @param bestMove  输出参数，记录首手坐标 [x, y]
     * @return 找到必胜路径返回true
     */
    private boolean vcfSearch(int[][] board, int attacker, int depth, int maxDepth,
                              long deadline, int[] bestMove) {
        // 超时检查
        if (System.currentTimeMillis() > deadline) return false;
        if (depth >= maxDepth) return false;

        int defender = getOpponent(attacker);
        List<int[]> fourMoves = generateFourMoves(board, attacker);
        if (fourMoves.isEmpty()) return false;

        for (int[] move : fourMoves) {
            if (System.currentTimeMillis() > deadline) return false;

            board[move[1]][move[0]] = attacker;

            // 检查是否形成五连（直接获胜）
            if (checkWinAt(move[0], move[1], attacker, board)) {
                board[move[1]][move[0]] = GomokuGame.EMPTY;
                if (depth == 0) {
                    bestMove[0] = move[0];
                    bestMove[1] = move[1];
                }
                return true;
            }

            // 对手防守：取评分前5的防守着法，所有防守都失败才算必胜
            List<int[]> defenseMoves = scoreAndSortMoves(
                    getCandidateMoves(board), board, defender, 5);
            boolean allDefensesFail = true;

            if (defenseMoves.isEmpty()) {
                // 对手无着法（棋盘满），AI获胜
                allDefensesFail = true;
            } else {
                for (int[] defMove : defenseMoves) {
                    if (System.currentTimeMillis() > deadline) {
                        allDefensesFail = false;
                        break;
                    }
                    board[defMove[1]][defMove[0]] = defender;
                    boolean win = vcfSearch(board, attacker, depth + 1, maxDepth, deadline, bestMove);
                    board[defMove[1]][defMove[0]] = GomokuGame.EMPTY;
                    if (!win) {
                        // 对手有防守能避免失败，当前进攻着法不是必胜
                        allDefensesFail = false;
                        break;
                    }
                }
            }

            board[move[1]][move[0]] = GomokuGame.EMPTY;

            if (allDefensesFail) {
                if (depth == 0) {
                    bestMove[0] = move[0];
                    bestMove[1] = move[1];
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 生成冲四着法（含五连着法）。
     * <p>
     * 遍历候选着法，筛选下子后能形成五连或冲四（{@link Threat#fours} > 0
     * 且 {@link Threat#openFours} == 0）的着法，按着法评分降序排列。
     * 活四不纳入VCF（VCF只考虑冲四这种"迫使防守"的着法）。
     *
     * @param board  棋盘数组
     * @param player 进攻方
     * @return 冲四着法列表（按评分降序）
     */
    private List<int[]> generateFourMoves(int[][] board, int player) {
        List<int[]> moves = new ArrayList<>();
        for (int[] move : getCandidateMoves(board)) {
            board[move[1]][move[0]] = player;
            boolean wins = checkWinAt(move[0], move[1], player, board);
            Threat threat = evaluateMoveThreat(move[0], move[1], player, board);
            board[move[1]][move[0]] = GomokuGame.EMPTY;
            // 五连或冲四（不含活四）
            if (wins || (threat.fours > 0 && threat.openFours == 0)) {
                // 黑方禁手过滤：冲四着法若构成禁手则跳过
                if (applyForbiddenRule && player == GomokuGame.BLACK
                        && gameRef != null && gameRef.getForbiddenType(move[0], move[1], player, board) != GomokuGame.ForbiddenType.NONE) {
                    continue;
                }
                moves.add(move);
            }
        }
        // 按着法评分降序排列，优先尝试高分着法
        moves.sort((a, b) -> Integer.compare(
                scoreMoveForPlayer(b[0], b[1], player, board),
                scoreMoveForPlayer(a[0], a[1], player, board)));
        return moves;
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
    /**
     * 获取指定着法的教育性分析
     */
    public String getEducationalAnalysis(GomokuGame game, int x, int y, int player) {
        int[][] tempBoard = new int[15][15];
        for (int i = 0; i < 15; i++) {
            System.arraycopy(game.getBoard()[i], 0, tempBoard[i], 0, 15);
        }
        int opponent = (player == GomokuGame.BLACK) ? GomokuGame.WHITE : GomokuGame.BLACK;
        
        // 评估防守（如果不走这里，对手走这里的威胁）
        tempBoard[y][x] = opponent;
        Threat defense = evaluateMoveThreat(x, y, opponent, tempBoard);
        
        // 评估进攻（走这里的威胁）
        tempBoard[y][x] = player;
        Threat attack = evaluateMoveThreat(x, y, player, tempBoard);
        
        if (attack.wins > 0) return "形成连五，直接获胜！";
        if (defense.wins > 0) return "阻止对手连五，关键防守！";
        
        if (attack.openFours > 0) return "形成活四，必胜之局！";
        if (defense.openFours > 0 || defense.fours > 0) return "阻挡对手的四子威胁！";
        
        if (attack.fours >= 2) return "形成双冲四，双杀！";
        if (attack.fours > 0 && attack.openThrees > 0) return "形成冲四活三，强力攻击！";
        if (attack.openThrees >= 2) return "形成双活三，对手难以兼顾！";
        
        if (defense.openThrees >= 2) return "化解对手的双活三危机！";
        if (defense.openThrees > 0) return "破坏对手的活三攻势。";
        
        if (attack.openThrees > 0) return "形成活三，开始组织进攻。";
        if (attack.fours > 0) return "形成冲四，迫使对手防守。";
        
        return "占据要点，拓展我方发展空间。";
    }

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

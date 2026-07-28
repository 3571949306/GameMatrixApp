package com.gamecenter.app.gomoku;

import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋游戏核心逻辑类。
 * <p>
 * 负责15×15棋盘上的五子棋规则实现，包括：
 * <ul>
 *   <li>落子与合法性判断</li>
 *   <li>五连胜负检测</li>
 *   <li>悔棋（按"一手"为单位，同时撤销双方各一手）</li>
 *   <li>平局检测（棋盘下满）</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘大小固定为15×15（标准五子棋规格）</li>
 *   <li>方向数组仅包含4个方向（横、竖、正斜、反斜），因为胜负检测时正反双向都扫描</li>
 *   <li>悔棋通过 {@link #undoLastMoves} 实现，每次撤销"一手"即双方各一手</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是五子棋的"裁判"，负责记住棋盘上每个位置的状态（空/黑/白），
 * 判断落子是否合法，检测是否有人赢了，以及支持悔棋等功能。
 * 它不关心界面怎么画，只关心游戏规则——就像真正的裁判只看棋盘，不管棋盘长什么样。
 * 三层分工：Activity是"指挥官"，View是"画师"，Game是"裁判"。
 */
public class GomokuGame {

    /** 棋盘边长（标准15路） */
    public static final int BOARD_SIZE = 15;

    /** 空位标识 */
    public static final int EMPTY = 0;

    /** 黑子标识 */
    public static final int BLACK = 1;

    /** 白子标识 */
    public static final int WHITE = 2;

    // 四个方向：水平、垂直、正对角线、反对角线
    // 只需要4个方向就够了，因为检测五连时会同时向正反两个方向数
    // 比如：水平方向会同时向左数和向右数，加起来就是整条线上的连子数
    public static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    // 棋盘数据：board[y][x]，0=空 1=黑 2=白
    // 注意：第一个下标是行(y)，第二个下标是列(x)，这是二维数组的常见约定
    // 可以想象成：y是从上到下的行号，x是从左到右的列号
    private int[][] board;

    /** 当前执子方 */
    private int currentPlayer;

    /** 对局是否结束 */
    private boolean gameOver;

    /** 获胜方（null表示平局或未结束） */
    private Integer winner;

    // 落子历史记录：按顺序记录每一步棋，用于悔棋时回退
    private List<MoveRecord> moveHistory;

    /** 总落子数 */
    private int moveCount;

    /** 最后一手坐标 [x, y] */
    private int[] lastMove;

    /** 胜利五连线两端坐标 [x1, y1, x2, y2]，未结束时为null */
    private int[] winningLine;

    /** 禁手规则开关（仅约束黑方，标准 renju 规则）。默认开启。 */
    private boolean forbiddenMovesEnabled = true;

    /**
     * 构造函数，初始化空棋盘和默认状态。
     */
    public GomokuGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK; // 黑方先手
        gameOver = false;
        winner = null;
        moveHistory = new ArrayList<>();
        moveCount = 0;
        lastMove = null;
    }

    /**
     * 获取棋盘数据。
     *
     * @return 二维棋盘数组，board[y][x]
     */
    public int[][] getBoard() {
        return board;
    }

    /**
     * 获取当前执子方。
     *
     * @return {@link #BLACK} 或 {@link #WHITE}
     */
    public int getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * 设置当前执子方（用于联机同步）。
     *
     * @param player 执子方颜色
     */
    public void setCurrentPlayer(int player) {
        this.currentPlayer = player;
    }

    /**
     * 查询对局是否结束。
     *
     * @return 对局结束返回true
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * 获取获胜方。
     *
     * @return 获胜方颜色，平局或未结束时返回null
     */
    public Integer getWinner() {
        return winner;
    }

    /**
     * 设置对局结束和获胜方。
     *
     * @param winnerPlayer 获胜方颜色
     */
    public void setGameOver(int winnerPlayer) {
        this.gameOver = true;
        this.winner = winnerPlayer;
    }

    /**
     * 获取最后一手坐标。
     *
     * @return 坐标数组 [x, y]，无最后一手时返回null
     */
    public int[] getLastMove() {
        return lastMove;
    }

    /**
     * 获取胜利五连线两端坐标。
     *
     * @return 坐标数组 [x1, y1, x2, y2]，未结束或平局时返回null
     */
    public int[] getWinningLine() {
        return winningLine;
    }

    /**
     * 获取落子历史记录。
     *
     * @return 落子记录列表
     */
    public List<MoveRecord> getMoveHistory() {
        return moveHistory;
    }

    /**
     * 获取总落子数。
     *
     * @return 落子数
     */
    public int getMoveCount() {
        return moveCount;
    }

    /**
     * 判断指定位置是否为合法落子点。
     * <p>
     * 五子棋规则简单：仅需位置在棋盘内且为空位。
     * （不像围棋有"禁入点"，五子棋任何空位都可以下）
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 合法返回true
     */
    public boolean isValidMove(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE && board[y][x] == EMPTY;
    }

    /**
     * 在棋盘上执行落子操作。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 落子方颜色
     * @return 落子记录对象，非法落子返回null
     */
    public MoveRecord makeMove(int x, int y, int player) {
        if (!isValidMove(x, y)) return null;
        board[y][x] = player; // 在棋盘上放置棋子
        MoveRecord record = new MoveRecord(x, y, player);
        moveHistory.add(record); // 记录到历史，方便以后悔棋
        moveCount++;
        lastMove = new int[]{x, y};
        return record;
    }

    /**
     * 撤销单手棋（内部方法）。
     * <p>
     * 从历史记录末尾移除，还原棋盘格和落子计数。
     *
     * @param record 要撤销的落子记录
     */
    private void undoMove(MoveRecord record) {
        board[record.y][record.x] = EMPTY; // 把棋子从棋盘上拿走
        moveCount--;
        // 更新最后一手指针
        if (!moveHistory.isEmpty()) {
            MoveRecord last = moveHistory.get(moveHistory.size() - 1);
            lastMove = new int[]{last.x, last.y};
        } else {
            lastMove = null;
        }
    }

    /**
     * 撤销指定手数的棋（每手包含双方各一手）。
     * <p>
     * 每手撤销时先移除AI的棋子，再移除玩家的棋子，
     * 确保撤销后轮到玩家落子。
     * 就像"时光倒流"：先撤销AI刚下的那手，再撤销你之前下的那手，回到你该走的时候
     *
     * @param count 要撤销的手数
     * @return 实际撤销的手数
     */
    public int undoLastMoves(int count) {
        int undoCount = Math.min(count, moveCount / 2);
        for (int i = 0; i < undoCount; i++) {
            if (moveHistory.size() >= 2) {
                // 先撤销AI的一手（后下的在列表末尾）
                MoveRecord aiRecord = moveHistory.remove(moveHistory.size() - 1);
                undoMove(aiRecord);
                // 再撤销玩家的一手（先下的）
                MoveRecord playerRecord = moveHistory.remove(moveHistory.size() - 1);
                undoMove(playerRecord);
            }
        }
        return undoCount;
    }

    /**
     * 检查指定位置是否形成五连。
     * <p>
     * 在四个方向上分别向正反两方向延伸计数，
     * 任一方向连续同色棋子数≥5即获胜。
     * 就像数数：从当前位置向两边数同色的棋子，数到5个就赢了
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 棋子颜色
     * @return 形成五连返回true
     */
    public boolean checkWinAt(int x, int y, int player) {
        if (player == EMPTY) return false;
        for (int[] dir : DIRECTIONS) {
            int count = 1; // 算上当前位置本身
            int forwardSteps = 0;
            int backwardSteps = 0;
            // 向正方向数
            for (int step = 1; step < 5; step++) {
                int nx = x + dir[0] * step;
                int ny = y + dir[1] * step;
                if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && board[ny][nx] == player) {
                    count++;
                    forwardSteps = step;
                } else break;
            }
            // 向反方向数
            for (int step = 1; step < 5; step++) {
                int nx = x - dir[0] * step;
                int ny = y - dir[1] * step;
                if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && board[ny][nx] == player) {
                    count++;
                    backwardSteps = step;
                } else break;
            }
            if (count >= 5) {
                // 记录胜利五连线两端坐标
                int x1 = x - dir[0] * backwardSteps;
                int y1 = y - dir[1] * backwardSteps;
                int x2 = x + dir[0] * forwardSteps;
                int y2 = y + dir[1] * forwardSteps;
                winningLine = new int[]{x1, y1, x2, y2};
                return true;
            }
        }
        return false;
    }

    /**
     * 检查游戏是否结束。
     * <p>
     * 检查最后一手是否形成五连，或棋盘是否已满（平局）。
     *
     * @return 游戏结束返回true
     */
    public boolean checkGameOver() {
        if (lastMove != null) {
            int x = lastMove[0];
            int y = lastMove[1];
            int player = board[y][x];
            if (checkWinAt(x, y, player)) {
                gameOver = true;
                winner = player;
                return true;
            }
        }
        // 棋盘下满，平局
        if (moveCount >= BOARD_SIZE * BOARD_SIZE) {
            gameOver = true;
            winner = null;
            return true;
        }
        return false;
    }

    /**
     * 切换当前执子方。
     * 黑方下完换白方，白方下完换黑方
     */
    public void switchPlayer() {
        currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
    }

    /**
     * 重置游戏到初始状态。
     * 清空棋盘，回到黑方先手
     */
    public void reset() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        winner = null;
        moveHistory.clear();
        moveCount = 0;
        lastMove = null;
        winningLine = null;
    }

    // ===== 禁手（Forbidden Moves）规则支持 =====

    /** 禁手类型枚举（仅黑方受约束）。 */
    public enum ForbiddenType {
        /** 非禁手（合法） */
        NONE,
        /** 三三禁手（同时形成两个活三） */
        THREE_THREE,
        /** 四四禁手（同时形成两个四） */
        FOUR_FOUR,
        /** 长连禁手（形成六子及以上连线） */
        OVERLINE
    }

    /**
     * 设置禁手规则开关。
     *
     * @param enabled true 表示启用禁手（黑方不可走三三/四四/长连）
     */
    public void setForbiddenMovesEnabled(boolean enabled) {
        this.forbiddenMovesEnabled = enabled;
    }

    /**
     * 查询禁手规则是否启用。
     *
     * @return 启用返回true
     */
    public boolean isForbiddenMovesEnabled() {
        return forbiddenMovesEnabled;
    }

    /**
     * 判断当前执子方在 (x,y) 落子是否构成禁手。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 构成禁手返回true
     */
    public boolean isForbiddenMove(int x, int y) {
        return getForbiddenType(x, y, currentPlayer, board) != ForbiddenType.NONE;
    }

    /**
     * 获取当前执子方在 (x,y) 落子的禁手类型。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 禁手类型，合法返回 {@link ForbiddenType#NONE}
     */
    public ForbiddenType getForbiddenType(int x, int y) {
        return getForbiddenType(x, y, currentPlayer, board);
    }

    /**
     * 通用禁手判定：在指定棋盘上，player 在 (x,y) 落子是否构成禁手。
     * <p>
     * 规则（仅约束黑方）：
     * <ul>
     *   <li>长连：形成六子及以上连线 → 禁手</li>
     *   <li>五连：形成恰好五连 → 获胜，优先于禁手，合法</li>
     *   <li>四四：同时形成两个"四"（含活四/冲四）→ 禁手</li>
     *   <li>三三：同时形成两个"活三" → 禁手</li>
     * </ul>
     *
     * @param x          横坐标
     * @param y          纵坐标
     * @param player     落子方颜色
     * @param testBoard  棋盘数组（会被临时修改后复原）
     * @return 禁手类型
     */
    public ForbiddenType getForbiddenType(int x, int y, int player, int[][] testBoard) {
        if (!forbiddenMovesEnabled) return ForbiddenType.NONE;
        if (player != BLACK) return ForbiddenType.NONE;
        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) return ForbiddenType.NONE;
        if (testBoard[y][x] != EMPTY) return ForbiddenType.NONE;

        int saved = testBoard[y][x];
        testBoard[y][x] = BLACK;
        ForbiddenType result = analyzeForbidden(x, y, testBoard);
        testBoard[y][x] = saved;
        return result;
    }

    /**
     * 分析 (x,y) 落子（已模拟为黑子）是否构成禁手。
     */
    private ForbiddenType analyzeForbidden(int x, int y, int[][] board) {
        boolean overline = false;
        boolean five = false;
        int fourCount = 0;
        int threeCount = 0;

        for (int[] dir : DIRECTIONS) {
            // 以 (x,y) 为中心、半径4取直线（9格），中心索引4
            int[] line = new int[9];
            for (int i = 0; i < 9; i++) {
                int cx = x + dir[0] * (i - 4);
                int cy = y + dir[1] * (i - 4);
                if (cx < 0 || cx >= BOARD_SIZE || cy < 0 || cy >= BOARD_SIZE) {
                    line[i] = -1; // 墙：视为阻挡
                } else {
                    line[i] = board[cy][cx];
                }
            }
            line[4] = BLACK; // 模拟落子
            int run = maxRunThrough(line, 4);

            if (run >= 6) {
                overline = true;
            } else if (run == 5) {
                five = true;
            } else {
                boolean hasFour = false;
                for (int i = 0; i < 9; i++) {
                    if (i == 4 || line[i] != EMPTY) continue;
                    if (wouldMakeFive(line, i)) { hasFour = true; break; }
                }
                if (hasFour) {
                    fourCount++;
                } else {
                    boolean hasOpenThree = false;
                    for (int i = 0; i < 9; i++) {
                        if (i == 4 || line[i] != EMPTY) continue;
                        if (wouldMakeOpenFour(line, i)) { hasOpenThree = true; break; }
                    }
                    if (hasOpenThree) threeCount++;
                }
            }
        }

        if (overline) return ForbiddenType.OVERLINE;
        if (five) return ForbiddenType.NONE;
        if (fourCount >= 2) return ForbiddenType.FOUR_FOUR;
        if (threeCount >= 2) return ForbiddenType.THREE_THREE;
        return ForbiddenType.NONE;
    }

    /** 计算直线中通过 idx 的最大连续黑子数。 */
    private static int maxRunThrough(int[] line, int idx) {
        int run = 1;
        int i = idx + 1;
        while (i < line.length && line[i] == BLACK) { run++; i++; }
        i = idx - 1;
        while (i >= 0 && line[i] == BLACK) { run++; i--; }
        return run;
    }

    /** 在 line[idx] 模拟落黑子后，是否能形成五连。 */
    private static boolean wouldMakeFive(int[] line, int idx) {
        int saved = line[idx];
        line[idx] = BLACK;
        boolean r = maxRunThrough(line, idx) >= 5;
        line[idx] = saved;
        return r;
    }

    /** 在 line[idx] 模拟落黑子后，是否能形成"活四"（四子且两端均为空）。 */
    private static boolean wouldMakeOpenFour(int[] line, int idx) {
        int saved = line[idx];
        line[idx] = BLACK;
        int left = idx, right = idx;
        while (left - 1 >= 0 && line[left - 1] == BLACK) left--;
        while (right + 1 < line.length && line[right + 1] == BLACK) right++;
        boolean openFour = false;
        if (right - left + 1 == 4) {
            boolean beforeOpen = (left - 1 >= 0) && line[left - 1] == EMPTY;
            boolean afterOpen = (right + 1 < line.length) && line[right + 1] == EMPTY;
            openFour = beforeOpen && afterOpen;
        }
        line[idx] = saved;
        return openFour;
    }

    /**
     * 落子记录，用于历史回放和悔棋。
     * 就像棋谱上的每一行：记录了谁在哪个位置下了什么棋
     */
    public static class MoveRecord {
        /** 落子横坐标 */
        public int x, y;
        /** 落子方颜色 */
        public int player;

        public MoveRecord(int x, int y, int player) {
            this.x = x;
            this.y = y;
            this.player = player;
        }
    }
}

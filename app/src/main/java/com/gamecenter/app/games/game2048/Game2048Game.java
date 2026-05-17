package com.gamecenter.app.games.game2048;

import java.util.Random;

/**
 * 2048 游戏核心逻辑类
 *
 * <p>负责管理 4×4 棋盘状态、方块移动与合并、分数计算以及游戏结束判定。
 * 采用"压缩-合并"策略实现四个方向的移动：左移为基础操作，
 * 右移通过反转行实现，上/下移通过转置矩阵实现，避免重复编写合并逻辑。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>新方块以 90% 概率生成 2、10% 概率生成 4，与原版 2048 一致</li>
 *   <li>每次移动后仅当棋盘发生变化时才添加新方块</li>
 *   <li>游戏结束条件：棋盘满且无相邻相同方块</li>
 * </ul>
 * </p>
 */
public class Game2048Game {
    /** 4×4 棋盘，board[y][x]，0 表示空格 */
    private int[][] board;
    /** 当前累计分数 */
    private int score;
    /** 游戏是否结束 */
    private boolean gameOver;
    /** 随机数生成器，用于添加新方块 */
    private Random random;

    /**
     * 构造方法，初始化随机数生成器并重置棋盘
     */
    public Game2048Game() {
        random = new Random();
        reset();
    }

    /**
     * 重置棋盘到初始状态
     *
     * <p>清空棋盘、归零分数，然后在空棋盘上随机添加两个方块作为开局。</p>
     */
    public void reset() {
        board = new int[4][4];
        score = 0;
        gameOver = false;
        addRandomTile();
        addRandomTile();
    }

    /**
     * 获取指定位置的方块数值
     *
     * @param x 列索引（0-3）
     * @param y 行索引（0-3）
     * @return 该位置的数值，0 表示空格
     */
    public int getTile(int x, int y) {
        return board[y][x];
    }

    /**
     * 获取当前分数
     *
     * @return 累计分数
     */
    public int getScore() {
        return score;
    }

    /**
     * 判断游戏是否结束
     *
     * @return 如果棋盘满且无法合并则返回 true
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * 获取棋盘快照（深拷贝）
     *
     * <p>用于保存/恢复游戏状态，返回的数组与内部棋盘互不影响。</p>
     *
     * @return 4×4 棋盘的深拷贝
     */
    public int[][] getBoardSnapshot() {
        int[][] copy = new int[4][4];
        for (int y = 0; y < 4; y++) {
            System.arraycopy(board[y], 0, copy[y], 0, 4);
        }
        return copy;
    }

    /**
     * 从外部数据恢复游戏状态
     *
     * @param savedBoard    保存的棋盘数据
     * @param savedScore    保存的分数
     * @param savedGameOver 保存的游戏结束标志
     */
    public void restoreState(int[][] savedBoard, int savedScore, boolean savedGameOver) {
        board = savedBoard;
        score = savedScore;
        gameOver = savedGameOver;
        random = new Random();
    }

    /**
     * 在随机空位添加一个新方块
     *
     * <p>遍历棋盘收集所有空位，随机选择一个放置新方块。
     * 新方块有 90% 概率为 2，10% 概率为 4。</p>
     */
    private void addRandomTile() {
        java.util.List<int[]> emptyTiles = new java.util.ArrayList<>();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (board[y][x] == 0) {
                    emptyTiles.add(new int[]{x, y});
                }
            }
        }
        if (!emptyTiles.isEmpty()) {
            int[] tile = emptyTiles.get(random.nextInt(emptyTiles.size()));
            // 90% 概率生成 2，10% 概率生成 4
            board[tile[1]][tile[0]] = random.nextDouble() < 0.9 ? 2 : 4;
        }
    }

    /**
     * 压缩一行：将非零元素向左靠拢，消除空隙
     *
     * @param row 长度为 4 的一行数据
     * @return 压缩后的行（非零值靠左，右侧补零）
     */
    private int[] compress(int[] row) {
        int[] result = new int[4];
        int index = 0;
        for (int value : row) {
            if (value != 0) {
                result[index++] = value;
            }
        }
        return result;
    }

    /**
     * 合并一行中相邻相同的方块（向左合并）
     *
     * <p>从左到右扫描，若相邻两个方块数值相同则合并为两倍，
     * 合并后的分数累加到总分。合并后再次压缩以消除产生的空位。</p>
     *
     * @param row 已压缩的行数据
     * @return 合并并再次压缩后的行
     */
    private int[] merge(int[] row) {
        for (int i = 0; i < 3; i++) {
            if (row[i] != 0 && row[i] == row[i + 1]) {
                row[i] *= 2;
                score += row[i];
                row[i + 1] = 0;
            }
        }
        return compress(row);
    }

    /**
     * 反转一行（用于右移操作）
     *
     * @param row 长度为 4 的行数据
     * @return 反转后的行
     */
    private int[] reverse(int[] row) {
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            result[i] = row[3 - i];
        }
        return result;
    }

    /**
     * 转置棋盘（用于上/下移操作）
     *
     * <p>将行与列互换，使得上移可以复用左移的压缩-合并逻辑。</p>
     *
     * @param board 4×4 棋盘
     * @return 转置后的棋盘
     */
    private int[][] transpose(int[][] board) {
        int[][] result = new int[4][4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                result[x][y] = board[y][x];
            }
        }
        return result;
    }

    /**
     * 比较两个棋盘是否完全相同
     *
     * @param a 第一个棋盘
     * @param b 第二个棋盘
     * @return 如果所有位置数值相同则返回 true
     */
    private boolean boardsEqual(int[][] a, int[][] b) {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (a[y][x] != b[y][x]) return false;
            }
        }
        return true;
    }

    /**
     * 深拷贝当前棋盘
     *
     * @return 棋盘的深拷贝
     */
    private int[][] copyBoard() {
        int[][] copy = new int[4][4];
        for (int y = 0; y < 4; y++) {
            System.arraycopy(board[y], 0, copy[y], 0, 4);
        }
        return copy;
    }

    /**
     * 向左移动所有方块
     *
     * <p>对每一行执行"压缩→合并"操作。仅当棋盘发生变化时才添加新方块并检查游戏结束。</p>
     */
    public void moveLeft() {
        int[][] oldBoard = copyBoard();
        for (int y = 0; y < 4; y++) {
            board[y] = merge(compress(board[y]));
        }
        if (!boardsEqual(oldBoard, board)) {
            addRandomTile();
            checkGameOver();
        }
    }

    /**
     * 向右移动所有方块
     *
     * <p>对每一行先反转、再压缩合并、再反转回来，实现右移效果。</p>
     */
    public void moveRight() {
        int[][] oldBoard = copyBoard();
        for (int y = 0; y < 4; y++) {
            board[y] = reverse(merge(compress(reverse(board[y]))));
        }
        if (!boardsEqual(oldBoard, board)) {
            addRandomTile();
            checkGameOver();
        }
    }

    /**
     * 向上移动所有方块
     *
     * <p>转置棋盘后执行左移逻辑（即原棋盘的上移），再转置回来。</p>
     */
    public void moveUp() {
        int[][] oldBoard = copyBoard();
        board = transpose(board);
        for (int y = 0; y < 4; y++) {
            board[y] = merge(compress(board[y]));
        }
        board = transpose(board);
        if (!boardsEqual(oldBoard, board)) {
            addRandomTile();
            checkGameOver();
        }
    }

    /**
     * 向下移动所有方块
     *
     * <p>转置棋盘后执行右移逻辑（即原棋盘的下移），再转置回来。</p>
     */
    public void moveDown() {
        int[][] oldBoard = copyBoard();
        board = transpose(board);
        for (int y = 0; y < 4; y++) {
            board[y] = reverse(merge(compress(reverse(board[y]))));
        }
        board = transpose(board);
        if (!boardsEqual(oldBoard, board)) {
            addRandomTile();
            checkGameOver();
        }
    }

    /**
     * 检查游戏是否结束
     *
     * <p>游戏结束条件：棋盘上没有空格，且没有任何相邻的相同方块可以合并。
     * 只要存在空格或可合并的相邻方块，游戏就继续。</p>
     */
    private void checkGameOver() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                // 存在空格，游戏未结束
                if (board[y][x] == 0) return;
                // 水平方向存在可合并的相邻方块
                if (x < 3 && board[y][x] == board[y][x + 1]) return;
                // 垂直方向存在可合并的相邻方块
                if (y < 3 && board[y][x] == board[y + 1][x]) return;
            }
        }
        gameOver = true;
    }
}

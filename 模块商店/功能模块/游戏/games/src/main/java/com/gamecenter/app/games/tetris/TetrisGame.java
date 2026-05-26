package com.gamecenter.app.games.tetris;

import java.util.Random;

/**
 * 俄罗斯方块游戏核心逻辑类
 *
 * <p>封装俄罗斯方块的完整游戏状态和规则，与 UI 完全解耦。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>维护10×20的游戏棋盘和当前/下一个方块状态</li>
 *   <li>处理方块的移动、旋转、锁定和消行逻辑</li>
 *   <li>计算得分、等级和下落速度</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>棋盘 board 使用二维数组，-1表示空格，非负整数表示已锁定方块的颜色索引</li>
 *   <li>7种标准方块形状（I/O/T/J/L/S/Z）以二维数组硬编码定义</li>
 *   <li>旋转通过矩阵转置+行反转实现顺时针90度旋转</li>
 *   <li>旋转时支持"墙踢"（wall kick）：原位置不合法时尝试左右偏移1格</li>
 *   <li>消行得分公式：lines² × 100 × level，鼓励一次消多行</li>
 *   <li>每消除10行升1级，每级下落间隔减少80ms，最低100ms</li>
 * </ul>
 */
public class TetrisGame {

    /** 棋盘列数 */
    public static final int COLS = 10;

    /** 棋盘行数 */
    public static final int ROWS = 20;

    /**
     * 7种标准俄罗斯方块形状定义。
     *
     * <p>每种方块以二维数组表示，1表示有方块，0表示空。
     * 顺序：I(长条)、O(方块)、T(T形)、J(J形)、L(L形)、S(S形)、Z(Z形)。</p>
     */
    public static final int[][][] TETROMINOS = {
            {{1, 1, 1, 1}},
            {{1, 1}, {1, 1}},
            {{1, 1, 1}, {0, 1, 0}},
            {{1, 1, 1}, {1, 0, 0}},
            {{1, 1, 1}, {0, 0, 1}},
            {{0, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {0, 1, 1}}
    };

    /**
     * 7种方块对应的RGB颜色。
     *
     * <p>顺序与 TETROMINOS 对应：青、黄、紫、绿、红、蓝、橙。</p>
     */
    public static final int[][] COLORS = {
            {0, 255, 255},
            {255, 255, 0},
            {170, 0, 255},
            {0, 255, 0},
            {255, 0, 0},
            {0, 0, 255},
            {255, 165, 0}
    };

    /** 游戏棋盘，-1=空格，>=0=已锁定方块的颜色索引 */
    private int[][] board;

    /** 当前正在下落的方块形状 */
    private int[][] currentPiece;

    /** 当前方块类型索引（对应 TETROMINOS） */
    private int currentPieceType;

    /** 当前方块颜色索引（对应 COLORS） */
    private int currentColor;

    /** 当前方块左上角在棋盘上的列坐标 */
    private int currentX;

    /** 当前方块左上角在棋盘上的行坐标 */
    private int currentY;

    /** 下一个方块类型索引 */
    private int nextPieceType;

    /** 下一个方块颜色索引 */
    private int nextColor;

    /** 游戏是否结束 */
    private boolean gameOver;

    /** 当前得分 */
    private int score;

    /** 当前等级 */
    private int level;

    /** 已消除的总行数 */
    private int linesCleared;

    /** 随机数生成器 */
    private Random random;

    /** 当前方块自动下落间隔（毫秒） */
    private int dropInterval;

    /**
     * 构造函数，初始化棋盘并生成第一个方块。
     */
    public TetrisGame() {
        board = new int[ROWS][COLS];
        random = new Random();
        gameOver = false;
        score = 0;
        level = 1;
        linesCleared = 0;
        dropInterval = 800;
        spawnPiece();
    }

    /**
     * 生成新的方块。
     *
     * <p>将预存的 nextPieceType 作为当前方块，同时随机生成新的下一个方块。
     * 新方块出现在棋盘顶部水平居中位置。
     * 如果新方块生成时位置不合法，则判定游戏结束。</p>
     *
     * <p>注意：首次调用时 nextPieceType 为0，会在方法内被覆盖为随机值，
     * 因此第一个方块的颜色和类型都是随机的。</p>
     */
    private void spawnPiece() {
        currentPieceType = nextPieceType;
        currentColor = nextColor;

        currentPieceType = random.nextInt(TETROMINOS.length);
        currentColor = random.nextInt(COLORS.length);
        nextPieceType = random.nextInt(TETROMINOS.length);
        nextColor = random.nextInt(COLORS.length);

        currentPiece = TETROMINOS[currentPieceType];
        // 水平居中放置
        currentX = COLS / 2 - currentPiece[0].length / 2;
        currentY = 0;

        // 如果初始位置就不合法，说明棋盘已满，游戏结束
        if (!isValidPosition(currentX, currentY, currentPiece)) {
            gameOver = true;
        }
    }

    /**
     * 检查方块在指定位置是否合法（不越界、不与已锁定方块重叠）。
     *
     * @param x     方块左上角的列坐标
     * @param y     方块左上角的行坐标
     * @param piece 方块形状二维数组
     * @return 位置合法返回 true，否则返回 false
     */
    public boolean isValidPosition(int x, int y, int[][] piece) {
        for (int row = 0; row < piece.length; row++) {
            for (int col = 0; col < piece[row].length; col++) {
                if (piece[row][col] != 0) {
                    int newX = x + col;
                    int newY = y + row;
                    // 左右边界和底部边界检测
                    if (newX < 0 || newX >= COLS || newY >= ROWS) {
                        return false;
                    }
                    // 与已锁定方块重叠检测（newY>=0 允许方块部分在棋盘上方）
                    if (newY >= 0 && board[newY][newX] != -1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 将当前方块下移一格。
     *
     * <p>如果下方位置合法则移动；否则锁定当前方块到棋盘，
     * 执行消行检测，并生成新方块。</p>
     *
     * @return 成功下移返回 true，方块被锁定返回 false
     */
    public boolean moveDown() {
        if (gameOver) return false;

        if (isValidPosition(currentX, currentY + 1, currentPiece)) {
            currentY++;
            return true;
        } else {
            // 无法继续下落，锁定方块
            lockPiece();
            clearLines();
            spawnPiece();
            return false;
        }
    }

    /**
     * 将当前方块左移一格。
     *
     * <p>仅在目标位置合法时执行移动。</p>
     */
    public void moveLeft() {
        if (gameOver) return;
        if (isValidPosition(currentX - 1, currentY, currentPiece)) {
            currentX--;
        }
    }

    /**
     * 将当前方块右移一格。
     *
     * <p>仅在目标位置合法时执行移动。</p>
     */
    public void moveRight() {
        if (gameOver) return;
        if (isValidPosition(currentX + 1, currentY, currentPiece)) {
            currentX++;
        }
    }

    /**
     * 顺时针旋转当前方块90度。
     *
     * <p>旋转后如果原位置不合法，会尝试"墙踢"：
     * 先左移1格，再右移1格，寻找合法位置。
     * 如果三种位置都不合法，则放弃本次旋转。</p>
     */
    public void rotate() {
        if (gameOver) return;

        int[][] rotated = rotatePiece(currentPiece);
        // 先尝试原位旋转
        if (isValidPosition(currentX, currentY, rotated)) {
            currentPiece = rotated;
        } else if (isValidPosition(currentX - 1, currentY, rotated)) {
            // 墙踢：左移1格
            currentX--;
            currentPiece = rotated;
        } else if (isValidPosition(currentX + 1, currentY, rotated)) {
            // 墙踢：右移1格
            currentX++;
            currentPiece = rotated;
        }
        // 三种位置都不合法，放弃旋转
    }

    /**
     * 将方块形状矩阵顺时针旋转90度。
     *
     * <p>算法：先转置矩阵，再反转每行的元素顺序。
     * 等价于 rotated[c][rows-1-r] = piece[r][c]。</p>
     *
     * @param piece 原始方块形状
     * @return 旋转后的方块形状
     */
    private int[][] rotatePiece(int[][] piece) {
        int rows = piece.length;
        int cols = piece[0].length;
        int[][] rotated = new int[cols][rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotated[c][rows - 1 - r] = piece[r][c];
            }
        }
        return rotated;
    }

    /**
     * 将当前方块锁定到棋盘上。
     *
     * <p>遍历当前方块的每个有效格子，将其颜色索引写入棋盘对应位置。</p>
     */
    private void lockPiece() {
        for (int row = 0; row < currentPiece.length; row++) {
            for (int col = 0; col < currentPiece[row].length; col++) {
                if (currentPiece[row][col] != 0) {
                    int y = currentY + row;
                    int x = currentX + col;
                    if (y >= 0 && y < ROWS && x >= 0 && x < COLS) {
                        board[y][x] = currentColor;
                    }
                }
            }
        }
    }

    /**
     * 检测并消除已满的行。
     *
     * <p>从底部向上逐行检查，如果某行所有格子都非-1（即已填满），
     * 则将该行消除，上方所有行下移一行，顶部补空行。
     * 消行后 row++ 重新检查当前行（因为上方行下移了）。</p>
     *
     * <p>得分计算：lines² × 100 × level，鼓励一次消多行。</p>
     * <p>等级计算：每消除10行升1级，每级下落间隔减少80ms，最低100ms。</p>
     */
    private void clearLines() {
        int lines = 0;
        for (int row = ROWS - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] == -1) {
                    full = false;
                    break;
                }
            }
            if (full) {
                // 将该行上方所有行下移一行
                for (int r = row; r > 0; r--) {
                    board[r] = board[r - 1].clone();
                }
                // 顶部补空行
                board[0] = new int[COLS];
                for (int i = 0; i < COLS; i++) board[0][i] = -1;
                lines++;
                // 重新检查当前行（因为上方行已下移至此）
                row++;
            }
        }

        if (lines > 0) {
            linesCleared += lines;
            // 消行得分：行数的平方 × 100 × 等级，鼓励一次消多行
            score += lines * lines * 100 * level;

            // 等级提升：每消除10行升1级
            int newLevel = linesCleared / 10 + 1;
            if (newLevel > level) {
                level = newLevel;
                // 每级减少80ms下落间隔，最低100ms
                dropInterval = Math.max(100, 800 - (level - 1) * 80);
            }
        }
    }

    /**
     * 获取游戏棋盘。
     *
     * @return 棋盘二维数组，-1=空格，>=0=颜色索引
     */
    public int[][] getBoard() {
        return board;
    }

    /**
     * 获取当前正在下落的方块形状。
     *
     * @return 方块形状二维数组
     */
    public int[][] getCurrentPiece() {
        return currentPiece;
    }

    /**
     * 获取当前方块的颜色索引。
     *
     * @return 颜色索引（对应 COLORS 数组）
     */
    public int getCurrentColor() {
        return currentColor;
    }

    /**
     * 获取当前方块左上角的列坐标。
     *
     * @return 列坐标
     */
    public int getCurrentX() {
        return currentX;
    }

    /**
     * 获取当前方块左上角的行坐标。
     *
     * @return 行坐标
     */
    public int getCurrentY() {
        return currentY;
    }

    /**
     * 获取下一个方块的类型索引。
     *
     * @return 方块类型索引（对应 TETROMINOS 数组）
     */
    public int getNextPieceType() {
        return nextPieceType;
    }

    /**
     * 获取下一个方块的颜色索引。
     *
     * @return 颜色索引（对应 COLORS 数组）
     */
    public int getNextColor() {
        return nextColor;
    }

    /**
     * 判断游戏是否结束。
     *
     * @return 游戏结束返回 true
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * 获取当前得分。
     *
     * @return 得分值
     */
    public int getScore() {
        return score;
    }

    /**
     * 获取当前等级。
     *
     * @return 等级值
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取已消除的总行数。
     *
     * @return 消行数
     */
    public int getLinesCleared() {
        return linesCleared;
    }

    /**
     * 获取当前方块自动下落间隔。
     *
     * @return 下落间隔（毫秒）
     */
    public int getDropInterval() {
        return dropInterval;
    }

    /**
     * 重置游戏状态到初始值。
     *
     * <p>清空棋盘（所有格子设为-1），重置得分、等级和下落间隔，
     * 然后生成新的方块。</p>
     */
    public void reset() {
        board = new int[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = -1;
            }
        }
        gameOver = false;
        score = 0;
        level = 1;
        linesCleared = 0;
        dropInterval = 800;
        spawnPiece();
    }
}

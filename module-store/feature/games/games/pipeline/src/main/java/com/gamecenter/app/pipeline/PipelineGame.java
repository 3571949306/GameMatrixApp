package com.gamecenter.app.pipeline;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * 管道工游戏逻辑类（独立 APK 模块版本）。
 *
 * <p>由宿主 PipelineActivity 的游戏逻辑提取而来。
 * 持有管道类型、旋转状态与谜题生成/校验逻辑，不依赖任何 UI 组件。</p>
 *
 * <p>管道类型：直线、L型、T型、十字。点击旋转90°。</p>
 */
public class PipelineGame {

    // ==================== 管道类型常量 ====================
    public static final int PIPE_NONE = 0;
    public static final int PIPE_STRAIGHT = 1;  // 直线（上下或左右）
    public static final int PIPE_L = 2;         // L型弯
    public static final int PIPE_T = 3;         // T型
    public static final int PIPE_CROSS = 4;     // 十字

    // 管道符号（旋转值 0-3，每次顺时针旋转 90°）
    public static final String[] PIPE_CHARS = {"│", "─", "│", "─"};
    public static final String[] PIPE_L_CHARS = {"└", "┌", "┐", "┘"};
    public static final String[] PIPE_T_CHARS = {"├", "┬", "┤", "┴"};
    public static final String PIPE_CROSS_CHAR = "┼";

    // 与上述可视字符一一对应：上=1、右=2、下=4、左=8。
    // 生成解与玩家连通判定共用此表，不能分别维护两套朝向约定。
    private static final int[][] PIPE_PORTS = {
            {0, 0, 0, 0},
            {5, 10, 5, 10},
            {3, 6, 12, 9},
            {7, 14, 13, 11},
            {15, 15, 15, 15}
    };
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    // ==================== 游戏状态 ====================
    private int currentLevel = 1;
    private int gridSize = 5;
    private int[][] pipeTypes;        // 管道类型
    private int[][] pipeRotations;    // 管道旋转（0-3）
    private int[][] targetRotations;  // 生成器的一组可解旋转见证，不参与玩家胜利判定
    private int moveCount = 0;
    private boolean gameActive = false;
    private final Random random = new Random();

    /** 关卡通关分数累计 */
    private int totalScore = 0;

    // ==================== 关卡管理 ====================

    public int getCurrentLevel() { return currentLevel; }
    public int getGridSize() { return gridSize; }
    public int getMoveCount() { return moveCount; }
    public int getTotalScore() { return totalScore; }
    public boolean isGameActive() { return gameActive; }

    /**
     * 开始新关卡：根据关卡决定网格大小，生成谜题，重置步数。
     */
    public void startLevel() {
        gameActive = true;
        moveCount = 0;

        if (currentLevel <= 2) {
            gridSize = 4;
        } else if (currentLevel <= 4) {
            gridSize = 5;
        } else {
            gridSize = 6;
        }

        generatePuzzle();
    }

    /**
     * 生成谜题：随机游走生成路径，再为路径与非路径格子分配管道类型与目标旋转。
     */
    private void generatePuzzle() {
        pipeTypes = new int[gridSize][gridSize];
        targetRotations = new int[gridSize][gridSize];
        pipeRotations = new int[gridSize][gridSize];

        // 生成一条随机路径
        boolean[][] onPath = new boolean[gridSize][gridSize];
        int row = 0;
        int col = 0;
        onPath[row][col] = true;

        // 随机游走生成路径
        while (row < gridSize - 1 || col < gridSize - 1) {
            if (row == gridSize - 1) {
                col++;
            } else if (col == gridSize - 1) {
                row++;
            } else {
                if (random.nextBoolean()) {
                    row++;
                } else {
                    col++;
                }
            }
            onPath[row][col] = true;
        }

        // 为非路径格子分配随机管道
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (!onPath[r][c]) {
                    if (random.nextInt(3) == 0) {
                        pipeTypes[r][c] = PIPE_STRAIGHT + random.nextInt(3);
                        targetRotations[r][c] = random.nextInt(4);
                    } else {
                        pipeTypes[r][c] = PIPE_NONE;
                    }
                }
            }
        }

        // 为路径上的格子分配管道类型与目标旋转
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (onPath[r][c]) {
                    int requiredPorts = 0;
                    for (int direction = 0; direction < 4; direction++) {
                        int nextRow = r + ROW_STEP[direction];
                        int nextCol = c + COL_STEP[direction];
                        if (inBounds(nextRow, nextCol) && onPath[nextRow][nextCol]) {
                            requiredPorts |= 1 << direction;
                        }
                    }
                    assignPathPipe(r, c, requiredPorts);
                }
            }
        }
    }

    private void assignPathPipe(int row, int col, int requiredPorts) {
        // 优先使用能满足路径的最简单管形；端点只要求向棋盘内的连接。
        for (int type = PIPE_STRAIGHT; type <= PIPE_CROSS; type++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                if ((PIPE_PORTS[type][rotation] & requiredPorts) == requiredPorts) {
                    pipeTypes[row][col] = type;
                    targetRotations[row][col] = rotation;
                    return;
                }
            }
        }
        throw new IllegalStateException("No pipe can satisfy the generated path ports");
    }

    /**
     * 为指定格子设置随机初始旋转（用于打乱）。
     */
    public int randomizeRotation(int row, int col) {
        pipeRotations[row][col] = random.nextInt(4);
        return pipeRotations[row][col];
    }

    /**
     * 获取指定格子的管道类型。
     */
    public int getPipeType(int row, int col) {
        return pipeTypes[row][col];
    }

    /**
     * 旋转指定格子的管道 90°，返回新的旋转值，步数 +1。
     */
    public int rotatePipe(int row, int col) {
        if (!gameActive || pipeTypes[row][col] == PIPE_NONE) {
            return pipeRotations[row][col];
        }
        pipeRotations[row][col] = (pipeRotations[row][col] + 1) % 4;
        moveCount++;
        return pipeRotations[row][col];
    }

    /**
     * 获取指定格子当前显示字符。
     */
    public String getPipeChar(int row, int col) {
        switch (pipeTypes[row][col]) {
            case PIPE_NONE:    return "";
            case PIPE_STRAIGHT: return PIPE_CHARS[pipeRotations[row][col]];
            case PIPE_L:       return PIPE_L_CHARS[pipeRotations[row][col]];
            case PIPE_T:       return PIPE_T_CHARS[pipeRotations[row][col]];
            case PIPE_CROSS:   return PIPE_CROSS_CHAR;
            default:           return "";
        }
    }

    /**
     * 判断该格是否能沿当前显示管道从左上起点到达，供 UI 高亮水路。
     * 保留原 API 名称；不比较生成器的旋转见证，空格或断开的支管返回 false。
     */
    public boolean isPipeCorrect(int row, int col) {
        return inBounds(row, col) && reachableFromStart()[row][col];
    }

    /**
     * 检查左上起点与右下终点是否双向连通，允许支路、空闲开口和非路径管道。
     */
    public boolean isAllCorrect() {
        return reachableFromStart()[gridSize - 1][gridSize - 1];
    }

    private boolean[][] reachableFromStart() {
        boolean[][] reached = new boolean[gridSize][gridSize];
        if (pipeTypes == null || pipeRotations == null || pipeTypes[0][0] == PIPE_NONE) {
            return reached;
        }
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        reached[0][0] = true;
        pending.add(0);
        while (!pending.isEmpty()) {
            int cell = pending.remove();
            int row = cell / gridSize;
            int col = cell % gridSize;
            int ports = PIPE_PORTS[pipeTypes[row][col]][pipeRotations[row][col]];
            for (int direction = 0; direction < 4; direction++) {
                int nextRow = row + ROW_STEP[direction];
                int nextCol = col + COL_STEP[direction];
                if (!inBounds(nextRow, nextCol) || reached[nextRow][nextCol]) continue;
                int nextPorts = PIPE_PORTS[pipeTypes[nextRow][nextCol]][pipeRotations[nextRow][nextCol]];
                int opposite = (direction + 2) % 4;
                if ((ports & (1 << direction)) != 0 && (nextPorts & (1 << opposite)) != 0) {
                    reached[nextRow][nextCol] = true;
                    pending.add(nextRow * gridSize + nextCol);
                }
            }
        }
        return reached;
    }

    private boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < gridSize && col < gridSize;
    }

    /**
     * 关卡通关：结算分数、推进关卡、结束本关。
     * @return 本关得分；未开始、尚未连通或已经结算时返回 0，不改变关卡和分数。
     */
    public int completeLevel() {
        if (!gameActive || !isAllCorrect()) return 0;
        gameActive = false;
        int score = Math.max(200 - moveCount * 5, 20) * currentLevel;
        totalScore += score;
        currentLevel++;
        return score;
    }
}

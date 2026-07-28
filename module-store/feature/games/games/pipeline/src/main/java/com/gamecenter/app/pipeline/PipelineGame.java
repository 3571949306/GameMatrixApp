package com.gamecenter.app.pipeline;

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

    // 管道符号（0=上, 1=右, 2=下, 3=左）
    public static final String[] PIPE_CHARS = {"│", "─", "│", "─"};
    public static final String[] PIPE_L_CHARS = {"└", "┌", "┐", "┘"};
    public static final String[] PIPE_T_CHARS = {"├", "┬", "┤", "┴"};
    public static final String PIPE_CROSS_CHAR = "┼";

    // ==================== 游戏状态 ====================
    private int currentLevel = 1;
    private int gridSize = 5;
    private int[][] pipeTypes;        // 管道类型
    private int[][] pipeRotations;    // 管道旋转（0-3）
    private int[][] targetRotations;  // 目标旋转
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
                    boolean hasUp = r > 0 && onPath[r - 1][c];
                    boolean hasDown = r < gridSize - 1 && onPath[r + 1][c];
                    boolean hasLeft = c > 0 && onPath[r][c - 1];
                    boolean hasRight = c < gridSize - 1 && onPath[r][c + 1];

                    int connections = (hasUp ? 1 : 0) + (hasDown ? 1 : 0)
                            + (hasLeft ? 1 : 0) + (hasRight ? 1 : 0);

                    if (connections == 4) {
                        pipeTypes[r][c] = PIPE_CROSS;
                        targetRotations[r][c] = 0;
                    } else if (connections == 3) {
                        pipeTypes[r][c] = PIPE_T;
                        if (!hasUp) targetRotations[r][c] = 2;
                        else if (!hasRight) targetRotations[r][c] = 3;
                        else if (!hasDown) targetRotations[r][c] = 0;
                        else targetRotations[r][c] = 1;
                    } else if (connections == 2) {
                        if ((hasUp && hasDown) || (hasLeft && hasRight)) {
                            pipeTypes[r][c] = PIPE_STRAIGHT;
                            targetRotations[r][c] = (hasUp && hasDown) ? 0 : 1;
                        } else {
                            pipeTypes[r][c] = PIPE_L;
                            if (hasDown && hasRight) targetRotations[r][c] = 0;
                            else if (hasDown && hasLeft) targetRotations[r][c] = 3;
                            else if (hasUp && hasRight) targetRotations[r][c] = 1;
                            else targetRotations[r][c] = 2;
                        }
                    } else {
                        // 死胡同 - 用直线
                        pipeTypes[r][c] = PIPE_STRAIGHT;
                        targetRotations[r][c] = hasUp || hasDown ? 0 : 1;
                    }
                }
            }
        }
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
     * 判断指定格子的管道旋转是否与目标一致（十字管道恒为正确）。
     */
    public boolean isPipeCorrect(int row, int col) {
        if (pipeTypes[row][col] == PIPE_NONE || pipeTypes[row][col] == PIPE_CROSS) {
            return true;
        }
        return pipeRotations[row][col] == targetRotations[row][col];
    }

    /**
     * 检查所有管道是否全部正确。
     */
    public boolean isAllCorrect() {
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (!isPipeCorrect(r, c)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 关卡通关：结算分数、推进关卡、结束本关。
     * @return 本关得分
     */
    public int completeLevel() {
        gameActive = false;
        int score = Math.max(200 - moveCount * 5, 20) * currentLevel;
        totalScore += score;
        currentLevel++;
        return score;
    }
}

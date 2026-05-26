package com.gamecenter.app.games.pipeline;

import java.util.Random;

/**
 * 接水管游戏的核心逻辑类
 *
 * <p>游戏规则：5x5网格中随机生成从水源（左边缘）到出口（右边缘）的管道路径，
 * 玩家通过旋转管道使水流从水源连通到出口。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>管道类型用整数常量表示（0-11），包括直管、弯管、十字管和T型管</li>
 *   <li>关卡生成采用"先构建正确路径，再随机填充和旋转"的策略，
 *       确保每关都有解</li>
 *   <li>水流检测使用递归洪水填充算法，从水源开始沿管道连接方向扩散</li>
 *   <li>canConnectTo方法判断管道类型是否允许向特定方向输出水流</li>
 * </ul>
 */
public class PipelineGame {
    /** 空格 */
    public static final int EMPTY = 0;
    /** 水平直管 ─ */
    public static final int HORIZONTAL = 1;
    /** 垂直直管 │ */
    public static final int VERTICAL = 2;
    /** 左上角弯管 ┐（连接右和下） */
    public static final int CORNER_TL = 3;
    /** 右上角弯管 ┌（连接左和下） */
    public static final int CORNER_TR = 4;
    /** 右下角弯管 ┘（连接左和上） */
    public static final int CORNER_BR = 5;
    /** 左下角弯管 └（连接右和上） */
    public static final int CORNER_BL = 6;
    /** 十字管 ┼（四个方向都连通） */
    public static final int CROSS = 7;
    /** T型管（向上，连接左、右、下） */
    public static final int T_UP = 8;
    /** T型管（向右，连接左、上、下） */
    public static final int T_RIGHT = 9;
    /** T型管（向下，连接左、右、上） */
    public static final int T_DOWN = 10;
    /** T型管（向左，连接右、上、下） */
    public static final int T_LEFT = 11;

    /** 网格尺寸（5x5） */
    private int size = 5;
    /** 管道类型网格 */
    private int[][] grid;
    /** 水流标记网格，true表示该格有水流通过 */
    private boolean[][] water;
    /** 水源列坐标（固定在第0列） */
    private int sourceX, sourceY;
    /** 出口列坐标（固定在最后一列） */
    private int destX, destY;
    /** 随机数生成器 */
    private Random random;

    /**
     * 构造方法，初始化随机数生成器并生成关卡
     */
    public PipelineGame() {
        random = new Random();
        generateLevel();
    }

    /**
     * 生成新的关卡
     *
     * <p>生成流程：</p>
     * <ol>
     *   <li>初始化5x5空网格</li>
     *   <li>随机选择水源（左边缘）和出口（右边缘）的行位置</li>
     *   <li>生成从水源到出口的路径</li>
     *   <li>用随机管道填充空格并随机旋转所有管道</li>
     * </ol>
     */
    public void generateLevel() {
        size = 5;
        grid = new int[size][size];
        water = new boolean[size][size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                grid[y][x] = EMPTY;
                water[y][x] = false;
            }
        }

        // 水源在左边缘随机行，出口在右边缘随机行
        sourceX = 0;
        sourceY = random.nextInt(size);
        destX = size - 1;
        destY = random.nextInt(size);

        generatePath();
        fillRemainingCells();
    }

    /**
     * 从水源到出口生成一条连通路径
     *
     * <p>路径生成策略：从水源出发，优先向右移动，必要时上下调整行位置，
     * 直到到达出口列和出口行。每一步根据当前方向设置管道类型。</p>
     */
    private void generatePath() {
        int x = sourceX;
        int y = sourceY;
        grid[y][x] = VERTICAL;

        while (x < destX || y != destY) {
            // 如果还没到出口列且（已在出口行或随机选择），则向右移动
            boolean goRight = x < destX && (y == destY || random.nextBoolean());
            // 如果需要向下移动
            boolean goDown = y < destY && !goRight;
            // 如果需要向上移动
            boolean goUp = y > destY && !goRight && (x == destX || random.nextBoolean());

            if (goRight) {
                x++;
                connectPipes(x - 1, y, x, y);
            } else if (goDown) {
                y++;
                connectPipes(x, y - 1, x, y);
            } else if (goUp) {
                y--;
                connectPipes(x, y + 1, x, y);
            } else {
                // 兜底：无法上下移动时强制向右
                x++;
            }
        }
    }

    /**
     * 连接两个相邻格子的管道
     *
     * <p>根据两个格子之间的方向关系（水平/垂直），
     * 将两个格子都设置为对应的直管类型。</p>
     *
     * @param x1 前一个格子的列坐标
     * @param y1 前一个格子的行坐标
     * @param x2 后一个格子的列坐标
     * @param y2 后一个格子的行坐标
     */
    private void connectPipes(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;

        if (dy < 0) {
            grid[y1][x1] = VERTICAL;
            grid[y2][x2] = VERTICAL;
        } else if (dy > 0) {
            grid[y1][x1] = VERTICAL;
            grid[y2][x2] = VERTICAL;
        } else {
            grid[y1][x1] = HORIZONTAL;
            grid[y2][x2] = HORIZONTAL;
        }
    }

    /**
     * 填充剩余空格并随机旋转所有管道
     *
     * <p>先用随机管道类型填充空格，然后对每个格子随机旋转0-3次，
     * 打乱管道方向使玩家需要旋转才能接通。</p>
     */
    private void fillRemainingCells() {
        int[] pipes = {HORIZONTAL, VERTICAL, CORNER_TL, CORNER_TR, CORNER_BR, CORNER_BL};
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (grid[y][x] == EMPTY) {
                    grid[y][x] = pipes[random.nextInt(pipes.length)];
                }
            }
        }

        // 随机旋转每个管道0-3次，打乱方向
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rotations = random.nextInt(4);
                for (int i = 0; i < rotations; i++) {
                    grid[y][x] = rotatePipeType(grid[y][x]);
                }
            }
        }
    }

    /**
     * 将管道类型旋转90度（顺时针）
     *
     * <p>旋转规则：</p>
     * <ul>
     *   <li>水平↔垂直互换</li>
     *   <li>弯管按TL→TR→BR→BL→TL循环</li>
     *   <li>T型管按UP→RIGHT→DOWN→LEFT→UP循环</li>
     *   <li>十字管和空格旋转后不变</li>
     * </ul>
     *
     * @param pipe 原始管道类型
     * @return 旋转后的管道类型
     */
    private int rotatePipeType(int pipe) {
        switch (pipe) {
            case HORIZONTAL: return VERTICAL;
            case VERTICAL: return HORIZONTAL;
            case CORNER_TL: return CORNER_TR;
            case CORNER_TR: return CORNER_BR;
            case CORNER_BR: return CORNER_BL;
            case CORNER_BL: return CORNER_TL;
            case T_UP: return T_RIGHT;
            case T_RIGHT: return T_DOWN;
            case T_DOWN: return T_LEFT;
            case T_LEFT: return T_UP;
            default: return pipe;
        }
    }

    /**
     * 旋转指定位置的管道90度
     *
     * <p>旋转后清除该格的水流标记，需要重新检测水流。</p>
     *
     * @param x 列坐标
     * @param y 行坐标
     */
    public void rotatePipe(int x, int y) {
        if (x >= 0 && x < size && y >= 0 && y < size) {
            grid[y][x] = rotatePipeType(grid[y][x]);
            water[y][x] = false;
        }
    }

    /**
     * 检测水流是否从水源连通到出口
     *
     * <p>先清除所有水流标记，然后从水源开始递归扩散水流，
     * 最后检查出口格是否有水流。</p>
     *
     * @return true表示水流已接通到出口
     */
    public boolean checkWaterFlow() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                water[y][x] = false;
            }
        }

        flowFromSource(sourceX, sourceY);
        return water[destY][destX];
    }

    /**
     * 从指定位置开始递归扩散水流
     *
     * <p>水流扩散逻辑：</p>
     * <ol>
     *   <li>水源格直接标记为有水</li>
     *   <li>其他格需要检查相邻格是否有水且能向本格输出水流</li>
     *   <li>本格有水后，继续检查本格管道是否能向四个方向的相邻格输出水流</li>
     * </ol>
     *
     * @param x 当前列坐标
     * @param y 当前行坐标
     */
    private void flowFromSource(int x, int y) {
        if (x < 0 || x >= size || y < 0 || y >= size) return;
        if (water[y][x]) return;

        int pipe = grid[y][x];
        if (pipe == EMPTY) return;

        if (x == sourceX && y == sourceY) {
            // 水源格直接标记为有水
            water[y][x] = true;
        } else {
            // 非水源格：检查是否有相邻的已通水格能向本格输出水流
            boolean canReceive = false;
            if (x > 0 && water[y][x - 1]) canReceive = canConnectTo(grid[y][x - 1], x - 1, y, x, y);
            if (x < size - 1 && !canReceive && water[y][x + 1]) canReceive = canConnectTo(grid[y][x + 1], x + 1, y, x, y);
            if (y > 0 && !canReceive && water[y - 1][x]) canReceive = canConnectTo(grid[y - 1][x], x, y - 1, x, y);
            if (y < size - 1 && !canReceive && water[y + 1][x]) canReceive = canConnectTo(grid[y + 1][x], x, y + 1, x, y);

            if (canReceive) {
                water[y][x] = true;
            }
        }

        // 本格有水后，递归向四个方向扩散
        if (water[y][x]) {
            if (x > 0 && !water[y][x - 1] && canConnectTo(pipe, x, y, x - 1, y)) {
                flowFromSource(x - 1, y);
            }
            if (x < size - 1 && !water[y][x + 1] && canConnectTo(pipe, x, y, x + 1, y)) {
                flowFromSource(x + 1, y);
            }
            if (y > 0 && !water[y - 1][x] && canConnectTo(pipe, x, y, x, y - 1)) {
                flowFromSource(x, y - 1);
            }
            if (y < size - 1 && !water[y + 1][x] && canConnectTo(pipe, x, y, x, y + 1)) {
                flowFromSource(x, y + 1);
            }
        }
    }

    /**
     * 判断从from格的管道是否能向to格方向输出水流
     *
     * <p>根据管道类型和方向偏移量判断连通性：</p>
     * <ul>
     *   <li>水平管：只能水平方向连通（dy==0）</li>
     *   <li>垂直管：只能垂直方向连通（dx==0）</li>
     *   <li>弯管：只能向两个特定方向连通</li>
     *   <li>十字管和T型管：所有方向都连通</li>
     * </ul>
     *
     * @param pipe 管道类型
     * @param fromX 起始列坐标
     * @param fromY 起始行坐标
     * @param toX 目标列坐标
     * @param toY 目标行坐标
     * @return true表示管道可以向该方向输出水流
     */
    private boolean canConnectTo(int pipe, int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;

        switch (pipe) {
            case HORIZONTAL:
                return dy == 0;
            case VERTICAL:
                return dx == 0;
            case CORNER_TL:
                return (dx == 1 && dy == 0) || (dx == 0 && dy == 1);
            case CORNER_TR:
                return (dx == -1 && dy == 0) || (dx == 0 && dy == 1);
            case CORNER_BR:
                return (dx == -1 && dy == 0) || (dx == 0 && dy == -1);
            case CORNER_BL:
                return (dx == 1 && dy == 0) || (dx == 0 && dy == -1);
            case CROSS:
            case T_UP:
            case T_RIGHT:
            case T_DOWN:
            case T_LEFT:
                return true;
            default:
                return false;
        }
    }

    /**
     * 获取指定位置的管道类型
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return 管道类型常量
     */
    public int getPipe(int x, int y) {
        return grid[y][x];
    }

    /**
     * 判断指定位置是否有水流通过
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true表示有水流
     */
    public boolean hasWater(int x, int y) {
        return water[y][x];
    }

    /**
     * 获取网格尺寸
     *
     * @return 网格边长
     */
    public int getSize() {
        return size;
    }

    /**
     * 重置游戏，生成新关卡
     */
    public void reset() {
        generateLevel();
    }
}

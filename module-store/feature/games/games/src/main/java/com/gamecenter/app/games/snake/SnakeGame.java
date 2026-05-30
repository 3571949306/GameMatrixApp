package com.gamecenter.app.games.snake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇游戏核心逻辑类
 *
 * <p>封装贪吃蛇的完整游戏状态和规则，与 UI 完全解耦。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>维护棋盘（board）、蛇身（snake）、食物（food）等游戏状态</li>
 *   <li>处理蛇的移动、碰撞检测和食物生成</li>
 *   <li>管理方向切换，防止180度反向移动</li>
 *   <li>计算得分和控制速度</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>棋盘使用二维数组 board 表示，0=空格，-1=食物，正数未使用（蛇位置由 snake 列表维护）</li>
 *   <li>蛇身用 List&lt;int[]&gt; 存储，索引0为蛇头，尾部追加/移除实现移动</li>
 *   <li>使用 nextDirection 缓冲方向输入，避免同帧内多次转向导致穿越自身</li>
 * </ul>
 */
public class SnakeGame {

    /** 移动方向枚举 */
    public enum Direction { UP, DOWN, LEFT, RIGHT }

    /** 棋盘列数 */
    public static final int COLS = 20;

    /** 棋盘行数 */
    public static final int ROWS = 20;

    /** 棋盘二维数组，0=空，-1=食物 */
    private int[][] board;

    /** 蛇身坐标列表，索引0为蛇头，每个元素为 [x, y] */
    private List<int[]> snake;

    /** 食物坐标 [x, y] */
    private int[] food;

    /** 当前实际移动方向 */
    private Direction direction;

    /** 下一帧将要执行的方向（输入缓冲），防止同帧多次转向 */
    private Direction nextDirection;

    /** 游戏是否结束 */
    private boolean gameOver;

    /** 当前得分，每吃一个食物加10分 */
    private int score;

    /** 游戏刷新间隔（毫秒），值越小速度越快 */
    private int speed;

    /** 随机数生成器，用于食物位置随机化 */
    private Random random;

    /**
     * 构造函数，初始化游戏状态。
     *
     * <p>创建棋盘、蛇身（初始3节，从中心向左排列）和第一颗食物。</p>
     */
    public SnakeGame() {
        random = new Random();
        board = new int[ROWS][COLS];
        snake = new ArrayList<>();
        direction = Direction.RIGHT;
        nextDirection = Direction.RIGHT;
        gameOver = false;
        score = 0;
        speed = 200;
        initGame();
    }

    /**
     * 初始化/重置游戏状态。
     *
     * <p>清空棋盘，将蛇放置在棋盘中央（3节，朝右），
     * 重置方向、得分和游戏结束标志，然后生成第一颗食物。</p>
     */
    private void initGame() {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                board[y][x] = 0;
            }
        }

        // 蛇初始位置：从中心向左排列3节，朝右移动
        snake.clear();
        int startX = COLS / 2;
        int startY = ROWS / 2;
        snake.add(new int[]{startX, startY});
        snake.add(new int[]{startX - 1, startY});
        snake.add(new int[]{startX - 2, startY});

        placeFood();

        direction = Direction.RIGHT;
        nextDirection = Direction.RIGHT;
        gameOver = false;
        score = 0;
    }

    /**
     * 在棋盘上随机放置一颗食物。
     *
     * <p>随机选择坐标，如果与蛇身重叠则重新选择，
     * 直到找到空位。食物在 board 数组中标记为 -1。</p>
     */
    private void placeFood() {
        int x, y;
        do {
            x = random.nextInt(COLS);
            y = random.nextInt(ROWS);
        } while (isSnakeCell(x, y));
        food = new int[]{x, y};
        board[y][x] = -1;
    }

    /**
     * 判断指定坐标是否被蛇身占据。
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return 如果该位置有蛇身则返回 true
     */
    private boolean isSnakeCell(int x, int y) {
        for (int[] segment : snake) {
            if (segment[0] == x && segment[1] == y) return true;
        }
        return false;
    }

    /**
     * 执行一次蛇的移动。
     *
     * <p>移动逻辑：</p>
     * <ol>
     *   <li>将 nextDirection 应用为当前方向</li>
     *   <li>根据方向计算新蛇头位置</li>
     *   <li>边界检测：超出棋盘则游戏结束</li>
     *   <li>自身碰撞检测：蛇头与蛇身（排除尾部，因为尾部会移走）重叠则游戏结束</li>
     *   <li>将新蛇头插入列表头部</li>
     *   <li>如果吃到食物：加分并生成新食物（蛇变长，不移除尾部）</li>
     *   <li>如果未吃到食物：移除尾部（蛇保持长度不变）</li>
     * </ol>
     *
     * @return 移动成功返回 true，游戏结束返回 false
     */
    public boolean move() {
        direction = nextDirection;

        int[] head = snake.get(0);
        int newX = head[0];
        int newY = head[1];

        // 根据方向计算新蛇头坐标
        switch (direction) {
            case UP: newY--; break;
            case DOWN: newY++; break;
            case LEFT: newX--; break;
            case RIGHT: newX++; break;
        }

        // 边界碰撞检测
        if (newX < 0 || newX >= COLS || newY < 0 || newY >= ROWS) {
            gameOver = true;
            return false;
        }

        // 自身碰撞检测：检查除尾部以外的蛇身（因为尾部在本次移动中会移走）
        for (int i = 0; i < snake.size() - 1; i++) {
            if (snake.get(i)[0] == newX && snake.get(i)[1] == newY) {
                gameOver = true;
                return false;
            }
        }

        // 在列表头部插入新蛇头
        int[] newHead = new int[]{newX, newY};
        snake.add(0, newHead);

        // 判断是否吃到食物
        if (newX == food[0] && newY == food[1]) {
            score += 10;
            placeFood();
        } else {
            // 未吃到食物：移除尾部，蛇保持原长度
            int[] tail = snake.remove(snake.size() - 1);
            board[tail[1]][tail[0]] = 0;
        }

        return true;
    }

    /**
     * 设置蛇的移动方向。
     *
     * <p>防止180度反向移动（例如正在向右时不能直接向左），
     * 只有与当前方向不相反时才更新 nextDirection。</p>
     *
     * @param newDir 期望的新方向
     */
    public void setDirection(Direction newDir) {
        if ((direction == Direction.UP && newDir != Direction.DOWN) ||
            (direction == Direction.DOWN && newDir != Direction.UP) ||
            (direction == Direction.LEFT && newDir != Direction.RIGHT) ||
            (direction == Direction.RIGHT && newDir != Direction.LEFT)) {
            nextDirection = newDir;
        }
    }

    /**
     * 获取棋盘二维数组。
     *
     * @return 棋盘数组，0=空，-1=食物
     */
    public int[][] getBoard() { return board; }

    /**
     * 获取蛇身坐标列表。
     *
     * @return 蛇身列表，索引0为蛇头，每个元素为 [x, y]
     */
    public List<int[]> getSnake() { return snake; }

    /**
     * 获取食物坐标。
     *
     * @return 食物坐标 [x, y]
     */
    public int[] getFood() { return food; }

    /**
     * 判断游戏是否结束。
     *
     * @return 游戏结束返回 true
     */
    public boolean isGameOver() { return gameOver; }

    /**
     * 获取当前得分。
     *
     * @return 得分值
     */
    public int getScore() { return score; }

    /**
     * 获取当前游戏刷新间隔。
     *
     * @return 刷新间隔（毫秒）
     */
    public int getSpeed() { return speed; }

    /**
     * 获取当前移动方向。
     *
     * @return 当前方向
     */
    public Direction getDirection() { return direction; }

    /**
     * 重置游戏状态到初始值。
     */
    public void reset() {
        initGame();
    }

    /**
     * 加速游戏（减少刷新间隔）。
     *
     * <p>每次调用减少5ms，最低不低于80ms。</p>
     */
    public void increaseSpeed() {
        if (speed > 80) {
            speed -= 5;
        }
    }
}

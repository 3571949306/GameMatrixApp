package com.gamecenter.app.snake;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇游戏逻辑（纯状态，不依赖 Android UI）。
 *
 * <p>从宿主 SnakeView 中提取的核心游戏逻辑：蛇身坐标、食物、方向、
 * 碰撞检测与分数计算。渲染由 {@link SnakeView} 负责。</p>
 */
public class SnakeGame {

    public static final int GRID_COLS = 20;
    public static final int GRID_ROWS = 30;

    public static final int DIR_UP = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_DOWN = 2;
    public static final int DIR_LEFT = 3;

    /** tick 结果 */
    public static final int TICK_MOVED = 0;
    public static final int TICK_ATE = 1;
    public static final int TICK_DIED = 2;

    private final List<Point> snake = new ArrayList<>();
    private Point food;
    private int direction = DIR_RIGHT;
    private int nextDirection = DIR_RIGHT;
    private int score = 0;
    private boolean gameOver = false;
    private boolean running = false;
    private final Random random = new Random();

    public void reset() {
        snake.clear();
        score = 0;
        direction = DIR_RIGHT;
        nextDirection = DIR_RIGHT;
        gameOver = false;
        running = true;

        int startX = GRID_COLS / 2;
        int startY = GRID_ROWS / 2;
        snake.add(new Point(startX, startY));
        snake.add(new Point(startX - 1, startY));
        snake.add(new Point(startX - 2, startY));

        spawnFood();
    }

    public void setNextDirection(int dir) {
        // 禁止 180 度反转
        if (dir == DIR_UP && direction != DIR_DOWN) nextDirection = DIR_UP;
        else if (dir == DIR_DOWN && direction != DIR_UP) nextDirection = DIR_DOWN;
        else if (dir == DIR_LEFT && direction != DIR_RIGHT) nextDirection = DIR_LEFT;
        else if (dir == DIR_RIGHT && direction != DIR_LEFT) nextDirection = DIR_RIGHT;
    }

    /**
     * 推进一步，返回 {@link #TICK_MOVED}/{@link #TICK_ATE}/{@link #TICK_DIED}。
     */
    public int tick() {
        if (!running || gameOver) return TICK_MOVED;
        direction = nextDirection;

        Point head = snake.get(0);
        int newX = head.x;
        int newY = head.y;
        switch (direction) {
            case DIR_UP:    newY--; break;
            case DIR_DOWN:  newY++; break;
            case DIR_LEFT:  newX--; break;
            case DIR_RIGHT: newX++; break;
        }

        if (newX < 0 || newX >= GRID_COLS || newY < 0 || newY >= GRID_ROWS) {
            onGameOver();
            return TICK_DIED;
        }

        Point newHead = new Point(newX, newY);
        for (Point segment : snake) {
            if (segment.equals(newHead.x, newHead.y)) {
                onGameOver();
                return TICK_DIED;
            }
        }

        snake.add(0, newHead);

        if (newHead.equals(food.x, food.y)) {
            score += 10;
            spawnFood();
            return TICK_ATE;
        } else {
            snake.remove(snake.size() - 1);
            return TICK_MOVED;
        }
    }

    private void onGameOver() {
        gameOver = true;
        running = false;
    }

    private void spawnFood() {
        List<Point> emptyCells = new ArrayList<>();
        for (int x = 0; x < GRID_COLS; x++) {
            for (int y = 0; y < GRID_ROWS; y++) {
                boolean occupied = false;
                for (Point segment : snake) {
                    if (segment.x == x && segment.y == y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    emptyCells.add(new Point(x, y));
                }
            }
        }
        if (!emptyCells.isEmpty()) {
            food = emptyCells.get(random.nextInt(emptyCells.size()));
        }
    }

    public List<Point> getSnake() { return snake; }
    public Point getFood() { return food; }
    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }
    public boolean isRunning() { return running; }

    public void pause() { running = false; }
    public void resume() { if (!gameOver) running = true; }
    public void stop() { running = false; }
}

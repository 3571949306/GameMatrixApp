package com.gamecenter.app.games.snake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnakeGame {

    public enum Direction { UP, DOWN, LEFT, RIGHT }

    public static final int COLS = 20;
    public static final int ROWS = 20;

    private int[][] board;
    private List<int[]> snake;
    private int[] food;
    private Direction direction;
    private Direction nextDirection;
    private boolean gameOver;
    private int score;
    private int speed;
    private Random random;

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

    private void initGame() {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                board[y][x] = 0;
            }
        }

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

    private void placeFood() {
        int x, y;
        do {
            x = random.nextInt(COLS);
            y = random.nextInt(ROWS);
        } while (isSnakeCell(x, y));
        food = new int[]{x, y};
        board[y][x] = -1;
    }

    private boolean isSnakeCell(int x, int y) {
        for (int[] segment : snake) {
            if (segment[0] == x && segment[1] == y) return true;
        }
        return false;
    }

    public boolean move() {
        direction = nextDirection;

        int[] head = snake.get(0);
        int newX = head[0];
        int newY = head[1];

        switch (direction) {
            case UP: newY--; break;
            case DOWN: newY++; break;
            case LEFT: newX--; break;
            case RIGHT: newX++; break;
        }

        if (newX < 0 || newX >= COLS || newY < 0 || newY >= ROWS) {
            gameOver = true;
            return false;
        }

        for (int i = 0; i < snake.size() - 1; i++) {
            if (snake.get(i)[0] == newX && snake.get(i)[1] == newY) {
                gameOver = true;
                return false;
            }
        }

        int[] newHead = new int[]{newX, newY};
        snake.add(0, newHead);

        if (newX == food[0] && newY == food[1]) {
            score += 10;
            placeFood();
        } else {
            int[] tail = snake.remove(snake.size() - 1);
            board[tail[1]][tail[0]] = 0;
        }

        return true;
    }

    public void setDirection(Direction newDir) {
        if ((direction == Direction.UP && newDir != Direction.DOWN) ||
            (direction == Direction.DOWN && newDir != Direction.UP) ||
            (direction == Direction.LEFT && newDir != Direction.RIGHT) ||
            (direction == Direction.RIGHT && newDir != Direction.LEFT)) {
            nextDirection = newDir;
        }
    }

    public int[][] getBoard() { return board; }
    public List<int[]> getSnake() { return snake; }
    public int[] getFood() { return food; }
    public boolean isGameOver() { return gameOver; }
    public int getScore() { return score; }
    public int getSpeed() { return speed; }
    public Direction getDirection() { return direction; }

    public void reset() {
        initGame();
    }

    public void increaseSpeed() {
        if (speed > 80) {
            speed -= 5;
        }
    }
}
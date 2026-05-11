package com.gamecenter.app.games.pipeline;

import java.util.Random;

public class PipelineGame {
    public static final int EMPTY = 0;
    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;
    public static final int CORNER_TL = 3;
    public static final int CORNER_TR = 4;
    public static final int CORNER_BR = 5;
    public static final int CORNER_BL = 6;
    public static final int CROSS = 7;
    public static final int T_UP = 8;
    public static final int T_RIGHT = 9;
    public static final int T_DOWN = 10;
    public static final int T_LEFT = 11;

    private int size = 5;
    private int[][] grid;
    private boolean[][] water;
    private int sourceX, sourceY;
    private int destX, destY;
    private Random random;

    public PipelineGame() {
        random = new Random();
        generateLevel();
    }

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

        sourceX = 0;
        sourceY = random.nextInt(size);
        destX = size - 1;
        destY = random.nextInt(size);

        generatePath();
        fillRemainingCells();
    }

    private void generatePath() {
        int x = sourceX;
        int y = sourceY;
        grid[y][x] = VERTICAL;

        while (x < destX || y != destY) {
            boolean goRight = x < destX && (y == destY || random.nextBoolean());
            boolean goDown = y < destY && !goRight;
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
                x++;
            }
        }
    }

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

    private void fillRemainingCells() {
        int[] pipes = {HORIZONTAL, VERTICAL, CORNER_TL, CORNER_TR, CORNER_BR, CORNER_BL};
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (grid[y][x] == EMPTY) {
                    grid[y][x] = pipes[random.nextInt(pipes.length)];
                }
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rotations = random.nextInt(4);
                for (int i = 0; i < rotations; i++) {
                    grid[y][x] = rotatePipeType(grid[y][x]);
                }
            }
        }
    }

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

    public void rotatePipe(int x, int y) {
        if (x >= 0 && x < size && y >= 0 && y < size) {
            grid[y][x] = rotatePipeType(grid[y][x]);
            water[y][x] = false;
        }
    }

    public boolean checkWaterFlow() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                water[y][x] = false;
            }
        }

        flowFromSource(sourceX, sourceY);
        return water[destY][destX];
    }

    private void flowFromSource(int x, int y) {
        if (x < 0 || x >= size || y < 0 || y >= size) return;
        if (water[y][x]) return;

        int pipe = grid[y][x];
        if (pipe == EMPTY) return;

        if (x == sourceX && y == sourceY) {
            water[y][x] = true;
        } else {
            boolean canReceive = false;
            if (x > 0 && water[y][x - 1]) canReceive = canConnectTo(grid[y][x - 1], x - 1, y, x, y);
            if (x < size - 1 && !canReceive && water[y][x + 1]) canReceive = canConnectTo(grid[y][x + 1], x + 1, y, x, y);
            if (y > 0 && !canReceive && water[y - 1][x]) canReceive = canConnectTo(grid[y - 1][x], x, y - 1, x, y);
            if (y < size - 1 && !canReceive && water[y + 1][x]) canReceive = canConnectTo(grid[y + 1][x], x, y + 1, x, y);

            if (canReceive) {
                water[y][x] = true;
            }
        }

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

    public int getPipe(int x, int y) {
        return grid[y][x];
    }

    public boolean hasWater(int x, int y) {
        return water[y][x];
    }

    public int getSize() {
        return size;
    }

    public void reset() {
        generateLevel();
    }
}
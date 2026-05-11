package com.gamecenter.app.games.tetris;

import java.util.Random;

public class TetrisGame {

    public static final int COLS = 10;
    public static final int ROWS = 20;

    public static final int[][][] TETROMINOS = {
            {{1, 1, 1, 1}},
            {{1, 1}, {1, 1}},
            {{1, 1, 1}, {0, 1, 0}},
            {{1, 1, 1}, {1, 0, 0}},
            {{1, 1, 1}, {0, 0, 1}},
            {{0, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {0, 1, 1}}
    };

    public static final int[][] COLORS = {
            {0, 255, 255},
            {255, 255, 0},
            {170, 0, 255},
            {0, 255, 0},
            {255, 0, 0},
            {0, 0, 255},
            {255, 165, 0}
    };

    private int[][] board;
    private int[][] currentPiece;
    private int currentPieceType;
    private int currentColor;
    private int currentX;
    private int currentY;
    private int nextPieceType;
    private int nextColor;
    private boolean gameOver;
    private int score;
    private int level;
    private int linesCleared;
    private Random random;
    private int dropInterval;

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

    private void spawnPiece() {
        currentPieceType = nextPieceType;
        currentColor = nextColor;

        currentPieceType = random.nextInt(TETROMINOS.length);
        currentColor = random.nextInt(COLORS.length);
        nextPieceType = random.nextInt(TETROMINOS.length);
        nextColor = random.nextInt(COLORS.length);

        currentPiece = TETROMINOS[currentPieceType];
        currentX = COLS / 2 - currentPiece[0].length / 2;
        currentY = 0;

        if (!isValidPosition(currentX, currentY, currentPiece)) {
            gameOver = true;
        }
    }

    public boolean isValidPosition(int x, int y, int[][] piece) {
        for (int row = 0; row < piece.length; row++) {
            for (int col = 0; col < piece[row].length; col++) {
                if (piece[row][col] != 0) {
                    int newX = x + col;
                    int newY = y + row;
                    if (newX < 0 || newX >= COLS || newY >= ROWS) {
                        return false;
                    }
                    if (newY >= 0 && board[newY][newX] != -1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean moveDown() {
        if (gameOver) return false;

        if (isValidPosition(currentX, currentY + 1, currentPiece)) {
            currentY++;
            return true;
        } else {
            lockPiece();
            clearLines();
            spawnPiece();
            return false;
        }
    }

    public void moveLeft() {
        if (gameOver) return;
        if (isValidPosition(currentX - 1, currentY, currentPiece)) {
            currentX--;
        }
    }

    public void moveRight() {
        if (gameOver) return;
        if (isValidPosition(currentX + 1, currentY, currentPiece)) {
            currentX++;
        }
    }

    public void rotate() {
        if (gameOver) return;

        int[][] rotated = rotatePiece(currentPiece);
        if (isValidPosition(currentX, currentY, rotated)) {
            currentPiece = rotated;
        } else if (isValidPosition(currentX - 1, currentY, rotated)) {
            currentX--;
            currentPiece = rotated;
        } else if (isValidPosition(currentX + 1, currentY, rotated)) {
            currentX++;
            currentPiece = rotated;
        }
    }

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
                for (int r = row; r > 0; r--) {
                    board[r] = board[r - 1].clone();
                }
                board[0] = new int[COLS];
                for (int i = 0; i < COLS; i++) board[0][i] = -1;
                lines++;
                row++;
            }
        }

        if (lines > 0) {
            linesCleared += lines;
            score += lines * lines * 100 * level;

            int newLevel = linesCleared / 10 + 1;
            if (newLevel > level) {
                level = newLevel;
                dropInterval = Math.max(100, 800 - (level - 1) * 80);
            }
        }
    }

    public int[][] getBoard() {
        return board;
    }

    public int[][] getCurrentPiece() {
        return currentPiece;
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public int getCurrentX() {
        return currentX;
    }

    public int getCurrentY() {
        return currentY;
    }

    public int getNextPieceType() {
        return nextPieceType;
    }

    public int getNextColor() {
        return nextColor;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getScore() {
        return score;
    }

    public int getLevel() {
        return level;
    }

    public int getLinesCleared() {
        return linesCleared;
    }

    public int getDropInterval() {
        return dropInterval;
    }

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
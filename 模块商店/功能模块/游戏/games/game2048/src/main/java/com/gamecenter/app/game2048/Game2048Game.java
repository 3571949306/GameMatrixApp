package com.gamecenter.app.game2048;

import java.util.Random;

public class Game2048Game {
    private int[][] board;
    private int score;
    private boolean gameOver;
    private Random random;

    public Game2048Game() {
        random = new Random();
        reset();
    }

    public void reset() {
        board = new int[4][4];
        score = 0;
        gameOver = false;
        addRandomTile();
        addRandomTile();
    }

    public int getTile(int x, int y) {
        return board[y][x];
    }

    public int getScore() {
        return score;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int[][] getBoardSnapshot() {
        int[][] copy = new int[4][4];
        for (int y = 0; y < 4; y++) {
            System.arraycopy(board[y], 0, copy[y], 0, 4);
        }
        return copy;
    }

    public void restoreState(int[][] savedBoard, int savedScore, boolean savedGameOver) {
        board = savedBoard;
        score = savedScore;
        gameOver = savedGameOver;
        random = new Random();
    }

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
            board[tile[1]][tile[0]] = random.nextDouble() < 0.9 ? 2 : 4;
        }
    }

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

    private int[] reverse(int[] row) {
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            result[i] = row[3 - i];
        }
        return result;
    }

    private int[][] transpose(int[][] board) {
        int[][] result = new int[4][4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                result[x][y] = board[y][x];
            }
        }
        return result;
    }

    private boolean boardsEqual(int[][] a, int[][] b) {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (a[y][x] != b[y][x]) return false;
            }
        }
        return true;
    }

    private int[][] copyBoard() {
        int[][] copy = new int[4][4];
        for (int y = 0; y < 4; y++) {
            System.arraycopy(board[y], 0, copy[y], 0, 4);
        }
        return copy;
    }

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

    private void checkGameOver() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (board[y][x] == 0) return;
                if (x < 3 && board[y][x] == board[y][x + 1]) return;
                if (y < 3 && board[y][x] == board[y + 1][x]) return;
            }
        }
        gameOver = true;
    }
}

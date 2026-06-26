package com.gamecenter.app.games.go;

import java.util.ArrayList;
import java.util.List;

public class GoGame {
    public static final int BOARD_SIZE = 9;
    public static final int EMPTY = 0; // 0 for EMPTY
    public static final int BLACK = 1; // 1 for BLACK
    public static final int WHITE = 2; // 2 for WHITE
    public static final int PASS_MOVE = -1;
    public static final int MAX_CONSECUTIVE_PASSES = 2;
    public static final float KOMI = 6.5f;

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int[][] previousBoard = null;
    private int currentPlayer = BLACK;
    private int capturedByBlack = 0;
    private int capturedByWhite = 0;
    private int consecutivePasses = 0;
    private boolean gameOver = false;

    public void startNewGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        previousBoard = null;
        currentPlayer = BLACK;
        capturedByBlack = 0;
        capturedByWhite = 0;
        consecutivePasses = 0;
        gameOver = false;
    }

    public int[][] getBoard() {
        return board;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int getCapturedByBlack() {
        return capturedByBlack;
    }

    public int getCapturedByWhite() {
        return capturedByWhite;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public int getConsecutivePasses() {
        return consecutivePasses;
    }

    public boolean playMove(int row, int col) {
        if (gameOver) return false;
        if (board[row][col] != EMPTY) return false;
        if (!isValidMove(row, col, currentPlayer)) return false;

        previousBoard = copyBoard(board);
        board[row][col] = currentPlayer;
        consecutivePasses = 0;

        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;
        int captured = removeCapturedStones(opponent, row, col);
        
        if (currentPlayer == BLACK) {
            capturedByBlack += captured;
        } else {
            capturedByWhite += captured;
        }

        switchPlayer();
        return true;
    }

    public void passMove() {
        if (gameOver) return;
        consecutivePasses++;
        if (consecutivePasses >= MAX_CONSECUTIVE_PASSES) {
            gameOver = true;
            return;
        }
        switchPlayer();
    }

    private void switchPlayer() {
        currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
    }

    public boolean isValidMove(int row, int col, int color) {
        if (board[row][col] != EMPTY) return false;
        int[][] simulated = copyBoard(board);
        simulated[row][col] = color;
        int opponent = color == BLACK ? WHITE : BLACK;
        int captured = simulateCapture(simulated, opponent, row, col);
        if (countLiberties(simulated, row, col) == 0 && captured == 0) {
            return false;
        }
        if (previousBoard != null && boardsEqual(simulated, previousBoard)) {
            return false;
        }
        return true;
    }

    public static boolean isValidMove(int[][] state, int row, int col, int color) {
        if (state[row][col] != EMPTY) return false;
        int[][] simulated = copyBoard(state);
        simulated[row][col] = color;
        int opponent = color == BLACK ? WHITE : BLACK;
        int captured = simulateCapture(simulated, opponent, row, col);
        if (countLiberties(simulated, row, col) == 0 && captured == 0) return false;
        return true;
    }

    public static int simulateCapture(int[][] sim, int opponent, int row, int col) {
        int totalCaptured = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && sim[nr][nc] == opponent) {
                if (countLiberties(sim, nr, nc) == 0) {
                    totalCaptured += removeGroup(sim, nr, nc);
                }
            }
        }
        return totalCaptured;
    }

    private int removeCapturedStones(int opponent, int row, int col) {
        int totalCaptured = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] == opponent) {
                if (countLiberties(board, nr, nc) == 0) {
                    totalCaptured += removeGroup(board, nr, nc);
                }
            }
        }
        return totalCaptured;
    }

    public static int countLiberties(int[][] grid, int row, int col) {
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        int[] liberties = {0};
        countLibertiesDFS(grid, row, col, grid[row][col], visited, liberties);
        return liberties[0];
    }

    private static void countLibertiesDFS(int[][] grid, int row, int col, int color, boolean[][] visited, int[] liberties) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
        if (visited[row][col]) return;
        if (grid[row][col] != color && grid[row][col] != EMPTY) return;
        if (grid[row][col] == EMPTY) {
            liberties[0]++;
            return;
        }
        visited[row][col] = true;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            countLibertiesDFS(grid, row + d[0], col + d[1], color, visited, liberties);
        }
    }

    public static int removeGroup(int[][] grid, int row, int col) {
        int color = grid[row][col];
        if (color == EMPTY) return 0;
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        return removeGroupDFS(grid, row, col, color, visited);
    }

    private static int removeGroupDFS(int[][] grid, int row, int col, int color, boolean[][] visited) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return 0;
        if (visited[row][col] || grid[row][col] != color) return 0;
        visited[row][col] = true;
        grid[row][col] = EMPTY;
        int count = 1;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            count += removeGroupDFS(grid, row + d[0], col + d[1], color, visited);
        }
        return count;
    }

    public int countTerritory(int color) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color) count++;
            }
        }
        return count;
    }

    public float[][] calculateTerritory() {
        float[][] territory = new float[BOARD_SIZE][BOARD_SIZE];
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == EMPTY && !visited[r][c]) {
                    List<int[]> region = new ArrayList<>();
                    int borderBlack = 0;
                    int borderWhite = 0;
                    floodFill(r, c, visited, region);

                    for (int[] cell : region) {
                        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                        for (int[] d : dirs) {
                            int nr = cell[0] + d[0];
                            int nc = cell[1] + d[1];
                            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE) {
                                if (board[nr][nc] == BLACK) borderBlack = 1;
                                if (board[nr][nc] == WHITE) borderWhite = 1;
                            }
                        }
                    }

                    float owner = 0;
                    if (borderBlack > 0 && borderWhite == 0) owner = -1;
                    if (borderWhite > 0 && borderBlack == 0) owner = 1;

                    for (int[] cell : region) {
                        territory[cell[0]][cell[1]] = owner;
                    }
                }
            }
        }
        return territory;
    }

    private void floodFill(int r, int c, boolean[][] visited, List<int[]> region) {
        if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) return;
        if (visited[r][c] || board[r][c] != EMPTY) return;
        visited[r][c] = true;
        region.add(new int[]{r, c});
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            floodFill(r + d[0], c + d[1], visited, region);
        }
    }

    public static int[][] copyBoard(int[][] src) {
        int[][] dst = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(src[r], 0, dst[r], 0, BOARD_SIZE);
        }
        return dst;
    }

    public static boolean boardsEqual(int[][] a, int[][] b) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (a[r][c] != b[r][c]) return false;
            }
        }
        return true;
    }
}

package com.gamecenter.app.sudoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 数独游戏逻辑（从 SudokuActivity 提取）。
 *
 * <p>负责生成完整解、对称挖洞（带唯一解验证）、数字输入校验与完成检测。
 * 不包含成就系统与 UI 逻辑（模块化后仅保留基本游戏功能）。</p>
 */
public class SudokuGame {

    public static final int GRID_SIZE = 9;
    public static final int BOX_SIZE = 3;

    /** 难度挖洞数：简单30，中等40，困难50，专家60 */
    public static final int[] HOLE_COUNTS = {30, 40, 50, 60};
    public static final String[] DIFFICULTY_NAMES = {"简单", "中等", "困难", "专家"};

    private static final int MAX_DIG_ATTEMPTS = 200;
    private static final int SOLVER_NODE_LIMIT = 5000;
    private static final int UNIQUE_SOLUTION_LIMIT = 2;

    private int[][] solution = new int[GRID_SIZE][GRID_SIZE];
    private int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean[][] isGiven = new boolean[GRID_SIZE][GRID_SIZE];
    private int currentDifficultyIndex = 0;
    private int hintsUsed = 0;
    private final Random random = new Random();

    public int[][] getBoard() {
        return board;
    }

    public boolean[][] getIsGiven() {
        return isGiven;
    }

    public int getCurrentDifficultyIndex() {
        return currentDifficultyIndex;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    /**
     * 按难度开始新游戏：生成完整解并挖洞。
     */
    public void startNewGame(int difficultyIndex) {
        currentDifficultyIndex = difficultyIndex;
        hintsUsed = 0;
        generateSolution();
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                board[r][c] = solution[r][c];
                isGiven[r][c] = true;
            }
        }
        digHoles(board, HOLE_COUNTS[difficultyIndex]);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                isGiven[r][c] = board[r][c] != 0;
            }
        }
    }

    /**
     * 输入数字到指定格子。返回是否触发完成。
     *
     * @return true 表示填入后棋盘完成
     */
    public boolean inputNumber(int row, int col, int num) {
        if (isGiven[row][col]) return false;
        board[row][col] = num;
        return num != 0 && isValidPlacement(board, row, col, num) && isBoardComplete();
    }

    /**
     * 检查输入是否冲突。
     */
    public boolean hasConflict(int row, int col, int num) {
        return num != 0 && !isValidPlacement(board, row, col, num);
    }

    /**
     * 对指定格子使用提示（填入正解）。
     *
     * @return true 表示填入后棋盘完成
     */
    public boolean useHint(int row, int col) {
        if (isGiven[row][col]) return false;
        if (solution[row][col] == 0) return false;
        board[row][col] = solution[row][col];
        isGiven[row][col] = true;
        hintsUsed++;
        return isBoardComplete();
    }

    private void generateSolution() {
        solution = new int[GRID_SIZE][GRID_SIZE];
        fillBoard(solution);
    }

    private boolean fillBoard(int[][] grid) {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] == 0) {
                    List<Integer> nums = new ArrayList<>();
                    for (int n = 1; n <= 9; n++) nums.add(n);
                    Collections.shuffle(nums, random);
                    for (int num : nums) {
                        if (isValidPlacement(grid, r, c, num)) {
                            grid[r][c] = num;
                            if (fillBoard(grid)) return true;
                            grid[r][c] = 0;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isValidPlacement(int[][] grid, int row, int col, int num) {
        for (int c = 0; c < GRID_SIZE; c++) {
            if (grid[row][c] == num) return false;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (grid[r][col] == num) return false;
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if (grid[r][c] == num) return false;
            }
        }
        return true;
    }

    private int countSolutions(int[][] board, int limit, int nodeLimit) {
        int[][] copy = new int[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, GRID_SIZE);
        }
        int[] count = {0};
        int[] nodes = {0};
        solveCount(copy, limit, nodeLimit, count, nodes);
        if (nodes[0] > nodeLimit) {
            return limit + 1;
        }
        return count[0];
    }

    private boolean solveCount(int[][] grid, int limit, int nodeLimit,
                               int[] count, int[] nodes) {
        if (nodes[0] > nodeLimit) return true;
        nodes[0]++;

        int row = -1;
        int col = -1;
        for (int r = 0; r < GRID_SIZE && row == -1; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] == 0) {
                    row = r;
                    col = c;
                    break;
                }
            }
        }
        if (row == -1) {
            count[0]++;
            return count[0] >= limit;
        }
        for (int num = 1; num <= 9; num++) {
            if (isValidPlacement(grid, row, col, num)) {
                grid[row][col] = num;
                if (solveCount(grid, limit, nodeLimit, count, nodes)) {
                    grid[row][col] = 0;
                    return true;
                }
                grid[row][col] = 0;
            }
        }
        return false;
    }

    private void digHoles(int[][] board, int targetHoles) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions, random);

        int dug = 0;
        int attempts = 0;
        for (int pos : positions) {
            if (dug >= targetHoles || attempts >= MAX_DIG_ATTEMPTS) break;
            attempts++;
            int r = pos / GRID_SIZE;
            int c = pos % GRID_SIZE;
            if (board[r][c] == 0) continue;

            int r2 = GRID_SIZE - 1 - r;
            int c2 = GRID_SIZE - 1 - c;
            int saved1 = board[r][c];
            int saved2 = board[r2][c2];

            board[r][c] = 0;
            boolean dugPartner = false;
            if (!(r == r2 && c == c2) && board[r2][c2] != 0) {
                board[r2][c2] = 0;
                dugPartner = true;
            }

            int solutions = countSolutions(board, UNIQUE_SOLUTION_LIMIT, SOLVER_NODE_LIMIT);
            if (solutions == 1) {
                dug++;
                if (dugPartner) dug++;
            } else {
                board[r][c] = saved1;
                if (dugPartner) board[r2][c2] = saved2;
            }
        }
    }

    private boolean isBoardComplete() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] == 0) return false;
            }
        }
        return true;
    }
}

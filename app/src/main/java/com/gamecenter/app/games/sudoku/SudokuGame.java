package com.gamecenter.app.games.sudoku;

import java.util.Random;

/**
 * 数独游戏逻辑类
 *
 * 职责：
 * - 生成有效数独题目（回溯算法）
 * - 管理 9x9 棋盘数据 board[][]（0=空）
 * - 区分固定格子（题目给定）vs 用户可填格子
 * - 记录填入错误（冲突检测）
 *
 * 数据结构：
 * - board[9][9] 当前棋盘，0 表示空
 * - solution[9][9] 完整解
 * - fixed[9][9] true=题目给定不可改
 * - error[9][9] true=当前值违反规则
 */
public class SudokuGame {
    private int[][] board;
    private int[][] solution;
    private boolean[][] fixed;
    private boolean[][] error;
    private Random random;
    private int selectedValue = 0;

    public SudokuGame() {
        random = new Random();
        board = new int[9][9];
        solution = new int[9][9];
        fixed = new boolean[9][9];
        error = new boolean[9][9];
        generatePuzzle();
    }

    /**
     * 生成新数独题目
     * 1. 用回溯法填充完整解
     * 2. 随机移除 40-50 个格子作为题目
     */
    public void generatePuzzle() {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = 0;
                fixed[i][j] = false;
                error[i][j] = false;
            }
        }
        fillBoard(board);
        copyBoard(board, solution);

        int cellsToRemove = 40 + random.nextInt(11);
        for (int i = 0; i < cellsToRemove; i++) {
            int x = random.nextInt(9);
            int y = random.nextInt(9);
            if (!fixed[y][x]) {
                board[y][x] = 0;
                fixed[y][x] = false;
            } else {
                i--;
            }
        }
    }

    /**
     * 回溯法填充数独
     * @return 是否成功填充
     */
    private boolean fillBoard(int[][] grid) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (grid[row][col] == 0) {
                    int[] nums = {1, 2, 3, 4, 5, 6, 7, 8, 9};
                    shuffleArray(nums);
                    for (int num : nums) {
                        if (isValidPlacement(grid, row, col, num)) {
                            grid[row][col] = num;
                            if (fillBoard(grid)) {
                                return true;
                            }
                            grid[row][col] = 0;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 检查在 (row, col) 放入 num 是否合法
     * 规则：同行、同列、同宫（3x3）无重复
     */
    private boolean isValidPlacement(int[][] grid, int row, int col, int num) {
        for (int i = 0; i < 9; i++) {
            if (grid[row][i] == num) return false;
        }
        for (int i = 0; i < 9; i++) {
            if (grid[i][col] == num) return false;
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (grid[boxRow + i][boxCol + j] == num) return false;
            }
        }
        return true;
    }

    /** Fisher-Yates 洗牌算法 */
    private void shuffleArray(int[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
    }

    /** 复制棋盘 src -> dest */
    private void copyBoard(int[][] src, int[][] dest) {
        for (int i = 0; i < 9; i++) {
            System.arraycopy(src[i], 0, dest[i], 0, 9);
        }
    }

    /**
     * 向 (x, y) 填入数字
     * @return 是否成功（固定格子或数字无效则失败）
     */
    public boolean setNumber(int x, int y, int number) {
        if (fixed[y][x]) return false;
        if (number < 0 || number > 9) return false;

        board[y][x] = number;
        if (number != 0) {
            error[y][x] = !isValidPlacement(board, y, x, number);
        } else {
            error[y][x] = false;
        }
        return true;
    }

    /** @return (x, y) 是否为题目预留给定 */
    public boolean isFixed(int x, int y) {
        return fixed[y][x];
    }

    public boolean isError(int x, int y) {
        return error[y][x];
    }

    public boolean[][] getErrorMatrix() {
        return error;
    }

    /** @return 当前 9x9 棋盘状态 */
    public int[][] getBoard() {
        return board;
    }

    /** @return 是否完成（所有格子填满且无错误） */
    public boolean isSolved() {
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                if (board[y][x] == 0) return false;
                if (error[y][x]) return false;
            }
        }
        return true;
    }

    /** 重新开始，生成新题目 */
    public void reset() {
        generatePuzzle();
    }

    public int getSelectedValue() {
        return selectedValue;
    }

    public void setSelectedValue(int value) {
        this.selectedValue = value;
    }

    /**
     * 方法作用：将当前游戏状态序列化为 JSON 字符串
     * @return 包含 board、solution、fixed、error 的 JSON 字符串
     * 调用时机：SudokuActivity.onPause() 中保存存档
     * 副作用：无
     */
    public String serializeState() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"board\":[");
        for (int y = 0; y < 9; y++) {
            if (y > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < 9; x++) {
                if (x > 0) sb.append(",");
                sb.append(board[y][x]);
            }
            sb.append("]");
        }
        sb.append("],\"solution\":[");
        for (int y = 0; y < 9; y++) {
            if (y > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < 9; x++) {
                if (x > 0) sb.append(",");
                sb.append(solution[y][x]);
            }
            sb.append("]");
        }
        sb.append("],\"fixed\":[");
        for (int y = 0; y < 9; y++) {
            if (y > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < 9; x++) {
                if (x > 0) sb.append(",");
                sb.append(fixed[y][x] ? 1 : 0);
            }
            sb.append("]");
        }
        sb.append("],\"error\":[");
        for (int y = 0; y < 9; y++) {
            if (y > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < 9; x++) {
                if (x > 0) sb.append(",");
                sb.append(error[y][x] ? 1 : 0);
            }
            sb.append("]");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 方法作用：从 JSON 字符串恢复游戏状态
     * @param json serializeState() 生成的 JSON 字符串
     * @return true 表示恢复成功
     * 调用时机：SudokuActivity.onCreate() 中恢复存档
     * 副作用：覆盖 board、solution、fixed、error 四个数组
     */
    public boolean restoreState(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            int[][] restoredBoard = parseInt2D(obj.getJSONArray("board"));
            int[][] restoredSolution = parseInt2D(obj.getJSONArray("solution"));
            boolean[][] restoredFixed = parseBoolean2D(obj.getJSONArray("fixed"));
            boolean[][] restoredError = parseBoolean2D(obj.getJSONArray("error"));
            if (restoredBoard == null || restoredSolution == null
                    || restoredFixed == null || restoredError == null) {
                return false;
            }
            board = restoredBoard;
            solution = restoredSolution;
            fixed = restoredFixed;
            error = restoredError;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int[][] parseInt2D(org.json.JSONArray arr2d) throws Exception {
        int[][] result = new int[9][9];
        for (int y = 0; y < 9; y++) {
            org.json.JSONArray row = arr2d.getJSONArray(y);
            for (int x = 0; x < 9; x++) {
                result[y][x] = row.getInt(x);
            }
        }
        return result;
    }

    private boolean[][] parseBoolean2D(org.json.JSONArray arr2d) throws Exception {
        boolean[][] result = new boolean[9][9];
        for (int y = 0; y < 9; y++) {
            org.json.JSONArray row = arr2d.getJSONArray(y);
            for (int x = 0; x < 9; x++) {
                result[y][x] = row.getInt(x) == 1;
            }
        }
        return result;
    }
}
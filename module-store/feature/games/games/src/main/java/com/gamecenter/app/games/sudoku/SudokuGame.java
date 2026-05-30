package com.gamecenter.app.games.sudoku;

import java.util.Random;

/**
 * 数独游戏逻辑类
 *
 * <p>职责：</p>
 * <ul>
 *   <li>生成有效数独题目（回溯算法）</li>
 *   <li>管理 9x9 棋盘数据 board[][]（0=空）</li>
 *   <li>区分固定格子（题目给定）vs 用户可填格子</li>
 *   <li>记录填入错误（冲突检测）</li>
 *   <li>支持游戏状态的序列化/反序列化（存档功能）</li>
 * </ul>
 *
 * <p>数据结构：</p>
 * <ul>
 *   <li>board[9][9] 当前棋盘，0 表示空</li>
 *   <li>solution[9][9] 完整解</li>
 *   <li>fixed[9][9] true=题目给定不可改</li>
 *   <li>error[9][9] true=当前值违反规则</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用回溯法 + Fisher-Yates 洗牌生成随机数独，保证每次题目不同</li>
 *   <li>移除 40-50 个格子作为题目难度，剩余格子为固定提示</li>
 *   <li>错误检测基于实时冲突检查（同行/同列/同宫重复），不依赖与 solution 对比</li>
 * </ul>
 */
public class SudokuGame {
    /** 当前棋盘状态，0 表示空格 */
    private int[][] board;

    /** 完整解答，用于验证和参考 */
    private int[][] solution;

    /** 固定格子标记，true 表示题目给定不可修改 */
    private boolean[][] fixed;

    /** 错误标记，true 表示当前值违反数独规则 */
    private boolean[][] error;

    /** 随机数生成器，用于题目生成和洗牌 */
    private Random random;

    /** 当前选中的数字值（用于高亮相同数字） */
    private int selectedValue = 0;

    /**
     * 构造方法：初始化所有数组并生成新题目
     */
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
     *
     * <p>生成流程：</p>
     * <ol>
     *   <li>清空棋盘和标记数组</li>
     *   <li>用回溯法填充完整解（fillBoard）</li>
     *   <li>复制完整解到 solution 数组</li>
     *   <li>随机移除 40-50 个格子作为题目</li>
     * </ol>
     *
     * <p>注意：移除格子时若选中了已移除的位置，会重试（i--），
     * 确保实际移除数量达到目标。</p>
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
     *
     * <p>算法流程：</p>
     * <ol>
     *   <li>遍历每个格子，找到第一个空格（值为0）</li>
     *   <li>将1-9随机洗牌后依次尝试填入</li>
     *   <li>若填入合法则递归填充下一个空格</li>
     *   <li>若递归失败则回溯（置0），尝试下一个数字</li>
     *   <li>所有数字都尝试失败则返回 false，触发上层回溯</li>
     * </ol>
     *
     * @param grid 待填充的9x9网格
     * @return true 表示成功填充完整数独
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
     *
     * <p>数独规则：同行、同列、同宫（3x3）无重复。</p>
     *
     * @param grid 棋盘网格
     * @param row  行索引（0-8）
     * @param col  列索引（0-8）
     * @param num  待检查的数字（1-9）
     * @return true 表示放置合法
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

    /**
     * Fisher-Yates 洗牌算法
     *
     * <p>将数组随机打乱，保证每种排列等概率出现。
     * 用于 fillBoard 中随机化数字尝试顺序，确保每次生成不同的数独。</p>
     *
     * @param arr 待洗牌的数组
     */
    private void shuffleArray(int[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
    }

    /**
     * 复制棋盘 src -> dest
     *
     * @param src  源棋盘
     * @param dest 目标棋盘
     */
    private void copyBoard(int[][] src, int[][] dest) {
        for (int i = 0; i < 9; i++) {
            System.arraycopy(src[i], 0, dest[i], 0, 9);
        }
    }

    /**
     * 向 (x, y) 填入数字
     *
     * <p>填入后立即进行冲突检测，更新 error 标记。
     * 若填入0（清除），则清除错误标记。</p>
     *
     * @param x      列索引（0-8）
     * @param y      行索引（0-8）
     * @param number 要填入的数字（0=清除，1-9=填入）
     * @return true 表示成功；false 表示格子为固定格子或数字无效
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

    /**
     * @param x 列索引
     * @param y 行索引
     * @return 该格子是否为题目预留给定（不可修改）
     */
    public boolean isFixed(int x, int y) {
        return fixed[y][x];
    }

    /**
     * @param x 列索引
     * @param y 行索引
     * @return 该格子当前值是否违反数独规则
     */
    public boolean isError(int x, int y) {
        return error[y][x];
    }

    /**
     * @return 错误标记矩阵（9x9）
     */
    public boolean[][] getErrorMatrix() {
        return error;
    }

    /**
     * @return 当前 9x9 棋盘状态
     */
    public int[][] getBoard() {
        return board;
    }

    /**
     * 检查数独是否已完成
     *
     * <p>判定条件：所有格子非空且无错误标记。</p>
     *
     * @return true 表示数独已解完
     */
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

    /**
     * @return 当前选中的数字值（用于高亮相同数字）
     */
    public int getSelectedValue() {
        return selectedValue;
    }

    /**
     * 设置当前选中的数字值
     * @param value 选中的数字
     */
    public void setSelectedValue(int value) {
        this.selectedValue = value;
    }

    /**
     * 将当前游戏状态序列化为 JSON 字符串
     *
     * <p>序列化内容：board（当前棋盘）、solution（完整解）、
     * fixed（固定标记）、error（错误标记）。</p>
     *
     * <p>调用时机：SudokuActivity.onPause() 中保存存档。</p>
     *
     * @return 包含完整游戏状态的 JSON 字符串
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
     * 从 JSON 字符串恢复游戏状态
     *
     * <p>反序列化内容：board、solution、fixed、error 四个数组。
     * 若任一数组解析失败则返回 false，不修改当前状态。</p>
     *
     * <p>调用时机：SudokuActivity.onCreate() 中恢复存档。</p>
     *
     * @param json serializeState() 生成的 JSON 字符串
     * @return true 表示恢复成功
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

    /**
     * 解析 JSON 二维整型数组
     *
     * @param arr2d JSON 二维数组
     * @return 9x9 整型数组
     * @throws Exception JSON 解析异常
     */
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

    /**
     * 解析 JSON 二维布尔数组
     *
     * <p>JSON 中用 1/0 表示 true/false，解析时转换为布尔值。</p>
     *
     * @param arr2d JSON 二维数组
     * @return 9x9 布尔数组
     * @throws Exception JSON 解析异常
     */
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

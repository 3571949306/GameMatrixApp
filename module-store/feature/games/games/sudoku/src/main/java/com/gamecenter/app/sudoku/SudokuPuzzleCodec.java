package com.gamecenter.app.sudoku;

/**
 * 数独盘面与 81 字符关卡码之间的编解码器（纯静态，无 Android 依赖）。
 *
 * <p>关卡码格式：恰好 81 个字符，每个字符为 '0'-'9'。按行优先排列 9x9 盘面
 * （第 0 行左到右、第 1 行左到右、……直到第 8 行），'0' 表示空格，
 * '1'-'9' 表示对应给定数字。解码前允许首尾空白（内部 trim）。</p>
 */
public final class SudokuPuzzleCodec {

    private static final int CODE_LENGTH = SudokuGame.GRID_SIZE * SudokuGame.GRID_SIZE;

    private SudokuPuzzleCodec() {
    }

    /**
     * 编码 9x9 盘面为 81 字符关卡码（行优先，'0' 表示空格）。
     *
     * @param grid 待编码的 9x9 盘面，每格取值 0-9
     * @return 81 字符关卡码；结构非法（非 9x9）或值域越界返回 null
     */
    public static String encode(int[][] grid) {
        if (grid == null || grid.length != SudokuGame.GRID_SIZE) return null;
        for (int r = 0; r < SudokuGame.GRID_SIZE; r++) {
            if (grid[r] == null || grid[r].length != SudokuGame.GRID_SIZE) return null;
            for (int c = 0; c < SudokuGame.GRID_SIZE; c++) {
                int value = grid[r][c];
                if (value < 0 || value > SudokuGame.GRID_SIZE) return null;
            }
        }
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int r = 0; r < SudokuGame.GRID_SIZE; r++) {
            for (int c = 0; c < SudokuGame.GRID_SIZE; c++) {
                builder.append((char) ('0' + grid[r][c]));
            }
        }
        return builder.toString();
    }

    /**
     * 解码 81 字符关卡码为 9x9 盘面（行优先，'0' 表示空格）。
     *
     * @param code 关卡码文本，允许首尾空白
     * @return 9x9 盘面；trim 后长度不为 81 或含 '0'-'9' 之外的字符返回 null
     */
    public static int[][] decode(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.length() != CODE_LENGTH) return null;
        int[][] grid = new int[SudokuGame.GRID_SIZE][SudokuGame.GRID_SIZE];
        for (int i = 0; i < CODE_LENGTH; i++) {
            char ch = trimmed.charAt(i);
            if (ch < '0' || ch > '9') return null;
            grid[i / SudokuGame.GRID_SIZE][i % SudokuGame.GRID_SIZE] = ch - '0';
        }
        return grid;
    }
}

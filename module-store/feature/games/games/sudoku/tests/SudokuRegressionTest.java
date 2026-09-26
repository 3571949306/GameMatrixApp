import com.gamecenter.app.sudoku.SudokuGame;
import com.gamecenter.app.sudoku.SudokuGame.InputResult;
import com.gamecenter.app.sudoku.SudokuPuzzleCodec;

/**
 * 数独逻辑回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>validateCustomPuzzle 恰一解校验：完整解盘/22 空谜题返回唯一解，
 *       多解、无解、结构/值域非法一律拒绝且无副作用；</li>
 *   <li>loadCustomPuzzle 装载：自定义标记、given/board/solution 初始化、
 *       撤销重置，按唯一解逐格填入可游玩至 COMPLETED；</li>
 *   <li>{@link SudokuPuzzleCodec} 编解码 roundtrip 与非法输入拒绝；</li>
 *   <li>生成器回归（防现有功能退化）：各难度 newGame 可生成、初始无冲突、题面恰一解。</li>
 * </ul>
 *
 * <p>固定测试向量说明：{@code SOLUTION} 为标准完整解盘；{@code CLASSIC_PUZZLE}
 * 为其 51 空经典题面（公知唯一解）；{@code PUZZLE22} 的 22 个挖洞位置全部落在
 * 经典题面的挖洞集合内，给定数严格更多，故解集是经典题面解集的子集且包含原解，
 * 数学上保证恰一解，用例完全确定性。</p>
 *
 * <p>运行：见 scripts/verify_sudoku.py。</p>
 */
public class SudokuRegressionTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    static final int N = SudokuGame.GRID_SIZE;

    /** 标准数独完整解盘（固定测试向量，用例确定性的基准解）。 */
    static final int[][] SOLUTION = {
        {5, 3, 4, 6, 7, 8, 9, 1, 2},
        {6, 7, 2, 1, 9, 5, 3, 4, 8},
        {1, 9, 8, 3, 4, 2, 5, 6, 7},
        {8, 5, 9, 7, 6, 1, 4, 2, 3},
        {4, 2, 6, 8, 5, 3, 7, 9, 1},
        {7, 1, 3, 9, 2, 4, 8, 5, 6},
        {9, 6, 1, 5, 3, 7, 2, 8, 4},
        {2, 8, 7, 4, 1, 9, 6, 3, 5},
        {3, 4, 5, 2, 8, 6, 1, 7, 9},
    };

    /** SOLUTION 挖 51 空的经典题面（公知唯一解），作为测试向量的锚点。 */
    static final int[][] CLASSIC_PUZZLE = {
        {5, 3, 0, 0, 7, 0, 0, 0, 0},
        {6, 0, 0, 1, 9, 5, 0, 0, 0},
        {0, 9, 8, 0, 0, 0, 0, 6, 0},
        {8, 0, 0, 0, 6, 0, 0, 0, 3},
        {4, 0, 0, 8, 0, 3, 0, 0, 1},
        {7, 0, 0, 0, 2, 0, 0, 0, 6},
        {0, 6, 0, 0, 0, 0, 2, 8, 0},
        {0, 0, 0, 4, 1, 9, 0, 0, 5},
        {0, 0, 0, 0, 8, 0, 0, 7, 9},
    };

    /** SOLUTION 挖 22 个固定位置（均在经典题面挖洞集内）：给定 ⊇ 经典题面，必恰一解。 */
    static final int[][] PUZZLE22 = {
        {5, 3, 4, 6, 7, 8, 9, 1, 2},
        {6, 7, 2, 1, 9, 5, 3, 4, 8},
        {1, 9, 8, 3, 4, 2, 5, 6, 7},
        {8, 5, 9, 7, 6, 1, 4, 2, 3},
        {4, 2, 6, 8, 5, 3, 7, 9, 1},
        {7, 1, 0, 0, 2, 0, 0, 0, 6},
        {0, 6, 0, 0, 0, 0, 2, 8, 0},
        {0, 0, 0, 4, 1, 9, 0, 0, 5},
        {0, 0, 0, 0, 8, 0, 0, 7, 9},
    };

    // ====================================================================
    // 工具方法
    // ====================================================================

    static int[][] copyGrid(int[][] src) {
        int[][] dst = new int[src.length][];
        for (int r = 0; r < src.length; r++) dst[r] = src[r].clone();
        return dst;
    }

    static boolean gridEqual(int[][] a, int[][] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int r = 0; r < a.length; r++) {
            if (a[r].length != b[r].length) return false;
            for (int c = 0; c < a[r].length; c++) {
                if (a[r][c] != b[r][c]) return false;
            }
        }
        return true;
    }

    static int[][] withValue(int[][] src, int row, int col, int value) {
        int[][] dst = copyGrid(src);
        dst[row][col] = value;
        return dst;
    }

    static int countZeros(int[][] grid) {
        int n = 0;
        for (int[] row : grid) {
            for (int v : row) if (v == 0) n++;
        }
        return n;
    }

    static String repeat(char ch, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(ch);
        return sb.toString();
    }

    /** 仅保留 SOLUTION 中 10 个分散给定值的盘面（少于 17 个给定必然多解）。 */
    static int[][] sparseTen() {
        int[][] grid = new int[N][N];
        int[][] keep = {{0, 0}, {0, 4}, {0, 8}, {2, 2}, {2, 6}, {4, 4}, {6, 2}, {6, 6}, {8, 0}, {8, 8}};
        for (int[] rc : keep) grid[rc[0]][rc[1]] = SOLUTION[rc[0]][rc[1]];
        return grid;
    }

    /** 无解盘：同一行放两个相同数（初始给定冲突）。 */
    static int[][] unsolvableRow() {
        int[][] grid = new int[N][N];
        grid[0][0] = 1;
        grid[0][8] = 1;
        return grid;
    }

    static boolean givenMaskEqual(boolean[][] mask, int[][] puzzle) {
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (mask[r][c] != (puzzle[r][c] != 0)) return false;
            }
        }
        return true;
    }

    static boolean noConflict(SudokuGame game, int[][] board) {
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < board[r].length; c++) {
                if (board[r][c] != 0 && game.hasConflict(r, c, board[r][c])) return false;
            }
        }
        return true;
    }

    // ====================================================================
    // T1 validateCustomPuzzle 恰一解校验
    // ====================================================================
    static void testValidateUniqueSolution() {
        System.out.println("[T1] validateCustomPuzzle 恰一解校验");
        check("向量自检：经典题面 51 空", countZeros(CLASSIC_PUZZLE) == 51);
        check("向量自检：测试谜题 22 空", countZeros(PUZZLE22) == 22);
        SudokuGame g = new SudokuGame();
        int[][] solved = g.validateCustomPuzzle(SOLUTION);
        check("完整解盘非 null", solved != null);
        check("完整解盘返回解等于原盘", gridEqual(solved, SOLUTION));
        int[][] classic = g.validateCustomPuzzle(CLASSIC_PUZZLE);
        check("51 空经典题面非 null", classic != null);
        check("51 空经典题面解等于标准解", gridEqual(classic, SOLUTION));
        int[][] puzzle22 = g.validateCustomPuzzle(PUZZLE22);
        check("22 空谜题非 null", puzzle22 != null);
        check("22 空谜题解等于标准解", gridEqual(puzzle22, SOLUTION));
    }

    // ====================================================================
    // T2 拒绝场景与无副作用
    // ====================================================================
    static void testValidateRejection() {
        System.out.println("[T2] validateCustomPuzzle 拒绝与无副作用");
        SudokuGame g = new SudokuGame();
        check("前置：装载 22 空谜题成功", g.loadCustomPuzzle(PUZZLE22));
        int[][] boardBefore = g.getBoard();
        int[][] solutionBefore = g.getSolution();

        check("拒绝 null 盘", g.validateCustomPuzzle(null) == null);
        check("拒绝 8 行盘", g.validateCustomPuzzle(new int[8][9]) == null);
        check("拒绝 9 行 8 列盘", g.validateCustomPuzzle(new int[9][8]) == null);
        check("拒绝值域 17", g.validateCustomPuzzle(withValue(SOLUTION, 0, 0, 17)) == null);
        check("拒绝值域 -1", g.validateCustomPuzzle(withValue(SOLUTION, 0, 0, -1)) == null);
        check("拒绝同行冲突盘", g.validateCustomPuzzle(unsolvableRow()) == null);
        check("拒绝多解盘（仅 10 给定）", g.validateCustomPuzzle(sparseTen()) == null);
        check("拒绝全空盘", g.validateCustomPuzzle(new int[N][N]) == null);

        check("装载多解盘被拒", !g.loadCustomPuzzle(sparseTen()));
        check("拒绝后仍为自定义模式", g.isCustomPuzzle());
        check("拒绝后难度标记不变", g.getCurrentDifficultyIndex() == SudokuGame.CUSTOM_PUZZLE);
        check("拒绝后盘面不变", gridEqual(g.getBoard(), boardBefore));
        check("拒绝后解不变", gridEqual(g.getSolution(), solutionBefore));
    }

    // ====================================================================
    // T3 自定义谜题装载与游玩
    // ====================================================================
    static void testCustomLoadAndPlay() {
        System.out.println("[T3] loadCustomPuzzle 装载与游玩");
        SudokuGame g = new SudokuGame();
        check("装载 22 空唯一解谜题成功", g.loadCustomPuzzle(PUZZLE22));
        check("自定义模式标记", g.isCustomPuzzle());
        check("难度索引 == CUSTOM_PUZZLE", g.getCurrentDifficultyIndex() == SudokuGame.CUSTOM_PUZZLE);
        check("开局 started", g.isStarted());
        check("初始盘面等于谜题", gridEqual(g.getBoard(), PUZZLE22));
        check("solution 为唯一解", gridEqual(g.getSolution(), SOLUTION));
        check("给定标记与谜题非零一致", givenMaskEqual(g.getIsGiven(), PUZZLE22));
        check("无提示无失误", g.getHintsUsed() == 0 && g.getMistakes() == 0);
        check("撤销/重做栈为空", !g.canUndo() && !g.canRedo());

        check("合规错误值标记 INCORRECT", g.setValue(8, 6, 6) == InputResult.INCORRECT);
        check("错误计数为 1", g.getMistakes() == 1);
        check("撤销可用", g.canUndo());
        check("撤销成功", g.undo());
        check("撤销后错误归零", g.getMistakes() == 0);
        check("撤销后该格恢复为空", g.getValue(8, 6) == 0);
        check("给定格输入被忽略", g.setValue(0, 0, 6) == InputResult.IGNORED_GIVEN);

        InputResult last = null;
        int placed = 0;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (PUZZLE22[r][c] == 0) {
                    last = g.setValue(r, c, SOLUTION[r][c]);
                    if (last == InputResult.PLACED) placed++;
                }
            }
        }
        check("前 21 手全部 PLACED", placed == 21);
        check("最后一手 COMPLETED", last == InputResult.COMPLETED);
        check("完成判定 isBoardComplete", g.isBoardComplete());
        check("完成后盘面等于解", gridEqual(g.getBoard(), SOLUTION));
        check("全程零失误", g.getMistakes() == 0);

        g.startNewGame(0, 123L);
        check("切回内置难度非自定义", !g.isCustomPuzzle());
        check("切回内置难度索引归位", g.getCurrentDifficultyIndex() == 0);
    }

    // ====================================================================
    // T4 关卡码编解码 roundtrip 与拒绝
    // ====================================================================
    static void testCodec() {
        System.out.println("[T4] 关卡码编解码");
        String code = SudokuPuzzleCodec.encode(PUZZLE22);
        check("编码非空且 81 字符", code != null && code.length() == 81);
        check("含 0 与非 0 的 roundtrip", gridEqual(SudokuPuzzleCodec.decode(code), PUZZLE22));
        String solvedCode = SudokuPuzzleCodec.encode(SOLUTION);
        check("完整解盘编码非空", solvedCode != null);
        check("完整解盘 roundtrip", gridEqual(SudokuPuzzleCodec.decode(solvedCode), SOLUTION));
        check("首尾空白容错解码", gridEqual(SudokuPuzzleCodec.decode("  " + code + "\n"), PUZZLE22));

        check("编码拒绝 null", SudokuPuzzleCodec.encode(null) == null);
        check("编码拒绝 8 行", SudokuPuzzleCodec.encode(new int[8][9]) == null);
        check("编码拒绝 8 列", SudokuPuzzleCodec.encode(new int[9][8]) == null);
        check("编码拒绝值 17", SudokuPuzzleCodec.encode(withValue(SOLUTION, 0, 0, 17)) == null);
        check("编码拒绝值 -1", SudokuPuzzleCodec.encode(withValue(SOLUTION, 0, 0, -1)) == null);

        check("解码拒绝 null", SudokuPuzzleCodec.decode(null) == null);
        check("解码拒绝空串", SudokuPuzzleCodec.decode("") == null);
        check("解码拒绝 80 字符", SudokuPuzzleCodec.decode(repeat('7', 80)) == null);
        check("解码拒绝 82 字符", SudokuPuzzleCodec.decode(repeat('7', 82)) == null);
        check("解码拒绝含字母", SudokuPuzzleCodec.decode(repeat('0', 40) + "a" + repeat('0', 40)) == null);
        check("解码全 0 盘", gridEqual(SudokuPuzzleCodec.decode(repeat('0', 81)), new int[N][N]));
    }

    // ====================================================================
    // T5 生成器回归（防现有功能退化）
    // ====================================================================
    static void testGeneratorRegression() {
        System.out.println("[T5] 生成器回归");
        for (int difficulty = 0; difficulty < SudokuGame.HOLE_COUNTS.length; difficulty++) {
            SudokuGame g = new SudokuGame();
            g.startNewGame(difficulty, 20260923L + difficulty);
            int[][] board = g.getBoard();
            check("难度 " + difficulty + " 开局成功", g.isStarted());
            check("难度 " + difficulty + " 非自定义模式", !g.isCustomPuzzle());
            check("难度 " + difficulty + " 难度索引一致", g.getCurrentDifficultyIndex() == difficulty);
            check("难度 " + difficulty + " 初始无冲突", noConflict(g, board));
            check("难度 " + difficulty + " 生成题面恰一解", g.validateCustomPuzzle(board) != null);
        }
    }

    public static void main(String[] args) {
        System.out.println("==== 数独逻辑回归测试 ====");
        testValidateUniqueSolution();
        testValidateRejection();
        testCustomLoadAndPlay();
        testCodec();
        testGeneratorRegression();
        System.out.println("================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed == 0) {
            System.out.println("结论：全部通过，自定义谜题校验/装载/编解码均符合契约。");
        } else {
            System.out.println("结论：存在失败用例，禁止交付。");
        }
        System.out.println("SUDOKU_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
    }
}

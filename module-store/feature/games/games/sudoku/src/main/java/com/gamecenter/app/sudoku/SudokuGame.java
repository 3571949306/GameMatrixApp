package com.gamecenter.app.sudoku;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 数独规则与对局状态的唯一真源。
 *
 * <p>这个类不依赖 Android UI。它负责生成唯一解题面、验证输入、维护草稿与历史，
 * 并通过 {@link State} 提供不会暴露内部数组的可持久化快照。</p>
 */
public class SudokuGame {

    public static final int GRID_SIZE = 9;
    public static final int BOX_SIZE = 3;

    /** 难度挖洞数：简单 30、中等 40、困难 50、专家 60。 */
    public static final int[] HOLE_COUNTS = {30, 40, 50, 60};
    public static final String[] DIFFICULTY_NAMES = {"简单", "中等", "困难", "专家"};

    private static final int ALL_DIGITS_MASK = 0x3FE;
    private static final int UNIQUE_SOLUTION_LIMIT = 2;
    private static final int SOLVER_NODE_LIMIT = 100_000;
    private static final int MAX_PUZZLE_ATTEMPTS = 6;
    private static final int MAX_DIG_PASSES = 80;
    private static final int MAX_HISTORY_SIZE = 100;

    /** 一次输入对局层实际产生的结果，供 UI 做准确反馈。 */
    public enum InputResult {
        INVALID_INPUT,
        IGNORED_GIVEN,
        CONFLICT,
        CLEARED,
        PLACED,
        INCORRECT,
        COMPLETED
    }

    /**
     * 对局快照。所有数组 getter 都返回副本，避免视图或存档层绕过规则层直接改盘面。
     */
    public static final class State {
        private final int difficultyIndex;
        private final long seed;
        private final int[][] solution;
        private final int[][] board;
        private final boolean[][] given;
        private final boolean[][] hinted;
        private final int[][] notes;
        private final int hintsUsed;
        private final int mistakes;

        State(int difficultyIndex, long seed, int[][] solution, int[][] board,
              boolean[][] given, boolean[][] hinted, int[][] notes,
              int hintsUsed, int mistakes) {
            this.difficultyIndex = difficultyIndex;
            this.seed = seed;
            this.solution = copy(solution);
            this.board = copy(board);
            this.given = copy(given);
            this.hinted = copy(hinted);
            this.notes = copy(notes);
            this.hintsUsed = Math.max(0, hintsUsed);
            this.mistakes = Math.max(0, mistakes);
        }

        public int getDifficultyIndex() {
            return difficultyIndex;
        }

        public long getSeed() {
            return seed;
        }

        public int[][] getSolution() {
            return copy(solution);
        }

        public int[][] getBoard() {
            return copy(board);
        }

        /** 返回原始题面给定格，不包含后来通过提示锁定的格子。 */
        public boolean[][] getGiven() {
            return copy(given);
        }

        /** 返回提示填入后锁定的格子。 */
        public boolean[][] getHinted() {
            return copy(hinted);
        }

        /** 每个格子的草稿位图，数字 n 对应第 n 位。 */
        public int[][] getNotes() {
            return copy(notes);
        }

        public int getHintsUsed() {
            return hintsUsed;
        }

        public int getMistakes() {
            return mistakes;
        }
    }

    /** 仅用于撤销/重做的内部状态，不重复保存不变的题面和答案。 */
    private static final class HistoryState {
        private final int[][] board;
        private final boolean[][] hinted;
        private final int[][] notes;
        private final int hintsUsed;
        private final int mistakes;

        HistoryState(int[][] board, boolean[][] hinted, int[][] notes,
                     int hintsUsed, int mistakes) {
            this.board = copy(board);
            this.hinted = copy(hinted);
            this.notes = copy(notes);
            this.hintsUsed = hintsUsed;
            this.mistakes = mistakes;
        }
    }

    private int[][] solution = new int[GRID_SIZE][GRID_SIZE];
    private int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean[][] given = new boolean[GRID_SIZE][GRID_SIZE];
    private boolean[][] hinted = new boolean[GRID_SIZE][GRID_SIZE];
    private int[][] notes = new int[GRID_SIZE][GRID_SIZE];
    private int currentDifficultyIndex = 0;
    private long seed = 0L;
    private int hintsUsed = 0;
    private int mistakes = 0;
    private boolean started = false;

    private final Deque<HistoryState> undoStack = new ArrayDeque<>();
    private final Deque<HistoryState> redoStack = new ArrayDeque<>();

    public int[][] getBoard() {
        return copy(board);
    }

    /** 返回“题面给定 + 提示锁定”的不可编辑格子。 */
    public boolean[][] getIsGiven() {
        boolean[][] result = copy(given);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                result[r][c] = result[r][c] || hinted[r][c];
            }
        }
        return result;
    }

    public int[][] getSolution() {
        return copy(solution);
    }

    public int[][] getNotes() {
        return copy(notes);
    }

    public int getCurrentDifficultyIndex() {
        return currentDifficultyIndex;
    }

    public String getCurrentDifficultyName() {
        return DIFFICULTY_NAMES[currentDifficultyIndex];
    }

    public long getSeed() {
        return seed;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public int getMistakes() {
        return mistakes;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public int getFilledCellCount() {
        int count = 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] != 0) count++;
            }
        }
        return count;
    }

    public int getRemainingCellCount() {
        return GRID_SIZE * GRID_SIZE - getFilledCellCount();
    }

    /**
     * 以随机种子开始新局。普通入口保留随机体验，测试和回放可使用显式种子重现题面。
     */
    public void startNewGame(int difficultyIndex) {
        startNewGame(difficultyIndex, new Random().nextLong());
    }

    public void startNewGame(int difficultyIndex, long seed) {
        currentDifficultyIndex = normalizeDifficulty(difficultyIndex);
        this.seed = seed;
        hintsUsed = 0;
        mistakes = 0;
        started = true;

        Random random = new Random(seed);
        int targetHoles = HOLE_COUNTS[currentDifficultyIndex];
        int[][] generatedBoard = null;
        int[][] generatedSolution = null;

        for (int attempt = 0; attempt < MAX_PUZZLE_ATTEMPTS; attempt++) {
            int[][] candidateSolution = new int[GRID_SIZE][GRID_SIZE];
            generateSolution(candidateSolution, random);
            int[][] candidateBoard = copy(candidateSolution);
            digHoles(candidateBoard, targetHoles, random);
            generatedSolution = candidateSolution;
            generatedBoard = candidateBoard;
            if (countEmptyCells(candidateBoard) >= targetHoles) break;
        }

        solution = generatedSolution;
        board = generatedBoard;
        given = new boolean[GRID_SIZE][GRID_SIZE];
        hinted = new boolean[GRID_SIZE][GRID_SIZE];
        notes = new int[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                given[r][c] = board[r][c] != 0;
            }
        }
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * 输入一个数字。冲突输入会被拒绝；符合规则但不符合答案的数字会保留并标记为错误，
     * 让玩家可以自行发现和修正，而不是被规则层静默改写。
     */
    public InputResult setValue(int row, int col, int num) {
        if (!started || !isInside(row, col) || num < 0 || num > GRID_SIZE) {
            return InputResult.INVALID_INPUT;
        }
        if (isLocked(row, col)) return InputResult.IGNORED_GIVEN;

        int previous = board[row][col];
        if (num == 0) {
            if (previous == 0 && notes[row][col] == 0) return InputResult.CLEARED;
            recordHistory();
            board[row][col] = 0;
            notes[row][col] = 0;
            return InputResult.CLEARED;
        }

        if (!isValidPlacement(board, row, col, num)) {
            return InputResult.CONFLICT;
        }
        if (previous == num) {
            return num == solution[row][col]
                    ? InputResult.PLACED : InputResult.INCORRECT;
        }

        recordHistory();
        board[row][col] = num;
        notes[row][col] = 0;
        removeNoteFromPeersInternal(row, col, num);
        if (num != solution[row][col]) mistakes++;

        if (isBoardComplete()) return InputResult.COMPLETED;
        return num == solution[row][col]
                ? InputResult.PLACED : InputResult.INCORRECT;
    }

    /** 兼容旧调用方：返回“本次输入后是否完成”。 */
    public boolean inputNumber(int row, int col, int num) {
        return setValue(row, col, num) == InputResult.COMPLETED;
    }

    public boolean hasConflict(int row, int col, int num) {
        return num != 0 && !isValidPlacement(board, row, col, num);
    }

    public boolean isCellLocked(int row, int col) {
        return isInside(row, col) && isLocked(row, col);
    }

    public int getValue(int row, int col) {
        return isInside(row, col) ? board[row][col] : 0;
    }

    public int getNoteMask(int row, int col) {
        return isInside(row, col) ? notes[row][col] : 0;
    }

    /** 当前格是否允许输入数字 n（会忽略该格原有数字）。 */
    public boolean canPlace(int row, int col, int num) {
        return started && isInside(row, col) && !isLocked(row, col)
                && num >= 1 && num <= GRID_SIZE
                && isValidPlacement(board, row, col, num);
    }

    /** 返回当前盘面上的错误格：冲突或与唯一答案不一致。 */
    public boolean[][] getErrors() {
        boolean[][] errors = new boolean[GRID_SIZE][GRID_SIZE];
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int value = board[r][c];
                if (value == 0 || isLocked(r, c)) continue;
                errors[r][c] = value != solution[r][c]
                        || !isValidPlacement(board, r, c, value);
            }
        }
        return errors;
    }

    /** 切换当前格草稿 n；草稿只能存在于空的可编辑格。 */
    public boolean toggleNote(int row, int col, int num) {
        if (!started || !isInside(row, col) || isLocked(row, col)
                || board[row][col] != 0 || num < 1 || num > GRID_SIZE) {
            return false;
        }
        recordHistory();
        notes[row][col] ^= (1 << num);
        return true;
    }

    public boolean clearNotes(int row, int col) {
        if (!started || !isInside(row, col) || notes[row][col] == 0) return false;
        recordHistory();
        notes[row][col] = 0;
        return true;
    }

    /** 清理某个数字落子后同行、同列和同宫格里的同号草稿。 */
    public boolean removeNoteFromPeers(int row, int col, int num) {
        if (!started || !isInside(row, col) || num < 1 || num > GRID_SIZE) return false;
        boolean changed = hasPeerNote(row, col, num);
        if (!changed) return false;
        recordHistory();
        removeNoteFromPeersInternal(row, col, num);
        return true;
    }

    /** 对指定格使用提示，并将该格锁定为答案格。 */
    public boolean useHint(int row, int col) {
        if (!started || !isInside(row, col) || isLocked(row, col)
                || board[row][col] == solution[row][col]) {
            return false;
        }
        recordHistory();
        board[row][col] = solution[row][col];
        hinted[row][col] = true;
        notes[row][col] = 0;
        removeNoteFromPeersInternal(row, col, board[row][col]);
        hintsUsed++;
        return isBoardComplete();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(captureHistoryState());
        restoreHistoryState(undoStack.pop());
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(captureHistoryState());
        restoreHistoryState(redoStack.pop());
        return true;
    }

    /** 检测是否所有格都填入了唯一答案，而不只是“没有空格”。 */
    public boolean isBoardComplete() {
        if (!started) return false;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] == 0 || board[r][c] != solution[r][c]) return false;
            }
        }
        return true;
    }

    public boolean isSolved() {
        return isBoardComplete();
    }

    /** 导出快照；快照可直接交给 SudokuSaveManager 序列化。 */
    public State getState() {
        return new State(currentDifficultyIndex, seed, solution, board,
                given, hinted, notes, hintsUsed, mistakes);
    }

    /** 从受校验的快照恢复，恢复后历史从当前节点重新开始。 */
    public boolean restoreState(State state) {
        if (!isValidState(state)) return false;
        currentDifficultyIndex = state.difficultyIndex;
        seed = state.seed;
        solution = copy(state.solution);
        board = copy(state.board);
        given = copy(state.given);
        hinted = copy(state.hinted);
        notes = copy(state.notes);
        hintsUsed = state.hintsUsed;
        mistakes = state.mistakes;
        started = true;
        undoStack.clear();
        redoStack.clear();
        return true;
    }

    /** 公共规则校验，允许“正在替换的格子”保留同一个数字。 */
    public boolean isValidPlacement(int[][] grid, int row, int col, int num) {
        if (grid == null || !isInside(row, col) || num < 1 || num > GRID_SIZE
                || grid.length != GRID_SIZE) {
            return false;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (grid[r] == null || grid[r].length != GRID_SIZE) return false;
        }
        for (int c = 0; c < GRID_SIZE; c++) {
            if (c != col && grid[row][c] == num) return false;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (r != row && grid[r][col] == num) return false;
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if ((r != row || c != col) && grid[r][c] == num) return false;
            }
        }
        return true;
    }

    private void generateSolution(int[][] target, Random random) {
        fillBoard(target, random);
    }

    private boolean fillBoard(int[][] grid, Random random) {
        int[] best = findBestEmptyCell(grid);
        if (best[0] == -1) return true;
        int mask = candidateMask(grid, best[0], best[1]);
        List<Integer> numbers = numbersFromMask(mask);
        Collections.shuffle(numbers, random);
        for (int number : numbers) {
            grid[best[0]][best[1]] = number;
            if (fillBoard(grid, random)) return true;
            grid[best[0]][best[1]] = 0;
        }
        return false;
    }

    private void digHoles(int[][] puzzle, int targetHoles, Random random) {
        int[][] solved = copy(puzzle);
        int[][] best = copy(puzzle);
        int bestDug = 0;
        // 专家档不强制对称，优先保证目标空格数；其余档位保留更整齐的中心对称观感。
        boolean preferSymmetry = targetHoles < 60;
        for (int pass = 0; pass < MAX_DIG_PASSES; pass++) {
            int[][] candidate = copy(solved);
            int dug = digHolesOnce(candidate, targetHoles, random, preferSymmetry);
            if (dug > bestDug) {
                bestDug = dug;
                best = candidate;
            }
            if (dug >= targetHoles) {
                copyInto(candidate, puzzle);
                return;
            }
        }
        copyInto(best, puzzle);
    }

    private int digHolesOnce(int[][] puzzle, int targetHoles, Random random,
                             boolean preferSymmetry) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) positions.add(i);
        Collections.shuffle(positions, random);

        int dug = 0;
        if (preferSymmetry) {
            // 先按中心对称挖洞，让盘面视觉更稳定；所有目标值都是偶数。
            for (int position : positions) {
                if (dug + 2 > targetHoles) break;
                int row = position / GRID_SIZE;
                int col = position % GRID_SIZE;
                int partner = GRID_SIZE * GRID_SIZE - 1 - position;
                int partnerRow = partner / GRID_SIZE;
                int partnerCol = partner % GRID_SIZE;
                if (position == partner || puzzle[row][col] == 0
                        || puzzle[partnerRow][partnerCol] == 0) continue;

                int first = puzzle[row][col];
                int second = puzzle[partnerRow][partnerCol];
                puzzle[row][col] = 0;
                puzzle[partnerRow][partnerCol] = 0;
                if (countSolutions(puzzle, UNIQUE_SOLUTION_LIMIT, SOLVER_NODE_LIMIT) == 1) {
                    dug += 2;
                } else {
                    puzzle[row][col] = first;
                    puzzle[partnerRow][partnerCol] = second;
                }
            }
        }

        // 对称挖洞无法达到目标时，或专家档直接使用单格挖洞，仍然保持唯一解。
        if (dug < targetHoles) {
            for (int position : positions) {
                if (dug >= targetHoles) break;
                int row = position / GRID_SIZE;
                int col = position % GRID_SIZE;
                if (puzzle[row][col] == 0) continue;
                int saved = puzzle[row][col];
                puzzle[row][col] = 0;
                if (countSolutions(puzzle, UNIQUE_SOLUTION_LIMIT, SOLVER_NODE_LIMIT) == 1) {
                    dug++;
                } else {
                    puzzle[row][col] = saved;
                }
            }
        }
        return dug;
    }

    private int countSolutions(int[][] input, int limit, int nodeLimit) {
        int[][] candidate = copy(input);
        int[] count = {0};
        SearchBudget budget = new SearchBudget(nodeLimit);
        solveCount(candidate, limit, count, budget);
        return budget.aborted ? limit + 1 : count[0];
    }

    private boolean solveCount(int[][] grid, int limit, int[] count, SearchBudget budget) {
        if (budget.aborted) return true;
        if (++budget.nodes > budget.nodeLimit) {
            budget.aborted = true;
            return true;
        }
        int[] best = findBestEmptyCell(grid);
        if (best[0] == -1) {
            count[0]++;
            return count[0] >= limit;
        }
        int mask = candidateMask(grid, best[0], best[1]);
        while (mask != 0) {
            int bit = mask & -mask;
            mask -= bit;
            int number = Integer.numberOfTrailingZeros(bit);
            grid[best[0]][best[1]] = number;
            if (solveCount(grid, limit, count, budget)) {
                grid[best[0]][best[1]] = 0;
                return true;
            }
            grid[best[0]][best[1]] = 0;
        }
        return false;
    }

    private int[] findBestEmptyCell(int[][] grid) {
        int bestRow = -1;
        int bestCol = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] != 0) continue;
                int count = Integer.bitCount(candidateMask(grid, r, c));
                if (count < bestCount) {
                    bestCount = count;
                    bestRow = r;
                    bestCol = c;
                    if (count <= 1) return new int[]{bestRow, bestCol};
                }
            }
        }
        return new int[]{bestRow, bestCol};
    }

    private int candidateMask(int[][] grid, int row, int col) {
        int mask = ALL_DIGITS_MASK;
        for (int c = 0; c < GRID_SIZE; c++) {
            if (c != col) mask &= ~(1 << grid[row][c]);
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (r != row) mask &= ~(1 << grid[r][col]);
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if (r != row || c != col) mask &= ~(1 << grid[r][c]);
            }
        }
        return mask;
    }

    private List<Integer> numbersFromMask(int mask) {
        List<Integer> numbers = new ArrayList<>();
        while (mask != 0) {
            int bit = mask & -mask;
            mask -= bit;
            numbers.add(Integer.numberOfTrailingZeros(bit));
        }
        return numbers;
    }

    private void recordHistory() {
        undoStack.push(captureHistoryState());
        while (undoStack.size() > MAX_HISTORY_SIZE) undoStack.removeLast();
        redoStack.clear();
    }

    private HistoryState captureHistoryState() {
        return new HistoryState(board, hinted, notes, hintsUsed, mistakes);
    }

    private void restoreHistoryState(HistoryState state) {
        board = copy(state.board);
        hinted = copy(state.hinted);
        notes = copy(state.notes);
        hintsUsed = state.hintsUsed;
        mistakes = state.mistakes;
    }

    private boolean hasPeerNote(int row, int col, int num) {
        int bit = 1 << num;
        for (int c = 0; c < GRID_SIZE; c++) {
            if (c != col && (notes[row][c] & bit) != 0) return true;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (r != row && (notes[r][col] & bit) != 0) return true;
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if ((r != row || c != col) && (notes[r][c] & bit) != 0) return true;
            }
        }
        return false;
    }

    private void removeNoteFromPeersInternal(int row, int col, int num) {
        int bit = ~(1 << num);
        for (int c = 0; c < GRID_SIZE; c++) {
            if (c != col) notes[row][c] &= bit;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (r != row) notes[r][col] &= bit;
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if (r != row || c != col) notes[r][c] &= bit;
            }
        }
    }

    private boolean isValidState(State state) {
        if (state == null || state.difficultyIndex < 0
                || state.difficultyIndex >= HOLE_COUNTS.length
                || !isMatrixShape(state.solution) || !isMatrixShape(state.board)
                || !isBooleanMatrixShape(state.given) || !isBooleanMatrixShape(state.hinted)
                || !isMatrixShape(state.notes) || !isCompleteSolution(state.solution)) {
            return false;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int value = state.board[r][c];
                if (value < 0 || value > GRID_SIZE || state.notes[r][c] < 0
                        || (state.notes[r][c] & ~ALL_DIGITS_MASK) != 0) return false;
                if (state.given[r][c] && value != state.solution[r][c]) return false;
                if (state.hinted[r][c]
                        && (state.given[r][c] || value != state.solution[r][c])) return false;
                if (value != 0 && state.notes[r][c] != 0) return false;
                if ((state.given[r][c] || state.hinted[r][c]) && value == 0) return false;
                if (value != 0 && !isValidPlacement(state.board, r, c, value)) return false;
            }
        }
        return true;
    }

    private boolean isCompleteSolution(int[][] candidate) {
        if (!isMatrixShape(candidate)) return false;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (candidate[r][c] < 1 || candidate[r][c] > GRID_SIZE
                        || !isValidPlacement(candidate, r, c, candidate[r][c])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countEmptyCells(int[][] candidate) {
        int count = 0;
        for (int[] row : candidate) {
            for (int value : row) if (value == 0) count++;
        }
        return count;
    }

    private static void copyInto(int[][] source, int[][] target) {
        for (int r = 0; r < GRID_SIZE; r++) {
            System.arraycopy(source[r], 0, target[r], 0, GRID_SIZE);
        }
    }

    private static int normalizeDifficulty(int difficultyIndex) {
        return Math.max(0, Math.min(HOLE_COUNTS.length - 1, difficultyIndex));
    }

    private boolean isLocked(int row, int col) {
        return given[row][col] || hinted[row][col];
    }

    private static boolean isInside(int row, int col) {
        return row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE;
    }

    private static boolean isMatrixShape(int[][] matrix) {
        if (matrix == null || matrix.length != GRID_SIZE) return false;
        for (int[] row : matrix) if (row == null || row.length != GRID_SIZE) return false;
        return true;
    }

    private static boolean isBooleanMatrixShape(boolean[][] matrix) {
        if (matrix == null || matrix.length != GRID_SIZE) return false;
        for (boolean[] row : matrix) if (row == null || row.length != GRID_SIZE) return false;
        return true;
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }

    private static boolean[][] copy(boolean[][] source) {
        boolean[][] result = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }

    private static final class SearchBudget {
        private final int nodeLimit;
        private int nodes;
        private boolean aborted;

        private SearchBudget(int nodeLimit) {
            this.nodeLimit = nodeLimit;
        }
    }
}

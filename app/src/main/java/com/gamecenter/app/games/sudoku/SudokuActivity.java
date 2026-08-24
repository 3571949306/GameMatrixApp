package com.gamecenter.app.games.sudoku;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 数独游戏 Activity。
 *
 * <p>9×9 数独，支持 4 级难度（挖洞数 30/40/50/60）。
 * 点击格子后在底部选择数字输入。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>中等难度通关</li>
 *   <li>困难难度通关</li>
 *   <li>无提示通关</li>
 *   <li>10分钟内通关</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class SudokuActivity extends BaseGameActivity {

    private static final int GRID_SIZE = 9;
    private static final int BOX_SIZE = 3;

    /** 难度挖洞数：简单30，中等40，困难50，专家60 */
    private static final int[] HOLE_COUNTS = {30, 40, 50, 60};

    /** 唯一解验证：挖洞尝试上限，避免极端难度下耗时过长 */
    private static final int MAX_DIG_ATTEMPTS = 200;
    /** 唯一解验证：countSolutions 搜索节点上限，超过则视为非唯一（保守回退） */
    private static final int SOLVER_NODE_LIMIT = 5000;
    /** 唯一解验证：计数上限，找到 2 个解即判定多解 */
    private static final int UNIQUE_SOLUTION_LIMIT = 2;

    /** 笔记功能 feature flag（规则17：新增功能尽量带 flag） */
    private static final boolean NOTES_FEATURE_ENABLED = true;

    // 游戏状态
    private int[][] solution = new int[GRID_SIZE][GRID_SIZE];
    private int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean[][] isGiven = new boolean[GRID_SIZE][GRID_SIZE];
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int currentDifficultyIndex = 0;
    private int hintsUsed = 0;
    private int puzzlesSolved = 0;
    /** 笔记模式开关：开启后点数字键记候选，不填入答案 */
    private boolean notesMode = false;

    private Random random = new Random();

    /** 2026-08-23 P2-2: 中断续玩存档管理器 */
    private com.gamecenter.app.games.save.GameSaveManager saveManager;

    /** 2026-08-23 P3: 统一音效/震动反馈（内部实时遵循设置开关） */
    private com.gamecenter.app.games.base.GameFeedback feedback;

    // UI 组件
    private SudokuView sudokuView;
    private TextView tvStatus;
    private TextView tvDifficulty;
    private LinearLayout numPadPanel;
    private LinearLayout menuPanel;
    private LinearLayout gamePanel;
    private MaterialButton btnHint;
    private MaterialButton btnNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ==================== BaseGameActivity 抽象方法实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "sudoku";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_sudoku_name);
    }

    @Override
    protected void initGame() {
        // 2026-08-23 P2-2：初始化存档管理器
        saveManager = new com.gamecenter.app.games.save.GameSaveManager(this);
        // 2026-08-23 P3：初始化音效/震动反馈
        feedback = new com.gamecenter.app.games.base.GameFeedback(this);
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_bg));

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_text));
        tvStatus.setPadding(0, 24, 0, 8);

        tvDifficulty = new TextView(this);
        tvDifficulty.setGravity(Gravity.CENTER);
        tvDifficulty.setTextSize(14f);
        tvDifficulty.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_difficulty));
        tvDifficulty.setPadding(0, 4, 0, 16);

        // 菜单面板
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        String[] diffNames = {
                getString(R.string.difficulty_easy),
                getString(R.string.difficulty_medium),
                getString(R.string.difficulty_hard),
                getString(R.string.game_sudoku_expert)
        };
        for (int i = 0; i < diffNames.length; i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(diffNames[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 8, 0, 8);
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_difficulty));
            // 2026-08-23 P2-2：开始入口先走 beginPlay 检测未完成对局存档
            btn.setOnClickListener(v -> beginPlay(idx));
            menuPanel.addView(btn);
        }

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        sudokuView = new SudokuView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.92);
        sudokuView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        sudokuView.setOnCellSelectListener((row, col) -> {
            selectedRow = row;
            selectedCol = col;
        });

        // 数字键盘
        numPadPanel = new LinearLayout(this);
        numPadPanel.setOrientation(LinearLayout.HORIZONTAL);
        numPadPanel.setGravity(Gravity.CENTER);
        numPadPanel.setPadding(0, 16, 0, 8);

        int btnSize = getResources().getDisplayMetrics().widthPixels / 10;
        for (int n = 1; n <= 9; n++) {
            final int num = n;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(String.valueOf(n));
            btn.setTextSize(18f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_num));
            btn.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_num_text));
            btn.setOnClickListener(v -> inputNumber(num));
            numPadPanel.addView(btn);
        }
        // 清除按钮
        MaterialButton btnClear = new MaterialButton(this);
        btnClear.setText("✕");
        btnClear.setTextSize(18f);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        clearLp.setMargins(4, 4, 4, 4);
        btnClear.setLayoutParams(clearLp);
        btnClear.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_clear));
        btnClear.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_clear_text));
        btnClear.setOnClickListener(v -> inputNumber(0));
        numPadPanel.addView(btnClear);

        // 底部按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        btnHint = new MaterialButton(this);
        btnHint.setText(R.string.game_sudoku_hint);
        btnHint.setOnClickListener(v -> showHint());

        MaterialButton btnRestart = new MaterialButton(this);
        btnRestart.setText(R.string.btn_restart);
        btnRestart.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 16, 0);
        btnHint.setLayoutParams(btnLp);
        btnRestart.setLayoutParams(btnLp);

        btnRow.addView(btnHint);
        btnRow.addView(btnRestart);

        // 笔记模式开关（feature flag 控制）
        if (NOTES_FEATURE_ENABLED) {
            btnNotes = new MaterialButton(this);
            btnNotes.setText(R.string.game_sudoku_notes_off);
            LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            notesLp.setMargins(16, 0, 16, 0);
            btnNotes.setLayoutParams(notesLp);
            btnNotes.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_notes));
            btnNotes.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_notes_text));
            btnNotes.setOnClickListener(v -> toggleNotesMode());
            btnRow.addView(btnNotes);
        }

        gamePanel.addView(sudokuView);
        gamePanel.addView(numPadPanel);
        gamePanel.addView(btnRow);

        root.addView(tvStatus);
        root.addView(tvDifficulty);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_sudoku_select_difficulty);
        tvDifficulty.setText("");
    }

    /**
     * 2026-08-23 P2-2：开始游戏入口——检测未完成对局存档，
     * 有存档时弹"继续上局"对话框，否则直接新开一局。
     */
    private void beginPlay(int difficultyIndex) {
        if (saveManager != null && saveManager.hasSave(getGameId())) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("继续上局？")
                    .setMessage("检测到上次未完成的对局，是否继续？")
                    .setPositiveButton("继续上局", (d, w) -> restoreFromSave())
                    .setNegativeButton("新开一局", (d, w) -> {
                        saveManager.clear(getGameId());
                        startGameWithDifficulty(difficultyIndex);
                    })
                    .setCancelable(true)
                    .show();
        } else {
            startGameWithDifficulty(difficultyIndex);
        }
    }

    /** 2026-08-23 P2-2：从存档恢复对局 */
    private void restoreFromSave() {
        JSONObject state = saveManager == null ? null : saveManager.load(getGameId());
        if (state == null) {
            startGameWithDifficulty(0);
            return;
        }
        try {
            // 恢复当前棋盘、完整解与题面固定标记
            JSONArray boardRows = state.getJSONArray("board");
            JSONArray solutionRows = state.getJSONArray("solution");
            JSONArray givenRows = state.getJSONArray("isGiven");
            for (int r = 0; r < GRID_SIZE && r < boardRows.length(); r++) {
                JSONArray row = boardRows.getJSONArray(r);
                JSONArray solRow = solutionRows.getJSONArray(r);
                JSONArray givenRow = givenRows.getJSONArray(r);
                for (int c = 0; c < GRID_SIZE && c < row.length(); c++) {
                    board[r][c] = row.getInt(c);
                    solution[r][c] = solRow.getInt(c);
                    isGiven[r][c] = givenRow.getInt(c) == 1;
                }
            }
            currentDifficultyIndex = state.optInt("difficultyIndex", 0);
            // 防止损坏存档的难度索引越界（HOLE_COUNTS/diffNames 长度为 4）
            currentDifficultyIndex = Math.max(0, Math.min(currentDifficultyIndex, HOLE_COUNTS.length - 1));
            hintsUsed = state.optInt("hintsUsed", 0);
            long elapsedMs = state.optLong("elapsedMs", 0);
            selectedRow = -1;
            selectedCol = -1;

            menuPanel.setVisibility(View.GONE);
            gamePanel.setVisibility(View.VISIBLE);
            String[] diffNames = {
                    getString(R.string.difficulty_easy),
                    getString(R.string.difficulty_medium),
                    getString(R.string.difficulty_hard),
                    getString(R.string.game_sudoku_expert)
            };
            tvDifficulty.setText(diffNames[currentDifficultyIndex]);
            tvStatus.setText(R.string.game_sudoku_playing);
            // 重置笔记模式
            notesMode = false;
            updateNotesButton();

            // 恢复棋盘视图并重新计算玩家填入数字的错误标记
            // isValidPlacement 契约为"在空格上放置 num"，检查前需临时清零该格
            sudokuView.restoreState(board, isGiven);
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    if (!isGiven[r][c] && board[r][c] != 0) {
                        int num = board[r][c];
                        board[r][c] = 0;
                        boolean error = !isValidPlacement(board, r, c, num);
                        board[r][c] = num;
                        sudokuView.setError(r, c, error);
                    }
                }
            }

            isGameRunning = true;
            gameStartTime = System.currentTimeMillis() - elapsedMs;
        } catch (Exception e) {
            android.util.Log.w("SudokuActivity", "存档恢复失败，新开一局: " + e.getMessage());
            startGameWithDifficulty(currentDifficultyIndex);
        }
    }

    /** 2026-08-23 P2-2：保存当前解题进度 */
    private void saveProgress() {
        if (saveManager == null || !isGameRunning) return;
        try {
            JSONObject state = new JSONObject();
            state.put("board", toJsonArray(board));
            state.put("solution", toJsonArray(solution));
            state.put("isGiven", toJsonArray(isGiven));
            state.put("difficultyIndex", currentDifficultyIndex);
            state.put("hintsUsed", hintsUsed);
            if (gameStartTime > 0) {
                state.put("elapsedMs", System.currentTimeMillis() - gameStartTime);
            }
            saveManager.save(getGameId(), state);
        } catch (Exception ignored) {
            // 存档失败不影响游戏主流程
        }
    }

    /** 2026-08-23 P2-2：int 矩阵序列化为 JSON 二维数组 */
    private JSONArray toJsonArray(int[][] matrix) {
        JSONArray rows = new JSONArray();
        for (int r = 0; r < GRID_SIZE; r++) {
            JSONArray row = new JSONArray();
            for (int c = 0; c < GRID_SIZE; c++) {
                row.put(matrix[r][c]);
            }
            rows.put(row);
        }
        return rows;
    }

    /** 2026-08-23 P2-2：boolean 矩阵序列化为 JSON 二维数组（true→1 / false→0） */
    private JSONArray toJsonArray(boolean[][] matrix) {
        JSONArray rows = new JSONArray();
        for (int r = 0; r < GRID_SIZE; r++) {
            JSONArray row = new JSONArray();
            for (int c = 0; c < GRID_SIZE; c++) {
                row.put(matrix[r][c] ? 1 : 0);
            }
            rows.put(row);
        }
        return rows;
    }

    /**
     * 按难度开始游戏
     */
    private void startGameWithDifficulty(int difficultyIndex) {
        currentDifficultyIndex = difficultyIndex;
        hintsUsed = 0;

        // 生成完整解
        generateSolution();

        // 复制解到棋盘
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                board[r][c] = solution[r][c];
                isGiven[r][c] = true;
            }
        }

        // 挖洞（对称挖洞 + 唯一解验证）
        digHoles(board, HOLE_COUNTS[difficultyIndex]);
        // 根据挖洞后的棋盘重算 isGiven 标记
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                isGiven[r][c] = board[r][c] != 0;
            }
        }

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);

        String[] diffNames = {
                getString(R.string.difficulty_easy),
                getString(R.string.difficulty_medium),
                getString(R.string.difficulty_hard),
                getString(R.string.game_sudoku_expert)
        };
        tvDifficulty.setText(diffNames[difficultyIndex]);
        tvStatus.setText(R.string.game_sudoku_playing);

        // 重置笔记模式
        notesMode = false;
        updateNotesButton();

        sudokuView.setBoard(board, isGiven);
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 切换笔记模式开/关，并更新按钮文案。
     */
    private void toggleNotesMode() {
        notesMode = !notesMode;
        updateNotesButton();
    }

    /**
     * 根据笔记模式状态刷新按钮文案与底色。
     */
    private void updateNotesButton() {
        if (btnNotes == null) return;
        if (notesMode) {
            btnNotes.setText(R.string.game_sudoku_notes_on);
            btnNotes.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_notes_on));
            btnNotes.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_notes_on_text));
        } else {
            btnNotes.setText(R.string.game_sudoku_notes_off);
            btnNotes.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_notes));
            btnNotes.setTextColor(ContextCompat.getColor(this, R.color.game_sudoku_color_btn_notes_text));
        }
    }

    /**
     * 生成完整数独解
     */
    private void generateSolution() {
        solution = new int[GRID_SIZE][GRID_SIZE];
        fillBoard(solution);
    }

    /**
     * 递归填充数独棋盘
     */
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

    /**
     * 检查数字放置是否有效
     */
    private boolean isValidPlacement(int[][] grid, int row, int col, int num) {
        // 检查行
        for (int c = 0; c < GRID_SIZE; c++) {
            if (grid[row][c] == num) return false;
        }
        // 检查列
        for (int r = 0; r < GRID_SIZE; r++) {
            if (grid[r][col] == num) return false;
        }
        // 检查3x3宫
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if (grid[r][c] == num) return false;
            }
        }
        return true;
    }

    /**
     * 计算棋盘解的数量（上限 limit，节点上限 nodeLimit）。
     * <p>在传入棋盘的副本上回溯求解，不修改原始棋盘。当搜索节点超过 nodeLimit 时
     * 返回 limit+1，调用方可据此保守判定为"非唯一解"。</p>
     *
     * @param board     待求解棋盘（0 表示空格）
     * @param limit     计数上限，到达即停止（通常传 2）
     * @param nodeLimit 搜索节点上限，超过则放弃精确计数
     * @return 解的数量；若超过 nodeLimit 则返回 limit+1
     */
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

    /**
     * 回溯求解并计数。找到 limit 个解或超过 nodeLimit 时提前终止。
     */
    private boolean solveCount(int[][] grid, int limit, int nodeLimit,
                               int[] count, int[] nodes) {
        if (nodes[0] > nodeLimit) return true;
        nodes[0]++;

        // 找到第一个空格
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
        // 没有空格：找到一个完整解
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

    /**
     * 对称挖洞 + 唯一解验证。
     * <p>中心对称（180°）挖洞：挖 (r,c) 时同步挖 (8-r,8-c)。每次挖洞后用
     * countSolutions 验证仍唯一解才保留，否则恢复。受 MAX_DIG_ATTEMPTS 上限约束，
     * 实际挖洞数可能少于 targetHoles（这保证了唯一性优先于难度）。</p>
     */
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
                // 多解或节点超限：恢复本次挖洞
                board[r][c] = saved1;
                if (dugPartner) board[r2][c2] = saved2;
            }
        }
    }

    /**
     * 输入数字
     */
    private void inputNumber(int num) {
        if (selectedRow < 0 || selectedCol < 0) return;
        if (isGiven[selectedRow][selectedCol]) return;

        // 笔记模式：数字键切换候选标记，清除键清空该格笔记
        if (NOTES_FEATURE_ENABLED && notesMode) {
            if (num == 0) {
                sudokuView.clearNotes(selectedRow, selectedCol);
            } else {
                sudokuView.toggleNote(selectedRow, selectedCol, num);
            }
            return;
        }

        board[selectedRow][selectedCol] = num;
        boolean hasError = num != 0 && !isValidPlacement(board, selectedRow, selectedCol, num);
        sudokuView.setError(selectedRow, selectedCol, hasError);
        sudokuView.updateCell(selectedRow, selectedCol, num);

        // 正常填入数字时：清除该格笔记，并从同行/列/宫的笔记中移除该数字
        if (num != 0 && NOTES_FEATURE_ENABLED) {
            sudokuView.clearNotes(selectedRow, selectedCol);
            sudokuView.removeNoteFromPeers(selectedRow, selectedCol, num);
        }

        // 检查是否完成
        if (num != 0 && !hasError && isBoardComplete()) {
            onPuzzleSolved();
        } else if (num != 0) {
            // 2026-08-23 P3：填入数字音效（完成时在 onPuzzleSolved 给出胜利反馈）
            if (feedback != null) feedback.playMove();
        }
        // 2026-08-23 P2-2：玩家填入/清除数字后保存进度
        // （胜利时 onPuzzleSolved 已置 isGameRunning=false，saveProgress 内部会跳过）
        saveProgress();
    }

    /**
     * 检查棋盘是否填满
     */
    private boolean isBoardComplete() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] == 0) return false;
            }
        }
        return true;
    }

    /**
     * 提示功能
     */
    private void showHint() {
        if (selectedRow < 0 || selectedCol < 0) return;
        if (isGiven[selectedRow][selectedCol]) return;
        if (solution[selectedRow][selectedCol] == 0) return;

        board[selectedRow][selectedCol] = solution[selectedRow][selectedCol];
        isGiven[selectedRow][selectedCol] = true;
        sudokuView.updateCell(selectedRow, selectedCol, solution[selectedRow][selectedCol]);
        sudokuView.setError(selectedRow, selectedCol, false);
        hintsUsed++;

        if (isBoardComplete()) {
            onPuzzleSolved();
        }
        // 2026-08-23 P2-2：提示填入正解后棋盘已变化，保存进度
        saveProgress();
    }

    /**
     * 解题完成处理
     */
    private void onPuzzleSolved() {
        isGameRunning = false;
        // 2026-08-23 P2-2：对局正常结束，清除存档
        if (saveManager != null) saveManager.clear(getGameId());
        puzzlesSolved++;
        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        long elapsedSec = elapsedMs / 1000;

        tvStatus.setText(getString(R.string.game_sudoku_congratulations, elapsedSec));

        // 2026-08-23 P3：胜利反馈
        if (feedback != null) feedback.feedbackWin();

        // 成就检查
        checkAchievement("win", puzzlesSolved);
        int difficultyLevel = currentDifficultyIndex + 1;
        if (difficultyLevel >= 2) {
            checkAchievement("special", true); // 中等难度以上
        }
        if (hintsUsed == 0) {
            checkAchievement("score", 1); // 无提示
        }
        checkAchievement("time", (int) elapsedSec);

        updateScore(currentScore + (currentDifficultyIndex + 1) * 50);
        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);
    }

    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_sudoku_diff_easy), 1, getString(R.string.game_sudoku_diff_easy_desc), true));
        levels.add(new DifficultyLevel(getString(R.string.game_sudoku_diff_medium), 2, getString(R.string.game_sudoku_diff_medium_desc), false));
        levels.add(new DifficultyLevel(getString(R.string.game_sudoku_diff_hard), 3, getString(R.string.game_sudoku_diff_hard_desc), false));
        levels.add(new DifficultyLevel(getString(R.string.game_sudoku_expert), 4, getString(R.string.game_sudoku_diff_expert_desc), false));
        return levels;
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 2026-08-23 P3：释放音效资源
        if (feedback != null) {
            feedback.release();
            feedback = null;
        }
    }
}

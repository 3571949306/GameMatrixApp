package com.gamecenter.app.games.sudoku;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;
import com.google.android.material.button.MaterialButton;

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

    // 游戏状态
    private int[][] solution = new int[GRID_SIZE][GRID_SIZE];
    private int[][] board = new int[GRID_SIZE][GRID_SIZE];
    private boolean[][] isGiven = new boolean[GRID_SIZE][GRID_SIZE];
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int currentDifficultyIndex = 0;
    private int hintsUsed = 0;
    private int puzzlesSolved = 0;

    private Random random = new Random();

    // UI 组件
    private SudokuView sudokuView;
    private TextView tvStatus;
    private TextView tvDifficulty;
    private LinearLayout numPadPanel;
    private LinearLayout menuPanel;
    private LinearLayout gamePanel;
    private MaterialButton btnHint;

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
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F0E8);

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 24, 0, 8);

        tvDifficulty = new TextView(this);
        tvDifficulty.setGravity(Gravity.CENTER);
        tvDifficulty.setTextSize(14f);
        tvDifficulty.setTextColor(0xFF5B8A72);
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
            btn.setBackgroundColor(0xFF5B8A72);
            btn.setOnClickListener(v -> startGameWithDifficulty(idx));
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
            btn.setBackgroundColor(0xFFFBF9F6);
            btn.setTextColor(0xFF2D2D2D);
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
        btnClear.setBackgroundColor(0xFFFEE2E2);
        btnClear.setTextColor(0xFFDC2626);
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

        // 挖洞
        int holes = HOLE_COUNTS[difficultyIndex];
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions, random);
        for (int i = 0; i < holes && i < positions.size(); i++) {
            int pos = positions.get(i);
            int r = pos / GRID_SIZE;
            int c = pos % GRID_SIZE;
            board[r][c] = 0;
            isGiven[r][c] = false;
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

        sudokuView.setBoard(board, isGiven);
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
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
     * 输入数字
     */
    private void inputNumber(int num) {
        if (selectedRow < 0 || selectedCol < 0) return;
        if (isGiven[selectedRow][selectedCol]) return;

        board[selectedRow][selectedCol] = num;
        boolean hasError = num != 0 && !isValidPlacement(board, selectedRow, selectedCol, num);
        sudokuView.setError(selectedRow, selectedCol, hasError);
        sudokuView.updateCell(selectedRow, selectedCol, num);

        // 检查是否完成
        if (num != 0 && !hasError && isBoardComplete()) {
            onPuzzleSolved();
        }
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
    }

    /**
     * 解题完成处理
     */
    private void onPuzzleSolved() {
        isGameRunning = false;
        puzzlesSolved++;
        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        long elapsedSec = elapsedMs / 1000;

        tvStatus.setText(getString(R.string.game_sudoku_congratulations, elapsedSec));

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
        levels.add(new DifficultyLevel("简单", 1, "挖30个格子", true));
        levels.add(new DifficultyLevel("中等", 2, "挖40个格子", false));
        levels.add(new DifficultyLevel("困难", 3, "挖50个格子", false));
        levels.add(new DifficultyLevel("专家", 4, "挖60个格子", false));
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
}

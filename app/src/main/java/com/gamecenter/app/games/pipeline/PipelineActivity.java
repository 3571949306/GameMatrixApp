package com.gamecenter.app.games.pipeline;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Random;

/**
 * 管道工游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>在网格中旋转管道片段，将起点连接到终点。
 * 管道类型：直线、L型、T型、十字。点击旋转90°。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>最少步数通关</li>
 *   <li>通过3关</li>
 *   <li>30秒内通关</li>
 *   <li>累计10关</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class PipelineActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    // 管道类型
    private static final int PIPE_NONE = 0;
    private static final int PIPE_STRAIGHT = 1;  // 直线（上下或左右）
    private static final int PIPE_L = 2;         // L型弯
    private static final int PIPE_T = 3;         // T型
    private static final int PIPE_CROSS = 4;     // 十字

    // 管道符号（0=上, 1=右, 2=下, 3=左）
    private static final String[] PIPE_CHARS = {"│", "─", "│", "─"};
    private static final String[] PIPE_L_CHARS = {"└", "┌", "┐", "┘"};
    private static final String[] PIPE_T_CHARS = {"├", "┬", "┤", "┴"};
    private static final String PIPE_CROSS_CHAR = "┼";

    // ==================== 游戏状态 ====================
    private int currentLevel = 1;
    private int gridSize = 5;
    private int[][] pipeTypes;    // 管道类型
    private int[][] pipeRotations; // 管道旋转（0-3）
    private int[][] targetRotations; // 目标旋转
    private int moveCount = 0;
    private long startTimeMs = 0;
    private boolean gameActive = false;
    private Random random = new Random();

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private MaterialButton[] pipeButtons;
    private MaterialButton btnStart;
    private MaterialButton btnCheck;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "pipeline";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_pipeline_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
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

    // ==================== 游戏视图创建 ====================

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_bg));
        root.setPadding(16, 16, 16, 16);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_pipeline_color_text));
        tvStatus.setPadding(0, 8, 0, 8);
        tvStatus.setText(R.string.game_pipeline_status_intro);

        // 统计
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_pipeline_color_stats));
        tvStats.setPadding(0, 4, 0, 12);
        tvStats.setText(R.string.game_pipeline_stats_initial);

        // 网格
        gridLayout = new GridLayout(this);
        gridLayout.setUseDefaultMargins(true);

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);

        btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_pipeline_start);
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_btn_start));
        btnStart.setTextColor(ContextCompat.getColor(this, R.color.game_pipeline_color_btn_start_text));
        btnStart.setOnClickListener(v -> startNewGame());

        btnCheck = new MaterialButton(this);
        btnCheck.setText(R.string.game_pipeline_check);
        btnCheck.setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_btn_check));
        btnCheck.setTextColor(ContextCompat.getColor(this, R.color.game_pipeline_color_btn_check_text));
        btnCheck.setVisibility(View.GONE);
        btnCheck.setOnClickListener(v -> checkConnection());

        buttonArea.addView(btnStart);
        buttonArea.addView(btnCheck);

        root.addView(tvStatus);
        root.addView(tvStats);
        root.addView(gridLayout);
        root.addView(buttonArea);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 开始新游戏
     */
    private void startNewGame() {
        btnStart.setVisibility(View.GONE);
        btnCheck.setVisibility(View.VISIBLE);
        gameActive = true;
        moveCount = 0;
        startTimeMs = System.currentTimeMillis();

        // 关卡难度
        if (currentLevel <= 2) {
            gridSize = 4;
        } else if (currentLevel <= 4) {
            gridSize = 5;
        } else {
            gridSize = 6;
        }

        // 生成管道布局
        generatePuzzle();

        // 重建网格
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridSize);
        gridLayout.setRowCount(gridSize);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellSize = (int) (screenWidth * 0.85 / gridSize);

        pipeButtons = new MaterialButton[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            final int index = i;
            int row = index / gridSize;
            int col = index % gridSize;

            MaterialButton btn = new MaterialButton(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(2, 2, 2, 2);
            btn.setLayoutParams(params);
            btn.setTextSize(18f);
            btn.setOnClickListener(v -> onPipeClick(index));

            // 设置初始颜色（随机旋转）
            if (pipeTypes[row][col] != PIPE_NONE) {
                pipeRotations[row][col] = random.nextInt(4);
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_pipe));
            } else {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_pipe_empty));
                btn.setEnabled(false);
            }

            pipeButtons[i] = btn;
            gridLayout.addView(btn);
        }

        updatePipeDisplay();
        tvStatus.setText(getString(R.string.game_pipeline_level_intro, currentLevel));
        updateStatsDisplay();
    }

    /**
     * 生成谜题
     */
    private void generatePuzzle() {
        pipeTypes = new int[gridSize][gridSize];
        targetRotations = new int[gridSize][gridSize];
        pipeRotations = new int[gridSize][gridSize];

        // 生成一条随机路径
        boolean[][] onPath = new boolean[gridSize][gridSize];
        int row = 0;
        int col = 0;
        onPath[row][col] = true;

        // 随机游走生成路径
        while (row < gridSize - 1 || col < gridSize - 1) {
            if (row == gridSize - 1) {
                col++;
            } else if (col == gridSize - 1) {
                row++;
            } else {
                if (random.nextBoolean()) {
                    row++;
                } else {
                    col++;
                }
            }
            onPath[row][col] = true;
        }

        // 为路径上的格子分配管道类型
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (!onPath[r][c]) {
                    // 随机添加一些非路径管道
                    if (random.nextInt(3) == 0) {
                        pipeTypes[r][c] = PIPE_STRAIGHT + random.nextInt(3);
                        targetRotations[r][c] = random.nextInt(4);
                    } else {
                        pipeTypes[r][c] = PIPE_NONE;
                    }
                }
            }
        }

        // 简化路径管道分配
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (onPath[r][c]) {
                    boolean hasUp = r > 0 && onPath[r - 1][c];
                    boolean hasDown = r < gridSize - 1 && onPath[r + 1][c];
                    boolean hasLeft = c > 0 && onPath[r][c - 1];
                    boolean hasRight = c < gridSize - 1 && onPath[r][c + 1];

                    int connections = (hasUp ? 1 : 0) + (hasDown ? 1 : 0) + (hasLeft ? 1 : 0) + (hasRight ? 1 : 0);

                    if (connections == 4) {
                        pipeTypes[r][c] = PIPE_CROSS;
                        targetRotations[r][c] = 0;
                    } else if (connections == 3) {
                        pipeTypes[r][c] = PIPE_T;
                        if (!hasUp) targetRotations[r][c] = 2;
                        else if (!hasRight) targetRotations[r][c] = 3;
                        else if (!hasDown) targetRotations[r][c] = 0;
                        else targetRotations[r][c] = 1;
                    } else if (connections == 2) {
                        if ((hasUp && hasDown) || (hasLeft && hasRight)) {
                            pipeTypes[r][c] = PIPE_STRAIGHT;
                            targetRotations[r][c] = (hasUp && hasDown) ? 0 : 1;
                        } else {
                            pipeTypes[r][c] = PIPE_L;
                            if (hasDown && hasRight) targetRotations[r][c] = 0;
                            else if (hasDown && hasLeft) targetRotations[r][c] = 3;
                            else if (hasUp && hasRight) targetRotations[r][c] = 1;
                            else targetRotations[r][c] = 2;
                        }
                    } else {
                        // 死胡同 - 用直线
                        pipeTypes[r][c] = PIPE_STRAIGHT;
                        targetRotations[r][c] = hasUp || hasDown ? 0 : 1;
                    }
                }
            }
        }
    }

    /**
     * 处理管道点击（旋转）
     */
    private void onPipeClick(int index) {
        if (!gameActive || isGamePaused) return;

        int row = index / gridSize;
        int col = index % gridSize;

        if (pipeTypes[row][col] == PIPE_NONE) return;

        // 旋转90°
        pipeRotations[row][col] = (pipeRotations[row][col] + 1) % 4;
        moveCount++;

        updatePipeDisplay();
        updateStatsDisplay();
    }

    /**
     * 更新管道显示
     */
    private void updatePipeDisplay() {
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int index = r * gridSize + c;
                MaterialButton btn = pipeButtons[index];

                switch (pipeTypes[r][c]) {
                    case PIPE_NONE:
                        btn.setText("");
                        break;
                    case PIPE_STRAIGHT:
                        btn.setText(PIPE_CHARS[pipeRotations[r][c]]);
                        break;
                    case PIPE_L:
                        btn.setText(PIPE_L_CHARS[pipeRotations[r][c]]);
                        break;
                    case PIPE_T:
                        btn.setText(PIPE_T_CHARS[pipeRotations[r][c]]);
                        break;
                    case PIPE_CROSS:
                        btn.setText(PIPE_CROSS_CHAR);
                        break;
                }
            }
        }
    }

    /**
     * 检查连接
     */
    private void checkConnection() {
        if (!gameActive) return;

        boolean allCorrect = true;
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (pipeTypes[r][c] != PIPE_NONE && pipeTypes[r][c] != PIPE_CROSS) {
                    if (pipeRotations[r][c] != targetRotations[r][c]) {
                        allCorrect = false;
                        break;
                    }
                }
            }
            if (!allCorrect) break;
        }

        if (allCorrect) {
            onLevelComplete();
        } else {
            tvStatus.setText(R.string.game_pipeline_not_connected);
            // 高亮错误的管道
            for (int r = 0; r < gridSize; r++) {
                for (int c = 0; c < gridSize; c++) {
                    int index = r * gridSize + c;
                    if (pipeTypes[r][c] != PIPE_NONE && pipeTypes[r][c] != PIPE_CROSS) {
                        if (pipeRotations[r][c] != targetRotations[r][c]) {
                            pipeButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_pipe_error));
                        } else {
                            pipeButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_pipeline_color_pipe_correct));
                        }
                    }
                }
            }
        }
    }

    /**
     * 关卡通关
     */
    private void onLevelComplete() {
        gameActive = false;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;

        int score = Math.max(200 - moveCount * 5, 20) * currentLevel;
        currentScore += score;
        updateScore(score);

        tvStatus.setText(getString(R.string.game_pipeline_level_complete, currentLevel, moveCount));

        checkAchievement("win", currentLevel);
        checkAchievement("score", moveCount);
        checkAchievement("time", (int) elapsedSec);
        checkAchievement("rounds", currentLevel);

        if (elapsedSec <= 30) {
            checkAchievement("special", 1);
        }

        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);

        currentLevel++;

        btnCheck.setVisibility(View.GONE);
        btnStart.setText(getString(R.string.game_pipeline_next_level, currentLevel));
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText(getString(R.string.game_pipeline_stats, currentLevel, moveCount));
    }
}

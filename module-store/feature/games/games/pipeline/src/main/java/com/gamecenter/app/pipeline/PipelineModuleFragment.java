package com.gamecenter.app.pipeline;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

/**
 * 管道工游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 PipelineActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。游戏逻辑由 {@link PipelineGame} 承载，
 * 不含成就系统，仅保留基本游戏功能。</p>
 */
public class PipelineModuleFragment extends Fragment {

    private static final String GAME_ID = "pipeline";

    // 主题感知颜色（在 onCreateView 中初始化）
    private int colorBg;
    private int colorText;
    private int colorStats;
    private int colorPipe;
    private int colorPipeEmpty;
    private int colorPipeError;
    private int colorPipeCorrect;
    private int colorBtnStart;
    private int colorBtnCheck;

    // UI 组件
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private Button[] pipeButtons;
    private Button btnStart;
    private Button btnCheck;

    // 游戏逻辑
    private PipelineGame game;
    private GameUsageStore usageStore;
    private long startTimeMs = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        initColors();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));

        // 状态文本
        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(colorText);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        tvStatus.setText("旋转管道，将起点连接到终点");

        // 统计
        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(colorStats);
        tvStats.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
        tvStats.setText("关卡 1 | 步数 0");

        // 网格
        gridLayout = new GridLayout(ctx);
        gridLayout.setUseDefaultMargins(true);

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(ctx);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);

        btnStart = new Button(ctx);
        btnStart.setText("开始");
        btnStart.setBackgroundColor(colorBtnStart);
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        btnCheck = new Button(ctx);
        btnCheck.setText("检查连接");
        btnCheck.setBackgroundColor(colorBtnCheck);
        btnCheck.setTextColor(Color.WHITE);
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        game = new PipelineGame();
    }

    private void initColors() {
        boolean dark = isNightMode();
        colorBg = dark ? 0xFF121622 : 0xFFFAFAFA;
        colorText = dark ? 0xFFE4E6F0 : 0xFF212121;
        colorStats = dark ? 0xFFAAAAAA : 0xFF757575;
        colorPipe = dark ? 0xFF3949AB : 0xFF3F51B5;
        colorPipeEmpty = dark ? 0xFF2A2D3A : 0xFFE0E0E0;
        colorPipeError = 0xFFE53935;
        colorPipeCorrect = 0xFF43A047;
        colorBtnStart = 0xFF3949AB;
        colorBtnCheck = 0xFF43A047;
    }

    // ==================== 游戏流程 ====================

    private void startNewGame() {
        btnStart.setVisibility(View.GONE);
        btnCheck.setVisibility(View.VISIBLE);
        startTimeMs = System.currentTimeMillis();
        game.startLevel();
        rebuildGrid();
        tvStatus.setText("第 " + game.getCurrentLevel() + " 关");
        updateStatsDisplay();
    }

    private void rebuildGrid() {
        Context ctx = requireContext();
        int gridSize = game.getGridSize();
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridSize);
        gridLayout.setRowCount(gridSize);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellSize = (int) (screenWidth * 0.85 / gridSize);

        pipeButtons = new Button[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            final int index = i;
            int row = index / gridSize;
            int col = index % gridSize;

            Button btn = new Button(ctx);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(2, 2, 2, 2);
            btn.setLayoutParams(params);
            btn.setTextSize(18f);
            btn.setOnClickListener(v -> onPipeClick(index));

            if (game.getPipeType(row, col) != PipelineGame.PIPE_NONE) {
                game.randomizeRotation(row, col);
                btn.setBackgroundColor(colorPipe);
            } else {
                btn.setBackgroundColor(colorPipeEmpty);
                btn.setEnabled(false);
            }

            pipeButtons[i] = btn;
            gridLayout.addView(btn);
        }
        updatePipeDisplay();
    }

    private void onPipeClick(int index) {
        if (!game.isGameActive()) return;
        int gridSize = game.getGridSize();
        int row = index / gridSize;
        int col = index % gridSize;
        game.rotatePipe(row, col);
        updatePipeDisplay();
        updateStatsDisplay();
    }

    private void updatePipeDisplay() {
        int gridSize = game.getGridSize();
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int index = r * gridSize + c;
                pipeButtons[index].setText(game.getPipeChar(r, c));
            }
        }
    }

    private void checkConnection() {
        if (!game.isGameActive()) return;
        int gridSize = game.getGridSize();

        if (game.isAllCorrect()) {
            onLevelComplete();
        } else {
            tvStatus.setText("管道未连通，请继续调整");
            for (int r = 0; r < gridSize; r++) {
                for (int c = 0; c < gridSize; c++) {
                    int index = r * gridSize + c;
                    if (game.getPipeType(r, c) != PipelineGame.PIPE_NONE
                            && game.getPipeType(r, c) != PipelineGame.PIPE_CROSS) {
                        pipeButtons[index].setBackgroundColor(
                                game.isPipeCorrect(r, c) ? colorPipeCorrect : colorPipeError);
                    }
                }
            }
        }
    }

    private void onLevelComplete() {
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        int score = game.completeLevel();
        tvStatus.setText("第 " + (game.getCurrentLevel() - 1) + " 关通关！步数 " + game.getMoveCount());

        if (usageStore != null) {
            usageStore.recordWin(GAME_ID);
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }

        btnCheck.setVisibility(View.GONE);
        btnStart.setText("下一关 (" + game.getCurrentLevel() + ")");
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText("关卡 " + game.getCurrentLevel() + " | 步数 " + game.getMoveCount());
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
        // 事件驱动游戏，无需特殊处理
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}

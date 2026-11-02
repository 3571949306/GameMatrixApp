package com.gamecenter.app.pipeline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

/**
 * 管道工游戏 Fragment（五星级即开即玩现代化升级）。
 *
 * <p>具备标准统一导航栏、退出确认、战况 HUD、规则说明与安全自适应尺寸。</p>
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
    private TextView tvRouteHint;
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
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(colorBg);
        root.setPadding(0, 0, 0, (int) (8 * dp));

        // 1. 顶部标准导航栏
        View navBar = createNavBar(ctx, dp);
        root.addView(navBar);

        // 2. HUD 战况与提示栏
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding((int) (12 * dp), (int) (4 * dp), (int) (12 * dp), (int) (4 * dp));

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(15f);
        tvStatus.setTextColor(colorText);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setPadding(0, (int) (2 * dp), 0, (int) (2 * dp));
        tvStatus.setText("旋转管道，将起点连接到终点");

        tvRouteHint = new TextView(ctx);
        tvRouteHint.setGravity(Gravity.CENTER);
        tvRouteHint.setTextSize(13f);
        tvRouteHint.setTextColor(colorStats);
        tvRouteHint.setText("左上起点 → 右下终点");

        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(13f);
        tvStats.setTextColor(colorStats);
        tvStats.setPadding(0, (int) (2 * dp), 0, (int) (6 * dp));
        tvStats.setText("关卡 1 | 步数 0");

        hud.addView(tvStatus);
        hud.addView(tvRouteHint);
        hud.addView(tvStats);
        root.addView(hud);

        // 3. 管道网格容器 (自适应居中)
        LinearLayout gridWrapper = new LinearLayout(ctx);
        gridWrapper.setOrientation(LinearLayout.VERTICAL);
        gridWrapper.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gridWrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        gridWrapper.setLayoutParams(gridWrapLp);

        gridLayout = new GridLayout(ctx);
        gridLayout.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.gravity = Gravity.CENTER;
        gridLayout.setLayoutParams(gridLp);
        gridWrapper.addView(gridLayout);
        root.addView(gridWrapper);

        // 4. 底部胶囊按钮区域
        LinearLayout buttonArea = new LinearLayout(ctx);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.setPadding((int) (16 * dp), (int) (6 * dp), (int) (16 * dp), (int) (10 * dp));

        btnStart = new Button(ctx);
        btnStart.setText("开始游戏");
        btnStart.setBackgroundColor(colorBtnStart);
        btnStart.setTextColor(Color.WHITE);
        ensureMinimumTouchSize(btnStart);
        applyCapsuleStyle(btnStart, colorBtnStart, dp);
        btnStart.setOnClickListener(v -> startNewGame());

        btnCheck = new Button(ctx);
        btnCheck.setText("🔍 检查连通");
        btnCheck.setBackgroundColor(colorBtnCheck);
        btnCheck.setTextColor(Color.WHITE);
        ensureMinimumTouchSize(btnCheck);
        applyCapsuleStyle(btnCheck, colorBtnCheck, dp);
        btnCheck.setVisibility(View.GONE);
        btnCheck.setOnClickListener(v -> checkConnection());

        buttonArea.addView(btnStart);
        buttonArea.addView(btnCheck);
        root.addView(buttonArea);

        return root;
    }

    private View createNavBar(Context ctx, float dp) {
        boolean dark = isNightMode();
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        bar.setBackgroundColor(dark ? 0xFF1E222D : 0xFFFFFFFF);

        // 返回胶囊
        Button btnBack = new Button(ctx);
        btnBack.setText("❮ 返回");
        btnBack.setTextSize(13f);
        btnBack.setTextColor(colorText);
        applyCapsuleStyle(btnBack, dark ? 0xFF2A2E3D : 0xFFEEEEEE, dp);
        btnBack.setOnClickListener(v -> showExitConfirmDialog());
        bar.addView(btnBack);

        // 居中标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("🔧 接管道");
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(colorText);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        bar.addView(tvTitle, titleLp);

        // 规则按钮
        Button btnRule = new Button(ctx);
        btnRule.setText("ℹ");
        btnRule.setTextSize(14f);
        btnRule.setTextColor(colorText);
        applyCapsuleStyle(btnRule, dark ? 0xFF2A2E3D : 0xFFEEEEEE, dp);
        btnRule.setOnClickListener(v -> showRuleDialog());
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        ruleLp.setMargins(0, 0, (int) (6 * dp), 0);
        bar.addView(btnRule, ruleLp);

        // 设置按钮
        Button btnSettings = new Button(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(14f);
        btnSettings.setTextColor(colorText);
        applyCapsuleStyle(btnSettings, dark ? 0xFF2A2E3D : 0xFFEEEEEE, dp);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        bar.addView(btnSettings, setLp);

        return bar;
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出确认")
                .setMessage("当前关卡进度已保留，确定要返回大厅吗？")
                .setPositiveButton("确定退出", (d, w) -> {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                })
                .setNegativeButton("继续连接", null)
                .show();
    }

    private void showRuleDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("🔧 接管道规则说明")
                .setMessage("1. 目标：\n"
                        + "从左上角的【起点】顺畅接通到右下角的【终点】。\n\n"
                        + "2. 操作方式：\n"
                        + "轻触任意管道格子可顺时针旋转 90 度，调整接头方向。\n\n"
                        + "3. 检验与通关：\n"
                        + "铺设完成后，轻触底部【检查连通】。连通成功将变绿并进入下一关；断开处将标红提示。")
                .setPositiveButton("我知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("设置与选项")
                .setItems(new String[]{"重新生成本关", "查看玩法规则", "关于管道工"}, (dialog, which) -> {
                    if (which == 0) {
                        startNewGame();
                    } else if (which == 1) {
                        showRuleDialog();
                    } else if (which == 2) {
                        Toast.makeText(requireContext(), "经典接管道小游戏 v1.0", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
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

        float dp = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        // 保障 360dp 下 48dp 按钮完全容纳在父容器内
        int totalMargin = (gridSize - 1) * (int) (2 * dp);
        int availableWidth = screenWidth - (int) (32 * dp) - totalMargin;
        int cellSize = Math.max((int) (48 * dp), availableWidth / gridSize);

        pipeButtons = new Button[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            final int index = i;
            int row = index / gridSize;
            int col = index % gridSize;

            Button btn = new Button(ctx);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins((int) (1 * dp), (int) (1 * dp), (int) (1 * dp), (int) (1 * dp));
            btn.setLayoutParams(params);
            btn.setTextSize(18f);
            btn.setTextColor(Color.WHITE);
            ensureMinimumTouchSize(btn);
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
        tvStatus.setText("第 " + game.getCurrentLevel() + " 关");
        updateStatsDisplay();
    }

    private void updatePipeDisplay() {
        int gridSize = game.getGridSize();
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int index = r * gridSize + c;
                String pipe = game.getPipeChar(r, c);
                pipeButtons[index].setText(pipe);
                String role = r == 0 && c == 0 ? "起点，"
                        : r == gridSize - 1 && c == gridSize - 1 ? "终点，" : "";
                pipeButtons[index].setContentDescription(role + "第 " + (r + 1) + " 行第 " + (c + 1)
                        + " 列，" + (pipe.isEmpty() ? "空格" : "管道 " + pipe));
                // 新棋盘或任意旋转都会使上一次检查结果失效。
                pipeButtons[index].setBackgroundColor(game.getPipeType(r, c) == PipelineGame.PIPE_NONE
                        ? colorPipeEmpty : colorPipe);
            }
        }
    }

    private void checkConnection() {
        if (!game.isGameActive()) return;
        int gridSize = game.getGridSize();

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (game.getPipeType(r, c) != PipelineGame.PIPE_NONE) {
                    pipeButtons[r * gridSize + c].setBackgroundColor(
                            game.isPipeCorrect(r, c) ? colorPipeCorrect : colorPipeError);
                }
            }
        }
        if (game.isAllCorrect()) {
            onLevelComplete();
        } else {
            tvStatus.setText("管道未连通，请继续调整");
        }
    }

    private void onLevelComplete() {
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        int score = game.completeLevel();
        tvStatus.setText("第 " + (game.getCurrentLevel() - 1) + " 关通关！步数 " + game.getMoveCount());

        if (usageStore != null) {
            usageStore.recordWin(GAME_ID);
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
            usageStore.recordScore(GAME_ID, game.getTotalScore());
        }

        btnCheck.setVisibility(View.GONE);
        btnStart.setText("下一关 (" + game.getCurrentLevel() + ")");
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText("关卡 " + game.getCurrentLevel() + " | 步数 " + game.getMoveCount());
    }

    private void applyCapsuleStyle(View view, int bgColor, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(18 * dp);
        gd.setColor(bgColor);
        view.setBackground(gd);
    }

    private void ensureMinimumTouchSize(Button button) {
        int size = (int) Math.ceil(48 * button.getResources().getDisplayMetrics().density);
        button.setMinWidth(size);
        button.setMinimumWidth(size);
        button.setMinHeight(size);
        button.setMinimumHeight(size);
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}

package com.gamecenter.app.sokoban;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;

/**
 * 推箱子游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 SokobanActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。游戏逻辑由 {@link SokobanGame} 承载，
 * 渲染由 {@link SokobanView} 负责，不含成就系统，仅保留基本游戏功能。</p>
 */
public class SokobanModuleFragment extends Fragment {

    private static final String GAME_ID = "sokoban";

    // 主题感知颜色
    private int colorBg;
    private int colorText;
    private int colorLevel;
    private int colorMoves;
    private int colorBtnLevel;
    private int colorBtnDir;

    // UI 组件
    private TextView tvStatus;
    private TextView tvLevel;
    private TextView tvMoves;
    private LinearLayout menuPanel;
    private LinearLayout gamePanel;
    private SokobanView sokobanView;

    // 游戏逻辑
    private SokobanGame game;
    private GameUsageStore usageStore;
    private long gameStartTime = 0;

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
        root.setPadding(0, (int) (16 * dp), 0, (int) (16 * dp));

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(colorText);
        tvStatus.setPadding(0, (int) (16 * dp), 0, (int) (8 * dp));

        tvLevel = new TextView(ctx);
        tvLevel.setGravity(Gravity.CENTER);
        tvLevel.setTextSize(14f);
        tvLevel.setTextColor(colorLevel);

        tvMoves = new TextView(ctx);
        tvMoves.setGravity(Gravity.CENTER);
        tvMoves.setTextSize(14f);
        tvMoves.setTextColor(colorMoves);
        tvMoves.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));

        // 菜单面板（关卡选择，用 ScrollView 包裹避免溢出）
        ScrollView menuScroll = new ScrollView(ctx);
        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);
        for (int i = 1; i <= SokobanGame.TOTAL_LEVELS; i++) {
            final int level = i;
            Button btn = new Button(ctx);
            btn.setText(getString(R.string.game_level_format, i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, (int) (4 * dp), 0, (int) (4 * dp));
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(colorBtnLevel);
            btn.setTextColor(Color.WHITE);
            btn.setOnClickListener(v -> startLevel(level));
            menuPanel.addView(btn);
        }
        menuScroll.addView(menuPanel);

        // 游戏面板
        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        sokobanView = new SokobanView(ctx);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        sokobanView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 方向控制按钮
        LinearLayout controlPanel = new LinearLayout(ctx);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setGravity(Gravity.CENTER);
        controlPanel.setPadding(0, (int) (16 * dp), 0, 0);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER);
        Button btnUp = createDirectionButton(ctx, "↑");
        btnUp.setOnClickListener(v -> movePlayer(-1, 0));
        topRow.addView(createSpacer(ctx));
        topRow.addView(btnUp);
        topRow.addView(createSpacer(ctx));

        LinearLayout midRow = new LinearLayout(ctx);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        Button btnLeft = createDirectionButton(ctx, "←");
        btnLeft.setOnClickListener(v -> movePlayer(0, -1));
        Button btnDown = createDirectionButton(ctx, "↓");
        btnDown.setOnClickListener(v -> movePlayer(1, 0));
        Button btnRight = createDirectionButton(ctx, "→");
        btnRight.setOnClickListener(v -> movePlayer(0, 1));
        midRow.addView(btnLeft);
        midRow.addView(btnDown);
        midRow.addView(btnRight);

        controlPanel.addView(topRow);
        controlPanel.addView(midRow);

        // 底部按钮
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, (int) (12 * dp), 0, 0);

        Button btnUndo = new Button(ctx);
        btnUndo.setText(getString(R.string.game_btn_undo));
        btnUndo.setOnClickListener(v -> undoMove());

        Button btnReset = new Button(ctx);
        btnReset.setText(getString(R.string.game_btn_reset));
        btnReset.setOnClickListener(v -> startLevel(game.getCurrentLevel()));

        Button btnMenu = new Button(ctx);
        btnMenu.setText(getString(R.string.game_btn_back_menu));
        btnMenu.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), 0);
        btnUndo.setLayoutParams(btnLp);
        btnReset.setLayoutParams(btnLp);
        btnMenu.setLayoutParams(btnLp);

        btnRow.addView(btnUndo);
        btnRow.addView(btnReset);
        btnRow.addView(btnMenu);

        gamePanel.addView(sokobanView);
        gamePanel.addView(controlPanel);
        gamePanel.addView(btnRow);

        root.addView(tvStatus);
        root.addView(tvLevel);
        root.addView(tvMoves);
        root.addView(menuScroll);
        root.addView(gamePanel);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        game = new SokobanGame();
        showMenu();
    }

    private void initColors() {
        boolean dark = isNightMode();
        colorBg = dark ? 0xFF121622 : 0xFFFAFAFA;
        colorText = dark ? 0xFFE4E6F0 : 0xFF212121;
        colorLevel = dark ? 0xFF90CAF9 : 0xFF1976D2;
        colorMoves = dark ? 0xFFAAAAAA : 0xFF757575;
        colorBtnLevel = dark ? 0xFF3949AB : 0xFF3F51B5;
        colorBtnDir = dark ? 0xFF3949AB : 0xFF3F51B5;
    }

    private Button createDirectionButton(Context ctx, String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(20f);
        int size = getResources().getDisplayMetrics().widthPixels / 6;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins((int) (8 * dp(ctx)), (int) (4 * dp(ctx)), (int) (8 * dp(ctx)), (int) (4 * dp(ctx)));
        btn.setLayoutParams(lp);
        btn.setBackgroundColor(colorBtnDir);
        btn.setTextColor(Color.WHITE);
        return btn;
    }

    private View createSpacer(Context ctx) {
        View spacer = new View(ctx);
        int size = getResources().getDisplayMetrics().widthPixels / 6;
        spacer.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return spacer;
    }

    private float dp(Context ctx) {
        return ctx.getResources().getDisplayMetrics().density;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(getString(R.string.game_select_level));
        tvLevel.setText("");
        tvMoves.setText("");
    }

    private void startLevel(int level) {
        game.startLevel(level);
        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.game_sokoban_status));
        tvLevel.setText(getString(R.string.game_level_format, level));
        updateMovesDisplay();
        sokobanView.setMap(game.getMap());
        gameStartTime = System.currentTimeMillis();
    }

    private void movePlayer(int dr, int dc) {
        if (!game.isRunning()) return;
        boolean moved = game.movePlayer(dr, dc);
        if (moved) {
            sokobanView.setMap(game.getMap());
            updateMovesDisplay();
            if (game.isLevelComplete()) {
                onLevelComplete();
            }
        }
    }

    private void undoMove() {
        if (!game.isRunning()) return;
        if (game.undoMove()) {
            sokobanView.setMap(game.getMap());
            updateMovesDisplay();
        }
    }

    private void onLevelComplete() {
        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        long elapsedSec = elapsedMs / 1000;
        game.onLevelComplete();

        tvStatus.setText(getString(R.string.game_sokoban_win_format, game.getMoveCount())
                + " | 推动 " + game.getPushCount() + " | 用时 " + elapsedSec + "s");

        if (usageStore != null) {
            usageStore.recordWin(GAME_ID);
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }
    }

    private void updateMovesDisplay() {
        tvMoves.setText(getString(R.string.game_sokoban_moves_format, game.getMoveCount(), game.getPushCount()));
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
        if (game != null) {
            game.stop();
        }
    }
}

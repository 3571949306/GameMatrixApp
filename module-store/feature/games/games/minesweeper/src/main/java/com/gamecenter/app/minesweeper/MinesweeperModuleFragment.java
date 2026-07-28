package com.gamecenter.app.minesweeper;

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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;

/**
 * 扫雷游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 MinesweeperActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。游戏渲染与逻辑由 {@link MinesweeperView}
 * 自包含处理，{@link MinesweeperGame} 提供难度与状态配置，不含成就系统，
 * 仅保留基本游戏功能。</p>
 */
public class MinesweeperModuleFragment extends Fragment {

    private static final String GAME_ID = "minesweeper";

    private MinesweeperView minesweeperView;
    private MinesweeperGame game;
    private GameUsageStore usageStore;

    private TextView tvStatus;
    private TextView tvMines;
    private TextView tvWins;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;
    private Button btnRestart;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int colorBg = isNightMode() ? 0xFF121622 : 0xFFF5F5F5;
        int colorTextPrimary = isNightMode() ? 0xFFE4E6F0 : 0xFF212121;
        int colorTextSecondary = isNightMode() ? 0xFFAAAAAA : 0xFF757575;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_minesweeper));
        tvTitle.setTextSize(24);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 状态栏
        LinearLayout statBar = new LinearLayout(ctx);
        statBar.setOrientation(LinearLayout.HORIZONTAL);
        statBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statBarLp.topMargin = (int) (8 * dp);
        statBar.setLayoutParams(statBarLp);

        tvMines = new TextView(ctx);
        tvMines.setTextSize(16);
        tvMines.setTextColor(colorTextPrimary);
        tvMines.setText(getString(R.string.game_mines_remaining_init));
        LinearLayout.LayoutParams minesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        minesLp.rightMargin = (int) (16 * dp);
        tvMines.setLayoutParams(minesLp);
        statBar.addView(tvMines);

        tvWins = new TextView(ctx);
        tvWins.setTextSize(16);
        tvWins.setTextColor(colorTextSecondary);
        tvWins.setText(getString(R.string.game_wins_init));
        statBar.addView(tvWins);
        root.addView(statBar);

        // 状态文本
        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(14f);
        tvStatus.setTextColor(colorTextSecondary);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        tvStatus.setText(getString(R.string.game_mines_status_init));
        root.addView(tvStatus);

        // 难度按钮
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.topMargin = (int) (4 * dp);
        diffBarLp.bottomMargin = (int) (4 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = new Button(ctx);
        btnEasy.setText(getString(R.string.game_diff_easy));
        btnEasy.setTextSize(12);
        btnEasy.setOnClickListener(v -> setDifficulty(MinesweeperGame.DIFF_EASY));
        diffBar.addView(btnEasy);

        btnNormal = new Button(ctx);
        btnNormal.setText(getString(R.string.game_diff_normal));
        btnNormal.setTextSize(12);
        btnNormal.setOnClickListener(v -> setDifficulty(MinesweeperGame.DIFF_NORMAL));
        diffBar.addView(btnNormal);

        btnHard = new Button(ctx);
        btnHard.setText(getString(R.string.game_diff_hard));
        btnHard.setTextSize(12);
        btnHard.setOnClickListener(v -> setDifficulty(MinesweeperGame.DIFF_HARD));
        diffBar.addView(btnHard);
        root.addView(diffBar);

        // 游戏容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = (int) (8 * dp);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        minesweeperView = new MinesweeperView(ctx);
        minesweeperView.setOnGameWinListener(elapsedSeconds -> {
            if (usageStore != null) {
                usageStore.recordWin(GAME_ID);
            }
            game.recordWin(100);
            tvStatus.setText(getString(R.string.game_mines_win_format, elapsedSeconds));
            updateWinsDisplay();
        });
        minesweeperView.setOnGameLoseListener(() -> {
            if (usageStore != null) {
                usageStore.recordLoss(GAME_ID);
            }
            game.recordLoss();
            tvStatus.setText(getString(R.string.game_mines_lose));
        });
        gameContainer.addView(minesweeperView);

        // 重新开始按钮
        btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = (int) (8 * dp);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> startGame());
        root.addView(btnRestart);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        game = new MinesweeperGame();
        updateDifficultyButtons();
        updateWinsDisplay();
        startGame();
    }

    private void startGame() {
        if (minesweeperView == null) return;
        minesweeperView.setDifficulty(game.getDifficulty());
        minesweeperView.startGame();
        tvStatus.setText(getString(R.string.game_mines_status_init));
        updateMinesDisplay();
    }

    private void setDifficulty(int level) {
        game.setDifficulty(level);
        updateDifficultyButtons();
        startGame();
    }

    private void updateDifficultyButtons() {
        if (btnEasy == null || game == null) return;
        int active = 0xFF3949AB;
        int inactive = isNightMode() ? 0xFF333A4D : 0xFFE0E0E0;
        int cur = game.getDifficulty();
        btnEasy.setBackgroundColor(cur == MinesweeperGame.DIFF_EASY ? active : inactive);
        btnNormal.setBackgroundColor(cur == MinesweeperGame.DIFF_NORMAL ? active : inactive);
        btnHard.setBackgroundColor(cur == MinesweeperGame.DIFF_HARD ? active : inactive);
        btnEasy.setTextColor(Color.WHITE);
        btnNormal.setTextColor(Color.WHITE);
        btnHard.setTextColor(Color.WHITE);
    }

    private void updateMinesDisplay() {
        if (tvMines != null && minesweeperView != null) {
            int remaining = minesweeperView.getMineCount() - minesweeperView.getFlaggedCount();
            tvMines.setText(getString(R.string.game_mines_remaining_format, remaining));
        }
    }

    private void updateWinsDisplay() {
        if (tvWins != null && game != null) {
            tvWins.setText(getString(R.string.game_wins_format, game.getWins()));
        }
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (minesweeperView != null) {
            minesweeperView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (minesweeperView != null) {
            minesweeperView.resumeGame();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (minesweeperView != null) {
            minesweeperView.stopGame();
        }
    }
}

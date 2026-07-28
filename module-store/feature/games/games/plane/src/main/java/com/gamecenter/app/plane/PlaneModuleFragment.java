package com.gamecenter.app.plane;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.gamecenter.app.games.GameUsageStore;

/**
 * 飞机大战游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 PlaneActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。难度选择以代码内按钮实现，
 * 不含成就系统，仅保留基本游戏功能。</p>
 */
public class PlaneModuleFragment extends Fragment {

    private static final String GAME_ID = "plane";
    private static final long FRAME_INTERVAL_MS = 16;

    private PlaneView planeView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int highScore = 0;
    private int totalGames = 0;
    private float difficultyFactor = 0.5f;

    private TextView tvScore;
    private TextView tvWave;
    private TextView tvBest;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;
    private Button btnRestart;
    private GameUsageStore usageStore;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (planeView != null && planeView.isGameRunning()) {
                planeView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

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
        tvTitle.setText("飞机大战");
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

        tvScore = new TextView(ctx);
        tvScore.setTextSize(16);
        tvScore.setTextColor(colorTextPrimary);
        tvScore.setText("分: 0");
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.rightMargin = (int) (16 * dp);
        tvScore.setLayoutParams(scoreLp);
        statBar.addView(tvScore);

        tvWave = new TextView(ctx);
        tvWave.setTextSize(16);
        tvWave.setTextColor(colorTextPrimary);
        tvWave.setText("波次 1");
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        waveLp.rightMargin = (int) (16 * dp);
        tvWave.setLayoutParams(waveLp);
        statBar.addView(tvWave);

        tvBest = new TextView(ctx);
        tvBest.setTextSize(16);
        tvBest.setTextColor(colorTextSecondary);
        tvBest.setText("最高分: 0");
        statBar.addView(tvBest);
        root.addView(statBar);

        // 难度按钮
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.topMargin = (int) (8 * dp);
        diffBarLp.bottomMargin = (int) (4 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = new Button(ctx);
        btnEasy.setText("简单");
        btnEasy.setTextSize(12);
        btnEasy.setOnClickListener(v -> setDifficulty(0.3f));
        diffBar.addView(btnEasy);

        btnNormal = new Button(ctx);
        btnNormal.setText("普通");
        btnNormal.setTextSize(12);
        btnNormal.setOnClickListener(v -> setDifficulty(0.5f));
        diffBar.addView(btnNormal);

        btnHard = new Button(ctx);
        btnHard.setText("困难");
        btnHard.setTextSize(12);
        btnHard.setOnClickListener(v -> setDifficulty(0.8f));
        diffBar.addView(btnHard);
        root.addView(diffBar);

        // 游戏容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = (int) (8 * dp);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        planeView = new PlaneView(ctx);
        applyDifficulty();
        planeView.setOnGameListener(new PlaneView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText("分: " + score);
            }

            @Override
            public void onGameOver(int score) {
                handler.removeCallbacks(gameLoop);
                totalGames++;
                if (score > highScore) {
                    highScore = score;
                }
                if (usageStore != null) {
                    usageStore.recordScore(GAME_ID, highScore);
                    usageStore.recordLoss(GAME_ID);
                }
                tvBest.setText("最高分: " + highScore);
            }
        });
        gameContainer.addView(planeView);

        // 重新开始按钮
        btnRestart = new Button(ctx);
        btnRestart.setText("重新开始");
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
        highScore = usageStore.getHighScore(GAME_ID);
        if (tvBest != null) {
            tvBest.setText("最高分: " + highScore);
        }
        updateDifficultyButtons();
        startGame();
    }

    private void startGame() {
        if (planeView == null) return;
        planeView.startGame(1);
        if (tvScore != null) tvScore.setText("分: 0");
        if (tvWave != null) tvWave.setText("波次 1");
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    private void setDifficulty(float factor) {
        difficultyFactor = factor;
        applyDifficulty();
        updateDifficultyButtons();
    }

    private void applyDifficulty() {
        if (planeView == null) return;
        planeView.setDifficultyFactor(difficultyFactor);
    }

    private void updateDifficultyButtons() {
        if (btnEasy == null) return;
        int active = 0xFF3949AB;
        int inactive = isNightMode() ? 0xFF333A4D : 0xFFE0E0E0;
        btnEasy.setBackgroundColor(difficultyFactor == 0.3f ? active : inactive);
        btnNormal.setBackgroundColor(difficultyFactor == 0.5f ? active : inactive);
        btnHard.setBackgroundColor(difficultyFactor == 0.8f ? active : inactive);
        btnEasy.setTextColor(Color.WHITE);
        btnNormal.setTextColor(Color.WHITE);
        btnHard.setTextColor(Color.WHITE);
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(gameLoop);
        if (planeView != null) {
            planeView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (planeView != null && planeView.isGameRunning()) {
            planeView.resumeGame();
            handler.post(gameLoop);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (planeView != null) {
            planeView.stopGame();
        }
    }
}

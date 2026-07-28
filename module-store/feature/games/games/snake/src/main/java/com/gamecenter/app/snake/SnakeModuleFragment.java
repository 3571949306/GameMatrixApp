package com.gamecenter.app.snake;

import android.content.Context;
import android.content.res.Configuration;
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

import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 贪吃蛇模块 Fragment。
 *
 * <p>从原 {@code SnakeActivity} 迁移而来，使用代码构建 UI（纯 Android widget），
 * 支持浅色/深色主题，不依赖 R.layout 资源。</p>
 */
public class SnakeModuleFragment extends Fragment {

    private static final String GAME_ID = "snake";
    private static final String SLOT_AUTO = "auto";

    private SnakeView snakeView;
    private TextView tvScore;
    private TextView tvHighScore;
    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private int highScore;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF121212 : 0xFFF5F5F5);
        root.setPadding(0, (int) (28 * dp), 0, 0);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("贪吃蛇");
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(dark ? 0xFFEEEEEE : 0xFF212121);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.topMargin = (int) (16 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        tvScore = new TextView(ctx);
        tvScore.setTextSize(18);
        tvScore.setTextColor(dark ? 0xFFBDBDBD : 0xFF757575);
        tvScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.gravity = Gravity.CENTER;
        scoreLp.topMargin = (int) (8 * dp);
        tvScore.setLayoutParams(scoreLp);
        root.addView(tvScore);

        tvHighScore = new TextView(ctx);
        tvHighScore.setTextSize(16);
        tvHighScore.setTextColor(dark ? 0xFF9E9E9E : 0xFF888888);
        tvHighScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams highScoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        highScoreLp.gravity = Gravity.CENTER;
        highScoreLp.topMargin = (int) (4 * dp);
        tvHighScore.setLayoutParams(highScoreLp);
        root.addView(tvHighScore);

        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.setMargins((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (8 * dp));
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        snakeView = new SnakeView(ctx);
        gameContainer.addView(snakeView);

        LinearLayout buttonBar = new LinearLayout(ctx);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), (int) (16 * dp));
        buttonBar.setLayoutParams(barLp);
        root.addView(buttonBar);

        Button btnRestart = new Button(ctx);
        btnRestart.setText("重新开始");
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                (int) (120 * dp), (int) (48 * dp));
        restartLp.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnRestart.setLayoutParams(restartLp);
        buttonBar.addView(btnRestart);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        highScore = loadHighScore();

        snakeView.setOnScoreChangeListener(score -> {
            tvScore.setText("分数: " + score);
            if (score > highScore) {
                highScore = score;
                saveHighScore(highScore);
            }
            tvHighScore.setText("最高分: " + highScore);
        });
        snakeView.setOnGameOverListener(score -> {
            usageStore.recordScore(GAME_ID, Math.max(highScore, score));
        });

        tvScore.setText("分数: 0");
        tvHighScore.setText("最高分: " + highScore);
        snakeView.startGame();

        LinearLayout buttonBar = (LinearLayout) ((LinearLayout) view).getChildAt(4);
        Button btnRestart = (Button) buttonBar.getChildAt(0);
        btnRestart.setOnClickListener(v -> {
            snakeView.startGame();
            tvScore.setText("分数: 0");
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (snakeView != null) snakeView.pauseGame();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (snakeView != null && !snakeView.getGame().isGameOver()) snakeView.resumeGame();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (snakeView != null) snakeView.stopGame();
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int loadHighScore() {
        String json = saveManager.loadProgress(GAME_ID);
        if (json != null) {
            try {
                return new JSONObject(json).optInt("highScore", 0);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void saveHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception ignored) {
        }
    }
}

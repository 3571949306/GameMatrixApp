package com.gamecenter.app.tetris;

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

import com.gamecenter.app.R;

/**
 * 俄罗斯方块模块 Fragment。
 *
 * <p>将原 TetrisActivity 的 UI 与生命周期逻辑迁移到 Fragment，
 * 使用纯 Android widget（不依赖 R.layout），支持浅色/深色主题。
 * 难度选择、重新开始、暂停/恢复均在 Fragment 内用代码实现。</p>
 */
public class TetrisModuleFragment extends Fragment {

    private TetrisView tetrisView;
    private TetrisGame game;
    private TextView tvScore;
    private TextView tvHighScore;
    private TextView tvDifficulty;
    private LinearLayout difficultyBar;

    private int colorBg;
    private int colorTitle;
    private int colorSub;
    private int colorButtonBg;
    private int colorButtonText;
    private int colorButtonActiveBg;
    private int colorButtonActiveText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        applyThemeColors();

        game = new TetrisGame();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding(0, (int) (28 * dp), 0, 0);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_tetris));
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTitle);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.topMargin = (int) (16 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        tvDifficulty = new TextView(ctx);
        tvDifficulty.setTextSize(14);
        tvDifficulty.setTextColor(colorSub);
        tvDifficulty.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffLp.gravity = Gravity.CENTER;
        diffLp.topMargin = (int) (8 * dp);
        tvDifficulty.setLayoutParams(diffLp);
        root.addView(tvDifficulty);

        difficultyBar = new LinearLayout(ctx);
        difficultyBar.setOrientation(LinearLayout.HORIZONTAL);
        difficultyBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins((int) (16 * dp), (int) (4 * dp), (int) (16 * dp), 0);
        difficultyBar.setLayoutParams(barLp);
        root.addView(difficultyBar);

        for (int i = 0; i < TetrisGame.DIFFICULTY_NAMES.length; i++) {
            final int level = i + 1;
            Button btn = new Button(ctx);
            btn.setText(TetrisGame.DIFFICULTY_NAMES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (88 * dp), (int) (44 * dp));
            lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> setDifficulty(level));
            difficultyBar.addView(btn);
        }

        tvScore = new TextView(ctx);
        tvScore.setTextSize(18);
        tvScore.setTextColor(colorSub);
        tvScore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.gravity = Gravity.CENTER;
        scoreLp.topMargin = (int) (8 * dp);
        tvScore.setLayoutParams(scoreLp);
        root.addView(tvScore);

        tvHighScore = new TextView(ctx);
        tvHighScore.setTextSize(16);
        tvHighScore.setTextColor(colorSub);
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

        tetrisView = new TetrisView(ctx);
        gameContainer.addView(tetrisView);

        LinearLayout buttonBar = new LinearLayout(ctx);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnBarLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), (int) (16 * dp));
        buttonBar.setLayoutParams(btnBarLp);
        root.addView(buttonBar);

        Button btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
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

        tetrisView.setSpeedFactor(game.getSpeedFactor());

        tetrisView.setOnScoreChangeListener(score -> {
            game.setScore(score);
            updateScore();
        });

        tetrisView.setOnLinesClearedListener(lines -> {
            game.setLines(game.getLines() + lines);
        });

        tetrisView.setOnLevelChangeListener(level -> {
            game.setLevel(level);
        });

        tetrisView.setOnGameOverListener(finalScore -> {
            if (finalScore > game.getHighScore()) {
                game.setHighScore(finalScore);
            }
            updateScore();
        });

        updateDifficultyButtons();
        updateScore();

        LinearLayout root = (LinearLayout) view;
        LinearLayout buttonBar = (LinearLayout) root.getChildAt(root.getChildCount() - 1);
        Button btnRestart = (Button) buttonBar.getChildAt(0);
        btnRestart.setOnClickListener(v -> {
            game.resetRound();
            tetrisView.startGame();
            updateScore();
        });

        tetrisView.post(() -> tetrisView.startGame());
    }

    private void setDifficulty(int level) {
        game.setDifficultyLevel(level);
        tetrisView.setSpeedFactor(game.getSpeedFactor());
        updateDifficultyButtons();
        tvDifficulty.setText(getString(R.string.game_tetris_diff_format, TetrisGame.DIFFICULTY_NAMES[level - 1]));
    }

    private void updateDifficultyButtons() {
        int current = game.getDifficultyLevel();
        for (int i = 0; i < difficultyBar.getChildCount(); i++) {
            Button btn = (Button) difficultyBar.getChildAt(i);
            if (i + 1 == current) {
                btn.setBackgroundColor(colorButtonActiveBg);
                btn.setTextColor(colorButtonActiveText);
                tvDifficulty.setText(getString(R.string.game_tetris_diff_format, TetrisGame.DIFFICULTY_NAMES[i]));
            } else {
                btn.setBackgroundColor(colorButtonBg);
                btn.setTextColor(colorButtonText);
            }
        }
    }

    private void updateScore() {
        tvScore.setText(getString(R.string.game_score_alt_format, game.getScore()));
        tvHighScore.setText(getString(R.string.game_high_score_format, game.getHighScore()));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (tetrisView != null) {
            tetrisView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tetrisView != null) {
            tetrisView.resumeGame();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tetrisView != null) {
            tetrisView.stopGame();
        }
    }

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        if (isDark) {
            colorBg = 0xFF1B1E22;
            colorTitle = 0xFFE4E6F0;
            colorSub = 0xFF9AA0A6;
            colorButtonBg = 0xFF2A2E3A;
            colorButtonText = 0xFFE4E6F0;
            colorButtonActiveBg = 0xFF5B8A72;
            colorButtonActiveText = 0xFFFFFFFF;
        } else {
            colorBg = 0xFFF5F5F5;
            colorTitle = 0xFF212121;
            colorSub = 0xFF757575;
            colorButtonBg = 0xFFE0E0E0;
            colorButtonText = 0xFF212121;
            colorButtonActiveBg = 0xFF5B8A72;
            colorButtonActiveText = 0xFFFFFFFF;
        }
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}

package com.gamecenter.app.whack;

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
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 打地鼠模块 Fragment。
 *
 * <p>从原 {@code WhackActivity} 迁移而来。3x3 网格限时打地鼠，
 * 使用纯 Android widget（Button），支持浅/深主题。</p>
 */
public class WhackModuleFragment extends Fragment {

    private static final String GAME_ID = "whack";

    private static final int COLOR_HOLE = 0xFF6D4C41;
    private static final int COLOR_MOLE = 0xFFFF7043;
    private static final int COLOR_HIT = 0xFFFFEB3B;

    private final WhackGame game = new WhackGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvTimer;
    private TextView tvScore;
    private GridLayout gridLayout;
    private Button[] moleButtons = new Button[WhackGame.GRID_SIZE * WhackGame.GRID_SIZE];
    private Button btnStart;

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private boolean paused = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(dark ? 0xFF121212 : 0xFFFFF8E1);
        root.setPadding((int) (16 * dp), (int) (24 * dp), (int) (16 * dp), (int) (16 * dp));

        int textPrimary = dark ? 0xFFEEEEEE : 0xFF4E342E;
        int textSecondary = dark ? 0xFFBDBDBD : 0xFF795548;
        int timerColor = dark ? 0xFFFFAB40 : 0xFFE65100;

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(textPrimary);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        tvStatus.setText(getString(R.string.game_whack_status_init));

        tvTimer = new TextView(ctx);
        tvTimer.setGravity(Gravity.CENTER);
        tvTimer.setTextSize(24f);
        tvTimer.setTextColor(timerColor);
        tvTimer.setText(getString(R.string.game_whack_time_format, WhackGame.GAME_DURATION_SEC));

        tvScore = new TextView(ctx);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setTextSize(16f);
        tvScore.setTextColor(textSecondary);
        tvScore.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));
        tvScore.setText(getString(R.string.game_score_alt_init));

        gridLayout = new GridLayout(ctx);
        gridLayout.setColumnCount(WhackGame.GRID_SIZE);
        gridLayout.setRowCount(WhackGame.GRID_SIZE);
        gridLayout.setUseDefaultMargins(true);

        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int cellSize = (int) (screenWidth * 0.75 / WhackGame.GRID_SIZE);
        for (int i = 0; i < WhackGame.GRID_SIZE * WhackGame.GRID_SIZE; i++) {
            final int index = i;
            Button btn = new Button(ctx);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(6, 6, 6, 6);
            btn.setLayoutParams(params);
            btn.setText("🕳");
            btn.setTextSize(28f);
            btn.setBackgroundColor(COLOR_HOLE);
            btn.setOnClickListener(v -> onMoleClick(index));
            moleButtons[i] = btn;
            gridLayout.addView(btn);
        }

        btnStart = new Button(ctx);
        btnStart.setText(getString(R.string.game_btn_start));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setBackgroundColor(0xFFFF7043);
        btnStart.setOnClickListener(v -> startNewGame());

        root.addView(tvStatus);
        root.addView(tvTimer);
        root.addView(tvScore);
        root.addView(gridLayout);
        root.addView(btnStart);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        saveManager = SaveManager.getInstance(requireContext());
        usageStore = new GameUsageStore(requireContext());
    }

    private void startNewGame() {
        game.reset(WhackGame.INITIAL_MOLE_INTERVAL_MS);
        btnStart.setVisibility(View.GONE);
        tvStatus.setText(getString(R.string.game_in_progress));
        tvScore.setText(getString(R.string.game_score_alt_init));
        tvTimer.setText(getString(R.string.game_whack_time_format, WhackGame.GAME_DURATION_SEC));

        for (int i = 0; i < moleButtons.length; i++) {
            moleButtons[i].setText("🕳");
            moleButtons[i].setBackgroundColor(COLOR_HOLE);
            moleButtons[i].setEnabled(true);
        }

        startGameLoop();
    }

    private void startGameLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!game.isGameActive() || paused) return;
                game.onTickSecond();
                tvTimer.setText(getString(R.string.game_whack_time_format, game.getTimeRemaining()));
                if (game.getTimeRemaining() <= 0) {
                    onGameEnd();
                    return;
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);

        showNextMole();
    }

    private void showNextMole() {
        if (!game.isGameActive() || paused) return;
        if (game.getCurrentMolePos() >= 0) {
            moleButtons[game.getCurrentMolePos()].setText("🕳");
            moleButtons[game.getCurrentMolePos()].setBackgroundColor(COLOR_HOLE);
        }

        int pos = game.nextMolePos();
        final int finalPos = pos;
        moleButtons[pos].setText("🐹");
        moleButtons[pos].setBackgroundColor(COLOR_MOLE);

        handler.postDelayed(() -> {
            if (game.isMoleVisible() && game.getCurrentMolePos() == finalPos) {
                game.missMole();
                moleButtons[finalPos].setText("🕳");
                moleButtons[finalPos].setBackgroundColor(COLOR_HOLE);
                showNextMole();
            }
        }, game.getMoleIntervalMs());
    }

    private void onMoleClick(int index) {
        if (!game.isGameActive() || paused) return;
        if (index == game.getCurrentMolePos() && game.isMoleVisible()) {
            int gain = game.hitMole();
            moleButtons[index].setText("💥");
            moleButtons[index].setBackgroundColor(COLOR_HIT);
            tvScore.setText(getString(R.string.game_score_alt_format, game.getScore()));

            handler.postDelayed(() -> {
                if (game.isGameActive()) {
                    moleButtons[index].setText("🕳");
                    moleButtons[index].setBackgroundColor(COLOR_HOLE);
                    showNextMole();
                }
            }, 300);
        } else {
            game.consecutiveHitsReset();
        }
    }

    private void onGameEnd() {
        game.endGame();
        handler.removeCallbacksAndMessages(null);

        if (game.getCurrentMolePos() >= 0) {
            moleButtons[game.getCurrentMolePos()].setText("🕳");
            moleButtons[game.getCurrentMolePos()].setBackgroundColor(COLOR_HOLE);
        }

        tvStatus.setText("游戏结束！最终分数: " + game.getScore());
        btnStart.setText("再玩一次");
        btnStart.setVisibility(View.VISIBLE);

        usageStore.recordWin(GAME_ID);
        recordHighScore(game.getScore());
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paused && game.isGameActive()) {
            paused = false;
            startGameLoop();
        } else {
            paused = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private void recordHighScore(int score) {
        try {
            String json = saveManager.loadProgress(GAME_ID);
            int best = 0;
            if (json != null) {
                best = new JSONObject(json).optInt("highScore", 0);
            }
            if (score > best) {
                JSONObject obj = new JSONObject();
                obj.put("highScore", score);
                saveManager.saveProgress(GAME_ID, obj.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}

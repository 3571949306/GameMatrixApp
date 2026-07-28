package com.gamecenter.app.reaction;

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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 反应力模块 Fragment。
 *
 * <p>从原 {@code ReactionActivity} 迁移而来。方块变绿后尽快点击，
 * 使用纯 Android widget，支持浅/深主题。</p>
 */
public class ReactionModuleFragment extends Fragment {

    private static final String GAME_ID = "reaction";

    private static final int COLOR_IDLE = 0xFF607D8B;
    private static final int COLOR_WAITING = 0xFFFFC107;
    private static final int COLOR_READY = 0xFF4CAF50;
    private static final int COLOR_RESULT = 0xFF2196F3;
    private static final int COLOR_TOO_EARLY = 0xFFF44336;

    private final ReactionGame game = new ReactionGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvStats;
    private View targetBox;
    private Button btnStart;
    private Button btnRetry;

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private int totalScore = 0;
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
        root.setBackgroundColor(dark ? 0xFF121212 : 0xFFECEFF1);
        root.setPadding((int) (16 * dp), (int) (24 * dp), (int) (16 * dp), (int) (16 * dp));

        int textPrimary = dark ? 0xFFEEEEEE : 0xFF263238;
        int textSecondary = dark ? 0xFFBDBDBD : 0xFF546E7A;

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(textPrimary);
        tvStatus.setPadding(0, (int) (16 * dp), 0, (int) (16 * dp));
        tvStatus.setText("点击开始按钮测试反应力");

        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(textSecondary);
        tvStats.setPadding(0, (int) (8 * dp), 0, (int) (24 * dp));
        tvStats.setText("最佳: -- | 平均: -- | 轮次: 0");

        targetBox = new View(ctx);
        int boxSize = (int) (300 * dp);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(boxSize, boxSize);
        boxParams.gravity = Gravity.CENTER;
        boxParams.setMargins(0, (int) (24 * dp), 0, (int) (24 * dp));
        targetBox.setLayoutParams(boxParams);
        targetBox.setBackgroundColor(COLOR_IDLE);
        targetBox.setOnClickListener(v -> onTargetClick());

        btnStart = new Button(ctx);
        btnStart.setText("开始");
        btnStart.setTextColor(Color.WHITE);
        btnStart.setBackgroundColor(0xFF4CAF50);
        btnStart.setOnClickListener(v -> startRound());

        btnRetry = new Button(ctx);
        btnRetry.setText("再来一次");
        btnRetry.setTextColor(textSecondary);
        btnRetry.setBackgroundColor(0xFF607D8B);
        btnRetry.setVisibility(View.GONE);
        btnRetry.setOnClickListener(v -> startRound());

        root.addView(tvStatus);
        root.addView(tvStats);
        root.addView(targetBox);
        root.addView(btnStart);
        root.addView(btnRetry);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        saveManager = SaveManager.getInstance(requireContext());
        usageStore = new GameUsageStore(requireContext());
    }

    private void startRound() {
        if (paused) return;
        game.startRound();
        targetBox.setBackgroundColor(COLOR_WAITING);
        tvStatus.setText("等待方块变绿...");
        btnStart.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        int delay = game.computeDelayMs();
        handler.postDelayed(() -> {
            if (paused) return;
            if (game.isWaitingForGreen()) {
                game.markReady();
                targetBox.setBackgroundColor(COLOR_READY);
                tvStatus.setText("点击！");
            }
        }, delay);
    }

    private void onTargetClick() {
        if (paused) return;
        if (!game.isWaitingForGreen()) return;

        if (!game.isGreenShown()) {
            game.tooEarly();
            targetBox.setBackgroundColor(COLOR_TOO_EARLY);
            tvStatus.setText("点早了！请等方块变绿");
            btnRetry.setVisibility(View.VISIBLE);
            return;
        }

        long reactionMs = game.recordHit();
        totalScore += game.computeScore(reactionMs);

        targetBox.setBackgroundColor(COLOR_RESULT);
        tvStatus.setText("反应时间: " + reactionMs + " ms");

        String bestStr = game.getBestReactionTimeMs() == Long.MAX_VALUE
                ? "--" : String.valueOf(game.getBestReactionTimeMs());
        tvStats.setText("最佳: " + bestStr + " | 平均: " + game.getAverageMs() + " | 轮次: " + game.getTotalRounds());

        usageStore.recordWin(GAME_ID);
        recordHighScore(game.getHighScore());
        btnRetry.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacksAndMessages(null);
        game.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        paused = false;
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

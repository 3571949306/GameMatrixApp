package com.gamecenter.app.dice;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;
import org.json.JSONObject;

import java.util.Random;

/**
 * 骰子对决 Fragment（从 DiceActivity 迁移）。
 *
 * <p>玩家和电脑各掷两个骰子，比总点数大小。玩家可以选择"加倍"以获得双倍积分。</p>
 */
public class DiceModuleFragment extends Fragment {

    private static final String TAG = "DiceModuleFragment";
    private static final String GAME_ID = "dice";

    // 游戏状态
    private int totalRounds = 0;
    private int playerWins = 0;
    private int aiWins = 0;
    private int draws = 0;
    private int winStreak = 0;
    private int playerDice1 = 0;
    private int playerDice2 = 0;
    private int aiDice1 = 0;
    private int aiDice2 = 0;
    private boolean isDoubleUp = false;
    private boolean roundActive = false;
    private int currentScore = 0;
    private int highScore = 0;
    private Random random = new Random();

    // UI 组件
    private TextView tvStatus;
    private TextView tvPlayerDice;
    private TextView tvAiDice;
    private TextView tvStats;
    private Button btnRoll;
    private Button btnDoubleUp;
    private Button btnNextRound;

    private SaveManager saveManager;
    private GameUsageStore usageStore;

    private static final String[] DICE_FACES = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F5F5);
        root.setPadding((int) (24 * dp), (int) (24 * dp), (int) (24 * dp), (int) (24 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_dice));
        tvTitle.setTextSize(26);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.bottomMargin = (int) (16 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(20f);
        tvStatus.setTextColor(0xFF212121);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));
        tvStatus.setText(getString(R.string.game_status_click_roll));
        root.addView(tvStatus);

        TextView tvPlayerLabel = new TextView(ctx);
        tvPlayerLabel.setGravity(Gravity.CENTER);
        tvPlayerLabel.setTextSize(16f);
        tvPlayerLabel.setTextColor(0xFF757575);
        tvPlayerLabel.setText(getString(R.string.game_player_label));
        root.addView(tvPlayerLabel);

        tvPlayerDice = new TextView(ctx);
        tvPlayerDice.setGravity(Gravity.CENTER);
        tvPlayerDice.setTextSize(48f);
        tvPlayerDice.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));
        tvPlayerDice.setText("⚀ ⚀");
        root.addView(tvPlayerDice);

        TextView tvAiLabel = new TextView(ctx);
        tvAiLabel.setGravity(Gravity.CENTER);
        tvAiLabel.setTextSize(16f);
        tvAiLabel.setTextColor(0xFF757575);
        tvAiLabel.setText(getString(R.string.game_ai_label));
        root.addView(tvAiLabel);

        tvAiDice = new TextView(ctx);
        tvAiDice.setGravity(Gravity.CENTER);
        tvAiDice.setTextSize(48f);
        tvAiDice.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));
        tvAiDice.setText("⚀ ⚀");
        root.addView(tvAiDice);

        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF757575);
        tvStats.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));
        tvStats.setText(String.format("分数: 0  胜: 0  负: 0  平: 0"));
        root.addView(tvStats);

        LinearLayout buttonArea = new LinearLayout(ctx);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonArea.setLayoutParams(barLp);

        btnRoll = new Button(ctx);
        btnRoll.setText(getString(R.string.game_btn_roll));
        btnRoll.setBackgroundColor(0xFF1976D2);
        btnRoll.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams rollParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rollParams.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnRoll.setLayoutParams(rollParams);
        buttonArea.addView(btnRoll);

        btnDoubleUp = new Button(ctx);
        btnDoubleUp.setText(getString(R.string.game_btn_double_up));
        btnDoubleUp.setBackgroundColor(0xFFFF9800);
        btnDoubleUp.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams doubleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        doubleParams.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnDoubleUp.setLayoutParams(doubleParams);
        btnDoubleUp.setVisibility(View.GONE);
        buttonArea.addView(btnDoubleUp);

        btnNextRound = new Button(ctx);
        btnNextRound.setText(getString(R.string.game_btn_next_round));
        btnNextRound.setBackgroundColor(0xFF1976D2);
        btnNextRound.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nextParams.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnNextRound.setLayoutParams(nextParams);
        btnNextRound.setVisibility(View.GONE);
        buttonArea.addView(btnNextRound);

        root.addView(buttonArea);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        highScore = loadHighScore();

        btnRoll.setOnClickListener(v -> onRollDice());
        btnDoubleUp.setOnClickListener(v -> onDoubleUp());
        btnNextRound.setOnClickListener(v -> onNextRound());
    }

    private void onRollDice() {
        playerDice1 = random.nextInt(6) + 1;
        playerDice2 = random.nextInt(6) + 1;
        aiDice1 = random.nextInt(6) + 1;
        aiDice2 = random.nextInt(6) + 1;

        tvPlayerDice.setText(DICE_FACES[playerDice1 - 1] + " " + DICE_FACES[playerDice2 - 1]);
        tvAiDice.setText(DICE_FACES[aiDice1 - 1] + " " + DICE_FACES[aiDice2 - 1]);

        totalRounds++;
        roundActive = true;
        isDoubleUp = false;

        btnRoll.setVisibility(View.GONE);
        btnDoubleUp.setVisibility(View.VISIBLE);
        btnNextRound.setVisibility(View.VISIBLE);

        tvStatus.setText(previewResultText(playerDice1 + playerDice2, aiDice1 + aiDice2));
    }

    private void onDoubleUp() {
        if (!roundActive) return;
        isDoubleUp = true;
        btnDoubleUp.setVisibility(View.GONE);
        tvStatus.setText(previewResultText(playerDice1 + playerDice2, aiDice1 + aiDice2));
    }

    private String previewResultText(int playerTotal, int aiTotal) {
        int multiplier = isDoubleUp ? 2 : 1;
        if (playerTotal > aiTotal) {
            int points = playerTotal * multiplier;
            return String.format("玩家 %d 比 %d，预计获胜 +%d 分", playerTotal, aiTotal, points);
        } else if (playerTotal < aiTotal) {
            return String.format("玩家 %d 比 %d，预计失败", playerTotal, aiTotal);
        } else {
            int points = playerTotal * multiplier;
            return String.format("平局 %d，预计 +%d 分", playerTotal, points);
        }
    }

    private void resolveRound(int playerTotal, int aiTotal, boolean doubled) {
        String resultText;
        int multiplier = doubled ? 2 : 1;

        if (playerTotal > aiTotal) {
            playerWins++;
            winStreak++;
            int points = playerTotal * multiplier;
            currentScore += points;
            resultText = String.format("玩家 %d 比 %d 获胜！+%d 分", playerTotal, aiTotal, points);
        } else if (playerTotal < aiTotal) {
            aiWins++;
            winStreak = 0;
            resultText = String.format("玩家 %d 比 %d 失败", playerTotal, aiTotal);
        } else {
            draws++;
            int points = playerTotal * multiplier;
            currentScore += points;
            resultText = String.format("平局 %d，+%d 分", playerTotal, points);
        }

        tvStatus.setText(resultText);
        tvStats.setText(String.format("分数: %d  胜: %d  负: %d  平: %d", currentScore, playerWins, aiWins, draws));

        if (currentScore > highScore) {
            highScore = currentScore;
            saveHighScore(highScore);
        }
        usageStore.recordScore(GAME_ID, highScore);
    }

    private void onNextRound() {
        if (roundActive) {
            int playerTotal = playerDice1 + playerDice2;
            int aiTotal = aiDice1 + aiDice2;
            resolveRound(playerTotal, aiTotal, isDoubleUp);
            roundActive = false;
        }
        isDoubleUp = false;
        btnDoubleUp.setVisibility(View.GONE);
        btnNextRound.setVisibility(View.GONE);
        btnRoll.setVisibility(View.VISIBLE);
        tvPlayerDice.setText("⚀ ⚀");
        tvAiDice.setText("⚀ ⚀");
        tvStatus.setText(getString(R.string.game_status_click_roll));
    }

    private int loadHighScore() {
        String progressJson = saveManager.loadProgress(GAME_ID);
        if (progressJson != null) {
            try {
                JSONObject obj = new JSONObject(progressJson);
                return obj.optInt("highScore", 0);
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
        } catch (Exception e) {
            Log.w(TAG, "存档操作失败", e);
        }
    }
}

package com.gamecenter.app.guess;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
 * 猜数字 Fragment（从 GuessActivity 迁移）。
 *
 * <p>系统生成 0-100 的随机数，玩家输入猜测，系统提示"大了"或"小了"。
 * 提供三档难度（简单 0-100 / 普通 0-500 / 困难 0-1000）。</p>
 */
public class GuessModuleFragment extends Fragment {

    private static final String TAG = "GuessModuleFragment";
    private static final String GAME_ID = "guess";

    // 游戏状态
    private int targetNumber = 0;
    private int guessCount = 0;
    private int currentMax = 100;
    private int totalGames = 0;
    private int bestGuessCount = Integer.MAX_VALUE;
    private boolean gameActive = false;
    private int currentScore = 0;
    private int highScore = 0;
    private Random random = new Random();

    // UI 组件
    private TextView tvStatus;
    private TextView tvRange;
    private TextView tvGuessCount;
    private TextView tvHistory;
    private EditText etGuess;
    private Button btnGuess;
    private Button btnNewGame;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;
    private StringBuilder historyBuilder = new StringBuilder();

    private SaveManager saveManager;
    private GameUsageStore usageStore;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFFF5F5F5);
        root.setPadding((int) (32 * dp), (int) (24 * dp), (int) (32 * dp), (int) (24 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_guess));
        tvTitle.setTextSize(26);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.bottomMargin = (int) (12 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 难度按钮区
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.bottomMargin = (int) (8 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = new Button(ctx);
        btnEasy.setText(getString(R.string.game_guess_easy));
        btnEasy.setTextSize(12f);
        LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        easyLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnEasy.setLayoutParams(easyLp);
        diffBar.addView(btnEasy);

        btnNormal = new Button(ctx);
        btnNormal.setText(getString(R.string.game_guess_normal));
        btnNormal.setTextSize(12f);
        LinearLayout.LayoutParams normalLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        normalLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnNormal.setLayoutParams(normalLp);
        diffBar.addView(btnNormal);

        btnHard = new Button(ctx);
        btnHard.setText(getString(R.string.game_guess_hard));
        btnHard.setTextSize(12f);
        LinearLayout.LayoutParams hardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        hardLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnHard.setLayoutParams(hardLp);
        diffBar.addView(btnHard);

        root.addView(diffBar);

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(0xFF212121);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        tvStatus.setText(getString(R.string.game_select_difficulty_start));
        root.addView(tvStatus);

        tvRange = new TextView(ctx);
        tvRange.setGravity(Gravity.CENTER);
        tvRange.setTextSize(16f);
        tvRange.setTextColor(0xFF757575);
        tvRange.setText(getString(R.string.game_guess_range_format, 100));
        root.addView(tvRange);

        tvGuessCount = new TextView(ctx);
        tvGuessCount.setGravity(Gravity.CENTER);
        tvGuessCount.setTextSize(14f);
        tvGuessCount.setTextColor(0xFF757575);
        tvGuessCount.setPadding(0, (int) (8 * dp), 0, (int) (12 * dp));
        tvGuessCount.setText(getString(R.string.game_guess_count_init));
        root.addView(tvGuessCount);

        etGuess = new EditText(ctx);
        etGuess.setHint(getString(R.string.game_guess_hint));
        etGuess.setInputType(InputType.TYPE_CLASS_NUMBER);
        etGuess.setGravity(Gravity.CENTER);
        etGuess.setTextSize(18f);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins((int) (24 * dp), (int) (4 * dp), (int) (24 * dp), (int) (12 * dp));
        etGuess.setLayoutParams(inputParams);
        root.addView(etGuess);

        btnGuess = new Button(ctx);
        btnGuess.setText(getString(R.string.game_btn_guess));
        btnGuess.setBackgroundColor(0xFF1976D2);
        btnGuess.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams guessParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guessParams.setMargins((int) (24 * dp), 0, (int) (24 * dp), (int) (8 * dp));
        btnGuess.setLayoutParams(guessParams);
        root.addView(btnGuess);

        btnNewGame = new Button(ctx);
        btnNewGame.setText(getString(R.string.game_btn_new_game));
        btnNewGame.setBackgroundColor(0xFF607D8B);
        btnNewGame.setTextColor(Color.WHITE);
        btnNewGame.setVisibility(View.GONE);
        LinearLayout.LayoutParams newGameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        newGameParams.setMargins((int) (24 * dp), 0, (int) (24 * dp), (int) (8 * dp));
        btnNewGame.setLayoutParams(newGameParams);
        root.addView(btnNewGame);

        tvHistory = new TextView(ctx);
        tvHistory.setTextSize(13f);
        tvHistory.setTextColor(0xFF888888);
        tvHistory.setPadding(0, (int) (12 * dp), 0, 0);
        root.addView(tvHistory);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        highScore = loadHighScore();

        btnEasy.setOnClickListener(v -> { setDifficulty(100); startNewRound(); });
        btnNormal.setOnClickListener(v -> { setDifficulty(500); startNewRound(); });
        btnHard.setOnClickListener(v -> { setDifficulty(1000); startNewRound(); });
        btnGuess.setOnClickListener(v -> onGuess());
        btnNewGame.setOnClickListener(v -> startNewRound());

        startNewRound();
    }

    private void setDifficulty(int max) {
        currentMax = max;
        tvRange.setText(String.format("范围: 0 - %d", currentMax));
    }

    private void startNewRound() {
        guessCount = 0;
        gameActive = true;
        historyBuilder.setLength(0);

        targetNumber = random.nextInt(currentMax + 1);

        tvStatus.setText(String.format("已生成 0-%d 之间的数字，请猜测", currentMax));
        tvGuessCount.setText(getString(R.string.game_guess_count_init));
        tvHistory.setText("");
        etGuess.setText("");
        etGuess.setEnabled(true);
        btnGuess.setEnabled(true);
        btnGuess.setVisibility(View.VISIBLE);
        btnNewGame.setVisibility(View.GONE);
    }

    private void onGuess() {
        if (!gameActive) return;

        String input = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (input.isEmpty()) return;

        int guess;
        try {
            guess = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            tvStatus.setText(getString(R.string.game_guess_invalid));
            return;
        }

        if (guess < 0 || guess > currentMax) {
            tvStatus.setText(String.format("请输入 0-%d 范围内的数字", currentMax));
            return;
        }

        guessCount++;
        etGuess.setText("");

        String historyLine;
        if (guess == targetNumber) {
            historyLine = String.format("✓ %d 猜对了！", guess);
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            onGameWin();
        } else if (guess < targetNumber) {
            historyLine = String.format("%d 太小了", guess);
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            tvStatus.setText(String.format("%d 太小了，再大一点", guess));
        } else {
            historyLine = String.format("%d 太大了", guess);
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            tvStatus.setText(String.format("%d 太大了，再小一点", guess));
        }

        tvGuessCount.setText(String.format("已猜次数: %d", guessCount));
    }

    private void onGameWin() {
        gameActive = false;
        totalGames++;

        if (guessCount < bestGuessCount) {
            bestGuessCount = guessCount;
        }

        int score = Math.max(100 - guessCount * 10, 10);
        if (currentMax > 100) {
            score = (int) (score * 1.5f);
        }
        currentScore += score;

        tvStatus.setText(String.format("恭喜！数字是 %d，用了 %d 次，+%d 分", targetNumber, guessCount, score));

        if (currentScore > highScore) {
            highScore = currentScore;
            saveHighScore(highScore);
        }
        usageStore.recordScore(GAME_ID, highScore);

        etGuess.setEnabled(false);
        btnGuess.setVisibility(View.GONE);
        btnNewGame.setVisibility(View.VISIBLE);
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

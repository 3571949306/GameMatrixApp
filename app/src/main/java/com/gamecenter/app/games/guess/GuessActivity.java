package com.gamecenter.app.games.guess;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 猜数字游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>系统生成 0-100 的随机数，玩家输入猜测，系统提示"大了"或"小了"。
 * 随着难度增加，猜测范围扩大到 0-200、0-500、0-1000。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次猜对</li>
 *   <li>5次内猜对</li>
 *   <li>3次内猜对（高难度范围）</li>
 *   <li>累计10局</li>
 *   <li>一局内猜了超过15次</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class GuessActivity extends BaseGameActivity {

    // ==================== 游戏状态 ====================
    private int targetNumber = 0;
    private int guessCount = 0;
    private int currentMax = 100;
    private int totalGames = 0;
    private int bestGuessCount = Integer.MAX_VALUE;
    private boolean gameActive = false;

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvRange;
    private TextView tvGuessCount;
    private TextView tvHistory;
    private TextInputEditText etGuess;
    private MaterialButton btnGuess;
    private MaterialButton btnNewGame;
    private StringBuilder historyBuilder = new StringBuilder();

    private Random random = new Random();

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "guess";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_guess_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
        startNewRound();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_guess_diff_easy), 1, getString(R.string.game_guess_diff_easy_desc), 0, 0, 1.0f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_guess_diff_normal), 2, getString(R.string.game_guess_diff_normal_desc), 0, 0, 1.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_guess_diff_hard), 3, getString(R.string.game_guess_diff_hard_desc), 0, 0, 2.0f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        // 根据难度设置猜测范围上限（下一轮 startNewRound 时生效）
        switch (newLevel.level) {
            case 1:
                currentMax = 100;
                break;
            case 2:
                currentMax = 500;
                break;
            case 3:
                currentMax = 1000;
                break;
            default:
                break;
        }
    }

    // ==================== 游戏视图创建 ====================

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_guess_color_bg));
        root.setPadding(48, 32, 48, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_guess_color_text_primary));
        tvStatus.setPadding(0, 16, 0, 8);
        tvStatus.setText(getString(R.string.game_guess_status_init));

        // 范围提示
        tvRange = new TextView(this);
        tvRange.setGravity(Gravity.CENTER);
        tvRange.setTextSize(16f);
        tvRange.setTextColor(ContextCompat.getColor(this, R.color.game_guess_color_text_secondary));
        tvRange.setText(getString(R.string.game_guess_range_init));

        // 猜测次数
        tvGuessCount = new TextView(this);
        tvGuessCount.setGravity(Gravity.CENTER);
        tvGuessCount.setTextSize(14f);
        tvGuessCount.setTextColor(ContextCompat.getColor(this, R.color.game_guess_color_text_secondary));
        tvGuessCount.setPadding(0, 8, 0, 16);
        tvGuessCount.setText(getString(R.string.game_guess_count_init));

        // 输入框
        etGuess = new TextInputEditText(this);
        etGuess.setHint(getString(R.string.game_guess_hint_input));
        etGuess.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etGuess.setGravity(Gravity.CENTER);
        etGuess.setTextSize(18f);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(32, 8, 32, 16);
        etGuess.setLayoutParams(inputParams);

        // 猜测按钮
        btnGuess = new MaterialButton(this);
        btnGuess.setText(getString(R.string.game_guess_btn_guess));
        btnGuess.setBackgroundColor(ContextCompat.getColor(this, R.color.game_guess_color_btn_primary));
        btnGuess.setTextColor(Color.WHITE);
        btnGuess.setOnClickListener(v -> onGuess());
        LinearLayout.LayoutParams guessParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guessParams.setMargins(32, 0, 32, 16);
        btnGuess.setLayoutParams(guessParams);

        // 新游戏按钮
        btnNewGame = new MaterialButton(this);
        btnNewGame.setText(getString(R.string.game_guess_btn_new_game));
        btnNewGame.setBackgroundColor(ContextCompat.getColor(this, R.color.game_guess_color_btn_new_game_bg));
        btnNewGame.setTextColor(ContextCompat.getColor(this, R.color.game_guess_color_text_secondary));
        btnNewGame.setVisibility(View.GONE);
        btnNewGame.setOnClickListener(v -> startNewRound());
        LinearLayout.LayoutParams newGameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        newGameParams.setMargins(32, 0, 32, 16);
        btnNewGame.setLayoutParams(newGameParams);

        // 历史记录
        tvHistory = new TextView(this);
        tvHistory.setTextSize(13f);
        tvHistory.setTextColor(ContextCompat.getColor(this, R.color.game_guess_color_text_history));
        tvHistory.setPadding(0, 16, 0, 0);

        root.addView(tvStatus);
        root.addView(tvRange);
        root.addView(tvGuessCount);
        root.addView(etGuess);
        root.addView(btnGuess);
        root.addView(btnNewGame);
        root.addView(tvHistory);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 开始新一轮
     */
    private void startNewRound() {
        guessCount = 0;
        gameActive = true;
        historyBuilder.setLength(0);

        targetNumber = random.nextInt(currentMax + 1);

        tvStatus.setText(getString(R.string.game_guess_status_new_round, currentMax));
        tvRange.setText(getString(R.string.game_guess_range_format, currentMax));
        tvGuessCount.setText(getString(R.string.game_guess_count_init));
        tvHistory.setText("");
        etGuess.setText("");
        etGuess.setEnabled(true);
        btnGuess.setEnabled(true);
        btnGuess.setVisibility(View.VISIBLE);
        btnNewGame.setVisibility(View.GONE);
    }

    /**
     * 处理猜测
     */
    private void onGuess() {
        if (!gameActive || !isGameRunning || isGamePaused) return;

        String input = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (input.isEmpty()) return;

        int guess;
        try {
            guess = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            tvStatus.setText(getString(R.string.game_guess_err_invalid));
            return;
        }

        if (guess < 0 || guess > currentMax) {
            tvStatus.setText(getString(R.string.game_guess_err_out_of_range, currentMax));
            return;
        }

        guessCount++;
        etGuess.setText("");

        String historyLine;
        if (guess == targetNumber) {
            // 猜对了
            historyLine = getString(R.string.game_guess_history_correct, guess);
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());

            onGameWin();
        } else if (guess < targetNumber) {
            historyLine = getString(R.string.game_guess_history_too_small, guess);
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            tvStatus.setText(getString(R.string.game_guess_status_too_small, guess));
        } else {
            historyLine = getString(R.string.game_guess_history_too_big, guess);
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            tvStatus.setText(getString(R.string.game_guess_status_too_big, guess));
        }

        tvGuessCount.setText(getString(R.string.game_guess_count_format, guessCount));
    }

    /**
     * 游戏胜利处理
     */
    private void onGameWin() {
        gameActive = false;
        totalGames++;

        if (guessCount < bestGuessCount) {
            bestGuessCount = guessCount;
        }

        // 计算分数
        int score = Math.max(100 - guessCount * 10, 10);
        if (currentMax > 100) {
            score = (int) (score * 1.5f);
        }
        currentScore += score;
        updateScore(currentScore);

        tvStatus.setText(getString(R.string.game_guess_status_win, targetNumber, guessCount));

        // 成就检查
        checkAchievement("win", totalGames);
        checkAchievement("score", guessCount);
        checkAchievement("rounds", totalGames);

        if (guessCount <= 3 && currentMax > 100) {
            checkAchievement("special", 1);
        }
        if (guessCount > 15) {
            checkAchievement("special", 2);
        }

        usageStore.recordWin(getGameId());

        // 最高分持久化
        recordHighScore(currentScore);

        // 范围上限现由难度系统管理（onDifficultyChanged），不再每 3 局自动翻倍。

        etGuess.setEnabled(false);
        btnGuess.setVisibility(View.GONE);
        btnNewGame.setVisibility(View.VISIBLE);
    }
}

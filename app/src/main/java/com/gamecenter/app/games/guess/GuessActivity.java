package com.gamecenter.app.games.guess;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

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
        return "猜数字";
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

    // ==================== 游戏视图创建 ====================

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFFF5F0E8);
        root.setPadding(48, 32, 48, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 16, 0, 8);
        tvStatus.setText("猜一个数字！");

        // 范围提示
        tvRange = new TextView(this);
        tvRange.setGravity(Gravity.CENTER);
        tvRange.setTextSize(16f);
        tvRange.setTextColor(0xFF5B8A72);
        tvRange.setText("范围：0 ~ 100");

        // 猜测次数
        tvGuessCount = new TextView(this);
        tvGuessCount.setGravity(Gravity.CENTER);
        tvGuessCount.setTextSize(14f);
        tvGuessCount.setTextColor(0xFF5B8A72);
        tvGuessCount.setPadding(0, 8, 0, 16);
        tvGuessCount.setText("已猜次数：0");

        // 输入框
        etGuess = new TextInputEditText(this);
        etGuess.setHint("输入你的猜测");
        etGuess.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etGuess.setGravity(Gravity.CENTER);
        etGuess.setTextSize(18f);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(32, 8, 32, 16);
        etGuess.setLayoutParams(inputParams);

        // 猜测按钮
        btnGuess = new MaterialButton(this);
        btnGuess.setText("猜！");
        btnGuess.setBackgroundColor(0xFF5B8A72);
        btnGuess.setTextColor(Color.WHITE);
        btnGuess.setOnClickListener(v -> onGuess());
        LinearLayout.LayoutParams guessParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guessParams.setMargins(32, 0, 32, 16);
        btnGuess.setLayoutParams(guessParams);

        // 新游戏按钮
        btnNewGame = new MaterialButton(this);
        btnNewGame.setText("新游戏");
        btnNewGame.setBackgroundColor(0xFFFBF9F6);
        btnNewGame.setTextColor(0xFF5B8A72);
        btnNewGame.setVisibility(View.GONE);
        btnNewGame.setOnClickListener(v -> startNewRound());
        LinearLayout.LayoutParams newGameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        newGameParams.setMargins(32, 0, 32, 16);
        btnNewGame.setLayoutParams(newGameParams);

        // 历史记录
        tvHistory = new TextView(this);
        tvHistory.setTextSize(13f);
        tvHistory.setTextColor(0xFF888888);
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

        tvStatus.setText("猜一个 0 ~ " + currentMax + " 的数字！");
        tvRange.setText("范围：0 ~ " + currentMax);
        tvGuessCount.setText("已猜次数：0");
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
            tvStatus.setText("请输入有效的数字！");
            return;
        }

        if (guess < 0 || guess > currentMax) {
            tvStatus.setText("请输入 0 ~ " + currentMax + " 之间的数字！");
            return;
        }

        guessCount++;
        etGuess.setText("");

        String historyLine;
        if (guess == targetNumber) {
            // 猜对了
            historyLine = guess + " ✅ 正确！";
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());

            onGameWin();
        } else if (guess < targetNumber) {
            historyLine = guess + " ⬆ 太小了";
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            tvStatus.setText(guess + " 太小了！再大一点");
        } else {
            historyLine = guess + " ⬇ 太大了";
            historyBuilder.insert(0, historyLine + "\n");
            tvHistory.setText(historyBuilder.toString());
            tvStatus.setText(guess + " 太大了！再小一点");
        }

        tvGuessCount.setText("已猜次数：" + guessCount);
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

        tvStatus.setText("🎉 恭喜！答案就是 " + targetNumber + "！用了 " + guessCount + " 次");

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

        // 难度递增
        if (totalGames % 3 == 0 && currentMax < 1000) {
            currentMax = Math.min(currentMax * 2, 1000);
        }

        etGuess.setEnabled(false);
        btnGuess.setVisibility(View.GONE);
        btnNewGame.setVisibility(View.VISIBLE);
    }
}

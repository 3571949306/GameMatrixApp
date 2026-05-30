package com.gamecenter.app.games.dice;

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

import java.util.Random;

/**
 * 骰子游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>玩家和电脑各掷两个骰子，比总点数大小。
 * 玩家可以选择"加倍"以获得双倍积分。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次掷出12点（双6）</li>
 *   <li>三连胜</li>
 *   <li>连续掷出两个1点（双1）</li>
 *   <li>累计20局</li>
 *   <li>单局得分50+</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class DiceActivity extends BaseGameActivity {

    // ==================== 游戏状态 ====================
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
    private Random random = new Random();

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvPlayerDice;
    private TextView tvAiDice;
    private TextView tvStats;
    private MaterialButton btnRoll;
    private MaterialButton btnDoubleUp;
    private MaterialButton btnNextRound;

    private static final String[] DICE_FACES = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "dice";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return "骰子";
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
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F0E8);
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(20f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 16, 0, 24);
        tvStatus.setText("点击骰子开始游戏！");

        // 玩家区域
        TextView tvPlayerLabel = new TextView(this);
        tvPlayerLabel.setGravity(Gravity.CENTER);
        tvPlayerLabel.setTextSize(16f);
        tvPlayerLabel.setTextColor(0xFF5B8A72);
        tvPlayerLabel.setText("🎮 你的骰子");

        tvPlayerDice = new TextView(this);
        tvPlayerDice.setGravity(Gravity.CENTER);
        tvPlayerDice.setTextSize(48f);
        tvPlayerDice.setPadding(0, 16, 0, 24);
        tvPlayerDice.setText("⚀ ⚀");

        // 电脑区域
        TextView tvAiLabel = new TextView(this);
        tvAiLabel.setGravity(Gravity.CENTER);
        tvAiLabel.setTextSize(16f);
        tvAiLabel.setTextColor(0xFF5B8A72);
        tvAiLabel.setText("🤖 电脑的骰子");

        tvAiDice = new TextView(this);
        tvAiDice.setGravity(Gravity.CENTER);
        tvAiDice.setTextSize(48f);
        tvAiDice.setPadding(0, 16, 0, 24);
        tvAiDice.setText("⚀ ⚀");

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF5B8A72);
        tvStats.setPadding(0, 8, 0, 24);
        tvStats.setText("总分：0 | 胜：0 | 负：0 | 平：0");

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);

        btnRoll = new MaterialButton(this);
        btnRoll.setText("🎲 掷骰子");
        btnRoll.setBackgroundColor(0xFF5B8A72);
        btnRoll.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams rollParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rollParams.setMargins(8, 0, 8, 0);
        btnRoll.setLayoutParams(rollParams);
        btnRoll.setOnClickListener(v -> onRollDice());

        btnDoubleUp = new MaterialButton(this);
        btnDoubleUp.setText("💰 加倍");
        btnDoubleUp.setBackgroundColor(0xFFFF9800);
        btnDoubleUp.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams doubleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        doubleParams.setMargins(8, 0, 8, 0);
        btnDoubleUp.setLayoutParams(doubleParams);
        btnDoubleUp.setVisibility(View.GONE);
        btnDoubleUp.setOnClickListener(v -> onDoubleUp());

        btnNextRound = new MaterialButton(this);
        btnNextRound.setText("下一局");
        btnNextRound.setBackgroundColor(0xFF5B8A72);
        btnNextRound.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nextParams.setMargins(8, 0, 8, 0);
        btnNextRound.setLayoutParams(nextParams);
        btnNextRound.setVisibility(View.GONE);
        btnNextRound.setOnClickListener(v -> onNextRound());

        buttonArea.addView(btnRoll);
        buttonArea.addView(btnDoubleUp);
        buttonArea.addView(btnNextRound);

        root.addView(tvStatus);
        root.addView(tvPlayerLabel);
        root.addView(tvPlayerDice);
        root.addView(tvAiLabel);
        root.addView(tvAiDice);
        root.addView(tvStats);
        root.addView(buttonArea);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 掷骰子
     */
    private void onRollDice() {
        if (!isGameRunning || isGamePaused) return;

        // 掷骰子
        playerDice1 = random.nextInt(6) + 1;
        playerDice2 = random.nextInt(6) + 1;
        aiDice1 = random.nextInt(6) + 1;
        aiDice2 = random.nextInt(6) + 1;

        // 显示骰子
        tvPlayerDice.setText(DICE_FACES[playerDice1 - 1] + " " + DICE_FACES[playerDice2 - 1]);
        tvAiDice.setText(DICE_FACES[aiDice1 - 1] + " " + DICE_FACES[aiDice2 - 1]);

        int playerTotal = playerDice1 + playerDice2;
        int aiTotal = aiDice1 + aiDice2;

        // 判断胜负
        totalRounds++;
        roundActive = true;

        // 显示加倍按钮
        btnRoll.setVisibility(View.GONE);
        btnDoubleUp.setVisibility(View.VISIBLE);
        btnNextRound.setVisibility(View.VISIBLE);

        // 判断并显示结果
        resolveRound(playerTotal, aiTotal, false);
    }

    /**
     * 加倍
     */
    private void onDoubleUp() {
        if (!roundActive) return;
        isDoubleUp = true;
        btnDoubleUp.setVisibility(View.GONE);

        int playerTotal = playerDice1 + playerDice2;
        int aiTotal = aiDice1 + aiDice2;

        // 掷新的骰子
        playerDice1 = random.nextInt(6) + 1;
        playerDice2 = random.nextInt(6) + 1;
        aiDice1 = random.nextInt(6) + 1;
        aiDice2 = random.nextInt(6) + 1;

        tvPlayerDice.setText(DICE_FACES[playerDice1 - 1] + " " + DICE_FACES[playerDice2 - 1]);
        tvAiDice.setText(DICE_FACES[aiDice1 - 1] + " " + DICE_FACES[aiDice2 - 1]);

        playerTotal = playerDice1 + playerDice2;
        aiTotal = aiDice1 + aiDice2;

        resolveRound(playerTotal, aiTotal, true);
        roundActive = false;
    }

    /**
     * 判定结果
     */
    private void resolveRound(int playerTotal, int aiTotal, boolean doubled) {
        String resultText;
        int multiplier = doubled ? 2 : 1;

        if (playerTotal > aiTotal) {
            playerWins++;
            winStreak++;
            int points = playerTotal * multiplier;
            currentScore += points;
            resultText = "你赢了！" + playerTotal + " vs " + aiTotal + " (+" + points + "分)";
            checkAchievement("win", playerWins);
            checkAchievement("streak", winStreak);
        } else if (playerTotal < aiTotal) {
            aiWins++;
            winStreak = 0;
            resultText = "你输了！" + playerTotal + " vs " + aiTotal;
        } else {
            draws++;
            int points = playerTotal;
            currentScore += points;
            resultText = "平局！都是" + playerTotal + "点 (+" + points + "分)";
        }

        // 成就：掷出12点
        if (playerDice1 == 6 && playerDice2 == 6) {
            checkAchievement("special", 1);
        }

        // 成就：双1
        if (playerDice1 == 1 && playerDice2 == 1) {
            checkAchievement("special", 2);
        }

        checkAchievement("rounds", totalRounds);

        tvStatus.setText(resultText);
        tvStats.setText("总分：" + currentScore + " | 胜：" + playerWins + " | 负：" + aiWins + " | 平：" + draws);
        updateScore(currentScore);
    }

    /**
     * 下一局
     */
    private void onNextRound() {
        roundActive = false;
        isDoubleUp = false;
        btnDoubleUp.setVisibility(View.GONE);
        btnNextRound.setVisibility(View.GONE);
        btnRoll.setVisibility(View.VISIBLE);
        tvPlayerDice.setText("⚀ ⚀");
        tvAiDice.setText("⚀ ⚀");
        tvStatus.setText("点击骰子开始游戏！");
    }
}

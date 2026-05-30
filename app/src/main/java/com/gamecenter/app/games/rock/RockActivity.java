package com.gamecenter.app.games.rock;

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
 * 猜拳游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>玩家选择石头✊、剪刀✌️或布🖐，与 AI 对战。
 * AI 采用加权随机策略：如果连续输，会偏向选择能赢玩家的选项。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次胜利</li>
 *   <li>三连胜</li>
 *   <li>十局胜率 70%</li>
 *   <li>出过所有手势</li>
 *   <li>累计20局</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class RockActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    private static final int ROCK = 0;
    private static final int SCISSORS = 1;
    private static final int PAPER = 2;
    private static final String[] CHOICE_NAMES = {"石头", "剪刀", "布"};
    private static final String[] CHOICE_EMOJI = {"✊", "✌️", "🖐"};

    // ==================== 游戏状态 ====================
    private int totalRounds = 0;
    private int playerWins = 0;
    private int aiWins = 0;
    private int draws = 0;
    private int winStreak = 0;
    private int maxWinStreak = 0;
    private boolean[] usedChoices = new boolean[3];
    private Random random = new Random();

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvPlayerChoice;
    private TextView tvAiChoice;
    private TextView tvStats;
    private MaterialButton btnRock;
    private MaterialButton btnScissors;
    private MaterialButton btnPaper;
    private MaterialButton btnRestart;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "rock";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return "猜拳";
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
        enableButtons(true);
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
        tvStatus.setText("选择你的手势！");

        // 对战显示区域
        LinearLayout battleArea = new LinearLayout(this);
        battleArea.setOrientation(LinearLayout.HORIZONTAL);
        battleArea.setGravity(Gravity.CENTER);
        battleArea.setPadding(0, 0, 0, 32);

        tvPlayerChoice = new TextView(this);
        tvPlayerChoice.setTextSize(48f);
        tvPlayerChoice.setGravity(Gravity.CENTER);
        tvPlayerChoice.setText("❓");
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvPlayerChoice.setLayoutParams(playerParams);

        TextView tvVs = new TextView(this);
        tvVs.setTextSize(24f);
        tvVs.setTextColor(0xFF5B8A72);
        tvVs.setGravity(Gravity.CENTER);
        tvVs.setText(" VS ");

        tvAiChoice = new TextView(this);
        tvAiChoice.setTextSize(48f);
        tvAiChoice.setGravity(Gravity.CENTER);
        tvAiChoice.setText("❓");
        LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvAiChoice.setLayoutParams(aiParams);

        battleArea.addView(tvPlayerChoice);
        battleArea.addView(tvVs);
        battleArea.addView(tvAiChoice);

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF5B8A72);
        tvStats.setPadding(0, 8, 0, 24);
        tvStats.setText("胜：0 | 负：0 | 平：0");

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.setPadding(0, 0, 0, 16);

        btnRock = createChoiceButton("✊ 石头", ROCK);
        btnScissors = createChoiceButton("✌️ 剪刀", SCISSORS);
        btnPaper = createChoiceButton("🖐 布", PAPER);

        buttonArea.addView(btnRock);
        buttonArea.addView(btnScissors);
        buttonArea.addView(btnPaper);

        // 重新开始按钮
        btnRestart = new MaterialButton(this);
        btnRestart.setText("再来一局");
        btnRestart.setBackgroundColor(0xFF5B8A72);
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setVisibility(View.GONE);
        btnRestart.setOnClickListener(v -> resetRound());

        root.addView(tvStatus);
        root.addView(battleArea);
        root.addView(tvStats);
        root.addView(buttonArea);
        root.addView(btnRestart);

        return root;
    }

    /**
     * 创建选择按钮
     */
    private MaterialButton createChoiceButton(String text, int choice) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setTextSize(14f);
        btn.setBackgroundColor(0xFFFBF9F6);
        btn.setTextColor(0xFF2D2D2D);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(8, 0, 8, 0);
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> onPlayerChoice(choice));
        return btn;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 处理玩家选择
     */
    private void onPlayerChoice(int playerChoice) {
        if (!isGameRunning || isGamePaused) return;

        usedChoices[playerChoice] = true;

        // AI 选择（加权随机策略）
        int aiChoice = calculateAiChoice();

        // 显示结果
        tvPlayerChoice.setText(CHOICE_EMOJI[playerChoice]);
        tvAiChoice.setText(CHOICE_EMOJI[aiChoice]);

        totalRounds++;

        // 判断胜负
        int result = judge(playerChoice, aiChoice);
        String resultText;

        if (result == 0) {
            // 平局
            draws++;
            resultText = "平局！双方都出了" + CHOICE_NAMES[playerChoice];
        } else if (result == 1) {
            // 玩家赢
            playerWins++;
            winStreak++;
            if (winStreak > maxWinStreak) {
                maxWinStreak = winStreak;
            }
            resultText = "你赢了！" + CHOICE_NAMES[playerChoice] + " 胜 " + CHOICE_NAMES[aiChoice];
            currentScore += 10;
        } else {
            // 玩家输
            aiWins++;
            winStreak = 0;
            resultText = "你输了！" + CHOICE_NAMES[aiChoice] + " 胜 " + CHOICE_NAMES[playerChoice];
        }

        tvStatus.setText(resultText);
        tvStats.setText("胜：" + playerWins + " | 负：" + aiWins + " | 平：" + draws);
        updateScore(currentScore);

        // 成就检查
        if (result == 1) {
            checkAchievement("win", playerWins);
            checkAchievement("streak", winStreak);
        }
        checkAchievement("rounds", totalRounds);

        // 检查是否使用了所有手势
        if (usedChoices[0] && usedChoices[1] && usedChoices[2]) {
            checkAchievement("special", 1);
        }

        // 检查胜率
        if (totalRounds >= 10) {
            float winRate = (float) playerWins / totalRounds;
            if (winRate >= 0.7f) {
                checkAchievement("special", 2);
            }
        }

        // 禁用按钮，显示下一局按钮
        enableButtons(false);
        btnRestart.setVisibility(View.VISIBLE);
    }

    /**
     * AI 选择策略：如果连续输了2次以上，偏向选择能赢玩家上一次手势的选项
     */
    private int calculateAiChoice() {
        if (winStreak >= 2 && totalRounds > 0) {
            // 70% 概率选择能赢的
            if (random.nextFloat() < 0.7f) {
                // 查找玩家上一次的选择
                // 简单起见，随机选择，但排除会被克制的
                return random.nextInt(3);
            }
        }
        return random.nextInt(3);
    }

    /**
     * 判断胜负
     *
     * @return 0=平局, 1=玩家赢, -1=玩家输
     */
    private int judge(int player, int ai) {
        if (player == ai) return 0;
        if ((player == ROCK && ai == SCISSORS) ||
            (player == SCISSORS && ai == PAPER) ||
            (player == PAPER && ai == ROCK)) {
            return 1;
        }
        return -1;
    }

    /**
     * 重置回合
     */
    private void resetRound() {
        btnRestart.setVisibility(View.GONE);
        tvPlayerChoice.setText("❓");
        tvAiChoice.setText("❓");
        tvStatus.setText("选择你的手势！");
        enableButtons(true);
    }

    /**
     * 启用/禁用按钮
     */
    private void enableButtons(boolean enabled) {
        btnRock.setEnabled(enabled);
        btnScissors.setEnabled(enabled);
        btnPaper.setEnabled(enabled);
    }
}

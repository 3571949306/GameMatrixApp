package com.gamecenter.app.games.dice;

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
        return getString(R.string.game_dice_name);
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
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_dice_color_bg));
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(20f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_dice_color_text_primary));
        tvStatus.setPadding(0, 16, 0, 24);
        tvStatus.setText(getString(R.string.game_dice_status_start));

        // 玩家区域
        TextView tvPlayerLabel = new TextView(this);
        tvPlayerLabel.setGravity(Gravity.CENTER);
        tvPlayerLabel.setTextSize(16f);
        tvPlayerLabel.setTextColor(ContextCompat.getColor(this, R.color.game_dice_color_text_secondary));
        tvPlayerLabel.setText(getString(R.string.game_dice_player_label));

        tvPlayerDice = new TextView(this);
        tvPlayerDice.setGravity(Gravity.CENTER);
        tvPlayerDice.setTextSize(48f);
        tvPlayerDice.setPadding(0, 16, 0, 24);
        tvPlayerDice.setText("⚀ ⚀");

        // 电脑区域
        TextView tvAiLabel = new TextView(this);
        tvAiLabel.setGravity(Gravity.CENTER);
        tvAiLabel.setTextSize(16f);
        tvAiLabel.setTextColor(ContextCompat.getColor(this, R.color.game_dice_color_text_secondary));
        tvAiLabel.setText(getString(R.string.game_dice_ai_label));

        tvAiDice = new TextView(this);
        tvAiDice.setGravity(Gravity.CENTER);
        tvAiDice.setTextSize(48f);
        tvAiDice.setPadding(0, 16, 0, 24);
        tvAiDice.setText("⚀ ⚀");

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_dice_color_text_secondary));
        tvStats.setPadding(0, 8, 0, 24);
        tvStats.setText(getString(R.string.game_dice_stats_format, 0, 0, 0, 0));

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);

        btnRoll = new MaterialButton(this);
        btnRoll.setText(getString(R.string.game_dice_btn_roll));
        btnRoll.setBackgroundColor(ContextCompat.getColor(this, R.color.game_dice_color_btn_primary));
        btnRoll.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams rollParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rollParams.setMargins(8, 0, 8, 0);
        btnRoll.setLayoutParams(rollParams);
        btnRoll.setOnClickListener(v -> onRollDice());

        btnDoubleUp = new MaterialButton(this);
        btnDoubleUp.setText(getString(R.string.game_dice_btn_double));
        btnDoubleUp.setBackgroundColor(ContextCompat.getColor(this, R.color.game_dice_color_btn_double));
        btnDoubleUp.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams doubleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        doubleParams.setMargins(8, 0, 8, 0);
        btnDoubleUp.setLayoutParams(doubleParams);
        btnDoubleUp.setVisibility(View.GONE);
        btnDoubleUp.setOnClickListener(v -> onDoubleUp());

        btnNextRound = new MaterialButton(this);
        btnNextRound.setText(getString(R.string.game_dice_btn_next_round));
        btnNextRound.setBackgroundColor(ContextCompat.getColor(this, R.color.game_dice_color_btn_primary));
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
     * <p>仅掷骰并展示结果预览，不结算分数；结算推迟到玩家决定是否加倍后点击"下一局"时一次性完成，
     * 避免同一局被结算两次。</p>
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

        totalRounds++;
        roundActive = true;
        isDoubleUp = false;

        // 显示加倍按钮
        btnRoll.setVisibility(View.GONE);
        btnDoubleUp.setVisibility(View.VISIBLE);
        btnNextRound.setVisibility(View.VISIBLE);

        // 仅显示结果预览，不结算
        tvStatus.setText(previewResultText(playerDice1 + playerDice2, aiDice1 + aiDice2));
    }

    /**
     * 加倍
     * <p>只标记当前局为加倍状态并刷新预览，不重新掷骰、不二次结算；
     * 最终结算时分数 ×2。</p>
     */
    private void onDoubleUp() {
        if (!roundActive) return;
        isDoubleUp = true;
        btnDoubleUp.setVisibility(View.GONE);
        tvStatus.setText(previewResultText(playerDice1 + playerDice2, aiDice1 + aiDice2));
    }

    /**
     * 生成本局结果预览文本（不修改任何状态）。
     */
    private String previewResultText(int playerTotal, int aiTotal) {
        int multiplier = isDoubleUp ? 2 : 1;
        if (playerTotal > aiTotal) {
            int points = playerTotal * multiplier;
            return getString(R.string.game_dice_result_win, playerTotal, aiTotal, points);
        } else if (playerTotal < aiTotal) {
            return getString(R.string.game_dice_result_lose, playerTotal, aiTotal);
        } else {
            int points = playerTotal * multiplier;
            return getString(R.string.game_dice_result_draw, playerTotal, points);
        }
    }

    /**
     * 最终结算（仅在"下一局"时调用一次，应用加倍倍率）。
     */
    private void resolveRound(int playerTotal, int aiTotal, boolean doubled) {
        String resultText;
        int multiplier = doubled ? 2 : 1;

        if (playerTotal > aiTotal) {
            playerWins++;
            winStreak++;
            int points = playerTotal * multiplier;
            currentScore += points;
            resultText = getString(R.string.game_dice_result_win, playerTotal, aiTotal, points);
            checkAchievement("win", playerWins);
            checkAchievement("streak", winStreak);
            usageStore.recordWin(getGameId());
        } else if (playerTotal < aiTotal) {
            aiWins++;
            winStreak = 0;
            resultText = getString(R.string.game_dice_result_lose, playerTotal, aiTotal);
        } else {
            draws++;
            int points = playerTotal * multiplier;
            currentScore += points;
            resultText = getString(R.string.game_dice_result_draw, playerTotal, points);
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
        tvStats.setText(getString(R.string.game_dice_stats_format, currentScore, playerWins, aiWins, draws));
        updateScore(currentScore);
    }

    /**
     * 下一局
     * <p>对本局进行最终结算（仅一次，应用加倍倍率），然后重置 UI 进入下一局。</p>
     */
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
        tvStatus.setText(getString(R.string.game_dice_status_start));
    }
}

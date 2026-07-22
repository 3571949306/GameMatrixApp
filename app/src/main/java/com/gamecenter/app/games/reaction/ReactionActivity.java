package com.gamecenter.app.games.reaction;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * 反应力游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>屏幕显示一个方块，等待随机时间后变色，玩家需要尽快点击。
 * 随着轮次增加，等待时间缩短，难度递增。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次完成</li>
 *   <li>反应时间 < 300ms</li>
 *   <li>反应时间 < 200ms</li>
 *   <li>连续5轮 < 400ms</li>
 *   <li>累计20轮</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class ReactionActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    // UI 颜色已迁移至 colors_game_group_d.xml，运行时通过 ContextCompat.getColor() 读取

    // ==================== 游戏状态 ====================
    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private long readyTimeMs = 0;
    private int currentRound = 0;
    private int totalRounds = 0;
    private long lastReactionTimeMs = 0;
    private long bestReactionTimeMs = Long.MAX_VALUE;
    private long totalReactionTimeMs = 0;
    private int fastCount = 0; // < 400ms 连续计数
    private boolean waitingForGreen = false;
    private boolean greenShown = false;
    private int baseDelayMs = 3000; // 初始等待时间

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvStats;
    private View targetBox;
    private MaterialButton btnStart;
    private MaterialButton btnRetry;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "reaction";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_reaction_name);
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
        handler.removeCallbacksAndMessages(null);
        waitingForGreen = false;
        greenShown = false;
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        handler.removeCallbacksAndMessages(null);
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
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_bg));
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_reaction_color_text_primary));
        tvStatus.setPadding(0, 16, 0, 16);
        tvStatus.setText(getString(R.string.game_reaction_status_init));

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_reaction_color_text_secondary));
        tvStats.setPadding(0, 8, 0, 24);
        tvStats.setText(getString(R.string.game_reaction_stats_init));

        // 目标方块
        targetBox = new View(this);
        int boxSize = 300;
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(boxSize, boxSize);
        boxParams.gravity = Gravity.CENTER;
        boxParams.setMargins(0, 32, 0, 32);
        targetBox.setLayoutParams(boxParams);
        targetBox.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_idle));
        targetBox.setOnClickListener(v -> onTargetClick());

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText(getString(R.string.game_reaction_btn_start));
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_btn_start));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startRound());

        // 重试按钮
        btnRetry = new MaterialButton(this);
        btnRetry.setText(getString(R.string.game_reaction_btn_retry));
        btnRetry.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_btn_retry_bg));
        btnRetry.setTextColor(ContextCompat.getColor(this, R.color.game_reaction_color_text_secondary));
        btnRetry.setVisibility(View.GONE);
        btnRetry.setOnClickListener(v -> startRound());

        root.addView(tvStatus);
        root.addView(tvStats);
        root.addView(targetBox);
        root.addView(btnStart);
        root.addView(btnRetry);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 开始一轮测试
     */
    private void startRound() {
        if (!isGameRunning || isGamePaused) return;

        // 轮次计数移至成功反应后执行，避免"点早了"的失败轮次被计入 totalRounds
        waitingForGreen = true;
        greenShown = false;

        targetBox.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_waiting));
        tvStatus.setText(getString(R.string.game_reaction_status_waiting));
        btnStart.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        // 随机延迟后变绿
        int delay = baseDelayMs + random.nextInt(2000);
        // 难度递减
        if (currentRound > 3) {
            delay = Math.max(1000, baseDelayMs - (currentRound - 3) * 200) + random.nextInt(1500);
        }

        handler.postDelayed(() -> {
            if (!isGameRunning || isGamePaused) return;
            if (waitingForGreen) {
                targetBox.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_ready));
                tvStatus.setText(getString(R.string.game_reaction_status_go));
                readyTimeMs = System.currentTimeMillis();
                greenShown = true;
            }
        }, delay);
    }

    /**
     * 处理方块点击
     */
    private void onTargetClick() {
        if (!isGameRunning || isGamePaused) return;

        if (!waitingForGreen) {
            // 还没开始新一轮
            return;
        }

        if (!greenShown) {
            // 点早了
            waitingForGreen = false;
            targetBox.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_too_early));
            tvStatus.setText(getString(R.string.game_reaction_status_too_early));
            fastCount = 0;
            btnRetry.setVisibility(View.VISIBLE);
            return;
        }

        // 正常点击 - 计算反应时间
        long reactionMs = System.currentTimeMillis() - readyTimeMs;
        lastReactionTimeMs = reactionMs;
        waitingForGreen = false;
        greenShown = false;
        // 仅成功反应才计入轮次（修复失败轮计入统计 Bug）
        currentRound++;
        totalRounds++;

        if (reactionMs < bestReactionTimeMs) {
            bestReactionTimeMs = reactionMs;
            // 反应时间越小越好，转换为"越大越好"的分数后持久化最高分
            recordHighScore(Math.max(0, 1000 - (int) reactionMs));
        }
        totalReactionTimeMs += reactionMs;

        // 连续快反应计数
        if (reactionMs < 400) {
            fastCount++;
        } else {
            fastCount = 0;
        }

        // 显示结果
        targetBox.setBackgroundColor(ContextCompat.getColor(this, R.color.game_reaction_color_result));
        tvStatus.setText(getString(R.string.game_reaction_status_result, reactionMs));

        // 计算分数
        int score = Math.max(50 - (int)(reactionMs / 20), 5);
        currentScore += score;
        updateScore(currentScore);

        // 更新统计
        long avgMs = totalReactionTimeMs / totalRounds;
        String bestStr = bestReactionTimeMs == Long.MAX_VALUE ? "--" : String.valueOf(bestReactionTimeMs);
        tvStats.setText(getString(R.string.game_reaction_stats_format, bestStr, avgMs, totalRounds));

        // 成就检查
        checkAchievement("win", totalRounds);
        checkAchievement("score", (int) reactionMs);
        checkAchievement("rounds", totalRounds);

        if (reactionMs < 300) {
            checkAchievement("special", 1);
        }
        if (reactionMs < 200) {
            checkAchievement("special", 2);
        }
        if (fastCount >= 5) {
            checkAchievement("special", 3);
        }

        usageStore.recordWin(getGameId());
        btnRetry.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

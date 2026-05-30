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
    private static final int COLOR_WAITING = 0xFFE53935;   // 红色 - 等待
    private static final int COLOR_READY = 0xFF4CAF50;     // 绿色 - 可以点击
    private static final int COLOR_IDLE = 0xFFFBF9F6;      // 白色 - 空闲
    private static final int COLOR_TOO_EARLY = 0xFFFF9800;  // 橙色 - 点早了
    private static final int COLOR_RESULT = 0xFF5B8A72;    // 绿色主题 - 结果

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
        return "反应力";
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
        root.setBackgroundColor(0xFFF5F0E8);
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 16, 0, 16);
        tvStatus.setText("测试你的反应速度！");

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF5B8A72);
        tvStats.setPadding(0, 8, 0, 24);
        tvStats.setText("最佳：-- ms | 平均：-- ms");

        // 目标方块
        targetBox = new View(this);
        int boxSize = 300;
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(boxSize, boxSize);
        boxParams.gravity = Gravity.CENTER;
        boxParams.setMargins(0, 32, 0, 32);
        targetBox.setLayoutParams(boxParams);
        targetBox.setBackgroundColor(COLOR_IDLE);
        targetBox.setOnClickListener(v -> onTargetClick());

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText("开始测试");
        btnStart.setBackgroundColor(0xFF5B8A72);
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startRound());

        // 重试按钮
        btnRetry = new MaterialButton(this);
        btnRetry.setText("再来一轮");
        btnRetry.setBackgroundColor(0xFFFBF9F6);
        btnRetry.setTextColor(0xFF5B8A72);
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

        currentRound++;
        totalRounds++;
        waitingForGreen = true;
        greenShown = false;

        targetBox.setBackgroundColor(COLOR_WAITING);
        tvStatus.setText("等待变绿...");
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
                targetBox.setBackgroundColor(COLOR_READY);
                tvStatus.setText("快点击！");
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
            targetBox.setBackgroundColor(COLOR_TOO_EARLY);
            tvStatus.setText("太早了！等变绿再点");
            fastCount = 0;
            btnRetry.setVisibility(View.VISIBLE);
            return;
        }

        // 正常点击 - 计算反应时间
        long reactionMs = System.currentTimeMillis() - readyTimeMs;
        lastReactionTimeMs = reactionMs;
        waitingForGreen = false;
        greenShown = false;

        if (reactionMs < bestReactionTimeMs) {
            bestReactionTimeMs = reactionMs;
        }
        totalReactionTimeMs += reactionMs;

        // 连续快反应计数
        if (reactionMs < 400) {
            fastCount++;
        } else {
            fastCount = 0;
        }

        // 显示结果
        targetBox.setBackgroundColor(COLOR_RESULT);
        tvStatus.setText("反应时间：" + reactionMs + " ms！");

        // 计算分数
        int score = Math.max(50 - (int)(reactionMs / 20), 5);
        currentScore += score;
        updateScore(currentScore);

        // 更新统计
        long avgMs = totalReactionTimeMs / totalRounds;
        String bestStr = bestReactionTimeMs == Long.MAX_VALUE ? "--" : String.valueOf(bestReactionTimeMs);
        tvStats.setText("最佳：" + bestStr + " ms | 平均：" + avgMs + " ms | 轮次：" + totalRounds);

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

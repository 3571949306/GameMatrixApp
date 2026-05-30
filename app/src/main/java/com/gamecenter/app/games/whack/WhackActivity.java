package com.gamecenter.app.games.whack;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Random;

/**
 * 打地鼠游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>3×3 网格中随机冒出地鼠，玩家需要在限定时间内点击地鼠得分。
 * 随着时间推移，地鼠出现的速度越来越快。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次得分</li>
 *   <li>单局30分+</li>
 *   <li>单局50分+</li>
 *   <li>连续击中10只</li>
 *   <li>累计10局</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class WhackActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    private static final int GRID_SIZE = 3;
    private static final int GAME_DURATION_SEC = 30;
    private static final long INITIAL_MOLE_INTERVAL_MS = 1500;
    private static final long MIN_MOLE_INTERVAL_MS = 500;

    // ==================== 游戏状态 ====================
    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private int score = 0;
    private int hitCount = 0;
    private int missCount = 0;
    private int consecutiveHits = 0;
    private int totalGames = 0;
    private int timeRemaining = GAME_DURATION_SEC;
    private int currentMolePos = -1;
    private long moleIntervalMs = INITIAL_MOLE_INTERVAL_MS;
    private boolean moleVisible = false;
    private boolean gameActive = false;

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvTimer;
    private TextView tvScore;
    private GridLayout gridLayout;
    private MaterialButton[] moleButtons = new MaterialButton[GRID_SIZE * GRID_SIZE];
    private MaterialButton btnStart;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "whack";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return "打地鼠";
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
        stopGameLoop();
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (gameActive) {
            startGameLoop();
        }
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        gameActive = false;
        stopGameLoop();
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
        LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F0E8);
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 16, 0, 8);
        tvStatus.setText("点击开始，消灭地鼠！");

        // 计时器
        tvTimer = new TextView(this);
        tvTimer.setGravity(Gravity.CENTER);
        tvTimer.setTextSize(24f);
        tvTimer.setTextColor(0xFFE53935);
        tvTimer.setText("⏱ " + GAME_DURATION_SEC + "秒");

        // 分数显示
        tvScore = new TextView(this);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setTextSize(16f);
        tvScore.setTextColor(0xFF5B8A72);
        tvScore.setPadding(0, 8, 0, 16);
        tvScore.setText("得分：0");

        // 网格
        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(GRID_SIZE);
        gridLayout.setRowCount(GRID_SIZE);
        gridLayout.setUseDefaultMargins(true);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellSize = (int) (screenWidth * 0.75 / GRID_SIZE);

        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            params.setMargins(6, 6, 6, 6);
            btn.setLayoutParams(params);
            btn.setText("🕳");
            btn.setTextSize(28f);
            btn.setBackgroundColor(0xFF8D6E63);
            btn.setOnClickListener(v -> onMoleClick(index));
            moleButtons[i] = btn;
            gridLayout.addView(btn);
        }

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText("开始游戏");
        btnStart.setBackgroundColor(0xFF5B8A72);
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        root.addView(tvStatus);
        root.addView(tvTimer);
        root.addView(tvScore);
        root.addView(gridLayout);
        root.addView(btnStart);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 开始新游戏
     */
    private void startNewGame() {
        score = 0;
        hitCount = 0;
        missCount = 0;
        consecutiveHits = 0;
        timeRemaining = GAME_DURATION_SEC;
        moleIntervalMs = INITIAL_MOLE_INTERVAL_MS;
        currentMolePos = -1;
        moleVisible = false;
        gameActive = true;
        totalGames++;

        btnStart.setVisibility(View.GONE);
        tvStatus.setText("打地鼠！");
        tvScore.setText("得分：0");
        tvTimer.setText("⏱ " + GAME_DURATION_SEC + "秒");

        // 重置所有格子
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            moleButtons[i].setText("🕳");
            moleButtons[i].setBackgroundColor(0xFF8D6E63);
            moleButtons[i].setEnabled(true);
        }

        startGameLoop();
    }

    /**
     * 开始游戏循环
     */
    private void startGameLoop() {
        // 倒计时
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!gameActive || isGamePaused) return;
                timeRemaining--;
                tvTimer.setText("⏱ " + timeRemaining + "秒");

                if (timeRemaining <= 0) {
                    onGameEnd();
                    return;
                }

                // 难度递增：每10秒加快速度
                if (timeRemaining % 10 == 0) {
                    moleIntervalMs = Math.max(MIN_MOLE_INTERVAL_MS, moleIntervalMs - 300);
                }

                handler.postDelayed(this, 1000);
            }
        }, 1000);

        // 地鼠出现循环
        showNextMole();
    }

    /**
     * 停止游戏循环
     */
    private void stopGameLoop() {
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * 显示下一只地鼠
     */
    private void showNextMole() {
        if (!gameActive || isGamePaused) return;

        // 隐藏之前的地鼠
        if (currentMolePos >= 0) {
            moleButtons[currentMolePos].setText("🕳");
            moleButtons[currentMolePos].setBackgroundColor(0xFF8D6E63);
        }

        // 随机选择新位置
        int newPos;
        do {
            newPos = random.nextInt(GRID_SIZE * GRID_SIZE);
        } while (newPos == currentMolePos);

        currentMolePos = newPos;
        final int finalMolePos = newPos;
        moleVisible = true;
        moleButtons[currentMolePos].setText("🐹");
        moleButtons[currentMolePos].setBackgroundColor(0xFF4CAF50);

        // 设置超时自动消失
        handler.postDelayed(() -> {
            if (moleVisible && currentMolePos == finalMolePos) {
                // 地鼠未被击中
                moleVisible = false;
                missCount++;
                consecutiveHits = 0;
                moleButtons[finalMolePos].setText("🕳");
                moleButtons[finalMolePos].setBackgroundColor(0xFF8D6E63);
                showNextMole();
            }
        }, moleIntervalMs);
    }

    /**
     * 处理地鼠点击
     */
    private void onMoleClick(int index) {
        if (!gameActive || isGamePaused) return;

        if (index == currentMolePos && moleVisible) {
            // 击中地鼠
            moleVisible = false;
            hitCount++;
            consecutiveHits++;
            score += 10 + consecutiveHits;
            currentScore = score;

            moleButtons[index].setText("💥");
            moleButtons[index].setBackgroundColor(0xFFFFEB3B);

            tvScore.setText("得分：" + score);
            updateScore(score);

            // 短暂显示击中效果
            handler.postDelayed(() -> {
                if (gameActive) {
                    moleButtons[index].setText("🕳");
                    moleButtons[index].setBackgroundColor(0xFF8D6E63);
                    showNextMole();
                }
            }, 300);
        } else {
            // 点击了空位
            consecutiveHits = 0;
            missCount++;
        }
    }

    /**
     * 游戏结束
     */
    private void onGameEnd() {
        gameActive = false;
        stopGameLoop();

        // 隐藏最后的地鼠
        if (currentMolePos >= 0) {
            moleButtons[currentMolePos].setText("🕳");
            moleButtons[currentMolePos].setBackgroundColor(0xFF8D6E63);
        }

        tvStatus.setText("⏰ 时间到！得分：" + score);
        btnStart.setText("再来一局");
        btnStart.setVisibility(View.VISIBLE);

        // 成就检查
        checkAchievement("win", totalGames);
        checkAchievement("score", score);
        checkAchievement("rounds", totalGames);

        if (score >= 30) {
            checkAchievement("special", 1);
        }
        if (score >= 50) {
            checkAchievement("special", 2);
        }
        if (consecutiveHits >= 10) {
            checkAchievement("special", 3);
        }

        usageStore.recordWin(getGameId());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

package com.gamecenter.app.games.match;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配对游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>4×4 网格卡牌翻转配对，与 Memory 游戏不同的是：
 * 配对成功的卡牌会消失，而不是变色。随着关卡提升，卡牌数量递增。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>无失误通关</li>
 *   <li>30秒内通关</li>
 *   <li>通过3关</li>
 *   <li>通过5关</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class MatchActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    private static final String[] CARD_SYMBOLS = {"🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑", "🍌", "🥑", "🌽", "🥕"};
    private static final long FLIP_DELAY_MS = 800;

    // ==================== 游戏状态 ====================
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentLevel = 1;
    private int gridRows = 4;
    private int gridCols = 4;
    private int totalCards = 16;
    private int pairCount = 8;
    private int[] cardValues;
    private boolean[] cardMatched;
    private boolean[] cardRevealed;
    private int firstSelectedIndex = -1;
    private int secondSelectedIndex = -1;
    private boolean isProcessing = false;
    private int moveCount = 0;
    private int matchedPairs = 0;
    private int errorCount = 0;
    private int totalCompletions = 0;
    private long startTimeMs = 0;
    private boolean gameActive = false;

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private MaterialButton[] cardButtons;
    private MaterialButton btnStart;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "match";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return "配对";
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
        tvStatus.setText("翻开卡牌找到配对！");

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF5B8A72);
        tvStats.setPadding(0, 8, 0, 16);
        tvStats.setText("关卡：1 | 步数：0");

        // 网格容器
        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);
        gridLayout.setUseDefaultMargins(true);

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText("开始游戏");
        btnStart.setBackgroundColor(0xFF5B8A72);
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        root.addView(tvStatus);
        root.addView(tvStats);
        root.addView(gridLayout);
        root.addView(btnStart);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 开始新游戏
     */
    private void startNewGame() {
        btnStart.setVisibility(View.GONE);
        gameActive = true;
        moveCount = 0;
        matchedPairs = 0;
        errorCount = 0;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
        isProcessing = false;
        startTimeMs = System.currentTimeMillis();

        // 计算当前关卡的网格大小
        if (currentLevel <= 2) {
            gridRows = 4; gridCols = 4; pairCount = 8;
        } else if (currentLevel <= 4) {
            gridRows = 4; gridCols = 5; pairCount = 10;
        } else {
            gridRows = 4; gridCols = 6; pairCount = 12;
        }
        totalCards = gridRows * gridCols;

        // 创建配对
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values);

        cardValues = new int[totalCards];
        cardMatched = new boolean[totalCards];
        cardRevealed = new boolean[totalCards];
        for (int i = 0; i < totalCards; i++) {
            cardValues[i] = values.get(i);
        }

        // 重建网格
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardSize = (int) (screenWidth * 0.85 / gridCols);

        cardButtons = new MaterialButton[totalCards];
        for (int i = 0; i < totalCards; i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardSize;
            params.height = cardSize;
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(20f);
            btn.setTextColor(0xFF5B8A72);
            btn.setBackgroundColor(0xFFFBF9F6);
            btn.setOnClickListener(v -> onCardClick(index));
            cardButtons[i] = btn;
            gridLayout.addView(btn);
        }

        tvStatus.setText("关卡 " + currentLevel + " - 翻开卡牌找配对！");
        updateStatsDisplay();
    }

    /**
     * 处理卡牌点击
     */
    private void onCardClick(int index) {
        if (!gameActive || isProcessing || isGamePaused) return;
        if (cardMatched[index] || cardRevealed[index]) return;
        if (index == firstSelectedIndex) return;

        // 翻开卡牌
        revealCard(index);

        if (firstSelectedIndex == -1) {
            firstSelectedIndex = index;
        } else {
            secondSelectedIndex = index;
            moveCount++;
            isProcessing = true;

            if (cardValues[firstSelectedIndex] == cardValues[secondSelectedIndex]) {
                handler.postDelayed(() -> {
                    onMatchSuccess();
                    isProcessing = false;
                }, FLIP_DELAY_MS);
            } else {
                errorCount++;
                handler.postDelayed(() -> {
                    hideCard(firstSelectedIndex);
                    hideCard(secondSelectedIndex);
                    firstSelectedIndex = -1;
                    secondSelectedIndex = -1;
                    isProcessing = false;
                }, FLIP_DELAY_MS);
            }
        }

        updateStatsDisplay();
    }

    private void revealCard(int index) {
        cardRevealed[index] = true;
        cardButtons[index].setText(CARD_SYMBOLS[cardValues[index]]);
        cardButtons[index].setTextColor(0xFF2D2D2D);
        cardButtons[index].setBackgroundColor(0xFFE8F5E9);
    }

    private void hideCard(int index) {
        cardRevealed[index] = false;
        cardButtons[index].setText("?");
        cardButtons[index].setTextColor(0xFF5B8A72);
        cardButtons[index].setBackgroundColor(0xFFFBF9F6);
    }

    /**
     * 配对成功处理
     */
    private void onMatchSuccess() {
        cardMatched[firstSelectedIndex] = true;
        cardMatched[secondSelectedIndex] = true;

        // 卡牌消失效果
        cardButtons[firstSelectedIndex].setVisibility(View.INVISIBLE);
        cardButtons[secondSelectedIndex].setVisibility(View.INVISIBLE);

        matchedPairs++;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;

        updateStatsDisplay();

        // 检查是否全部配对完成
        if (matchedPairs == pairCount) {
            onLevelComplete();
        }
    }

    /**
     * 关卡通关处理
     */
    private void onLevelComplete() {
        gameActive = false;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;
        totalCompletions++;

        // 计算分数
        int score = Math.max(100 - moveCount * 3, 10) * currentLevel;
        currentScore += score;
        updateScore(currentScore);

        tvStatus.setText("🎉 关卡 " + currentLevel + " 通关！用了 " + moveCount + " 步，耗时 " + elapsedSec + " 秒");

        // 成就检查
        checkAchievement("win", totalCompletions);
        checkAchievement("score", moveCount);
        checkAchievement("time", (int) elapsedSec);
        checkAchievement("level", currentLevel);

        if (errorCount == 0) {
            checkAchievement("special", 1);
        }
        if (elapsedSec <= 30) {
            checkAchievement("special", 2);
        }

        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);

        // 进入下一关
        currentLevel++;

        btnStart.setText("下一关（关卡 " + currentLevel + "）");
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText("关卡：" + currentLevel + " | 步数：" + moveCount + " | 配对：" + matchedPairs + "/" + pairCount);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

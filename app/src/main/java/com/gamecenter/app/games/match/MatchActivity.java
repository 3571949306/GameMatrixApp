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
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;
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
    /** 当前难度对应的基础网格大小（由 onDifficultyChanged 设置） */
    private int baseGridRows = 4;
    private int baseGridCols = 4;
    private int basePairCount = 8;
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
        return getString(R.string.game_match_name);
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

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_match_diff_easy), 1, getString(R.string.game_match_diff_easy_desc), 0, 0, 1.0f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_match_diff_normal), 2, getString(R.string.game_match_diff_normal_desc), 0, 0, 1.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_match_diff_hard), 3, getString(R.string.game_match_diff_hard_desc), 0, 0, 2.0f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        // 根据难度调整初始网格大小
        switch (newLevel.level) {
            case 1:
                baseGridRows = 4; baseGridCols = 4; basePairCount = 8;
                break;
            case 2:
                baseGridRows = 4; baseGridCols = 5; basePairCount = 10;
                break;
            case 3:
                baseGridRows = 4; baseGridCols = 6; basePairCount = 12;
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
        LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_match_color_bg));
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_match_color_text));
        tvStatus.setPadding(0, 16, 0, 8);
        tvStatus.setText(R.string.game_match_status_intro);

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_match_color_stats));
        tvStats.setPadding(0, 8, 0, 16);
        tvStats.setText(R.string.game_match_stats_initial);

        // 网格容器
        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);
        gridLayout.setUseDefaultMargins(true);

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_match_start);
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_match_color_btn_start));
        btnStart.setTextColor(ContextCompat.getColor(this, R.color.game_match_color_btn_start_text));
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

        // 网格大小由当前难度决定（难度系统取代原 currentLevel 网格递增）
        gridRows = baseGridRows;
        gridCols = baseGridCols;
        pairCount = basePairCount;
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
            btn.setTextColor(ContextCompat.getColor(this, R.color.game_match_color_card_back_text));
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_match_color_card_back));
            btn.setOnClickListener(v -> onCardClick(index));
            cardButtons[i] = btn;
            gridLayout.addView(btn);
        }

        tvStatus.setText(getString(R.string.game_match_level_intro, currentLevel));
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
        cardButtons[index].setTextColor(ContextCompat.getColor(this, R.color.game_match_color_card_front_text));
        cardButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_match_color_card_front));
    }

    private void hideCard(int index) {
        cardRevealed[index] = false;
        cardButtons[index].setText("?");
        cardButtons[index].setTextColor(ContextCompat.getColor(this, R.color.game_match_color_card_back_text));
        cardButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_match_color_card_back));
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

        tvStatus.setText(getString(R.string.game_match_level_complete, currentLevel, moveCount, elapsedSec));

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

        // 最高分持久化
        recordHighScore(currentScore);

        // 进入下一关
        currentLevel++;

        btnStart.setText(getString(R.string.game_match_next_level, currentLevel));
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText(getString(R.string.game_match_stats, currentLevel, moveCount, matchedPairs, pairCount));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

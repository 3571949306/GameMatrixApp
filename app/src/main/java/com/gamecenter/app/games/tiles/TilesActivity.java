package com.gamecenter.app.games.tiles;

import android.graphics.Color;
import android.os.Bundle;
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
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 麻将连连看游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>在网格中找出两个相同的牌并通过连线消除。
 * 简化实现：直接点击两张相同的牌消除（无需路径判定）。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>30秒内通关</li>
 *   <li>通过3关</li>
 *   <li>连续消除5对</li>
 *   <li>累计10关</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class TilesActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    private static final String[] TILE_SYMBOLS = {
            "🀇", "🀈", "🀉", "🀊", "🀋", "🀌", "🀍", "🀎", "🀏", "🀙",
            "🀚", "🀛", "🀜", "🀝", "🀞", "🀟", "🀠", "🀡"
    };
    private static final String[] SIMPLE_SYMBOLS = {
            "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "東", "南", "西", "北", "中", "發", "🀀", "🀁", "🀂"
    };

    // ==================== 游戏状态 ====================
    private int currentLevel = 1;
    private int gridRows = 4;
    private int gridCols = 6;
    private int totalTiles = 24;
    private int pairCount = 12;
    private int[] tileValues;
    private boolean[] tileMatched;
    private boolean[] tileRevealed;
    private int firstSelectedIndex = -1;
    private int secondSelectedIndex = -1;
    private boolean isProcessing = false;
    private int moveCount = 0;
    private int matchedPairs = 0;
    private int consecutiveMatches = 0;
    private long startTimeMs = 0;
    private boolean gameActive = false;

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private MaterialButton[] tileButtons;
    private MaterialButton btnStart;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "tiles";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_tiles_name);
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
        LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_tiles_color_bg));
        root.setPadding(16, 16, 16, 16);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_tiles_color_text_primary));
        tvStatus.setPadding(0, 8, 0, 8);
        tvStatus.setText(getString(R.string.game_tiles_status_hint));

        // 统计
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_tiles_color_accent));
        tvStats.setPadding(0, 4, 0, 12);
        tvStats.setText(getString(R.string.game_tiles_stats, currentLevel, pairCount, moveCount));

        // 网格
        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);
        gridLayout.setUseDefaultMargins(true);

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText(getString(R.string.game_tiles_start));
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_tiles_color_accent));
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
        consecutiveMatches = 0;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
        isProcessing = false;
        startTimeMs = System.currentTimeMillis();

        // 关卡难度递增
        if (currentLevel <= 2) {
            gridRows = 4; gridCols = 6; pairCount = 12;
        } else if (currentLevel <= 4) {
            gridRows = 6; gridCols = 6; pairCount = 18;
        } else {
            gridRows = 6; gridCols = 8; pairCount = 24;
        }
        totalTiles = gridRows * gridCols;

        // 创建配对
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            values.add(i % SIMPLE_SYMBOLS.length);
            values.add(i % SIMPLE_SYMBOLS.length);
        }
        Collections.shuffle(values);

        tileValues = new int[totalTiles];
        tileMatched = new boolean[totalTiles];
        tileRevealed = new boolean[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            tileValues[i] = values.get(i);
        }

        // 重建网格
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int tileSize = (int) (screenWidth * 0.9 / gridCols);
        tileSize = Math.min(tileSize, 70);

        tileButtons = new MaterialButton[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileSize;
            params.height = tileSize;
            params.setMargins(2, 2, 2, 2);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(14f);
            btn.setTextColor(ContextCompat.getColor(this, R.color.game_tiles_color_accent));
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_tiles_color_tile_bg));
            btn.setOnClickListener(v -> onTileClick(index));
            tileButtons[i] = btn;
            gridLayout.addView(btn);
        }

        tvStatus.setText(getString(R.string.game_tiles_level_hint, currentLevel));
        updateStatsDisplay();
    }

    /**
     * 处理牌点击
     */
    private void onTileClick(int index) {
        if (!gameActive || isProcessing || isGamePaused) return;
        if (tileMatched[index] || tileRevealed[index]) return;
        if (index == firstSelectedIndex) return;

        revealTile(index);

        if (firstSelectedIndex == -1) {
            firstSelectedIndex = index;
        } else {
            secondSelectedIndex = index;
            moveCount++;
            isProcessing = true;

            if (tileValues[firstSelectedIndex] == tileValues[secondSelectedIndex]) {
                // 匹配成功
                tileMatched[firstSelectedIndex] = true;
                tileMatched[secondSelectedIndex] = true;
                tileButtons[firstSelectedIndex].setVisibility(View.INVISIBLE);
                tileButtons[secondSelectedIndex].setVisibility(View.INVISIBLE);
                matchedPairs++;
                consecutiveMatches++;

                firstSelectedIndex = -1;
                secondSelectedIndex = -1;
                isProcessing = false;

                updateStatsDisplay();

                if (matchedPairs == pairCount) {
                    onLevelComplete();
                }
            } else {
                consecutiveMatches = 0;
                // 延迟翻回
                final int fIdx = firstSelectedIndex;
                final int sIdx = secondSelectedIndex;
                tileButtons[fIdx].postDelayed(() -> {
                    hideTile(fIdx);
                    hideTile(sIdx);
                    firstSelectedIndex = -1;
                    secondSelectedIndex = -1;
                    isProcessing = false;
                }, 600);
            }
        }
    }

    private void revealTile(int index) {
        tileRevealed[index] = true;
        tileButtons[index].setText(SIMPLE_SYMBOLS[tileValues[index]]);
        tileButtons[index].setTextColor(ContextCompat.getColor(this, R.color.game_tiles_color_text_primary));
        tileButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_tiles_color_tile_revealed_bg));
    }

    private void hideTile(int index) {
        tileRevealed[index] = false;
        tileButtons[index].setText("?");
        tileButtons[index].setTextColor(ContextCompat.getColor(this, R.color.game_tiles_color_accent));
        tileButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_tiles_color_tile_bg));
    }

    /**
     * 关卡通关
     */
    private void onLevelComplete() {
        gameActive = false;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;

        int score = Math.max(200 - moveCount * 5, 20) * currentLevel;
        currentScore += score;
        updateScore(currentScore);

        tvStatus.setText(getString(R.string.game_tiles_level_complete, currentLevel, moveCount, elapsedSec));

        checkAchievement("win", currentLevel);
        checkAchievement("score", moveCount);
        checkAchievement("time", (int) elapsedSec);
        checkAchievement("rounds", currentLevel);
        checkAchievement("streak", consecutiveMatches);

        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);

        currentLevel++;

        btnStart.setText(getString(R.string.game_tiles_next_level, currentLevel));
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText(getString(R.string.game_tiles_stats, currentLevel, pairCount - matchedPairs, moveCount));
    }
}

package com.gamecenter.app.games.memory;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记忆翻牌游戏 Activity。
 *
 * <p>4×4 网格（8对卡牌），玩家翻开两张卡牌，若相同则配对成功。
 * 记录步数和用时，通关后计算成就。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>12步内通关</li>
 *   <li>30秒内通关</li>
 *   <li>无失误通关（无翻错）</li>
 *   <li>记忆大师（累计10次通关）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class MemoryActivity extends BaseGameActivity {

    /** 网格行数 */
    private static final int GRID_ROWS = 4;
    /** 网格列数 */
    private static final int GRID_COLS = 4;
    /** 总卡牌数 */
    private static final int TOTAL_CARDS = GRID_ROWS * GRID_COLS;
    /** 对数 */
    private static final int PAIR_COUNT = TOTAL_CARDS / 2;

    /** 卡牌图案（emoji 表情符号） */
    private static final String[] CARD_SYMBOLS = {"🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑"};

    /** 翻牌延迟（毫秒） */
    private static final long FLIP_DELAY_MS = 800;

    // 游戏状态
    private int[] cardValues = new int[TOTAL_CARDS];
    private boolean[] cardRevealed = new boolean[TOTAL_CARDS];
    private boolean[] cardMatched = new boolean[TOTAL_CARDS];
    private int firstSelectedIndex = -1;
    private int secondSelectedIndex = -1;
    private boolean isProcessing = false;
    private int moveCount = 0;
    private int matchedPairs = 0;
    private int errorCount = 0;
    private int totalCompletions = 0;
    private long startTimeMs = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

    // UI 组件
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private MaterialButton[] cardButtons;
    private MaterialButton btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ==================== BaseGameActivity 抽象方法实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "memory";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_memory_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F0E8);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 32, 0, 8);
        tvStatus.setText(R.string.game_memory_press_start);

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF5B8A72);
        tvStats.setPadding(0, 8, 0, 16);
        tvStats.setText("");

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_memory_start);
        btnStart.setOnClickListener(v -> startNewGame());
        btnStart.setBackgroundColor(0xFF5B8A72);

        // 卡牌网格
        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(GRID_COLS);
        gridLayout.setRowCount(GRID_ROWS);
        gridLayout.setUseDefaultMargins(true);
        gridLayout.setVisibility(View.GONE);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardSize = (int) (screenWidth * 0.85 / GRID_COLS);

        cardButtons = new MaterialButton[TOTAL_CARDS];
        for (int i = 0; i < TOTAL_CARDS; i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardSize;
            params.height = cardSize;
            params.setMargins(6, 6, 6, 6);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(24f);
            btn.setTextColor(0xFF5B8A72);
            btn.setBackgroundColor(0xFFFBF9F6);
            btn.setOnClickListener(v -> onCardClick(index));
            cardButtons[i] = btn;
            gridLayout.addView(btn);
        }

        root.addView(tvStatus);
        root.addView(tvStats);
        root.addView(btnStart);
        root.addView(gridLayout);

        return root;
    }

    /**
     * 开始新游戏
     */
    private void startNewGame() {
        btnStart.setVisibility(View.GONE);
        gridLayout.setVisibility(View.VISIBLE);

        // 初始化卡牌
        cardValues = new int[TOTAL_CARDS];
        cardRevealed = new boolean[TOTAL_CARDS];
        cardMatched = new boolean[TOTAL_CARDS];
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
        isProcessing = false;
        moveCount = 0;
        matchedPairs = 0;
        errorCount = 0;
        startTimeMs = System.currentTimeMillis();

        // 创建配对
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < PAIR_COUNT; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values);
        for (int i = 0; i < TOTAL_CARDS; i++) {
            cardValues[i] = values.get(i);
        }

        // 更新 UI
        for (int i = 0; i < TOTAL_CARDS; i++) {
            cardButtons[i].setText("?");
            cardButtons[i].setTextColor(0xFF5B8A72);
            cardButtons[i].setBackgroundColor(0xFFFBF9F6);
            cardButtons[i].setEnabled(true);
        }

        tvStatus.setText(R.string.game_memory_playing);
        updateStatsDisplay();

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 处理卡牌点击
     */
    private void onCardClick(int index) {
        if (isProcessing) return;
        if (cardMatched[index]) return;
        if (cardRevealed[index]) return;
        if (index == firstSelectedIndex) return;

        // 翻开卡牌
        revealCard(index);

        if (firstSelectedIndex == -1) {
            // 第一张翻开
            firstSelectedIndex = index;
        } else {
            // 第二张翻开
            secondSelectedIndex = index;
            moveCount++;
            isProcessing = true;

            // 检查是否配对
            if (cardValues[firstSelectedIndex] == cardValues[secondSelectedIndex]) {
                // 配对成功
                handler.postDelayed(() -> {
                    onMatchSuccess();
                    isProcessing = false;
                }, FLIP_DELAY_MS);
            } else {
                // 配对失败
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

    /**
     * 翻开卡牌
     */
    private void revealCard(int index) {
        cardRevealed[index] = true;
        cardButtons[index].setText(CARD_SYMBOLS[cardValues[index]]);
        cardButtons[index].setTextColor(0xFF2D2D2D);
        cardButtons[index].setBackgroundColor(0xFFE8F5E9);
    }

    /**
     * 翻回卡牌
     */
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
        cardButtons[firstSelectedIndex].setBackgroundColor(0xFF5B8A72);
        cardButtons[firstSelectedIndex].setTextColor(0xFFFFFFFF);
        cardButtons[secondSelectedIndex].setBackgroundColor(0xFF5B8A72);
        cardButtons[secondSelectedIndex].setTextColor(0xFFFFFFFF);
        cardButtons[firstSelectedIndex].setEnabled(false);
        cardButtons[secondSelectedIndex].setEnabled(false);

        matchedPairs++;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;

        updateStatsDisplay();

        // 检查是否全部配对完成
        if (matchedPairs == PAIR_COUNT) {
            onGameComplete();
        }
    }

    /**
     * 游戏通关处理
     */
    private void onGameComplete() {
        isGameRunning = false;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;
        totalCompletions++;

        tvStatus.setText(getString(R.string.game_memory_congratulations, moveCount, elapsedSec));

        // 成就检查
        checkAchievement("win", totalCompletions);
        checkAchievement("score", moveCount);
        checkAchievement("time", (int) elapsedSec);
        checkAchievement("special", errorCount == 0);

        // 更新分数
        int score = Math.max(100 - moveCount * 5, 10);
        updateScore(currentScore + score);

        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);

        // 显示重新开始按钮
        btnStart.setText(R.string.game_memory_play_again);
        btnStart.setVisibility(View.VISIBLE);
    }

    /**
     * 更新统计显示
     */
    private void updateStatsDisplay() {
        long elapsed = 0;
        if (startTimeMs > 0 && isGameRunning) {
            elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
        }
        tvStats.setText(getString(R.string.game_memory_stats, moveCount, matchedPairs, PAIR_COUNT, elapsed));
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

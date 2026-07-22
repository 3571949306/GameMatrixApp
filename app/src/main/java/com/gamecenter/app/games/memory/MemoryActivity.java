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
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;
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

    /** 网格行数（可变，由难度设置） */
    private int gridRows = 4;
    /** 网格列数（可变，由难度设置） */
    private int gridCols = 4;
    /** 总卡牌数（可变，由难度设置） */
    private int totalCards = gridRows * gridCols;
    /** 对数（可变，由难度设置） */
    private int pairCount = totalCards / 2;

    /** 卡牌图案（emoji 表情符号），覆盖最高难度（12 对）所需 */
    private static final String[] CARD_SYMBOLS = {"🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑", "🍌", "🥑", "🌽", "🥕"};

    /** 翻牌延迟（毫秒） */
    private static final long FLIP_DELAY_MS = 800;

    // 游戏状态
    private int[] cardValues = new int[totalCards];
    private boolean[] cardRevealed = new boolean[totalCards];
    private boolean[] cardMatched = new boolean[totalCards];
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

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_memory_diff_easy), 1, getString(R.string.game_memory_diff_easy_desc), 0, 0, 1.0f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_memory_diff_normal), 2, getString(R.string.game_memory_diff_normal_desc), 0, 0, 1.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_memory_diff_hard), 3, getString(R.string.game_memory_diff_hard_desc), 0, 0, 2.0f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        // 根据难度设置可变网格大小字段（实际网格在 startNewGame->rebuildGrid 中重建）
        switch (newLevel.level) {
            case 1:
                gridRows = 4; gridCols = 4;
                break;
            case 2:
                gridRows = 4; gridCols = 5;
                break;
            case 3:
                gridRows = 4; gridCols = 6;
                break;
            default:
                break;
        }
        totalCards = gridRows * gridCols;
        pairCount = totalCards / 2;
    }

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_bg));

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_text));
        tvStatus.setPadding(0, 32, 0, 8);
        tvStatus.setText(R.string.game_memory_press_start);

        // 统计信息
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_stats));
        tvStats.setPadding(0, 8, 0, 16);
        tvStats.setText("");

        // 开始按钮
        btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_memory_start);
        btnStart.setOnClickListener(v -> startNewGame());
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_btn_start));

        // 卡牌网格
        gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);
        gridLayout.setUseDefaultMargins(true);
        gridLayout.setVisibility(View.GONE);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardSize = (int) (screenWidth * 0.85 / gridCols);

        cardButtons = new MaterialButton[totalCards];
        for (int i = 0; i < totalCards; i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardSize;
            params.height = cardSize;
            params.setMargins(6, 6, 6, 6);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(24f);
            btn.setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_card_back_text));
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_card_back));
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
        cardValues = new int[totalCards];
        cardRevealed = new boolean[totalCards];
        cardMatched = new boolean[totalCards];
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
        isProcessing = false;
        moveCount = 0;
        matchedPairs = 0;
        errorCount = 0;
        startTimeMs = System.currentTimeMillis();

        // 创建配对
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values);
        for (int i = 0; i < totalCards; i++) {
            cardValues[i] = values.get(i);
        }

        // 重建网格以适配当前难度对应的网格大小
        rebuildGrid();

        tvStatus.setText(R.string.game_memory_playing);
        updateStatsDisplay();

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 根据当前网格大小重建卡牌网格。
     *
     * <p>难度变更会改变 gridRows/gridCols/totalCards，因此每局开始时重建网格，
     * 确保按钮数量与当前难度匹配（简单 4x4 / 普通 4x5 / 困难 4x6）。</p>
     */
    private void rebuildGrid() {
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
            params.setMargins(6, 6, 6, 6);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(24f);
            btn.setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_card_back_text));
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_card_back));
            btn.setOnClickListener(v -> onCardClick(index));
            cardButtons[i] = btn;
            gridLayout.addView(btn);
        }
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
        cardButtons[index].setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_card_front_text));
        cardButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_card_front));
    }

    /**
     * 翻回卡牌
     */
    private void hideCard(int index) {
        cardRevealed[index] = false;
        cardButtons[index].setText("?");
        cardButtons[index].setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_card_back_text));
        cardButtons[index].setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_card_back));
    }

    /**
     * 配对成功处理
     */
    private void onMatchSuccess() {
        cardMatched[firstSelectedIndex] = true;
        cardMatched[secondSelectedIndex] = true;
        cardButtons[firstSelectedIndex].setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_card_matched));
        cardButtons[firstSelectedIndex].setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_card_matched_text));
        cardButtons[secondSelectedIndex].setBackgroundColor(ContextCompat.getColor(this, R.color.game_memory_color_card_matched));
        cardButtons[secondSelectedIndex].setTextColor(ContextCompat.getColor(this, R.color.game_memory_color_card_matched_text));
        cardButtons[firstSelectedIndex].setEnabled(false);
        cardButtons[secondSelectedIndex].setEnabled(false);

        matchedPairs++;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;

        updateStatsDisplay();

        // 检查是否全部配对完成
        if (matchedPairs == pairCount) {
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

        // 最高分持久化
        recordHighScore(currentScore);

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
        tvStats.setText(getString(R.string.game_memory_stats, moveCount, matchedPairs, pairCount, elapsed));
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

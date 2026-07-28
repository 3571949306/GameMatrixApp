package com.gamecenter.app.match;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配对消除 Fragment（从 MatchActivity 迁移）。
 *
 * <p>4×4 网格卡牌翻转配对，与记忆翻牌不同：配对成功的卡牌会消失。
 * 关卡递增，难度可调（简单 4x4 / 普通 4x5 / 困难 4x6）。</p>
 */
public class MatchModuleFragment extends Fragment {

    private static final String TAG = "MatchModuleFragment";
    private static final String GAME_ID = "match";
    private static final long FLIP_DELAY_MS = 800;

    private static final String[] CARD_SYMBOLS = {"🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑", "🍌", "🥑", "🌽", "🥕"};

    // 基础网格参数（由难度设置）
    private int baseGridRows = 4;
    private int baseGridCols = 4;
    private int basePairCount = 8;

    // 当前关卡网格参数
    private int currentLevel = 1;
    private int gridRows = 4;
    private int gridCols = 4;
    private int totalCards = 16;
    private int pairCount = 8;

    // 游戏状态
    private int[] cardValues;
    private boolean[] cardMatched;
    private boolean[] cardRevealed;
    private int firstSelectedIndex = -1;
    private int secondSelectedIndex = -1;
    private boolean isProcessing = false;
    private int moveCount = 0;
    private int matchedPairs = 0;
    private int errorCount = 0;
    private long startTimeMs = 0;
    private boolean gameActive = false;
    private int currentScore = 0;
    private int highScore = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

    // UI 组件
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private Button[] cardButtons;
    private Button btnStart;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;

    private SaveManager saveManager;
    private GameUsageStore usageStore;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F5F5);
        root.setPadding((int) (16 * dp), (int) (20 * dp), (int) (16 * dp), (int) (20 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_match));
        tvTitle.setTextSize(26);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.bottomMargin = (int) (8 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 难度按钮区
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.bottomMargin = (int) (8 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = new Button(ctx);
        btnEasy.setText(getString(R.string.game_match_easy));
        btnEasy.setTextSize(12f);
        LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        easyLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnEasy.setLayoutParams(easyLp);
        diffBar.addView(btnEasy);

        btnNormal = new Button(ctx);
        btnNormal.setText(getString(R.string.game_match_normal));
        btnNormal.setTextSize(12f);
        LinearLayout.LayoutParams normalLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        normalLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnNormal.setLayoutParams(normalLp);
        diffBar.addView(btnNormal);

        btnHard = new Button(ctx);
        btnHard.setText(getString(R.string.game_match_hard));
        btnHard.setTextSize(12f);
        LinearLayout.LayoutParams hardLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        hardLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnHard.setLayoutParams(hardLp);
        diffBar.addView(btnHard);

        root.addView(diffBar);

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(0xFF212121);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        tvStatus.setText(getString(R.string.game_click_to_start));
        root.addView(tvStatus);

        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF1976D2);
        tvStats.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
        tvStats.setText(String.format("关卡 %d  步数 0  配对 0/%d", currentLevel, pairCount));
        root.addView(tvStats);

        gridLayout = new GridLayout(ctx);
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);
        gridLayout.setUseDefaultMargins(true);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLayout.setLayoutParams(gridLp);
        root.addView(gridLayout);

        btnStart = new Button(ctx);
        btnStart.setText(getString(R.string.game_btn_start));
        btnStart.setBackgroundColor(0xFF1976D2);
        btnStart.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.gravity = Gravity.CENTER;
        startLp.topMargin = (int) (12 * dp);
        btnStart.setLayoutParams(startLp);
        root.addView(btnStart);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        highScore = loadHighScore();

        btnEasy.setOnClickListener(v -> { setDifficulty(4, 4, 8); });
        btnNormal.setOnClickListener(v -> { setDifficulty(4, 5, 10); });
        btnHard.setOnClickListener(v -> { setDifficulty(4, 6, 12); });
        btnStart.setOnClickListener(v -> startNewGame());
    }

    private void setDifficulty(int rows, int cols, int pairs) {
        baseGridRows = rows;
        baseGridCols = cols;
        basePairCount = pairs;
        tvStatus.setText(String.format("难度已设为 %dx%d（%d 对），点击开始", rows, cols, pairs));
    }

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

        gridRows = baseGridRows;
        gridCols = baseGridCols;
        pairCount = basePairCount;
        totalCards = gridRows * gridCols;

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

        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardSize = (int) (screenWidth * 0.85 / gridCols);

        cardButtons = new Button[totalCards];
        for (int i = 0; i < totalCards; i++) {
            final int index = i;
            Button btn = new Button(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardSize;
            params.height = cardSize;
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(20f);
            btn.setTextColor(0xFF757575);
            btn.setBackgroundColor(0xFFEF9A9A);
            btn.setOnClickListener(v -> onCardClick(index));
            cardButtons[i] = btn;
            gridLayout.addView(btn);
        }

        tvStatus.setText(String.format("第 %d 关，翻开两张相同的卡牌消除", currentLevel));
        updateStatsDisplay();
    }

    private void onCardClick(int index) {
        if (!gameActive || isProcessing) return;
        if (cardMatched[index] || cardRevealed[index]) return;
        if (index == firstSelectedIndex) return;

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
        cardButtons[index].setTextColor(0xFF212121);
        cardButtons[index].setBackgroundColor(0xFFFFF8E1);
    }

    private void hideCard(int index) {
        cardRevealed[index] = false;
        cardButtons[index].setText("?");
        cardButtons[index].setTextColor(0xFF757575);
        cardButtons[index].setBackgroundColor(0xFFEF9A9A);
    }

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

        if (matchedPairs == pairCount) {
            onLevelComplete();
        }
    }

    private void onLevelComplete() {
        gameActive = false;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;

        int score = Math.max(100 - moveCount * 3, 10) * currentLevel;
        currentScore += score;

        tvStatus.setText(String.format("第 %d 关通关！步数 %d，用时 %d 秒，+%d 分", currentLevel, moveCount, elapsedSec, score));

        if (currentScore > highScore) {
            highScore = currentScore;
            saveHighScore(highScore);
        }
        usageStore.recordScore(GAME_ID, highScore);

        currentLevel++;
        btnStart.setText(String.format("下一关 %d", currentLevel));
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        tvStats.setText(String.format("关卡 %d  步数 %d  配对 %d/%d", currentLevel, moveCount, matchedPairs, pairCount));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private int loadHighScore() {
        String progressJson = saveManager.loadProgress(GAME_ID);
        if (progressJson != null) {
            try {
                JSONObject obj = new JSONObject(progressJson);
                return obj.optInt("highScore", 0);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void saveHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception e) {
            Log.w(TAG, "存档操作失败", e);
        }
    }
}

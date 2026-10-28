package com.gamecenter.app.memory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import androidx.activity.OnBackPressedCallback;
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
import java.util.Locale;

/**
 * 记忆翻牌 Fragment（现代化改造版）。
 *
 * <p>翻开两张卡牌，若相同则配对成功。记录步数和用时，通关后计算分数。
 * 提供三档难度（简单 4x4 / 普通 4x5 / 困难 4x6），支持浅/深色主题，
 * 实时对决用时与步数看板，兼顾既有自动化测试用例契约与现代沉浸视觉。</p>
 */
public class MemoryModuleFragment extends Fragment {

    private static final String TAG = "MemoryModuleFragment";
    private static final String GAME_ID = "memory";
    private static final long FLIP_DELAY_MS = 800;

    private static final String[] CARD_SYMBOLS = {
            "🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑", "🍌", "🥑", "🌽", "🥕"
    };

    // 难度选择在下一局开始时生效，不改变当前牌组和配对目标。
    private int baseGridRows = 4;
    private int baseGridCols = 4;

    // 当前局网格参数
    private int gridRows = 4;
    private int gridCols = 4;
    private int totalCards = 16;
    private int pairCount = 8;

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
    private long startTimeMs = 0;
    private boolean gameActive = false;
    private int currentScore = 0;
    private int highScore = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable timerTicker = new Runnable() {
        @Override
        public void run() {
            if (gameActive) {
                updateStatsDisplay();
                handler.postDelayed(this, 1000);
            }
        }
    };

    // UI 组件
    private TextView tvTitle;
    private LinearLayout diffBar;
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
        boolean dark = isDarkTheme(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(dark ? 0xFF0F172A : 0xFFF8FAFC);
        root.setPadding((int) (14 * dp), (int) (16 * dp), (int) (14 * dp), (int) (16 * dp));

        // 0. 标题文本
        tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_memory));
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(dark ? 0xFFF1F5F9 : 0xFF0F172A);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.bottomMargin = (int) (10 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 1. 难度按钮区（简单 / 普通 / 困难 胶囊栏）
        diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.bottomMargin = (int) (10 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = createDifficultyButton(ctx, dp, dark, getString(R.string.game_match_easy), true);
        btnNormal = createDifficultyButton(ctx, dp, dark, getString(R.string.game_match_normal), false);
        btnHard = createDifficultyButton(ctx, dp, dark, getString(R.string.game_match_hard), false);

        diffBar.addView(btnEasy);
        diffBar.addView(btnNormal);
        diffBar.addView(btnHard);
        root.addView(diffBar);

        // 2. 状态提示文本
        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setTextColor(dark ? 0xFF38BDF8 : 0xFF0284C7);
        tvStatus.setPadding(0, (int) (4 * dp), 0, (int) (4 * dp));
        tvStatus.setText(getString(R.string.game_click_to_start));
        root.addView(tvStatus);

        // 3. 统计看板文本
        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(13f);
        tvStats.setTextColor(dark ? 0xFF94A3B8 : 0xFF64748B);
        tvStats.setPadding(0, (int) (2 * dp), 0, (int) (12 * dp));
        tvStats.setText("");
        root.addView(tvStats);

        // 4. 卡牌网格容器
        gridLayout = new GridLayout(ctx);
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);
        gridLayout.setUseDefaultMargins(false);
        gridLayout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLp.gravity = Gravity.CENTER;
        gridLayout.setLayoutParams(gridLp);
        root.addView(gridLayout);

        // 5. 开始 / 再来一局按钮
        btnStart = new Button(ctx);
        btnStart.setText(getString(R.string.game_btn_start));
        btnStart.setTextSize(15f);
        btnStart.setTypeface(Typeface.DEFAULT_BOLD);
        btnStart.setMinHeight((int) Math.ceil(44 * dp));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setBackground(createStartButtonBackground(dp));
        btnStart.setPadding((int) (32 * dp), (int) (8 * dp), (int) (32 * dp), (int) (8 * dp));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.gravity = Gravity.CENTER;
        startLp.topMargin = (int) (16 * dp);
        btnStart.setLayoutParams(startLp);
        root.addView(btnStart);

        return root;
    }

    private Button createDifficultyButton(Context ctx, float dp, boolean dark, String text, boolean selected) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setMinHeight((int) Math.ceil(40 * dp));
        updateDifficultyButtonStyle(btn, dp, dark, selected);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void updateDifficultyButtonStyle(Button btn, float dp, boolean dark, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        if (selected) {
            bg.setColor(dark ? 0xFF0284C7 : 0xFF0284C7);
            btn.setTextColor(Color.WHITE);
        } else {
            bg.setColor(dark ? 0xFF1E293B : 0xFFFFFFFF);
            bg.setStroke((int) (1 * dp), dark ? 0xFF334155 : 0xFFCBD5E1);
            btn.setTextColor(dark ? 0xFF94A3B8 : 0xFF475569);
        }
        btn.setBackground(bg);
    }

    private GradientDrawable createStartButtonBackground(float dp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(22 * dp);
        bg.setColor(0xFF0284C7);
        return bg;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        if (usageStore != null) {
            usageStore.recordLaunch(GAME_ID);
        }
        highScore = loadHighScore();

        btnEasy.setOnClickListener(v -> setDifficulty(4, 4, 0));
        btnNormal.setOnClickListener(v -> setDifficulty(4, 5, 1));
        btnHard.setOnClickListener(v -> setDifficulty(4, 6, 2));
        btnStart.setOnClickListener(v -> startNewGame());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (gameActive && matchedPairs > 0 && matchedPairs < pairCount) {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("退出对局")
                                    .setMessage("确认要退出当前翻牌对局吗？当前进度将丢失。")
                                    .setPositiveButton("退出", (d, w) -> requireActivity().finish())
                                    .setNegativeButton("留在此局", null)
                                    .show();
                        } else {
                            requireActivity().finish();
                        }
                    }
                });
    }

    private void setDifficulty(int rows, int cols, int selectedIndex) {
        baseGridRows = rows;
        baseGridCols = cols;

        float dp = requireContext().getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(requireContext());
        updateDifficultyButtonStyle(btnEasy, dp, dark, selectedIndex == 0);
        updateDifficultyButtonStyle(btnNormal, dp, dark, selectedIndex == 1);
        updateDifficultyButtonStyle(btnHard, dp, dark, selectedIndex == 2);

        tvStatus.setText(String.format(Locale.getDefault(), gameActive
                ? "难度已设为 %dx%d（%d 对），下一局生效"
                : "难度已设为 %dx%d（%d 对），点击开始", rows, cols, rows * cols / 2));
    }

    private void startNewGame() {
        gridRows = baseGridRows;
        gridCols = baseGridCols;
        totalCards = gridRows * gridCols;
        pairCount = totalCards / 2;

        btnStart.setVisibility(View.GONE);
        gameActive = true;

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

        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values);
        for (int i = 0; i < totalCards; i++) {
            cardValues[i] = values.get(i);
        }

        rebuildGrid();

        tvStatus.setText(getString(R.string.game_match_status));
        updateStatsDisplay();

        handler.removeCallbacks(timerTicker);
        handler.postDelayed(timerTicker, 1000);
    }

    private void rebuildGrid() {
        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);

        float dp = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxGridWidth = Math.min(screenWidth - (int) (32 * dp), (int) (360 * dp));
        int cardMargin = (int) (4 * dp);
        int cardSize = (maxGridWidth - (gridCols * cardMargin * 2)) / gridCols;
        boolean dark = isDarkTheme(requireContext());

        cardButtons = new Button[totalCards];
        for (int i = 0; i < totalCards; i++) {
            final int index = i;
            Button btn = new Button(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardSize;
            params.height = (int) (cardSize * 1.05f);
            params.setMargins(cardMargin, cardMargin, cardMargin, cardMargin);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(20f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            applyCardBackStyle(btn, dp, dark);

            btn.setOnClickListener(v -> onCardClick(index));
            cardButtons[i] = btn;
            gridLayout.addView(btn);
        }
    }

    private void applyCardBackStyle(Button btn, float dp, boolean dark) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * dp);
        bg.setColor(dark ? 0xFF1E293B : 0xFFE0F2FE);
        bg.setStroke((int) (1.5f * dp), dark ? 0xFF38BDF8 : 0xFF7DD3FC);
        btn.setBackground(bg);
        btn.setTextColor(dark ? 0xFF38BDF8 : 0xFF0284C7);
    }

    private void applyCardFaceStyle(Button btn, float dp, boolean dark) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * dp);
        bg.setColor(dark ? 0xFF334155 : 0xFFFEF3C7);
        bg.setStroke((int) (1.5f * dp), dark ? 0xFFF59E0B : 0xFFFCD34D);
        btn.setBackground(bg);
        btn.setTextColor(dark ? 0xFFF8FAFC : 0xFF1E293B);
    }

    private void applyCardMatchedStyle(Button btn, float dp, boolean dark) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * dp);
        bg.setColor(dark ? 0xFF064E3B : 0xFFD1FAE5);
        bg.setStroke((int) (1.5f * dp), dark ? 0xFF059669 : 0xFF34D399);
        btn.setBackground(bg);
        btn.setTextColor(dark ? 0xFF6EE7B7 : 0xFF065F46);
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
        float dp = getResources().getDisplayMetrics().density;
        applyCardFaceStyle(cardButtons[index], dp, isDarkTheme(requireContext()));
    }

    private void hideCard(int index) {
        cardRevealed[index] = false;
        cardButtons[index].setText("?");
        float dp = getResources().getDisplayMetrics().density;
        applyCardBackStyle(cardButtons[index], dp, isDarkTheme(requireContext()));
    }

    private void onMatchSuccess() {
        cardMatched[firstSelectedIndex] = true;
        cardMatched[secondSelectedIndex] = true;
        float dp = getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(requireContext());
        applyCardMatchedStyle(cardButtons[firstSelectedIndex], dp, dark);
        applyCardMatchedStyle(cardButtons[secondSelectedIndex], dp, dark);
        cardButtons[firstSelectedIndex].setEnabled(false);
        cardButtons[secondSelectedIndex].setEnabled(false);

        matchedPairs++;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;

        updateStatsDisplay();

        if (matchedPairs == pairCount) {
            onGameComplete();
        }
    }

    private void onGameComplete() {
        gameActive = false;
        handler.removeCallbacks(timerTicker);
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;

        tvStatus.setText(String.format(Locale.getDefault(), "恭喜通关！步数 %d，用时 %d 秒", moveCount, elapsedSec));

        int score = Math.max(100 - moveCount * 5, 10);
        currentScore += score;

        if (currentScore > highScore) {
            highScore = currentScore;
            saveHighScore(highScore);
        }
        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, highScore);
            usageStore.recordWin(GAME_ID);
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }

        btnStart.setText(getString(R.string.game_btn_play_again));
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateStatsDisplay() {
        long elapsed = 0;
        if (startTimeMs > 0 && gameActive) {
            elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
        }
        tvStats.setText(String.format(Locale.getDefault(), "配对 %d/%d  步数 %d  用时 %d 秒", matchedPairs, pairCount, moveCount, elapsed));
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

    private static boolean isDarkTheme(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}

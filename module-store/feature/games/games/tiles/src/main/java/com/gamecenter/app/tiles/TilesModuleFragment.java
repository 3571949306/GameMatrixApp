package com.gamecenter.app.tiles;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
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
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 麻将连连看 Fragment（从 TilesActivity 迁移）。
 *
 * <p>在网格中找出两个相同的牌并消除。简化实现：直接点击两张相同的牌消除。
 * 关卡难度随等级递增（4x6 / 6x6 / 6x8）。</p>
 */
public class TilesModuleFragment extends Fragment {

    private static final String TAG = "TilesModuleFragment";
    private static final String GAME_ID = "tiles";

    private static final String[] SIMPLE_SYMBOLS = {
            "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "東", "南", "西", "北", "中", "發", "🀀", "🀁", "🀂"
    };

    // 游戏状态
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
    private long startTimeMs = 0;
    private boolean gameActive = false;
    private int currentScore = 0;
    private int highScore = 0;

    // UI 组件
    private TextView tvStatus;
    private TextView tvStats;
    private GridLayout gridLayout;
    private Button[] tileButtons;
    private Button btnStart;

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
        root.setPadding((int) (12 * dp), (int) (20 * dp), (int) (12 * dp), (int) (20 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("麻将连连看");
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

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(0xFF212121);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        tvStatus.setText("点击开始按钮启动游戏");
        root.addView(tvStatus);

        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(0xFF1976D2);
        tvStats.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
        tvStats.setText(String.format("关卡 %d  剩余 %d 对  步数 %d", currentLevel, pairCount, moveCount));
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
        btnStart.setText("开始游戏");
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

        btnStart.setOnClickListener(v -> startNewGame());
    }

    private void startNewGame() {
        btnStart.setVisibility(View.GONE);
        gameActive = true;
        moveCount = 0;
        matchedPairs = 0;
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
        isProcessing = false;
        startTimeMs = System.currentTimeMillis();

        if (currentLevel <= 2) {
            gridRows = 4; gridCols = 6; pairCount = 12;
        } else if (currentLevel <= 4) {
            gridRows = 6; gridCols = 6; pairCount = 18;
        } else {
            gridRows = 6; gridCols = 8; pairCount = 24;
        }
        totalTiles = gridRows * gridCols;

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

        gridLayout.removeAllViews();
        gridLayout.setColumnCount(gridCols);
        gridLayout.setRowCount(gridRows);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int tileSize = (int) (screenWidth * 0.9 / gridCols);
        tileSize = Math.min(tileSize, 70);

        tileButtons = new Button[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            final int index = i;
            Button btn = new Button(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileSize;
            params.height = tileSize;
            params.setMargins(2, 2, 2, 2);
            btn.setLayoutParams(params);
            btn.setText("?");
            btn.setTextSize(14f);
            btn.setTextColor(0xFF1976D2);
            btn.setBackgroundColor(0xFFEEEEEE);
            btn.setOnClickListener(v -> onTileClick(index));
            tileButtons[i] = btn;
            gridLayout.addView(btn);
        }

        tvStatus.setText(String.format("第 %d 关，找出所有配对", currentLevel));
        updateStatsDisplay();
    }

    private void onTileClick(int index) {
        if (!gameActive || isProcessing) return;
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
                tileMatched[firstSelectedIndex] = true;
                tileMatched[secondSelectedIndex] = true;
                tileButtons[firstSelectedIndex].setVisibility(View.INVISIBLE);
                tileButtons[secondSelectedIndex].setVisibility(View.INVISIBLE);
                matchedPairs++;

                firstSelectedIndex = -1;
                secondSelectedIndex = -1;
                isProcessing = false;

                updateStatsDisplay();

                if (matchedPairs == pairCount) {
                    onLevelComplete();
                }
            } else {
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
        tileButtons[index].setTextColor(0xFF212121);
        tileButtons[index].setBackgroundColor(0xFFFFF8E1);
    }

    private void hideTile(int index) {
        tileRevealed[index] = false;
        tileButtons[index].setText("?");
        tileButtons[index].setTextColor(0xFF1976D2);
        tileButtons[index].setBackgroundColor(0xFFEEEEEE);
    }

    private void onLevelComplete() {
        gameActive = false;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;

        int score = Math.max(200 - moveCount * 5, 20) * currentLevel;
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
        tvStats.setText(String.format("关卡 %d  剩余 %d 对  步数 %d", currentLevel, pairCount - matchedPairs, moveCount));
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

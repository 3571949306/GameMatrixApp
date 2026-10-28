package com.gamecenter.app.tiles;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 麻将连连看 Fragment (五星级即开即玩现代化升级)。
 */
public class TilesModuleFragment extends Fragment {

    private static final String GAME_ID = "tiles";
    private static final String PREF_NAME = "tiles_prefs";
    private static final String KEY_DIFFICULTY = "difficulty";

    private static final String[] DIFFICULTY_NAMES = {"休闲轻松 (4x4)", "经典挑战 (4x6)", "大师硬核 (6x6)"};

    private final TilesGame game = new TilesGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout;
    private TextView tvNavDifficulty;
    private TextView tvHudRemaining;
    private TextView tvHudMoves;
    private TextView tvHudBest;
    private Chronometer chronoTimer;
    private GridLayout gridLayout;
    private Button[] tileButtons;

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private SharedPreferences prefs;

    private int currentDifficulty = TilesGame.DIFFICULTY_CLASSIC;
    private int highScore = 0;
    private boolean isProcessing = false;
    private long startTimeMs = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        rootLayout = new LinearLayout(ctx);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(dark ? 0xFF14171D : 0xFFF2F5F8);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. 顶部标准导航栏
        View navBar = createNavBar(ctx, dp, dark);
        rootLayout.addView(navBar);

        // 2. HUD 战况看板
        View hudBar = createHudBar(ctx, dp, dark);
        rootLayout.addView(hudBar);

        // 3. 牌面容器 (支持小屏滑动)
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);

        LinearLayout gridContainer = new LinearLayout(ctx);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setGravity(Gravity.CENTER);
        gridContainer.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));

        gridLayout = new GridLayout(ctx);
        gridLayout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        gridLayout.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.gravity = Gravity.CENTER;
        gridLayout.setLayoutParams(gridLp);

        gridContainer.addView(gridLayout);
        scrollView.addView(gridContainer);
        rootLayout.addView(scrollView);

        // 4. 底部胶囊控制栏
        View bottomBar = createBottomBar(ctx, dp, dark);
        rootLayout.addView(bottomBar);

        return rootLayout;
    }

    private View createNavBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        bar.setBackgroundColor(dark ? 0xFF1E222B : 0xFFFFFFFF);

        // 返回按钮
        Button btnBack = new Button(ctx);
        btnBack.setText("❮ 返回");
        btnBack.setTextSize(13f);
        btnBack.setTextColor(dark ? 0xFFEEEEEE : 0xFF333333);
        applyCapsuleStyle(btnBack, dark ? 0xFF2A303C : 0xFFEEEEEE, dp);
        btnBack.setOnClickListener(v -> showExitConfirmDialog());
        bar.addView(btnBack);

        // 居中标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("🀄 麻将连连看");
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextColor(dark ? 0xFFFFFFFF : 0xFF222222);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        bar.addView(tvTitle, titleParams);

        // 难度胶囊
        tvNavDifficulty = new TextView(ctx);
        tvNavDifficulty.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        tvNavDifficulty.setTextSize(12f);
        tvNavDifficulty.setTextColor(0xFF2E7D32);
        tvNavDifficulty.setPadding((int) (10 * dp), (int) (6 * dp), (int) (10 * dp), (int) (6 * dp));
        applyCapsuleStyle(tvNavDifficulty, dark ? 0xFF1B2E1E : 0xFFE8F5E9, dp);
        tvNavDifficulty.setOnClickListener(v -> showDifficultyDialog());
        bar.addView(tvNavDifficulty);

        // 规则说明按钮
        Button btnRule = new Button(ctx);
        btnRule.setText("ℹ");
        btnRule.setTextSize(14f);
        btnRule.setTextColor(dark ? 0xFFB0BEC5 : 0xFF607D8B);
        applyCapsuleStyle(btnRule, dark ? 0xFF2A303C : 0xFFECEFF1, dp);
        btnRule.setOnClickListener(v -> showRuleDialog());
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        ruleParams.setMargins((int) (6 * dp), 0, 0, 0);
        bar.addView(btnRule, ruleParams);

        // 设置按钮
        Button btnSettings = new Button(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(14f);
        btnSettings.setTextColor(dark ? 0xFFB0BEC5 : 0xFF607D8B);
        applyCapsuleStyle(btnSettings, dark ? 0xFF2A303C : 0xFFECEFF1, dp);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        setParams.setMargins((int) (6 * dp), 0, 0, 0);
        bar.addView(btnSettings, setParams);

        return bar;
    }

    private View createHudBar(Context ctx, float dp, boolean dark) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));
        hud.setBackgroundColor(dark ? 0xFF242A36 : 0xFFE3E8EE);

        tvHudRemaining = createHudVal(ctx, "0", 0xFF2E7D32);
        tvHudMoves = createHudVal(ctx, "0", 0xFF1976D2);

        chronoTimer = new Chronometer(ctx);
        chronoTimer.setTextSize(14f);
        chronoTimer.setGravity(Gravity.CENTER);
        chronoTimer.setTextColor(dark ? 0xFFEEEEEE : 0xFF333333);

        tvHudBest = createHudVal(ctx, "0", 0xFFE65100);

        hud.addView(wrapHudItem(tvHudRemaining, "剩余", dark));
        hud.addView(wrapHudItem(tvHudMoves, "步数", dark));
        hud.addView(wrapHudItem(chronoTimer, "时间", dark));
        hud.addView(wrapHudItem(tvHudBest, "最高分", dark));

        return hud;
    }

    private TextView createHudVal(Context ctx, String initVal, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(initVal);
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(color);
        return tv;
    }

    private View wrapHudItem(View valView, String label, boolean dark) {
        LinearLayout box = new LinearLayout(valView.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        box.setLayoutParams(lp);

        TextView tvLabel = new TextView(valView.getContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setTextColor(dark ? 0xFF9E9E9E : 0xFF616161);

        box.addView(tvLabel);
        box.addView(valView);
        return box;
    }

    private View createBottomBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (14 * dp));

        Button btnRestart = new Button(ctx);
        btnRestart.setText("🔄 重新发牌");
        btnRestart.setTextSize(14f);
        btnRestart.setTextColor(Color.WHITE);
        applyCapsuleStyle(btnRestart, 0xFF2E7D32, dp);
        btnRestart.setOnClickListener(v -> startNewGame());
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rLp.setMargins(0, 0, (int) (8 * dp), 0);
        bar.addView(btnRestart, rLp);

        Button btnHint = new Button(ctx);
        btnHint.setText("💡 智能提示");
        btnHint.setTextSize(14f);
        btnHint.setTextColor(Color.WHITE);
        applyCapsuleStyle(btnHint, 0xFF1976D2, dp);
        btnHint.setOnClickListener(v -> showHint());
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        hLp.setMargins((int) (8 * dp), 0, 0, 0);
        bar.addView(btnHint, hLp);

        return bar;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        currentDifficulty = prefs.getInt(KEY_DIFFICULTY, TilesGame.DIFFICULTY_CLASSIC);
        game.setDifficulty(currentDifficulty);
        tvNavDifficulty.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");

        highScore = loadHighScore();
        tvHudBest.setText(String.valueOf(highScore));

        startNewGame();
    }

    private void startNewGame() {
        handler.removeCallbacksAndMessages(null);
        isProcessing = false;
        game.startNewGame();

        chronoTimer.stop();
        chronoTimer.setBase(SystemClock.elapsedRealtime());
        chronoTimer.start();
        startTimeMs = System.currentTimeMillis();

        buildGrid();
        updateHud();
    }

    private void buildGrid() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cols = game.getCols();
        int rows = game.getRows();
        int total = game.getTotalTiles();

        gridLayout.removeAllViews();
        gridLayout.setColumnCount(cols);
        gridLayout.setRowCount(rows);

        int maxTileWidth = (int) ((screenWidth - 48 * dp) / cols);
        int tileSize = Math.min(maxTileWidth, (int) (58 * dp));
        int margin = (int) (3 * dp);

        tileButtons = new Button[total];
        for (int i = 0; i < total; i++) {
            final int index = i;
            Button btn = new Button(ctx);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = tileSize;
            glp.height = (int) (tileSize * 1.25f);
            glp.setMargins(margin, margin, margin, margin);
            btn.setLayoutParams(glp);

            btn.setText("🀄");
            btn.setTextSize(tileSize > 45 * dp ? 15f : 12f);
            btn.setTextColor(0xFF388E3C);
            applyTileCardStyle(btn, 0xFFE0E0E0, 0xFF9E9E9E, dp);

            btn.setOnClickListener(v -> onTileClick(index));
            tileButtons[i] = btn;
            gridLayout.addView(btn);
        }
    }

    private void onTileClick(int index) {
        if (isProcessing) return;
        if (game.isTileMatched(index) || game.isTileRevealed(index)) return;

        float dp = getResources().getDisplayMetrics().density;
        int result = game.onTileClick(index);

        if (result == TilesGame.RESULT_FIRST_SELECTED) {
            revealButton(index, dp);
        } else if (result == TilesGame.RESULT_MATCH) {
            revealButton(index, dp);
            isProcessing = true;
            updateHud();

            final int fIdx = game.getFirstSelectedIndex();
            final int sIdx = game.getSecondSelectedIndex();

            handler.postDelayed(() -> {
                if (fIdx != -1 && sIdx != -1) {
                    fadeMatchedTile(fIdx);
                    fadeMatchedTile(sIdx);
                }
                game.resolveMatch();
                isProcessing = false;
                updateHud();

                if (game.isGameComplete()) {
                    onGameWon();
                }
            }, 300);
        } else if (result == TilesGame.RESULT_MISMATCH) {
            revealButton(index, dp);
            isProcessing = true;
            updateHud();

            final int fIdx = game.getFirstSelectedIndex();
            final int sIdx = game.getSecondSelectedIndex();

            handler.postDelayed(() -> {
                if (fIdx != -1) hideButton(fIdx, dp);
                if (sIdx != -1) hideButton(sIdx, dp);
                game.resolveMismatch();
                isProcessing = false;
                updateHud();
            }, 550);
        }
    }

    private void revealButton(int index, float dp) {
        Button btn = tileButtons[index];
        btn.setText(game.getTileSymbol(index));
        btn.setTextColor(0xFFBF360C);
        applyTileCardStyle(btn, 0xFFFFF8E1, 0xFFFFB300, dp);
    }

    private void hideButton(int index, float dp) {
        Button btn = tileButtons[index];
        btn.setText("🀄");
        btn.setTextColor(0xFF388E3C);
        applyTileCardStyle(btn, 0xFFE0E0E0, 0xFF9E9E9E, dp);
    }

    private void fadeMatchedTile(int index) {
        Button btn = tileButtons[index];
        btn.setVisibility(View.INVISIBLE);
        btn.setEnabled(false);
    }

    private void showHint() {
        if (isProcessing || game.isGameComplete()) return;
        int[] pair = game.findHintPair();
        if (pair == null) {
            Toast.makeText(requireContext(), "没有可消除的配对了！", Toast.LENGTH_SHORT).show();
            return;
        }

        float dp = getResources().getDisplayMetrics().density;
        applyTileCardStyle(tileButtons[pair[0]], 0xFFFFE082, 0xFFFF6F00, dp);
        applyTileCardStyle(tileButtons[pair[1]], 0xFFFFE082, 0xFFFF6F00, dp);

        Toast.makeText(requireContext(), "已为您高亮一对可消除麻将牌！", Toast.LENGTH_SHORT).show();
    }

    private void onGameWon() {
        chronoTimer.stop();
        long elapsedSec = Math.max(1, (System.currentTimeMillis() - startTimeMs) / 1000);
        int score = game.computeScore(elapsedSec);

        if (score > highScore) {
            highScore = score;
            saveHighScore(highScore);
            tvHudBest.setText(String.valueOf(highScore));
        }

        usageStore.recordWin(GAME_ID);
        usageStore.recordScore(GAME_ID, highScore);

        new AlertDialog.Builder(requireContext())
                .setTitle("🎉 恭喜通关！")
                .setMessage("用时: " + elapsedSec + " 秒\n"
                        + "步数: " + game.getMoveCount() + " 步\n"
                        + "获得积分: +" + score + " 分\n"
                        + "历史最高: " + highScore + " 分")
                .setPositiveButton("再来一局", (d, w) -> startNewGame())
                .setNeutralButton("切换难度", (d, w) -> showDifficultyDialog())
                .setNegativeButton("返回大厅", (d, w) -> {
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .setCancelable(false)
                .show();
    }

    private void updateHud() {
        if (tvHudRemaining != null) {
            tvHudRemaining.setText(game.getRemainingPairs() + " 对");
        }
        if (tvHudMoves != null) {
            tvHudMoves.setText(game.getMoveCount() + " 步");
        }
    }

    private void showDifficultyDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("选择麻将连连看难度")
                .setSingleChoiceItems(DIFFICULTY_NAMES, currentDifficulty, (dialog, which) -> {
                    currentDifficulty = which;
                    game.setDifficulty(which);
                    prefs.edit().putInt(KEY_DIFFICULTY, which).apply();
                    tvNavDifficulty.setText(DIFFICULTY_NAMES[which] + " ▾");
                    startNewGame();
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "难度已切换为: " + DIFFICULTY_NAMES[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRuleDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("🀄 麻将连连看规则")
                .setMessage("1. 玩法规则：\n"
                        + "点击翻开两张麻将牌，如果牌面花色文字相同，即可成功消除并得分；若不相同，则会翻转盖回。\n\n"
                        + "2. 难度分级：\n"
                        + "• 休闲轻松: 4x4 网格 (8 对)，适合速配热身；\n"
                        + "• 经典挑战: 4x6 网格 (12 对)，标准经典体验；\n"
                        + "• 大师硬核: 6x6 网格 (18 对)，考验最强脑力！\n\n"
                        + "3. 智能辅助：\n"
                        + "遇阻时可随时点击底部【智能提示】高亮一对可消除牌面。")
                .setPositiveButton("我知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("设置与选项")
                .setItems(new String[]{"切换难度档位", "重置最高纪录", "查看游戏规则"}, (dialog, which) -> {
                    if (which == 0) {
                        showDifficultyDialog();
                    } else if (which == 1) {
                        clearHighScore();
                    } else if (which == 2) {
                        showRuleDialog();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出确认")
                .setMessage("当前对局进度将重置，确定要返回大厅吗？")
                .setPositiveButton("确定退出", (dialog, which) -> {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                })
                .setNegativeButton("继续对局", null)
                .show();
    }

    private void clearHighScore() {
        highScore = 0;
        saveHighScore(0);
        tvHudBest.setText("0");
        Toast.makeText(requireContext(), "最高纪录已重置", Toast.LENGTH_SHORT).show();
    }

    private int loadHighScore() {
        try {
            String json = saveManager.loadProgress(GAME_ID);
            if (json != null) {
                return new JSONObject(json).optInt("highScore", 0);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void saveHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception ignored) {
        }
    }

    private void applyTileCardStyle(View v, int bgColor, int strokeColor, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(8 * dp);
        gd.setColor(bgColor);
        gd.setStroke((int) (1.5f * dp), strokeColor);
        v.setBackground(gd);
    }

    private void applyCapsuleStyle(View view, int bgColor, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(18 * dp);
        gd.setColor(bgColor);
        view.setBackground(gd);
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (chronoTimer != null) {
            chronoTimer.stop();
        }
    }
}

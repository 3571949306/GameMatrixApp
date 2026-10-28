package com.gamecenter.app.whack;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;
import com.gamecenter.app.core.common.ModuleScopedPreferences;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 打地鼠模块 Fragment（即开即玩战场 + 标准顶部导航 + 战况HUD + 难度分级）。
 */
public class WhackModuleFragment extends Fragment {

    private static final String GAME_ID = "whack";
    private static final String PREF_NAME = "whack_prefs";
    private static final String KEY_DIFFICULTY = "last_difficulty";

    private static final int COLOR_HOLE = 0xFF5D4037;
    private static final int COLOR_MOLE = 0xFFFF7043;
    private static final int COLOR_HIT = 0xFFFFD54F;

    private final WhackGame game = new WhackGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvDifficultyBadge;
    private TextView tvTimerHud;
    private TextView tvScoreHud;
    private TextView tvComboHud;
    private TextView tvBestHud;

    private Button btnPause;
    private Button[] moleButtons = new Button[WhackGame.GRID_SIZE * WhackGame.GRID_SIZE];

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private boolean paused = false;
    private int bestScore = 0;

    private int colorBg;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorCardBg;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        resolveThemeColors(ctx);

        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        usageStore.recordLaunch(GAME_ID);
        bestScore = loadHighScore();

        int savedDiff = ModuleScopedPreferences.get(ctx, GAME_ID, PREF_NAME)
                .getInt(KEY_DIFFICULTY, WhackGame.DIFFICULTY_NORMAL);
        game.setDifficulty(savedDiff);

        ScrollView rootScroll = new ScrollView(ctx);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(colorBg);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (16 * dp));
        rootScroll.addView(root);

        // 1. 标准统一顶部导航栏
        root.addView(createTopBar(ctx, dp));

        // 2. 战况 HUD 看板
        root.addView(createHudDashboard(ctx, dp));

        // 3. 3x3 地鼠战场
        root.addView(createBattlefield(ctx, dp));

        // 4. 底部控制栏（暂停/继续、重新开始）
        root.addView(createBottomControlBar(ctx, dp));

        // 即开即玩：首屏秒入战场开打
        startNewGame();

        return rootScroll;
    }

    private void resolveThemeColors(Context ctx) {
        boolean dark = isDarkTheme(ctx);
        colorBg = dark ? 0xFF121418 : 0xFFF9F7F3;
        colorTextPrimary = dark ? 0xFFEEEEEE : 0xFF2D231F;
        colorTextSecondary = dark ? 0xFFB0BEC5 : 0xFF795548;
        colorAccent = dark ? 0xFFFFAB40 : 0xFFE65100;
        colorCardBg = dark ? 0xFF1E222B : 0xFFFFFFFF;
    }

    private View createTopBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));

        TextView tvBack = new TextView(ctx);
        tvBack.setText("❮");
        tvBack.setTextSize(20f);
        tvBack.setTextColor(colorTextPrimary);
        tvBack.setPadding((int) (8 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        tvBack.setOnClickListener(v -> handleBackPress());
        bar.addView(tvBack);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(R.string.game_whack);
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        bar.addView(tvTitle);

        tvDifficultyBadge = new TextView(ctx);
        updateDifficultyBadgeText();
        tvDifficultyBadge.setTextSize(12f);
        tvDifficultyBadge.setTextColor(colorAccent);
        tvDifficultyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvDifficultyBadge.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(applyAlpha(colorAccent, 0.15f));
        badgeBg.setCornerRadius(12 * dp);
        tvDifficultyBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = (int) (10 * dp);
        tvDifficultyBadge.setLayoutParams(badgeLp);
        tvDifficultyBadge.setOnClickListener(v -> showDifficultyPicker());
        bar.addView(tvDifficultyBadge);

        View spacer = new View(ctx);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1f);
        bar.addView(spacer, spacerLp);

        TextView tvRules = new TextView(ctx);
        tvRules.setText("ℹ");
        tvRules.setTextSize(18f);
        tvRules.setTypeface(Typeface.DEFAULT_BOLD);
        tvRules.setTextColor(colorTextSecondary);
        tvRules.setPadding((int) (10 * dp), (int) (8 * dp), (int) (10 * dp), (int) (8 * dp));
        tvRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(tvRules);

        TextView tvSettings = new TextView(ctx);
        tvSettings.setText("⚙");
        tvSettings.setTextSize(18f);
        tvSettings.setTextColor(colorTextSecondary);
        tvSettings.setPadding((int) (10 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        tvSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(tvSettings);

        return bar;
    }

    private View createHudDashboard(Context ctx, float dp) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorCardBg);
        bg.setCornerRadius(14 * dp);
        bg.setStroke((int) (1 * dp), applyAlpha(colorTextSecondary, 0.2f));
        hud.setBackground(bg);

        LinearLayout.LayoutParams hudLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hudLp.bottomMargin = (int) (14 * dp);
        hud.setLayoutParams(hudLp);

        tvTimerHud = createHudColumn(ctx, "⏱ 倒计时", "30s", colorAccent);
        tvScoreHud = createHudColumn(ctx, "⭐ 得分", "0", colorTextPrimary);
        tvComboHud = createHudColumn(ctx, "🔥 连击", "0 COMBO", 0xFFFF7043);
        tvBestHud = createHudColumn(ctx, "🏆 纪录", String.valueOf(bestScore), colorTextSecondary);

        hud.addView(createColumnWrapper(tvTimerHud, 1f));
        hud.addView(createColumnWrapper(tvScoreHud, 1f));
        hud.addView(createColumnWrapper(tvComboHud, 1f));
        hud.addView(createColumnWrapper(tvBestHud, 1f));

        return hud;
    }

    private TextView createHudColumn(Context ctx, String label, String value, int valColor) {
        TextView tv = new TextView(ctx);
        tv.setGravity(Gravity.CENTER);
        updateHudText(tv, label, value, valColor);
        return tv;
    }

    private View createColumnWrapper(TextView tv, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void updateHudText(TextView tv, String label, String value, int valColor) {
        if (tv == null) return;
        tv.setText(label + "\n" + value);
        tv.setTextColor(valColor);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private View createBattlefield(Context ctx, float dp) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding((int) (12 * dp), (int) (16 * dp), (int) (12 * dp), (int) (16 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorCardBg);
        bg.setCornerRadius(16 * dp);
        bg.setStroke((int) (1 * dp), applyAlpha(colorTextSecondary, 0.2f));
        container.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (14 * dp);
        container.setLayoutParams(lp);

        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(WhackGame.GRID_SIZE);
        grid.setRowCount(WhackGame.GRID_SIZE);
        grid.setUseDefaultMargins(false);

        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int cellSize = Math.min((int) (screenWidth * 0.76 / WhackGame.GRID_SIZE), (int) (96 * dp));

        for (int i = 0; i < WhackGame.GRID_SIZE * WhackGame.GRID_SIZE; i++) {
            final int index = i;
            Button btn = new Button(ctx);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = cellSize;
            int margin = (int) (6 * dp);
            params.setMargins(margin, margin, margin, margin);
            btn.setLayoutParams(params);
            btn.setText("🕳");
            btn.setTextSize(32f);

            GradientDrawable holeBg = new GradientDrawable();
            holeBg.setColor(COLOR_HOLE);
            holeBg.setCornerRadius(cellSize / 2f);
            btn.setBackground(holeBg);

            btn.setOnClickListener(v -> onMoleClick(index));
            moleButtons[i] = btn;
            grid.addView(btn);
        }

        container.addView(grid);
        return container;
    }

    private View createBottomControlBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        btnPause = makeActionButton(ctx, "⏸ 暂停", false, v -> togglePause());
        Button btnRestart = makeActionButton(ctx, "🔄 重新开始", true, v -> {
            if (game.isGameActive()) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("重新开始")
                        .setMessage("确定放弃当前回合并立即重新开始吗？")
                        .setPositiveButton("确定", (d, w) -> startNewGame())
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                startNewGame();
            }
        });

        bar.addView(btnPause);
        bar.addView(btnRestart);
        return bar;
    }

    private Button makeActionButton(Context ctx, String text, boolean primary, View.OnClickListener l) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(14f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? colorAccent : (isDarkTheme(ctx) ? 0xFF37474F : 0xFF546E7A));
        bg.setCornerRadius(20 * dp);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, (int) (44 * dp), 1f);
        lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge == null) return;
        tvDifficultyBadge.setText(WhackGame.DIFFICULTY_NAMES[game.getDifficulty()] + " ▾");
    }

    private void startNewGame() {
        handler.removeCallbacksAndMessages(null);
        paused = false;
        if (btnPause != null) btnPause.setText("⏸ 暂停");
        game.reset();

        for (int i = 0; i < moleButtons.length; i++) {
            moleButtons[i].setText("🕳");
            setHoleBackground(moleButtons[i], COLOR_HOLE);
            moleButtons[i].setEnabled(true);
        }

        updateAllHud();
        startGameLoop();
    }

    private void updateAllHud() {
        updateHudText(tvTimerHud, "⏱ 倒计时", game.getTimeRemaining() + "s", colorAccent);
        updateHudText(tvScoreHud, "⭐ 得分", String.valueOf(game.getScore()), colorTextPrimary);
        updateHudText(tvComboHud, "🔥 连击", game.getConsecutiveHits() + " COMBO", 0xFFFF7043);
        updateHudText(tvBestHud, "🏆 纪录", String.valueOf(bestScore), colorTextSecondary);
    }

    private void startGameLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!game.isGameActive() || paused) return;
                game.onTickSecond();
                updateHudText(tvTimerHud, "⏱ 倒计时", game.getTimeRemaining() + "s", colorAccent);
                if (game.getTimeRemaining() <= 0) {
                    onGameEnd();
                    return;
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);

        showNextMole();
    }

    private void showNextMole() {
        if (!game.isGameActive() || paused) return;
        if (game.getCurrentMolePos() >= 0) {
            moleButtons[game.getCurrentMolePos()].setText("🕳");
            setHoleBackground(moleButtons[game.getCurrentMolePos()], COLOR_HOLE);
        }

        int pos = game.nextMolePos();
        final int finalPos = pos;
        moleButtons[pos].setText("🐹");
        setHoleBackground(moleButtons[pos], COLOR_MOLE);

        handler.postDelayed(() -> {
            if (game.isMoleVisible() && game.getCurrentMolePos() == finalPos) {
                game.missMole();
                moleButtons[finalPos].setText("🕳");
                setHoleBackground(moleButtons[finalPos], COLOR_HOLE);
                updateAllHud();
                showNextMole();
            }
        }, game.getMoleIntervalMs());
    }

    private void onMoleClick(int index) {
        if (!game.isGameActive() || paused) return;
        if (index == game.getCurrentMolePos() && game.isMoleVisible()) {
            game.hitMole();
            moleButtons[index].setText("💥");
            setHoleBackground(moleButtons[index], COLOR_HIT);
            updateAllHud();

            handler.postDelayed(() -> {
                if (game.isGameActive() && !paused) {
                    moleButtons[index].setText("🕳");
                    setHoleBackground(moleButtons[index], COLOR_HOLE);
                    showNextMole();
                }
            }, 250);
        } else {
            game.consecutiveHitsReset();
            updateAllHud();
        }
    }

    private void setHoleBackground(Button btn, int color) {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        int cellSize = (int) (80 * dp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(cellSize / 2f);
        btn.setBackground(bg);
    }

    private void togglePause() {
        if (!game.isGameActive()) return;
        paused = !paused;
        if (paused) {
            handler.removeCallbacksAndMessages(null);
            btnPause.setText("▶ 继续");
        } else {
            btnPause.setText("⏸ 暂停");
            startGameLoop();
        }
    }

    private void onGameEnd() {
        game.endGame();
        handler.removeCallbacksAndMessages(null);

        if (game.getCurrentMolePos() >= 0) {
            moleButtons[game.getCurrentMolePos()].setText("🕳");
            setHoleBackground(moleButtons[game.getCurrentMolePos()], COLOR_HOLE);
        }

        int score = game.getScore();
        if (score > bestScore) {
            bestScore = score;
            recordHighScore(score);
        }
        updateAllHud();

        usageStore.recordWin(GAME_ID);
        usageStore.recordScore(GAME_ID, score);

        showSettlementDialog(score);
    }

    private void showSettlementDialog(int score) {
        if (!isAdded() || getActivity() == null) return;
        int hits = game.getHitCount();
        int misses = game.getMissCount();
        int maxCombo = game.getMaxCombo();
        int hitRate = game.getHitRate();

        String rating;
        if (score >= 300) {
            rating = "🏆 神速捕鼠达人！";
        } else if (score >= 150) {
            rating = "⭐ 反应敏捷，战绩出众！";
        } else {
            rating = "💪 再接再厉，手速狂飙！";
        }

        String msg = rating + "\n\n"
                + "最终得分：" + score + "\n"
                + "击中地鼠：" + hits + " 只\n"
                + "挥击失误：" + misses + " 次\n"
                + "命中效率：" + hitRate + "%\n"
                + "最高连击：" + maxCombo + " 连击\n"
                + "历史最高：" + bestScore + " 分";

        new AlertDialog.Builder(requireContext())
                .setTitle("游戏结算")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("再玩一局", (d, w) -> startNewGame())
                .setNegativeButton("返回大厅", (d, w) -> {
                    if (getActivity() != null) getActivity().finish();
                })
                .show();
    }

    private void showDifficultyPicker() {
        int current = game.getDifficulty();
        new AlertDialog.Builder(requireContext())
                .setTitle("选择游戏难度")
                .setSingleChoiceItems(WhackGame.DIFFICULTY_NAMES, current, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == game.getDifficulty()) return;
                    game.setDifficulty(which);
                    updateDifficultyBadgeText();
                    ModuleScopedPreferences.get(requireContext(), GAME_ID, PREF_NAME)
                            .edit().putInt(KEY_DIFFICULTY, which).apply();
                    startNewGame();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRulesDialog() {
        String msg = "1. 限时挑战：游戏时间为 30 秒，时间内地鼠会随机从洞口探出。\n\n"
                + "2. 得分与连击：每次击中地鼠获得基础分与递增连击奖励（10 + 连击数）。失误点击空洞将重置连击！\n\n"
                + "3. 难度加速：随着游戏进行，地鼠停留时间每 10 秒加速一次，考验你的极致极限反应！";
        new AlertDialog.Builder(requireContext())
                .setTitle("打地鼠游戏规则")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        final String[] items = {
                "重新开局",
                "音效与震动 (请在宿主设置中调节)"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startNewGame();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleBackPress() {
        if (game.isGameActive()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("当前对局正在进行中，退出将结算当前战绩。确定退出吗？")
                    .setPositiveButton("退出", (d, w) -> {
                        if (getActivity() != null) getActivity().finish();
                    })
                    .setNegativeButton("继续", null)
                    .show();
        } else {
            if (getActivity() != null) getActivity().finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paused && game.isGameActive()) {
            paused = false;
            startGameLoop();
        } else {
            paused = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private int loadHighScore() {
        try {
            String json = saveManager.loadProgress(GAME_ID);
            if (json != null) {
                return new JSONObject(json).optInt("highScore", 0);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void recordHighScore(int score) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("highScore", score);
            saveManager.saveProgress(GAME_ID, obj.toString());
        } catch (Exception ignored) {}
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}

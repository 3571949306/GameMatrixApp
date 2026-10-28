package com.gamecenter.app.snake;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.SaveManager;
import com.gamecenter.app.games.GameUsageStore;

import org.json.JSONObject;

/**
 * 贪吃蛇模块 Fragment（五星级即开即玩现代化改造版本）。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩：启动秒入战场，无阻断式冷启动菜单。</li>
 *   <li>标准顶部导航栏：防误触退出确认、居中标题、难度下拉胶囊、规则弹窗、快捷设置。</li>
 *   <li>多档难度系统：休闲慢速、标准挑战、极速狂飙，支持即时无缝切换与持久化。</li>
 *   <li>战况 HUD 看板：当前得分、吞食苹果数、最高分纪录与速度档位。</li>
 *   <li>双模操控：全屏滑动手势 + 底部微操方向快捷键。</li>
 *   <li>沉浸式对局结算：战绩明细与再来一局无缝衔接。</li>
 * </ul>
 * </p>
 */
public class SnakeModuleFragment extends Fragment {

    private static final String GAME_ID = "snake";
    private static final String PREF_NAME = "snake_prefs";
    private static final String KEY_DIFFICULTY = "last_difficulty";

    private SnakeView snakeView;
    private TextView tvDifficultyBadge;
    private TextView tvScoreHud;
    private TextView tvApplesHud;
    private TextView tvBestHud;
    private TextView tvSpeedHud;
    private Button btnPause;

    private SaveManager saveManager;
    private GameUsageStore usageStore;
    private boolean isPaused = false;
    private int bestScore = 0;
    private int currentDifficulty = SnakeGame.DIFFICULTY_NORMAL;

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

        currentDifficulty = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DIFFICULTY, SnakeGame.DIFFICULTY_NORMAL);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (12 * dp));

        // 1. 标准统一现代化顶部导航栏
        root.addView(createTopBar(ctx, dp));

        // 2. 战况 HUD 看板
        root.addView(createHudDashboard(ctx, dp));

        // 3. 游戏核心视图容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.setMargins(0, (int) (8 * dp), 0, (int) (8 * dp));
        gameContainer.setLayoutParams(containerLp);

        GradientDrawable boardBg = new GradientDrawable();
        boardBg.setColor(0xFF161A24);
        boardBg.setCornerRadius(16 * dp);
        gameContainer.setBackground(boardBg);
        gameContainer.setClipToOutline(true);

        snakeView = new SnakeView(ctx);
        snakeView.getGame().setDifficulty(currentDifficulty);
        snakeView.setSpeedFactor(snakeView.getGame().getSpeedFactor());
        gameContainer.addView(snakeView);
        root.addView(gameContainer);

        // 4. 底部微操与控制栏
        root.addView(createBottomControls(ctx, dp));

        return root;
    }

    private void resolveThemeColors(Context ctx) {
        boolean dark = isDarkTheme(ctx);
        colorBg = dark ? 0xFF121418 : 0xFFF5F7FA;
        colorTextPrimary = dark ? 0xFFEEEEEE : 0xFF212529;
        colorTextSecondary = dark ? 0xFF9E9E9E : 0xFF6C757D;
        colorAccent = dark ? 0xFF66BB6A : 0xFF2E7D32;
        colorCardBg = dark ? 0xFF1E222B : 0xFFFFFFFF;
    }

    private View createTopBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (4 * dp), 0, (int) (6 * dp));

        // 返回防误触按钮
        TextView tvBack = new TextView(ctx);
        tvBack.setText("❮");
        tvBack.setTextSize(20f);
        tvBack.setTextColor(colorTextPrimary);
        tvBack.setPadding((int) (8 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        tvBack.setOnClickListener(v -> handleBackPress());
        bar.addView(tvBack);

        // 游戏标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("贪吃蛇");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        bar.addView(tvTitle);

        // 难度下拉胶囊
        tvDifficultyBadge = new TextView(ctx);
        updateDifficultyBadgeText();
        tvDifficultyBadge.setTextSize(12f);
        tvDifficultyBadge.setTextColor(colorAccent);
        tvDifficultyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvDifficultyBadge.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(isDarkTheme(ctx) ? 0x2266BB6A : 0x1A2E7D32);
        badgeBg.setCornerRadius(14 * dp);
        badgeBg.setStroke((int) (1 * dp), colorAccent);
        tvDifficultyBadge.setBackground(badgeBg);

        LinearLayout.LayoutParams diffLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffLp.setMargins((int) (10 * dp), 0, 0, 0);
        tvDifficultyBadge.setLayoutParams(diffLp);
        tvDifficultyBadge.setOnClickListener(v -> showDifficultyDialog());
        bar.addView(tvDifficultyBadge);

        // 弹性占位
        View spacer = new View(ctx);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // 规则说明按钮
        TextView tvRules = new TextView(ctx);
        tvRules.setText("ℹ");
        tvRules.setTextSize(18f);
        tvRules.setTextColor(colorTextSecondary);
        tvRules.setPadding((int) (8 * dp), (int) (6 * dp), (int) (8 * dp), (int) (6 * dp));
        tvRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(tvRules);

        // 设置 / 重开按钮
        TextView tvSettings = new TextView(ctx);
        tvSettings.setText("⚙");
        tvSettings.setTextSize(18f);
        tvSettings.setTextColor(colorTextSecondary);
        tvSettings.setPadding((int) (8 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
        tvSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(tvSettings);

        return bar;
    }

    private View createHudDashboard(Context ctx, float dp) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        hud.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorCardBg);
        bg.setCornerRadius(12 * dp);
        hud.setBackground(bg);

        LinearLayout colScore = createHudColumn(ctx, "🎯 当前得分", "0", colorAccent);
        tvScoreHud = (TextView) colScore.getChildAt(1);

        LinearLayout colApples = createHudColumn(ctx, "🍎 吞食苹果", "0", colorTextPrimary);
        tvApplesHud = (TextView) colApples.getChildAt(1);

        LinearLayout colBest = createHudColumn(ctx, "👑 历史纪录", String.valueOf(bestScore), 0xFFFFA000);
        tvBestHud = (TextView) colBest.getChildAt(1);

        LinearLayout colSpeed = createHudColumn(ctx, "⚡ 档位速度", SnakeGame.DIFFICULTY_NAMES[currentDifficulty], colorTextSecondary);
        tvSpeedHud = (TextView) colSpeed.getChildAt(1);

        hud.addView(colScore, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hud.addView(colApples, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hud.addView(colBest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hud.addView(colSpeed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return hud;
    }

    private LinearLayout createHudColumn(Context ctx, String label, String value, int valColor) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setTextColor(colorTextSecondary);
        col.addView(tvLabel);

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextSize(15f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setTextColor(valColor);
        col.addView(tvVal);

        return col;
    }

    private View createBottomControls(Context ctx, float dp) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        // 1. D-Pad 微操方向盘
        LinearLayout dpad = new LinearLayout(ctx);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);
        dpad.setPadding(0, 0, 0, (int) (6 * dp));

        Button btnUp = createDirectionButton(ctx, dp, "▲", SnakeGame.DIR_UP);
        dpad.addView(btnUp);

        LinearLayout dpadMiddle = new LinearLayout(ctx);
        dpadMiddle.setOrientation(LinearLayout.HORIZONTAL);
        dpadMiddle.setGravity(Gravity.CENTER);

        Button btnLeft = createDirectionButton(ctx, dp, "◀", SnakeGame.DIR_LEFT);
        View dpadCenter = new View(ctx);
        dpadCenter.setLayoutParams(new LinearLayout.LayoutParams((int) (48 * dp), (int) (38 * dp)));
        Button btnRight = createDirectionButton(ctx, dp, "▶", SnakeGame.DIR_RIGHT);

        dpadMiddle.addView(btnLeft);
        dpadMiddle.addView(dpadCenter);
        dpadMiddle.addView(btnRight);
        dpad.addView(dpadMiddle);

        Button btnDown = createDirectionButton(ctx, dp, "▼", SnakeGame.DIR_DOWN);
        dpad.addView(btnDown);

        container.addView(dpad);

        // 2. 状态操作栏（暂停/重开）
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        btnPause = new Button(ctx);
        btnPause.setText("⏸ 暂停");
        styleActionButton(btnPause, dp, colorCardBg, colorTextPrimary);
        btnPause.setOnClickListener(v -> togglePause());

        Button btnRestart = new Button(ctx);
        btnRestart.setText("🔄 重新开始");
        styleActionButton(btnRestart, dp, colorAccent, Color.WHITE);
        btnRestart.setOnClickListener(v -> restartGame());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, (int) (42 * dp), 1f);
        btnLp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
        actions.addView(btnPause, btnLp);
        actions.addView(btnRestart, btnLp);

        container.addView(actions);
        return container;
    }

    private Button createDirectionButton(Context ctx, float dp, String text, int dir) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(14f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(colorTextPrimary);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorCardBg);
        bg.setCornerRadius(10 * dp);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (52 * dp), (int) (38 * dp));
        lp.setMargins((int) (4 * dp), (int) (2 * dp), (int) (4 * dp), (int) (2 * dp));
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            if (snakeView != null && snakeView.getGame().isRunning()) {
                snakeView.getGame().setNextDirection(dir);
            }
        });
        return btn;
    }

    private void styleActionButton(Button btn, float dp, int bgCol, int textCol) {
        btn.setTextSize(14f);
        btn.setTextColor(textCol);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgCol);
        bg.setCornerRadius(20 * dp);
        btn.setBackground(bg);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        snakeView.setOnScoreChangeListener(score -> {
            if (tvScoreHud != null) tvScoreHud.setText(String.valueOf(score));
            if (tvApplesHud != null) tvApplesHud.setText(String.valueOf(snakeView.getGame().getApplesEaten()));
            if (score > bestScore) {
                bestScore = score;
                saveHighScore(bestScore);
                if (tvBestHud != null) tvBestHud.setText(String.valueOf(bestScore));
            }
        });

        snakeView.setOnGameOverListener(finalScore -> {
            usageStore.recordScore(GAME_ID, Math.max(bestScore, finalScore));
            showGameOverDialog(finalScore);
        });

        startNewGame();
    }

    private void startNewGame() {
        if (snakeView != null) {
            isPaused = false;
            if (btnPause != null) btnPause.setText("⏸ 暂停");
            snakeView.getGame().setDifficulty(currentDifficulty);
            snakeView.setSpeedFactor(snakeView.getGame().getSpeedFactor());
            snakeView.startGame();
            if (tvScoreHud != null) tvScoreHud.setText("0");
            if (tvApplesHud != null) tvApplesHud.setText("0");
            if (tvSpeedHud != null) tvSpeedHud.setText(SnakeGame.DIFFICULTY_NAMES[currentDifficulty]);
        }
    }

    private void togglePause() {
        if (snakeView == null || snakeView.getGame().isGameOver()) return;
        if (isPaused) {
            snakeView.resumeGame();
            isPaused = false;
            btnPause.setText("⏸ 暂停");
        } else {
            snakeView.pauseGame();
            isPaused = true;
            btnPause.setText("▶ 继续");
        }
    }

    private void restartGame() {
        startNewGame();
    }

    private void showDifficultyDialog() {
        Context ctx = requireContext();
        new AlertDialog.Builder(ctx)
                .setTitle("选择对弈难度")
                .setSingleChoiceItems(SnakeGame.DIFFICULTY_NAMES, currentDifficulty, (dialog, which) -> {
                    currentDifficulty = which;
                    ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_DIFFICULTY, currentDifficulty)
                            .apply();
                    updateDifficultyBadgeText();
                    restartGame();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge != null) {
            tvDifficultyBadge.setText(SnakeGame.DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        }
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("贪吃蛇游戏规则")
                .setMessage("【操作方式】\n" +
                        "• 方式一：在中间战场区域上下左右滑动手势转向\n" +
                        "• 方式二：点击底部的方向按键（▲ ◀ ▼ ▶）精准微操转向\n\n" +
                        "【对弈规则】\n" +
                        "• 控制小蛇吞食出现的红苹果，每吃一个得 10 分并增长一节身体\n" +
                        "• 撞击四周墙壁或咬到自己的身体则游戏结束\n" +
                        "• 随所选难度不同，移动速度将有明显差异，极速档位考验极限反应！")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(new String[]{"切换难度", "重新开始本局", "重置历史最高分"}, (dialog, which) -> {
                    if (which == 0) {
                        showDifficultyDialog();
                    } else if (which == 1) {
                        restartGame();
                    } else if (which == 2) {
                        bestScore = 0;
                        saveHighScore(0);
                        if (tvBestHud != null) tvBestHud.setText("0");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showGameOverDialog(int finalScore) {
        if (!isAdded()) return;
        boolean isNewRecord = finalScore >= bestScore && finalScore > 0;
        String title = isNewRecord ? "🎉 创造新纪录！" : "战局结算";
        String msg = "最终得分: " + finalScore + "\n" +
                "吞食苹果: " + (snakeView != null ? snakeView.getGame().getApplesEaten() : 0) + " 个\n" +
                "历史最高: " + bestScore + "\n" +
                "当前档位: " + SnakeGame.DIFFICULTY_NAMES[currentDifficulty];

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("再来一局", (dialog, which) -> restartGame())
                .setNegativeButton("返回大厅", (dialog, which) -> {
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                })
                .show();
    }

    private void handleBackPress() {
        if (snakeView != null && snakeView.getGame().isRunning() && !snakeView.getGame().isGameOver()) {
            snakeView.pauseGame();
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("确定返回大厅？当前对局进度将被保留。")
                    .setPositiveButton("确定退出", (dialog, which) -> {
                        if (getActivity() != null) getActivity().onBackPressed();
                    })
                    .setNegativeButton("继续对局", (dialog, which) -> {
                        if (snakeView != null && !isPaused) snakeView.resumeGame();
                    })
                    .show();
        } else {
            if (getActivity() != null) getActivity().onBackPressed();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (snakeView != null) snakeView.pauseGame();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (snakeView != null && !isPaused && !snakeView.getGame().isGameOver()) {
            snakeView.resumeGame();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (snakeView != null) snakeView.stopGame();
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int loadHighScore() {
        String json = saveManager.loadProgress(GAME_ID);
        if (json != null) {
            try {
                return new JSONObject(json).optInt("highScore", 0);
            } catch (Exception ignored) {
            }
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
}

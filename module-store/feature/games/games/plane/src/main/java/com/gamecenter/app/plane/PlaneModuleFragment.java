package com.gamecenter.app.plane;

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
 * 飞机大战游戏 Fragment（五星级即开即玩现代化升级版本）。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场：首屏秒入空战，开箱即飞。</li>
 *   <li>标准统一导航栏：防误触退出确认、居中标题、难度下拉胶囊、规则说明、快捷设置。</li>
 *   <li>三级动态难度：巡航休闲（舒缓敌机）、普通挑战（经典波次）、极限狂潮（密集空袭）。</li>
 *   <li>战况 HUD 看板：当前积分、波次、战机生命值与历史纪录。</li>
 *   <li>底部圆角控制栏：重新开机与暂停继续快捷键。</li>
 *   <li>沉浸式对局结算：终局弹窗与自动持久化。</li>
 * </ul>
 * </p>
 */
public class PlaneModuleFragment extends Fragment {

    private static final String GAME_ID = "plane";
    private static final String PREF_NAME = "plane_prefs";
    private static final String KEY_DIFFICULTY = "last_difficulty";
    private static final long FRAME_INTERVAL_MS = 16;

    public static final String[] DIFFICULTY_NAMES = {"巡航休闲", "普通挑战", "极限狂潮"};
    public static final float[] DIFFICULTY_FACTORS = {0.3f, 0.5f, 0.8f};

    private PlaneView planeView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int highScore = 0;
    private int totalGames = 0;
    private int currentDifficultyIndex = 1;
    private float difficultyFactor = 0.5f;
    private boolean isPaused = false;

    private TextView tvDifficultyBadge;
    private TextView tvScoreHud;
    private TextView tvWaveHud;
    private TextView tvLivesHud;
    private TextView tvBestHud;
    private Button btnRestart;
    private Button btnPause;

    private SaveManager saveManager;
    private GameUsageStore usageStore;

    private int colorBg;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorCardBg;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (planeView != null && planeView.isGameRunning() && !isPaused) {
                planeView.update();
                updateHudValues();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        resolveThemeColors(ctx);

        saveManager = SaveManager.getInstance(ctx);
        usageStore = new GameUsageStore(ctx);
        usageStore.recordLaunch(GAME_ID);
        highScore = loadHighScore();

        currentDifficultyIndex = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DIFFICULTY, 1);
        if (currentDifficultyIndex < 0 || currentDifficultyIndex >= DIFFICULTY_FACTORS.length) {
            currentDifficultyIndex = 1;
        }
        difficultyFactor = DIFFICULTY_FACTORS[currentDifficultyIndex];

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (12 * dp));

        // 1. 标准统一现代化顶部导航栏
        root.addView(createTopBar(ctx, dp));

        // 2. 战况 HUD 看板
        root.addView(createHudDashboard(ctx, dp));

        // 3. 游戏画布容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = (int) (4 * dp);
        containerLp.bottomMargin = (int) (6 * dp);
        gameContainer.setLayoutParams(containerLp);

        GradientDrawable boardBg = new GradientDrawable();
        boardBg.setColor(0xFF0F1420);
        boardBg.setCornerRadius(16 * dp);
        gameContainer.setBackground(boardBg);
        gameContainer.setClipToOutline(true);

        planeView = new PlaneView(ctx);
        planeView.setDifficultyFactor(difficultyFactor);
        planeView.setOnGameListener(new PlaneView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                if (score > highScore) {
                    highScore = score;
                    saveHighScore(highScore);
                }
                updateHudValues();
            }

            @Override
            public void onGameOver(int score) {
                handler.removeCallbacks(gameLoop);
                totalGames++;
                boolean isNewRecord = score > highScore;
                if (isNewRecord) {
                    highScore = score;
                    saveHighScore(highScore);
                }
                if (usageStore != null) {
                    usageStore.recordScore(GAME_ID, highScore);
                    usageStore.recordLoss(GAME_ID);
                }
                updateHudValues();
                showGameOverDialog(score, isNewRecord);
            }
        });
        gameContainer.addView(planeView);
        root.addView(gameContainer);

        // 4. 底部控制栏
        root.addView(createBottomControlBar(ctx, dp));

        return root;
    }

    private void resolveThemeColors(Context ctx) {
        boolean dark = isNightMode();
        colorBg = dark ? 0xFF101216 : 0xFFF2F5F8;
        colorTextPrimary = dark ? 0xFFEEEEEE : 0xFF212529;
        colorTextSecondary = dark ? 0xFF9E9E9E : 0xFF6C757D;
        colorAccent = dark ? 0xFFEF5350 : 0xFFD32F2F;
        colorCardBg = dark ? 0xFF1A1E26 : 0xFFFFFFFF;
    }

    private View createTopBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (4 * dp), 0, (int) (6 * dp));

        // ❮ 返回防误触
        TextView tvBack = new TextView(ctx);
        tvBack.setText("❮");
        tvBack.setTextSize(20f);
        tvBack.setTextColor(colorTextPrimary);
        tvBack.setPadding((int) (8 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
        tvBack.setOnClickListener(v -> handleBackPress());
        bar.addView(tvBack);

        // 标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("飞机大战");
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
        badgeBg.setColor(isNightMode() ? 0x22EF5350 : 0x1AD32F2F);
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

        LinearLayout colScore = createHudColumn(ctx, "🎯 击坠积分", "0", colorAccent);
        tvScoreHud = (TextView) colScore.getChildAt(1);

        LinearLayout colWave = createHudColumn(ctx, "🌊 敌机波次", "第 1 波", colorTextPrimary);
        tvWaveHud = (TextView) colWave.getChildAt(1);

        LinearLayout colLives = createHudColumn(ctx, "❤️ 战机生命", "❤❤❤", 0xFFFF5252);
        tvLivesHud = (TextView) colLives.getChildAt(1);

        LinearLayout colBest = createHudColumn(ctx, "👑 历史纪录", String.valueOf(highScore), 0xFFFFA000);
        tvBestHud = (TextView) colBest.getChildAt(1);

        hud.addView(colScore, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hud.addView(colWave, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hud.addView(colLives, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hud.addView(colBest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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
        tvVal.setTextSize(14f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setTextColor(valColor);
        col.addView(tvVal);

        return col;
    }

    private View createBottomControlBar(Context ctx, float dp) {
        LinearLayout bottomBar = new LinearLayout(ctx);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = (int) (4 * dp);
        bottomBar.setLayoutParams(bottomLp);

        btnRestart = new Button(ctx);
        btnRestart.setText("🔄 重新起飞");
        btnRestart.setMinHeight((int) Math.ceil(46 * dp));
        styleActionButton(btnRestart, dp, colorAccent, Color.WHITE);
        btnRestart.setOnClickListener(v -> startFlight(1));

        btnPause = new Button(ctx);
        btnPause.setText("⏸ 暂停");
        btnPause.setMinHeight((int) Math.ceil(46 * dp));
        styleActionButton(btnPause, dp, colorCardBg, colorTextPrimary);
        btnPause.setOnClickListener(v -> togglePause());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, (int) (46 * dp), 1f);
        btnLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        bottomBar.addView(btnRestart, btnLp);
        bottomBar.addView(btnPause, btnLp);

        return bottomBar;
    }

    private void styleActionButton(Button btn, float dp, int bgCol, int textCol) {
        btn.setTextSize(14f);
        btn.setTextColor(textCol);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgCol);
        bg.setCornerRadius(14 * dp);
        btn.setBackground(bg);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startFlight(1);
    }

    private void startFlight(int wave) {
        if (planeView == null) return;
        isPaused = false;
        if (btnPause != null) btnPause.setText("⏸ 暂停");
        planeView.setDifficultyFactor(difficultyFactor);
        planeView.startGame(wave);
        updateHudValues();
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    private void updateHudValues() {
        if (planeView == null) return;
        if (tvScoreHud != null) tvScoreHud.setText(String.valueOf(planeView.getScore()));
        if (tvWaveHud != null) tvWaveHud.setText("第 " + planeView.getWave() + " 波");
        if (tvLivesHud != null) {
            int lives = planeView.getLives();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lives; i++) sb.append("❤");
            tvLivesHud.setText(sb.length() == 0 ? "已坠毁" : sb.toString());
        }
        if (tvBestHud != null) tvBestHud.setText(String.valueOf(highScore));
    }

    private void togglePause() {
        if (planeView == null || !planeView.isGameRunning()) return;
        if (isPaused) {
            isPaused = false;
            btnPause.setText("⏸ 暂停");
            planeView.resumeGame();
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        } else {
            isPaused = true;
            btnPause.setText("▶ 继续");
            planeView.pauseGame();
            handler.removeCallbacks(gameLoop);
        }
    }

    private void showDifficultyDialog() {
        Context ctx = requireContext();
        new AlertDialog.Builder(ctx)
                .setTitle("选择空战难度")
                .setSingleChoiceItems(DIFFICULTY_NAMES, currentDifficultyIndex, (dialog, which) -> {
                    currentDifficultyIndex = which;
                    difficultyFactor = DIFFICULTY_FACTORS[which];
                    ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_DIFFICULTY, currentDifficultyIndex)
                            .apply();
                    updateDifficultyBadgeText();
                    startFlight(1);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge != null) {
            tvDifficultyBadge.setText(DIFFICULTY_NAMES[currentDifficultyIndex] + " ▾");
        }
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("飞机大战游戏规则")
                .setMessage("【操作方式】\n" +
                        "• 手指在屏幕上左右拖拽控制战机位移，战机会自动连续射击\n" +
                        "• 击毁迎面飞来的敌机获取积分，每 500 分自动晋级下一波次\n\n" +
                        "【生命与生存】\n" +
                        "• 战机初始拥有 3 点生命（❤❤❤）\n" +
                        "• 敌机若突破防线飞出底端或撞击自机，扣除 1 点生命\n" +
                        "• 生命耗尽则战机坠毁，对局结束\n\n" +
                        "【难度档位】\n" +
                        "• 巡航休闲：敌机速度缓和，生成间隔充裕，适合新手巡航\n" +
                        "• 普通挑战：标准空战波次，敌机编队齐整，攻防节奏适中\n" +
                        "• 极限狂潮：敌机高速突袭，弹幕密集，考验极限微操！")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(new String[]{"切换难度", "重新起飞", "重置历史最高分"}, (dialog, which) -> {
                    if (which == 0) {
                        showDifficultyDialog();
                    } else if (which == 1) {
                        startFlight(1);
                    } else if (which == 2) {
                        highScore = 0;
                        saveHighScore(0);
                        updateHudValues();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showGameOverDialog(int finalScore, boolean isNewRecord) {
        if (!isAdded()) return;
        String title = isNewRecord && finalScore > 0 ? "🎉 创造新纪录！" : "空战结算";
        String msg = "本次击坠得分: " + finalScore + "\n" +
                "突破最高波次: 第 " + (planeView != null ? planeView.getWave() : 1) + " 波\n" +
                "历史最佳纪录: " + highScore + "\n" +
                "当前档位: " + DIFFICULTY_NAMES[currentDifficultyIndex] + "\n" +
                "累计出击场次: " + totalGames;

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("重装起飞", (dialog, which) -> startFlight(1))
                .setNegativeButton("返回大厅", (dialog, which) -> {
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .show();
    }

    private void handleBackPress() {
        if (planeView != null && planeView.isGameRunning()) {
            togglePause();
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("确定返航退出？当前空战进度将被记录。")
                    .setPositiveButton("确定退出", (dialog, which) -> {
                        if (getActivity() != null) getActivity().onBackPressed();
                    })
                    .setNegativeButton("继续战斗", (dialog, which) -> {
                        if (isPaused) togglePause();
                    })
                    .show();
        } else {
            if (getActivity() != null) getActivity().onBackPressed();
        }
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
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

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(gameLoop);
        if (planeView != null) {
            planeView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (planeView != null && planeView.isGameRunning() && !isPaused) {
            planeView.resumeGame();
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (planeView != null) {
            planeView.stopGame();
        }
    }
}

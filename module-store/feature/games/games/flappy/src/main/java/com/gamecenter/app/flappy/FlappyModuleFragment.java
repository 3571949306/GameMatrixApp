package com.gamecenter.app.flappy;

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

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;

/**
 * 飞翔的小鸟游戏 Fragment（五星级即开即玩现代化升级版本）。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场：首屏秒入，开箱即飞。</li>
 *   <li>标准统一导航栏：防误触退出确认、居中标题、难度下拉胶囊、规则说明、快捷设置。</li>
 *   <li>三级动态难度：休闲飞翔（宽管慢速）、经典挑战（标准原版）、极速地狱（窄隙狂飙）。</li>
 *   <li>战况 HUD 看板：当前飞行得分、历史最高分纪录与难度标识。</li>
 *   <li>底部圆角控制栏：重新开始与暂停继续快捷键。</li>
 *   <li>沉浸式对局结算：终局弹窗与自动持久化。</li>
 * </ul>
 * </p>
 */
public class FlappyModuleFragment extends Fragment {

    private static final String GAME_ID = "flappy";
    private static final String PREF_NAME = "flappy_prefs";
    private static final String KEY_DIFFICULTY = "last_difficulty";
    private static final long FRAME_INTERVAL_MS = 16;

    public static final String[] DIFFICULTY_NAMES = {"休闲飞翔", "经典挑战", "极速地狱"};
    public static final float[] DIFFICULTY_FACTORS = {0.3f, 0.5f, 0.8f};

    private FlappyView flappyView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int bestScore = 0;
    private int totalGames = 0;
    private int currentDifficultyIndex = 1;
    private float difficultyFactor = 0.5f;
    private boolean isPaused = false;

    private TextView tvDifficultyBadge;
    private TextView tvScore;
    private TextView tvBest;
    private TextView tvDiffTag;
    private Button btnRestart;
    private Button btnPause;
    private GameUsageStore usageStore;

    private int colorBg;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorCardBg;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (flappyView != null && flappyView.isGameRunning() && !isPaused) {
                flappyView.update();
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

        // Child 0: 标准统一现代化顶部导航栏
        root.addView(createTopBar(ctx, dp));

        // Child 1: 分数与战况 HUD 栏（按测试契约必须为 root.getChildAt(1)，且 child 0 为 tvScore）
        LinearLayout scoreBar = new LinearLayout(ctx);
        scoreBar.setOrientation(LinearLayout.HORIZONTAL);
        scoreBar.setGravity(Gravity.CENTER_VERTICAL);
        scoreBar.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(colorCardBg);
        hudBg.setCornerRadius(12 * dp);
        scoreBar.setBackground(hudBg);

        LinearLayout.LayoutParams scoreBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreBarLp.topMargin = (int) (4 * dp);
        scoreBarLp.bottomMargin = (int) (6 * dp);
        scoreBar.setLayoutParams(scoreBarLp);

        tvScore = new TextView(ctx);
        tvScore.setTextSize(16);
        tvScore.setTypeface(Typeface.DEFAULT_BOLD);
        tvScore.setTextColor(colorAccent);
        tvScore.setText(getString(R.string.game_score_alt_init));
        scoreBar.addView(tvScore, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        tvBest = new TextView(ctx);
        tvBest.setTextSize(15);
        tvBest.setTextColor(0xFFFFA000);
        tvBest.setText(getString(R.string.game_best_init));
        scoreBar.addView(tvBest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        tvDiffTag = new TextView(ctx);
        tvDiffTag.setTextSize(13);
        tvDiffTag.setTextColor(colorTextSecondary);
        tvDiffTag.setText("⚡ " + DIFFICULTY_NAMES[currentDifficultyIndex]);
        tvDiffTag.setGravity(Gravity.END);
        scoreBar.addView(tvDiffTag, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(scoreBar);

        // Child 2: 游戏画布容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = (int) (4 * dp);
        containerLp.bottomMargin = (int) (6 * dp);
        gameContainer.setLayoutParams(containerLp);

        GradientDrawable boardBg = new GradientDrawable();
        boardBg.setColor(0xFF1B2234);
        boardBg.setCornerRadius(16 * dp);
        gameContainer.setBackground(boardBg);
        gameContainer.setClipToOutline(true);

        flappyView = new FlappyView(ctx);
        applyDifficulty();
        flappyView.setOnGameListener(new FlappyView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText(getString(R.string.game_score_alt_format, score));
            }

            @Override
            public void onGameOver(int score) {
                handler.removeCallbacks(gameLoop);
                totalGames++;
                boolean isNewRecord = score > bestScore;
                if (isNewRecord) {
                    bestScore = score;
                }
                if (usageStore != null) {
                    usageStore.recordScore(GAME_ID, bestScore);
                    usageStore.recordLoss(GAME_ID);
                }
                tvBest.setText(getString(R.string.game_best_format, bestScore));
                showGameOverDialog(score, isNewRecord);
            }
        });
        gameContainer.addView(flappyView);
        root.addView(gameContainer);

        // Child 3: 底部控制按键栏（保证满足包含 btnRestart 的契约）
        LinearLayout bottomBar = new LinearLayout(ctx);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = (int) (4 * dp);
        bottomBar.setLayoutParams(bottomLp);

        btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        btnRestart.setMinHeight((int) Math.ceil(46 * dp));
        styleActionButton(btnRestart, dp, colorAccent, Color.WHITE);
        btnRestart.setOnClickListener(v -> startGame());

        btnPause = new Button(ctx);
        btnPause.setText("⏸ 暂停");
        btnPause.setMinHeight((int) Math.ceil(46 * dp));
        styleActionButton(btnPause, dp, colorCardBg, colorTextPrimary);
        btnPause.setOnClickListener(v -> togglePause());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, (int) (46 * dp), 1f);
        btnLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        bottomBar.addView(btnRestart, btnLp);
        bottomBar.addView(btnPause, btnLp);

        root.addView(bottomBar);

        return root;
    }

    private void resolveThemeColors(Context ctx) {
        boolean dark = isNightMode();
        colorBg = dark ? 0xFF121418 : 0xFFF0F4F8;
        colorTextPrimary = dark ? 0xFFEEEEEE : 0xFF212529;
        colorTextSecondary = dark ? 0xFF9E9E9E : 0xFF6C757D;
        colorAccent = dark ? 0xFF42A5F5 : 0xFF1976D2;
        colorCardBg = dark ? 0xFF1E222B : 0xFFFFFFFF;
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
        tvTitle.setText(getString(R.string.game_title_flappy));
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
        badgeBg.setColor(isNightMode() ? 0x2242A5F5 : 0x1A1976D2);
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
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        bestScore = usageStore.getHighScore(GAME_ID);
        if (tvBest != null) {
            tvBest.setText(getString(R.string.game_best_format, bestScore));
        }
        startGame();
    }

    private void startGame() {
        if (flappyView == null) return;
        isPaused = false;
        if (btnPause != null) btnPause.setText("⏸ 暂停");
        flappyView.startGame();
        tvScore.setText(getString(R.string.game_score_alt_format, 0));
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    private void togglePause() {
        if (flappyView == null || !flappyView.isGameRunning()) return;
        if (isPaused) {
            isPaused = false;
            btnPause.setText("⏸ 暂停");
            flappyView.resumeGame();
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        } else {
            isPaused = true;
            btnPause.setText("▶ 继续");
            flappyView.pauseGame();
            handler.removeCallbacks(gameLoop);
        }
    }

    private void showDifficultyDialog() {
        Context ctx = requireContext();
        new AlertDialog.Builder(ctx)
                .setTitle("选择飞行难度")
                .setSingleChoiceItems(DIFFICULTY_NAMES, currentDifficultyIndex, (dialog, which) -> {
                    currentDifficultyIndex = which;
                    difficultyFactor = DIFFICULTY_FACTORS[which];
                    ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_DIFFICULTY, currentDifficultyIndex)
                            .apply();
                    updateDifficultyBadgeText();
                    if (tvDiffTag != null) {
                        tvDiffTag.setText("⚡ " + DIFFICULTY_NAMES[currentDifficultyIndex]);
                    }
                    applyDifficulty();
                    startGame();
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

    private void applyDifficulty() {
        if (flappyView == null) return;
        float baseSpeed = 3f;
        float baseGap = 180f;
        float pipeSpeed = baseSpeed * (0.5f + difficultyFactor);
        float pipeGap = baseGap * (1.5f - difficultyFactor);
        flappyView.setPipeConfig(pipeSpeed, pipeGap);
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("像素鸟游戏规则")
                .setMessage("【操作方式】\n" +
                        "• 点击屏幕任意位置即可拍打翅膀向上飞翔\n" +
                        "• 不点击时受自然重力影响下坠\n\n" +
                        "【对弈规则】\n" +
                        "• 躲避沿途出现的上下绿色管道，每成功穿过一组获得 1 分\n" +
                        "• 触碰管道边缘或跌落至地面则游戏结束\n\n" +
                        "【难度档位】\n" +
                        "• 休闲飞翔：管道间隙超宽（216px），速度舒缓，极佳上手\n" +
                        "• 经典挑战：官方标准间距（180px）与移速，节奏考究\n" +
                        "• 极速地狱：管道极速穿梭且间隙骤降（126px），极限反应！")
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
                        startGame();
                    } else if (which == 2) {
                        bestScore = 0;
                        if (usageStore != null) usageStore.recordScore(GAME_ID, 0);
                        if (tvBest != null) tvBest.setText(getString(R.string.game_best_format, 0));
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showGameOverDialog(int finalScore, boolean isNewRecord) {
        if (!isAdded()) return;
        String title = isNewRecord && finalScore > 0 ? "🎉 创造新纪录！" : "飞行结束";
        String msg = "本次飞行得分: " + finalScore + "\n" +
                "历史最佳纪录: " + bestScore + "\n" +
                "当前档位: " + DIFFICULTY_NAMES[currentDifficultyIndex] + "\n" +
                "累计飞行局数: " + totalGames;

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("再飞一次", (dialog, which) -> startGame())
                .setNegativeButton("返回大厅", (dialog, which) -> {
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .show();
    }

    private void handleBackPress() {
        if (flappyView != null && flappyView.isGameRunning()) {
            togglePause();
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("退出将结束当前飞行对局，确定返回大厅？")
                    .setPositiveButton("确定退出", (dialog, which) -> {
                        if (getActivity() != null) getActivity().onBackPressed();
                    })
                    .setNegativeButton("继续飞翔", (dialog, which) -> {
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

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(gameLoop);
        if (flappyView != null) {
            flappyView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (flappyView != null && flappyView.isGameRunning() && !isPaused) {
            flappyView.resumeGame();
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (flappyView != null) {
            flappyView.stopGame();
        }
    }
}

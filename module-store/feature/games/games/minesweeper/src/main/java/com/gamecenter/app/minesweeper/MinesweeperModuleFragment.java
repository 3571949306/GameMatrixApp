package com.gamecenter.app.minesweeper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;

import java.util.Locale;

/**
 * 扫雷 2.0 现代化即开即玩游戏界面。
 *
 * <p>特性：
 * 1. 即开即玩战场，开局秒进；
 * 2. 顶部标准导航：返回 ❮、标题、可点选难度胶囊 [简单 ▾]、规则说明 ℹ、设置 ⚙；
 * 3. 经典动态表情 HUD 看板：😊 平常、😮 触碰盘面紧张、😎 胜利墨镜、😵 踩雷眩晕，点按笑脸一键速开；
 * 4. 剩余雷数、实时计时器与胜场计数实时滚动；
 * 5. 现代化胶囊控制栏：⛏ 翻开 / 🚩 插旗模式秒切开关，告别长按痛苦；
 * 6. 完整保留满足单元测试的 Button/TextView 组件契约。</p>
 */
public class MinesweeperModuleFragment extends Fragment {

    private static final String GAME_ID = "minesweeper";

    private MinesweeperView minesweeperView;
    private MinesweeperGame game;
    private GameUsageStore usageStore;

    // 视图组件
    private TextView tvDifficultyCapsule;
    private TextView tvStatus;
    private TextView tvMines;
    private TextView tvWins;
    private TextView tvTimer;
    private TextView tvSmiley;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;
    private Button btnRestart;
    private LinearLayout btnModeToggle;
    private TextView tvModeLabel;

    // 计时器控制
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean timerRunning = false;
    private long timerStartElapsedRealtime = 0L;
    private long currentElapsedSeconds = 0L;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning || minesweeperView == null || minesweeperView.isGameOver()) return;
            long now = SystemClock.elapsedRealtime();
            currentElapsedSeconds = Math.max(0L, (now - timerStartElapsedRealtime) / 1000L);
            updateTimerDisplay();
            timerHandler.postDelayed(this, 1000L);
        }
    };

    // 状态与配色
    private boolean isNight;
    private int colorBg;
    private int colorSurface;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorAccentLight;
    private int colorActiveButton;
    private int colorInactiveButton;
    private int colorDanger;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        isNight = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        initThemeColors();

        int minTouchHeight = (int) Math.ceil(48f * dp);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (12 * dp));

        // 1. 顶部标准导航栏
        View topBar = buildTopAppBar(ctx, dp);
        root.addView(topBar);

        // 2. 现代 HUD 看板 (剩余雷数 + 经典动态笑脸 + 计时器与胜场)
        View hudCard = buildModernHud(ctx, dp);
        LinearLayout.LayoutParams hudLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hudLp.topMargin = (int) (6 * dp);
        hudLp.bottomMargin = (int) (6 * dp);
        root.addView(hudCard, hudLp);

        // 3. 难度选择胶囊行 (保留 Button 契约保证单元测试 100% 匹配)
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.bottomMargin = (int) (6 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = createDifficultyButton(ctx, getString(R.string.game_diff_easy), minTouchHeight,
                v -> setDifficulty(MinesweeperGame.DIFF_EASY));
        diffBar.addView(btnEasy, weightedParams(1f, dp));

        btnNormal = createDifficultyButton(ctx, getString(R.string.game_diff_normal), minTouchHeight,
                v -> setDifficulty(MinesweeperGame.DIFF_NORMAL));
        diffBar.addView(btnNormal, weightedParams(1f, dp));

        btnHard = createDifficultyButton(ctx, getString(R.string.game_diff_hard), minTouchHeight,
                v -> setDifficulty(MinesweeperGame.DIFF_HARD));
        diffBar.addView(btnHard, weightedParams(1f, dp));

        root.addView(diffBar);

        // 4. 扫雷棋盘容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.bottomMargin = (int) (6 * dp);
        gameContainer.setLayoutParams(containerLp);
        gameContainer.setBackground(roundBackground(colorSurface, (int) (12 * dp)));
        gameContainer.setPadding((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));

        minesweeperView = new MinesweeperView(ctx);
        minesweeperView.setOnTouchStateChangeListener(isPressed -> {
            if (minesweeperView != null && !minesweeperView.isGameOver()) {
                tvSmiley.setText(isPressed ? "😮" : "😊");
            }
        });
        minesweeperView.setOnFlagCountChangedListener(count -> updateMinesDisplay());
        minesweeperView.setOnGameWinListener(elapsedSeconds -> {
            stopTimer();
            tvSmiley.setText("😎");
            if (usageStore != null) {
                usageStore.recordWin(GAME_ID);
            }
            game.recordWin(100);
            tvStatus.setText(getString(R.string.game_mines_win_format, elapsedSeconds));
            updateWinsDisplay();
            showWinDialog(elapsedSeconds);
        });
        minesweeperView.setOnGameLoseListener(() -> {
            stopTimer();
            tvSmiley.setText("😵");
            if (usageStore != null) {
                usageStore.recordLoss(GAME_ID);
            }
            game.recordLoss();
            tvStatus.setText(getString(R.string.game_mines_lose));
            showLoseDialog();
        });
        gameContainer.addView(minesweeperView);
        root.addView(gameContainer);

        // 5. 现代化操作控制栏 (翻开/插旗模式切换 + 重新开始)
        LinearLayout controlBar = new LinearLayout(ctx);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        controlBar.setLayoutParams(controlBarLp);

        // 模式切换胶囊
        btnModeToggle = new LinearLayout(ctx);
        btnModeToggle.setOrientation(LinearLayout.HORIZONTAL);
        btnModeToggle.setGravity(Gravity.CENTER);
        btnModeToggle.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        btnModeToggle.setBackground(roundBackground(colorAccentLight, (int) (10 * dp)));
        btnModeToggle.setOnClickListener(v -> toggleFlagMode());

        tvModeLabel = new TextView(ctx);
        tvModeLabel.setText("⛏ 当前模式：翻开");
        tvModeLabel.setTextSize(13);
        tvModeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvModeLabel.setTextColor(colorAccent);
        btnModeToggle.addView(tvModeLabel);

        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(0, minTouchHeight, 1.2f);
        modeLp.rightMargin = (int) (4 * dp);
        btnModeToggle.setLayoutParams(modeLp);
        controlBar.addView(btnModeToggle);

        // 重新开始按钮 (保留生产 Button 契约)
        btnRestart = new Button(ctx);
        btnRestart.setMinimumHeight(minTouchHeight);
        btnRestart.setMinHeight(minTouchHeight);
        btnRestart.setText(getString(R.string.game_btn_restart));
        btnRestart.setTextSize(13);
        btnRestart.setTypeface(Typeface.DEFAULT_BOLD);
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setBackground(roundBackground(colorActiveButton, (int) (10 * dp)));
        btnRestart.setStateListAnimator(null);
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(0, minTouchHeight, 1f);
        restartLp.leftMargin = (int) (4 * dp);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> startGame());
        controlBar.addView(btnRestart);

        root.addView(controlBar);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();

        // 注册返回拦截
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        usageStore = new GameUsageStore(ctx);
        game = new MinesweeperGame();
        updateDifficultyButtons();
        updateWinsDisplay();
        startGame();
    }

    // ==========================================
    // UI 构建逻辑
    // ==========================================

    private View buildTopAppBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, (int) (2 * dp));

        // ❮ 返回按键
        TextView btnBack = new TextView(ctx);
        btnBack.setText("❮");
        btnBack.setTextSize(19);
        btnBack.setTextColor(colorTextPrimary);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setBackground(createCircleRipple(colorSurface, (int) (38 * dp)));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int) (38 * dp), (int) (38 * dp));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> handleBackPress());
        bar.addView(btnBack);

        // 标题与难度胶囊
        LinearLayout centerBox = new LinearLayout(ctx);
        centerBox.setOrientation(LinearLayout.HORIZONTAL);
        centerBox.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        centerBox.setLayoutParams(centerLp);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_minesweeper));
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setPadding(0, 0, (int) (8 * dp), 0);
        centerBox.addView(tvTitle);

        tvDifficultyCapsule = new TextView(ctx);
        tvDifficultyCapsule.setText("简单 ▾");
        tvDifficultyCapsule.setTextSize(12);
        tvDifficultyCapsule.setTypeface(Typeface.DEFAULT_BOLD);
        tvDifficultyCapsule.setTextColor(colorAccent);
        tvDifficultyCapsule.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        tvDifficultyCapsule.setBackground(roundBackground(colorAccentLight, (int) (12 * dp)));
        tvDifficultyCapsule.setOnClickListener(v -> showDifficultyDialog());
        centerBox.addView(tvDifficultyCapsule);

        bar.addView(centerBox);

        // 规则说明 ℹ
        TextView btnRules = new TextView(ctx);
        btnRules.setText("ℹ");
        btnRules.setTextSize(18);
        btnRules.setTextColor(colorTextPrimary);
        btnRules.setGravity(Gravity.CENTER);
        btnRules.setBackground(createCircleRipple(colorSurface, (int) (38 * dp)));
        LinearLayout.LayoutParams rulesLp = new LinearLayout.LayoutParams((int) (38 * dp), (int) (38 * dp));
        btnRules.setLayoutParams(rulesLp);
        btnRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(btnRules);

        // 设置 ⚙
        TextView btnSettings = new TextView(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(18);
        btnSettings.setTextColor(colorTextPrimary);
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setBackground(createCircleRipple(colorSurface, (int) (38 * dp)));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams((int) (38 * dp), (int) (38 * dp));
        settingsLp.leftMargin = (int) (4 * dp);
        btnSettings.setLayoutParams(settingsLp);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(btnSettings);

        return bar;
    }

    private View buildModernHud(Context ctx, float dp) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        card.setBackground(roundBackground(colorSurface, (int) (12 * dp)));

        // 上排：剩余地雷 + 经典复刻笑脸 + 计时器与胜场
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // 剩余雷数
        tvMines = new TextView(ctx);
        tvMines.setTextSize(15);
        tvMines.setTypeface(Typeface.DEFAULT_BOLD);
        tvMines.setTextColor(colorDanger);
        tvMines.setText(getString(R.string.game_mines_remaining_init));
        topRow.addView(tvMines, weightedParams(1f, dp));

        // 经典动态笑脸重开按钮
        tvSmiley = new TextView(ctx);
        tvSmiley.setText("😊");
        tvSmiley.setTextSize(26);
        tvSmiley.setGravity(Gravity.CENTER);
        tvSmiley.setBackground(createCircleRipple(colorAccentLight, (int) (44 * dp)));
        LinearLayout.LayoutParams smileyLp = new LinearLayout.LayoutParams((int) (44 * dp), (int) (44 * dp));
        tvSmiley.setLayoutParams(smileyLp);
        tvSmiley.setOnClickListener(v -> startGame());
        topRow.addView(tvSmiley);

        // 右侧计时与胜场容器
        LinearLayout rightBox = new LinearLayout(ctx);
        rightBox.setOrientation(LinearLayout.VERTICAL);
        rightBox.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        tvTimer = new TextView(ctx);
        tvTimer.setTextSize(14);
        tvTimer.setTypeface(Typeface.DEFAULT_BOLD);
        tvTimer.setTextColor(colorTextPrimary);
        tvTimer.setText("⏱ 00:00");
        rightBox.addView(tvTimer);

        tvWins = new TextView(ctx);
        tvWins.setTextSize(12);
        tvWins.setTextColor(colorTextSecondary);
        tvWins.setText(getString(R.string.game_wins_init));
        rightBox.addView(tvWins);

        topRow.addView(rightBox, weightedParams(1f, dp));

        card.addView(topRow);

        // 下排：状态提示
        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(12.5f);
        tvStatus.setTextColor(colorTextSecondary);
        tvStatus.setPadding(0, (int) (6 * dp), 0, (int) (2 * dp));
        tvStatus.setText(getString(R.string.game_mines_status_init));
        card.addView(tvStatus);

        return card;
    }

    private Button createDifficultyButton(Context ctx, String text, int minTouchHeight, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setMinimumHeight(minTouchHeight);
        b.setMinHeight(minTouchHeight);
        b.setText(text);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams weightedParams(float weight, float dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
        return lp;
    }

    // ==========================================
    // 游戏逻辑控制
    // ==========================================

    private void startGame() {
        if (minesweeperView == null) return;
        minesweeperView.setDifficulty(game.getDifficulty());
        minesweeperView.startGame();
        tvSmiley.setText("😊");
        tvStatus.setText(getString(R.string.game_mines_status_init));
        updateMinesDisplay();
        startTimer();
    }

    private void setDifficulty(int level) {
        game.setDifficulty(level);
        updateDifficultyButtons();
        updateDifficultyCapsule();
        startGame();
    }

    private void updateDifficultyCapsule() {
        if (tvDifficultyCapsule == null || game == null) return;
        tvDifficultyCapsule.setText(MinesweeperGame.getDifficultyName(game.getDifficulty()) + " ▾");
    }

    private void updateDifficultyButtons() {
        if (btnEasy == null || game == null) return;
        float dp = requireContext().getResources().getDisplayMetrics().density;
        int cur = game.getDifficulty();

        updateButtonTint(btnEasy, cur == MinesweeperGame.DIFF_EASY, dp);
        updateButtonTint(btnNormal, cur == MinesweeperGame.DIFF_NORMAL, dp);
        updateButtonTint(btnHard, cur == MinesweeperGame.DIFF_HARD, dp);
    }

    private void updateButtonTint(Button b, boolean active, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(8 * dp);
        gd.setColor(active ? colorActiveButton : colorInactiveButton);
        b.setBackground(gd);
        b.setTextColor(active ? Color.WHITE : (isNight ? 0xFFCFD8DC : 0xFF455A64));
    }

    private void toggleFlagMode() {
        if (minesweeperView == null) return;
        boolean newMode = !minesweeperView.isFlagMode();
        minesweeperView.setFlagMode(newMode);
        if (newMode) {
            tvModeLabel.setText("🚩 当前模式：插旗");
            tvModeLabel.setTextColor(Color.WHITE);
            btnModeToggle.setBackground(roundBackground(colorDanger, dp(10)));
            tvStatus.setText("已开启快速插旗模式：轻触格子直接标记/取消地雷");
        } else {
            tvModeLabel.setText("⛏ 当前模式：翻开");
            tvModeLabel.setTextColor(colorAccent);
            btnModeToggle.setBackground(roundBackground(colorAccentLight, dp(10)));
            tvStatus.setText("已开启翻开模式：轻触探雷，长按插旗");
        }
    }

    private void updateMinesDisplay() {
        if (tvMines != null && minesweeperView != null) {
            int remaining = minesweeperView.getMineCount() - minesweeperView.getFlaggedCount();
            tvMines.setText(getString(R.string.game_mines_remaining_format, remaining));
        }
    }

    private void updateWinsDisplay() {
        if (tvWins != null && game != null) {
            tvWins.setText(getString(R.string.game_wins_format, game.getWins()));
        }
    }

    private void startTimer() {
        stopTimer();
        timerRunning = true;
        timerStartElapsedRealtime = SystemClock.elapsedRealtime();
        currentElapsedSeconds = 0L;
        updateTimerDisplay();
        timerHandler.postDelayed(timerRunnable, 1000L);
    }

    private void stopTimer() {
        timerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateTimerDisplay() {
        if (tvTimer == null) return;
        long min = currentElapsedSeconds / 60L;
        long sec = currentElapsedSeconds % 60L;
        tvTimer.setText(String.format(Locale.getDefault(), "⏱ %02d:%02d", min, sec));
    }

    // ==========================================
    // 弹窗与返回控制
    // ==========================================

    private void showWinDialog(long elapsedSeconds) {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("🎉 扫雷大捷！")
                .setMessage("用时: " + String.format(Locale.getDefault(), "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60) + "\n" +
                        "难度: " + MinesweeperGame.getDifficultyName(game.getDifficulty()) + "\n" +
                        "累计胜场: " + game.getWins() + " 胜")
                .setCancelable(false)
                .setPositiveButton("再来一局", (d, w) -> startGame())
                .setNegativeButton("返回大厅", (d, w) -> {
                    if (getActivity() != null) getActivity().finish();
                })
                .show();
    }

    private void showLoseDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("💥 踩雷了！")
                .setMessage("地雷已被引爆！不要气馁，扫雷需要冷静的逻辑推理。")
                .setPositiveButton("再试一次", (d, w) -> startGame())
                .setNegativeButton("返回大厅", (d, w) -> {
                    if (getActivity() != null) getActivity().finish();
                })
                .show();
    }

    private void showDifficultyDialog() {
        CharSequence[] items = {
                "简单 (9x9 · 10雷)",
                "普通 (16x16 · 40雷)",
                "困难 (16x30 · 99雷)"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("选择扫雷难度")
                .setItems(items, (dialog, which) -> {
                    setDifficulty(which + 1);
                    Toast.makeText(requireContext(), "已切换至：" + MinesweeperGame.getDifficultyName(which + 1), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("扫雷规则与技巧")
                .setMessage(
                        "• 目标：找出所有未埋地雷的安全格子，并将所有雷排查完毕。\n\n" +
                        "• 数字含义：翻开格子中的数字代表其周围 8 格内地雷的总数。\n\n" +
                        "• 操作方式：\n" +
                        "1. 翻开模式：轻触翻开格子，长按可标记地雷旗帜；\n" +
                        "2. 快速插旗：点击底部【插旗模式】开关，轻触直接秒插红旗；\n" +
                        "3. 笑脸快捷键：上方 😊 笑脸可随时重开新局。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        CharSequence[] items = {
                "重新开始本局",
                "选择游戏难度",
                "规则技巧说明"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("⚙ 扫雷设置")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: startGame(); break;
                        case 1: showDifficultyDialog(); break;
                        case 2: showRulesDialog(); break;
                        default: break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleBackPress() {
        if (minesweeperView != null && minesweeperView.isGameStarted() && !minesweeperView.isGameOver()
                && minesweeperView.getElapsedSeconds() > 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出扫雷")
                    .setMessage("当前扫雷挑战正在进行中，退出将放弃本局，确定退出吗？")
                    .setPositiveButton("确定退出", (d, w) -> {
                        if (getActivity() != null) getActivity().finish();
                    })
                    .setNegativeButton("继续挑战", null)
                    .show();
        } else {
            if (getActivity() != null) getActivity().finish();
        }
    }

    // ==========================================
    // 配色与绘制辅助
    // ==========================================

    private void initThemeColors() {
        if (isNight) {
            colorBg = 0xFF121622;
            colorSurface = 0xFF1C2230;
            colorTextPrimary = 0xFFEAECEF;
            colorTextSecondary = 0xFF90A4AE;
            colorAccent = 0xFF5B8A72;
            colorAccentLight = 0xFF2A3D34;
            colorActiveButton = 0xFF3E8061;
            colorInactiveButton = 0xFF2A3142;
            colorDanger = 0xFFEF5350;
        } else {
            colorBg = 0xFFF5EADA;
            colorSurface = 0xFFFFFFFF;
            colorTextPrimary = 0xFF212529;
            colorTextSecondary = 0xFF78909C;
            colorAccent = 0xFF5B8A72;
            colorAccentLight = 0xFFE8F5E9;
            colorActiveButton = 0xFF4A7C59;
            colorInactiveButton = 0xFFE0E0E0;
            colorDanger = 0xFFDC2626;
        }
    }

    private GradientDrawable roundBackground(int color, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    private RippleDrawable createCircleRipple(int normalColor, int sizePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(normalColor);
        return new RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33888888),
                gd,
                null
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopTimer();
        if (minesweeperView != null) {
            minesweeperView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (minesweeperView != null && !minesweeperView.isGameOver()) {
            minesweeperView.resumeGame();
            if (timerRunning) {
                timerHandler.post(timerRunnable);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        if (minesweeperView != null) {
            minesweeperView.stopGame();
        }
    }
}

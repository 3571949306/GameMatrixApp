package com.gamecenter.app.guess;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

import java.util.List;

/**
 * 现代化 猜数字战场 Fragment。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场，首屏秒入猜数挑战；</li>
 *   <li>标准顶部导航栏：❮ 退出确认、游戏标题、[难度 ▾] 下拉胶囊、ℹ 规则弹窗、⚙ 设置弹窗；</li>
 *   <li>双雄 HUD 看板：有效区间范围跟踪、已猜次数、动态秒表与 🔥 连胜火焰；</li>
 *   <li>动态二分收敛展示板：实时呈现 [Low ~ High] 区间与剩余可能数字数；</li>
 *   <li>数学最优二分决策建议 (💡 智能提示)：一键填充或查看理论折半中位数；</li>
 *   <li>便捷微调按键：[-10], [-1], [+1], [+10] 疾速微调；</li>
 *   <li>历史猜测轨迹卡片流；</li>
 *   <li>底部圆角胶囊控制栏：↩ 撤回、🔄 新局；</li>
 *   <li>沉浸式通关结算弹窗与持久化。</li>
 * </ul>
 * </p>
 */
public class GuessModuleFragment extends Fragment {

    private static final String GAME_ID = "guess";

    private GuessGame game;
    private GameUsageStore usageStore;

    // 计时器相关
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long roundStartTime = 0;
    private long elapsedSeconds = 0;
    private boolean isTimerRunning = false;

    // 顶部导航组件
    private TextView tvBack;
    private TextView tvTitle;
    private TextView tvDifficultyBadge;
    private TextView tvRules;
    private TextView tvSettings;

    // HUD 组件
    private TextView tvAttemptsStats;
    private TextView tvTimer;
    private TextView tvStreak;
    private TextView tvRangeStats;

    // 战场核心组件
    private TextView tvIntervalBanner;
    private TextView tvRemainingCount;
    private TextView tvStatusBanner;
    private EditText etGuessInput;
    private Button btnBinaryHint;
    private Button btnSubmitGuess;
    private LinearLayout llHistoryChips;

    // 底部胶囊
    private Button btnUndo;
    private Button btnNewGame;

    // 主题色
    private int colorBg;
    private int colorSurface;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorTextMuted;
    private int colorAccent;
    private int colorChipBg;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                elapsedSeconds = (System.currentTimeMillis() - roundStartTime) / 1000;
                long mins = elapsedSeconds / 60;
                long secs = elapsedSeconds % 60;
                if (tvTimer != null) {
                    tvTimer.setText(String.format("⏱ %02d:%02d", mins, secs));
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new GuessGame();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        resolveThemeColors(ctx);

        if (usageStore == null) {
            usageStore = new GameUsageStore(ctx);
        }
        usageStore.recordLaunch(GAME_ID);
        game.setHighScore(usageStore.getHighScore(GAME_ID));

        ScrollView rootScroll = new ScrollView(ctx);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(colorBg);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (16 * dp));
        rootScroll.addView(root);

        // 1. 顶部标准导航栏
        root.addView(createTopBar(ctx, dp));

        // 2. 双雄对战 HUD 看板
        root.addView(createHudDashboard(ctx, dp));

        // 3. 二分收敛主擂台
        root.addView(createBattleArea(ctx, dp));

        // 4. 底部操作胶囊栏
        root.addView(createActionCapsules(ctx, dp));

        // 即开即玩
        startNewRound(false);

        return rootScroll;
    }

    private View createTopBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));

        tvBack = new TextView(ctx);
        tvBack.setText("❮");
        tvBack.setTextSize(20f);
        tvBack.setTextColor(colorTextPrimary);
        tvBack.setPadding((int) (8 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        tvBack.setOnClickListener(v -> handleBackPress());
        bar.addView(tvBack);

        tvTitle = new TextView(ctx);
        tvTitle.setText("猜数字");
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

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = (int) (8 * dp);
        tvDifficultyBadge.setLayoutParams(badgeParams);
        tvDifficultyBadge.setOnClickListener(v -> showDifficultyDialog());
        bar.addView(tvDifficultyBadge);

        View spacer = new View(ctx);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
        bar.addView(spacer, spacerParams);

        tvRules = new TextView(ctx);
        tvRules.setText("ℹ");
        tvRules.setTextSize(18f);
        tvRules.setTextColor(colorTextSecondary);
        tvRules.setPadding((int) (10 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        tvRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(tvRules);

        tvSettings = new TextView(ctx);
        tvSettings.setText("⚙");
        tvSettings.setTextSize(18f);
        tvSettings.setTextColor(colorTextSecondary);
        tvSettings.setPadding((int) (8 * dp), (int) (8 * dp), (int) (4 * dp), (int) (8 * dp));
        tvSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(tvSettings);

        return bar;
    }

    private View createHudDashboard(Context ctx, float dp) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        hud.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));

        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(colorSurface);
        hudBg.setCornerRadius(14 * dp);
        hudBg.setStroke((int) (1 * dp), applyAlpha(colorTextMuted, 0.2f));
        hud.setBackground(hudBg);

        // 左列：尝试次数与得分
        LinearLayout colLeft = new LinearLayout(ctx);
        colLeft.setOrientation(LinearLayout.VERTICAL);
        colLeft.setGravity(Gravity.START);
        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLeft.setLayoutParams(lParams);

        TextView tvPlayerLabel = new TextView(ctx);
        tvPlayerLabel.setText("🎯 猜测次数");
        tvPlayerLabel.setTextSize(13f);
        tvPlayerLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerLabel.setTextColor(colorTextPrimary);
        colLeft.addView(tvPlayerLabel);

        tvAttemptsStats = new TextView(ctx);
        tvAttemptsStats.setText("已猜 0 次 | 得分 0");
        tvAttemptsStats.setTextSize(11f);
        tvAttemptsStats.setTextColor(colorTextSecondary);
        colLeft.addView(tvAttemptsStats);
        hud.addView(colLeft);

        // 中间：计时与连胜
        LinearLayout colCenter = new LinearLayout(ctx);
        colCenter.setOrientation(LinearLayout.VERTICAL);
        colCenter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cParams.leftMargin = (int) (4 * dp);
        cParams.rightMargin = (int) (4 * dp);
        colCenter.setLayoutParams(cParams);

        tvTimer = new TextView(ctx);
        tvTimer.setText("⏱ 00:00");
        tvTimer.setTextSize(13f);
        tvTimer.setTypeface(Typeface.MONOSPACE);
        tvTimer.setTextColor(colorAccent);
        colCenter.addView(tvTimer);

        tvStreak = new TextView(ctx);
        tvStreak.setText("🔥 连胜 0 (最高 0)");
        tvStreak.setTextSize(11f);
        tvStreak.setTextColor(colorAccent);
        colCenter.addView(tvStreak);
        hud.addView(colCenter);

        // 右列：区间信息
        LinearLayout colRight = new LinearLayout(ctx);
        colRight.setOrientation(LinearLayout.VERTICAL);
        colRight.setGravity(Gravity.END);
        LinearLayout.LayoutParams rParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colRight.setLayoutParams(rParams);

        TextView tvRangeLabel = new TextView(ctx);
        tvRangeLabel.setText("🔍 理论上限");
        tvRangeLabel.setTextSize(13f);
        tvRangeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvRangeLabel.setTextColor(colorTextPrimary);
        colRight.addView(tvRangeLabel);

        tvRangeStats = new TextView(ctx);
        tvRangeStats.setText("0 ~ " + game.getCurrentMax());
        tvRangeStats.setTextSize(11f);
        tvRangeStats.setTextColor(colorTextSecondary);
        colRight.addView(tvRangeStats);
        hud.addView(colRight);

        LinearLayout.LayoutParams hudParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hudParams.bottomMargin = (int) (12 * dp);
        hud.setLayoutParams(hudParams);

        return hud;
    }

    private View createBattleArea(Context ctx, float dp) {
        LinearLayout battle = new LinearLayout(ctx);
        battle.setOrientation(LinearLayout.VERTICAL);
        battle.setGravity(Gravity.CENTER_HORIZONTAL);
        battle.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));

        GradientDrawable battleBg = new GradientDrawable();
        battleBg.setColor(colorSurface);
        battleBg.setCornerRadius(18 * dp);
        battleBg.setStroke((int) (1 * dp), applyAlpha(colorTextMuted, 0.25f));
        battle.setBackground(battleBg);

        // 1. 动态区间显示
        tvIntervalBanner = new TextView(ctx);
        tvIntervalBanner.setText("当前可能区间: [ 0 ~ 100 ]");
        tvIntervalBanner.setTextSize(16f);
        tvIntervalBanner.setTypeface(Typeface.DEFAULT_BOLD);
        tvIntervalBanner.setTextColor(colorAccent);
        tvIntervalBanner.setGravity(Gravity.CENTER);
        battle.addView(tvIntervalBanner);

        tvRemainingCount = new TextView(ctx);
        tvRemainingCount.setText("还剩 101 个未知数字");
        tvRemainingCount.setTextSize(12f);
        tvRemainingCount.setTextColor(colorTextSecondary);
        tvRemainingCount.setGravity(Gravity.CENTER);
        battle.addView(tvRemainingCount);

        // 2. 状态反馈横幅
        tvStatusBanner = new TextView(ctx);
        tvStatusBanner.setText("已生成神秘数字，请开始猜测！");
        tvStatusBanner.setTextSize(14f);
        tvStatusBanner.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusBanner.setTextColor(colorTextPrimary);
        tvStatusBanner.setGravity(Gravity.CENTER);
        tvStatusBanner.setPadding((int) (10 * dp), (int) (8 * dp), (int) (10 * dp), (int) (8 * dp));

        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(applyAlpha(colorAccent, 0.1f));
        statusBg.setCornerRadius(12 * dp);
        tvStatusBanner.setBackground(statusBg);

        LinearLayout.LayoutParams sParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sParams.topMargin = (int) (10 * dp);
        sParams.bottomMargin = (int) (12 * dp);
        tvStatusBanner.setLayoutParams(sParams);
        battle.addView(tvStatusBanner);

        // 3. 输入框与快捷填充
        LinearLayout inputRow = new LinearLayout(ctx);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        etGuessInput = new EditText(ctx);
        etGuessInput.setHint("输入数字");
        etGuessInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        etGuessInput.setTextSize(22f);
        etGuessInput.setTypeface(Typeface.DEFAULT_BOLD);
        etGuessInput.setTextColor(colorTextPrimary);
        etGuessInput.setGravity(Gravity.CENTER);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(colorBg);
        inputBg.setCornerRadius(12 * dp);
        inputBg.setStroke((int) (1.5f * dp), colorAccent);
        etGuessInput.setBackground(inputBg);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, (int) (52 * dp), 1f);
        inputParams.rightMargin = (int) (8 * dp);
        etGuessInput.setLayoutParams(inputParams);
        inputRow.addView(etGuessInput);

        btnBinaryHint = new Button(ctx);
        btnBinaryHint.setText("💡 最优中位");
        btnBinaryHint.setTextSize(12f);
        btnBinaryHint.setTypeface(Typeface.DEFAULT_BOLD);
        btnBinaryHint.setTextColor(Color.WHITE);

        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setColor(0xFF1976D2);
        hintBg.setCornerRadius(12 * dp);
        btnBinaryHint.setBackground(hintBg);

        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (52 * dp));
        btnBinaryHint.setLayoutParams(hintParams);
        btnBinaryHint.setOnClickListener(v -> fillOptimalBinaryMid());
        inputRow.addView(btnBinaryHint);
        battle.addView(inputRow);

        // 4. 微调按钮行 ([-10], [-1], [+1], [+10])
        LinearLayout stepRow = new LinearLayout(ctx);
        stepRow.setOrientation(LinearLayout.HORIZONTAL);
        stepRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stepRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepRowParams.topMargin = (int) (8 * dp);
        stepRowParams.bottomMargin = (int) (10 * dp);
        stepRow.setLayoutParams(stepRowParams);

        addStepButton(ctx, dp, stepRow, -10);
        addStepButton(ctx, dp, stepRow, -1);
        addStepButton(ctx, dp, stepRow, 1);
        addStepButton(ctx, dp, stepRow, 10);
        battle.addView(stepRow);

        // 5. 提交猜测按钮
        btnSubmitGuess = new Button(ctx);
        btnSubmitGuess.setText("🎯 确认猜测");
        btnSubmitGuess.setTextSize(15f);
        btnSubmitGuess.setTypeface(Typeface.DEFAULT_BOLD);
        btnSubmitGuess.setTextColor(Color.WHITE);

        GradientDrawable submitBg = new GradientDrawable();
        submitBg.setColor(colorAccent);
        submitBg.setCornerRadius(24 * dp);
        btnSubmitGuess.setBackground(submitBg);

        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * dp));
        btnSubmitGuess.setLayoutParams(submitParams);
        btnSubmitGuess.setOnClickListener(v -> onSubmitGuess());
        battle.addView(btnSubmitGuess);

        // 6. 历史记录卡片流
        TextView tvHistLabel = new TextView(ctx);
        tvHistLabel.setText("📋 猜测轨迹 (由新到旧):");
        tvHistLabel.setTextSize(12f);
        tvHistLabel.setTextColor(colorTextSecondary);
        LinearLayout.LayoutParams histLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        histLp.topMargin = (int) (12 * dp);
        histLp.bottomMargin = (int) (6 * dp);
        tvHistLabel.setLayoutParams(histLp);
        battle.addView(tvHistLabel);

        HorizontalScrollView hsvHist = new HorizontalScrollView(ctx);
        hsvHist.setHorizontalScrollBarEnabled(false);
        llHistoryChips = new LinearLayout(ctx);
        llHistoryChips.setOrientation(LinearLayout.HORIZONTAL);
        llHistoryChips.setGravity(Gravity.CENTER_VERTICAL);
        hsvHist.addView(llHistoryChips);
        battle.addView(hsvHist);

        LinearLayout.LayoutParams battleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        battleParams.bottomMargin = (int) (10 * dp);
        battle.setLayoutParams(battleParams);

        return battle;
    }

    private void addStepButton(Context ctx, float dp, LinearLayout parent, int delta) {
        Button btn = new Button(ctx);
        btn.setText((delta > 0 ? "+" : "") + delta);
        btn.setTextSize(12f);
        btn.setTextColor(colorTextPrimary);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(colorBg);
        gd.setCornerRadius(8 * dp);
        gd.setStroke((int) (1 * dp), applyAlpha(colorTextMuted, 0.3f));
        btn.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (36 * dp), 1f);
        lp.leftMargin = (int) (3 * dp);
        lp.rightMargin = (int) (3 * dp);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> applyInputDelta(delta));
        parent.addView(btn);
    }

    private View createActionCapsules(Context ctx, float dp) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        btnUndo = createCapsuleButton(ctx, dp, "↩ 撤销", colorSurface, v -> onUndoGuess());
        btnUndo.setTextColor(colorTextPrimary);
        btnNewGame = createCapsuleButton(ctx, dp, "🔄 新局", colorAccent, v -> startNewRound(true));

        LinearLayout.LayoutParams uParams = new LinearLayout.LayoutParams(0, (int) (42 * dp), 1f);
        uParams.rightMargin = (int) (6 * dp);
        row.addView(btnUndo, uParams);

        LinearLayout.LayoutParams nParams = new LinearLayout.LayoutParams(0, (int) (42 * dp), 1f);
        nParams.leftMargin = (int) (6 * dp);
        row.addView(btnNewGame, nParams);

        return row;
    }

    private Button createCapsuleButton(Context ctx, float dp, String text, int bgColor, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(22 * dp);
        btn.setBackground(gd);

        btn.setOnClickListener(listener);
        return btn;
    }

    // ==================== 游戏交互逻辑 ====================

    private void startNewRound(boolean showToast) {
        game.startNewRound();
        resetTimer();
        updateBattleDisplay();
        updateHudStats();
        llHistoryChips.removeAllViews();
        etGuessInput.setText("");
        etGuessInput.setEnabled(true);
        btnSubmitGuess.setEnabled(true);
        btnSubmitGuess.setAlpha(1.0f);
        btnBinaryHint.setEnabled(true);

        if (showToast && getContext() != null) {
            Toast.makeText(getContext(), "已开启全新神秘数字挑战！", Toast.LENGTH_SHORT).show();
        }
    }

    private void onSubmitGuess() {
        if (!game.isGameActive()) return;

        String input = etGuessInput.getText() != null ? etGuessInput.getText().toString().trim() : "";
        if (input.isEmpty()) {
            Toast.makeText(getContext(), "请输入一个数字！", Toast.LENGTH_SHORT).show();
            return;
        }

        int guessVal;
        try {
            guessVal = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "格式不正确，请输入纯数字", Toast.LENGTH_SHORT).show();
            return;
        }

        if (guessVal < 0 || guessVal > game.getCurrentMax()) {
            Toast.makeText(getContext(), "请输入 0 ~ " + game.getCurrentMax() + " 范围内的数字！", Toast.LENGTH_SHORT).show();
            return;
        }

        GuessGame.GuessResult res = game.makeGuess(guessVal);
        updateBattleDisplay();
        updateHudStats();
        addHistoryChip(guessVal, res);

        if (res == GuessGame.GuessResult.CORRECT) {
            handleWinSettlement();
        }
    }

    private void applyInputDelta(int delta) {
        String input = etGuessInput.getText() != null ? etGuessInput.getText().toString().trim() : "";
        int current = game.getOptimalBinaryMid();
        if (!input.isEmpty()) {
            try {
                current = Integer.parseInt(input);
            } catch (Exception ignored) {}
        }
        int next = Math.max(0, Math.min(game.getCurrentMax(), current + delta));
        etGuessInput.setText(String.valueOf(next));
        etGuessInput.setSelection(etGuessInput.getText().length());
    }

    private void fillOptimalBinaryMid() {
        int mid = game.getOptimalBinaryMid();
        etGuessInput.setText(String.valueOf(mid));
        etGuessInput.setSelection(etGuessInput.getText().length());
        Toast.makeText(getContext(), "已填入理论二分中位数: " + mid, Toast.LENGTH_SHORT).show();
    }

    private void onUndoGuess() {
        if (!game.canUndo()) return;
        if (game.undoLastGuess()) {
            updateBattleDisplay();
            updateHudStats();
            if (llHistoryChips.getChildCount() > 0) {
                llHistoryChips.removeViewAt(0);
            }
            Toast.makeText(getContext(), "↩ 已撤销上一次猜测", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleWinSettlement() {
        stopTimer();
        etGuessInput.setEnabled(false);
        btnSubmitGuess.setEnabled(false);
        btnSubmitGuess.setAlpha(0.5f);
        btnBinaryHint.setEnabled(false);

        String msg = String.format("恭喜！神秘数字正是 %d！\n总计耗时 %02d:%02d，共猜测 %d 次！\n斩获 +%d 分！",
                game.getTargetNumber(), elapsedSeconds / 60, elapsedSeconds % 60, game.getAttempts(), game.getScore());

        // 持久化记录
        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, game.getHighScore());
            usageStore.recordPlayTime(GAME_ID, elapsedSeconds * 1000);
            usageStore.recordWin(GAME_ID);
        }

        if (getContext() != null) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("🎉 猜中决胜！")
                    .setMessage(msg)
                    .setPositiveButton("再来一局", (dialog, which) -> startNewRound(true))
                    .setNegativeButton("留在此局", null)
                    .show();
        }
    }

    private void updateBattleDisplay() {
        int low = game.getCurrentLow();
        int high = game.getCurrentHigh();
        int remaining = Math.max(0, high - low + 1);

        tvIntervalBanner.setText("当前锁定区间: [ " + low + " ~ " + high + " ]");
        tvRemainingCount.setText("还剩 " + remaining + " 个未知可能数字");

        if (game.isWin()) {
            tvStatusBanner.setText("🎉 猜中了！神秘数字就是 " + game.getTargetNumber() + "！");
            tvStatusBanner.setTextColor(0xFF2E7D32);
        } else if (game.getAttempts() == 0) {
            tvStatusBanner.setText("已生成神秘数字，请开始猜测！");
            tvStatusBanner.setTextColor(colorTextPrimary);
        } else {
            List<GuessGame.GuessRecord> hist = game.getHistory();
            GuessGame.GuessRecord last = hist.get(hist.size() - 1);
            if (last.result == GuessGame.GuessResult.TOO_BIG) {
                tvStatusBanner.setText("📉 " + last.guess + " 太大了！请往小了猜 ⬇️");
                tvStatusBanner.setTextColor(0xFFE65100);
            } else if (last.result == GuessGame.GuessResult.TOO_SMALL) {
                tvStatusBanner.setText("📈 " + last.guess + " 太小了！请往大了猜 ⬆️");
                tvStatusBanner.setTextColor(0xFF1976D2);
            }
        }

        btnUndo.setEnabled(game.canUndo());
        btnUndo.setAlpha(game.canUndo() ? 1.0f : 0.4f);
    }

    private void updateHudStats() {
        tvAttemptsStats.setText("已猜 " + game.getAttempts() + " 次 | 得分 " + game.getScore());
        tvStreak.setText("🔥 连胜 " + game.getWinStreak() + " (最高 " + game.getMaxStreak() + ")");
        tvRangeStats.setText("0 ~ " + game.getCurrentMax());
    }

    private void addHistoryChip(int guess, GuessGame.GuessResult res) {
        Context ctx = getContext();
        if (ctx == null) return;
        float dp = ctx.getResources().getDisplayMetrics().density;

        TextView chip = new TextView(ctx);
        String label;
        int chipColor;

        if (res == GuessGame.GuessResult.CORRECT) {
            label = "✓ " + guess + " (中)";
            chipColor = 0xFF2E7D32;
        } else if (res == GuessGame.GuessResult.TOO_BIG) {
            label = "⬇️ " + guess + " (大)";
            chipColor = 0xFFE65100;
        } else {
            label = "⬆️ " + guess + " (小)";
            chipColor = 0xFF1976D2;
        }

        chip.setText(label);
        chip.setTextSize(11f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(Color.WHITE);
        chip.setPadding((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(chipColor);
        gd.setCornerRadius(12 * dp);
        chip.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) (6 * dp);
        chip.setLayoutParams(lp);

        llHistoryChips.addView(chip, 0); // 插入最前
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge == null) return;
        switch (game.getDifficulty()) {
            case GuessGame.DIFFICULTY_MEDIUM:
                tvDifficultyBadge.setText("0-500 ▾");
                break;
            case GuessGame.DIFFICULTY_HARD:
                tvDifficultyBadge.setText("0-1000 ▾");
                break;
            case GuessGame.DIFFICULTY_EASY:
            default:
                tvDifficultyBadge.setText("0-100 ▾");
                break;
        }
    }

    private void showDifficultyDialog() {
        if (getContext() == null) return;
        String[] options = {
                "简单 (0 ~ 100 范围，推荐新手)",
                "中等 (0 ~ 500 范围，标准挑战)",
                "困难 (0 ~ 1000 范围，高倍积分)"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("选择数字范围难度")
                .setSingleChoiceItems(options, game.getDifficulty(), (dialog, which) -> {
                    game.setDifficulty(which);
                    updateDifficultyBadgeText();
                    dialog.dismiss();
                    startNewRound(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRulesDialog() {
        if (getContext() == null) return;
        String rules = "【玩法规则】\n"
                + "• 系统随机生成指定范围内的神秘数字，玩家每次猜测后会得到“偏大”或“偏小”的精确提示。\n"
                + "• 目标：使用最少猜测次数锁定并猜中该神秘数字！\n\n"
                + "【算法秘诀：二分查找法】\n"
                + "• 每次选择当前区间的正中间数 (Low + High) / 2。\n"
                + "• 无论提示偏大还是偏小，都能直接排除 50% 的错误范围！\n"
                + "• 0~100 范围最多只需 7 次即可 100% 破局！\n"
                + "• 点击【💡 最优中位】可直接一键填入该理论最优值。";

        new AlertDialog.Builder(requireContext())
                .setTitle("猜数字与二分搜索秘籍")
                .setMessage(rules)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        if (getContext() == null) return;
        String[] items = {"重置本次得分与连胜", "清空历史高分纪录"};
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        game = new GuessGame();
                        startNewRound(false);
                        Toast.makeText(getContext(), "已重置本次战绩", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        if (usageStore != null) {
                            usageStore.recordScore(GAME_ID, 0);
                        }
                        game.setHighScore(0);
                        updateHudStats();
                        Toast.makeText(getContext(), "高分纪录已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("返回", null)
                .show();
    }

    private void handleBackPress() {
        if (game.isGameActive() && game.getAttempts() > 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("当前猜数挑战正在进行中，退出将记录离开。确定退出吗？")
                    .setPositiveButton("退出", (dialog, which) -> finishFragment())
                    .setNegativeButton("继续挑战", null)
                    .show();
        } else {
            finishFragment();
        }
    }

    private void finishFragment() {
        stopTimer();
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    private void resetTimer() {
        roundStartTime = System.currentTimeMillis();
        elapsedSeconds = 0;
        isTimerRunning = true;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }

    private void resolveThemeColors(Context ctx) {
        int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightMode == Configuration.UI_MODE_NIGHT_YES;

        if (isNight) {
            colorBg = 0xFF121418;
            colorSurface = 0xFF1E222B;
            colorTextPrimary = 0xFFEEEEEE;
            colorTextSecondary = 0xFFB0BEC5;
            colorTextMuted = 0xFF78909C;
            colorAccent = 0xFF1E88E5;
            colorChipBg = 0xFF263238;
        } else {
            colorBg = 0xFFF5F7FA;
            colorSurface = 0xFFFFFFFF;
            colorTextPrimary = 0xFF212121;
            colorTextSecondary = 0xFF546E7A;
            colorTextMuted = 0xFF90A4AE;
            colorAccent = 0xFF1976D2;
            colorChipBg = 0xFFECEFF1;
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}

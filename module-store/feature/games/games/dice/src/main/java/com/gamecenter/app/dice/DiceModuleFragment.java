package com.gamecenter.app.dice;

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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

/**
 * 现代化 骰子对决战场 Fragment。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场，首屏秒入摇骰竞技；</li>
 *   <li>标准顶部导航栏：❮ 退出确认、游戏标题、[难度 ▾] 下拉胶囊、ℹ 规则弹窗、⚙ 设置弹窗；</li>
 *   <li>双雄 HUD 看板：双方胜负平率、动态秒表与 🔥 连胜火焰；</li>
 *   <li>拟真仿真牌桌：圆角立体象牙骰子、真实点数花色、双雄摇骰盘；</li>
 *   <li>概率论智能决策建议 (💡 提示)：依据数学期望值分析当前点数加倍期望；</li>
 *   <li>底部圆角胶囊控制栏：🎲 摇骰、⚡ 加倍、💡 提示、↩ 撤回、🔄 新局；</li>
 *   <li>沉浸式对局结算弹窗与持久化。</li>
 * </ul>
 * </p>
 */
public class DiceModuleFragment extends Fragment {

    private static final String GAME_ID = "dice";

    private DiceGame game;
    private GameUsageStore usageStore;

    // 计时器
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long roundStartTime = 0;
    private long elapsedSeconds = 0;
    private boolean isTimerRunning = false;

    // 顶部导航
    private TextView tvBack;
    private TextView tvTitle;
    private TextView tvDifficultyBadge;
    private TextView tvRules;
    private TextView tvSettings;

    // HUD 组件
    private TextView tvPlayerStats;
    private TextView tvTimer;
    private TextView tvStreak;
    private TextView tvAiStats;

    // 牌桌组件
    private TextView tvAiTotalHeader;
    private LinearLayout llAiDiceTray;
    private TextView tvStatusBanner;
    private TextView tvPlayerTotalHeader;
    private LinearLayout llPlayerDiceTray;

    // 操作胶囊
    private Button btnRoll;
    private Button btnDoubleUp;
    private Button btnHint;
    private Button btnUndo;
    private Button btnNewGame;

    // 主题配色
    private int colorBg;
    private int colorSurface;
    private int colorTableFelt;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorTextMuted;
    private int colorAccent;
    private int colorWin;
    private int colorDiceBg;
    private int colorDiceBorder;

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
        game = new DiceGame();
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
        root.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (16 * dp));
        rootScroll.addView(root);

        // 1. 顶部标准导航栏
        root.addView(createTopBar(ctx, dp));

        // 2. 双雄对战 HUD 看板
        root.addView(createHudDashboard(ctx, dp));

        // 3. 拟真仿真摇骰盘
        root.addView(createTabletopView(ctx, dp));

        // 4. 底部操作胶囊栏
        root.addView(createActionCapsules(ctx, dp));

        // 即开即玩：首屏自动摇出首局
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
        tvTitle.setText("骰子对决");
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
        hud.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));

        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(colorSurface);
        hudBg.setCornerRadius(14 * dp);
        hudBg.setStroke((int) (1 * dp), applyAlpha(colorTextMuted, 0.2f));
        hud.setBackground(hudBg);

        // 玩家左侧
        LinearLayout colPlayer = new LinearLayout(ctx);
        colPlayer.setOrientation(LinearLayout.VERTICAL);
        colPlayer.setGravity(Gravity.START);
        LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colPlayer.setLayoutParams(pParams);

        TextView tvPlayerName = new TextView(ctx);
        tvPlayerName.setText("🧑 我方点数");
        tvPlayerName.setTextSize(13f);
        tvPlayerName.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerName.setTextColor(colorTextPrimary);
        colPlayer.addView(tvPlayerName);

        tvPlayerStats = new TextView(ctx);
        tvPlayerStats.setText("胜 0 | 负 0 (0%)");
        tvPlayerStats.setTextSize(11f);
        tvPlayerStats.setTextColor(colorTextSecondary);
        colPlayer.addView(tvPlayerStats);
        hud.addView(colPlayer);

        // 中间计时与连胜
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

        // 电脑右侧
        LinearLayout colAi = new LinearLayout(ctx);
        colAi.setOrientation(LinearLayout.VERTICAL);
        colAi.setGravity(Gravity.END);
        LinearLayout.LayoutParams aParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colAi.setLayoutParams(aParams);

        TextView tvAiName = new TextView(ctx);
        tvAiName.setText("🤖 电脑应手");
        tvAiName.setTextSize(13f);
        tvAiName.setTypeface(Typeface.DEFAULT_BOLD);
        tvAiName.setTextColor(colorTextPrimary);
        colAi.addView(tvAiName);

        tvAiStats = new TextView(ctx);
        tvAiStats.setText("胜 0 | 平 0");
        tvAiStats.setTextSize(11f);
        tvAiStats.setTextColor(colorTextSecondary);
        colAi.addView(tvAiStats);
        hud.addView(colAi);

        LinearLayout.LayoutParams hudParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hudParams.bottomMargin = (int) (10 * dp);
        hud.setLayoutParams(hudParams);

        return hud;
    }

    private View createTabletopView(Context ctx, float dp) {
        LinearLayout table = new LinearLayout(ctx);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding((int) (16 * dp), (int) (14 * dp), (int) (16 * dp), (int) (14 * dp));

        GradientDrawable tableBg = new GradientDrawable();
        tableBg.setColor(colorTableFelt);
        tableBg.setCornerRadius(18 * dp);
        tableBg.setStroke((int) (2 * dp), applyAlpha(colorAccent, 0.4f));
        table.setBackground(tableBg);

        // 电脑骰盘标题
        tvAiTotalHeader = new TextView(ctx);
        tvAiTotalHeader.setText("🤖 电脑骰子  总点数: 0 点");
        tvAiTotalHeader.setTextSize(14f);
        tvAiTotalHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvAiTotalHeader.setTextColor(0xFFE8F5E9);
        table.addView(tvAiTotalHeader);

        // 电脑骰子卡片托盘
        llAiDiceTray = new LinearLayout(ctx);
        llAiDiceTray.setOrientation(LinearLayout.HORIZONTAL);
        llAiDiceTray.setGravity(Gravity.CENTER);
        llAiDiceTray.setPadding(0, (int) (8 * dp), 0, (int) (12 * dp));
        table.addView(llAiDiceTray);

        // 中央胜负状态与加倍指示
        tvStatusBanner = new TextView(ctx);
        tvStatusBanner.setText("摇骰成功！请选择【⚡ 加倍】或【🔄 决胜】");
        tvStatusBanner.setTextSize(14f);
        tvStatusBanner.setGravity(Gravity.CENTER);
        tvStatusBanner.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusBanner.setTextColor(0xFFFFFFFF);
        tvStatusBanner.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(0x88000000);
        bannerBg.setCornerRadius(12 * dp);
        tvStatusBanner.setBackground(bannerBg);

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerParams.topMargin = (int) (4 * dp);
        bannerParams.bottomMargin = (int) (12 * dp);
        tvStatusBanner.setLayoutParams(bannerParams);
        table.addView(tvStatusBanner);

        // 我方骰盘标题
        tvPlayerTotalHeader = new TextView(ctx);
        tvPlayerTotalHeader.setText("🧑 我方骰子  总点数: 0 点");
        tvPlayerTotalHeader.setTextSize(14f);
        tvPlayerTotalHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerTotalHeader.setTextColor(0xFFE8F5E9);
        table.addView(tvPlayerTotalHeader);

        // 我方骰子卡片托盘
        llPlayerDiceTray = new LinearLayout(ctx);
        llPlayerDiceTray.setOrientation(LinearLayout.HORIZONTAL);
        llPlayerDiceTray.setGravity(Gravity.CENTER);
        llPlayerDiceTray.setPadding(0, (int) (8 * dp), 0, (int) (6 * dp));
        table.addView(llPlayerDiceTray);

        LinearLayout.LayoutParams tableParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tableParams.bottomMargin = (int) (10 * dp);
        table.setLayoutParams(tableParams);

        return table;
    }

    private View createActionCapsules(Context ctx, float dp) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);

        // Row 1: 🎲 重新摇骰、⚡ 加倍翻倍、💡 胜率提示
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);

        btnRoll = createCapsuleButton(ctx, dp, "🎲 重新摇骰", colorAccent, v -> onRollClick());
        btnDoubleUp = createCapsuleButton(ctx, dp, "⚡ 加倍 (2x)", 0xFFE65100, v -> onDoubleUpClick());
        btnHint = createCapsuleButton(ctx, dp, "💡 胜率建议", 0xFF1976D2, v -> showProbabilityHint());

        row1.addView(btnRoll, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
        row1.addView(btnDoubleUp, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
        row1.addView(btnHint, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
        container.addView(row1);

        // Row 2: ↩ 撤销、🔄 决胜/下一局
        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams r2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2Params.topMargin = (int) (8 * dp);
        row2.setLayoutParams(r2Params);

        btnUndo = createCapsuleButton(ctx, dp, "↩ 撤销", colorSurface, v -> onUndoClick());
        btnUndo.setTextColor(colorTextPrimary);
        btnNewGame = createCapsuleButton(ctx, dp, "🏁 决胜结算", colorWin, v -> onResolveClick());

        LinearLayout.LayoutParams uParams = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
        uParams.rightMargin = (int) (6 * dp);
        row2.addView(btnUndo, uParams);

        LinearLayout.LayoutParams nParams = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
        nParams.leftMargin = (int) (6 * dp);
        row2.addView(btnNewGame, nParams);

        container.addView(row2);
        return container;
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (42 * dp), 1f);
        lp.leftMargin = (int) (3 * dp);
        lp.rightMargin = (int) (3 * dp);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    // ==================== 象牙骰子实体卡片渲染 ====================

    private View createDiceView(Context ctx, float dp, int val, boolean isPlayer) {
        FrameLayout diceFrame = new FrameLayout(ctx);
        int size = (int) (56 * dp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorDiceBg);
        bg.setCornerRadius(14 * dp);
        bg.setStroke((int) (2 * dp), isPlayer ? colorAccent : 0xFFE57373);
        diceFrame.setBackground(bg);

        TextView tvEmoji = new TextView(ctx);
        String face = (val >= 1 && val <= 6) ? DiceGame.DICE_FACES[val - 1] : "⚀";
        tvEmoji.setText(face);
        tvEmoji.setTextSize(34f);
        // 红点一/四特别着色，其余黑色
        int dotColor = (val == 1 || val == 4) ? 0xFFD32F2F : 0xFF212121;
        tvEmoji.setTextColor(dotColor);
        tvEmoji.setGravity(Gravity.CENTER);
        diceFrame.addView(tvEmoji, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = (int) (6 * dp);
        lp.rightMargin = (int) (6 * dp);
        diceFrame.setLayoutParams(lp);
        return diceFrame;
    }

    private void updateTableDisplay() {
        Context ctx = getContext();
        if (ctx == null) return;
        float dp = ctx.getResources().getDisplayMetrics().density;

        // 1. 渲染电脑骰子
        llAiDiceTray.removeAllViews();
        int[] aiDice = game.getAiDice();
        for (int v : aiDice) {
            llAiDiceTray.addView(createDiceView(ctx, dp, v, false));
        }
        tvAiTotalHeader.setText("🤖 电脑骰子  总点数: " + game.getAiTotal() + " 点");

        // 2. 渲染我方骰子
        llPlayerDiceTray.removeAllViews();
        int[] playerDice = game.getPlayerDice();
        for (int v : playerDice) {
            llPlayerDiceTray.addView(createDiceView(ctx, dp, v, true));
        }
        tvPlayerTotalHeader.setText("🧑 我方骰子  总点数: " + game.getPlayerTotal() + " 点");

        // 3. 更新按钮状态
        btnDoubleUp.setEnabled(game.isRoundActive() && !game.isDoubleUp());
        btnDoubleUp.setAlpha((game.isRoundActive() && !game.isDoubleUp()) ? 1.0f : 0.4f);
        btnUndo.setEnabled(game.canUndo());
        btnUndo.setAlpha(game.canUndo() ? 1.0f : 0.4f);

        // 4. 横幅更新
        if (game.isRoundActive()) {
            if (game.isDoubleUp()) {
                tvStatusBanner.setText("⚡ 已开启 2x 双倍积分加注！点击【🏁 决胜结算】");
                tvStatusBanner.setTextColor(0xFFFFB74D);
            } else {
                tvStatusBanner.setText("对决中！点数 " + game.getPlayerTotal() + " vs " + game.getAiTotal() + "，可选择【加倍】或直接【决胜】");
                tvStatusBanner.setTextColor(0xFFFFFFFF);
            }
        }
    }

    // ==================== 游戏交互逻辑 ====================

    private void startNewRound(boolean showToast) {
        game.roll();
        resetTimer();
        updateTableDisplay();
        updateHudStats();

        if (showToast && getContext() != null) {
            Toast.makeText(getContext(), "已掷出全新点数！", Toast.LENGTH_SHORT).show();
        }
    }

    private void onRollClick() {
        startNewRound(true);
    }

    private void onDoubleUpClick() {
        if (!game.isRoundActive() || game.isDoubleUp()) return;
        game.setDoubleUp(true);
        updateTableDisplay();
        Toast.makeText(getContext(), "⚡ 成功加倍！获胜将斩获双倍积分！", Toast.LENGTH_SHORT).show();
    }

    private void onUndoClick() {
        if (!game.canUndo()) return;
        if (game.undoLastRoll()) {
            updateTableDisplay();
            updateHudStats();
            Toast.makeText(getContext(), "↩ 已撤销上一回合摇骰", Toast.LENGTH_SHORT).show();
        }
    }

    private void onResolveClick() {
        if (!game.isRoundActive()) {
            startNewRound(true);
            return;
        }

        stopTimer();
        DiceGame.RoundResult res = game.resolveRound();
        updateTableDisplay();
        updateHudStats();

        String bannerMsg;
        int bannerColor;

        if (res == DiceGame.RoundResult.PLAYER_WIN) {
            bannerMsg = "🎉 我方胜出！" + game.getPlayerTotal() + " 比 " + game.getAiTotal() + " 点！(+积分)";
            bannerColor = 0xFF81C784;
            if (usageStore != null) usageStore.recordWin(GAME_ID);
        } else if (res == DiceGame.RoundResult.AI_WIN) {
            bannerMsg = "🤖 电脑胜出！" + game.getAiTotal() + " 比 " + game.getPlayerTotal() + " 点！";
            bannerColor = 0xFFEF5350;
            if (usageStore != null) usageStore.recordLoss(GAME_ID);
        } else {
            bannerMsg = "🤝 双方点数一致 (" + game.getPlayerTotal() + "点)，战成平局！";
            bannerColor = 0xFFFFF59D;
        }

        tvStatusBanner.setText(bannerMsg);
        tvStatusBanner.setTextColor(bannerColor);

        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, game.getHighScore());
            usageStore.recordPlayTime(GAME_ID, elapsedSeconds * 1000);
        }

        btnNewGame.setText("🔄 下一轮");
    }

    private void showProbabilityHint() {
        String hint = game.getProbabilityHint();
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("💡 骰子胜率与加倍建议")
                .setMessage(hint)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void updateHudStats() {
        int total = game.getPlayerWins() + game.getAiWins() + game.getDraws();
        int winRate = total > 0 ? (game.getPlayerWins() * 100 / total) : 0;
        tvPlayerStats.setText("胜 " + game.getPlayerWins() + " | 负 " + game.getAiWins() + " (" + winRate + "%)");
        tvStreak.setText("🔥 连胜 " + game.getWinStreak() + " (最高 " + game.getMaxStreak() + ")");
        tvAiStats.setText("胜 " + game.getAiWins() + " | 平 " + game.getDraws());
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge == null) return;
        switch (game.getDifficulty()) {
            case DiceGame.DIFFICULTY_MEDIUM:
                tvDifficultyBadge.setText("中等 (3骰) ▾");
                break;
            case DiceGame.DIFFICULTY_HARD:
                tvDifficultyBadge.setText("狂暴 (豹子3倍) ▾");
                break;
            case DiceGame.DIFFICULTY_EASY:
            default:
                tvDifficultyBadge.setText("简单 (2骰) ▾");
                break;
        }
    }

    private void showDifficultyDialog() {
        if (getContext() == null) return;
        String[] options = {
                "简单 (双骰对决，经典 2 颗骰子)",
                "中等 (三骰对决，大点数 3 颗骰子)",
                "困难 (狂暴三骰，豹子 3 倍暴击，失利扣分)"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("选择骰子对决难度")
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
                + "• 双方同时掷骰，比较双方骰子总点数大小。\n"
                + "• 胜利将按自身总点数获得相应积分，连胜更有额外加成！\n\n"
                + "【博弈机制】\n"
                + "• ⚡ 加倍：在对局未结算前加注，若获胜将直接翻倍斩获 2 倍积分！\n"
                + "• 💡 胜率建议：基于两颗/三颗骰子离散均匀分布期望值，指导最优加注策略！\n"
                + "• 豹子暴击：在困难模式下，若摇出全相同点数 (豹子)，可直接激活 3 倍暴击！";

        new AlertDialog.Builder(requireContext())
                .setTitle("骰子对决规则与博弈秘籍")
                .setMessage(rules)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        if (getContext() == null) return;
        String[] items = {"重置本次战绩", "清空最高分纪录"};
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        game = new DiceGame();
                        startNewRound(false);
                        Toast.makeText(getContext(), "战绩已重置", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        if (usageStore != null) usageStore.recordScore(GAME_ID, 0);
                        game.setHighScore(0);
                        updateHudStats();
                        Toast.makeText(getContext(), "最高分已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("返回", null)
                .show();
    }

    private void handleBackPress() {
        if (game.isRoundActive() && game.isDoubleUp()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("当前对局已加倍，退出将放弃本局。确定离开吗？")
                    .setPositiveButton("退出", (dialog, which) -> finishFragment())
                    .setNegativeButton("继续对局", null)
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
            colorTableFelt = 0xFF1A2E22;
            colorTextPrimary = 0xFFEEEEEE;
            colorTextSecondary = 0xFFB0BEC5;
            colorTextMuted = 0xFF78909C;
            colorAccent = 0xFF43A047;
            colorWin = 0xFF2E7D32;
            colorDiceBg = 0xFFFFFFFF;
            colorDiceBorder = 0xFFCFD8DC;
        } else {
            colorBg = 0xFFF5F7FA;
            colorSurface = 0xFFFFFFFF;
            colorTableFelt = 0xFF2E7D32;
            colorTextPrimary = 0xFF212121;
            colorTextSecondary = 0xFF546E7A;
            colorTextMuted = 0xFF90A4AE;
            colorAccent = 0xFF2E7D32;
            colorWin = 0xFF388E3C;
            colorDiceBg = 0xFFFFFFFF;
            colorDiceBorder = 0xFFB0BEC5;
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}

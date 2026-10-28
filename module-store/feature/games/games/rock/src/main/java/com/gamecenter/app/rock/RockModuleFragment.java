package com.gamecenter.app.rock;

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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

import java.util.Locale;

/**
 * 猜拳模块 Fragment（现代化改造版）。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩擂台战场；</li>
 *   <li>顶部标准化导航栏（❮ 返回确认、标题、[难度 ▾] 下拉胶囊、ℹ 规则、⚙ 设置）；</li>
 *   <li>双雄 HUD 对决看板（胜率统计、用时计时器、连胜火焰纪录）；</li>
 *   <li>沉浸式对决擂台（大型手势对决卡、胜负即时动效、出招指引）；</li>
 *   <li>底部高质感手势选择卡（✊ 石头、✌️ 剪刀、🖐 布）与控制胶囊；</li>
 *   <li>难度梯度（简单/中等/困难马尔可夫博弈）与战绩持久化保存。</li>
 * </ul>
 * </p>
 */
public class RockModuleFragment extends Fragment {

    private static final String GAME_ID = "rock";
    private static final String PREF_NAME = "pref_rock";
    private static final String KEY_DIFFICULTY = "rock_difficulty";
    private static final String KEY_PLAYER_WINS = "rock_player_wins";
    private static final String KEY_AI_WINS = "rock_ai_wins";
    private static final String KEY_DRAWS = "rock_draws";
    private static final String KEY_MAX_STREAK = "rock_max_streak";

    private static final String[] DIFFICULTY_NAMES = {"简单", "中等", "困难"};
    private static final String[] DIFFICULTY_DESCS = {
            "简单 (纯随机 · 轻松娱乐)",
            "中等 (惯性分析 · 频率预判)",
            "困难 (马尔可夫博弈 · 心智对抗)"
    };

    private final RockGame game = new RockGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvDiffBadge;
    private TextView tvPlayerStats;
    private TextView tvAiStats;
    private TextView tvTimer;
    private TextView tvStreak;
    private TextView tvBattleStatus;
    private TextView tvPlayerEmoji;
    private TextView tvPlayerLabel;
    private TextView tvAiEmoji;
    private TextView tvAiLabel;
    private LinearLayout battleArena;

    private int currentDifficulty = RockGame.DIFFICULTY_MEDIUM;
    private int totalPlayerWins = 0;
    private int totalAiWins = 0;
    private int totalDraws = 0;
    private int maxStreak = 0;

    private int elapsedSeconds = 0;
    private boolean isTimerRunning = false;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                elapsedSeconds++;
                updateTimerDisplay();
                handler.postDelayed(this, 1000);
            }
        }
    };

    private GameUsageStore usageStore;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentDifficulty = prefs.getInt(KEY_DIFFICULTY, RockGame.DIFFICULTY_MEDIUM);
        totalPlayerWins = prefs.getInt(KEY_PLAYER_WINS, 0);
        totalAiWins = prefs.getInt(KEY_AI_WINS, 0);
        totalDraws = prefs.getInt(KEY_DRAWS, 0);
        maxStreak = prefs.getInt(KEY_MAX_STREAK, 0);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF0F172A : 0xFFFFFBEB);
        root.setPadding((int) (14 * dp), (int) (12 * dp), (int) (14 * dp), (int) (16 * dp));

        // 1. 顶部标准化导航栏
        LinearLayout navBar = buildTopNavBar(ctx, dp, dark);
        root.addView(navBar);

        // 2. 双雄对决 HUD 看板
        LinearLayout hudCard = buildHudCard(ctx, dp, dark);
        LinearLayout.LayoutParams hudLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hudLp.topMargin = (int) (8 * dp);
        hudLp.bottomMargin = (int) (12 * dp);
        root.addView(hudCard, hudLp);

        // 3. 对决擂台核心区 (Battle Arena)
        battleArena = buildBattleArena(ctx, dp, dark);
        LinearLayout.LayoutParams arenaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        arenaLp.bottomMargin = (int) (12 * dp);
        root.addView(battleArena, arenaLp);

        // 4. 手势选择区 (Rock / Scissors / Paper)
        LinearLayout gestureBar = buildGestureBar(ctx, dp, dark);
        LinearLayout.LayoutParams gestureLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gestureLp.bottomMargin = (int) (8 * dp);
        root.addView(gestureBar, gestureLp);

        // 5. 底部控制胶囊 (悔棋 / 重开)
        LinearLayout bottomBar = buildBottomControlBar(ctx, dp, dark);
        root.addView(bottomBar);

        startNewBattle(currentDifficulty);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        usageStore = new GameUsageStore(requireContext());
        if (usageStore != null) {
            usageStore.recordLaunch(GAME_ID);
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        showExitConfirmDialog();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        handler.removeCallbacksAndMessages(null);
    }

    // ==========================================
    // UI 构建辅助函数
    // ==========================================

    private LinearLayout buildTopNavBar(Context ctx, float dp, boolean dark) {
        LinearLayout nav = new LinearLayout(ctx);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));

        TextView btnBack = new TextView(ctx);
        btnBack.setText("❮");
        btnBack.setTextSize(20f);
        btnBack.setTextColor(dark ? 0xFF94A3B8 : 0xFF78350F);
        btnBack.setPadding((int) (8 * dp), (int) (4 * dp), (int) (12 * dp), (int) (4 * dp));
        btnBack.setOnClickListener(v -> showExitConfirmDialog());
        nav.addView(btnBack);

        LinearLayout titleArea = new LinearLayout(ctx);
        titleArea.setOrientation(LinearLayout.HORIZONTAL);
        titleArea.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleArea.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("猜拳");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(dark ? 0xFFF1F5F9 : 0xFF78350F);
        titleArea.addView(tvTitle);

        tvDiffBadge = new TextView(ctx);
        tvDiffBadge.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        tvDiffBadge.setTextSize(12f);
        tvDiffBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvDiffBadge.setTextColor(dark ? 0xFFF59E0B : 0xFFD97706);
        tvDiffBadge.setPadding((int) (8 * dp), (int) (2 * dp), (int) (8 * dp), (int) (2 * dp));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(12 * dp);
        badgeBg.setColor(dark ? 0xFF1E293B : 0xFFFEF3C7);
        badgeBg.setStroke((int) (1 * dp), dark ? 0xFFF59E0B : 0xFFFCD34D);
        tvDiffBadge.setBackground(badgeBg);

        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = (int) (8 * dp);
        tvDiffBadge.setLayoutParams(badgeLp);
        tvDiffBadge.setOnClickListener(v -> showDifficultyDialog());
        titleArea.addView(tvDiffBadge);
        nav.addView(titleArea);

        TextView btnRules = new TextView(ctx);
        btnRules.setText("ℹ");
        btnRules.setTextSize(18f);
        btnRules.setTextColor(dark ? 0xFF94A3B8 : 0xFF78350F);
        btnRules.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        btnRules.setOnClickListener(v -> showRulesDialog());
        nav.addView(btnRules);

        TextView btnSettings = new TextView(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(18f);
        btnSettings.setTextColor(dark ? 0xFF94A3B8 : 0xFF78350F);
        btnSettings.setPadding((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        nav.addView(btnSettings);

        return nav;
    }

    private LinearLayout buildHudCard(Context ctx, float dp, boolean dark) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * dp);
        bg.setColor(dark ? 0xFF1E293B : 0xFFFFFFFF);
        bg.setStroke((int) (1 * dp), dark ? 0xFF334155 : 0xFFFDE68A);
        card.setBackground(bg);

        // 我方
        LinearLayout playerBox = new LinearLayout(ctx);
        playerBox.setOrientation(LinearLayout.VERTICAL);
        playerBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPlayerTitle = new TextView(ctx);
        tvPlayerTitle.setText("🧑 我方");
        tvPlayerTitle.setTextSize(13f);
        tvPlayerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerTitle.setTextColor(dark ? 0xFF34D399 : 0xFF059669);
        playerBox.addView(tvPlayerTitle);

        tvPlayerStats = new TextView(ctx);
        tvPlayerStats.setText("胜 0 | 局 0");
        tvPlayerStats.setTextSize(11f);
        tvPlayerStats.setTextColor(dark ? 0xFF94A3B8 : 0xFF92400E);
        playerBox.addView(tvPlayerStats);
        card.addView(playerBox);

        // 中央：计时与连胜
        LinearLayout centerBox = new LinearLayout(ctx);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);
        centerBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvTimer = new TextView(ctx);
        tvTimer.setText("⏱ 00:00");
        tvTimer.setTextSize(13f);
        tvTimer.setTypeface(Typeface.MONOSPACE);
        tvTimer.setTextColor(dark ? 0xFFF8FAFC : 0xFF78350F);
        centerBox.addView(tvTimer);

        tvStreak = new TextView(ctx);
        tvStreak.setText("🔥 连胜 0 (最高 0)");
        tvStreak.setTextSize(11f);
        tvStreak.setTypeface(Typeface.DEFAULT_BOLD);
        tvStreak.setTextColor(0xFFEA580C);
        centerBox.addView(tvStreak);
        card.addView(centerBox);

        // 电脑
        LinearLayout aiBox = new LinearLayout(ctx);
        aiBox.setOrientation(LinearLayout.VERTICAL);
        aiBox.setGravity(Gravity.END);
        aiBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvAiTitle = new TextView(ctx);
        tvAiTitle.setText("🤖 电脑");
        tvAiTitle.setTextSize(13f);
        tvAiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvAiTitle.setTextColor(dark ? 0xFFF87171 : 0xFFDC2626);
        aiBox.addView(tvAiTitle);

        tvAiStats = new TextView(ctx);
        tvAiStats.setText("胜 0 | 平 0");
        tvAiStats.setTextSize(11f);
        tvAiStats.setTextColor(dark ? 0xFF94A3B8 : 0xFF92400E);
        aiBox.addView(tvAiStats);
        card.addView(aiBox);

        return card;
    }

    private LinearLayout buildBattleArena(Context ctx, float dp, boolean dark) {
        LinearLayout arena = new LinearLayout(ctx);
        arena.setOrientation(LinearLayout.VERTICAL);
        arena.setGravity(Gravity.CENTER);
        arena.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24 * dp);
        bg.setColor(dark ? 0xFF1E293B : 0xFFFFFFFF);
        bg.setStroke((int) (1.5f * dp), dark ? 0xFF334155 : 0xFFFCD34D);
        arena.setBackground(bg);

        // 局势提示
        tvBattleStatus = new TextView(ctx);
        tvBattleStatus.setText("请在下方选择出拳手势");
        tvBattleStatus.setTextSize(16f);
        tvBattleStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvBattleStatus.setTextColor(dark ? 0xFFF59E0B : 0xFFD97706);
        tvBattleStatus.setGravity(Gravity.CENTER);
        tvBattleStatus.setPadding(0, 0, 0, (int) (16 * dp));
        arena.addView(tvBattleStatus);

        // 对决手势区
        LinearLayout handsBox = new LinearLayout(ctx);
        handsBox.setOrientation(LinearLayout.HORIZONTAL);
        handsBox.setGravity(Gravity.CENTER);

        // 玩家手势
        LinearLayout playerHandBox = new LinearLayout(ctx);
        playerHandBox.setOrientation(LinearLayout.VERTICAL);
        playerHandBox.setGravity(Gravity.CENTER);
        playerHandBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tvPlayerEmoji = new TextView(ctx);
        tvPlayerEmoji.setText("❓");
        tvPlayerEmoji.setTextSize(64f);
        tvPlayerEmoji.setGravity(Gravity.CENTER);
        playerHandBox.addView(tvPlayerEmoji);

        tvPlayerLabel = new TextView(ctx);
        tvPlayerLabel.setText("我方出招");
        tvPlayerLabel.setTextSize(13f);
        tvPlayerLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerLabel.setTextColor(dark ? 0xFF34D399 : 0xFF059669);
        playerHandBox.addView(tvPlayerLabel);
        handsBox.addView(playerHandBox);

        // VS 标志
        TextView tvVs = new TextView(ctx);
        tvVs.setText("⚡ VS ⚡");
        tvVs.setTextSize(16f);
        tvVs.setTypeface(Typeface.DEFAULT_BOLD);
        tvVs.setTextColor(dark ? 0xFF64748B : 0xFFB45309);
        tvVs.setGravity(Gravity.CENTER);
        tvVs.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
        handsBox.addView(tvVs);

        // 电脑手势
        LinearLayout aiHandBox = new LinearLayout(ctx);
        aiHandBox.setOrientation(LinearLayout.VERTICAL);
        aiHandBox.setGravity(Gravity.CENTER);
        aiHandBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tvAiEmoji = new TextView(ctx);
        tvAiEmoji.setText("❓");
        tvAiEmoji.setTextSize(64f);
        tvAiEmoji.setGravity(Gravity.CENTER);
        aiHandBox.addView(tvAiEmoji);

        tvAiLabel = new TextView(ctx);
        tvAiLabel.setText("电脑应手");
        tvAiLabel.setTextSize(13f);
        tvAiLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvAiLabel.setTextColor(dark ? 0xFFF87171 : 0xFFDC2626);
        aiHandBox.addView(tvAiLabel);
        handsBox.addView(aiHandBox);

        arena.addView(handsBox);
        return arena;
    }

    private LinearLayout buildGestureBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        bar.addView(createGestureCard(ctx, dp, dark, "✊ 石头", RockGame.ROCK));
        bar.addView(createGestureCard(ctx, dp, dark, "✌️ 剪刀", RockGame.SCISSORS));
        bar.addView(createGestureCard(ctx, dp, dark, "🖐 布", RockGame.PAPER));

        return bar;
    }

    private LinearLayout createGestureCard(Context ctx, float dp, boolean dark, String text, int choice) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * dp);
        bg.setColor(dark ? 0xFF1E293B : 0xFFFFFFFF);
        bg.setStroke((int) (1.5f * dp), dark ? 0xFF334155 : 0xFFFCD34D);
        card.setBackground(bg);

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(dark ? 0xFFF1F5F9 : 0xFF78350F);
        tv.setGravity(Gravity.CENTER);
        card.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        card.setLayoutParams(lp);

        card.setOnClickListener(v -> makeMove(choice));
        return card;
    }

    private LinearLayout buildBottomControlBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        TextView btnUndo = createCapsuleBtn(ctx, dp, dark, "↩ 悔棋", v -> performUndo());
        TextView btnRestart = createCapsuleBtn(ctx, dp, dark, "🔄 新局", v -> startNewBattle(currentDifficulty));

        bar.addView(btnUndo);
        bar.addView(btnRestart);
        return bar;
    }

    private TextView createCapsuleBtn(Context ctx, float dp, boolean dark, String text, View.OnClickListener l) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(dark ? 0xFFE2E8F0 : 0xFF78350F);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding((int) (20 * dp), (int) (8 * dp), (int) (20 * dp), (int) (8 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        bg.setColor(dark ? 0xFF1E293B : 0xFFFEF3C7);
        bg.setStroke((int) (1 * dp), dark ? 0xFF334155 : 0xFFFDE68A);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    // ==========================================
    // 游戏核心流程
    // ==========================================

    private void startNewBattle(int diff) {
        this.currentDifficulty = diff;
        prefs.edit().putInt(KEY_DIFFICULTY, currentDifficulty).apply();
        game.reset(currentDifficulty);

        if (tvDiffBadge != null) {
            tvDiffBadge.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        }
        if (tvPlayerEmoji != null) tvPlayerEmoji.setText("❓");
        if (tvPlayerLabel != null) tvPlayerLabel.setText("我方出招");
        if (tvAiEmoji != null) tvAiEmoji.setText("❓");
        if (tvAiLabel != null) tvAiLabel.setText("电脑应手");
        if (tvBattleStatus != null) tvBattleStatus.setText("请在下方选择出拳手势");

        resetTimer();
        startTimer();
        updateHud();
    }

    private void makeMove(int playerChoice) {
        int[] result = game.play(playerChoice);
        int aiChoice = result[0];
        int outcome = result[1];

        tvPlayerEmoji.setText(RockGame.CHOICE_EMOJI[playerChoice]);
        tvPlayerLabel.setText(RockGame.CHOICE_NAMES[playerChoice]);
        tvAiEmoji.setText(RockGame.CHOICE_EMOJI[aiChoice]);
        tvAiLabel.setText(RockGame.CHOICE_NAMES[aiChoice]);

        if (outcome == RockGame.RESULT_PLAYER_WIN) {
            totalPlayerWins++;
            prefs.edit().putInt(KEY_PLAYER_WINS, totalPlayerWins).apply();
            if (game.getWinStreak() > maxStreak) {
                maxStreak = game.getWinStreak();
                prefs.edit().putInt(KEY_MAX_STREAK, maxStreak).apply();
            }
            if (usageStore != null) {
                usageStore.recordWin(GAME_ID);
                usageStore.recordPlayTime(GAME_ID, elapsedSeconds * 1000L);
            }
            tvBattleStatus.setText(String.format(Locale.getDefault(), "🎉 我方克制对手获胜！(+1分，当前连胜 %d)", game.getWinStreak()));
        } else if (outcome == RockGame.RESULT_PLAYER_LOSE) {
            totalAiWins++;
            prefs.edit().putInt(KEY_AI_WINS, totalAiWins).apply();
            if (usageStore != null) {
                usageStore.recordLoss(GAME_ID);
                usageStore.recordPlayTime(GAME_ID, elapsedSeconds * 1000L);
            }
            tvBattleStatus.setText("💔 电脑看穿了你的出拳！");
        } else {
            totalDraws++;
            prefs.edit().putInt(KEY_DRAWS, totalDraws).apply();
            tvBattleStatus.setText("🤝 心有灵犀，双方打成平手！");
        }

        updateHud();
    }

    private void performUndo() {
        if (!game.canUndo()) {
            Toast.makeText(requireContext(), "当前无法悔棋", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = game.undo();
        if (ok) {
            tvPlayerEmoji.setText("❓");
            tvPlayerLabel.setText("我方出招");
            tvAiEmoji.setText("❓");
            tvAiLabel.setText("电脑应手");
            tvBattleStatus.setText("已撤销上一轮出拳，请重新出招");
            updateHud();
            Toast.makeText(requireContext(), "已悔棋", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHud() {
        if (tvPlayerStats != null) {
            int total = game.getTotalRounds();
            int winRate = total > 0 ? (game.getPlayerWins() * 100 / total) : 0;
            tvPlayerStats.setText(String.format(Locale.getDefault(), "胜 %d | 局 %d (%d%%)", game.getPlayerWins(), total, winRate));
        }
        if (tvAiStats != null) {
            tvAiStats.setText(String.format(Locale.getDefault(), "胜 %d | 平 %d", game.getAiWins(), game.getDraws()));
        }
        if (tvStreak != null) {
            tvStreak.setText(String.format(Locale.getDefault(), "🔥 连胜 %d (最高 %d)", game.getWinStreak(), Math.max(game.getMaxWinStreak(), maxStreak)));
        }
    }

    private void startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            handler.post(timerRunnable);
        }
    }

    private void stopTimer() {
        isTimerRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    private void resetTimer() {
        stopTimer();
        elapsedSeconds = 0;
        updateTimerDisplay();
    }

    private void updateTimerDisplay() {
        if (tvTimer != null) {
            tvTimer.setText(String.format(Locale.getDefault(), "⏱ %02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
        }
    }

    // ==========================================
    // 弹窗与交互
    // ==========================================

    private void showDifficultyDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("选择 AI 难度")
                .setSingleChoiceItems(DIFFICULTY_DESCS, currentDifficulty, (dialog, which) -> {
                    dialog.dismiss();
                    if (which != currentDifficulty) {
                        startNewBattle(which);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRulesDialog() {
        String msg = "【石头剪刀布经典规则】\n"
                + "• 石头克制剪刀，剪刀克制布，布克制石头。\n"
                + "• 双方手势相同则为平局。\n\n"
                + "【难度分级机制】\n"
                + "• 简单：纯随机出招，概率均等，休闲易胜。\n"
                + "• 中等：分析玩家历史出手频率，以 65% 概率执行针对性克制。\n"
                + "• 困难：基于一阶马尔可夫链转移矩阵，深度预测玩家下一步变招趋势并执行反制！";

        new AlertDialog.Builder(requireContext())
                .setTitle("玩法与难度说明")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        String[] items = {"重新开始对局", "选择游戏难度", "游戏规则说明", "退出对局"};
        new AlertDialog.Builder(requireContext())
                .setTitle("⚙ 对局设置")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            startNewBattle(currentDifficulty);
                            break;
                        case 1:
                            showDifficultyDialog();
                            break;
                        case 2:
                            showRulesDialog();
                            break;
                        case 3:
                            showExitConfirmDialog();
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showExitConfirmDialog() {
        if (game.getTotalRounds() == 0) {
            requireActivity().finish();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("退出猜拳")
                .setMessage("确认要退出当前对战吗？")
                .setPositiveButton("退出", (dialog, which) -> requireActivity().finish())
                .setNegativeButton("留在此局", null)
                .show();
    }

    private static boolean isDarkTheme(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}

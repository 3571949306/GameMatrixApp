package com.gamecenter.app.tic;

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
 * 井字棋模块 Fragment（现代化改造版）。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场，彻底去除阻断式冷启动菜单；</li>
 *   <li>顶部标准化导航栏（❮ 返回确认、标题、[难度 ▾] 下拉胶囊、ℹ 规则、⚙ 设置）；</li>
 *   <li>双雄 HUD 对决看板（我方 X vs 电脑 O、棋子与胜场统计、实时局势与计时器）；</li>
 *   <li>底部圆角胶囊控制栏（↩ 悔棋、💡 智能提示、🔄 新局）；</li>
 *   <li>对局结算弹窗（胜负平统计与快速再战）；</li>
 *   <li>难度与战绩持久化保存。</li>
 * </ul>
 * </p>
 */
public class TicTacToeModuleFragment extends Fragment {

    private static final String GAME_ID = "tic";
    private static final String PREF_NAME = "pref_tic";
    private static final String KEY_DIFFICULTY = "tic_difficulty";
    private static final String KEY_PLAYER_WINS = "tic_player_wins";
    private static final String KEY_AI_WINS = "tic_ai_wins";
    private static final String KEY_DRAWS = "tic_draws";

    private static final String[] DIFFICULTY_NAMES = {"简单", "中等", "困难"};
    private static final String[] DIFFICULTY_DESCS = {
            "简单 (基础 · 易上手)",
            "中等 (启发式 · 势均力敌)",
            "困难 (完整 Minimax · 极具挑战)"
    };

    private final TicTacToeGame game = new TicTacToeGame();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TicTacToeView ticTacToeView;
    private TextView tvDiffBadge;
    private TextView tvTimer;
    private TextView tvTurnStatus;
    private TextView tvPlayerStats;
    private TextView tvAiStats;
    private TextView btnUndo;
    private TextView btnHint;
    private TextView btnRestart;

    private int currentDifficulty = 0;
    private int playerWins = 0;
    private int aiWins = 0;
    private int draws = 0;

    private long gameStartTime = 0;
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
        currentDifficulty = prefs.getInt(KEY_DIFFICULTY, 0);
        playerWins = prefs.getInt(KEY_PLAYER_WINS, 0);
        aiWins = prefs.getInt(KEY_AI_WINS, 0);
        draws = prefs.getInt(KEY_DRAWS, 0);

        // 主容器
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF0F172A : 0xFFF8FAFC);
        root.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (16 * dp));

        // 1. 顶部标准化导航栏
        LinearLayout navBar = buildTopNavBar(ctx, dp, dark);
        root.addView(navBar);

        // 2. 双雄对决 HUD 看板
        LinearLayout hudCard = buildHudCard(ctx, dp, dark);
        LinearLayout.LayoutParams hudLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hudLp.topMargin = (int) (8 * dp);
        hudLp.bottomMargin = (int) (8 * dp);
        root.addView(hudCard, hudLp);

        // 3. 核心战场区域 (棋盘)
        FrameLayout boardContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardContainer.setLayoutParams(boardLp);

        ticTacToeView = new TicTacToeView(ctx);
        ticTacToeView.setDarkMode(dark);
        FrameLayout.LayoutParams viewLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        ticTacToeView.setLayoutParams(viewLp);
        ticTacToeView.setOnCellClickListener(this::onCellClick);
        boardContainer.addView(ticTacToeView);

        root.addView(boardContainer);

        // 4. 底部胶囊控制栏
        LinearLayout bottomBar = buildBottomBar(ctx, dp, dark);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = (int) (12 * dp);
        root.addView(bottomBar, barLp);

        // 初始化对局
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

        // ❮ 返回键
        TextView btnBack = new TextView(ctx);
        btnBack.setText("❮");
        btnBack.setTextSize(20f);
        btnBack.setTextColor(dark ? 0xFF94A3B8 : 0xFF475569);
        btnBack.setPadding((int) (8 * dp), (int) (4 * dp), (int) (12 * dp), (int) (4 * dp));
        btnBack.setOnClickListener(v -> showExitConfirmDialog());
        nav.addView(btnBack);

        // 标题与难度胶囊区
        LinearLayout titleArea = new LinearLayout(ctx);
        titleArea.setOrientation(LinearLayout.HORIZONTAL);
        titleArea.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleArea.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("井字棋");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(dark ? 0xFFF1F5F9 : 0xFF0F172A);
        titleArea.addView(tvTitle);

        // [难度 ▾] 下拉胶囊
        tvDiffBadge = new TextView(ctx);
        tvDiffBadge.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        tvDiffBadge.setTextSize(12f);
        tvDiffBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvDiffBadge.setTextColor(dark ? 0xFF38BDF8 : 0xFF0284C7);
        tvDiffBadge.setPadding((int) (8 * dp), (int) (2 * dp), (int) (8 * dp), (int) (2 * dp));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(12 * dp);
        badgeBg.setColor(dark ? 0xFF1E293B : 0xFFE0F2FE);
        badgeBg.setStroke((int) (1 * dp), dark ? 0xFF38BDF8 : 0xFFBAE6FD);
        tvDiffBadge.setBackground(badgeBg);

        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = (int) (8 * dp);
        tvDiffBadge.setLayoutParams(badgeLp);
        tvDiffBadge.setOnClickListener(v -> showDifficultyDialog());
        titleArea.addView(tvDiffBadge);

        nav.addView(titleArea);

        // ℹ 玩法说明
        TextView btnRules = new TextView(ctx);
        btnRules.setText("ℹ");
        btnRules.setTextSize(18f);
        btnRules.setTextColor(dark ? 0xFF94A3B8 : 0xFF475569);
        btnRules.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        btnRules.setOnClickListener(v -> showRulesDialog());
        nav.addView(btnRules);

        // ⚙ 设置按钮
        TextView btnSettings = new TextView(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(18f);
        btnSettings.setTextColor(dark ? 0xFF94A3B8 : 0xFF475569);
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
        bg.setStroke((int) (1 * dp), dark ? 0xFF334155 : 0xFFE2E8F0);
        card.setBackground(bg);

        // 左方：我方 (X)
        LinearLayout playerBox = new LinearLayout(ctx);
        playerBox.setOrientation(LinearLayout.VERTICAL);
        playerBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPlayerTitle = new TextView(ctx);
        tvPlayerTitle.setText("🧑 我方 (X)");
        tvPlayerTitle.setTextSize(13f);
        tvPlayerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerTitle.setTextColor(dark ? 0xFF34D399 : 0xFF059669);
        playerBox.addView(tvPlayerTitle);

        tvPlayerStats = new TextView(ctx);
        tvPlayerStats.setText(String.format(Locale.getDefault(), "胜: %d  子: 0", playerWins));
        tvPlayerStats.setTextSize(11f);
        tvPlayerStats.setTextColor(dark ? 0xFF94A3B8 : 0xFF64748B);
        playerBox.addView(tvPlayerStats);
        card.addView(playerBox);

        // 中央：计时与行动指示
        LinearLayout centerBox = new LinearLayout(ctx);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);
        centerBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvTimer = new TextView(ctx);
        tvTimer.setText("⏱ 00:00");
        tvTimer.setTextSize(13f);
        tvTimer.setTypeface(Typeface.MONOSPACE);
        tvTimer.setTextColor(dark ? 0xFFF8FAFC : 0xFF0F172A);
        centerBox.addView(tvTimer);

        tvTurnStatus = new TextView(ctx);
        tvTurnStatus.setText("🎯 轮到你了");
        tvTurnStatus.setTextSize(11f);
        tvTurnStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvTurnStatus.setTextColor(dark ? 0xFF38BDF8 : 0xFF0284C7);
        centerBox.addView(tvTurnStatus);
        card.addView(centerBox);

        // 右方：电脑 (O)
        LinearLayout aiBox = new LinearLayout(ctx);
        aiBox.setOrientation(LinearLayout.VERTICAL);
        aiBox.setGravity(Gravity.END);
        aiBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvAiTitle = new TextView(ctx);
        tvAiTitle.setText("🤖 电脑 (O)");
        tvAiTitle.setTextSize(13f);
        tvAiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvAiTitle.setTextColor(dark ? 0xFFF87171 : 0xFFDC2626);
        aiBox.addView(tvAiTitle);

        tvAiStats = new TextView(ctx);
        tvAiStats.setText(String.format(Locale.getDefault(), "胜: %d  子: 0", aiWins));
        tvAiStats.setTextSize(11f);
        tvAiStats.setTextColor(dark ? 0xFF94A3B8 : 0xFF64748B);
        aiBox.addView(tvAiStats);
        card.addView(aiBox);

        return card;
    }

    private LinearLayout buildBottomBar(Context ctx, float dp, boolean dark) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        btnUndo = createCapsuleButton(ctx, dp, dark, "↩ 悔棋", v -> performUndo());
        btnHint = createCapsuleButton(ctx, dp, dark, "💡 提示", v -> requestPlayerHint());
        btnRestart = createCapsuleButton(ctx, dp, dark, "🔄 新局", v -> startNewBattle(currentDifficulty));

        bar.addView(btnUndo);
        bar.addView(btnHint);
        bar.addView(btnRestart);

        return bar;
    }

    private TextView createCapsuleButton(Context ctx, float dp, boolean dark, String text, View.OnClickListener listener) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(dark ? 0xFFE2E8F0 : 0xFF1E293B);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * dp);
        bg.setColor(dark ? 0xFF1E293B : 0xFFF1F5F9);
        bg.setStroke((int) (1 * dp), dark ? 0xFF334155 : 0xFFCBD5E1);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    // ==========================================
    // 游戏核心流程
    // ==========================================

    private void startNewBattle(int difficulty) {
        this.currentDifficulty = difficulty;
        prefs.edit().putInt(KEY_DIFFICULTY, currentDifficulty).apply();

        if (tvDiffBadge != null) {
            tvDiffBadge.setText(DIFFICULTY_NAMES[currentDifficulty] + " ▾");
        }

        game.reset(currentDifficulty);
        ticTacToeView.clearBoard();
        ticTacToeView.clearHighlights();
        ticTacToeView.setBoard(game.getBoard());

        resetTimer();
        startTimer();

        updateHud();
        if (tvTurnStatus != null) {
            tvTurnStatus.setText("🎯 轮到你了");
        }
    }

    private void onCellClick(int row, int col) {
        if (game.isGameOver() || !game.isPlayerTurn()) {
            return;
        }

        boolean success = game.placePlayer(row, col);
        if (!success) {
            return;
        }

        ticTacToeView.clearHighlights();
        ticTacToeView.setBoard(game.getBoard());
        ticTacToeView.setLastMove(row, col);
        updateHud();

        if (game.isGameOver()) {
            handleGameOver();
            return;
        }

        // 电脑思考与应手
        tvTurnStatus.setText("🤖 思考中...");
        handler.postDelayed(() -> {
            if (!isAdded() || game.isGameOver()) return;

            int[] aiCell = game.aiMove();
            ticTacToeView.setBoard(game.getBoard());
            if (aiCell != null) {
                ticTacToeView.setLastMove(aiCell[0], aiCell[1]);
            }
            updateHud();

            if (game.isGameOver()) {
                handleGameOver();
            } else {
                tvTurnStatus.setText("🎯 轮到你了");
            }
        }, 300);
    }

    private void performUndo() {
        if (!game.canUndo()) {
            Toast.makeText(requireContext(), "当前无法悔棋", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = game.undo();
        if (ok) {
            ticTacToeView.clearHighlights();
            ticTacToeView.setBoard(game.getBoard());
            int[] last = game.getLastMove();
            if (last != null) {
                ticTacToeView.setLastMove(last[0], last[1]);
            }
            updateHud();
            tvTurnStatus.setText("🎯 轮到你了");
            Toast.makeText(requireContext(), "已悔棋", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPlayerHint() {
        if (game.isGameOver() || !game.isPlayerTurn()) {
            return;
        }
        int[] hint = game.getPlayerHint();
        if (hint != null) {
            ticTacToeView.setHintCell(hint[0], hint[1]);
            tvTurnStatus.setText(String.format(Locale.getDefault(), "💡 推荐落子: 第%d行 第%d列", hint[0] + 1, hint[1] + 1));
        } else {
            Toast.makeText(requireContext(), "当前无可推荐落子", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleGameOver() {
        stopTimer();
        int winner = game.getWinner();
        int[] winLine = game.getWinLine();
        if (winLine != null) {
            ticTacToeView.setWinLine(winLine[0], winLine[1], winLine[2], winLine[3]);
        }

        if (usageStore != null) {
            usageStore.recordPlayTime(GAME_ID, elapsedSeconds * 1000L);
        }

        String title;
        String message;
        if (winner == TicTacToeGame.RESULT_PLAYER_WIN) {
            playerWins++;
            prefs.edit().putInt(KEY_PLAYER_WINS, playerWins).apply();
            if (usageStore != null) {
                usageStore.recordWin(GAME_ID);
            }
            title = "🎉 旗开得胜！";
            message = String.format(Locale.getDefault(), "恭喜获胜！用时 %02d:%02d，共行棋 %d 步。",
                    elapsedSeconds / 60, elapsedSeconds % 60, game.getTotalMoves());
            tvTurnStatus.setText("🏆 我方获胜！");
        } else if (winner == TicTacToeGame.RESULT_AI_WIN) {
            aiWins++;
            prefs.edit().putInt(KEY_AI_WINS, aiWins).apply();
            if (usageStore != null) {
                usageStore.recordLoss(GAME_ID);
            }
            title = "💔 功亏一篑";
            message = String.format(Locale.getDefault(), "电脑获胜。用时 %02d:%02d，再来一局试试看！",
                    elapsedSeconds / 60, elapsedSeconds % 60);
            tvTurnStatus.setText("💥 电脑获胜");
        } else {
            draws++;
            prefs.edit().putInt(KEY_DRAWS, draws).apply();
            title = "🤝 势均力敌";
            message = String.format(Locale.getDefault(), "盘面占满，双方战成平局！用时 %02d:%02d。",
                    elapsedSeconds / 60, elapsedSeconds % 60);
            tvTurnStatus.setText("🤝 和棋平局");
        }
        updateHud();

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("再来一局", (d, w) -> startNewBattle(currentDifficulty))
                .setNegativeButton("切换难度", (d, w) -> showDifficultyDialog())
                .setCancelable(true)
                .show();
    }

    private void updateHud() {
        if (tvPlayerStats != null) {
            tvPlayerStats.setText(String.format(Locale.getDefault(), "胜: %d  子: %d", playerWins, game.getPlayerCount()));
        }
        if (tvAiStats != null) {
            tvAiStats.setText(String.format(Locale.getDefault(), "胜: %d  子: %d", aiWins, game.getAiCount()));
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
                .setTitle("选择游戏难度")
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
        String msg = "【井字棋经典规则】\n"
                + "• 双方轮流在 3x3 棋盘空白格落子，玩家执 X 先手，电脑执 O 后手。\n"
                + "• 任意横线、竖线或对角线连成 3 子的一方即可获胜。\n"
                + "• 棋盘占满无连线即为和棋平局。\n\n"
                + "【难度分级说明】\n"
                + "• 简单：80% 随机落子，适合新手与休闲娱乐。\n"
                + "• 中等：Minimax 深度=2 与启发式算法，具备防守与进攻意识。\n"
                + "• 困难：完整 Minimax 必胜/必和算法与精细开局库，不可战胜！";

        new AlertDialog.Builder(requireContext())
                .setTitle("玩法与规则说明")
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
        if (game.isGameOver() || game.getTotalMoves() == 0) {
            requireActivity().finish();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("退出对局")
                .setMessage("确认要退出当前对局吗？当前对战进度将丢失。")
                .setPositiveButton("退出", (dialog, which) -> requireActivity().finish())
                .setNegativeButton("留在此局", null)
                .show();
    }

    private static boolean isDarkTheme(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}

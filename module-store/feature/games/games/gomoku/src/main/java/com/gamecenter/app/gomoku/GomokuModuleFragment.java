package com.gamecenter.app.gomoku;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
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

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 五子棋人机对战 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 GomokuActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。难度选择与先手选择以代码内按钮实现，
 * 不含成就系统与音效（宿主 R.raw 资源不可用），保留震动反馈、计时、悔棋、提示、
 * 认输、禁手判定等基本游戏功能。</p>
 */
public class GomokuModuleFragment extends Fragment {

    private static final String TAG = "GomokuModuleFragment";
    private static final String GAME_ID = "gomoku";
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {80L, 120L, 170L, 230L};
    private static final int MAX_AI_DIFFICULTY = 4;
    private static final String[] DIFFICULTY_NAMES = {"低", "中", "高", "大师"};

    private GomokuView gomokuView;
    private ScrollView difficultyPanel;
    private LinearLayout controlPanel;
    private TextView tvDifficultyLabel;
    private TextView tvTimer;
    private TextView tvStatus;

    private GomokuGame game;
    private GomokuAI ai;
    private GomokuAI masterAi;

    private int aiPlayer = GomokuGame.WHITE;
    private int playerColor = GomokuGame.BLACK;
    private int aiDifficulty = 2;

    private GameUsageStore usageStore;
    private Handler mainHandler;
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;

    private Vibrator vibrator;
    private boolean vibrateEnabled = true;

    private long gameStartElapsedMs = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerDisplay();
            if (game != null && !game.isGameOver() && difficultyPanel != null
                    && difficultyPanel.getVisibility() == View.GONE) {
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    private Button[] difficultyButtons = new Button[4];
    private Button btnColorBlack;
    private Button btnColorWhite;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int colorBg = isNightMode() ? 0xFF121622 : 0xFFF5F5F5;
        int colorTextPrimary = isNightMode() ? 0xFFE4E6F0 : 0xFF212121;
        int colorTextSecondary = isNightMode() ? 0xFFAAAAAA : 0xFF757575;
        int colorPanelBg = isNightMode() ? 0xFF1E2230 : 0xFFFFFFFF;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));

        // 标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("五子棋");
        tvTitle.setTextSize(24);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 计时与状态栏
        LinearLayout statBar = new LinearLayout(ctx);
        statBar.setOrientation(LinearLayout.HORIZONTAL);
        statBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statBarLp.topMargin = (int) (8 * dp);
        statBar.setLayoutParams(statBarLp);

        tvTimer = new TextView(ctx);
        tvTimer.setTextSize(16);
        tvTimer.setTextColor(colorTextPrimary);
        tvTimer.setText("00:00");
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timerLp.rightMargin = (int) (16 * dp);
        tvTimer.setLayoutParams(timerLp);
        statBar.addView(tvTimer);

        tvStatus = new TextView(ctx);
        tvStatus.setTextSize(16);
        tvStatus.setTextColor(colorTextSecondary);
        tvStatus.setText("请选择难度");
        statBar.addView(tvStatus);
        root.addView(statBar);

        // 游戏容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = (int) (8 * dp);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        gomokuView = new GomokuView(ctx);
        gomokuView.setVisibility(View.GONE);
        gameContainer.addView(gomokuView);

        // 难度选择面板
        difficultyPanel = buildDifficultyPanel(ctx, dp, colorTextPrimary, colorTextSecondary, colorPanelBg);
        root.addView(difficultyPanel);

        // 控制面板
        controlPanel = buildControlPanel(ctx, dp);
        controlPanel.setVisibility(View.GONE);
        root.addView(controlPanel);

        return root;
    }

    private ScrollView buildDifficultyPanel(Context ctx, float dp, int colorPrimary,
                                             int colorSecondary, int panelBg) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = (int) (8 * dp);
        scroll.setLayoutParams(scrollLp);

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));
        panel.setBackgroundColor(panelBg);

        // 难度标签
        tvDifficultyLabel = new TextView(ctx);
        tvDifficultyLabel.setTextSize(16);
        tvDifficultyLabel.setTextColor(colorPrimary);
        tvDifficultyLabel.setGravity(Gravity.CENTER);
        panel.addView(tvDifficultyLabel);

        // 难度按钮
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.topMargin = (int) (12 * dp);
        diffBar.setLayoutParams(diffBarLp);

        for (int i = 0; i < 4; i++) {
            final int difficulty = i + 1;
            Button btn = new Button(ctx);
            btn.setText(DIFFICULTY_NAMES[i]);
            btn.setTextSize(12);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
            btn.setLayoutParams(btnLp);
            btn.setOnClickListener(v -> selectDifficulty(difficulty));
            difficultyButtons[i] = btn;
            diffBar.addView(btn);
        }
        panel.addView(diffBar);

        // 先手选择
        TextView tvColorLabel = new TextView(ctx);
        tvColorLabel.setText("先手选择");
        tvColorLabel.setTextSize(14);
        tvColorLabel.setTextColor(colorSecondary);
        tvColorLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams colorLabelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colorLabelLp.topMargin = (int) (16 * dp);
        tvColorLabel.setLayoutParams(colorLabelLp);
        panel.addView(tvColorLabel);

        LinearLayout colorBar = new LinearLayout(ctx);
        colorBar.setOrientation(LinearLayout.HORIZONTAL);
        colorBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams colorBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colorBarLp.topMargin = (int) (8 * dp);
        colorBar.setLayoutParams(colorBarLp);

        btnColorBlack = new Button(ctx);
        btnColorBlack.setText("执黑（先手）");
        LinearLayout.LayoutParams blackLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blackLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnColorBlack.setLayoutParams(blackLp);
        btnColorBlack.setOnClickListener(v -> selectPlayerColor(GomokuGame.BLACK));
        colorBar.addView(btnColorBlack);

        btnColorWhite = new Button(ctx);
        btnColorWhite.setText("执白（后手）");
        LinearLayout.LayoutParams whiteLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        whiteLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnColorWhite.setLayoutParams(whiteLp);
        btnColorWhite.setOnClickListener(v -> selectPlayerColor(GomokuGame.WHITE));
        colorBar.addView(btnColorWhite);
        panel.addView(colorBar);

        // 开始按钮
        Button btnStart = new Button(ctx);
        btnStart.setText("开始对局");
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.topMargin = (int) (20 * dp);
        btnStart.setLayoutParams(startLp);
        btnStart.setOnClickListener(v -> startGame(aiDifficulty));
        panel.addView(btnStart);

        // 说明文字
        TextView tvHint = new TextView(ctx);
        tvHint.setText("提示：黑方禁手规则启用（三三、四四、长连）。大师难度提供更智能的攻防。");
        tvHint.setTextSize(12);
        tvHint.setTextColor(colorSecondary);
        tvHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = (int) (12 * dp);
        tvHint.setLayoutParams(hintLp);
        panel.addView(tvHint);

        scroll.addView(panel);
        return scroll;
    }

    private LinearLayout buildControlPanel(Context ctx, float dp) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelLp.topMargin = (int) (8 * dp);
        panel.setLayoutParams(panelLp);

        Button btnUndo = new Button(ctx);
        btnUndo.setText("悔棋");
        btnUndo.setTextSize(12);
        LinearLayout.LayoutParams undoLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        undoLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnUndo.setLayoutParams(undoLp);
        btnUndo.setOnClickListener(v -> handleUndo());
        panel.addView(btnUndo);

        Button btnHint = new Button(ctx);
        btnHint.setText("提示");
        btnHint.setTextSize(12);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        hintLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnHint.setLayoutParams(hintLp);
        btnHint.setOnClickListener(v -> handleHint());
        panel.addView(btnHint);

        Button btnRestart = new Button(ctx);
        btnRestart.setText("重开");
        btnRestart.setTextSize(12);
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        restartLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> handleRestart());
        panel.addView(btnRestart);

        Button btnResign = new Button(ctx);
        btnResign.setText("认输");
        btnResign.setTextSize(12);
        LinearLayout.LayoutParams resignLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        resignLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btnResign.setLayoutParams(resignLp);
        btnResign.setOnClickListener(v -> handleResign());
        panel.addView(btnResign);

        return panel;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();

        mainHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        game = new GomokuGame();
        ai = new GomokuAI(aiDifficulty);
        masterAi = new GomokuAI(4);
        gomokuView.setGame(game);
        gomokuView.setInteractive(false);
        gomokuView.setOnCellClickListener(this::handleCellClick);
        gomokuView.setOnGameOverListener(this::handleGameOver);

        usageStore = new GameUsageStore(ctx);
        initVibrator();

        selectDifficulty(aiDifficulty);
        selectPlayerColor(playerColor);
    }

    private void initVibrator() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) requireContext()
                        .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Exception e) {
            vibrateEnabled = false;
        }
    }

    private void vibrateLight() {
        if (!vibrateEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(20);
            }
        } catch (Exception e) {
            Log.w(TAG, "震动反馈失败", e);
        }
    }

    private void selectDifficulty(int difficulty) {
        aiDifficulty = Math.max(1, Math.min(difficulty, MAX_AI_DIFFICULTY));
        if (tvDifficultyLabel != null) {
            tvDifficultyLabel.setText("难度：" + DIFFICULTY_NAMES[aiDifficulty - 1]
                    + " (" + aiDifficulty + "/" + MAX_AI_DIFFICULTY + ")");
        }
        int active = 0xFF3949AB;
        int inactive = isNightMode() ? 0xFF333A4D : 0xFFE0E0E0;
        for (int i = 0; i < difficultyButtons.length; i++) {
            if (difficultyButtons[i] != null) {
                difficultyButtons[i].setBackgroundColor(i + 1 == aiDifficulty ? active : inactive);
                difficultyButtons[i].setTextColor(android.graphics.Color.WHITE);
            }
        }
    }

    private void selectPlayerColor(int color) {
        playerColor = color;
        aiPlayer = (color == GomokuGame.BLACK) ? GomokuGame.WHITE : GomokuGame.BLACK;
        int active = 0xFF3949AB;
        int inactive = isNightMode() ? 0xFF333A4D : 0xFFE0E0E0;
        if (btnColorBlack != null) {
            btnColorBlack.setBackgroundColor(color == GomokuGame.BLACK ? active : inactive);
            btnColorBlack.setTextColor(android.graphics.Color.WHITE);
        }
        if (btnColorWhite != null) {
            btnColorWhite.setBackgroundColor(color == GomokuGame.WHITE ? active : inactive);
            btnColorWhite.setTextColor(android.graphics.Color.WHITE);
        }
    }

    private void startGame(int difficulty) {
        aiDifficulty = difficulty;
        ai = new GomokuAI(difficulty);
        game.reset();
        gomokuView.setGame(game);
        gomokuView.setInteractive(true);
        gomokuView.setVisibility(View.VISIBLE);
        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        tvStatus.setText("你的回合");
        gameStartElapsedMs = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);
        gomokuView.invalidate();

        if (playerColor == GomokuGame.WHITE) {
            triggerAiMove();
        }
    }

    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        if (game.getCurrentPlayer() != playerColor) return;
        if (aiThinking) return;
        if (!game.isValidMove(x, y)) return;
        if (playerColor == GomokuGame.BLACK && game.isForbiddenMovesEnabled()
                && game.isForbiddenMove(x, y)) {
            Toast.makeText(requireContext(), forbiddenToastText(game.getForbiddenType(x, y)),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        gomokuView.clearHint();
        game.makeMove(x, y, playerColor);
        game.switchPlayer();
        gomokuView.animateLastMove();
        vibrateLight();
        if (game.checkGameOver()) {
            gomokuView.invalidate();
            stopTimer();
            return;
        }
        triggerAiMove();
    }

    private void triggerAiMove() {
        aiThinking = true;
        gomokuView.setAiThinking(true);
        gomokuView.clearHover();
        gomokuView.invalidate();
        final long currentGen = aiGeneration;
        final long startMs = System.currentTimeMillis();
        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(game, aiPlayer);
            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(getAiMinResponseDelayMs() - elapsed, 0L);
            Runnable applyMove = () -> {
                if (currentGen != aiGeneration) return;
                if (bestMove != null) {
                    if (aiPlayer == GomokuGame.BLACK && game.isForbiddenMovesEnabled()
                            && game.isForbiddenMove(bestMove[0], bestMove[1])) {
                        game.setGameOver(playerColor);
                        gomokuView.invalidate();
                        stopTimer();
                        return;
                    }
                    game.makeMove(bestMove[0], bestMove[1], aiPlayer);
                    game.switchPlayer();
                    game.checkGameOver();
                    gomokuView.animateLastMove();
                    vibrateLight();
                }
                aiThinking = false;
                gomokuView.setAiThinking(false);
                if (game.isGameOver()) {
                    stopTimer();
                }
                gomokuView.invalidate();
            };
            if (delay > 0L) {
                mainHandler.postDelayed(applyMove, delay);
            } else {
                mainHandler.post(applyMove);
            }
        });
    }

    private long getAiMinResponseDelayMs() {
        int idx = Math.max(0, Math.min(aiDifficulty - 1, AI_MIN_RESPONSE_DELAYS_MS.length - 1));
        return AI_MIN_RESPONSE_DELAYS_MS[idx];
    }

    private String forbiddenToastText(GomokuGame.ForbiddenType type) {
        if (type == null) return "禁手";
        switch (type) {
            case THREE_THREE: return "三三禁手";
            case FOUR_FOUR: return "四四禁手";
            case OVERLINE: return "长连禁手";
            default: return "禁手";
        }
    }

    private void handleUndo() {
        if (aiThinking) return;
        int undoCount = game.undoLastMoves(1);
        if (undoCount > 0) {
            gomokuView.clearHint();
            gomokuView.invalidate();
            tvStatus.setText("你的回合");
        }
    }

    private void handleHint() {
        if (game.isGameOver() || aiThinking || game.getCurrentPlayer() != playerColor) return;
        gomokuView.clearHint();
        final long currentGen = aiGeneration;
        tvStatus.setText("大师思考中…");
        aiExecutor.execute(() -> {
            int[] hint = masterAi.getBestMove(game, playerColor);
            if (hint != null) {
                String analysis = masterAi.getEducationalAnalysis(game, hint[0], hint[1], playerColor);
                mainHandler.post(() -> {
                    if (currentGen != aiGeneration) return;
                    if (!game.isGameOver() && game.getCurrentPlayer() == playerColor) {
                        gomokuView.showHint(hint[0], hint[1]);
                        char colLabel = (char) ('A' + hint[0]);
                        int rowLabel = 15 - hint[1];
                        tvStatus.setText("💡 " + colLabel + rowLabel);
                        Toast.makeText(requireContext(),
                                "大师建议: " + colLabel + rowLabel + "\n" + analysis,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void handleResign() {
        if (game.isGameOver() || aiThinking) return;
        game.setGameOver(aiPlayer);
        stopTimer();
        gomokuView.invalidate();
        tvStatus.setText("你已认输");
    }

    private void handleRestart() {
        aiGeneration++;
        aiThinking = false;
        gomokuView.setAiThinking(false);
        game.reset();
        gomokuView.clearHover();
        gomokuView.clearHint();
        gomokuView.setInteractive(false);
        gomokuView.setVisibility(View.GONE);
        gomokuView.setGame(game);
        stopTimer();
        if (tvTimer != null) {
            tvTimer.setText("00:00");
        }
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        tvStatus.setText("请选择难度");
        gomokuView.invalidate();
    }

    private void updateTimerDisplay() {
        if (tvTimer == null || gameStartElapsedMs == 0L) return;
        long elapsedSec = (SystemClock.elapsedRealtime() - gameStartElapsedMs) / 1000;
        long min = elapsedSec / 60;
        long sec = elapsedSec % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void handleGameOver(Integer winner) {
        stopTimer();
        if (winner != null && winner == playerColor) {
            tvStatus.setText("你赢了！");
            if (usageStore != null) usageStore.recordWin(GAME_ID);
        } else if (winner != null && winner == aiPlayer) {
            tvStatus.setText("AI获胜");
            if (usageStore != null) usageStore.recordLoss(GAME_ID);
        } else {
            tvStatus.setText("平局");
        }
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        stopTimer();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}

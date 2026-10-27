package com.gamecenter.app.gomoku;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
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
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
 * 现代高品质五子棋模块 Fragment。
 * <p>
 * 核心升级：
 * <ul>
 *   <li><b>多模式支持</b>：智能人机对战（4级AI）与双人同屏对战自由切换</li>
 *   <li><b>现代对弈看板 (Player HUD)</b>：黑白双雄头像、用时计时器、落子手数、实时行动呼吸指示</li>
 *   <li><b>专业规则与辅助</b>：竞技禁手开关、落子十字瞄准准星、棋子记谱手数显示、大师建议提示</li>
 *   <li><b>清脆落子音效与震动</b>：音效与触觉双重反馈，还原实体木盘落子叩击质感</li>
 *   <li><b>结算荣誉卡片</b>：对局结束典雅结算弹窗，支持一键洗盘重开与复盘</li>
 * </ul>
 */
public class GomokuModuleFragment extends Fragment {

    private static final String TAG = "GomokuModuleFragment";
    private static final String GAME_ID = "gomoku";

    public static final int MODE_AI = 0;
    public static final int MODE_PVP = 1;

    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {80L, 120L, 170L, 230L};
    private static final int MAX_AI_DIFFICULTY = 4;
    private static final String[] DIFFICULTY_NAMES = {"入门", "进阶", "挑战", "大师"};

    private GomokuView gomokuView;
    private ScrollView setupPanel;
    private LinearLayout gameContentLayout;
    private LinearLayout hudCardLayout;

    // HUD 控件
    private TextView tvBlackName;
    private TextView tvBlackStatus;
    private LinearLayout blackPlayerBox;
    private TextView tvWhiteName;
    private TextView tvWhiteStatus;
    private LinearLayout whitePlayerBox;
    private TextView tvCenterTimer;
    private TextView tvCenterMoves;
    private TextView tvModeBadge;

    // 底部控制按钮
    private LinearLayout btnUndo;
    private LinearLayout btnHint;
    private LinearLayout btnStepToggle;
    private TextView tvStepToggleLabel;
    private LinearLayout btnRestart;

    // 游戏状态
    private GomokuGame game;
    private GomokuAI ai;
    private GomokuAI masterAi;

    private int gameMode = MODE_AI;
    private int aiPlayer = GomokuGame.WHITE;
    private int playerColor = GomokuGame.BLACK;
    private int aiDifficulty = 3; // 默认挑战难度

    private boolean showStepNumbers = false;
    private boolean crosshairEnabled = true;
    private boolean soundEnabled = true;
    private boolean forbiddenMovesEnabled = true;

    private GameUsageStore usageStore;
    private Handler mainHandler;
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;

    private Vibrator vibrator;
    private boolean vibrateEnabled = true;
    private AudioManager audioManager;
    private AlertDialog victoryDialog;

    private long gameStartElapsedMs = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerDisplay();
            if (game != null && !game.isGameOver() && gameContentLayout != null
                    && gameContentLayout.getVisibility() == View.VISIBLE) {
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        boolean night = isNightMode();
        int colorBg = night ? 0xFF14171F : 0xFFF7F8FA;
        int colorTextPrimary = night ? 0xFFEAECEF : 0xFF212529;
        int colorTextSecondary = night ? 0xFF9E9E9E : 0xFF757575;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);

        // 1. 顶部标准导航栏
        View topBar = buildTopAppBar(ctx, dp, colorTextPrimary, night);
        root.addView(topBar);

        // 2. 主体游戏对战界面容器
        gameContentLayout = new LinearLayout(ctx);
        gameContentLayout.setOrientation(LinearLayout.VERTICAL);
        gameContentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // 2.1 对弈双方信息看板 (Player HUD)
        View hud = buildPlayerHud(ctx, dp, night);
        gameContentLayout.addView(hud);

        // 2.2 棋盘自适应居中容器
        FrameLayout boardContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));
        boardContainer.setLayoutParams(boardLp);

        gomokuView = new GomokuView(ctx);
        gomokuView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        boardContainer.addView(gomokuView);
        gameContentLayout.addView(boardContainer);

        // 2.3 底部现代化圆角胶囊操作栏
        View controlBar = buildModernControlBar(ctx, dp, night);
        gameContentLayout.addView(controlBar);

        root.addView(gameContentLayout);

        // 3. 开局对战设置面板（初次或重置时可调）
        setupPanel = buildSetupPanel(ctx, dp, night);
        setupPanel.setVisibility(View.GONE);
        root.addView(setupPanel);

        return root;
    }

    /**
     * 顶部标准导航栏：带返回按键、标题、模式徽标、设置按键
     */
    private View buildTopAppBar(Context ctx, float dp, int textPrimary, boolean night) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * dp);
        int padV = (int) (10 * dp);
        bar.setPadding(padH, padV, padH, padV);

        // 返回按钮
        TextView btnBack = new TextView(ctx);
        btnBack.setText("❮");
        btnBack.setTextSize(18);
        btnBack.setTextColor(textPrimary);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setBackground(createCircleRippleDrawable(night ? 0x22FFFFFF : 0x11000000, 36 * dp));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> handleBackPress());
        bar.addView(btnBack);

        // 标题与副标
        LinearLayout titleBox = new LinearLayout(ctx);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleBox.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("五子棋");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        titleBox.addView(tvTitle);

        tvModeBadge = new TextView(ctx);
        tvModeBadge.setText("人机对战 · " + DIFFICULTY_NAMES[aiDifficulty - 1]);
        tvModeBadge.setTextSize(11);
        tvModeBadge.setTextColor(night ? 0xFF81D4FA : 0xFF0288D1);
        tvModeBadge.setGravity(Gravity.CENTER);
        titleBox.addView(tvModeBadge);

        bar.addView(titleBox);

        // 设置按钮
        TextView btnSettings = new TextView(ctx);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(20);
        btnSettings.setTextColor(textPrimary);
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setBackground(createCircleRippleDrawable(night ? 0x22FFFFFF : 0x11000000, 36 * dp));
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        btnSettings.setLayoutParams(setLp);
        btnSettings.setOnClickListener(v -> showGameSettingsDialog());
        bar.addView(btnSettings);

        return bar;
    }

    /**
     * 对弈双方信息看板 (Player HUD Card)
     */
    private View buildPlayerHud(Context ctx, float dp, boolean night) {
        hudCardLayout = new LinearLayout(ctx);
        hudCardLayout.setOrientation(LinearLayout.HORIZONTAL);
        hudCardLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hudLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hudLp.setMargins((int) (14 * dp), (int) (2 * dp), (int) (14 * dp), (int) (4 * dp));
        hudCardLayout.setLayoutParams(hudLp);
        hudCardLayout.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));

        // 现代微边框圆角背景
        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setCornerRadius(14 * dp);
        hudBg.setColor(night ? 0xFF1C2230 : 0xFFFFFFFF);
        hudBg.setStroke((int) (1 * dp), night ? 0xFF2D3548 : 0xFFE0E0E0);
        hudCardLayout.setBackground(hudBg);

        // 黑方选手
        blackPlayerBox = buildPlayerBox(ctx, dp, true, night);
        hudCardLayout.addView(blackPlayerBox);

        // 中间用时与局势
        LinearLayout centerBox = new LinearLayout(ctx);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        centerBox.setLayoutParams(centerLp);

        tvCenterTimer = new TextView(ctx);
        tvCenterTimer.setText("00:00");
        tvCenterTimer.setTextSize(16);
        tvCenterTimer.setTypeface(Typeface.DEFAULT_BOLD);
        tvCenterTimer.setTextColor(night ? 0xFFFFFFFF : 0xFF212121);
        tvCenterTimer.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterTimer);

        tvCenterMoves = new TextView(ctx);
        tvCenterMoves.setText("第 0 手");
        tvCenterMoves.setTextSize(11);
        tvCenterMoves.setTextColor(night ? 0xFFB0BEC5 : 0xFF78909C);
        tvCenterMoves.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterMoves);

        hudCardLayout.addView(centerBox);

        // 白方选手
        whitePlayerBox = buildPlayerBox(ctx, dp, false, night);
        hudCardLayout.addView(whitePlayerBox);

        return hudCardLayout;
    }

    private LinearLayout buildPlayerBox(Context ctx, float dp, boolean isBlack, boolean night) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
        box.setLayoutParams(lp);
        box.setPadding((int) (6 * dp), (int) (4 * dp), (int) (6 * dp), (int) (4 * dp));

        // 棋子微图标
        View pieceIcon = new View(ctx);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) (22 * dp), (int) (22 * dp));
        iconLp.setMargins(0, 0, (int) (8 * dp), 0);
        pieceIcon.setLayoutParams(iconLp);
        GradientDrawable pDrawable = new GradientDrawable();
        pDrawable.setShape(GradientDrawable.OVAL);
        if (isBlack) {
            pDrawable.setColor(0xFF263238);
            pDrawable.setStroke((int) (1.5f * dp), 0xFF78909C);
        } else {
            pDrawable.setColor(0xFFFFFFFF);
            pDrawable.setStroke((int) (1.5f * dp), 0xFFCFD8DC);
        }
        pieceIcon.setBackground(pDrawable);
        box.addView(pieceIcon);

        LinearLayout textBox = new LinearLayout(ctx);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(ctx);
        tvName.setText(isBlack ? "黑方 (我)" : "白方 (AI)");
        tvName.setTextSize(13);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setTextColor(night ? 0xFFECEFF1 : 0xFF263238);
        textBox.addView(tvName);

        TextView tvStatus = new TextView(ctx);
        tvStatus.setText(isBlack ? "先行" : "后手");
        tvStatus.setTextSize(10.5f);
        tvStatus.setTextColor(night ? 0xFF90A4AE : 0xFF78909C);
        textBox.addView(tvStatus);

        box.addView(textBox);

        if (isBlack) {
            tvBlackName = tvName;
            tvBlackStatus = tvStatus;
        } else {
            tvWhiteName = tvName;
            tvWhiteStatus = tvStatus;
        }
        return box;
    }

    /**
     * 底部现代化操作栏：悔棋、提示、手数切换、重开
     */
    private View buildModernControlBar(Context ctx, float dp, boolean night) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins((int) (14 * dp), (int) (4 * dp), (int) (14 * dp), (int) (12 * dp));
        bar.setLayoutParams(barLp);

        btnUndo = createControlTile(ctx, dp, "↩", "悔棋", night, v -> handleUndo());
        bar.addView(btnUndo);

        btnHint = createControlTile(ctx, dp, "💡", "提示", night, v -> handleHint());
        bar.addView(btnHint);

        btnStepToggle = createControlTile(ctx, dp, "🔢", "手数", night, v -> toggleStepNumbers());
        tvStepToggleLabel = (TextView) btnStepToggle.getChildAt(1);
        bar.addView(btnStepToggle);

        btnRestart = createControlTile(ctx, dp, "🔄", "重开", night, v -> confirmRestart());
        bar.addView(btnRestart);

        return bar;
    }

    private LinearLayout createControlTile(Context ctx, float dp, String icon, String label,
                                           boolean night, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (52 * dp), 1f);
        lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        tile.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * dp);
        bg.setColor(night ? 0xFF1E2536 : 0xFFFFFFFF);
        bg.setStroke((int) (1 * dp), night ? 0xFF2D384E : 0xFFE0E0E0);
        tile.setBackground(bg);
        tile.setOnClickListener(listener);

        TextView tvIcon = new TextView(ctx);
        tvIcon.setText(icon);
        tvIcon.setTextSize(16);
        tvIcon.setGravity(Gravity.CENTER);
        tile.addView(tvIcon);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextSize(11);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvLabel.setTextColor(night ? 0xFFCFD8DC : 0xFF455A64);
        tvLabel.setGravity(Gravity.CENTER);
        tile.addView(tvLabel);

        return tile;
    }

    /**
     * 开局与对战设置面板
     */
    private ScrollView buildSetupPanel(Context ctx, float dp, boolean night) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (20 * dp), (int) (20 * dp), (int) (20 * dp), (int) (20 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("对局模式设置");
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(night ? 0xFFECEFF1 : 0xFF212121);
        panel.addView(tvTitle);

        // 模式选择按键
        panel.addView(createSectionTitle(ctx, "对战模式", dp, night));
        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnModeAi = createOptionButton(ctx, "人机对战", gameMode == MODE_AI, dp, night);
        Button btnModePvp = createOptionButton(ctx, "双人同屏", gameMode == MODE_PVP, dp, night);
        btnModeAi.setOnClickListener(v -> {
            gameMode = MODE_AI;
            btnModeAi.setSelected(true);
            btnModePvp.setSelected(false);
            refreshSetupUi(btnModeAi, btnModePvp, night);
        });
        btnModePvp.setOnClickListener(v -> {
            gameMode = MODE_PVP;
            btnModePvp.setSelected(true);
            btnModeAi.setSelected(false);
            refreshSetupUi(btnModeAi, btnModePvp, night);
        });
        modeRow.addView(btnModeAi);
        modeRow.addView(btnModePvp);
        panel.addView(modeRow);

        scroll.addView(panel);
        return scroll;
    }

    private TextView createSectionTitle(Context ctx, String text, float dp, boolean night) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(night ? 0xFF90A4AE : 0xFF78909C);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) (14 * dp), 0, (int) (6 * dp));
        tv.setLayoutParams(lp);
        return tv;
    }

    private Button createOptionButton(Context ctx, String text, boolean selected, float dp, boolean night) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f);
        lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
        btn.setLayoutParams(lp);
        updateButtonStyle(btn, selected, dp, night);
        return btn;
    }

    private void updateButtonStyle(Button btn, boolean selected, float dp, boolean night) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(10 * dp);
        if (selected) {
            gd.setColor(night ? 0xFF1976D2 : 0xFF2196F3);
            btn.setTextColor(Color.WHITE);
        } else {
            gd.setColor(night ? 0xFF263238 : 0xFFECEFF1);
            btn.setTextColor(night ? 0xFFB0BEC5 : 0xFF455A64);
        }
        btn.setBackground(gd);
    }

    private void refreshSetupUi(Button btn1, Button btn2, boolean night) {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        updateButtonStyle(btn1, btn1.isSelected(), dp, night);
        updateButtonStyle(btn2, btn2.isSelected(), dp, night);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();

        mainHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();
        audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        game = new GomokuGame();
        game.setForbiddenMovesEnabled(forbiddenMovesEnabled);
        ai = new GomokuAI(aiDifficulty);
        masterAi = new GomokuAI(4);

        gomokuView.setGame(game);
        gomokuView.setInteractive(true);
        gomokuView.setShowStepNumbers(showStepNumbers);
        gomokuView.setCrosshairEnabled(crosshairEnabled);
        gomokuView.setOnCellClickListener(this::handleCellClick);
        gomokuView.setOnGameOverListener(this::handleGameOver);

        usageStore = new GameUsageStore(ctx);
        initVibrator();

        startGame();
    }

    private void startGame() {
        if (victoryDialog != null && victoryDialog.isShowing()) {
            victoryDialog.dismiss();
        }
        victoryDialog = null;

        game.reset();
        game.setForbiddenMovesEnabled(forbiddenMovesEnabled);
        gomokuView.setGame(game);
        gomokuView.setInteractive(true);
        gomokuView.clearHover();
        gomokuView.clearHint();

        gameStartElapsedMs = SystemClock.elapsedRealtime();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        updateHudDisplay();
        gomokuView.invalidate();

        if (gameMode == MODE_AI && playerColor == GomokuGame.WHITE) {
            triggerAiMove();
        }
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

    private void playPieceSound() {
        if (!soundEnabled) return;
        try {
            if (audioManager != null) {
                audioManager.playSoundEffect(SoundEffectConstants.CLICK, 1.0f);
            }
        } catch (Throwable ignore) {}
    }

    private void vibrateLight() {
        if (!vibrateEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(22, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(22);
            }
        } catch (Exception e) {
            Log.w(TAG, "震动反馈失败", e);
        }
    }

    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        if (aiThinking && gameMode == MODE_AI) return;

        int current = game.getCurrentPlayer();
        if (gameMode == MODE_AI && current != playerColor) return;
        if (!game.isValidMove(x, y)) return;

        // 禁手检测（仅黑方）
        if (current == GomokuGame.BLACK && game.isForbiddenMovesEnabled()
                && game.isForbiddenMove(x, y)) {
            GomokuGame.ForbiddenType type = game.getForbiddenType(x, y);
            Toast.makeText(requireContext(), "⚠️ 规则提示：" + forbiddenToastText(type) + "，无法落子！",
                    Toast.LENGTH_SHORT).show();
            vibrateLight();
            return;
        }

        gomokuView.clearHint();
        game.makeMove(x, y, current);
        game.switchPlayer();
        gomokuView.animateLastMove();
        playPieceSound();
        vibrateLight();

        boolean isOver = game.checkGameOver();
        updateHudDisplay();

        if (isOver) {
            gomokuView.invalidate();
            stopTimer();
            handleGameOver(game.getWinner());
            return;
        }

        if (gameMode == MODE_AI) {
            triggerAiMove();
        }
    }

    private void triggerAiMove() {
        aiThinking = true;
        gomokuView.setAiThinking(true);
        gomokuView.clearHover();
        updateHudDisplay();
        gomokuView.invalidate();

        final long currentGen = aiGeneration;
        final long startMs = System.currentTimeMillis();

        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(game, aiPlayer);
            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(getAiMinResponseDelayMs() - elapsed, 0L);

            Runnable applyMove = () -> {
                if (currentGen != aiGeneration) return;
                if (bestMove != null && !game.isGameOver()) {
                    if (aiPlayer == GomokuGame.BLACK && game.isForbiddenMovesEnabled()
                            && game.isForbiddenMove(bestMove[0], bestMove[1])) {
                        game.setGameOver(playerColor);
                        gomokuView.invalidate();
                        stopTimer();
                        updateHudDisplay();
                        return;
                    }
                    game.makeMove(bestMove[0], bestMove[1], aiPlayer);
                    game.switchPlayer();
                    game.checkGameOver();
                    gomokuView.animateLastMove();
                    playPieceSound();
                    vibrateLight();
                }
                aiThinking = false;
                gomokuView.setAiThinking(false);
                updateHudDisplay();
                if (game.isGameOver()) {
                    stopTimer();
                    handleGameOver(game.getWinner());
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

    private void updateHudDisplay() {
        if (tvCenterMoves != null && game != null) {
            tvCenterMoves.setText("第 " + game.getMoveCount() + " 手");
        }

        boolean night = isNightMode();
        float dp = requireContext().getResources().getDisplayMetrics().density;

        int activeColor = night ? 0xFF00E676 : 0xFF2E7D32;
        int activeBg = night ? 0x2200E676 : 0x184CAF50;
        int defaultBg = Color.TRANSPARENT;

        int currentPlayer = game != null ? game.getCurrentPlayer() : GomokuGame.BLACK;

        // 黑方状态
        if (blackPlayerBox != null && whitePlayerBox != null) {
            boolean isBlackTurn = (currentPlayer == GomokuGame.BLACK && !game.isGameOver());
            boolean isWhiteTurn = (currentPlayer == GomokuGame.WHITE && !game.isGameOver());

            blackPlayerBox.setBackground(createBoxBackground(isBlackTurn ? activeBg : defaultBg,
                    isBlackTurn ? activeColor : Color.TRANSPARENT, dp));
            whitePlayerBox.setBackground(createBoxBackground(isWhiteTurn ? activeBg : defaultBg,
                    isWhiteTurn ? activeColor : Color.TRANSPARENT, dp));

            if (gameMode == MODE_AI) {
                tvBlackName.setText(playerColor == GomokuGame.BLACK ? "我 (执黑)" : "AI (执黑)");
                tvWhiteName.setText(playerColor == GomokuGame.WHITE ? "我 (执白)" : "AI (执白)");
                tvBlackStatus.setText(isBlackTurn ? (playerColor == GomokuGame.BLACK ? "● 你的回合" : "● AI思考中") : "等待");
                tvWhiteStatus.setText(isWhiteTurn ? (playerColor == GomokuGame.WHITE ? "● 你的回合" : "● AI思考中") : "等待");
            } else {
                tvBlackName.setText("玩家 1 (黑)");
                tvWhiteName.setText("玩家 2 (白)");
                tvBlackStatus.setText(isBlackTurn ? "● 请落子" : "等待");
                tvWhiteStatus.setText(isWhiteTurn ? "● 请落子" : "等待");
            }
        }
    }

    private GradientDrawable createBoxBackground(int bgColor, int strokeColor, float dp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(10 * dp);
        gd.setColor(bgColor);
        if (strokeColor != Color.TRANSPARENT) {
            gd.setStroke((int) (1.5f * dp), strokeColor);
        }
        return gd;
    }

    private void handleUndo() {
        if (aiThinking && gameMode == MODE_AI) return;
        if (game.getMoveCount() == 0) {
            Toast.makeText(requireContext(), "当前棋盘为空，无需悔棋", Toast.LENGTH_SHORT).show();
            return;
        }

        if (gameMode == MODE_AI) {
            // 人机对战：撤回双方各一手
            int count = game.undoLastMoves(1);
            if (count > 0) {
                gomokuView.clearHint();
                gomokuView.invalidate();
                updateHudDisplay();
                Toast.makeText(requireContext(), "已撤回上一手", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 双人模式：撤销单手
            GomokuGame.MoveRecord r = game.undoSingleMove();
            if (r != null) {
                gomokuView.clearHint();
                gomokuView.invalidate();
                updateHudDisplay();
                Toast.makeText(requireContext(), "已撤回单步", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleHint() {
        if (game.isGameOver() || (aiThinking && gameMode == MODE_AI)) return;
        gomokuView.clearHint();
        final long currentGen = aiGeneration;
        int targetPlayer = game.getCurrentPlayer();

        Toast.makeText(requireContext(), "大师 AI 正在推演局势…", Toast.LENGTH_SHORT).show();
        aiExecutor.execute(() -> {
            int[] hint = masterAi.getBestMove(game, targetPlayer);
            if (hint != null) {
                String analysis = masterAi.getEducationalAnalysis(game, hint[0], hint[1], targetPlayer);
                mainHandler.post(() -> {
                    if (currentGen != aiGeneration || game.isGameOver()) return;
                    gomokuView.showHint(hint[0], hint[1]);
                    char colLabel = (char) ('A' + hint[0]);
                    int rowLabel = 15 - hint[1];
                    Toast.makeText(requireContext(),
                            "💡 大师建议落子点: " + colLabel + rowLabel + "\n" + analysis,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void toggleStepNumbers() {
        showStepNumbers = !showStepNumbers;
        gomokuView.setShowStepNumbers(showStepNumbers);
        if (tvStepToggleLabel != null) {
            tvStepToggleLabel.setText(showStepNumbers ? "手数 √" : "手数");
        }
        Toast.makeText(requireContext(), showStepNumbers ? "已开启棋子手数显示" : "已隐藏手数",
                Toast.LENGTH_SHORT).show();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(requireContext())
                .setTitle("重新开局")
                .setMessage("确定要清空棋盘并重新开始对局吗？")
                .setPositiveButton("重新开局", (dialog, which) -> {
                    aiGeneration++;
                    aiThinking = false;
                    gomokuView.setAiThinking(false);
                    startGame();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleBackPress() {
        if (game != null && game.getMoveCount() > 0 && !game.isGameOver()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出五子棋")
                    .setMessage("当前对局尚未结束，确定要退出返回游戏大厅吗？")
                    .setPositiveButton("退出", (dialog, which) -> {
                        if (getActivity() != null) getActivity().finish();
                    })
                    .setNegativeButton("继续对弈", null)
                    .show();
        } else {
            if (getActivity() != null) getActivity().finish();
        }
    }

    private void showGameSettingsDialog() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode();

        LinearLayout dialogView = new LinearLayout(ctx);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding((int) (20 * dp), (int) (14 * dp), (int) (20 * dp), (int) (14 * dp));

        // 1. 模式切换
        dialogView.addView(createSectionTitle(ctx, "对战模式", dp, night));
        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnAi = createOptionButton(ctx, "人机对战", gameMode == MODE_AI, dp, night);
        Button btnPvp = createOptionButton(ctx, "双人同屏", gameMode == MODE_PVP, dp, night);
        modeRow.addView(btnAi);
        modeRow.addView(btnPvp);
        dialogView.addView(modeRow);

        // 2. 难度选择（仅人机模式有效）
        dialogView.addView(createSectionTitle(ctx, "AI 难度（人机模式）", dp, night));
        LinearLayout diffRow = new LinearLayout(ctx);
        diffRow.setOrientation(LinearLayout.HORIZONTAL);
        Button[] diffBtns = new Button[4];
        for (int i = 0; i < 4; i++) {
            final int diff = i + 1;
            Button b = createOptionButton(ctx, DIFFICULTY_NAMES[i], aiDifficulty == diff, dp, night);
            diffBtns[i] = b;
            diffRow.addView(b);
        }
        dialogView.addView(diffRow);

        // 3. 辅助功能开关
        dialogView.addView(createSectionTitle(ctx, "规则与辅助", dp, night));
        LinearLayout togglesRow = new LinearLayout(ctx);
        togglesRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnForbidden = createOptionButton(ctx, forbiddenMovesEnabled ? "禁手：开启" : "禁手：关闭",
                forbiddenMovesEnabled, dp, night);
        Button btnCrosshair = createOptionButton(ctx, crosshairEnabled ? "准星：开启" : "准星：关闭",
                crosshairEnabled, dp, night);
        Button btnSound = createOptionButton(ctx, soundEnabled ? "音效：开启" : "音效：关闭",
                soundEnabled, dp, night);
        togglesRow.addView(btnForbidden);
        togglesRow.addView(btnCrosshair);
        togglesRow.addView(btnSound);
        dialogView.addView(togglesRow);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("五子棋对战设置")
                .setView(dialogView)
                .setPositiveButton("应用并开局", (d, w) -> {
                    ai = new GomokuAI(aiDifficulty);
                    game.setForbiddenMovesEnabled(forbiddenMovesEnabled);
                    gomokuView.setCrosshairEnabled(crosshairEnabled);
                    tvModeBadge.setText(gameMode == MODE_AI
                            ? "人机对战 · " + DIFFICULTY_NAMES[aiDifficulty - 1]
                            : "双人同屏对战");
                    startGame();
                })
                .setNegativeButton("关闭", null)
                .create();

        btnAi.setOnClickListener(v -> {
            gameMode = MODE_AI;
            updateButtonStyle(btnAi, true, dp, night);
            updateButtonStyle(btnPvp, false, dp, night);
        });
        btnPvp.setOnClickListener(v -> {
            gameMode = MODE_PVP;
            updateButtonStyle(btnPvp, true, dp, night);
            updateButtonStyle(btnAi, false, dp, night);
        });

        for (int i = 0; i < 4; i++) {
            final int selectedIdx = i;
            diffBtns[i].setOnClickListener(v -> {
                aiDifficulty = selectedIdx + 1;
                for (int j = 0; j < 4; j++) {
                    updateButtonStyle(diffBtns[j], j == selectedIdx, dp, night);
                }
            });
        }

        btnForbidden.setOnClickListener(v -> {
            forbiddenMovesEnabled = !forbiddenMovesEnabled;
            btnForbidden.setText(forbiddenMovesEnabled ? "禁手：开启" : "禁手：关闭");
            updateButtonStyle(btnForbidden, forbiddenMovesEnabled, dp, night);
        });
        btnCrosshair.setOnClickListener(v -> {
            crosshairEnabled = !crosshairEnabled;
            btnCrosshair.setText(crosshairEnabled ? "准星：开启" : "准星：关闭");
            updateButtonStyle(btnCrosshair, crosshairEnabled, dp, night);
        });
        btnSound.setOnClickListener(v -> {
            soundEnabled = !soundEnabled;
            btnSound.setText(soundEnabled ? "音效：开启" : "音效：关闭");
            updateButtonStyle(btnSound, soundEnabled, dp, night);
        });

        dialog.show();
    }

    private void handleGameOver(Integer winner) {
        stopTimer();
        updateHudDisplay();
        if (victoryDialog != null && victoryDialog.isShowing()) {
            victoryDialog.dismiss();
        }

        long elapsedSec = (SystemClock.elapsedRealtime() - gameStartElapsedMs) / 1000;
        String durationStr = String.format(Locale.getDefault(), "%02d:%02d", elapsedSec / 60, elapsedSec % 60);
        int totalMoves = game.getMoveCount();

        String title;
        String message;

        if (winner == null) {
            title = "🤝 棋逢对手 · 平局";
            message = "双方激战 " + totalMoves + " 手，棋盘下满握手言和！\n总用时: " + durationStr;
        } else if (gameMode == MODE_AI) {
            if (winner == playerColor) {
                title = "🏆 连珠称霸 · 恭喜获胜！";
                message = "你在 " + totalMoves + " 手内击败了 " + DIFFICULTY_NAMES[aiDifficulty - 1] + " AI！\n总用时: " + durationStr;
                if (usageStore != null) usageStore.recordWin(GAME_ID);
            } else {
                title = "⚔️ 遗憾落败 · 再接再厉";
                message = "AI 在第 " + totalMoves + " 手实现五连绝杀。\n总用时: " + durationStr;
                if (usageStore != null) usageStore.recordLoss(GAME_ID);
            }
        } else {
            String winnerName = (winner == GomokuGame.BLACK) ? "黑方 (玩家 1)" : "白方 (玩家 2)";
            title = "🎉 " + winnerName + " 获胜！";
            message = "恭喜以五连珠锁定胜局！\n总手数: " + totalMoves + " 手 | 总用时: " + durationStr;
        }

        victoryDialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("再来一局", (d, w) -> {
                    victoryDialog = null;
                    startGame();
                })
                .setNegativeButton("查看棋盘", (d, w) -> {
                    victoryDialog = null;
                    gomokuView.invalidate();
                })
                .setCancelable(true)
                .setOnDismissListener(d -> victoryDialog = null)
                .create();
        victoryDialog.show();
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

    private void updateTimerDisplay() {
        if (tvCenterTimer == null || gameStartElapsedMs == 0L) return;
        long elapsedSec = (SystemClock.elapsedRealtime() - gameStartElapsedMs) / 1000;
        long min = elapsedSec / 60;
        long sec = elapsedSec % 60;
        tvCenterTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    private GradientDrawable createCircleRippleDrawable(int color, float sizePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        return gd;
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

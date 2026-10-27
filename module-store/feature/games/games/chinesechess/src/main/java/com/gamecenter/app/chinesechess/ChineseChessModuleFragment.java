package com.gamecenter.app.chinesechess;

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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.adaptive.AdaptiveAdvisor;
import com.gamecenter.app.games.rating.RatingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 现代高品质中国象棋模块 Fragment（对齐五子棋成功范式改造）。
 * <p>
 * 核心升级：
 * <ul>
 *   <li><b>沉浸式开局（即开即玩）</b>：进入直接呈现大气棋盘，消除遮挡半屏的旧式难度面板</li>
 *   <li><b>现代对弈看板 (Player HUD)</b>：红黑双雄立体木质帅/将头像、对局计时、回合手数、将军高亮警报与动态呼吸指示</li>
 *   <li><b>防穿模 3D 浮雕棋盘与棋子</b>：预留充裕呼吸边距，象牙温润木质渐变底色与双圈阴刻书法中文字</li>
 *   <li><b>紧凑圆角操作胶囊</b>：悔棋、AI 智能提示、重开、残局挑战、局域网对战</li>
 *   <li><b>专业荣誉结算弹窗</b>：终局荣誉勋章、耗时/步数/Elo 棋力增减、支持一键进入 AI 智能标注复盘</li>
 * </ul>
 */
public class ChineseChessModuleFragment extends Fragment {

    private static final String TAG = "ChineseChessModule";
    private static final String GAME_ID = "chinesechess";
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {140L, 220L, 340L, 480L};
    private static final int MAX_AI_DIFFICULTY = 4;

    private static final String[] DIFFICULTY_NAMES = {"低", "中", "高", "大师"};
    private static final String[] DIFFICULTY_DESCRIPTIONS = {
            "适合首次体验：保留变化，不随意送子",
            "适合休闲对局：兼顾响应速度与基础战术",
            "进阶大师水平：四层纵深搜索与稳健将区判断",
            "最高挑战段位：稳定择优，残局自动加深搜索"
    };

    // 棋盘视图
    private ChineseChessView chessView;

    // 顶部与 HUD 控件
    private TextView tvModeBadge;
    private TextView tvRedName;
    private TextView tvRedStatus;
    private View viewRedDot;
    private TextView tvRedPieces;
    private TextView tvBlackName;
    private TextView tvBlackStatus;
    private View viewBlackDot;
    private TextView tvBlackPieces;
    private TextView tvCenterTimer;
    private TextView tvCenterMoves;
    private TextView tvCenterAlert;

    // 复盘控件
    private LinearLayout replayBar;
    private TextView tvReplayStep;
    private TextView tvReplayAnnotation;
    private List<ChineseChessReviewAnnotator.Annotation> replayAnnotations;
    private final ChineseChessReplay replay = new ChineseChessReplay();
    private boolean isReplaying = false;
    private int replayIndex = 0;
    private ChineseChessGame replayGame;

    // 控制栏按钮
    private LinearLayout controlBar;
    private LinearLayout btnUndo;
    private LinearLayout btnHint;
    private LinearLayout btnRestart;
    private LinearLayout btnEndgame;
    private LinearLayout btnOnline;

    // 游戏核心状态
    private ChineseChessGame game;
    private ChineseChessAI ai;
    private int aiDifficulty = 2; // 默认中等
    private ChineseChessEndgames.EndgameSpec currentEndgame = null;
    private int recommendedDifficultyTier = 0;

    private Handler uiHandler;
    private ExecutorService aiExecutor;
    private volatile boolean isProcessing = false;
    private int gameGeneration = 0;

    private int[] selectedPos = null;
    private List<int[]> currentValidMoves = null;

    private GameUsageStore usageStore;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private AlertDialog victoryDialog;

    private long gameStartElapsedMs = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerDisplay();
            if (game != null && !game.isGameOver() && !isReplaying) {
                timerHandler.postDelayed(this, 1000);
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

        int colorBg = night ? 0xFF14171F : 0xFFF5EADA;
        int colorTextPrimary = night ? 0xFFEAECEF : 0xFF212529;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);

        // 1. 顶部标准导航栏
        View topBar = buildTopAppBar(ctx, dp, colorTextPrimary, night);
        root.addView(topBar);

        // 2. 对弈双方信息看板 (Player HUD)
        View hud = buildPlayerHud(ctx, dp, night);
        root.addView(hud);

        // 3. 棋盘居中容器
        FrameLayout boardContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins((int) (6 * dp), (int) (2 * dp), (int) (6 * dp), (int) (2 * dp));
        boardContainer.setLayoutParams(boardLp);

        chessView = new ChineseChessView(ctx);
        chessView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        boardContainer.addView(chessView);
        root.addView(boardContainer);

        // 4. 复盘控制条 (默认 GONE)
        replayBar = buildReplayBar(ctx, dp, night);
        root.addView(replayBar);

        // 4.1 复盘 AI 标注短评
        tvReplayAnnotation = new TextView(ctx);
        tvReplayAnnotation.setGravity(Gravity.CENTER);
        tvReplayAnnotation.setTextColor(night ? 0xFFFFD54F : 0xFF8B5A2B);
        tvReplayAnnotation.setTextSize(13);
        tvReplayAnnotation.setPadding((int) (14 * dp), (int) (4 * dp), (int) (14 * dp), (int) (6 * dp));
        tvReplayAnnotation.setVisibility(View.GONE);
        root.addView(tvReplayAnnotation);

        // 5. 底部现代化胶囊操作栏
        controlBar = buildModernControlBar(ctx, dp, night);
        root.addView(controlBar);

        return root;
    }

    private boolean isNightMode() {
        return (requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 顶部标准导航栏：返回按键、游戏标题、模式副标、设置按键
     */
    private View buildTopAppBar(Context ctx, float dp, int textPrimary, boolean night) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * dp);
        int padV = (int) (8 * dp);
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
        tvTitle.setText("中国象棋");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        titleBox.addView(tvTitle);

        tvModeBadge = new TextView(ctx);
        tvModeBadge.setText("人机对战 · " + DIFFICULTY_NAMES[aiDifficulty - 1]);
        tvModeBadge.setTextSize(11.5f);
        tvModeBadge.setTextColor(night ? 0xFFFFB74D : 0xFFC62828);
        tvModeBadge.setGravity(Gravity.CENTER);
        tvModeBadge.setOnClickListener(v -> showGameSettingsDialog());
        titleBox.addView(tvModeBadge);

        bar.addView(titleBox);

        // 难度设置按键
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
     * 对弈双方信息看板 (Player HUD)
     */
    private View buildPlayerHud(Context ctx, float dp, boolean night) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hudLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hudLp.setMargins((int) (12 * dp), (int) (2 * dp), (int) (12 * dp), (int) (4 * dp));
        hud.setLayoutParams(hudLp);
        hud.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));

        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setCornerRadius(14 * dp);
        hudBg.setColor(night ? 0xFF1C2230 : 0xFFFFFDF8);
        hudBg.setStroke((int) (1 * dp), night ? 0xFF2D3548 : 0xFFE8D5B5);
        hud.setBackground(hudBg);

        // 红方选手 (玩家)
        LinearLayout redBox = buildPlayerCard(ctx, dp, true, night);
        hud.addView(redBox);

        // 中间用时、手数与将军警报
        LinearLayout centerBox = new LinearLayout(ctx);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f);
        centerBox.setLayoutParams(centerLp);

        tvCenterTimer = new TextView(ctx);
        tvCenterTimer.setText("00:00");
        tvCenterTimer.setTextSize(16);
        tvCenterTimer.setTypeface(Typeface.DEFAULT_BOLD);
        tvCenterTimer.setTextColor(night ? 0xFFFFFFFF : 0xFF212121);
        tvCenterTimer.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterTimer);

        tvCenterMoves = new TextView(ctx);
        tvCenterMoves.setText("第 1 回合");
        tvCenterMoves.setTextSize(11);
        tvCenterMoves.setTextColor(night ? 0xFFB0BEC5 : 0xFF78909C);
        tvCenterMoves.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterMoves);

        tvCenterAlert = new TextView(ctx);
        tvCenterAlert.setText("对局进行中");
        tvCenterAlert.setTextSize(10.5f);
        tvCenterAlert.setTextColor(night ? 0xFF90A4AE : 0xFF8D6E63);
        tvCenterAlert.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterAlert);

        hud.addView(centerBox);

        // 黑方选手 (AI)
        LinearLayout blackBox = buildPlayerCard(ctx, dp, false, night);
        hud.addView(blackBox);

        return hud;
    }

    private LinearLayout buildPlayerCard(Context ctx, float dp, boolean isRed, boolean night) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f);
        box.setLayoutParams(lp);

        // 帅/将徽章
        TextView pieceBadge = new TextView(ctx);
        pieceBadge.setText(isRed ? "帥" : "將");
        pieceBadge.setTextSize(15);
        pieceBadge.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        pieceBadge.setTextColor(isRed ? 0xFFC62828 : (night ? 0xFFECEFF1 : 0xFF212121));
        pieceBadge.setGravity(Gravity.CENTER);

        GradientDrawable pBg = new GradientDrawable();
        pBg.setShape(GradientDrawable.OVAL);
        pBg.setColor(night ? (isRed ? 0xFF3E1C1C : 0xFF263238) : 0xFFFFF8E7);
        pBg.setStroke((int) (1.5f * dp), isRed ? 0xFFB71C1C : 0xFF37474F);
        pieceBadge.setBackground(pBg);

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) (28 * dp), (int) (28 * dp));
        iconLp.setMargins(0, 0, (int) (6 * dp), 0);
        pieceBadge.setLayoutParams(iconLp);
        box.addView(pieceBadge);

        LinearLayout textBox = new LinearLayout(ctx);
        textBox.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(ctx);
        tvName.setText(isRed ? "红方 (我)" : "黑方 (AI)");
        tvName.setTextSize(12.5f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setTextColor(night ? 0xFFECEFF1 : 0xFF263238);
        titleRow.addView(tvName);

        // 行动呼吸指示灯
        View dot = new View(ctx);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams((int) (6 * dp), (int) (6 * dp));
        dotLp.setMargins((int) (4 * dp), 0, 0, 0);
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(isRed ? 0xFF4CAF50 : 0xFFFF9800);
        dot.setBackground(dotBg);
        dot.setVisibility(isRed ? View.VISIBLE : View.INVISIBLE);
        titleRow.addView(dot);

        textBox.addView(titleRow);

        TextView tvStatus = new TextView(ctx);
        tvStatus.setText(isRed ? "执红先行" : "后手");
        tvStatus.setTextSize(10.5f);
        tvStatus.setTextColor(night ? 0xFF90A4AE : 0xFF78909C);
        textBox.addView(tvStatus);

        TextView tvPieces = new TextView(ctx);
        tvPieces.setText("16子");
        tvPieces.setTextSize(10f);
        tvPieces.setTextColor(night ? 0xFF78909C : 0xFF8D6E63);
        textBox.addView(tvPieces);

        box.addView(textBox);

        if (isRed) {
            tvRedName = tvName;
            tvRedStatus = tvStatus;
            viewRedDot = dot;
            tvRedPieces = tvPieces;
        } else {
            tvBlackName = tvName;
            tvBlackStatus = tvStatus;
            viewBlackDot = dot;
            tvBlackPieces = tvPieces;
        }

        return box;
    }

    /**
     * 底部现代化圆角胶囊操作栏：悔棋、提示、重开、残局、联机
     */
    private LinearLayout buildModernControlBar(Context ctx, float dp, boolean night) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins((int) (10 * dp), (int) (2 * dp), (int) (10 * dp), (int) (10 * dp));
        bar.setLayoutParams(barLp);

        btnUndo = createControlTile(ctx, dp, "↩", "悔棋", night, v -> undoLastMove());
        bar.addView(btnUndo);

        btnHint = createControlTile(ctx, dp, "💡", "提示", night, v -> showHint());
        bar.addView(btnHint);

        btnRestart = createControlTile(ctx, dp, "🔄", "重开", night, v -> restartGame());
        bar.addView(btnRestart);

        btnEndgame = createControlTile(ctx, dp, "⚔️", "残局", night, v -> showEndgamePicker());
        bar.addView(btnEndgame);

        btnOnline = createControlTile(ctx, dp, "🌐", "联机", night, v -> enterOnlineMatch());
        bar.addView(btnOnline);

        return bar;
    }

    private LinearLayout createControlTile(Context ctx, float dp, String icon, String label,
                                           boolean night, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (50 * dp), 1f);
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        tile.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * dp);
        bg.setColor(night ? 0xFF1E2536 : 0xFFFFFDF8);
        bg.setStroke((int) (1 * dp), night ? 0xFF2D384E : 0xFFE0D4C3);
        tile.setBackground(bg);
        tile.setOnClickListener(listener);

        TextView tvIcon = new TextView(ctx);
        tvIcon.setText(icon);
        tvIcon.setTextSize(15);
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
     * 复盘回放控制条 (|◀, ◀, x/N, ▶, ▶|, 退出复盘)
     */
    private LinearLayout buildReplayBar(Context ctx, float dp, boolean night) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins((int) (10 * dp), (int) (2 * dp), (int) (10 * dp), (int) (6 * dp));
        bar.setLayoutParams(barLp);

        bar.addView(buildReplayButton(ctx, dp, "|◀", 1f, night, v -> seekReplay(0)));
        bar.addView(buildReplayButton(ctx, dp, "◀", 1f, night, v -> seekReplay(replayIndex - 1)));

        tvReplayStep = new TextView(ctx);
        tvReplayStep.setGravity(Gravity.CENTER);
        tvReplayStep.setTextColor(night ? 0xFFE0E0E0 : 0xFF8B5A2B);
        tvReplayStep.setTextSize(13);
        tvReplayStep.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
        tvReplayStep.setLayoutParams(stepLp);
        bar.addView(tvReplayStep);

        bar.addView(buildReplayButton(ctx, dp, "▶", 1f, night, v -> seekReplay(replayIndex + 1)));
        bar.addView(buildReplayButton(ctx, dp, "▶|", 1f, night, v -> seekReplay(replay.size() - 1)));
        bar.addView(buildReplayButton(ctx, dp, "退出复盘", 1.5f, night, v -> exitReplayMode()));

        return bar;
    }

    private Button buildReplayButton(Context ctx, float dp, String text, float weight, boolean night, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setStateListAnimator(null);
        btn.setOnClickListener(listener);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * dp);
        bg.setColor(night ? 0xFF2D3548 : 0xFFEFEBE9);
        btn.setBackground(bg);
        btn.setTextColor(night ? 0xFFECEFF1 : 0xFF4E342E);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (38 * dp), weight);
        lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();

        uiHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();
        audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        usageStore = new GameUsageStore(ctx);
        initVibrator();

        game = new ChineseChessGame();
        chessView.bindGame(game);
        applySavedBoardStyle();
        chessView.setOnCellClickListener(this::onCellTap);

        // 外部大厅传入难度预选参数
        if (getActivity() != null && getActivity().getIntent() != null) {
            int prefilledIndex = getActivity().getIntent().getIntExtra("game_difficulty_index", -1);
            if (prefilledIndex >= 0) {
                aiDifficulty = Math.min(prefilledIndex + 1, MAX_AI_DIFFICULTY);
            }
        }

        // 拦截系统返回键
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        // 读取自适应难度推荐
        refreshAdaptiveRecommendation();

        // 直接进入战场开局
        beginGame(aiDifficulty);
    }

    private void handleBackPress() {
        if (isReplaying) {
            exitReplayMode();
            return;
        }
        if (victoryDialog != null && victoryDialog.isShowing()) {
            victoryDialog.dismiss();
            victoryDialog = null;
        }
        // 如果棋局进行了多步，询问是否退出
        if (game != null && game.getMoveHistory().size() > 2 && !game.isGameOver()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出对局")
                    .setMessage("当前对局正在进行中，确定要返回大厅吗？")
                    .setPositiveButton("退出", (dialog, which) -> {
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    })
                    .setNegativeButton("继续对弈", null)
                    .show();
        } else {
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    private void beginGame(int difficulty) {
        gameGeneration++;
        isProcessing = false;
        currentEndgame = null;
        aiDifficulty = Math.max(1, Math.min(difficulty, MAX_AI_DIFFICULTY));
        ai = new ChineseChessAI(aiDifficulty);
        game.reset();

        selectedPos = null;
        currentValidMoves = null;

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();

        // 新局保存开局前快照
        replay.clear();
        recordReplaySnapshot();

        gameStartElapsedMs = SystemClock.elapsedRealtime();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        if (controlBar != null) controlBar.setVisibility(View.VISIBLE);
        if (replayBar != null) replayBar.setVisibility(View.GONE);
        if (tvReplayAnnotation != null) tvReplayAnnotation.setVisibility(View.GONE);

        updateHudDisplay();
        chessView.invalidate();
    }

    private void updateTimerDisplay() {
        if (tvCenterTimer == null || gameStartElapsedMs == 0L) return;
        long elapsedSec = (SystemClock.elapsedRealtime() - gameStartElapsedMs) / 1000L;
        long mins = elapsedSec / 60L;
        long secs = elapsedSec % 60L;
        tvCenterTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
    }

    private void updateHudDisplay() {
        if (tvModeBadge == null || game == null) return;

        // 顶栏徽标
        String diffSuffix = (recommendedDifficultyTier == aiDifficulty ? " (·荐)" : "");
        if (currentEndgame != null) {
            tvModeBadge.setText("残局·" + currentEndgame.name);
        } else {
            tvModeBadge.setText("人机对战 · " + DIFFICULTY_NAMES[aiDifficulty - 1] + diffSuffix);
        }

        // 手数与回合
        int plies = game.getMoveHistory().size();
        int round = plies / 2 + 1;
        if (tvCenterMoves != null) {
            tvCenterMoves.setText("第 " + round + " 回合");
        }

        // 剩余棋子统计
        int redPieces = 0;
        int blackPieces = 0;
        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int r = 0; r < ChineseChessGame.ROWS; r++) {
            for (int c = 0; c < ChineseChessGame.COLS; c++) {
                ChineseChessGame.Piece p = board[r][c];
                if (p != null) {
                    if (p.side == ChineseChessGame.Side.RED) redPieces++;
                    else blackPieces++;
                }
            }
        }
        if (tvRedPieces != null) tvRedPieces.setText(redPieces + "子");
        if (tvBlackPieces != null) tvBlackPieces.setText(blackPieces + "子");

        // 行动方指示
        boolean isRedTurn = game.getCurrentSide() == ChineseChessGame.Side.RED;
        if (viewRedDot != null) viewRedDot.setVisibility(isRedTurn ? View.VISIBLE : View.INVISIBLE);
        if (viewBlackDot != null) viewBlackDot.setVisibility(!isRedTurn ? View.VISIBLE : View.INVISIBLE);

        // 将军警报与状态
        boolean redInCheck = game.isInCheck(ChineseChessGame.Side.RED);
        boolean blackInCheck = game.isInCheck(ChineseChessGame.Side.BLACK);

        if (tvRedStatus != null) {
            if (redInCheck) tvRedStatus.setText("⚠️ 被将军！");
            else tvRedStatus.setText(isRedTurn ? "你的回合" : "等待中");
            tvRedStatus.setTextColor(redInCheck ? 0xFFD32F2F : (isRedTurn ? 0xFF2E7D32 : 0xFF78909C));
        }

        if (tvBlackStatus != null) {
            if (blackInCheck) tvBlackStatus.setText("⚠️ 照将！");
            else tvBlackStatus.setText(!isRedTurn ? "AI思考中" : "等待中");
            tvBlackStatus.setTextColor(blackInCheck ? 0xFFD32F2F : (!isRedTurn ? 0xFFE65100 : 0xFF78909C));
        }

        if (tvCenterAlert != null) {
            if (redInCheck) {
                tvCenterAlert.setText("⚠️ 红方被将军");
                tvCenterAlert.setTextColor(0xFFD32F2F);
            } else if (blackInCheck) {
                tvCenterAlert.setText("⚔️ 照将黑方！");
                tvCenterAlert.setTextColor(0xFF2E7D32);
            } else if (isProcessing) {
                tvCenterAlert.setText("AI 正在计算...");
                tvCenterAlert.setTextColor(0xFFE65100);
            } else {
                tvCenterAlert.setText("对局进行中");
                tvCenterAlert.setTextColor(0xFF8D6E63);
            }
        }
    }

    private void onCellTap(int col, int row) {
        if (isReplaying) return;
        if (isProcessing) return;
        if (game == null || game.isGameOver()) return;
        if (game.getCurrentSide() != ChineseChessGame.Side.RED) return;

        ChineseChessGame.Piece target = game.getBoard()[row][col];

        if (selectedPos != null) {
            boolean isValidMove = false;
            if (currentValidMoves != null) {
                for (int[] mv : currentValidMoves) {
                    if (mv[0] == col && mv[1] == row) {
                        isValidMove = true;
                        break;
                    }
                }
            }

            if (isValidMove) {
                performPlayerMove(selectedPos[0], selectedPos[1], col, row);
                return;
            }

            // 点自己其他子，切换选中
            if (target != null && target.side == ChineseChessGame.Side.RED) {
                selectedPos = new int[]{col, row};
                currentValidMoves = game.getLegalMoves(col, row);
                chessView.setSelected(col, row, currentValidMoves);
                playTapFeedback();
                return;
            }

            // 点其他空白或敌子，取消选中
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            return;
        }

        // 初次选子
        if (target != null && target.side == ChineseChessGame.Side.RED) {
            selectedPos = new int[]{col, row};
            currentValidMoves = game.getLegalMoves(col, row);
            chessView.setSelected(col, row, currentValidMoves);
            playTapFeedback();
        }
    }

    private void performPlayerMove(int fromX, int fromY, int toX, int toY) {
        final int generation = gameGeneration;
        isProcessing = true;
        chessView.setLocked(true);
        chessView.clearHint();

        chessView.animateMove(fromX, fromY, toX, toY, () -> {
            if (generation != gameGeneration) return;
            ChineseChessGame.MoveRecord rec = game.commitMove(fromX, fromY, toX, toY);
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();

            if (rec == null) {
                isProcessing = false;
                chessView.setLocked(false);
                chessView.invalidate();
                updateHudDisplay();
                return;
            }

            playPieceDropFeedback();
            recordReplaySnapshot();

            chessView.setLastMove(fromX, fromY, toX, toY);
            chessView.invalidate();
            updateHudDisplay();

            if (game.isGameOver()) {
                isProcessing = false;
                showGameEndStatus();
                return;
            }

            startAITurn();
        });
    }

    private void startAITurn() {
        isProcessing = true;
        chessView.setLocked(true);
        updateHudDisplay();

        final long startMs = System.currentTimeMillis();
        final int generation = gameGeneration;
        final ChineseChessAI currentAi = ai;
        final int[][] boardSnapshot = game.getBoardAsIntArray();
        final List<Long> positionHistorySnapshot = game.getPositionHistory();
        final List<int[]> recentBlackMoves = buildRecentAiMoveHistory(ChineseChessGame.Side.BLACK);

        aiExecutor.execute(() -> {
            currentAi.setPositionHistory(positionHistorySnapshot);
            currentAi.setRecentMoveHistory(recentBlackMoves);
            int[] aiRaw = currentAi.getBestMove(boardSnapshot, aiDifficulty);
            final int[] move = (aiRaw == null) ? null : new int[]{aiRaw[1], aiRaw[0], aiRaw[3], aiRaw[2]};

            long elapsed = System.currentTimeMillis() - startMs;
            long delay = Math.max(getAiMinResponseDelayMs() - elapsed, 0L);
            if (delay > 0L) {
                uiHandler.postDelayed(() -> applyAIMove(move, generation), delay);
            } else {
                uiHandler.post(() -> applyAIMove(move, generation));
            }
        });
    }

    private long getAiMinResponseDelayMs() {
        int idx = Math.max(0, Math.min(aiDifficulty - 1, AI_MIN_RESPONSE_DELAYS_MS.length - 1));
        return AI_MIN_RESPONSE_DELAYS_MS[idx];
    }

    private void applyAIMove(int[] candidate, int generation) {
        if (generation != gameGeneration || game == null || game.isGameOver()
                || game.getCurrentSide() != ChineseChessGame.Side.BLACK) {
            return;
        }
        int[] move = resolveLegalAIMove(candidate);
        if (move != null) {
            chessView.animateMove(move[0], move[1], move[2], move[3], () -> {
                if (generation != gameGeneration) return;
                ChineseChessGame.MoveRecord rec = game.commitMove(move[0], move[1], move[2], move[3]);
                isProcessing = false;
                chessView.setLocked(false);
                chessView.invalidate();
                selectedPos = null;
                currentValidMoves = null;
                chessView.clearSelected();

                if (rec == null) {
                    Log.e(TAG, "AI_COMMIT_REJECTED move=" + formatMove(move));
                    updateHudDisplay();
                    return;
                }

                playPieceDropFeedback();
                recordReplaySnapshot();

                chessView.setLastMove(move[0], move[1], move[2], move[3]);
                updateHudDisplay();
                if (game.isGameOver()) {
                    showGameEndStatus();
                }
            });
        } else {
            isProcessing = false;
            chessView.setLocked(false);
            chessView.invalidate();
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            game.checkGameOver();
            if (game.isGameOver()) {
                showGameEndStatus();
            }
            updateHudDisplay();
        }
    }

    private int[] resolveLegalAIMove(int[] candidate) {
        if (candidate != null && game.isMoveLegal(candidate[0], candidate[1], candidate[2], candidate[3])) {
            return candidate;
        }
        List<int[]> legal = game.getAllMoves(ChineseChessGame.Side.BLACK);
        if (legal.isEmpty()) return null;
        return chooseSafeFallbackMove(legal);
    }

    private int[] chooseSafeFallbackMove(List<int[]> legalMoves) {
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[] move : legalMoves) {
            ChineseChessGame.Piece moving = game.getBoard()[move[1]][move[0]];
            ChineseChessGame.Piece target = game.getBoard()[move[3]][move[2]];
            ChineseChessGame simulation = game.deepCopy();
            if (simulation.commitMove(move[0], move[1], move[2], move[3]) == null) continue;

            int score = target == null ? 0 : pieceValue(target.type) * 10;
            if (simulation.isGameOver()) {
                if (simulation.getWinner() == ChineseChessGame.Side.BLACK) score += 100_000;
                else if (simulation.getWinner() == ChineseChessGame.Side.RED) score -= 100_000;
            } else {
                if (simulation.isInCheck(ChineseChessGame.Side.RED)) score += 350;
                int movingValue = moving == null ? 0 : pieceValue(moving.type);
                for (int[] reply : simulation.getAllMoves(ChineseChessGame.Side.RED)) {
                    if (reply[2] == move[2] && reply[3] == move[3]) {
                        score -= movingValue * 2;
                        break;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    private int pieceValue(ChineseChessGame.PieceType type) {
        switch (type) {
            case GENERAL: return 10_000;
            case CHARIOT: return 900;
            case CANNON: return 450;
            case HORSE: return 400;
            case ADVISOR:
            case ELEPHANT: return 200;
            case SOLDIER: return 100;
            default: return 0;
        }
    }

    private List<int[]> buildRecentAiMoveHistory(ChineseChessGame.Side side) {
        List<int[]> result = new ArrayList<>();
        List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
        int start = Math.max(0, history.size() - 16);
        for (int i = start; i < history.size(); i++) {
            ChineseChessGame.MoveRecord record = history.get(i);
            if (record.piece != null && record.piece.side == side) {
                result.add(new int[]{record.fromY, record.fromX, record.toY, record.toX});
            }
        }
        return result;
    }

    private String formatMove(int[] move) {
        if (move == null) return "null";
        return move[0] + "," + move[1] + "->" + move[2] + "," + move[3];
    }

    /**
     * 终局荣誉结算与胜利弹窗展示
     */
    private void showGameEndStatus() {
        boolean playerWon = false;
        boolean isDraw = false;
        boolean isEndgame = currentEndgame != null;
        String title;
        String desc;

        if (isEndgame) {
            if (game.getWinner() == null) {
                isDraw = true;
                title = "残局未破";
                desc = "双方和棋，未达成通关条件。";
            } else if (game.getWinner() == ChineseChessGame.Side.RED) {
                playerWon = true;
                boolean firstClear = !ChineseChessUiPreferences.isEndgameSolved(
                        requireContext(), currentEndgame.id);
                if (firstClear) {
                    ChineseChessUiPreferences.markEndgameSolved(requireContext(), currentEndgame.id);
                }
                title = "🎉 残局通关！";
                desc = "恭喜破解经典残局《" + currentEndgame.name + "》" + (firstClear ? "（首次通关）" : "！");
            } else {
                title = "挑战未成功";
                desc = "AI 防守成功，黑方胜出，可再来一局。";
            }
        } else {
            if (game.getWinner() == null) {
                isDraw = true;
                title = "棋逢对手";
                desc = "双方力战成和！" + ratingSuffix(0.5);
            } else if (game.getWinner() == ChineseChessGame.Side.RED) {
                playerWon = true;
                title = "🎉 旗开得胜！";
                desc = "恭喜获得本局胜利！" + ratingSuffix(1.0);
                usageStore.recordWin(GAME_ID);
            } else {
                title = "胜败乃兵家常事";
                desc = "AI 棋高一着获得胜利，再接再厉！" + ratingSuffix(0.0);
                usageStore.recordLoss(GAME_ID);
            }
        }

        updateHudDisplay();
        showVictoryDialog(playerWon, isDraw, isEndgame, title, desc);
    }

    private String ratingSuffix(double score) {
        Context context = requireContext();
        int before = RatingStore.getRating(context, GAME_ID);
        int after = RatingStore.recordResult(context, GAME_ID,
                RatingStore.aiRatingForTier(aiDifficulty), score);
        int delta = after - before;
        return " 棋力 " + after + "（" + (delta >= 0 ? "+" : "") + delta + "）";
    }

    private void showVictoryDialog(boolean playerWon, boolean isDraw, boolean isEndgame,
                                   String title, String message) {
        if (victoryDialog != null && victoryDialog.isShowing()) {
            victoryDialog.dismiss();
        }

        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode();

        LinearLayout dialogView = new LinearLayout(ctx);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (22 * dp);
        dialogView.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        bg.setColor(night ? 0xFF1C2230 : 0xFFFFFFFF);
        dialogView.setBackground(bg);

        // 荣誉图标
        TextView tvIcon = new TextView(ctx);
        tvIcon.setText(playerWon ? "🏆" : (isDraw ? "🤝" : "♟️"));
        tvIcon.setTextSize(38);
        tvIcon.setGravity(Gravity.CENTER);
        dialogView.addView(tvIcon);

        // 标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(playerWon ? 0xFFC62828 : (night ? 0xFFECEFF1 : 0xFF212121));
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, (int) (8 * dp), 0, (int) (4 * dp));
        tvTitle.setLayoutParams(titleLp);
        dialogView.addView(tvTitle);

        // 战局描述
        TextView tvDesc = new TextView(ctx);
        tvDesc.setText(message);
        tvDesc.setTextSize(13);
        tvDesc.setTextColor(night ? 0xFFB0BEC5 : 0xFF616161);
        tvDesc.setGravity(Gravity.CENTER);
        dialogView.addView(tvDesc);

        // 战绩明细小卡片 (用时与手数)
        LinearLayout statsCard = new LinearLayout(ctx);
        statsCard.setOrientation(LinearLayout.HORIZONTAL);
        statsCard.setGravity(Gravity.CENTER);
        int sPad = (int) (10 * dp);
        statsCard.setPadding(sPad, sPad, sPad, sPad);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.setMargins(0, (int) (14 * dp), 0, (int) (16 * dp));
        statsCard.setLayoutParams(sLp);

        GradientDrawable scBg = new GradientDrawable();
        scBg.setCornerRadius(10 * dp);
        scBg.setColor(night ? 0xFF263238 : 0xFFF5F5F5);
        statsCard.setBackground(scBg);

        int plies = game != null ? game.getMoveHistory().size() : 0;
        int rounds = plies / 2 + 1;
        long elapsedSec = (SystemClock.elapsedRealtime() - gameStartElapsedMs) / 1000L;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", elapsedSec / 60L, elapsedSec % 60L);

        statsCard.addView(buildStatItem(ctx, "总计回合", rounds + " 回合", night));
        statsCard.addView(buildStatItem(ctx, "对弈用时", timeStr, night));
        dialogView.addView(statsCard);

        // 三个操作按钮：查看棋盘(复盘)、再来一局、返回大厅
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRow.setLayoutParams(rowLp);

        Button btnReplay = createDialogActionButton(ctx, "🔍 查看棋盘 (复盘)", dp, false, night);
        btnReplay.setOnClickListener(v -> {
            if (victoryDialog != null) victoryDialog.dismiss();
            enterReplayMode();
        });
        btnRow.addView(btnReplay);

        Button btnAgain = createDialogActionButton(ctx, "🔄 再来一局", dp, true, night);
        btnAgain.setOnClickListener(v -> {
            if (victoryDialog != null) victoryDialog.dismiss();
            restartGame();
        });
        btnRow.addView(btnAgain);

        Button btnBack = createDialogActionButton(ctx, "🚪 返回大厅", dp, false, night);
        btnBack.setOnClickListener(v -> {
            if (victoryDialog != null) victoryDialog.dismiss();
            if (getActivity() != null) getActivity().finish();
        });
        btnRow.addView(btnBack);

        dialogView.addView(btnRow);

        victoryDialog = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        victoryDialog.show();
    }

    private View buildStatItem(Context ctx, String label, String value, boolean night) {
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        item.setLayoutParams(lp);

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextSize(14);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setTextColor(night ? 0xFFFFFFFF : 0xFF212121);
        item.addView(tvVal);

        TextView tvLbl = new TextView(ctx);
        tvLbl.setText(label);
        tvLbl.setTextSize(10.5f);
        tvLbl.setTextColor(night ? 0xFF90A4AE : 0xFF757575);
        item.addView(tvLbl);

        return item;
    }

    private Button createDialogActionButton(Context ctx, String text, float dp, boolean isPrimary, boolean night) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(13.5f);
        btn.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (42 * dp));
        lp.setMargins(0, (int) (4 * dp), 0, (int) (4 * dp));
        btn.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(10 * dp);
        if (isPrimary) {
            gd.setColor(0xFFC62828); // 朱砂红突出主行动
            btn.setTextColor(Color.WHITE);
        } else {
            gd.setColor(night ? 0xFF2D3548 : 0xFFEEEEEE);
            btn.setTextColor(night ? 0xFFECEFF1 : 0xFF424242);
        }
        btn.setBackground(gd);
        return btn;
    }

    // ==================== 对局回放（复盘） ====================

    private void recordReplaySnapshot() {
        if (game != null) {
            replay.recordSnapshot(game.getBoardAsIntArray(),
                    game.getCurrentSide() == ChineseChessGame.Side.RED ? 0 : 1);
        }
    }

    private void enterReplayMode() {
        if (isReplaying || replay.size() < 2 || chessView == null) return;
        if (replayGame == null) replayGame = new ChineseChessGame();
        isReplaying = true;
        selectedPos = null;
        currentValidMoves = null;
        chessView.clearSelected();
        chessView.clearHint();
        chessView.setLocked(true);

        if (controlBar != null) controlBar.setVisibility(View.GONE);
        if (replayBar != null) replayBar.setVisibility(View.VISIBLE);
        if (tvReplayAnnotation != null) {
            tvReplayAnnotation.setVisibility(View.VISIBLE);
            tvReplayAnnotation.setText("正在计算 AI 标注...");
        }

        computeReplayAnnotations();
        seekReplay(0);
    }

    private void exitReplayMode() {
        if (!isReplaying) return;
        isReplaying = false;
        if (replayBar != null) replayBar.setVisibility(View.GONE);
        if (tvReplayAnnotation != null) tvReplayAnnotation.setVisibility(View.GONE);

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();

        List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
        if (!history.isEmpty()) {
            ChineseChessGame.MoveRecord lastRec = history.get(history.size() - 1);
            chessView.setLastMove(lastRec.fromX, lastRec.fromY, lastRec.toX, lastRec.toY);
        }
        chessView.invalidate();

        if (controlBar != null) controlBar.setVisibility(View.VISIBLE);
        updateHudDisplay();
    }

    private void seekReplay(int index) {
        if (!isReplaying || replay.size() == 0) return;
        replayIndex = Math.max(0, Math.min(index, replay.size() - 1));
        renderReplayPosition(replayIndex);
        updateReplayStepText();
    }

    private void renderReplayPosition(int index) {
        int[][] board = replay.snapshot(index);
        int side = replay.snapshotSide(index);
        if (board == null) return;

        if (!replayGame.loadEndgamePosition(ChineseChessReplay.toEndgameSpec(board), side)) {
            Log.w(TAG, "复盘快照装载失败 index=" + index + " side=" + side);
            return;
        }
        chessView.bindGame(replayGame);
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();

        if (index > 0) {
            int[] move = ChineseChessReviewAnnotator.diffMove(replay.snapshot(index - 1), board);
            if (move != null) {
                chessView.setLastMove(move[0], move[1], move[2], move[3]);
            }
        }
        chessView.invalidate();
    }

    private void updateReplayStepText() {
        int total = replay.size() - 1;
        if (tvReplayStep != null) {
            tvReplayStep.setText(replayIndex + "/" + total);
        }
        updateReplayAnnotationText();
    }

    private void updateReplayAnnotationText() {
        if (tvReplayAnnotation == null) return;
        tvReplayAnnotation.setText("");
        if (replayAnnotations == null || replayIndex <= 0) return;
        int idx = replayIndex - 1;
        if (idx >= replayAnnotations.size()) return;

        ChineseChessReviewAnnotator.Annotation annotation = replayAnnotations.get(idx);
        String side = replay.snapshotSide(idx) == 0 ? "红方" : "黑方";
        String label;
        int color;
        switch (annotation.grade) {
            case GOOD:
                label = "好棋";
                color = 0xFF2E7D32;
                break;
            case BLUNDER:
                label = "疑问手";
                color = 0xFFC62828;
                break;
            default:
                label = "普通";
                color = 0xFF8B5A2B;
                break;
        }
        tvReplayAnnotation.setTextColor(color);
        tvReplayAnnotation.setText(side + " · " + label + "：" + annotation.comment);
    }

    private void computeReplayAnnotations() {
        replayAnnotations = null;
        final int generation = gameGeneration;
        new Thread(() -> {
            List<ChineseChessReviewAnnotator.Annotation> result =
                    new ChineseChessReviewAnnotator().annotateGame(replay);
            uiHandler.post(() -> {
                if (generation != gameGeneration || !isReplaying) return;
                replayAnnotations = result;
                updateReplayStepText();
            });
        }).start();
    }

    // ==================== 底部控制栏功能 ====================

    private void undoLastMove() {
        if (isProcessing) return;
        int undone = game.undoLastMoves(1);
        if (undone > 0) {
            replay.pop();
            replay.pop();
            selectedPos = null;
            currentValidMoves = null;
            chessView.clearSelected();
            chessView.clearHint();
            chessView.clearLastMove();

            List<ChineseChessGame.MoveRecord> history = game.getMoveHistory();
            if (history.size() > 0) {
                ChineseChessGame.MoveRecord lastRec = history.get(history.size() - 1);
                chessView.setLastMove(lastRec.fromX, lastRec.fromY, lastRec.toX, lastRec.toY);
            }
            playTapFeedback();
            updateHudDisplay();
        } else {
            Toast.makeText(requireContext(), "没有可以悔棋的步数", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHint() {
        if (isProcessing || game == null || game.isGameOver()) return;
        if (game.getCurrentSide() != ChineseChessGame.Side.RED) return;

        Toast.makeText(requireContext(), "正在计算大师提示...", Toast.LENGTH_SHORT).show();
        final int generation = gameGeneration;
        final int[][] boardSnapshot = game.getBoardAsIntArray();
        final List<Long> positionHistorySnapshot = game.getPositionHistory();
        final List<int[]> recentRedMoves = buildRecentAiMoveHistory(ChineseChessGame.Side.RED);

        aiExecutor.execute(() -> {
            ChineseChessAI hintAi = new ChineseChessAI(Math.max(1, Math.min(aiDifficulty, MAX_AI_DIFFICULTY)));
            hintAi.setPositionHistory(positionHistorySnapshot);
            hintAi.setRecentMoveHistory(recentRedMoves);
            int[] raw = hintAi.getBestMove(boardSnapshot, aiDifficulty, 1);
            final int[] move = (raw == null) ? null : new int[]{raw[1], raw[0], raw[3], raw[2]};

            uiHandler.post(() -> {
                if (generation != gameGeneration || move == null || game.isGameOver()
                        || game.getCurrentSide() != ChineseChessGame.Side.RED
                        || !game.isMoveLegal(move[0], move[1], move[2], move[3])) {
                    Toast.makeText(requireContext(), "暂无可用提示", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedPos = new int[]{move[0], move[1]};
                currentValidMoves = game.getLegalMoves(move[0], move[1]);
                chessView.setSelected(move[0], move[1], currentValidMoves);
                chessView.setHintMove(move[0], move[1], move[2], move[3]);

                ChineseChessGame.Piece piece = game.getBoard()[move[1]][move[0]];
                String pieceName = piece != null ? piece.getName() : "";
                String hintDesc = buildHintDescription(pieceName, move[0], move[1], move[2], move[3]);
                Toast.makeText(requireContext(), "💡 推荐着法: " + hintDesc, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private String buildHintDescription(String pieceName, int fromX, int fromY, int toX, int toY) {
        String[] colNames = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
        String fromCol = colNames[fromX];
        String toCol = colNames[toX];

        if (fromX == toX) {
            int steps = Math.abs(toY - fromY);
            String direction = toY < fromY ? "进" : "退";
            return pieceName + fromCol + direction + numToChinese(steps);
        } else if (fromY == toY) {
            return pieceName + fromCol + "平" + toCol;
        } else {
            String direction = toY < fromY ? "进" : "退";
            return pieceName + fromCol + direction + toCol;
        }
    }

    private String numToChinese(int num) {
        String[] nums = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (num >= 0 && num < nums.length) return nums[num];
        return String.valueOf(num);
    }

    private void restartGame() {
        if (isReplaying) {
            exitReplayMode();
        }
        if (currentEndgame != null) {
            beginEndgame(currentEndgame);
            return;
        }
        beginGame(aiDifficulty);
    }

    private void enterOnlineMatch() {
        getParentFragmentManager().beginTransaction()
                .replace(com.gamecenter.app.R.id.fragment_container, new ChineseChessOnlineFragment())
                .addToBackStack(null)
                .commit();
    }

    // ==================== 残局挑战 ====================

    private void showEndgamePicker() {
        ChineseChessEndgames.EndgameSpec[] levels = ChineseChessEndgames.LEVELS;
        String[] items = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            boolean solved = ChineseChessUiPreferences.isEndgameSolved(requireContext(), levels[i].id);
            items[i] = (i + 1) + ". " + levels[i].name + "（" + levels[i].tag + "）"
                    + (solved ? " ✓已通关" : "");
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("⚔️ 经典残局挑战")
                .setItems(items, (dialog, which) -> beginEndgame(levels[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void beginEndgame(ChineseChessEndgames.EndgameSpec spec) {
        gameGeneration++;
        isProcessing = false;
        if (ai != null) ai.cancel();
        chessView.cancelAnimation();

        ai = new ChineseChessAI(spec.aiDifficulty);
        aiDifficulty = spec.aiDifficulty;

        game.reset();
        if (!game.loadEndgamePosition(spec.pieces, 0)) {
            currentEndgame = null;
            Toast.makeText(requireContext(), "残局装载失败", Toast.LENGTH_SHORT).show();
            restartGame();
            return;
        }
        currentEndgame = spec;

        selectedPos = null;
        currentValidMoves = null;

        chessView.bindGame(game);
        chessView.setLocked(false);
        chessView.clearSelected();
        chessView.clearHint();
        chessView.clearLastMove();

        replay.clear();
        recordReplaySnapshot();

        gameStartElapsedMs = SystemClock.elapsedRealtime();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        if (controlBar != null) controlBar.setVisibility(View.VISIBLE);
        if (replayBar != null) replayBar.setVisibility(View.GONE);
        if (tvReplayAnnotation != null) tvReplayAnnotation.setVisibility(View.GONE);

        updateHudDisplay();
        Toast.makeText(requireContext(), "残局《" + spec.name + "》红方先行，将死黑方即破局！", Toast.LENGTH_LONG).show();
    }

    // ==================== 设置与难度对话框 ====================

    private void showGameSettingsDialog() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode();

        LinearLayout dialogView = new LinearLayout(ctx);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        dialogView.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        bg.setColor(night ? 0xFF1C2230 : 0xFFFFFFFF);
        dialogView.setBackground(bg);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("对战设置与模式");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(night ? 0xFFECEFF1 : 0xFF212121);
        dialogView.addView(tvTitle);

        // 难度选择
        TextView tvSub = new TextView(ctx);
        tvSub.setText("AI 对战难度");
        tvSub.setTextSize(12.5f);
        tvSub.setTextColor(night ? 0xFF90A4AE : 0xFF78909C);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, (int) (12 * dp), 0, (int) (6 * dp));
        tvSub.setLayoutParams(subLp);
        dialogView.addView(tvSub);

        LinearLayout diffRow = new LinearLayout(ctx);
        diffRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button[] diffBtns = new Button[MAX_AI_DIFFICULTY];
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        for (int i = 0; i < MAX_AI_DIFFICULTY; i++) {
            final int tier = i + 1;
            boolean selected = (tier == aiDifficulty && currentEndgame == null);
            boolean isRecommended = (recommendedDifficultyTier == tier);

            Button btn = new Button(ctx);
            String label = DIFFICULTY_NAMES[i] + (isRecommended ? "·荐" : "");
            btn.setText(label);
            btn.setTextSize(12);
            btn.setStateListAnimator(null);

            GradientDrawable bBg = new GradientDrawable();
            bBg.setCornerRadius(8 * dp);
            if (selected) {
                bBg.setColor(0xFFC62828);
                btn.setTextColor(Color.WHITE);
            } else {
                bBg.setColor(night ? 0xFF2D3548 : 0xFFF0F0F0);
                btn.setTextColor(night ? 0xFFCFD8DC : 0xFF455A64);
            }
            btn.setBackground(bBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
            lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                aiDifficulty = tier;
                currentEndgame = null;
                beginGame(aiDifficulty);
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            });
            diffBtns[i] = btn;
            diffRow.addView(btn);
        }
        dialogView.addView(diffRow);

        // 棋盘风格
        TextView tvStyleSub = new TextView(ctx);
        tvStyleSub.setText("棋盘视觉风格");
        tvStyleSub.setTextSize(12.5f);
        tvStyleSub.setTextColor(night ? 0xFF90A4AE : 0xFF78909C);
        LinearLayout.LayoutParams sSubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sSubLp.setMargins(0, (int) (14 * dp), 0, (int) (6 * dp));
        tvStyleSub.setLayoutParams(sSubLp);
        dialogView.addView(tvStyleSub);

        Button btnToggleStyle = createDialogActionButton(
                ctx, chessView.isSimpleMode() ? "切换为：古典增强木纹棋盘" : "切换为：极简素雅棋盘", dp, false, night);
        btnToggleStyle.setOnClickListener(v -> {
            boolean nextSimple = !chessView.isSimpleMode();
            setSimpleBoardEnabled(nextSimple, true);
            btnToggleStyle.setText(nextSimple ? "切换为：古典增强木纹棋盘" : "切换为：极简素雅棋盘");
        });
        dialogView.addView(btnToggleStyle);

        // 新手教程
        Button btnTutorial = createDialogActionButton(ctx, "📖 象棋规则与走子教程", dp, false, night);
        btnTutorial.setOnClickListener(v -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            GameTutorialHelper.showChineseChessTutorial(requireContext());
        });
        dialogView.addView(btnTutorial);

        // 残局关卡快捷入口
        Button btnPickEndgame = createDialogActionButton(ctx, "⚔️ 经典古谱残局挑战 (7关)", dp, false, night);
        btnPickEndgame.setOnClickListener(v -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            showEndgamePicker();
        });
        dialogView.addView(btnPickEndgame);

        dialogHolder[0] = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();
        dialogHolder[0].show();
    }

    private void refreshAdaptiveRecommendation() {
        final GameUsageStore store = usageStore;
        if (store == null) return;
        final int currentTier = aiDifficulty;

        new Thread(() -> {
            try {
                int win = store.getWinCount(GAME_ID);
                int loss = store.getLossCount(GAME_ID);
                int tier = AdaptiveAdvisor.recommendedTier(win, loss, currentTier, MAX_AI_DIFFICULTY);
                uiHandler.post(() -> {
                    if (!isAdded()) return;
                    recommendedDifficultyTier = tier;
                    updateHudDisplay();
                });
            } catch (Exception e) {
                Log.w(TAG, "读取难度自适应推荐数据失败", e);
            }
        }).start();
    }

    private void applySavedBoardStyle() {
        boolean simple = ChineseChessUiPreferences.isSimpleMode(requireContext());
        chessView.setSimpleMode(simple);
    }

    private void setSimpleBoardEnabled(boolean enabled, boolean persist) {
        if (chessView == null) return;
        chessView.setSimpleMode(enabled);
        if (persist) ChineseChessUiPreferences.setSimpleMode(requireContext(), enabled);
        updateHudDisplay();
    }

    // ==================== 音效与振动反馈 ====================

    private void initVibrator() {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Vibrator init failed", t);
        }
    }

    private void playTapFeedback() {
        if (audioManager != null) {
            audioManager.playSoundEffect(SoundEffectConstants.CLICK);
        }
    }

    private void playPieceDropFeedback() {
        if (audioManager != null) {
            audioManager.playSoundEffect(SoundEffectConstants.CLICK);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(18);
                }
            } catch (Throwable ignored) {}
        }
    }

    private GradientDrawable createCircleRippleDrawable(int color, float sizePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        gd.setSize((int) sizePx, (int) sizePx);
        return gd;
    }

    @Override
    public void onDestroy() {
        gameGeneration++;
        timerHandler.removeCallbacks(timerRunnable);
        if (victoryDialog != null && victoryDialog.isShowing()) {
            victoryDialog.dismiss();
            victoryDialog = null;
        }
        if (ai != null) ai.cancel();
        super.onDestroy();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}

package com.gamecenter.app.go;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 围棋模块 Fragment（现代化重构版）。
 *
 * <p>对标五子棋与中国象棋全新视效规范：
 * 1. 即开即玩沉浸式战场：移除臃肿的预设菜单，进入即对弈；
 * 2. 现代对弈双方信息看板 (Player HUD)：包含玩家与 AI 头像、提子统计、手数与回合计时；
 * 3. 紧凑圆角胶囊控制栏：支持 悔棋、AI 智能提示、停一手、认输与难度/设置弹窗；
 * 4. 终局数子结算弹窗：基于中国面积规则精准计算黑白领地与 6.5 贴目。</p>
 */
public class GoModuleFragment extends Fragment {

    private static final String TAG = "GoModule";
    private static final String[] DIFFICULTY_NAMES = {"入门", "普通", "困难", "大师"};
    private static final String[] DIFFICULTY_DESCRIPTIONS = {
            "遵守基本规则，避开明显坏棋",
            "能提子、救棋并识别打吃与连接",
            "多步推演应对，兼顾大局与死活",
            "深度 MCTS 蒙特卡洛树搜索，稳定弈出主干变化"
    };

    // 核心引擎与状态
    private GoGame game;
    private GoAI ai;
    private GoView goView;

    // 顶部与 HUD 视图
    private TextView tvModeBadge;
    private TextView tvPlayerCaptures;
    private TextView tvAiCaptures;
    private TextView tvAiDifficulty;
    private TextView tvCenterTimer;
    private TextView tvCenterMoves;
    private TextView tvCenterAlert;
    private View viewPlayerDot;
    private View viewAiDot;

    // 控制栏按钮
    private LinearLayout btnUndo;
    private LinearLayout btnHint;
    private LinearLayout btnPass;
    private LinearLayout btnResign;
    private LinearLayout btnSettings;

    // 对局与线程控制
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;
    private int moveCount = 0;
    private long gameStartElapsedMs = 0L;
    private boolean gameStarted = false;
    private int restartCount = 0;
    private int aiContractViolationCount = 0;
    private int aiFallbackCount = 0;

    // 计时器
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (gameStarted && game != null && !game.isGameOver()) {
                long elapsed = System.currentTimeMillis() - gameStartElapsedMs;
                long sec = elapsed / 1000L;
                if (tvCenterTimer != null) {
                    tvCenterTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L));
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    // 悔棋快照历史记录
    private static class HistoryStep {
        final GoGame.PositionSnapshot snapshot;
        final int lastMoveRow;
        final int lastMoveCol;

        HistoryStep(GoGame.PositionSnapshot s, int r, int c) {
            this.snapshot = s;
            this.lastMoveRow = r;
            this.lastMoveCol = c;
        }
    }
    private final List<HistoryStep> history = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new GoGame();
        ai = new GoAI();
        aiExecutor = Executors.newSingleThreadExecutor();

        if (getActivity() != null && getActivity().getIntent() != null) {
            int prefilledIndex = getActivity().getIntent().getIntExtra("game_difficulty_index", -1);
            if (prefilledIndex >= 0) {
                ai.setDifficulty(Math.min(prefilledIndex + 1, DIFFICULTY_NAMES.length));
            }
        }
    }

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

        goView = new GoView(ctx);
        goView.setSimpleMode(GoUiPreferences.isSimpleMode(ctx));
        goView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        goView.setOnCellClickListener(this::onCellClick);
        boardContainer.addView(goView);
        root.addView(boardContainer);

        // 4. 底部现代化圆角胶囊操作栏
        LinearLayout controlBar = buildModernControlBar(ctx, dp, night);
        root.addView(controlBar);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        startNewGame();
    }

    // ==========================================
    // UI 构建逻辑
    // ==========================================

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
        tvTitle.setText("围棋");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        titleBox.addView(tvTitle);

        tvModeBadge = new TextView(ctx);
        tvModeBadge.setText("人机对战 · " + DIFFICULTY_NAMES[ai.getDifficulty() - 1]);
        tvModeBadge.setTextSize(11.5f);
        tvModeBadge.setTextColor(night ? 0xFFFFB74D : 0xFF5B8A72);
        tvModeBadge.setGravity(Gravity.CENTER);
        tvModeBadge.setOnClickListener(v -> showGameSettingsDialog());
        titleBox.addView(tvModeBadge);

        bar.addView(titleBox);

        // 规则说明按钮
        TextView btnRules = new TextView(ctx);
        btnRules.setText("ℹ");
        btnRules.setTextSize(18);
        btnRules.setTextColor(textPrimary);
        btnRules.setGravity(Gravity.CENTER);
        btnRules.setBackground(createCircleRippleDrawable(night ? 0x22FFFFFF : 0x11000000, 36 * dp));
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        btnRules.setLayoutParams(ruleLp);
        btnRules.setOnClickListener(v -> showRuleDialog());
        bar.addView(btnRules);

        // 难度/设置按钮
        TextView btnSettingsTop = new TextView(ctx);
        btnSettingsTop.setText("⚙");
        btnSettingsTop.setTextSize(20);
        btnSettingsTop.setTextColor(textPrimary);
        btnSettingsTop.setGravity(Gravity.CENTER);
        btnSettingsTop.setBackground(createCircleRippleDrawable(night ? 0x22FFFFFF : 0x11000000, 36 * dp));
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        btnSettingsTop.setLayoutParams(setLp);
        btnSettingsTop.setOnClickListener(v -> showGameSettingsDialog());
        bar.addView(btnSettingsTop);

        return bar;
    }

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

        // 黑方选手 (玩家)
        LinearLayout blackBox = buildPlayerCard(ctx, dp, true, night);
        hud.addView(blackBox);

        // 中间用时、手数与局况
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
        tvCenterMoves.setText("第 1 手");
        tvCenterMoves.setTextSize(11);
        tvCenterMoves.setTextColor(night ? 0xFFB0BEC5 : 0xFF78909C);
        tvCenterMoves.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterMoves);

        tvCenterAlert = new TextView(ctx);
        tvCenterAlert.setText("黑先 · 贴 6.5 目");
        tvCenterAlert.setTextSize(10.5f);
        tvCenterAlert.setTextColor(night ? 0xFF90A4AE : 0xFF8D6E63);
        tvCenterAlert.setGravity(Gravity.CENTER);
        centerBox.addView(tvCenterAlert);

        hud.addView(centerBox);

        // 白方选手 (AI)
        LinearLayout whiteBox = buildPlayerCard(ctx, dp, false, night);
        hud.addView(whiteBox);

        return hud;
    }

    private LinearLayout buildPlayerCard(Context ctx, float dp, boolean isBlack, boolean night) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f);
        box.setLayoutParams(lp);

        // 棋子图标
        TextView pieceBadge = new TextView(ctx);
        pieceBadge.setText(isBlack ? "●" : "○");
        pieceBadge.setTextSize(17);
        pieceBadge.setTextColor(isBlack ? (night ? 0xFFFFFFFF : 0xFF1A1A1A) : (night ? 0xFFE0E0E0 : 0xFF424242));
        pieceBadge.setGravity(Gravity.CENTER);

        GradientDrawable pBg = new GradientDrawable();
        pBg.setShape(GradientDrawable.OVAL);
        pBg.setColor(night ? (isBlack ? 0xFF212121 : 0xFFECEFF1) : (isBlack ? 0xFF2A2A2A : 0xFFFFFDF8));
        pBg.setStroke((int) (1.5f * dp), isBlack ? 0xFF424242 : 0xFFB0BEC5);
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
        tvName.setText(isBlack ? "黑方 (我)" : "白方 (AI)");
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
        dotBg.setColor(isBlack ? 0xFF4CAF50 : 0xFFFF9800);
        dot.setBackground(dotBg);
        dot.setVisibility(isBlack ? View.VISIBLE : View.INVISIBLE);
        titleRow.addView(dot);

        textBox.addView(titleRow);

        // 提子状态
        TextView tvCaptures = new TextView(ctx);
        tvCaptures.setText("提子: 0");
        tvCaptures.setTextSize(10.5f);
        tvCaptures.setTextColor(night ? 0xFF90A4AE : 0xFF78909C);
        textBox.addView(tvCaptures);

        box.addView(textBox);

        if (isBlack) {
            tvPlayerCaptures = tvCaptures;
            viewPlayerDot = dot;
        } else {
            tvAiCaptures = tvCaptures;
            viewAiDot = dot;
            tvAiDifficulty = new TextView(ctx);
            tvAiDifficulty.setText(DIFFICULTY_NAMES[ai.getDifficulty() - 1]);
        }

        return box;
    }

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

        btnPass = createControlTile(ctx, dp, "⏸", "停一手", night, v -> passMove());
        bar.addView(btnPass);

        btnResign = createControlTile(ctx, dp, "🏳", "认输", night, v -> resign());
        bar.addView(btnResign);

        btnSettings = createControlTile(ctx, dp, "⚙", "设置", night, v -> showGameSettingsDialog());
        bar.addView(btnSettings);

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

        int bgCol = night ? 0xFF1E2536 : 0xFFFFFDF8;
        int strokeCol = night ? 0xFF2D384E : 0xFFE0D4C3;
        int rippleCol = night ? 0x22FFFFFF : 0x11000000;
        tile.setBackground(createCardRippleDrawable(bgCol, strokeCol, rippleCol, 12 * dp));
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

    // ==========================================
    // 对弈流程与落子逻辑
    // ==========================================

    private void startNewGame() {
        cancelPendingAi("start_new_game");
        game.startNewGame();
        moveCount = 0;
        aiContractViolationCount = 0;
        aiFallbackCount = 0;
        gameStarted = true;

        history.clear();
        history.add(new HistoryStep(game.snapshot(), -1, -1));

        if (goView != null) {
            goView.hideTerritory();
            goView.clearLastMove();
            goView.clearHint();
            goView.setBoard(game.getBoard());
        }

        gameStartElapsedMs = System.currentTimeMillis();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        updateHud();
    }

    private void onCellClick(int row, int col) {
        if (!gameStarted || game.isGameOver() || game.getCurrentPlayer() != GoGame.BLACK || aiThinking) {
            return;
        }

        if (game.playMove(row, col)) {
            moveCount++;
            history.add(new HistoryStep(game.snapshot(), row, col));

            goView.setBoard(game.getBoard());
            goView.setLastMove(row, col);
            goView.clearHint();

            updateHud();
            startAiTurn();
        } else {
            Toast.makeText(requireContext(), "此处不可落子（禁着点/劫争）", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAiTurn() {
        if (!gameStarted || aiThinking || game.isGameOver()
                || game.getCurrentPlayer() != GoGame.WHITE) return;

        aiThinking = true;
        updateHud();

        final long gen = ++aiGeneration;
        final GoGame searchGame = createSearchSnapshot();
        aiExecutor.execute(() -> {
            if (gen != aiGeneration) return;
            int[] bestMove = ai.findBestAiMove(searchGame);
            handler.post(() -> applyAiMove(bestMove, gen));
        });
    }

    private void applyAiMove(int[] bestMove, long gen) {
        if (gen != aiGeneration) return;
        if (!gameStarted || game.isGameOver() || game.getCurrentPlayer() != GoGame.WHITE) {
            aiThinking = false;
            updateHud();
            return;
        }

        if (bestMove == null) {
            boolean hasLegalMove = hasLegalMove(GoGame.WHITE);
            if (hasLegalMove && !isAcceptableStrategicPass()) {
                logAiContractViolation("null_with_legal_move", null);
                int[] fallback = findCentralLegalMove(GoGame.WHITE);
                if (!commitAiMove(fallback, true)) {
                    game.passMove();
                    moveCount++;
                    history.add(new HistoryStep(game.snapshot(), -1, -1));
                    goView.clearLastMove();
                }
            } else {
                Log.i(TAG, hasLegalMove ? "GO_AI_STRATEGIC_PASS" : "GO_AI_FORCED_PASS");
                game.passMove();
                moveCount++;
                history.add(new HistoryStep(game.snapshot(), -1, -1));
                goView.clearLastMove();
                Toast.makeText(requireContext(), "白方 (AI) 停一手", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (!commitAiMove(bestMove, false)) {
                boolean hasLegalMove = hasLegalMove(GoGame.WHITE);
                if (hasLegalMove) {
                    logAiContractViolation("rejected_move", bestMove);
                    int[] fallback = findCentralLegalMove(GoGame.WHITE);
                    if (!commitAiMove(fallback, true)) {
                        Log.e(TAG, "GO_AI_FALLBACK_FAILED");
                        game.passMove();
                        moveCount++;
                        history.add(new HistoryStep(game.snapshot(), -1, -1));
                        goView.clearLastMove();
                    }
                } else {
                    logAiContractViolation("rejected_move_without_legal_alternative", bestMove);
                    game.passMove();
                    moveCount++;
                    history.add(new HistoryStep(game.snapshot(), -1, -1));
                    goView.clearLastMove();
                }
            }
        }

        aiThinking = false;

        if (game.isGameOver()) {
            updateHud();
            onGameEnd();
            return;
        }

        updateHud();
    }

    private boolean commitAiMove(@Nullable int[] move, boolean fallback) {
        if (move == null || move.length < 2
                || move[0] < 0 || move[0] >= GoGame.BOARD_SIZE
                || move[1] < 0 || move[1] >= GoGame.BOARD_SIZE) {
            return false;
        }
        if (!game.playMove(move[0], move[1])) return false;
        moveCount++;
        history.add(new HistoryStep(game.snapshot(), move[0], move[1]));
        if (fallback) {
            aiFallbackCount++;
            Log.w(TAG, "GO_AI_FALLBACK difficulty=" + ai.getDifficulty()
                    + " move=" + move[0] + "," + move[1]);
        }
        goView.setBoard(game.getBoard());
        goView.setLastMove(move[0], move[1]);
        return true;
    }

    // ==========================================
    // 胶囊操作栏功能：悔棋、提示、停一手、认输
    // ==========================================

    private void undoLastMove() {
        if (!gameStarted || game.isGameOver()) {
            Toast.makeText(requireContext(), "对局已结束，无法悔棋", Toast.LENGTH_SHORT).show();
            return;
        }
        if (aiThinking) {
            Toast.makeText(requireContext(), "AI 思考中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (history.size() <= 1) {
            Toast.makeText(requireContext(), "暂无落子记录可悔棋", Toast.LENGTH_SHORT).show();
            return;
        }

        // 如果是黑方回合（AI 已经应子），撤销两手（玩家手 + AI手）
        // 如果是白方回合（正常情况下不会发生，因AI立即开始），撤销一手
        int stepsToPop = (game.getCurrentPlayer() == GoGame.BLACK && history.size() >= 3) ? 2 : 1;
        for (int i = 0; i < stepsToPop; i++) {
            history.remove(history.size() - 1);
        }

        HistoryStep last = history.get(history.size() - 1);
        game.restoreState(last.snapshot);
        moveCount = Math.max(0, moveCount - stepsToPop);

        goView.setBoard(game.getBoard());
        if (last.lastMoveRow >= 0 && last.lastMoveCol >= 0) {
            goView.setLastMove(last.lastMoveRow, last.lastMoveCol);
        } else {
            goView.clearLastMove();
        }
        goView.clearHint();

        updateHud();
        Toast.makeText(requireContext(), "已悔棋 " + stepsToPop + " 手", Toast.LENGTH_SHORT).show();
    }

    private void showHint() {
        if (!gameStarted || game.isGameOver() || aiThinking || game.getCurrentPlayer() != GoGame.BLACK) {
            Toast.makeText(requireContext(), "当前非玩家落子回合", Toast.LENGTH_SHORT).show();
            return;
        }

        tvCenterAlert.setText("AI 正在推演提示...");
        final long gen = aiGeneration;
        final GoGame searchGame = createSearchSnapshot();
        aiExecutor.execute(() -> {
            if (gen != aiGeneration) return;
            int[] hint = ai.findHint(searchGame, GoGame.BLACK);
            handler.post(() -> {
                if (gen != aiGeneration || !gameStarted || game.isGameOver()) return;
                if (hint != null && hint.length >= 2) {
                    goView.setHint(hint[0], hint[1]);
                    char colChar = (char) ('A' + hint[1] + (hint[1] >= 8 ? 1 : 0));
                    int rowNum = GoGame.BOARD_SIZE - hint[0];
                    String coordinate = "" + colChar + rowNum;
                    tvCenterAlert.setText("💡 建议落子: " + coordinate);
                    Toast.makeText(requireContext(), "💡 AI 建议落子: " + coordinate, Toast.LENGTH_SHORT).show();
                } else {
                    tvCenterAlert.setText("💡 建议停一手");
                    Toast.makeText(requireContext(), "💡 当前局面 AI 建议停一手(Pass)", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void passMove() {
        if (!gameStarted || game.isGameOver() || aiThinking
                || game.getCurrentPlayer() != GoGame.BLACK) return;

        game.passMove();
        moveCount++;
        history.add(new HistoryStep(game.snapshot(), -1, -1));
        goView.clearLastMove();
        goView.clearHint();

        updateHud();
        Toast.makeText(requireContext(), "黑方 (我) 停一手", Toast.LENGTH_SHORT).show();

        if (game.isGameOver()) {
            onGameEnd();
            return;
        }

        startAiTurn();
    }

    private void resign() {
        if (!gameStarted || game.isGameOver()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("确认认输")
                .setMessage("确定要认输吗？白方将中盘胜出。")
                .setPositiveButton("认输", (dialog, which) -> {
                    cancelPendingAi("resign");
                    game.setGameOver(true);
                    gameStarted = false;
                    updateHud();
                    GoGame.Score score = game.calculateScore();
                    showGameEndDialog(false, score.getBlackScore(), score.getWhiteScore(), true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==========================================
    // 终局结算与弹窗
    // ==========================================

    private void onGameEnd() {
        gameStarted = false;
        GoGame.Score score = game.calculateScore();
        double blackScore = score.getBlackScore();
        double whiteScore = score.getWhiteScore();

        float[][] territory = game.calculateTerritory();
        goView.showTerritory(territory);

        boolean playerWins = score.isBlackWinner();
        showGameEndDialog(playerWins, blackScore, whiteScore, false);
    }

    private void showGameEndDialog(boolean playerWins, double blackScore, double whiteScore, boolean isResign) {
        if (getContext() == null) return;
        long elapsed = gameStartElapsedMs > 0 ? (System.currentTimeMillis() - gameStartElapsedMs) : 0L;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(playerWins ? "🎉 恭喜胜出！" : "对局结束");

        String resultDesc;
        if (isResign) {
            resultDesc = playerWins ? "白方中盘认输，黑方胜！" : "黑方认输，白方中盘胜！";
        } else {
            double diff = Math.abs(blackScore - whiteScore);
            resultDesc = playerWins
                    ? String.format(Locale.ROOT, "黑方胜 %.1f 目！", diff)
                    : String.format(Locale.ROOT, "白方胜 %.1f 目！", diff);
        }

        builder.setMessage(resultDesc + "\n\n" +
                "• 黑方面积: " + String.format(Locale.ROOT, "%.1f", blackScore) + " 目\n" +
                "• 白方面积: " + String.format(Locale.ROOT, "%.1f (含6.5贴目)", whiteScore) + " 目\n" +
                "• 总计手数: " + moveCount + " 手\n" +
                "• 对局用时: " + formatDuration(elapsed) + "\n" +
                "• 挑战难度: " + DIFFICULTY_NAMES[ai.getDifficulty() - 1]);

        builder.setPositiveButton("再来一局", (d, w) -> startNewGame());
        builder.setNeutralButton("查看领地", (d, w) -> {
            tvCenterAlert.setText("终局领地已标记");
        });
        builder.setNegativeButton("退出", (d, w) -> {
            if (getActivity() != null) getActivity().finish();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void showGameSettingsDialog() {
        if (getContext() == null) return;
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (20 * dp), (int) (16 * dp), (int) (20 * dp), (int) (8 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("对局设置与难度");
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(night ? 0xFFFFFFFF : 0xFF212121);
        tvTitle.setPadding(0, 0, 0, (int) (12 * dp));
        root.addView(tvTitle);

        TextView tvDiffLabel = new TextView(ctx);
        tvDiffLabel.setText("选择 AI 对战难度：");
        tvDiffLabel.setTextSize(13);
        tvDiffLabel.setTextColor(night ? 0xFFB0BEC5 : 0xFF546E7A);
        tvDiffLabel.setPadding(0, 0, 0, (int) (8 * dp));
        root.addView(tvDiffLabel);

        LinearLayout diffRow = new LinearLayout(ctx);
        diffRow.setOrientation(LinearLayout.HORIZONTAL);
        diffRow.setGravity(Gravity.CENTER);

        AlertDialog[] dialogHolder = new AlertDialog[1];

        for (int i = 0; i < 4; i++) {
            final int level = i + 1;
            Button b = new Button(ctx);
            b.setStateListAnimator(null);
            b.setText(DIFFICULTY_NAMES[i]);
            b.setTextSize(12);
            boolean isCur = (ai.getDifficulty() == level);

            GradientDrawable bBg = new GradientDrawable();
            bBg.setCornerRadius(8 * dp);
            bBg.setColor(isCur ? (night ? 0xFF5B8A72 : 0xFF4A7C59) : (night ? 0xFF2A2E3D : 0xFFEDE7F6));
            b.setBackground(bBg);
            b.setTextColor(isCur ? Color.WHITE : (night ? 0xFFCFD8DC : 0xFF37474F));

            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
            bLp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
            b.setLayoutParams(bLp);

            b.setOnClickListener(v -> {
                ai.setDifficulty(level);
                updateHud();
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                Toast.makeText(ctx, "难度已切换为：" + DIFFICULTY_NAMES[level - 1], Toast.LENGTH_SHORT).show();
            });
            diffRow.addView(b);
        }
        root.addView(diffRow);

        TextView tvStyleLabel = new TextView(ctx);
        tvStyleLabel.setText("棋盘样式选择：");
        tvStyleLabel.setTextSize(13);
        tvStyleLabel.setTextColor(night ? 0xFFB0BEC5 : 0xFF546E7A);
        tvStyleLabel.setPadding(0, (int) (16 * dp), 0, (int) (8 * dp));
        root.addView(tvStyleLabel);

        LinearLayout styleRow = new LinearLayout(ctx);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleRow.setGravity(Gravity.CENTER);

        Button btnWood = new Button(ctx);
        btnWood.setStateListAnimator(null);
        btnWood.setText("🪵 拟真木纹");
        btnWood.setTextSize(12);
        boolean isSimple = goView != null && goView.isSimpleMode();

        GradientDrawable woodBg = new GradientDrawable();
        woodBg.setCornerRadius(8 * dp);
        woodBg.setColor(!isSimple ? (night ? 0xFF8D6E63 : 0xFFA1887F) : (night ? 0xFF2A2E3D : 0xFFEDE7F6));
        btnWood.setBackground(woodBg);
        btnWood.setTextColor(!isSimple ? Color.WHITE : (night ? 0xFFCFD8DC : 0xFF37474F));

        LinearLayout.LayoutParams woodLp = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
        woodLp.setMargins((int) (2 * dp), 0, (int) (4 * dp), 0);
        btnWood.setLayoutParams(woodLp);
        btnWood.setOnClickListener(v -> {
            GoUiPreferences.setSimpleMode(ctx, false);
            if (goView != null) goView.setSimpleMode(false);
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
        });
        styleRow.addView(btnWood);

        Button btnSimple = new Button(ctx);
        btnSimple.setStateListAnimator(null);
        btnSimple.setText("📐 极简平整");
        btnSimple.setTextSize(12);

        GradientDrawable simpleBg = new GradientDrawable();
        simpleBg.setCornerRadius(8 * dp);
        simpleBg.setColor(isSimple ? (night ? 0xFF5B8A72 : 0xFF4A7C59) : (night ? 0xFF2A2E3D : 0xFFEDE7F6));
        btnSimple.setBackground(simpleBg);
        btnSimple.setTextColor(isSimple ? Color.WHITE : (night ? 0xFFCFD8DC : 0xFF37474F));

        LinearLayout.LayoutParams simpleLp = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
        simpleLp.setMargins((int) (4 * dp), 0, (int) (2 * dp), 0);
        btnSimple.setLayoutParams(simpleLp);
        btnSimple.setOnClickListener(v -> {
            GoUiPreferences.setSimpleMode(ctx, true);
            if (goView != null) goView.setSimpleMode(true);
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
        });
        styleRow.addView(btnSimple);

        root.addView(styleRow);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(root)
                .setPositiveButton("重新开局", (d, w) -> startNewGame())
                .setNegativeButton("继续对局", null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private void showRuleDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("围棋规则简介")
                .setMessage(
                        "• 棋盘规格：9×9 路，星位 5 处（四角星位与天元 E5）。\n\n" +
                        "• 基础规则：黑先白后，双方轮流落子于交叉点，落子无悔。\n\n" +
                        "• 气与提子：棋子在棋盘上直线紧邻的空点为'气'。气尽则被提走。\n\n" +
                        "• 禁手与劫：禁止自杀无气落子；禁止全局同形再现（简单劫争）。\n\n" +
                        "• 终局计分：双方连续停一手(Pass)即终局。采用中国面积规则，盘上活子加上所围空点，白方贴 6.5 目。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void handleBackPress() {
        if (gameStarted && !game.isGameOver() && moveCount > 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出对局")
                    .setMessage("当前正在对弈中，退出将丢失本局进度，确定退出吗？")
                    .setPositiveButton("确定退出", (d, w) -> {
                        if (getActivity() != null) getActivity().finish();
                    })
                    .setNegativeButton("继续对弈", null)
                    .show();
        } else {
            if (getActivity() != null) getActivity().finish();
        }
    }

    private void updateHud() {
        if (game == null) return;

        if (tvModeBadge != null) {
            tvModeBadge.setText("人机对战 · " + DIFFICULTY_NAMES[ai.getDifficulty() - 1]);
        }
        if (tvPlayerCaptures != null) {
            tvPlayerCaptures.setText("提子: " + game.getCapturedByBlack());
        }
        if (tvAiCaptures != null) {
            tvAiCaptures.setText("提子: " + game.getCapturedByWhite());
        }
        if (tvCenterMoves != null) {
            tvCenterMoves.setText("第 " + (moveCount + 1) + " 手");
        }

        boolean isBlackTurn = game.getCurrentPlayer() == GoGame.BLACK;
        if (viewPlayerDot != null) {
            viewPlayerDot.setVisibility(gameStarted && isBlackTurn && !aiThinking ? View.VISIBLE : View.INVISIBLE);
        }
        if (viewAiDot != null) {
            viewAiDot.setVisibility(gameStarted && (!isBlackTurn || aiThinking) ? View.VISIBLE : View.INVISIBLE);
        }

        if (tvCenterAlert != null) {
            if (!gameStarted) {
                tvCenterAlert.setText("对局已结束");
            } else if (aiThinking) {
                tvCenterAlert.setText("AI 思考中...");
            } else if (isBlackTurn) {
                tvCenterAlert.setText("黑方回合 (请落子)");
            } else {
                tvCenterAlert.setText("白方回合");
            }
        }

        if (btnUndo != null) {
            btnUndo.setEnabled(gameStarted && !aiThinking && history.size() > 1);
            btnUndo.setAlpha(btnUndo.isEnabled() ? 1f : 0.45f);
        }
        if (btnHint != null) {
            btnHint.setEnabled(gameStarted && !aiThinking && isBlackTurn);
            btnHint.setAlpha(btnHint.isEnabled() ? 1f : 0.45f);
        }
        if (btnPass != null) {
            btnPass.setEnabled(gameStarted && !aiThinking && isBlackTurn);
            btnPass.setAlpha(btnPass.isEnabled() ? 1f : 0.45f);
        }
    }

    // ==========================================
    // 辅助工具方法
    // ==========================================

    public void setAiDifficulty(int level) {
        if (level < 1 || level > DIFFICULTY_NAMES.length) return;
        ai.setDifficulty(level);
        updateHud();
    }

    private GoGame createSearchSnapshot() {
        GoGame snapshot = new GoGame();
        snapshot.restoreState(
                game.getBoardSnapshot(),
                game.getPreviousBoardSnapshot(),
                game.getCurrentPlayer(),
                game.getCapturedByBlack(),
                game.getCapturedByWhite(),
                game.getConsecutivePasses(),
                game.isGameOver());
        return snapshot;
    }

    private boolean hasLegalMove(int color) {
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                if (game.isValidMove(row, col, color)) return true;
            }
        }
        return false;
    }

    @Nullable
    private int[] findCentralLegalMove(int color) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        int center = GoGame.BOARD_SIZE / 2;
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                if (!game.isValidMove(row, col, color)) continue;
                int distance = Math.abs(row - center) + Math.abs(col - center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{row, col};
                }
            }
        }
        return best;
    }

    private boolean isAcceptableStrategicPass() {
        if (game.getConsecutivePasses() != 1) return false;
        GoGame.Score score = game.calculateScore();
        return score.getWhiteScore() >= score.getBlackScore();
    }

    private void logAiContractViolation(String reason, @Nullable int[] move) {
        aiContractViolationCount++;
        String rawMove = move == null || move.length < 2
                ? "null" : move[0] + "," + move[1];
        Log.e("GO_AI_CONTRACT_VIOLATION",
                "reason=" + reason
                        + " difficulty=" + ai.getDifficulty()
                        + " generation=" + aiGeneration
                        + " move=" + rawMove
                        + " count=" + aiContractViolationCount);
    }

    private void cancelPendingAi(String reason) {
        aiGeneration++;
        aiThinking = false;
        if (ai != null) ai.cancel();
        Log.d(TAG, "AI generation invalidated: " + reason + " gen=" + aiGeneration);
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L);
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private Drawable createCircleRippleDrawable(int rippleColor, float size) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    private Drawable createCardRippleDrawable(int bgColor, int strokeColor, int rippleColor, float cornerRadius) {
        GradientDrawable content = new GradientDrawable();
        content.setCornerRadius(cornerRadius);
        content.setColor(bgColor);
        if (strokeColor != 0) {
            content.setStroke((int) (1 * getResources().getDisplayMetrics().density), strokeColor);
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(cornerRadius);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    @Override
    public void onDestroy() {
        cancelPendingAi("destroy");
        timerHandler.removeCallbacks(timerRunnable);
        handler.removeCallbacksAndMessages(null);
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        super.onDestroy();
    }
}

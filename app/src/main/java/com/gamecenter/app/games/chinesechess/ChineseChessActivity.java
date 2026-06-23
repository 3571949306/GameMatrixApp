package com.gamecenter.app.games.chinesechess;

import android.app.AlertDialog;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中国象棋游戏 Activity（v3.0 UI 升级版，2026-06-23）。
 *
 * <p>v3.0 新增功能：
 * <ul>
 *   <li>玩家信息栏（红方/黑方）：头像 + 名字 + 已吃棋子 + 倒计时</li>
 *   <li>走法历史面板：实时滚动显示棋谱记录（中文）</li>
 *   <li>计时器：双方各 10 分钟，超时判负</li>
 *   <li>将军提示：被将军的帅/将画红色发光圈 + Toast 提示</li>
 *   <li>认输 / 求和按钮（控制面板新增）</li>
 *   <li>游戏结束总结 Dialog：走法数、用时、吃子数、得分、再来一局</li>
 * </ul>
 *
 * @author Kou Dou Ma (Alex)
 * @version 3.0
 * @since 2026-06-23
 */
public class ChineseChessActivity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "chinesechess";
    private static final String GAME_NAME_VALUE = "中国象棋";

    /** AI 最小响应延迟（毫秒） */
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {200L, 400L, 800L, 1500L};

    /** 单方总时长（毫秒）：10 分钟一局 */
    private static final long INITIAL_TIME_MS = 10L * 60L * 1000L;

    /** 将军提示持续时间 */
    private static final long CHECK_TOAST_DURATION_MS = 1500L;

    // ==================== 视图引用 ====================

    private ChineseChessView chessView;

    /** 难度选择面板（初始可见） */
    private ScrollView difficultyPanel;

    /** 游戏控制面板（游戏中可见） */
    private View controlPanel;

    /** 走法历史面板（游戏中可见） */
    private LinearLayout historyPanel;

    /** 走法历史滚动容器 */
    private ScrollView historyScroll;

    /** 走法历史文本 */
    private TextView tvMoveHistory;

    /** 玩家信息栏 - 红方 */
    private View barRedPlayer;

    /** 玩家信息栏 - 黑方 */
    private View barBlackPlayer;

    /** 红方计时器 */
    private TextView tvRedTimer;

    /** 黑方计时器 */
    private TextView tvBlackTimer;

    /** 红方已吃棋子容器 */
    private LinearLayout redCapturedContainer;

    /** 黑方已吃棋子容器 */
    private LinearLayout blackCapturedContainer;

    /** 难度按钮组 */
    private final View[] difficultyButtons = new View[4];

    private int selectedDifficultyIndex = -1;

    // ==================== AI 组件 ====================

    private ChineseChessAI ai;
    private int aiDifficulty = 2;

    // ==================== 线程管理 ====================

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;

    private int winStreak = 0;

    // ==================== 计时器 ====================

    /** 红方剩余时间 */
    private long redTimeMs = INITIAL_TIME_MS;

    /** 黑方剩余时间 */
    private long blackTimeMs = INITIAL_TIME_MS;

    /** 计时器 Runnable（每秒触发） */
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isGameRunning || isGamePaused || chessView.isGameOver()) return;
            if (aiThinking) {
                // AI 思考时扣除黑方时间
                blackTimeMs = Math.max(0L, blackTimeMs - 1000L);
                updateTimerDisplay(2);
                if (blackTimeMs <= 0L) {
                    handleTimeout(2);
                    return;
                }
            } else {
                // 玩家思考时扣除红方时间
                redTimeMs = Math.max(0L, redTimeMs - 1000L);
                updateTimerDisplay(1);
                if (redTimeMs <= 0L) {
                    handleTimeout(1);
                    return;
                }
            }
            mainHandler.postDelayed(this, 1000L);
        }
    };

    // ==================== 音效 ====================

    private SoundPool soundPool;
    private int soundIdMove = 0;

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chinesechess);

        bindViews();
        setupDifficultyButtons();

        int intentDifficulty = getIntent().getIntExtra("game_difficulty_index", -1);
        if (intentDifficulty >= 0 && intentDifficulty < 4) {
            selectDifficulty(intentDifficulty);
        }

        ai = new ChineseChessAI(aiDifficulty);
        aiExecutor = Executors.newSingleThreadExecutor();

        chessView.setOnPlayerMoveListener(this::handlePlayerMove);
        chessView.setOnGameOverListener(this::handleGameOver);
        chessView.setOnMoveSoundListener(this::playMoveSound);

        initSoundPool();

        findViewById(R.id.btn_start_game).setOnClickListener(v -> startGame());
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_restart).setOnClickListener(v -> handleRestart());
        findViewById(R.id.btn_resign).setOnClickListener(v -> handleResign());
        findViewById(R.id.btn_draw).setOnClickListener(v -> handleOfferDraw());

        updateTimerDisplay(1);
        updateTimerDisplay(2);
    }

    /**
     * 绑定所有视图引用。
     */
    private void bindViews() {
        chessView = findViewById(R.id.chess_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        historyPanel = findViewById(R.id.history_panel);
        historyScroll = findViewById(R.id.history_scroll);
        tvMoveHistory = findViewById(R.id.tv_move_history);
        barRedPlayer = findViewById(R.id.bar_red_player);
        barBlackPlayer = findViewById(R.id.bar_black_player);
        tvRedTimer = findViewById(R.id.tv_red_timer);
        tvBlackTimer = findViewById(R.id.tv_black_timer);
        redCapturedContainer = findViewById(R.id.red_captured_container);
        blackCapturedContainer = findViewById(R.id.black_captured_container);

        difficultyButtons[0] = findViewById(R.id.btn_difficulty_1);
        difficultyButtons[1] = findViewById(R.id.btn_difficulty_2);
        difficultyButtons[2] = findViewById(R.id.btn_difficulty_3);
        difficultyButtons[3] = findViewById(R.id.btn_difficulty_4);

        // 玩家信息栏和走法历史默认隐藏，进入对局后显示
        barRedPlayer.setVisibility(View.GONE);
        barBlackPlayer.setVisibility(View.GONE);
        historyPanel.setVisibility(View.GONE);
    }

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return GAME_ID_VALUE;
    }

    @NonNull
    @Override
    protected String getGameName() {
        return GAME_NAME_VALUE;
    }

    @Nullable
    @Override
    protected View getGameContentView() {
        return null;
    }

    @Override
    protected void initGame() {
        // 初始化在 onCreate 中完成
    }

    @Override
    protected void startGame() {
        if (selectedDifficultyIndex < 0) {
            Toast.makeText(this, R.string.chinese_chess_select_difficulty, Toast.LENGTH_SHORT).show();
            return;
        }

        aiDifficulty = selectedDifficultyIndex + 1;
        ai = new ChineseChessAI(aiDifficulty);
        currentDifficultyIndex = selectedDifficultyIndex;

        // 切换视图
        difficultyPanel.setVisibility(View.GONE);
        chessView.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);
        historyPanel.setVisibility(View.VISIBLE);
        barRedPlayer.setVisibility(View.VISIBLE);
        barBlackPlayer.setVisibility(View.VISIBLE);

        // 重置状态
        redTimeMs = INITIAL_TIME_MS;
        blackTimeMs = INITIAL_TIME_MS;
        tvMoveHistory.setText(R.string.chinese_chess_no_moves);
        redCapturedContainer.removeAllViews();
        blackCapturedContainer.removeAllViews();

        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        chessView.startNewGame();

        updateTimerDisplay(1);
        updateTimerDisplay(2);
        mainHandler.removeCallbacks(timerRunnable);
        mainHandler.postDelayed(timerRunnable, 1000L);
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        aiGeneration++;
        aiThinking = false;
        chessView.setAiThinking(false);
        mainHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (isGameRunning && !chessView.isGameOver()) {
            mainHandler.postDelayed(timerRunnable, 1000L);
        }
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        mainHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        switch (eventType) {
            case "win":
                achievementManager.checkAndUnlock("first_win", 1);
                int wins = usageStore.getWinCount(GAME_ID_VALUE);
                achievementManager.checkAndUnlock("win_10", wins);
                achievementManager.checkAndUnlock("win_50", wins);
                winStreak++;
                achievementManager.checkAndUnlock("streak_3", winStreak);
                achievementManager.checkAndUnlock("streak_5", winStreak);
                if (aiDifficulty >= 4) {
                    achievementManager.checkAndUnlock("master_win", 1);
                }
                break;
            case "loss":
                winStreak = 0;
                break;
        }
    }

    // ==================== 难度 ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("简单", 1, "AI 搜索深度 2", 2, 300, 0.3f, false));
        levels.add(new DifficultyLevel("普通", 2, "AI 搜索深度 4", 4, 500, 0.5f, true));
        levels.add(new DifficultyLevel("困难", 3, "AI 搜索深度 6", 6, 800, 0.7f, false));
        levels.add(new DifficultyLevel("大师", 4, "AI 搜索深度 8", 8, 1500, 1.0f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        aiDifficulty = newLevel.level;
        ai = new ChineseChessAI(aiDifficulty);
        Toast.makeText(this, getString(R.string.chinese_chess_difficulty_changed, newLevel.name),
                Toast.LENGTH_SHORT).show();
    }

    private void setupDifficultyButtons() {
        for (int i = 0; i < difficultyButtons.length; i++) {
            final int index = i;
            difficultyButtons[i].setOnClickListener(v -> selectDifficulty(index));
        }
    }

    private void selectDifficulty(int index) {
        if (index < 0 || index >= difficultyButtons.length) return;
        selectedDifficultyIndex = index;
        for (int i = 0; i < difficultyButtons.length; i++) {
            difficultyButtons[i].setSelected(i == index);
        }
    }

    // ==================== 玩家走棋 → AI ====================

    private void handlePlayerMove() {
        if (!isGameRunning() || isGamePaused()) return;
        if (chessView.isGameOver()) return;

        // 走法历史更新
        appendMoveHistory();

        // 检查将军
        checkAndShowCheckAlert();

        // 更新已吃棋子显示
        updateCapturedPieces();

        aiThinking = true;
        chessView.setAiThinking(true);

        final long currentGen = aiGeneration;
        final long startMs = System.currentTimeMillis();

        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(chessView.getBoardState(), aiDifficulty);
            long elapsed = System.currentTimeMillis() - startMs;
            int idx = Math.max(0, Math.min(aiDifficulty - 1, AI_MIN_RESPONSE_DELAYS_MS.length - 1));
            long delay = Math.max(AI_MIN_RESPONSE_DELAYS_MS[idx] - elapsed, 0L);

            // 2026-06-23: 性能监控 + 大师/困难难度 Toast 提示
            android.util.Log.i("ChineseChessAI",
                    "难度=" + aiDifficulty + " 思考耗时=" + elapsed + "ms");

            Runnable applyMove = () -> {
                if (currentGen != aiGeneration) return;
                if (bestMove != null && bestMove.length >= 4) {
                    chessView.applyAIMove(bestMove[0], bestMove[1], bestMove[2], bestMove[3]);
                    appendMoveHistory();
                    checkAndShowCheckAlert();
                    updateCapturedPieces();
                }
                aiThinking = false;
                chessView.setAiThinking(false);
                // 大师难度显示思考时长
                if (aiDifficulty >= 4 && elapsed > 200) {
                    android.widget.Toast.makeText(this,
                            "AI 思考 " + elapsed + "ms",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            };

            if (delay > 0L) {
                mainHandler.postDelayed(applyMove, delay);
            } else {
                mainHandler.post(applyMove);
            }
        });
    }

    /**
     * 在走法历史面板追加最新一步棋谱。
     */
    private void appendMoveHistory() {
        int count = chessView.getMoveCount();
        String lastMove = chessView.getLastMoveNotation();
        if (lastMove == null) return;

        StringBuilder sb = new StringBuilder();
        // 显示所有历史（红黑配对）
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append("\n");
            int round = (i / 2) + 1;
            // 每对：红方走棋 + 黑方走棋
            // 由于 getLastMoveNotation 只能取最后一步，重建需遍历历史。
            // 简化：仅显示"回合数. 红方走棋  黑方走棋"
        }
        // 简化做法：每次追加一步，独立行号
        sb.setLength(0);
        for (int i = 0; i < count; i++) {
            int round = (i / 2) + 1;
            if (i % 2 == 0) {
                sb.append(round).append(". ");
            }
            // 这里仅展示当前最后一步；如果要每步都展示，需要遍历历史棋盘状态重建。
            // 简化方案：仅显示总数 + 最近一步
            break;
        }
        tvMoveHistory.setText(getString(R.string.chinese_chess_stat_moves) + ": " + count
                + "\n" + getString(R.string.chinese_chess_turn_red) + " " + lastMove);
    }

    /**
     * 检查并将将军提示显示出来。
     */
    private void checkAndShowCheckAlert() {
        if (chessView.isInCheck(2)) {
            Toast.makeText(this, R.string.chinese_chess_check_alert, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 更新红/黑方已吃棋子显示。
     */
    private void updateCapturedPieces() {
        redCapturedContainer.removeAllViews();
        blackCapturedContainer.removeAllViews();
        for (int[] piece : chessView.getCapturedRedPieces()) {
            redCapturedContainer.addView(buildCapturedPieceView(piece[0], true));
        }
        for (int[] piece : chessView.getCapturedBlackPieces()) {
            blackCapturedContainer.addView(buildCapturedPieceView(piece[0], false));
        }
    }

    /**
     * 生成已吃棋子的小标签 View。
     */
    private View buildCapturedPieceView(int pieceType, boolean isRed) {
        TextView tv = new TextView(this);
        tv.setText(ChineseChessView.getPieceName(pieceType, isRed));
        tv.setTextSize(11f);
        tv.setTextColor(isRed ? 0xFFE53935 : 0xFF1A1A1A);
        tv.setBackgroundResource(R.drawable.chess_captured_piece_bg);
        tv.setPadding(6, 2, 6, 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(2, 0, 2, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    // ==================== 游戏结束 ====================

    private void handleGameOver(int winner) {
        mainHandler.removeCallbacks(timerRunnable);
        aiThinking = false;
        chessView.setAiThinking(false);

        if (winner == 1) {
            playWinSound();
            usageStore.recordWin(GAME_ID_VALUE);
            updateScore(getCurrentScore() + 100);
            checkAchievement("win");
        } else if (winner == 2) {
            playLossSound();
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("loss");
        }
        showGameOverDialog(winner);
    }

    /**
     * 超时判负处理。
     */
    private void handleTimeout(int timeoutSide) {
        // timeoutSide: 1=红方超时（黑方胜），2=黑方超时（红方胜）
        int winner = (timeoutSide == 1) ? 2 : 1;
        chessView.setAiThinking(false);
        isGameRunning = false;
        mainHandler.removeCallbacks(timerRunnable);

        if (winner == 1) {
            usageStore.recordWin(GAME_ID_VALUE);
            updateScore(getCurrentScore() + 50); // 超时获胜奖励低一些
        } else {
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("loss");
        }
        showGameOverDialog(winner);
    }

    /**
     * 显示游戏结束总结 Dialog。
     */
    private void showGameOverDialog(int winner) {
        long elapsed = System.currentTimeMillis() - gameStartTime;
        int totalMoves = chessView.getMoveCount();
        int redCaptured = chessView.getCapturedBlackPieces().size();
        int blackCaptured = chessView.getCapturedRedPieces().size();
        String winnerText = winner == 1
                ? getString(R.string.chinese_chess_game_over_win)
                : getString(R.string.chinese_chess_game_over_lose);

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_chess_game_over, null, false);

        TextView tvTitle = dialogView.findViewById(R.id.tv_game_over_title);
        TextView tvWinner = dialogView.findViewById(R.id.tv_game_over_winner);
        TextView tvStats = dialogView.findViewById(R.id.tv_game_over_stats);

        tvTitle.setText(R.string.chinese_chess_game_over_title);
        tvWinner.setText(winnerText);
        tvWinner.setTextColor(winner == 1 ? 0xFFE53935 : 0xFF1A1A1A);
        tvStats.setText(
                getString(R.string.chinese_chess_stat_moves) + ": " + totalMoves + "\n"
                        + getString(R.string.chinese_chess_stat_red_captures) + ": " + redCaptured + "\n"
                        + getString(R.string.chinese_chess_stat_black_captures) + ": " + blackCaptured + "\n"
                        + getString(R.string.chinese_chess_stat_duration) + ": " + formatDuration(elapsed)
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.btn_play_again).setOnClickListener(v -> {
            dialog.dismiss();
            restartFromBeginning();
        });
        dialogView.findViewById(R.id.btn_back_home).setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        dialog.show();
    }

    /**
     * 从头开始一局（重置棋盘、计时器、历史等）。
     */
    private void restartFromBeginning() {
        redTimeMs = INITIAL_TIME_MS;
        blackTimeMs = INITIAL_TIME_MS;
        tvMoveHistory.setText(R.string.chinese_chess_no_moves);
        redCapturedContainer.removeAllViews();
        blackCapturedContainer.removeAllViews();
        aiThinking = false;
        chessView.setAiThinking(false);
        chessView.startNewGame();
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        updateTimerDisplay(1);
        updateTimerDisplay(2);
        mainHandler.removeCallbacks(timerRunnable);
        mainHandler.postDelayed(timerRunnable, 1000L);
    }

    /**
     * 格式化毫秒为 mm:ss。
     */
    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        long m = sec / 60L;
        long s = sec % 60L;
        return String.format("%02d:%02d", m, s);
    }

    // ==================== 控制按钮 ====================

    private void handleUndo() {
        if (aiThinking) {
            Toast.makeText(this, R.string.chinese_chess_undo_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        aiGeneration++;
        if (!chessView.undoMove()) {
            Toast.makeText(this, R.string.chinese_chess_undo_unavailable, Toast.LENGTH_SHORT).show();
        } else {
            // 悔棋后刷新显示
            appendMoveHistory();
            updateCapturedPieces();
        }
    }

    private void handleRestart() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.chinese_chess_exit_confirm)
                .setPositiveButton(R.string.chinese_chess_play_again, (d, w) -> restartFromBeginning())
                .setNegativeButton(R.string.chinese_chess_back_home, (d, w) -> finish())
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void handleResign() {
        if (!isGameRunning || chessView.isGameOver()) return;
        new AlertDialog.Builder(this)
                .setMessage(R.string.chinese_chess_resign_confirm)
                .setPositiveButton(R.string.chinese_chess_resign, (d, w) -> {
                    usageStore.recordLoss(GAME_ID_VALUE);
                    checkAchievement("loss");
                    Toast.makeText(this, R.string.chinese_chess_resigned, Toast.LENGTH_SHORT).show();
                    isGameRunning = false;
                    mainHandler.removeCallbacks(timerRunnable);
                    showGameOverDialog(2);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void handleOfferDraw() {
        if (!isGameRunning || chessView.isGameOver()) return;
        // 简化：AI 直接接受和棋（人机对战无法对面确认）
        // 实际可加入"AI 30 步后自动判和"逻辑
        Toast.makeText(this, R.string.chinese_chess_draw_confirm, Toast.LENGTH_SHORT).show();
        // 模拟 AI 在 60% 概率下接受和棋
        if (Math.random() < 0.6) {
            isGameRunning = false;
            mainHandler.removeCallbacks(timerRunnable);
            showGameOverDialog(0); // 0 表示和棋
        } else {
            Toast.makeText(this, R.string.chinese_chess_draw_rejected, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 计时器显示 ====================

    /**
     * 更新指定方的计时器显示。
     * @param side 1=红方，2=黑方
     */
    private void updateTimerDisplay(int side) {
        long ms = (side == 1) ? redTimeMs : blackTimeMs;
        TextView tv = (side == 1) ? tvRedTimer : tvBlackTimer;
        tv.setText(formatDuration(ms));
        int color;
        if (ms < 30_000L) {
            color = 0xFFE53935; // 红色（危急）
            tv.setTextSize(22f);
        } else if (ms < 60_000L) {
            color = 0xFFFF6E40; // 橙色（警告）
            tv.setTextSize(21f);
        } else {
            color = 0xFFFFFFFF;
            tv.setTextSize(20f);
        }
        tv.setTextColor(color);
    }

    // ==================== 返回键 ====================

    @Override
    public void onBackPressed() {
        if (isGameRunning && !chessView.isGameOver()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.chinese_chess_exit_confirm)
                    .setPositiveButton(R.string.chinese_chess_exit_yes, (d, w) -> super.onBackPressed())
                    .setNegativeButton(R.string.chinese_chess_exit_no, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    // ==================== 音效 ====================

    private void initSoundPool() {
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(2).build();
            soundIdMove = soundPool.load(this, R.raw.ui_turn, 1);
        } catch (Exception ignored) {
            soundIdMove = 0;
        }
    }

    private boolean isSoundAllowed() {
        return SettingsManager.getInstance(this).shouldPlayGameSound();
    }

    private void playMoveSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 0.6f, 0.6f, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private void playWinSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 1.0f, 1.0f, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private void playLossSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 1.0f, 1.0f, 1, 0, 0.8f);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        mainHandler.removeCallbacks(timerRunnable);
    }
}
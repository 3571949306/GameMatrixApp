package com.gamecenter.app.games.chinesechess;

import android.app.AlertDialog;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gamecenter.app.games.chinesechess.GameReviewAnalyzer;
import com.gamecenter.app.games.chinesechess.HintTutorialManager;
import com.gamecenter.app.games.chinesechess.GameRecorder;
import com.gamecenter.app.games.chinesechess.GameRecord;
import com.gamecenter.app.games.chinesechess.ReviewResult;
import com.gamecenter.app.games.chinesechess.TutorialStep;

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
    private static final String TAG = "ChineseChessActivity";

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
    private ChineseChessAI masterAi;
    private int aiDifficulty = 2;

    // ==================== 提示限制器 ====================

    private HintLimiter hintLimiter;

    // ==================== 对局记录 ====================
    private GameRecorder gameRecorder;

    // ==================== 异步提示计算器 ====================

    private HintAsyncCalculator hintAsyncCalculator;
    private HintCache hintCache;
    private View btnHint;
    private volatile boolean hintCalculating = false;

    // ==================== 提示视觉反馈 ====================

    private HintVisualManager hintVisualManager;

    // ==================== 复盘和引导 ====================
    private GameReviewAnalyzer reviewAnalyzer;
    private HintTutorialManager tutorialManager;

    // ==================== 线程管理 ====================

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;

    private int winStreak = 0;

    /** 预测式返回手势回调（API 33+），与 onBackPressed() 双轨兼容 */
    private OnBackInvokedCallback backInvokedCallback;

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
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chinesechess);

        bindViews();
        setupDifficultyButtons();

        int intentDifficulty = getIntent().getIntExtra("game_difficulty_index", -1);
        if (intentDifficulty >= 0 && intentDifficulty < 4) {
            selectDifficulty(intentDifficulty);
        }

        ai = new ChineseChessAI(aiDifficulty);
        masterAi = new ChineseChessAI(4); // 大师级提示专用
        aiExecutor = Executors.newSingleThreadExecutor();

        // 初始化复盘分析器
        try {
            reviewAnalyzer = new GameReviewAnalyzer(aiDifficulty);
        } catch (Exception e) {
            Log.w(TAG, "GameReviewAnalyzer初始化失败", e);
        }

        // 初始化新手引导
        tutorialManager = new HintTutorialManager(this);

        chessView.setOnPlayerMoveListener(this::handlePlayerMove);
        chessView.setOnGameOverListener(this::handleGameOver);
        chessView.setOnMoveSoundListener(this::playMoveSound);

        // 初始化提示视觉反馈管理器
        hintVisualManager = new HintVisualManager(chessView);
        hintVisualManager.setOnHintExecuteListener(this::handleHintExecute);
        hintVisualManager.setOnHintCancelListener(this::handleHintCancel);

        // 初始化异步提示计算器
        hintCache = new HintCache();
        hintAsyncCalculator = new HintAsyncCalculator(hintCache, aiExecutor);

        // 初始化对局记录
        try {
            gameRecorder = new GameRecorder(this);
        } catch (Exception e) {
            Log.w(TAG, "GameRecorder初始化失败", e);
        }

        initSoundPool();
        registerPredictiveBack();

        btnHint = findViewById(R.id.btn_hint);
        findViewById(R.id.btn_start_game).setOnClickListener(v -> startGame());
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_restart).setOnClickListener(v -> handleRestart());
        findViewById(R.id.btn_resign).setOnClickListener(v -> handleResign());
        findViewById(R.id.btn_draw).setOnClickListener(v -> handleOfferDraw());
        btnHint.setOnClickListener(v -> handleHint());

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
        return getString(R.string.game_chinesechess_name);
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
        // 检查是否需要显示新手引导
        if (tutorialManager != null && tutorialManager.shouldShowTutorial()) {
            showTutorial();
            return; // 引导完成后才能开始游戏
        }
        
        if (selectedDifficultyIndex < 0) {
            Toast.makeText(this, R.string.chinese_chess_select_difficulty, Toast.LENGTH_SHORT).show();
            return;
        }

        // 4个按钮映射到5级难度：1→1, 2→2, 3→3, 4→5（跳过4级，直接大师）
        int[] difficultyMapping = {1, 2, 3, 5};
        aiDifficulty = difficultyMapping[selectedDifficultyIndex];
        ai = new ChineseChessAI(aiDifficulty);
        currentDifficultyIndex = selectedDifficultyIndex;

        // 开始录制对局
        if (gameRecorder != null) {
            try {
                gameRecorder.startRecording(aiDifficulty);
            } catch (Exception e) {
                Log.w(TAG, "开始录制失败", e);
            }
        }

        // 初始化提示限制器
        if (hintLimiter == null) {
            hintLimiter = new HintLimiter(this);
        } else {
            hintLimiter.resetGameHints();
        }

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
        if (hintVisualManager != null) {
            hintVisualManager.clearHint();
        }
        // 清除提示缓存（新对局）
        if (hintCache != null) {
            hintCache.clear();
        }

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
        if (hintVisualManager != null) {
            hintVisualManager.clearHint();
        }
        // 取消进行中的提示计算
        if (hintAsyncCalculator != null) {
            hintAsyncCalculator.cancelCalculation();
        }
        hintCalculating = false;
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
        levels.add(new DifficultyLevel(getString(R.string.game_chinesechess_diff_1), 1, getString(R.string.game_chinesechess_diff_1_desc), 2, 300, 0.3f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_chinesechess_diff_2), 2, getString(R.string.game_chinesechess_diff_2_desc), 4, 500, 0.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_chinesechess_diff_3), 3, getString(R.string.game_chinesechess_diff_3_desc), 6, 800, 0.7f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_chinesechess_diff_4), 4, getString(R.string.game_chinesechess_diff_4_desc), 8, 1500, 1.0f, false));
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

        // 重置当前步的提示次数
        if (hintLimiter != null) {
            hintLimiter.resetMoveHints();
        }

        // 记录走法
        if (gameRecorder != null) {
            try {
                int[] lastMove = chessView.getLastMove();
                if (lastMove != null) {
                    int score = ai != null ? evaluateCurrentBoard() : 0;
                    gameRecorder.recordMove(lastMove, score);
                }
            } catch (Exception e) {
                Log.w(TAG, "记录走法失败", e);
            }
        }

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
                // 对局已在 AI 计算期间结束（如玩家走子后黑方被将死），直接收尾
                if (chessView.isGameOver()) {
                    aiThinking = false;
                    chessView.setAiThinking(false);
                    return;
                }
                if (bestMove != null && bestMove.length >= 4) {
                    chessView.applyAIMove(bestMove[0], bestMove[1], bestMove[2], bestMove[3]);
                    appendMoveHistory();
                    checkAndShowCheckAlert();
                    updateCapturedPieces();
                } else {
                    // AI 无合法着法（应已被 View 的将死/困毙检测提前结束，此处为安全兜底）
                    if (chessView.isInCheck(2)) {
                        Toast.makeText(ChineseChessActivity.this,
                                R.string.game_chinesechess_black_checkmate_win, Toast.LENGTH_LONG).show();
                        showGameOverDialog(1);
                    } else {
                        Toast.makeText(ChineseChessActivity.this,
                                R.string.game_chinesechess_stalemate_draw, Toast.LENGTH_LONG).show();
                        showGameOverDialog(0);
                    }
                }
                aiThinking = false;
                chessView.setAiThinking(false);
                // 大师难度显示思考时长
                if (aiDifficulty >= 4 && elapsed > 200) {
                    android.widget.Toast.makeText(this,
                            getString(R.string.game_chinesechess_ai_think_ms, elapsed),
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
     * 重建并显示完整走法历史棋谱。
     * 每步一行，红黑交替，格式如 "1. 红 炮(7,1)→(7,4)"。
     * 通过 ChineseChessView.getAllMoveNotations() 从初始棋盘重放，
     * 确保每步棋子类型识别正确。
     */
    private void appendMoveHistory() {
        List<String> notations = chessView.getAllMoveNotations();
        int count = notations.size();
        if (count == 0) {
            tvMoveHistory.setText(R.string.chinese_chess_no_moves);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int round = (i / 2) + 1;
            String side = (i % 2 == 0) ? "红" : "黑";
            if (i > 0) sb.append("\n");
            sb.append(round).append(". ").append(side).append(" ").append(notations.get(i));
        }
        tvMoveHistory.setText(sb.toString());

        // 自动滚动到底部，显示最新走法
        historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_DOWN));
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
        tv.setTextColor(isRed
                ? ContextCompat.getColor(this, R.color.game_chinesechess_color_red_piece)
                : ContextCompat.getColor(this, R.color.game_chinesechess_color_black_piece));
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
        // 结束录制对局
        if (gameRecorder != null) {
            try {
                GameResult result = (winner == 1) ? GameResult.WIN :
                                   (winner == 2) ? GameResult.LOSE : GameResult.DRAW;
                gameRecorder.endRecording(result);
            } catch (Exception e) {
                Log.w(TAG, "结束录制失败", e);
            }
        }

        mainHandler.removeCallbacks(timerRunnable);
        isGameRunning = false;
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
        
        // 显示复盘提示
        if (reviewAnalyzer != null && gameRecorder != null) {
            try {
                GameRecord record = gameRecorder.getCurrentRecord();
                if (record != null && record.getMoves().size() > 0) {
                    showReviewOption(record);
                }
            } catch (Exception e) {
                Log.w(TAG, "复盘分析失败", e);
            }
        }
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
        tvWinner.setTextColor(winner == 1
                ? ContextCompat.getColor(this, R.color.game_chinesechess_color_red_piece)
                : ContextCompat.getColor(this, R.color.game_chinesechess_color_black_piece));
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

        // 重置提示限制器
        if (hintLimiter != null) {
            hintLimiter.resetGameHints();
        }
    }

    /**
     * 格式化毫秒为 mm:ss。
     */
    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        long m = sec / 60L;
        long s = sec % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
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

    private void handleHint() {
        if (chessView.isGameOver() || aiThinking || chessView.getCurrentSide() != 1) return;
        if (hintCalculating) return; // 防止重复点击

        // 清除之前的提示
        hintVisualManager.clearHint();

        // 检查提示次数限制
        if (!hintLimiter.canUseHint()) {
            hintLimiter.useHint(); // 这会显示Toast提示
            return;
        }

        // 使用提示
        hintLimiter.useHint();

        // 显示加载状态
        hintCalculating = true;
        Toast.makeText(this, R.string.game_chinesechess_master_thinking, Toast.LENGTH_SHORT).show();

        final long currentGen = aiGeneration;
        int[][] board = chessView.getBoardState();

        hintAsyncCalculator.calculateHintAsync(board, 1, 4, new HintAsyncCalculator.HintCallback() {
            @Override
            public void onHintReady(HintResult result) {
                hintCalculating = false;
                if (currentGen != aiGeneration) return;
                if (result.getMove() != null && result.getMove().length >= 4) {
                    // 使用 HintVisualManager 显示增强提示
                    hintVisualManager.showHint(result.getMove(), result.getExplanation());
                }
                // 记录提示
                if (gameRecorder != null && result != null) {
                    try {
                        gameRecorder.recordHint(result);
                    } catch (Exception e) {
                        Log.w(TAG, "记录提示失败", e);
                    }
                }
            }

            @Override
            public void onHintError(String error) {
                hintCalculating = false;
                Toast.makeText(ChineseChessActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 处理提示执行：用户点击"执行走法"按钮后自动走棋。
     */
    private void handleHintExecute(int[] move) {
        if (move == null || move.length < 4) return;
        if (chessView.isGameOver() || aiThinking || chessView.getCurrentSide() != 1) return;

        int fromR = move[0], fromC = move[1];
        int toR = move[2], toC = move[3];

        // 选中起始位置的棋子
        chessView.setSelected(fromR, fromC);

        // 模拟点击目标位置触发走棋
        android.graphics.PointF targetPoint = getCellCenter(toR, toC);
        if (targetPoint != null) {
            android.view.MotionEvent downEvent = android.view.MotionEvent.obtain(
                    0, 0, android.view.MotionEvent.ACTION_DOWN,
                    targetPoint.x, targetPoint.y, 0);
            chessView.dispatchTouchEvent(downEvent);
            downEvent.recycle();
        }

        // 清除提示
        hintVisualManager.clearHint();
    }

    /**
     * 处理提示取消：用户点击"取消"按钮。
     */
    private void handleHintCancel() {
        hintVisualManager.clearHint();
    }

    /**
     * 获取指定单元格的中心像素坐标。
     */
    private android.graphics.PointF getCellCenter(int row, int col) {
        float[] metrics = chessView.getBoardMetrics();
        if (metrics == null) return null;
        float cellSize = metrics[0];
        float offsetX = metrics[1];
        float offsetY = metrics[2];
        float x = offsetX + col * cellSize;
        float y = offsetY + row * cellSize;
        return new android.graphics.PointF(x, y);
    }

    /**
     * 设置棋盘选中位置（供提示执行使用）。
     */
    private void setSelected(int row, int col) {
        // 通过反射或公开方法设置选中位置
        // 由于 ChineseChessView 没有公开 setSelected 方法，
        // 我们使用触摸事件模拟
        android.graphics.PointF center = getCellCenter(row, col);
        if (center != null) {
            android.view.MotionEvent downEvent = android.view.MotionEvent.obtain(
                    0, 0, android.view.MotionEvent.ACTION_DOWN,
                    center.x, center.y, 0);
            chessView.dispatchTouchEvent(downEvent);
            downEvent.recycle();
        }
    }

    /**
     * 生成提示解释文本。
     * 根据走法特征分析可能的战术意图。
     */
    private String generateHintExplanation(int[][] board, int[] move) {
        if (move == null || move.length < 4) return "";

        int fromR = move[0], fromC = move[1];
        int toR = move[2], toC = move[3];

        int piece = board[fromR][fromC];
        int target = board[toR][toC];

        if (piece == 0) return "";

        boolean isRed = piece > 0;
        int type = Math.abs(piece);
        String pieceName = ChineseChessView.getPieceName(type, isRed);

        StringBuilder sb = new StringBuilder();

        // 检查是否吃子
        if (target != 0) {
            int targetType = Math.abs(target);
            boolean targetIsRed = target > 0;
            String targetName = ChineseChessView.getPieceName(targetType, targetIsRed);
            sb.append("吃掉对方").append(targetName);
        }

        // 检查是否将军
        int[][] testBoard = copyBoard(board);
        testBoard[toR][toC] = testBoard[fromR][fromC];
        testBoard[fromR][fromC] = 0;
        int opponentSide = isRed ? 2 : 1;
        if (isInCheckOnBoard(testBoard, opponentSide)) {
            if (sb.length() > 0) sb.append("，");
            sb.append("形成将军");
        }

        // 如果没有特殊效果，给出基本描述
        if (sb.length() == 0) {
            sb.append(pieceName).append("移动到目标位置");
        }

        return sb.toString();
    }

    /**
     * 检查棋盘上指定方是否被将军。
     */
    private boolean isInCheckOnBoard(int[][] board, int side) {
        // 查找将/帅位置
        int target = (side == 1) ? 1 : -1; // KING = 1
        int kingRow = -1, kingCol = -1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (Math.abs(board[r][c]) == 1) {
                    if ((side == 1 && board[r][c] > 0) || (side == 2 && board[r][c] < 0)) {
                        kingRow = r;
                        kingCol = c;
                        break;
                    }
                }
            }
            if (kingRow >= 0) break;
        }

        if (kingRow < 0) return false;

        // 检查对方棋子是否能攻击到将/帅
        int attackerSide = (side == 1) ? -1 : 1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == 0) continue;
                if ((attackerSide > 0 && board[r][c] > 0) || (attackerSide < 0 && board[r][c] < 0)) {
                    // 简化的攻击检测
                    if (canAttackSimple(board, r, c, kingRow, kingCol)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 简化的攻击检测（用于将军判断）。
     */
    private boolean canAttackSimple(int[][] board, int fromR, int fromC, int toR, int toC) {
        int piece = board[fromR][fromC];
        if (piece == 0) return false;

        int type = Math.abs(piece);
        int dr = toR - fromR;
        int dc = toC - fromC;

        switch (type) {
            case 1: // 将/帅
                return Math.abs(dr) + Math.abs(dc) == 1;
            case 2: // 仕/士
                return Math.abs(dr) == 1 && Math.abs(dc) == 1;
            case 3: // 相/象
                return Math.abs(dr) == 2 && Math.abs(dc) == 2;
            case 4: // 马
                return (Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2);
            case 5: // 车
                if (dr != 0 && dc != 0) return false;
                return isPathClearSimple(board, fromR, fromC, toR, toC);
            case 6: // 炮
                if (dr != 0 && dc != 0) return false;
                int count = countPiecesBetweenSimple(board, fromR, fromC, toR, toC);
                return count == 1; // 吃子时必须隔一个
            case 7: // 兵/卒
                if (piece > 0) { // 红方
                    if (fromR >= 5) return dr == -1 && dc == 0; // 未过河
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                } else { // 黑方
                    if (fromR <= 4) return dr == 1 && dc == 0;
                    return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
        }
        return false;
    }

    /**
     * 简化的路径检测。
     */
    private boolean isPathClearSimple(int[][] board, int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) {
                if (board[r1][c] != 0) return false;
            }
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) {
                if (board[r][c1] != 0) return false;
            }
        }
        return true;
    }

    /**
     * 简化的棋子计数。
     */
    private int countPiecesBetweenSimple(int[][] board, int r1, int c1, int r2, int c2) {
        int count = 0;
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++) {
                if (board[r1][c] != 0) count++;
            }
        } else {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++) {
                if (board[r][c1] != 0) count++;
            }
        }
        return count;
    }

    private int evaluateCurrentBoard() {
        if (ai == null) return 0;
        int[][] board = chessView.getBoardState();
        int score = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                int piece = board[r][c];
                if (piece == 0) continue;
                int type = Math.abs(piece);
                int[] values = {0, 10000, 200, 200, 400, 900, 450, 100};
                score += (piece > 0 ? 1 : -1) * values[type];
            }
        }
        return score;
    }

    /**
     * 复制棋盘。
     */
    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[10][9];
        for (int r = 0; r < 10; r++) {
            System.arraycopy(src[r], 0, copy[r], 0, 9);
        }
        return copy;
    }

    /**
     * 将棋盘坐标转换为标准中国象棋记谱法。
     * 规则：
     * - 红方用汉字数字（一~九，从右到左），黑方用阿拉伯数字（1~9，从左到右）
     * - 格式：棋子 + 起始列 + 动作(进/退/平) + 目标列或步数
     * - 直行棋子（车/炮/兵/将）：进退用步数，平用目标列
     * - 斜走棋子（马/相/仕）：进退用目标列，无平
     *
     * @param board 走子前的棋盘状态
     * @param move  [fromR, fromC, toR, toC]
     * @param side  1=红方，2=黑方（仅作参考，实际颜色由棋子本身决定）
     */
    private String getNotation(int[][] board, int[] move, int side) {
        int r1 = move[0], c1 = move[1], r2 = move[2], c2 = move[3];
        int piece = board[r1][c1];
        if (piece == 0) return getString(R.string.game_chinesechess_suggested_move);

        // 棋子类型和颜色（从棋子本身判断，避免负数索引）
        int type = Math.abs(piece);
        boolean isRed = piece > 0;

        // 棋子名称
        String[] blackNames = {"", "将", "士", "象", "马", "车", "炮", "卒"};
        String[] redNames = {"", "帅", "仕", "相", "马", "车", "炮", "兵"};
        String name = isRed ? redNames[type] : blackNames[type];

        // 列号转换：红方从右到左（9-c），黑方从左到右（c+1）
        int startCol = isRed ? (9 - c1) : (c1 + 1);
        int endCol = isRed ? (9 - c2) : (c2 + 1);

        // 斜走棋子（仕/士=2, 相/象=3, 马=4）：进退用目标列
        boolean isDiagonal = (type == 2 || type == 3 || type == 4);

        String action;
        String numStr;

        if (r1 == r2) {
            // 平移（只有直行棋子才会平移）
            action = "平";
            numStr = isRed ? cnNum(endCol) : String.valueOf(endCol);
        } else {
            // 进/退：红方在下（行号大），向上走（行号减）为进；黑方在上，向下走（行号增）为进
            boolean isAdvance = isRed ? (r2 < r1) : (r2 > r1);
            action = isAdvance ? "进" : "退";
            if (isDiagonal) {
                // 斜走棋子：进退用目标列
                numStr = isRed ? cnNum(endCol) : String.valueOf(endCol);
            } else {
                // 直走棋子：进退用步数
                int steps = Math.abs(r2 - r1);
                numStr = isRed ? cnNum(steps) : String.valueOf(steps);
            }
        }

        String startStr = isRed ? cnNum(startCol) : String.valueOf(startCol);
        return name + startStr + action + numStr;
    }

    /**
     * 将 1-9 的数字转为中文数字（红方记谱用）。
     */
    private String cnNum(int n) {
        String[] cnNums = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n >= 0 && n <= 9) return cnNums[n];
        return String.valueOf(n);
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
            color = ContextCompat.getColor(this, R.color.game_chinesechess_color_timer_critical);
            tv.setTextSize(22f);
        } else if (ms < 60_000L) {
            color = ContextCompat.getColor(this, R.color.game_chinesechess_color_timer_warning);
            tv.setTextSize(21f);
        } else {
            color = ContextCompat.getColor(this, R.color.game_chinesechess_color_timer_normal);
            tv.setTextSize(20f);
        }
        tv.setTextColor(color);
    }

    // ==================== 返回键 ====================

    /**
     * 注册预测式返回手势回调（API 33+）。
     * <p>API < 33 的设备继续走 onBackPressed() 兼容路径，实现双轨返回逻辑。</p>
     */
    private void registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = () -> handleBack();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backInvokedCallback
            );
        }
    }

    @android.annotation.SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    /**
     * 统一的返回处理逻辑：游戏进行中弹出确认退出对话框，否则直接结束。
     */
    private void handleBack() {
        if (isGameRunning && !chessView.isGameOver()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.chinese_chess_exit_confirm)
                    .setPositiveButton(R.string.chinese_chess_exit_yes, (d, w) -> finish())
                    .setNegativeButton(R.string.chinese_chess_exit_no, null)
                    .show();
        } else {
            finish();
        }
    }

    private void showTutorial() {
        if (tutorialManager == null) return;

        tutorialManager.startTutorial(new HintTutorialManager.OnTutorialListener() {
            @Override
            public void onTutorialStep(int stepIndex, TutorialStep step) {
                // 显示引导步骤
                Log.d(TAG, "引导步骤: " + step.getTitle());
                showTutorialStep(step);
            }

            @Override
            public void onTutorialComplete() {
                tutorialManager.markTutorialShown();
                Toast.makeText(ChineseChessActivity.this,
                    "引导完成！现在可以开始游戏了", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTutorialSkip() {
                tutorialManager.markTutorialShown();
            }
        });
    }

    private TutorialDialog tutorialDialog;

    private void showTutorialStep(TutorialStep step) {
        if (tutorialDialog == null) {
            tutorialDialog = new TutorialDialog(this);
        }
        tutorialDialog.setStep(step);
        tutorialDialog.setOnNextClickListener(v -> {
            if (tutorialManager != null) {
                tutorialManager.nextStep();
            }
        });
        tutorialDialog.setOnSkipClickListener(v -> {
            if (tutorialManager != null) {
                tutorialManager.skipTutorial();
            }
        });
        tutorialDialog.show();
    }

    private void showReviewOption(GameRecord record) {
        new AlertDialog.Builder(this)
            .setTitle("复盘分析")
            .setMessage("是否要查看本局复盘分析？")
            .setPositiveButton("查看", (dialog, which) -> {
                performReview(record);
            })
            .setNegativeButton("跳过", null)
            .show();
    }

    private void performReview(GameRecord record) {
        if (reviewAnalyzer == null) return;
        
        // 在后台线程执行复盘
        aiExecutor.execute(() -> {
            try {
                ReviewResult result = reviewAnalyzer.analyzeGame(record);
                mainHandler.post(() -> showReviewResult(result));
            } catch (Exception e) {
                Log.e(TAG, "复盘分析失败", e);
                mainHandler.post(() -> Toast.makeText(this, 
                    "复盘分析失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showReviewResult(ReviewResult result) {
        String message = String.format(
            "总步数: %d\n好棋: %d\n失误: %d\n\n%s",
            result.totalMoves,
            result.goodMoves,
            result.mistakes,
            result.summary
        );
        
        new AlertDialog.Builder(this)
            .setTitle("复盘报告")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
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
        } catch (Exception e) {
            Log.w(TAG, "音效播放失败", e);
        }
    }

    private void playWinSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 1.0f, 1.0f, 1, 0, 1.0f);
        } catch (Exception e) {
            Log.w(TAG, "音效播放失败", e);
        }
    }

    private void playLossSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 1.0f, 1.0f, 1, 0, 0.8f);
        } catch (Exception e) {
            Log.w(TAG, "音效播放失败", e);
        }
    }

    @Override
    protected void onDestroy() {
        if (backInvokedCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (hintVisualManager != null) {
            hintVisualManager.release();
        }
        // 释放异步提示计算器
        if (hintAsyncCalculator != null) {
            hintAsyncCalculator.cancelCalculation();
        }
        if (hintCache != null) {
            hintCache.clear();
        }
        super.onDestroy();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}

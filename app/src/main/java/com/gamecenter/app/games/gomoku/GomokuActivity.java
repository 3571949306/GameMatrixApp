package com.gamecenter.app.games.gomoku;

import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 五子棋人机对战Activity。
 * <p>
 * 负责管理五子棋单局对战的完整生命周期，包括：
 * <ul>
 *   <li>难度选择（低 / 中 / 高 / 大师，对应不同AI思考时间）</li>
 *   <li>先手选择（执黑或执白）</li>
 *   <li>玩家落子交互与落子动画</li>
 *   <li>AI异步计算与落子</li>
 *   <li>对局计时（双方用时统计）</li>
 *   <li>悔棋功能（可撤销1-5手）</li>
 *   <li>认输、提示、重新开始</li>
 *   <li>胜负统计记录</li>
 *   <li>落子音效与震动反馈</li>
 *   <li>对局状态保存（旋转屏幕不丢局）</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>AI计算在单独线程池中执行，通过Handler按难度最小响应延迟回传结果</li>
 *   <li>{@code interactive} 开关控制棋盘是否响应触摸，未开始游戏时禁止下子</li>
 *   <li>玩家可选执黑（先手）或执白（后手），AI自动执另一方</li>
 *   <li>对局计时使用 SystemClock.elapsedRealtime，不受系统时间调整影响</li>
 * </ul>
 *
 * 【初学者指南】
 * Activity是Android中的"页面"，你可以把它理解为游戏的一个屏幕。
 * 这个类就是五子棋人机对战的主屏幕，负责把棋盘、按钮、难度选择等组合在一起。
 * 它就像一个"指挥官"：自己不下棋，但协调棋盘视图(GomokuView)、游戏规则(GomokuGame)和AI大脑(GomokuAI)协同工作。
 */
public class GomokuActivity extends AppCompatActivity {

    private static final String TAG = "GomokuActivity";
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {80L, 120L, 170L, 230L};

    /** 游戏标识，用于胜负统计 */
    private static final String GAME_ID = "gomoku";

    /** 状态保存的key前缀 */
    private static final String STATE_BOARD = "gomoku_board";
    private static final String STATE_CURRENT_PLAYER = "gomoku_current_player";
    private static final String STATE_MOVE_COUNT = "gomoku_move_count";
    private static final String STATE_AI_DIFFICULTY = "gomoku_ai_difficulty";
    private static final String STATE_PLAYER_COLOR = "gomoku_player_color";
    private static final String STATE_GAME_STARTED = "gomoku_game_started";
    private static final String STATE_HISTORY_X = "gomoku_history_x";
    private static final String STATE_HISTORY_Y = "gomoku_history_y";
    private static final String STATE_HISTORY_PLAYER = "gomoku_history_player";

    /** 棋盘视图组件 */
    private GomokuView gomokuView;

    /** 难度选择面板 */
    private ScrollView difficultyPanel;

    /** 游戏控制面板（悔棋、重开等按钮） */
    private LinearLayout controlPanel;

    /** 难度标签文本 */
    private TextView tvDifficultyLabel;

    /** 对局计时文本 */
    private TextView tvTimer;

    /** 五子棋游戏逻辑对象 */
    private GomokuGame game;

    /** AI决策引擎 */
    private GomokuAI ai;

    /** P2-7: 对局回放录制器 */
    private com.gamecenter.app.games.replay.ReplayRecorder replayRecorder;

    /** 2026-08-23 P2-2: 中断续玩存档管理器 */
    private com.gamecenter.app.games.save.GameSaveManager saveManager;

    /** 大师级AI专门用于提示 */
    private GomokuAI masterAi;

    /** AI执子颜色 */
    private int aiPlayer = GomokuGame.WHITE;

    /** 玩家执子颜色 */
    private int playerColor = GomokuGame.BLACK;

    /** 当前AI难度等级（1~4） */
    private int aiDifficulty = 2;

    private static final int MAX_AI_DIFFICULTY = 4;

    /** 游戏使用统计存储 */
    private GameUsageStore usageStore;

    /** 主线程Handler */
    private Handler mainHandler;

    /** AI计算专用线程池 */
    private ExecutorService aiExecutor;

    /** AI是否正在思考的标志位 */
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;

    /** 难度名称数组，与 1-4 档按钮对应（从资源读取，支持本地化） */
    private String[] getDifficultyNames() {
        return new String[]{
                getString(R.string.game_gomoku_diff_low),
                getString(R.string.game_gomoku_diff_medium),
                getString(R.string.game_gomoku_diff_high),
                getString(R.string.game_gomoku_diff_master)
        };
    }

    /** 对局开始时间戳（elapsedRealtime） */
    private long gameStartElapsedMs = 0L;

    /** 计时刷新Handler */
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerDisplay();
            if (game != null && !game.isGameOver() && difficultyPanel.getVisibility() == View.GONE) {
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    /** 落子音效与震动 */
    private SoundPool soundPool;
    private int pieceSoundId = 0;
    private Vibrator vibrator;
    private boolean soundEnabled = true;
    private boolean vibrateEnabled = true;

    /**
     * Activity创建时的初始化入口。
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android 16+ (API 36) 将忽略 manifest 中的 android:screenOrientation，
        // 需在运行时强制锁定竖屏。
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_gomoku);

        // 创建主线程信使和AI专用线程池
        mainHandler = new Handler(Looper.getMainLooper());
        aiExecutor = Executors.newSingleThreadExecutor();

        // 找到布局中的各个UI组件
        gomokuView = findViewById(R.id.gomoku_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);
        tvDifficultyLabel = findViewById(R.id.tv_difficulty_label);
        tvTimer = findViewById(R.id.tv_timer);

        // 创建游戏逻辑对象和AI引擎
        game = new GomokuGame();
        ai = new GomokuAI(aiDifficulty);
        masterAi = new GomokuAI(4); // 难度4（大师级）专门用于提示
        gomokuView.setGame(game);
        // 初始未开始游戏，禁止棋盘交互并隐藏棋盘，让难度选择面板获得充足显示空间
        gomokuView.setInteractive(false);
        gomokuView.setVisibility(View.GONE);
        usageStore = new GameUsageStore(this);
        // 2026-08-23 P2-2：初始化存档管理器
        saveManager = new com.gamecenter.app.games.save.GameSaveManager(this);

        // 初始化音效与震动
        initSoundAndVibration();

        // 设置棋盘点击监听器
        gomokuView.setOnCellClickListener((x, y) -> handleCellClick(x, y));
        // 设置游戏结束监听器
        gomokuView.setOnGameOverListener(this::handleGameOver);

        setupDifficultyButtons();
        setupColorSelectionButtons();

        // 绑定各个按钮的点击事件
        // 2026-08-23 P2-2：开始入口改为先经 beginPlay 检测未完成存档
        findViewById(R.id.btn_start_game).setOnClickListener(v -> beginPlay());
        findViewById(R.id.btn_tutorial).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_hint).setOnClickListener(v -> handleHint());
        findViewById(R.id.btn_restart).setOnClickListener(v -> confirmRestart());
        findViewById(R.id.btn_resign).setOnClickListener(v -> handleResign());
        findViewById(R.id.btn_tutorial_ingame).setOnClickListener(v ->
                GameTutorialHelper.showGomokuTutorial(this));

        // 返回键：游戏中弹确认对话框，难度选择界面直接退出
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (difficultyPanel.getVisibility() == View.GONE && game != null && !game.isGameOver()) {
                    showExitConfirmDialog();
                } else {
                    finish();
                }
            }
        });

        // 恢复保存的对局状态
        if (savedInstanceState != null) {
            restoreGameState(savedInstanceState);
        }
    }

    /**
     * 初始化落子音效与震动反馈。
     */
    private void initSoundAndVibration() {
        soundEnabled = SettingsManager.getInstance(this).shouldPlayGameSound();
        vibrateEnabled = SettingsManager.getInstance(this).shouldVibrate();
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(2).build();
            // 尝试加载落子音效，失败则静默处理
            pieceSoundId = soundPool.load(this, R.raw.ui_turn, 1);
        } catch (Exception e) {
            soundEnabled = false;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
        } catch (Exception e) {
            vibrateEnabled = false;
        }
    }

    /**
     * 播放落子音效。
     */
    private void playPieceSound() {
        if (!SettingsManager.getInstance(this).shouldPlayGameSound() || soundPool == null || pieceSoundId == 0) return;
        try {
            soundPool.play(pieceSoundId, 0.6f, 0.6f, 1, 0, 1.0f);
        } catch (Exception e) {
            Log.w(TAG, "落子音效播放失败", e);
        }
    }

    /**
     * 触发轻震动反馈。
     */
    private void vibrateLight() {
        if (!SettingsManager.getInstance(this).shouldVibrate() || vibrator == null || !vibrator.hasVibrator()) return;
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

    private void setupDifficultyButtons() {
        int[] ids = {
                R.id.btn_difficulty_1,
                R.id.btn_difficulty_2,
                R.id.btn_difficulty_3,
                R.id.btn_difficulty_4
        };
        for (int i = 0; i < ids.length; i++) {
            final int difficulty = i + 1;
            View button = findViewById(ids[i]);
            if (button != null) {
                button.setOnClickListener(v -> selectDifficulty(difficulty));
            }
        }
        selectDifficulty(aiDifficulty);
    }

    /**
     * 设置先手选择按钮（执黑/执白）。
     */
    private void setupColorSelectionButtons() {
        View btnBlack = findViewById(R.id.btn_color_black);
        View btnWhite = findViewById(R.id.btn_color_white);
        if (btnBlack != null) {
            btnBlack.setOnClickListener(v -> selectPlayerColor(GomokuGame.BLACK));
        }
        if (btnWhite != null) {
            btnWhite.setOnClickListener(v -> selectPlayerColor(GomokuGame.WHITE));
        }
        selectPlayerColor(playerColor);
    }

    private void selectPlayerColor(int color) {
        playerColor = color;
        aiPlayer = (color == GomokuGame.BLACK) ? GomokuGame.WHITE : GomokuGame.BLACK;
        View btnBlack = findViewById(R.id.btn_color_black);
        View btnWhite = findViewById(R.id.btn_color_white);
        if (btnBlack != null) {
            btnBlack.setSelected(color == GomokuGame.BLACK);
        }
        if (btnWhite != null) {
            btnWhite.setSelected(color == GomokuGame.WHITE);
        }
    }

    private void selectDifficulty(int difficulty) {
        aiDifficulty = Math.max(1, Math.min(difficulty, MAX_AI_DIFFICULTY));
        if (tvDifficultyLabel != null) {
            tvDifficultyLabel.setText(getString(R.string.game_gomoku_difficulty_label,
                    getDifficultyNames()[aiDifficulty - 1], aiDifficulty, MAX_AI_DIFFICULTY));
        }
        // 更新难度按钮选中态（LinearLayout 用 setSelected 触发 selector）
        int[] ids = {
                R.id.btn_difficulty_1,
                R.id.btn_difficulty_2,
                R.id.btn_difficulty_3,
                R.id.btn_difficulty_4
        };
        for (int i = 0; i < ids.length; i++) {
            View btn = findViewById(ids[i]);
            if (btn != null) {
                btn.setSelected(i + 1 == aiDifficulty);
            }
        }
    }

    /**
     * 2026-08-23 P2-2：开始游戏入口——检测未完成对局存档，
     * 有存档时弹"继续上局"对话框，否则直接新开一局。
     */
    private void beginPlay() {
        if (saveManager != null && saveManager.hasSave(GAME_ID)) {
            new AlertDialog.Builder(this)
                    .setTitle("继续上局？")
                    .setMessage("检测到上次未完成的对局，是否继续？")
                    .setPositiveButton("继续上局", (d, w) -> restoreFromSave())
                    .setNegativeButton("新开一局", (d, w) -> {
                        saveManager.clear(GAME_ID);
                        startGame(aiDifficulty);
                    })
                    .setCancelable(true)
                    .show();
        } else {
            startGame(aiDifficulty);
        }
    }

    /** 2026-08-23 P2-2：从存档恢复对局 */
    private void restoreFromSave() {
        org.json.JSONObject state = saveManager == null ? null : saveManager.load(GAME_ID);
        if (state == null) {
            startGame(aiDifficulty);
            return;
        }
        try {
            // 通过重放落子历史重建棋盘（makeMove 会同步恢复历史/moveCount/lastMove）
            game.reset();
            org.json.JSONArray history = state.getJSONArray("history");
            for (int i = 0; i < history.length(); i++) {
                org.json.JSONArray move = history.getJSONArray(i);
                game.makeMove(move.getInt(0), move.getInt(1), move.getInt(2));
            }
            int difficulty = state.optInt("aiDifficulty", aiDifficulty);
            int savedPlayerColor = state.optInt("playerColor", GomokuGame.BLACK);
            int currentPlayer = state.optInt("currentPlayer", GomokuGame.BLACK);
            game.setCurrentPlayer(currentPlayer);

            // 作废可能进行中的后台 AI 计算，避免旧结果落子到恢复后的棋盘
            aiGeneration++;
            aiThinking = false;

            aiDifficulty = difficulty;
            ai = new GomokuAI(difficulty);
            selectDifficulty(difficulty);
            selectPlayerColor(savedPlayerColor);

            // 切换到对局界面（显示棋盘与控制面板，隐藏难度选择面板）
            difficultyPanel.setVisibility(View.GONE);
            controlPanel.setVisibility(View.VISIBLE);
            gomokuView.setInteractive(true);
            gomokuView.setVisibility(View.VISIBLE);
            gomokuView.setGame(game);
            gomokuView.clearHover();
            gomokuView.clearHint();
            gomokuView.setAiThinking(false);
            gameStartElapsedMs = SystemClock.elapsedRealtime();
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
            gomokuView.invalidate();

            // 恢复的对局重新开始回放录制（旧走法从恢复点继续记）
            replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, GAME_ID);
            replayRecorder.startRecording(difficulty);

            // 若存档停在 AI 回合，恢复后触发 AI 行动
            if (game.getCurrentPlayer() == aiPlayer && !game.isGameOver()) {
                triggerAiMove();
            }
        } catch (Exception e) {
            Log.w(TAG, "存档恢复失败，新开一局: " + e.getMessage());
            startGame(aiDifficulty);
        }
    }

    /** 2026-08-23 P2-2：保存当前对局进度 */
    private void saveProgress() {
        // 本 Activity 未使用 BaseGameActivity 的 isGameRunning，
        // 以"对局界面可见且未结束"作为运行中判据
        if (saveManager == null || game == null || game.isGameOver()) return;
        if (difficultyPanel.getVisibility() != View.GONE) return;
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            // 保存落子历史（重放即可完整重建棋盘、手数与悔棋栈）
            org.json.JSONArray history = new org.json.JSONArray();
            for (GomokuGame.MoveRecord record : game.getMoveHistory()) {
                org.json.JSONArray move = new org.json.JSONArray();
                move.put(record.x);
                move.put(record.y);
                move.put(record.player);
                history.put(move);
            }
            state.put("history", history);
            state.put("currentPlayer", game.getCurrentPlayer());
            state.put("aiDifficulty", aiDifficulty);
            state.put("playerColor", playerColor);
            state.put("moveCount", game.getMoveCount());
            saveManager.save(GAME_ID, state);
        } catch (Exception ignored) {
            // 存档失败不影响游戏主流程
        }
    }

    /**
     * 开始游戏，根据选择的难度和先手创建AI引擎。
     *
     * @param difficulty AI难度等级（1~4）
     */
    private void startGame(int difficulty) {
        aiDifficulty = difficulty;
        ai = new GomokuAI(difficulty);
        game.reset();
        gomokuView.setGame(game);
        // 开启棋盘交互并显示棋盘
        gomokuView.setInteractive(true);
        gomokuView.setVisibility(View.VISIBLE);
        // 切换界面：隐藏难度选择面板，显示游戏控制面板
        difficultyPanel.setVisibility(View.GONE);
        controlPanel.setVisibility(View.VISIBLE);
        // 启动对局计时
        gameStartElapsedMs = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);
        gomokuView.invalidate();

        // P2-7: 开始回放录制
        replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, GAME_ID);
        replayRecorder.startRecording(difficulty);

        // 若玩家执白，AI先手
        if (playerColor == GomokuGame.WHITE) {
            triggerAiMove();
        }
    }

    /**
     * 处理玩家点击棋盘的落子操作。
     *
     * @param x 横坐标（列索引）
     * @param y 纵坐标（行索引）
     */
    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) return;
        if (game.getCurrentPlayer() != playerColor) return;
        if (aiThinking) return;
        if (!game.isValidMove(x, y)) return;
        // 禁手判定（仅黑方）：玩家走出禁手则提示并拒绝落子
        if (playerColor == GomokuGame.BLACK && game.isForbiddenMovesEnabled()
                && game.isForbiddenMove(x, y)) {
            Toast.makeText(this, forbiddenToastText(game.getForbiddenType(x, y)), Toast.LENGTH_SHORT).show();
            return;
        }
        gomokuView.clearHint();
        game.makeMove(x, y, playerColor);
        // P2-7: 记录走法（Gomoku 用 (x,y)=(列,行)，转成 (row=y,col=x) 统一坐标）
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(y, x, playerColor));
        }
        game.switchPlayer();
        gomokuView.animateLastMove();
        playPieceSound();
        vibrateLight();
        if (game.checkGameOver()) {
            gomokuView.invalidate();
            stopTimer();
            return;
        }
        // 2026-08-23 P2-2：玩家落子成功后保存进度（轮到 AI）
        saveProgress();
        triggerAiMove();
    }

    /**
     * 触发AI计算落子。
     */
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
                    // 禁手判定（黑方）：AI 若走出禁手则判负（玩家胜）
                    if (aiPlayer == GomokuGame.BLACK && game.isForbiddenMovesEnabled()
                            && game.isForbiddenMove(bestMove[0], bestMove[1])) {
                        game.setGameOver(playerColor);
                        gomokuView.invalidate();
                        stopTimer();
                        return;
                    }
                    game.makeMove(bestMove[0], bestMove[1], aiPlayer);
                    // P2-7: 记录 AI 走法
                    if (replayRecorder != null && replayRecorder.isRecording()) {
                        replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(bestMove[1], bestMove[0], aiPlayer));
                    }
                    game.switchPlayer();
                    game.checkGameOver();
                    gomokuView.animateLastMove();
                    playPieceSound();
                }
                aiThinking = false;
                gomokuView.setAiThinking(false);
                if (game.isGameOver()) {
                    stopTimer();
                }
                // 2026-08-23 P2-2：AI 落子后保存进度（轮到玩家），对局结束则跳过
                if (!game.isGameOver()) {
                    saveProgress();
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

    /**
     * 将禁手类型转换为中文提示文本。
     */
    private String forbiddenToastText(GomokuGame.ForbiddenType type) {
        if (type == null) return getString(R.string.game_gomoku_forbidden);
        switch (type) {
            case THREE_THREE: return getString(R.string.game_gomoku_forbidden_three_three);
            case FOUR_FOUR: return getString(R.string.game_gomoku_forbidden_four_four);
            case OVERLINE: return getString(R.string.game_gomoku_forbidden_overline);
            default: return getString(R.string.game_gomoku_forbidden);
        }
    }

    /**
     * 处理悔棋操作，撤销最多5手（玩家+AI各一手为一手）。
     */
    private void handleUndo() {
        if (aiThinking) return;
        int undoCount = game.undoLastMoves(1);
        if (undoCount > 0) {
            gomokuView.clearHint();
            gomokuView.invalidate();
            // 2026-08-23 P2-2：悔棋后局面变化，同步保存进度
            saveProgress();
        }
    }

    private void handleHint() {
        if (game.isGameOver() || aiThinking || game.getCurrentPlayer() != playerColor) return;
        gomokuView.clearHint();
        final long currentGen = aiGeneration;
        
        // 显示提示加载中
        Toast.makeText(this, R.string.game_gomoku_master_thinking, Toast.LENGTH_SHORT).show();
        
        aiExecutor.execute(() -> {
            // 使用大师级 AI 获取最佳走法
            int[] hint = masterAi.getBestMove(game, playerColor);
            
            if (hint != null) {
                // 获取教育性分析文本
                String analysis = masterAi.getEducationalAnalysis(game, hint[0], hint[1], playerColor);
                
                mainHandler.post(() -> {
                    if (currentGen != aiGeneration) return;
                    if (!game.isGameOver() && game.getCurrentPlayer() == playerColor) {
                        gomokuView.showHint(hint[0], hint[1]);
                        // 将坐标 (x,y) 转换为棋盘标记，例如 A1, H8
                        char colLabel = (char) ('A' + hint[0]);
                        int rowLabel = 15 - hint[1];
                        String message = getString(R.string.game_gomoku_master_hint, colLabel, rowLabel, analysis);
                        Toast.makeText(GomokuActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /**
     * 处理认输操作。
     */
    private void handleResign() {
        if (game.isGameOver() || aiThinking) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_gomoku_resign_title)
                .setMessage(R.string.game_gomoku_resign_message)
                .setPositiveButton(R.string.game_gomoku_resign_confirm, (d, w) -> {
                    game.setGameOver(aiPlayer);
                    stopTimer();
                    gomokuView.invalidate();
                })
                .setNegativeButton(R.string.game_gomoku_resign_cancel, null)
                .show();
    }

    /**
     * 确认重新开始（游戏中弹确认）。
     */
    private void confirmRestart() {
        if (game.isGameOver()) {
            handleRestart();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_gomoku_restart_title)
                .setMessage(R.string.game_gomoku_restart_message)
                .setPositiveButton(R.string.game_gomoku_restart_confirm, (d, w) -> handleRestart())
                .setNegativeButton(R.string.game_gomoku_restart_cancel, null)
                .show();
    }

    /**
     * 处理重新开始操作，重置游戏状态返回难度选择界面。
     */
    private void handleRestart() {
        aiGeneration++;
        aiThinking = false;
        gomokuView.setAiThinking(false);
        game.reset();
        gomokuView.clearHover();
        gomokuView.clearHint();
        gomokuView.setInteractive(false);
        // 隐藏棋盘，让难度选择面板获得充足显示空间
        gomokuView.setVisibility(View.GONE);
        gomokuView.setGame(game);
        stopTimer();
        if (tvTimer != null) {
            tvTimer.setText("00:00");
        }
        // 切换界面：显示难度选择面板，隐藏游戏控制面板
        difficultyPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.GONE);
        gomokuView.invalidate();
    }

    /**
     * 显示退出确认对话框。
     */
    private void showExitConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_gomoku_exit_title)
                .setMessage(R.string.game_gomoku_exit_message)
                .setPositiveButton(R.string.game_gomoku_exit_confirm, (d, w) -> finish())
                .setNegativeButton(R.string.game_gomoku_exit_restart, (d, w) -> handleRestart())
                .setNeutralButton(R.string.game_gomoku_exit_continue, null)
                .show();
    }

    /**
     * 更新计时显示。
     */
    private void updateTimerDisplay() {
        if (tvTimer == null || gameStartElapsedMs == 0L) return;
        long elapsedSec = (SystemClock.elapsedRealtime() - gameStartElapsedMs) / 1000;
        long min = elapsedSec / 60;
        long sec = elapsedSec % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
    }

    /**
     * 停止计时。
     */
    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    /**
     * 游戏结束回调，记录胜负统计。
     *
     * @param winner 获胜方（BLACK/WHITE），null表示平局
     */
    private void handleGameOver(Integer winner) {
        stopTimer();
        // 2026-08-23 P2-2：对局正常结束（胜/负/和/认输），清除存档
        if (saveManager != null) saveManager.clear(GAME_ID);
        // P2-7: 结束回放录制
        if (replayRecorder != null && replayRecorder.isRecording()) {
            String result;
            if (winner == null) {
                result = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_DRAW;
            } else if (winner == playerColor) {
                result = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_WIN;
            } else {
                result = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_LOSS;
            }
            replayRecorder.endRecording(result);
        }
        if (winner != null && winner == playerColor) {
            usageStore.recordWin(GAME_ID);
        } else if (winner != null && winner == aiPlayer) {
            usageStore.recordLoss(GAME_ID);
        }
        // P2-7: 提示查看回放
        if (replayRecorder != null && replayRecorder.hasHistory()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("对局结束")
                    .setMessage("是否查看回放？")
                    .setPositiveButton("查看回放", (d, w) -> {
                        com.gamecenter.app.games.replay.ReplayRecord rec = replayRecorder.loadLatest();
                        com.gamecenter.app.games.replay.ReplayDialog.show(this, rec,
                                new com.gamecenter.app.games.replay.ReplayPlayer.Listener() {
                                    @Override
                                    public void onBoardUpdated(int step, List<com.gamecenter.app.games.replay.ReplayMove> played) {
                                        // 重建棋盘到第 step 步
                                        game.reset();
                                        for (com.gamecenter.app.games.replay.ReplayMove m : played) {
                                            // 统一坐标 (row,col) -> Gomoku (x=col, y=row)
                                            game.makeMove(m.toCol, m.toRow, m.player);
                                            game.switchPlayer();
                                        }
                                        gomokuView.invalidate();
                                    }
                                    @Override
                                    public void onReplayFinished() {}
                                    @Override
                                    public void onReplayReset() {}
                                });
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        }
    }

    /**
     * 保存对局状态，支持旋转屏幕恢复。
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (game == null) return;
        int[][] board = game.getBoard();
        int size = GomokuGame.BOARD_SIZE;
        // 保存棋盘（一维数组）
        int[] flatBoard = new int[size * size];
        for (int y = 0; y < size; y++) {
            System.arraycopy(board[y], 0, flatBoard, y * size, size);
        }
        outState.putIntArray(STATE_BOARD, flatBoard);
        outState.putInt(STATE_CURRENT_PLAYER, game.getCurrentPlayer());
        outState.putInt(STATE_MOVE_COUNT, game.getMoveCount());
        outState.putInt(STATE_AI_DIFFICULTY, aiDifficulty);
        outState.putInt(STATE_PLAYER_COLOR, playerColor);
        outState.putBoolean(STATE_GAME_STARTED, difficultyPanel.getVisibility() == View.GONE);
        // 保存落子历史
        java.util.List<GomokuGame.MoveRecord> history = game.getMoveHistory();
        int[] histX = new int[history.size()];
        int[] histY = new int[history.size()];
        int[] histPlayer = new int[history.size()];
        for (int i = 0; i < history.size(); i++) {
            histX[i] = history.get(i).x;
            histY[i] = history.get(i).y;
            histPlayer[i] = history.get(i).player;
        }
        outState.putIntArray(STATE_HISTORY_X, histX);
        outState.putIntArray(STATE_HISTORY_Y, histY);
        outState.putIntArray(STATE_HISTORY_PLAYER, histPlayer);
    }

    /**
     * 恢复对局状态。
     */
    private void restoreGameState(Bundle state) {
        int[] flatBoard = state.getIntArray(STATE_BOARD);
        if (flatBoard == null) return;
        int size = GomokuGame.BOARD_SIZE;
        int[][] board = game.getBoard();
        for (int y = 0; y < size; y++) {
            System.arraycopy(flatBoard, y * size, board[y], 0, size);
        }
        // 恢复历史记录
        int[] histX = state.getIntArray(STATE_HISTORY_X);
        int[] histY = state.getIntArray(STATE_HISTORY_Y);
        int[] histPlayer = state.getIntArray(STATE_HISTORY_PLAYER);
        if (histX != null) {
            java.util.List<GomokuGame.MoveRecord> history = game.getMoveHistory();
            history.clear();
            for (int i = 0; i < histX.length; i++) {
                history.add(new GomokuGame.MoveRecord(histX[i], histY[i], histPlayer[i]));
            }
        }
        // 恢复状态
        try {
            java.lang.reflect.Field fCurrent = GomokuGame.class.getDeclaredField("currentPlayer");
            fCurrent.setAccessible(true);
            fCurrent.setInt(game, state.getInt(STATE_CURRENT_PLAYER, GomokuGame.BLACK));
            java.lang.reflect.Field fCount = GomokuGame.class.getDeclaredField("moveCount");
            fCount.setAccessible(true);
            fCount.setInt(game, state.getInt(STATE_MOVE_COUNT, 0));
        } catch (Exception e) {
            Log.w(TAG, "恢复游戏状态失败", e);
        }
        // 恢复最后一手
        if (histX != null && histX.length > 0) {
            try {
                java.lang.reflect.Field fLast = GomokuGame.class.getDeclaredField("lastMove");
                fLast.setAccessible(true);
                fLast.set(game, new int[]{histX[histX.length - 1], histY[histY.length - 1]});
            } catch (Exception e) {
                Log.w(TAG, "恢复最后一手失败", e);
            }
        }
        // 恢复难度和先手
        aiDifficulty = state.getInt(STATE_AI_DIFFICULTY, 2);
        playerColor = state.getInt(STATE_PLAYER_COLOR, GomokuGame.BLACK);
        aiPlayer = (playerColor == GomokuGame.BLACK) ? GomokuGame.WHITE : GomokuGame.BLACK;
        boolean started = state.getBoolean(STATE_GAME_STARTED, false);
        if (started) {
            ai = new GomokuAI(aiDifficulty);
            selectDifficulty(aiDifficulty);
            selectPlayerColor(playerColor);
            difficultyPanel.setVisibility(View.GONE);
            controlPanel.setVisibility(View.VISIBLE);
            gomokuView.setInteractive(true);
            gomokuView.setVisibility(View.VISIBLE);
            gomokuView.setGame(game);
            gameStartElapsedMs = SystemClock.elapsedRealtime();
            timerHandler.post(timerRunnable);
        }
    }

    /**
     * Activity销毁时关闭AI线程池和音效资源，防止泄漏。
     */
    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        stopTimer();
        aiExecutor.shutdownNow();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}

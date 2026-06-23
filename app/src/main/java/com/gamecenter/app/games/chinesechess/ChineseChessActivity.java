package com.gamecenter.app.games.chinesechess;

import android.app.AlertDialog;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
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
 * 中国象棋游戏 Activity（继承 BaseGameActivity，UI 升级版）。
 *
 * <p>使用 activity_chinesechess.xml 布局，提供：
 * <ul>
 *   <li>难度选择主页面（4 个难度卡片，木纹卡片风格）</li>
 *   <li>游戏中控制面板（悔棋、重新开始）</li>
 *   <li>返回键确认对话框</li>
 *   <li>AI 思考状态联动（chessView.setAiThinking）</li>
 *   <li>音效集成（落子/胜利/失败）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 2.0
 * @since 2026-06-23
 */
public class ChineseChessActivity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "chinesechess";
    private static final String GAME_NAME_VALUE = "中国象棋";

    /** AI 最小响应延迟（毫秒） */
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {200L, 400L, 800L, 1500L};

    // ==================== 视图引用 ====================

    /** 棋盘视图 */
    private ChineseChessView chessView;

    /** 难度选择面板（初始可见） */
    private ScrollView difficultyPanel;

    /** 游戏控制面板（游戏中可见） */
    private View controlPanel;

    /** 难度按钮组 */
    private final View[] difficultyButtons = new View[4];

    /** 当前选中的难度索引（-1 表示未选择） */
    private int selectedDifficultyIndex = -1;

    // ==================== AI 组件 ====================

    /** AI 引擎 */
    private ChineseChessAI ai;

    /** 当前 AI 难度（1-4） */
    private int aiDifficulty = 2;

    // ==================== 线程管理 ====================

    /** 主线程 Handler */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** AI 计算线程池 */
    private ExecutorService aiExecutor;

    /** AI 思考标志 */
    private volatile boolean aiThinking = false;

    /** AI 代次（用于取消过期的 AI 计算） */
    private volatile long aiGeneration = 0;

    /** 连胜计数 */
    private int winStreak = 0;

    // ==================== 音效 ====================

    /** 落子音效池，复用 R.raw.ui_turn 资源 */
    private SoundPool soundPool;

    /** 落子音效 ID */
    private int soundIdMove = 0;

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 注意：BaseGameActivity.onCreate 会调用 setupGameFramework() 创建 gameContentContainer
        // 并调用 initGame()，但本Activity使用自定义布局，需在 super.onCreate 之前设置布局
        super.onCreate(savedInstanceState);

        // 使用自定义布局替代 BaseGameActivity 的默认 FrameLayout 容器
        setContentView(R.layout.activity_chinesechess);

        // 绑定视图
        chessView = findViewById(R.id.chess_view);
        difficultyPanel = findViewById(R.id.difficulty_panel);
        controlPanel = findViewById(R.id.control_panel);

        difficultyButtons[0] = findViewById(R.id.btn_difficulty_1);
        difficultyButtons[1] = findViewById(R.id.btn_difficulty_2);
        difficultyButtons[2] = findViewById(R.id.btn_difficulty_3);
        difficultyButtons[3] = findViewById(R.id.btn_difficulty_4);

        // 如果 Intent 传入了难度索引，预选该难度
        int intentDifficulty = getIntent().getIntExtra("game_difficulty_index", -1);
        if (intentDifficulty >= 0 && intentDifficulty < 4) {
            selectDifficulty(intentDifficulty);
        }

        // 初始化 AI 引擎（默认普通难度）
        ai = new ChineseChessAI(aiDifficulty);
        aiExecutor = Executors.newSingleThreadExecutor();

        // 设置棋盘交互监听
        chessView.setOnPlayerMoveListener(this::handlePlayerMove);
        chessView.setOnGameOverListener(this::handleGameOver);
        chessView.setOnMoveSoundListener(this::playMoveSound);

        // 初始化音效
        initSoundPool();

        // 绑定按钮事件
        setupDifficultyButtons();
        findViewById(R.id.btn_start_game).setOnClickListener(v -> startGame());
        findViewById(R.id.btn_undo).setOnClickListener(v -> handleUndo());
        findViewById(R.id.btn_restart).setOnClickListener(v -> handleRestart());
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
        // 返回 null，因为本 Activity 使用 setContentView 自定义布局，不使用 BaseGameActivity 的容器
        return null;
    }

    @Override
    protected void initGame() {
        // 初始化在 onCreate 中完成，此处无需操作
    }

    @Override
    protected void startGame() {
        // 检查是否已选择难度
        if (selectedDifficultyIndex < 0) {
            Toast.makeText(this, R.string.chinese_chess_select_difficulty, Toast.LENGTH_SHORT).show();
            return;
        }

        // 应用难度
        aiDifficulty = selectedDifficultyIndex + 1;
        ai = new ChineseChessAI(aiDifficulty);
        currentDifficultyIndex = selectedDifficultyIndex;

        // 切换视图：隐藏难度面板，显示棋盘和控制面板
        difficultyPanel.setVisibility(View.GONE);
        chessView.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);

        // 开始新游戏
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        chessView.startNewGame();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        aiGeneration++;
        aiThinking = false;
        chessView.setAiThinking(false);
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
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

    // ==================== 难度管理 ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("简单", 1, "AI 搜索深度 2，适合新手",
                2, 300, 0.3f, false));
        levels.add(new DifficultyLevel("普通", 2, "AI 搜索深度 4，均衡挑战",
                4, 500, 0.5f, true));
        levels.add(new DifficultyLevel("困难", 3, "AI 搜索深度 6，高手对决",
                6, 800, 0.7f, false));
        levels.add(new DifficultyLevel("大师", 4, "AI 搜索深度 8，终极挑战",
                8, 1500, 1.0f, false));
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

    // ==================== 难度选择 UI ====================

    /**
     * 设置难度按钮点击事件
     */
    private void setupDifficultyButtons() {
        for (int i = 0; i < difficultyButtons.length; i++) {
            final int index = i;
            difficultyButtons[i].setOnClickListener(v -> selectDifficulty(index));
        }
    }

    /**
     * 选择难度
     */
    private void selectDifficulty(int index) {
        if (index < 0 || index >= difficultyButtons.length) return;
        selectedDifficultyIndex = index;
        // 更新按钮选中状态
        for (int i = 0; i < difficultyButtons.length; i++) {
            difficultyButtons[i].setSelected(i == index);
        }
    }

    // ==================== 游戏交互 ====================

    /**
     * 处理玩家走棋后触发 AI
     */
    private void handlePlayerMove() {
        if (!isGameRunning() || isGamePaused()) return;
        if (chessView.isGameOver()) return;

        // 设置 AI 思考状态
        aiThinking = true;
        chessView.setAiThinking(true);

        final long currentGen = aiGeneration;
        final long startMs = System.currentTimeMillis();

        aiExecutor.execute(() -> {
            int[] bestMove = ai.getBestMove(chessView.getBoardState(), aiDifficulty);
            long elapsed = System.currentTimeMillis() - startMs;
            int idx = Math.max(0, Math.min(aiDifficulty - 1, AI_MIN_RESPONSE_DELAYS_MS.length - 1));
            long delay = Math.max(AI_MIN_RESPONSE_DELAYS_MS[idx] - elapsed, 0L);

            Runnable applyMove = () -> {
                if (currentGen != aiGeneration) return;
                if (bestMove != null && bestMove.length >= 4) {
                    chessView.applyAIMove(bestMove[0], bestMove[1], bestMove[2], bestMove[3]);
                }
                aiThinking = false;
                chessView.setAiThinking(false);
            };

            if (delay > 0L) {
                mainHandler.postDelayed(applyMove, delay);
            } else {
                mainHandler.post(applyMove);
            }
        });
    }

    /**
     * 游戏结束回调
     */
    private void handleGameOver(int winner) {
        // winner: 1=红方(玩家)胜, 2=黑方(AI)胜
        aiThinking = false;
        chessView.setAiThinking(false);

        if (winner == 1) {
            playWinSound();
            usageStore.recordWin(GAME_ID_VALUE);
            updateScore(getCurrentScore() + 100);
            checkAchievement("win");
            Toast.makeText(this, R.string.chinese_chess_win, Toast.LENGTH_LONG).show();
        } else if (winner == 2) {
            playLossSound();
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("loss");
            Toast.makeText(this, R.string.chinese_chess_lose, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 悔棋
     */
    private void handleUndo() {
        if (aiThinking) {
            Toast.makeText(this, R.string.chinese_chess_undo_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        // 取消可能正在进行的 AI 计算
        aiGeneration++;
        if (!chessView.undoMove()) {
            Toast.makeText(this, R.string.chinese_chess_undo_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 重新开始
     */
    private void handleRestart() {
        // 取消 AI 计算
        aiGeneration++;
        aiThinking = false;
        chessView.setAiThinking(false);

        // 重置棋盘
        chessView.startNewGame();
        isGameRunning = true;
        isGamePaused = false;
    }

    /**
     * 返回键处理：显示确认对话框
     */
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

    /**
     * 初始化 SoundPool 并加载落子音效（复用 R.raw.ui_turn）。
     */
    private void initSoundPool() {
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(2).build();
            soundIdMove = soundPool.load(this, R.raw.ui_turn, 1);
        } catch (Exception ignored) {
            soundIdMove = 0;
        }
    }

    /**
     * 判断是否应播放游戏音效（受用户设置总开关控制）。
     */
    private boolean isSoundAllowed() {
        return SettingsManager.getInstance(this).shouldPlayGameSound();
    }

    /**
     * 播放落子音效。
     */
    private void playMoveSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 0.6f, 0.6f, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    /**
     * 播放胜利音效。
     */
    private void playWinSound() {
        if (!isSoundAllowed() || soundPool == null || soundIdMove == 0) return;
        try {
            soundPool.play(soundIdMove, 1.0f, 1.0f, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    /**
     * 播放失败音效。
     */
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
    }
}

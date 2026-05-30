package com.gamecenter.app.games.chinesechess;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中国象棋游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用 BaseGameActivity 框架，集成成就系统、教程系统和难度管理系统。
 * 复用 {@link ChineseChessView} 进行渲染，{@link ChineseChessAI} 提供 AI 对手。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>继承 BaseGameActivity 获得统一的游戏生命周期管理</li>
 *   <li>AI 使用 Minimax + Alpha-Beta 剪枝算法</li>
 *   <li>4 个难度级别对应不同的搜索深度</li>
 *   <li>成就系统跟踪首次胜利、连胜和不同难度胜利</li>
 *   <li>AI 计算在后台线程池执行，避免阻塞 UI</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class ChineseChessActivity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "chinesechess";
    private static final String GAME_NAME_VALUE = "中国象棋";

    /** AI 最小响应延迟（毫秒） */
    private static final long[] AI_MIN_RESPONSE_DELAYS_MS = {200L, 400L, 800L, 1500L};

    // ==================== 游戏组件 ====================

    /** 棋盘视图 */
    private ChineseChessView chessView;

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

    /** AI 代次 */
    private volatile long aiGeneration = 0;

    /** 连胜计数 */
    private int winStreak = 0;

    // ==================== BaseGameActivity 实现 ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // BaseGameActivity.onCreate 未自动调用 startGame，这里手动启动游戏
        startGame();
    }

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
        return chessView;
    }

    @Override
    protected void initGame() {
        // 使用从对话框传来的难度索引（BaseGameActivity已读取）
        List<DifficultyLevel> levels = getDifficultyLevels();
        if (currentDifficultyIndex >= 0 && currentDifficultyIndex < levels.size()) {
            aiDifficulty = levels.get(currentDifficultyIndex).level;
        }
        // 创建游戏组件
        ai = new ChineseChessAI(aiDifficulty);
        chessView = new ChineseChessView(this);

        // AI 线程池
        aiExecutor = Executors.newSingleThreadExecutor();

        // 设置交互监听
        chessView.setOnPlayerMoveListener(this::handlePlayerMove);
        chessView.setOnGameOverListener(this::handleGameOver);
        // 注意：chessView 由 BaseGameActivity.onCreate() 通过 getGameContentView() 自动添加到容器
    }

    @Override
    protected void startGame() {
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
                // 首次胜利
                achievementManager.checkAndUnlock("first_win", 1);
                // 累计胜利
                int wins = usageStore.getWinCount(GAME_ID_VALUE);
                achievementManager.checkAndUnlock("win_10", wins);
                achievementManager.checkAndUnlock("win_50", wins);
                // 连胜
                winStreak++;
                achievementManager.checkAndUnlock("streak_3", winStreak);
                achievementManager.checkAndUnlock("streak_5", winStreak);
                // 不同难度胜利
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
        Toast.makeText(this, "难度已切换为：" + newLevel.name, Toast.LENGTH_SHORT).show();
    }

    // ==================== 游戏交互 ====================

    /**
     * 处理玩家走棋后触发 AI
     */
    private void handlePlayerMove() {
        if (!isGameRunning() || isGamePaused()) return;

        // 检查游戏是否结束
        if (chessView.isGameOver()) return;

        // 触发 AI 计算
        aiThinking = true;
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
        // winner: 1=红方(玩家)胜, 2=黑方(AI)胜, 0=平局
        if (winner == 1) {
            usageStore.recordWin(GAME_ID_VALUE);
            updateScore(getCurrentScore() + 100);
            checkAchievement("win");
        } else if (winner == 2) {
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("loss");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}

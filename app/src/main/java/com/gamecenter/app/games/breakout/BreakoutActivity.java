package com.gamecenter.app.games.breakout;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;

/**
 * 打砖块游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用自定义 {@link BreakoutView} 进行游戏渲染。
 * 挡板随手指移动，球反弹消除砖块。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>不丢球通关</li>
 *   <li>通过3关</li>
 *   <li>单关得分100+</li>
 *   <li>累计10关</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class BreakoutActivity extends BaseGameActivity {

    // ==================== 游戏组件 ====================
    private BreakoutView breakoutView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentLevel = 1;
    private int totalLevels = 0;

    /** 2026-08-23 P3: 统一音效/震动反馈（内部实时遵循设置开关） */
    private com.gamecenter.app.games.base.GameFeedback feedback;

    // 游戏循环
    private static final long FRAME_INTERVAL_MS = 16; // ~60 FPS
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            // 由 Activity 的 isGameRunning / isGamePaused 控制循环存亡，
            // 不再依赖 view.isGameRunning()，避免初次测量前或过关/结束瞬间循环误停。
            if (!isGameRunning || isGamePaused) return;
            if (breakoutView != null) {
                breakoutView.update();
            }
            handler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "breakout";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_breakout_name);
    }

    @Override
    protected void initGame() {
        // 2026-08-23 P3：初始化音效/震动反馈
        feedback = new com.gamecenter.app.games.base.GameFeedback(this);
        breakoutView = new BreakoutView(this);

        breakoutView.setOnGameListener(new BreakoutView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                // 分数由 BreakoutView 统一累计（含过关奖励），此处直接镜像，避免重复累加。
                updateScore(score);
            }

            @Override
            public void onGameOver(boolean win) {
                handler.removeCallbacks(gameLoop);
                // BUG-005 修复：根据 win 参数判断记录胜场还是负场，之前忽略 win 一律 recordLoss，
                // 导致 5 次对局但胜 0/负 0（实际上负场应被记录）。
                if (win) {
                    usageStore.recordWin(getGameId());
                    // 2026-08-23 P3：胜利反馈
                    if (feedback != null) feedback.feedbackWin();
                } else {
                    usageStore.recordLoss(getGameId());
                    // 2026-08-23 P3：失败反馈
                    if (feedback != null) feedback.feedbackLose();
                }
                checkAchievement("game_over", breakoutView.getScore());
                checkAchievement("rounds", totalLevels);
                // BUG-005 剩余修复：游戏自然结束时（球掉光触发 onGameOver）也要记录 play_time。
                // 之前 onGameOver 末尾设置 isGameRunning = false，导致 onDestroy 中
                // if (isGameRunning) 判断为 false，endGame() 不会被调用，recordPlayTime 未执行。
                // 修复：在 isGameRunning 置 false 之前先调用 endGame()，确保 play_time 被记录。
                // 注意：endGame() 内部会判断 gameStartTime > 0 才记录，且重复调用安全。
                long duration = gameStartTime > 0 ? System.currentTimeMillis() - gameStartTime : 0L;
                endGame();
                // P0-1: 提交本局分数到本地排行榜
                int finalScore = breakoutView != null ? breakoutView.getScore() : currentScore;
                if (finalScore > 0) {
                    submitScoreToLeaderboard(finalScore, duration);
                }
                isGameRunning = false;
            }

            @Override
            public void onLevelComplete(int level) {
                handler.removeCallbacks(gameLoop);
                totalLevels++;

                // 分数已在 BreakoutView.onLevelCleared 中累计（含过关奖励），直接镜像。
                updateScore(breakoutView.getScore());

                usageStore.recordWin(getGameId());

                // 2026-08-23 P3：过关胜利反馈
                if (feedback != null) feedback.feedbackWin();

                checkAchievement("win", totalLevels);
                checkAchievement("score", breakoutView.getLastLevelScore());
                checkAchievement("level", level);
                checkAchievement("rounds", totalLevels);
                // 不丢球通关成就
                if (breakoutView.isLevelNoMiss()) {
                    checkAchievement("no_miss", true);
                }

                // 进入下一关（保留分数与生命）
                currentLevel++;
                handler.postDelayed(() -> {
                    if (isGameRunning) {
                        breakoutView.startNextLevel(currentLevel);
                        handler.post(gameLoop);
                    }
                }, 1500);
            }
        });

        if (gameContentContainer instanceof FrameLayout) {
            ((FrameLayout) gameContentContainer).addView(breakoutView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        breakoutView.startGame(currentLevel);
        handler.post(gameLoop);
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        if (breakoutView != null) {
            breakoutView.pauseGame();
        }
        handler.removeCallbacks(gameLoop);
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (breakoutView != null) {
            breakoutView.resumeGame();
        }
        handler.post(gameLoop);
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        handler.removeCallbacks(gameLoop);
        if (breakoutView != null) {
            breakoutView.stopGame();
        }
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        // 使用带 gameId 隔离的新签名，避免跨游戏串扰
        if (params == null || params.length == 0) return;
        Object first = params[0];
        if (first instanceof Boolean) {
            if ((Boolean) first) achievementManager.unlock(getGameId(), eventType);
        } else if (first instanceof Number) {
            int currentValue = ((Number) first).intValue();
            if (params.length >= 2 && params[1] instanceof Number) {
                int threshold = ((Number) params[1]).intValue();
                achievementManager.checkAndUnlock(getGameId(), eventType, currentValue, threshold);
            } else {
                // 单参数：记录进度不解锁，需要提供合理阈值
                // 根据事件类型设置默认阈值
                int threshold = getAchievementThreshold(eventType);
                achievementManager.checkAndUnlock(getGameId(), eventType, currentValue, threshold);
            }
        }
    }

    /** 根据事件类型返回默认解锁阈值 */
    private int getAchievementThreshold(@NonNull String eventType) {
        switch (eventType) {
            case "score": return 100;       // 单关得分100+
            case "level": return 1;          // 通过第1关
            case "rounds": return 3;         // 累计3关
            case "win": return 1;            // 首次通关
            case "game_over": return 1;      // 游戏结束
            default: return Integer.MAX_VALUE; // 未知类型不解锁
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 自动启动 / 从后台恢复：未运行时开始新游戏；已暂停时恢复（修复后台返回后游戏冻结）。
        if (!isGameRunning) {
            startGame();
        } else if (isGamePaused) {
            resumeGame();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // BUG-005 修复：onPause 时暂停游戏循环，避免后台运行浪费 CPU。
        // 不调用 endGame()，因为用户可能只是切到后台，会再回来（onResume 会判断 isGameRunning）。
        if (isGameRunning && !isGamePaused) {
            pauseGame();
        }
    }

    @Override
    protected void onDestroy() {
        // BUG-005 修复：onDestroy 中先调用 endGame() 再清理 handler，
        // 确保用户中途退出时 recordPlayTime 仍会被调用，避免总时长不被记录。
        // 之前 onDestroy 只清理 handler，导致 endGame 永远不会被调用，总时长始终为 0。
        if (isGameRunning) {
            // BUG-005 剩余修复：用户中途退出（BACK/离开 Activity）时，BreakoutView.onGameOver
            // 不会被触发（onGameOver 仅在 lives<=0 球掉光时调用）。这导致总对局=3 但胜 0/负 0，
            // 因为 recordWin/recordLoss 从未被调用。
            // 修复：onDestroy 时如果游戏还在运行（即 onGameOver 未触发），视为本局失败，
            // 记录一次负场，使胜负数据与总对局匹配。
            // 注意：onGameOver 中会设置 isGameRunning=false，因此若 onGameOver 已被触发，
            // 此处 if (isGameRunning) 为 false，不会重复记录，避免双重计入。
            usageStore.recordLoss(getGameId());
            endGame();
        }
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        // 2026-08-23 P3：释放音效资源
        if (feedback != null) {
            feedback.release();
            feedback = null;
        }
    }
}

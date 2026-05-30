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

    // 游戏循环
    private static final long FRAME_INTERVAL_MS = 16; // ~60 FPS
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (breakoutView != null && breakoutView.isGameRunning()) {
                breakoutView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
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
        return "打砖块";
    }

    @Override
    protected void initGame() {
        breakoutView = new BreakoutView(this);

        breakoutView.setOnGameListener(new BreakoutView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                updateScore(currentScore + score);
            }

            @Override
            public void onGameOver(boolean win) {
                handler.removeCallbacks(gameLoop);
                usageStore.recordLoss(getGameId());
                checkAchievement("game_over", breakoutView.getScore());
                checkAchievement("rounds", totalLevels);
                isGameRunning = false;
            }

            @Override
            public void onLevelComplete(int level) {
                handler.removeCallbacks(gameLoop);
                totalLevels++;
                int levelScore = breakoutView.getScore();
                currentScore += levelScore + level * 50;
                updateScore(currentScore);

                usageStore.recordWin(getGameId());

                checkAchievement("win", totalLevels);
                checkAchievement("score", levelScore);
                checkAchievement("level", level);
                checkAchievement("rounds", totalLevels);

                // 进入下一关
                currentLevel++;
                handler.postDelayed(() -> {
                    if (isGameRunning) {
                        breakoutView.startGame(currentLevel);
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
        achievementManager.checkAndUnlock(eventType, params);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

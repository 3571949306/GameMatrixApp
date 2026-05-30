package com.gamecenter.app.games.flappy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;

/**
 * 飞翔的小鸟游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用自定义 {@link FlappyView} 进行游戏渲染。
 * 点击屏幕使小鸟上升，穿过管道间隙得分。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次得分</li>
 *   <li>得分10+</li>
 *   <li>得分30+</li>
 *   <li>累计10局</li>
 *   <li>得分50+</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class FlappyActivity extends BaseGameActivity {

    // ==================== 游戏组件 ====================
    private FlappyView flappyView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int totalGames = 0;
    private int bestScore = 0;

    private static final long FRAME_INTERVAL_MS = 16;
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (flappyView != null && flappyView.isGameRunning()) {
                flappyView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "flappy";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return "飞翔的小鸟";
    }

    @Override
    protected void initGame() {
        flappyView = new FlappyView(this);

        flappyView.setOnGameListener(new FlappyView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                updateScore(currentScore + score);
            }

            @Override
            public void onGameOver(int score) {
                handler.removeCallbacks(gameLoop);
                totalGames++;
                if (score > bestScore) {
                    bestScore = score;
                }
                currentScore += score;
                updateScore(currentScore);

                usageStore.recordLoss(getGameId());

                checkAchievement("score", score);
                checkAchievement("rounds", totalGames);
                checkAchievement("win", totalGames);

                if (score >= 10) checkAchievement("special", 1);
                if (score >= 30) checkAchievement("special", 2);
                if (score >= 50) checkAchievement("special", 3);

                isGameRunning = false;
            }
        });

        if (gameContentContainer instanceof FrameLayout) {
            ((FrameLayout) gameContentContainer).addView(flappyView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        flappyView.startGame();
        handler.post(gameLoop);
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        if (flappyView != null) {
            flappyView.pauseGame();
        }
        handler.removeCallbacks(gameLoop);
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (flappyView != null) {
            flappyView.resumeGame();
        }
        handler.post(gameLoop);
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        handler.removeCallbacks(gameLoop);
        if (flappyView != null) {
            flappyView.stopGame();
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

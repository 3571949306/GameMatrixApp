package com.gamecenter.app.games.brotato;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;

/**
 * 土豆兄弟游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用自定义 {@link BrotatoView} 进行游戏渲染。
 * 控制土豆角色移动并自动射击敌人，生存更多波次。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次生存过第1波</li>
 *   <li>生存过第3波</li>
 *   <li>生存过第5波</li>
 *   <li>单局得分200+</li>
 *   <li>累计10局</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class BrotatoActivity extends BaseGameActivity {

    // ==================== 游戏组件 ====================
    private BrotatoView brotatoView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int totalGames = 0;
    private int bestWave = 0;

    private static final long FRAME_INTERVAL_MS = 16;
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (brotatoView != null && brotatoView.isGameRunning()) {
                brotatoView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "brotato";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return "土豆兄弟";
    }

    @Override
    protected void initGame() {
        brotatoView = new BrotatoView(this);

        brotatoView.setOnGameListener(new BrotatoView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                updateScore(currentScore + score);
            }

            @Override
            public void onGameOver(int score, int wave) {
                handler.removeCallbacks(gameLoop);
                totalGames++;
                if (wave > bestWave) {
                    bestWave = wave;
                }
                currentScore += score;
                updateScore(currentScore);

                usageStore.recordLoss(getGameId());

                checkAchievement("score", score);
                checkAchievement("wave", wave);
                checkAchievement("rounds", totalGames);
                checkAchievement("win", totalGames);

                if (wave >= 3) checkAchievement("special", 1);
                if (wave >= 5) checkAchievement("special", 2);
                if (score >= 200) checkAchievement("special", 3);

                isGameRunning = false;
            }

            @Override
            public void onWaveComplete(int wave) {
                checkAchievement("wave", wave);
                if (wave >= 1) checkAchievement("special", 0);
            }
        });

        if (gameContentContainer instanceof FrameLayout) {
            ((FrameLayout) gameContentContainer).addView(brotatoView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        brotatoView.startGame();
        handler.post(gameLoop);
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        if (brotatoView != null) {
            brotatoView.pauseGame();
        }
        handler.removeCallbacks(gameLoop);
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (brotatoView != null) {
            brotatoView.resumeGame();
        }
        handler.post(gameLoop);
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        handler.removeCallbacks(gameLoop);
        if (brotatoView != null) {
            brotatoView.stopGame();
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

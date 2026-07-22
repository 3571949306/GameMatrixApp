package com.gamecenter.app.games.plane;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞机大战游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用自定义 {@link PlaneView} 进行游戏渲染。
 * 控制飞机左右移动并自动射击敌人。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次击毁敌机</li>
 *   <li>单局得分100+</li>
 *   <li>单局得分500+</li>
 *   <li>达到波次3</li>
 *   <li>累计10局</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class PlaneActivity extends BaseGameActivity {

    // ==================== 游戏组件 ====================
    private PlaneView planeView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentWave = 1;
    private int totalGames = 0;
    private float difficultyFactor = 0.5f;

    private static final long FRAME_INTERVAL_MS = 16;
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (planeView != null && planeView.isGameRunning()) {
                planeView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "plane";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_plane_name);
    }

    @Override
    protected void initGame() {
        planeView = new PlaneView(this);
        applyDifficulty();

        planeView.setOnGameListener(new PlaneView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                updateScore(score);
                // 同步当前波次（修复 currentWave 恒为 1 的死逻辑）
                currentWave = planeView.getWave();
            }

            @Override
            public void onGameOver(int score) {
                handler.removeCallbacks(gameLoop);
                totalGames++;
                updateScore(score);
                recordHighScore(score);

                usageStore.recordLoss(getGameId());

                checkAchievement("score", score);
                checkAchievement("rounds", totalGames);
                checkAchievement("win", totalGames);
                checkAchievement("wave", planeView.getWave());

                if (score >= 100) checkAchievement("special", 1);
                if (score >= 500) checkAchievement("special", 2);
                if (planeView.getWave() >= 3) checkAchievement("special", 3);

                isGameRunning = false;
            }
        });

        if (gameContentContainer instanceof FrameLayout) {
            ((FrameLayout) gameContentContainer).addView(planeView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        currentWave = 1;
        planeView.startGame(currentWave);
        handler.post(gameLoop);
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        if (planeView != null) {
            planeView.pauseGame();
        }
        handler.removeCallbacks(gameLoop);
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (planeView != null) {
            planeView.resumeGame();
        }
        handler.post(gameLoop);
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        handler.removeCallbacks(gameLoop);
        if (planeView != null) {
            planeView.stopGame();
        }
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_plane_diff_easy), 1, getString(R.string.game_plane_diff_easy_desc), 0, 0, 0.3f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_plane_diff_normal), 2, getString(R.string.game_plane_diff_normal_desc), 0, 0, 0.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_plane_diff_hard), 3, getString(R.string.game_plane_diff_hard_desc), 0, 0, 0.8f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        difficultyFactor = newLevel.difficultyFactor;
        applyDifficulty();
    }

    /**
     * 根据当前难度因子配置 PlaneView（影响敌机速度/生成间隔）。
     */
    private void applyDifficulty() {
        if (planeView == null) return;
        planeView.setDifficultyFactor(difficultyFactor);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

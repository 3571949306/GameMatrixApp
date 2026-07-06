package com.gamecenter.app.games.snake;

import android.media.SoundPool;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 贪吃蛇游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用 BaseGameActivity 框架，集成成就系统、教程系统和难度管理系统。
 * 游戏逻辑和渲染由 {@link SnakeView} 负责。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>继承 BaseGameActivity 获得统一的游戏生命周期管理</li>
 *   <li>重写 getGameContentView() 提供自定义游戏视图</li>
 *   <li>难度通过速度系数调节（简单=慢速，困难=快速）</li>
 *   <li>成就系统跟踪首次得分、高分、特定长度等里程碑</li>
 *   <li>支持暂停/恢复（停止/恢复定时器）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class SnakeActivity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "snake";
    private static final String GAME_NAME_VALUE = "贪吃蛇";
    private static final String TAG = "SnakeActivity";

    // ==================== 游戏组件 ====================

    /** 游戏视图 */
    private SnakeView snakeView;

    /** 连胜计数 */
    private int winStreak = 0;

    // ==================== 音效组件 ====================

    /** 音效播放器，用于播放吃食物和游戏结束音效 */
    private SoundPool soundPool;

    /** 吃食物音效 ID（复用 R.raw.ui_turn） */
    private int eatSoundId = 0;

    /** 游戏结束（撞墙/死亡）音效 ID（复用 R.raw.ui_turn） */
    private int deathSoundId = 0;

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
        return snakeView;
    }

    @Override
    protected void initGame() {
        snakeView = new SnakeView(this);

        // 设置游戏事件监听
        snakeView.setOnScoreChangeListener(score -> {
            updateScore(score);
            checkAchievement("score", score);
        });

        snakeView.setOnGameOverListener(score -> {
            // 游戏结束（撞墙/死亡）时播放死亡音效
            playDeathSound();
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("game_over", score);
            isGameRunning = false;
        });

        snakeView.setOnFoodEatenListener(length -> {
            // 吃到食物时播放吃食物音效
            playEatSound();
            checkAchievement("length", length);
        });

        // 添加视图到容器
        if (gameContentContainer != null) {
            ((android.widget.FrameLayout) gameContentContainer).addView(snakeView);
        }

        // 初始化音效：复用现有 R.raw.ui_turn 资源，不新增资源文件
        initSounds();
    }

    /**
     * 初始化 SoundPool 并加载音效资源。
     * <p>复用现有的 {@code R.raw.ui_turn} 作为吃食物音和死亡音，避免新增资源文件。
     * 加载失败时静默处理，不影响游戏正常运行。</p>
     */
    private void initSounds() {
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(2).build();
            eatSoundId = soundPool.load(this, R.raw.ui_turn, 1);
            deathSoundId = soundPool.load(this, R.raw.ui_turn, 1);
        } catch (Exception e) {
            Log.w(TAG, "音效操作失败", e);
        }
    }

    /**
     * 播放吃食物音效。
     * <p>播放前检查用户设置 {@link SettingsManager#shouldPlayGameSound()}，
     * 仅在音效开关开启时播放。</p>
     */
    private void playEatSound() {
        if (!SettingsManager.getInstance(this).shouldPlayGameSound()) return;
        if (soundPool == null || eatSoundId == 0) return;
        try {
            soundPool.play(eatSoundId, 0.6f, 0.6f, 1, 0, 1.0f);
        } catch (Exception e) {
            Log.w(TAG, "音效操作失败", e);
        }
    }

    /**
     * 播放游戏结束（撞墙/死亡）音效。
     * <p>播放前检查用户设置 {@link SettingsManager#shouldPlayGameSound()}，
     * 仅在音效开关开启时播放。音调略低以区分吃食物音。</p>
     */
    private void playDeathSound() {
        if (!SettingsManager.getInstance(this).shouldPlayGameSound()) return;
        if (soundPool == null || deathSoundId == 0) return;
        try {
            soundPool.play(deathSoundId, 0.8f, 0.8f, 1, 0, 0.6f);
        } catch (Exception e) {
            Log.w(TAG, "音效操作失败", e);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        snakeView.startGame();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        snakeView.pauseGame();
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        snakeView.resumeGame();
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        snakeView.stopGame();
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        switch (eventType) {
            case "score":
                int score = (int) params[0];
                achievementManager.checkAndUnlock("first_score", 1);
                achievementManager.checkAndUnlock("score_100", score);
                achievementManager.checkAndUnlock("score_500", score);
                achievementManager.checkAndUnlock("score_1000", score);
                break;
            case "length":
                int length = (int) params[0];
                achievementManager.checkAndUnlock("length_20", length);
                break;
            case "game_over":
                int finalScore = (int) params[0];
                if (finalScore >= 100) {
                    usageStore.recordWin(GAME_ID_VALUE);
                }
                break;
        }
    }

    // ==================== 难度管理 ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("简单", 1, "蛇移动速度较慢，适合新手",
                0, 0, 0.3f, false));
        levels.add(new DifficultyLevel("普通", 2, "标准速度，均衡挑战",
                0, 0, 0.5f, true));
        levels.add(new DifficultyLevel("困难", 3, "蛇移动速度较快，反应挑战",
                0, 0, 0.8f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        if (snakeView != null) snakeView.setSpeedFactor(newLevel.difficultyFactor);
        Toast.makeText(this, "难度已切换为：" + newLevel.name, Toast.LENGTH_SHORT).show();
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放 SoundPool 资源，避免内存泄漏
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}

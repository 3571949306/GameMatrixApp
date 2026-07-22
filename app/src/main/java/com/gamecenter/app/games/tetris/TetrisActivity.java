package com.gamecenter.app.games.tetris;

import android.media.SoundPool;
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
 * 俄罗斯方块游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用 BaseGameActivity 框架，集成成就系统、教程系统和难度管理系统。
 * 游戏逻辑和渲染由 {@link TetrisView} 负责。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>继承 BaseGameActivity 获得统一的游戏生命周期管理</li>
 *   <li>难度通过下落速度调节（简单=慢速下落，困难=快速下落）</li>
 *   <li>成就系统跟踪得分、消行数、等级等里程碑</li>
 *   <li>支持暂停/恢复（停止/恢复定时器）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class TetrisActivity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "tetris";

    // ==================== 游戏组件 ====================

    /** 游戏视图 */
    private TetrisView tetrisView;

    /** 消行计数（用于成就） */
    private int totalLinesCleared = 0;

    /** 音效播放器（复用 R.raw.ui_turn 资源） */
    private SoundPool soundPool;
    /** 旋转音效 id */
    private int soundIdRotate = 0;
    /** 落地音效 id */
    private int soundIdLand = 0;
    /** 消行音效 id */
    private int soundIdClear = 0;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return GAME_ID_VALUE;
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_tetris_name);
    }

    @Nullable
    @Override
    protected View getGameContentView() {
        return tetrisView;
    }

    @Override
    protected void initGame() {
        tetrisView = new TetrisView(this);

        // 初始化音效播放器，加载音效资源（复用现有 R.raw.ui_turn）
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(4).build();
            soundIdRotate = soundPool.load(this, R.raw.ui_turn, 1);
            soundIdLand = soundPool.load(this, R.raw.ui_turn, 1);
            soundIdClear = soundPool.load(this, R.raw.ui_turn, 1);
        } catch (Exception e) {
            soundPool = null;
        }

        // 设置游戏事件监听
        tetrisView.setOnScoreChangeListener(score -> {
            updateScore(score);
            checkAchievement("score", score);
        });

        tetrisView.setOnLinesClearedListener(lines -> {
            totalLinesCleared += lines;
            checkAchievement("lines", totalLinesCleared);
            // 消4行额外奖励
            if (lines >= 4) {
                checkAchievement("tetris_clear", lines);
            }
            playSound(soundIdClear);
        });

        tetrisView.setOnLevelChangeListener(level -> {
            checkAchievement("level", level);
        });

        tetrisView.setOnGameOverListener(score -> {
            usageStore.recordLoss(GAME_ID_VALUE);
            isGameRunning = false;
        });

        // 方块旋转音效
        tetrisView.setOnPieceRotateListener(() -> playSound(soundIdRotate));
        // 方块落地音效
        tetrisView.setOnPieceLandListener(() -> playSound(soundIdLand));

        // 添加视图到容器
        if (gameContentContainer != null) {
            ((android.widget.FrameLayout) gameContentContainer).addView(tetrisView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        totalLinesCleared = 0;
        tetrisView.startGame();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        tetrisView.pauseGame();
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        tetrisView.resumeGame();
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        tetrisView.stopGame();
    }

    // ==================== 音效 ====================

    /**
     * 播放指定音效。播放前检查用户设置中的游戏音效开关。
     *
     * @param soundId SoundPool 加载返回的音效 id
     */
    private void playSound(int soundId) {
        if (soundPool == null || soundId == 0) return;
        if (!SettingsManager.getInstance(this).shouldPlayGameSound()) return;
        try {
            soundPool.play(soundId, 0.6f, 0.6f, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        switch (eventType) {
            case "score":
                int score = (int) params[0];
                achievementManager.checkAndUnlock("first_score", 1);
                achievementManager.checkAndUnlock("score_1000", score);
                achievementManager.checkAndUnlock("score_5000", score);
                achievementManager.checkAndUnlock("score_10000", score);
                break;
            case "lines":
                int lines = (int) params[0];
                achievementManager.checkAndUnlock("clear_10", lines);
                achievementManager.checkAndUnlock("clear_50", lines);
                break;
            case "tetris_clear":
                usageStore.recordWin(GAME_ID_VALUE);
                achievementManager.checkAndUnlock("tetris_perfect", 1);
                break;
            case "level":
                int level = (int) params[0];
                achievementManager.checkAndUnlock("level_5", level);
                break;
        }
    }

    // ==================== 难度管理 ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_easy), 1, getString(R.string.game_tetris_diff_easy_desc),
                0, 0, 0.3f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_normal), 2, getString(R.string.game_tetris_diff_normal_desc),
                0, 0, 0.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_tetris_diff_hard), 3, getString(R.string.game_tetris_diff_hard_desc),
                0, 0, 0.8f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        if (tetrisView != null) tetrisView.setSpeedFactor(newLevel.difficultyFactor);
        Toast.makeText(this, getString(R.string.game_tetris_difficulty_changed, newLevel.name), Toast.LENGTH_SHORT).show();
    }
}

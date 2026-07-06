package com.gamecenter.app.games.game2048;

import android.media.SoundPool;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 2048 游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用 BaseGameActivity 框架，集成成就系统、教程系统和难度管理系统。
 * 游戏逻辑和渲染由 {@link Game2048View} 负责。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>继承 BaseGameActivity 获得统一的游戏生命周期管理</li>
 *   <li>无需 AI，难度通过特殊方块出现概率调节</li>
 *   <li>成就系统跟踪首次合成、特定数字、高分等里程碑</li>
 *   <li>事件驱动游戏，无需定时器</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class Game2048Activity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "game2048";
    private static final String GAME_NAME_VALUE = "2048";
    private static final String TAG = "Game2048Activity";

    // ==================== 游戏组件 ====================

    /** 游戏视图 */
    private Game2048View game2048View;

    /** 已合成的最大数字 */
    private int maxTileValue = 0;

    /** 音效播放器，用于滑动和合并音效 */
    private SoundPool soundPool;

    /** 加载的音效资源 ID（复用 R.raw.ui_turn） */
    private int gameSoundId = 0;

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
        return game2048View;
    }

    @Override
    protected void initGame() {
        game2048View = new Game2048View(this);

        // 初始化音效：复用现有的 R.raw.ui_turn 资源
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(2).build();
            gameSoundId = soundPool.load(this, R.raw.ui_turn, 1);
            game2048View.setSoundPool(soundPool, gameSoundId);
        } catch (Exception e) {
            Log.w(TAG, "SoundPool 初始化失败", e);
        }

        // 设置游戏事件监听
        game2048View.setOnScoreChangeListener(score -> {
            updateScore(score);
            checkAchievement("score", score);
        });

        game2048View.setOnTileMergedListener(newValue -> {
            if (newValue > maxTileValue) {
                maxTileValue = newValue;
                checkAchievement("tile", maxTileValue);
            }
        });

        game2048View.setOnGameOverListener(score -> {
            usageStore.recordLoss(GAME_ID_VALUE);
            isGameRunning = false;
        });

        game2048View.setOnWinListener(score -> {
            usageStore.recordWin(GAME_ID_VALUE);
            checkAchievement("win", score);
        });

        // 添加视图到容器
        if (gameContentContainer != null) {
            ((android.widget.FrameLayout) gameContentContainer).addView(game2048View);
        }

        // 2026-06-23: 撤销按钮（悬浮在右上角，点击撤销上一步）
        if (gameContentContainer instanceof android.widget.FrameLayout) {
            com.google.android.material.button.MaterialButton btnUndo =
                    new com.google.android.material.button.MaterialButton(this);
            btnUndo.setText("↶ 撤销");
            btnUndo.setTextSize(12f);
            btnUndo.setMinWidth(0);
            btnUndo.setPadding(16, 6, 16, 6);
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            lp.setMargins(0, 24, 16, 0);
            btnUndo.setLayoutParams(lp);
            btnUndo.setOnClickListener(v -> {
                if (game2048View.undo()) {
                    updateScore(game2048View.getScore() - 10);
                    android.widget.Toast.makeText(this, "已撤销", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(this, "已无历史可撤销",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            ((android.widget.FrameLayout) gameContentContainer).addView(btnUndo);

            // 2026-06-23: 重做按钮（undo 的反向，还原撤销前的状态）
            com.google.android.material.button.MaterialButton btnRedo =
                    new com.google.android.material.button.MaterialButton(this);
            btnRedo.setText("↷ 重做");
            btnRedo.setTextSize(12f);
            btnRedo.setMinWidth(0);
            btnRedo.setPadding(16, 6, 16, 6);
            android.widget.FrameLayout.LayoutParams lpRedo =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            lpRedo.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            lpRedo.setMargins(0, 24, 100, 0);  // 偏左，避免和撤销按钮重叠
            btnRedo.setLayoutParams(lpRedo);
            btnRedo.setOnClickListener(v -> {
                if (game2048View.redo()) {
                    updateScore(game2048View.getScore() + 10);
                    android.widget.Toast.makeText(this, "已重做", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(this, "已无可重做",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            ((android.widget.FrameLayout) gameContentContainer).addView(btnRedo);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        maxTileValue = 0;
        game2048View.startGame();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        game2048View.pauseGame();
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        game2048View.resumeGame();
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        game2048View.stopGame();
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
                achievementManager.checkAndUnlock("first_merge", 1);
                achievementManager.checkAndUnlock("score_1000", score);
                achievementManager.checkAndUnlock("score_5000", score);
                achievementManager.checkAndUnlock("score_20000", score);
                break;
            case "tile":
                int value = (int) params[0];
                achievementManager.checkAndUnlock("tile_128", value);
                achievementManager.checkAndUnlock("tile_512", value);
                achievementManager.checkAndUnlock("tile_2048", value);
                break;
            case "win":
                achievementManager.checkAndUnlock("reach_2048", 1);
                break;
        }
    }

    // ==================== 难度管理 ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("简单", 1, "4×4 棋盘，大方块出现概率高",
                0, 0, 0.3f, false));
        levels.add(new DifficultyLevel("普通", 2, "4×4 棋盘，标准概率",
                0, 0, 0.5f, true));
        levels.add(new DifficultyLevel("困难", 3, "4×4 棋盘，小方块出现概率高",
                0, 0, 0.8f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        if (game2048View != null) {
            game2048View.setDifficultyFactor(newLevel.difficultyFactor);
        }
        Toast.makeText(this, "难度已切换为：" + newLevel.name, Toast.LENGTH_SHORT).show();
    }
}

package com.gamecenter.app.games.minesweeper;

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
 * 扫雷游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>使用 BaseGameActivity 框架，集成成就系统、教程系统和难度管理系统。
 * 游戏逻辑和渲染由 {@link MinesweeperView} 负责。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>继承 BaseGameActivity 获得统一的游戏生命周期管理</li>
 *   <li>无需 AI，难度通过雷数/格子数比调节</li>
 *   <li>成就系统跟踪首次通关、不同难度通关、速度通关等</li>
 *   <li>事件驱动游戏，无需定时器</li>
 *   <li>支持单击翻开和长按标记旗帜</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class MinesweeperActivity extends BaseGameActivity {

    // ==================== 常量 ====================

    private static final String GAME_ID_VALUE = "minesweeper";
    private static final String GAME_NAME_VALUE = "扫雷";

    // ==================== 游戏组件 ====================

    /** 游戏视图 */
    private MinesweeperView minesweeperView;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return GAME_ID_VALUE;
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_minesweeper_name);
    }

    @Nullable
    @Override
    protected View getGameContentView() {
        return minesweeperView;
    }

    @Override
    protected void initGame() {
        minesweeperView = new MinesweeperView(this);

        // 设置游戏事件监听
        minesweeperView.setOnGameWinListener(elapsedSeconds -> {
            usageStore.recordWin(GAME_ID_VALUE);
            updateScore(getCurrentScore() + 100);
            checkAchievement("win", elapsedSeconds);
        });

        minesweeperView.setOnGameLoseListener(() -> {
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("lose", 0);
        });

        minesweeperView.setOnCellRevealedListener(revealedCount -> {
            checkAchievement("reveal", revealedCount);
        });

        // 添加视图到容器
        if (gameContentContainer != null) {
            ((android.widget.FrameLayout) gameContentContainer).addView(minesweeperView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        minesweeperView.startGame();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
        minesweeperView.pauseGame();
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        minesweeperView.resumeGame();
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        minesweeperView.stopGame();
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        switch (eventType) {
            case "win":
                long elapsedSeconds = (long) params[0];
                int wins = usageStore.getWinCount(GAME_ID_VALUE);
                achievementManager.checkAndUnlock("first_win", 1);
                achievementManager.checkAndUnlock("win_10", wins);
                achievementManager.checkAndUnlock("win_50", wins);
                // 速度通关成就（60秒内）
                if (elapsedSeconds <= 60) {
                    achievementManager.checkAndUnlock("speed_clear", 1);
                }
                // 根据当前难度解锁成就
                DifficultyLevel current = getCurrentDifficulty();
                if (current.level >= 3) {
                    achievementManager.checkAndUnlock("hard_win", 1);
                }
                break;
            case "lose":
                break;
            case "reveal":
                int revealed = (int) params[0];
                achievementManager.checkAndUnlock("reveal_all_safe", revealed);
                break;
        }
    }

    // ==================== 难度管理 ====================

    @NonNull
    @Override
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel(getString(R.string.game_minesweeper_diff_easy), 1, getString(R.string.game_minesweeper_diff_easy_desc),
                0, 0, 0.3f, false));
        levels.add(new DifficultyLevel(getString(R.string.game_minesweeper_diff_normal), 2, getString(R.string.game_minesweeper_diff_normal_desc),
                0, 0, 0.5f, true));
        levels.add(new DifficultyLevel(getString(R.string.game_minesweeper_diff_hard), 3, getString(R.string.game_minesweeper_diff_hard_desc),
                0, 0, 0.8f, false));
        return levels;
    }

    @Override
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        if (minesweeperView != null) minesweeperView.setDifficulty(newLevel.level);
        Toast.makeText(this, getString(R.string.game_minesweeper_difficulty_changed, newLevel.name), Toast.LENGTH_SHORT).show();
    }
}

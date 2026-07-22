package com.gamecenter.app.games.base;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 新版游戏Activity基类
 * <p>
 * 为所有游戏Activity提供统一的生命周期管理、成就系统、教程系统和难度管理。
 * 子类只需重写抽象方法即可快速接入完整的游戏框架。
 * </p>
 */
public abstract class BaseGameActivity extends AppCompatActivity {

    /** 游戏内容容器，由子类通过 getGameContentView() 返回的视图添加到此容器 */
    protected FrameLayout gameContentContainer;

    /** 游戏是否正在运行 */
    protected boolean isGameRunning = false;

    /** 游戏是否暂停 */
    protected boolean isGamePaused = false;

    /** 游戏开始时间 */
    protected long gameStartTime = 0;

    /** 当前得分 */
    protected int currentScore = 0;

    /** 游戏使用统计存储 */
    protected GameUsageStore usageStore;

    /** 游戏教程辅助 */
    protected GameTutorialHelper tutorialHelper;

    /** 成就管理器 */
    protected AchievementManager achievementManager;

    /** 当前选择的难度等级索引 */
    protected int currentDifficultyIndex = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android 16+ (API 36) 将忽略 manifest 中的 android:screenOrientation，
        // 需在运行时强制锁定竖屏，以保证旧版本与新版本行为一致。
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setupGameFramework();

        // 读取由启动方传入的难度索引（key必须与 GameLauncherHelper.EXTRA_DIFFICULTY_INDEX 一致）
        int difficultyIndex = getIntent().getIntExtra("game_difficulty_index", -1);
        if (difficultyIndex >= 0) {
            setDifficulty(difficultyIndex);
        }

        initGame();
        View contentView = getGameContentView();
        if (contentView != null && gameContentContainer != null) {
            // 移除已有的父视图，避免 "The specified child already has a parent" 错误
            if (contentView.getParent() != null) {
                ((ViewGroup) contentView.getParent()).removeView(contentView);
            }
            gameContentContainer.addView(contentView);
        }
    }

    /**
     * 设置游戏框架的基础设施
     */
    private void setupGameFramework() {
        // 创建主容器
        gameContentContainer = new FrameLayout(this);
        setContentView(gameContentContainer);

        // 初始化使用统计存储
        usageStore = new GameUsageStore(this);

        // 初始化成就管理器
        achievementManager = new AchievementManager(this);

        // 记录游戏打开
        String gameId = getGameId();
        if (gameId != null && !gameId.isEmpty()) {
            usageStore.recordLaunch(gameId);
        }
    }

    // ==================== 子类可重写的方法（非抽象，提供默认实现） ====================

    /** 返回游戏唯一标识（如"snake"、"tetris"）。子类应重写此方法。 */
    @NonNull
    protected String getGameId() { return ""; }

    /** 返回游戏显示名称（如"贪吃蛇"）。子类应重写此方法。 */
    @NonNull
    protected String getGameName() { return ""; }

    /** 返回游戏内容视图（由子类创建的游戏视图）。子类应重写此方法。 */
    @Nullable
    protected View getGameContentView() { return null; }

    /** 初始化游戏组件和视图。子类应重写此方法。 */
    protected void initGame() {}

    /** 开始/重新开始游戏。子类应重写此方法。 */
    protected void startGame() {}

    /** 暂停游戏。子类应重写此方法。 */
    protected void pauseGame() {}

    /** 恢复已暂停的游戏。子类应重写此方法。 */
    protected void resumeGame() {}

    /** 结束当前游戏。子类应重写此方法。 */
    protected void endGame() {}

    /**
     * 检查并解锁成就。默认调用 achievementManager.checkAndUnlock。
     * @param eventType 事件类型（如"score"、"game_over"）
     * @param params 事件参数
     */
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        if (achievementManager != null) {
            achievementManager.checkAndUnlock(eventType, params);
        }
    }

    /**
     * 直接解锁成就（带 gameId 隔离，推荐用于 win/game_over 等 boolean 型事件）。
     * GAME_REVAMP_2026：修正跨游戏串扰。
     * @param achievementId 成就标识
     */
    protected void unlockAchievement(@NonNull String achievementId) {
        if (achievementManager != null) {
            achievementManager.unlock(getGameId(), achievementId);
        }
    }

    /**
     * 按阈值检查并解锁成就（带 gameId 隔离，推荐用于 score/level/streak 等数值型事件）。
     * GAME_REVAMP_2026：修正无条件解锁 bug。
     * @param achievementId 成就标识
     * @param currentValue  当前进度值
     * @param threshold     解锁阈值
     */
    protected void checkAchievementThreshold(@NonNull String achievementId, int currentValue, int threshold) {
        if (achievementManager != null) {
            achievementManager.checkAndUnlock(getGameId(), achievementId, currentValue, threshold);
        }
    }

    /**
     * 记录最高分并返回是否破纪录（GAME_REVAMP_2026 统一最高分持久化）。
     * @param score 本局最终得分
     * @return 是否为新纪录
     */
    protected boolean recordHighScore(int score) {
        if (usageStore == null) return false;
        String gameId = getGameId();
        if (gameId == null || gameId.isEmpty()) return false;
        int oldHigh = usageStore.getHighScore(gameId);
        usageStore.recordScore(gameId, score);
        return score > oldHigh;
    }

    /**
     * 获取本游戏历史最高分（GAME_REVAMP_2026）。
     */
    protected int getHighScore() {
        if (usageStore == null) return 0;
        return usageStore.getHighScore(getGameId());
    }

    /**
     * 游戏是否正在运行
     */
    public boolean isGameRunning() {
        return isGameRunning;
    }

    /**
     * 游戏是否暂停
     */
    public boolean isGamePaused() {
        return isGamePaused;
    }

    /**
     * 获取当前难度等级
     */
    @NonNull
    public DifficultyLevel getCurrentDifficulty() {
        List<DifficultyLevel> levels = getDifficultyLevels();
        if (currentDifficultyIndex >= 0 && currentDifficultyIndex < levels.size()) {
            return levels.get(currentDifficultyIndex);
        }
        return levels.isEmpty() ? new DifficultyLevel("默认", 1, "", 0, 0, 1.0f, false) :
                levels.get(0);
    }

    /** 获取已解锁的成就编号列表 */
    @NonNull
    protected List<String> getUnlockedAchievements() {
        return new ArrayList<>();
    }

    /** 加载游戏音效资源。子类应重写此方法。 */
    protected void loadGameSounds() {}

    // ==================== 可选重写的方法 ====================

    /**
     * 获取游戏的难度等级列表
     * <p>默认返回包含简单/普通/困难的默认列表。子类可重写以提供自定义难度。</p>
     */
    @NonNull
    public List<DifficultyLevel> getDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("简单", 1, "适合新手", 0, 0, 1.0f, false));
        levels.add(new DifficultyLevel("普通", 2, "标准难度", 0, 0, 1.5f, true));
        levels.add(new DifficultyLevel("困难", 3, "高手挑战", 0, 0, 2.0f, false));
        return levels;
    }

    /**
     * 难度变更回调
     * @param oldLevel 旧难度等级
     * @param newLevel 新难度等级
     */
    public void onDifficultyChanged(@NonNull DifficultyLevel oldLevel,
                                    @NonNull DifficultyLevel newLevel) {
        // 默认空实现，子类可重写
    }

    // ==================== 框架辅助方法 ====================

    /** 更新当前得分 */
    protected void updateScore(int score) {
        this.currentScore = score;
    }

    /** 获取当前得分 */
    protected int getCurrentScore() {
        return currentScore;
    }

    /** 设置难度 */
    public void setDifficulty(int index) {
        List<DifficultyLevel> levels = getDifficultyLevels();
        if (index >= 0 && index < levels.size()) {
            DifficultyLevel oldLevel = (currentDifficultyIndex < levels.size())
                    ? levels.get(currentDifficultyIndex) : levels.get(0);
            currentDifficultyIndex = index;
            onDifficultyChanged(oldLevel, levels.get(index));
        }
    }
}

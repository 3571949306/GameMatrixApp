package com.gamecenter.app.games.base;

import android.content.ComponentCallbacks2;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.LeaderboardStore;
import com.gamecenter.app.games.PlayTimeManager;
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

    /** 本地排行榜存储（P0-1） */
    protected LeaderboardStore leaderboardStore;

    /** 当前选择的难度等级索引 */
    protected int currentDifficultyIndex = 0;

    /**
     * 2026-08-23 P1 生命周期安全：
     * 标记是否因切后台被框架自动暂停。与用户主动暂停（isGamePaused）
     * 区分，onResume 时只恢复框架自己暂停的游戏，不打断用户暂停状态。
     */
    private boolean autoPausedByLifecycle = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android 16+ (API 36) 将忽略 manifest 中的 android:screenOrientation，
        // 需在运行时强制锁定竖屏，以保证旧版本与新版本行为一致。
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setupGameFramework();

        // P2-9 (PLAY_TIME_MANAGEMENT): 进入游戏前检测是否已超出每日限额
        checkPlayTimeLimit();

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
     * P2-9: 检查今日游玩时长是否已超出每日限额。若超限且当日未弹过警告，
     * 弹出警告对话框（不强制阻止游玩，尊重用户选择）。
     */
    private void checkPlayTimeLimit() {
        try {
            PlayTimeManager mgr = new PlayTimeManager(this);
            PlayTimeManager.WarnResult warn = mgr.checkLimitAndWarn();
            if (warn == null) return;
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(com.gamecenter.app.R.string.play_limit_warn_title)
                    .setMessage(getString(com.gamecenter.app.R.string.play_limit_warn_msg,
                            warn.playedMin, warn.limitMin, warn.overMin))
                    .setPositiveButton(com.gamecenter.app.R.string.play_limit_warn_continue,
                            (d, w) -> d.dismiss())
                    .setNegativeButton(com.gamecenter.app.R.string.play_limit_warn_exit,
                            (d, w) -> {
                                d.dismiss();
                                finish();
                            })
                    .setCancelable(false)
                    .show();
        } catch (Exception ignored) {
            // 时长管理失败不应阻塞游戏启动
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

        // P0-1: 初始化本地排行榜存储
        leaderboardStore = new LeaderboardStore(this);

        // 记录游戏打开
        String gameId = getGameId();
        if (gameId != null && !gameId.isEmpty()) {
            usageStore.recordLaunch(gameId);
            // 同步连胜统计：记录一次活跃 + 对局数
            com.gamecenter.app.games.achievement.StreakTracker.getInstance(this).recordGamePlayed(gameId, false);
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
     * P0-1: 提交本局分数到本地排行榜。
     * <p>应在 onGameOver 或 endGame 中调用，传入本局最终得分与本局耗时。</p>
     * @param score 本局得分（<=0 不入榜）
     * @param durationMs 本局耗时（毫秒）
     * @return 入榜排名（1-based），未入榜返回 -1
     */
    protected int submitScoreToLeaderboard(int score, long durationMs) {
        if (leaderboardStore == null) return -1;
        String gameId = getGameId();
        if (gameId == null || gameId.isEmpty()) return -1;
        DifficultyLevel diff = getCurrentDifficulty();
        int diffIdx = currentDifficultyIndex;
        String diffName = diff != null ? diff.name : "默认";
        return leaderboardStore.submitScore(gameId, score, diffIdx, diffName, durationMs);
    }

    /**
     * P0-3 / P1-6: 构建本游戏的战绩分享数据。
     * <p>子类可重写以提供游戏特有的额外字段（如关卡数、连击数等）。
     * 默认实现从 {@link GameUsageStore} 读取该游戏的常规战绩。</p>
     */
    @NonNull
    protected com.gamecenter.app.games.ShareCardGenerator.Data buildShareCardData() {
        com.gamecenter.app.games.ShareCardGenerator.Data data =
                new com.gamecenter.app.games.ShareCardGenerator.Data();
        String gameId = getGameId();
        data.gameId = gameId;
        data.gameName = getGameName();
        data.highScore = getHighScore();
        if (usageStore != null) {
            data.playCount = usageStore.getPlayCount(gameId);
            data.winCount = usageStore.getWinCount(gameId);
            data.lossCount = usageStore.getLossCount(gameId);
            data.playTimeMs = usageStore.getTotalPlayTimeMs(gameId);
        }
        return data;
    }

    /**
     * P0-3 / P1-6: 触发当前游戏的战绩分享（子线程生成 Bitmap + ACTION_SEND）。
     */
    protected void shareGameStats() {
        final com.gamecenter.app.games.ShareCardGenerator.Data data = buildShareCardData();
        if (!data.hasData()) {
            android.widget.Toast.makeText(this,
                    com.gamecenter.app.R.string.share_card_no_data,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            com.gamecenter.app.games.ShareCardGenerator generator =
                    new com.gamecenter.app.games.ShareCardGenerator(this);
            final android.content.Intent intent = generator.buildShareIntent(data);
            runOnUiThread(() -> {
                if (intent != null) {
                    try {
                        startActivity(android.content.Intent.createChooser(intent,
                                getString(com.gamecenter.app.R.string.share_card_chooser_title)));
                    } catch (Exception e) {
                        android.widget.Toast.makeText(this,
                                com.gamecenter.app.R.string.share_card_save_failed,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else {
                    android.widget.Toast.makeText(this,
                            com.gamecenter.app.R.string.share_card_save_failed,
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
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

    /**
     * 2026-08-23 P1 生命周期安全：
     * 切后台时自动暂停游戏循环（贪吃蛇/俄罗斯方块/打砖块等基于 Handler 的
     * 循环此前在后台继续空转，浪费电量且逻辑继续推进）。
     * 仅在游戏运行中且未被用户主动暂停时触发，避免覆盖用户暂停状态。
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (isGameRunning && !isGamePaused) {
            autoPausedByLifecycle = true;
            pauseGame();
        }
    }

    /**
     * 2026-08-23 P1：从后台返回时恢复被框架自动暂停的游戏。
     * 用户主动暂停（暂停菜单）不受影响——此时 autoPausedByLifecycle 为 false。
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (autoPausedByLifecycle) {
            autoPausedByLifecycle = false;
            if (isGameRunning) {
                resumeGame();
            }
        }
    }

    @Override
    protected void onDestroy() {
        // 确保游戏异常退出时也记录游玩时间，避免漏记
        if (isGameRunning && gameStartTime > 0 && usageStore != null) {
            String gameId = getGameId();
            if (gameId != null && !gameId.isEmpty()) {
                usageStore.recordPlayTime(gameId, System.currentTimeMillis() - gameStartTime);
            }
            gameStartTime = 0;
        }
        super.onDestroy();
    }

    // ==================== P1-内存：内存压力回调 ====================

    /**
     * 系统内存压力回调。基类只记录日志；具体释放策略交给子类
     * （典型动作：取消 in-flight AI 搜索、清 Bitmap LruCache、关闭协程）。
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(getClass().getSimpleName(), "[trim] level=" + level);
        onMemoryTrim(level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(getClass().getSimpleName(), "[trim] onLowMemory → 视为 COMPLETE");
        onMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
    }

    /**
     * 子类可重写此方法处理 trim 信号。默认空实现。
     * 推荐阈值：
     * <ul>
     *   <li>{@code TRIM_MEMORY_BACKGROUND (40)}：取消正在执行的 AI 搜索（玩家已切到后台）</li>
     *   <li>{@code TRIM_MEMORY_UI_HIDDEN (20)}：释放非必要 UI Bitmap 缓存</li>
     *   <li>{@code TRIM_MEMORY_COMPLETE (80)}：激进释放 — 关闭所有可重建缓存</li>
     * </ul>
     */
    protected void onMemoryTrim(int level) {
        // 默认空实现；子类按需重写
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}

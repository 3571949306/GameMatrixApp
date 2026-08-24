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

    /** 2026-08-23 P2-2: 中断续玩存档管理器 */
    private com.gamecenter.app.games.save.GameSaveManager saveManager;

    /** 2026-08-23 P3: 统一音效/震动反馈（内部实时遵循设置开关） */
    private com.gamecenter.app.games.base.GameFeedback feedback;

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
        // 2026-08-23 P2-2：初始化存档管理器
        saveManager = new com.gamecenter.app.games.save.GameSaveManager(this);
        // 2026-08-23 P3：初始化音效/震动反馈
        feedback = new com.gamecenter.app.games.base.GameFeedback(this);
        minesweeperView = new MinesweeperView(this);

        // 设置游戏事件监听
        minesweeperView.setOnGameWinListener(elapsedSeconds -> {
            // 2026-08-23 P2-2：对局正常结束（胜利），停止保存并清除存档
            isGameRunning = false;
            if (saveManager != null) saveManager.clear(GAME_ID_VALUE);
            usageStore.recordWin(GAME_ID_VALUE);
            updateScore(getCurrentScore() + 100);
            checkAchievement("win", elapsedSeconds);
            // 2026-08-23 P3：胜利反馈
            if (feedback != null) feedback.feedbackWin();
        });

        minesweeperView.setOnGameLoseListener(() -> {
            // 2026-08-23 P2-2：踩雷失败，清除存档
            isGameRunning = false;
            if (saveManager != null) saveManager.clear(GAME_ID_VALUE);
            usageStore.recordLoss(GAME_ID_VALUE);
            checkAchievement("lose", 0);
            // 2026-08-23 P3：失败反馈
            if (feedback != null) feedback.feedbackLose();
        });

        minesweeperView.setOnCellRevealedListener(revealedCount -> {
            checkAchievement("reveal", revealedCount);
            // 2026-08-23 P3：翻开格子音效（不震动，避免疲劳）
            if (feedback != null) feedback.playMove();
        });

        // 2026-08-23 P2-2：玩家翻开/标记后保存续玩进度
        minesweeperView.setOnStateChangeListener(this::saveProgress);

        // 添加视图到容器
        if (gameContentContainer != null) {
            ((android.widget.FrameLayout) gameContentContainer).addView(minesweeperView);
        }

        // 2026-08-23 P2-2：开始游戏入口——检测未完成对局存档。
        // onCreate 阶段窗口尚未 attach，post 到视图就绪后再弹"继续上局"对话框
        minesweeperView.post(this::beginPlay);
    }

    /**
     * 2026-08-23 P2-2：开始游戏入口——检测未完成对局存档，
     * 有存档时弹"继续上局"对话框，否则直接新开一局。
     */
    private void beginPlay() {
        if (saveManager != null && saveManager.hasSave(GAME_ID_VALUE)) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("继续上局？")
                    .setMessage("检测到上次未完成的对局，是否继续？")
                    .setPositiveButton("继续上局", (d, w) -> restoreFromSave())
                    .setNegativeButton("新开一局", (d, w) -> {
                        saveManager.clear(GAME_ID_VALUE);
                        startNewGame();
                    })
                    .setCancelable(true)
                    .show();
        } else {
            startNewGame();
        }
    }

    /** 2026-08-23 P2-2：从存档恢复对局 */
    private void restoreFromSave() {
        org.json.JSONObject state = saveManager == null ? null : saveManager.load(GAME_ID_VALUE);
        if (state == null || !minesweeperView.restoreState(state)) {
            // 存档缺失或数据损坏，回退新开一局
            startNewGame();
            return;
        }
        // 同步难度索引（成就判定经 getCurrentDifficulty 读取）
        int savedDifficulty = state.optInt("difficulty", 1);
        currentDifficultyIndex = Math.max(0, Math.min(savedDifficulty - 1, 2));
        long elapsedMs = state.optLong("elapsedMs", 0);
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = elapsedMs > 0
                ? System.currentTimeMillis() - elapsedMs
                : System.currentTimeMillis();
    }

    /** 2026-08-23 P2-2：保存当前对局进度 */
    private void saveProgress() {
        if (saveManager == null || !isGameRunning) return;
        try {
            org.json.JSONObject state = minesweeperView.serializeState();
            if (state != null) {
                saveManager.save(GAME_ID_VALUE, state);
            }
        } catch (Exception ignored) {
            // 存档失败不影响游戏主流程
        }
    }

    /** 2026-08-23 P2-2：新开一局（重置计时与棋盘） */
    private void startNewGame() {
        isGameRunning = true;
        isGamePaused = false;
        gameStartTime = System.currentTimeMillis();
        minesweeperView.startGame();
    }

    @Override
    protected void startGame() {
        // 2026-08-23 P2-2：框架生命周期入口，委托统一的新开一局逻辑
        startNewGame();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 2026-08-23 P3：释放音效资源
        if (feedback != null) {
            feedback.release();
            feedback = null;
        }
    }
}

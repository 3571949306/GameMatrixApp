package com.gamecenter.app.games.ui;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.DynamicGameActivity;
import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏启动辅助类 — 统一处理游戏启动前的难度选择对话框
 * <p>
 * 所有游戏启动都应通过此类，确保用户在进入游戏前必须选择难度。
 * </p>
 */
public class GameLauncherHelper {

    /** Intent extra key: 选择的难度索引 */
    public static final String EXTRA_DIFFICULTY_INDEX = "game_difficulty_index";

    /** Intent extra key: 是否为联机模式 */
    public static final String EXTRA_ONLINE_MODE = "game_online_mode";

    /**
     * 显示游戏启动对话框，选择难度后启动游戏
     * <p>
     * 对于不需要难度选择的游戏（休闲类），直接启动游戏；
     * 对于有AI对手的游戏，显示难度选择面板。
     * </p>
     *
     * @param context 上下文
     * @param gameId  游戏 ID
     * @return true 表示找到了游戏并启动/显示了对话框，false 表示游戏未找到
     */
    public static boolean launchGameWithDialog(@NonNull Context context, @NonNull String gameId) {
        return launchGameWithDialog(context, gameId, null);
    }

    /**
     * 显示游戏启动对话框，选择难度后启动游戏
     *
     * @param context       上下文
     * @param gameId        游戏 ID
     * @param customLevels  自定义难度列表（null 表示使用默认）
     * @return true 表示找到了游戏并启动/显示了对话框，false 表示游戏未找到
     */
    public static boolean launchGameWithDialog(@NonNull Context context,
                                               @NonNull String gameId,
                                               @Nullable List<DifficultyLevel> customLevels) {
        // 获取游戏 Activity 类
        Class<?> activityClass = GameRegistry.getActivityClassById(context, gameId);
        Log.d("GameLauncherHelper", "launchGameWithDialog: gameId=" + gameId
                + " activityClass=" + (activityClass != null ? activityClass.getSimpleName() : "null"));
        if (activityClass == null) {
            return false;
        }

        // 如果是不需要难度选择的游戏，直接启动
        if (!needsDifficultySelection(gameId)) {
            startGameActivity(context, gameId, activityClass, 0, false);
            return true;
        }

        // 获取游戏名称
        String gameName = GameRegistry.getGameNameById(context, gameId);
        if (gameName == null || gameName.isEmpty()) {
            gameName = gameId;
        }

        // 获取难度列表
        List<DifficultyLevel> levels = customLevels;
        if (levels == null || levels.isEmpty()) {
            levels = getDefaultDifficultyLevels();
        }

        // 检查是否支持联机
        boolean supportsOnline = supportsOnlineMode(gameId);

        // 显示启动对话框
        new GameStartDialog(context, gameName, levels, supportsOnline,
                new GameStartDialog.Listener() {
                    @Override
                    public void onDifficultySelected(int difficultyIndex) {
                        startGameActivity(context, gameId, activityClass, difficultyIndex, false);
                    }

                    @Override
                    public void onOnlineSelected() {
                        startGameActivity(context, gameId, activityClass, 0, true);
                    }

                    @Override
                    public void onCancelled() {
                        // 用户取消，不做任何事
                    }
                }).show();

        return true;
    }

    /**
     * 直接启动游戏（带难度参数），用于已知难度的情况
     */
    public static void startGameDirectly(@NonNull Context context,
                                         @NonNull String gameId,
                                         int difficultyIndex) {
        Class<?> activityClass = GameRegistry.getActivityClassById(context, gameId);
        if (activityClass != null) {
            startGameActivity(context, gameId, activityClass, difficultyIndex, false);
        }
    }

    /**
     * 启动游戏 Activity
     */
    private static void startGameActivity(@NonNull Context context,
                                          @NonNull String gameId,
                                          @NonNull Class<?> activityClass,
                                          int difficultyIndex,
                                          boolean onlineMode) {
        Intent intent;
        // 如果是 DynamicGameActivity，需要传递 gameId
        if (activityClass == DynamicGameActivity.class) {
            intent = new Intent(context, activityClass);
            intent.putExtra("gameId", gameId);
        } else {
            intent = new Intent(context, activityClass);
        }
        intent.putExtra(EXTRA_DIFFICULTY_INDEX, difficultyIndex);
        intent.putExtra(EXTRA_ONLINE_MODE, onlineMode);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 获取默认难度等级列表
     */
    @NonNull
    public static List<DifficultyLevel> getDefaultDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("简单", 1, "适合新手入门，节奏轻松",
                0, 0, 1.0f, false));
        levels.add(new DifficultyLevel("普通", 2, "标准难度，均衡体验",
                0, 0, 1.5f, true));
        levels.add(new DifficultyLevel("困难", 3, "高手挑战，快节奏",
                0, 0, 2.0f, false));
        return levels;
    }

    /**
     * 获取棋类游戏的难度等级列表（带 AI 深度）
     */
    @NonNull
    public static List<DifficultyLevel> getChessDifficultyLevels() {
        List<DifficultyLevel> levels = new ArrayList<>();
        levels.add(new DifficultyLevel("新手", 1, "AI 思考较浅，适合学习",
                2, 500, 1.0f, false));
        levels.add(new DifficultyLevel("普通", 3, "AI 有一定策略，均衡对弈",
                4, 1000, 1.5f, true));
        levels.add(new DifficultyLevel("大师", 5, "AI 深度思考，强力挑战",
                6, 3000, 2.0f, false));
        return levels;
    }

    /**
     * 判断游戏是否需要难度选择面板
     * <p>
     * 有AI对手的游戏需要难度选择，纯休闲游戏直接启动
     */
    public static boolean needsDifficultySelection(@NonNull String gameId) {
        switch (gameId) {
            // 有AI对手的游戏 - 需要难度选择
            case "gomoku":
            case "chinesechess":
            case "go":
            case "doudizhu":
            case "blackjack":
            case "checkers":
            case "tic":
                return true;
            // 休闲游戏 - 不需要难度选择
            default:
                return false;
        }
    }

    /**
     * 判断游戏是否支持联机模式
     */
    private static boolean supportsOnlineMode(@NonNull String gameId) {
        switch (gameId) {
            case "gomoku":
            case "chess":
            case "chinesechess":
            case "tic":
            case "tic_tac_toe":
            case "doudizhu":
            case "poker":
                return true;
            default:
                return false;
        }
    }
}

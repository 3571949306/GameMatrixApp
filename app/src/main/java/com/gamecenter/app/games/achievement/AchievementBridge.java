package com.gamecenter.app.games.achievement;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.games.base.AchievementManager;
import com.gamecenter.app.games.config.GameConfigLoader;
import com.gamecenter.app.games.model.AchievementDef;
import com.gamecenter.app.games.model.GameConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主侧成就桥：按 game_configs.json 在全平台统一数据入口驱动成就解锁。
 *
 * <p>背景：28 个游戏（game_configs.json 定义 27 个 gameId，TD 模块另有模块内成就）中
 * 仅 TD 模块自行调用了解锁 API，其余模块的成就永不解锁（成就中心恒 0/x）。
 * 本桥挂在 {@link com.gamecenter.app.games.GameUsageStore} 的
 * {@code recordWin} / {@code recordScore} 两个全平台数据入口上，按配置统一判定：</p>
 * <ul>
 *   <li>{@code WIN_COUNT} 型 → 胜场数（Room 更新后的总胜场）达到 threshold 解锁</li>
 *   <li>{@code STREAK} 型 → 每日活跃连胜天数（StreakTracker）达到 threshold 解锁</li>
 *   <li>{@code SCORE} 型 → 单局分数达到 threshold 解锁</li>
 * </ul>
 *
 * <p><b>SPECIAL / TIME 型不在本桥覆盖范围</b>：其语义（象棋将死特定局面、限时速通等）
 * 存在于模块内部，宿主统一数据源无从判定，必须由模块自身调用
 * {@link AchievementManager} 解锁（TD 模块为先例）。</p>
 *
 * <p>容错策略：桥的任何异常（配置解析、Room 读写、解锁判定）只记 WARN 日志，
 * 绝不影响 GameUsageStore 的原数据流（胜场/分数/金币/每日挑战记账）。</p>
 */
public final class AchievementBridge {

    private static final String TAG = "AchievementBridge";

    /** game_configs.json 中的解锁条件类型字面量（大小写敏感，Gson 反序列化保留原文） */
    private static final String TYPE_WIN_COUNT = "WIN_COUNT";
    private static final String TYPE_STREAK = "STREAK";
    private static final String TYPE_SCORE = "SCORE";

    private static volatile AchievementBridge instance;

    private final Context appContext;
    /** gameId → achievements 索引；首次访问时一次性解析构建，此后只读（config 是静态资源） */
    private volatile Map<String, List<AchievementDef>> achievementIndex;

    private AchievementBridge(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** 获取单例（桥持有 applicationContext，无泄漏风险）。 */
    @NonNull
    public static AchievementBridge getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AchievementBridge.class) {
                if (instance == null) {
                    instance = new AchievementBridge(context);
                }
            }
        }
        return instance;
    }

    /**
     * 对局胜利事件：判定该游戏的 WIN_COUNT / STREAK 型成就。
     *
     * @param gameId      游戏唯一标识
     * @param totalWins   该游戏当前总胜场（调用方须在 Room incrementWinSync 之后取值）
     * @param streakDays  当前每日活跃连胜天数（复用金币逻辑的 StreakTracker 数据源）
     */
    public void onGameWin(@Nullable String gameId, int totalWins, int streakDays) {
        List<AchievementDef> defs = achievementsOf(gameId);
        if (defs == null || defs.isEmpty()) {
            return;
        }
        try {
            AchievementManager manager = new AchievementManager(appContext);
            for (AchievementDef def : defs) {
                if (def == null || def.key == null || def.conditionType == null) {
                    continue;
                }
                try {
                    if (TYPE_WIN_COUNT.equals(def.conditionType)) {
                        manager.checkAndUnlock(gameId, def.key, totalWins, def.threshold);
                    } else if (TYPE_STREAK.equals(def.conditionType)) {
                        manager.checkAndUnlock(gameId, def.key, streakDays, def.threshold);
                    }
                    // SCORE/SPECIAL/TIME 型：胜利事件无法判定，跳过
                } catch (Exception e) {
                    Log.w(TAG, "WIN/STREAK 成就判定失败: " + gameId + "/" + def.key, e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "onGameWin 桥接失败: " + gameId, e);
        }
    }

    /**
     * 分数事件：判定该游戏的 SCORE 型成就。
     *
     * <p>调用方在"仅破纪录时"传入新最高分；阈值判定由
     * {@link AchievementManager#checkAndUnlock} 内置完成，桥不做二次过滤。</p>
     *
     * @param gameId 游戏唯一标识
     * @param score  本局分数（新最高分）
     */
    public void onGameScore(@Nullable String gameId, int score) {
        List<AchievementDef> defs = achievementsOf(gameId);
        if (defs == null || defs.isEmpty()) {
            return;
        }
        try {
            AchievementManager manager = new AchievementManager(appContext);
            for (AchievementDef def : defs) {
                if (def == null || def.key == null || !TYPE_SCORE.equals(def.conditionType)) {
                    continue;
                }
                try {
                    manager.checkAndUnlock(gameId, def.key, score, def.threshold);
                } catch (Exception e) {
                    Log.w(TAG, "SCORE 成就判定失败: " + gameId + "/" + def.key, e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "onGameScore 桥接失败: " + gameId, e);
        }
    }

    /** 取指定游戏的成就定义；配置未收录（含解析失败降级为空索引）时返回 null，桥空转。 */
    @Nullable
    private List<AchievementDef> achievementsOf(@Nullable String gameId) {
        if (gameId == null) {
            return null;
        }
        Map<String, List<AchievementDef>> index = achievementIndex;
        if (index == null) {
            synchronized (this) {
                index = achievementIndex;
                if (index == null) {
                    index = buildIndex();
                    achievementIndex = index;
                }
            }
        }
        return index.get(gameId);
    }

    /**
     * 解析 game_configs.json 并构建 gameId → achievements 索引。
     * loadAllConfigs 失败/为空时降级为空 Map（桥空转，不抛错；结果同样缓存，
     * 避免每次数据事件都重试全量 JSON 解析）。
     */
    @NonNull
    private Map<String, List<AchievementDef>> buildIndex() {
        Map<String, List<AchievementDef>> index = new HashMap<>();
        try {
            List<GameConfig> configs = new GameConfigLoader(appContext).loadAllConfigs();
            if (configs == null || configs.isEmpty()) {
                Log.w(TAG, "game_configs.json 为空或解析失败，成就桥空转");
                return index;
            }
            for (GameConfig config : configs) {
                if (config == null || config.gameId == null || config.achievements == null) {
                    continue;
                }
                index.put(config.gameId, config.achievements);
            }
            Log.i(TAG, "成就桥索引就绪: " + index.size() + " 个游戏");
        } catch (Exception e) {
            Log.w(TAG, "构建成就索引失败，成就桥空转", e);
        }
        return index;
    }
}

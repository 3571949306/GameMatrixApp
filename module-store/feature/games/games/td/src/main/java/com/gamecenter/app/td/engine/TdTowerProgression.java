package com.gamecenter.app.td.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 主线塔牌解锁表。
 *
 * <p>解锁状态故意从已有的「已解锁关卡数」派生，而不是再保存一份可漂移的塔牌状态：
 * 同一个玩家进度永远得到同一套可用塔，旧存档也无需迁移。主线每推进一关只引入一到两种
 * 新塔，既让新玩家能学习当前机制，也让通关本身成为可见的长期奖励。</p>
 */
public final class TdTowerProgression {

    private static final Map<TowerType, Integer> UNLOCK_LEVELS;

    static {
        EnumMap<TowerType, Integer> schedule = new EnumMap<>(TowerType.class);

        // 第 1 关：基础建造、经济与减速。
        schedule.put(TowerType.BOTTLE, 1);
        schedule.put(TowerType.SUN, 1);
        schedule.put(TowerType.SNOW, 1);

        // 通关第 1 关后：开始学习清群和范围爆发。
        schedule.put(TowerType.FAN, 2);
        schedule.put(TowerType.ROCKET, 2);

        // 通关第 2 关后：持续伤害与连锁清群。
        schedule.put(TowerType.POISON, 3);
        schedule.put(TowerType.LIGHTNING, 3);

        // 通关第 3 关后：关键目标处理与路径陷阱。
        schedule.put(TowerType.SNIPER, 4);
        schedule.put(TowerType.MINE, 4);

        // 通关第 4 关后：终局前的阵地强化。
        schedule.put(TowerType.AMPLIFIER, 5);

        // 新增塔时必须显式决定教学/解锁位置，避免无意间首关全开放。
        if (schedule.size() != TowerType.values().length) {
            throw new IllegalStateException("every tower must have a progression unlock level");
        }
        UNLOCK_LEVELS = Collections.unmodifiableMap(schedule);
    }

    private TdTowerProgression() { }

    /** 返回已解锁关卡数量对应的可用塔，保持 {@link TowerType} 枚举顺序。 */
    public static List<TowerType> availableForUnlockedLevelCount(int unlockedLevelCount) {
        int reachedLevel = Math.max(1, unlockedLevelCount);
        List<TowerType> result = new ArrayList<>();
        for (TowerType tower : TowerType.values()) {
            if (unlockLevel(tower) <= reachedLevel) {
                result.add(tower);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 某塔在哪一关开始可带入塔组（关卡编号从 1 开始）。 */
    public static int unlockLevel(TowerType tower) {
        if (tower == null) {
            throw new IllegalArgumentException("tower must not be null");
        }
        Integer level = UNLOCK_LEVELS.get(tower);
        if (level == null) {
            throw new IllegalStateException("tower missing progression entry: " + tower);
        }
        return level;
    }

    /** 是否已经可用。 */
    public static boolean isUnlocked(TowerType tower, int unlockedLevelCount) {
        return tower != null && unlockLevel(tower) <= Math.max(1, unlockedLevelCount);
    }

    /**
     * 两个主线进度之间真正新增的塔。用于胜利结算，保证重复通关不会伪造“新解锁”。
     */
    public static List<TowerType> newlyUnlockedBetween(int beforeUnlockedLevelCount,
                                                        int afterUnlockedLevelCount) {
        int before = Math.max(1, beforeUnlockedLevelCount);
        int after = Math.max(before, afterUnlockedLevelCount);
        List<TowerType> result = new ArrayList<>();
        for (TowerType tower : TowerType.values()) {
            int unlockLevel = unlockLevel(tower);
            if (unlockLevel > before && unlockLevel <= after) {
                result.add(tower);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 锁定卡牌直接展示的解锁条件。 */
    public static String unlockRequirement(TowerType tower) {
        int level = unlockLevel(tower);
        return level <= 1 ? "初始可用" : "通关第 " + (level - 1) + " 关解锁";
    }
}

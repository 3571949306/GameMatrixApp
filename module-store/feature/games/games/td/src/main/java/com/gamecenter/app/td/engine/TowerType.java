package com.gamecenter.app.td.engine;

/**
 * 塔防「保卫蛋蛋」塔类型定义。
 */
public enum TowerType {
    BOTTLE("瓶子炮", 60, 1.6f, 5.5f, 26f, 0.32f, true, 0f),
    SUN("太阳花", 90, 0f, 0f, 0f, 0f, false, 9f),
    SNOW("雪花", 70, 1.45f, 5.0f, 8f, 0.90f, false, 0f),
    FAN("风扇", 110, 1.6f, 4.2f, 12f, 1.15f, true, 0f),
    POISON("毒泡泡", 100, 1.5f, 5.5f, 8f, 1.0f, true, 0f),
    ROCKET("火箭", 150, 1.8f, 6.5f, 48f, 2.0f, true, 0f),
    /** 低单体伤害，依靠有限次数的近距弹射清理密集队列。 */
    LIGHTNING("雷电塔", 125, 1.35f, 4.8f, 19f, 1.05f, true, 0f);

    public final String displayName;
    /** 基础造价 */
    public final int baseCost;
    /** 升级倍率（每级攻/范围/射速') */
    public final float dmgMul;
    /** 射程（格子单位） */
    public final float range;
    /** 单发伤害 */
    public final float damage;
    /** 开火间隔（秒） */
    public final float fireInterval;
    /** 是否可对空 */
    public final boolean canAir;
    /** 太阳花：每 incomeInterval 秒产金币 */
    public final float income;

    /** 雪花减速系数（0.3 = 减速30%） */
    public static final float SNOW_SLOW_PCT = 0.35f;
    /** 雪花减速时长（秒） */
    public static final float SNOW_SLOW_SEC = 1.4f;
    /** 毒泡泡每秒伤害 */
    public static final float POISON_DPS = 9f;
    /** 毒泡泡持续时间（秒） */
    public static final float POISON_SEC = 3.0f;
    /** 火箭/风扇溅射半径（格子） */
    public static final float AOE_RADIUS = 1.3f;
    /** 雷电塔相邻两次弹射允许的最大距离（格子）。 */
    public static final float LIGHTNING_CHAIN_RANGE = 2.25f;

    TowerType(String displayName, int baseCost, float dmgMul, float range,
              float damage, float fireInterval, boolean canAir, float income) {
        this.displayName = displayName;
        this.baseCost = baseCost;
        this.dmgMul = dmgMul;
        this.range = range;
        this.damage = damage;
        this.fireInterval = fireInterval;
        this.canAir = canAir;
        this.income = income;
    }

    /** 升级到 lv(1..3) 后的攻击间隔 */
    public float fireIntervalAt(int level) {
        return (float) (fireInterval * Math.pow(0.88, level - 1));
    }

    /** 升级到 lv 后的伤害 */
    public float damageAt(int level) {
        return damage * pow(dmgMul, level - 1);
    }

    /** 升级到 lv 后的射程 */
    public float rangeAt(int level) {
        return range * (1f + 0.08f * (level - 1));
    }

    /** 建塔 + 升到 lv 的累计花费 */
    public int totalCostUpTo(int level) {
        int total = baseCost;
        for (int i = 2; i <= level; i++) {
            total += upgradeCost(i);
        }
        return total;
    }

    /** 从当前 level 升到 level+1 的花费 */
    public int upgradeCost(int nextLevel) {
        return (int) (baseCost * (0.55f + 0.28f * (nextLevel - 1)));
    }

    /** 含首个目标在内的最大命中数，连锁永远有限。 */
    public int chainTargetCountAt(int level) {
        if (this != LIGHTNING) return 1;
        return Math.max(2, Math.min(4, level + 1));
    }

    /** 首目标之后每次弹射相对首击的伤害比例。 */
    public float chainDamageMultiplierAt(int level) {
        if (this != LIGHTNING) return 1f;
        switch (Math.max(1, Math.min(3, level))) {
            case 1: return .70f;
            case 2: return .72f;
            default: return .75f;
        }
    }

    private static float pow(float base, int exp) {
        float r = 1f;
        for (int i = 0; i < exp; i++) r *= base;
        return r;
    }
}

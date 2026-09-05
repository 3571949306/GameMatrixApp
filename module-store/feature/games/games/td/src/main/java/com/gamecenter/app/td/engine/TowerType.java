package com.gamecenter.app.td.engine;

/**
 * 塔防「保卫蛋蛋」塔类型定义。
 *
 * <p>i18n 契约：displayName 是英文中性数据（守卫测试 TdModelEnglishDataTest 锁定），
 * 本地化名由 UI 层经宿主资源 com.gamecenter.app.R.string.game_td_tower_* 解析。
 */
public enum TowerType {
    BOTTLE("Bottle", 60, 1.6f, 5.5f, 26f, 0.32f, true, 0f, 1f, 0f),
    SUN("Sunflower", 90, 0f, 0f, 0f, 0f, false, 9f, 1f, 1.8f),
    SNOW("Snow", 70, 1.45f, 5.0f, 8f, 0.90f, false, 0f, 0.4f, 0f),
    FAN("Fan", 110, 1.6f, 4.2f, 12f, 1.15f, true, 0f, 0.8f, 0f),
    POISON("Poison", 100, 1.5f, 5.5f, 8f, 1.0f, true, 0f, 0.3f, 0f),
    ROCKET("Rocket", 150, 1.8f, 6.5f, 48f, 2.0f, true, 0f, 0.7f, 0f),
    /** 低单体伤害，依靠有限次数的近距弹射清理密集队列。 */
    LIGHTNING("Lightning", 125, 1.35f, 4.8f, 19f, 1.05f, true, 0f, 1f, 0f),
    /** 超远程重击塔，不具备对空能力，不能替代所有基础塔。 */
    SNIPER("Sniper", 175, 1.35f, 8.4f, 105f, 2.45f, false, 0f, 1f, 0f),
    /** 可重复触发的近路径陷阱，不是一次性消耗品。 */
    MINE("Mine", 90, 1.35f, 1.05f, 58f, 2.6f, false, 0f, 1f, 0f),
    /** 不直接造成伤害，只为范围内其他防御塔提供最高一份强化。 */
    AMPLIFIER("Amplifier", 130, 1f, 2.35f, 0f, 0f, false, 0f, 1f, 0f);

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
    /**
     * 开火时每次命中施加的直伤比例（1f = 全额单发伤害）。
     * 雪花/风扇/毒液/火箭的直伤低于单发伤害（历史引擎内联系数 0.4/0.8/0.3/0.7），
     * 提为字段供图鉴「有效直伤」展示，与 TdGame.fire() 同源；其余塔恒为 1f。
     */
    public final float directHitMultiplier;
    /** 太阳花产币周期（秒，历史引擎内联值 1.8f）；非经济塔为 0。 */
    public final float incomeIntervalSec;

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
    public static final float MINE_BURN_DPS = 13f;
    public static final float MINE_BURN_SEC = 2.8f;

    TowerType(String displayName, int baseCost, float dmgMul, float range,
              float damage, float fireInterval, boolean canAir, float income,
              float directHitMultiplier, float incomeIntervalSec) {
        this.displayName = displayName;
        this.baseCost = baseCost;
        this.dmgMul = dmgMul;
        this.range = range;
        this.damage = damage;
        this.fireInterval = fireInterval;
        this.canAir = canAir;
        this.income = income;
        this.directHitMultiplier = directHitMultiplier;
        this.incomeIntervalSec = incomeIntervalSec;
    }

    /** 升级到 lv(1..3) 后的攻击间隔 */
    public float fireIntervalAt(int level) {
        return (float) (fireInterval * Math.pow(.93, level - 1));
    }

    /** 升级到 lv 后的伤害 */
    public float damageAt(int level) {
        return damage * pow(1.35f, level - 1);
    }

    /** 升级到 lv 后的射程 */
    public float rangeAt(int level) {
        return range * (1f + .05f * (level - 1));
    }

    /** 经济塔遵循与伤害塔相同的主属性成长规则。 */
    public float incomeAt(int level) {
        return income * pow(1.35f, level - 1);
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

    /** 地雷爆炸半径；Lv2 扩大，Lv3 保持扩大半径并额外留下燃烧区。 */
    public float mineBlastRadiusAt(int level) {
        if (this != MINE) return 0f;
        return level >= 2 ? 1.45f : 1.05f;
    }

    public float amplifierAttackSpeedBonusAt(int level) {
        if (this != AMPLIFIER) return 0f;
        switch (Math.max(1, Math.min(3, level))) {
            case 1: return .10f;
            case 2: return .15f;
            default: return .20f;
        }
    }

    public float amplifierRangeBonusAt(int level) {
        if (this != AMPLIFIER) return 0f;
        switch (Math.max(1, Math.min(3, level))) {
            case 1: return 0f;
            case 2: return .05f;
            default: return .10f;
        }
    }

    private static float pow(float base, int exp) {
        float r = 1f;
        for (int i = 0; i < exp; i++) r *= base;
        return r;
    }
}

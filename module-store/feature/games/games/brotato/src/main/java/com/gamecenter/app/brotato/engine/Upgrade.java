package com.gamecenter.app.brotato.engine;

/**
 * 波次奖励的六种升级卡（与内容 JSON 无关的固定成长表）。
 *
 * <p>效果直接落在 {@link BrotatoArena} 的成长字段上，初始值为
 * fireIntervalMs = 300、moveSpeed = 5、bulletDamage = 1、shotCount = 1、hp/maxHp = 10/10。
 * 同一张卡可以重复出现并堆叠，堆叠口径写死在本类的系数里：</p>
 * <ul>
 *   <li>{@link #RAPID_FIRE}：射击间隔 ×0.85（复利叠乘，下限 {@link #MIN_FIRE_INTERVAL_MS}）。</li>
 *   <li>{@link #SWIFT_BOOTS}：移动速度 ×1.2（复利）。</li>
 *   <li>{@link #VITALITY}：生命上限 +4，并立刻回 4（不溢出新上限）。</li>
 *   <li>{@link #MULTISHOT}：每枪弹数 +1，相邻弹道夹角 {@link #SPREAD_STEP_DEG}，上限 {@link #MAX_SHOT_COUNT}。</li>
 *   <li>{@link #DAMAGE_UP}：单发伤害 ×1.25（复利叠乘，上限 {@link #MAX_BULLET_DAMAGE}）。</li>
 *   <li>{@link #REGEN}：每波结束后的回血从基础 3 点抬到 4 点（再叠每张 +1）。</li>
 * </ul>
 *
 * <p>枚举名同时是存档与测试里的稳定 id（{@link #stableId()} 为小写下划线形式），不要改名。
 * 落到战场上的具体加法写在 {@link BrotatoArena} 的成长段里，本类只发布口径常量与文案。</p>
 */
public enum Upgrade {

    /** 急速射击：射速提升（间隔缩短）。 */
    RAPID_FIRE("rapid_fire", "急速射击", "射击间隔 -15%"),

    /** 疾行靴：走位速度提升。 */
    SWIFT_BOOTS("swift_boots", "疾行靴", "移动速度 +20%"),

    /** 强健身躯：上限与当前生命同时提升。 */
    VITALITY("vitality", "强健身躯", "生命上限 +4，并回复 4 点"),

    /** 多重弹：每枪多一发弹道。 */
    MULTISHOT("multishot", "多重弹", "每枪弹数 +1（相邻夹角 12°，最多 3 发）"),

    /** 利刃弹药：单发伤害提升。 */
    DAMAGE_UP("damage_up", "利刃弹药", "单发伤害 +25%"),

    /** 再生：每波结束后额外回复生命。 */
    REGEN("regen", "再生", "每波结束后回复 4 点生命");

    /** RAPID_FIRE 的间隔乘数（可叠乘）。 */
    public static final float RAPID_FIRE_FACTOR = 0.85f;
    /** SWIFT_BOOTS 的速度乘数（可叠乘）。 */
    public static final float SWIFT_BOOTS_FACTOR = 1.2f;
    /** VITALITY 提升的上限与立刻回复量。 */
    public static final int VITALITY_MAX_HP = 4;
    /** MULTISHOT 每层的额外弹数。 */
    public static final int MULTISHOT_EXTRA_SHOTS = 1;
    /** DAMAGE_UP 的伤害乘数（可叠乘）。 */
    public static final float DAMAGE_UP_FACTOR = 1.25f;
    /** 不带 REGEN 时的每波回血量。 */
    public static final int BASE_WAVE_HEAL = 3;
    /** 带 1 层 REGEN 时的每波回血量（契约口径：REGEN 波回 4）。 */
    public static final int REGEN_WAVE_HEAL = 4;

    /**
     * 间隔下限，防止无限叠 RAPID_FIRE 后一 tick 射一串。
     * 三处上限与叠乘系数一起把「满配 dps」钉在 120（= 6 × 3 / 0.15s）：20 波出怪血量的
     * 峰值到达率约 18 hp/s，上限再高则第 11 波之后场上不可能聚集，走位失去意义。
     */
    public static final float MIN_FIRE_INTERVAL_MS = 130f;
    /** 移速上限（px/tick）。 */
    public static final float MAX_MOVE_SPEED = 20f;
    /** 单枪弹数上限。 */
    public static final int MAX_SHOT_COUNT = 3;
    /** 单发伤害上限。 */
    public static final float MAX_BULLET_DAMAGE = 8f;
    /** MULTISHOT 的相邻弹道夹角（度）。 */
    public static final float SPREAD_STEP_DEG = 12f;

    private final String stableId;
    private final String displayNameCn;
    private final String descriptionCn;

    Upgrade(String stableId, String displayNameCn, String descriptionCn) {
        this.stableId = stableId;
        this.displayNameCn = displayNameCn;
        this.descriptionCn = descriptionCn;
    }

    /** 升级卡标题（简体中文）。 */
    public String displayNameCn() {
        return displayNameCn;
    }

    /** 一行效果说明（简体中文）。 */
    public String descriptionCn() {
        return descriptionCn;
    }

    /** 稳定 id（小写下划线），供测试与后续存档使用。 */
    public String stableId() {
        return stableId;
    }

    /** 按稳定 id 反查；未知 id 返回 null（不抛，供存档兼容读取）。 */
    public static Upgrade find(String id) {
        if (id != null) {
            for (Upgrade value : values()) {
                if (value.stableId.equalsIgnoreCase(id.trim())) return value;
            }
        }
        return null;
    }

    /** 卡片标题数组，按传入顺序；{@link BrotatoArena} 用它回调 onRewardCards。 */
    public static String[] titlesOf(Upgrade[] cards) {
        if (cards == null) return new String[0];
        String[] titles = new String[cards.length];
        for (int i = 0; i < cards.length; i++) titles[i] = cards[i] == null ? "" : cards[i].displayNameCn();
        return titles;
    }

    @Override
    public String toString() {
        return name() + "(" + stableId + "," + displayNameCn + ")";
    }
}

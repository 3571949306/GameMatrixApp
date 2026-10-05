package com.gamecenter.app.brotato.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 一种敌人的不可变定义，全部数值来自 enemies.json。
 *
 * <p>Java 侧不持有回退表：内容缺失、越界或引用未知 behavior 都会在
 * {@link BrotatoContent#load(String, String, String)} 期直接抛
 * {@link IllegalStateException}（fail-closed），引擎运行期因此可以无条件信任这里的数值。</p>
 *
 * <p>数值口径（与内容 schema 一致）：</p>
 * <ul>
 *   <li>{@link #speed()} 单位是 px/tick，引擎一 tick 固定推进 16ms（60fps 语义），不看墙上时间。</li>
 *   <li>{@link #sizeDp()} 是敌人圆的半径（dp）；引擎内部按 1 px/dp 使用，渲染层可再乘自己的比例。</li>
 *   <li>{@link #color()} 是 0xAARRGGBB。enemies.json 里以有符号 int 书写（如 -2355168），解析按 32 位整数回收。</li>
 * </ul>
 *
 * <p>{@link #id()} 一律归一为小写，和 {@link BrotatoContent#kinds()} 的 key 完全一致；
 * behaviors 里的 ZIGZAG / SPLIT 分别映射到 {@link #zigzag()} 与 {@link #split()}。</p>
 */
public final class EnemyKind {

    /** SPLIT 行为死亡时产出的子体 id（小写规范形式）；内容里必须存在，否则分裂无声失效。 */
    public static final String MINION_ID = "minion";

    /** behaviors 允许的取值；值域外由 {@link #normalizeBehavior(String)} fail-closed。 */
    public static final String BEHAVIOR_ZIGZAG = "ZIGZAG";
    public static final String BEHAVIOR_SPLIT = "SPLIT";

    /** 分裂时每只母体产出的子体数量。 */
    public static final int SPLIT_CHILD_COUNT = 2;

    /**
     * 把 behaviors 里的一个取值归一成规范大写名；未知取值 fail-closed。
     * 大小写不敏感（内容里写 zigzag 也认），前后空白允许。
     */
    public static String normalizeBehavior(String raw) {
        if (raw == null) {
            throw BrotatoContentParser.fail("behavior must not be null");
        }
        String value = raw.trim().toUpperCase(Locale.US);
        if (BEHAVIOR_ZIGZAG.equals(value) || BEHAVIOR_SPLIT.equals(value)) return value;
        throw BrotatoContentParser.fail("unknown behavior \"" + raw
                + "\" (expected ZIGZAG or SPLIT)");
    }

    /** 把 kind id 归一成小写规范形式；空/含非法字符 fail-closed。 */
    public static String normalizeId(String raw) {
        if (raw == null) throw BrotatoContentParser.fail("kind id must not be null");
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.isEmpty()) throw BrotatoContentParser.fail("kind id must not be blank");
        if (value.length() > 32 || !value.matches("[a-z][a-z0-9_]*")) {
            throw BrotatoContentParser.fail("illegal kind id \"" + raw + "\"");
        }
        return value;
    }

    private final String id;
    private final String name;
    private final String nameEn;
    private final float hp;
    private final float speed;
    private final float sizeDp;
    private final int color;
    private final int contactDamage;
    private final int score;
    private final boolean zigzag;
    private final boolean split;
    private final Set<String> behaviors;

    public EnemyKind(String id, String name, String nameEn, float hp, float speed, float sizeDp,
                     int color, int contactDamage, int score, Set<String> behaviors) {
        this.id = normalizeId(id);
        this.name = name;
        this.nameEn = (nameEn == null || nameEn.trim().isEmpty()) ? name : nameEn;
        this.hp = hp;
        this.speed = speed;
        this.sizeDp = sizeDp;
        this.color = color;
        this.contactDamage = contactDamage;
        this.score = score;
        Set<String> normalized = new LinkedHashSet<>();
        boolean zig = false;
        boolean spl = false;
        if (behaviors != null) {
            for (String raw : behaviors) {
                String behavior = normalizeBehavior(raw);
                normalized.add(behavior);
                if (BEHAVIOR_ZIGZAG.equals(behavior)) zig = true;
                if (BEHAVIOR_SPLIT.equals(behavior)) spl = true;
            }
        }
        this.behaviors = Collections.unmodifiableSet(normalized);
        this.zigzag = zig;
        this.split = spl;
    }

    /** 小写规范 id，同时是 {@link BrotatoContent#kinds()} 的 key。 */
    public String id() {
        return id;
    }

    /** 中文展示名（内容源文本）。 */
    public String name() {
        return name;
    }

    /** 英文展示名；内容缺省 name_en 时回落到中文名。 */
    public String nameEn() {
        return nameEn;
    }

    /** 基础血量（未乘难度倍率）。 */
    public float hp() {
        return hp;
    }

    /** 移动速度，px/tick（未乘难度倍率）。 */
    public float speed() {
        return speed;
    }

    /** 敌人圆半径（dp）。 */
    public float sizeDp() {
        return sizeDp;
    }

    /** 0xAARRGGBB 填充色（有符号 int，与 JSON 字面量逐位一致）。 */
    public int color() {
        return color;
    }

    /** 撞到我时扣除的玩家生命。 */
    public int contactDamage() {
        return contactDamage;
    }

    /** 击杀得分。 */
    public int score() {
        return score;
    }

    /** 是否带 ZIGZAG 行为（追踪向量叠加横向正弦分量）。 */
    public boolean zigzag() {
        return zigzag;
    }

    /** 是否带 SPLIT 行为（死亡原地生成 {@link #SPLIT_CHILD_COUNT} 只 minion）。 */
    public boolean split() {
        return split;
    }

    /** 只读行为集合（规范大写名，可能为空）。 */
    public Set<String> behaviors() {
        return behaviors;
    }

    /** 该种类是否携带某个行为（大小写不敏感）。 */
    public boolean hasBehavior(String behavior) {
        return behavior != null && behaviors.contains(behavior.trim().toUpperCase(Locale.US));
    }

    @Override
    public String toString() {
        return "EnemyKind(" + id + ",hp=" + hp + ",speed=" + speed + ",sizeDp=" + sizeDp
                + ",score=" + score + ",contactDamage=" + contactDamage + ",behaviors=" + behaviors + ")";
    }
}

package com.gamecenter.app.brotato.engine;

/**
 * 战场上的一只敌人实例。
 *
 * <p>字段对渲染层只读、由引擎独占写入：{@link #x}/{@link #y} 是像素坐标，{@link #hp} 是当前血量，
 * {@link #kind} 指向内容定义，{@link #tickAge} 是本实例已存活的帧数（生成当帧为 0），
 * ZIGZAG 行为的相位就是由它推导出来的，所以同种子同流程可完全复现。</p>
 *
 * <p>{@link #maxHp}/{@link #speed}/{@link #radiusPx}/{@link #contactDamage} 是"结算后"的实际值：
 * 血量与速度已乘过难度倍率，半径由 kind 的 dp 值乘引擎的 px/dp 比例（固定 1）得出。</p>
 */
public class Enemy {

    /** 内容定义；小写 id、展示名、颜色与得分都由它提供。 */
    public EnemyKind kind;

    /** 像素坐标（引擎坐标系，原点在竞技场左上角）。 */
    public float x;
    public float y;

    /** 当前血量，≤ 0 即在本帧的碰撞阶段走死亡管线。 */
    public float hp;

    /** 存活帧数，生成当帧为 0，之后每 tick +1（ZIGZAG 相位来源）。 */
    public int tickAge;

    /** 出生血量（已含难度倍率），供血条比例使用。 */
    public float maxHp;

    /** 实际移动速度（px/tick，已含难度倍率）。 */
    public float speed;

    /** 碰撞与渲染共用的圆半径（px）。 */
    public float radiusPx;

    /** 撞到我时扣除的玩家生命（来自 kind，不随难度缩放，避免"简单难度不打疼"的倒挂）。 */
    public int contactDamage;

    /** 死亡标记：置位后由引擎在本帧末统一移除，保证遍历期间的列表稳定。 */
    public boolean removed;

    /** 空构造：给测试/序列化留的口子，字段自行填。 */
    public Enemy() {
    }

    /**
     * 以 kind 的基础数值直接建一只敌人（难度倍率 1、px/dp 比例 1）。
     * 引擎内部出怪走 {@link #Enemy(EnemyKind, float, float, float, float, float)}。
     */
    public Enemy(EnemyKind kind, float x, float y) {
        this(kind, x, y, 1f, 1f, BrotatoArena.PX_PER_DP);
    }

    /**
     * 完整构造。
     *
     * @param hpMul    血量倍率（难度标量）
     * @param speedMul 速度倍率（难度标量推导）
     * @param pxPerDp  dp → px 比例，引擎固定 {@link BrotatoArena#PX_PER_DP}
     */
    public Enemy(EnemyKind kind, float x, float y, float hpMul, float speedMul, float pxPerDp) {
        reset(kind, x, y, hpMul, speedMul, pxPerDp);
    }

    /** 复用一个实例（对象池语义）；引擎当前不做池化，保留给后续优化与测试。 */
    public void reset(EnemyKind kind, float x, float y, float hpMul, float speedMul, float pxPerDp) {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.maxHp = Math.max(0.01f, kind.hp() * hpMul);
        this.hp = this.maxHp;
        this.speed = kind.speed() * speedMul;
        this.radiusPx = Math.max(1f, kind.sizeDp() * pxPerDp);
        this.contactDamage = kind.contactDamage();
        this.tickAge = 0;
        this.removed = false;
    }

    /** 是否还活着（未被移出血量归零）。 */
    public boolean isAlive() {
        return hp > 0f && !removed;
    }

    /** 与另一点是否圆-圆相交（按平方距离比较，避免开方）。 */
    public boolean touches(float otherX, float otherY, float otherRadius) {
        float dx = x - otherX;
        float dy = y - otherY;
        float sum = radiusPx + otherRadius;
        return dx * dx + dy * dy <= sum * sum;
    }

    /** 血量比例（0..1），血条用；kind 未填时返回 0。 */
    public float hpRatio() {
        if (maxHp <= 0f) return 0f;
        float ratio = hp / maxHp;
        return ratio < 0f ? 0f : (ratio > 1f ? 1f : ratio);
    }

    /** 展示名（中文名），日志与调试浮层用。 */
    public String displayName() {
        return kind == null ? "?" : kind.name();
    }

    @Override
    public String toString() {
        return "Enemy(" + (kind == null ? "?" : kind.id()) + ",x=" + x + ",y=" + y
                + ",hp=" + hp + "/" + maxHp + ",tickAge=" + tickAge + ")";
    }
}

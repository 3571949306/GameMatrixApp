package com.gamecenter.app.brotato.engine;

/**
 * 一颗子弹。
 *
 * <p>位置按 {@link #vx}/{@link #vy} 逐 tick 推进（px/tick，由出膛角度乘固定弹速得到），
 * {@link #damage} 在出膛瞬间快照玩家当时的伤害，之后即使选了 DAMAGE_UP 也不影响已飞行中的弹。
 * 引擎在弹飞出竞技场边界或命中敌人时把它移除。</p>
 */
public class Projectile {

    public float x;
    public float y;
    public float vx;
    public float vy;
    public float damage;

    /** 半径（px），命中判定用；引擎常量 {@link BrotatoArena#BULLET_RADIUS_PX}。 */
    public float radiusPx;

    /** 存活帧数，出膛当帧为 0。 */
    public int tickAge;

    /** 命中或出界后由引擎置位，本帧末统一移除。 */
    public boolean removed;

    /** 空构造：给测试/序列化留的口子。 */
    public Projectile() {
    }

    public Projectile(float x, float y, float vx, float vy, float damage) {
        this(x, y, vx, vy, damage, BrotatoArena.BULLET_RADIUS_PX);
    }

    public Projectile(float x, float y, float vx, float vy, float damage, float radiusPx) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.radiusPx = radiusPx;
        this.tickAge = 0;
        this.removed = false;
    }

    /** 与某圆心的命中判定（半径和的平方比较）。 */
    public boolean hits(float otherX, float otherY, float otherRadius) {
        float dx = x - otherX;
        float dy = y - otherY;
        float sum = radiusPx + otherRadius;
        return dx * dx + dy * dy <= sum * sum;
    }

    /** 是否已经飞出矩形竞技场（含 margin 的容差，避免贴边抖动）。 */
    public boolean outside(float width, float height, float margin) {
        return x < -margin || y < -margin || x > width + margin || y > height + margin;
    }

    @Override
    public String toString() {
        return "Projectile(x=" + x + ",y=" + y + ",vx=" + vx + ",vy=" + vy + ",dmg=" + damage + ")";
    }
}

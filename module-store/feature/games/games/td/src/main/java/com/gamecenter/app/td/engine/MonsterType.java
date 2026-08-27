package com.gamecenter.app.td.engine;

/**
 * 塔防怪物类型定义。
 */
public enum MonsterType {
    NORMAL("小怪", 34f, 1.5f, 8, false, 0, 1),
    FAST("飞毛腿", 16f, 2.6f, 10, false, 0, 1),
    TANK("胖子", 100f, 1.0f, 20, false, 2, 2),
    FLY("飞行兵", 26f, 1.8f, 12, true, 0, 1),
    SWARM("喽罗", 12f, 2.0f, 5, false, 0, 1),
    HEALER("医生", 44f, 1.3f, 15, false, 0, 1),
    SHIELD("护盾兵", 40f, 1.2f, 14, false, 0, 2),
    BOSS("Boss", 600f, 0.8f, 60, false, 1, 3);

    public final String displayName;
    /** 基础血量 */
    public final float hp;
    /** 基础速度（格/秒） */
    public final float speed;
    /** 击杀金币 */
    public final int value;
    /** 是否飞行（需要可对空塔） */
    public final boolean fly;
    /** 护甲：每次受击减伤 */
    public final int armor;
    /** 漏到蛋蛋时造成的生命伤害。 */
    public final int leakDamage;

    MonsterType(String displayName, float hp, float speed, int value, boolean fly, int armor,
                int leakDamage) {
        this.displayName = displayName;
        this.hp = hp;
        this.speed = speed;
        this.value = value;
        this.fly = fly;
        this.armor = armor;
        this.leakDamage = leakDamage;
    }

    /** 护盾兵：初始护盾吸收量 */
    public int shieldHp(MonsterType t) {
        return t == SHIELD ? 50 : 0;
    }
}

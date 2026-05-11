package com.gamecenter.app.games.doudizhu.model;

/**
 * 斗地主牌型枚举 (Card Type Enum)
 * 定义了斗地主游戏中所有合法的牌型
 * 每个牌型都有对应的权重比较规则
 */
public enum CardType {
    // 无效牌型 - 当输入的牌无法组成合法牌型时返回此类型
    ERROR("错误牌型", 0),

    // 单牌 - 任意一张单牌
    SINGLE("单牌", 1),

    // 对子 - 两张牌值相同的牌
    PAIR("对子", 2),

    // 三张 - 三张牌值相同的牌（不能单独出，需要带牌）
    TRIO("三张", 3),

    // 三带一 - 三张相同牌值 + 任意一张单牌
    TRIO_SINGLE("三带一", 4),

    // 三带一对 - 三张相同牌值 + 一对
    TRIO_PAIR("三带一对", 5),

    // 单顺 - 五张或更多连续单牌（3到A，不含2和王）
    STRAIGHT("顺子", 6),

    // 双顺（连对）- 三对或更多连续对子
    STRAIGHT_PAIRS("连对", 7),

    // 飞机 - 两个或更多连续三张（可带可不带单牌或对子）
    AIRPLANE("飞机", 8),

    // 飞机带翅膀 - 飞机带单牌或对子
    AIRPLANE_WITH_WINGS("飞机带翅膀", 9),

    // 炸弹 - 四张牌值相同的牌
    BOMB("炸弹", 10),

    // 王炸 - 小王加大王
    JOKER_BOMB("王炸", 11),

    // 四带两单 - 四张相同牌值 + 两张单牌（不是炸弹，按普通牌型处理）
    QUAD_SINGLE("四带两单", 4),

    // 四带两对 - 四张相同牌值 + 两对（不是炸弹，按普通牌型处理）
    QUAD_PAIR("四带两对", 5);

    // 牌型的中文名称
    private final String name;
    // 牌型的基本权重，用于比较不同牌型之间的优先级
    private final int priority;

    CardType(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    /**
     * 获取牌型的中文名称
     * @return 牌型名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取牌型的优先级
     * @return 优先级数值
     */
    public int getPriority() {
        return priority;
    }

    /**
     * 判断当前牌型是否为错误牌型
     * @return 是否为错误牌型
     */
    public boolean isError() {
        return this == ERROR;
    }

    /**
     * 判断当前牌型是否为炸弹或王炸
     * 炸弹和王炸可以打任意牌型
     * @return 是否为炸弹或王炸
     */
    public boolean isBomb() {
        return this == BOMB || this == JOKER_BOMB;
    }

    /**
     * 判断当前牌型是否可以打过指定的牌型
     * 只有相同牌型才能比较（炸弹和王炸除外）
     * @param other 另一个牌型
     * @return 是否可以打过
     */
    public boolean canBeat(CardType other) {
        if (other == null) {
            return true;
        }
        // 王炸可以打任意牌
        if (this == JOKER_BOMB) {
            return true;
        }
        // 炸弹可以打非炸弹的任意牌
        if (this == BOMB && !other.isBomb()) {
            return true;
        }
        // 同牌型比较
        return this == other;
    }
}

package com.gamecenter.app.games.doudizhu.model;

/**
 * 扑克牌牌值枚举 (Rank Enum)
 * 定义斗地主游戏中所有的牌值类型及对应权重
 * 权重 (Weight) 用于比较大小的逻辑，权重越大牌越大
 * 注意：斗地主中 2 和大小王是特殊牌，2 比 A 大
 */
public enum Rank {
    // 牌值 3-10，以及 J、Q、K、A
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
    ACE("A", 14),
    // 2 是最大的单牌
    TWO("2", 15),
    // 小王 - 仅次于大王
    SMALL_JOKER("joker_small", 16),
    // 大王 - 最大的牌
    BIG_JOKER("joker_big", 17);

    // 牌值的显示字符
    private final String symbol;
    // 权重值，用于比较牌的大小，权重越大牌越大
    private final int weight;

    Rank(String symbol, int weight) {
        this.symbol = symbol;
        this.weight = weight;
    }

    /**
     * 获取牌值的显示符号
     * @return 牌值符号，如 "3", "J", "Q", "A", "2", "joker_small", "joker_big"
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 获取牌值的权重
     * @return 权重值，用于比较大小的比较
     */
    public int getWeight() {
        return weight;
    }

    /**
     * 根据牌值符号查找对应的 Rank 枚举
     * @param symbol 牌值符号
     * @return 对应的 Rank 枚举，如果未找到返回 null
     */
    public static Rank fromSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        for (Rank rank : values()) {
            if (rank.symbol.equals(symbol)) {
                return rank;
            }
        }
        return null;
    }

    /**
     * 判断当前牌值是否为王牌（小王或大王）
     * @return 是否为王牌
     */
    public boolean isJoker() {
        return this == SMALL_JOKER || this == BIG_JOKER;
    }

    /**
     * 判断当前牌值是否为 2
     * @return 是否为 2
     */
    public boolean isTwo() {
        return this == TWO;
    }

    /**
     * 判断当前牌值是否为 A
     * @return 是否为 A
     */
    public boolean isAce() {
        return this == ACE;
    }
}

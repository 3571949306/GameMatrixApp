package com.gamecenter.app.doudizhu.model;

/**
 * 扑克牌牌值枚举，定义斗地主游戏中所有可能的牌值及其对应权重。
 * <p>
 * 你可以把牌值想象成扑克牌右下角的数字或字母——3、4、5…J、Q、K、A、2、小王、大王。
 * 每个牌值都有一个"权重"，权重越大牌越大。在斗地主中，2比A大，大小王最大。
 * <p>
 * 职责：
 * <ul>
 *   <li>枚举所有牌值（3~2、小王、大王），作为 Card 的核心属性之一</li>
 *   <li>提供权重(weight)体系，用于牌的大小比较和排序</li>
 *   <li>提供牌值符号(symbol)，用于 UI 资源名称拼接和显示</li>
 *   <li>提供便捷判断方法（isJoker、isTwo、isAce）</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>权重体系是斗地主规则的核心：3最小(3)→2最大(15)→小王(16)→大王(17)，
 *       这与普通扑克牌中A最大不同，斗地主中2比A大
 *       （记住：斗地主里2是"二哥"，比A还大！）</li>
 *   <li>权重值直接等于牌面数值（3的权重=3，4的权重=4），使算法更直观，
 *       但J、Q、K、A的权重为11~14，2的权重为15</li>
 *   <li>王牌的 symbol 使用 "joker_small"/"joker_big" 而非中文，
 *       因为 symbol 主要用于资源文件名拼接，需要符合资源命名规范</li>
 *   <li>枚举声明顺序为 THREE→BIG_JOKER，与权重递增一致，
 *       ordinal() 值可用于需要顺序索引的场景</li>
 * </ul>
 */
public enum Rank {
    /**
     * 牌值3 —— 斗地主中最小的牌，权重为3。
     * 也是顺子的起始牌（顺子最小从3开始）。
     */
    THREE("3", 3),

    /**
     * 牌值4，权重为4。
     */
    FOUR("4", 4),

    /**
     * 牌值5，权重为5。
     */
    FIVE("5", 5),

    /**
     * 牌值6，权重为6。
     */
    SIX("6", 6),

    /**
     * 牌值7，权重为7。
     */
    SEVEN("7", 7),

    /**
     * 牌值8，权重为8。
     */
    EIGHT("8", 8),

    /**
     * 牌值9，权重为9。
     */
    NINE("9", 9),

    /**
     * 牌值10，权重为10。
     */
    TEN("10", 10),

    /**
     * 牌值J（Jack），权重为11。
     */
    JACK("J", 11),

    /**
     * 牌值Q（Queen），权重为12。
     */
    QUEEN("Q", 12),

    /**
     * 牌值K（King），权重为13。
     */
    KING("K", 13),

    /**
     * 牌值A（Ace），权重为14。
     * 在斗地主中A是顺子的最高牌（如10-J-Q-K-A），
     * 但A比2小，不能组成Q-K-A-2-3这样的顺子。
     */
    ACE("A", 14),

    /**
     * 牌值2 —— 斗地主中最大的普通牌，权重为15。
     * 2不能参与顺子或连对，是独立的特殊牌值。
     * 四个2是最大的炸弹。
     */
    TWO("2", 15),

    /**
     * 小王 —— 权重为16，仅次于大王。
     * 小王+大王组成王炸（火箭），是斗地主中最大的牌型。
     * 小王不能参与任何顺子、连对等组合。
     */
    SMALL_JOKER("joker_small", 16),

    /**
     * 大王 —— 权重为17，斗地主中最大的单牌。
     * 大王+小王组成王炸（火箭）。
     * 大王不能参与任何顺子、连对等组合。
     */
    BIG_JOKER("joker_big", 17);

    private final String symbol;
    private final int weight;

    /**
     * 枚举构造函数。
     *
     * @param symbol 牌值的显示符号，普通牌为数字或字母（"3"~"2"），
     *               王牌为资源标识符（"joker_small"、"joker_big"）
     * @param weight 权重值，用于比较牌的大小；权重越大牌越大，
     *               范围从3（牌值3）到17（大王）
     */
    Rank(String symbol, int weight) {
        this.symbol = symbol;
        this.weight = weight;
    }

    /**
     * 获取牌值的显示符号。
     * <p>
     * 普通牌返回数字或字母（如 "3"、"J"、"A"），
     * 王牌返回资源标识符（"joker_small"、"joker_big"）。
     * 该符号主要用于拼接 UI 资源名称（如 poker_s_3）。
     *
     * @return 牌值符号字符串
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 获取牌值的权重。
     * <p>
     * 权重是斗地主大小比较的核心依据：
     * <ul>
     *   <li>3~10的权重等于牌面数值（3→3, 10→10）</li>
     *   <li>J=11, Q=12, K=13, A=14</li>
     *   <li>2=15（斗地主中2比A大）</li>
     *   <li>小王=16, 大王=17</li>
     * </ul>
     *
     * @return 权重值，范围 3~17
     */
    public int getWeight() {
        return weight;
    }

    /**
     * 根据牌值符号查找对应的 Rank 枚举实例。
     * <p>
     * 用于从配置或序列化数据中反序列化牌值。
     *
     * @param symbol 牌值符号，如 "3"、"J"、"joker_small" 等
     * @return 对应的 Rank 枚举值；如果未找到或 symbol 为 null，返回 null
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
     * 判断当前牌值是否为王牌（小王或大王）。
     * <p>
     * 王牌在斗地主中有特殊规则限制：
     * <ul>
     *   <li>不能参与顺子、连对</li>
     *   <li>不能组成普通炸弹</li>
     *   <li>小王+大王组成王炸</li>
     * </ul>
     *
     * @return 如果是 SMALL_JOKER 或 BIG_JOKER 返回 true，否则返回 false
     */
    public boolean isJoker() {
        return this == SMALL_JOKER || this == BIG_JOKER;
    }

    /**
     * 判断当前牌值是否为2。
     * <p>
     * 2在斗地主中是特殊牌值：
     * <ul>
     *   <li>2是最大的普通牌，权重为15</li>
     *   <li>2不能参与顺子或连对（顺子最大到A）</li>
     *   <li>四个2是最大的普通炸弹</li>
     * </ul>
     *
     * @return 如果是 TWO 返回 true，否则返回 false
     */
    public boolean isTwo() {
        return this == TWO;
    }

    /**
     * 判断当前牌值是否为A。
     * <p>
     * A在斗地主中的特殊地位：
     * <ul>
     *   <li>A是顺子的最高牌（顺子不能超过A）</li>
     *   <li>A的权重为14，比2(15)小</li>
     *   <li>A不能作为顺子的最低牌连接2（A-2-3不合法）</li>
     * </ul>
     *
     * @return 如果是 ACE 返回 true，否则返回 false
     */
    public boolean isAce() {
        return this == ACE;
    }
}

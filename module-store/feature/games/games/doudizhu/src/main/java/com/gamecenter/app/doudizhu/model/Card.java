package com.gamecenter.app.doudizhu.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 扑克牌实体类，是斗地主游戏中最核心的数据模型。
 * <p>
 * 你可以把每张 Card 想象成一张真实的扑克牌——它有花色（黑桃、红桃等）
 * 和牌值（3、4、5…K、A、2、小王、大王），一旦创建就不能修改
 * （就像印好的扑克牌，不可能把黑桃3变成红桃3）。
 * <p>
 * 职责：
 * <ul>
 *   <li>封装单张扑克牌的所有属性：唯一ID、花色(Suit)、牌值(Rank)</li>
 *   <li>提供牌面显示相关的信息（资源名称、显示名称）</li>
 *   <li>支持牌的大小比较（实现 Comparable 接口），用于手牌排序</li>
 *   <li>支持序列化（实现 Serializable），以便在 Android Intent 和 Bundle 中传递</li>
 *   <li>提供工厂方法创建单张牌和整副牌</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>采用不可变设计（所有字段为 final），确保卡牌创建后不可修改，避免运行时状态不一致
 *       （就像真正的扑克牌，印好了就不能改）</li>
 *   <li>使用 Builder 模式创建实例，支持流式调用，同时保证必填字段（花色、牌值）的完整性
 *       （Builder就像"造牌工厂"，一步步设置属性后才能出厂）</li>
 *   <li>id 字段由 Builder 内部自增计数器自动分配，保证全局唯一性，使用 synchronized 保证线程安全</li>
 *   <li>equals/hashCode 基于花色和牌值判断，而非 id，因为同一张逻辑牌可能有不同 id</li>
 *   <li>compareTo 先比较权重再比较花色，权重相同时花色仅作为次级排序依据</li>
 * </ul>
 */
public class Card implements Serializable, Comparable<Card> {

    private static final long serialVersionUID = 1L;

    private final int id;

    private final Suit suit;

    private final Rank rank;

    /**
     * 私有构造函数，强制通过 Builder 或工厂方法创建实例。
     * <p>
     * 这样设计的原因：Card 是不可变对象，构造时必须提供所有必要属性，
     * 避免创建出状态不完整的卡牌实例。
     *
     * @param id   卡牌的唯一标识，由 Builder 自增分配
     * @param suit 花色枚举，不能为 null
     * @param rank 牌值枚举，不能为 null
     */
    private Card(int id, Suit suit, Rank rank) {
        this.id = id;
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * 获取卡牌的唯一标识 ID。
     * <p>
     * ID 由 Builder 内部的自增计数器分配，在整个应用生命周期内保证唯一。
     * 主要用于在数组或列表中快速定位和标识卡牌。
     *
     * @return 卡牌的唯一 ID
     */
    public int getId() {
        return id;
    }

    /**
     * 获取卡牌的花色。
     *
     * @return 卡牌的花色枚举值，如 SPADE、HEART、JOKER_small 等
     */
    public Suit getSuit() {
        return suit;
    }

    /**
     * 获取卡牌的牌值。
     *
     * @return 卡牌的牌值枚举值，如 THREE、ACE、BIG_JOKER 等
     */
    public Rank getRank() {
        return rank;
    }

    /**
     * 获取卡牌的权重值。
     * <p>
     * 权重是斗地主中比较大小的核心依据，由 Rank 枚举定义。
     * 权重越大牌越大，例如：3的权重为3，A的权重为14，2的权重为15，大王权重为17。
     *
     * @return 牌值对应的权重值
     */
    public int getWeight() {
        return rank.getWeight();
    }

    /**
     * 获取卡牌对应的 UI 图片资源名称。
     * <p>
     * 资源命名规则：
     * <ul>
     *   <li>普通牌：poker_[花色代号]_[牌值符号]，如 poker_s_3（黑桃3）、poker_h_K（红桃K）</li>
     *   <li>小王：poker_joker_small</li>
     *   <li>大王：poker_joker_big</li>
     * </ul>
     * 王牌使用特殊命名规则，因为王牌没有花色的概念。
     *
     * @return 资源名称字符串，供 ImageView 或 Canvas 绘制时使用
     */
    public String getResName() {
        if (rank == Rank.SMALL_JOKER) {
            return "poker_joker_small";
        } else if (rank == Rank.BIG_JOKER) {
            return "poker_joker_big";
        } else {
            return "poker_" + suit.getCode() + "_" + rank.getSymbol();
        }
    }

    /**
     * 获取卡牌的中文显示名称。
     * <p>
     * 用于调试日志或在 UI 上显示牌的信息。
     * 王牌直接返回"小王"/"大王"，普通牌返回花色+牌值的组合。
     *
     * @return 花色+牌值的组合名称，如 "黑桃3"、"红桃K"、"小王"
     */
    public String getDisplayName() {
        String suitName = getSuitDisplayName();
        if (rank == Rank.SMALL_JOKER) {
            return "小王";
        } else if (rank == Rank.BIG_JOKER) {
            return "大王";
        } else {
            return suitName + rank.getSymbol();
        }
    }

    /**
     * 获取花色的中文显示名称。
     * <p>
     * 王牌的花色名称为空字符串，因为显示时不需要花色前缀。
     *
     * @return 花色的中文名称，如 "黑桃"、"红桃"、"梅花"、"方块"；王牌返回空字符串
     */
    private String getSuitDisplayName() {
        switch (suit) {
            case SPADE: return "黑桃";
            case HEART: return "红桃";
            case CLUB: return "梅花";
            case DIAMOND: return "方块";
            case JOKER_small: return "";
            case JOKER_big: return "";
            default: return "";
        }
    }

    /**
     * 判断当前牌是否为王牌（小王或大王）。
     * <p>
     * 王牌在斗地主中有特殊规则：不能参与顺子、连对等组合，
     * 但小王+大王组成王炸，是最大的牌型。
     *
     * @return 如果是小王或大王返回 true，否则返回 false
     */
    public boolean isJoker() {
        return rank.isJoker();
    }

    /**
     * 判断当前牌是否可以参与炸弹牌型。
     * <p>
     * 只有非王牌才能组成炸弹（四张相同牌值），
     * 王牌只能组成王炸（小王+大王），不属于普通炸弹。
     *
     * @return 如果不是王牌返回 true（可以参与炸弹），王牌返回 false
     */
    public boolean isBombable() {
        return !rank.isJoker();
    }

    /**
     * 比较两个卡牌的大小，用于手牌排序。
     * <p>
     * 比较规则：
     * <ol>
     *   <li>首先比较权重（牌值大小），权重大的牌排在后面</li>
     *   <li>权重相同时，比较花色的 ordinal 值作为次级排序依据（仅对非王牌有效）</li>
     *   <li>王牌之间不区分花色排序</li>
     * </ol>
     *
     * @param other 另一个卡牌，不能为 null
     * @return 负数表示当前牌较小，正数表示当前牌较大，0 表示相等
     * @throws NullPointerException 如果 other 为 null
     */
    @Override
    public int compareTo(Card other) {
        if (other == null) {
            throw new NullPointerException("比较对象不能为空");
        }
        int weightCompare = Integer.compare(this.getWeight(), other.getWeight());
        if (weightCompare != 0) {
            return weightCompare;
        }
        if (!this.rank.isJoker() && !other.rank.isJoker()) {
            return this.suit.ordinal() - other.suit.ordinal();
        }
        return 0;
    }

    /**
     * 判断两个卡牌是否相等。
     * <p>
     * 相等判断基于花色和牌值，而非 id。
     * 这意味着同一逻辑牌（如黑桃3）即使 id 不同也被视为相等。
     *
     * @param obj 待比较的对象
     * @return 如果花色和牌值都相同返回 true，否则返回 false
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Card card = (Card) obj;
        return this.suit == card.suit && this.rank == card.rank;
    }

    /**
     * 获取卡牌的哈希码。
     * <p>
     * 哈希码基于花色和牌值计算，与 equals 保持一致。
     * 使用 31 作为乘数是 Java 中的常见做法，31 是质数且可以被位运算优化。
     *
     * @return 基于花色和牌值计算的哈希码
     */
    @Override
    public int hashCode() {
        int result = suit != null ? suit.hashCode() : 0;
        result = 31 * result + (rank != null ? rank.hashCode() : 0);
        return result;
    }

    /**
     * 转换为字符串表示，返回卡牌的中文显示名称。
     *
     * @return 卡牌的中文名称，如 "黑桃3"、"大王"
     */
    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * 卡牌构建器（Builder 模式）。
     * <p>
     * 设计目的：
     * <ul>
     *   <li>支持流式调用创建 Card 实例</li>
     *   <li>在 build() 时校验必填字段，避免创建不完整的卡牌</li>
     *   <li>通过内部 idCounter 自动分配唯一 ID</li>
     * </ul>
     * <p>
     * 注意：idCounter 使用 synchronized 同步，保证多线程环境下 ID 的唯一性。
     */
    public static class Builder {
        private static int idCounter = 0;
        private Suit suit;
        private Rank rank;

        /**
         * 设置花色。
         *
         * @param suit 花色枚举值，如 SPADE、HEART 等
         * @return Builder 实例本身，支持链式调用
         */
        public Builder setSuit(Suit suit) {
            this.suit = suit;
            return this;
        }

        /**
         * 设置牌值。
         *
         * @param rank 牌值枚举值，如 THREE、ACE、BIG_JOKER 等
         * @return Builder 实例本身，支持链式调用
         */
        public Builder setRank(Rank rank) {
            this.rank = rank;
            return this;
        }

        /**
         * 构建卡牌实例。
         * <p>
         * 构建前会校验花色和牌值是否已设置，未设置则抛出异常。
         * ID 的分配使用 synchronized 保证线程安全。
         *
         * @return 创建的 Card 对象
         * @throws IllegalStateException 如果花色或牌值未设置
         */
        public Card build() {
            if (suit == null || rank == null) {
                throw new IllegalStateException("花色和牌值必须设置");
            }
            synchronized (Builder.class) {
                return new Card(++idCounter, suit, rank);
            }
        }
    }

    /**
     * 静态工厂方法：快速创建指定花色和牌值的卡牌。
     * <p>
     * 这是创建单张卡牌的便捷方法，内部委托给 Builder 实现。
     *
     * @param suit 花色枚举
     * @param rank 牌值枚举
     * @return 新创建的 Card 实例
     */
    public static Card create(Suit suit, Rank rank) {
        return new Builder().setSuit(suit).setRank(rank).build();
    }

    /**
     * 创建一副完整的扑克牌（54张）。
     * <p>
     * 按照斗地主规则，一副牌包含：
     * <ul>
     *   <li>黑桃、红桃、梅花、方块各13张（3~2），共52张</li>
     *   <li>小王1张 + 大王1张</li>
     *   <li>总计54张</li>
     * </ul>
     * <p>
     * 牌值顺序为 THREE→TWO（3,4,5,...,K,A,2），符合斗地主中牌的大小顺序。
     *
     * @return 包含所有54张牌的数组，前52张为普通牌，后2张为小王和大王
     */
    public static Card[] createFullDeck() {
        Card[] deck = new Card[54];
        int index = 0;

        for (Suit suit : new Suit[]{Suit.SPADE, Suit.HEART, Suit.CLUB, Suit.DIAMOND}) {
            for (Rank rank : new Rank[]{Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN,
                                        Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN,
                                        Rank.KING, Rank.ACE, Rank.TWO}) {
                deck[index++] = Card.create(suit, rank);
            }
        }

        deck[index++] = Card.create(Suit.JOKER_small, Rank.SMALL_JOKER);
        deck[index++] = Card.create(Suit.JOKER_big, Rank.BIG_JOKER);

        return deck;
    }
}

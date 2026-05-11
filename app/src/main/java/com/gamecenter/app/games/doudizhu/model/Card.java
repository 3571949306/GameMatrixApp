package com.gamecenter.app.games.doudizhu.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 扑克牌实体类 (Card Entity)
 * 扑克牌是斗地主游戏的基本单元
 * 每张牌由花色 (Suit) 和牌值 (Rank) 组成
 * 实现了 Serializable 接口以支持 Intent 传递和存储
 */
public class Card implements Serializable, Comparable<Card> {

    // 序列化版本号，用于反序列化时验证
    private static final long serialVersionUID = 1L;

    // 卡牌的唯一标识 ID，用于在数组或列表中快速定位
    private final int id;

    // 卡牌的花色 (Suit)，不能为 null
    private final Suit suit;

    // 卡牌的牌值 (Rank)，不能为 null
    private final Rank rank;

    // 构造函数，私有化以强制使用 Builder 模式或工厂方法创建
    private Card(int id, Suit suit, Rank rank) {
        this.id = id;
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * 获取卡牌的唯一标识 ID
     * @return 卡牌 ID
     */
    public int getId() {
        return id;
    }

    /**
     * 获取卡牌的花色
     * @return 卡牌花色枚举
     */
    public Suit getSuit() {
        return suit;
    }

    /**
     * 获取卡牌的牌值
     * @return 卡牌牌值枚举
     */
    public Rank getRank() {
        return rank;
    }

    /**
     * 获取卡牌的权重值 (Weight)
     * 权重用于比较大小的逻辑
     * @return 牌值对应的权重
     */
    public int getWeight() {
        return rank.getWeight();
    }

    /**
     * 获取卡牌对应的 UI 图片资源名称
     * 资源命名规则：poker_[花色]_[牌值]
     * 例如：poker_spade_3, poker_heart_K, poker_joker_small
     * @return 资源名称字符串，供 ImageView 或 Canvas 绘制时使用
     */
    public String getResName() {
        if (rank == Rank.SMALL_JOKER) {
            // 小王的资源名称为 poker_joker_small
            return "poker_joker_small";
        } else if (rank == Rank.BIG_JOKER) {
            // 大王的资源名称为 poker_joker_big
            return "poker_joker_big";
        } else {
            // 普通牌的资源名称，格式：poker_[花色简称]_[牌值]
            return "poker_" + suit.getCode() + "_" + rank.getSymbol();
        }
    }

    /**
     * 获取卡牌的显示名称
     * 用于调试或在 UI 上显示牌的信息
     * @return 花色+牌值的组合名称，如 "黑桃3", "红桃K", "小王"
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
     * 获取花色的中文显示名称
     * @return 花色的中文名称
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
     * 判断当前牌是否为王牌（小王或大王）
     * @return 是否为王牌
     */
    public boolean isJoker() {
        return rank.isJoker();
    }

    /**
     * 判断当前牌是否为炸弹（两张相同的牌值）
     * 注意：这个方法需要配合 GameRuleUtil 使用来判断炸弹牌型
     * @return 是否可能是炸弹的组成部分
     */
    public boolean isBombable() {
        return !rank.isJoker();
    }

    /**
     * 比较两个卡牌的大小
     * 按照权重 (Weight) 进行比较，用于手牌排序
     * @param other 另一个卡牌
     * @return 负数表示当前牌小，正数表示当前牌大，0 表示相等
     */
    @Override
    public int compareTo(Card other) {
        if (other == null) {
            throw new NullPointerException("比较对象不能为空");
        }
        // 首先比较权重
        int weightCompare = Integer.compare(this.getWeight(), other.getWeight());
        if (weightCompare != 0) {
            return weightCompare;
        }
        // 权重相同时，比较花色（仅对非王牌有效）
        if (!this.rank.isJoker() && !other.rank.isJoker()) {
            return this.suit.ordinal() - other.suit.ordinal();
        }
        return 0;
    }

    /**
     * 判断两个卡牌是否相等
     * 基于花色和牌值判断
     * @param obj 待比较的对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Card card = (Card) obj;
        return this.suit == card.suit && this.rank == card.rank;
    }

    /**
     * 获取卡牌的哈希码
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        int result = suit != null ? suit.hashCode() : 0;
        result = 31 * result + (rank != null ? rank.hashCode() : 0);
        return result;
    }

    /**
     * 转换为字符串表示
     * @return 卡牌的字符串描述
     */
    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * 卡牌构建器 (Card Builder)
     * 用于创建 Card 实例的流式构建模式
     */
    public static class Builder {
        private static int idCounter = 0;
        private Suit suit;
        private Rank rank;

        /**
         * 设置花色
         * @param suit 花色枚举
         * @return Builder 实例
         */
        public Builder setSuit(Suit suit) {
            this.suit = suit;
            return this;
        }

        /**
         * 设置牌值
         * @param rank 牌值枚举
         * @return Builder 实例
         */
        public Builder setRank(Rank rank) {
            this.rank = rank;
            return this;
        }

        /**
         * 构建卡牌实例
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
     * 静态工厂方法：创建指定花色和牌值的卡牌
     * @param suit 花色
     * @param rank 牌值
     * @return 卡牌实例
     */
    public static Card create(Suit suit, Rank rank) {
        return new Builder().setSuit(suit).setRank(rank).build();
    }

    /**
     * 创建一副完整的扑克牌（54张）
     * 包含黑桃、红桃、梅花、方块各 13 张，以及小王和大王
     * @return 包含所有 54 张牌的数组
     */
    public static Card[] createFullDeck() {
        Card[] deck = new Card[54];
        int index = 0;

        // 创建黑桃、红桃、梅花、方块的牌
        for (Suit suit : new Suit[]{Suit.SPADE, Suit.HEART, Suit.CLUB, Suit.DIAMOND}) {
            for (Rank rank : new Rank[]{Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN,
                                        Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN,
                                        Rank.KING, Rank.ACE, Rank.TWO}) {
                deck[index++] = Card.create(suit, rank);
            }
        }

        // 添加小王和大王
        deck[index++] = Card.create(Suit.JOKER_small, Rank.SMALL_JOKER);
        deck[index++] = Card.create(Suit.JOKER_big, Rank.BIG_JOKER);

        return deck;
    }
}

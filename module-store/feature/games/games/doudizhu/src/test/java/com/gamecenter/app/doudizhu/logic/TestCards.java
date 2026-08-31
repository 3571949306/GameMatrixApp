package com.gamecenter.app.doudizhu.logic;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.Rank;
import com.gamecenter.app.doudizhu.model.Suit;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用建牌工具：按权重快速造牌（花色轮转避免重复）。
 */
public final class TestCards {

    private static final Suit[] SUITS = {Suit.SPADE, Suit.HEART, Suit.CLUB, Suit.DIAMOND};

    private TestCards() {}

    /** 权重 3..15 对应 3..2；16 小王；17 大王。同一权重的多张牌花色不同。 */
    public static Card card(int weight, int copy) {
        if (weight == Rank.SMALL_JOKER.getWeight()) {
            return Card.create(Suit.JOKER_small, Rank.SMALL_JOKER);
        }
        if (weight == Rank.BIG_JOKER.getWeight()) {
            return Card.create(Suit.JOKER_big, Rank.BIG_JOKER);
        }
        Rank rank = rankOfWeight(weight);
        return Card.create(SUITS[copy % SUITS.length], rank);
    }

    /** 按"权重×张数"展开成牌列表，如 of(3,3, 4,4) → 3334444。 */
    public static List<Card> of(int... weightCopies) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < weightCopies.length; i += 2) {
            int weight = weightCopies[i];
            int count = weightCopies[i + 1];
            for (int c = 0; c < count; c++) {
                cards.add(card(weight, c));
            }
        }
        return cards;
    }

    /** 连续权重段各 1 张，如 straight(3,5) → 34567。 */
    public static List<Card> straight(int startWeight, int len) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            cards.add(card(startWeight + i, 0));
        }
        return cards;
    }

    /** 连续权重段各 perRank 张。 */
    public static List<Card> run(int startWeight, int len, int perRank) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            for (int c = 0; c < perRank; c++) {
                cards.add(card(startWeight + i, c));
            }
        }
        return cards;
    }

    public static Rank rankOfWeight(int weight) {
        for (Rank r : Rank.values()) {
            if (r.getWeight() == weight) return r;
        }
        throw new IllegalArgumentException("unknown weight " + weight);
    }

    public static List<Card> shuffled(List<Card> cards) {
        List<Card> copy = new ArrayList<>(cards);
        java.util.Collections.shuffle(copy, new java.util.Random(42));
        return copy;
    }
}

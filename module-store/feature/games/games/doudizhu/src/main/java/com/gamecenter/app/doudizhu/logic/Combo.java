package com.gamecenter.app.doudizhu.logic;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 斗地主牌型组合（不可变值对象）——规则判定与比较的唯一真源。
 *
 * <p>替代旧 {@code GameRuleUtil} 中依赖传入顺序的牌型判定/主权重实现。
 * 所有属性（类型、主权重、长度、翅膀形态）都从"牌值分布"推导，
 * 与调用方传入的卡牌顺序完全无关——玩家无论按什么顺序选牌，结果一致。</p>
 *
 * <p>修复的旧内核缺陷（见 docs/斗地主全方位改造执行计划_2026-08-30.md §1.2）：
 * <ul>
 *   <li>R1 四带两单/四带两对从未接线 → 本类完整支持</li>
 *   <li>R2 主权重取 {@code cards.get(0)} 依赖排序假设 → 改为按分布推导</li>
 *   <li>R3 飞机主权重取 HashMap 迭代序 → 改为连续段最大三张权重</li>
 *   <li>R5 四张拆三张的飞机（如 33334444 带两单）→ 翅膀搜索覆盖</li>
 *   <li>R7 顺子/连对/飞机比较不校验长度 → {@link #beats(Combo)} 强制同长度</li>
 * </ul>
 */
public final class Combo {

    /** 非飞机牌型的翅膀标记 */
    public static final int WINGS_NONE = 0;
    /** 飞机带单翅膀（翅膀数量 = 三张组数） */
    public static final int WINGS_SINGLES = 1;
    /** 飞机带对翅膀（翅膀数量 = 三张组数） */
    public static final int WINGS_PAIRS = 2;

    /** 权重下界（3）与上界（大王 17） */
    private static final int MIN_WEIGHT = Rank.THREE.getWeight();
    private static final int MAX_WEIGHT = Rank.BIG_JOKER.getWeight();
    /** 顺子/连对/飞机允许的最大权重（A=14；2 与王不参与） */
    private static final int MAX_SEQUENCE_WEIGHT = Rank.ACE.getWeight();

    private final CardType type;
    private final List<Card> cards;
    private final int mainWeight;
    /** STRAIGHT：张数；STRAIGHT_PAIRS：对数；AIRPLANE*：三张组数；其余：张数 */
    private final int length;
    private final int wingsKind;

    private Combo(CardType type, List<Card> cards, int mainWeight, int length, int wingsKind) {
        this.type = type;
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        this.mainWeight = mainWeight;
        this.length = length;
        this.wingsKind = wingsKind;
    }

    public CardType getType() { return type; }
    public List<Card> getCards() { return cards; }
    public int getMainWeight() { return mainWeight; }
    public int getLength() { return length; }
    public int getWingsKind() { return wingsKind; }
    public int size() { return cards.size(); }

    public boolean isBomb() {
        return type == CardType.BOMB || type == CardType.JOKER_BOMB;
    }

    // ============ 分类 ============

    /**
     * 判定一组牌的牌型。
     *
     * @param cards 任意顺序的牌组
     * @return 合法牌型返回 Combo；非法组合返回 null
     */
    public static Combo of(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return null;
        for (Card c : cards) {
            if (c == null || c.getRank() == null) return null;
        }

        int[] counts = new int[MAX_WEIGHT + 1];
        for (Card c : cards) {
            counts[c.getWeight()]++;
        }
        List<Integer> distinct = distinctWeights(counts);
        int n = cards.size();

        switch (n) {
            case 1:
                return new Combo(CardType.SINGLE, cards, distinct.get(0), 1, WINGS_NONE);
            case 2:
                if (isRocket(counts)) {
                    return new Combo(CardType.JOKER_BOMB, cards, MAX_WEIGHT, 2, WINGS_NONE);
                }
                if (distinct.size() == 1) {
                    return new Combo(CardType.PAIR, cards, distinct.get(0), 2, WINGS_NONE);
                }
                return null;
            case 3:
                if (distinct.size() == 1) {
                    return new Combo(CardType.TRIO, cards, distinct.get(0), 3, WINGS_NONE);
                }
                return null;
            case 4:
                if (distinct.size() == 1) {
                    return new Combo(CardType.BOMB, cards, distinct.get(0), 4, WINGS_NONE);
                }
                if (hasExact(counts, 3) && hasExact(counts, 1)) {
                    return new Combo(CardType.TRIO_SINGLE, cards, weightWithCount(counts, 3),
                            4, WINGS_NONE);
                }
                return null;
            case 5:
                if (hasExact(counts, 3) && hasExact(counts, 2)) {
                    return new Combo(CardType.TRIO_PAIR, cards, weightWithCount(counts, 3),
                            5, WINGS_NONE);
                }
                if (isStraight(counts, n)) {
                    return new Combo(CardType.STRAIGHT, cards, maxWeight(counts), n, WINGS_NONE);
                }
                return null;
            default:
                return classifyLong(counts, cards);
        }
    }

    /** 6 张及以上的复杂牌型判定（含四带二、顺子、连对、飞机及带翅膀飞机）。 */
    private static Combo classifyLong(int[] counts, List<Card> cards) {
        int n = cards.size();

        // 顺子（全单张连续）
        if (isStraight(counts, n)) {
            return new Combo(CardType.STRAIGHT, cards, maxWeight(counts), n, WINGS_NONE);
        }
        // 连对（全对子连续）
        if (isStraightPairs(counts, n)) {
            int pairCount = n / 2;
            return new Combo(CardType.STRAIGHT_PAIRS, cards, maxWeight(counts), pairCount,
                    WINGS_NONE);
        }
        // 纯飞机（全三张连续）
        if (isPureAirplane(counts, n)) {
            int trioCount = n / 3;
            return new Combo(CardType.AIRPLANE, cards, maxWeight(counts), trioCount, WINGS_NONE);
        }
        // 四带两单（6 张：4+1+1 或 4+2 拆两单）
        if (n == 6 && hasQuad(counts)) {
            return new Combo(CardType.QUAD_SINGLE, cards, weightWithCount(counts, 4), n,
                    WINGS_NONE);
        }
        // 四带两对（8 张：4 + 两对）
        if (n == 8 && hasQuad(counts) && remainingAreTwoPairs(counts)) {
            return new Combo(CardType.QUAD_PAIR, cards, weightWithCount(counts, 4), n,
                    WINGS_NONE);
        }
        // 飞机带翅膀（含四张拆三张场景，如 33334444 带两单）
        Combo airplaneWings = classifyAirplaneWithWings(counts, cards, n);
        if (airplaneWings != null) {
            return airplaneWings;
        }
        return null;
    }

    /**
     * 飞机带翅膀：寻找一组连续三张（长度≥2，不含 2/王），
     * 剩余牌恰好构成"等量单张"或"等量对子"。
     * 优先更长的三张段；同长度优先单翅膀。
     */
    private static Combo classifyAirplaneWithWings(int[] counts, List<Card> cards, int n) {
        for (int len = n / 3; len >= 2; len--) {
            for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
                if (!runHasTrios(counts, start, len)) continue;
                int[] rest = counts.clone();
                for (int w = start; w < start + len; w++) {
                    rest[w] -= 3;
                }
                int restTotal = total(rest);
                // 带单翅膀：剩余 len 张单牌
                if (restTotal == len) {
                    return new Combo(CardType.AIRPLANE_WITH_WINGS, cards, start + len - 1,
                            len, WINGS_SINGLES);
                }
                // 带对翅膀：剩余 2*len 张且能拆成 len 个对子
                if (restTotal == 2 * len && allCountsEven(rest)) {
                    return new Combo(CardType.AIRPLANE_WITH_WINGS, cards, start + len - 1,
                            len, WINGS_PAIRS);
                }
            }
        }
        return null;
    }

    // ============ 比较 ============

    /**
     * 判断当前组合能否压过 other。
     *
     * @param other 要压过的组合；null 表示自由出牌（任何合法组合均可）
     */
    public boolean beats(Combo other) {
        if (other == null) return true;
        if (type == CardType.JOKER_BOMB) return true;
        if (other.type == CardType.JOKER_BOMB) return false;

        boolean thisBomb = type == CardType.BOMB;
        boolean otherBomb = other.type == CardType.BOMB;
        if (thisBomb && !otherBomb) return true;
        if (otherBomb && !thisBomb) return false;
        if (thisBomb) return mainWeight > other.mainWeight;

        if (type != other.type) return false;
        // 长牌型必须同长度（R7：旧内核漏掉此校验）
        if (type == CardType.STRAIGHT || type == CardType.STRAIGHT_PAIRS
                || type == CardType.AIRPLANE || type == CardType.AIRPLANE_WITH_WINGS) {
            if (length != other.length) return false;
        }
        // 飞机翅膀形态必须一致（带单不能压带对）
        if (type == CardType.AIRPLANE_WITH_WINGS && wingsKind != other.wingsKind) {
            return false;
        }
        return mainWeight > other.mainWeight;
    }

    // ============ 分布工具（全部与卡牌顺序无关） ============

    private static List<Integer> distinctWeights(int[] counts) {
        List<Integer> list = new ArrayList<>();
        for (int w = MIN_WEIGHT; w <= MAX_WEIGHT; w++) {
            if (counts[w] > 0) list.add(w);
        }
        return list;
    }

    private static boolean isRocket(int[] counts) {
        return counts[Rank.SMALL_JOKER.getWeight()] == 1
                && counts[Rank.BIG_JOKER.getWeight()] == 1;
    }

    private static boolean hasExact(int[] counts, int count) {
        for (int w = MIN_WEIGHT; w <= MAX_WEIGHT; w++) {
            if (counts[w] == count) return true;
        }
        return false;
    }

    private static boolean hasQuad(int[] counts) {
        return hasExact(counts, 4);
    }

    private static int weightWithCount(int[] counts, int count) {
        for (int w = MIN_WEIGHT; w <= MAX_WEIGHT; w++) {
            if (counts[w] == count) return w;
        }
        return 0;
    }

    private static int maxWeight(int[] counts) {
        int max = 0;
        for (int w = MIN_WEIGHT; w <= MAX_WEIGHT; w++) {
            if (counts[w] > 0) max = w;
        }
        return max;
    }

    private static int total(int[] counts) {
        int sum = 0;
        for (int w = MIN_WEIGHT; w <= MAX_WEIGHT; w++) {
            sum += counts[w];
        }
        return sum;
    }

    private static boolean allCountsEven(int[] counts) {
        for (int w = MIN_WEIGHT; w <= MAX_WEIGHT; w++) {
            if (counts[w] % 2 != 0) return false;
        }
        return true;
    }

    /** 四带两对：去掉四张后，剩余 4 张须为两对（允许同点两对）。 */
    private static boolean remainingAreTwoPairs(int[] counts) {
        int quadWeight = weightWithCount(counts, 4);
        if (quadWeight == 0) return false;
        int[] rest = counts.clone();
        rest[quadWeight] = 0;
        int restTotal = total(rest);
        if (restTotal != 4) return false;
        return allCountsEven(rest);
    }

    /** 顺子：全单张、连续、不含 2/王、长度≥5。 */
    private static boolean isStraight(int[] counts, int n) {
        if (n < 5) return false;
        List<Integer> distinct = distinctWeights(counts);
        if (distinct.size() != n) return false;
        for (int w : distinct) {
            if (w > MAX_SEQUENCE_WEIGHT) return false;
        }
        return isConsecutive(distinct);
    }

    /** 连对：全对子、连续、不含 2/王、≥3 对。 */
    private static boolean isStraightPairs(int[] counts, int n) {
        if (n < 6 || n % 2 != 0) return false;
        List<Integer> distinct = distinctWeights(counts);
        if (distinct.size() != n / 2) return false;
        for (int w : distinct) {
            if (counts[w] != 2 || w > MAX_SEQUENCE_WEIGHT) return false;
        }
        return isConsecutive(distinct);
    }

    /** 纯飞机：全三张、连续、不含 2/王、≥2 组。 */
    private static boolean isPureAirplane(int[] counts, int n) {
        if (n < 6 || n % 3 != 0) return false;
        List<Integer> distinct = distinctWeights(counts);
        if (distinct.size() != n / 3) return false;
        for (int w : distinct) {
            if (counts[w] != 3 || w > MAX_SEQUENCE_WEIGHT) return false;
        }
        return isConsecutive(distinct);
    }

    private static boolean isConsecutive(List<Integer> sortedWeights) {
        for (int i = 1; i < sortedWeights.size(); i++) {
            if (sortedWeights.get(i) - sortedWeights.get(i - 1) != 1) return false;
        }
        return true;
    }

    /** [start, start+len) 每个权重都有至少 3 张。 */
    private static boolean runHasTrios(int[] counts, int start, int len) {
        for (int w = start; w < start + len; w++) {
            if (counts[w] < 3) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return type.getName() + "(main=" + mainWeight + ",len=" + length
                + (wingsKind != WINGS_NONE ? ",wings=" + wingsKind : "") + ")";
    }
}

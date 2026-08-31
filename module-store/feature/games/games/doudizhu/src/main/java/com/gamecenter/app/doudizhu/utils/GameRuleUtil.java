package com.gamecenter.app.doudizhu.utils;

import com.gamecenter.app.doudizhu.logic.Combo;
import com.gamecenter.app.doudizhu.logic.HintFinder;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 斗地主工具门面。
 *
 * <p>牌型判定、压牌比较、主权重、出牌提示等规则逻辑已全部收敛到
 * {@link Combo} / {@link HintFinder}（唯一真源，顺序无关、13 种牌型全覆盖）。
 * 本类保留：洗牌发牌、排序、倍数计算、列表校验等无争议工具，
 * 以及供旧调用方（AIBot、对局控制器）使用的兼容方法签名。</p>
 *
 * <p>历史缺陷修复记录见 docs/斗地主全方位改造执行计划_2026-08-30.md §1.2
 * （R1 四带二未接线 / R2 主权重依赖排序 / R3 飞机主权重 HashMap 序 /
 * R4 提示引擎缺顺子连对飞机 / R5 四张拆三张 / R7 长牌型不校验长度）。</p>
 */
public class GameRuleUtil {

    /** 斗地主总牌数：54张（4花色×13张 + 大小王） */
    public static final int TOTAL_CARDS = 54;

    /** 每个玩家应得的手牌数：17张 */
    public static final int CARDS_PER_PLAYER = 17;

    /** 底牌数量：3张，由地主获得 */
    public static final int BOTTOM_CARDS_COUNT = 3;

    private GameRuleUtil() {}

    /**
     * 洗牌并发牌。
     *
     * @return 包含4个List的数组：[0][1][2]为三家手牌（升序），[3]为底牌（升序）
     */
    public static List<Card>[] shuffleAndDeal() {
        List<Card> deck = new ArrayList<>(Arrays.asList(Card.createFullDeck()));
        Collections.shuffle(deck);

        @SuppressWarnings("unchecked")
        List<Card>[] result = new ArrayList[4];
        for (int i = 0; i < 3; i++) {
            result[i] = new ArrayList<>(deck.subList(i * CARDS_PER_PLAYER, (i + 1) * CARDS_PER_PLAYER));
            sortCardsByWeightAscending(result[i]);
        }
        result[3] = new ArrayList<>(deck.subList(3 * CARDS_PER_PLAYER, TOTAL_CARDS));
        sortCardsByWeightAscending(result[3]);
        return result;
    }

    /** 按权重升序排序（3在左→大王在右），权重相同按花色 ordinal。 */
    public static void sortCardsByWeightAscending(List<Card> cards) {
        if (cards == null || cards.size() <= 1) {
            return;
        }
        Collections.sort(cards, new Comparator<Card>() {
            @Override
            public int compare(Card c1, Card c2) {
                int weightCompare = Integer.compare(c1.getWeight(), c2.getWeight());
                if (weightCompare != 0) {
                    return weightCompare;
                }
                if (!c1.getRank().isJoker() && !c2.getRank().isJoker()) {
                    return c1.getSuit().ordinal() - c2.getSuit().ordinal();
                }
                return 0;
            }
        });
    }

    /**
     * 判定牌组类型（兼容旧签名）。
     *
     * @return 合法牌型或 {@link CardType#ERROR}
     */
    public static CardType getCardType(List<Card> cards) {
        Combo combo = Combo.of(cards);
        return combo == null ? CardType.ERROR : combo.getType();
    }

    /**
     * 判断 currentCards 能否压过 previousCards。
     *
     * @param previousCards null 或空表示自由出牌
     */
    public static boolean canPlayPass(List<Card> currentCards, List<Card> previousCards) {
        Combo current = Combo.of(currentCards);
        if (current == null) {
            return false;
        }
        if (previousCards == null || previousCards.isEmpty()) {
            return true;
        }
        Combo previous = Combo.of(previousCards);
        return current.beats(previous);
    }

    /**
     * 牌组主权重（兼容旧签名；与传入顺序无关）。非法牌型返回 0。
     */
    public static int getMainWeight(List<Card> cards) {
        Combo combo = Combo.of(cards);
        return combo == null ? 0 : combo.getMainWeight();
    }

    /**
     * 从手牌中找出所有能压过 previousCards 的候选组合（兼容旧签名）。
     *
     * @param previousCards null 或空表示自由出牌
     */
    public static List<List<Card>> findPlayableCombos(List<Card> handCards,
                                                      List<Card> previousCards) {
        Combo previous = (previousCards == null || previousCards.isEmpty())
                ? null : Combo.of(previousCards);
        List<Combo> combos = HintFinder.findPlayable(handCards, previous);
        List<List<Card>> result = new ArrayList<>();
        for (Combo combo : combos) {
            result.add(new ArrayList<>(combo.getCards()));
        }
        return result;
    }

    /** 炸弹倍数：普通炸弹 2，王炸 4，其他 1。 */
    public static int getBombMultiplier(List<Card> cards) {
        CardType type = getCardType(cards);
        if (type == CardType.BOMB) {
            return 2;
        } else if (type == CardType.JOKER_BOMB) {
            return 4;
        }
        return 1;
    }

    /** 牌组非空且不含 null 元素。 */
    public static boolean isValidCardList(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (Card card : cards) {
            if (card == null) {
                return false;
            }
        }
        return true;
    }

    /** 是否包含大王。 */
    public static boolean hasBigJoker(List<Card> cards) {
        if (cards == null) return false;
        for (Card card : cards) {
            if (card.getRank() == Rank.BIG_JOKER) {
                return true;
            }
        }
        return false;
    }

    /** 是否包含小王。 */
    public static boolean hasSmallJoker(List<Card> cards) {
        if (cards == null) return false;
        for (Card card : cards) {
            if (card.getRank() == Rank.SMALL_JOKER) {
                return true;
            }
        }
        return false;
    }
}

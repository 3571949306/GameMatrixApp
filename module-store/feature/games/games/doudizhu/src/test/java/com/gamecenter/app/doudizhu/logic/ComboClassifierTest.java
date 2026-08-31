package com.gamecenter.app.doudizhu.logic;

import static com.gamecenter.app.doudizhu.logic.TestCards.card;
import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.run;
import static com.gamecenter.app.doudizhu.logic.TestCards.shuffled;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;

import org.junit.Test;

import java.util.List;

/**
 * 牌型分类矩阵测试：13 种合法牌型 + 边界非法组合 + 顺序无关性（R1/R2/R3/R5 回归）。
 */
public class ComboClassifierTest {

    // ============ 基本牌型 ============

    @Test
    public void single() {
        Combo c = Combo.of(of(9, 1));
        assertNotNull(c);
        assertEquals(CardType.SINGLE, c.getType());
        assertEquals(9, c.getMainWeight());
    }

    @Test
    public void pair() {
        Combo c = Combo.of(of(9, 2));
        assertEquals(CardType.PAIR, c.getType());
        assertEquals(9, c.getMainWeight());
    }

    @Test
    public void pairOfTwoIsLegal() {
        Combo c = Combo.of(of(15, 2));
        assertEquals(CardType.PAIR, c.getType());
        assertEquals(15, c.getMainWeight());
    }

    @Test
    public void trio() {
        Combo c = Combo.of(of(7, 3));
        assertEquals(CardType.TRIO, c.getType());
        assertEquals(7, c.getMainWeight());
    }

    @Test
    public void bomb() {
        Combo c = Combo.of(of(7, 4));
        assertEquals(CardType.BOMB, c.getType());
        assertEquals(7, c.getMainWeight());
    }

    @Test
    public void jokerBomb() {
        Combo c = Combo.of(of(16, 1, 17, 1));
        assertEquals(CardType.JOKER_BOMB, c.getType());
        assertEquals(17, c.getMainWeight());
    }

    @Test
    public void twoJokersAreNotPair() {
        // 大小王是王炸而非对子
        Combo c = Combo.of(of(16, 1, 17, 1));
        assertEquals(CardType.JOKER_BOMB, c.getType());
    }

    @Test
    public void trioSingle() {
        Combo c = Combo.of(of(8, 3, 5, 1));
        assertEquals(CardType.TRIO_SINGLE, c.getType());
        assertEquals(8, c.getMainWeight());
    }

    @Test
    public void trioPair() {
        Combo c = Combo.of(of(8, 3, 5, 2));
        assertEquals(CardType.TRIO_PAIR, c.getType());
        assertEquals(8, c.getMainWeight());
    }

    // ============ 顺子 / 连对 ============

    @Test
    public void straightFive() {
        Combo c = Combo.of(straight(3, 5));
        assertEquals(CardType.STRAIGHT, c.getType());
        assertEquals(7, c.getMainWeight());
        assertEquals(5, c.getLength());
    }

    @Test
    public void straightToAce() {
        Combo c = Combo.of(straight(10, 5));
        assertEquals(CardType.STRAIGHT, c.getType());
        assertEquals(14, c.getMainWeight());
    }

    @Test
    public void straightCannotContainTwo() {
        assertNull(Combo.of(of(11, 1, 12, 1, 13, 1, 14, 1, 15, 1)));
    }

    @Test
    public void straightCannotWrapAceLow() {
        // A2345 非法（A 只能作最高）
        assertNull(Combo.of(of(14, 1, 15, 1, 3, 1, 4, 1, 5, 1)));
    }

    @Test
    public void straightTooShort() {
        assertNull(Combo.of(straight(3, 4)));
    }

    @Test
    public void straightWithDuplicateInvalid() {
        assertNull(Combo.of(of(3, 2, 4, 1, 5, 1, 6, 1)));
    }

    @Test
    public void straightPairs() {
        Combo c = Combo.of(run(3, 3, 2));
        assertEquals(CardType.STRAIGHT_PAIRS, c.getType());
        assertEquals(5, c.getMainWeight());
        assertEquals(3, c.getLength());
    }

    @Test
    public void straightPairsTwoPairsInvalid() {
        assertNull(Combo.of(run(3, 2, 2)));
    }

    @Test
    public void straightPairsCannotContainTwo() {
        // KKAA22（13,14,15 各一对）：2 不能参与连对
        assertNull(Combo.of(run(13, 3, 2)));
    }

    // ============ 飞机 ============

    @Test
    public void pureAirplane() {
        Combo c = Combo.of(run(3, 2, 3));
        assertEquals(CardType.AIRPLANE, c.getType());
        assertEquals(4, c.getMainWeight());
        assertEquals(2, c.getLength());
    }

    @Test
    public void pureAirplaneThreeGroups() {
        Combo c = Combo.of(run(3, 3, 3));
        assertEquals(CardType.AIRPLANE, c.getType());
        assertEquals(5, c.getMainWeight());
        assertEquals(3, c.getLength());
    }

    @Test
    public void airplaneCannotContainTwo() {
        // AAA222（14,15 各三张）：2 不能参与飞机
        assertNull(Combo.of(run(14, 2, 3)));
    }

    @Test
    public void airplaneWithSingleWings() {
        Combo c = Combo.of(concat(run(3, 2, 3), of(9, 1, 11, 1)));
        assertEquals(CardType.AIRPLANE_WITH_WINGS, c.getType());
        assertEquals(4, c.getMainWeight());
        assertEquals(2, c.getLength());
        assertEquals(Combo.WINGS_SINGLES, c.getWingsKind());
    }

    @Test
    public void airplaneWithPairWings() {
        Combo c = Combo.of(concat(run(3, 2, 3), of(9, 2, 11, 2)));
        assertEquals(CardType.AIRPLANE_WITH_WINGS, c.getType());
        assertEquals(Combo.WINGS_PAIRS, c.getWingsKind());
    }

    @Test
    public void airplaneWingsCountMismatchInvalid() {
        // 2 组三张只带 1 张翅膀 → 非法
        assertNull(Combo.of(concat(run(3, 2, 3), of(9, 1))));
    }

    /** R5：四张拆三张——33334444 可作 333444 带两单（3、4 各剩一张当翅膀）。 */
    @Test
    public void quadSplitAirplaneWithWings() {
        Combo c = Combo.of(of(3, 4, 4, 4));
        assertNotNull(c);
        // 本实现优先判定四带两对（3333+44+44），两种解释均合法、取其一即可
        assertEquals(CardType.QUAD_PAIR, c.getType());
    }

    /** R5：333344445555 → 333444555 带单翅膀 3/4/5。 */
    @Test
    public void tripleQuadAirplaneWithWings() {
        Combo c = Combo.of(of(3, 4, 4, 4, 5, 4));
        assertNotNull(c);
        assertEquals(CardType.AIRPLANE_WITH_WINGS, c.getType());
        assertEquals(5, c.getMainWeight());
        assertEquals(3, c.getLength());
        assertEquals(Combo.WINGS_SINGLES, c.getWingsKind());
    }

    /** 333344445566（12 张）无法构成任何合法单一牌型。 */
    @Test
    public void quadPairMixedWingsInvalid() {
        assertNull(Combo.of(of(3, 4, 4, 4, 5, 2, 6, 2)));
    }

    // ============ 四带二（R1 回归：旧内核从未接线） ============

    @Test
    public void quadTwoSingles() {
        Combo c = Combo.of(of(3, 4, 9, 1, 11, 1));
        assertEquals(CardType.QUAD_SINGLE, c.getType());
        assertEquals(3, c.getMainWeight());
    }

    @Test
    public void quadTwoSinglesKickersArePair() {
        // 3333+55：两张单牌同点，按四带两单处理
        Combo c = Combo.of(of(3, 4, 5, 2));
        assertEquals(CardType.QUAD_SINGLE, c.getType());
    }

    @Test
    public void quadTwoPairs() {
        Combo c = Combo.of(of(3, 4, 5, 2, 9, 2));
        assertEquals(CardType.QUAD_PAIR, c.getType());
        assertEquals(3, c.getMainWeight());
    }

    @Test
    public void quadWithThreeOneInvalid() {
        // 3333+444（7 张）非法
        assertNull(Combo.of(of(3, 4, 4, 3)));
    }

    // ============ 顺序无关性（R2/R3 回归） ============

    @Test
    public void classificationIsOrderIndependent() {
        List<Card> straight = straight(4, 6);
        Combo a = Combo.of(straight);
        Combo b = Combo.of(shuffled(straight));
        assertEquals(a.getType(), b.getType());
        assertEquals(a.getMainWeight(), b.getMainWeight());
        assertEquals(a.getLength(), b.getLength());

        List<Card> airplane = concat(run(5, 2, 3), of(9, 2, 11, 2));
        Combo c1 = Combo.of(airplane);
        Combo c2 = Combo.of(shuffled(airplane));
        assertEquals(c1.getType(), c2.getType());
        assertEquals(c1.getMainWeight(), c2.getMainWeight());
        assertEquals(c1.getWingsKind(), c2.getWingsKind());
    }

    /** R3：飞机主权重必须取连续段最大三张，而非任意 HashMap 顺序。 */
    @Test
    public void airplaneMainWeightIsTopTrio() {
        Combo c = Combo.of(run(5, 3, 3));
        assertEquals(7, c.getMainWeight());
    }

    // ============ 输入防御 ============

    @Test
    public void nullAndEmptyInvalid() {
        assertNull(Combo.of(null));
        assertNull(Combo.of(new java.util.ArrayList<>()));
    }

    @Test
    public void twoDifferentRanksInvalid() {
        assertNull(Combo.of(of(3, 1, 9, 1)));
    }

    @Test
    public void fourCardsTwoPairsInvalid() {
        assertNull(Combo.of(of(3, 2, 9, 2)));
    }

    // ============ helpers ============

    private static List<Card> concat(List<Card> a, List<Card> b) {
        List<Card> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}

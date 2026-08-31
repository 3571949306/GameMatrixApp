package com.gamecenter.app.doudizhu.logic;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.run;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 提示引擎测试（R4 回归：旧实现搜不了顺子/连对/飞机/四带二、自由出牌只给单牌）。
 */
public class HintFinderTest {

    private static List<Card> hand(int... weightCopies) {
        return of(weightCopies);
    }

    private static boolean containsType(List<Combo> combos, CardType type) {
        for (Combo c : combos) {
            if (c.getType() == type) return true;
        }
        return false;
    }

    private static boolean containsBeat(List<Combo> combos, Combo target) {
        for (Combo c : combos) {
            if (c.getType() == target.getType() && c.getMainWeight() == target.getMainWeight()
                    && c.getLength() == target.getLength()) {
                return true;
            }
        }
        return false;
    }

    // ============ 自由出牌 ============

    @Test
    public void freeLeadCoversAllTypes() {
        List<Card> h = new ArrayList<>();
        h.addAll(of(3, 1, 4, 1, 5, 1, 6, 1, 7, 1));  // 顺子
        h.addAll(of(9, 2));                            // 对子
        h.addAll(of(11, 3, 13, 1));                    // 三带一
        h.addAll(of(15, 4));                           // 炸弹
        h.addAll(of(16, 1, 17, 1));                    // 王炸
        List<Combo> leads = HintFinder.findPlayable(h, null);
        assertTrue(containsType(leads, CardType.SINGLE));
        assertTrue("自由出牌必须能出对子", containsType(leads, CardType.PAIR));
        assertTrue("自由出牌必须能出三带一", containsType(leads, CardType.TRIO_SINGLE));
        assertTrue("自由出牌必须能出顺子", containsType(leads, CardType.STRAIGHT));
        assertTrue(containsType(leads, CardType.BOMB));
        assertTrue(containsType(leads, CardType.JOKER_BOMB));
    }

    @Test
    public void leadsSortedByCost() {
        List<Combo> leads = HintFinder.findPlayable(hand(5, 1, 9, 1, 15, 4), null);
        assertFalse(leads.isEmpty());
        // 首个候选应是最小单牌，炸弹/王炸排在普通牌之后
        assertTrue(leads.get(0).getMainWeight() <= 5);
        for (int i = 1; i < leads.size(); i++) {
            Combo prev = leads.get(i - 1);
            Combo cur = leads.get(i);
            if (!prev.isBomb() && cur.isBomb()) continue;
            if (prev.isBomb() && !cur.isBomb()) {
                assertTrue("炸弹必须排在普通牌后", false);
            }
        }
    }

    // ============ 跟牌 ============

    @Test
    public void beatSingleFindsAllHigherSingles() {
        List<Combo> out = HintFinder.findPlayable(hand(3, 1, 9, 1, 13, 1),
                Combo.of(hand(5, 1)));
        assertTrue(containsBeat(out, Combo.of(hand(9, 1))));
        assertTrue(containsBeat(out, Combo.of(hand(13, 1))));
        assertFalse("3 压不了 5", containsBeat(out, Combo.of(hand(3, 1))));
    }

    @Test
    public void beatPairWithBomb() {
        List<Combo> out = HintFinder.findPlayable(hand(3, 4), Combo.of(hand(9, 2)));
        assertTrue("炸弹可压对子", containsType(out, CardType.BOMB));
    }

    /** R4 核心回归：顺子必须能被提示。 */
    @Test
    public void beatStraight() {
        List<Card> h = straight(4, 5); // 45678
        Combo prev = Combo.of(straight(3, 5)); // 34567 top=7
        assertNotNull(prev);
        List<Combo> out = HintFinder.findPlayable(h, prev);
        assertTrue("应提示 45678 压 34567", containsBeat(out, Combo.of(straight(4, 5))));
    }

    @Test
    public void beatStraightLengthMismatchNotOffered() {
        List<Card> h = straight(4, 6); // 456789（6张）
        Combo prev = Combo.of(straight(3, 5)); // 5张
        List<Combo> out = HintFinder.findPlayable(h, prev);
        for (Combo c : out) {
            if (c.getType() == CardType.STRAIGHT) {
                assertTrue("只应给同长度顺子", c.getLength() == 5);
            }
        }
    }

    @Test
    public void beatStraightPairs() {
        List<Card> h = run(4, 3, 2); // 445566
        Combo prev = Combo.of(run(3, 3, 2)); // 334455
        List<Combo> out = HintFinder.findPlayable(h, prev);
        assertTrue(containsBeat(out, Combo.of(run(4, 3, 2))));
    }

    @Test
    public void beatAirplane() {
        List<Card> h = run(5, 2, 3); // 555666
        Combo prev = Combo.of(run(3, 2, 3)); // 333444
        List<Combo> out = HintFinder.findPlayable(h, prev);
        assertTrue(containsBeat(out, Combo.of(run(5, 2, 3))));
    }

    @Test
    public void beatAirplaneWithWingsMatchesWingsKind() {
        List<Card> h = new ArrayList<>();
        h.addAll(run(5, 2, 3));
        h.addAll(of(9, 1, 11, 1)); // 带单翅膀
        Combo prevPairs = Combo.of(concatAll(run(3, 2, 3), of(9, 2, 11, 2))); // 带对
        List<Combo> out = HintFinder.findPlayable(h, prevPairs);
        for (Combo c : out) {
            if (c.getType() == CardType.AIRPLANE_WITH_WINGS) {
                assertTrue("带对牌型不应提示带单", c.getWingsKind() == Combo.WINGS_PAIRS);
            }
        }
    }

    @Test
    public void beatQuadSingle() {
        List<Card> h = of(9, 4, 3, 1, 5, 1);
        Combo prev = Combo.of(of(7, 4, 3, 1, 5, 1));
        List<Combo> out = HintFinder.findPlayable(h, prev);
        assertTrue(containsType(out, CardType.QUAD_SINGLE));
    }

    @Test
    public void rocketBeatsBomb() {
        List<Card> h = of(16, 1, 17, 1);
        Combo prev = Combo.of(of(9, 4));
        List<Combo> out = HintFinder.findPlayable(h, prev);
        assertTrue(containsType(out, CardType.JOKER_BOMB));
    }

    @Test
    public void nothingBeatsRocket() {
        List<Combo> out = HintFinder.findPlayable(hand(3, 4, 16, 1, 17, 1),
                Combo.of(of(16, 1, 17, 1)));
        assertTrue(out.isEmpty());
    }

    @Test
    public void bombVsBombRequiresHigher() {
        List<Combo> out = HintFinder.findPlayable(hand(5, 4), Combo.of(hand(9, 4)));
        assertTrue(out.isEmpty());
        List<Combo> out2 = HintFinder.findPlayable(hand(13, 4), Combo.of(hand(9, 4)));
        assertTrue(containsType(out2, CardType.BOMB));
    }

    // ============ 自洽性（随机属性） ============

    /** 每个提示候选都必须：可分类、能压过 previous、不超用手牌。 */
    @Test
    public void hintsAreAlwaysValidAndSelfConsistent() {
        Random random = new Random(20260831);
        for (int round = 0; round < 300; round++) {
            List<Card> h = randomHand(random, 6 + random.nextInt(15));
            Combo prev = randomComboOrNull(random, h);
            List<Combo> hints = HintFinder.findPlayable(h, prev);
            for (Combo hint : hints) {
                assertNotNull(Combo.of(hint.getCards()));
                assertTrue("候选必须能压过 previous", hint.beats(prev));
                assertTrue("候选张数不超手牌", hint.size() <= h.size());
            }
        }
    }

    /** 完整性抽查：能压单张的手牌，提示必须给出最小可压单张。 */
    @Test
    public void hintCompletenessForSingles() {
        List<Card> h = of(3, 1, 7, 1, 12, 1);
        List<Combo> out = HintFinder.findPlayable(h, Combo.of(of(6, 1)));
        assertTrue(containsBeat(out, Combo.of(of(7, 1))));
        assertTrue(containsBeat(out, Combo.of(of(12, 1))));
    }

    // ============ helpers ============

    private static List<Card> concatAll(List<Card> a, List<Card> b) {
        List<Card> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static List<Card> randomHand(Random random, int size) {
        List<Card> deck = new ArrayList<>();
        for (int w = 3; w <= 17; w++) {
            int copies = w >= 16 ? 1 : 4;
            for (int c = 0; c < copies; c++) {
                deck.add(TestCards.card(w, c));
            }
        }
        java.util.Collections.shuffle(deck, random);
        return new ArrayList<>(deck.subList(0, size));
    }

    private static Combo randomComboOrNull(Random random, List<Card> hand) {
        if (random.nextBoolean()) return null;
        List<Combo> leads = HintFinder.findPlayable(hand, null);
        if (leads.isEmpty()) return null;
        return leads.get(random.nextInt(leads.size()));
    }
}

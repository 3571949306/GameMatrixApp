package com.gamecenter.app.doudizhu.utils;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.shuffled;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门面委派与工具测试：GameRuleUtil 公开行为必须与新内核一致。
 */
public class GameRuleUtilTest {

    @Test
    public void getCardTypeDelegatesToCombo() {
        assertEquals(CardType.QUAD_SINGLE, GameRuleUtil.getCardType(of(3, 4, 9, 1, 11, 1)));
        assertEquals(CardType.STRAIGHT, GameRuleUtil.getCardType(straight(3, 5)));
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(of(3, 1, 9, 1)));
    }

    @Test
    public void canPlayPassIsOrderIndependent() {
        List<Card> high = straight(5, 5);
        List<Card> low = shuffled(straight(3, 5));
        assertTrue(GameRuleUtil.canPlayPass(high, low));
        assertFalse(GameRuleUtil.canPlayPass(low, high));
    }

    @Test
    public void canPlayPassRejectsDifferentLengthStraights() {
        // R7 回归：旧内核 5 张顺子能"压"6 张顺子
        assertFalse(GameRuleUtil.canPlayPass(straight(9, 5), straight(3, 6)));
    }

    @Test
    public void getMainWeightMatchesCombo() {
        assertEquals(7, GameRuleUtil.getMainWeight(straight(3, 5)));
        assertEquals(0, GameRuleUtil.getMainWeight(of(3, 1, 9, 1)));
    }

    @Test
    public void findPlayableCombosDelegates() {
        List<List<Card>> combos = GameRuleUtil.findPlayableCombos(of(9, 1, 15, 4), of(5, 1));
        assertFalse(combos.isEmpty());
        for (List<Card> combo : combos) {
            assertTrue(GameRuleUtil.canPlayPass(combo, of(5, 1)));
        }
    }

    @Test
    public void shuffleAndDealIntegrity() {
        for (int i = 0; i < 20; i++) {
            List<Card>[] dealt = GameRuleUtil.shuffleAndDeal();
            assertEquals(17, dealt[0].size());
            assertEquals(17, dealt[1].size());
            assertEquals(17, dealt[2].size());
            assertEquals(3, dealt[3].size());
            Set<Card> all = new HashSet<>();
            for (List<Card> part : dealt) {
                all.addAll(part);
            }
            assertEquals("54 张不重复", 54, all.size());
            // 手牌升序
            for (int w = 1; w < dealt[0].size(); w++) {
                assertTrue(dealt[0].get(w - 1).getWeight() <= dealt[0].get(w).getWeight());
            }
        }
    }

    @Test
    public void bombMultiplier() {
        assertEquals(2, GameRuleUtil.getBombMultiplier(of(7, 4)));
        assertEquals(4, GameRuleUtil.getBombMultiplier(of(16, 1, 17, 1)));
        assertEquals(1, GameRuleUtil.getBombMultiplier(of(7, 1)));
        assertEquals(1, GameRuleUtil.getBombMultiplier(of(3, 1, 9, 1)));
    }

    @Test
    public void validationHelpers() {
        assertTrue(GameRuleUtil.isValidCardList(of(3, 1)));
        assertFalse(GameRuleUtil.isValidCardList(null));
        assertFalse(GameRuleUtil.isValidCardList(new java.util.ArrayList<>()));
        List<Card> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        assertFalse(GameRuleUtil.isValidCardList(withNull));
        assertTrue(GameRuleUtil.hasBigJoker(of(17, 1)));
        assertFalse(GameRuleUtil.hasBigJoker(of(16, 1)));
        assertTrue(GameRuleUtil.hasSmallJoker(of(16, 1)));
    }
}

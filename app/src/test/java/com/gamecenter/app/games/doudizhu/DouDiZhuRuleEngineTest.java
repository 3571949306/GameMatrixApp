package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DouDiZhuRuleEngineTest {

    private List<Card> cardsOf(Rank... ranks) {
        List<Card> list = new ArrayList<>();
        for (Rank rank : ranks) {
            list.add(Card.create(Suit.SPADE, rank));
        }
        return list;
    }

    private List<Card> cardsOf(Suit suit, Rank... ranks) {
        List<Card> list = new ArrayList<>();
        for (Rank rank : ranks) {
            list.add(Card.create(suit, rank));
        }
        return list;
    }

    // ========== validatePlay ==========

    @Test
    public void validatePlay_nullCards_returnsFalse() {
        assertFalse(DouDiZhuRuleEngine.validatePlay(null, null));
    }

    @Test
    public void validatePlay_emptyCards_returnsFalse() {
        assertFalse(DouDiZhuRuleEngine.validatePlay(Collections.<Card>emptyList(), null));
    }

    @Test
    public void validatePlay_invalidType_returnsFalse() {
        List<Card> invalid = cardsOf(Rank.THREE, Rank.FIVE, Rank.SEVEN, Rank.NINE, Rank.JACK, Rank.KING);
        assertFalse(DouDiZhuRuleEngine.validatePlay(invalid, null));
    }

    @Test
    public void validatePlay_singleCard_noPrevious_returnsTrue() {
        List<Card> single = cardsOf(Rank.THREE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(single, null));
    }

    @Test
    public void validatePlay_pair_noPrevious_returnsTrue() {
        List<Card> pair = cardsOf(Rank.THREE, Rank.THREE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(pair, null));
    }

    @Test
    public void validatePlay_bomb_noPrevious_returnsTrue() {
        List<Card> bomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(bomb, null));
    }

    @Test
    public void validatePlay_jokerBomb_noPrevious_returnsTrue() {
        List<Card> jokerBomb = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertTrue(DouDiZhuRuleEngine.validatePlay(jokerBomb, null));
    }

    @Test
    public void validatePlay_sameTypeHigherWeight_returnsTrue() {
        List<Card> current = cardsOf(Rank.FIVE);
        List<Card> previous = cardsOf(Rank.THREE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(current, previous));
    }

    @Test
    public void validatePlay_sameTypeLowerWeight_returnsFalse() {
        List<Card> current = cardsOf(Rank.THREE);
        List<Card> previous = cardsOf(Rank.FIVE);
        assertFalse(DouDiZhuRuleEngine.validatePlay(current, previous));
    }

    @Test
    public void validatePlay_bombBeatsNonBomb_returnsTrue() {
        List<Card> bomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        List<Card> previous = cardsOf(Rank.ACE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(bomb, previous));
    }

    @Test
    public void validatePlay_jokerBombBeatsBomb_returnsTrue() {
        List<Card> jokerBomb = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        List<Card> bomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(jokerBomb, bomb));
    }

    @Test
    public void validatePlay_differentTypeNonBomb_returnsFalse() {
        List<Card> pair = cardsOf(Rank.THREE, Rank.THREE);
        List<Card> previous = cardsOf(Rank.FIVE);
        assertFalse(DouDiZhuRuleEngine.validatePlay(pair, previous));
    }

    @Test
    public void validatePlay_higherBombBeatsLowerBomb_returnsTrue() {
        List<Card> highBomb = cardsOf(Rank.KING, Rank.KING, Rank.KING, Rank.KING);
        List<Card> lowBomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertTrue(DouDiZhuRuleEngine.validatePlay(highBomb, lowBomb));
    }

    @Test
    public void validatePlay_lowerBombCannotBeatHigherBomb_returnsFalse() {
        List<Card> lowBomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        List<Card> highBomb = cardsOf(Rank.KING, Rank.KING, Rank.KING, Rank.KING);
        assertFalse(DouDiZhuRuleEngine.validatePlay(lowBomb, highBomb));
    }

    @Test
    public void validatePlay_straight_noPrevious_returnsTrue() {
        List<Card> straight = cardsOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN);
        assertTrue(DouDiZhuRuleEngine.validatePlay(straight, null));
    }

    @Test
    public void validatePlay_trioSingle_noPrevious_returnsTrue() {
        List<Card> trioSingle = new ArrayList<>();
        trioSingle.addAll(cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE));
        trioSingle.add(Card.create(Suit.HEART, Rank.THREE));
        assertTrue(DouDiZhuRuleEngine.validatePlay(trioSingle, null));
    }

    // ========== shouldCallLandlord ==========

    @Test
    public void shouldCallLandlord_nullHand_returnsFalse() {
        assertFalse(DouDiZhuRuleEngine.shouldCallLandlord(null));
    }

    @Test
    public void shouldCallLandlord_emptyHand_returnsFalse() {
        assertFalse(DouDiZhuRuleEngine.shouldCallLandlord(Collections.<Card>emptyList()));
    }

    @Test
    public void shouldCallLandlord_weakHand_returnsFalse() {
        List<Card> weakHand = cardsOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN);
        assertFalse(DouDiZhuRuleEngine.shouldCallLandlord(weakHand));
    }

    @Test
    public void shouldCallLandlord_bigJokerOnly_returnsTrue() {
        List<Card> hand = new ArrayList<>();
        hand.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertTrue(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_smallJokerOnly_returnsTrue() {
        List<Card> hand = new ArrayList<>();
        hand.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        assertTrue(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_multipleTwos_returnsTrue() {
        List<Card> hand = cardsOf(Rank.TWO, Rank.TWO, Rank.TWO, Rank.ACE);
        assertTrue(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_bombOnly_returnsFalse() {
        List<Card> hand = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertFalse(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_bombPlusAce_returnsTrue() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE));
        hand.add(Card.create(Suit.HEART, Rank.ACE));
        assertTrue(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_bombPlusTwo_returnsTrue() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE));
        hand.add(Card.create(Suit.HEART, Rank.TWO));
        assertTrue(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_twoAces_returnsFalse() {
        List<Card> hand = cardsOf(Rank.ACE, Rank.ACE);
        assertFalse(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_oneTwoOneAce_returnsFalse() {
        List<Card> hand = cardsOf(Rank.TWO, Rank.ACE);
        assertFalse(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    @Test
    public void shouldCallLandlord_jokerAndTwo_returnsTrue() {
        List<Card> hand = new ArrayList<>();
        hand.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        hand.add(Card.create(Suit.SPADE, Rank.TWO));
        assertTrue(DouDiZhuRuleEngine.shouldCallLandlord(hand));
    }

    // ========== shouldClearTable ==========

    @Test
    public void shouldClearTable_allOthersPassed_returnsTrue() {
        boolean[] passed = {true, true, false};
        assertTrue(DouDiZhuRuleEngine.shouldClearTable(passed, 2, 3));
    }

    @Test
    public void shouldClearTable_notAllPassed_returnsFalse() {
        boolean[] passed = {true, false, false};
        assertFalse(DouDiZhuRuleEngine.shouldClearTable(passed, 2, 3));
    }

    @Test
    public void shouldClearTable_nullPassed_returnsFalse() {
        assertFalse(DouDiZhuRuleEngine.shouldClearTable(null, 0, 3));
    }

    @Test
    public void shouldClearTable_invalidLastPlayer_returnsFalse() {
        boolean[] passed = {true, true, false};
        assertFalse(DouDiZhuRuleEngine.shouldClearTable(passed, -1, 3));
        assertFalse(DouDiZhuRuleEngine.shouldClearTable(passed, 3, 3));
    }

    @Test
    public void shouldClearTable_shortArray_returnsFalse() {
        boolean[] passed = {true};
        assertFalse(DouDiZhuRuleEngine.shouldClearTable(passed, 0, 3));
    }

    @Test
    public void shouldClearTable_seat0Played_seat1And2Passed_returnsTrue() {
        boolean[] passed = {false, true, true};
        assertTrue(DouDiZhuRuleEngine.shouldClearTable(passed, 0, 3));
    }

    @Test
    public void shouldClearTable_seat1Played_seat0And2NotPassed_returnsFalse() {
        boolean[] passed = {false, false, false};
        assertFalse(DouDiZhuRuleEngine.shouldClearTable(passed, 1, 3));
    }

    // ========== evaluateHandScore ==========

    @Test
    public void evaluateHandScore_nullHand_returnsZero() {
        assertEquals(0, DouDiZhuRuleEngine.evaluateHandScore(null));
    }

    @Test
    public void evaluateHandScore_emptyHand_returnsZero() {
        assertEquals(0, DouDiZhuRuleEngine.evaluateHandScore(Collections.<Card>emptyList()));
    }

    @Test
    public void evaluateHandScore_bigJoker_returns8() {
        List<Card> hand = new ArrayList<>();
        hand.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertEquals(8, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_smallJoker_returns8() {
        List<Card> hand = new ArrayList<>();
        hand.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        assertEquals(8, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_two_returns2() {
        List<Card> hand = cardsOf(Rank.TWO);
        assertEquals(2, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_ace_returns1() {
        List<Card> hand = cardsOf(Rank.ACE);
        assertEquals(1, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_normalCard_returns0() {
        List<Card> hand = cardsOf(Rank.THREE);
        assertEquals(0, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_bomb_returns6() {
        List<Card> hand = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertEquals(6, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_jokerBomb_returns16() {
        List<Card> hand = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertEquals(16, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_complexHand_returnsCorrectScore() {
        List<Card> hand = new ArrayList<>();
        hand.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        hand.add(Card.create(Suit.SPADE, Rank.TWO));
        hand.add(Card.create(Suit.HEART, Rank.TWO));
        hand.add(Card.create(Suit.SPADE, Rank.ACE));
        hand.addAll(cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE));
        int expected = 8 + 2 + 2 + 1 + 6;
        assertEquals(expected, DouDiZhuRuleEngine.evaluateHandScore(hand));
    }

    @Test
    public void evaluateHandScore_consistentWithShouldCallLandlord() {
        List<Card> strongHand = new ArrayList<>();
        strongHand.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        strongHand.add(Card.create(Suit.SPADE, Rank.TWO));
        int score = DouDiZhuRuleEngine.evaluateHandScore(strongHand);
        boolean shouldCall = DouDiZhuRuleEngine.shouldCallLandlord(strongHand);
        assertEquals(score >= 7, shouldCall);
    }
}

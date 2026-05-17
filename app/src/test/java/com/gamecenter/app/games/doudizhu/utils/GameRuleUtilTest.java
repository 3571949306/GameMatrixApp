package com.gamecenter.app.games.doudizhu.utils;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GameRuleUtilTest {

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

    // ========== getCardType ==========

    @Test
    public void getCardType_null_returnsError() {
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(null));
    }

    @Test
    public void getCardType_empty_returnsError() {
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(Collections.<Card>emptyList()));
    }

    @Test
    public void getCardType_singleCard_returnsSingle() {
        assertEquals(CardType.SINGLE, GameRuleUtil.getCardType(cardsOf(Rank.THREE)));
    }

    @Test
    public void getCardType_pair_returnsPair() {
        assertEquals(CardType.PAIR, GameRuleUtil.getCardType(cardsOf(Rank.FIVE, Rank.FIVE)));
    }

    @Test
    public void getCardType_twoDifferentCards_returnsError() {
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(cardsOf(Rank.THREE, Rank.FIVE)));
    }

    @Test
    public void getCardType_trio_returnsTrio() {
        assertEquals(CardType.TRIO, GameRuleUtil.getCardType(cardsOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN)));
    }

    @Test
    public void getCardType_trioSingle_returnsTrioSingle() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN));
        cards.add(Card.create(Suit.HEART, Rank.THREE));
        assertEquals(CardType.TRIO_SINGLE, GameRuleUtil.getCardType(cards));
    }

    @Test
    public void getCardType_trioPair_returnsTrioPair() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN));
        cards.addAll(cardsOf(Suit.HEART, Rank.THREE, Rank.THREE));
        assertEquals(CardType.TRIO_PAIR, GameRuleUtil.getCardType(cards));
    }

    @Test
    public void getCardType_bomb_returnsBomb() {
        assertEquals(CardType.BOMB, GameRuleUtil.getCardType(cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE)));
    }

    @Test
    public void getCardType_jokerBomb_returnsJokerBomb() {
        List<Card> jokerBomb = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertEquals(CardType.JOKER_BOMB, GameRuleUtil.getCardType(jokerBomb));
    }

    @Test
    public void getCardType_straight5_returnsStraight() {
        assertEquals(CardType.STRAIGHT, GameRuleUtil.getCardType(
                cardsOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN)));
    }

    @Test
    public void getCardType_straight12_returnsStraight() {
        assertEquals(CardType.STRAIGHT, GameRuleUtil.getCardType(
                cardsOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN,
                        Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE)));
    }

    @Test
    public void getCardType_straightWithTwo_returnsError() {
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(
                cardsOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE, Rank.TWO)));
    }

    @Test
    public void getCardType_straightWithJoker_returnsError() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX));
        cards.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(cards));
    }

    @Test
    public void getCardType_straightTooShort_returnsError() {
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(
                cardsOf(Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX)));
    }

    @Test
    public void getCardType_straightNotConsecutive_returnsError() {
        assertEquals(CardType.ERROR, GameRuleUtil.getCardType(
                cardsOf(Rank.THREE, Rank.FOUR, Rank.SIX, Rank.SEVEN, Rank.EIGHT)));
    }

    @Test
    public void getCardType_straightPairs_returnsStraightPairs() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.THREE, Rank.THREE));
        cards.addAll(cardsOf(Suit.HEART, Rank.FOUR, Rank.FOUR));
        cards.addAll(cardsOf(Suit.CLUB, Rank.FIVE, Rank.FIVE));
        assertEquals(CardType.STRAIGHT_PAIRS, GameRuleUtil.getCardType(cards));
    }

    @Test
    public void getCardType_airplane_returnsAirplane() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.THREE, Rank.THREE, Rank.THREE));
        cards.addAll(cardsOf(Suit.HEART, Rank.FOUR, Rank.FOUR, Rank.FOUR));
        assertEquals(CardType.AIRPLANE, GameRuleUtil.getCardType(cards));
    }

    @Test
    public void getCardType_airplaneWithWings_singles_returnsAirplaneWithWings() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.THREE, Rank.THREE, Rank.THREE));
        cards.addAll(cardsOf(Suit.HEART, Rank.FOUR, Rank.FOUR, Rank.FOUR));
        cards.add(Card.create(Suit.CLUB, Rank.FIVE));
        cards.add(Card.create(Suit.DIAMOND, Rank.SIX));
        assertEquals(CardType.AIRPLANE_WITH_WINGS, GameRuleUtil.getCardType(cards));
    }

    // ========== canPlayPass ==========

    @Test
    public void canPlayPass_nullPrevious_returnsTrueForValidType() {
        List<Card> single = cardsOf(Rank.THREE);
        assertTrue(GameRuleUtil.canPlayPass(single, null));
    }

    @Test
    public void canPlayPass_emptyPrevious_returnsTrueForValidType() {
        List<Card> single = cardsOf(Rank.THREE);
        assertTrue(GameRuleUtil.canPlayPass(single, Collections.<Card>emptyList()));
    }

    @Test
    public void canPlayPass_higherSingle_returnsTrue() {
        assertTrue(GameRuleUtil.canPlayPass(cardsOf(Rank.FIVE), cardsOf(Rank.THREE)));
    }

    @Test
    public void canPlayPass_lowerSingle_returnsFalse() {
        assertFalse(GameRuleUtil.canPlayPass(cardsOf(Rank.THREE), cardsOf(Rank.FIVE)));
    }

    @Test
    public void canPlayPass_bombBeatsSingle_returnsTrue() {
        List<Card> bomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertTrue(GameRuleUtil.canPlayPass(bomb, cardsOf(Rank.ACE)));
    }

    @Test
    public void canPlayPass_jokerBombBeatsBomb_returnsTrue() {
        List<Card> jokerBomb = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        List<Card> bomb = cardsOf(Rank.KING, Rank.KING, Rank.KING, Rank.KING);
        assertTrue(GameRuleUtil.canPlayPass(jokerBomb, bomb));
    }

    @Test
    public void canPlayPass_differentTypesNonBomb_returnsFalse() {
        List<Card> pair = cardsOf(Rank.THREE, Rank.THREE);
        List<Card> single = cardsOf(Rank.FIVE);
        assertFalse(GameRuleUtil.canPlayPass(pair, single));
    }

    @Test
    public void canPlayPass_higherPair_returnsTrue() {
        List<Card> current = cardsOf(Rank.FIVE, Rank.FIVE);
        List<Card> previous = cardsOf(Rank.THREE, Rank.THREE);
        assertTrue(GameRuleUtil.canPlayPass(current, previous));
    }

    @Test
    public void canPlayPass_higherBomb_returnsTrue() {
        List<Card> current = cardsOf(Rank.KING, Rank.KING, Rank.KING, Rank.KING);
        List<Card> previous = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertTrue(GameRuleUtil.canPlayPass(current, previous));
    }

    // ========== getMainWeight ==========

    @Test
    public void getMainWeight_null_returnsZero() {
        assertEquals(0, GameRuleUtil.getMainWeight(null));
    }

    @Test
    public void getMainWeight_empty_returnsZero() {
        assertEquals(0, GameRuleUtil.getMainWeight(Collections.<Card>emptyList()));
    }

    @Test
    public void getMainWeight_single_returnsWeight() {
        assertEquals(Rank.FIVE.getWeight(), GameRuleUtil.getMainWeight(cardsOf(Rank.FIVE)));
    }

    @Test
    public void getMainWeight_pair_returnsWeight() {
        assertEquals(Rank.SEVEN.getWeight(), GameRuleUtil.getMainWeight(cardsOf(Rank.SEVEN, Rank.SEVEN)));
    }

    @Test
    public void getMainWeight_bomb_returnsWeight() {
        List<Card> bomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertEquals(Rank.FIVE.getWeight(), GameRuleUtil.getMainWeight(bomb));
    }

    @Test
    public void getMainWeight_jokerBomb_returnsBigJokerWeight() {
        List<Card> jokerBomb = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertEquals(Rank.BIG_JOKER.getWeight(), GameRuleUtil.getMainWeight(jokerBomb));
    }

    @Test
    public void getMainWeight_trioSingle_returnsTrioWeight() {
        List<Card> cards = new ArrayList<>();
        cards.addAll(cardsOf(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN));
        cards.add(Card.create(Suit.HEART, Rank.THREE));
        assertEquals(Rank.SEVEN.getWeight(), GameRuleUtil.getMainWeight(cards));
    }

    // ========== getBombMultiplier ==========

    @Test
    public void getBombMultiplier_bomb_returns2() {
        List<Card> bomb = cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE);
        assertEquals(2, GameRuleUtil.getBombMultiplier(bomb));
    }

    @Test
    public void getBombMultiplier_jokerBomb_returns4() {
        List<Card> jokerBomb = Arrays.asList(
                Card.create(Suit.JOKER_small, Rank.SMALL_JOKER),
                Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertEquals(4, GameRuleUtil.getBombMultiplier(jokerBomb));
    }

    @Test
    public void getBombMultiplier_single_returns1() {
        assertEquals(1, GameRuleUtil.getBombMultiplier(cardsOf(Rank.THREE)));
    }

    @Test
    public void getBombMultiplier_pair_returns1() {
        assertEquals(1, GameRuleUtil.getBombMultiplier(cardsOf(Rank.THREE, Rank.THREE)));
    }

    // ========== isValidCardList ==========

    @Test
    public void isValidCardList_null_returnsFalse() {
        assertFalse(GameRuleUtil.isValidCardList(null));
    }

    @Test
    public void isValidCardList_empty_returnsFalse() {
        assertFalse(GameRuleUtil.isValidCardList(Collections.<Card>emptyList()));
    }

    @Test
    public void isValidCardList_validCards_returnsTrue() {
        assertTrue(GameRuleUtil.isValidCardList(cardsOf(Rank.THREE)));
    }

    @Test
    public void isValidCardList_containsNull_returnsFalse() {
        List<Card> cards = new ArrayList<>();
        cards.add(Card.create(Suit.SPADE, Rank.THREE));
        cards.add(null);
        assertFalse(GameRuleUtil.isValidCardList(cards));
    }

    // ========== hasBigJoker / hasSmallJoker ==========

    @Test
    public void hasBigJoker_withBigJoker_returnsTrue() {
        List<Card> cards = new ArrayList<>();
        cards.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        assertTrue(GameRuleUtil.hasBigJoker(cards));
    }

    @Test
    public void hasBigJoker_withoutBigJoker_returnsFalse() {
        assertFalse(GameRuleUtil.hasBigJoker(cardsOf(Rank.THREE)));
    }

    @Test
    public void hasBigJoker_null_returnsFalse() {
        assertFalse(GameRuleUtil.hasBigJoker(null));
    }

    @Test
    public void hasSmallJoker_withSmallJoker_returnsTrue() {
        List<Card> cards = new ArrayList<>();
        cards.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        assertTrue(GameRuleUtil.hasSmallJoker(cards));
    }

    @Test
    public void hasSmallJoker_withoutSmallJoker_returnsFalse() {
        assertFalse(GameRuleUtil.hasSmallJoker(cardsOf(Rank.THREE)));
    }

    @Test
    public void hasSmallJoker_null_returnsFalse() {
        assertFalse(GameRuleUtil.hasSmallJoker(null));
    }

    // ========== shuffleAndDeal ==========

    @Test
    public void shuffleAndDeal_returns4Lists() {
        List<Card>[] result = GameRuleUtil.shuffleAndDeal();
        assertEquals(4, result.length);
    }

    @Test
    public void shuffleAndDeal_eachPlayerGets17Cards() {
        List<Card>[] result = GameRuleUtil.shuffleAndDeal();
        for (int i = 0; i < 3; i++) {
            assertEquals(17, result[i].size());
        }
    }

    @Test
    public void shuffleAndDeal_bottomCardsAre3() {
        List<Card>[] result = GameRuleUtil.shuffleAndDeal();
        assertEquals(3, result[3].size());
    }

    @Test
    public void shuffleAndDeal_totalCardsIs54() {
        List<Card>[] result = GameRuleUtil.shuffleAndDeal();
        int total = 0;
        for (List<Card> list : result) {
            total += list.size();
        }
        assertEquals(54, total);
    }

    // ========== CardType properties ==========

    @Test
    public void cardType_isBomb_bombAndJokerBomb() {
        assertTrue(CardType.BOMB.isBomb());
        assertTrue(CardType.JOKER_BOMB.isBomb());
    }

    @Test
    public void cardType_isBomb_nonBombTypes() {
        assertFalse(CardType.SINGLE.isBomb());
        assertFalse(CardType.PAIR.isBomb());
        assertFalse(CardType.TRIO.isBomb());
        assertFalse(CardType.STRAIGHT.isBomb());
    }

    @Test
    public void cardType_isError_onlyErrorType() {
        assertTrue(CardType.ERROR.isError());
        assertFalse(CardType.SINGLE.isError());
        assertFalse(CardType.BOMB.isError());
    }

    @Test
    public void cardType_canBeat_jokerBombBeatsAll() {
        assertTrue(CardType.JOKER_BOMB.canBeat(CardType.SINGLE));
        assertTrue(CardType.JOKER_BOMB.canBeat(CardType.BOMB));
        assertTrue(CardType.JOKER_BOMB.canBeat(CardType.JOKER_BOMB));
        assertTrue(CardType.JOKER_BOMB.canBeat(null));
    }

    @Test
    public void cardType_canBeat_bombBeatsNonBomb() {
        assertTrue(CardType.BOMB.canBeat(CardType.SINGLE));
        assertTrue(CardType.BOMB.canBeat(CardType.PAIR));
        assertTrue(CardType.BOMB.canBeat(CardType.STRAIGHT));
    }

    @Test
    public void cardType_canBeat_bombCannotBeatJokerBomb() {
        assertFalse(CardType.BOMB.canBeat(CardType.JOKER_BOMB));
    }

    @Test
    public void cardType_canBeat_sameTypeReturnsTrue() {
        assertTrue(CardType.SINGLE.canBeat(CardType.SINGLE));
        assertTrue(CardType.PAIR.canBeat(CardType.PAIR));
        assertTrue(CardType.BOMB.canBeat(CardType.BOMB));
    }

    @Test
    public void cardType_canBeat_differentNonBombTypesReturnsFalse() {
        assertFalse(CardType.SINGLE.canBeat(CardType.PAIR));
        assertFalse(CardType.PAIR.canBeat(CardType.TRIO));
    }

    @Test
    public void cardType_canBeat_nullReturnsTrue() {
        assertTrue(CardType.SINGLE.canBeat(null));
    }
}

package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AIBotTest {

    private List<Card> cardsOf(Rank... ranks) {
        List<Card> cards = new ArrayList<>();
        for (Rank rank : ranks) {
            cards.add(Card.create(Suit.SPADE, rank));
        }
        return cards;
    }

    @Test
    public void farmerDoesNotBeatTeammate() {
        AIBot.GameContext context = new AIBot.GameContext(
                AIBot.ROLE_FARMER,
                1,
                new int[]{AIBot.ROLE_LANDLORD, AIBot.ROLE_FARMER, AIBot.ROLE_FARMER},
                0,
                8,
                2,
                2,
                4,
                8
        );

        List<Card> result = AIBot.decidePlay(
                cardsOf(Rank.FOUR, Rank.SEVEN, Rank.ACE),
                cardsOf(Rank.THREE),
                context
        );

        assertNull(result);
    }

    @Test
    public void farmerUsesSmallestCardThatBeatsLandlord() {
        AIBot.GameContext context = new AIBot.GameContext(
                AIBot.ROLE_FARMER,
                1,
                new int[]{AIBot.ROLE_LANDLORD, AIBot.ROLE_FARMER, AIBot.ROLE_FARMER},
                0,
                6,
                0,
                2,
                7,
                7
        );

        List<Card> result = AIBot.decidePlay(
                cardsOf(Rank.FOUR, Rank.SEVEN, Rank.ACE),
                cardsOf(Rank.SIX),
                context
        );

        assertEquals(1, result.size());
        assertEquals(Rank.SEVEN, result.get(0).getRank());
    }

    @Test
    public void farmerCanUseBombWhenLandlordIsAboutToWin() {
        AIBot.GameContext context = new AIBot.GameContext(
                AIBot.ROLE_FARMER,
                1,
                new int[]{AIBot.ROLE_LANDLORD, AIBot.ROLE_FARMER, AIBot.ROLE_FARMER},
                0,
                2,
                0,
                2,
                7,
                7
        );

        List<Card> result = AIBot.decidePlay(
                cardsOf(Rank.FIVE, Rank.FIVE, Rank.FIVE, Rank.FIVE),
                cardsOf(Rank.ACE),
                context
        );

        assertEquals(4, result.size());
        assertEquals(Rank.FIVE, result.get(0).getRank());
    }
}

package com.gamecenter.app.games.doudizhu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DouDiZhuProtocolTest {

    @Test
    public void testConstants() {
        assertEquals("JOIN", DouDiZhuProtocol.TYPE_JOIN);
        assertEquals("SEAT_ASSIGNED", DouDiZhuProtocol.TYPE_SEAT_ASSIGNED);
    }

    @Test
    public void testCardsToJsonAndBack() {
        List<Card> originalCards = new ArrayList<>();
        originalCards.add(Card.create(Suit.SPADE, Rank.ACE));
        originalCards.add(Card.create(Suit.HEART, Rank.TWO));
        
        String json = DouDiZhuProtocol.cardsToJson(originalCards);
        assertNotNull(json);
        assertTrue(json.contains("SPADE"));
        assertTrue(json.contains("ACE"));
        
        List<Card> parsedCards = DouDiZhuProtocol.parseCardsFromJson(json);
        assertEquals(2, parsedCards.size());
        assertEquals(Suit.SPADE, parsedCards.get(0).getSuit());
        assertEquals(Rank.ACE, parsedCards.get(0).getRank());
        assertEquals(Suit.HEART, parsedCards.get(1).getSuit());
        assertEquals(Rank.TWO, parsedCards.get(1).getRank());
    }
}

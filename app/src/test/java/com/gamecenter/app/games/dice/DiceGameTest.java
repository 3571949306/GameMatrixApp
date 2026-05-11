package com.gamecenter.app.games.dice;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiceGameTest {

    private DiceGame game;

    @Before
    public void setUp() {
        game = new DiceGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isRoundOver());
        assertEquals(0, game.getPlayerRolls());
        assertEquals(0, game.getAiRolls());
        assertEquals(0, game.getPlayerWins());
        assertEquals(0, game.getAiWins());
        assertEquals(0, game.getDraws());
        assertEquals(0, game.getRound());
    }

    @Test
    public void testInitialDice() {
        int[] playerDice = game.getPlayerDice();
        int[] aiDice = game.getAiDice();
        
        assertNotNull(playerDice);
        assertNotNull(aiDice);
        assertEquals(3, playerDice.length);
        assertEquals(3, aiDice.length);
    }

    @Test
    public void testRollPlayer() {
        game.rollPlayer();
        assertEquals(1, game.getPlayerRolls());
        assertEquals(1, game.getAiRolls());
        
        int[] playerDice = game.getPlayerDice();
        for (int die : playerDice) {
            assertTrue(die >= 1 && die <= 6);
        }
    }

    @Test
    public void testMaxRerolls() {
        assertEquals(2, game.getMaxRerolls());
    }

    @Test
    public void testHandTypeThreeOfAKind() {
        assertEquals(DiceGame.HandType.THREE_OF_A_KIND, 
            DiceGame.getHandType(new int[]{3, 3, 3}));
    }

    @Test
    public void testHandTypeStraight() {
        assertEquals(DiceGame.HandType.STRAIGHT, 
            DiceGame.getHandType(new int[]{1, 2, 3}));
        assertEquals(DiceGame.HandType.STRAIGHT, 
            DiceGame.getHandType(new int[]{4, 5, 6}));
    }

    @Test
    public void testHandTypePair() {
        assertEquals(DiceGame.HandType.PAIR, 
            DiceGame.getHandType(new int[]{1, 1, 2}));
        assertEquals(DiceGame.HandType.PAIR, 
            DiceGame.getHandType(new int[]{3, 4, 4}));
    }

    @Test
    public void testHandTypeHighCard() {
        assertEquals(DiceGame.HandType.HIGH_CARD, 
            DiceGame.getHandType(new int[]{1, 3, 5}));
    }

    @Test
    public void testNextRound() {
        game.rollPlayer();
        game.nextRound();
        
        assertFalse(game.isRoundOver());
        assertEquals(0, game.getPlayerRolls());
        assertEquals("", game.getResultText());
    }

    @Test
    public void testReset() {
        game.rollPlayer();
        game.reset();
        
        assertFalse(game.isRoundOver());
        assertEquals(0, game.getPlayerRolls());
        assertEquals(0, game.getAiRolls());
        assertEquals(0, game.getRound());
    }
}

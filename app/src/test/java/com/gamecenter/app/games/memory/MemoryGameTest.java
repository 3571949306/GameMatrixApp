package com.gamecenter.app.games.memory;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemoryGameTest {

    private MemoryGame game;

    @Before
    public void setUp() {
        game = new MemoryGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertEquals(0, game.getScore());
        assertEquals(0, game.getMatched());
        assertEquals(4, game.getCols());
        assertEquals(4, game.getRows());
    }

    @Test
    public void testBoardSize() {
        assertEquals(MemoryGame.PAIRS * 2, game.getCols() * game.getRows());
    }

    @Test
    public void testCanFlipValidPosition() {
        assertTrue(game.canFlip(0, 0));
        assertTrue(game.canFlip(3, 3));
    }

    @Test
    public void testCannotFlipInvalidPosition() {
        assertFalse(game.canFlip(-1, 0));
        assertFalse(game.canFlip(0, -1));
        assertFalse(game.canFlip(4, 0));
        assertFalse(game.canFlip(0, 4));
    }

    @Test
    public void testFlipCard() {
        assertTrue(game.flipCard(0, 0));
        assertTrue(game.isRevealed(0, 0));
    }

    @Test
    public void testCannotFlipSameCardTwice() {
        game.flipCard(0, 0);
        assertFalse(game.canFlip(0, 0));
    }

    @Test
    public void testFlipTwoCardsMatching() {
        int firstVal = game.getCardValue(0, 0);
        
        int matchX = -1, matchY = -1;
        for (int y = 0; y < game.getRows(); y++) {
            for (int x = 0; x < game.getCols(); x++) {
                if ((x != 0 || y != 0) && game.getCardValue(x, y) == firstVal) {
                    matchX = x;
                    matchY = y;
                    break;
                }
            }
            if (matchX >= 0) break;
        }
        
        if (matchX >= 0) {
            game.flipCard(0, 0);
            game.flipCard(matchX, matchY);
            assertTrue(game.lastMatchSuccessful());
        }
    }

    @Test
    public void testConfirmMatch() {
        int firstVal = game.getCardValue(0, 0);
        
        int matchX = -1, matchY = -1;
        for (int y = 0; y < game.getRows(); y++) {
            for (int x = 0; x < game.getCols(); x++) {
                if ((x != 0 || y != 0) && game.getCardValue(x, y) == firstVal) {
                    matchX = x;
                    matchY = y;
                    break;
                }
            }
            if (matchX >= 0) break;
        }
        
        if (matchX >= 0) {
            game.flipCard(0, 0);
            game.flipCard(matchX, matchY);
            if (game.lastMatchSuccessful()) {
                game.confirmMatch();
                assertTrue(game.getMatched() >= 1);
                assertTrue(game.getScore() >= 10);
            }
        }
    }

    @Test
    public void testReset() {
        game.flipCard(0, 0);
        game.reset();
        
        assertFalse(game.isGameOver());
        assertEquals(0, game.getScore());
        assertEquals(0, game.getMatched());
        
        for (int y = 0; y < game.getRows(); y++) {
            for (int x = 0; x < game.getCols(); x++) {
                assertFalse(game.isRevealed(x, y));
            }
        }
    }

    @Test
    public void testAllPairsExist() {
        int[] counts = new int[MemoryGame.PAIRS];
        for (int y = 0; y < game.getRows(); y++) {
            for (int x = 0; x < game.getCols(); x++) {
                int val = game.getCardValue(x, y);
                assertTrue(val >= 0 && val < MemoryGame.PAIRS);
                counts[val]++;
            }
        }
        for (int count : counts) {
            assertEquals(2, count);
        }
    }
}

package com.gamecenter.app.games.go;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GoGameTest {

    private GoGame game;

    @Before
    public void setUp() {
        game = new GoGame();
        game.startNewGame();
    }

    @Test
    public void testInitialState() {
        assertEquals(GoGame.BLACK, game.getCurrentPlayer());
        assertEquals(0, game.getCapturedByBlack());
        assertEquals(0, game.getCapturedByWhite());
        assertFalse(game.isGameOver());
        assertEquals(0, game.getConsecutivePasses());
    }

    @Test
    public void testPlayMove_ValidMove() {
        // Black plays at (0, 0)
        boolean success = game.playMove(0, 0);
        assertTrue(success);
        assertEquals(GoGame.BLACK, game.getBoard()[0][0]);
        assertEquals(GoGame.WHITE, game.getCurrentPlayer());
        assertEquals(0, game.getConsecutivePasses());
    }

    @Test
    public void testPlayMove_InvalidMove_Occupied() {
        game.playMove(1, 1);
        boolean success = game.playMove(1, 1); // White tries to play on same spot
        assertFalse(success);
    }

    @Test
    public void testPassMove() {
        game.passMove();
        assertEquals(GoGame.WHITE, game.getCurrentPlayer());
        assertEquals(1, game.getConsecutivePasses());
        assertFalse(game.isGameOver());

        game.passMove(); // Both pass -> Game Over
        assertTrue(game.isGameOver());
        assertEquals(2, game.getConsecutivePasses());
    }

    @Test
    public void testCapture() {
        // Simulate a capture of a white stone at (1,1) by black stones at (0,1), (2,1), (1,0), (1,2)
        game.playMove(0, 1); // B
        game.playMove(0, 0); // W
        game.playMove(2, 1); // B
        game.playMove(2, 0); // W
        game.playMove(1, 0); // B
        game.playMove(1, 1); // W (The stone to be captured)
        game.playMove(1, 2); // B (Captures W at 1,1)
        
        assertEquals(GoGame.EMPTY, game.getBoard()[1][1]);
        assertEquals(2, game.getCapturedByBlack()); // Captures (0,0) and (1,1)
    }

    @Test
    public void testSuicide_InvalidMove() {
        // Setup B surrounding W's potential spot, W playing there is suicide
        game.playMove(0, 1); // B
        game.playMove(8, 8); // W
        game.playMove(1, 0); // B
        game.playMove(8, 7); // W
        
        // Corner (0,0) is surrounded by B. 
        // Current player is B, let B play somewhere else
        game.playMove(2, 2); // B
        
        // W tries to play at (0,0), which has 0 liberties and captures nothing.
        boolean success = game.playMove(0, 0); 
        assertFalse(success); // Suicide should be invalid
    }
}

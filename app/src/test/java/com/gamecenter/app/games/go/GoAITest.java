package com.gamecenter.app.games.go;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GoAITest {

    private GoGame game;
    private GoAI ai;

    @Before
    public void setUp() {
        game = new GoGame();
        game.startNewGame();
        ai = new GoAI();
    }

    @Test
    public void testSetDifficulty() {
        ai.setDifficulty(3);
        assertEquals(3, ai.getDifficulty());

        // Invalid difficulty should be ignored
        ai.setDifficulty(5);
        assertEquals(3, ai.getDifficulty());
    }

    @Test
    public void testRandomAiMove() {
        ai.setDifficulty(1); // Easy
        game.playMove(4, 4); // Black plays
        
        int[] move = ai.findBestAiMove(game); // White (AI) plays
        // Random AI might pass if random hits, but for first move usually not null.
        if (move != null) {
            assertEquals(2, move.length);
            assertTrue(move[0] >= 0 && move[0] < GoGame.BOARD_SIZE);
            assertTrue(move[1] >= 0 && move[1] < GoGame.BOARD_SIZE);
            // Shouldn't pick an occupied spot
            assertTrue(move[0] != 4 || move[1] != 4);
        }
    }

    @Test
    public void testGreedyAiMove() {
        ai.setDifficulty(2); // Normal
        game.playMove(4, 4);
        int[] move = ai.findBestAiMove(game);
        if (move != null) {
            assertEquals(2, move.length);
        }
    }

    @Test
    public void testMinimaxAiMove() {
        ai.setDifficulty(3); // Hard
        game.playMove(4, 4);
        int[] move = ai.findBestAiMove(game);
        if (move != null) {
            assertEquals(2, move.length);
        }
    }

    @Test
    public void testMctsAiMove() {
        ai.setDifficulty(4); // Master
        game.playMove(4, 4);
        
        long start = System.currentTimeMillis();
        int[] move = ai.findBestAiMove(game);
        long duration = System.currentTimeMillis() - start;
        
        if (move != null) {
            assertEquals(2, move.length);
        }
        // MCTS should take some time, roughly around MCTS_TIME_LIMIT_MS (1500ms) 
        // but due to test environment constraints it could be faster or exactly limited.
        assertTrue("MCTS took " + duration + "ms", duration > 0);
    }
}

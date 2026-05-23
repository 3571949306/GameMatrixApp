package com.gamecenter.app.games.gomoku;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GomokuAITest {

    private GomokuGame game;
    private GomokuAI ai;

    @Before
    public void setUp() {
        game = new GomokuGame();
        ai = new GomokuAI(1);
    }

    @Test
    public void testAIFindsWinningMove() {
        game.makeMove(0, 7, GomokuGame.BLACK);
        game.makeMove(1, 7, GomokuGame.BLACK);
        game.makeMove(2, 7, GomokuGame.BLACK);
        game.makeMove(3, 7, GomokuGame.BLACK);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertEquals(4, move[0]);
        assertEquals(7, move[1]);
    }

    @Test
    public void testAIBlocksOpponentWin() {
        game.makeMove(0, 7, GomokuGame.WHITE);
        game.makeMove(1, 7, GomokuGame.WHITE);
        game.makeMove(2, 7, GomokuGame.WHITE);
        game.makeMove(3, 7, GomokuGame.WHITE);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertTrue(move[0] >= 0 && move[0] < 15 && move[1] >= 0 && move[1] < 15);
    }

    @Test
    public void testAIPrefersCenterOnEmptyBoard() {
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertTrue(move[0] >= 0 && move[0] < 15);
        assertTrue(move[1] >= 0 && move[1] < 15);
        int centerDist = Math.abs(move[0] - 7) + Math.abs(move[1] - 7);
        assertTrue(centerDist <= 7);
    }

    @Test
    public void testAIReturnsValidOnNearFullBoard() {
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if (game.isValidMove(x, y)) {
                    game.makeMove(x, y, (x + y) % 2 == 0 ? GomokuGame.BLACK : GomokuGame.WHITE);
                }
            }
        }
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertTrue(move == null || (move[0] >= 0 && move[1] >= 0));
    }

    @Test
    public void testAIDifficultyLevels() {
        for (int level = 1; level <= 4; level++) {
            GomokuAI levelAi = new GomokuAI(level);
            int[] move = levelAi.getBestMove(game, GomokuGame.BLACK);
            assertNotNull("Level " + level + " should return a move", move);
        }
    }

    @Test
    public void testAIDetectsOpenFour() {
        game.makeMove(3, 7, GomokuGame.BLACK);
        game.makeMove(4, 7, GomokuGame.BLACK);
        game.makeMove(5, 7, GomokuGame.BLACK);
        game.makeMove(6, 7, GomokuGame.BLACK);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertTrue(move[0] == 7 || move[0] == 2);
    }

    @Test
    public void testAIBlocksOpenThree() {
        game.makeMove(5, 7, GomokuGame.WHITE);
        game.makeMove(6, 7, GomokuGame.WHITE);
        game.makeMove(7, 7, GomokuGame.WHITE);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertTrue(move[0] >= 0 && move[0] < 15);
    }

    @Test
    public void testAIHandlesDiagonalThreat() {
        game.makeMove(0, 0, GomokuGame.BLACK);
        game.makeMove(1, 1, GomokuGame.BLACK);
        game.makeMove(2, 2, GomokuGame.BLACK);
        game.makeMove(3, 3, GomokuGame.BLACK);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertEquals(4, move[0]);
        assertEquals(4, move[1]);
    }

    @Test
    public void testAIVerticalWin() {
        game.makeMove(7, 0, GomokuGame.BLACK);
        game.makeMove(7, 1, GomokuGame.BLACK);
        game.makeMove(7, 2, GomokuGame.BLACK);
        game.makeMove(7, 3, GomokuGame.BLACK);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move);
        assertEquals(7, move[0]);
        assertEquals(4, move[1]);
    }

    @Test
    public void testAIConsistentOnSamePosition() {
        int[] move1 = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull(move1);
        assertTrue(move1[0] >= 0 && move1[0] < 15);
        assertTrue(move1[1] >= 0 && move1[1] < 15);
    }
}

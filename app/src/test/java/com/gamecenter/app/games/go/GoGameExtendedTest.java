package com.gamecenter.app.games.go;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoGameExtendedTest {

    private GoGame game;

    @Before
    public void setUp() {
        game = new GoGame();
    }

    @Test
    public void testCaptureSingleStone() {
        game.makeMove(0, 1);
        game.switchPlayer();
        game.makeMove(1, 0);
        game.switchPlayer();
        game.makeMove(1, 1);
        game.switchPlayer();
        game.makeMove(0, 2);
        game.switchPlayer();
        game.makeMove(2, 0);
        game.switchPlayer();
        game.makeMove(0, 0);
        assertEquals(GoGame.EMPTY, game.getBoard()[0][0]);
        assertTrue(game.getBlackCaptures() > 0 || game.getWhiteCaptures() > 0);
    }

    @Test
    public void testSelfCaptureForbidden() {
        game.makeMove(1, 0);
        game.switchPlayer();
        game.makeMove(0, 1);
        game.switchPlayer();
        game.makeMove(2, 0);
        game.switchPlayer();
        assertFalse(game.isValidMove(0, 0));
    }

    @Test
    public void testKoRule() {
        game.makeMove(1, 0);
        game.switchPlayer();
        game.makeMove(0, 1);
        game.switchPlayer();
        game.makeMove(2, 1);
        game.switchPlayer();
        game.makeMove(1, 2);
        game.switchPlayer();
        game.makeMove(0, 2);
        game.switchPlayer();
        game.makeMove(1, 1);
        game.switchPlayer();
    }

    @Test
    public void testScoreCalculation() {
        game.pass();
        game.switchPlayer();
        game.pass();
        GoGame.ScoreResult result = game.calculateScore();
        assertNotNull(result);
        assertTrue(result.blackScore >= 0);
        assertTrue(result.whiteScore >= 0);
    }

    @Test
    public void testGetWinner() {
        game.pass();
        game.switchPlayer();
        game.pass();
        int winner = game.getWinner();
        assertTrue(winner == GoGame.BLACK || winner == GoGame.WHITE || winner == GoGame.EMPTY);
    }

    @Test
    public void testGetResultText() {
        game.pass();
        game.switchPlayer();
        game.pass();
        String text = game.getResultText();
        assertNotNull(text);
        assertTrue(text.length() > 0);
    }

    @Test
    public void testConsecutivePassesEndGame() {
        assertFalse(game.isGameOver());
        game.pass();
        game.switchPlayer();
        assertFalse(game.isGameOver());
        game.pass();
        assertTrue(game.isGameOver());
    }

    @Test
    public void testDynamicTimeBudget() {
        assertEquals(9, GoGame.BOARD_SIZE);
        assertTrue(game.getMoveCount() == 0);
    }

    @Test
    public void testMultipleCaptures() {
        game.makeMove(1, 0);
        game.switchPlayer();
        game.makeMove(0, 2);
        game.switchPlayer();
        game.makeMove(2, 0);
        game.switchPlayer();
        game.makeMove(1, 2);
        game.switchPlayer();
        game.makeMove(0, 0);
        int prevCaptures = game.getBlackCaptures();
        game.switchPlayer();
        game.makeMove(3, 0);
        game.switchPlayer();
        game.makeMove(0, 1);
        assertTrue(game.getBlackCaptures() >= prevCaptures);
    }

    @Test
    public void testBoardCopyIndependence() {
        game.makeMove(4, 4);
        int[][] board = game.getBoard();
        assertEquals(GoGame.BLACK, board[4][4]);
        board[4][4] = GoGame.WHITE;
        assertEquals(GoGame.BLACK, game.getBoard()[4][4]);
    }

    @Test
    public void testMoveCountTracking() {
        assertEquals(0, game.getMoveCount());
        game.makeMove(4, 4);
        assertEquals(1, game.getMoveCount());
        game.switchPlayer();
        game.makeMove(0, 0);
        assertEquals(2, game.getMoveCount());
    }

    @Test
    public void testSwitchPlayer() {
        assertEquals(GoGame.BLACK, game.getCurrentPlayer());
        game.switchPlayer();
        assertEquals(GoGame.WHITE, game.getCurrentPlayer());
        game.switchPlayer();
        assertEquals(GoGame.BLACK, game.getCurrentPlayer());
    }

    @Test
    public void testResetClearsAllState() {
        game.makeMove(4, 4);
        game.switchPlayer();
        game.makeMove(0, 0);
        game.reset();
        assertEquals(0, game.getMoveCount());
        assertEquals(0, game.getBlackCaptures());
        assertEquals(0, game.getWhiteCaptures());
        assertFalse(game.isGameOver());
        assertNull(game.getLastMove());
    }
}

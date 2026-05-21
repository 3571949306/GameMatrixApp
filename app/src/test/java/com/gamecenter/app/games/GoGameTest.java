package com.gamecenter.app.games.go;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoGameTest {

    private GoGame game;

    @Before
    public void setUp() {
        game = new GoGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertEquals(GoGame.BLACK, game.getCurrentPlayer());
        assertEquals(0, game.getBlackCaptures());
        assertEquals(0, game.getWhiteCaptures());
        assertNull(game.getLastMove());
    }

    @Test
    public void testValidMove() {
        assertTrue(game.isValidMove(0, 0));
        assertTrue(game.isValidMove(4, 4));
        assertTrue(game.isValidMove(8, 8));
    }

    @Test
    public void testInvalidMove() {
        assertFalse(game.isValidMove(-1, 0));
        assertFalse(game.isValidMove(0, -1));
        assertFalse(game.isValidMove(9, 0));
        assertFalse(game.isValidMove(0, 9));
    }

    @Test
    public void testMakeMove() {
        GoGame.MoveRecord record = game.makeMove(4, 4);
        assertNotNull(record);
        assertEquals(4, record.x);
        assertEquals(4, record.y);
        assertEquals(GoGame.BLACK, record.player);
        
        int[][] board = game.getBoard();
        assertEquals(GoGame.BLACK, board[4][4]);
    }

    @Test
    public void testCannotMoveOnOccupied() {
        game.makeMove(4, 4);
        assertFalse(game.isValidMove(4, 4));
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
    public void testPass() {
        game.pass();
        assertNull(game.getLastMove());
    }

    @Test
    public void testGameOverAfterTwoPasses() {
        game.pass();
        assertFalse(game.isGameOver());
        game.pass();
        assertTrue(game.isGameOver());
    }

    @Test
    public void testReset() {
        game.makeMove(4, 4);
        game.pass();
        game.reset();
        
        assertFalse(game.isGameOver());
        assertEquals(GoGame.BLACK, game.getCurrentPlayer());
        assertEquals(0, game.getBlackCaptures());
        assertEquals(0, game.getWhiteCaptures());
        assertNull(game.getLastMove());
    }

    @Test
    public void testSetLastMove() {
        game.setLastMove(3, 5);
        int[] lastMove = game.getLastMove();
        assertNotNull(lastMove);
        assertEquals(3, lastMove[0]);
        assertEquals(5, lastMove[1]);
    }

    @Test
    public void testClearLastMove() {
        game.setLastMove(3, 5);
        game.clearLastMove();
        assertNull(game.getLastMove());
    }

    @Test
    public void testSetGameOver() {
        assertFalse(game.isGameOver());
        game.setGameOver(true);
        assertTrue(game.isGameOver());
    }

    @Test
    public void testBoardSize() {
        assertEquals(9, GoGame.BOARD_SIZE);
    }

    @Test
    public void testConsecutivePassesEndGameAndCalculateWinner() {
        game.pass();
        game.switchPlayer();
        game.pass();

        assertTrue(game.isGameOver());
        assertEquals(GoGame.WHITE, game.getWinner());
        assertTrue(game.getResultText().contains("白"));
    }
}

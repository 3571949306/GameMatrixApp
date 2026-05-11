package com.gamecenter.app.games.gomoku;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GomokuGameTest {

    private GomokuGame game;

    @Before
    public void setUp() {
        game = new GomokuGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertNull(game.getWinner());
        assertEquals(GomokuGame.BLACK, game.getCurrentPlayer());
        assertEquals(0, game.getMoveCount());
    }

    @Test
    public void testValidMove() {
        assertTrue(game.isValidMove(7, 7));
        game.makeMove(7, 7, GomokuGame.BLACK);
        assertFalse(game.isValidMove(7, 7)); // 已经有棋子了
    }

    @Test
    public void testInvalidMove() {
        assertFalse(game.isValidMove(-1, 0));
        assertFalse(game.isValidMove(0, -1));
        assertFalse(game.isValidMove(15, 0));
        assertFalse(game.isValidMove(0, 15));
    }

    @Test
    public void testSwitchPlayer() {
        assertEquals(GomokuGame.BLACK, game.getCurrentPlayer());
        game.switchPlayer();
        assertEquals(GomokuGame.WHITE, game.getCurrentPlayer());
        game.switchPlayer();
        assertEquals(GomokuGame.BLACK, game.getCurrentPlayer());
    }

    @Test
    public void testHorizontalWin() {
        // 黑方连成5子横线
        game.makeMove(0, 7, GomokuGame.BLACK);
        game.makeMove(1, 7, GomokuGame.BLACK);
        game.makeMove(2, 7, GomokuGame.BLACK);
        game.makeMove(3, 7, GomokuGame.BLACK);
        game.makeMove(4, 7, GomokuGame.BLACK);
        
        assertTrue(game.checkGameOver());
        assertTrue(game.isGameOver());
        assertEquals(Integer.valueOf(GomokuGame.BLACK), game.getWinner());
    }

    @Test
    public void testVerticalWin() {
        // 白方连成5子竖线
        game.makeMove(7, 0, GomokuGame.WHITE);
        game.makeMove(7, 1, GomokuGame.WHITE);
        game.makeMove(7, 2, GomokuGame.WHITE);
        game.makeMove(7, 3, GomokuGame.WHITE);
        game.makeMove(7, 4, GomokuGame.WHITE);
        
        assertTrue(game.checkGameOver());
        assertTrue(game.isGameOver());
        assertEquals(Integer.valueOf(GomokuGame.WHITE), game.getWinner());
    }

    @Test
    public void testDiagonalWin() {
        // 黑方连成5子斜线
        game.makeMove(0, 0, GomokuGame.BLACK);
        game.makeMove(1, 1, GomokuGame.BLACK);
        game.makeMove(2, 2, GomokuGame.BLACK);
        game.makeMove(3, 3, GomokuGame.BLACK);
        game.makeMove(4, 4, GomokuGame.BLACK);
        
        assertTrue(game.checkGameOver());
        assertTrue(game.isGameOver());
        assertEquals(Integer.valueOf(GomokuGame.BLACK), game.getWinner());
    }

    @Test
    public void testAntiDiagonalWin() {
        // 白方连成5子反斜线
        game.makeMove(14, 0, GomokuGame.WHITE);
        game.makeMove(13, 1, GomokuGame.WHITE);
        game.makeMove(12, 2, GomokuGame.WHITE);
        game.makeMove(11, 3, GomokuGame.WHITE);
        game.makeMove(10, 4, GomokuGame.WHITE);
        
        assertTrue(game.checkGameOver());
        assertTrue(game.isGameOver());
        assertEquals(Integer.valueOf(GomokuGame.WHITE), game.getWinner());
    }

    @Test
    public void testNoWinYet() {
        // 只有4子，还没赢
        game.makeMove(0, 7, GomokuGame.BLACK);
        game.makeMove(1, 7, GomokuGame.BLACK);
        game.makeMove(2, 7, GomokuGame.BLACK);
        game.makeMove(3, 7, GomokuGame.BLACK);
        
        assertFalse(game.checkGameOver());
        assertFalse(game.isGameOver());
        assertNull(game.getWinner());
    }

    @Test
    public void testReset() {
        game.makeMove(7, 7, GomokuGame.BLACK);
        game.switchPlayer();
        game.reset();
        
        assertFalse(game.isGameOver());
        assertNull(game.getWinner());
        assertEquals(GomokuGame.BLACK, game.getCurrentPlayer());
        assertEquals(0, game.getMoveCount());
        assertNull(game.getLastMove());
    }

    @Test
    public void testSetGameOver() {
        game.setGameOver(GomokuGame.WHITE);
        assertTrue(game.isGameOver());
        assertEquals(Integer.valueOf(GomokuGame.WHITE), game.getWinner());
    }

    @Test
    public void testSetCurrentPlayer() {
        game.setCurrentPlayer(GomokuGame.WHITE);
        assertEquals(GomokuGame.WHITE, game.getCurrentPlayer());
    }
}

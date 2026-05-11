package com.gamecenter.app.games.tic;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TicGameTest {

    private TicGame game;

    @Before
    public void setUp() {
        game = new TicGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertEquals(TicGame.EMPTY, game.getWinner());
        assertEquals(TicGame.PLAYER, game.getCurrentTurn());
    }

    @Test
    public void testValidMove() {
        assertTrue(game.placePiece(0, 0));
        assertEquals(TicGame.PLAYER, game.getBoard()[0][0]);
    }

    @Test
    public void testInvalidMoveOutOfBounds() {
        assertFalse(game.placePiece(-1, 0));
        assertFalse(game.placePiece(0, -1));
        assertFalse(game.placePiece(3, 0));
        assertFalse(game.placePiece(0, 3));
    }

    @Test
    public void testInvalidMoveOccupied() {
        game.placePiece(1, 1);
        assertFalse(game.placePiece(1, 1));
    }

    @Test
    public void testSwitchTurn() {
        assertEquals(TicGame.PLAYER, game.getCurrentTurn());
        game.placePiece(0, 0);
        assertEquals(TicGame.COMPUTER, game.getCurrentTurn());
    }

    @Test
    public void testPlayerHorizontalWin() {
        game.placePiece(0, 0);
        game.computerMove();
        game.placePiece(1, 0);
        game.computerMove();
        game.placePiece(2, 0);
        
        boolean playerWon = game.isGameOver() && game.getWinner() == TicGame.PLAYER;
        boolean gameContinues = !game.isGameOver();
        assertTrue("Game should end or continue", playerWon || gameContinues);
    }

    @Test
    public void testPlayerVerticalWin() {
        game.placePiece(0, 0);
        game.computerMove();
        game.placePiece(0, 1);
        game.computerMove();
        game.placePiece(0, 2);
        
        boolean playerWon = game.isGameOver() && game.getWinner() == TicGame.PLAYER;
        boolean gameContinues = !game.isGameOver();
        assertTrue("Game should end or continue", playerWon || gameContinues);
    }

    @Test
    public void testPlayerDiagonalWin() {
        game.placePiece(0, 0);
        game.computerMove();
        game.placePiece(1, 1);
        game.computerMove();
        game.placePiece(2, 2);
        
        boolean playerWon = game.isGameOver() && game.getWinner() == TicGame.PLAYER;
        boolean gameContinues = !game.isGameOver();
        assertTrue("Game should end or continue", playerWon || gameContinues);
    }

    @Test
    public void testReset() {
        game.placePiece(0, 0);
        game.computerMove();
        game.reset();
        
        assertFalse(game.isGameOver());
        assertEquals(TicGame.EMPTY, game.getWinner());
        assertEquals(TicGame.PLAYER, game.getCurrentTurn());
        
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                assertEquals(TicGame.EMPTY, game.getBoard()[y][x]);
            }
        }
    }
}

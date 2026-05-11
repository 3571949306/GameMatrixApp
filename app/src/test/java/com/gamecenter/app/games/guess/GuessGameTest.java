package com.gamecenter.app.games.guess;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuessGameTest {

    private GuessGame game;

    @Before
    public void setUp() {
        game = new GuessGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertEquals(0, game.getAttempts());
        assertEquals(1, game.getMinRange());
        assertEquals(GuessGame.MEDIUM, game.getDifficulty());
    }

    @Test
    public void testTargetNumberInRange() {
        int target = game.getTargetNumber();
        assertTrue(target >= 1 && target <= 100);
    }

    @Test
    public void testMakeGuessTooLow() {
        String hint = game.makeGuess(1);
        assertNotNull(hint);
        assertEquals(1, game.getAttempts());
    }

    @Test
    public void testMakeGuessTooHigh() {
        String hint = game.makeGuess(100);
        assertNotNull(hint);
        assertEquals(1, game.getAttempts());
    }

    @Test
    public void testMakeGuessCorrect() {
        int target = game.getTargetNumber();
        String hint = game.makeGuess(target);
        assertTrue(hint.contains("猜对了"));
        assertTrue(game.isGameOver());
    }

    @Test
    public void testMakeGuessInvalid() {
        String hint = game.makeGuess(0);
        assertEquals(0, game.getAttempts());
        
        hint = game.makeGuess(101);
        assertEquals(0, game.getAttempts());
    }

    @Test
    public void testSetDifficulty() {
        game.setDifficulty(GuessGame.EASY);
        assertEquals(GuessGame.EASY, game.getDifficulty());
        assertEquals(50, game.getMaxRange());
        
        game.setDifficulty(GuessGame.HARD);
        assertEquals(GuessGame.HARD, game.getDifficulty());
        assertEquals(500, game.getMaxRange());
    }

    @Test
    public void testReset() {
        game.makeGuess(50);
        game.reset();
        
        assertFalse(game.isGameOver());
        assertEquals(0, game.getAttempts());
        assertEquals(-1, game.getLastGuess());
    }

    @Test
    public void testGetDifficultyName() {
        assertNotNull(game.getDifficultyName());
        assertFalse(game.getDifficultyName().isEmpty());
    }
}

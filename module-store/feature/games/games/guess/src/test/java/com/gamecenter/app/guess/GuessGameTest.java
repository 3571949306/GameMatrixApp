package com.gamecenter.app.guess;

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
    public void testDifficultyRanges() {
        assertEquals(GuessGame.DIFFICULTY_EASY, game.getDifficulty());
        assertEquals(100, game.getCurrentMax());

        game.setDifficulty(GuessGame.DIFFICULTY_MEDIUM);
        assertEquals(500, game.getCurrentMax());

        game.setDifficulty(GuessGame.DIFFICULTY_HARD);
        assertEquals(1000, game.getCurrentMax());
    }

    @Test
    public void testRangeNarrowingAndBinarySearch() {
        game.setDifficulty(GuessGame.DIFFICULTY_EASY);
        game.setTargetNumberForTest(42);

        assertEquals(0, game.getCurrentLow());
        assertEquals(100, game.getCurrentHigh());
        assertEquals(50, game.getOptimalBinaryMid());

        // Guess 50 -> TOO_BIG -> High should become 49
        GuessGame.GuessResult res1 = game.makeGuess(50);
        assertEquals(GuessGame.GuessResult.TOO_BIG, res1);
        assertEquals(0, game.getCurrentLow());
        assertEquals(49, game.getCurrentHigh());
        assertEquals(24, game.getOptimalBinaryMid());

        // Guess 24 -> TOO_SMALL -> Low should become 25
        GuessGame.GuessResult res2 = game.makeGuess(24);
        assertEquals(GuessGame.GuessResult.TOO_SMALL, res2);
        assertEquals(25, game.getCurrentLow());
        assertEquals(49, game.getCurrentHigh());
        assertEquals(37, game.getOptimalBinaryMid());

        // Guess 42 -> CORRECT
        GuessGame.GuessResult res3 = game.makeGuess(42);
        assertEquals(GuessGame.GuessResult.CORRECT, res3);
        assertTrue(game.isWin());
        assertFalse(game.isGameActive());
        assertEquals(3, game.getAttempts());
        assertTrue(game.getScore() > 0);
    }

    @Test
    public void testUndoLastGuess() {
        game.setDifficulty(GuessGame.DIFFICULTY_EASY);
        game.setTargetNumberForTest(60);

        game.makeGuess(50); // too small -> [51, 100]
        assertEquals(51, game.getCurrentLow());
        assertEquals(100, game.getCurrentHigh());
        assertTrue(game.canUndo());

        boolean undone = game.undoLastGuess();
        assertTrue(undone);
        assertEquals(0, game.getCurrentLow());
        assertEquals(100, game.getCurrentHigh());
        assertEquals(0, game.getAttempts());
        assertFalse(game.canUndo());
    }

    @Test
    public void testOutOfBounds() {
        game.setDifficulty(GuessGame.DIFFICULTY_EASY);
        game.setTargetNumberForTest(50);

        assertEquals(GuessGame.GuessResult.OUT_OF_BOUNDS, game.makeGuess(-1));
        assertEquals(GuessGame.GuessResult.OUT_OF_BOUNDS, game.makeGuess(101));
    }
}

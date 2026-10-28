package com.gamecenter.app.dice;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DiceGameTest {

    private DiceGame game;

    @Before
    public void setUp() {
        game = new DiceGame();
    }

    @Test
    public void testDifficultySettings() {
        assertEquals(DiceGame.DIFFICULTY_EASY, game.getDifficulty());
        assertEquals(2, game.getDiceCount());

        game.setDifficulty(DiceGame.DIFFICULTY_MEDIUM);
        assertEquals(DiceGame.DIFFICULTY_MEDIUM, game.getDifficulty());
        assertEquals(3, game.getDiceCount());

        game.setDifficulty(DiceGame.DIFFICULTY_HARD);
        assertEquals(DiceGame.DIFFICULTY_HARD, game.getDifficulty());
        assertEquals(3, game.getDiceCount());
    }

    @Test
    public void testRollAndResolve() {
        game.roll();
        assertTrue(game.isRoundActive());
        assertEquals(game.getDiceCount(), game.getPlayerDice().length);
        assertEquals(game.getDiceCount(), game.getAiDice().length);

        // Test forced hands
        game.setDiceForTest(new int[]{6, 5}, new int[]{3, 2}, true);
        assertEquals(11, game.getPlayerTotal());
        assertEquals(5, game.getAiTotal());

        DiceGame.RoundResult result = game.resolveRound();
        assertEquals(DiceGame.RoundResult.PLAYER_WIN, result);
        assertEquals(1, game.getPlayerWins());
        assertEquals(1, game.getWinStreak());
        assertEquals(11, game.getCurrentScore());
    }

    @Test
    public void testDoubleUpMultiplier() {
        game.setDiceForTest(new int[]{5, 5}, new int[]{2, 2}, true);
        game.setDoubleUp(true);

        DiceGame.RoundResult result = game.resolveRound();
        assertEquals(DiceGame.RoundResult.PLAYER_WIN, result);
        // Player total 10 * 2 = 20
        assertEquals(20, game.getCurrentScore());
    }

    @Test
    public void testLeopardDetection() {
        assertTrue(DiceGame.isLeopard(new int[]{6, 6, 6}));
        assertTrue(DiceGame.isLeopard(new int[]{1, 1}));
        assertFalse(DiceGame.isLeopard(new int[]{6, 5, 6}));
    }

    @Test
    public void testProbabilityHint() {
        game.setDiceForTest(new int[]{6, 6}, new int[]{1, 1}, true);
        String hint = game.getProbabilityHint();
        assertTrue(hint.contains("加倍"));
    }

    @Test
    public void testUndoRoll() {
        game.setDiceForTest(new int[]{2, 3}, new int[]{4, 5}, true);
        game.roll(); // roll new
        assertTrue(game.canUndo());

        boolean undone = game.undoLastRoll();
        assertTrue(undone);
        assertEquals(5, game.getPlayerTotal());
    }
}

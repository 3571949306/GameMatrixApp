package com.gamecenter.app.rock;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class RockGameTest {

    private RockGame game;

    @Before
    public void setUp() {
        game = new RockGame();
        game.reset(RockGame.DIFFICULTY_MEDIUM);
    }

    @Test
    public void testInitialState() {
        assertEquals(0, game.getTotalRounds());
        assertEquals(0, game.getPlayerWins());
        assertEquals(0, game.getAiWins());
        assertEquals(0, game.getDraws());
        assertEquals(0, game.getWinStreak());
        assertEquals(0, game.getMaxWinStreak());
        assertEquals(RockGame.DIFFICULTY_MEDIUM, game.getDifficulty());
        assertFalse(game.canUndo());
    }

    @Test
    public void testCounterChoice() {
        assertEquals(RockGame.PAPER, RockGame.getCounterChoice(RockGame.ROCK));
        assertEquals(RockGame.ROCK, RockGame.getCounterChoice(RockGame.SCISSORS));
        assertEquals(RockGame.SCISSORS, RockGame.getCounterChoice(RockGame.PAPER));
    }

    @Test
    public void testPlayAndUndo() {
        int[] result = game.play(RockGame.ROCK);
        assertNotNull(result);
        assertEquals(2, result.length);
        assertTrue(result[0] >= 0 && result[0] <= 2);
        assertTrue(result[1] >= -1 && result[1] <= 1);

        assertEquals(1, game.getTotalRounds());
        assertTrue(game.canUndo());

        boolean undone = game.undo();
        assertTrue(undone);
        assertEquals(0, game.getTotalRounds());
        assertFalse(game.canUndo());
    }

    @Test
    public void testDifficultyClamping() {
        game.reset(-1);
        assertEquals(RockGame.DIFFICULTY_EASY, game.getDifficulty());

        game.reset(10);
        assertEquals(RockGame.DIFFICULTY_HARD, game.getDifficulty());

        game.reset(RockGame.DIFFICULTY_MEDIUM);
        assertEquals(RockGame.DIFFICULTY_MEDIUM, game.getDifficulty());
    }
}

package com.gamecenter.app.whack;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class WhackGameTest {

    private WhackGame game;

    @Before
    public void setUp() {
        game = new WhackGame();
    }

    @Test
    public void testDifficultySettings() {
        game.setDifficulty(WhackGame.DIFFICULTY_EASY);
        game.reset();
        assertEquals(1800L, game.getMoleIntervalMs());

        game.setDifficulty(WhackGame.DIFFICULTY_HARD);
        game.reset();
        assertEquals(1000L, game.getMoleIntervalMs());

        game.setDifficulty(WhackGame.DIFFICULTY_NORMAL);
        game.reset();
        assertEquals(1400L, game.getMoleIntervalMs());
    }

    @Test
    public void testHitAndCombo() {
        game.reset();
        game.nextMolePos();
        assertTrue(game.isMoleVisible());

        int gain1 = game.hitMole();
        assertEquals(11, gain1); // 10 + 1
        assertEquals(1, game.getConsecutiveHits());
        assertEquals(1, game.getMaxCombo());
        assertEquals(11, game.getScore());
        assertFalse(game.isMoleVisible());

        game.nextMolePos();
        int gain2 = game.hitMole();
        assertEquals(12, gain2); // 10 + 2
        assertEquals(2, game.getConsecutiveHits());
        assertEquals(2, game.getMaxCombo());
        assertEquals(23, game.getScore());

        // Miss resets combo
        game.missMole();
        assertEquals(0, game.getConsecutiveHits());
        assertEquals(2, game.getMaxCombo());
    }

    @Test
    public void testTickDecay() {
        game.setDifficulty(WhackGame.DIFFICULTY_NORMAL);
        game.reset();
        long initial = game.getMoleIntervalMs();

        for (int i = 0; i < 10; i++) {
            game.onTickSecond();
        }
        assertEquals(WhackGame.GAME_DURATION_SEC - 10, game.getTimeRemaining());
        assertTrue(game.getMoleIntervalMs() < initial);
    }

    @Test
    public void testEndGame() {
        game.reset();
        assertTrue(game.isGameActive());
        game.endGame();
        assertFalse(game.isGameActive());
    }
}

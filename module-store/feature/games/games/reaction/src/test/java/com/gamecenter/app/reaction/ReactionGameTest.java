package com.gamecenter.app.reaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ReactionGameTest {

    private ReactionGame game;

    @Before
    public void setUp() {
        game = new ReactionGame();
    }

    @Test
    public void testInitialState() {
        assertEquals(ReactionGame.DIFFICULTY_STANDARD, game.getDifficulty());
        assertEquals(0, game.getCurrentRound());
        assertEquals(0, game.getTotalRounds());
        assertFalse(game.isWaitingForGreen());
        assertFalse(game.isGreenShown());
        assertFalse(game.isRunning());
    }

    @Test
    public void testDifficultySettings() {
        game.setDifficulty(ReactionGame.DIFFICULTY_CASUAL);
        assertEquals(ReactionGame.DIFFICULTY_CASUAL, game.getDifficulty());
        int delayCasual = game.computeDelayMs();
        assertTrue("Casual delay should be at least 2500", delayCasual >= 2500);

        game.setDifficulty(ReactionGame.DIFFICULTY_EXTREME);
        assertEquals(ReactionGame.DIFFICULTY_EXTREME, game.getDifficulty());
        int delayExtreme = game.computeDelayMs();
        assertTrue("Extreme delay should be at least 900", delayExtreme >= 700);
    }

    @Test
    public void testRoundLifecycle() throws InterruptedException {
        game.startRound();
        assertTrue(game.isWaitingForGreen());
        assertTrue(game.isRunning());
        assertFalse(game.isGreenShown());

        game.markReady();
        assertTrue(game.isGreenShown());

        Thread.sleep(15);
        long hitMs = game.recordHit();
        assertTrue("Hit ms should be positive", hitMs > 0);
        assertEquals(1, game.getTotalRounds());
        assertEquals(hitMs, game.getLastReactionTimeMs());
        assertEquals(hitMs, game.getBestReactionTimeMs());
        assertFalse(game.isRunning());
        assertFalse(game.isWaitingForGreen());
        assertFalse(game.isGreenShown());
    }

    @Test
    public void testTooEarlyPenalty() {
        game.startRound();
        game.tooEarly();
        assertFalse(game.isWaitingForGreen());
        assertFalse(game.isRunning());
        assertFalse(game.isGreenShown());
        assertEquals(0, game.getFastCount());
    }

    @Test
    public void testRankTitleEvaluation() {
        assertNotNull(game.getRankTitle(180));
        assertTrue(game.getRankTitle(180).contains("神仙反应"));
        assertTrue(game.getRankTitle(250).contains("电竞职业"));
        assertTrue(game.getRankTitle(350).contains("敏捷先锋"));
        assertTrue(game.getRankTitle(450).contains("正常水平"));
        assertTrue(game.getRankTitle(600).contains("迟疑"));
    }

    @Test
    public void testResetStats() {
        game.startRound();
        game.markReady();
        game.recordHit();
        assertEquals(1, game.getTotalRounds());

        game.resetStats();
        assertEquals(0, game.getTotalRounds());
        assertEquals(0, game.getLastReactionTimeMs());
        assertEquals(Long.MAX_VALUE, game.getBestReactionTimeMs());
    }
}

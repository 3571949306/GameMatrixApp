package com.gamecenter.app.tiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class TilesGameTest {

    private TilesGame game;

    @Before
    public void setUp() {
        game = new TilesGame();
    }

    @Test
    public void testInitialClassicDifficulty() {
        assertEquals(TilesGame.DIFFICULTY_CLASSIC, game.getDifficulty());
        assertEquals(4, game.getRows());
        assertEquals(6, game.getCols());
        assertEquals(24, game.getTotalTiles());
        assertEquals(12, game.getPairCount());
        assertEquals(12, game.getRemainingPairs());
        assertEquals(0, game.getMoveCount());
        assertFalse(game.isGameComplete());
    }

    @Test
    public void testDifficultySwitching() {
        game.setDifficulty(TilesGame.DIFFICULTY_CASUAL);
        assertEquals(4, game.getRows());
        assertEquals(4, game.getCols());
        assertEquals(16, game.getTotalTiles());
        assertEquals(8, game.getPairCount());

        game.setDifficulty(TilesGame.DIFFICULTY_MASTER);
        assertEquals(6, game.getRows());
        assertEquals(6, game.getCols());
        assertEquals(36, game.getTotalTiles());
        assertEquals(18, game.getPairCount());
    }

    @Test
    public void testTileMatchingLifecycle() {
        int[] pair = game.findHintPair();
        assertNotNull("Hint pair should not be null initially", pair);
        assertEquals(2, pair.length);

        int idx1 = pair[0];
        int idx2 = pair[1];

        int res1 = game.onTileClick(idx1);
        assertEquals(TilesGame.RESULT_FIRST_SELECTED, res1);
        assertTrue(game.isTileRevealed(idx1));

        int res2 = game.onTileClick(idx2);
        assertEquals(TilesGame.RESULT_MATCH, res2);
        assertTrue(game.isTileMatched(idx1));
        assertTrue(game.isTileMatched(idx2));
        assertEquals(1, game.getMatchedPairs());
        assertEquals(11, game.getRemainingPairs());
        assertEquals(1, game.getMoveCount());
    }

    @Test
    public void testMismatchHandling() {
        // find two tiles with different values
        int idx1 = 0;
        int idx2 = 1;
        if (game.getTileValue(idx1) == game.getTileValue(idx2)) {
            idx2 = 2;
        }

        int res1 = game.onTileClick(idx1);
        assertEquals(TilesGame.RESULT_FIRST_SELECTED, res1);

        int res2 = game.onTileClick(idx2);
        assertEquals(TilesGame.RESULT_MISMATCH, res2);

        game.resolveMismatch();
        assertFalse(game.isTileRevealed(idx1));
        assertFalse(game.isTileRevealed(idx2));
        assertFalse(game.isTileMatched(idx1));
        assertFalse(game.isTileMatched(idx2));
    }

    @Test
    public void testScoreComputation() {
        int score = game.computeScore(30);
        assertTrue("Score should be positive", score > 0);
    }
}

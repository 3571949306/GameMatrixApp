package com.gamecenter.app.games.klotski;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class KlotskiGameTest {
    @Test
    public void initialBoard_hasHintAndSolutionPathToExit() {
        KlotskiGame game = new KlotskiGame();

        KlotskiGame.HintResult hint = game.getHint();
        List<int[]> path = game.getSolutionPath();

        assertNotNull(hint);
        assertNotNull(path);
        assertFalse(path.isEmpty());
        assertTrue(path.size() < 200);
        assertTrue(game.canMove(game.getBlocks().get(hint.blockId), hint.dx, hint.dy));
    }

    @Test
    public void followingHintsMovesCaoCaoToExit() {
        KlotskiGame game = new KlotskiGame();

        for (int i = 0; i < 200 && !game.isWon(); i++) {
            KlotskiGame.HintResult hint = game.getHint();
            assertNotNull("hint missing at step " + i, hint);
            assertTrue("illegal hint at step " + i,
                    game.moveBlock(game.getBlocks().get(hint.blockId), hint.dx, hint.dy));
        }

        assertTrue(game.isWon());
    }

    @Test
    public void restoreState_rejectsOverlappingBoard() {
        KlotskiGame game = new KlotskiGame();

        assertFalse(game.restoreState("0,1,0,1,0,3,0,0,2,3,2,1,2,1,3,2,3,0,4,3,4"));
    }
}

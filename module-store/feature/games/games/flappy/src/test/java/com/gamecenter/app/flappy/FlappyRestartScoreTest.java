package com.gamecenter.app.flappy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.gamecenter.app.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * One naturally generated Normal pipe is crossed using the real Handler and touches.
 * Only the public parent layout chooses a 280 px board: gap180 + bottom margin100.
 * The production random center formula therefore naturally yields 90 for any RNG output.
 * This is a bounded public-layout fixture, not a typical phone or human-input play test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w600dp-h900dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class FlappyRestartScoreTest {
    private FlappyFragmentTestHarness game;

    @Before
    public void setUp() {
        game = new FlappyFragmentTestHarness();
    }

    @After
    public void tearDown() {
        if (game != null) game.close();
    }

    @Test
    public void restartResetsVisibleScoreAfterANaturallyCrossedFirstPipe() throws Exception {
        game.sizeBoardBeforeLaunch(280);
        // Isolate score reset from the separate first-resume double-chain defect.
        // The real restart listener already removes all old gameLoop callbacks.
        game.restartThroughButton();
        assertEquals(180f, game.value("pipeGap"), 0f);
        assertTrue(game.pipes().isEmpty());
        assertEquals(game.fragment.getString(R.string.game_score_alt_format, 0), game.score.getText().toString());

        boolean crossed = false;
        for (int frame = 1; frame <= 500; frame++) {
            if ((frame - 1) % 31 == 0) game.tapBoard();
            FlappyFragmentTestHarness.advance(16);
            assertTrue("The legal periodic flight must remain alive at frame " + frame,
                    game.board.isGameRunning());
            assertEquals("The untouched production generator must make the natural gap center",
                    90f, game.pipes().get(0)[1], 0f);
            if (game.board.getScore() == 1) {
                crossed = true;
                break;
            }
            assertEquals("Score comes only from actually crossing the natural pipe", 0, game.board.getScore());
        }
        assertTrue("The first natural pipe must be crossed within 500 actual Handler frames", crossed);
        float[] firstPipe = game.pipes().get(0);
        assertTrue("The pipe's trailing edge has genuinely passed the bird center",
                firstPipe[0] + game.value("pipeWidth") < game.value("birdX"));
        assertEquals(game.fragment.getString(R.string.game_score_alt_format, 1), game.score.getText().toString());

        game.restartThroughButton();

        assertEquals("The new round's public score is already zero", 0, game.board.getScore());
        assertTrue("A real restart removed the preceding round's pipes", game.pipes().isEmpty());
        assertEquals("The visible score must reset immediately with the new round",
                game.fragment.getString(R.string.game_score_alt_format, 0), game.score.getText().toString());
    }
}

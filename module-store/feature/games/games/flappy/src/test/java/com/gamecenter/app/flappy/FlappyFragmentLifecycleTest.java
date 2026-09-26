package com.gamecenter.app.flappy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Real Fragment lifecycle and scheduled Handler updates; never calls View.update(). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w600dp-h900dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class FlappyFragmentLifecycleTest {
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
    public void firstFragmentResumeRunsOneNormalUpdatePerSixteenMilliseconds() throws Exception {
        game.launchAndAwaitFirstPipe();
        assertSingleLoopMovement();
    }

    @Test
    public void restartButtonKeepsOneLoopForTheNewRound() throws Exception {
        game.launchAndAwaitFirstPipe();
        FlappyFragmentTestHarness.advance(64);
        assertTrue(game.board.isGameRunning());
        game.restartThroughButton();
        game.launchAndAwaitFirstPipe();
        assertSingleLoopMovement();
    }

    @Test
    public void actualActivityPauseFreezesAndResumeRetainsOneLoop() throws Exception {
        game.launchAndAwaitFirstPipe();
        for (int cycle = 0; cycle < 2; cycle++) {
            game.pauseActivity();
            float pipeX = game.firstPipeX();
            float birdY = game.value("birdY");
            float velocity = game.value("birdVelocity");
            FlappyFragmentTestHarness.advance(160);
            assertEquals("A paused Activity must not move its existing pipe", pipeX, game.firstPipeX(), 0f);
            assertEquals("A paused Activity must not move its bird", birdY, game.value("birdY"), 0f);
            assertEquals("Background time must not accumulate gravity", velocity, game.value("birdVelocity"), 0f);
            assertTrue("Backgrounding preserves the active round", game.board.isGameRunning());

            game.resumeActivity();
            assertSingleLoopMovement();
        }
    }

    private void assertSingleLoopMovement() throws Exception {
        float beforeX = game.firstPipeX();
        assertEquals("The actual Fragment selected Normal's original speed", 3f, game.value("pipeSpeed"), 0f);
        FlappyFragmentTestHarness.advance(160);
        assertTrue("The timing window must still be a live pre-collision round", game.board.isGameRunning());
        assertEquals(0, game.board.getScore());
        float moved = beforeX - game.firstPipeX();
        assertEquals("160 ms must move the natural pipe by ten 3 px updates; duplicate chains move 60 px",
                30f, moved, 0.001f);
    }
}

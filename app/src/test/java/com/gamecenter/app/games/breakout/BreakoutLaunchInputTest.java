package com.gamecenter.app.games.breakout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Uses only the real View's startGame, complete MotionEvent gestures and update.
 * Reflection observes the real ball and paddle because no public entity snapshot
 * API exists; it never changes a field, random seed, listener, state or geometry.
 *
 * <p>A gesture beginning in READY must place the paddle at the clamped touch
 * position and launch from that paddle. ACTION_DOWN still launches immediately.
 * Subsequent MOVE events belong to PLAYING: they move the paddle without dragging
 * or relaunching the already flying ball. No orphan MOVE event or invented
 * READY-drag mode is used. These are View input regressions, not device evidence.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class BreakoutLaunchInputTest {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 640;
    private static final float EPSILON = 0.001f;

    private BreakoutView view;

    @Before
    public void setUp() {
        view = new BreakoutView(ApplicationProvider.getApplicationContext());
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        view.startGame(1);
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void leftEdgeTapLaunchesFromThePaddleAfterLeftClamping() throws Exception {
        assertTapLaunchesFromPaddle(20f, 0f);
    }

    @Test
    public void rightEdgeTapLaunchesFromThePaddleAfterRightClamping() throws Exception {
        float paddleWidth = number(view, "paddleWidth");
        assertTapLaunchesFromPaddle(WIDTH - 20f, WIDTH - paddleWidth);
    }

    @Test
    public void centreTapStillLaunchesFromTheOriginalCentredPaddle() throws Exception {
        float paddleWidth = number(view, "paddleWidth");
        assertTapLaunchesFromPaddle(WIDTH / 2f, (WIDTH - paddleWidth) / 2f);
    }

    @Test
    public void dragBeginningInReadyMovesOnlyThePaddleAfterItsInitialLaunch() throws Exception {
        assertReadyBallOnPaddle();
        long downTime = SystemClock.uptimeMillis();
        dispatch(downTime, MotionEvent.ACTION_DOWN, 20f);
        assertEquals("ACTION_DOWN launches immediately, without waiting for UP",
                "PLAYING", state());
        Ball launched = new Ball(view);
        assertTrue(launched.vy < 0);

        dispatch(downTime, MotionEvent.ACTION_MOVE, WIDTH / 2f);
        dispatch(downTime, MotionEvent.ACTION_MOVE, WIDTH - 20f);
        dispatch(downTime, MotionEvent.ACTION_UP, WIDTH - 20f);

        Ball afterDrag = new Ball(view);
        assertSame(launched.entity, afterDrag.entity);
        assertEquals("The finger moves the paddle to the right edge",
                WIDTH - number(view, "paddleWidth"), number(view, "paddleX"), EPSILON);
        assertEquals("Input alone cannot teleport the already launched ball",
                launched.x, afterDrag.x, 0f);
        assertEquals(launched.y, afterDrag.y, 0f);
        assertEquals("Dragging cannot reroll horizontal launch velocity", launched.vx, afterDrag.vx, 0f);
        assertEquals("Dragging cannot reroll vertical launch velocity", launched.vy, afterDrag.vy, 0f);
        assertEquals("PLAYING", state());
        assertOneOrdinaryFlightStep(afterDrag);
    }

    private void assertTapLaunchesFromPaddle(float touchX, float expectedPaddleLeft) throws Exception {
        Ball ready = assertReadyBallOnPaddle();
        long downTime = SystemClock.uptimeMillis();
        dispatch(downTime, MotionEvent.ACTION_DOWN, touchX);
        dispatch(downTime, MotionEvent.ACTION_UP, touchX);

        Ball launched = new Ball(view);
        assertEquals("PLAYING", state());
        assertSame("Launching uses the existing ready ball", ready.entity, launched.entity);
        assertEquals("The paddle must first respect the touch and screen boundary",
                expectedPaddleLeft, number(view, "paddleX"), EPSILON);
        float expectedLaunchX = expectedPaddleLeft + number(view, "paddleWidth") / 2f;
        System.out.println("Public tap x=" + touchX + ", clamped paddle left="
                + number(view, "paddleX") + ", expected launch x=" + expectedLaunchX
                + ", observed launch x=" + launched.x);
        assertEquals("Launch must start above the paddle at its new clamped position",
                expectedLaunchX, launched.x, EPSILON);
        assertEquals("Input must preserve the ready ball's vertical launch position",
                ready.y, launched.y, 0f);
        assertTrue("The game's actual launch must point upward", launched.vy < 0);
        assertOneOrdinaryFlightStep(launched);
    }

    private Ball assertReadyBallOnPaddle() throws Exception {
        assertEquals("READY", state());
        Ball ready = new Ball(view);
        assertEquals(number(view, "paddleX") + number(view, "paddleWidth") / 2f,
                ready.x, EPSILON);
        assertEquals(number(view, "paddleY") - number(view, "ballRadius") - 2f,
                ready.y, EPSILON);
        assertEquals(0f, ready.vx, 0f);
        assertEquals(0f, ready.vy, 0f);
        return ready;
    }

    private void assertOneOrdinaryFlightStep(Ball before) throws Exception {
        // This first upward step is clear of walls, paddle and the original
        // level-one bricks. No clock, physics callback or velocity is replaced.
        view.update();
        Ball after = new Ball(view);
        assertSame(before.entity, after.entity);
        assertEquals(before.x + before.vx, after.x, EPSILON);
        assertEquals(before.y + before.vy, after.y, EPSILON);
        assertEquals(before.vx, after.vx, 0f);
        assertEquals(before.vy, after.vy, 0f);
        assertEquals(0, view.getScore());
        assertEquals(3, ((Number) read(view, "lives")).intValue());
        assertEquals("PLAYING", state());
    }

    private void dispatch(long downTime, int action, float x) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x, HEIGHT / 2f, 0);
        try {
            assertTrue("The real View consumes this gesture event", view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private String state() throws Exception {
        return read(view, "state").toString();
    }

    private static float number(Object owner, String name) throws Exception {
        return ((Number) read(owner, name)).floatValue();
    }

    private static Object read(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static final class Ball {
        final Object entity;
        final float x;
        final float y;
        final float vx;
        final float vy;

        Ball(BreakoutView view) throws Exception {
            List<?> balls = (List<?>) read(view, "balls");
            assertEquals("Observe the game's one actual initial ball", 1, balls.size());
            entity = balls.get(0);
            x = number(entity, "x");
            y = number(entity, "y");
            vx = number(entity, "vx");
            vy = number(entity, "vy");
        }
    }
}

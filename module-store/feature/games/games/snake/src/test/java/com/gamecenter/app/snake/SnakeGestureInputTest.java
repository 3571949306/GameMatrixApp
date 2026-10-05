package com.gamecenter.app.snake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Point;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;

/**
 * Real SnakeView, full gestures, explicit stale-event probes and actual Handler ticks.
 * No reflection, private state writes, fake detector, direct tick invocation or
 * runnable replacement. The only explicit game fixture moves the public food
 * Point to the legal empty cell (0,0), away from these short input paths.
 *
 * <p>One gesture may request only its first above-slop dominant direction.
 * Existing SnakeGame reversal checks remain authoritative. Accepted requests
 * survive pause; unfinished gestures require a fresh DOWN after interruption.
 * View attachment/removal uses a real Activity window, not a direct protected
 * onDetachedFromWindow call. This is Robolectric coverage, not device evidence.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w400dp-h700dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class SnakeGestureInputTest {
    private static final long TICK_MS = 140; // Existing default 200 * (1 - 0.5 * 0.6).
    private ActivityController<Activity> controller;
    private FrameLayout parent;
    private SnakeView view;
    private int slop;
    private long nextTickAt;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).create().start().resume();
        Activity activity = controller.get();
        parent = new FrameLayout(activity);
        view = new SnakeView(activity);
        parent.addView(view, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(parent);
        controller.visible();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(view.isAttachedToWindow());
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        assertTrue(slop > 0);
        restart();
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void slowMoveTurnsBeforeUpAndLaterMovementCannotRequestASecondDirection() {
        long downTime = down(200, 300);
        int minimumFling = ViewConfiguration.get(view.getContext()).getScaledMinimumFlingVelocity();
        assertTrue(minimumFling > 0);
        float distance = slop + 1f;
        long slowDuration = Math.max(220L, (long) Math.ceil(distance * 2000f / minimumFling));
        // Derive duration from ViewConfiguration: average speed stays at or below
        // half the platform minimum fling velocity on the configured device.
        // Real natural ticks continue while the finger moves slowly.
        advance(slowDuration);
        Point beforeTurn = head();
        assertFalse("The short fixture must not reach a wall", view.getGame().isGameOver());
        event(downTime, MotionEvent.ACTION_MOVE, 200, 300 - distance);
        nextTick();
        assertHead(beforeTurn.x, beforeTurn.y - 1, "Slow MOVE must turn before UP");

        // Still the same gesture: a larger subsequent leftward excursion must
        // not overwrite the first accepted upward request.
        event(downTime, MotionEvent.ACTION_MOVE, 100, 300 - distance);
        nextTick();
        assertHead(beforeTurn.x, beforeTurn.y - 2, "Only the first crossing counts");
        event(downTime, MotionEvent.ACTION_UP, 100, 300 - distance);
        nextTick();
        assertHead(beforeTurn.x, beforeTurn.y - 3, "UP cannot queue a second turn");
    }

    @Test
    public void ordinaryFastCompleteSwipeStillTurnsOnTheNextNaturalTick() {
        swipe(200, 300, 200, 200);
        nextTick();
        assertHead(10, 14, "Fast complete upward swipe");
        nextTick();
        assertHead(10, 13, "The real loop continues exactly once per period");
    }

    @Test
    public void shortJitterAndOppositeSwipeKeepHeadingButANewValidSwipeWorks() {
        long downTime = down(200, 300);
        event(downTime, MotionEvent.ACTION_MOVE, 200 + slop / 2f, 300 - slop / 2f);
        event(downTime, MotionEvent.ACTION_UP, 200 + slop / 2f, 300 - slop / 2f);
        nextTick();
        assertHead(11, 15, "Sub-slop jitter must not turn");
        swipe(200, 300, 100, 300);
        nextTick();
        assertHead(12, 15, "A 180-degree reverse remains rejected");
        swipe(200, 300, 200, 200);
        nextTick();
        assertHead(12, 14, "A rejected gesture cannot disable the next valid gesture");
    }

    @Test
    public void cancelDropsTheOldAnchorAndANewDownDefinesAFreshDirection() {
        long cancelled = down(200, 300);
        event(cancelled, MotionEvent.ACTION_CANCEL, 200, 300);
        // Late events from the cancelled stream must not acquire a new anchor.
        fastRemainder(cancelled, 200, 270, 200, 200);
        nextTick();
        assertHead(11, 15, "CANCEL invalidates unfinished input");

        // Relative to the new DOWN this is DOWN; relative to the discarded
        // (200,300) anchor it would instead be predominantly UP.
        swipe(100, 100, 100, 100 + slop * 3f);
        nextTick();
        assertHead(11, 16, "The new gesture must use its own DOWN coordinates");
    }

    @Test
    public void pauseStopAndRealWindowDetachRequireFreshDownAfterResumeOrRestart() {
        for (String interruption : new String[]{"pause", "stop", "detach"}) {
            restart();
            long unfinished = down(200, 300);
            Point before = head();
            if ("pause".equals(interruption)) view.pauseGame();
            else if ("stop".equals(interruption)) view.stopGame();
            else {
                parent.removeView(view);
                assertFalse(view.isAttachedToWindow());
            }
            advance(TICK_MS * 2);
            assertHead(before.x, before.y, interruption + " must stop real ticks");
            if ("stop".equals(interruption)) restart();
            else {
                if ("detach".equals(interruption)) {
                    parent.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    assertTrue(view.isAttachedToWindow());
                }
                view.resumeGame();
                nextTickAt = SystemClock.uptimeMillis() + TICK_MS;
            }
            // Fast old MOVE/UP events arrive after resume but without a fresh DOWN.
            // This catches stale GestureDetector/anchor state, not just slow input.
            fastRemainder(unfinished, 200, 270, 200, 200);
            nextTick();
            assertHead(11, 15, interruption + " must discard the unfinished old gesture");
            swipe(200, 300, 200, 200);
            nextTick();
            assertHead(11, 14, interruption + " must accept a new complete gesture");
            System.out.println("Real gesture interruption control passed: " + interruption);
        }

        // Preserve a request already accepted before pause; only unfinished
        // gesture input is discarded, not the game's pending valid direction.
        restart();
        long accepted = down(200, 300);
        event(accepted, MotionEvent.ACTION_MOVE, 200, 300 - slop - 1);
        view.pauseGame();
        advance(TICK_MS * 2);
        view.resumeGame();
        nextTickAt = SystemClock.uptimeMillis() + TICK_MS;
        nextTick();
        assertHead(10, 14, "An already accepted direction survives pause");
        event(accepted, MotionEvent.ACTION_UP, 200, 300 - slop - 1);
    }

    @Test
    public void gameOverDownImmediatelyRestartsWithoutTurningFromThatSameTouch() {
        advance(TICK_MS * 10);
        assertTrue("Natural rightward movement must hit the wall", view.getGame().isGameOver());
        long restartDown = down(200, 300);
        assertTrue(view.getGame().isRunning());
        assertFalse(view.getGame().isGameOver());
        assertHead(10, 15, "GameOver DOWN restarts immediately");
        moveFoodAway();
        nextTickAt = SystemClock.uptimeMillis() + TICK_MS;
        fastRemainder(restartDown, 200, 270, 200, 200);
        nextTick();
        assertHead(11, 15, "Restart DOWN must not also begin a turn gesture");
        swipe(200, 300, 200, 200);
        nextTick();
        assertHead(11, 14, "Fresh DOWN works after the restart touch");
    }

    private void restart() {
        view.startGame();
        moveFoodAway();
        nextTickAt = SystemClock.uptimeMillis() + TICK_MS;
        assertHead(10, 15, "Original three-cell starting position");
    }

    private void moveFoodAway() {
        // Explicit legal food-position fixture via the existing public Point API.
        for (Point cell : view.getGame().getSnake()) assertFalse(cell.equals(0, 0));
        view.getGame().getFood().set(0, 0);
    }

    private Point head() {
        return new Point(view.getGame().getSnake().get(0));
    }

    private void assertHead(int x, int y, String message) {
        Point head = head();
        assertEquals(message + ": x", x, head.x);
        assertEquals(message + ": y", y, head.y);
    }

    private long down(float x, float y) {
        long time = SystemClock.uptimeMillis();
        event(time, MotionEvent.ACTION_DOWN, x, y);
        return time;
    }

    private void swipe(float x1, float y1, float x2, float y2) {
        long time = down(x1, y1);
        advance(10);
        event(time, MotionEvent.ACTION_MOVE, (x1 + x2) / 2, (y1 + y2) / 2);
        advance(10);
        event(time, MotionEvent.ACTION_MOVE, x2, y2);
        advance(10);
        event(time, MotionEvent.ACTION_UP, x2, y2);
    }

    private void fastRemainder(long time, float x1, float y1, float x2, float y2) {
        event(time, MotionEvent.ACTION_MOVE, x1, y1);
        advance(10);
        event(time, MotionEvent.ACTION_MOVE, x2, y2);
        advance(10);
        event(time, MotionEvent.ACTION_UP, x2, y2);
    }

    private void event(long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            assertTrue(view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private void nextTick() {
        long now = SystemClock.uptimeMillis();
        while (nextTickAt <= now) nextTickAt += TICK_MS;
        advance(nextTickAt - now);
        nextTickAt += TICK_MS;
    }

    private void advance(long milliseconds) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));
    }
}

package com.gamecenter.app.klotski;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.graphics.PointF;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
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
import org.robolectric.shadows.ShadowChoreographer;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Existing public KlotskiGame / View API regression, independent of practice-level UI.
 * Real attached View dispatches MotionEvents and advances its own 120ms ValueAnimator.
 * Reflection only reads layout geometry and transient state; no private state writes,
 * animator seeking, direct callback invocation, fake game, or replacement Runnable.
 * This is Robolectric coverage, not system touch or device-rendering evidence.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w400dp-h700dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class KlotskiViewSetGameTest {
    private static final String CLASSIC_AFTER_SOLDIER_RIGHT =
            "1,1,0,0,0,3,0,0,2,3,2,1,2,1,3,2,3,1,4,3,4";
    // Legal complete board, loaded only through the pre-existing public restoreState.
    // Frozen from practice-4/board.csv; no new level class or Fragment API is required.
    private static final String FOUR_MOVE_HINT_FIXTURE =
            "0,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,1,2";

    private ActivityController<Activity> controller;
    private FrameLayout parent;
    private KlotskiView view;
    private KlotskiGame original;
    private int moveCallbacks;
    private int winCallbacks;
    private boolean framePausedBefore;
    private Duration frameDelayBefore;
    private float animatorScaleBefore;
    private boolean animationScaleConfigured;

    @Before
    public void setUp() throws Exception {
        // PAUSED Looper alone does not stop Robolectric's automatic vsync clock jumps.
        // Configure normal 16ms frames before constructing any animated View.
        framePausedBefore = ShadowChoreographer.isPaused();
        frameDelayBefore = ShadowChoreographer.getFrameDelay();
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        controller = Robolectric.buildActivity(Activity.class).create().start().resume();
        Activity activity = controller.get();
        animatorScaleBefore = ValueAnimator.getDurationScale();
        assertTrue(Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f));
        animationScaleConfigured = true;
        assertEquals("Fixture: standard system animation scale must be enabled", 1f,
                ValueAnimator.getDurationScale(), 0f);
        assertTrue(ValueAnimator.areAnimatorsEnabled());
        System.out.println("Klotski animation fixture configured: previousScale=" + animatorScaleBefore
                + " scale=" + ValueAnimator.getDurationScale() + " choreographerWasPaused=" + framePausedBefore
                + " frameDelay=" + ShadowChoreographer.getFrameDelay());
        parent = new FrameLayout(activity);
        original = new KlotskiGame();
        view = new KlotskiView(activity);
        view.setGame(original);
        view.setOnMoveListener(() -> moveCallbacks++);
        view.setOnWinListener(() -> winCallbacks++);
        parent.addView(view, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(parent);
        controller.visible();
        shadowOf(Looper.getMainLooper()).idle();
        advance(32);
        parent.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(580, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 400, 580);
        assertTrue(view.isAttachedToWindow());
        assertEquals(400, view.getWidth());
        assertEquals(580, view.getHeight());
        assertTrue(value("cellSize") > 0f);
        assertEquals(0, original.getMoves());
        assertFalse(original.isWon());
    }

    @After
    public void tearDown() {
        try {
            if (parent != null && view != null) parent.removeView(view);
            if (controller != null) controller.pause().stop().destroy();
        } finally {
            if (animationScaleConfigured) {
                Settings.Global.putFloat(controller.get().getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, animatorScaleBefore);
            }
            if (frameDelayBefore != null) {
                ShadowChoreographer.setFrameDelay(frameDelayBefore);
                ShadowChoreographer.setPaused(framePausedBefore);
            }
        }
    }

    @Test
    public void replacingGameDiscardsOldDownWithoutWritingEitherBoardAndAcceptsFreshDown()
            throws Exception {
        String originalState = original.serializeState();
        PointF oldStart = soldierEightCenter(original);
        long oldDown = down(oldStart);
        assertSame("The real DOWN must select the original board's block",
                original.getBlocks().get(8), read("draggingBlock"));

        KlotskiGame replacement = new KlotskiGame();
        String replacementState = replacement.serializeState();
        assertNotSame(original.getBlocks().get(8), replacement.getBlocks().get(8));
        view.setGame(replacement);
        // This stream has no DOWN on the replacement board.
        PointF oldEnd = toRight(oldStart);
        event(oldDown, MotionEvent.ACTION_MOVE, oldEnd);
        event(oldDown, MotionEvent.ACTION_UP, oldEnd);

        assertEquals("A stale MOVE must not increment the replacement board's moves",
                0, replacement.getMoves());
        assertEquals("No cell or step count of the new board may change", replacementState,
                replacement.serializeState());
        assertEquals("The discarded old Block reference must not be mutated", originalState,
                original.serializeState());
        assertEquals(0, moveCallbacks);
        assertEquals(0, winCallbacks);

        completeRightDrag(replacement);
        assertEquals("A fresh DOWN must move only the new board's soldier and add one move",
                CLASSIC_AFTER_SOLDIER_RIGHT, replacement.serializeState());
        assertEquals(1, replacement.getMoves());
        assertEquals(originalState, original.serializeState());
        assertEquals(1, moveCallbacks);
        assertEquals(0, winCallbacks);
        advance(160);
        assertEquals(CLASSIC_AFTER_SOLDIER_RIGHT, replacement.serializeState());
        assertEquals(1, moveCallbacks);
    }

    @Test
    public void replacingDuringRealAnimationClearsItAndOldFramesCannotEraseNewAnimation()
            throws Exception {
        PointF oldStart = soldierEightCenter(original);
        long oldDown = down(oldStart);
        event(oldDown, MotionEvent.ACTION_MOVE, toRight(oldStart));
        assertEquals(CLASSIC_AFTER_SOLDIER_RIGHT, original.serializeState());
        ValueAnimator oldAnimator = (ValueAnimator) read("currentAnimator");
        assertNotNull(oldAnimator);
        assertEquals(120L, oldAnimator.getDuration());
        logAnimation("old-before-48ms", oldAnimator);
        advance(48);
        logAnimation("old-after-48ms-before-setGame", oldAnimator);
        assertTrue("Fixture: the production animator must still be running", oldAnimator.isRunning());
        assertTrue("Fixture: real frames must have advanced, without seeking",
                oldAnimator.getCurrentPlayTime() >= 32L && oldAnimator.getCurrentPlayTime() < 120L);
        assertTrue("Fixture: the old block must still have a visible movement offset",
                value("animOffsetX") < 0f);
        String oldCompletedMove = original.serializeState();

        KlotskiGame replacement = new KlotskiGame();
        String replacementState = replacement.serializeState();
        view.setGame(replacement);
        assertFalse("setGame must cancel the still-running old movement animator", oldAnimator.isRunning());
        assertNull(read("currentAnimator"));
        assertNull(read("animatingBlock"));
        assertNull(read("draggingBlock"));
        assertFalse((Boolean) read("moveHandled"));
        assertEquals(0f, value("touchStartX"), 0f);
        assertEquals(0f, value("touchStartY"), 0f);
        assertEquals(0f, value("animOffsetX"), 0f);
        assertEquals(0f, value("animOffsetY"), 0f);
        event(oldDown, MotionEvent.ACTION_MOVE, toRight(oldStart));
        event(oldDown, MotionEvent.ACTION_UP, toRight(oldStart));
        assertEquals(replacementState, replacement.serializeState());
        assertEquals(oldCompletedMove, original.serializeState());
        assertEquals(1, moveCallbacks);

        // Do not wait out the old animation before starting the new gesture.
        completeRightDrag(replacement);
        assertEquals(CLASSIC_AFTER_SOLDIER_RIGHT, replacement.serializeState());
        assertEquals(2, moveCallbacks);
        ValueAnimator newAnimator = (ValueAnimator) read("currentAnimator");
        assertNotNull(newAnimator);
        assertNotSame(oldAnimator, newAnimator);
        Object newAnimatingBlock = read("animatingBlock");
        assertNotNull(newAnimatingBlock);

        // Old play time was >=32ms: 96ms crosses its former 120ms end, while the
        // newly started 120ms animation must remain active. No callback is invoked by test.
        advance(96);
        logAnimation("new-after-96ms-crossing-old-end", newAnimator);
        assertSame(newAnimator, read("currentAnimator"));
        assertTrue(newAnimator.isRunning());
        assertTrue(newAnimator.getCurrentPlayTime() > 0L && newAnimator.getCurrentPlayTime() < 120L);
        assertSame("No old completion may clear the new moving-block marker",
                newAnimatingBlock, read("animatingBlock"));
        assertTrue("No old completion may zero the new animation offset", value("animOffsetX") < 0f);
        assertFalse(oldAnimator.isRunning());
        assertEquals(CLASSIC_AFTER_SOLDIER_RIGHT, replacement.serializeState());
        assertEquals(oldCompletedMove, original.serializeState());
        assertEquals(2, moveCallbacks);
        assertEquals(0, winCallbacks);

        advance(64);
        assertFalse(newAnimator.isRunning());
        assertNull(read("animatingBlock"));
        assertEquals(0f, value("animOffsetX"), 0f);
        assertEquals(0f, value("animOffsetY"), 0f);
        assertEquals(CLASSIC_AFTER_SOLDIER_RIGHT, replacement.serializeState());
        assertEquals(2, moveCallbacks);
    }

    @Test
    public void replacingGameClearsARealSolverHintWithoutChangingEitherBoard() throws Exception {
        assertTrue(original.restoreState(FOUR_MOVE_HINT_FIXTURE));
        KlotskiGame.HintResult hint = original.getHint();
        assertNotNull("The legal public restore fixture must have a solver hint", hint);
        assertEquals(4, hint.totalSteps);
        view.showHint(hint);
        assertTrue((Boolean) read("showHint"));
        assertEquals(4, ((Number) read("hintTotalSteps")).intValue());
        assertTrue(Math.abs(value("hintArrowDx")) + Math.abs(value("hintArrowDy")) > 0f);
        String oldState = original.serializeState();
        KlotskiGame replacement = new KlotskiGame();
        String replacementState = replacement.serializeState();

        view.setGame(replacement);
        assertHintCleared();
        advance(160);
        assertHintCleared();
        assertEquals(oldState, original.serializeState());
        assertEquals(replacementState, replacement.serializeState());
        assertEquals(0, moveCallbacks);
        assertEquals(0, winCallbacks);
    }

    private void assertHintCleared() throws Exception {
        assertFalse("A hint belongs only to the board that produced it", (Boolean) read("showHint"));
        assertEquals(0, ((Number) read("hintTotalSteps")).intValue());
        for (String name : new String[]{"hintArrowX", "hintArrowY", "hintArrowDx", "hintArrowDy"}) {
            assertEquals(name + " must no longer retain the old hint geometry", 0f, value(name), 0f);
        }
    }

    private PointF soldierEightCenter(KlotskiGame game) throws Exception {
        KlotskiGame.Block block = game.getBlocks().get(8);
        assertEquals(8, block.id);
        assertEquals(0, block.x);
        assertEquals(4, block.y);
        assertTrue("The fixture uses an existing legal classic-board move", game.canMove(block, 1, 0));
        float cell = value("cellSize");
        return new PointF(value("offsetX") + (block.x + 0.5f) * cell,
                value("offsetY") + (block.y + 0.5f) * cell);
    }

    private PointF toRight(PointF start) throws Exception {
        return new PointF(start.x + value("cellSize") * 0.6f, start.y);
    }

    private void completeRightDrag(KlotskiGame game) throws Exception {
        PointF start = soldierEightCenter(game);
        PointF end = toRight(start);
        long downTime = down(start);
        event(downTime, MotionEvent.ACTION_MOVE, end);
        event(downTime, MotionEvent.ACTION_UP, end);
    }

    private long down(PointF point) {
        long time = SystemClock.uptimeMillis();
        event(time, MotionEvent.ACTION_DOWN, point);
        return time;
    }

    private void event(long downTime, int action, PointF point) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                point.x, point.y, 0);
        try {
            assertTrue("The attached real View must consume this MotionEvent", view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private Object read(String name) throws Exception {
        Field field = KlotskiView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }

    private float value(String name) throws Exception {
        return ((Number) read(name)).floatValue();
    }

    private void logAnimation(String label, ValueAnimator animator) throws Exception {
        System.out.println(label + " uptime=" + SystemClock.uptimeMillis()
                + " scale=" + ValueAnimator.getDurationScale() + " enabled=" + ValueAnimator.areAnimatorsEnabled()
                + " choreographerPaused=" + ShadowChoreographer.isPaused()
                + " playTime=" + animator.getCurrentPlayTime() + " duration=" + animator.getDuration()
                + " started=" + animator.isStarted() + " running=" + animator.isRunning()
                + " fraction=" + animator.getAnimatedFraction() + " offsetX=" + value("animOffsetX"));
    }

    private void advance(long milliseconds) {
        // Each clock step delivers a normal framework vsync through the main Looper.
        // A single large idleFor jump can deliver only one paused-vsync notification.
        long before = SystemClock.uptimeMillis();
        for (long remaining = milliseconds; remaining > 0L;) {
            long step = Math.min(16L, remaining);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step));
            remaining -= step;
        }
        assertEquals("Fixture: requested Looper time must not auto-drain extra animation frames",
                before + milliseconds, SystemClock.uptimeMillis());
    }
}

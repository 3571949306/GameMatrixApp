package com.gamecenter.app.games.breakout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Exercises the actual Activity, View touch handling and main-Looper game loop.
 *
 * <p>Two explicitly artificial endgame fixtures use private entity fields because
 * Breakout has no public ball, brick, life or snapshot API: a launched ball one
 * pixel before leaving the bottom on the last life, and the original level-one
 * board with only its bottom-centre brick alive. Fixture setup never invokes a
 * win/loss callback, changes the game state enum, replaces the Activity listener
 * or runnable, or calls update directly. The real scheduled update must lose the
 * ball or destroy the last brick and notify the real Activity.</p>
 *
 * <p>These are bounded artificial-endgame regressions, not naturally completed
 * full games or device/pixel evidence. Canvas text recording only checks which
 * restart/launch prompts the actual onDraw emits. Other private reads observe
 * entities; after setup all progress uses touch input and the real Handler clock.
 * Frame counts come from independent displacement / launch-velocity arithmetic
 * in a verified collision-free window, not from a mocked loop or scheduler count.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class BreakoutGameplayLifecycleTest {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 640;
    private static final long FRAME_MS = 16;
    private static final long FLIGHT_WINDOW_MS = 160;
    private static final float EPSILON = 0.02f;

    private ActivityController<BreakoutActivity> controller;
    private BreakoutActivity activity;
    private BreakoutView view;
    private boolean controllerPaused;

    @Before
    public void setUp() throws Exception {
        controller = Robolectric.buildActivity(BreakoutActivity.class);
        controller.create().start().resume();
        activity = controller.get();
        view = (BreakoutView) read(activity, "breakoutView");
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        // Drain only work due now. Running all future tasks would never terminate
        // while this real Activity schedules its next frame every 16 ms.
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.isGameRunning());
        assertEquals("READY", state());
        assertEquals(27, aliveBricks());
    }

    @After
    public void tearDown() {
        if (controller != null) {
            if (!controllerPaused) controller.pause();
            controller.stop().destroy();
        }
    }

    @Test
    public void gameOverScreenTapRestartsAndLaunchesABallThatActuallyMoves() throws Exception {
        tap();
        assertEquals("PLAYING", state());
        arrangeLastLifeAboutToLeaveBottom();

        advance(FRAME_MS);

        assertEquals("GAME_OVER", state());
        assertEquals(0, number(view, "lives").intValue());
        assertFalse("The real loss callback must end the Activity game", activity.isGameRunning());
        assertFalse(view.isGameRunning());
        assertDrawsText("点击重新开始");

        // Use the View's advertised restart action, then its advertised launch
        // action. No lifecycle resume or direct Activity start is used as a fix.
        tap();
        assertEquals("READY", state());
        assertEquals(3, number(view, "lives").intValue());
        assertEquals(1, view.getLevel());
        assertEquals(0, view.getScore());
        assertDrawsText("点击屏幕发射小球");
        tap();
        assertEquals("PLAYING", state());
        shadowOf(Looper.getMainLooper()).idle();
        Flight before = new Flight(view);
        assertTrue("A real launch must supply upward velocity", before.vy < 0);

        advance(FLIGHT_WINDOW_MS);

        Flight after = new Flight(view);
        System.out.println("Artificial last-life fixture: restart flight y=" + before.y
                + " -> " + after.y + ", launch vy=" + before.vy);
        assertSame("The launched ball must remain in this short safe window", before.ball, after.ball);
        assertTrue("Tapping restart and launch after a real GameOver must resume actual motion",
                after.y < before.y - 1);
        assertTrue("The restarted View and Activity must both be running", activity.isGameRunning());
        assertTrue(view.isGameRunning());
    }

    @Test
    public void lastBrickCollisionAdvancesAfter1500msWithExactlyOneFrameChain() throws Exception {
        tap();
        arrangeLastBrickAboutToBeHit();
        int scoreBeforeHit = view.getScore();
        assertEquals(1, aliveBricks());
        assertEquals("PLAYING", state());

        advance(FRAME_MS);

        assertEquals("A real collision must remove the last live brick", 0, aliveBricks());
        assertEquals("LEVEL_CLEAR", state());
        // Local game content: 15 for the destroyed brick plus 1 * 50 clear bonus.
        assertEquals(scoreBeforeHit + 65, view.getScore());
        assertEquals(1, view.getLevel());
        assertTrue(activity.isGameRunning());

        advance(1499);
        assertEquals("The completion screen must last the full 1500 ms", "LEVEL_CLEAR", state());
        assertEquals(1, view.getLevel());
        advance(1);

        assertEquals(2, view.getLevel());
        assertEquals("READY", state());
        assertEquals(27, aliveBricks());
        assertEquals(scoreBeforeHit + 65, view.getScore());
        assertEquals(3, number(view, "lives").intValue());
        tap();
        shadowOf(Looper.getMainLooper()).idle();

        assertSingleFrameChain("Artificial last-brick fixture, level two");
    }

    @Test
    public void repeatedActivityPauseAndResumeFreezesThenKeepsOneFrameChain() throws Exception {
        // Control case: no private state writes and no artificial endgame.
        tap();
        shadowOf(Looper.getMainLooper()).idle();
        assertSingleFrameChain("Natural launch before lifecycle changes");

        for (int cycle = 1; cycle <= 2; cycle++) {
            controller.pause();
            controllerPaused = true;
            assertTrue(activity.isGamePaused());
            assertEquals("PAUSED", state());
            Flight paused = new Flight(view);

            advance(320);

            Flight stillPaused = new Flight(view);
            assertSame(paused.ball, stillPaused.ball);
            assertEquals("Paused ball x must not advance", paused.x, stillPaused.x, 0f);
            assertEquals("Paused ball y must not advance", paused.y, stillPaused.y, 0f);
            controller.resume();
            controllerPaused = false;
            assertFalse(activity.isGamePaused());
            assertEquals("PLAYING", state());
            // Resume may schedule an immediate first frame. Observe a full
            // 160 ms interval after due-now work, avoiding a boundary extra frame.
            shadowOf(Looper.getMainLooper()).idle();
            assertSingleFrameChain("Natural launch after pause/resume " + cycle);
        }
    }

    @Test
    public void manualPauseSurvivesBackgroundAndForegroundUntilExplicitResume() throws Exception {
        tap();
        shadowOf(Looper.getMainLooper()).idle();
        // Call the actual game's manual pause control. BaseGameActivity must not
        // confuse this with an automatic lifecycle pause and resume it for us.
        activity.pauseGame();
        assertTrue(activity.isGamePaused());
        assertEquals("PAUSED", state());
        Flight manuallyPaused = new Flight(view);
        controller.pause();
        controllerPaused = true;
        advance(320);

        controller.resume();
        controllerPaused = false;
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue("Foregrounding must preserve a manual pause", activity.isGamePaused());
        assertEquals("PAUSED", state());
        advance(FLIGHT_WINDOW_MS);
        Flight stillPaused = new Flight(view);
        assertSame(manuallyPaused.ball, stillPaused.ball);
        assertEquals(manuallyPaused.x, stillPaused.x, 0f);
        assertEquals(manuallyPaused.y, stillPaused.y, 0f);

        activity.resumeGame();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(activity.isGamePaused());
        assertSingleFrameChain("Explicit resume after preserved manual pause");
    }

    @Test
    public void levelClearWaitCannotStartNextLevelInBackgroundAndResumesOneChain() throws Exception {
        tap();
        arrangeLastBrickAboutToBeHit();
        advance(FRAME_MS);
        assertEquals("LEVEL_CLEAR", state());
        assertEquals(0, aliveBricks());
        assertEquals(1, view.getLevel());
        int completedScore = view.getScore();
        Flight completedBall = new Flight(view);
        controller.pause();
        controllerPaused = true;
        assertTrue(activity.isGamePaused());

        // The original 1500 ms deadline expires while the real Activity is
        // paused. Neither generating level two nor advancing a ball is allowed.
        advance(2000);

        System.out.println("Artificial last-brick fixture after 2000 ms in background:"
                + " level=" + view.getLevel() + ", state=" + state());
        assertEquals("Do not create the next level in the background", 1, view.getLevel());
        assertEquals("LEVEL_CLEAR", state());
        assertEquals(0, aliveBricks());
        assertEquals(completedScore, view.getScore());
        Flight stillCompleted = new Flight(view);
        assertSame(completedBall.ball, stillCompleted.ball);
        assertEquals(completedBall.x, stillCompleted.x, 0f);
        assertEquals(completedBall.y, stillCompleted.y, 0f);

        controller.resume();
        controllerPaused = false;
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(activity.isGamePaused());
        assertEquals("The expired transition completes once after foregrounding", 2, view.getLevel());
        assertEquals("READY", state());
        assertEquals(27, aliveBricks());
        assertEquals(completedScore, view.getScore());
        assertEquals(3, number(view, "lives").intValue());
        tap();
        shadowOf(Looper.getMainLooper()).idle();
        assertSingleFrameChain("Foreground after artificial last-brick wait");
    }

    private void assertSingleFrameChain(String label) throws Exception {
        assertEquals("PLAYING", state());
        Flight before = new Flight(view);
        int scoreBefore = view.getScore();
        int livesBefore = number(view, "lives").intValue();
        int aliveBefore = aliveBricks();
        assertTrue("No slow effect may alter displacement", number(view, "slowUntil").longValue()
                <= System.currentTimeMillis());
        assertTrue("The observation starts with a real upward launch", before.vy < 0);

        // Even the defective double chain (20 updates) stays below every brick,
        // above the paddle and clear of side walls in this measurement window.
        float radius = number(view, "ballRadius").floatValue();
        float brickBottom = 0;
        for (RectF brick : bricks()) brickBottom = Math.max(brickBottom, brick.bottom);
        assertTrue("The ball cannot reach any brick in twenty updates",
                before.y + before.vy * 20 - radius > brickBottom);
        assertTrue("The ball starts above the paddle and travels away from it",
                before.y + radius < number(view, "paddleY").floatValue());
        assertTrue("The ball stays clear of either side wall",
                Math.min(before.x, before.x + before.vx * 20) > radius
                        && Math.max(before.x, before.x + before.vx * 20) < WIDTH - radius);

        advance(FLIGHT_WINDOW_MS);

        Flight after = new Flight(view);
        assertSame(before.ball, after.ball);
        assertEquals("No collision may change horizontal velocity", before.vx, after.vx, 0f);
        assertEquals("No collision may change vertical velocity", before.vy, after.vy, 0f);
        assertEquals(scoreBefore, view.getScore());
        assertEquals(livesBefore, number(view, "lives").intValue());
        assertEquals(aliveBefore, aliveBricks());
        float observedFrames = (after.y - before.y) / before.vy;
        System.out.println(label + ": 160 ms, y=" + before.y + " -> " + after.y
                + ", vy=" + before.vy + ", independently inferred frames=" + observedFrames);
        assertEquals(label + ": one update per 16 ms means ten updates in 160 ms",
                10f, observedFrames, EPSILON);
        assertEquals("The same ten advances must explain horizontal movement",
                before.x + before.vx * 10, after.x, EPSILON);
    }

    private void arrangeLastLifeAboutToLeaveBottom() throws Exception {
        Object ball = onlyBall();
        float radius = number(view, "ballRadius").floatValue();
        float launchSpeed = (float) Math.hypot(number(ball, "vx").floatValue(),
                number(ball, "vy").floatValue());
        // A valid final-life state after two prior misses, still just inside the
        // bottom removal threshold. Only the upcoming real update loses it.
        write(view, "lives", 1);
        write(view, "missedThisLevel", true);
        write(ball, "x", WIDTH / 2f);
        write(ball, "y", HEIGHT + radius - 1);
        write(ball, "vx", 0f);
        write(ball, "vy", launchSpeed);
        assertTrue(number(ball, "y").floatValue() - radius <= HEIGHT);
        System.out.println("Artificial endgame fixture: last life, one launched ball,"
                + " one pixel before bottom removal; real Handler update causes loss.");
    }

    private void arrangeLastBrickAboutToBeHit() throws Exception {
        List<Boolean> alive = aliveFlags();
        assertEquals(27, alive.size());
        int last = 22; // Third row, fifth column of the existing nine-column level.
        for (int index = 0; index < alive.size(); index++) alive.set(index, index == last);
        hitPoints().set(last, 1);
        // Keep the fixture's accumulated score consistent with 26 ordinary
        // level-one bricks already destroyed. No completion bonus is injected.
        write(view, "score", 26 * 15);
        write(view, "lastLevelScore", 26 * 15);
        RectF brick = bricks().get(last);
        Object ball = onlyBall();
        float radius = number(view, "ballRadius").floatValue();
        float launchSpeed = (float) Math.hypot(number(ball, "vx").floatValue(),
                number(ball, "vy").floatValue());
        write(ball, "x", brick.centerX());
        write(ball, "y", brick.bottom + radius + 1);
        write(ball, "vx", 0f);
        write(ball, "vy", -launchSpeed);
        assertTrue("Fixture starts outside the remaining brick",
                number(ball, "y").floatValue() - radius > brick.bottom);
        System.out.println("Artificial endgame fixture: original 27-brick board,"
                + " 26 destroyed, last bottom-centre brick has one HP;"
                + " real Handler update must collide, clear, and invoke Activity.");
    }

    private void tap() {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                WIDTH / 2f, HEIGHT / 2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                WIDTH / 2f, HEIGHT / 2f, 0);
        try {
            assertTrue(view.dispatchTouchEvent(down));
            assertTrue(view.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private void assertDrawsText(String expected) {
        TextCanvas canvas = new TextCanvas();
        view.onDraw(canvas);
        assertTrue("Actual onDraw must offer: " + expected + "; emitted=" + canvas.text,
                canvas.text.contains(expected));
    }

    private void advance(long milliseconds) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));
    }

    private String state() throws Exception {
        return read(view, "state").toString();
    }

    private int aliveBricks() throws Exception {
        int count = 0;
        for (boolean alive : aliveFlags()) if (alive) count++;
        return count;
    }

    @SuppressWarnings("unchecked")
    private List<Boolean> aliveFlags() throws Exception {
        return (List<Boolean>) read(view, "brickAlive");
    }

    @SuppressWarnings("unchecked")
    private List<Integer> hitPoints() throws Exception {
        return (List<Integer>) read(view, "brickHp");
    }

    @SuppressWarnings("unchecked")
    private List<RectF> bricks() throws Exception {
        return (List<RectF>) read(view, "bricks");
    }

    private Object onlyBall() throws Exception {
        List<?> balls = (List<?>) read(view, "balls");
        assertEquals("The fixture observes the game's single real ball", 1, balls.size());
        return balls.get(0);
    }

    private static Number number(Object owner, String name) throws Exception {
        return (Number) read(owner, name);
    }

    private static Object read(Object owner, String name) throws Exception {
        return field(owner, name).get(owner);
    }

    private static void write(Object owner, String name, Object value) throws Exception {
        field(owner, name).set(owner, value);
    }

    private static Field field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Flight {
        final Object ball;
        final float x;
        final float y;
        final float vx;
        final float vy;

        Flight(BreakoutView view) throws Exception {
            List<?> balls = (List<?>) read(view, "balls");
            assertEquals("Measure the real single-ball launch", 1, balls.size());
            ball = balls.get(0);
            x = number(ball, "x").floatValue();
            y = number(ball, "y").floatValue();
            vx = number(ball, "vx").floatValue();
            vy = number(ball, "vy").floatValue();
        }
    }

    private static final class TextCanvas extends Canvas {
        final List<String> text = new ArrayList<>();

        @Override
        public void drawText(String value, float x, float y, Paint paint) {
            text.add(value);
        }
    }
}

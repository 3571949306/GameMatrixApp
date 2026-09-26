package com.gamecenter.app.games.breakout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The same real View is measured and laid out at different portrait widths.
 * State changes use only startGame, touch dispatch, update and pauseGame. Private
 * entities are observed read-only because Breakout exposes no brick snapshots or
 * damage API. No ball/brick state, random seed, damage, or score is injected.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class BreakoutResizeTest {
    private BreakoutView view;

    @Before
    public void setUp() {
        view = new BreakoutView(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void shrinkingTheReadyRoundKeepsAllLiveBricksInsideTheNewPlayableWidth() throws Exception {
        layout(640, 960);
        view.startGame(1);
        Round before = new Round(view);
        assertEquals(27, before.bricks.size());
        assertEquals("READY", before.state);
        assertLiveBricksReachable(before);

        layout(360, 960);

        Round after = new Round(view);
        before.assertProgressEquals(after);
        assertLiveBricksReachable(after);
    }

    @Test
    public void growingThenReturningTheSameRoundReflowsWithoutRestartingIt() throws Exception {
        layout(360, 960);
        view.startGame(5);
        Round original = new Round(view);
        assertEquals(45, original.bricks.size());
        assertTrue("This level includes real durable bricks", original.maxHp.stream().anyMatch(hp -> hp > 1));

        layout(640, 960);

        Round grown = new Round(view);
        original.assertProgressEquals(grown);
        assertLiveBricksReachable(grown);
        assertTrue("A wider window must give the same nine-column board more horizontal space",
                grown.bricks.get(0).width() > original.bricks.get(0).width());
        assertTrue("The last column must use the newly available width",
                grown.bricks.get(8).right > 360);

        layout(360, 960);

        Round returned = new Round(view);
        original.assertProgressEquals(returned);
        assertLiveBricksReachable(returned);
        assertRectanglesEqual(original.bricks, returned.bricks);
    }

    @Test
    public void resizingAnActuallyDamagedPausedRoundPreservesDestroyedBricksAndRemainingDurability() throws Exception {
        layout(640, 960);
        view.startGame(5);
        playUntilDestroyedAndDamagedBricksExist();
        view.pauseGame();
        Round before = new Round(view);
        assertEquals("PAUSED", before.state);
        assertTrue("A real ball collision must already have removed a brick", before.hasDestroyedBrick());
        assertTrue("A real collision must leave a durable brick partially damaged", before.hasDamagedLiveBrick());
        assertTrue(view.getScore() > 0);

        layout(360, 960);

        Round narrow = new Round(view);
        before.assertProgressEquals(narrow);
        assertLiveBricksReachable(narrow);

        layout(640, 960);

        Round restored = new Round(view);
        before.assertProgressEquals(restored);
        assertRectanglesEqual(before.bricks, restored.bricks);
        // Geometry changes must not silently resume the paused game or revive bricks.
        for (int frame = 0; frame < 120; frame++) view.update();
        restored.assertProgressEquals(new Round(view));
    }

    private void layout(int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private void assertLiveBricksReachable(Round round) throws Exception {
        float paddleY = ((Number) read(view, "paddleY")).floatValue();
        float radius = ((Number) read(view, "ballRadius")).floatValue();
        int live = 0;
        for (int index = 0; index < round.bricks.size(); index++) {
            if (!round.alive.get(index)) continue;
            live++;
            RectF brick = round.bricks.get(index);
            assertTrue("Live brick " + index + " must stay wholly inside width " + view.getWidth()
                            + ": " + brick,
                    brick.left >= 0 && brick.right <= view.getWidth() + 0.01f);
            assertTrue("Live brick geometry must remain nonempty", brick.width() > 0 && brick.height() > 0);
            assertTrue("Live bricks remain above the paddle in the playable area",
                    brick.top >= 0 && brick.bottom < paddleY - radius);
        }
        assertTrue("The resized round must still contain live content", live > 0);
    }

    private void playUntilDestroyedAndDamagedBricksExist() throws Exception {
        // Bounded public gameplay. Paddle input follows a visible descending ball;
        // the game's own random launch, collisions and power-ups remain untouched.
        for (int frame = 0; frame < 12000; frame++) {
            Round round = new Round(view);
            if (round.hasDestroyedBrick() && round.hasDamagedLiveBrick()) {
                dispatch(MotionEvent.ACTION_UP, view.getWidth() / 2f);
                return;
            }
            assertFalse("Natural setup must not exhaust the current game", "GAME_OVER".equals(round.state));
            assertFalse("Inspect damage before naturally clearing the whole level", "LEVEL_CLEAR".equals(round.state));
            List<?> balls = (List<?>) read(view, "balls");
            assertFalse("The real current round must contain a ball", balls.isEmpty());
            float targetX = ((Number) read(balls.get(0), "x")).floatValue();
            if ("READY".equals(round.state)) {
                dispatch(MotionEvent.ACTION_DOWN, targetX);
            } else {
                float bestPriority = -Float.MAX_VALUE;
                for (Object ball : balls) {
                    float x = ((Number) read(ball, "x")).floatValue();
                    float y = ((Number) read(ball, "y")).floatValue();
                    float vx = ((Number) read(ball, "vx")).floatValue();
                    float vy = ((Number) read(ball, "vy")).floatValue();
                    float priority = y + (vy > 0 ? view.getHeight() * 2f : 0f);
                    if (priority > bestPriority) {
                        bestPriority = priority;
                        targetX = x + vx;
                    }
                }
                dispatch(MotionEvent.ACTION_MOVE, Math.max(0, Math.min(view.getWidth(), targetX)));
            }
            view.update();
        }
        throw new AssertionError("Bounded natural gameplay did not reach both a removed and a damaged live brick");
    }

    private void dispatch(int action, float x) {
        long time = SystemClock.uptimeMillis();
        MotionEvent touch = MotionEvent.obtain(time, time, action, x, view.getHeight() - 25f, 0);
        try {
            assertTrue("The real View must consume the paddle input", view.dispatchTouchEvent(touch));
        } finally {
            touch.recycle();
        }
    }

    private static Object read(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static List<Integer> integerList(Object object, String name) throws Exception {
        List<Integer> copy = new ArrayList<>();
        for (Object value : (List<?>) read(object, name)) copy.add(((Number) value).intValue());
        return copy;
    }

    private static void assertRectanglesEqual(List<RectF> expected, List<RectF> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals("Brick " + index + " left", expected.get(index).left, actual.get(index).left, 0.01f);
            assertEquals("Brick " + index + " top", expected.get(index).top, actual.get(index).top, 0.01f);
            assertEquals("Brick " + index + " right", expected.get(index).right, actual.get(index).right, 0.01f);
            assertEquals("Brick " + index + " bottom", expected.get(index).bottom, actual.get(index).bottom, 0.01f);
        }
    }

    private static final class Round {
        final List<RectF> bricks = new ArrayList<>();
        final List<Boolean> alive = new ArrayList<>();
        final List<Integer> hp, maxHp, colors;
        final int score, levelScore, level, lives;
        final boolean running, noMiss;
        final String state;

        Round(BreakoutView view) throws Exception {
            for (Object brick : (List<?>) read(view, "bricks")) bricks.add(new RectF((RectF) brick));
            for (Object value : (List<?>) read(view, "brickAlive")) alive.add((Boolean) value);
            hp = integerList(view, "brickHp");
            maxHp = integerList(view, "brickMaxHp");
            colors = integerList(view, "brickColors");
            score = view.getScore();
            levelScore = view.getLastLevelScore();
            level = view.getLevel();
            lives = ((Number) read(view, "lives")).intValue();
            running = view.isGameRunning();
            noMiss = view.isLevelNoMiss();
            state = read(view, "state").toString();
            assertEquals(bricks.size(), alive.size());
            assertEquals(bricks.size(), hp.size());
            assertEquals(bricks.size(), maxHp.size());
            assertEquals(bricks.size(), colors.size());
        }

        boolean hasDestroyedBrick() {
            return alive.contains(false);
        }

        boolean hasDamagedLiveBrick() {
            for (int index = 0; index < bricks.size(); index++) {
                if (alive.get(index) && hp.get(index) > 0 && hp.get(index) < maxHp.get(index)) return true;
            }
            return false;
        }

        void assertProgressEquals(Round actual) {
            assertEquals("Resize must preserve every removed/live brick", alive, actual.alive);
            assertEquals("Resize must preserve current durability, including partial damage", hp, actual.hp);
            assertEquals(maxHp, actual.maxHp);
            assertEquals(colors, actual.colors);
            assertEquals(score, actual.score);
            assertEquals(levelScore, actual.levelScore);
            assertEquals(level, actual.level);
            assertEquals(lives, actual.lives);
            assertEquals(running, actual.running);
            assertEquals(noMiss, actual.noMiss);
            assertEquals(state, actual.state);
        }
    }
}

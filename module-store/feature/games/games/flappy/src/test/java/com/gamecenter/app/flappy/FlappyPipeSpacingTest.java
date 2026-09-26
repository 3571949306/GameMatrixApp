package com.gamecenter.app.flappy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts the real View, taps once per 31 public updates, and waits for two pipes
 * to be generated naturally. Reflection only copies existing geometry. No bird,
 * pipe, velocity, random seed, started state, or spawn countdown is written.
 *
 * This checks that two independently generated openings cannot impose conflicting
 * vertical requirements on the bird at the same horizontal position. It does not
 * claim a natural crossing, a score, device timing, or playable random trajectories.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class FlappyPipeSpacingTest {
    private static final int MAX_UPDATES = 700;
    private static final int UPDATES_PER_TAP = 31;
    private static final float POSITION_TOLERANCE_PX = 0.25f;

    @Test
    @Config(qualifiers = "w600dp-h600dp-mdpi")
    public void mdpiThreeDifficultySettingsKeepTheirOriginalNaturalSpacing() throws Exception {
        assertNaturallyGeneratedPair(0.3f, 1f);
        assertNaturallyGeneratedPair(0.5f, 1f);
        assertNaturallyGeneratedPair(0.8f, 1f);
    }

    @Test
    @Config(qualifiers = "w600dp-h600dp-xxxhdpi")
    public void xxxhdpiNormalPipesCannotConstrainTheBirdSimultaneously() throws Exception {
        assertNaturallyGeneratedPair(0.5f, 4f);
    }

    @Test
    @Config(qualifiers = "w600dp-h600dp-xxxhdpi")
    public void xxxhdpiHardPipesCannotConstrainTheBirdSimultaneously() throws Exception {
        assertNaturallyGeneratedPair(0.8f, 4f);
    }

    private static void assertNaturallyGeneratedPair(float difficultyFactor, float expectedDensity)
            throws Exception {
        FlappyView view = new FlappyView(RuntimeEnvironment.getApplication());
        float density = view.getResources().getDisplayMetrics().density;
        assertEquals("Use the requested Android resource density", expectedDensity, density, 0f);
        int width = Math.round(600f * density);
        int height = Math.round(600f * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);

        // The same public difficulty inputs calculated by the production Fragment.
        float speed = 3f * (0.5f + difficultyFactor);
        float gap = 180f * (1.5f - difficultyFactor);
        view.setPipeConfig(speed, gap);
        view.startGame();
        try {
            List<float[]> pair = null;
            boolean sawFirstPipeAlone = false;
            for (int update = 1; update <= MAX_UPDATES; update++) {
                if ((update - 1) % UPDATES_PER_TAP == 0) {
                    tap(view);
                }
                // Public game updates, not private spawn calls or a substituted game model.
                view.update();
                assertTrue("Legal pre-pipe flight must remain active at update " + update,
                        view.isGameRunning());
                assertEquals("No pipe has been crossed in this geometry fixture", 0, view.getScore());
                List<float[]> current = pipeSnapshots(view);
                if (current.size() == 1) {
                    sawFirstPipeAlone = true;
                } else if (current.size() == 2) {
                    pair = current;
                    break;
                } else {
                    assertEquals("At most two pipes are generated before this loop stops",
                            0, current.size());
                }
            }

            assertTrue("The first pipe must have existed before the second was generated",
                    sawFirstPipeAlone);
            assertNotNull("Two natural pipes must appear within " + MAX_UPDATES + " updates", pair);
            assertEquals(2, pair.size());
            float firstX = pair.get(0)[0];
            float secondX = pair.get(1)[0];
            float radius = readFloat(view, "birdSize");
            float birdX = readFloat(view, "birdX");
            float pipeWidth = readFloat(view, "pipeWidth");
            float gapPx = readFloat(view, "pipeGap");
            float spacing = secondX - firstX;
            float horizontalCollisionSpan = pipeWidth + 2f * radius;

            assertEquals("The new pipe is naturally born at the right edge", width, secondX, 0f);
            assertEquals("The real bird radius remains 24 dp", 24f * density, radius, 0f);
            assertEquals("The real pipe body remains 60 dp", 60f * density, pipeWidth, 0f);
            assertTrue("The wide fixture keeps both pipes ahead of the bird",
                    firstX > birdX + radius);
            assertTrue("Each independent opening can contain the complete bird", gapPx > 2f * radius);

            // A bird overlaps one pipe while its x is in (birdX-radius-pipeWidth,
            // birdX+radius). Separating consecutive left edges by at least this
            // interval's length prevents simultaneous, potentially incompatible,
            // vertical collision requirements for arbitrary independently drawn gaps.
            // This is a geometry guarantee, not a claim that all flight paths work.
            assertTrue("Two natural pipes must not constrain bird height simultaneously: density="
                            + density + ", difficulty=" + difficultyFactor + ", spacing=" + spacing
                            + "px, pipeWidth+birdDiameter=" + horizontalCollisionSpan + "px",
                    spacing >= horizontalCollisionSpan);

            // Spawning is quantized to whole public updates; allow one speed step,
            // plus a quarter-pixel bound for accumulated float subtraction.
            float nominalSpacingPx = 300f * density;
            assertTrue("Natural spacing must preserve the 300 dp baseline: " + spacing,
                    spacing >= nominalSpacingPx - POSITION_TOLERANCE_PX);
            assertTrue("Natural spacing may overshoot by at most one update: " + spacing,
                    spacing <= nominalSpacingPx + speed + POSITION_TOLERANCE_PX);
        } finally {
            view.stopGame();
        }
    }

    private static void tap(FlappyView view) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                view.getWidth() / 2f, view.getHeight() / 2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                view.getWidth() / 2f, view.getHeight() / 2f, 0);
        try {
            assertTrue("A complete public touch starts or flaps the bird", view.dispatchTouchEvent(down));
            assertTrue(view.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<float[]> pipeSnapshots(FlappyView view) throws Exception {
        Field field = FlappyView.class.getDeclaredField("pipes");
        field.setAccessible(true);
        List<float[]> copies = new ArrayList<>();
        for (float[] pipe : (List<float[]>) field.get(view)) {
            copies.add(pipe.clone());
        }
        return copies;
    }

    private static float readFloat(FlappyView view, String name) throws Exception {
        Field field = FlappyView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(view);
    }
}

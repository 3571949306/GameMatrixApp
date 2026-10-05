package com.gamecenter.app.flappy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Real FlappyView at two screen densities, a real touch launch, two public updates,
 * a naturally generated first pipe, and native Canvas pixels. Reflection only reads
 * bird/pipe geometry; no position, pipe, random seed, velocity or state is written.
 * This proves a visible single-pipe opening can contain the full collision circle.
 * It does not claim a natural pipe crossing, a score, or a playable random sequence.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FlappyDensityGeometryTest {
    private static final int BIRD_COLOR = 0xFFFFEB3B;
    private static final int PIPE_COLOR = 0xFF4CAF50;
    private static final int PIPE_EDGE_COLOR = 0xFF388E3C;

    @Test
    @Config(qualifiers = "w360dp-h600dp-mdpi")
    public void mdpiDefaultAndThreeDifficultySettingsKeepTheirOriginalOpenings() throws Exception {
        assertOpening(null, 180f, 1f);
        // Existing Fragment inputs: factors .3/.5/.8 produce 216/180/126 dp gaps.
        assertOpening(new float[] {2.4f, 216f}, 216f, 1f);
        assertOpening(new float[] {3f, 180f}, 180f, 1f);
        assertOpening(new float[] {3.9f, 126f}, 126f, 1f);
    }

    @Test
    @Config(qualifiers = "w360dp-h600dp-xxxhdpi")
    public void xxxhdpiDefaultAndSelectedNormalGapCanContainTheFullBird() throws Exception {
        assertOpening(null, 180f, 4f);
        assertOpening(new float[] {3f, 180f}, 180f, 4f);
    }

    @Test
    @Config(qualifiers = "w360dp-h600dp-xxxhdpi")
    public void xxxhdpiSelectedHardGapCanContainTheFullBird() throws Exception {
        assertOpening(new float[] {3.9f, 126f}, 126f, 4f);
    }

    private void assertOpening(float[] config, float expectedGapDp, float expectedDensity) throws Exception {
        FlappyView view = new FlappyView(RuntimeEnvironment.getApplication());
        float density = view.getResources().getDisplayMetrics().density;
        assertEquals("Use the requested real Android resource density", expectedDensity, density, 0f);
        int width = Math.round(360 * density);
        int height = Math.round(600 * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        if (config != null) view.setPipeConfig(config[0], config[1]);
        view.startGame();
        launchThroughTouch(view);
        view.update(); // Normal production update generates the first random pipe at the right edge.
        view.update(); // Move that pipe a few pixels on-screen; no pipe position fixture is installed.
        assertTrue("The short legal launch must still be active", view.isGameRunning());
        assertEquals("The first pipe has not been crossed", 0, view.getScore());

        float[] pipe = firstPipeSnapshot(view);
        float radius = readFloat(view, "birdSize");
        float birdX = readFloat(view, "birdX");
        float gapPx = readFloat(view, "pipeGap");
        float pipeWidth = readFloat(view, "pipeWidth");
        assertEquals("The existing bird visual/collision radius is 24 dp", 24f * density, radius, 0f);
        assertEquals("The existing pipe body width is 60 dp", 60f * density, pipeWidth, 0f);
        assertTrue(pipe[0] < width - 1f && pipe[0] + pipeWidth > width);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            int drawnBirdDiameter = yellowDiameterAt(bitmap, Math.round(birdX));
            int drawnGap = pipeOpeningAt(bitmap, width - 1, pipe[1]);
            assertEquals("Native bird pixels must agree with the actual collision circle",
                    radius * 2f, drawnBirdDiameter, 2f);
            assertEquals("Native pipe pixels must agree with the same opening used by collisions",
                    gapPx, drawnGap, 2f);

            // Old xxxhdpi normal/hard fail here at an actually rendered opening
            // smaller than the bird, before only comparing an expected scaled value.
            assertTrue("A rendered pipe opening must fit the full bird: gap=" + drawnGap
                            + "px, bird=" + drawnBirdDiameter + "px, density=" + density,
                    drawnGap > drawnBirdDiameter);
            float safeCenterMin = pipe[1] - gapPx / 2f + radius;
            float safeCenterMax = pipe[1] + gapPx / 2f - radius;
            assertTrue("The production collision bounds must admit a nonempty center interval",
                    safeCenterMax > safeCenterMin);
            assertEquals("Difficulty gap is a dp dimension just like bird/pipe width",
                    expectedGapDp * density, gapPx, 0.001f);
            assertEquals("The dp-sized opening must also be present in actual rendered pixels",
                    expectedGapDp * density, drawnGap, 2f);
        } finally {
            bitmap.recycle();
            view.stopGame();
        }
    }

    private static void launchThroughTouch(FlappyView view) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                view.getWidth() / 2f, view.getHeight() / 2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                view.getWidth() / 2f, view.getHeight() / 2f, 0);
        try {
            assertTrue(view.dispatchTouchEvent(down));
            assertTrue(view.dispatchTouchEvent(up));
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static int yellowDiameterAt(Bitmap bitmap, int x) {
        int first = -1;
        int last = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            if (bitmap.getPixel(x, y) == BIRD_COLOR) {
                if (first == -1) first = y;
                last = y;
            }
        }
        assertTrue("Native Canvas must produce actual yellow bird pixels", first >= 0 && last > first);
        return last - first + 1;
    }

    private static int pipeOpeningAt(Bitmap bitmap, int x, float gapCenter) {
        int center = Math.round(gapCenter);
        assertTrue(center >= 0 && center < bitmap.getHeight());
        int topBoundary = 0;
        for (int y = center; y >= 0; y--) {
            if (isPipeColor(bitmap.getPixel(x, y))) {
                topBoundary = y + 1;
                break;
            }
        }
        int bottomBoundary = -1;
        for (int y = center; y < bitmap.getHeight(); y++) {
            if (isPipeColor(bitmap.getPixel(x, y))) {
                bottomBoundary = y;
                break;
            }
        }
        assertTrue("The first naturally generated pipe must produce bottom-pipe pixels",
                bottomBoundary >= center);
        return bottomBoundary - topBoundary;
    }

    private static boolean isPipeColor(int color) {
        return color == PIPE_COLOR || color == PIPE_EDGE_COLOR;
    }

    @SuppressWarnings("unchecked")
    private static float[] firstPipeSnapshot(FlappyView view) throws Exception {
        Field field = FlappyView.class.getDeclaredField("pipes");
        field.setAccessible(true);
        List<float[]> pipes = (List<float[]>) field.get(view);
        assertEquals("Only the naturally generated first pipe should exist after two updates", 1, pipes.size());
        return pipes.get(0).clone();
    }

    private static float readFloat(FlappyView view, String name) throws Exception {
        Field field = FlappyView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(view);
    }
}

package com.gamecenter.app.flappy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gamecenter.app.R;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Real Fragment and real scheduled loop; only geometry is reflected, read-only. */
public final class FlappyFragmentTestHarness implements AutoCloseable {
    final ActivityController<GameHostActivity> controller;
    final FlappyModuleFragment fragment;
    final LinearLayout root;
    final FlappyView board;
    final Button restart;
    final TextView score;
    private boolean activityPaused;

    FlappyFragmentTestHarness() {
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        fragment = new FlappyModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "flappy-real-handler").commitNow();
        root = (LinearLayout) fragment.requireView();
        layoutRoot(600, 900);
        idleNow();
        board = findBoard(root);
        assertNotNull(board);
        assertTrue("The real board must have usable geometry", board.getWidth() > 300 && board.getHeight() > 300);
        assertEquals("The timing fixture uses real mdpi resources", 1f,
                board.getResources().getDisplayMetrics().density, 0f);
        restart = findButton(root, fragment.getString(R.string.game_btn_restart));
        assertNotNull("The real Fragment exposes its restart button", restart);
        LinearLayout scoreBar = (LinearLayout) root.getChildAt(1);
        score = (TextView) scoreBar.getChildAt(0);
        assertTrue(board.isGameRunning());
        assertEquals(0, board.getScore());
    }

    void tapBoard() {
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent touch = MotionEvent.obtain(now, now, action,
                    board.getWidth() / 2f, board.getHeight() / 2f, 0);
            try {
                assertTrue("Use a complete real View touch", board.dispatchTouchEvent(touch));
            } finally {
                touch.recycle();
            }
        }
    }

    void restartThroughButton() {
        assertTrue("Use the actual restart Button and listener", restart.performClick());
        idleNow();
        assertTrue(board.isGameRunning());
        assertEquals(0, board.getScore());
    }

    void launchAndAwaitFirstPipe() throws Exception {
        assertTrue("Launch begins with no injected or leftover pipes", pipes().isEmpty());
        tapBoard();
        advance(16);
        assertTrue(board.isGameRunning());
        assertEquals(0, board.getScore());
        assertEquals("The actual scheduled loop must create the first natural pipe", 1, pipes().size());
    }

    void pauseActivity() {
        controller.pause();
        activityPaused = true;
    }

    void resumeActivity() {
        controller.resume();
        activityPaused = false;
        idleNow();
    }

    void sizeBoardBeforeLaunch(int desiredHeight) {
        // Public parent layout only. Keep its header/buttons and weight calculation;
        // do not write the View's private dimensions or its physical game state.
        int rootHeight = root.getHeight() + desiredHeight - board.getHeight();
        layoutRoot(root.getWidth(), rootHeight);
        idleNow();
        assertEquals("The real weighted game container must have the requested height",
                desiredHeight, board.getHeight());
    }

    private void layoutRoot(int width, int height) {
        root.setLayoutParams(new FrameLayout.LayoutParams(width, height));
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    static void advance(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    static void idleNow() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @SuppressWarnings("unchecked")
    List<float[]> pipes() throws Exception {
        Field field = FlappyView.class.getDeclaredField("pipes");
        field.setAccessible(true);
        List<float[]> copies = new ArrayList<>();
        for (float[] pipe : (List<float[]>) field.get(board)) copies.add(pipe.clone());
        return copies;
    }

    float value(String name) throws Exception {
        Field field = FlappyView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(board);
    }

    float firstPipeX() throws Exception {
        List<float[]> current = pipes();
        assertEquals("The short timing window retains only the first pipe", 1, current.size());
        return current.get(0)[0];
    }

    @Override
    public void close() {
        if (!activityPaused) controller.pause();
        controller.stop().destroy();
    }

    private static FlappyView findBoard(View current) {
        if (current instanceof FlappyView) return (FlappyView) current;
        if (current instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) current;
            for (int index = 0; index < group.getChildCount(); index++) {
                FlappyView found = findBoard(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButton(View current, String text) {
        if (current instanceof Button && text.contentEquals(((Button) current).getText())) return (Button) current;
        if (current instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) current;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button found = findButton(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Load the real host resource table used by the module's compile-only R.jar. */
    public static class GameHostActivity extends FragmentActivity {
        private Resources hostResources;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
            super.onCreate(savedInstanceState);
        }

        @Override
        public Resources getResources() {
            if (hostResources == null) {
                Resources delegate = super.getResources();
                Path repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();
                while (repoRoot != null && !Files.isRegularFile(repoRoot.resolve("settings.gradle"))) {
                    repoRoot = repoRoot.getParent();
                }
                assertNotNull("Locate the repository containing the real host resources", repoRoot);
                Path resourceApk = repoRoot.resolve("app/build/intermediates/linked_resources_binary_format/"
                        + "debug/processDebugResources/linked-resources-binary-format-debug.ap_");
                assertTrue("The compile dependency must generate real host resources: " + resourceApk,
                        Files.isRegularFile(resourceApk));
                try {
                    AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
                    int cookie = (int) AssetManager.class.getMethod("addAssetPath", String.class)
                            .invoke(assets, resourceApk.toString());
                    assertTrue("AssetManager must load real host resources", cookie != 0);
                    hostResources = new Resources(assets, delegate.getDisplayMetrics(), delegate.getConfiguration());
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot load real host resources for Flappy Fragment", exception);
                }
            }
            return hostResources;
        }
    }
}

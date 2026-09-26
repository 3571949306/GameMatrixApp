package com.gamecenter.app.klotski;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowChoreographer;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Actual attached View layout and production draw calls, backed by native bitmap pixels.
 * No expected cell-size formula, replacement View, private writes or animator seeking.
 * This is a View-render regression; real DynamicGameActivity screenshots remain separate.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w400dp-h700dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class KlotskiExitVisibilityTest {
    private ActivityController<Activity> controller;
    private FrameLayout parent;
    private KlotskiView view;
    private KlotskiGame game;
    private boolean oldFramePaused;
    private Duration oldFrameDelay;
    private float oldAnimationScale;

    @Before public void setUp() {
        oldFramePaused = ShadowChoreographer.isPaused();
        oldFrameDelay = ShadowChoreographer.getFrameDelay();
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        controller = Robolectric.buildActivity(Activity.class).create().start().resume();
        oldAnimationScale = ValueAnimator.getDurationScale();
        assertTrue(Settings.Global.putFloat(controller.get().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f));
        assertEquals(1f, ValueAnimator.getDurationScale(), 0f);
        parent = new FrameLayout(controller.get());
        view = new KlotskiView(controller.get());
        game = new KlotskiGame();
        view.setGame(game);
        parent.addView(view, new FrameLayout.LayoutParams(-1, -1));
        controller.get().setContentView(parent);
        controller.visible();
        shadowOf(Looper.getMainLooper()).idle();
        advance(32);
    }

    @After public void tearDown() {
        try {
            if (parent != null && view != null) parent.removeView(view);
            if (controller != null) controller.pause().stop().destroy();
        } finally {
            if (controller != null) Settings.Global.putFloat(controller.get().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, oldAnimationScale);
            ShadowChoreographer.setFrameDelay(oldFrameDelay);
            ShadowChoreographer.setPaused(oldFramePaused);
        }
    }

    @Test public void heightLimitedDeviceAndLandscapeDrawWholeExitPanelLabelAndArrows() throws Exception {
        assertDrawingFits(1376, 1740); // 1440px device, after two 8dp/32px horizontal margins.
        assertDrawingFits(1440, 1740);
        assertDrawingFits(580, 400);
    }

    @Test public void widthLimitedViewsKeepBothSidesOfTheActualBoardFrame() throws Exception {
        assertDrawingFits(400, 580);
        assertDrawingFits(320, 900);
    }

    @Test public void resizedGeometryStillDeliversTheSameNaturalSingleStepMove() throws Exception {
        String expected = "1,1,0,0,0,3,0,0,2,3,2,1,2,1,3,2,3,1,4,3,4";
        for (int[] size : new int[][]{{400, 580}, {580, 400}, {1376, 1740}}) {
            game = new KlotskiGame();
            view.setGame(game);
            layout(size[0], size[1]);
            KlotskiGame.Block block = game.getBlocks().get(8);
            assertTrue(game.canMove(block, 1, 0));
            float cell = readFloat("cellSize");
            float x = readFloat("offsetX") + (block.x + 0.5f) * cell;
            float y = readFloat("offsetY") + (block.y + 0.5f) * cell;
            long down = SystemClock.uptimeMillis();
            touch(down, MotionEvent.ACTION_DOWN, x, y);
            touch(down, MotionEvent.ACTION_MOVE, x + 0.6f * cell, y);
            touch(down, MotionEvent.ACTION_UP, x + 0.6f * cell, y);
            assertEquals(expected, game.serializeState());
            advance(160);
            assertEquals(expected, game.serializeState());
        }
    }

    private void assertDrawingFits(int width, int height) throws Exception {
        layout(width, height);
        assertRenderedBoundsAndPixels();
        // Let the actual 1200ms repeating animator reach its largest visible phase.
        int frames = 0;
        while (readFloat("exitPulsePhase") < 0.999f && frames++ < 160) advance(16);
        assertTrue("Real frames must reach the pulse peak without seeking", readFloat("exitPulsePhase") >= 0.999f);
        assertRenderedBoundsAndPixels();
    }

    private void layout(int width, int height) {
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, width, height);
        assertTrue(view.isAttachedToWindow());
        assertEquals(width, view.getWidth());
        assertEquals(height, view.getHeight());
    }

    private void assertRenderedBoundsAndPixels() {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            ObservedCanvas canvas = new ObservedCanvas(bitmap);
            view.draw(canvas); // Every observed operation still goes to native Canvas.
            assertNotNull("The production draw must paint its outer board frame", canvas.board);
            assertNotNull("The production draw must paint its golden exit panel", canvas.exit);
            assertNotNull("The production draw must paint the actual 出口 label", canvas.exitText);
            assertEquals("Both original direction markers must still be drawn", 2, canvas.arrows.size());
            System.out.println("Klotski drawn " + view.getWidth() + "x" + view.getHeight()
                    + " frame=" + canvas.board + " exit=" + canvas.exit + " text=" + canvas.exitText);

            // These are the actual Canvas commands, not a copy of onSizeChanged arithmetic.
            inside("Whole board frame", canvas.board);
            inside("Whole pulsing exit panel", canvas.exit);
            inside("Whole exit label glyphs", canvas.exitText);
            for (RectF arrow : canvas.arrows) inside("Whole exit direction glyph", arrow);
            assertTrue(canvas.exit.contains(canvas.exitText));

            int panelX = Math.round(canvas.exit.left + canvas.exit.width() * 0.15f);
            int panelTop = (int) Math.ceil(canvas.exit.top) + 2;
            int panelBottom = (int) Math.floor(canvas.exit.bottom) - 2;
            assertTrue("Native gold pixels must exist at the complete panel top",
                    gold(bitmap.getPixel(panelX, panelTop)));
            assertTrue("Native gold pixels must exist at the complete panel bottom",
                    gold(bitmap.getPixel(panelX, panelBottom)));
            int below = (int) Math.floor(canvas.exit.bottom) + 1;
            assertTrue("Leave a visible scanline below the exit", below < bitmap.getHeight());
            assertFalse("The panel must finish before the View clips it", gold(bitmap.getPixel(panelX, below)));
            assertTrue("Native text pixels must be present inside the retained label", whitePixels(bitmap, canvas.exitText) > 4);

            int frameY = Math.round(canvas.board.centerY());
            int left = (int) Math.ceil(canvas.board.left) + 2;
            int right = (int) Math.floor(canvas.board.right) - 2;
            assertEquals("Native left frame pixels must remain on-screen", 0xFF3E2723, bitmap.getPixel(left, frameY));
            assertEquals("Native right frame pixels must remain on-screen", 0xFF3E2723, bitmap.getPixel(right, frameY));
        } finally {
            bitmap.recycle();
        }
    }

    private void inside(String name, RectF rect) {
        assertTrue(name + " is clipped at " + view.getWidth() + "x" + view.getHeight() + ": " + rect,
                rect.width() > 0f && rect.height() > 0f && rect.left >= 1f && rect.top >= 1f
                        && rect.right <= view.getWidth() - 1f && rect.bottom <= view.getHeight() - 1f);
    }

    private static boolean gold(int color) {
        return Color.red(color) > 190 && Color.green(color) > 120 && Color.blue(color) < 80;
    }

    private static int whitePixels(Bitmap bitmap, RectF rect) {
        int count = 0;
        for (int y = (int) Math.floor(rect.top); y < Math.ceil(rect.bottom); y++) {
            for (int x = (int) Math.floor(rect.left); x < Math.ceil(rect.right); x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.red(pixel) > 235 && Color.green(pixel) > 235 && Color.blue(pixel) > 235) count++;
            }
        }
        return count;
    }

    private static final class ObservedCanvas extends Canvas {
        RectF board;
        RectF exit;
        RectF exitText;
        final List<RectF> arrows = new ArrayList<>();

        ObservedCanvas(Bitmap bitmap) { super(bitmap); }

        @Override public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            if (paint.getShader() == null && paint.getStyle() == Paint.Style.FILL) {
                if (paint.getColor() == 0xFF3E2723 && board == null) board = new RectF(rect);
                if ((paint.getColor() & 0x00FFFFFF) == 0x00FFC107) exit = new RectF(rect);
            }
            super.drawRoundRect(rect, rx, ry, paint);
        }

        @Override public void drawText(String text, float x, float y, Paint paint) {
            if ("出口".equals(text) || "▲".equals(text)) {
                Rect glyphs = new Rect();
                paint.getTextBounds(text, 0, text.length(), glyphs);
                float originX = x;
                if (paint.getTextAlign() == Paint.Align.CENTER) originX -= paint.measureText(text) / 2f;
                else if (paint.getTextAlign() == Paint.Align.RIGHT) originX -= paint.measureText(text);
                RectF bounds = new RectF(originX + glyphs.left, y + glyphs.top,
                        originX + glyphs.right, y + glyphs.bottom);
                if ("出口".equals(text)) exitText = bounds;
                else arrows.add(bounds);
            }
            super.drawText(text, x, y, paint);
        }
    }

    private void touch(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try { assertTrue(view.dispatchTouchEvent(event)); } finally { event.recycle(); }
    }

    private float readFloat(String name) throws Exception {
        Field field = KlotskiView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(view);
    }

    private void advance(long milliseconds) {
        long before = SystemClock.uptimeMillis();
        for (long remaining = milliseconds; remaining > 0;) {
            long step = Math.min(16, remaining);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step));
            remaining -= step;
        }
        assertEquals(before + milliseconds, SystemClock.uptimeMillis());
    }
}

package com.gamecenter.app.tetris;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Real onDraw commands, not device pixels or private ScorePop inspection.
 * Portrait heights 546/550dp represent the remaining game area beneath the
 * Fragment header in a 360x640dp screen. Scoring cases use explicitly legal
 * public restoreSnapshot fixtures, not naturally played endgame claims.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisHudFeedbackTest {
    private static final int[] NEXT = {
            TetrisView.PIECE_O, TetrisView.PIECE_T, TetrisView.PIECE_L,
            TetrisView.PIECE_J, TetrisView.PIECE_S
    };
    private static final int ACCENT = 0xFFFFC107;
    private static final int CONTROL_BACKGROUND = 0x55000000;
    private TetrisView view;

    @Before
    public void setUp() {
        view = new TetrisView(ApplicationProvider.getApplicationContext());
        view.setDifficultyLevel(2);
        layout(360, 550);
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void portraitNextOutlineLeavesFourDpOfClearSpaceAboveTheActualGrid() {
        for (int height : new int[]{546, 550}) {
            layout(360, height);
            view.startGame();
            RecordingCanvas canvas = drawCommands();
            List<RoundRectCommand> outlines = new ArrayList<>();
            for (RoundRectCommand command : canvas.rects) {
                if (command.color == ACCENT && command.style == Paint.Style.STROKE) outlines.add(command);
            }
            assertEquals("Portrait NEXT must have one actual highlighted outline", 1, outlines.size());
            RoundRectCommand next = outlines.get(0);
            float paintedNextBottom = next.rect.bottom + next.strokeWidth / 2f;
            float firstGridLine = canvas.horizontalLines.firstKey();
            float paintedGridTop = firstGridLine - canvas.horizontalLines.get(firstGridLine) / 2f;
            float gap = paintedGridTop - paintedNextBottom;
            assertTrue("At remaining height " + height + "dp the painted NEXT outline needs a 4dp gap; actual=" + gap,
                    gap >= 4f - 0.01f);
            assertControlsVisibleBelowGrid(canvas);
        }
    }

    @Test
    public void landscapeKeepsAllFiveUsableControlsBelowTheGrid() {
        layout(640, 360);
        view.startGame();

        assertControlsVisibleBelowGrid(drawCommands());
    }

    @Test
    public void singlePerfectClearDrawsItsNameAndExactNineHundredReward() {
        int[][] fixture = singleLineWell(false);
        restore(fixture, 0, 3, 0, 0, 1, 0, false);

        view.hardDrop();
        finishClear();

        assertEquals("36 manual hard-drop points + 100 Single + 800 PC", 936, view.getScore());
        TextCommand popup = onlyVisibleReward(drawCommands());
        assertTrue("The player must actually see the Perfect Clear achievement", popup.text.contains("Perfect Clear!"));
        assertTrue("The floating reward excludes the separately earned hard-drop points", popup.text.endsWith(" +900"));
        assertPopupFitsView(popup);
    }

    @Test
    public void backToBackPerfectClearDrawsTheLongNameAndExactSixThousandFiveHundredReward() {
        int[][] fixture = new int[20][10];
        for (int row = 16; row < 20; row++) {
            for (int col = 0; col < 10; col++) if (col != 4) fixture[row][col] = (row + col) % 7 + 1;
        }
        restore(fixture, 1, 2, 137, 10, 2, 1, true);

        view.hardDrop();
        finishClear();

        // First-level-two B2B base 2400 + second-clear combo 100 + four-line PC 4000.
        assertEquals(137 + 32 + 6500, view.getScore());
        TextCommand popup = onlyVisibleReward(drawCommands());
        assertTrue(popup.text.contains("B2B"));
        assertTrue(popup.text.contains("Perfect Clear!"));
        assertTrue("Only the 6500-point clear reward belongs in this floating label", popup.text.endsWith(" +6500"));
        assertPopupFitsView(popup);
    }

    @Test
    public void anOrdinarySingleKeepsItsNameAndNeverClaimsPerfectClear() {
        int[][] fixture = singleLineWell(true);
        restore(fixture, 0, 3, 0, 0, 1, 0, false);

        view.hardDrop();
        finishClear();

        assertEquals(36 + 100, view.getScore());
        assertEquals("The upper anchor remains, so the board is not empty", 7, view.getGrid()[4][0]);
        RecordingCanvas canvas = drawCommands();
        TextCommand popup = onlyVisibleReward(canvas);
        assertEquals("Single +100", popup.text);
        for (TextCommand text : canvas.texts) assertFalse(text.text.contains("Perfect Clear"));
        assertPopupFitsView(popup);
    }

    private void assertControlsVisibleBelowGrid(RecordingCanvas canvas) {
        assertEquals("Observe the actual 10-column board", 11, canvas.verticalLines.size());
        assertEquals("Observe the actual 20-row board", 21, canvas.horizontalLines.size());
        float bottomLine = canvas.horizontalLines.lastKey();
        float paintedGridBottom = bottomLine + canvas.horizontalLines.get(bottomLine) / 2f;
        List<RoundRectCommand> controls = new ArrayList<>();
        for (RoundRectCommand rect : canvas.rects) if (rect.color == CONTROL_BACKGROUND) controls.add(rect);
        assertEquals(5, controls.size());
        RectF window = new RectF(0, 0, view.getWidth(), view.getHeight());
        for (RoundRectCommand control : controls) {
            assertTrue("Every actual control rectangle stays visible", window.contains(control.rect));
            assertTrue(control.rect.width() >= 48f && control.rect.height() >= 48f);
            assertTrue("The controls must not cover the board", control.rect.top >= paintedGridBottom);
        }
        for (String label : Arrays.asList("HOLD", "◀", "⟳", "▶", "▼")) {
            boolean visible = false;
            for (TextCommand text : canvas.texts) {
                if (!label.equals(text.text)) continue;
                for (RoundRectCommand control : controls) {
                    if (control.rect.contains(text.x, text.y)) visible = true;
                }
            }
            assertTrue("Each control must retain its real visible label: " + label, visible);
        }
    }

    private TextCommand onlyVisibleReward(RecordingCanvas canvas) {
        List<TextCommand> rewards = new ArrayList<>();
        for (TextCommand text : canvas.texts) {
            if (text.text.contains(" +") && text.alpha > 0) rewards.add(text);
        }
        assertEquals("The actual drawing must contain one visible clear-reward popup", 1, rewards.size());
        return rewards.get(0);
    }

    private void assertPopupFitsView(TextCommand popup) {
        assertTrue("Measure the actual drawn label, not an empty placeholder", popup.bounds.width() > 0);
        assertTrue("The complete label must fit the current View: " + popup.text + " " + popup.bounds,
                new RectF(0, 0, view.getWidth(), view.getHeight()).contains(popup.bounds));
    }

    private static int[][] singleLineWell(boolean upperAnchor) {
        int[][] fixture = new int[20][10];
        for (int col = 0; col < 10; col++) {
            if (col < 3 || col > 6) fixture[19][col] = TetrisView.PIECE_J + 1;
        }
        if (upperAnchor) fixture[3][0] = 7;
        return fixture;
    }

    private void restore(int[][] fixture, int rotation, int x, int score, int lines, int level,
                         int combo, boolean backToBack) {
        for (int[] row : fixture) {
            boolean full = true;
            for (int cell : row) if (cell == 0) full = false;
            assertFalse("Fixtures cannot start with a completed line", full);
        }
        for (int offset = 0; offset < 4; offset++) {
            int row = rotation == 0 ? 1 : offset;
            int col = rotation == 0 ? x + offset : x + 2;
            assertEquals("The restored active I must not overlap a locked cell", 0, fixture[row][col]);
        }
        assertEquals(lines / 10 + 1, level);
        assertTrue(view.restoreSnapshot(fixture, TetrisView.PIECE_I, rotation, x, 0, NEXT.clone(),
                -1, score, lines, level, false, combo, backToBack));
    }

    private void finishClear() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals(NEXT[0], view.getCurrentPiece());
        assertFalse(view.isGameOver());
    }

    private void layout(int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private RecordingCanvas drawCommands() {
        RecordingCanvas canvas = new RecordingCanvas();
        view.onDraw(canvas);
        return canvas;
    }

    private static final class RoundRectCommand {
        final RectF rect;
        final int color;
        final Paint.Style style;
        final float strokeWidth;

        RoundRectCommand(RectF rect, Paint paint) {
            this.rect = new RectF(rect);
            color = paint.getColor();
            style = paint.getStyle();
            strokeWidth = paint.getStrokeWidth();
        }
    }

    private static final class TextCommand {
        final String text;
        final float x, y;
        final int alpha;
        final RectF bounds;

        TextCommand(String text, float x, float y, Paint paint) {
            this.text = text;
            this.x = x;
            this.y = y;
            alpha = paint.getAlpha();
            float width = paint.measureText(text);
            float left = x;
            if (paint.getTextAlign() == Paint.Align.CENTER) left -= width / 2f;
            else if (paint.getTextAlign() == Paint.Align.RIGHT) left -= width;
            Paint.FontMetrics metrics = paint.getFontMetrics();
            bounds = new RectF(left, y + metrics.top, left + width, y + metrics.bottom);
        }
    }

    private static final class RecordingCanvas extends Canvas {
        final List<RoundRectCommand> rects = new ArrayList<>();
        final List<TextCommand> texts = new ArrayList<>();
        final TreeSet<Float> verticalLines = new TreeSet<>();
        final TreeMap<Float, Float> horizontalLines = new TreeMap<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            rects.add(new RoundRectCommand(rect, paint));
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            texts.add(new TextCommand(text, x, y, paint));
        }

        @Override
        public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
            if (startX == stopX && stopY > startY) verticalLines.add(startX);
            if (startY == stopY && stopX > startX) horizontalLines.put(startY, paint.getStrokeWidth());
        }
    }
}

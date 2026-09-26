package com.gamecenter.app.tetris;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
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
import java.util.List;

/**
 * Explicit legal saved-position fixtures, loaded through public restoreSnapshot;
 * these are not claimed to be naturally played rounds. Public softDrop/hardDrop and
 * the real scheduled gravity/clear animation exercise the actual View pipeline.
 *
 * Local contracts: strings_game_tetris.xml declares Single=100, Tetris=800, times
 * level, and Perfect Clear support. TetrisView documents manual Soft Drop +1/cell
 * and Hard Drop +2/cell. TetrisRules documents first clear combo=1 with no bonus,
 * subsequent 50*(combo-1)*level, PC Single=800*level and Tetris=2000*level, with
 * B2B 1.5 already applied to the base by the caller. Expectations below use these
 * independent arithmetic values, never call the production score helper.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisScoringTest {
    private static final int ROWS = 20;
    private static final int COLS = 10;
    private static final int[] NEXT = {
            TetrisView.PIECE_O, TetrisView.PIECE_T, TetrisView.PIECE_L,
            TetrisView.PIECE_J, TetrisView.PIECE_S
    };

    private TetrisView view;
    private final List<String> events = new ArrayList<>();

    @Before
    public void setUp() {
        view = new TetrisView(ApplicationProvider.getApplicationContext());
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 360, 640);
        view.setDifficultyLevel(2);
        view.setOnActionEventListener(events::add);
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void scheduledGravityMovesThePieceWithoutAwardingManualDropPoints() {
        int[][] board = new int[ROWS][COLS];
        restore(board, 0, 3, 41, 0, 1, 0, false);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(999));
        assertEquals("Normal level-one gravity should not run before 1000ms", 0, view.getPieceY());
        assertEquals(41, view.getScore());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals("The real scheduled tick must move the active I down one row", 1, view.getPieceY());
        assertEquals("Passive gravity is not a player Soft Drop and earns no drop bonus", 41, view.getScore());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertEquals(2, view.getPieceY());
        assertEquals("Waiting for another gravity tick must not farm score", 41, view.getScore());
        assertEquals(0, view.getLines());
        assertGridEquals(board, view.getGrid());
        assertTrue(events.isEmpty());
    }

    @Test
    public void manualSoftAndHardDropsAwardOnlyTheirTravelDistanceAtAnyLevel() {
        // Empty post-clear board, level three, and existing B2B history are explicit
        // saved metadata. No-line locking resets combo but preserves B2B history.
        restore(new int[ROWS][COLS], 0, 3, 37, 20, 3, 2, true);
        for (int step = 1; step <= 3; step++) {
            assertTrue(view.softDrop());
            assertEquals(step, view.getPieceY());
            assertEquals("Manual Soft Drop adds exactly one point per cell, without a level multiplier",
                    37 + step, view.getScore());
        }

        view.hardDrop();

        // Horizontal I occupies local row one. Its anchor finishes at row 18;
        // after three manual soft steps, fifteen hard-drop rows remain.
        assertEquals("Manual Hard Drop adds two per remaining cell, without a level multiplier",
                37 + 3 + 15 * 2, view.getScore());
        int[][] expected = new int[ROWS][COLS];
        for (int column = 3; column <= 6; column++) expected[19][column] = TetrisView.PIECE_I + 1;
        assertGridEquals(expected, view.getGrid());
        assertEquals(20, view.getLines());
        assertEquals(3, view.getLevel());
        assertEquals(0, view.getCombo());
        assertTrue("A no-line lock must preserve the existing B2B history", view.isBackToBack());
        assertEquals(NEXT[0], view.getCurrentPiece());
        assertTrue(events.isEmpty());
    }

    @Test
    public void aSingleLineThatEmptiesTheBoardReceivesItsPerfectClearBonusOnce() {
        int[][] board = new int[ROWS][COLS];
        // Six floor cells and a four-cell horizontal gap; falling I fills row 19.
        for (int column = 0; column < COLS; column++) {
            if (column < 3 || column > 6) board[19][column] = TetrisView.PIECE_J + 1;
        }
        restore(board, 0, 3, 0, 0, 1, 0, false);

        view.hardDrop();
        finishClear();

        assertGridEquals(new int[ROWS][COLS], view.getGrid());
        assertEquals("18 hard-drop rows + Single 100 + level-one Single Perfect Clear 800",
                18 * 2 + 100 + 800, view.getScore());
        assertEquals(1, view.getLines());
        assertEquals(1, view.getCombo());
        assertFalse("An ordinary Single does not continue difficult-clear B2B", view.isBackToBack());
        assertEquals(1L, countEvent("Perfect Clear!"));
        assertEquals(1L, countEvent("Single"));
        assertRewardsStayStable();
    }

    @Test
    public void fourLinePerfectClearAddsPcToExistingComboAndB2BUsingTheCurrentLevel() {
        int[][] board = fourRowWell(false);
        restore(board, 1, 2, 137, 10, 2, 1, true);

        view.hardDrop();
        finishClear();

        assertGridEquals(new int[ROWS][COLS], view.getGrid());
        // Sixteen manually dropped rows: 32, independent of level.
        // B2B Tetris: 800*1.5*2=2400; second consecutive clear combo: 50*1*2=100.
        // Four-line PC: 2000*2=4000, without a second B2B multiplier.
        assertEquals("Drop, B2B base, combo and PC must each be added exactly once",
                137 + 16 * 2 + 800 * 3 / 2 * 2 + 50 * 2 + 2000 * 2, view.getScore());
        assertEquals(14, view.getLines());
        assertEquals(2, view.getLevel());
        assertEquals(2, view.getCombo());
        assertTrue(view.isBackToBack());
        assertEquals(1L, countEvent("Perfect Clear!"));
        assertEquals(1L, countEvent("Combo x2"));
        assertEquals(1L, events.stream().filter(event -> event.startsWith("Back-to-Back ")).count());
        assertRewardsStayStable();
    }

    @Test
    public void fourClearedRowsWithAnUpperAnchorDoNotQualifyAsPerfectClear() {
        int[][] board = fourRowWell(true);
        restore(board, 1, 2, 137, 10, 2, 1, true);

        view.hardDrop();
        finishClear();

        int[][] expected = new int[ROWS][COLS];
        expected[6][9] = TetrisView.PIECE_Z + 1;
        assertGridEquals(expected, view.getGrid());
        assertEquals("The same B2B/Combo clear with one surviving anchor must receive no PC bonus",
                137 + 16 * 2 + 800 * 3 / 2 * 2 + 50 * 2, view.getScore());
        assertEquals(14, view.getLines());
        assertEquals(2, view.getCombo());
        assertTrue(view.isBackToBack());
        assertEquals(0L, countEvent("Perfect Clear!"));
        assertRewardsStayStable();
    }

    private static int[][] fourRowWell(boolean upperAnchor) {
        int[][] board = new int[ROWS][COLS];
        for (int row = 16; row < ROWS; row++) {
            for (int column = 0; column < COLS; column++) {
                if (column != 4) board[row][column] = (row + column) % 7 + 1;
            }
        }
        if (upperAnchor) board[2][9] = TetrisView.PIECE_Z + 1;
        return board;
    }

    private void restore(int[][] board, int rotation, int x, int score, int lines, int level,
                         int combo, boolean backToBack) {
        for (int[] row : board) {
            int occupied = 0;
            for (int cell : row) if (cell != 0) occupied++;
            assertTrue("Fixtures must not already contain full rows", occupied < COLS);
        }
        // I rotation zero occupies local row one and columns zero..three;
        // rotation one occupies local column two and rows zero..three.
        for (int offset = 0; offset < 4; offset++) {
            int row = rotation == 0 ? 1 : offset;
            int column = rotation == 0 ? x + offset : x + 2;
            assertEquals("Restored active I must not overlap a fixed block", 0, board[row][column]);
        }
        assertEquals("Saved line count must agree with the saved level", lines / 10 + 1, level);
        assertTrue(view.restoreSnapshot(board, TetrisView.PIECE_I, rotation, x, 0,
                NEXT.clone(), -1, score, lines, level, false, combo, backToBack));
        assertGridEquals(board, view.getGrid());
        assertEquals(score, view.getScore());
        assertEquals(0, view.getPieceY());
        assertFalse(view.isPaused());
        assertFalse(view.isGameOver());
    }

    private void finishClear() {
        // Normal level-two gravity is 850ms; 400ms completes the 330ms clear
        // animation before a successor gravity tick can affect unrelated scoring.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals("Exactly one queued successor should spawn", NEXT[0], view.getCurrentPiece());
        assertFalse(view.isGameOver());
    }

    private void assertRewardsStayStable() {
        int score = view.getScore(), lines = view.getLines(), combo = view.getCombo();
        List<String> awarded = new ArrayList<>(events);
        // Total is 700ms since lock, still below either tested gravity interval.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals("Animation completion must not award a second score", score, view.getScore());
        assertEquals(lines, view.getLines());
        assertEquals(combo, view.getCombo());
        assertEquals("Action rewards must not be emitted twice", awarded, events);
    }

    private long countEvent(String event) {
        return events.stream().filter(event::equals).count();
    }

    private static void assertGridEquals(int[][] expected, int[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals("Expected board row " + row, expected[row], actual[row]);
        }
    }
}

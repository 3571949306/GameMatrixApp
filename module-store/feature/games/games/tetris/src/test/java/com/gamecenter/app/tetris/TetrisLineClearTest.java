package com.gamecenter.app.tetris;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Looper;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Explicit legal saved-position fixtures, not naturally played or seeded games.
 * A vertical I starts at row 0 in an open column; only the public restoreSnapshot
 * loads the fixture. Locking uses the actual drawn DROP control and real View
 * touch dispatch. Public getters observe outcomes after the real animation loop.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisLineClearTest {
    private static final int ROWS = 20;
    private static final int COLS = 10;
    private static final int I_COLOR = TetrisView.PIECE_I + 1;
    private static final int I_COLUMN = 4;
    private static final int DROP_DISTANCE = 16;
    private static final int[] SAVED_NEXT = {
            TetrisView.PIECE_O, TetrisView.PIECE_T, TetrisView.PIECE_L,
            TetrisView.PIECE_J, TetrisView.PIECE_S
    };

    private TetrisView view;

    @Before
    public void setUp() {
        view = new TetrisView(ApplicationProvider.getApplicationContext());
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 360, 640);
        view.setDifficultyLevel(2);
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void singleBottomLinePreservesEveryUpperBlockAndItsColor() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 19);
        restore(fixture);

        touchControl(4);
        finishAndCheckOneLock(fixture, 1, 100);

        assertEquals("The high blue block must move down one row", 5, view.getGrid()[4][1]);
        assertEquals("The yellow block must move down one row", 2, view.getGrid()[11][8]);
        assertEquals("The low orange block must survive above the cleared floor", 4, view.getGrid()[19][0]);
        assertEquals(I_COLOR, view.getGrid()[19][I_COLUMN]);
    }

    @Test
    public void adjacentTwoLinesCompactTheUpperStackByExactlyTwoRows() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 18, 19);
        restore(fixture);

        touchControl(4);
        finishAndCheckOneLock(fixture, 2, 300);

        assertEquals(5, view.getGrid()[5][1]);
        assertEquals(2, view.getGrid()[12][8]);
        assertEquals(I_COLOR, view.getGrid()[18][I_COLUMN]);
        assertEquals(I_COLOR, view.getGrid()[19][I_COLUMN]);
    }

    @Test
    public void nonAdjacentLinesPreserveMiddleAndBottomRowsInOrder() throws Exception {
        int[][] fixture = coloredUpperStack();
        fixture[17][6] = 3;
        fixture[17][8] = 6;
        fixture[19][0] = 7;
        fixture[19][9] = 2;
        completeRowsExceptIColumn(fixture, 16, 18);
        restore(fixture);

        touchControl(4);
        finishAndCheckOneLock(fixture, 2, 300);

        assertEquals("The retained middle row falls through only the lower deleted row", 3,
                view.getGrid()[18][6]);
        assertEquals(6, view.getGrid()[18][8]);
        assertEquals("A nonfull bottom row must not be deleted or shifted", 7, view.getGrid()[19][0]);
        assertEquals(2, view.getGrid()[19][9]);
        assertEquals(I_COLOR, view.getGrid()[19][I_COLUMN]);
    }

    @Test
    public void fourLinesKeepTheRemainingStackAndAwardOneTetris() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 16, 17, 18, 19);
        restore(fixture);

        touchControl(4);
        // Existing colored upper blocks mean this is deliberately not a Perfect Clear.
        finishAndCheckOneLock(fixture, 4, 800);

        assertEquals(5, view.getGrid()[7][1]);
        assertEquals(2, view.getGrid()[14][8]);
        assertTrue(view.isBackToBack());
    }

    @Test
    public void aLockWithNoCompletedLineKeepsTheEntireBoardAndAddsFourCells() throws Exception {
        int[][] fixture = coloredUpperStack();
        fixture[19][9] = 7;
        restore(fixture);

        touchControl(4);
        finishAndCheckOneLock(fixture, 0, 0);

        assertEquals(5, view.getGrid()[3][1]);
        assertEquals(2, view.getGrid()[10][8]);
        assertEquals(4, view.getGrid()[18][0]);
        assertEquals(7, view.getGrid()[19][9]);
        assertEquals(0, view.getCombo());
    }

    @Test
    public void repeatedDropDuringLineAnimationCannotRewardOrLockAgain() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 19);
        restore(fixture);
        touchControl(4);
        State afterLock = new State(view);

        // No clock advancement: all extra taps occur within the same pending clear.
        for (int tap = 0; tap < 4; tap++) touchControl(4);

        afterLock.assertMatches(view);
        finishAndCheckOneLock(fixture, 1, 100);
    }

    @Test
    public void holdDuringLineAnimationCannotConsumeNextOrReplaceTheLockedPiece() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 19);
        restore(fixture);
        touchControl(4);
        State afterLock = new State(view);

        touchControl(0);

        afterLock.assertMatches(view);
        finishAndCheckOneLock(fixture, 1, 100);
        assertEquals(-1, view.getHoldPiece());
        assertFalse("After the clear finishes, the new piece must have its normal Hold allowance",
                view.isHoldUsedThisTurn());
    }

    @Test
    public void pausingPendingLineClearFreezesBoardQueueAndRewardsUntilResume() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 19);
        restore(fixture);
        touchControl(4);
        State afterLock = new State(view);
        view.pauseGame();

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        assertTrue(view.isPaused());
        afterLock.assertMatches(view);
        view.resumeGame();
        finishAndCheckOneLock(fixture, 1, 100);
        assertFalse(view.isPaused());
    }

    @Test
    public void fastestGravitySkipsPendingClearThenContinuesForExactlyOneSuccessor() throws Exception {
        int[][] fixture = coloredUpperStack();
        completeRowsExceptIColumn(fixture, 19);
        view.setDifficultyLevel(4);
        // Explicit legal saved-position fixture: 190 cleared lines correspond to level 20.
        // Master gravity is clamped to 60ms, so several ticks fall inside the clear animation.
        assertTrue(view.restoreSnapshot(fixture, TetrisView.PIECE_I, 1, 2, 0,
                SAVED_NEXT.clone(), -1, 0, 190, 20, false, 0, false));
        assertGridEquals(fixture, view.getGrid());
        touchControl(4);
        assertEquals(191, view.getLines());
        assertEquals("One first level-20 Single plus the 16-cell manual hard drop",
                100 * 20 + DROP_DISTANCE * 2, view.getScore());
        State pendingClear = new State(view);

        // 60/120/180/240/300ms gravity ticks must neither relock nor consume NEXT.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        pendingClear.assertMatches(view);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        int[][] locked = copyGrid(fixture);
        for (int row = DROP_DISTANCE; row < ROWS; row++) locked[row][I_COLUMN] = I_COLOR;
        int[][] expected = survivingRowsAtBottom(locked);
        assertGridEquals(expected, view.getGrid());
        assertEquals(occupiedCells(fixture) + 4 - COLS, occupiedCells(view.getGrid()));
        assertEquals(191, view.getLines());
        assertEquals(20, view.getLevel());
        assertEquals(SAVED_NEXT[0], view.getCurrentPiece());
        List<Integer> afterClearNext = new ArrayList<>(view.getNextQueue());
        assertEquals(5, afterClearNext.size());
        assertEquals(Arrays.asList(SAVED_NEXT[1], SAVED_NEXT[2], SAVED_NEXT[3], SAVED_NEXT[4]),
                afterClearNext.subList(0, 4));
        int afterClearY = view.getPieceY();

        // Allow a fresh full gravity interval after animation completion. This verifies
        // eventual motion without requiring the next piece to fall at its spawn instant.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120));
        assertTrue("Skipping gravity during a clear must not leave the successor stalled",
                view.getPieceY() > afterClearY);
        assertGridEquals(expected, view.getGrid());
        assertEquals(191, view.getLines());
        assertEquals(SAVED_NEXT[0], view.getCurrentPiece());
        assertEquals(afterClearNext, new ArrayList<>(view.getNextQueue()));
        assertFalse(view.isGameOver());
    }

    private int[][] coloredUpperStack() {
        int[][] fixture = new int[ROWS][COLS];
        fixture[3][1] = 5;
        fixture[10][8] = 2;
        fixture[18][0] = 4;
        return fixture;
    }

    private void completeRowsExceptIColumn(int[][] fixture, int... fullRows) {
        for (int row : fullRows) {
            for (int col = 0; col < COLS; col++) {
                fixture[row][col] = col == I_COLUMN ? 0 : (row + col) % 7 + 1;
            }
        }
    }

    private void restore(int[][] fixture) {
        assertEquals("No fixture may already contain a completed line", 0, countFullRows(fixture));
        for (int row = 0; row < ROWS; row++) {
            assertEquals("The entire falling I column must be open", 0, fixture[row][I_COLUMN]);
        }
        // I rotation 1 occupies its local column 2 at rows 0..3. x=2 places it
        // in board column 4. It falls exactly 16 rows onto the empty floor gap.
        assertTrue(view.restoreSnapshot(fixture, TetrisView.PIECE_I, 1, 2, 0,
                SAVED_NEXT.clone(), -1, 0, 0, 1, false, 0, false));
        assertGridEquals(fixture, view.getGrid());
        assertEquals(0, view.getPieceY());
        assertEquals(TetrisView.PIECE_I, view.getCurrentPiece());
        assertEquals(1, view.getCurrentRotation());
        assertEquals(0, view.getScore());
        assertFalse(view.isPaused());
        assertFalse(view.isGameOver());
    }

    private void finishAndCheckOneLock(int[][] fixture, int clearedRows, int baseLineScore) {
        int[][] locked = copyGrid(fixture);
        for (int row = DROP_DISTANCE; row < ROWS; row++) locked[row][I_COLUMN] = I_COLOR;
        assertEquals("The known landing must complete exactly the specified rows", clearedRows,
                countFullRows(locked));
        int[][] expected = survivingRowsAtBottom(locked);
        // 400ms completes the 330ms animation while remaining below normal 1000ms gravity.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertGridEquals(expected, view.getGrid());
        assertEquals("Every noncleared cell must survive, including each distinct color",
                occupiedCells(fixture) + 4 - clearedRows * COLS, occupiedCells(view.getGrid()));
        assertEquals(clearedRows, view.getLines());
        // Independently specified: 16 manually dropped cells * 2, plus first level-one
        // Single/Double/Tetris reward. No combo, B2B multiplier or Perfect Clear applies.
        assertEquals(DROP_DISTANCE * 2 + baseLineScore, view.getScore());
        assertEquals(1, view.getLevel());
        assertEquals(clearedRows == 0 ? 0 : 1, view.getCombo());
        assertEquals("Exactly one successor must spawn", SAVED_NEXT[0], view.getCurrentPiece());
        List<Integer> next = new ArrayList<>(view.getNextQueue());
        assertEquals(5, next.size());
        assertEquals(Arrays.asList(SAVED_NEXT[1], SAVED_NEXT[2], SAVED_NEXT[3], SAVED_NEXT[4]),
                next.subList(0, 4));
        State completed = new State(view);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        completed.assertMatches(view);
    }

    private void touchControl(int index) throws Exception {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(image));
        } finally {
            image.recycle();
        }
        Field controls = TetrisView.class.getDeclaredField("ctrlBtnRects");
        controls.setAccessible(true);
        RectF button = ((RectF[]) controls.get(view))[index];
        assertNotNull("Production drawing must place the real screen control", button);
        assertTrue(button.width() > 0 && button.height() > 0);
        long downTime = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent touch = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    button.centerX(), button.centerY(), 0);
            try {
                assertTrue(view.dispatchTouchEvent(touch));
            } finally {
                touch.recycle();
            }
        }
    }

    private static int[][] survivingRowsAtBottom(int[][] locked) {
        List<int[]> surviving = new ArrayList<>();
        for (int[] row : locked) if (!isFull(row)) surviving.add(row.clone());
        int[][] result = new int[ROWS][COLS];
        int first = ROWS - surviving.size();
        for (int index = 0; index < surviving.size(); index++) result[first + index] = surviving.get(index);
        return result;
    }

    private static boolean isFull(int[] row) {
        for (int cell : row) if (cell == 0) return false;
        return true;
    }

    private static int countFullRows(int[][] grid) {
        int count = 0;
        for (int[] row : grid) if (isFull(row)) count++;
        return count;
    }

    private static int occupiedCells(int[][] grid) {
        int count = 0;
        for (int[] row : grid) for (int cell : row) if (cell != 0) count++;
        return count;
    }

    private static int[][] copyGrid(int[][] grid) {
        int[][] copy = new int[grid.length][];
        for (int row = 0; row < grid.length; row++) copy[row] = grid[row].clone();
        return copy;
    }

    private static void assertGridEquals(int[][] expected, int[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals("Preserved board row " + row, expected[row], actual[row]);
        }
    }

    private static final class State {
        private final int[][] grid;
        private final int score, lines, level, piece, rotation, x, y, hold, combo;
        private final boolean holdUsed, backToBack, gameOver;
        private final List<Integer> next;

        State(TetrisView board) {
            grid = board.getGrid();
            score = board.getScore();
            lines = board.getLines();
            level = board.getLevel();
            piece = board.getCurrentPiece();
            rotation = board.getCurrentRotation();
            x = board.getPieceX();
            y = board.getPieceY();
            hold = board.getHoldPiece();
            combo = board.getCombo();
            holdUsed = board.isHoldUsedThisTurn();
            backToBack = board.isBackToBack();
            gameOver = board.isGameOver();
            next = new ArrayList<>(board.getNextQueue());
        }

        void assertMatches(TetrisView board) {
            assertGridEquals(grid, board.getGrid());
            assertEquals("An already locked piece cannot earn a second reward", score, board.getScore());
            assertEquals("A pending full row cannot be counted twice", lines, board.getLines());
            assertEquals(level, board.getLevel());
            assertEquals(piece, board.getCurrentPiece());
            assertEquals(rotation, board.getCurrentRotation());
            assertEquals(x, board.getPieceX());
            assertEquals(y, board.getPieceY());
            assertEquals(hold, board.getHoldPiece());
            assertEquals(combo, board.getCombo());
            assertEquals(holdUsed, board.isHoldUsedThisTurn());
            assertEquals(backToBack, board.isBackToBack());
            assertEquals(gameOver, board.isGameOver());
            assertEquals("A single clear must consume NEXT only once", next,
                    new ArrayList<>(board.getNextQueue()));
        }
    }
}

package com.gamecenter.app.tetris;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regressions for the project's own eight annotated rotation-transition rows;
 * these do not certify conformance to an external SRS/Guideline specification.
 *
 * The seven-piece CCW cycle uses the natural first bag. Named wall/floor scenarios
 * use explicit legal spawn snapshots, then public movement and the real drawn CW
 * control: independent empty boards ensure a prior test piece cannot obstruct the
 * floor. No reflection, private state writes, seeded RNG or production table reads.
 * CCW is tested through its public spare API; the normal UI exposes only CW.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisRotationKickTest {
    private static final int[] NEXT = {
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
    public void tUsesItsAnnotatedCwKickAtTheRightWallAndFloor() {
        assertRightFloorCw(TetrisView.PIECE_T);
    }

    @Test
    public void jUsesItsAnnotatedCwKickAtTheRightWallAndFloor() {
        assertRightFloorCw(TetrisView.PIECE_J);
    }

    @Test
    public void lUsesItsAnnotatedCwKickAtTheRightWallAndFloor() {
        assertRightFloorCw(TetrisView.PIECE_L);
    }

    @Test
    public void sUsesItsAnnotatedCwKickAtTheRightWallAndFloor() {
        assertRightFloorCw(TetrisView.PIECE_S);
    }

    @Test
    public void zUsesItsAnnotatedCwKickAtTheRightWallAndFloor() {
        assertRightFloorCw(TetrisView.PIECE_Z);
    }

    @Test
    public void iCwUsesTheFirstLegalCandidateInItsOwnAnnotatedTransition() {
        restoreSpawn(TetrisView.PIECE_I, new int[20][10]);
        for (int step = 0; step < 17; step++) assertTrue(view.softDrop());
        assertPose(0, 3, 17);
        RoundState before = new RoundState(view);

        touchRotateControl();

        // I at (3,17), r0 -> r1: (0,0),(-2,0),(1,0) cross the floor.
        // Local I 0->1 fourth candidate (-2,-1) is legal at (1,16).
        // A reverse-row lookup instead reaches (2,15), also legal but incorrect.
        assertPose(1, 1, 16);
        before.assertStateExceptPose(view);
    }

    @Test
    public void everyNaturalFirstBagPieceCanCompleteAllFourPublicCcwTransitions() {
        view.startGame();
        Set<Integer> seen = new HashSet<>();
        for (int turn = 0; turn < 7; turn++) {
            int piece = view.getCurrentPiece();
            assertTrue("The natural first bag must contain each piece once", seen.add(piece));
            assertPose(0, 3, 0);
            RoundState before = new RoundState(view);
            for (int expected : new int[]{3, 2, 1, 0}) {
                // This public spare API currently has no normal on-screen binding.
                view.rotateCCW();
                assertPose(expected, 3, 0);
                before.assertStateExceptPose(view);
            }
            view.hardDrop();
            assertFalse("Seven centered spawn-shape pieces must fit below the spawn area", view.isGameOver());
            assertEquals(0, view.getLines());
            assertEquals("One natural piece must lock per completed CCW cycle", (turn + 1) * 4,
                    occupied(view.getGrid()));
        }
        assertEquals(new HashSet<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6)), seen);
    }

    @Test
    public void tCcwReturnsFromTheLeftWallUsingItsPositiveOneColumnKick() {
        restoreSpawn(TetrisView.PIECE_T, new int[20][10]);
        touchRotateControl();
        assertPose(1, 3, 0);
        for (int step = 0; step < 4; step++) assertTrue(view.moveLeft());
        assertFalse(view.moveLeft());
        assertPose(1, -1, 0);
        RoundState before = new RoundState(view);

        view.rotateCCW();

        // Local T 1->0: zero offset crosses the left wall; (+1,0) succeeds.
        assertPose(0, 0, 0);
        before.assertStateExceptPose(view);
    }

    @Test
    public void iCcwReturnsFromTheLeftWallUsingItsDistinctPositiveTwoColumnKick() {
        restoreSpawn(TetrisView.PIECE_I, new int[20][10]);
        touchRotateControl();
        assertPose(1, 3, 0);
        for (int step = 0; step < 5; step++) assertTrue(view.moveLeft());
        assertFalse(view.moveLeft());
        assertPose(1, -2, 0);
        RoundState before = new RoundState(view);

        view.rotateCCW();

        // Local I 1->0: zero offset crosses the wall; (+2,0) is the first legal kick.
        assertPose(0, 0, 0);
        before.assertStateExceptPose(view);
    }

    @Test
    public void blockedCwWithNoLegalCandidateLeavesTheEntireRoundUnchanged() {
        int[][] board = new int[20][10];
        board[2][4] = TetrisView.PIECE_O + 1;
        board[0][3] = TetrisView.PIECE_J + 1;
        board[2][5] = TetrisView.PIECE_Z + 1;
        restoreSpawn(TetrisView.PIECE_T, board);
        RoundState before = new RoundState(view);

        touchRotateControl();

        // For local T 0->1, (0,0) and (-1,+1) hit (4,2); (-1,0) hits (3,0);
        // (0,-2) and (-1,-2) cross the top. No candidate may commit any pose.
        assertPose(0, 3, 0);
        before.assertStateExceptPose(view);
    }

    private void assertRightFloorCw(int piece) {
        restoreSpawn(piece, new int[20][10]);
        for (int step = 0; step < 4; step++) assertTrue(view.moveRight());
        assertFalse("Spawn shape already touches the right wall", view.moveRight());
        for (int step = 0; step < 18; step++) assertTrue(view.softDrop());
        assertFalse("The active piece is at the floor but is not locked yet", view.softDrop());
        assertPose(0, 7, 18);
        assertEquals("No locked blocks may obscure this wall/floor scenario", 0, occupied(view.getGrid()));
        RoundState before = new RoundState(view);

        touchRotateControl();

        // In this file's annotated JLSTZ 0->1 row, the first three candidates
        // (0,0),(-1,0),(-1,+1) cross the floor; fourth (0,-2) is legal.
        // The incorrect reverse row has only positive-x/up-one or down-two
        // alternatives, all blocked by the right wall or floor.
        assertPose(1, 7, 16);
        before.assertStateExceptPose(view);
    }

    private void restoreSpawn(int piece, int[][] board) {
        // Explicit geometrically valid saved state. Every fixture uses rotation
        // zero and the ordinary (3,0) spawn; subsequent poses come from public play.
        assertTrue(view.restoreSnapshot(board, piece, 0, 3, 0, NEXT.clone(),
                -1, 0, 0, 1, false, 0, false));
        assertTrue(Arrays.deepEquals(board, view.getGrid()));
        assertEquals(piece, view.getCurrentPiece());
        assertPose(0, 3, 0);
        assertFalse(view.isGameOver());
        assertFalse(view.isPaused());
    }

    private void assertPose(int rotation, int x, int y) {
        assertEquals("Expected rotation state from the local transition contract", rotation,
                view.getCurrentRotation());
        assertEquals("Expected first legal kick's horizontal anchor", x, view.getPieceX());
        assertEquals("Expected first legal kick's vertical anchor", y, view.getPieceY());
    }

    private void touchRotateControl() {
        RecordingCanvas canvas = new RecordingCanvas();
        view.onDraw(canvas);
        RectF target = canvas.rotateButton();
        assertTrue(new RectF(0, 0, view.getWidth(), view.getHeight()).contains(target));
        long downTime = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent touch = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    target.centerX(), target.centerY(), 0);
            try {
                assertTrue("The actual drawn rotate control must consume its touch",
                        view.dispatchTouchEvent(touch));
            } finally {
                touch.recycle();
            }
        }
    }

    private static int occupied(int[][] board) {
        int count = 0;
        for (int[] row : board) for (int value : row) if (value != 0) count++;
        return count;
    }

    private static final class RoundState {
        final int piece, score, lines, level, combo, hold;
        final boolean backToBack, holdUsed, paused, gameOver;
        final int[][] board;
        final List<Integer> next;

        RoundState(TetrisView view) {
            piece = view.getCurrentPiece();
            score = view.getScore();
            lines = view.getLines();
            level = view.getLevel();
            combo = view.getCombo();
            hold = view.getHoldPiece();
            backToBack = view.isBackToBack();
            holdUsed = view.isHoldUsedThisTurn();
            paused = view.isPaused();
            gameOver = view.isGameOver();
            board = view.getGrid();
            next = new ArrayList<>(view.getNextQueue());
        }

        void assertStateExceptPose(TetrisView view) {
            assertEquals("Rotation cannot consume the active piece", piece, view.getCurrentPiece());
            assertEquals("Rotation cannot award drop or line-clear points", score, view.getScore());
            assertEquals(lines, view.getLines());
            assertEquals(level, view.getLevel());
            assertEquals(combo, view.getCombo());
            assertEquals(hold, view.getHoldPiece());
            assertEquals(backToBack, view.isBackToBack());
            assertEquals(holdUsed, view.isHoldUsedThisTurn());
            assertEquals(paused, view.isPaused());
            assertEquals(gameOver, view.isGameOver());
            assertTrue("Rotation cannot change any locked cell", Arrays.deepEquals(board, view.getGrid()));
            assertEquals("Rotation cannot consume NEXT", next, new ArrayList<>(view.getNextQueue()));
        }
    }

    private static final class RecordingCanvas extends Canvas {
        final List<RectF> controls = new ArrayList<>();
        RectF rotateLabel;

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            if (paint.getColor() == 0x55000000) controls.add(new RectF(rect));
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            if ("⟳".equals(text)) rotateLabel = new RectF(x, y, x, y);
        }

        RectF rotateButton() {
            assertNotNull("The production onDraw must place its rotate label", rotateLabel);
            for (RectF rect : controls) {
                if (rect.contains(rotateLabel.left, rotateLabel.top)) return rect;
            }
            throw new AssertionError("No actual drawn control contains the rotate label");
        }
    }
}

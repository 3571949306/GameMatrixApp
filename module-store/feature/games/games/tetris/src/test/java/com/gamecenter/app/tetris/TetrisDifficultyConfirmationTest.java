package com.gamecenter.app.tetris;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Real Fragment and confirmation buttons; the live board is changed only by real touch. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisDifficultyConfirmationTest {
    private ActivityController<GameHostActivity> controller;
    private TetrisModuleFragment fragment;
    private TetrisView board;
    private SharedPreferences prefs;
    private AlertDialog dialog;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Explicit preference fixture in Robolectric's sandbox, not device user data.
        context.getSharedPreferences("tetris_module", Context.MODE_PRIVATE).edit().clear().commit();
        prefs = ModuleScopedPreferences.get(context, "tetris", "tetris_module");
        prefs.edit().clear().putInt("last_difficulty", 2).commit();
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        fragment = new TetrisModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "tetris-difficulty").commitNow();
        View root = fragment.requireView();
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 900);
        // Execute the Fragment's posted startGame, without advancing the live gravity clock
        // or attempting to drain the perpetual render loop to the end of all future tasks.
        shadowOf(Looper.getMainLooper()).idle();
        board = (TetrisView) fragmentField("tetrisView");
        assertTrue("The real Fragment must have started a falling piece", board.getCurrentPiece() >= 0);
        assertEquals(2, board.getDifficultyLevel());
        assertEquals(2, prefs.getInt("last_difficulty", -1));
        assertFalse(board.isGameOver());
        hardDropThroughActualControl();
        assertEquals("One real hard drop must leave exactly four locked cells", 4, occupiedCells(board.getGrid()));
        assertTrue("The current round must have observable progress before opening the dialog", board.getScore() > 0);
    }

    @After
    public void tearDown() {
        try {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        } finally {
            if (controller != null) controller.pause().stop().destroy();
            if (prefs != null) prefs.edit().clear().commit();
        }
    }

    @Test
    public void cancelMasterDifficultyKeepsNormalViewPreferenceHighlightAndCurrentRound() throws Exception {
        RoundSnapshot before = new RoundSnapshot(board);
        List<Integer> colorsBefore = difficultyColors();

        openMasterConfirmation();
        clickDialogButton(DialogInterface.BUTTON_NEGATIVE);

        assertEquals("Cancel must keep the live View at normal difficulty", 2, board.getDifficultyLevel());
        assertEquals("Cancel must not persist the unconfirmed master difficulty", 2,
                prefs.getInt("last_difficulty", -1));
        assertEquals("Cancel must keep the selected difficulty's real button colors", colorsBefore, difficultyColors());
        assertSame("Cancel must retain the actual live View", board, fragmentField("tetrisView"));
        before.assertMatches(board);
    }

    @Test
    public void onlyConfirmingMasterDifficultyChangesSelectionAndStartsFreshRound() throws Exception {
        RoundSnapshot before = new RoundSnapshot(board);
        List<Integer> colorsBefore = difficultyColors();

        openMasterConfirmation();

        assertEquals("An open confirmation is not permission to change the live difficulty", 2,
                board.getDifficultyLevel());
        assertEquals("An open confirmation must not persist the requested difficulty", 2,
                prefs.getInt("last_difficulty", -1));
        assertEquals(colorsBefore, difficultyColors());
        // A modal may pause the clock, but must preserve all current-round content.
        before.assertContentMatches(board);

        clickDialogButton(DialogInterface.BUTTON_POSITIVE);

        assertEquals(4, board.getDifficultyLevel());
        assertEquals(4, prefs.getInt("last_difficulty", -1));
        List<Integer> colorsAfter = difficultyColors();
        // Each difficulty contributes background and text colors; normal is index 1,
        // master index 3. Their active/inactive styles must swap after confirmation.
        assertEquals(colorsBefore.subList(2, 4), colorsAfter.subList(6, 8));
        assertEquals(colorsBefore.subList(6, 8), colorsAfter.subList(2, 4));
        assertEquals("Confirm must reset the actual View score", 0, board.getScore());
        assertEquals("Confirm must clear the previously locked piece", 0, occupiedCells(board.getGrid()));
        assertEquals(0, board.getLines());
        assertEquals(1, board.getLevel());
        assertEquals(-1, board.getHoldPiece());
        assertEquals(5, board.getNextQueue().size());
        assertTrue(board.getCurrentPiece() >= 0);
        assertFalse(board.isPaused());
        assertFalse(board.isGameOver());
    }

    @Test
    public void activeRoundWaitsForConfirmationAndCancelResumesItsGravity() {
        RoundSnapshot before = new RoundSnapshot(board);

        openMasterConfirmation();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        before.assertContentMatches(board);
        assertTrue("A confirmation must suspend the live round while the player decides", board.isPaused());
        clickDialogButton(DialogInterface.BUTTON_NEGATIVE);
        before.assertMatches(board);
        assertFalse("Cancel must resume a round that was active before the confirmation", board.isPaused());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue("Normal gravity must actually resume after cancellation", board.getPieceY() > before.y);
    }

    @Test
    public void cancelConfirmationPreservesAnAlreadyPausedRound() {
        pauseThroughActualButton();
        RoundSnapshot before = new RoundSnapshot(board);

        openMasterConfirmation();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        before.assertMatches(board);
        clickDialogButton(DialogInterface.BUTTON_NEGATIVE);

        assertTrue("Cancellation must not undo the player's earlier explicit pause", board.isPaused());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        before.assertMatches(board);
    }

    @Test
    public void confirmedNewRoundAfterManualPauseResumesAcrossActivityPause() {
        pauseThroughActualButton();
        openMasterConfirmation();
        clickDialogButton(DialogInterface.BUTTON_POSITIVE);
        assertFalse("A confirmed new round must start playing", board.isPaused());
        assertEquals(4, board.getDifficultyLevel());
        assertEquals(0, occupiedCells(board.getGrid()));
        RoundSnapshot freshRound = new RoundSnapshot(board);

        controller.pause();
        assertTrue("The activity pause must pause the new live round", board.isPaused());
        controller.resume();

        assertFalse("The old round's manual-pause flag must not keep the new round paused", board.isPaused());
        freshRound.assertMatches(board);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertTrue("Master gravity must run after the new round returns to foreground",
                board.getPieceY() > freshRound.y);
    }

    @Test
    public void backDismissesConfirmationWithoutChangingDifficultyOrCurrentRound() {
        RoundSnapshot before = new RoundSnapshot(board);
        openMasterConfirmation();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));

        dialog.onBackPressed();
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(dialog.isShowing());
        before.assertMatches(board);
        assertEquals(2, board.getDifficultyLevel());
        assertEquals(2, prefs.getInt("last_difficulty", -1));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue("Back must resume the previously active round", board.getPieceY() > before.y);
    }

    @Test
    public void returningToForegroundWithConfirmationStillOpenKeepsRoundPaused() {
        RoundSnapshot before = new RoundSnapshot(board);
        openMasterConfirmation();
        controller.pause();
        controller.resume();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        assertTrue(dialog.isShowing());
        assertTrue("Foregrounding must not run gravity behind the pending confirmation", board.isPaused());
        before.assertContentMatches(board);
        clickDialogButton(DialogInterface.BUTTON_NEGATIVE);
        before.assertMatches(board);
    }

    @Test
    public void dismissingConfirmationInBackgroundWaitsUntilForegroundToResume() {
        RoundSnapshot before = new RoundSnapshot(board);
        openMasterConfirmation();
        controller.pause();
        dialog.dismiss();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        assertFalse(dialog.isShowing());
        assertTrue("Dismissal must not reactivate a background game", board.isPaused());
        before.assertContentMatches(board);
        controller.resume();
        before.assertMatches(board);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue("The active round resumes only when its host returns", board.getPieceY() > before.y);
    }

    @Test
    public void destroyingTheFragmentDismissesConfirmationAndStopsItsOldView() {
        RoundSnapshot before = new RoundSnapshot(board);
        openMasterConfirmation();
        controller.get().getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        assertFalse("A removed game must not leave an actionable restart dialog", dialog.isShowing());
        before.assertContentMatches(board);
        // A late repeat dismissal must not bring the detached game back to life.
        dialog.dismiss();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        before.assertContentMatches(board);
    }

    @Test
    public void removingAFragmentBeforePostedStartDoesNotStartItsDetachedView() throws Exception {
        controller.get().getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
        fragment = new TetrisModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "tetris-fast-close").commitNow();
        TetrisView unstartedView = (TetrisView) fragmentField("tetrisView");
        assertEquals(-1, unstartedView.getCurrentPiece());
        controller.get().getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        assertEquals("A late startup callback must not create a round in a detached View", -1,
                unstartedView.getCurrentPiece());
        assertEquals(0, occupiedCells(unstartedView.getGrid()));
    }

    private void pauseThroughActualButton() {
        FrameLayout gameContainer = (FrameLayout) ((LinearLayout) fragment.requireView()).getChildAt(2);
        assertTrue(gameContainer.getChildAt(1) instanceof ImageButton);
        assertTrue("The production pause Button must execute its real listener",
                gameContainer.getChildAt(1).performClick());
        assertTrue(board.isPaused());
    }

    private void openMasterConfirmation() {
        Button master = (Button) difficultyBar().getChildAt(3);
        assertEquals("大师", master.getText().toString());
        assertTrue("The real master difficulty Button must run its listener", master.performClick());
        Dialog latest = ShadowDialog.getLatestDialog();
        assertTrue("Production must show an actual AppCompat confirmation", latest instanceof AlertDialog);
        dialog = (AlertDialog) latest;
        assertTrue(dialog.isShowing());
    }

    private void clickDialogButton(int which) {
        Button button = dialog.getButton(which);
        assertNotNull("The real confirmation must expose its action Button", button);
        assertTrue(button.isEnabled());
        assertTrue(button.performClick());
        // AppCompat dispatches its listener/dismissal through the main Handler.
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("The actual action must dismiss the confirmation", dialog.isShowing());
    }

    private void hardDropThroughActualControl() throws Exception {
        assertTrue(board.getWidth() > 0 && board.getHeight() > 0);
        Bitmap image = Bitmap.createBitmap(board.getWidth(), board.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            // Production drawing computes its real on-screen control rectangles.
            board.draw(new Canvas(image));
        } finally {
            image.recycle();
        }
        Field controls = TetrisView.class.getDeclaredField("ctrlBtnRects");
        controls.setAccessible(true);
        RectF drop = ((RectF[]) controls.get(board))[4];
        assertNotNull("Production drawing must lay out the hard-drop control", drop);
        assertTrue(drop.width() > 0 && drop.height() > 0);
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(downTime, MotionEvent.ACTION_DOWN, drop.centerX(), drop.centerY());
        dispatchTouch(downTime, MotionEvent.ACTION_UP, drop.centerX(), drop.centerY());
    }

    private void dispatchTouch(long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            assertTrue("The real TetrisView must consume its control gesture", board.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private LinearLayout difficultyBar() {
        return (LinearLayout) ((LinearLayout) fragment.requireView()).getChildAt(1);
    }

    private List<Integer> difficultyColors() {
        List<Integer> colors = new ArrayList<>();
        LinearLayout bar = difficultyBar();
        assertEquals(4, bar.getChildCount());
        for (int index = 0; index < bar.getChildCount(); index++) {
            Button button = (Button) bar.getChildAt(index);
            assertTrue(button.getBackground() instanceof ColorDrawable);
            colors.add(((ColorDrawable) button.getBackground()).getColor());
            colors.add(button.getCurrentTextColor());
        }
        return colors;
    }

    private Object fragmentField(String name) throws Exception {
        Field field = TetrisModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fragment);
    }

    private static int occupiedCells(int[][] grid) {
        int result = 0;
        for (int[] row : grid) for (int cell : row) if (cell != 0) result++;
        return result;
    }

    private static final class RoundSnapshot {
        final int[][] grid;
        final int score, lines, level, piece, rotation, x, y, hold;
        final boolean holdUsed, paused, gameOver;
        final List<Integer> next;

        RoundSnapshot(TetrisView view) {
            grid = view.getGrid();
            score = view.getScore();
            lines = view.getLines();
            level = view.getLevel();
            piece = view.getCurrentPiece();
            rotation = view.getCurrentRotation();
            x = view.getPieceX();
            y = view.getPieceY();
            hold = view.getHoldPiece();
            holdUsed = view.isHoldUsedThisTurn();
            paused = view.isPaused();
            gameOver = view.isGameOver();
            next = new ArrayList<>(view.getNextQueue());
        }

        void assertMatches(TetrisView view) {
            assertContentMatches(view);
            assertEquals(paused, view.isPaused());
        }

        void assertContentMatches(TetrisView view) {
            int[][] actual = view.getGrid();
            assertEquals(grid.length, actual.length);
            for (int row = 0; row < grid.length; row++) assertArrayEquals("Locked board row " + row, grid[row], actual[row]);
            assertEquals(score, view.getScore());
            assertEquals(lines, view.getLines());
            assertEquals(level, view.getLevel());
            assertEquals(piece, view.getCurrentPiece());
            assertEquals(rotation, view.getCurrentRotation());
            assertEquals(x, view.getPieceX());
            assertEquals(y, view.getPieceY());
            assertEquals(hold, view.getHoldPiece());
            assertEquals(holdUsed, view.isHoldUsedThisTurn());
            assertEquals(gameOver, view.isGameOver());
            assertEquals(next, new ArrayList<>(view.getNextQueue()));
        }
    }

    /** Load the same host resource table used by the compile-only host R classes. */
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
                Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
                while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) {
                    root = root.getParent();
                }
                assertNotNull("Locate the repository containing the host resource task output", root);
                Path resourceApk = root.resolve("app/build/intermediates/linked_resources_binary_format/"
                        + "debug/processDebugResources/linked-resources-binary-format-debug.ap_");
                assertTrue("The existing compile dependency must generate host resources: " + resourceApk,
                        Files.isRegularFile(resourceApk));
                try {
                    // This module compiles against the host R.jar and packages no AppCompat
                    // resources. Real Dialog inflation therefore needs the corresponding host
                    // resource APK, not stubs for strings, themes or Dialog implementation.
                    AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
                    int cookie = (int) AssetManager.class.getMethod("addAssetPath", String.class)
                            .invoke(assets, resourceApk.toString());
                    assertTrue("Host resource APK must be accepted by Android AssetManager", cookie != 0);
                    hostResources = new Resources(assets, delegate.getDisplayMetrics(), delegate.getConfiguration());
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot load real host resources for AppCompat Dialog", exception);
                }
            }
            return hostResources;
        }
    }
}

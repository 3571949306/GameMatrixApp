package com.gamecenter.app.tetris;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.gamecenter.app.R;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Real Fragment, natural 7-bag and real pause/settings/list/dialog listeners.
 * Only sandbox preferences select normal difficulty; no board/seed/private state
 * is injected. Reuses the real host resource Activity from the confirmation tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisPauseAndRulesTest {
    private ActivityController<TetrisDifficultyConfirmationTest.GameHostActivity> controller;
    private TetrisModuleFragment fragment;
    private TetrisView board;
    private SharedPreferences prefs;
    private final List<AlertDialog> shownDialogs = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("tetris_module", Context.MODE_PRIVATE).edit().clear().commit();
        prefs = ModuleScopedPreferences.get(context, "tetris", "tetris_module");
        prefs.edit().clear().putInt("last_difficulty", 2).commit();
        controller = Robolectric.buildActivity(TetrisDifficultyConfirmationTest.GameHostActivity.class).setup();
        installFreshFragment();
    }

    @After
    public void tearDown() {
        try {
            for (AlertDialog dialog : shownDialogs) if (dialog.isShowing()) dialog.dismiss();
            shadowOf(Looper.getMainLooper()).idle();
        } finally {
            if (controller != null) controller.pause().stop().destroy();
            if (prefs != null) prefs.edit().clear().commit();
        }
    }

    @Test
    public void tappingPauseOverlayResumesTheFragmentAndItsButtonAcrossActivityReturn() throws Exception {
        RoundContent before = new RoundContent(board);
        pauseThroughButton();
        assertTrue((Boolean) fragmentField("paused"));
        assertPauseIcon(android.R.drawable.ic_media_play);

        drawBoard();
        float x = board.getWidth() / 2f;
        float y = board.getHeight() / 2f;
        for (RectF button : controlRects()) {
            assertFalse("Tap the actual pause overlay, outside the bottom control buttons", button.contains(x, y));
        }
        tapBoard(x, y);
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse("The real overlay tap must resume the View", board.isPaused());
        assertFalse("Overlay resume must clear the Fragment's manual-pause intent",
                (Boolean) fragmentField("paused"));
        assertPauseIcon(android.R.drawable.ic_media_pause);
        before.assertMatches(board);
        controller.pause();
        assertTrue(board.isPaused());
        controller.resume();
        assertFalse("A later foreground transition must not reapply stale manual pause", board.isPaused());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue("The resumed round must actually fall", board.getPieceY() > before.y);
    }

    @Test
    public void settingsCancelFreezesTheActiveRoundThenRestoresItsGravity() {
        RoundContent before = new RoundContent(board);
        AlertDialog settings = openSettings();

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        before.assertMatches(board);
        assertTrue("Reading settings must pause the current round", board.isPaused());
        clickDialogButton(settings, DialogInterface.BUTTON_NEGATIVE);
        assertFalse(board.isPaused());
        before.assertMatches(board);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(board.getPieceY() > before.y);
    }

    @Test
    public void settingsBackPreservesThePlayersExistingManualPause() throws Exception {
        pauseThroughButton();
        RoundContent before = new RoundContent(board);
        AlertDialog settings = openSettings();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        before.assertMatches(board);

        backFrom(settings);

        assertTrue(board.isPaused());
        assertTrue((Boolean) fragmentField("paused"));
        assertPauseIcon(android.R.drawable.ic_media_play);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        before.assertMatches(board);
    }

    @Test
    public void selectingRulesKeepsTheRoundPausedUntilTheRulesDialogCloses() {
        RoundContent before = new RoundContent(board);
        AlertDialog settings = openSettings();
        AlertDialog rules = chooseRulesFromActualList(settings);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

        assertFalse("Opening rules replaces the settings list", settings.isShowing());
        assertTrue(rules.isShowing());
        before.assertMatches(board);
        assertTrue("Closing the parent list must not resume underneath its rules dialog", board.isPaused());
        backFrom(rules);
        assertFalse(board.isPaused());
        before.assertMatches(board);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(board.getPieceY() > before.y);
    }

    @Test
    public void settingsSurviveForegroundingAndBackgroundRulesDismissWaitsToResume() {
        RoundContent before = new RoundContent(board);
        AlertDialog settings = openSettings();
        controller.pause();
        controller.resume();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertTrue(settings.isShowing());
        assertTrue("Foregrounding must not run a round behind its settings", board.isPaused());
        before.assertMatches(board);

        AlertDialog rules = chooseRulesFromActualList(settings);
        controller.pause();
        rules.dismiss();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertFalse(rules.isShowing());
        assertTrue("A background rules dismissal must not start gravity", board.isPaused());
        before.assertMatches(board);
        controller.resume();
        assertFalse(board.isPaused());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(board.getPieceY() > before.y);
    }

    @Test
    public void removingTheFragmentClosesSettingsOrRulesWithoutRestartingItsOldView() throws Exception {
        for (boolean selectRules : new boolean[]{false, true}) {
            if (selectRules) installFreshFragment();
            RoundContent before = new RoundContent(board);
            AlertDialog settings = openSettings();
            AlertDialog visible = selectRules ? chooseRulesFromActualList(settings) : settings;

            controller.get().getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));

            assertFalse("Removing the game must close its " + (selectRules ? "rules" : "settings"),
                    visible.isShowing());
            assertFalse(settings.isShowing());
            before.assertMatches(board);
            visible.dismiss();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            before.assertMatches(board);
        }
    }

    private void installFreshFragment() throws Exception {
        fragment = new TetrisModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "tetris-pause-rules").commitNow();
        View root = fragment.requireView();
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 900);
        shadowOf(Looper.getMainLooper()).idle();
        board = (TetrisView) fragmentField("tetrisView");
        assertFalse(board.isPaused());
        assertFalse(board.isGameOver());
        assertEquals(2, board.getDifficultyLevel());
        drawBoard();
        RectF drop = controlRects()[4];
        tapBoard(drop.centerX(), drop.centerY());
        assertEquals("Make actual progress with one real DROP before each dialog", 4,
                occupiedCells(board.getGrid()));
        assertTrue(board.getScore() > 0);
    }

    private void pauseThroughButton() {
        assertTrue(findImageButton("暂停或继续游戏").performClick());
        assertTrue(board.isPaused());
    }

    private void assertPauseIcon(int resourceId) {
        ImageButton button = findImageButton("暂停或继续游戏");
        assertNotNull(button.getDrawable());
        assertEquals("The pause button's actual image must match the resumed/paused intent", resourceId,
                shadowOf(button.getDrawable()).getCreatedFromResId());
    }

    private AlertDialog openSettings() {
        assertTrue(findImageButton("游戏设置与规则").performClick());
        AlertDialog settings = latestDialog();
        assertNotNull(settings.getListView());
        assertEquals(3, settings.getListView().getAdapter().getCount());
        return settings;
    }

    private AlertDialog chooseRulesFromActualList(AlertDialog settings) {
        ListView list = settings.getListView();
        assertNotNull(list);
        String rulesTitle = controller.get().getString(R.string.tetris_rules_title);
        int position = -1;
        for (int index = 0; index < list.getAdapter().getCount(); index++) {
            if (rulesTitle.contentEquals(String.valueOf(list.getAdapter().getItem(index)))) position = index;
        }
        assertTrue("The actual settings adapter must expose the rules item", position >= 0);
        list.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 600, 300);
        View row = list.getChildAt(position - list.getFirstVisiblePosition());
        assertNotNull("The real rules row must be laid out before clicking", row);
        assertTrue(list.performItemClick(row, position, list.getAdapter().getItemId(position)));
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog rules = latestDialog();
        assertNotSame(settings, rules);
        TextView message = rules.findViewById(android.R.id.message);
        assertNotNull("The second dialog must show the actual rules body", message);
        assertTrue(message.getText().toString().contains(controller.get().getString(R.string.tetris_rules_basic)));
        return rules;
    }

    private AlertDialog latestDialog() {
        Dialog latest = ShadowDialog.getLatestDialog();
        assertTrue(latest instanceof AlertDialog);
        AlertDialog dialog = (AlertDialog) latest;
        assertTrue(dialog.isShowing());
        shownDialogs.add(dialog);
        return dialog;
    }

    private void clickDialogButton(AlertDialog dialog, int which) {
        Button button = dialog.getButton(which);
        assertNotNull(button);
        assertTrue(button.performClick());
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
    }

    private void backFrom(AlertDialog dialog) {
        dialog.onBackPressed();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
    }

    private ImageButton findImageButton(String description) {
        List<View> pending = new ArrayList<>();
        pending.add(fragment.requireView());
        for (int index = 0; index < pending.size(); index++) {
            View view = pending.get(index);
            if (view instanceof ImageButton && description.contentEquals(String.valueOf(view.getContentDescription()))) {
                return (ImageButton) view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int child = 0; child < group.getChildCount(); child++) pending.add(group.getChildAt(child));
            }
        }
        throw new AssertionError("Missing real ImageButton: " + description);
    }

    private Object fragmentField(String name) throws Exception {
        Field field = TetrisModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fragment);
    }

    private RectF[] controlRects() throws Exception {
        Field field = TetrisView.class.getDeclaredField("ctrlBtnRects");
        field.setAccessible(true);
        return (RectF[]) field.get(board);
    }

    private void drawBoard() {
        Bitmap image = Bitmap.createBitmap(board.getWidth(), board.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            board.draw(new Canvas(image));
        } finally {
            image.recycle();
        }
    }

    private void tapBoard(float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent touch = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
            try {
                assertTrue(board.dispatchTouchEvent(touch));
            } finally {
                touch.recycle();
            }
        }
    }

    private static int occupiedCells(int[][] grid) {
        int count = 0;
        for (int[] row : grid) for (int cell : row) if (cell != 0) count++;
        return count;
    }

    private static final class RoundContent {
        final int[][] grid;
        final int score, lines, level, piece, rotation, x, y, hold, combo;
        final boolean holdUsed, backToBack, gameOver;
        final List<Integer> next;

        RoundContent(TetrisView view) {
            grid = view.getGrid();
            score = view.getScore();
            lines = view.getLines();
            level = view.getLevel();
            piece = view.getCurrentPiece();
            rotation = view.getCurrentRotation();
            x = view.getPieceX();
            y = view.getPieceY();
            hold = view.getHoldPiece();
            combo = view.getCombo();
            holdUsed = view.isHoldUsedThisTurn();
            backToBack = view.isBackToBack();
            gameOver = view.isGameOver();
            next = new ArrayList<>(view.getNextQueue());
        }

        void assertMatches(TetrisView view) {
            int[][] actual = view.getGrid();
            for (int row = 0; row < grid.length; row++) assertArrayEquals("Round board row " + row, grid[row], actual[row]);
            assertEquals("A modal must not advance the round score", score, view.getScore());
            assertEquals(lines, view.getLines());
            assertEquals(level, view.getLevel());
            assertEquals(piece, view.getCurrentPiece());
            assertEquals(rotation, view.getCurrentRotation());
            assertEquals(x, view.getPieceX());
            assertEquals(y, view.getPieceY());
            assertEquals(hold, view.getHoldPiece());
            assertEquals(combo, view.getCombo());
            assertEquals(holdUsed, view.isHoldUsedThisTurn());
            assertEquals(backToBack, view.isBackToBack());
            assertEquals(gameOver, view.isGameOver());
            assertEquals(next, new ArrayList<>(view.getNextQueue()));
        }
    }
}

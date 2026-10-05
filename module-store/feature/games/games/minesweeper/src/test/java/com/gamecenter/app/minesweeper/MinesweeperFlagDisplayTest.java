package com.gamecenter.app.minesweeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.gamecenter.app.R;
import com.gamecenter.app.database.AppDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Real Fragment header updates after a long press through its laid-out view hierarchy. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class MinesweeperFlagDisplayTest {
    private ActivityController<GameHostActivity> controller;
    private MinesweeperModuleFragment fragment;
    private AppDatabase database;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Keep the production GameUsageStore constructor and DAO within this JVM sandbox.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        setDatabaseSingleton(database);
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        fragment = new MinesweeperModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "minesweeper-flags").commitNow();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (controller != null) controller.pause().stop().destroy();
        } finally {
            setDatabaseSingleton(null);
            if (database != null) database.close();
        }
    }

    @Test
    public void easyHeaderTracksLongPressFlagAndUnflag() {
        assertHeaderTracksFlag(MinesweeperGame.DIFF_EASY, R.string.game_diff_easy, 10, 9, 9);
    }

    @Test
    public void normalHeaderTracksLongPressFlagAndUnflag() {
        assertHeaderTracksFlag(MinesweeperGame.DIFF_NORMAL, R.string.game_diff_normal, 40, 16, 16);
    }

    @Test
    public void hardHeaderTracksLongPressFlagAndUnflag() {
        assertHeaderTracksFlag(MinesweeperGame.DIFF_HARD, R.string.game_diff_hard, 99, 16, 30);
    }

    private void assertHeaderTracksFlag(int difficulty, int buttonId, int mines, int rows, int cols) {
        ViewGroup root = (ViewGroup) fragment.requireView();
        TextView difficultyButton = findText(root, controller.get().getString(buttonId));
        assertTrue("Difficulty selection must use the production Button", difficultyButton instanceof Button);
        assertTrue("The difficulty Button must invoke its real listener", difficultyButton.performClick());
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 900);

        MinesweeperView board = findBoard(root);
        assertEquals(difficulty, board.getDifficulty());
        assertEquals(mines, board.getMineCount());
        assertEquals(0, board.getFlaggedCount());
        assertTrue(board.isGameStarted());
        assertFalse(board.isGameOver());
        TextView header = findText(root, remainingText(mines));
        assertTrue("The production mine counter must be visible", header.isShown());

        longPressFirstCell(root, board, rows, cols);

        assertEquals("The real long press must flag exactly one cell", 1, board.getFlaggedCount());
        assertEquals("Flagging must immediately refresh the visible remaining-mine header",
                remainingText(mines - 1), header.getText().toString());

        longPressFirstCell(root, board, rows, cols);

        assertEquals("Long-pressing the same cell must remove its flag", 0, board.getFlaggedCount());
        assertEquals("Removing the flag must restore the same header",
                remainingText(mines), header.getText().toString());
        assertFalse("Flagging and unflagging must not end the round", board.isGameOver());
    }

    private void longPressFirstCell(ViewGroup root, MinesweeperView board, int rows, int cols) {
        assertTrue("Touch coordinates require a laid-out production board",
                board.isLaidOut() && board.getWidth() > 0 && board.getHeight() > 0);
        float cell = Math.min((float) board.getWidth() / cols, (float) board.getHeight() / rows);
        float offsetX = (board.getWidth() - cell * cols) / 2f;
        float offsetY = (board.getHeight() - cell * rows) / 2f;
        Rect bounds = new Rect(0, 0, board.getWidth(), board.getHeight());
        root.offsetDescendantRectToMyCoords(board, bounds);
        float x = bounds.left + offsetX + cell / 2f;
        float y = bounds.top + offsetY + cell / 2f;
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(root, downTime, MotionEvent.ACTION_DOWN, x, y);
        try {
            // Advance virtual time beyond the production 500 ms long-press timeout.
            // Do not invoke toggleFlag or the posted Runnable directly.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
        } finally {
            dispatchTouch(root, downTime, MotionEvent.ACTION_UP, x, y);
        }
    }

    private static void dispatchTouch(View root, long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            assertTrue("The real Fragment hierarchy must consume the gesture", root.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private String remainingText(int mines) {
        return controller.get().getString(R.string.game_mines_remaining_format, mines);
    }

    private static TextView findText(View root, String text) {
        List<TextView> matches = new ArrayList<>();
        for (View view : descendants(root)) {
            if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
                matches.add((TextView) view);
            }
        }
        assertEquals("Expected exactly one visible UI label: " + text, 1, matches.size());
        return matches.get(0);
    }

    private static MinesweeperView findBoard(View root) {
        MinesweeperView result = null;
        for (View view : descendants(root)) {
            if (view instanceof MinesweeperView) {
                assertTrue("The Fragment must contain only one game board", result == null);
                result = (MinesweeperView) view;
            }
        }
        assertNotNull("Missing production MinesweeperView", result);
        return result;
    }

    private static List<View> descendants(View root) {
        List<View> result = new ArrayList<>();
        result.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                result.addAll(descendants(group.getChildAt(i)));
            }
        }
        return result;
    }

    private static void setDatabaseSingleton(AppDatabase value) throws Exception {
        Field field = AppDatabase.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, value);
    }

    /** Supply host string IDs absent from the standalone module's resource table. */
    public static class GameHostActivity extends FragmentActivity {
        private Resources strings;

        @Override
        public Resources getResources() {
            if (strings == null) {
                Resources delegate = super.getResources();
                strings = new Resources(delegate.getAssets(), delegate.getDisplayMetrics(),
                        delegate.getConfiguration()) {
                    @Override
                    public String getString(int id) {
                        if (id == R.string.game_title_minesweeper) return "Minesweeper";
                        if (id == R.string.game_mines_remaining_init) return "Mines: 10";
                        if (id == R.string.game_wins_init) return "Wins: 0";
                        if (id == R.string.game_mines_status_init) return "Tap to reveal; long press to flag";
                        if (id == R.string.game_diff_easy) return "Easy";
                        if (id == R.string.game_diff_normal) return "Normal";
                        if (id == R.string.game_diff_hard) return "Hard";
                        if (id == R.string.game_btn_restart) return "Restart";
                        if (id == R.string.game_mines_lose) return "Mine hit";
                        return super.getString(id);
                    }

                    @Override
                    public String getString(int id, Object... args) {
                        if (id == R.string.game_mines_remaining_format) return "Mines: " + args[0];
                        if (id == R.string.game_wins_format) return "Wins: " + args[0];
                        if (id == R.string.game_mines_win_format) return "Won in " + args[0];
                        return super.getString(id, args);
                    }
                };
            }
            return strings;
        }
    }
}

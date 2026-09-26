package com.gamecenter.app.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

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
import java.util.ArrayList;
import java.util.List;

/** Real Fragment/Button flows; explicit generated-board fixtures are not natural play. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w360dp-h640dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PipelineUiConnectionTest {
    private ActivityController<GameHostActivity> controller;
    private PipelineModuleFragment fragment;
    private AppDatabase database;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        setField(AppDatabase.class, null, "INSTANCE", database);
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        fragment = new PipelineModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "pipeline-ui").commitNow();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (controller != null) controller.pause().stop().destroy();
        } finally {
            setField(AppDatabase.class, null, "INSTANCE", null);
            if (database != null) database.close();
        }
    }

    @Test
    public void realStartShowsBothCornerRolesAndVisibleRouteHintWithoutReplacingGlyphs() throws Exception {
        // This test uses the real random generator, without any board fixture.
        click((Button) fragmentField("btnStart"));
        layoutSmallWindow();

        assertTrue(game().isGameActive());
        assertEquals(4, game().getGridSize());
        assertEndpointRolesAndGlyphs();
    }

    @Test
    public void checkColorsActuallyReachablePipesIncludingCrosses() throws Exception {
        startFixture(false);
        click((Button) fragmentField("btnCheck"));

        assertTrue("A broken route cannot complete the level", game().isGameActive());
        // Only the first two cells are mutually connected from the top-left source.
        assertColor(0, 0, "colorPipeCorrect");
        assertColor(0, 1, "colorPipeCorrect"); // Reachable cross is not exempt from feedback.
        assertColor(0, 2, "colorPipeError");   // Target rotation matches, but its ports disconnect it.
        assertColor(2, 1, "colorPipeError");   // Isolated cross is also disconnected.
        assertColor(3, 3, "colorPipeError");
        assertVisibleRouteHint();
    }

    @Test
    public void rotatingAPipeClearsEveryOldRedGreenResultAndStaleFailureMessage() throws Exception {
        startFixture(false);
        click((Button) fragmentField("btnCheck"));
        int correct = (Integer) fragmentField("colorPipeCorrect");
        int error = (Integer) fragmentField("colorPipeError");
        boolean hasFeedback = false;
        for (Button button : pipeButtons()) {
            int color = backgroundColor(button);
            if (color == correct || color == error) hasFeedback = true;
        }
        assertTrue("The actual check Button must first create visible feedback", hasFeedback);

        click(pipeButtons()[2]); // The broken first-row vertical pipe rotates to horizontal.

        assertEquals(1, game().getMoveCount());
        assertEquals("The real rotate callback must update its pipe glyph", "─",
                pipeButtons()[2].getText().toString());
        int size = game().getGridSize();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                assertColor(row, col, game().getPipeType(row, col) == PipelineGame.PIPE_NONE
                        ? "colorPipeEmpty" : "colorPipe");
            }
        }
        TextView status = (TextView) fragmentField("tvStatus");
        assertFalse("A changed board must not keep the previous failed check as its status",
                status.getText().toString().contains("未连通"));
        assertVisibleRouteHint();
    }

    @Test
    public void nextLevelRebuildsCornerRolesWhenTheGridGrowsFromFourToFive() throws Exception {
        startFixture(true);
        assertEndpointRolesAndGlyphs();
        for (int completed = 1; completed <= 2; completed++) {
            Button[] previous = pipeButtons();
            Button previousEnd = previous[previous.length - 1];
            click((Button) fragmentField("btnCheck"));
            assertFalse("The actual check callback must settle the connected fixture", game().isGameActive());
            assertEquals(completed + 1, game().getCurrentLevel());
            assertVisibleRouteHint();
            Button next = (Button) fragmentField("btnStart");
            assertTrue("Progression must use the actual next-level Button", next.getText().toString().contains("下一关"));
            click(next);
            layoutSmallWindow();
            assertTrue(game().isGameActive());
            assertEquals(completed == 1 ? 4 : 5, game().getGridSize());
            assertNotSame(previousEnd, pipeButtons()[pipeButtons().length - 1]);
            assertNull("The old corner must be removed from the grid", previousEnd.getParent());
            assertEndpointRolesAndGlyphs();
        }
    }

    @Test
    public void fourFiveAndSixCellGridsAndActionButtonsFitA360By640DpWindow() throws Exception {
        startFixture(true);
        // Reach the 4x4, 5x5 and 6x6 layouts via real win/next Buttons. Only generated
        // boards are deterministic fixtures; progression and view construction are real.
        for (int level = 1; level <= 5; level++) {
            layoutSmallWindow();
            assertEquals(level <= 2 ? 4 : level <= 4 ? 5 : 6, game().getGridSize());
            assertFullyWithinAncestors((GridLayout) fragmentField("gridLayout"));
            for (Button cell : pipeButtons()) {
                assertFullyWithinAncestors(cell);
                if (cell.isEnabled()) assertMinimumTouchSize(cell);
            }
            Button check = (Button) fragmentField("btnCheck");
            assertFullyWithinAncestors(check);
            assertMinimumTouchSize(check);
            click(check);
            layoutSmallWindow();
            Button next = (Button) fragmentField("btnStart");
            assertFullyWithinAncestors(next);
            assertMinimumTouchSize(next);
            if (level < 5) click(next);
        }
    }

    private void startFixture(boolean connected) throws Exception {
        // Explicit UI fixture: fixed board generation plus no random initial rotations.
        // Connectivity, rotation, completion, statistics and all Fragment callbacks remain
        // production implementations. No call to private checkConnection/rebuildGrid occurs.
        setField(PipelineModuleFragment.class, fragment, "game", new FixtureGame(connected));
        click((Button) fragmentField("btnStart"));
        layoutSmallWindow();
    }

    private void assertEndpointRolesAndGlyphs() throws Exception {
        int size = game().getGridSize();
        Button[] buttons = pipeButtons();
        assertEquals(size * size, buttons.length);
        int starts = 0;
        int ends = 0;
        for (int index = 0; index < buttons.length; index++) {
            String description = String.valueOf(buttons[index].getContentDescription());
            if (description.contains("起点")) starts++;
            if (description.contains("终点")) ends++;
            assertEquals("Endpoint identification must preserve each visible pipe glyph",
                    game().getPipeChar(index / size, index % size), buttons[index].getText().toString());
        }
        assertEquals("Exactly one current cell must identify the source", 1, starts);
        assertEquals("Exactly one current cell must identify the destination", 1, ends);
        assertTrue(String.valueOf(buttons[0].getContentDescription()).contains("起点"));
        assertTrue(String.valueOf(buttons[buttons.length - 1].getContentDescription()).contains("终点"));
        assertTrue(buttons[0].isEnabled() && buttons[buttons.length - 1].isEnabled());
        assertVisibleRouteHint();
    }

    private void assertVisibleRouteHint() {
        TextView hint = null;
        for (View view : descendants(fragment.requireView())) {
            if (view instanceof TextView) {
                String text = ((TextView) view).getText().toString();
                if (text.contains("左上起点") && text.contains("右下终点")) {
                    hint = (TextView) view;
                    break;
                }
            }
        }
        assertNotNull("Keep a visible hint explaining 左上起点 → 右下终点", hint);
        assertTrue("The route instruction must be shown, not only accessibility metadata", hint.isShown());
    }

    private void layoutSmallWindow() {
        View root = fragment.requireView();
        float density = root.getResources().getDisplayMetrics().density;
        assertEquals("The fixture must expose 360dp display width", 360f,
                root.getResources().getDisplayMetrics().widthPixels / density, 0.1f);
        int width = Math.round(360 * density);
        int height = Math.round(640 * density);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void assertMinimumTouchSize(Button button) {
        int minimum = (int) Math.ceil(48 * button.getResources().getDisplayMetrics().density);
        assertTrue("Every enabled pipe/action Button must retain a 48dp touch target",
                button.getWidth() >= minimum && button.getHeight() >= minimum);
    }

    private void assertFullyWithinAncestors(View target) {
        assertTrue("The tested control must be visible and laid out",
                target.isShown() && target.isLaidOut() && target.getWidth() > 0 && target.getHeight() > 0);
        View root = fragment.requireView();
        View current = target;
        while (current != root && current.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) current.getParent();
            Rect childBounds = new Rect(0, 0, target.getWidth(), target.getHeight());
            parent.offsetDescendantRectToMyCoords(target, childBounds);
            Rect available = new Rect(0, 0, parent.getWidth(), parent.getHeight());
            if (parent.getClipToPadding()) {
                available.set(parent.getPaddingLeft(), parent.getPaddingTop(),
                        parent.getWidth() - parent.getPaddingRight(), parent.getHeight() - parent.getPaddingBottom());
            }
            assertTrue("Control " + target.getClass().getSimpleName() + " " + childBounds
                    + " must fit inside " + parent.getClass().getSimpleName() + " " + available,
                    available.contains(childBounds));
            current = parent;
        }
        assertEquals("The tested target must belong to this production Fragment", root, current);
    }

    private static void click(Button button) {
        assertTrue("Use a visible enabled production Button", button.isShown() && button.isEnabled());
        assertTrue("The real Button listener must run", button.performClick());
    }

    private void assertColor(int row, int col, String colorField) throws Exception {
        assertEquals("Pipe color at " + row + "," + col, (int) (Integer) fragmentField(colorField),
                backgroundColor(pipeButtons()[row * game().getGridSize() + col]));
    }

    private static int backgroundColor(Button button) {
        assertTrue("Production pipe color must be represented by its visible background",
                button.getBackground() instanceof ColorDrawable);
        return ((ColorDrawable) button.getBackground()).getColor();
    }

    private PipelineGame game() throws Exception { return (PipelineGame) fragmentField("game"); }
    private Button[] pipeButtons() throws Exception { return (Button[]) fragmentField("pipeButtons"); }

    private Object fragmentField(String name) throws Exception {
        Field field = PipelineModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fragment);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<View> descendants(View root) {
        List<View> result = new ArrayList<>();
        result.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(descendants(group.getChildAt(i)));
        }
        return result;
    }

    private static final class FixtureGame extends PipelineGame {
        private final boolean connected;

        FixtureGame(boolean connected) { this.connected = connected; }

        @Override
        public void startLevel() {
            super.startLevel(); // Preserve real level sizing, active state and move reset.
            int size = getGridSize();
            int[][] types = new int[size][size];
            int[][] rotations = new int[size][size];
            int[][] targets = new int[size][size];
            for (int col = 0; col < size - 1; col++) {
                types[0][col] = PIPE_STRAIGHT;
                rotations[0][col] = targets[0][col] = 1; // Horizontal across the top.
            }
            types[0][1] = PIPE_CROSS;
            rotations[0][1] = targets[0][1] = 0;
            types[0][size - 1] = PIPE_L;
            rotations[0][size - 1] = targets[0][size - 1] = 2; // ┐: left + down.
            for (int row = 1; row < size; row++) types[row][size - 1] = PIPE_STRAIGHT;
            types[size - 2][1] = PIPE_CROSS; // An allowed idle branch, unreachable from the source.
            if (!connected) {
                rotations[0][2] = targets[0][2] = 0; // Vertical pipe blocks the top-row route.
                targets[0][0] = 0; // Old answer-matching logic must not accidentally settle this fixture.
            }
            try {
                setField(PipelineGame.class, this, "pipeTypes", types);
                setField(PipelineGame.class, this, "pipeRotations", rotations);
                setField(PipelineGame.class, this, "targetRotations", targets);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Cannot install explicit generated-board fixture", error);
            }
        }

        @Override
        public int randomizeRotation(int row, int col) {
            try {
                Field field = PipelineGame.class.getDeclaredField("pipeRotations");
                field.setAccessible(true);
                return ((int[][]) field.get(this))[row][col];
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Cannot read explicit fixture rotation", error);
            }
        }
    }

    public static class GameHostActivity extends FragmentActivity { }
}

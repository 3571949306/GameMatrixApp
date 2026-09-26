package com.gamecenter.app.memory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gamecenter.app.R;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Real randomly dealt rounds, real Fragment buttons and the real 800 ms match callback.
 * The only game reflection reads and clones cardValues to choose legal matching clicks.
 * No deck, count, selection, listener, completion callback or game state is replaced.
 * Button.performClick verifies the Fragment's behavior; device touch reachability is a
 * separate instrumentation concern, not evidence supplied by these Robolectric tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class MemoryDifficultyTransitionTest {
    private ActivityController<GameHostActivity> controller;
    private MemoryModuleFragment fragment;
    private LinearLayout root;
    private GridLayout grid;
    private TextView status;
    private TextView stats;
    private Button start;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        fragment = new MemoryModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "memory-difficulty").commitNow();
        root = (LinearLayout) fragment.requireView();
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 900);
        shadowOf(Looper.getMainLooper()).idle();
        status = (TextView) root.getChildAt(2);
        stats = (TextView) root.getChildAt(3);
        grid = (GridLayout) root.getChildAt(4);
        start = (Button) root.getChildAt(5);
        assertEquals(fragment.getString(R.string.game_btn_start), start.getText().toString());
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void easyRoundFinishesItsEightPairsAfterSelectingHardThenStartsTwentyFourCards() throws Exception {
        beginRound(0, 16, 4);
        Round round = readRound();
        playPair(round.pairs.get(0));
        clickCard(round.pairs.get(1)[0]);
        String revealedFace = card(round.pairs.get(1)[0]).getText().toString();

        selectDifficulty(2);
        String selectionMessage = status.getText().toString();
        assertLiveProgressPreserved(round, revealedFace);
        clickCard(round.pairs.get(1)[1]);
        finishMatchDelay();
        for (int index = 2; index < round.pairs.size(); index++) playPair(round.pairs.get(index));

        // The old implementation changes the target to 12 while only 8 pairs exist:
        // this assertion fails at the player-visible soft lock, not just changed copy.
        assertCompletedRound(16, 8);
        assertTrue("A live difficulty selection must explain when it takes effect",
                selectionMessage.contains("下一局生效"));
        assertTrue(start.performClick());
        assertFreshRound(24, 6);
    }

    @Test
    public void hardRoundStillNeedsTwelvePairsAfterSelectingEasyThenStartsSixteenCards() throws Exception {
        beginRound(2, 24, 6);
        Round round = readRound();
        playPair(round.pairs.get(0));
        clickCard(round.pairs.get(1)[0]);
        String revealedFace = card(round.pairs.get(1)[0]).getText().toString();

        selectDifficulty(0);
        String selectionMessage = status.getText().toString();
        assertLiveProgressPreserved(round, revealedFace);
        clickCard(round.pairs.get(1)[1]);
        finishMatchDelay();
        for (int index = 2; index < 8; index++) playPair(round.pairs.get(index));

        // The old implementation declares a win here with 8 cards still unmatched.
        assertEquals("The existing 24-card round must remain active after only 8 pairs",
                View.GONE, start.getVisibility());
        assertEquals(8, enabledCards());
        assertFalse(status.getText().toString().contains("恭喜通关"));
        assertTrue(stats.getText().toString().contains("配对 8/12"));
        for (int index = 8; index < round.pairs.size(); index++) playPair(round.pairs.get(index));

        assertCompletedRound(24, 12);
        assertTrue("A live difficulty selection must explain when it takes effect",
                selectionMessage.contains("下一局生效"));
        assertTrue(start.performClick());
        assertFreshRound(16, 4);
    }

    @Test
    public void eachDifficultySelectedBeforeStartingCreatesAndCompletesItsFullDeck() throws Exception {
        int[] cardCounts = {16, 20, 24};
        int[] columnCounts = {4, 5, 6};
        for (int difficulty = 0; difficulty < cardCounts.length; difficulty++) {
            beginRound(difficulty, cardCounts[difficulty], columnCounts[difficulty]);
            Round round = readRound();
            for (int[] pair : round.pairs) playPair(pair);
            assertCompletedRound(cardCounts[difficulty], cardCounts[difficulty] / 2);
        }
    }

    private void beginRound(int difficulty, int expectedCards, int expectedColumns) {
        selectDifficulty(difficulty);
        assertEquals("A round starts only through the visible start/play-again button",
                View.VISIBLE, start.getVisibility());
        assertTrue(start.performClick());
        assertFreshRound(expectedCards, expectedColumns);
    }

    private void selectDifficulty(int index) {
        LinearLayout bar = (LinearLayout) root.getChildAt(1);
        assertEquals(3, bar.getChildCount());
        Button button = (Button) bar.getChildAt(index);
        int[] labels = {R.string.game_match_easy, R.string.game_match_normal, R.string.game_match_hard};
        assertEquals(fragment.getString(labels[index]), button.getText().toString());
        assertTrue(button.isEnabled());
        assertTrue("Use the production difficulty button and its real listener", button.performClick());
    }

    private void assertFreshRound(int cards, int columns) {
        assertEquals(cards, grid.getChildCount());
        assertEquals(columns, grid.getColumnCount());
        assertEquals(4, grid.getRowCount());
        assertEquals(View.GONE, start.getVisibility());
        assertTrue(stats.getText().toString().contains("配对 0/" + cards / 2));
        assertTrue(stats.getText().toString().contains("步数 0"));
        for (int index = 0; index < cards; index++) {
            assertTrue(card(index).isEnabled());
            assertEquals("?", card(index).getText().toString());
        }
    }

    private void assertLiveProgressPreserved(Round round, String revealedFace) throws Exception {
        assertEquals(round.buttons.size(), grid.getChildCount());
        assertArrayEquals("Changing pending difficulty must not redeal the active random deck",
                round.values, readCardValues());
        for (int index = 0; index < round.buttons.size(); index++) {
            assertSame("The existing card views and their progress must survive selection",
                    round.buttons.get(index), grid.getChildAt(index));
        }
        assertFalse(card(round.pairs.get(0)[0]).isEnabled());
        assertFalse(card(round.pairs.get(0)[1]).isEnabled());
        assertEquals(revealedFace, card(round.pairs.get(1)[0]).getText().toString());
        assertFalse(revealedFace.equals("?"));
        assertEquals("?", card(round.pairs.get(1)[1]).getText().toString());
        assertEquals(View.GONE, start.getVisibility());
    }

    private void assertCompletedRound(int cards, int pairs) {
        assertEquals("Matching every pair of the current deck must reveal play again",
                View.VISIBLE, start.getVisibility());
        assertEquals(fragment.getString(R.string.game_btn_play_again), start.getText().toString());
        assertTrue(status.getText().toString().contains("恭喜通关"));
        assertEquals(cards, grid.getChildCount());
        assertEquals(0, enabledCards());
        assertTrue(stats.getText().toString().contains("配对 " + pairs + "/" + pairs));
        assertTrue(stats.getText().toString().contains("步数 " + pairs + " "));
    }

    private void playPair(int[] pair) {
        clickCard(pair[0]);
        clickCard(pair[1]);
        finishMatchDelay();
        assertFalse(card(pair[0]).isEnabled());
        assertFalse(card(pair[1]).isEnabled());
    }

    private void clickCard(int index) {
        Button button = card(index);
        assertEquals(View.VISIBLE, button.getVisibility());
        assertTrue("Only currently unmatched legal cards are clicked", button.isEnabled());
        assertTrue("Use the production card Button and its real listener", button.performClick());
    }

    private void finishMatchDelay() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
    }

    private Button card(int index) {
        return (Button) grid.getChildAt(index);
    }

    private int enabledCards() {
        int count = 0;
        for (int index = 0; index < grid.getChildCount(); index++) if (card(index).isEnabled()) count++;
        return count;
    }

    private int[] readCardValues() throws Exception {
        Field values = MemoryModuleFragment.class.getDeclaredField("cardValues");
        values.setAccessible(true);
        return ((int[]) values.get(fragment)).clone();
    }

    private Round readRound() throws Exception {
        int[] values = readCardValues();
        assertEquals(grid.getChildCount(), values.length);
        Map<Integer, List<Integer>> indicesByValue = new TreeMap<>();
        List<Button> buttons = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            indicesByValue.computeIfAbsent(values[index], ignored -> new ArrayList<>()).add(index);
            buttons.add(card(index));
        }
        List<int[]> pairs = new ArrayList<>();
        for (List<Integer> indices : indicesByValue.values()) {
            assertEquals("The naturally shuffled deck must contain exactly two of each value", 2, indices.size());
            pairs.add(new int[] {indices.get(0), indices.get(1)});
        }
        assertEquals(values.length / 2, pairs.size());
        return new Round(values, buttons, pairs);
    }

    private static final class Round {
        final int[] values;
        final List<Button> buttons;
        final List<int[]> pairs;

        Round(int[] values, List<Button> buttons, List<int[]> pairs) {
            this.values = values;
            this.buttons = buttons;
            this.pairs = pairs;
        }
    }

    /** Use the real host resource table associated with the module's compile-only R.jar. */
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
                assertNotNull("Locate the repository containing the real host resource task output", repoRoot);
                Path resourceApk = repoRoot.resolve("app/build/intermediates/linked_resources_binary_format/"
                        + "debug/processDebugResources/linked-resources-binary-format-debug.ap_");
                assertTrue("The existing compile dependency must generate host resources: " + resourceApk,
                        Files.isRegularFile(resourceApk));
                try {
                    AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
                    int cookie = (int) AssetManager.class.getMethod("addAssetPath", String.class)
                            .invoke(assets, resourceApk.toString());
                    assertTrue("AssetManager must load the corresponding real host resources", cookie != 0);
                    hostResources = new Resources(assets, delegate.getDisplayMetrics(), delegate.getConfiguration());
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot load real host resources for Memory Fragment", exception);
                }
            }
            return hostResources;
        }
    }
}

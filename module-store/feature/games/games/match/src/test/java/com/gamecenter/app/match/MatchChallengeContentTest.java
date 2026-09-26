package com.gamecenter.app.match;

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
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gamecenter.app.R;
import com.gamecenter.app.SaveManager;

import org.json.JSONObject;
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
 * Additive challenge/HUD expectations, not a claim that the old game violates these new rules.
 * All rounds use naturally shuffled production decks, real Button listeners and the unchanged
 * 800 ms Handler. Game reflection only clones cardValues to choose legal face-up pairs.
 * No score, error, level, card state, listener, callback or save is injected to manufacture play.
 * The public SaveManager is cleared only in the isolated Robolectric application's setup.
 * Layout checks use actual measured children; device touch/screenshot validation is separate.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class,
        qualifiers = "w360dp-h640dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class MatchChallengeContentTest {
    // A bounded host-layout fixture, not a claim about a specific emulator's insets:
    // 640dp portrait minus 24dp status bar and 48dp navigation area. The Match title
    // remains a measured child, and the real host XML/theme have no extra ActionBar.
    private static final int CONTENT_HEIGHT_DP = 568;
    private static final String[] STAGES = {"熟悉牌面", "稳稳配对", "精准消除"};
    private ActivityController<GameHostActivity> controller;
    private MatchModuleFragment fragment;
    private LinearLayout root;
    private GridLayout grid;
    private TextView status;
    private TextView stats;
    private Button start;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        SaveManager.getInstance(controller.get()).deleteProgress("match");
        attachFreshFragment();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void freshReadyPreviewStatsAgreeWithEverySelectedDifficulty() {
        int[] pairs = {8, 10, 12};
        for (int difficulty = 0; difficulty < pairs.length; difficulty++) {
            selectDifficulty(difficulty);
            assertEquals("No round has begun while its preview changes", View.VISIBLE, start.getVisibility());
            assertEquals("A fresh READY selection cannot silently deal cards", 0, grid.getChildCount());
            String preview = status.getText().toString();
            assertTrue("The READY description must preview the selected deck: " + preview,
                    preview.contains("4x" + (difficulty + 4) + "（" + pairs[difficulty] + " 对）"));
            String displayed = stats.getText().toString();
            java.util.regex.Matcher pairCount = java.util.regex.Pattern
                    .compile("配对\\s+(\\d+)/(\\d+)").matcher(displayed);
            assertTrue("The existing statistics must expose its actual pair counter: " + displayed, pairCount.find());
            assertEquals("Fresh preview starts with zero matched pairs", "0", pairCount.group(1));
            assertEquals("Fresh READY statistics must agree with the selected deck: " + displayed,
                    String.valueOf(pairs[difficulty]), pairCount.group(2));
        }
    }

    @Test
    public void allThreeDifficultiesFinishNaturalDecksWithUnchangedLegacyRewards() throws Exception {
        int[] counts = {16, 20, 24};
        int[] columns = {4, 5, 6};
        int[] budgets = {6, 4, 2};
        int[] oldRewards = {76, 140, 192};
        int[] totals = {76, 216, 408};
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            String completedMessage = status.getText().toString();
            String completedStats = stats.getText().toString();
            selectDifficulty(difficulty);
            if (difficulty > 0) {
                assertTrue("Changing the next deck must retain the just-finished challenge result",
                        status.getText().toString().startsWith(completedMessage + "\n下一关使用"));
                assertEquals("Changing the next deck must retain the finished pairs, error goal, total and best",
                        completedStats, stats.getText().toString());
            }
            startRound(counts[difficulty], columns[difficulty]);
            assertGoal(difficulty + 1, budgets[difficulty], 0, true);
            Round round = readRound();
            for (int[] pair : round.pairs) playPair(pair);
            assertCompleted(difficulty + 1, counts[difficulty] / 2, 0, true,
                    oldRewards[difficulty], totals[difficulty], totals[difficulty]);
            assertEquals("Persist the original total, without adding a challenge bonus",
                    totals[difficulty], savedHighScore());
        }
    }

    @Test
    public void eachBudgetAllowsItsBoundaryAndOneExtraErrorStillClearsTheBoard() throws Exception {
        int[] errors = {6, 4, 2, 7, 5, 3};
        int[] budgets = {6, 4, 2, 6, 4, 2};
        int[] oldRewards = {58, 128, 210, 220, 305, 402};
        int[] totals = {58, 186, 396, 616, 921, 1323};
        for (int roundNumber = 0; roundNumber < errors.length; roundNumber++) {
            startRound(16, 4);
            assertGoal(roundNumber % 3 + 1, budgets[roundNumber], 0, true);
            Round round = readRound();
            for (int error = 1; error <= errors[roundNumber]; error++) {
                clickCard(round.pairs.get(0)[0]);
                clickCard(round.pairs.get(1)[0]);
                // Error progress is visible immediately, before the original delayed flip-back.
                assertGoal(roundNumber % 3 + 1, budgets[roundNumber], error,
                        error <= budgets[roundNumber]);
                assertEquals(View.GONE, start.getVisibility());
                finishDelay();
                assertEquals("?", card(round.pairs.get(0)[0]).getText().toString());
                assertEquals("?", card(round.pairs.get(1)[0]).getText().toString());
            }
            for (int[] pair : round.pairs) playPair(pair);
            assertCompleted(roundNumber + 1, 8, errors[roundNumber], roundNumber < 3,
                    oldRewards[roundNumber], totals[roundNumber], totals[roundNumber]);
        }
    }

    @Test
    public void pendingDifficultyAndRelayoutKeepTheCurrentDeckGoalAndProgress() throws Exception {
        startRound(16, 4);
        Round round = readRound();
        playPair(round.pairs.get(0));
        clickCard(round.pairs.get(1)[0]);
        String face = card(round.pairs.get(1)[0]).getText().toString();
        selectDifficulty(2);
        assertTrue(status.getText().toString().contains("下一关生效"));
        assertGoal(1, 6, 0, true);
        assertSameRound(round);
        assertEquals(face, card(round.pairs.get(1)[0]).getText().toString());
        assertEquals(View.INVISIBLE, card(round.pairs.get(0)[0]).getVisibility());
        assertEquals(View.INVISIBLE, card(round.pairs.get(0)[1]).getVisibility());
        assertEquals(4, grid.getColumnCount());

        // Reflow the actual production Grid with padding, without recreating card views.
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        layoutContent(CONTENT_HEIGHT_DP);
        assertSameRound(round);
        assertBoardAndControlsFit(true);
        assertEquals(face, card(round.pairs.get(1)[0]).getText().toString());
        clickCard(round.pairs.get(1)[1]);
        finishDelay();
        for (int index = 2; index < round.pairs.size(); index++) playPair(round.pairs.get(index));
        assertCompleted(1, 8, 0, true, 76, 76, 76);

        startRound(24, 6);
        assertGoal(2, 4, 0, true);
        Round next = readRound();
        for (int[] pair : next.pairs) playPair(pair);
        // The next harder board retains the old 12-pair reward, multiplied by level two.
        assertCompleted(2, 12, 0, true, 128, 204, 204);
    }

    @Test
    public void cumulativeAndBestAreVisibleAndBestSurvivesANewSession() throws Exception {
        assertTrue(stats.getText().toString().contains("累计 0"));
        assertTrue(stats.getText().toString().contains("最佳 0"));
        startRound(16, 4);
        for (int[] pair : readRound().pairs) playPair(pair);
        assertCompleted(1, 8, 0, true, 76, 76, 76);
        startRound(16, 4);
        for (int[] pair : readRound().pairs) playPair(pair);
        assertCompleted(2, 8, 0, true, 152, 228, 228);
        assertEquals(228, savedHighScore());

        controller.get().getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
        attachFreshFragment();
        assertTrue("A new session starts at zero while retaining the real saved best",
                stats.getText().toString().contains("累计 0"));
        assertTrue(stats.getText().toString().contains("最佳 228"));
        selectDifficulty(2);
        startRound(24, 6);
        for (int[] pair : readRound().pairs) playPair(pair);
        assertCompleted(1, 12, 0, true, 64, 64, 228);
        assertEquals("A lower new session must not erase the previous best", 228, savedHighScore());
    }

    @Test
    public void originalEightHundredMillisecondLockStillPreventsExtraMovesOrMatches() throws Exception {
        startRound(16, 4);
        Round round = readRound();
        int[] pair = round.pairs.get(0);
        clickCard(pair[0]);
        clickCard(pair[0]);
        assertTrue(stats.getText().toString().contains("步数 0"));
        clickCard(pair[1]);
        clickCard(round.pairs.get(1)[0]);
        assertEquals("?", card(round.pairs.get(1)[0]).getText().toString());
        assertTrue(stats.getText().toString().contains("步数 1"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(799));
        assertEquals(View.VISIBLE, card(pair[0]).getVisibility());
        assertEquals(View.VISIBLE, card(pair[1]).getVisibility());
        assertTrue(stats.getText().toString().contains("配对 0/8"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(View.INVISIBLE, card(pair[0]).getVisibility());
        assertEquals(View.INVISIBLE, card(pair[1]).getVisibility());
        assertTrue(stats.getText().toString().contains("配对 1/8"));
        assertGoal(1, 6, 0, true);
        for (int index = 1; index < round.pairs.size(); index++) playPair(round.pairs.get(index));
        assertCompleted(1, 8, 0, true, 76, 76, 76);
    }

    @Test
    public void actualLayoutKeepsCardsAndButtonsVisibleForAllThreePortraitBoards() throws Exception {
        int[] counts = {16, 20, 24};
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            selectDifficulty(difficulty);
            layoutContent(CONTENT_HEIGHT_DP); // Real host theme/container; 72dp reserved outside the module.
            assertBoardAndControlsFit(false);
            startRound(counts[difficulty], difficulty + 4);
            Round round = readRound();
            grid.setPadding(dp(8), dp(8), dp(8), dp(8));
            layoutContent(CONTENT_HEIGHT_DP);
            assertBoardAndControlsFit(true);
            assertSameRound(round);
            // Also model a smaller content allocation inside the same portrait window.
            // This is an actual view measurement check, not a standalone sizing formula.
            layoutContent(480);
            assertBoardAndControlsFit(true);
            assertSameRound(round);
            layoutContent(CONTENT_HEIGHT_DP);
            for (int[] pair : round.pairs) playPair(pair);
            layoutContent(CONTENT_HEIGHT_DP);
            assertBoardAndControlsFit(true);
            assertEquals(View.VISIBLE, start.getVisibility());
            assertSameRound(round);
        }
    }

    private void attachFreshFragment() {
        fragment = new MatchModuleFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment, "match-content").commitNow();
        root = (LinearLayout) fragment.requireView();
        assertEquals("Keep the existing six visual areas", 6, root.getChildCount());
        status = (TextView) root.getChildAt(2);
        stats = (TextView) root.getChildAt(3);
        grid = (GridLayout) root.getChildAt(4);
        start = (Button) root.getChildAt(5);
        layoutContent(CONTENT_HEIGHT_DP);
    }

    private void selectDifficulty(int difficulty) {
        LinearLayout bar = (LinearLayout) root.getChildAt(1);
        assertEquals(3, bar.getChildCount());
        Button button = (Button) bar.getChildAt(difficulty);
        int[] labels = {R.string.game_match_easy, R.string.game_match_normal, R.string.game_match_hard};
        assertEquals(fragment.getString(labels[difficulty]), button.getText().toString());
        assertTrue(button.performClick());
    }

    private void startRound(int count, int columns) {
        assertEquals(View.VISIBLE, start.getVisibility());
        assertTrue(start.performClick());
        layoutContent(CONTENT_HEIGHT_DP);
        assertEquals(count, grid.getChildCount());
        assertEquals(4, grid.getRowCount());
        assertEquals(columns, grid.getColumnCount());
        assertEquals(View.GONE, start.getVisibility());
        for (int index = 0; index < count; index++) {
            assertEquals(View.VISIBLE, card(index).getVisibility());
            assertEquals("?", card(index).getText().toString());
        }
        assertTrue(stats.getText().toString().contains("步数 0"));
        assertTrue(stats.getText().toString().contains("配对 0/" + count / 2));
    }

    private void assertGoal(int phase, int budget, int errors, boolean withinBudget) {
        String message = status.getText().toString();
        assertTrue(message, message.contains("阶段 " + phase + "/3"));
        assertTrue(message, message.contains(STAGES[phase - 1]));
        assertTrue(message, message.contains("失误≤" + budget));
        assertTrue(message, message.contains(withinBudget ? "目标内" : "已超出，仍可通关"));
        assertTrue(stats.getText().toString(), stats.getText().toString().contains("失误 " + errors + "/" + budget));
    }

    private void assertCompleted(int level, int pairs, int errors, boolean achieved,
            int reward, int total, int best) {
        assertEquals(View.VISIBLE, start.getVisibility());
        assertEquals("下一关 " + (level + 1), start.getText().toString());
        String message = status.getText().toString();
        assertTrue(message, message.contains("第 " + level + " 关通关"));
        assertTrue(message, message.contains(achieved ? "挑战达成" : "挑战未达成"));
        assertTrue(message, message.contains("阶段 " + ((level - 1) % 3 + 1) + "/3"));
        assertTrue(message, message.contains("+" + reward + " 分"));
        assertTrue(stats.getText().toString().contains("步数 " + (pairs + errors) + " "));
        assertTrue(stats.getText().toString().contains("配对 " + pairs + "/" + pairs));
        int[] completedBudgets = {6, 4, 2};
        int completedBudget = completedBudgets[(level - 1) % completedBudgets.length];
        assertTrue("The result HUD must retain the completed round's error goal: " + stats.getText(),
                stats.getText().toString().contains("失误 " + errors + "/" + completedBudget));
        assertTrue(stats.getText().toString().contains("累计 " + total));
        assertTrue(stats.getText().toString().contains("最佳 " + best));
        for (int index = 0; index < grid.getChildCount(); index++) {
            assertEquals(View.INVISIBLE, card(index).getVisibility());
        }
    }

    private void playPair(int[] pair) {
        clickCard(pair[0]);
        clickCard(pair[1]);
        finishDelay();
        assertEquals(View.INVISIBLE, card(pair[0]).getVisibility());
        assertEquals(View.INVISIBLE, card(pair[1]).getVisibility());
    }

    private void clickCard(int index) {
        Button button = card(index);
        assertEquals(View.VISIBLE, button.getVisibility());
        assertTrue(button.isEnabled());
        assertTrue("Use the production Button listener", button.performClick());
    }

    private void finishDelay() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
    }

    private int savedHighScore() throws Exception {
        String saved = SaveManager.getInstance(controller.get()).loadProgress("match");
        assertNotNull("The real public score store must contain a completed round", saved);
        return new JSONObject(saved).getInt("highScore");
    }

    private int dp(int value) {
        return Math.round(value * root.getResources().getDisplayMetrics().density);
    }

    private void layoutContent(int heightDp) {
        // A size change can request a follow-up pass after existing child sizes change.
        // Four passes are a bound, not proof of convergence: observe stability explicitly.
        List<Integer> previousGeometry = null;
        for (int pass = 0; pass < 4; pass++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY));
            root.layout(0, 0, dp(360), dp(heightDp));
            shadowOf(Looper.getMainLooper()).idle();
            List<Integer> currentGeometry = new ArrayList<>();
            appendMeasuredGeometry(root, currentGeometry);
            if (pass == 3) {
                assertEquals("The last two actual layout passes must have identical geometry",
                        previousGeometry, currentGeometry);
                assertNoPendingLayout(root);
            }
            previousGeometry = currentGeometry;
        }
    }

    private void appendMeasuredGeometry(View view, List<Integer> geometry) {
        geometry.add(view.getLeft());
        geometry.add(view.getTop());
        geometry.add(view.getRight());
        geometry.add(view.getBottom());
        geometry.add(view.getMeasuredWidth());
        geometry.add(view.getMeasuredHeight());
        geometry.add(view.getVisibility());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            geometry.add(group.getChildCount());
            for (int index = 0; index < group.getChildCount(); index++) {
                appendMeasuredGeometry(group.getChildAt(index), geometry);
            }
        } else {
            geometry.add(0);
        }
    }

    private void assertNoPendingLayout(View view) {
        // GONE subtrees do not participate in this layout. A hidden start button may
        // retain a request until it is shown again; that is not a live reflow loop.
        if (view.getVisibility() == View.GONE) return;
        assertFalse("The actual module hierarchy must settle without a pending layout request: "
                + view.getClass().getSimpleName() + " bounds=" + view.getLeft() + "," + view.getTop()
                + "," + view.getRight() + "," + view.getBottom(), view.isLayoutRequested());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) assertNoPendingLayout(group.getChildAt(index));
        }
    }

    private void assertBoardAndControlsFit(boolean checkCards) {
        Rect rootBounds = new Rect(0, 0, root.getWidth(), root.getHeight());
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child.getVisibility() == View.GONE) continue;
            assertTrue("Visual area must remain inside actual root: " + index,
                    rootBounds.contains(child.getLeft(), child.getTop(), child.getRight(), child.getBottom()));
        }
        LinearLayout bar = (LinearLayout) root.getChildAt(1);
        for (int index = 0; index < bar.getChildCount(); index++) assertFullVisible(bar.getChildAt(index));
        if (start.getVisibility() == View.VISIBLE) assertFullVisible(start);
        if (!checkCards) return;
        Rect boardContent = new Rect(grid.getPaddingLeft(), grid.getPaddingTop(),
                grid.getWidth() - grid.getPaddingRight(), grid.getHeight() - grid.getPaddingBottom());
        for (int index = 0; index < grid.getChildCount(); index++) {
            Button card = card(index);
            assertEquals("Cards stay square", card.getWidth(), card.getHeight());
            assertTrue("Normal portrait cards must retain a 48dp target", card.getWidth() >= dp(48));
            assertTrue("A real measured card must fit its padded board allocation: " + index,
                    boardContent.contains(card.getLeft(), card.getTop(), card.getRight(), card.getBottom()));
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
            assertTrue(card.getLeft() - margins.leftMargin >= boardContent.left);
            assertTrue(card.getTop() - margins.topMargin >= boardContent.top);
            assertTrue(card.getRight() + margins.rightMargin <= boardContent.right);
            assertTrue(card.getBottom() + margins.bottomMargin <= boardContent.bottom);
            if (card.getVisibility() == View.VISIBLE) assertFullVisible(card);
        }
    }

    private void assertFullVisible(View view) {
        Rect visible = new Rect();
        assertTrue("The actual view must have a visible rectangle", view.getGlobalVisibleRect(visible));
        assertEquals(view.getWidth(), visible.width());
        assertEquals(view.getHeight(), visible.height());
        if (view instanceof Button) {
            assertTrue("Every actual menu/start/card button must be at least 48dp wide", view.getWidth() >= dp(48));
            assertTrue("Every actual menu/start/card button must be at least 48dp high", view.getHeight() >= dp(48));
        }
    }

    private Button card(int index) {
        return (Button) grid.getChildAt(index);
    }

    private int[] readCardValues() throws Exception {
        Field field = MatchModuleFragment.class.getDeclaredField("cardValues");
        field.setAccessible(true);
        return ((int[]) field.get(fragment)).clone();
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
            assertEquals("Exactly two of each value in the real random deck", 2, indices.size());
            pairs.add(new int[] {indices.get(0), indices.get(1)});
        }
        assertEquals(values.length / 2, pairs.size());
        return new Round(values, buttons, pairs);
    }

    private void assertSameRound(Round round) throws Exception {
        assertArrayEquals("UI updates must not redeal the natural deck", round.values, readCardValues());
        assertEquals(round.buttons.size(), grid.getChildCount());
        for (int index = 0; index < round.buttons.size(); index++) {
            assertSame("Layout and pending difficulty preserve actual card identity",
                    round.buttons.get(index), grid.getChildAt(index));
        }
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

    /** Use the host's actual resource table matching the existing compile-only R.jar. */
    public static class GameHostActivity extends FragmentActivity {
        private Resources hostResources;
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // Use actual host styling and the actual FrameLayout XML, with no loader,
            // registry, network, or DynamicGameActivity lifecycle behavior in this fixture.
            setTheme(R.style.Theme_GameMatrixApp);
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_dynamic_game);
        }
        @Override
        public Resources getResources() {
            if (hostResources == null) {
                Resources delegate = super.getResources();
                Path repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();
                while (repoRoot != null && !Files.isRegularFile(repoRoot.resolve("settings.gradle"))) {
                    repoRoot = repoRoot.getParent();
                }
                assertNotNull("Locate the real host resource output", repoRoot);
                Path resourceApk = repoRoot.resolve("app/build/intermediates/linked_resources_binary_format/"
                        + "debug/processDebugResources/linked-resources-binary-format-debug.ap_");
                assertTrue("Compile dependency must generate host resources", Files.isRegularFile(resourceApk));
                try {
                    AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
                    int cookie = (int) AssetManager.class.getMethod("addAssetPath", String.class)
                            .invoke(assets, resourceApk.toString());
                    assertTrue(cookie != 0);
                    hostResources = new Resources(assets, delegate.getDisplayMetrics(), delegate.getConfiguration());
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Cannot load real host resources for Match", error);
                }
            }
            return hostResources;
        }
    }
}

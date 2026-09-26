package com.gamecenter.app.td;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gamecenter.app.R;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevels;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Exercises production deck UI against the shipped campaign data. The existing host shell
 * substitutes resource keys and formatting arguments for host-only strings, so these tests
 * verify localization wiring, not translated wording or physical-device visual appearance.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class TdBattleBriefingTest {
    private ActivityController<TdUiRegressionTest.PanelHostActivity> controller;
    private TdUiRegressionTest.PanelHostActivity activity;
    private TdUiRegressionTest.StatsHostFragment fragment;
    private SharedPreferences prefs;
    private TdSaveManager save;
    private ViewGroup ui;

    @BeforeClass
    public static void loadCampaign() throws Exception {
        TdUiRegressionTest.loadProductionCampaignData();
    }

    @Before
    public void setUp() throws Exception {
        controller = Robolectric.buildActivity(TdUiRegressionTest.PanelHostActivity.class).setup();
        activity = controller.get();
        fragment = new TdUiRegressionTest.StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "battle-briefing-host").commitNow();
        prefs = activity.getSharedPreferences("td_battle_briefing_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        save = new TdSaveManager(prefs);
        setField("save", save);
        ui = (ViewGroup) invoke("buildUi", new Class<?>[0]);
        ((ViewGroup) fragment.requireView()).addView(ui);
        assertNoGameStarted();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        if (prefs != null) prefs.edit().clear().commit();
    }

    @Test
    public void firstCampaignBriefingShowsRealResourcesAndLocalizedEnemyComposition() throws Exception {
        openDeck(TdGame.Difficulty.NORMAL, false);

        assertEquals(expectedSummary(240, false), textAtTag("td_battle_brief_summary"));
        String enemies = textAtTag("td_battle_brief_enemies");
        assertEquals(activity.getString(R.string.game_td_brief_enemies, firstLevelEnemyNames()), enemies);
        assertFalse("The first level has no flying wave",
                enemies.contains(activity.getString(R.string.game_td_monster_fly)));
        assertFalse("The first level has no boss wave",
                enemies.contains(activity.getString(R.string.game_td_monster_boss)));
        assertNoGameStarted();

        assertTrue("Start must use the production button callback", startButton().performClick());

        assertStartedGame(TdGame.Difficulty.NORMAL, TdGame.Mode.CAMPAIGN, 240);
        assertNotNull("The actual campaign start must still show the level story", findTag("td_story_intro"));
    }

    @Test
    public void difficultyPreviewsDoNotStartGamesAndHardStartMatchesItsPreview() throws Exception {
        openDeck(TdGame.Difficulty.HARD, false);
        assertEquals(expectedSummary(192, false), textAtTag("td_battle_brief_summary"));
        assertNoGameStarted();

        openDeck(TdGame.Difficulty.EASY, false);
        assertEquals(expectedSummary(312, false), textAtTag("td_battle_brief_summary"));
        assertNoGameStarted();

        openDeck(TdGame.Difficulty.HARD, false);
        assertEquals(expectedSummary(192, false), textAtTag("td_battle_brief_summary"));
        assertNoGameStarted();
        assertTrue("Start must use the production button callback", startButton().performClick());

        assertStartedGame(TdGame.Difficulty.HARD, TdGame.Mode.CAMPAIGN, 192);
    }

    @Test
    public void endlessBriefingDescribesOpeningWavesInsteadOfFiniteCampaignVictory() throws Exception {
        openDeck(TdGame.Difficulty.NORMAL, true);

        String summary = textAtTag("td_battle_brief_summary");
        assertEquals(expectedSummary(240, true), summary);
        assertFalse("Endless must not promise victory after six waves",
                summary.contains(activity.getString(R.string.game_td_brief_campaign, 6)));
        assertEquals("Only the opening enemy roster is known from the campaign definition",
                activity.getString(R.string.game_td_brief_opening_enemies, firstLevelEnemyNames()),
                textAtTag("td_battle_brief_enemies"));
        assertNoGameStarted();

        assertTrue("Endless start must use the production button callback", startButton().performClick());

        assertStartedGame(TdGame.Difficulty.NORMAL, TdGame.Mode.ENDLESS, 240);
        assertNull("Endless starts without a campaign story", findTag("td_story_intro"));
    }

    @Test
    public void compactDeckScrollsBriefingAndCardsWhileKeepingStartOutsideScroll() throws Exception {
        openDeck(TdGame.Difficulty.NORMAL, false);
        View taggedScroll = findTag("td_deck_scroll");
        assertTrue("The deck content must be scrollable", taggedScroll instanceof ScrollView);
        ScrollView scroll = (ScrollView) taggedScroll;
        Button start = startButton();
        ViewGroup panel = (ViewGroup) scroll.getParent();
        assertSame("Start stays in the fixed panel, outside scrollable content", panel, start.getParent());

        // This checks Android measurement/containment only; it does not certify device visuals.
        int width = dp(360);
        int height = dp(380);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        panel.layout(0, 0, width, height);

        assertTrue("The scroll viewport must retain usable height", scroll.getHeight() > 0);
        assertTrue("The compact viewport must expose a real scroll range",
                scroll.getChildAt(0).getHeight() > scroll.getHeight());
        assertTrue("Start must follow the scroll viewport", start.getTop() >= scroll.getBottom());
        assertTrue("The complete start button must fit inside the panel",
                start.getBottom() <= panel.getHeight() - panel.getPaddingBottom());
        assertTrue("The fixed start button must retain height", start.getHeight() > 0);
        assertNoGameStarted();
    }

    private void openDeck(TdGame.Difficulty difficulty, boolean endless) throws Exception {
        invoke("showDeckSelect", new Class<?>[] {int.class, TdGame.Difficulty.class, boolean.class},
                0, difficulty, endless);
    }

    private String expectedSummary(int coin, boolean endless) {
        return TdLevels.levelDisplayName(0, "main_001") + "\n"
                + activity.getString(R.string.game_td_brief_resources, coin, 5, 1) + "\n"
                + activity.getString(endless ? R.string.game_td_brief_endless
                        : R.string.game_td_brief_campaign, 6);
    }

    private String firstLevelEnemyNames() {
        return activity.getString(R.string.game_td_monster_normal) + " · "
                + activity.getString(R.string.game_td_monster_fast) + " · "
                + activity.getString(R.string.game_td_monster_tank) + " · "
                + activity.getString(R.string.game_td_monster_swarm);
    }

    private void assertNoGameStarted() throws Exception {
        assertNull("Preview must not install a live game into the fragment", field("game"));
        assertEquals("Preview must not count a played game", 0, save.getPlayCount());
    }

    private void assertStartedGame(TdGame.Difficulty difficulty, TdGame.Mode mode, int coin)
            throws Exception {
        TdGame game = (TdGame) field("game");
        assertNotNull("Start must create a live game", game);
        assertEquals(difficulty, game.getDifficulty());
        assertEquals(mode, game.getMode());
        assertEquals(TdGame.State.PREPARING, game.getState());
        assertEquals(coin, game.getCoin());
        assertEquals(5, game.getMascotHp());
        assertEquals(1, game.getPaths().length);
        assertEquals(6, game.getTotalWaves());
        assertEquals("Only the actual start counts as one game", 1, save.getPlayCount());
    }

    private Button startButton() {
        String expected = activity.getString(R.string.game_td_btn_start_level);
        Deque<View> queue = new ArrayDeque<>();
        queue.add(ui);
        while (!queue.isEmpty()) {
            View view = queue.remove();
            if (view instanceof Button && expected.contentEquals(((Button) view).getText())) {
                return (Button) view;
            }
            enqueueChildren(queue, view);
        }
        throw new AssertionError("Missing production start button");
    }

    private String textAtTag(String tag) {
        View view = findTag(tag);
        assertTrue("Missing briefing TextView: " + tag, view instanceof TextView);
        return ((TextView) view).getText().toString();
    }

    private View findTag(String tag) {
        Deque<View> queue = new ArrayDeque<>();
        queue.add(ui);
        while (!queue.isEmpty()) {
            View view = queue.remove();
            if (tag.equals(view.getTag())) return view;
            enqueueChildren(queue, view);
        }
        return null;
    }

    private static void enqueueChildren(Deque<View> queue, View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private Object field(String name) throws Exception {
        Field field = TdModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fragment);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = TdModuleFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(fragment, value);
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = TdModuleFragment.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(fragment, args);
    }
}

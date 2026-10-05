package com.gamecenter.app.td;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.gamecenter.app.R;
import com.gamecenter.app.td.engine.MonsterType;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TowerType;

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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Counts fresh games opened through real deck/result buttons without counting menu navigation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class TdSessionStatisticsTest {
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
        // Reuse only the existing resource/lifecycle shells; every button and callback is
        // constructed by production buildUi(), not a duplicate test implementation.
        controller = Robolectric.buildActivity(TdUiRegressionTest.PanelHostActivity.class).setup();
        activity = controller.get();
        fragment = new TdUiRegressionTest.StatsHostFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "session-statistics-host").commitNow();
        prefs = activity.getSharedPreferences("td_session_statistics_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        save = new TdSaveManager(prefs);
        setField("save", save);
        ui = (ViewGroup) invoke("buildUi", new Class<?>[0]);
        ((ViewGroup) fragment.requireView()).addView(ui);
        assertEquals("Viewing the initial level menu does not start a game", 0, save.getPlayCount());
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        if (prefs != null) prefs.edit().clear().commit();
    }

    @Test
    public void firstGameCountsOnceButClosingStoryAndOpeningMenuDoNotCountAgain() throws Exception {
        startFirstGameFromDeck();
        assertNotNull("A new campaign game should show its story", findTag("td_story_intro"));

        clickButton(R.string.game_td_story_start);
        assertNull("Story continue closes the overlay", findTag("td_story_intro"));
        assertEquals("Continuing the same game's story must not count another game", 1,
                save.getPlayCount());

        clickButton(R.string.game_td_btn_levels);
        assertNotNull("The real levels button must open the menu",
                findText(activity.getString(R.string.game_td_title_level_select)));
        assertEquals("Opening the level menu must not count another game", 1, save.getPlayCount());
    }

    @Test
    public void retryFromSettlementCountsExactlyOneNewGame() throws Exception {
        startFirstGameFromDeck();
        clickButton(R.string.game_td_story_start);
        TdGame completed = finishCurrentGameWithWin();

        clickButton(R.string.game_td_btn_retry);

        assertNotSame("Retry must create a fresh engine session", completed, field("game"));
        assertEquals("Retry keeps the selected level", 0, ((Integer) field("selectedLevelIdx")).intValue());
        assertEquals(TdGame.State.PREPARING, ((TdGame) field("game")).getState());
        assertNotNull("The restarted campaign game shows its own intro", findTag("td_story_intro"));
        assertEquals("One initial game plus one settlement retry must total two games", 2,
                save.getPlayCount());

        clickButton(R.string.game_td_story_start);
        assertEquals("Closing the retry's intro must not count a third game", 2, save.getPlayCount());
    }

    @Test
    public void nextLevelOffersNewlyUnlockedTowersBeforeCountingExactlyOneNewGame() throws Exception {
        startFirstGameFromDeck();
        clickButton(R.string.game_td_story_start);
        TdGame completed = finishCurrentGameWithWin();
        assertEquals("The real first win must unlock the second level", 2,
                save.getUnlockedLevelCount());

        clickButton(R.string.game_td_btn_next_level);

        assertNotNull("Next level must offer a deck selection before starting another game",
                findText(activity.getString(R.string.game_td_title_deck_select)));
        assertSame("Opening the next deck must keep the completed game", completed, field("game"));
        assertEquals("Opening the next deck does not change the current level", 0,
                ((Integer) field("selectedLevelIdx")).intValue());
        assertEquals("Opening the next deck must not count another game", 1, save.getPlayCount());
        assertNull("The next story belongs to the new game, not deck selection",
                findTag("td_story_intro"));

        // Use the real unlocked-card callbacks. A locked card also has a click listener, so
        // require its unlocked description and then verify both choices reach the next game.
        clickUnselectedTowerCard(R.string.game_td_tower_fan);
        clickUnselectedTowerCard(R.string.game_td_tower_rocket);
        assertNotNull("Both new towers must count toward the five-card draft",
                findText(activity.getString(R.string.game_td_deck_count,
                        5, 5, 5, TowerType.values().length)));
        assertSame("Editing the draft must keep the completed game", completed, field("game"));
        assertEquals("Editing the draft must not count another game", 1, save.getPlayCount());

        clickButton(R.string.game_td_btn_start_level);

        assertNotSame("Starting the selected deck must create a fresh engine session", completed,
                field("game"));
        assertEquals("Next level advances the selection", 1, ((Integer) field("selectedLevelIdx")).intValue());
        assertEquals(TdGame.State.PREPARING, ((TdGame) field("game")).getState());
        assertEquals("Next level preserves the completed game's difficulty", TdGame.Difficulty.NORMAL,
                ((TdGame) field("game")).getDifficulty());
        assertEquals("Next level remains a campaign game", TdGame.Mode.CAMPAIGN,
                ((TdGame) field("game")).getMode());
        List<?> nextDeck = (List<?>) field("activeDeck");
        assertEquals(5, nextDeck.size());
        assertTrue("The next game's deck must contain the newly selected fan",
                nextDeck.contains(TowerType.FAN));
        assertTrue("The next game's deck must contain the newly selected rocket",
                nextDeck.contains(TowerType.ROCKET));
        assertNotNull("The next campaign level shows its own intro", findTag("td_story_intro"));
        assertEquals("One completed game plus the next level must total two games", 2,
                save.getPlayCount());

        clickButton(R.string.game_td_story_start);
        assertEquals("Closing the next level's intro must not count a third game", 2, save.getPlayCount());
    }

    private void clickUnselectedTowerCard(int towerNameResourceId) {
        String description = activity.getString(R.string.game_td_cd_unselected,
                activity.getString(towerNameResourceId));
        View card = null;
        for (View view : views()) {
            if (description.contentEquals(view.getContentDescription() == null
                    ? "" : view.getContentDescription())) {
                card = view;
                break;
            }
        }
        assertNotNull("Newly unlocked tower must have a selectable card: " + description, card);
        assertEquals("The unlocked tower card must be visible", View.VISIBLE, card.getVisibility());
        assertTrue("The unlocked tower card must accept input", card.isEnabled());
        assertTrue("Selecting a tower must use the production click callback", card.performClick());
    }

    private void startFirstGameFromDeck() throws Exception {
        invoke("showDeckSelect", new Class<?>[] {int.class, TdGame.Difficulty.class, boolean.class},
                0, TdGame.Difficulty.NORMAL, false);
        assertEquals("Merely opening the deck panel must not count a game", 0, save.getPlayCount());
        clickButton(R.string.game_td_btn_start_level);
        assertEquals("The real deck start callback counts the initial game once", 1, save.getPlayCount());
    }

    /**
     * Use a deterministic engine victory, as in TdUiRegressionTest, to avoid making the
     * statistics regression depend on production level balance. Settlement and navigation
     * remain the real fragment callbacks; the engine's private state is never forced to WON.
     */
    private TdGame finishCurrentGameWithWin() throws Exception {
        int[][] path = new int[8][2];
        for (int i = 0; i < path.length; i++) path[i] = new int[] {0, i};
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 0.5f, 0.1f, 1f, 1f));
        TdGame completed = new TdGame(8, 2, path, 0, 7, 1000, 5, waves)
                .setMode(TdGame.Mode.CAMPAIGN);
        assertNotNull(completed.placeTower(TowerType.BOTTLE, 1, 2));
        assertNotNull(completed.placeTower(TowerType.BOTTLE, 1, 4));
        assertNotNull(completed.placeTower(TowerType.BOTTLE, 1, 6));
        for (int tick = 0; tick < 60 * 60 && !completed.isEnded(); tick++) {
            completed.tick();
            if (!completed.isEnded() && !completed.isWaveSpawning() && completed.getMonsters().isEmpty()) {
                completed.startNextWaveEarly();
            }
        }
        assertEquals("The deterministic fixture must win within its tick budget", TdGame.State.WON,
                completed.getState());
        setField("game", completed);
        ((TdView) field("tdView")).bind(completed);
        invoke("onGameEnded", new Class<?>[0]);
        assertNotNull("A real win must produce the victory settlement",
                findText(activity.getString(R.string.game_td_result_win)));
        assertEquals("Settling the initial game must not count another game", 1, save.getPlayCount());
        return completed;
    }

    private void clickButton(int resourceId) {
        TextView view = findText(activity.getString(resourceId));
        assertNotNull("Missing button resource " + resourceId, view);
        assertTrue("The action must use an actual production Button", view instanceof Button);
        assertTrue("The production click callback must run", view.performClick());
    }

    private TextView findText(String text) {
        for (View view : views()) {
            if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
                return (TextView) view;
            }
        }
        return null;
    }

    private View findTag(String tag) {
        for (View view : views()) if (tag.equals(view.getTag())) return view;
        return null;
    }

    private List<View> views() {
        List<View> result = new ArrayList<>();
        Deque<View> queue = new ArrayDeque<>();
        queue.add(ui);
        while (!queue.isEmpty()) {
            View view = queue.remove();
            result.add(view);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
        return result;
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

package com.gamecenter.app.klotski;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gamecenter.app.SaveManager;
import com.gamecenter.app.modular.ModuleResourceLoader;
import com.gamecenter.app.modules.ModuleLoader;
import com.gamecenter.app.modules.ModuleManager;

import org.json.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowChoreographer;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Real module XML, Fragment, buttons and captured-board BFS. Only worker scheduling
 * and module lookup boundaries are replaced. This does NOT verify APK trust, Dex
 * loading, device rendering or system touch; shipped-module tests cover those.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "w400dp-h700dp-mdpi",
        shadows = {KlotskiHintOwnershipTest.ModuleResourcesShadow.class,
                KlotskiHintOwnershipTest.ModuleClassLoaderShadow.class,
                KlotskiHintOwnershipTest.ModuleThemeShadow.class,
                KlotskiHintOwnershipTest.SaveProgressObserver.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class KlotskiHintOwnershipTest {
    private ActivityController<GameHostActivity> controller;
    private QueuedHintFragment fragment;
    private KlotskiView board;
    private Button hint;
    private Button restart;
    private Button select;
    private TextView status;
    private boolean paused;
    private boolean framePausedBefore;
    private Duration frameDelayBefore;

    @Before
    public void setUp() throws Exception {
        framePausedBefore = ShadowChoreographer.isPaused();
        frameDelayBefore = ShadowChoreographer.getFrameDelay();
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        SaveProgressObserver.klotskiProgressWrites = 0;
        openSession(true);
    }

    private void openSession(boolean clearAutoSave) throws Exception {
        paused = false;
        controller = Robolectric.buildActivity(GameHostActivity.class).setup();
        if (clearAutoSave) SaveManager.getInstance(controller.get()).deleteSave("klotski", "auto");
        ModuleResourcesShadow.hostTheme = controller.get().getTheme();
        ModuleResourcesShadow.resources = realModuleResources(controller.get().getResources());
        fragment = new QueuedHintFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment, "klotski-hint-ownership").commitNow();
        controller.visible();
        shadowOf(Looper.getMainLooper()).idle();
        dumpAttachment("before first traversal");
        // Match the verified View fixture: PAUSED Choreographer needs actual frames
        // to deliver ViewRootImpl's first traversal and dispatchAttachedToWindow.
        advanceFrames(32);
        dumpAttachment("after two natural frames");
        assertTrue("Inflate the real module XML, never accept the failure FrameLayout",
                fragment.requireView() instanceof LinearLayout);
        LinearLayout root = (LinearLayout) fragment.requireView();
        root.setLayoutParams(new FrameLayout.LayoutParams(400, 700));
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 700);
        board = root.findViewById(id("klotski_view"));
        hint = root.findViewById(id("btn_hint"));
        restart = root.findViewById(id("btn_game_restart"));
        select = root.findViewById(id("btn_shuffle"));
        status = root.findViewById(id("tv_game_status"));
        assertNotNull(board);
        assertNotNull(hint);
        assertNotNull(restart);
        assertNotNull(select);
        assertNotNull(root.findViewById(id("btn_game_tutorial")));
        assertTrue(board.getWidth() > 200 && board.getHeight() > 300);
        assertEquals("选局", select.getText().toString());
        assertEquals(48, select.getHeight());
        assertEquals("com.gamecenter.app.klotski", board.getResources().getResourcePackageName(id("klotski_view")));
        assertTrue("The actual XML board must be attached for gesture delivery", board.isAttachedToWindow());
        if (clearAutoSave) {
            assertTrue(status.getText().toString().startsWith("经典局 · 横刀立马\n"));
            assertEquals(new KlotskiGame().serializeState(), game().serializeState());
        }
    }

    @After
    public void tearDown() {
        if (controller != null) {
            if (!paused) controller.pause();
            controller.stop().destroy();
        }
        ModuleResourcesShadow.resources = null;
        ModuleResourcesShadow.hostTheme = null;
        ShadowChoreographer.setFrameDelay(frameDelayBefore);
        ShadowChoreographer.setPaused(framePausedBefore);
    }

    private static final String COMPAT_PROGRESS = "{\"bestMoves\":97,\"completed\":true,"
            + "\"practiceBestMoves\":{\"practice_exit_03\":18},\"futureProgress\":{\"token\":\"keep\"}}";

    @Test
    public void legacyCsvKeepsBoardAndMovesThenRestartsThatHistoricalBoard() throws Exception {
        SaveManager saves = SaveManager.getInstance(controller.get());
        saves.saveProgress("klotski", COMPAT_PROGRESS);
        KlotskiGame historical = compatibilityHistoricalGame();
        String historicalCsv = historical.serializeState();
        compatibilityRebuild(historicalCsv);

        assertEquals(historicalCsv, game().serializeState());
        assertEquals(2, game().getMoves());
        assertTrue(status.getText().toString().startsWith("自由局 · 历史进度\n"));
        assertEquals("legacy_free", readFragment("selectedLevelId"));
        compatibilityPauseAndResume();
        org.json.JSONObject written = new org.json.JSONObject(saves.load("klotski", "auto"));
        assertEquals(1, written.getInt("formatVersion"));
        assertEquals("legacy_free", written.getString("levelId"));
        assertEquals(historicalCsv, written.getString("state"));
        assertEquals("0," + historical.serializeBoardState(), written.getString("initialState"));

        compatibilityRebuild(null);
        assertEquals(historicalCsv, game().serializeState());
        assertTrue(restart.performClick());
        assertEquals(0, game().getMoves());
        assertEquals("Historical source cannot be guessed as a built-in practice",
                historical.serializeBoardState(), game().serializeBoardState());
        assertTrue(status.getText().toString().startsWith("自由局 · 历史进度\n"));
        assertEquals(COMPAT_PROGRESS, saves.loadProgress("klotski"));
        assertEquals("Only the fixture seeded progress; restore/restart must not write it", 1,
                SaveProgressObserver.klotskiProgressWrites);
    }

    @Test
    public void unknownLevelAndFutureFieldsSurvivePauseRestartAndRecreation() throws Exception {
        SaveManager saves = SaveManager.getInstance(controller.get());
        saves.saveProgress("klotski", COMPAT_PROGRESS);
        KlotskiGame historical = compatibilityHistoricalGame();
        String startingCsv = KlotskiPracticeLevels.all().get(0).initialStateCsv;
        org.json.JSONObject source = new org.json.JSONObject()
                .put("formatVersion", 1).put("levelId", "future_practice_99")
                .put("state", historical.serializeState()).put("initialState", startingCsv)
                .put("futureFlag", true)
                .put("futurePayload", new org.json.JSONObject().put("mode", "keep")
                        .put("unlock", new org.json.JSONArray().put(1).put(3)));
        compatibilityRebuild(source.toString());

        assertEquals(historical.serializeState(), game().serializeState());
        assertEquals("legacy_free", readFragment("selectedLevelId"));
        assertTrue(status.getText().toString().startsWith("自由局 · 未知棋局\n"));
        compatibilityPauseAndResume();
        compatibilityAssertUnknownEnvelope(saves, historical.serializeState(), startingCsv);
        assertTrue(restart.performClick());
        assertEquals(startingCsv, game().serializeState());
        compatibilityAssertUnknownEnvelope(saves, startingCsv, startingCsv);

        compatibilityRebuild(null);
        assertEquals(startingCsv, game().serializeState());
        assertEquals("legacy_free", readFragment("selectedLevelId"));
        assertTrue(status.getText().toString().startsWith("自由局 · 未知棋局\n"));
        compatibilityPauseAndResume();
        compatibilityAssertUnknownEnvelope(saves, startingCsv, startingCsv);
        assertEquals(COMPAT_PROGRESS, saves.loadProgress("klotski"));
        assertEquals(1, SaveProgressObserver.klotskiProgressWrites);
    }

    @Test
    public void unreadableOrFutureEnvelopeIsNeverOverwrittenUntilExplicitRestart() throws Exception {
        SaveManager saves = SaveManager.getInstance(controller.get());
        saves.saveProgress("klotski", COMPAT_PROGRESS);
        String validState = KlotskiPracticeLevels.all().get(0).initialStateCsv;
        String[] unsupported = {
                "  {broken-json",
                new org.json.JSONObject().put("formatVersion", 2).put("levelId", "practice_exit_01")
                        .put("state", validState).put("futureData", "retain exact bytes").toString(),
                new org.json.JSONObject().put("formatVersion", 1).put("levelId", "practice_exit_01")
                        .put("state", "invalid,csv").put("futureData", "retain exact bytes").toString()
        };
        String classicCsv = new KlotskiGame().serializeState();
        for (String original : unsupported) {
            compatibilityRebuild(original);
            assertEquals(classicCsv, game().serializeState());
            assertTrue(status.getText().toString().contains("原存档保留"));
            assertFalse((Boolean) readFragment("allowAutoSave"));
            compatibilityPauseAndResume();
            assertEquals("Pause must preserve unsupported source byte-for-byte", original,
                    saves.load("klotski", "auto"));
            compatibilityRebuild(null);
            assertEquals("Destroy and recreate must also preserve it", original,
                    saves.load("klotski", "auto"));
            assertTrue(status.getText().toString().contains("原存档保留"));
            assertEquals(COMPAT_PROGRESS, saves.loadProgress("klotski"));
        }

        assertTrue("An explicit user restart may replace the retained source", restart.performClick());
        org.json.JSONObject replacement = new org.json.JSONObject(saves.load("klotski", "auto"));
        assertEquals(1, replacement.getInt("formatVersion"));
        assertEquals("classic", replacement.getString("levelId"));
        assertEquals(classicCsv, replacement.getString("state"));
        assertEquals(COMPAT_PROGRESS, saves.loadProgress("klotski"));
        assertEquals(1, SaveProgressObserver.klotskiProgressWrites);
    }

    /** Historical save fixture made using the actual public movement rules, not live Fragment writes. */
    private static KlotskiGame compatibilityHistoricalGame() {
        KlotskiGame historical = new KlotskiGame();
        assertTrue(historical.restoreState(KlotskiPracticeLevels.all().get(0).initialStateCsv));
        assertTrue(historical.moveBlock(historical.getBlocks().get(5), 0, -1));
        assertTrue(historical.moveBlock(historical.getBlocks().get(7), 0, -1));
        assertFalse(historical.isWon());
        assertEquals(2, historical.getMoves());
        return historical;
    }

    private static void compatibilityAssertUnknownEnvelope(SaveManager saves, String state, String initial)
            throws Exception {
        org.json.JSONObject written = new org.json.JSONObject(saves.load("klotski", "auto"));
        assertEquals(1, written.getInt("formatVersion"));
        assertEquals("legacy_free", written.getString("levelId"));
        assertEquals("future_practice_99", written.getString("sourceLevelId"));
        assertEquals(state, written.getString("state"));
        assertEquals(initial, written.getString("initialState"));
        assertTrue(written.getBoolean("futureFlag"));
        org.json.JSONObject extra = written.getJSONObject("futurePayload");
        assertEquals("keep", extra.getString("mode"));
        org.json.JSONArray unlock = extra.getJSONArray("unlock");
        assertEquals(2, unlock.length());
        assertEquals(1, unlock.getInt(0));
        assertEquals(3, unlock.getInt(1));
    }

    private void compatibilityPauseAndResume() {
        assertFalse(paused);
        controller.pause();
        paused = true;
        controller.resume();
        paused = false;
    }

    /** Null seed reloads the real existing auto; no write after destruction can hide accidental loss. */
    private void compatibilityRebuild(String seed) throws Exception {
        assertFalse(paused);
        SaveManager saves = SaveManager.getInstance(controller.get());
        GameHostActivity oldActivity = controller.get();
        QueuedHintFragment oldFragment = fragment;
        controller.pause().stop().destroy();
        paused = true;
        controller = null;
        assertNull(oldFragment.getView());
        if (seed != null) saves.save("klotski", "auto", seed);
        openSession(false);
        assertNotSame(oldActivity, controller.get());
        assertNotSame(oldFragment, fragment);
    }

    @Test
    public void sameBoardRestartRejectsOldResultBeforeChangingNewRequestBusyState() throws Exception {
        choosePractice(1);
        String initialState = game().serializeState();
        KlotskiGame oldGame = game();
        requestHint(1);
        assertTrue(restart.performClick());
        assertNotSame(oldGame, game());
        assertEquals("Exercise same-board ABA, not just a different board", initialState, game().serializeState());
        assertEquals(0, game().getMoves());
        requestHint(2);
        String newStatus = status.getText().toString();
        fragment.runSearch(0);
        assertEquals(newStatus, status.getText().toString());
        assertFalse("Old A cannot enable newer B's button", hint.isEnabled());
        assertTrue((Boolean) readFragment("isHintSearching"));
        assertFalse((Boolean) readBoard("showHint"));
        fragment.runSearch(1);
        assertValidHint(4, "练习 1/8 · 关羽让路");
    }

    @Test
    public void switchingPracticeRejectsAWhileBStillSolvesItsActualCapturedBoard() throws Exception {
        choosePractice(1);
        requestHint(1);
        choosePractice(3);
        assertEquals(KlotskiPracticeLevels.all().get(2).initialStateCsv, game().serializeState());
        requestHint(2);
        String newStatus = status.getText().toString();
        fragment.runSearch(0);
        assertEquals(newStatus, status.getText().toString());
        assertFalse(hint.isEnabled());
        assertTrue((Boolean) readFragment("isHintSearching"));
        assertFalse((Boolean) readBoard("showHint"));
        fragment.runSearch(1);
        assertValidHint(16, "练习 3/8 · 先腾底路");
    }

    @Test
    public void pauseAndDestroyInvalidatePendingRealSearchCallbacks() throws Exception {
        choosePractice(1);
        requestHint(1);
        controller.pause();
        paused = true;
        String pausedStatus = status.getText().toString();
        fragment.runSearch(0);
        assertEquals(pausedStatus, status.getText().toString());
        assertFalse((Boolean) readBoard("showHint"));
        assertFalse((Boolean) readFragment("isHintSearching"));
        controller.resume();
        paused = false;
        requestHint(2);
        controller.get().getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
        assertNull(fragment.getView());
        String destroyedStatus = status.getText().toString();
        boolean destroyedButtonEnabled = hint.isEnabled();
        fragment.runSearch(1);
        assertEquals(destroyedStatus, status.getText().toString());
        assertEquals(destroyedButtonEnabled, hint.isEnabled());
        assertFalse((Boolean) readBoard("showHint"));
        assertEquals(0, ((Integer) readBoard("hintTotalSteps")).intValue());
        assertNull(readBoard("game"));
    }

    @Test
    public void completedPracticeSurvivesNewActivityWithoutRepeatingRecordWriteAndCanAdvance() throws Exception {
        SaveManager saves = SaveManager.getInstance(controller.get());
        saves.saveProgress("klotski", "{\"bestMoves\":116,\"completed\":true,"
                + "\"unknownMeta\":{\"retain\":\"yes\"},"
                + "\"practiceBestMoves\":{\"practice_exit_02\":12}}");
        SaveProgressObserver.klotskiProgressWrites = 0;
        choosePractice(1);
        // Independently frozen solution: Guan up, soldier 7 up/right, Cao right.
        int[][] moves = {{5, 0, -1}, {7, 0, -1}, {7, 1, 0}, {0, 1, 0}};
        for (int i = 0; i < moves.length; i++) {
            assertFalse("Only the fourth legal gesture wins", game().isWon());
            dragBlock(moves[i][0], moves[i][1], moves[i][2]);
            assertEquals(i + 1, game().getMoves());
            assertEquals(i == 3, game().isWon());
        }
        assertEquals("下一关", hint.getText().toString());
        assertTrue(hint.isEnabled());
        assertTrue(status.getText().toString().contains("已用 4 步通关"));
        assertEquals("The natural win writes its practice record exactly once", 1,
                SaveProgressObserver.klotskiProgressWrites);
        String wonState = game().serializeState();
        String progressAfterWin = saves.loadProgress("klotski");
        JSONObject progress = new JSONObject(progressAfterWin);
        assertEquals(116, progress.getInt("bestMoves"));
        assertTrue(progress.getBoolean("completed"));
        assertEquals("yes", progress.getJSONObject("unknownMeta").getString("retain"));
        assertEquals(4, progress.getJSONObject("practiceBestMoves").getInt("practice_exit_01"));
        assertEquals(12, progress.getJSONObject("practiceBestMoves").getInt("practice_exit_02"));

        GameHostActivity oldActivity = controller.get();
        QueuedHintFragment oldFragment = fragment;
        KlotskiGame oldGame = game();
        controller.pause().stop().destroy();
        paused = true;
        controller = null;
        assertNull("The old Fragment view really was destroyed", oldFragment.getView());
        openSession(false); // A new Activity and Fragment, without clearing the saved round.
        assertNotSame(oldActivity, controller.get());
        assertNotSame(oldFragment, fragment);
        assertNotSame(oldGame, game());
        assertEquals("Completion must retain the practice identity and next-level entry",
                "practice_exit_01", readFragment("selectedLevelId"));
        assertTrue(game().isWon());
        assertEquals(4, game().getMoves());
        assertEquals(wonState, game().serializeState());
        assertTrue(status.getText().toString().startsWith("练习 1/"
                + KlotskiPracticeLevels.all().size() + " · 关羽让路\n"));
        TextView movesLabel = fragment.requireView().findViewById(id("tv_moves"));
        assertEquals("步数 4 · 参考 4 · 最佳 4", movesLabel.getText().toString());
        assertEquals("下一关", hint.getText().toString());
        assertTrue(hint.isEnabled());
        assertEquals(progressAfterWin, saves.loadProgress("klotski"));
        assertEquals("Restoring a won state must not write a record again", 1,
                SaveProgressObserver.klotskiProgressWrites);

        assertTrue("Advance through the actual completion button", hint.performClick());
        assertEquals("practice_exit_02", readFragment("selectedLevelId"));
        assertEquals(KlotskiPracticeLevels.all().get(1).initialStateCsv, game().serializeState());
        assertEquals(0, game().getMoves());
        assertFalse(game().isWon());
        assertEquals("提示", hint.getText().toString());
        assertEquals(progressAfterWin, saves.loadProgress("klotski"));
        assertEquals(1, SaveProgressObserver.klotskiProgressWrites);
    }

    private void dragBlock(int blockId, int dx, int dy) throws Exception {
        KlotskiGame.Block block = game().getBlocks().get(blockId);
        assertEquals(blockId, block.id);
        assertTrue("Replay uses an actually legal move", game().canMove(block, dx, dy));
        float cell = ((Number) readBoard("cellSize")).floatValue();
        float x = ((Number) readBoard("offsetX")).floatValue() + (block.x + block.width / 2f) * cell;
        float y = ((Number) readBoard("offsetY")).floatValue() + (block.y + block.height / 2f) * cell;
        long downTime = SystemClock.uptimeMillis();
        touch(downTime, MotionEvent.ACTION_DOWN, x, y);
        touch(downTime, MotionEvent.ACTION_MOVE, x + dx * cell * 0.6f, y + dy * cell * 0.6f);
        touch(downTime, MotionEvent.ACTION_UP, x + dx * cell * 0.6f, y + dy * cell * 0.6f);
        advanceFrames(160);
        assertNull("Natural 120ms animation must finish before the next gesture", readBoard("animatingBlock"));
    }

    private void touch(long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            assertTrue(board.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private void dumpAttachment(String phase) {
        View decor = controller.get().getWindow().getDecorView();
        View content = controller.get().findViewById(android.R.id.content);
        System.out.println("Klotski attach " + phase + " uptime=" + SystemClock.uptimeMillis()
                + " choreographerPaused=" + ShadowChoreographer.isPaused()
                + " frameDelay=" + ShadowChoreographer.getFrameDelay()
                + " decorAttached=" + decor.isAttachedToWindow()
                + " contentAttached=" + (content != null && content.isAttachedToWindow()));
        View current = fragment.requireView().findViewById(id("klotski_view"));
        for (int depth = 0; current != null && depth < 8; depth++) {
            android.view.ViewParent parent = current.getParent();
            System.out.println("  depth=" + depth + " class=" + current.getClass().getName()
                    + " id=" + current.getId() + " attached=" + current.isAttachedToWindow()
                    + " token=" + (current.getWindowToken() != null)
                    + " size=" + current.getWidth() + "x" + current.getHeight()
                    + " parent=" + (parent == null ? "null" : parent.getClass().getName()));
            current = parent instanceof View ? (View) parent : null;
        }
    }

    private void advanceFrames(long millis) {
        long before = SystemClock.uptimeMillis();
        for (long remaining = millis; remaining > 0;) {
            long step = Math.min(16, remaining);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step));
            remaining -= step;
        }
        assertEquals(before + millis, SystemClock.uptimeMillis());
    }

    private void choosePractice(int number) throws Exception {
        assertTrue(select.performClick());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        ListView list = dialog.getListView();
        assertEquals(KlotskiPracticeLevels.all().size() + 2, list.getAdapter().getCount());
        assertTrue(list.getAdapter().getItem(number).toString().startsWith("练习 " + number + " · "));
        assertTrue(list.performItemClick(list.getAdapter().getView(number, null, list), number,
                list.getAdapter().getItemId(number)));
        assertEquals(KlotskiPracticeLevels.all().get(number - 1).id, readFragment("selectedLevelId"));
        assertEquals(0, game().getMoves());
        assertFalse(game().isWon());
    }

    private void requestHint(int expectedCount) throws Exception {
        assertTrue(hint.isEnabled());
        assertTrue(hint.performClick());
        assertEquals(expectedCount, fragment.searches.size());
        assertFalse(hint.isEnabled());
        assertTrue((Boolean) readFragment("isHintSearching"));
        assertTrue(status.getText().toString().contains("正在计算最优解"));
    }

    private void assertValidHint(int steps, String title) throws Exception {
        assertTrue(hint.isEnabled());
        assertFalse((Boolean) readFragment("isHintSearching"));
        assertTrue((Boolean) readBoard("showHint"));
        assertEquals(steps, ((Integer) readBoard("hintTotalSteps")).intValue());
        assertTrue(status.getText().toString().startsWith(title + "\n"));
        assertTrue(status.getText().toString().contains("距出口 " + steps + " 步"));
        assertEquals("Hint computation does not move the actual game", 0, game().getMoves());
        assertEquals(2, status.getText().toString().split("\n", -1).length);
    }

    private int id(String name) {
        int result = ModuleResourcesShadow.resources.getResId(name, "id");
        assertTrue("Real module ID must resolve: " + name, result != 0);
        return result;
    }

    private KlotskiGame game() throws Exception { return (KlotskiGame) readFragment("game"); }
    private Object readFragment(String name) throws Exception { return read(KlotskiModuleFragment.class, fragment, name); }
    private Object readBoard(String name) throws Exception { return read(KlotskiView.class, board, name); }
    private static Object read(Class<?> type, Object owner, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    /** Only schedules genuine production Runnables; no fake hint or game mutation. */
    public static class QueuedHintFragment extends KlotskiModuleFragment {
        final List<Runnable> searches = new ArrayList<>();
        @Override void executeHintSearch(Runnable search) { searches.add(search); }
        void runSearch(int index) {
            searches.get(index).run();
            // A pending ViewRootImpl traversal leaves a sync barrier at the head of the
            // main queue, so plain idle() cannot reach synchronous messages behind it.
            // Deliver the frame first (async, like a live loop) and then drain the callback.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    @Implements(value = ModuleManager.class, isInAndroidSdk = false)
    public static class ModuleResourcesShadow {
        static ModuleResourceLoader.ModuleResources resources;
        static Resources.Theme hostTheme;
        @Implementation protected ModuleResourceLoader.ModuleResources getModuleResources(String id) {
            assertEquals("klotski", id);
            assertNotNull("Test must supply actual module resources", resources);
            return resources;
        }
    }

    @Implements(value = ModuleLoader.class, isInAndroidSdk = false)
    public static class ModuleClassLoaderShadow {
        @Implementation protected ClassLoader getModuleClassLoader(String id) {
            assertEquals("klotski", id);
            return KlotskiView.class.getClassLoader();
        }
    }

    /**
     * Keep the two real resource tables separate. The module XML/IDs use only the
     * real module Resources; its ContextThemeWrapper obtains the actual host theme.
     * This fixture boundary avoids mixing colliding 0x7f packages and does not fake
     * any XML, layout, resource name/type, View, string, game or theme attribute.
     */
    @Implements(className = "com.gamecenter.app.klotski.KlotskiModuleFragment$1",
            isInAndroidSdk = false)
    public static class ModuleThemeShadow {
        @RealObject private ContextThemeWrapper real;
        @Implementation protected void __constructor__(KlotskiModuleFragment owner, Context base,
                                                        int themeId, ClassLoader moduleLoader) {
            // Exact constructor signature verified from the compiled production anonymous class.
            Shadow.invokeConstructor(real.getClass(), real,
                    ClassParameter.from(KlotskiModuleFragment.class, owner),
                    ClassParameter.from(Context.class, base),
                    ClassParameter.from(int.class, themeId),
                    ClassParameter.from(ClassLoader.class, moduleLoader));
            assertNotNull("Supply the actual host theme to this module Context only",
                    ModuleResourcesShadow.hostTheme);
            // Framework-only fixture injection, equivalent to ContextThemeWrapper(Context, Theme).
            // Never changes a Fragment, Game, View, board, counter or completion callback.
            ReflectionHelpers.setField(real, "mTheme", ModuleResourcesShadow.hostTheme);
        }
    }

    /** Observe calls while retaining the actual SaveManager and SharedPreferences writes. */
    @Implements(value = SaveManager.class, isInAndroidSdk = false)
    public static class SaveProgressObserver {
        @RealObject private SaveManager real;
        static int klotskiProgressWrites;
        @Implementation protected void saveProgress(String gameId, String json) {
            if ("klotski".equals(gameId)) klotskiProgressWrites++;
            Shadow.directlyOn(real, SaveManager.class, "saveProgress",
                    ClassParameter.from(String.class, gameId), ClassParameter.from(String.class, json));
        }
    }

    private static Path repoRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !Files.isRegularFile(path.resolve("settings.gradle"))) path = path.getParent();
        assertNotNull("Find the current real resource build outputs", path);
        return path;
    }

    private static Path hostResourceApk() {
        return repoRoot().resolve("app/build/intermediates/linked_resources_binary_format/debug/"
                + "processDebugResources/linked-resources-binary-format-debug.ap_");
    }

    private static Resources loadResources(Resources displaySource, Path... apks) throws Exception {
        AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
        for (Path apk : apks) {
            assertTrue("Required generated resources must exist: " + apk, Files.isRegularFile(apk));
            int cookie = (int) AssetManager.class.getMethod("addAssetPath", String.class).invoke(assets, apk.toString());
            assertTrue("Resource archive must actually load: " + apk, cookie > 0);
        }
        return new Resources(assets, displaySource.getDisplayMetrics(), displaySource.getConfiguration());
    }

    private static ModuleResourceLoader.ModuleResources realModuleResources(Resources host) throws Exception {
        Path module = repoRoot().resolve("module-store/feature/games/games/klotski/build/intermediates/"
                + "linked_resources_binary_format/debug/processDebugResources/linked-resources-binary-format-debug.ap_");
        // Read the exact current compiled module XML/IDs from its own resource table.
        // A test-only theme boundary supplies the actual host theme separately.
        Resources resources = loadResources(host, module);
        ModuleResourceLoader.ModuleResources result = new ModuleResourceLoader.ModuleResources(
                "klotski", resources, resources.getAssets(), "com.gamecenter.app.klotski");
        int layout = result.getLayoutResId("activity_klotski");
        assertTrue("Do not substitute an empty or hand-built layout", layout != 0);
        assertEquals("Use the module's actual compiled package ID", 0x7f, layout >>> 24);
        assertEquals("layout", resources.getResourceTypeName(layout));
        assertEquals("com.gamecenter.app.klotski", resources.getResourcePackageName(layout));
        return result;
    }

    public static class GameHostActivity extends FragmentActivity {
        private Resources hostResources;
        @Override protected void onCreate(Bundle savedInstanceState) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
            super.onCreate(savedInstanceState);
        }
        @Override public Resources getResources() {
            if (hostResources == null) {
                try {
                    hostResources = loadResources(super.getResources(), hostResourceApk());
                } catch (Exception exception) {
                    throw new IllegalStateException("Load actual host resources", exception);
                }
            }
            return hostResources;
        }
    }
}

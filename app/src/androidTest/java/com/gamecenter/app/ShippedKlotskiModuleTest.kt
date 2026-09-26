package com.gamecenter.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.common.ModuleScopedPreferences
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import com.gamecenter.app.core.security.ModuleVerifier
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.database.entity.GameUsageEntity
import com.gamecenter.app.modules.ModuleDownloader
import com.gamecenter.app.modules.ModuleLoader
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.view.Window
import androidx.lifecycle.Lifecycle
import org.json.JSONArray

/**
 * Real shipped Klotski APK / dynamic resources / publisher pin / ClassLoader.
 * Three frozen practice solutions use full real DOWN/MOVE/UP gestures; every actual
 * board/step/win state is checked against independent frozen replay rows. Natural
 * production animations (120ms nominal, actual device scale recorded) finish before the next gesture. One real current-level
 * restart, completed-practice and mid-practice Activity close/relaunch are included.
 * Completed known practices retain their real winning auto save and next control.
 * Explicit public SaveManager compatibility fixtures are distinct from gameplay:
 * legacy progress/unknown fields and one unknown auto-envelope field. No game state
 * is written, no solver creates device moves, no private callbacks/performClick.
 * Original auto/progress values or null, all relevant prefs and the full Room row or
 * absence are restored after close/unload, with cleanup failures retained.
 */
@RunWith(AndroidJUnit4::class)
class ShippedKlotskiModuleTest {
    private val observationHandler = Handler(Looper.getMainLooper())
    private var activeActivity: DynamicGameActivity? = null
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedKlotskiActualTouchThreePracticesRestartRestoreAndEnterClassic() {
        withInstalledGame { session, saved ->
            val label = "klotski-three-practices"
            try {
                // Explicit public-storage compatibility fixture, separate from gameplay.
                // The real Fragment's exact original auto/progress values were captured first.
                saved.manager.saveProgress(GAME_ID, progressFixture().toString())
                assertProgress(saved.manager, 0)
                onGameMain {
                    val scale = ValueAnimator.getDurationScale()
                    assertTrue("INPUT_PRECONDITION: device movement animations must be enabled", scale.isFinite() && scale > 0f)
                    assertTrue(ValueAnimator.areAnimatorsEnabled())
                    Log.i(TAG, "Actual device animator duration scale=$scale; test preserves the existing setting")
                }
                captureScreen("$label-opened", ::screenView)
                selectFirstPractice()

                for ((index, level) in LEVELS.withIndex()) {
                    var fresh = readState()
                    assertFresh(level, index, fresh)
                    captureScreen("$label-${level.id}-fresh", ::screenView)
                    assertControlGeometry()
                    if (index == 0) {
                        playMove(level, level.steps.first())
                        val moved = readState()
                        touchControl("btn_game_restart")
                        fresh = readState()
                        assertFalse("Restart must create a fresh Game", moved.game === fresh.game)
                        assertFresh(level, index, fresh)
                        assertTrue(fresh.status.contains("已重开当前棋局"))
                        assertProgress(saved.manager, 0)
                        captureScreen("$label-current-practice-restarted", ::screenView)
                    }

                    for (step in level.steps) {
                        playMove(level, step)
                        if (index == 1 && step.number == 2) {
                            restoreMidPractice(session, saved, level)
                            assertProgress(saved.manager, 1)
                        }
                    }
                    var completed = readState()
                    captureScreen("$label-${level.id}-completed-before-outcome-assert", ::screenView)
                    assertEquals(level.steps.last().csv, completed.csv)
                    assertEquals(level.referenceMoves, completed.moves)
                    assertTrue(completed.won)
                    assertFalse(completed.active)
                    assertTrue(completed.status.startsWith("练习 ${index + 1}/3 · ${level.title}\n"))
                    assertTrue(completed.status.contains("已用 ${level.referenceMoves} 步通关！点击下方继续"))
                    assertEquals("步数 ${level.referenceMoves} · 参考 ${level.referenceMoves} · 最佳 ${level.referenceMoves}", completed.movesText)
                    assertEquals(if (index == 2) "经典局" else "下一关", completed.hintText)
                    assertTrue(completed.hintEnabled)
                    assertWinningAuto(saved.manager, level)
                    assertProgress(saved.manager, index + 1)
                    Log.i(TAG, "ACTUAL PRACTICE COMPLETE id=${level.id} moves=${completed.moves} " +
                        "won=${completed.won} status=${completed.status}; all steps used complete real gestures")
                    if (index == 0) {
                        completed = restoreCompletedPractice(session, saved, level)
                    }
                    touchControl("btn_hint")
                    val next = readState()
                    assertFalse("Next control must adopt a new actual Game", completed.game === next.game)
                    assertEquals(0, next.moves)
                    assertFalse(next.won)
                    if (index < 2) assertFresh(LEVELS[index + 1], index + 1, next)
                }

                val classic = readState()
                assertEquals("classic", classic.levelId)
                assertEquals(CLASSIC_CSV, classic.csv)
                assertEquals(0, classic.moves)
                assertFalse(classic.won)
                assertTrue(classic.active)
                assertTrue(classic.status.startsWith("经典局 · 横刀立马\n"))
                assertEquals("步数 0", classic.movesText)
                assertEquals("提示", classic.hintText)
                assertTrue(classic.hintEnabled)
                assertProgress(saved.manager, 3)
                captureScreen("$label-third-to-classic", ::screenView)
                assertControlGeometry()
            } catch (error: Throwable) {
                runCatching { captureScreen("$label-failure", ::screenView) }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private data class BlockState(val id: Int, val x: Int, val y: Int, val width: Int, val height: Int)
    private data class BoardState(
        val game: Any, val board: View, val levelId: String, val csv: String, val moves: Int,
        val won: Boolean, val active: Boolean, val blocks: List<BlockState>,
        val status: String, val movesText: String, val hintText: String, val hintEnabled: Boolean,
        val animationRunning: Boolean, val animationDuration: Long?, val offsetX: Float, val offsetY: Float
    )
    private data class Step(val number: Int, val blockId: Int, val dx: Int, val dy: Int, val won: Boolean, val csv: String)
    private data class Level(val id: String, val title: String, val referenceMoves: Int, val initial: String, val steps: List<Step>)
    private data class TouchPoint(val x: Float, val y: Float)
    private data class CanvasGeometry(val bounds: Rect, val localVisible: Rect, val visibleOnScreen: Rect, val windowFrame: Rect)
    private data class SavedState(val manager: SaveManager, val auto: String?, val autoExisted: Boolean,
        val progress: String?, val progressExisted: Boolean)
    private class GameSession(var scenario: ActivityScenario<DynamicGameActivity>)

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun call(owner: Any, name: String): Any? = owner.javaClass.getMethod(name).invoke(owner)

    private fun resourceView(activity: DynamicGameActivity, name: String): View {
        val resources = requireNotNull(ModuleManager.getModuleResources(MODULE_ID))
        val id = resources.getResId(name, "id")
        assertTrue("The actual loaded Klotski APK must expose resource $name", id != 0)
        return requireNotNull(gameFragment(activity).requireView().findViewById<View>(id))
    }

    private fun boardView(activity: DynamicGameActivity): View = resourceView(activity, "klotski_view").also {
        assertEquals("com.gamecenter.app.klotski.KlotskiView", it.javaClass.name)
        assertSame(ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
    }

    private fun readState(): BoardState {
        lateinit var result: BoardState
        onGameMain { activity ->
            val fragment = gameFragment(activity)
            val game = requireNotNull(field(fragment, "game"))
            assertEquals("com.gamecenter.app.klotski.KlotskiGame", game.javaClass.name)
            assertSame(ModuleLoader.getClassLoader(MODULE_ID), game.javaClass.classLoader)
            val board = boardView(activity)
            assertSame(game, field(board, "game"))
            val blocks = (call(game, "getBlocks") as List<*>).map { item ->
                val block = requireNotNull(item)
                fun int(name: String) = block.javaClass.getField(name).getInt(block)
                BlockState(int("id"), int("x"), int("y"), int("width"), int("height"))
            }
            val animator = field(board, "currentAnimator") as ValueAnimator?
            val hint = resourceView(activity, "btn_hint") as Button
            result = BoardState(game, board, field(fragment, "selectedLevelId") as String,
                call(game, "serializeState") as String, call(game, "getMoves") as Int,
                call(game, "isWon") as Boolean, field(fragment, "gameActive") as Boolean, blocks,
                (resourceView(activity, "tv_game_status") as TextView).text.toString(),
                (resourceView(activity, "tv_moves") as TextView).text.toString(), hint.text.toString(), hint.isEnabled,
                animator?.isRunning == true, animator?.duration, field(board, "animOffsetX") as Float,
                field(board, "animOffsetY") as Float)
        }
        return result
    }

    private fun assertFresh(level: Level, index: Int, state: BoardState) {
        assertEquals(level.id, state.levelId)
        assertEquals(level.initial, state.csv)
        assertEquals(0, state.moves)
        assertFalse(state.won)
        assertTrue(state.active)
        assertFalse(state.animationRunning)
        assertEquals(0f, state.offsetX, 0f)
        assertEquals(0f, state.offsetY, 0f)
        assertTrue(state.status.startsWith("练习 ${index + 1}/3 · ${level.title}\n"))
        assertEquals("步数 0 · 参考 ${level.referenceMoves} · 最佳 —", state.movesText)
        assertEquals("提示", state.hintText)
        assertTrue(state.hintEnabled)
    }

    private fun selectFirstPractice() {
        touchControl("btn_shuffle")
        onGameMain { activity ->
            val dialog = requireNotNull(field(gameFragment(activity), "levelDialog") as AlertDialog?)
            assertTrue(dialog.isShowing)
            val labels = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
            assertEquals(listOf("经典局 · 横刀立马", "练习 1 · 关羽让路（参考 4 步）",
                "练习 2 · 双兵腾位（参考 8 步）", "练习 3 · 先腾底路（参考 16 步）", "随机打乱 · 新棋局"), labels)
        }
        captureScreen("klotski-real-level-picker", { activity -> requireNotNull(picker(activity).window).decorView },
            windowFor = { activity -> requireNotNull(picker(activity).window) })
        touchTarget("first practice picker row") { activity ->
            val list = picker(activity).listView
            val child = requireNotNull(list.getChildAt(1 - list.firstVisiblePosition))
            assertTrue((child as TextView).text.toString().startsWith("练习 1 · 关羽让路"))
            child
        }
        // ListView may post its item-click callback after UP for pressed-state feedback.
        // Observe that real callback's dismissal before checking the adopted Game; never retry input.
        val selectionDeadline = SystemClock.uptimeMillis() + 1_500L
        var dismissed = false
        while (!dismissed && SystemClock.uptimeMillis() < selectionDeadline) {
            onGameMain { activity -> dismissed = !picker(activity).isShowing }
            if (!dismissed) SystemClock.sleep(16L)
        }
        assertTrue("The actual list item click must dismiss the picker within 1500ms", dismissed)
    }

    private fun picker(activity: DynamicGameActivity): AlertDialog =
        requireNotNull(field(gameFragment(activity), "levelDialog") as AlertDialog?)

    private fun touchControl(id: String) = touchTarget(id) { activity ->
        (resourceView(activity, id) as Button).also { assertTrue(it.isEnabled && it.isClickable) }
    }

    private fun touchTarget(label: String, resolve: (DynamicGameActivity) -> View) {
        lateinit var point: TouchPoint
        onGameMain { activity ->
            val target = resolve(activity)
            val bounds = targetBounds(target)
            assertTrue(target.isEnabled)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val down = SystemClock.uptimeMillis()
        var complete = false
        try {
            injectTouch(down, MotionEvent.ACTION_DOWN, point)
            injectTouch(down, MotionEvent.ACTION_UP, point)
            complete = true
            onGameMain { }
            val elapsed = SystemClock.uptimeMillis() - down
            assertTrue("INPUT_PRECONDITION: actual menu $label touch must finish within 1500ms", elapsed <= 1_500L)
            Log.i(TAG, "Actual control $label full DOWN/UP elapsed=${elapsed}ms")
        } finally { if (!complete) injectTouch(down, MotionEvent.ACTION_CANCEL, point) }
    }

    private fun targetBounds(view: View): Rect {
        val geometry = canvasGeometry(view)
        assertEquals("Entire real control must be visible", Rect(0, 0, view.width, view.height), geometry.localVisible)
        assertTrue("Real control must avoid system bars", geometry.windowFrame.contains(geometry.bounds))
        val minimum = 48f * view.resources.displayMetrics.density
        assertTrue("Real control hit area must be at least 48dp", view.width + 0.5f >= minimum && view.height + 0.5f >= minimum)
        if (view is TextView) assertTrue("Control text must be fully laid out without ellipsis", view.layout != null &&
            (0 until view.layout.lineCount).all { view.layout.getEllipsisCount(it) == 0 })
        return geometry.bounds
    }

    private fun assertControlGeometry() = onGameMain { activity ->
        val rects = listOf("btn_game_restart", "btn_shuffle", "btn_hint", "btn_game_tutorial")
            .map { targetBounds(resourceView(activity, it)) }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse(Rect.intersects(rects[i], rects[j]))
        for (id in listOf("tv_game_status", "tv_moves")) {
            val text = resourceView(activity, id) as TextView
            val geometry = canvasGeometry(text)
            assertEquals(Rect(0, 0, text.width, text.height), geometry.localVisible)
            assertTrue("Actual lesson/status text must not be truncated", text.layout != null &&
                (0 until text.layout.lineCount).all { text.layout.getEllipsisCount(it) == 0 })
        }
    }

    private fun playMove(level: Level, step: Step) {
        val before = readState()
        assertEquals(level.id, before.levelId)
        assertEquals(step.number - 1, before.moves)
        assertEquals(if (step.number == 1) level.initial else level.steps[step.number - 2].csv, before.csv)
        assertFalse("Every preceding position must remain unfinished", before.won)
        assertFalse("A new drag must wait for the preceding real animation", before.animationRunning)
        assertEquals(0f, before.offsetX, 0f)
        assertEquals(0f, before.offsetY, 0f)
        val (start, end) = dragPoints(before, step.blockId, step.dx, step.dy)
        val downTime = SystemClock.uptimeMillis()
        var complete = false
        var moveTime = 0L
        try {
            injectTouch(downTime, MotionEvent.ACTION_DOWN, start)
            assertEquals("DOWN alone must not move the actual board", before.csv, readState().csv)
            SystemClock.sleep(12L)
            moveTime = SystemClock.uptimeMillis()
            injectTouch(downTime, MotionEvent.ACTION_MOVE, end, moveTime)
            injectTouch(downTime, MotionEvent.ACTION_UP, end)
            complete = true
        } finally { if (!complete) injectTouch(downTime, MotionEvent.ACTION_CANCEL, end) }
        val acceptedAt = SystemClock.uptimeMillis()
        assertTrue("INPUT_PRECONDITION: complete actual drag must finish within 1500ms", acceptedAt - downTime <= 1_500L)
        var after = readState()
        assertSame(before.game, after.game)
        assertSame(before.board, after.board)
        assertEquals("A full actual drag advances exactly one cell and one move", step.csv, after.csv)
        assertEquals(step.number, after.moves)
        assertEquals("Only the frozen final step wins", step.won, after.won)
        assertEquals("The production movement animator retains its 120ms duration", 120L, requireNotNull(after.animationDuration))
        val deadline = moveTime + 2_000L
        while ((after.animationRunning || SystemClock.uptimeMillis() - moveTime < 120L) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(16L)
            after = readState()
        }
        val observedAt = SystemClock.uptimeMillis()
        assertTrue("Observe naturally settled board at least 120ms after MOVE within 2000ms", observedAt - moveTime in 120L..2_000L)
        assertFalse(after.animationRunning)
        assertEquals(0f, after.offsetX, 0f)
        assertEquals(0f, after.offsetY, 0f)
        assertEquals("No late animation may add a move or change another block", step.csv, after.csv)
        assertEquals(!step.won, after.active)
        assertEquals("步数 ${step.number} · 参考 ${level.referenceMoves} · 最佳 ${if (step.won) level.referenceMoves.toString() else "—"}", after.movesText)
        Log.i(TAG, "ACTUAL MOVE level=${level.id} step=${step.number} block=${step.blockId} direction=${step.dx}/${step.dy} " +
            "DOWN=$downTime MOVE=$moveTime accepted=$acceptedAt settled=$observedAt won=${after.won} csv=${after.csv}")
    }

    private fun dragPoints(before: BoardState, blockId: Int, dx: Int, dy: Int): Pair<TouchPoint, TouchPoint> {
        lateinit var start: TouchPoint
        lateinit var end: TouchPoint
        onGameMain { activity ->
            val board = boardView(activity)
            assertSame(before.board, board)
            val block = before.blocks.single { it.id == blockId }
            val cell = field(board, "cellSize") as Float
            val ox = field(board, "offsetX") as Float
            val oy = field(board, "offsetY") as Float
            assertTrue(cell > 0f)
            val geometry = canvasGeometry(board)
            start = TouchPoint(geometry.bounds.left + ox + (block.x + block.width / 2f) * cell,
                geometry.bounds.top + oy + (block.y + block.height / 2f) * cell)
            end = TouchPoint(start.x + dx * cell * 0.6f, start.y + dy * cell * 0.6f)
            val half = kotlin.math.ceil(24.0 * board.resources.displayMetrics.density).toInt()
            val corridor = Rect(kotlin.math.floor(minOf(start.x, end.x) - half).toInt(),
                kotlin.math.floor(minOf(start.y, end.y) - half).toInt(),
                kotlin.math.ceil(maxOf(start.x, end.x) + half).toInt(),
                kotlin.math.ceil(maxOf(start.y, end.y) + half).toInt())
            assertTrue("The complete 48dp drag corridor must be visible: $corridor", geometry.visibleOnScreen.contains(corridor))
            assertTrue("The complete drag corridor must avoid system bars", geometry.windowFrame.contains(corridor))
        }
        return start to end
    }

    private fun assertWinningAuto(manager: SaveManager, level: Level): JSONObject {
        assertTrue("Completed known practice must retain its actual winning auto save",
            manager.hasSave(GAME_ID, SLOT_AUTO))
        val actual = JSONObject(requireNotNull(manager.load(GAME_ID, SLOT_AUTO)))
        assertEquals(1, actual.getInt("formatVersion"))
        assertEquals(level.id, actual.getString("levelId"))
        assertEquals("Winning save must contain the full board and actual move count",
            level.steps.last().csv, actual.getString("state"))
        assertEquals(level.initial, actual.getString("initialState"))
        if (level.id == LEVELS[1].id) {
            assertEquals(canonicalJson(JSONObject().put("marker", "keep-me").put("version", 7)),
                canonicalJson(actual.getJSONObject("qaFutureEnvelope")))
        }
        return actual
    }

    private fun restoreCompletedPractice(session: GameSession, saved: SavedState, level: Level): BoardState {
        val before = readState()
        assertEquals(LEVELS.first().id, level.id)
        assertTrue(before.won)
        assertEquals(4, before.moves)
        assertFalse(before.active)
        val winningAuto = canonicalJson(assertWinningAuto(saved.manager, level))
        val progressBefore = saved.manager.loadProgress(GAME_ID)
        session.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals("Ordinary pause must retain the same complete winning save", winningAuto,
            canonicalJson(assertWinningAuto(saved.manager, level)))
        session.scenario.close()
        assertEquals(winningAuto, canonicalJson(assertWinningAuto(saved.manager, level)))
        session.scenario = launchGame()
        val restored = readState()
        assertFalse("A completed-game close/relaunch must create a new Game", before.game === restored.game)
        assertFalse("A completed-game close/relaunch must create a new View", before.board === restored.board)
        assertEquals(level.id, restored.levelId)
        assertEquals(level.steps.last().csv, restored.csv)
        assertEquals(4, restored.moves)
        assertTrue(restored.won)
        assertFalse("Restoring a completed practice must not restart active play", restored.active)
        assertFalse(restored.animationRunning)
        assertEquals(0f, restored.offsetX, 0f)
        assertEquals(0f, restored.offsetY, 0f)
        assertEquals("练习 1/3 · ${level.title}\n已完成，可重开或选择棋局", restored.status)
        assertEquals(before.movesText, restored.movesText)
        assertEquals("下一关", restored.hintText)
        assertTrue(restored.hintEnabled)
        onGameMain { assertSame(saved.manager, field(gameFragment(it), "saveManager")) }
        assertEquals(winningAuto, canonicalJson(assertWinningAuto(saved.manager, level)))
        assertEquals("Restoring completion must leave the saved progress value unchanged",
            progressBefore, saved.manager.loadProgress(GAME_ID))
        assertProgress(saved.manager, 1)
        captureScreen("klotski-completed-practice-restored-actual-window", ::screenView)
        assertControlGeometry()
        assertWonBoardIgnoresRealDrag(level, saved.manager)
        val retained = readState()
        assertSame(restored.game, retained.game)
        Log.i(TAG, "ACTUAL COMPLETED RESTORE id=${retained.levelId} moves=${retained.moves} won=${retained.won} " +
            "active=${retained.active} next=${retained.hintText}; winning auto and progress retained after real close/relaunch")
        return retained
    }

    private fun assertWonBoardIgnoresRealDrag(level: Level, manager: SaveManager) {
        val before = readState()
        assertTrue(before.won)
        assertFalse(before.animationRunning)
        val winningAuto = canonicalJson(assertWinningAuto(manager, level))
        val progressBefore = manager.loadProgress(GAME_ID)
        // Reverse the last real move: absent the completed-board guard, this would
        // move Cao Cao back into the proven empty cells from the preceding replay.
        val last = level.steps.last()
        assertEquals(0, last.blockId)
        assertEquals(1, last.dx)
        assertEquals(0, last.dy)
        val (start, end) = dragPoints(before, last.blockId, -last.dx, -last.dy)
        val downTime = SystemClock.uptimeMillis()
        var complete = false
        try {
            injectTouch(downTime, MotionEvent.ACTION_DOWN, start)
            assertEquals(before.csv, readState().csv)
            SystemClock.sleep(12L)
            injectTouch(downTime, MotionEvent.ACTION_MOVE, end)
            injectTouch(downTime, MotionEvent.ACTION_UP, end)
            complete = true
        } finally { if (!complete) injectTouch(downTime, MotionEvent.ACTION_CANCEL, end) }
        assertTrue("INPUT_PRECONDITION: completed-board real drag must finish within 1500ms",
            SystemClock.uptimeMillis() - downTime <= 1_500L)
        val immediate = readState()
        assertEquals("A real drag cannot change any completed-board/UI/animation field", before, immediate)
        // Observe beyond the ordinary movement animation duration without starting,
        // seeking, cancelling or manually invoking any animator/callback.
        val observedFrom = SystemClock.uptimeMillis()
        SystemClock.sleep(160L)
        val settled = readState()
        assertTrue("Observe ignored input beyond 120ms within a bounded 2000ms window",
            SystemClock.uptimeMillis() - observedFrom in 160L..2_000L)
        assertEquals("No delayed input may move or reactivate the completed practice", before, settled)
        assertEquals(winningAuto, canonicalJson(assertWinningAuto(manager, level)))
        assertEquals(progressBefore, manager.loadProgress(GAME_ID))
        captureScreen("klotski-completed-practice-real-drag-ignored", ::screenView)
        Log.i(TAG, "ACTUAL COMPLETED INPUT id=${level.id} reverse-final DOWN/MOVE/UP retained moves=${settled.moves} " +
            "won=${settled.won} csv=${settled.csv}")
    }

    private fun restoreMidPractice(session: GameSession, saved: SavedState, level: Level) {
        val before = readState()
        assertEquals(2, before.moves)
        assertFalse(before.won)
        captureScreen("klotski-mid-practice-before-real-pause", ::screenView)
        session.scenario.moveToState(Lifecycle.State.CREATED)
        val generated = JSONObject(requireNotNull(saved.manager.load(GAME_ID, SLOT_AUTO)))
        assertEquals(1, generated.getInt("formatVersion"))
        assertEquals(level.id, generated.getString("levelId"))
        assertEquals(before.csv, generated.getString("state"))
        assertEquals(level.initial, generated.getString("initialState"))
        // Compatibility-storage fixture only: retain the exact production board/identity.
        generated.put("qaFutureEnvelope", JSONObject().put("marker", "keep-me").put("version", 7))
        saved.manager.save(GAME_ID, SLOT_AUTO, generated.toString())
        session.scenario.close()
        session.scenario = launchGame()
        val restored = readState()
        assertFalse("A real close/relaunch creates a new Game", before.game === restored.game)
        assertEquals(before.csv, restored.csv)
        assertEquals(level.id, restored.levelId)
        assertEquals(2, restored.moves)
        assertFalse(restored.won)
        assertTrue(restored.active)
        assertTrue(restored.status.contains("已恢复棋局与步数"))
        assertEquals(before.movesText, restored.movesText)
        onGameMain { assertSame(saved.manager, field(gameFragment(it), "saveManager")) }
        captureScreen("klotski-mid-practice-restored-actual-window", ::screenView)
        // Force an ordinary lifecycle save after recovery to verify envelope retention.
        session.scenario.moveToState(Lifecycle.State.CREATED)
        val rewritten = JSONObject(requireNotNull(saved.manager.load(GAME_ID, SLOT_AUTO)))
        assertEquals(canonicalJson(generated), canonicalJson(rewritten))
        session.scenario.moveToState(Lifecycle.State.RESUMED)
        val resumed = readState()
        assertEquals(before.csv, resumed.csv)
        assertEquals(level.id, resumed.levelId)
        Log.i(TAG, "ACTUAL RESTORE id=${resumed.levelId} moves=${resumed.moves}; public SaveManager unknown-envelope sentinel preserved")
    }

    private fun progressFixture(): JSONObject = JSONObject().put("bestMoves", 99).put("completed", true)
        .put("qaUnrelated", JSONObject().put("marker", "legacy-progress-must-stay").put("flags", JSONArray(listOf(3, 1, 4))))
        .put("practiceBestMoves", JSONObject().put("unknown_future_practice", 77))

    private fun assertProgress(manager: SaveManager, completedLevels: Int) {
        val expected = progressFixture()
        val records = expected.getJSONObject("practiceBestMoves")
        LEVELS.take(completedLevels).forEach { records.put(it.id, it.referenceMoves) }
        val actual = JSONObject(requireNotNull(manager.loadProgress(GAME_ID)))
        assertEquals("Practice wins may update only their own best; legacy best/completed and unknown fields must survive",
            canonicalJson(expected), canonicalJson(actual))
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") {
            JSONObject.quote(it) + ":" + canonicalJson(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalJson(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    private fun launchGame(): ActivityScenario<DynamicGameActivity> {
        val launched = ActivityScenario.launch<DynamicGameActivity>(
            Intent(context, DynamicGameActivity::class.java).putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            launched.onActivity { activity ->
                activeActivity = activity
                gameFragment(activity)
                boardView(activity)
            }
            return launched
        } catch (error: Throwable) {
            runCatching { launched.close() }.exceptionOrNull()?.let(error::addSuppressed)
            activeActivity = null
            throw error
        }
    }

    private fun injectTouch(downTime: Long, action: Int, point: TouchPoint, eventTime: Long = SystemClock.uptimeMillis()) {
        val event = MotionEvent.obtain(downTime, eventTime, action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            val accepted = InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
            Log.i(TAG, "Actual Klotski input action=$action eventTime=$eventTime acceptedAt=${SystemClock.uptimeMillis()} accepted=$accepted point=$point")
            assertTrue("System must accept the real Klotski touchscreen event", accepted)
        } finally { event.recycle() }
    }

    private fun withInstalledGame(action: (GameSession, SavedState) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val known = listOf("klotski", "klotski_module", "klotski_save", "klotski_settings", "GameMatrix_saves",
                "game_usage", "streak_tracker", "play_time_limit")
            val prefsBefore = (known + known.map { ModuleScopedPreferences.scopedName(MODULE_ID, it) } + relevantPreferenceNames())
                .distinct().map(::capturePreferences)
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var session: GameSession? = null
            var saved: SavedState? = null
            var primaryFailure: Throwable? = null
            try {
                ensureShipped(manifest, apk)
                session = GameSession(launchGame())
                onGameMain { activity ->
                    val manager = field(gameFragment(activity), "saveManager") as SaveManager
                    saved = SavedState(manager, manager.load(GAME_ID, SLOT_AUTO), manager.hasSave(GAME_ID, SLOT_AUTO),
                        manager.loadProgress(GAME_ID), manager.hasProgress(GAME_ID))
                    Log.i(TAG, "Captured actual Klotski SaveManager auto/progress values and presence; original payloads not logged")
                }
                action(requireNotNull(session), requireNotNull(saved))
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                val failures = listOf(
                    runCatching { session?.scenario?.close() }.exceptionOrNull(),
                    runCatching { ModuleManager.unloadModule(context, MODULE_ID) }.exceptionOrNull(),
                    runCatching { restoreUserState(database, usageBefore, prefsBefore, saved) }.exceptionOrNull(),
                    runCatching {
                        activeActivity = null
                        if (previousCatalog != null) ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                    }.exceptionOrNull()
                ).filterNotNull()
                if (failures.isNotEmpty()) {
                    val cleanup = AssertionError("Failed to restore Klotski after device verification").apply { failures.forEach(::addSuppressed) }
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }

    private fun restoreUserState(database: AppDatabase, rowBefore: GameUsageEntity?, prefsBefore: List<PreferenceSnapshot>, saved: SavedState?) {
        val failures = mutableListOf<Throwable>()
        fun attempt(action: () -> Unit) { runCatching(action).exceptionOrNull()?.let(failures::add) }
        attempt { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }
        attempt { saved?.let { if (it.auto == null) it.manager.deleteSave(GAME_ID, SLOT_AUTO) else it.manager.save(GAME_ID, SLOT_AUTO, it.auto) } }
        attempt { saved?.let { if (it.progress == null) it.manager.deleteProgress(GAME_ID) else it.manager.saveProgress(GAME_ID, it.progress) } }
        attempt {
            val names = prefsBefore.map { it.name }.toSet()
            val appeared = relevantPreferenceNames().filterNot(names::contains).map { PreferenceSnapshot(it, emptyMap()) }
            restoreAllPreferences(prefsBefore + appeared)
        }
        attempt { saved?.let {
            assertTrue("Restore exact actual SaveManager auto or null", it.auto == it.manager.load(GAME_ID, SLOT_AUTO))
            assertEquals(it.autoExisted, it.manager.hasSave(GAME_ID, SLOT_AUTO))
            assertTrue("Restore exact actual SaveManager progress or null", it.progress == it.manager.loadProgress(GAME_ID))
            assertEquals(it.progressExisted, it.manager.hasProgress(GAME_ID))
        } }
        attempt {
            if (rowBefore != null) database.gameUsageDao().upsertSync(rowBefore) else runBlocking { database.gameUsageDao().delete(GAME_ID) }
            assertEquals("Restore complete original Klotski Room row or absence", rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
        }
        if (failures.isNotEmpty()) throw AssertionError("Failed to restore Klotski user data").apply { failures.forEach(::addSuppressed) }
        Log.i(TAG, "Restored exact original Klotski auto/progress value-or-null and presence, all relevant prefs and full Room row-or-absence")
    }
    private fun relevantPreferenceNames(): List<String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .filter { it.isFile && it.extension == "xml" }.map { it.nameWithoutExtension }
            .filter { it == "klotski" || it.startsWith("klotski_") || it.startsWith("mod_klotski__") || it.endsWith("__GameMatrix_saves") }

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("The real external Klotski Fragment must attach", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.klotski.KlotskiModuleFragment", it.javaClass.name)
            assertFalse("Klotski must not use host game classes", it.javaClass.classLoader === context.classLoader)
            assertSame(ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
            assertTrue(it.requireView().isShown)
        }
    }

    private fun screenView(activity: DynamicGameActivity): View = gameFragment(activity).requireView()

    private fun onGameMain(action: (DynamicGameActivity) -> Unit) {
        assertFalse("A bounded main-thread request must be awaited off the main thread",
            Looper.myLooper() === Looper.getMainLooper())
        val activity = requireNotNull(activeActivity)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val cancelled = AtomicBoolean(false)
        val request = Runnable {
            try {
                if (!cancelled.get()) {
                    assertFalse("The owned Activity must still be live", activity.isDestroyed || activity.isFinishing)
                    action(activity)
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }
        try {
            assertTrue("Queue a real main-thread observation request", observationHandler.post(request))
            assertTrue("Main-thread request must finish within 1500ms without requiring global idle",
                completed.await(1_500L, TimeUnit.MILLISECONDS))
            failure.get()?.let { throw it }
        } finally {
            cancelled.set(true)
            observationHandler.removeCallbacks(request)
        }
    }

    private fun canvasGeometry(view: View): CanvasGeometry {
        assertTrue("Input target must be attached, laid out and shown",
            view.isAttachedToWindow && view.isLaidOut && view.isShown && view.width > 0 && view.height > 0)
        var ancestor: View? = view
        while (ancestor != null) {
            val current = ancestor
            assertTrue("Input target cannot have an invisible or transparent ancestor",
                current.visibility == View.VISIBLE && current.alpha > 0f)
            ancestor = current.parent as? View
        }
        val local = Rect()
        assertTrue("The actual canvas must have a nonempty visible region", view.getLocalVisibleRect(local) && !local.isEmpty)
        val location = IntArray(2).also(view::getLocationOnScreen)
        val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val window = Rect().also(view::getWindowVisibleDisplayFrame)
        val visibleOnScreen = Rect(local).apply { offset(location[0], location[1]) }
        assertTrue("The visible canvas must intersect its real bounds", visibleOnScreen.intersect(bounds))
        assertTrue("The actual window must report a nonempty visible frame", !window.isEmpty)
        val geometry = CanvasGeometry(bounds, Rect(local), visibleOnScreen, window)
        // A full-screen canvas may draw behind system bars on Android 15. PixelCopy
        // should preserve that evidence; only actual touch regions must avoid the bars.
        Log.i(TAG, "Actual committed canvas geometry=$geometry, size=${view.width}x${view.height}, " +
            "density=${view.resources.displayMetrics.density}")
        return geometry
    }

    private fun captureScreen(
        label: String,
        targetView: (DynamicGameActivity) -> View,
        examinePixels: ((Bitmap) -> Unit)? = null,
        windowFor: (DynamicGameActivity) -> Window = { it.window }
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue("Screen evidence requires the API 29+ frame-commit callback", Build.VERSION.SDK_INT >= 29)
        val deadline = SystemClock.uptimeMillis() + 5_000L
        fun remainingMillis() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        val frameReady = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        var target: View? = null
        var root: View? = null
        var observer: ViewTreeObserver? = null
        var preDraw: ViewTreeObserver.OnPreDrawListener? = null
        var frameCommit: Runnable? = null
        try {
            onGameMain { activity ->
                val expected = targetView(activity)
                val windowRoot = expected.rootView
                target = expected
                root = windowRoot
                assertTrue("Screenshot target must be attached: $label", expected.isAttachedToWindow)
                assertTrue("Frame submission requires a hardware-rendered window", windowRoot.isHardwareAccelerated)
                val tree = windowRoot.viewTreeObserver
                observer = tree
                val committed = Runnable {
                    if (!cancelled.get()) frameReady.countDown()
                }
                frameCommit = committed
                val listener = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (!cancelled.get() && expected.isAttachedToWindow && expected.isShown
                            && expected.isLaidOut && expected.width > 0 && expected.height > 0
                            && !expected.isLayoutRequested && !windowRoot.isLayoutRequested) {
                            tree.removeOnPreDrawListener(this)
                            tree.registerFrameCommitCallback(committed)
                        }
                        return true
                    }
                }
                preDraw = listener
                tree.addOnPreDrawListener(listener)
                expected.postInvalidateOnAnimation()
                windowRoot.postInvalidateOnAnimation()
            }
            assertTrue("Timed out waiting for the $label target layout and committed draw",
                frameReady.await(remainingMillis(), TimeUnit.MILLISECONDS))
            onGameMain { activity ->
                assertTrue("The same screenshot target must still be attached and visible: $label",
                    target?.let { it.isAttachedToWindow && it.isShown && !it.isLayoutRequested
                        && it.rootView === root } == true)
                canvasGeometry(requireNotNull(target))
            }
        } finally {
            cancelled.set(true)
            instrumentation.runOnMainSync {
                observer?.takeIf { it.isAlive }?.let { tree ->
                    preDraw?.let(tree::removeOnPreDrawListener)
                    frameCommit?.let(tree::unregisterFrameCommitCallback)
                }
            }
        }

        // Frame commit promises a rendered Surface buffer, not its presentation on the
        // display. PixelCopy reads that actual window buffer without racing the compositor
        // as a full-screen UiAutomation capture can. No View.draw or synthetic pixels are used.
        val copyReady = CountDownLatch(1)
        val copyResult = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
        val copyCancelled = AtomicBoolean(false)
        val ownedBitmap = AtomicReference<Bitmap?>()
        try {
            onGameMain { activity ->
                val windowRoot = requireNotNull(root)
                val rootOrigin = IntArray(2).also(windowRoot::getLocationInWindow)
                assertEquals("PixelCopy Window starts at the Decor origin", listOf(0, 0), rootOrigin.toList())
                Log.i(TAG, "Actual PixelCopy Window root=${windowRoot.width}x${windowRoot.height} origin=${rootOrigin.toList()}")
                val bitmap = Bitmap.createBitmap(windowRoot.width, windowRoot.height, Bitmap.Config.ARGB_8888)
                ownedBitmap.set(bitmap)
                try {
                    PixelCopy.request(windowFor(activity), bitmap, { result ->
                        copyResult.set(result)
                        copyReady.countDown()
                        if (copyCancelled.get()) ownedBitmap.getAndSet(null)?.recycle()
                    }, Handler(Looper.getMainLooper()))
                } catch (error: Throwable) {
                    // Submission failed, so no callback can own this bitmap.
                    ownedBitmap.getAndSet(null)?.recycle()
                    throw error
                }
            }
            assertTrue("Timed out copying the committed $label window pixels",
                copyReady.await(remainingMillis(), TimeUnit.MILLISECONDS))
            assertEquals("PixelCopy must read the rendered $label window", PixelCopy.SUCCESS, copyResult.get())
            val bitmap = requireNotNull(ownedBitmap.get())
            val directory = File(context.filesDir, "gamematrix-qa")
            assertTrue("Create screenshot evidence directory", directory.isDirectory || directory.mkdirs())
            val destination = File(directory, "$label-${System.currentTimeMillis()}.png")
            destination.outputStream().use { output ->
                assertTrue("Save actual window pixels as PNG", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            assertTrue("Saved screenshot must not be empty", destination.length() > 0)
            Log.i(TAG, "Window pixel screenshot evidence (PixelCopy, not full screen) " +
                "${destination.absolutePath} ${bitmap.width}x${bitmap.height}")
            examinePixels?.invoke(bitmap)
        } finally {
            copyCancelled.set(true)
            // A main request may still be submitting after its bounded wait timed out.
            // Only a completed PixelCopy may be recycled here. Pending submission/copy
            // keeps ownership until its callback or the submission catch handles it.
            if (copyReady.count == 0L) ownedBitmap.getAndSet(null)?.recycle()
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed klotski package",
            installedVersion <= manifest.versionCode)
        val installedSnapshot = context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
            .getString("module_current_manifest_$MODULE_ID", null)?.let { raw ->
                runCatching { ModuleManifest.fromJson(JSONObject(raw)) }.getOrNull()
            }?.takeIf {
                it.id == MODULE_ID && it.fileName.isNotBlank() && File(it.fileName).name == it.fileName
            }
        val priorFile = (installedSnapshot ?: previousCatalog ?: manifest).let {
            ModuleDownloader.getModuleFileCompat(context, it)
        }.takeIf(File::isFile)
        val priorSha = priorFile?.let(::sha256)
        val alreadyCurrent = priorFile != null && priorSha == manifest.sha256
        val installBranch = when {
            alreadyCurrent -> "SHIPPED_BYTES_ALREADY_CURRENT_SKIP_UPDATE"
            priorFile == null || installedVersion <= 0 -> "FIRST_INSTALL_OR_RECOVERY"
            installedVersion == manifest.versionCode -> "SAME_VERSION_PACKAGE_REPLACEMENT"
            else -> "VERSION_UPGRADE"
        }
        Log.i(TAG, "Install branch=$installBranch beforeVersion=$installedVersion " +
            "beforeSha256=${priorSha ?: "unavailable"} shippedVersion=${manifest.versionCode} " +
            "shippedSha256=${manifest.sha256}")
        ModuleManager.registerAvailableManifests(listOf(manifest))
        val current = if (alreadyCurrent) {
            // Reusing verified bytes must not consume the previous package in last_good.
            // A stale installed snapshot would also affect the real loader; fail explicitly
            // rather than silently reinstalling identical bytes to repair test prerequisites.
            installedSnapshot?.let {
                assertEquals("Installed SHA metadata must match existing shipped bytes", manifest.sha256, it.sha256)
                assertEquals(manifest.fileSize, it.fileSize)
                assertEquals(manifest.versionCode, it.versionCode)
                assertEquals(manifest.entryClass, it.entryClass)
            }
            requireNotNull(priorFile)
        } else {
            // One necessary update is allowed. A fresh device demonstrates first install;
            // only an existing equal-version package with different bytes demonstrates replacement.
            assertTrue("Shipped Klotski must install through the production transaction",
                ModuleManager.applyExternalUpdate(context, MODULE_ID, apk, manifest.versionCode))
            TransactionInstaller.getCurrentFile(context, manifest)
        }
        assertEquals(manifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        verifyPackage(manifest, current)
        assertEquals("Current must contain exactly the shipped APK bytes", sha256(apk), sha256(current))
        Log.i(TAG, "Verified current ${current.name} v${manifest.versionCode} sha256=${manifest.sha256}")
    }

    private fun withShippedPackage(action: (ModuleManifest, File) -> Unit) {
        // Instrumentation enters directly, without Splash's context-aware catalog bootstrap.
        // Do this before any context-free manifest/version lookup can seed a VPN-only cache.
        ModuleManager.registerLocalFallbackIfNeeded(context)
        val modules = context.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("modules")
        }
        val records = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { it.optString("id") == MODULE_ID }
        assertEquals("The shipped catalog must identify klotski exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals(100, manifest.versionCode)
        assertEquals("game_klotski_v100.apk", manifest.fileName)
        assertEquals("com.gamecenter.app.klotski.KlotskiModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped Klotski must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped Klotski must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-klotski-instrumentation-", ".apk", context.cacheDir)
        try {
            context.assets.open("modules/${manifest.fileName}").use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
            action(manifest, apk)
        } finally {
            apk.delete()
        }
    }

    private fun verifyPackage(manifest: ModuleManifest, apk: File) {
        assertEquals("klotski APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("klotski APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("klotski APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
        @Suppress("DEPRECATION")
        val packageInfo = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES))
        assertEquals("com.gamecenter.app.klotski", packageInfo.packageName)
        val signing = requireNotNull(packageInfo.signingInfo)
        assertFalse("The shipped publisher has one current signer", signing.hasMultipleSigners())
        val currentSigners = signing.apkContentsSigners
        assertEquals(1, currentSigners.size)
        val certificateSha = MessageDigest.getInstance("SHA-256").digest(currentSigners.single().toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("Verify the actual archive certificate against the expected production pin",
            PUBLISHER_SHA256, certificateSha)
        assertEquals("1.0.0", packageInfo.versionName)
        @Suppress("DEPRECATION")
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        assertEquals("The shipped Klotski APK manifest must identify version 100", 100L, archiveVersion)
        Log.i(TAG, "Actual Klotski archive package=${packageInfo.packageName} version=$archiveVersion " +
            "size=${apk.length()} sha256=${sha256(apk)} catalogVersion=${manifest.versionCode}")
    }

    private fun sha256(file: File): String = com.gamecenter.app.modules.ModuleVerifier.computeSha256(file)

    private data class PreferenceSnapshot(val name: String, val values: Map<String, Any?>)

    private fun preferenceValues(prefs: SharedPreferences): Map<String, Any?> =
        prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }

    private fun capturePreferences(name: String) = PreferenceSnapshot(
        name, preferenceValues(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    )

    private fun restoreAllPreferences(snapshots: List<PreferenceSnapshot>) {
        val failures = snapshots.mapNotNull { snapshot ->
            runCatching { restorePreferences(snapshot) }.exceptionOrNull()
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore klotski preferences after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun restorePreferences(snapshot: PreferenceSnapshot) {
        val prefs = context.getSharedPreferences(snapshot.name, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filterNot(snapshot.values::containsKey).forEach(editor::remove)
        snapshot.values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                else -> error("Unsupported preference type for ${snapshot.name}/$key")
            }
        }
        assertTrue("Restore Klotski usage preferences: ${snapshot.name}", editor.commit())
        assertTrue("Klotski preference values must exactly match their pre-test state: ${snapshot.name}",
            snapshot.values == preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "klotski"
        private const val GAME_ID = "klotski"
        private const val SLOT_AUTO = "auto"
        private const val TAG = "ShippedKlotskiTest"
        private const val PUBLISHER_SHA256 = "d058a18f9e89a29b5339eda27ece3ff9f78e0dbefe605d551e7745f724d2eddc"
        private const val CLASSIC_CSV = "0,1,0,0,0,3,0,0,2,3,2,1,2,1,3,2,3,0,4,3,4"
        private val LEVELS = listOf(
            Level("practice_exit_01", "关羽让路", 4, "0,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,1,2", listOf(
                Step(1, 5, 0, -1, false, "1,0,3,3,0,2,0,1,0,0,0,2,2,0,2,2,4,3,4,1,2"),
                Step(2, 7, 0, -1, false, "2,0,3,3,0,2,0,1,0,0,0,2,2,0,2,2,3,3,4,1,2"),
                Step(3, 7, 1, 0, false, "3,0,3,3,0,2,0,1,0,0,0,2,2,0,2,3,3,3,4,1,2"),
                Step(4, 0, 1, 0, true, "4,1,3,3,0,2,0,1,0,0,0,2,2,0,2,3,3,3,4,1,2")
            )),
            Level("practice_exit_02", "双兵腾位", 8, "0,0,3,3,0,2,0,1,0,0,0,2,3,2,2,2,4,3,4,3,2", listOf(
                Step(1, 6, -1, 0, false, "1,0,3,3,0,2,0,1,0,0,0,2,3,1,2,2,4,3,4,3,2"),
                Step(2, 6, -1, 0, false, "2,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,3,2"),
                Step(3, 9, -1, 0, false, "3,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,2,2"),
                Step(4, 9, -1, 0, false, "4,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,1,2"),
                Step(5, 5, 0, -1, false, "5,0,3,3,0,2,0,1,0,0,0,2,2,0,2,2,4,3,4,1,2"),
                Step(6, 7, 0, -1, false, "6,0,3,3,0,2,0,1,0,0,0,2,2,0,2,2,3,3,4,1,2"),
                Step(7, 7, 1, 0, false, "7,0,3,3,0,2,0,1,0,0,0,2,2,0,2,3,3,3,4,1,2"),
                Step(8, 0, 1, 0, true, "8,1,3,3,0,2,0,1,0,0,0,2,2,0,2,3,3,3,4,1,2")
            )),
            Level("practice_exit_03", "先腾底路", 16, "0,0,2,3,0,2,0,1,0,0,0,2,4,3,3,0,4,1,4,3,2", listOf(
                Step(1, 6, -1, 0, false, "1,0,2,3,0,2,0,1,0,0,0,2,4,2,3,0,4,1,4,3,2"),
                Step(2, 6, 0, -1, false, "2,0,2,3,0,2,0,1,0,0,0,2,4,2,2,0,4,1,4,3,2"),
                Step(3, 5, 0, -1, false, "3,0,2,3,0,2,0,1,0,0,0,2,3,2,2,0,4,1,4,3,2"),
                Step(4, 8, 1, 0, false, "4,0,2,3,0,2,0,1,0,0,0,2,3,2,2,0,4,2,4,3,2"),
                Step(5, 7, 1, 0, false, "5,0,2,3,0,2,0,1,0,0,0,2,3,2,2,1,4,2,4,3,2"),
                Step(6, 8, 1, 0, false, "6,0,2,3,0,2,0,1,0,0,0,2,3,2,2,1,4,3,4,3,2"),
                Step(7, 7, 1, 0, false, "7,0,2,3,0,2,0,1,0,0,0,2,3,2,2,2,4,3,4,3,2"),
                Step(8, 0, 0, 1, false, "8,0,3,3,0,2,0,1,0,0,0,2,3,2,2,2,4,3,4,3,2"),
                Step(9, 6, -1, 0, false, "9,0,3,3,0,2,0,1,0,0,0,2,3,1,2,2,4,3,4,3,2"),
                Step(10, 6, -1, 0, false, "10,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,3,2"),
                Step(11, 9, -1, 0, false, "11,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,2,2"),
                Step(12, 9, -1, 0, false, "12,0,3,3,0,2,0,1,0,0,0,2,3,0,2,2,4,3,4,1,2"),
                Step(13, 5, 0, -1, false, "13,0,3,3,0,2,0,1,0,0,0,2,2,0,2,2,4,3,4,1,2"),
                Step(14, 7, 0, -1, false, "14,0,3,3,0,2,0,1,0,0,0,2,2,0,2,2,3,3,4,1,2"),
                Step(15, 7, 1, 0, false, "15,0,3,3,0,2,0,1,0,0,0,2,2,0,2,3,3,3,4,1,2"),
                Step(16, 0, 1, 0, true, "16,1,3,3,0,2,0,1,0,0,0,2,2,0,2,3,3,3,4,1,2")
            ))
        )
    }
}

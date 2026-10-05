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
import android.widget.GridLayout
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

/**
 * Real shipped Match APK, publisher pin, actual classloader and actual touchscreen input.
 * Reads the naturally shuffled cards only to plan legal pairs/mismatches; does not write
 * round state, replace callbacks, performClick or shorten the production 800ms delay.
 * One continuous Easy run completes challenges at error counts 6/5/2, then starts level4.
 * Full preference values, actual SaveManager progress (including null), and the Match Room
 * row/absence are restored after closing/unloading. Caller owns the fixed emulator serial.
 */
@RunWith(AndroidJUnit4::class)
class ShippedMatchModuleTest {
    private val observationHandler = Handler(Looper.getMainLooper())
    private var activeActivity: DynamicGameActivity? = null
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedMatchActualTouchThreeChallengesCompleteAndCycleToStageOne() {
        withInstalledGame { _, database, usageBefore, progressBefore ->
            val label = "match-three-stage-simple"
            try {
                captureScreen("$label-opened", ::screenView)
                assertDifficultyControls()
                touchField("btnEasy")
                touchField("btnStart")
                var fresh = readState()
                val initialBest = fresh.highScore
                val errorsByLevel = listOf(6, 5, 2)
                val limits = listOf(6, 4, 2)
                val names = listOf("熟悉牌面", "稳稳配对", "精准消除")
                var expectedTotal = 0
                var previousRound: BoardState? = null

                for (level in 1..3) {
                    val limit = limits[level - 1]
                    val expectedFreshBest = if (level == 1) initialBest else maxOf(initialBest, expectedTotal)
                    assertFreshRound(fresh, level, limit, names[level - 1], expectedTotal, expectedFreshBest)
                    previousRound?.let { assertNewRoundIdentity(it, fresh) }
                    captureScreen("$label-level-$level-fresh", ::screenView)
                    assertAllCardGeometry(16)
                    val pairs = fresh.values.indices.groupBy { fresh.values[it] }.toSortedMap().values.toList()
                    assertEquals(8, pairs.size)
                    assertTrue(pairs.all { it.size == 2 })
                    val wrongPair = listOf(pairs[0][0], pairs[1][0])
                    assertTrue("Choose a genuine mismatched pair from the natural shuffle",
                        fresh.values[wrongPair[0]] != fresh.values[wrongPair[1]])
                    val matched = mutableSetOf<Int>()
                    var moves = 0
                    for (error in 1..errorsByLevel[level - 1]) {
                        moves++
                        val resolved = playSelection(fresh, wrongPair, matched, moves, error, false,
                            "$label-level-$level-error-$error")
                        assertTrue("Exceeding an optional challenge must never lock the round", resolved.active)
                        assertEquals("No cards disappear on a mismatch", 16, resolved.visibility.count { it == View.VISIBLE })
                        assertTrue(resolved.status.contains(if (error <= limit) "目标内" else "已超出，仍可通关"))
                        assertTrue(resolved.stats.contains("失误 $error/$limit"))
                    }
                    captureScreen("$label-level-$level-errors-${errorsByLevel[level - 1]}", ::screenView)

                    var completed = readState()
                    for ((index, pair) in pairs.withIndex()) {
                        moves++
                        completed = playSelection(fresh, pair, matched, moves, errorsByLevel[level - 1], true,
                            "$label-level-$level-pair-${index + 1}")
                        assertEquals(index + 1, completed.matches)
                        assertEquals("Only the eighth pair completes this exact 16-card round",
                            index < 7, completed.active)
                    }
                    val levelPoints = maxOf(100 - moves * 3, 10) * level
                    expectedTotal += levelPoints
                    val best = maxOf(initialBest, expectedTotal)
                    captureScreen("$label-level-$level-completed-before-outcome-assert", ::screenView)
                    assertEquals("The original score formula remains unchanged", expectedTotal, completed.score)
                    assertEquals(best, completed.highScore)
                    assertEquals(level + 1, completed.level)
                    assertTrue(completed.status.contains("第 $level 关通关"))
                    assertTrue(completed.status.contains("阶段 $level/3 ${names[level - 1]}"))
                    assertTrue(completed.status.contains("+$levelPoints 分"))
                    assertTrue(completed.status.contains(if (level == 2) "挑战未达成" else "挑战达成"))
                    assertTrue(completed.stats.contains("配对 8/8"))
                    assertTrue(completed.stats.contains("失误 ${errorsByLevel[level - 1]}/$limit"))
                    assertTrue(completed.stats.contains("累计 $expectedTotal  最佳 $best"))
                    assertTrue(completed.visibility.all { it == View.INVISIBLE })
                    assertEquals(View.VISIBLE, completed.startVisibility)
                    assertEquals("下一关 ${level + 1}", completed.startText)
                    assertEquals("Match keeps its existing score persistence semantics",
                        maxOf(usageBefore?.highScore ?: 0L, best.toLong()),
                        requireNotNull(database.gameUsageDao().getByIdSync(GAME_ID)).highScore)
                    val persisted = progressBefore.manager.loadProgress(GAME_ID)
                    if (best > initialBest) {
                        assertEquals(best, JSONObject(requireNotNull(persisted)).getInt("highScore"))
                    } else {
                        assertTrue("A retained old best must leave its original full progress JSON untouched",
                            persisted == progressBefore.value)
                    }
                    Log.i(TAG, "$label level=$level actualErrors=${errorsByLevel[level - 1]} limit=$limit " +
                        "moves=$moves points=$levelPoints total=$expectedTotal completedStatus=${completed.status}")
                    previousRound = completed
                    touchField("btnStart")
                    fresh = readState()
                }

                assertEquals("The three unmodified score formulas yield 58 + 122 + 210", 390, expectedTotal)
                assertFreshRound(fresh, 4, 6, names[0], 390, maxOf(initialBest, 390))
                assertNewRoundIdentity(requireNotNull(previousRound), fresh)
                captureScreen("$label-level-4-stage-1-reset", ::screenView)
                assertAllCardGeometry(16)
            } catch (error: Throwable) {
                runCatching { captureScreen("$label-failure", ::screenView) }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private fun playSelection(
        original: BoardState, pair: List<Int>, matched: MutableSet<Int>, moves: Int, errors: Int,
        shouldMatch: Boolean, label: String
    ): BoardState {
        val before = readState()
        assertRoundIdentity(original, before)
        assertTrue(before.active)
        assertFalse(before.processing)
        assertEquals(moves - 1, before.moves)
        assertTrue(pair.all { it !in matched && !before.revealed[it] && before.visibility[it] == View.VISIBLE })
        touchCard(pair[0])
        val first = readState()
        assertEquals(moves - 1, first.moves)
        assertEquals(pair[0], first.first)
        assertEquals(-1, first.second)
        assertFalse(first.processing)
        assertEquals(original.values.indices.map { it in matched || it == pair[0] }, first.revealed)
        val receipt = touchCard(pair[1])
        val pending = readState()
        assertRoundIdentity(original, pending)
        assertTrue("The real second touch must enter the actual 800ms processing interval", pending.processing)
        assertEquals(moves, pending.moves)
        assertEquals(errors, pending.errors)
        assertEquals(matched.size / 2, pending.matches)
        assertEquals(pair[0], pending.first)
        assertEquals(pair[1], pending.second)
        assertEquals(original.values.indices.map { it in matched || it in pair }, pending.revealed)

        val deadline = receipt.upEventTime + 3_000L
        var resolved = pending
        while (resolved.processing && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20L)
            resolved = readState()
        }
        val observedAt = SystemClock.uptimeMillis()
        assertFalse("The actual production delayed callback must resolve within 3000ms", resolved.processing)
        assertTrue("Observe the actual 800ms callback, with bounded device scheduling allowance",
            observedAt - receipt.upEventTime in 800L..3_000L)
        if (shouldMatch) matched.addAll(pair)
        val expectedMatched = original.values.indices.map { it in matched }
        assertRoundIdentity(original, resolved)
        assertEquals(expectedMatched, resolved.matched)
        assertEquals(expectedMatched, resolved.revealed)
        assertEquals(original.values.indices.map { if (it in matched) View.INVISIBLE else View.VISIBLE }, resolved.visibility)
        assertEquals(moves, resolved.moves)
        assertEquals(errors, resolved.errors)
        assertEquals(matched.size / 2, resolved.matches)
        assertEquals(-1, resolved.first)
        assertEquals(-1, resolved.second)
        Log.i(TAG, "$label genuineTouchPair=$pair matching=$shouldMatch UP=${receipt.upEventTime} " +
            "accepted=${receipt.upAcceptedAt} observed=$observedAt elapsed=${observedAt - receipt.upEventTime}ms " +
            "moves=${resolved.moves} matches=${resolved.matches} errors=${resolved.errors} active=${resolved.active}")
        return resolved
    }

    private data class BoardState(
        val values: List<Int>, val valuesIdentity: Any, val buttons: List<Button>,
        val revealed: List<Boolean>, val matched: List<Boolean>, val visibility: List<Int>,
        val first: Int, val second: Int, val processing: Boolean, val moves: Int, val matches: Int,
        val errors: Int, val active: Boolean, val score: Int, val highScore: Int, val startedAt: Long,
        val goalPairs: Int, val rows: Int, val columns: Int, val level: Int,
        val status: String, val stats: String, val startText: String, val startVisibility: Int
    )
    private data class TouchPoint(val x: Float, val y: Float)
    private data class TouchReceipt(val upEventTime: Long, val upAcceptedAt: Long)
    private data class CanvasGeometry(val bounds: Rect, val localVisible: Rect, val visibleOnScreen: Rect, val windowFrame: Rect)
    private data class ProgressSnapshot(val manager: SaveManager, val value: String?, val existed: Boolean)

    private fun assertFreshRound(round: BoardState, level: Int, limit: Int, name: String, total: Int, best: Int) {
        assertEquals(16, round.values.size)
        assertEquals(16, round.revealed.size)
        assertEquals(16, round.matched.size)
        assertTrue(round.active)
        assertFalse(round.processing)
        assertTrue(round.revealed.none { it } && round.matched.none { it })
        assertTrue(round.visibility.all { it == View.VISIBLE })
        assertEquals(0, round.moves)
        assertEquals(0, round.matches)
        assertEquals(0, round.errors)
        assertEquals(-1, round.first)
        assertEquals(-1, round.second)
        assertTrue(round.startedAt > 0L)
        assertEquals(8, round.goalPairs)
        assertEquals(4, round.rows)
        assertEquals(4, round.columns)
        assertEquals(8, round.values.distinct().size)
        assertTrue(round.values.groupingBy { it }.eachCount().values.all { it == 2 })
        assertEquals(level, round.level)
        assertEquals(total, round.score)
        assertEquals(best, round.highScore)
        assertEquals(View.GONE, round.startVisibility)
        val stage = (level - 1) % 3 + 1
        assertTrue(round.status.contains("第 $level 关 · 阶段 $stage/3 $name"))
        assertTrue(round.status.contains("目标：失误≤$limit · 目标内"))
        assertTrue(round.stats.contains("步数 0  配对 0/8  失误 0/$limit"))
        assertTrue(round.stats.contains("累计 $total  最佳 $best"))
    }

    private fun assertRoundIdentity(original: BoardState, observed: BoardState) {
        assertSame("The current round must keep its original card array", original.valuesIdentity, observed.valuesIdentity)
        assertEquals("Read-only matching choices must not rewrite the natural shuffle", original.values, observed.values)
        assertEquals(original.startedAt, observed.startedAt)
        assertEquals(original.buttons.size, observed.buttons.size)
        original.buttons.indices.forEach { assertSame("Card identity $it must survive all progress", original.buttons[it], observed.buttons[it]) }
    }

    private fun assertNewRoundIdentity(old: BoardState, fresh: BoardState) {
        assertFalse("Starting the next real round creates a new natural card array", old.valuesIdentity === fresh.valuesIdentity)
        old.buttons.indices.forEach { assertFalse("New round card $it must be a new real Button", old.buttons[it] === fresh.buttons[it]) }
        assertTrue("The next round has a new start time", fresh.startedAt > old.startedAt)
        // Random shuffle order may legitimately repeat; identity, not random inequality, is the contract.
    }

    private fun field(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun cards(fragment: Fragment): List<Button> = (field(fragment, "cardButtons") as Array<*>).map { it as Button }

    private fun readState(): BoardState {
        lateinit var result: BoardState
        onGameMain { activity ->
            val fragment = gameFragment(activity)
            val grid = field(fragment, "gridLayout") as GridLayout
            val values = field(fragment, "cardValues") as IntArray
            val buttons = cards(fragment)
            val start = field(fragment, "btnStart") as Button
            result = BoardState(values.toList(), values, buttons,
                (field(fragment, "cardRevealed") as BooleanArray).toList(),
                (field(fragment, "cardMatched") as BooleanArray).toList(), buttons.map { it.visibility },
                field(fragment, "firstSelectedIndex") as Int, field(fragment, "secondSelectedIndex") as Int,
                field(fragment, "isProcessing") as Boolean, field(fragment, "moveCount") as Int,
                field(fragment, "matchedPairs") as Int, field(fragment, "errorCount") as Int,
                field(fragment, "gameActive") as Boolean, field(fragment, "currentScore") as Int,
                field(fragment, "highScore") as Int, field(fragment, "startTimeMs") as Long,
                field(fragment, "pairCount") as Int, grid.rowCount, grid.columnCount,
                field(fragment, "currentLevel") as Int, (field(fragment, "tvStatus") as TextView).text.toString(),
                (field(fragment, "tvStats") as TextView).text.toString(), start.text.toString(), start.visibility)
        }
        return result
    }
    private fun assertDifficultyControls() = onGameMain { activity ->
        val fragment = gameFragment(activity)
        listOf("btnEasy", "btnNormal", "btnHard", "btnStart").forEach { buttonBounds(field(fragment, it) as Button) }
    }

    private fun assertAllCardGeometry(count: Int) = onGameMain { activity ->
        val fragment = gameFragment(activity)
        val buttons = cards(fragment)
        assertEquals(count, buttons.size)
        val rects = buttons.map { buttonBounds(it) }
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse("Real card hit rectangles must not overlap: $i=${rects[i]} $j=${rects[j]}", Rect.intersects(rects[i], rects[j]))
        }
        val controls = listOf("btnEasy", "btnNormal", "btnHard", "btnStart")
            .map { field(fragment, it) as Button }.filter { it.isShown }
        for (control in controls) {
            val controlBounds = buttonBounds(control)
            for (cardBounds in rects) assertFalse("Difficulty/start controls must not cover any card",
                Rect.intersects(controlBounds, cardBounds))
        }
    }

    private fun buttonBounds(button: Button): Rect {
        val geometry = canvasGeometry(button)
        Log.i(TAG, "Actual Match button=${button.text} geometry=$geometry density=${button.resources.displayMetrics.density} " +
            "padding=${button.paddingLeft}/${button.paddingTop}/${button.paddingRight}/${button.paddingBottom} " +
            "textSize=${button.textSize} layoutWidth=${button.layout?.width}")
        assertEquals("The entire actual button must be locally visible", Rect(0, 0, button.width, button.height), geometry.localVisible)
        assertTrue("Entire actual button must fit the visible Window: $geometry", geometry.windowFrame.contains(geometry.bounds))
        val minimum = 48f * button.resources.displayMetrics.density
        assertTrue("Actual hit area must be at least 48dp in both dimensions",
            button.width + 0.5f >= minimum && button.height + 0.5f >= minimum)
        assertTrue("Buttons must expose complete laid-out text", button.layout != null &&
            (0 until button.layout.lineCount).all { button.layout.getEllipsisCount(it) == 0 })
        return geometry.bounds
    }

    private data class StaticControlState(
        val level: Int, val score: Int, val highScore: Int, val moves: Int, val matches: Int,
        val errors: Int, val startedAt: Long, val valuesIdentity: Any?, val values: List<Int>?,
        val buttons: List<Button>, val rows: Int, val columns: Int, val pairs: Int
    )

    private fun touchField(name: String): TouchReceipt {
        assertTrue("Only static difficulty/start controls receive the longer input budget",
            name in setOf("btnEasy", "btnNormal", "btnHard", "btnStart"))
        lateinit var before: StaticControlState
        val receipt = touchButton(1_500L, "static control $name") { activity ->
            val fragment = gameFragment(activity)
            assertFalse("Static controls are touched only between active rounds", field(fragment, "gameActive") as Boolean)
            assertFalse("No delayed card operation may be pending before a static control", field(fragment, "isProcessing") as Boolean)
            val values = field(fragment, "cardValues") as IntArray?
            val buttons = (field(fragment, "cardButtons") as Array<*>?)?.map { it as Button }.orEmpty()
            before = StaticControlState(field(fragment, "currentLevel") as Int,
                field(fragment, "currentScore") as Int, field(fragment, "highScore") as Int,
                field(fragment, "moveCount") as Int, field(fragment, "matchedPairs") as Int,
                field(fragment, "errorCount") as Int, field(fragment, "startTimeMs") as Long,
                values, values?.toList(), buttons, field(fragment, "baseGridRows") as Int,
                field(fragment, "baseGridCols") as Int, field(fragment, "basePairCount") as Int)
            field(fragment, name) as Button
        }
        onGameMain { activity ->
            val fragment = gameFragment(activity)
            assertFalse("A static control must not fabricate a pending card operation", field(fragment, "isProcessing") as Boolean)
            assertEquals("Static controls must not advance the level", before.level, field(fragment, "currentLevel") as Int)
            assertEquals("Static controls must preserve accumulated score", before.score, field(fragment, "currentScore") as Int)
            assertEquals("Static controls must preserve the actual best", before.highScore, field(fragment, "highScore") as Int)
            if (name == "btnStart") {
                assertTrue("The actual start listener must activate the new round", field(fragment, "gameActive") as Boolean)
                assertEquals(0, field(fragment, "moveCount") as Int)
                assertEquals(0, field(fragment, "matchedPairs") as Int)
                assertEquals(0, field(fragment, "errorCount") as Int)
                assertEquals(-1, field(fragment, "firstSelectedIndex") as Int)
                assertEquals(-1, field(fragment, "secondSelectedIndex") as Int)
                assertTrue("The actual new round records a fresh start time", (field(fragment, "startTimeMs") as Long) > before.startedAt)
                val values = field(fragment, "cardValues") as IntArray
                assertFalse("The start listener must create a new card array", before.valuesIdentity === values)
                assertEquals(before.rows * before.columns, values.size)
                assertEquals(before.pairs, field(fragment, "pairCount") as Int)
                assertTrue((field(fragment, "cardRevealed") as BooleanArray).none { it })
                assertTrue((field(fragment, "cardMatched") as BooleanArray).none { it })
                val grid = field(fragment, "gridLayout") as GridLayout
                assertEquals(before.rows, grid.rowCount)
                assertEquals(before.columns, grid.columnCount)
                assertEquals(View.GONE, (field(fragment, "btnStart") as Button).visibility)
                val currentButtons = cards(fragment)
                assertEquals(values.size, currentButtons.size)
                assertTrue(currentButtons.all { it.visibility == View.VISIBLE && it.text.toString() == "?" })
                currentButtons.forEach { current -> assertTrue("A new card must not reuse a preceding round's Button",
                    before.buttons.none { it === current }) }
            } else {
                assertFalse("Selecting difficulty must keep the game between rounds", field(fragment, "gameActive") as Boolean)
                val columns = when (name) { "btnEasy" -> 4; "btnNormal" -> 5; else -> 6 }
                assertEquals("The actual difficulty listener must select four rows", 4, field(fragment, "baseGridRows") as Int)
                assertEquals(columns, field(fragment, "baseGridCols") as Int)
                assertEquals(columns * 2, field(fragment, "basePairCount") as Int)
                assertEquals(before.moves, field(fragment, "moveCount") as Int)
                assertEquals(before.matches, field(fragment, "matchedPairs") as Int)
                assertEquals(before.errors, field(fragment, "errorCount") as Int)
                assertEquals(before.startedAt, field(fragment, "startTimeMs") as Long)
                val values = field(fragment, "cardValues") as IntArray?
                assertSame("Difficulty selection must not silently deal a round", before.valuesIdentity, values)
                assertEquals(before.values, values?.toList())
                assertTrue("Ready status must confirm the actual selection",
                    (field(fragment, "tvStatus") as TextView).text.toString().contains("4x$columns（${columns * 2} 对）"))
                assertEquals(View.VISIBLE, (field(fragment, "btnStart") as Button).visibility)
            }
        }
        return receipt
    }

    private fun touchCard(index: Int): TouchReceipt = touchButton(500L, "card $index") { activity ->
        val fragment = gameFragment(activity)
        assertTrue("A card touch requires an actual active round", field(fragment, "gameActive") as Boolean)
        assertFalse("A card touch must not overlap an existing 800ms operation", field(fragment, "isProcessing") as Boolean)
        cards(fragment)[index]
    }

    private fun touchButton(maximumDurationMs: Long, label: String, resolve: (DynamicGameActivity) -> Button): TouchReceipt {
        lateinit var point: TouchPoint
        onGameMain { activity ->
            val button = resolve(activity)
            val bounds = buttonBounds(button)
            assertTrue("A real touch target must be enabled and clickable", button.isEnabled && button.isClickable)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val downTime = SystemClock.uptimeMillis()
        var completed = false
        try {
            injectTouch(downTime, MotionEvent.ACTION_DOWN, point)
            val upTime = SystemClock.uptimeMillis()
            injectTouch(downTime, MotionEvent.ACTION_UP, point, upTime)
            completed = true
            val accepted = SystemClock.uptimeMillis()
            onGameMain { }
            val elapsed = SystemClock.uptimeMillis() - downTime
            assertTrue("Actual $label touch must return within ${maximumDurationMs}ms; card touches retain their 500ms bound before matching resolves",
                elapsed <= maximumDurationMs)
            Log.i(TAG, "Touch budget label=$label elapsed=${elapsed}ms maximum=${maximumDurationMs}ms")
            return TouchReceipt(upTime, accepted)
        } finally {
            if (!completed) injectTouch(downTime, MotionEvent.ACTION_CANCEL, point)
        }
    }

    private fun injectTouch(downTime: Long, action: Int, point: TouchPoint, eventTime: Long = SystemClock.uptimeMillis()) {
        val event = MotionEvent.obtain(downTime, eventTime, action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            val accepted = InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
            Log.i(TAG, "Actual Match input action=$action eventTime=$eventTime acceptedAt=${SystemClock.uptimeMillis()} accepted=$accepted point=$point")
            assertTrue("System must accept the real Match touchscreen event", accepted)
        } finally { event.recycle() }
    }

    private fun withInstalledGame(
        action: (ActivityScenario<DynamicGameActivity>, AppDatabase, GameUsageEntity?, ProgressSnapshot) -> Unit
    ) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val known = listOf("match", "match_module", "match_save", "match_settings", "GameMatrix_saves",
                "game_usage", "streak_tracker", "play_time_limit")
            val prefsBefore = (known + known.map { ModuleScopedPreferences.scopedName(MODULE_ID, it) } + relevantPreferenceNames())
                .distinct().map(::capturePreferences)
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            var progressBefore: ProgressSnapshot? = null
            var primaryFailure: Throwable? = null
            try {
                ensureShipped(manifest, apk)
                val launched = ActivityScenario.launch<DynamicGameActivity>(
                    Intent(context, DynamicGameActivity::class.java).putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                scenario = launched
                launched.onActivity { activity ->
                    activeActivity = activity
                    val fragment = gameFragment(activity)
                    // Do not initialize SaveManager using the host context before loading.
                    // Its process-wide singleton may own a previously selected module store.
                    // Read the exact instance used by this real Fragment before any game writes.
                    val manager = field(fragment, "saveManager") as SaveManager
                    progressBefore = ProgressSnapshot(manager, manager.loadProgress(GAME_ID), manager.hasProgress(GAME_ID))
                    Log.i(TAG, "Captured actual Match SaveManager progress presence=${progressBefore!!.existed}; payload not logged")
                }
                action(launched, database, usageBefore, requireNotNull(progressBefore))
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                val failures = listOf(
                    runCatching { scenario?.close() }.exceptionOrNull(),
                    runCatching { ModuleManager.unloadModule(context, MODULE_ID) }.exceptionOrNull(),
                    runCatching { restoreUserState(database, usageBefore, prefsBefore, progressBefore) }.exceptionOrNull(),
                    runCatching {
                        activeActivity = null
                        if (previousCatalog != null) ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                    }.exceptionOrNull()
                ).filterNotNull()
                if (failures.isNotEmpty()) {
                    val cleanup = AssertionError("Failed to restore Match after device verification").apply {
                        failures.forEach(::addSuppressed)
                    }
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }

    private fun relevantPreferenceNames(): List<String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .filter { it.isFile && it.extension == "xml" }.map { it.nameWithoutExtension }
            .filter { it == "match" || it.startsWith("match_") || it.startsWith("mod_match__") || it.endsWith("__GameMatrix_saves") }

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("The real external Match Fragment must attach", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.match.MatchModuleFragment", it.javaClass.name)
            assertFalse("Match must not use host game classes", it.javaClass.classLoader === context.classLoader)
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
        examinePixels: ((Bitmap) -> Unit)? = null
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
                    PixelCopy.request(activity.window, bitmap, { result ->
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

    private fun restoreUserState(
        database: AppDatabase, rowBefore: GameUsageEntity?, prefsBefore: List<PreferenceSnapshot>,
        progressBefore: ProgressSnapshot?
    ) {
        val failures = mutableListOf<Throwable>()
        fun attempt(action: () -> Unit) { runCatching(action).exceptionOrNull()?.let(failures::add) }
        attempt { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }
        attempt {
            progressBefore?.let {
                // Real API exists in app/src/main/java/com/gamecenter/app/SaveManager.java.
                // An original null must become absence again, never the string "null" or {}.
                if (it.value == null) it.manager.deleteProgress(GAME_ID) else it.manager.saveProgress(GAME_ID, it.value)
            }
        }
        attempt {
            val knownNames = prefsBefore.map { it.name }.toSet()
            val appeared = relevantPreferenceNames().filterNot(knownNames::contains)
                .map { PreferenceSnapshot(it, emptyMap()) }
            // Commit over any preceding apply() writes, restore all original entries and
            // remove test-created entries in both old and newly created relevant stores.
            restoreAllPreferences(prefsBefore + appeared)
        }
        attempt {
            progressBefore?.let {
                assertTrue("Match SaveManager must load the exact original progress value or null",
                    it.value == it.manager.loadProgress(GAME_ID))
                assertEquals("Match progress presence must return to its original state",
                    it.existed, it.manager.hasProgress(GAME_ID))
            }
        }
        attempt {
            if (rowBefore != null) database.gameUsageDao().upsertSync(rowBefore)
            else runBlocking { database.gameUsageDao().delete(GAME_ID) }
            assertEquals("The full Match Room row must match its original fields or original absence",
                rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore Match user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
        Log.i(TAG, "Restored Match SaveManager original-null/value presence, relevant preference values, and complete Room row/absence")
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed match package",
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
            assertTrue("Shipped match must install through the production transaction",
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
        assertEquals("The shipped catalog must identify match exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals(100, manifest.versionCode)
        assertEquals("game_match_v100.apk", manifest.fileName)
        assertEquals("com.gamecenter.app.match.MatchModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped match must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped match must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-match-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("match APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("match APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("match APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
        @Suppress("DEPRECATION")
        val packageInfo = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES))
        assertEquals("com.gamecenter.app.match", packageInfo.packageName)
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
        assertEquals("The shipped Match APK manifest must identify version 100", 100L, archiveVersion)
        Log.i(TAG, "Actual Match archive package=${packageInfo.packageName} version=$archiveVersion " +
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
            throw AssertionError("Failed to restore match preferences after device verification").apply {
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
        assertTrue("Restore Match usage preferences: ${snapshot.name}", editor.commit())
        assertTrue("Match preference values must exactly match their pre-test state: ${snapshot.name}",
            snapshot.values == preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "match"
        private const val GAME_ID = "match"
        private const val TAG = "ShippedMatchTest"
        private const val PUBLISHER_SHA256 = "d058a18f9e89a29b5339eda27ece3ff9f78e0dbefe605d551e7745f724d2eddc"
    }
}

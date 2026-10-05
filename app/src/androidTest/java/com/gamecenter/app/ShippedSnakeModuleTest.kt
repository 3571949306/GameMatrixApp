package com.gamecenter.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Point
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
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
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
 * The real catalog APK, external ClassLoader, Fragment, View Handler and system swipes.
 * This is an explicit legal public-API fixture, NOT a naturally played complete game:
 * pauseGame + getGame().reset + getFood().set(11,15), then resumeGame once. The first
 * actual timer tick eats that food. Its public Point is then moved once to empty (0,0).
 * No private body/state/direction/seed/listener is written, and tick is never invoked.
 * UP, LEFT, DOWN are real DOWN/MOVE/MOVE/UP flings between the unchanged View ticks.
 * A separate 4ms main Handler observes immutable copies of public game data only.
 * Each tick uptime and gesture start/end is logged, with strict full-body sequences.
 * Only after the final pre-pause snapshot is saved is pauseGame used for PixelCopy.
 * The separate held-DOWN case uses reset/food(0,0), two real rightward ticks, then one
 * small upward MOVE beyond platform touch slop. Its next tick is observed before any
 * UP; the pointer is finally released with actual CANCEL after screenshot evidence.
 * All known/legacy snake preferences, flat/scoped SaveManager data and usage, plus only
 * the snake Room usage row, are restored in finally. The caller selects the device.
 */
@RunWith(AndroidJUnit4::class)
class ShippedSnakeModuleTest {
    private val observationHandler = Handler(Looper.getMainLooper())
    private var activeActivity: DynamicGameActivity? = null
    private var activeBoard: View? = null
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedSnakeLegalPublicFoodFixtureActualSwipesMayEnterTheVacatingTail() {
        withInstalledGame { scenario ->
            lateinit var paths: Map<String, SwipePath>
            onGameMain { activity ->
                val board = boardView(activity)
                paths = actualSwipePaths(board)
                call(board, "pauseGame")
                val game = requireNotNull(call(board, "getGame"))
                call(game, "reset")
                (call(game, "getFood") as Point).set(11, 15)
                call(board, "pauseGame")
                board.invalidate()
                assertEquals(SnakeState(INITIAL_BODY, Cell(11, 15), 0, false, false), state(board))
                Log.i(TAG, "EXPLICIT LEGAL PUBLIC FIXTURE: reset body=$INITIAL_BODY, food=(11,15); " +
                    "no private state writes, no tick call, default View speed untouched")
            }
            captureScreen("snake-tail-public-fixture-before-resume", scenario, ::boardView)

            val events = LinkedBlockingQueue<TickObservation>()
            val observerFailure = AtomicReference<Throwable?>()
            val cancelled = AtomicBoolean(false)
            val sampler = AtomicReference<Runnable?>()
            val trace = mutableListOf<TickObservation>()
            val gestures = mutableListOf<GestureObservation>()
            var finalScreenshotSaved = false
            var resumedAt = 0L
            try {
                onGameMain { activity ->
                    val board = boardView(activity)
                    val game = requireNotNull(call(board, "getGame"))
                    call(board, "resumeGame")
                    resumedAt = SystemClock.uptimeMillis()
                    var previous = state(board)
                    assertEquals(SnakeState(INITIAL_BODY, Cell(11, 15), 0, true, false), previous)
                    var tickNumber = 0
                    val request = object : Runnable {
                        override fun run() {
                            if (cancelled.get()) return
                            try {
                                var current = state(board)
                                if (current.body != previous.body || current.score != previous.score ||
                                    current.running != previous.running || current.gameOver != previous.gameOver) {
                                    tickNumber++
                                    val observedAt = SystemClock.uptimeMillis()
                                    if (tickNumber == 1 && current.body == ATE_BODY && current.score == 10 &&
                                        current.running && !current.gameOver) {
                                        // The only post-resume preparation: move the new public food Point
                                        // to a legal empty cell, after the real View already awarded 10.
                                        (call(game, "getFood") as Point).set(0, 0)
                                        current = state(board)
                                        Log.i(TAG, "EXPLICIT PUBLIC FOOD FIXTURE after actual eating tick: food=(0,0); " +
                                            "body/score/running/direction/listeners unchanged")
                                    }
                                    val event = TickObservation(tickNumber, observedAt, current)
                                    Log.i(TAG, "Actual View tick observed: $event")
                                    events.add(event)
                                    previous = current
                                    if (tickNumber == 4 || current.gameOver) {
                                        // Store the actual running/gameOver snapshot BEFORE evidence pause.
                                        call(board, "pauseGame")
                                        board.invalidate()
                                        Log.i(TAG, "Evidence-only final pause AFTER saved snapshot #$tickNumber; " +
                                            "prePauseRunning=${current.running}, prePauseGameOver=${current.gameOver}")
                                        return
                                    }
                                }
                                assertTrue("Queue only the next read-only observation", observationHandler.postDelayed(this, 4L))
                            } catch (error: Throwable) {
                                observerFailure.set(error)
                            }
                        }
                    }
                    sampler.set(request)
                    assertTrue(observationHandler.post(request))
                }

                fun awaitTick(number: Int): TickObservation {
                    val event = events.poll(1_000L, TimeUnit.MILLISECONDS)
                    observerFailure.get()?.let { throw it }
                    assertNotNull("Real View tick #$number must arrive; resume=$resumedAt, trace=$trace", event)
                    return requireNotNull(event).also {
                        trace.add(it)
                        assertEquals("No extra or missing actual tick may be hidden", number, it.number)
                    }
                }

                val ate = awaitTick(1)
                assertEquals("The original View Handler must really eat and grow to four cells",
                    SnakeState(ATE_BODY, Cell(0, 0), 10, true, false), ate.state)
                gestures.add(actualSwipe("UP", requireNotNull(paths["UP"]), ate))
                val up = awaitTick(2)
                assertEquals("The actual UP fling must control the immediately following tick",
                    SnakeState(UP_BODY, Cell(0, 0), 10, true, false), up.state)
                gestures.add(actualSwipe("LEFT", requireNotNull(paths["LEFT"]), up))
                val left = awaitTick(3)
                assertEquals("The actual LEFT fling must control the immediately following tick",
                    SnakeState(LEFT_BODY, Cell(0, 0), 10, true, false), left.state)
                gestures.add(actualSwipe("DOWN", requireNotNull(paths["DOWN"]), left))
                val tail = awaitTick(4)
                captureScreen("snake-tail-after-actual-down-preassert", scenario, ::boardView)
                finalScreenshotSaved = true
                Log.i(TAG, "Actual tail sequence resume=$resumedAt ticks=$trace gestures=$gestures; " +
                    "final snapshot predates evidence pause")
                assertTrue("The default first 140ms timer must actually elapse", ate.uptime - resumedAt >= 135L)
                // These are observation times, not exact production tick execution times:
                // main-queue delay can make two observations appear less than 140ms apart.
                val observedIntervals = trace.zipWithNext().map { (a, b) -> b.uptime - a.uptime }
                val observedSpan = tail.uptime - ate.uptime
                Log.i(TAG, "Tail observation intervals=$observedIntervals span=${observedSpan}ms; not a precise physics-clock measurement")
                assertTrue("Strictly advancing observations must remain within a bounded default-speed run",
                    observedIntervals.all { it in 110L..280L } && observedSpan in 350L..750L)
                for (index in gestures.indices) {
                    val gesture = gestures[index]
                    assertTrue("$gesture must finish before the next observed real tick ${trace[index + 1]}",
                        gesture.endedAt < trace[index + 1].uptime)
                }
                assertTrue("No unconsumed transition may be omitted from the evidence", events.isEmpty())
                assertFalse("Entering the cell vacated by this non-growing tail must not end the actual game", tail.state.gameOver)
                assertTrue("The recorded state must still be running before the evidence-only pause", tail.state.running)
                assertEquals("The actual DOWN tick must move into (10,15), retain four exact cells and keep score 10",
                    SnakeState(TAIL_BODY, Cell(0, 0), 10, true, false), tail.state)
            } catch (error: Throwable) {
                // Evidence-only pause is not a production movement and must never be
                // reported by the still-live observer as an additional gameplay tick.
                cancelled.set(true)
                sampler.get()?.let(observationHandler::removeCallbacks)
                Log.e(TAG, "Actual timing/rule failure: ticks=$trace gestures=$gestures", error)
                if (!finalScreenshotSaved) {
                    runCatching {
                        onGameMain { call(boardView(it), "pauseGame") }
                        captureScreen("snake-tail-input-or-timing-failure", scenario, ::boardView)
                    }.exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            } finally {
                cancelled.set(true)
                sampler.get()?.let(observationHandler::removeCallbacks)
                onGameMain { call(boardView(it), "pauseGame") }
            }
        }
    }

    @Test
    fun shippedSnakeLegalPublicFoodFixtureActualSlowMoveTurnsBeforeUp() {
        withInstalledGame { scenario ->
            lateinit var target: SlowMoveTarget
            onGameMain { activity ->
                val board = boardView(activity)
                call(board, "pauseGame")
                val game = requireNotNull(call(board, "getGame"))
                call(game, "reset")
                (call(game, "getFood") as Point).set(0, 0)
                call(board, "pauseGame")
                board.invalidate()
                assertEquals(SnakeState(INITIAL_BODY, Cell(0, 0), 0, false, false), state(board))
                val geometry = canvasGeometry(board)
                val safe = Rect(geometry.visibleOnScreen)
                assertTrue(safe.intersect(geometry.windowFrame))
                val configuration = ViewConfiguration.get(board.context)
                val distance = configuration.scaledTouchSlop + maxOf(1f, board.resources.displayMetrics.density)
                val start = TouchPoint(safe.exactCenterX(), safe.exactCenterY())
                val end = TouchPoint(start.x, start.y - distance)
                val halfTarget = kotlin.math.ceil(24.0 * board.resources.displayMetrics.density).toInt()
                val corridor = Rect(kotlin.math.floor(start.x - halfTarget).toInt(),
                    kotlin.math.floor(end.y - halfTarget).toInt(), kotlin.math.ceil(start.x + halfTarget).toInt(),
                    kotlin.math.ceil(start.y + halfTarget).toInt())
                assertTrue("The complete small-MOVE 48dp corridor must be visible", geometry.visibleOnScreen.contains(corridor))
                assertTrue("The complete small-MOVE corridor must avoid system bars", geometry.windowFrame.contains(corridor))
                target = SlowMoveTarget(start, end, configuration.scaledTouchSlop, configuration.scaledMinimumFlingVelocity)
                assertTrue(distance > target.touchSlop)
                assertTrue(target.minimumFlingVelocity > 0)
                Log.i(TAG, "EXPLICIT LEGAL PUBLIC slow-MOVE fixture: reset body=$INITIAL_BODY food=(0,0); " +
                    "default speed unchanged, target=$target corridor=$corridor geometry=$geometry")
            }
            captureScreen("snake-small-move-public-fixture", scenario, ::boardView)

            val events = LinkedBlockingQueue<TickObservation>()
            val observerFailure = AtomicReference<Throwable?>()
            val cancelled = AtomicBoolean(false)
            val sampler = AtomicReference<Runnable?>()
            val trace = mutableListOf<TickObservation>()
            var resumedAt = 0L
            var resumeRequestedAt = 0L
            var downTime: Long? = null
            var finalScreenshotSaved = false
            var primaryFailure: Throwable? = null
            try {
                onGameMain { activity ->
                    val board = boardView(activity)
                    resumeRequestedAt = SystemClock.uptimeMillis()
                    call(board, "resumeGame")
                    resumedAt = SystemClock.uptimeMillis()
                    var previous = state(board)
                    assertEquals(SnakeState(INITIAL_BODY, Cell(0, 0), 0, true, false), previous)
                    var tickNumber = 0
                    val request = object : Runnable {
                        override fun run() {
                            if (cancelled.get()) return
                            try {
                                val current = state(board)
                                if (current != previous) {
                                    val event = TickObservation(++tickNumber, SystemClock.uptimeMillis(), current)
                                    Log.i(TAG, "Held-DOWN actual View tick observed: $event")
                                    events.add(event)
                                    previous = current
                                    if (tickNumber == 3 || current.gameOver) {
                                        // Publish the live snapshot first. No UP has been injected.
                                        call(board, "pauseGame")
                                        board.invalidate()
                                        Log.i(TAG, "Held-DOWN evidence-only pause after snapshot #$tickNumber; " +
                                            "prePauseRunning=${current.running}, prePauseGameOver=${current.gameOver}; no UP")
                                        return
                                    }
                                }
                                assertTrue(observationHandler.postDelayed(this, 4L))
                            } catch (error: Throwable) { observerFailure.set(error) }
                        }
                    }
                    sampler.set(request)
                    assertTrue(observationHandler.post(request))
                }

                val pointerDownAt = SystemClock.uptimeMillis()
                downTime = pointerDownAt
                injectTouch(pointerDownAt, MotionEvent.ACTION_DOWN, target.start)
                val downAcceptedAt = SystemClock.uptimeMillis()
                Log.i(TAG, "Actual held DOWN injected=$pointerDownAt accepted=$downAcceptedAt; " +
                    "resumeRequested=$resumeRequestedAt resume=$resumedAt, point=${target.start}; pointer remains down")
                // resumeGame posts the first default tick with a 140ms delay. Recording
                // before that call provides its earliest possible execution deadline;
                // input-injection latency has no independent 100ms gameplay requirement.
                assertTrue("DOWN must be accepted before the earliest default first-tick deadline",
                    downAcceptedAt - resumeRequestedAt in 0L until 140L)

                fun awaitTick(number: Int): TickObservation {
                    val event = events.poll(1_000L, TimeUnit.MILLISECONDS)
                    observerFailure.get()?.let { throw it }
                    assertNotNull("Held-DOWN actual tick #$number must arrive, trace=$trace", event)
                    return requireNotNull(event).also {
                        trace.add(it)
                        assertEquals("No extra or missing tick may be hidden", number, it.number)
                    }
                }

                val first = awaitTick(1)
                assertTrue("The real DOWN must be accepted before the first observed default tick", downAcceptedAt < first.uptime)
                assertEquals("Holding DOWN alone must allow the first normal rightward tick",
                    SnakeState(listOf(Cell(11, 15), Cell(10, 15), Cell(9, 15)), Cell(0, 0), 0, true, false), first.state)
                val second = awaitTick(2)
                assertEquals("Holding DOWN must preserve a second real rightward tick",
                    SnakeState(listOf(Cell(12, 15), Cell(11, 15), Cell(10, 15)), Cell(0, 0), 0, true, false), second.state)
                val moveStartedAt = SystemClock.uptimeMillis()
                assertTrue("Inject MOVE promptly within the next 140ms window", moveStartedAt - second.uptime in 0L..30L)
                val heldMillis = moveStartedAt - pointerDownAt
                assertTrue(heldMillis > 0L)
                val distance = target.start.y - target.end.y
                val averagePixelsPerSecond = distance * 1000f / heldMillis
                // This is the whole DOWN-to-MOVE average, not an invented VelocityTracker
                // instantaneous value. With no UP, the onFling path cannot cause the turn.
                assertTrue("This small movement's held-DOWN average must be below the platform minimum fling velocity",
                    averagePixelsPerSecond < target.minimumFlingVelocity)
                Log.i(TAG, "Actual small MOVE begin=$moveStartedAt previousTick=${second.uptime} held=${heldMillis}ms " +
                    "distance=$distance touchSlop=${target.touchSlop} average=${averagePixelsPerSecond}px/s " +
                    "minimumFling=${target.minimumFlingVelocity}px/s; no UP")
                injectTouch(pointerDownAt, MotionEvent.ACTION_MOVE, target.end)
                val moveAcceptedAt = SystemClock.uptimeMillis()
                Log.i(TAG, "Actual small MOVE accepted=$moveAcceptedAt elapsed=${moveAcceptedAt - moveStartedAt}ms; no UP")
                assertTrue("The actual MOVE must be accepted within 100ms", moveAcceptedAt - moveStartedAt <= 100L)
                assertTrue("The actual MOVE must complete before the next default tick window",
                    moveAcceptedAt - second.uptime <= 120L)
                val turned = awaitTick(3)
                captureScreen("snake-small-move-before-up-preassert", scenario, ::boardView)
                finalScreenshotSaved = true
                Log.i(TAG, "Held-DOWN small-MOVE sequence resume=$resumedAt DOWN=$pointerDownAt/$downAcceptedAt " +
                    "MOVE=$moveStartedAt/$moveAcceptedAt trace=$trace; final snapshot BEFORE pause and BEFORE UP")
                assertTrue("Default first 140ms timer must actually elapse", first.uptime - resumedAt >= 135L)
                val observedIntervals = trace.zipWithNext().map { (a, b) -> b.uptime - a.uptime }
                val observedSpan = turned.uptime - first.uptime
                Log.i(TAG, "Held-DOWN observation intervals=$observedIntervals span=${observedSpan}ms; not exact tick execution times")
                assertTrue("Strictly advancing observations must remain within a bounded default-speed run",
                    observedIntervals.all { it in 110L..280L } && observedSpan in 220L..560L)
                assertTrue("MOVE must precede the observed turning tick", moveAcceptedAt < turned.uptime)
                assertTrue("No extra observed tick can be omitted", events.isEmpty())
                assertEquals("An upward MOVE beyond platform slop must turn on the next real tick before the finger lifts",
                    SnakeState(listOf(Cell(12, 14), Cell(12, 15), Cell(11, 15)), Cell(0, 0), 0, true, false), turned.state)
            } catch (error: Throwable) {
                primaryFailure = error
                cancelled.set(true)
                sampler.get()?.let(observationHandler::removeCallbacks)
                Log.e(TAG, "Held-DOWN timing/input/turn failure: trace=$trace", error)
                if (!finalScreenshotSaved) runCatching {
                    onGameMain { call(boardView(it), "pauseGame") }
                    captureScreen("snake-small-move-timing-or-input-failure", scenario, ::boardView)
                }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            } finally {
                cancelled.set(true)
                sampler.get()?.let(observationHandler::removeCallbacks)
                val cleanupFailures = listOf(
                    runCatching {
                        downTime?.let {
                            injectTouch(it, MotionEvent.ACTION_CANCEL, target.end)
                            Log.i(TAG, "Actual held pointer released by accepted CANCEL uptime=${SystemClock.uptimeMillis()} after saved evidence")
                        }
                    }.exceptionOrNull(),
                    runCatching { onGameMain { call(boardView(it), "pauseGame") } }.exceptionOrNull()
                ).filterNotNull()
                if (primaryFailure != null) cleanupFailures.forEach(requireNotNull(primaryFailure)::addSuppressed)
                else if (cleanupFailures.isNotEmpty()) throw AssertionError("Failed to release held Snake input").apply {
                    cleanupFailures.forEach(::addSuppressed)
                }
            }
        }
    }

    private data class SlowMoveTarget(val start: TouchPoint, val end: TouchPoint, val touchSlop: Int, val minimumFlingVelocity: Int)
    private data class Cell(val x: Int, val y: Int)
    private data class SnakeState(val body: List<Cell>, val food: Cell, val score: Int, val running: Boolean, val gameOver: Boolean)
    private data class TickObservation(val number: Int, val uptime: Long, val state: SnakeState)
    private data class GestureObservation(val direction: String, val startedAt: Long, val endedAt: Long)
    private data class TouchPoint(val x: Float, val y: Float)
    private data class SwipePath(val start: TouchPoint, val end: TouchPoint)
    private data class CanvasGeometry(val bounds: Rect, val localVisible: Rect, val visibleOnScreen: Rect, val windowFrame: Rect)

    private fun call(owner: Any, name: String): Any? = owner.javaClass.getMethod(name).invoke(owner)

    private fun state(board: View): SnakeState {
        val game = requireNotNull(call(board, "getGame"))
        val body = (call(game, "getSnake") as List<*>).map {
            val point = it as Point
            Cell(point.x, point.y)
        }
        val food = call(game, "getFood") as Point
        return SnakeState(body, Cell(food.x, food.y), call(game, "getScore") as Int,
            call(game, "isRunning") as Boolean, call(game, "isGameOver") as Boolean)
    }

    private fun actualSwipe(direction: String, path: SwipePath, precedingTick: TickObservation): GestureObservation {
        assertFalse(Looper.myLooper() === Looper.getMainLooper())
        val startedAt = SystemClock.uptimeMillis()
        assertTrue("Begin $direction promptly after the previous real tick, not after a global idle wait",
            startedAt - precedingTick.uptime in 0L..30L)
        Log.i(TAG, "Actual $direction gesture DOWN uptime=$startedAt after tick=${precedingTick.number}/${precedingTick.uptime} path=$path")
        var finished = false
        try {
            injectTouch(startedAt, MotionEvent.ACTION_DOWN, path.start)
            SystemClock.sleep(8L)
            injectTouch(startedAt, MotionEvent.ACTION_MOVE,
                TouchPoint((path.start.x + path.end.x) / 2f, (path.start.y + path.end.y) / 2f))
            SystemClock.sleep(8L)
            // Native VelocityTracker ignores UP coordinates as a movement sample.
            // Supply the real terminal motion first; immediately lift at that same point.
            injectTouch(startedAt, MotionEvent.ACTION_MOVE, path.end)
            injectTouch(startedAt, MotionEvent.ACTION_UP, path.end)
            finished = true
        } finally {
            if (!finished) injectTouch(startedAt, MotionEvent.ACTION_CANCEL, path.start)
        }
        val endedAt = SystemClock.uptimeMillis()
        val observed = GestureObservation(direction, startedAt, endedAt)
        Log.i(TAG, "Actual gesture complete: $observed elapsed=${endedAt - startedAt}ms " +
            "sincePreviousTick=${endedAt - precedingTick.uptime}ms")
        assertTrue("A genuine $direction fling must finish within 100ms; do not slow or pause the game to pass",
            endedAt - startedAt in 16L..100L)
        assertTrue("Complete $direction inside the unchanged 140ms tick interval",
            endedAt - precedingTick.uptime <= 120L)
        return observed
    }

    private fun injectTouch(downTime: Long, action: Int, point: TouchPoint) {
        val eventTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, eventTime, action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            val accepted = InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
            Log.i(TAG, "Actual input action=$action downTime=$downTime eventTime=$eventTime " +
                "acceptedAt=${SystemClock.uptimeMillis()} accepted=$accepted point=$point")
            assertTrue("System must accept actual snake touchscreen event $action", accepted)
        } finally { event.recycle() }
    }

    private fun actualSwipePaths(board: View): Map<String, SwipePath> {
        val geometry = canvasGeometry(board)
        val safe = Rect(geometry.visibleOnScreen)
        assertTrue(safe.intersect(geometry.windowFrame))
        val dp = board.resources.displayMetrics.density
        val halfTarget = kotlin.math.ceil(24.0 * dp).toInt()
        val distance = minOf(100f * dp, safe.width() / 4f, safe.height() / 4f)
        assertTrue("Actual visible board must permit a clear 64dp or longer fling", distance >= 64f * dp)
        val start = TouchPoint(safe.exactCenterX(), safe.exactCenterY())
        val paths = mapOf("UP" to SwipePath(start, TouchPoint(start.x, start.y - distance)),
            "LEFT" to SwipePath(start, TouchPoint(start.x - distance, start.y)),
            "DOWN" to SwipePath(start, TouchPoint(start.x, start.y + distance)))
        for ((direction, path) in paths) {
            val corridor = Rect(kotlin.math.floor(minOf(path.start.x, path.end.x) - halfTarget).toInt(),
                kotlin.math.floor(minOf(path.start.y, path.end.y) - halfTarget).toInt(),
                kotlin.math.ceil(maxOf(path.start.x, path.end.x) + halfTarget).toInt(),
                kotlin.math.ceil(maxOf(path.start.y, path.end.y) + halfTarget).toInt())
            assertTrue("Complete 48dp-wide $direction touch corridor must be visible", geometry.visibleOnScreen.contains(corridor))
            assertTrue("Complete $direction touch corridor must avoid system bars", geometry.windowFrame.contains(corridor))
            Log.i(TAG, "Actual $direction 48dp touch corridor=$corridor path=$path geometry=$geometry")
        }
        return paths
    }

    private fun withInstalledGame(action: (ActivityScenario<DynamicGameActivity>) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val known = listOf("snake", "snake_module", "snake_save", "snake_settings", "GameMatrix_saves",
                "game_usage", "streak_tracker", "play_time_limit")
            val existing = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "xml" }.map { it.nameWithoutExtension }
                .filter { it == "snake" || it.startsWith("snake_") || it.startsWith("mod_snake__") ||
                    it.endsWith("__GameMatrix_saves") }
            // SaveManager is a host singleton. Preserve every existing scoped saves store
            // too, in case its first Context came from an earlier module in this process.
            val prefsBefore = (known + known.map { ModuleScopedPreferences.scopedName(MODULE_ID, it) } + existing)
                .distinct().map(::capturePreferences)
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            try {
                ensureShipped(manifest, apk)
                val launched = ActivityScenario.launch<DynamicGameActivity>(
                    Intent(context, DynamicGameActivity::class.java)
                        .putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                scenario = launched
                // Only this initial lookup uses ActivityScenario's global-idle path.
                // All live gameplay observations thereafter use our bounded main queue.
                launched.onActivity { activity ->
                    activeActivity = activity
                    val fragment = gameFragment(activity)
                    activeBoard = fragment.javaClass.getDeclaredField("snakeView").apply { isAccessible = true }.get(fragment) as View
                    assertEquals("com.gamecenter.app.snake.SnakeView", requireNotNull(activeBoard).javaClass.name)
                    assertSame(ModuleLoader.getClassLoader(MODULE_ID), requireNotNull(activeBoard).javaClass.classLoader)
                }
                action(launched)
            } finally {
                try { scenario?.close() } finally {
                    try { ModuleManager.unloadModule(context, MODULE_ID) } finally {
                        try { restoreUserState(database, usageBefore, prefsBefore) } finally {
                            activeBoard = null
                            activeActivity = null
                            if (previousCatalog != null) ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                        }
                    }
                }
            }
        }
    }

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("Real external Snake Fragment must attach", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.snake.SnakeModuleFragment", it.javaClass.name)
            assertFalse("Snake must not use host game classes", it.javaClass.classLoader === context.classLoader)
            assertSame("Snake must use the verified module ClassLoader", ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
            assertTrue(it.requireView().isShown)
        }
    }

    private fun boardView(activity: DynamicGameActivity): View = requireNotNull(activeBoard).also {
        assertSame("Keep the originally loaded Activity", activeActivity, activity)
        assertTrue("The real Snake view must remain attached", it.isAttachedToWindow)
    }

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
        scenario: ActivityScenario<DynamicGameActivity>,
        targetView: (DynamicGameActivity) -> View
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
        } finally {
            copyCancelled.set(true)
            // A main request may still be submitting after its bounded wait timed out.
            // Only a completed PixelCopy may be recycled here. Pending submission/copy
            // keeps ownership until its callback or the submission catch handles it.
            if (copyReady.count == 0L) ownedBitmap.getAndSet(null)?.recycle()
        }
    }

    private fun restoreUserState(
        database: AppDatabase, rowBefore: GameUsageEntity?, prefsBefore: List<PreferenceSnapshot>
    ) {
        // GameUsageStore writes Room synchronously; the UI callbacks have returned before
        // teardown. Drain UI work, commit over previous preference apply writes, and await
        // the existing suspend DAO delete when this game had no pre-test row.
        val failures = listOf(
            runCatching { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }.exceptionOrNull(),
            runCatching { restoreAllPreferences(prefsBefore) }.exceptionOrNull(),
            runCatching {
                if (rowBefore != null) database.gameUsageDao().upsertSync(rowBefore)
                else runBlocking { database.gameUsageDao().delete(GAME_ID) }
                assertEquals("snake Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore snake user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed snake package",
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
            assertTrue("Shipped snake must install through the production transaction",
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
        assertEquals("The shipped catalog must identify snake exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals(100, manifest.versionCode)
        assertEquals("game_snake_v100.apk", manifest.fileName)
        assertEquals("com.gamecenter.app.snake.SnakeModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped snake must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped snake must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-snake-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("snake APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("snake APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("snake APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
        @Suppress("DEPRECATION")
        val packageInfo = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0))
        assertEquals("com.gamecenter.app.snake", packageInfo.packageName)
        @Suppress("DEPRECATION")
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        assertEquals("The shipped Snake APK manifest must identify version 100", 100L, archiveVersion)
        Log.i(TAG, "Actual Snake archive package=${packageInfo.packageName} version=$archiveVersion " +
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
            throw AssertionError("Failed to restore snake preferences after device verification").apply {
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
        assertTrue("Restore Snake usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Snake usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "snake"
        private const val GAME_ID = "snake"
        private const val TAG = "ShippedSnakeTest"
        private val INITIAL_BODY = listOf(Cell(10, 15), Cell(9, 15), Cell(8, 15))
        private val ATE_BODY = listOf(Cell(11, 15), Cell(10, 15), Cell(9, 15), Cell(8, 15))
        private val UP_BODY = listOf(Cell(11, 14), Cell(11, 15), Cell(10, 15), Cell(9, 15))
        private val LEFT_BODY = listOf(Cell(10, 14), Cell(11, 14), Cell(11, 15), Cell(10, 15))
        private val TAIL_BODY = listOf(Cell(10, 15), Cell(10, 14), Cell(11, 14), Cell(11, 15))
    }
}

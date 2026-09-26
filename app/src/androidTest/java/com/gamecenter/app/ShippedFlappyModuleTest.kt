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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
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
 * Real shipped Flappy APK, real difficulty/launch system touches and the production
 * Fragment Handler generate and move the first unseeded pipe. Reflection only reads
 * fields. There are no private writes, direct update/jump calls, seeded randomness,
 * synthetic draw calls or fabricated pipe/bird fixtures. Public pauseGame freezes
 * evidence only AFTER one immutable running-state snapshot has been taken.
 * Window PixelCopy measures the actual bird circle and visible pipe opening. This
 * checks one opening's geometry; it does NOT prove natural crossing, scoring or that
 * a complete randomly generated sequence can be played. Full Flappy/SaveManager
 * preferences and the Flappy Room usage row are restored. Caller owns device selection.
 */
@RunWith(AndroidJUnit4::class)
class ShippedFlappyModuleTest {
    private val observationHandler = Handler(Looper.getMainLooper())
    private var activeActivity: DynamicGameActivity? = null
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedFlappyActualNormalTouchFirstNaturalPipePixelsContainTheBird() {
        checkFirstOpening("btnNormal", 0.5f, 180f)
    }

    @Test
    fun shippedFlappyActualHardTouchFirstNaturalPipePixelsContainTheBird() {
        checkFirstOpening("btnHard", 0.8f, 126f)
    }

    /**
     * One unseeded Normal round controlled by actual system touches using read-only
     * position/velocity feedback. This is automatic flight, not a human-skill test.
     * It proves one real pipe crossing and score update; it does not certify every
     * random sequence or another difficulty. The game is never paused until the
     * first pipe (including its visible rim) has completely cleared the bird.
     */
    @Test
    fun shippedFlappyActualNormalFlightClearsFirstNaturalPipeAndScoresOne() {
        withInstalledGame { _, _, _ ->
            val label = "flappy-normal-natural-first-pipe"
            val trace = org.json.JSONArray()
            val touches = org.json.JSONArray()
            try {
                captureScreen("$label-ready", ::screenView)
                touchButton("btnEasy")
                touchButton("btnNormal")
                onGameMain { activity ->
                    assertEquals(0.5f, field(gameFragment(activity), "difficultyFactor") as Float, 0f)
                    val ready = snapshot(boardView(activity))
                    assertTrue(ready.running && !ready.started && !ready.paused && !ready.gameOver)
                    assertEquals(0, ready.score)
                    assertTrue(ready.pipes.isEmpty())
                    assertEquals(4f, ready.density, 0f)
                    assertEquals(720f, ready.gap, 0.001f)
                }
                val launch = touchBoard()
                touches.put(flightTouchJson(launch))
                val deadline = launch.downEventTime + 12_000L
                var firstCenter: Float? = null
                var priorPipeX: Float? = null
                var priorScore = 0
                var firstScoreAt: Long? = null
                var observedFirstOverlap = false
                var finished: FlightObservation? = null
                var lastLoggedAt = 0L
                while (SystemClock.uptimeMillis() < deadline && finished == null) {
                    val observation = observeFlightAndPauseOnlyAfterFullClear(firstCenter)
                    val state = observation.state
                    val firstPipe = state.pipes.firstOrNull()
                    trace.put(JSONObject().put("at", state.observedAt).put("running", state.running)
                        .put("paused", state.paused).put("gameOver", state.gameOver)
                        .put("birdY", state.birdY).put("velocity", observation.velocity)
                        .put("score", state.score).put("pipeCount", state.pipes.size)
                        .put("firstX", firstPipe?.x).put("firstGapCenter", firstPipe?.gapCenter)
                        .put("birdX", state.birdX).put("radius", state.radius).put("gap", state.gap)
                        .put("pipeWidth", state.pipeWidth).put("width", state.width).put("height", state.height)
                        .put("pipes", org.json.JSONArray().apply {
                            state.pipes.forEach { put(JSONObject().put("x", it.x).put("gapCenter", it.gapCenter)) }
                        }))
                    assertTrue("Natural flight must remain active before any evidence pause: $observation",
                        state.running && state.started && !state.paused && !state.gameOver)
                    assertTrue("Natural score must move monotonically from zero to one", state.score in priorScore..1)
                    priorScore = state.score
                    if (firstPipe != null) {
                        if (firstCenter == null) firstCenter = firstPipe.gapCenter
                        assertEquals("The same unmodified first random pipe remains first until fully cleared",
                            requireNotNull(firstCenter), firstPipe.gapCenter, 0f)
                        priorPipeX?.let {
                            assertTrue("The natural first pipe can only move left", firstPipe.x <= it)
                        }
                        priorPipeX = firstPipe.x
                        if (state.birdX + state.radius > firstPipe.x &&
                            state.birdX - state.radius < firstPipe.x + state.pipeWidth) {
                            observedFirstOverlap = true
                            assertTrue("The complete bird circle must remain in the natural opening during overlap",
                                state.birdY - state.radius >= firstPipe.gapCenter - state.gap / 2f &&
                                    state.birdY + state.radius <= firstPipe.gapCenter + state.gap / 2f)
                        }
                    }
                    if (state.score == 1 && firstScoreAt == null) firstScoreAt = state.observedAt
                    if (state.observedAt - lastLoggedAt >= 250L || observation.pausedAfterFullClear) {
                        Log.i(TAG, "$label actual flight observation=$observation")
                        lastLoggedAt = state.observedAt
                    }
                    if (observation.pausedAfterFullClear) {
                        finished = observation
                    } else {
                        val target = (firstCenter ?: (state.height / 2f)) + 40f
                        // Source physics is +0.5px/update with a -8px touch impulse.
                        // Eight-update look-ahead is a control heuristic, not synthetic
                        // advancement. Every resulting action is a real touchscreen tap.
                        val projected = state.birdY + observation.velocity * 8f + 18f
                        if (state.birdY >= target || (observation.velocity >= 0f && projected >= target)) {
                            val receipt = touchBoard()
                            touches.put(flightTouchJson(receipt))
                            assertTrue("Flight input must be responsive enough for the observed physics",
                                receipt.upAcceptedAt - receipt.downEventTime <= 100L)
                        }
                        SystemClock.sleep(12L)
                    }
                }
                val completed = requireNotNull(finished) { "Actual first-pipe crossing must complete within 12 seconds" }
                val state = completed.state
                assertTrue("Read-only observations must include actual first-pipe overlap", observedFirstOverlap)
                assertEquals("The real public score must count exactly the first pipe", 1, state.score)
                assertNotNull("The score transition must actually be observed", firstScoreAt)
                assertTrue("Scoring precedes full clearance, rather than being treated as clearance",
                    requireNotNull(firstScoreAt) < state.observedAt)
                val pipe = state.pipes.first()
                assertTrue("The first pipe body and 4dp visible rim must fully pass the circle",
                    pipe.x + state.pipeWidth + 4f * state.density <= state.birdX - state.radius)
                assertTrue("Second pipe must not yet threaten the bird in this first-pipe test",
                    state.pipes.drop(1).all { it.x > state.birdX + state.radius })
                onGameMain { activity ->
                    val scoreLabel = field(gameFragment(activity), "tvScore") as android.widget.TextView
                    assertEquals("The real HUD must show the actual first point",
                        activity.getString(R.string.game_score_alt_format, 1), scoreLabel.text.toString())
                }
                saveFlightTrace(label, launch.downEventTime, trace, touches)
                assertOnlyEvidencePauseChanged(state)
                captureScreen("$label-cleared-score-1", ::screenView)
                assertOnlyEvidencePauseChanged(state)
                Log.i(TAG, "$label PASS actual normal first pipe fully cleared; score=1; " +
                    "elapsed=${state.observedAt - launch.downEventTime}ms observations=${trace.length()} actualTouches=${touches.length()}")
                assertActualRestartClearsScoreAndStartsANewRound(label, state)
            } catch (error: Throwable) {
                runCatching { saveFlightTrace("$label-failure", null, trace, touches) }
                    .exceptionOrNull()?.let(error::addSuppressed)
                runCatching {
                    onGameMain { activity ->
                        val board = boardView(activity)
                        Log.i(TAG, "$label failure BEFORE public evidence pause=${snapshot(board)}")
                        board.javaClass.getMethod("pauseGame").invoke(board)
                    }
                    captureScreen("$label-failure-evidence", ::screenView)
                }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private fun assertActualRestartClearsScoreAndStartsANewRound(label: String, completed: GameSnapshot) {
        lateinit var restartPoint: TouchPoint
        onGameMain { activity ->
            val board = boardView(activity)
            val before = snapshot(board)
            assertEquals("Restart begins only after the saved score-one flight and its evidence pause",
                completed.copy(paused = true, observedAt = before.observedAt), before)
            assertTrue(before.running && before.started && before.paused && !before.gameOver)
            assertEquals(1, before.score)
            val restart = field(gameFragment(activity), "btnRestart") as Button
            val bounds = buttonBounds(restart)
            assertTrue("The actual restart control must be enabled and clickable", restart.isEnabled && restart.isClickable)
            restartPoint = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        // The completed round is already paused for saved evidence. This menu action
        // uses the READY/menu budget; it does not extend launch or flight input limits.
        val restartTouch = touch(restartPoint, maximumDurationMs = 1_500L)
        val readyDeadline = SystemClock.uptimeMillis() + 1_500L
        var ready: GameSnapshot? = null
        var latest: GameSnapshot? = null
        while (ready == null && SystemClock.uptimeMillis() < readyDeadline) {
            onGameMain { activity ->
                val current = snapshot(boardView(activity))
                latest = current
                if (current.running && !current.started && !current.paused && !current.gameOver &&
                    current.score == 0 && current.pipes.isEmpty()) ready = current
            }
            if (ready == null) SystemClock.sleep(12L)
        }
        assertNotNull("The real restart click must reach a zero-score, empty-pipe READY state; latest=$latest", ready)
        val restarted = requireNotNull(ready)
        assertTrue("Observe the restarted READY state within its 1500ms menu deadline",
            restarted.observedAt <= readyDeadline)
        onGameMain { activity ->
            Log.i(TAG, "$label actual restart input=$restartTouch READY=$restarted " +
                "HUD=${(field(gameFragment(activity), "tvScore") as android.widget.TextView).text}")
        }
        // Preserve actual visible stale-HUD evidence BEFORE asserting the zero text.
        captureScreen("$label-restarted-ready-before-hud-assert", ::screenView)
        onGameMain { activity ->
            val now = assertReadyMenuState(boardView(activity), "after actual restart")
            assertEquals("READY must remain unchanged while its actual pixels are saved",
                restarted.copy(observedAt = now.observedAt), now)
            assertEquals("Restart must retain the selected Normal difficulty", 0.5f,
                field(gameFragment(activity), "difficultyFactor") as Float, 0f)
            assertEquals("Restart must clear the public game score", 0, now.score)
            assertEquals("Restart must clear the actual displayed score as well as the game score",
                activity.getString(R.string.game_score_alt_format, 0),
                (field(gameFragment(activity), "tvScore") as android.widget.TextView).text.toString())
        }

        // One new real launch and the original production Handler are enough to show
        // the restarted round advances. Do not fabricate an update or fly a second pipe.
        val secondLaunch = touchBoard() // Existing 250ms board-touch budget is unchanged.
        val secondFlight = waitForVisiblePipeAndPause(secondLaunch.downEventTime)
        Log.i(TAG, "$label restarted natural flight BEFORE public evidence pause=$secondFlight launch=$secondLaunch")
        assertTrue(secondFlight.running && secondFlight.started && !secondFlight.paused && !secondFlight.gameOver)
        assertEquals("The short restarted launch must not inherit the previous point", 0, secondFlight.score)
        assertEquals("The fresh Handler must naturally generate its own first pipe", 1, secondFlight.pipes.size)
        assertTrue("The fresh Handler must naturally move that new pipe visibly on screen",
            secondFlight.width - secondFlight.pipes.single().x >= 32f)
        assertTrue("Observe the restarted first pipe within the unchanged launch window",
            secondFlight.observedAt - secondLaunch.downEventTime in 1L..750L)
        assertTrue("This restart smoke check ends before any new pipe could be crossed",
            secondFlight.pipes.single().x > secondFlight.birdX + secondFlight.radius)
        assertOnlyEvidencePauseChanged(secondFlight)
        captureScreen("$label-restarted-natural-flight-score-0", ::screenView)
        assertOnlyEvidencePauseChanged(secondFlight)
        onGameMain { activity ->
            assertEquals("The newly advancing round must still display score zero",
                activity.getString(R.string.game_score_alt_format, 0),
                (field(gameFragment(activity), "tvScore") as android.widget.TextView).text.toString())
        }
        Log.i(TAG, "$label actual restart PASS: saved first-pipe score1, real restart to public/HUD0, " +
            "then real touch and fresh natural Handler advancement")
    }


    private data class FlightObservation(val state: GameSnapshot, val velocity: Float, val pausedAfterFullClear: Boolean)

    private fun observeFlightAndPauseOnlyAfterFullClear(firstCenter: Float?): FlightObservation {
        lateinit var result: FlightObservation
        onGameMain { activity ->
            val board = boardView(activity)
            val state = snapshot(board)
            val velocity = field(board, "birdVelocity") as Float
            val first = state.pipes.firstOrNull()
            val cleared = firstCenter != null && first != null && first.gapCenter == firstCenter &&
                state.running && state.started && !state.paused && !state.gameOver && state.score == 1 &&
                first.x + state.pipeWidth + 4f * state.density <= state.birdX - state.radius
            result = FlightObservation(state, velocity, cleared)
            if (cleared) board.javaClass.getMethod("pauseGame").invoke(board)
        }
        return result
    }

    private fun flightTouchJson(receipt: TouchReceipt): JSONObject = JSONObject()
        .put("downEventTime", receipt.downEventTime).put("upEventTime", receipt.upEventTime)
        .put("upAcceptedAt", receipt.upAcceptedAt)

    private fun saveFlightTrace(label: String, launchAt: Long?, trace: org.json.JSONArray, touches: org.json.JSONArray) {
        val directory = File(context.filesDir, "gamematrix-qa")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val destination = File(directory, "$label-${System.currentTimeMillis()}.json")
        destination.writeText(JSONObject().put("scope", "real system-touch Normal first-pipe flight, read-only feedback")
            .put("launchAt", launchAt).put("observations", trace).put("actualTouches", touches).toString(2))
        assertTrue(destination.length() > 0L)
        Log.i(TAG, "Actual flight observation/touch trace ${destination.absolutePath}")
    }


    private fun checkFirstOpening(difficultyButton: String, expectedFactor: Float, expectedGapDp: Float) {
        withInstalledGame { _, _, _ ->
            val label = "flappy-${difficultyButton.removePrefix("btn").lowercase()}"
            try {
                captureScreen("$label-opened", ::screenView)
                onGameMain { activity ->
                    val fragment = gameFragment(activity)
                    val rects = listOf("btnEasy", "btnNormal", "btnHard", "btnRestart")
                        .map { buttonBounds(field(fragment, it) as Button) }
                    for (i in rects.indices) for (j in i + 1 until rects.size) {
                        assertFalse("The actual controls must not overlap", Rect.intersects(rects[i], rects[j]))
                    }
                }
                // Start from a different real selection so the subsequent Normal touch
                // proves a menu transition even though Normal is the default difficulty.
                touchButton("btnEasy")
                onGameMain { activity ->
                    assertEquals(0.3f, field(gameFragment(activity), "difficultyFactor") as Float, 0f)
                }
                touchButton(difficultyButton)
                onGameMain { activity ->
                    val fragment = gameFragment(activity)
                    assertEquals("The actual selected menu must reach the Fragment",
                        expectedFactor, field(fragment, "difficultyFactor") as Float, 0f)
                    val before = snapshot(boardView(activity))
                    assertTrue("The untouched round must be ready and running", before.running)
                    assertFalse(before.started)
                    assertFalse(before.paused)
                    assertFalse(before.gameOver)
                    assertEquals(0, before.score)
                    assertTrue(before.pipes.isEmpty())
                    Log.i(TAG, "$label selected pre-launch state=$before")
                }
                val launch = touchBoard()
                val observed = waitForVisiblePipeAndPause(launch.downEventTime)
                Log.i(TAG, "$label snapshot BEFORE public evidence pause=$observed launch=$launch")
                assertTrue("Snapshot must precede evidence pause and report actual active play",
                    observed.running && observed.started && !observed.paused && !observed.gameOver)
                assertEquals("No pipe has been crossed in this bounded geometry observation", 0, observed.score)
                assertEquals("Only the first natural pipe should exist", 1, observed.pipes.size)
                assertTrue("Observe a bounded real Handler interval after the actual launch",
                    observed.observedAt - launch.downEventTime in 1L..750L)
                assertEquals("This regression targets the actual 640dpi emulator", 4f, observed.density, 0f)
                val first = observed.pipes.single()
                assertTrue("The first pipe body must be at least 32px on screen",
                    observed.width - first.x >= 32f)
                assertTrue("Observe well before the first pipe reaches the bird",
                    first.x > observed.birdX + observed.radius)
                assertTrue("The natural bird must remain wholly inside the board",
                    observed.birdY - observed.radius > 0f && observed.birdY + observed.radius < observed.height)
                assertOnlyEvidencePauseChanged(observed)
                captureScreen("$label-first-pipe-before-gap-assert", ::boardView) { bitmap ->
                    assertOnlyEvidencePauseChanged(observed)
                    assertRenderedOpening(bitmap, observed, expectedGapDp)
                }
            } catch (error: Throwable) {
                runCatching {
                    onGameMain { activity ->
                        val board = boardView(activity)
                        Log.i(TAG, "$label failure state BEFORE public evidence pause=${snapshot(board)}")
                        board.javaClass.getMethod("pauseGame").invoke(board)
                    }
                    captureScreen("$label-failure-evidence", ::screenView)
                }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private fun assertOnlyEvidencePauseChanged(before: GameSnapshot) = onGameMain { activity ->
        val after = snapshot(boardView(activity))
        assertTrue("Only public evidence pause changed the paused flag", after.paused)
        assertEquals(before.copy(paused = true, observedAt = after.observedAt), after)
    }

    private data class Pipe(val x: Float, val gapCenter: Float)
    private data class GameSnapshot(
        val observedAt: Long, val running: Boolean, val started: Boolean, val paused: Boolean,
        val gameOver: Boolean, val score: Int, val width: Int, val height: Int,
        val density: Float, val birdX: Float, val birdY: Float, val radius: Float,
        val gap: Float, val pipeWidth: Float, val pipes: List<Pipe>,
        val windowLeft: Int, val windowTop: Int
    )
    private data class TouchPoint(val x: Float, val y: Float)
    private data class TouchReceipt(val downEventTime: Long, val upEventTime: Long, val upAcceptedAt: Long)
    private data class CanvasGeometry(val bounds: Rect, val localVisible: Rect, val visibleOnScreen: Rect, val windowFrame: Rect)

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun boardView(activity: DynamicGameActivity): View = (field(gameFragment(activity), "flappyView") as View).also {
        assertEquals("com.gamecenter.app.flappy.FlappyView", it.javaClass.name)
        assertSame("The board must come from the current external APK", ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
        assertTrue(it.isAttachedToWindow && it.isShown)
    }

    private fun snapshot(view: View): GameSnapshot {
        assertTrue(Looper.myLooper() === Looper.getMainLooper())
        val location = IntArray(2).also(view::getLocationInWindow)
        val pipes = (field(view, "pipes") as List<*>).map {
            val values = it as FloatArray
            Pipe(values[0], values[1])
        }
        return GameSnapshot(SystemClock.uptimeMillis(), view.javaClass.getMethod("isGameRunning").invoke(view) as Boolean,
            field(view, "gameStarted") as Boolean, field(view, "gamePaused") as Boolean,
            field(view, "gameOver") as Boolean, view.javaClass.getMethod("getScore").invoke(view) as Int,
            view.width, view.height, view.resources.displayMetrics.density,
            field(view, "birdX") as Float, field(view, "birdY") as Float, field(view, "birdSize") as Float,
            field(view, "pipeGap") as Float, field(view, "pipeWidth") as Float, pipes, location[0], location[1])
    }

    private fun waitForVisiblePipeAndPause(launchAt: Long): GameSnapshot {
        val ready = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val result = AtomicReference<GameSnapshot?>()
        val cancelled = AtomicBoolean(false)
        val observe = object : Runnable {
            override fun run() {
                if (cancelled.get()) return
                try {
                    val board = boardView(requireNotNull(activeActivity))
                    val state = snapshot(board)
                    assertTrue("The natural initial launch must remain active until geometry is observed: $state",
                        state.running && state.started && !state.paused && !state.gameOver)
                    if (state.pipes.isNotEmpty() && state.width - state.pipes.first().x >= 32f) {
                        result.set(state) // The evidence snapshot records the running game BEFORE pausing.
                        board.javaClass.getMethod("pauseGame").invoke(board)
                        ready.countDown()
                    } else {
                        assertTrue("First pipe must enter at least 32px within 750ms of real launch: $state",
                            state.observedAt - launchAt <= 750L)
                        observationHandler.postDelayed(this, 4L)
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                    ready.countDown()
                }
            }
        }
        try {
            assertTrue(observationHandler.post(observe))
            assertTrue("Bounded first-pipe observer must finish within 1500ms", ready.await(1_500L, TimeUnit.MILLISECONDS))
            failure.get()?.let { throw it }
            return requireNotNull(result.get())
        } finally {
            // Never let a teardown pause create an apparent additional observed game state.
            cancelled.set(true)
            observationHandler.removeCallbacks(observe)
        }
    }

    private fun buttonBounds(button: Button): Rect {
        val geometry = canvasGeometry(button)
        Log.i(TAG, "Actual Flappy button=${button.text} geometry=$geometry density=${button.resources.displayMetrics.density}")
        assertEquals("The entire actual button must be locally visible", Rect(0, 0, button.width, button.height), geometry.localVisible)
        assertTrue("Entire actual button must fit the visible Window", geometry.windowFrame.contains(geometry.bounds))
        val minimum = 48f * button.resources.displayMetrics.density
        assertTrue("Actual hit area must be at least 48dp", button.width + 0.5f >= minimum && button.height + 0.5f >= minimum)
        assertTrue("Buttons must show complete text", button.layout != null &&
            (0 until button.layout.lineCount).all { button.layout.getEllipsisCount(it) == 0 })
        return geometry.bounds
    }

    private fun touchButton(name: String): TouchReceipt {
        val expectedFactor = when (name) {
            "btnEasy" -> 0.3f
            "btnNormal" -> 0.5f
            "btnHard" -> 0.8f
            else -> error("READY menu helper accepts difficulty controls only: $name")
        }
        lateinit var point: TouchPoint
        lateinit var before: GameSnapshot
        onGameMain { activity ->
            val board = boardView(activity)
            before = assertReadyMenuState(board, "before $name")
            val button = field(gameFragment(activity), name) as Button
            val bounds = buttonBounds(button)
            assertTrue(button.isEnabled && button.isClickable)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        // READY does not advance physics. Use the existing bounded observation budget
        // for menu input; flight/launch timing remains independent and unchanged.
        val receipt = touch(point, maximumDurationMs = 1_500L)
        onGameMain { activity ->
            val board = boardView(activity)
            val after = assertReadyMenuState(board, "after $name")
            assertEquals("Difficulty input must leave the complete READY board unchanged except its gap",
                before.copy(observedAt = after.observedAt, gap = after.gap), after)
            assertEquals("The actual menu selection must reach the Fragment", expectedFactor,
                field(gameFragment(activity), "difficultyFactor") as Float, 0f)
            assertEquals("The actual selection must configure its pipe speed", 3f * (0.5f + expectedFactor),
                field(board, "pipeSpeed") as Float, 0.001f)
            Log.i(TAG, "Actual READY menu $name selected=$expectedFactor " +
                "inputElapsed=${receipt.upAcceptedAt - receipt.downEventTime}ms before=$before after=$after")
        }
        return receipt
    }

    private fun assertReadyMenuState(board: View, phase: String): GameSnapshot {
        val state = snapshot(board)
        assertTrue("$phase must remain active READY without physics or game over: $state",
            state.running && !state.started && !state.paused && !state.gameOver)
        assertEquals("$phase must have no awarded score", 0, state.score)
        assertTrue("$phase must have no generated pipes", state.pipes.isEmpty())
        assertEquals("$phase must retain the initial bird x", state.width / 4f, state.birdX, 0f)
        assertEquals("$phase must retain the initial bird y", state.height / 2f, state.birdY, 0f)
        assertEquals("$phase must retain zero bird velocity", 0f, field(board, "birdVelocity") as Float, 0f)
        return state
    }

    private fun touchBoard(): TouchReceipt {
        lateinit var point: TouchPoint
        onGameMain { activity ->
            val board = boardView(activity)
            val geometry = canvasGeometry(board)
            val visible = Rect(geometry.visibleOnScreen)
            assertTrue(visible.intersect(geometry.windowFrame))
            val size = 48f * board.resources.displayMetrics.density
            val x = visible.exactCenterX()
            val y = visible.exactCenterY()
            val target = Rect(floor(x - size / 2f).toInt(), floor(y - size / 2f).toInt(),
                ceil(x + size / 2f).toInt(), ceil(y + size / 2f).toInt())
            assertTrue("The launch uses a full 48dp region inside the actual visible board", visible.contains(target))
            point = TouchPoint(x, y)
            Log.i(TAG, "Actual launch hit region=$target board=$geometry")
        }
        return touch(point)
    }

    private fun touch(point: TouchPoint, maximumDurationMs: Long = 250L): TouchReceipt {
        val downTime = SystemClock.uptimeMillis()
        var completed = false
        try {
            injectTouch(downTime, MotionEvent.ACTION_DOWN, point, downTime)
            val upTime = SystemClock.uptimeMillis()
            injectTouch(downTime, MotionEvent.ACTION_UP, point, upTime)
            completed = true
            val accepted = SystemClock.uptimeMillis()
            assertTrue("The complete real touch must finish within ${maximumDurationMs}ms",
                accepted - downTime <= maximumDurationMs)
            return TouchReceipt(downTime, upTime, accepted)
        } finally {
            if (!completed) injectTouch(downTime, MotionEvent.ACTION_CANCEL, point, SystemClock.uptimeMillis())
        }
    }

    private fun injectTouch(downTime: Long, action: Int, point: TouchPoint, eventTime: Long) {
        val event = MotionEvent.obtain(downTime, eventTime, action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            val accepted = InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
            Log.i(TAG, "Actual Flappy input action=$action eventTime=$eventTime acceptedAt=${SystemClock.uptimeMillis()} accepted=$accepted point=$point")
            assertTrue("System must accept the real Flappy touchscreen event", accepted)
        } finally { event.recycle() }
    }

    private fun assertRenderedOpening(bitmap: Bitmap, state: GameSnapshot, expectedGapDp: Float) {
        val bounds = Rect(state.windowLeft, state.windowTop, state.windowLeft + state.width, state.windowTop + state.height)
        assertTrue("Actual board pixels must fit the copied Window", Rect(0, 0, bitmap.width, bitmap.height).contains(bounds))
        assertEquals(24f * state.density, state.radius, 0f)
        assertEquals(60f * state.density, state.pipeWidth, 0f)
        val birdColumn = state.windowLeft + state.birdX.roundToInt()
        val birdRows = (0 until state.height).filter { bitmap.getPixel(birdColumn, state.windowTop + it) == BIRD_COLOR }
        assertTrue("The actual Window must contain the yellow collision-circle body", birdRows.isNotEmpty())
        val birdDiameter = birdRows.last() - birdRows.first() + 1
        assertEquals("Rendered yellow pixels and the collision circle must agree", state.radius * 2f, birdDiameter.toFloat(), 2f)
        assertEquals("Rendered bird position must match the immutable pre-pause geometry", state.birdY,
            (birdRows.first() + birdRows.last()) / 2f, 2f)

        val pipe = state.pipes.single()
        val localColumn = ((pipe.x.coerceAtLeast(0f) + state.width - 1f) / 2f).roundToInt()
        assertTrue("Pixel probe is inside at least 32px of the real pipe body", localColumn > pipe.x && localColumn < state.width)
        val pipeColumn = state.windowLeft + localColumn
        val center = pipe.gapCenter.roundToInt()
        assertTrue("Natural gap center must be inside the drawn board", center in 0 until state.height)
        fun isPipe(y: Int): Boolean {
            val color = bitmap.getPixel(pipeColumn, state.windowTop + y)
            return color == PIPE_COLOR || color == PIPE_EDGE_COLOR
        }
        assertFalse("Natural gap center must be visibly open", isPipe(center))
        var topBoundary = 0
        for (y in center downTo 0) if (isPipe(y)) { topBoundary = y + 1; break }
        var bottomBoundary = -1
        for (y in center until state.height) if (isPipe(y)) { bottomBoundary = y; break }
        assertTrue("Natural lower pipe must be visible in actual Window pixels", bottomBoundary >= center)
        val drawnGap = bottomBoundary - topBoundary
        assertEquals("Window pipe pixels must match actual collision geometry", state.gap, drawnGap.toFloat(), 2f)
        assertTrue("Visible opening must be one continuous non-pipe interval",
            (topBoundary until bottomBoundary).none(::isPipe))
        assertTrue("Pipe/bird geometry observations must match the actual rendering",
            abs(topBoundary - (pipe.gapCenter - state.gap / 2f)) <= 2f &&
                abs(bottomBoundary - (pipe.gapCenter + state.gap / 2f)) <= 2f)
        Log.i(TAG, "Window pixel measurement board=$bounds pipeColumn=$localColumn gap=$drawnGap birdDiameter=$birdDiameter " +
            "top=$topBoundary bottom=$bottomBoundary naturalGapCenter=${pipe.gapCenter} productionGap=${state.gap} density=${state.density}")
        // Deliberately inspect saved Window pixels before the regression assertion.
        // Old Normal/Hard fail because 180/126px cannot fit the actual 192px circle.
        assertTrue("The actual visible pipe must fit the full bird: gap=${drawnGap}px bird=${birdDiameter}px",
            drawnGap > birdDiameter)
        val safeCenterMin = pipe.gapCenter - state.gap / 2f + state.radius
        val safeCenterMax = pipe.gapCenter + state.gap / 2f - state.radius
        assertTrue("Collision bounds must admit a nonempty bird-center interval", safeCenterMax > safeCenterMin)
        assertEquals("Difficulty's dp opening must scale consistently with the bird", expectedGapDp * state.density, state.gap, 0.001f)
        assertEquals("The scaled opening must exist in the actual Window", expectedGapDp * state.density, drawnGap.toFloat(), 2f)
    }

    private fun withInstalledGame(action: (ActivityScenario<DynamicGameActivity>, AppDatabase, GameUsageEntity?) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val known = listOf("flappy", "flappy_module", "flappy_save", "flappy_settings", "GameMatrix_saves",
                "game_usage", "streak_tracker", "play_time_limit")
            val existing = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "xml" }.map { it.nameWithoutExtension }
                .filter { it == "flappy" || it.startsWith("flappy_") || it.startsWith("mod_flappy__") || it.endsWith("__GameMatrix_saves") }
            val prefsBefore = (known + known.map { ModuleScopedPreferences.scopedName(MODULE_ID, it) } + existing)
                .distinct().map(::capturePreferences)
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            var primaryFailure: Throwable? = null
            try {
                ensureShipped(manifest, apk)
                val launched = ActivityScenario.launch<DynamicGameActivity>(
                    Intent(context, DynamicGameActivity::class.java).putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                scenario = launched
                launched.onActivity { activeActivity = it; gameFragment(it) }
                action(launched, database, usageBefore)
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                // Attempt every recovery step and retain the original regression failure.
                val cleanupFailures = listOf(
                    runCatching { scenario?.close() }.exceptionOrNull(),
                    runCatching { ModuleManager.unloadModule(context, MODULE_ID) }.exceptionOrNull(),
                    runCatching { restoreUserState(database, usageBefore, prefsBefore) }.exceptionOrNull(),
                    runCatching {
                        activeActivity = null
                        if (previousCatalog != null) ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                    }.exceptionOrNull()
                ).filterNotNull()
                if (cleanupFailures.isNotEmpty()) {
                    val cleanup = AssertionError("Failed to restore Flappy after device verification").apply {
                        cleanupFailures.forEach(::addSuppressed)
                    }
                    val original = primaryFailure
                    if (original != null) original.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("The real external Flappy Fragment must attach", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.flappy.FlappyModuleFragment", it.javaClass.name)
            assertFalse("Flappy must not use host game classes", it.javaClass.classLoader === context.classLoader)
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
                assertEquals("flappy Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore flappy user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed flappy package",
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
            assertTrue("Shipped flappy must install through the production transaction",
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
        assertEquals("The shipped catalog must identify flappy exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals(100, manifest.versionCode)
        assertEquals("game_flappy_v100.apk", manifest.fileName)
        assertEquals("com.gamecenter.app.flappy.FlappyModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped flappy must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped flappy must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-flappy-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("flappy APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("flappy APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("flappy APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
        @Suppress("DEPRECATION")
        val packageInfo = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES))
        assertEquals("com.gamecenter.app.flappy", packageInfo.packageName)
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
        assertEquals("The shipped Flappy APK manifest must identify version 100", 100L, archiveVersion)
        Log.i(TAG, "Actual Flappy archive package=${packageInfo.packageName} version=$archiveVersion " +
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
            throw AssertionError("Failed to restore flappy preferences after device verification").apply {
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
        assertTrue("Restore Flappy usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Flappy usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "flappy"
        private const val GAME_ID = "flappy"
        private const val TAG = "ShippedFlappyTest"
        private const val PUBLISHER_SHA256 = "d058a18f9e89a29b5339eda27ece3ff9f78e0dbefe605d551e7745f724d2eddc"
        private const val BIRD_COLOR = 0xFFFFEB3B.toInt()
        private const val PIPE_COLOR = 0xFF4CAF50.toInt()
        private const val PIPE_EDGE_COLOR = 0xFF388E3C.toInt()
    }
}

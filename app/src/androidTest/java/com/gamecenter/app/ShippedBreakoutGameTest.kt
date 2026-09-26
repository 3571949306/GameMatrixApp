package com.gamecenter.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.database.entity.AchievementEntity
import com.gamecenter.app.database.entity.GameUsageEntity
import com.gamecenter.app.games.breakout.BreakoutActivity
import com.gamecenter.app.games.breakout.BreakoutView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot
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
 * The catalog's built-in host Breakout game, not a dynamically loaded module copy.
 * Real ActivityScenario, system touchscreen input and the Activity's existing Handler
 * drive first launch, all three actual lost lives, the actual GAME_OVER listener, restart
 * and relaunch. No update(), end callback, listener or game-loop replacement is invoked.
 *
 * Explicit near-bottom-loss fixtures are used, NOT a naturally failed full game. Ball is
 * private and there is no public entity/snapshot API. After each real launch, the sole
 * existing ball's y and vy alone are set to a legal visible point below the paddle and
 * its existing vertical-speed magnitude directed downward. Lives, state, score, bricks,
 * x/vx, listeners and Activity scheduling are never written. Real timer updates lose the
 * ball and decrement each of the three original lives normally.
 *
 * The separate left/right actualTouch launch cases use only the initially READY game
 * and ordinary screen DOWN/UP input. They observe existing entities without applying
 * any fixture, moving a ball directly or fixing the random launch angle.
 *
 * Actual Window pixels come from PixelCopy after a committed frame. Usage/leaderboard,
 * streak and play-limit preferences and only Breakout's Room usage/achievement rows are
 * restored after closing the Activity, including on assertion failure. Caller owns device
 * selection; this test never connects to or selects an emulator.
 */
@RunWith(AndroidJUnit4::class)
class ShippedBreakoutGameTest {
    private val observationHandler = Handler(Looper.getMainLooper())
    private var activeActivity: BreakoutActivity? = null
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedBuiltInBreakoutNearBottomLossFixturesRestartAndRelaunchRealMovingBall() {
        assertBuiltInCatalogEntry()
        withGame { scenario, database, originalLosses ->
            captureScreen("breakout-original-ready", scenario, ::boardView)
            val fresh = readState(scenario)
            assertEquals("READY", fresh.state)
            assertEquals(3, fresh.lives)
            assertEquals(1, fresh.level)
            assertEquals(0, fresh.score)
            assertEquals(27, fresh.aliveBricks)
            assertEquals(1, fresh.balls.size)

            for (loss in 1..3) {
                val ready = readState(scenario)
                assertEquals("READY", ready.state)
                assertEquals(4 - loss, ready.lives)
                touchBoardCenter(scenario)
                assertEquals("The actual ready-screen tap must launch the ball", "PLAYING", readState(scenario).state)
                if (loss == 1) observeContinuousRealBallMotion(scenario, "breakout-original-launch-moving")
                placeExistingBallNearBottom(scenario, loss)

                val deadline = SystemClock.uptimeMillis() + 2_000L
                var lost = readState(scenario)
                while (lost.lives == 4 - loss && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(20L)
                    lost = readState(scenario)
                }
                assertEquals("Only the real game loop must decrement one life for fixture $loss", 3 - loss, lost.lives)
                assertEquals(if (loss == 3) "GAME_OVER" else "READY", lost.state)
                assertEquals("Near-bottom fixtures must not invent brick scores", 0, lost.score)
                assertEquals(27, lost.aliveBricks)
                assertEquals(if (loss == 3) 0 else 1, lost.balls.size)
                assertEquals("Only the third actual loss ends a round and records a defeat",
                    originalLosses + if (loss == 3) 1 else 0,
                    database.gameUsageDao().getByIdSync(GAME_ID)?.losses ?: 0)
                Log.i(TAG, "Real Handler consumed near-bottom fixture $loss: lives=${lost.lives}, state=${lost.state}")
            }
            captureScreen("breakout-real-game-over-before-restart", scenario, ::boardView)

            // These are ordinary screen taps. No Activity lifecycle transition is used
            // as a workaround to restart the dead loop in the previous host package.
            touchBoardCenter(scenario)
            val restarted = readState(scenario)
            assertEquals("The real GAME_OVER tap must return to a fresh ready board", "READY", restarted.state)
            assertEquals(3, restarted.lives)
            assertEquals(1, restarted.level)
            assertEquals(0, restarted.score)
            assertEquals(27, restarted.aliveBricks)
            assertEquals(1, restarted.balls.size)
            captureScreen("breakout-game-over-tap-restarted-ready", scenario, ::boardView)
            touchBoardCenter(scenario)
            assertEquals("The second real tap must relaunch the new ball", "PLAYING", readState(scenario).state)
            observeContinuousRealBallMotion(scenario, "breakout-restarted-launch-timer-motion")
            onGameMain { activity ->
                assertTrue("The game's Activity must own an active loop after screen restart", activity.isGameRunning)
                assertFalse(activity.isGamePaused)
            }
        }
    }

    @Test
    fun shippedBuiltInBreakoutActualTouchLeftLaunchStartsAboveMovedPaddle() {
        assertActualTouchLaunchStartsAboveMovedPaddle(leftSide = true)
    }

    @Test
    fun shippedBuiltInBreakoutActualTouchRightLaunchStartsAboveMovedPaddle() {
        assertActualTouchLaunchStartsAboveMovedPaddle(leftSide = false)
    }

    private fun assertActualTouchLaunchStartsAboveMovedPaddle(leftSide: Boolean) {
        assertBuiltInCatalogEntry()
        withGame { scenario, _, _ ->
            val side = if (leftSide) "left" else "right"
            captureScreen("breakout-actualTouch-$side-ready", scenario, ::boardView)
            lateinit var target: LaunchTarget
            onGameMain { activity ->
                val board = boardView(activity)
                val ready = state(activity)
                assertEquals("READY", ready.state)
                assertEquals(3, ready.lives)
                assertEquals(1, ready.level)
                assertEquals(0, ready.score)
                assertEquals(27, ready.aliveBricks)
                assertEquals(1, ready.balls.size)
                val geometry = canvasGeometry(board)
                val safeCanvas = Rect(geometry.visibleOnScreen)
                assertTrue("The real canvas and window must have a shared visible area", safeCanvas.intersect(geometry.windowFrame))
                val density = board.resources.displayMetrics.density
                val halfTarget = kotlin.math.ceil(24.0 * density).toInt()
                // An additional 8dp inset keeps the complete 48dp input region clear
                // of the physical edge and any visible system-bar boundary.
                val inset = halfTarget + kotlin.math.ceil(8.0 * density).toInt()
                val point = TouchPoint(
                    (if (leftSide) safeCanvas.left + inset else safeCanvas.right - inset).toFloat(),
                    safeCanvas.exactCenterY())
                val touchRegion = Rect(
                    kotlin.math.floor(point.x - halfTarget).toInt(), kotlin.math.floor(point.y - halfTarget).toInt(),
                    kotlin.math.ceil(point.x + halfTarget).toInt(), kotlin.math.ceil(point.y + halfTarget).toInt())
                assertTrue("The entire side input target must lie in the visible canvas: $touchRegion $geometry",
                    geometry.visibleOnScreen.contains(touchRegion))
                assertTrue("The entire side input target must avoid system-bar areas: $touchRegion $geometry",
                    geometry.windowFrame.contains(touchRegion))
                val width = floatField(board, "viewWidth")
                val paddleWidth = floatField(board, "paddleWidth")
                val localTouchX = point.x - geometry.bounds.left
                val expectedPaddleLeft = (localTouchX - paddleWidth / 2f).coerceIn(0f, width - paddleWidth)
                val radius = floatField(board, "ballRadius")
                val paddleY = floatField(board, "paddleY")
                val readyBall = ready.balls.single()
                assertEquals("The original ready ball starts at the centered paddle", width / 2f, readyBall.x, 0.5f)
                assertEquals(0f, readyBall.vx, 0f)
                assertEquals(0f, readyBall.vy, 0f)
                assertTrue("The ready ball is completely above its paddle", readyBall.y + radius < paddleY)
                target = LaunchTarget(point, expectedPaddleLeft, paddleWidth, paddleY, radius, width,
                    (fieldValue(board, "bricks") as List<*>).maxOf { (it as RectF).bottom }, readyBall)
                Log.i(TAG, "actualTouch $side complete target=$touchRegion screenPoint=$point localX=$localTouchX " +
                    "expectedPaddleLeft=$expectedPaddleLeft originalBall=$readyBall geometry=$geometry")
            }

            val touchStartedAt = SystemClock.uptimeMillis()
            gesture(target.point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, target.point) }
            lateinit var observed: LaunchObservation
            onGameMain { activity ->
                val board = boardView(activity)
                observed = LaunchObservation(state(activity), floatField(board, "paddleX"),
                    floatField(board, "paddleWidth"), floatField(board, "paddleY"),
                    floatField(board, "ballRadius"), SystemClock.uptimeMillis() - touchStartedAt)
            }
            // Preserve the actual old-package ball/paddle mismatch before asserting it.
            // PixelCopy does not delay or substitute the already captured launch sample.
            captureScreen("breakout-actualTouch-$side-launched", scenario, ::boardView)
            Log.i(TAG, "actualTouch $side immediate launch sample=$observed target=$target")
            assertTrue("Capture the real launch within 250ms of touchscreen DOWN, before any distant brick collision",
                observed.elapsedMillis in 0L..250L)
            assertEquals("The actual side DOWN/UP must launch normally", "PLAYING", observed.game.state)
            assertEquals(3, observed.game.lives)
            assertEquals(1, observed.game.level)
            assertEquals(0, observed.game.score)
            assertEquals(27, observed.game.aliveBricks)
            assertEquals(1, observed.game.balls.size)
            assertTrue("The actual Activity loop must remain active", observed.game.activityRunning)
            assertEquals(target.expectedPaddleLeft, observed.paddleLeft, 0.5f)
            assertEquals(target.paddleWidth, observed.paddleWidth, 0f)
            assertEquals(target.paddleY, observed.paddleY, 0f)
            assertEquals(target.radius, observed.radius, 0f)
            val ball = observed.game.balls.single()
            assertTrue("Use the actual finite launch angle without fixing its random value",
                ball.vx.isFinite() && ball.vy.isFinite() && ball.vy < 0f)

            // The Activity schedules at most one update every 16ms. This is a loose
            // upper travel bound from measured elapsed time, never an exact frame count.
            // Two extra steps cover the input boundary and an already eligible update.
            val maximumSteps = kotlin.math.ceil(observed.elapsedMillis / 16.0).toInt() + 2
            val horizontalTravel = abs(ball.vx) * maximumSteps
            val verticalTravel = abs(ball.vy) * maximumSteps
            val horizontalTolerance = target.radius + horizontalTravel
            val expectedCenter = target.expectedPaddleLeft + target.paddleWidth / 2f
            val originalSeparation = abs(target.readyBall.x - expectedCenter)
            assertTrue("The side target must be far from the original centered ball",
                originalSeparation > target.boardWidth / 4f && originalSeparation > 2f * horizontalTolerance)
            assertTrue("The measured launch window must be clear of both side walls",
                expectedCenter - horizontalTravel - target.radius > 0f &&
                    expectedCenter + horizontalTravel + target.radius < target.boardWidth)
            assertTrue("The measured launch window must be clear of all original bricks",
                target.readyBall.y - verticalTravel - target.radius > target.lowestBrickBottom)
            Log.i(TAG, "actualTouch $side expectedCenter=$expectedCenter originalSeparation=$originalSeparation " +
                "actualBall=$ball elapsed=${observed.elapsedMillis}ms horizontalTolerance=$horizontalTolerance " +
                "maximumVerticalTravel=$verticalTravel")
            assertTrue("The actual ball must launch above the moved $side paddle, allowing only bounded natural travel: " +
                "ballX=${ball.x}, paddleCenter=$expectedCenter, tolerance=$horizontalTolerance",
                abs(ball.x - expectedCenter) <= horizontalTolerance)
            assertTrue("The launched ball must still lie over the moved paddle's horizontal span",
                ball.x >= observed.paddleLeft - target.radius &&
                    ball.x <= observed.paddleLeft + observed.paddleWidth + target.radius)
            assertTrue("The actual launch remains near and above the paddle while moving upward",
                ball.y + target.radius < observed.paddleY && ball.y <= target.readyBall.y &&
                    ball.y >= target.readyBall.y - verticalTravel - target.radius)
            observeContinuousRealBallMotion(scenario, "breakout-actualTouch-$side-continuous-motion")
        }
    }

    private data class LaunchTarget(
        val point: TouchPoint, val expectedPaddleLeft: Float, val paddleWidth: Float, val paddleY: Float,
        val radius: Float, val boardWidth: Float, val lowestBrickBottom: Float, val readyBall: BallState
    )
    private data class LaunchObservation(
        val game: GameState, val paddleLeft: Float, val paddleWidth: Float, val paddleY: Float,
        val radius: Float, val elapsedMillis: Long
    )
    private data class BallState(val x: Float, val y: Float, val vx: Float, val vy: Float)
    private data class GameState(
        val state: String, val lives: Int, val level: Int, val score: Int,
        val aliveBricks: Int, val balls: List<BallState>, val activityRunning: Boolean
    )
    private data class TouchPoint(val x: Float, val y: Float)
    private data class CanvasGeometry(
        val bounds: Rect, val localVisible: Rect, val visibleOnScreen: Rect, val windowFrame: Rect
    )

    private fun assertBuiltInCatalogEntry() {
        val modules = context.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("modules")
        }
        val records = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { it.optString("id") == GAME_ID }
        assertEquals("Breakout must appear exactly once in the actual shipped catalog", 1, records.size)
        val entry = records.single()
        assertTrue("This game is the explicitly retained host implementation", entry.getBoolean("builtIn"))
        assertEquals("builtin", entry.getString("deliveryType"))
        assertEquals(BreakoutActivity::class.java.name, entry.getString("activityClass"))
        assertEquals("", entry.optString("fileName"))
        assertEquals("", entry.optString("entryClass"))
    }

    private fun withGame(action: (ActivityScenario<BreakoutActivity>, AppDatabase, Int) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val prefsBefore = listOf("game_usage", "streak_tracker", "play_time_limit").map(::capturePreferences)
        val database = AppDatabase.getDatabase(context.applicationContext)
        val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
        val achievementDao = database.achievementDao()
        val existingAchievements = achievementDao.getByGameIdSync(GAME_ID)
        val ownedIds = (existingAchievements.map { it.achievementId } + listOf(
            "breakout_game_over", "breakout_rounds", "breakout_win", "breakout_score", "breakout_level", "breakout_no_miss"
        )).distinct()
        val achievementsBefore = ownedIds.associateWith { achievementDao.getByIdSync(it)?.copy() }
        var scenario: ActivityScenario<BreakoutActivity>? = null
        try {
            val launched = ActivityScenario.launch<BreakoutActivity>(
                Intent(context, BreakoutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            scenario = launched
            // Take the Activity once while the board is still READY. Repeated
            // ActivityScenario.onActivity calls wait for global UI idle first.
            launched.onActivity { activeActivity = it }
            // Respect the actual optional daily-limit warning via its real Continue
            // button; its recorded warning date is included in the preference snapshot.
            instrumentation.waitForIdleSync()
            val warning = UiDevice.getInstance(instrumentation)
                .findObject(By.text(context.getString(R.string.play_limit_warn_continue)))
            if (warning != null) {
                val bounds = Rect(warning.visibleBounds)
                assertTrue(warning.isEnabled && bounds.width() > 0 && bounds.height() > 0)
                val point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
                gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
            }
            action(launched, database, usageBefore?.losses ?: 0)
        } finally {
            try {
                scenario?.close()
            } finally {
                try {
                    restoreUserState(database, usageBefore, achievementsBefore, prefsBefore)
                } finally {
                    activeActivity = null
                }
            }
        }
    }

    private fun boardView(activity: BreakoutActivity): BreakoutView =
        (BreakoutActivity::class.java.getDeclaredField("breakoutView").apply { isAccessible = true }.get(activity) as BreakoutView).also {
            assertSame("Catalog built-in Breakout must use the host ClassLoader", context.classLoader, it.javaClass.classLoader)
        }

    private fun fieldValue(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun floatField(owner: Any, name: String): Float = fieldValue(owner, name) as Float

    private fun state(activity: BreakoutActivity): GameState {
        val board = boardView(activity)
        val balls = (fieldValue(board, "balls") as List<*>).map { ball ->
            val value = requireNotNull(ball)
            BallState(floatField(value, "x"), floatField(value, "y"), floatField(value, "vx"), floatField(value, "vy"))
        }
        return GameState(requireNotNull(fieldValue(board, "state")).toString(), fieldValue(board, "lives") as Int,
            board.level, board.score, (fieldValue(board, "brickAlive") as List<*>).count { it == true }, balls,
            activity.isGameRunning)
    }

    private fun readState(scenario: ActivityScenario<BreakoutActivity>): GameState {
        lateinit var result: GameState
        onGameMain { result = state(it) }
        return result
    }

    private fun onGameMain(action: (BreakoutActivity) -> Unit) {
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

    private fun placeExistingBallNearBottom(scenario: ActivityScenario<BreakoutActivity>, loss: Int) {
        onGameMain { activity ->
            val board = boardView(activity)
            val before = state(activity)
            assertEquals("PLAYING", before.state)
            assertEquals(4 - loss, before.lives)
            val balls = fieldValue(board, "balls") as List<*>
            assertEquals("The legal fixture uses the existing single launched ball", 1, balls.size)
            val ball = requireNotNull(balls.single())
            val radius = floatField(board, "ballRadius")
            val height = floatField(board, "viewHeight")
            val nearBottomY = height - radius
            val downwardVy = abs(floatField(ball, "vy"))
            assertTrue("Preserve the real launch's finite positive speed magnitude", downwardVy.isFinite() && downwardVy > 0f)
            assertTrue("Fixture's entire ball must be below the paddle plane",
                nearBottomY - radius > floatField(board, "paddleY") + floatField(board, "paddleHeight"))
            assertTrue("Fixture ball starts within the actual visible board", nearBottomY >= radius && nearBottomY <= height - radius)
            // The only two writes to private gameplay fields in this class.
            ball.javaClass.getDeclaredField("y").apply { isAccessible = true }.setFloat(ball, nearBottomY)
            ball.javaClass.getDeclaredField("vy").apply { isAccessible = true }.setFloat(ball, downwardVy)
            val after = state(activity)
            assertEquals("Near-bottom preparation must preserve every field except the single ball's y/vy",
                before.copy(balls = listOf(before.balls.single().copy(y = nearBottomY, vy = downwardVy))), after)
            Log.i(TAG, "EXPLICIT LEGAL NEAR-BOTTOM LOSS FIXTURE $loss: only existing Ball.y=$nearBottomY and " +
                "Ball.vy=$downwardVy changed; lives/state/listener/Activity loop untouched")
        }
    }

    private fun touchBoardCenter(scenario: ActivityScenario<BreakoutActivity>) {
        lateinit var point: TouchPoint
        onGameMain { activity ->
            val board = boardView(activity)
            val geometry = canvasGeometry(board)
            point = TouchPoint(geometry.bounds.exactCenterX(), geometry.bounds.exactCenterY())
            val halfTarget = kotlin.math.ceil(24.0 * board.resources.displayMetrics.density).toInt()
            val touchRegion = Rect(
                kotlin.math.floor(point.x - halfTarget).toInt(), kotlin.math.floor(point.y - halfTarget).toInt(),
                kotlin.math.ceil(point.x + halfTarget).toInt(), kotlin.math.ceil(point.y + halfTarget).toInt())
            assertTrue("The complete center touch target (at least 48dp) must lie in the actual visible canvas: " +
                "target=$touchRegion geometry=$geometry", geometry.visibleOnScreen.contains(touchRegion))
            assertTrue("The complete center touch target (at least 48dp) must avoid system-bar areas: " +
                "target=$touchRegion geometry=$geometry", geometry.windowFrame.contains(touchRegion))
            Log.i(TAG, "Actual canvas touchscreen target=$touchRegion center=$point, geometry=$geometry")
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun observeContinuousRealBallMotion(scenario: ActivityScenario<BreakoutActivity>, label: String) {
        val samples = arrayOfNulls<GameState>(3)
        val sampledAt = LongArray(3)
        val ready = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()
        var sampler: Runnable? = null
        try {
            onGameMain { activity ->
                var index = 0
                val request = object : Runnable {
                    override fun run() {
                        if (cancelled.get()) return
                        try {
                            sampledAt[index] = SystemClock.uptimeMillis()
                            samples[index] = state(activity)
                            index++
                            if (index == samples.size) ready.countDown()
                            else assertTrue("Schedule the next real 120ms observation", observationHandler.postDelayed(this, 120L))
                        } catch (error: Throwable) {
                            failure.set(error)
                            ready.countDown()
                        }
                    }
                }
                sampler = request
                // Only the observation runs here. Production physics continues on its
                // own existing Activity Handler, and is never manually advanced.
                request.run()
            }
            assertTrue("Three real Handler motion samples must arrive within 1500ms",
                ready.await(1_500L, TimeUnit.MILLISECONDS))
            failure.get()?.let { throw it }
        } finally {
            cancelled.set(true)
            sampler?.let(observationHandler::removeCallbacks)
        }
        val first = requireNotNull(samples[0])
        val middle = requireNotNull(samples[1])
        val last = requireNotNull(samples[2])
        val firstInterval = sampledAt[1] - sampledAt[0]
        val secondInterval = sampledAt[2] - sampledAt[1]
        val elapsed = sampledAt[2] - sampledAt[0]
        Log.i(TAG, "$label main-Handler timestamps=${sampledAt.toList()} intervals=$firstInterval/$secondInterval ms; " +
            "first=${first.balls}, middle=${middle.balls}, last=${last.balls}, Activity.running=${last.activityRunning}")
        // Save the actual still-ball failure before requiring physical movement.
        captureScreen(label, scenario, ::boardView)
        assertTrue("Actual sample intervals must be at least 120ms each", firstInterval >= 120L && secondInterval >= 120L)
        assertTrue("Keep the measured collision-free observation bounded to 240..600ms, not a multi-second idle wait",
            elapsed in 240L..600L)
        for (sample in listOf(first, middle, last)) {
            assertEquals("A real launch must remain PLAYING during this short observation", "PLAYING", sample.state)
            assertEquals(1, sample.balls.size)
            assertEquals(0, sample.score)
            assertEquals(27, sample.aliveBricks)
        }
        fun distance(a: BallState, b: BallState) = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        assertTrue("The ball must move during the first real-time interval after $label",
            distance(first.balls.single(), middle.balls.single()) > 0.5)
        assertTrue("The ball must keep moving during a second interval, not just receive one trailing update",
            distance(middle.balls.single(), last.balls.single()) > 0.5)
    }

    private fun restoreUserState(
        database: AppDatabase,
        usageBefore: GameUsageEntity?,
        achievementsBefore: Map<String, AchievementEntity?>,
        prefsBefore: List<PreferenceSnapshot>
    ) {
        val failures = listOf(
            runCatching { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }.exceptionOrNull(),
            runCatching { restoreAllPreferences(prefsBefore) }.exceptionOrNull(),
            runCatching {
                if (usageBefore != null) database.gameUsageDao().upsertSync(usageBefore)
                else runBlocking { database.gameUsageDao().delete(GAME_ID) }
                assertEquals("Restore exact Breakout usage row or original absence", usageBefore,
                    database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull(),
            runCatching {
                val dao = database.achievementDao()
                val ownedIds = (achievementsBefore.keys + dao.getByGameIdSync(GAME_ID).map { it.achievementId }).toSet()
                ownedIds.forEach { id ->
                    val prior = achievementsBefore[id]
                    if (prior != null) dao.upsertSync(prior)
                    else runBlocking { dao.delete(id) }
                    assertEquals("Restore exact Breakout achievement row or absence: $id", prior, dao.getByIdSync(id))
                }
                assertEquals("Do not retain any new Breakout achievement rows",
                    achievementsBefore.values.filterNotNull().filter { it.gameId == GAME_ID }.sortedBy { it.achievementId },
                    dao.getByGameIdSync(GAME_ID).sortedBy { it.achievementId })
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) throw AssertionError("Failed to restore Breakout user statistics").apply {
            failures.forEach(::addSuppressed)
        }
    }

    private fun gesture(start: TouchPoint, finish: (Long) -> Unit) {
        assertFalse("Touch injection must not block the Activity main thread", Looper.myLooper() === Looper.getMainLooper())
        val downTime = SystemClock.uptimeMillis()
        var finished = false
        try {
            injectTouch(downTime, MotionEvent.ACTION_DOWN, start)
            finish(downTime)
            finished = true
        } finally {
            // A failed assertion must not leave a held pointer or pending long press behind.
            if (!finished) injectTouch(downTime, MotionEvent.ACTION_CANCEL, start)
        }
        // Both injections are synchronous. A bounded queue request confirms that prior
        // main-thread work returned without waiting for the whole render loop to idle.
        onGameMain { }
        val elapsed = SystemClock.uptimeMillis() - downTime
        Log.i(TAG, "Actual touchscreen DOWN/UP and main-queue barrier took ${elapsed}ms")
        assertTrue("Real gesture delivery must finish within 750ms so gameplay observations remain bounded", elapsed <= 750L)
    }

    private fun injectTouch(downTime: Long, action: Int, point: TouchPoint) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("The system must accept real touchscreen event $action",
                InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
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
        scenario: ActivityScenario<BreakoutActivity>,
        targetView: (BreakoutActivity) -> View
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
        var copyRequested = false
        try {
            onGameMain { activity ->
                val windowRoot = requireNotNull(root)
                val bitmap = Bitmap.createBitmap(windowRoot.width, windowRoot.height, Bitmap.Config.ARGB_8888)
                ownedBitmap.set(bitmap)
                PixelCopy.request(activity.window, bitmap, { result ->
                    copyResult.set(result)
                    copyReady.countDown()
                    if (copyCancelled.get()) ownedBitmap.getAndSet(null)?.recycle()
                }, Handler(Looper.getMainLooper()))
                copyRequested = true
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
            // PixelCopy can complete after timeout. Its callback owns recycling while
            // the request is in flight; atomic ownership prevents a concurrent double recycle.
            if (!copyRequested || copyReady.count == 0L) ownedBitmap.getAndSet(null)?.recycle()
        }
    }

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
            throw AssertionError("Failed to restore breakout preferences after device verification").apply {
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
        assertTrue("Restore Breakout usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Breakout usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val GAME_ID = "breakout"
        private const val TAG = "ShippedBreakoutTest"
    }
}

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
 * Real shipped external Memory APK, Fragment, naturally shuffled cards, system touches
 * and the production 800ms matching Handler. Reflection ONLY reads existing card values,
 * progress and actual Views; it never writes cards, scores, handlers or private state.
 * Card values select matching pairs explicitly: this tests gameplay/difficulty transitions,
 * not human recall. Every target must be completely visible, unoccluded by ancestors,
 * inside the real window and at least 48dp. No performClick, scrolling fiction or View.draw.
 * Actual committed Window pixels precede critical victory/early-victory assertions.
 * Flat/scoped/legacy Memory preferences, full SaveManager stores and only the Memory Room
 * usage row are restored after closing/unloading. Caller owns emulator selection.
 */
@RunWith(AndroidJUnit4::class)
class ShippedMemoryModuleTest {
    private val observationHandler = Handler(Looper.getMainLooper())
    private var activeActivity: DynamicGameActivity? = null
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedMemoryActualTouchEasyRoundCompletesEightPairsBeforeHardNextRound() {
        playWithNextDifficulty("btnEasy", 16, "btnHard", 24)
    }

    @Test
    fun shippedMemoryActualTouchHardRoundCompletesTwelvePairsBeforeEasyNextRound() {
        playWithNextDifficulty("btnHard", 24, "btnEasy", 16)
    }

    private fun playWithNextDifficulty(initialDifficulty: String, initialCards: Int, nextDifficulty: String, nextCards: Int) {
        withInstalledGame { scenario, database, usageBefore ->
            val label = "memory-$initialCards-to-$nextCards"
            try {
                captureScreen("$label-opened", scenario, ::screenView)
                assertDifficultyControls()
                touchField(initialDifficulty)
                touchField("btnStart")
                captureScreen("$label-natural-shuffled-round", scenario, ::screenView)
                assertAllCardGeometry(initialCards)
                val initial = readState()
                assertFreshRound(initial, initialCards)
                val pairs = initial.values.indices.groupBy { initial.values[it] }.toSortedMap().values.toList()
                assertEquals(initialCards / 2, pairs.size)
                assertTrue("The actual shuffled deck must contain exactly two of each card", pairs.all { it.size == 2 })

                touchField(nextDifficulty)
                captureScreen("$label-next-difficulty-selected", scenario, ::screenView)
                assertAllCardGeometry(initialCards)
                val selected = readState()
                assertEquals("Changing next difficulty must preserve the actual shuffled cards", initial.values, selected.values)
                assertEquals(initial.matched, selected.matched)
                assertEquals(initial.revealed, selected.revealed)
                assertEquals(0, selected.moves)
                assertEquals(0, selected.matches)
                assertTrue(selected.active)
                assertEquals(initial.startedAt, selected.startedAt)
                // Log the old mutable goal but do not stop early on it: this regression
                // must actually finish eight pairs or expose Hard's premature eighth win.
                Log.i(TAG, "$label pending selection actual cards=${selected.values.size} currentGoal=${selected.goalPairs} " +
                    "status=${selected.status}; matching plan reads values without changing them")

                val matchedIndices = mutableSetOf<Int>()
                for ((pairIndex, pair) in pairs.withIndex()) {
                    val before = readState()
                    assertTrue("The round must still accept pair ${pairIndex + 1}", before.active)
                    touchCard(pair[0])
                    val first = readState()
                    assertEquals("First card alone must not add a move", pairIndex, first.moves)
                    assertEquals(pair[0], first.first)
                    assertEquals(-1, first.second)
                    assertFalse(first.processing)
                    val firstRevealed = initial.values.indices.map { it in matchedIndices || it == pair[0] }
                    assertEquals(firstRevealed, first.revealed)
                    val secondTouch = touchCard(pair[1])
                    val pending = readState()
                    assertTrue("The second actual touch must enter the real delayed match operation", pending.processing)
                    assertEquals(pairIndex + 1, pending.moves)
                    assertEquals(pairIndex, pending.matches)
                    assertEquals(pair[0], pending.first)
                    assertEquals(pair[1], pending.second)
                    assertEquals(initial.values, pending.values)

                    val deadline = secondTouch.upEventTime + 2_500L
                    var resolved = pending
                    while (resolved.processing && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(20L)
                        resolved = readState()
                    }
                    val observedAt = SystemClock.uptimeMillis()
                    assertFalse("Real 800ms matching callback must complete within 2500ms", resolved.processing)
                    assertTrue("Observe the real pair after its 800ms delay and within the 2500ms deadline",
                        observedAt - secondTouch.upEventTime in 800L..2_500L)
                    matchedIndices.addAll(pair)
                    val pairNumber = pairIndex + 1
                    Log.i(TAG, "$label actual pair=$pairNumber indexes=$pair secondUP=${secondTouch.upEventTime} " +
                        "accepted=${secondTouch.upAcceptedAt} resolved=$observedAt elapsed=${observedAt - secondTouch.upEventTime}ms " +
                        "matches=${resolved.matches} active=${resolved.active} goal=${resolved.goalPairs}")
                    if (pairNumber == 8 || pairNumber == pairs.size) {
                        captureScreen("$label-matched-$pairNumber-before-outcome-assert", scenario, ::screenView)
                    }
                    assertEquals("Matching must never replace the shuffled deck", initial.values, resolved.values)
                    val expectedMatched = initial.values.indices.map { it in matchedIndices }
                    assertEquals("Every matched card must be the naturally touched pair", expectedMatched, resolved.matched)
                    assertEquals(expectedMatched, resolved.revealed)
                    assertEquals(pairNumber, resolved.moves)
                    assertEquals(pairNumber, resolved.matches)
                    assertEquals(0, resolved.errors)
                    assertEquals(-1, resolved.first)
                    assertEquals(-1, resolved.second)
                    assertEquals("Current round must end after exactly its original ${pairs.size} pairs",
                        pairNumber < pairs.size, resolved.active)
                    onGameMain { activity ->
                        val buttons = cards(gameFragment(activity))
                        assertEquals(expectedMatched.map { !it }, buttons.map { it.isEnabled })
                    }
                }

                val completed = readState()
                val earned = maxOf(100 - pairs.size * 5, 10)
                assertEquals(initial.score + earned, completed.score)
                assertEquals(maxOf(initial.highScore, completed.score), completed.highScore)
                assertTrue("The real status must tell the player the round was completed", completed.status.contains("通关"))
                val usageAfter = requireNotNull(database.gameUsageDao().getByIdSync(GAME_ID))
                assertEquals("Actual completion must record the correct best score",
                    maxOf(usageBefore?.highScore ?: 0L, completed.highScore.toLong()), usageAfter.highScore)
                assertEquals(usageBefore?.wins ?: 0, usageAfter.wins)
                assertEquals(usageBefore?.losses ?: 0, usageAfter.losses)
                onGameMain { activity ->
                    val restart = field(gameFragment(activity), "btnStart") as Button
                    assertEquals(activity.getString(R.string.game_btn_play_again), restart.text.toString())
                    buttonBounds(restart)
                }
                touchField("btnStart")
                captureScreen("$label-next-round-$nextCards-cards", scenario, ::screenView)
                assertAllCardGeometry(nextCards)
                val next = readState()
                assertFreshRound(next, nextCards)
                assertEquals("The selected difficulty must configure the next real grid", nextCards / 4, next.columns)
                assertEquals(4, next.rows)
                assertEquals(nextCards / 2, next.goalPairs)
                assertEquals("Beginning the next round must not award another completion score", completed.score, next.score)
                assertEquals(completed.highScore, next.highScore)
                Log.i(TAG, "$label finished the original ${pairs.size} pairs via real touch and started $nextCards naturally shuffled cards")
            } catch (error: Throwable) {
                runCatching { captureScreen("$label-failure-evidence", scenario, ::screenView) }
                    .exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private data class BoardState(
        val values: List<Int>, val revealed: List<Boolean>, val matched: List<Boolean>,
        val first: Int, val second: Int, val processing: Boolean, val moves: Int, val matches: Int,
        val errors: Int, val active: Boolean, val score: Int, val highScore: Int, val startedAt: Long,
        val goalPairs: Int, val rows: Int, val columns: Int, val status: String
    )
    private data class TouchPoint(val x: Float, val y: Float)
    private data class TouchReceipt(val upEventTime: Long, val upAcceptedAt: Long)
    private data class CanvasGeometry(val bounds: Rect, val localVisible: Rect, val visibleOnScreen: Rect, val windowFrame: Rect)

    private fun assertFreshRound(round: BoardState, count: Int) {
        assertEquals(count, round.values.size)
        assertEquals(count, round.revealed.size)
        assertEquals(count, round.matched.size)
        assertTrue(round.active)
        assertFalse(round.processing)
        assertTrue(round.revealed.none { it } && round.matched.none { it })
        assertEquals(0, round.moves)
        assertEquals(0, round.matches)
        assertEquals(0, round.errors)
        assertEquals(-1, round.first)
        assertEquals(-1, round.second)
        assertTrue(round.startedAt > 0L)
        assertEquals(count / 2, round.values.distinct().size)
        assertTrue(round.values.groupingBy { it }.eachCount().values.all { it == 2 })
    }

    private fun field(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun cards(fragment: Fragment): List<Button> = (field(fragment, "cardButtons") as Array<*>).map { it as Button }

    private fun readState(): BoardState {
        lateinit var result: BoardState
        onGameMain { activity ->
            val fragment = gameFragment(activity)
            val grid = field(fragment, "gridLayout") as GridLayout
            result = BoardState((field(fragment, "cardValues") as IntArray).toList(),
                (field(fragment, "cardRevealed") as BooleanArray).toList(),
                (field(fragment, "cardMatched") as BooleanArray).toList(), field(fragment, "firstSelectedIndex") as Int,
                field(fragment, "secondSelectedIndex") as Int, field(fragment, "isProcessing") as Boolean,
                field(fragment, "moveCount") as Int, field(fragment, "matchedPairs") as Int,
                field(fragment, "errorCount") as Int, field(fragment, "gameActive") as Boolean,
                field(fragment, "currentScore") as Int, field(fragment, "highScore") as Int,
                field(fragment, "startTimeMs") as Long, field(fragment, "pairCount") as Int,
                grid.rowCount, grid.columnCount, (field(fragment, "tvStatus") as TextView).text.toString())
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
        Log.i(TAG, "Actual Memory button=${button.text} geometry=$geometry density=${button.resources.displayMetrics.density} " +
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

    private fun touchField(name: String): TouchReceipt = touchButton { field(gameFragment(it), name) as Button }
    private fun touchCard(index: Int): TouchReceipt = touchButton { cards(gameFragment(it))[index] }

    private fun touchButton(resolve: (DynamicGameActivity) -> Button): TouchReceipt {
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
            assertTrue("Actual card touch must return within 500ms, before delayed matching resolves",
                SystemClock.uptimeMillis() - downTime <= 500L)
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
            Log.i(TAG, "Actual Memory input action=$action eventTime=$eventTime acceptedAt=${SystemClock.uptimeMillis()} accepted=$accepted point=$point")
            assertTrue("System must accept the real Memory touchscreen event", accepted)
        } finally { event.recycle() }
    }

    private fun withInstalledGame(action: (ActivityScenario<DynamicGameActivity>, AppDatabase, GameUsageEntity?) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val known = listOf("memory", "memory_module", "memory_save", "memory_settings", "GameMatrix_saves",
                "game_usage", "streak_tracker", "play_time_limit")
            val existing = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "xml" }.map { it.nameWithoutExtension }
                .filter { it == "memory" || it.startsWith("memory_") || it.startsWith("mod_memory__") || it.endsWith("__GameMatrix_saves") }
            val prefsBefore = (known + known.map { ModuleScopedPreferences.scopedName(MODULE_ID, it) } + existing)
                .distinct().map(::capturePreferences)
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            try {
                ensureShipped(manifest, apk)
                val launched = ActivityScenario.launch<DynamicGameActivity>(
                    Intent(context, DynamicGameActivity::class.java).putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                scenario = launched
                launched.onActivity { activeActivity = it; gameFragment(it) }
                action(launched, database, usageBefore)
            } finally {
                try { scenario?.close() } finally {
                    try { ModuleManager.unloadModule(context, MODULE_ID) } finally {
                        try { restoreUserState(database, usageBefore, prefsBefore) } finally {
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
        assertNotNull("The real external Memory Fragment must attach", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.memory.MemoryModuleFragment", it.javaClass.name)
            assertFalse("Memory must not use host game classes", it.javaClass.classLoader === context.classLoader)
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
                assertEquals("memory Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore memory user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed memory package",
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
            assertTrue("Shipped memory must install through the production transaction",
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
        assertEquals("The shipped catalog must identify memory exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals(100, manifest.versionCode)
        assertEquals("game_memory_v100.apk", manifest.fileName)
        assertEquals("com.gamecenter.app.memory.MemoryModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped memory must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped memory must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-memory-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("memory APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("memory APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("memory APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
        @Suppress("DEPRECATION")
        val packageInfo = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0))
        assertEquals("com.gamecenter.app.memory", packageInfo.packageName)
        assertEquals("1.0.0", packageInfo.versionName)
        @Suppress("DEPRECATION")
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        assertEquals("The shipped Memory APK manifest must identify version 100", 100L, archiveVersion)
        Log.i(TAG, "Actual Memory archive package=${packageInfo.packageName} version=$archiveVersion " +
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
            throw AssertionError("Failed to restore memory preferences after device verification").apply {
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
        assertTrue("Restore Memory usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Memory usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "memory"
        private const val GAME_ID = "memory"
        private const val TAG = "ShippedMemoryTest"
    }
}

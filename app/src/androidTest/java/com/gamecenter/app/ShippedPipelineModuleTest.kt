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
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
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
import java.util.ArrayDeque
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
 * Natural levels 1-5 from the real shipped external module. Gameplay uses the production
 * Buttons' performClick listeners, NOT system touchscreen injection. Reflection only reads
 * the actual Fragment, public engine getters and Views; no seed, board, generator, private
 * method or engine state is changed. A bounded independent glyph/shape DFS supplies one
 * route; an independent glyph BFS verifies the displayed result before checking it.
 * Flat/scoped usage preferences and only the pipeline Room row are restored in finally.
 * PixelCopy captures actual committed window buffers. The caller owns device selection.
 */
@RunWith(AndroidJUnit4::class)
class ShippedPipelineModuleTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedPipelinePackagePassesIntegrityAndPinnedSignature() {
        withShippedPackage { manifest, apk -> verifyPackage(manifest, apk) }
    }

    @Test
    fun shippedPipelineNaturalLevelsOneThroughFiveWinThroughRealButtonListeners() {
        withInstalledGame { scenario, database, baselineWins ->
            Log.i(TAG, "Gameplay input: actual Button.performClick listeners; NOT system touchscreen injection")
            captureScreen("pipeline-opened-before-start", scenario) {
                fragmentField(gameFragment(it), "btnStart") as View
            }
            for (level in 1..5) {
                clickStartOrNext(scenario, level)
                val size = when (level) { 1, 2 -> 4; 3, 4 -> 5; else -> 6 }
                val initialLabel = if (level == 5) "pipeline-level5-next-6x6" else "pipeline-level$level-initial"
                captureScreen(initialLabel, scenario, ::boardView)
                val initial = assertNaturalBoard(scenario, level, size)
                assertEquals("Starting a level must not record a win", baselineWins + level - 1,
                    wins(database))

                // Search runs outside the Activity callback and is bounded by both nodes and time.
                val route = findIndependentRoute(initial)
                Log.i(TAG, "Natural level=$level size=${size}x$size routeCells=${route.glyphs.count { it != null }} " +
                    "searchNodes=${route.nodes}; no generated layout or random seed replaced")
                rotateRealButtonsToRoute(scenario, initial, route)
                val connected = readBoard(scenario)
                assertEquals("Solving must preserve every naturally generated pipe type", initial.types, connected.types)
                for (index in route.glyphs.indices) if (route.glyphs[index] == null) {
                    assertEquals("The solver must leave unused cells at their natural initial orientation",
                        initial.glyphs[index], connected.glyphs[index])
                }
                assertTrue("Independent visible-port BFS must reach the bottom-right cell",
                    visiblyConnected(connected))
                captureScreen("pipeline-level$level-connected-before-check", scenario, ::boardView)
                assertEquals("Rotation alone must not record a win", baselineWins + level - 1, wins(database))

                scenario.onActivity { activity ->
                    val fragment = gameFragment(activity)
                    val check = fragmentField(fragment, "btnCheck") as Button
                    assertButtonGeometry(check)
                    assertTrue(check.isEnabled && check.isClickable)
                    assertTrue("Invoke the actual check Button listener", check.performClick())
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val completed = readBoard(scenario)
                assertFalse("The real check listener must finish this connected level", completed.active)
                assertEquals("A single win must advance exactly one level", level + 1, completed.level)
                assertTrue("A real completed level must award score", completed.score > initial.score)
                assertEquals("The real usage store must record exactly one new win",
                    baselineWins + level, wins(database))
                scenario.onActivity { activity ->
                    val fragment = gameFragment(activity)
                    val status = fragmentField(fragment, "tvStatus") as TextView
                    fullyVisibleScreenBounds(status)
                    assertTrue("Completion must be visible to the player", status.text.toString().contains("通关"))
                    val next = fragmentField(fragment, "btnStart") as Button
                    assertButtonGeometry(next)
                    assertTrue(next.text.toString().contains("下一关"))
                    assertFalse((fragmentField(fragment, "btnCheck") as Button).isShown)

                    // Intentional listener-level idempotence check on the now-hidden check Button.
                    // This does not claim a user can tap a hidden widget or inject a system touch.
                    repeat(3) { (fragmentField(fragment, "btnCheck") as Button).performClick() }
                    pipeButtons(fragment).first().performClick()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals("Repeated listeners after completion must not rotate, rescore or advance",
                    completed, readBoard(scenario))
                assertEquals("Repeated check clicks must not add another win", baselineWins + level, wins(database))
                if (level == 5) captureScreen("pipeline-level5-completed", scenario, ::boardView)
            }
        }
    }

    private data class BoardState(
        val level: Int, val size: Int, val moves: Int, val score: Int, val active: Boolean,
        val types: List<Int>, val glyphs: List<String>
    )

    private data class Route(val glyphs: List<String?>, val nodes: Int)

    private fun withInstalledGame(action: (ActivityScenario<DynamicGameActivity>, AppDatabase, Int) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // Pipeline persists only GameUsageStore statistics, including the shared daily-time key.
            val prefsBefore = listOf(
                capturePreferences("game_usage"),
                capturePreferences(ModuleScopedPreferences.scopedName(MODULE_ID, "game_usage"))
            )
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            try {
                ensureShipped(manifest, apk)
                val launched = ActivityScenario.launch<DynamicGameActivity>(
                    Intent(context, DynamicGameActivity::class.java)
                        .putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                scenario = launched
                action(launched, database, usageBefore?.wins ?: 0)
            } finally {
                try {
                    scenario?.close()
                } finally {
                    try {
                        ModuleManager.unloadModule(context, MODULE_ID)
                    } finally {
                        try {
                            restoreUserState(database, usageBefore, prefsBefore)
                        } finally {
                            if (previousCatalog != null) {
                                ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun wins(database: AppDatabase): Int = database.gameUsageDao().getByIdSync(GAME_ID)?.wins ?: 0

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("DynamicGameActivity must attach the actual external Pipeline Fragment", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.pipeline.PipelineModuleFragment", it.javaClass.name)
            assertFalse("Pipeline must not come from host classes", it.javaClass.classLoader === context.classLoader)
            assertSame("Pipeline must use the verified module ClassLoader",
                ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
            assertTrue(it.requireView().isShown)
        }
    }

    private fun fragmentField(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun engine(fragment: Fragment): Any = requireNotNull(fragmentField(fragment, "game")).also {
        assertEquals("com.gamecenter.app.pipeline.PipelineGame", it.javaClass.name)
        assertSame(fragment.javaClass.classLoader, it.javaClass.classLoader)
    }

    private fun intGetter(game: Any, name: String): Int = game.javaClass.getMethod(name).invoke(game) as Int

    private fun glyph(game: Any, row: Int, col: Int): String = game.javaClass
        .getMethod("getPipeChar", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        .invoke(game, row, col) as String

    private fun state(fragment: Fragment): BoardState {
        val game = engine(fragment)
        val size = intGetter(game, "getGridSize")
        assertTrue("Production board must remain in the reviewed size range", size in 4..6)
        val typeGetter = game.javaClass.getMethod("getPipeType", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val types = ArrayList<Int>(size * size)
        val glyphs = ArrayList<String>(size * size)
        for (row in 0 until size) for (col in 0 until size) {
            val type = typeGetter.invoke(game, row, col) as Int
            val shown = glyph(game, row, col)
            assertTrue("Unknown pipe type from actual generator", type in SHAPES.indices)
            assertTrue("Actual displayed character must belong to its pipe type", shown in SHAPES[type])
            types.add(type)
            glyphs.add(shown)
        }
        return BoardState(intGetter(game, "getCurrentLevel"), size, intGetter(game, "getMoveCount"),
            intGetter(game, "getTotalScore"), game.javaClass.getMethod("isGameActive").invoke(game) as Boolean,
            types, glyphs)
    }

    private fun readBoard(scenario: ActivityScenario<DynamicGameActivity>): BoardState {
        lateinit var result: BoardState
        scenario.onActivity { result = state(gameFragment(it)) }
        return result
    }

    private fun boardView(activity: DynamicGameActivity): View = fragmentField(gameFragment(activity), "gridLayout") as View

    private fun pipeButtons(fragment: Fragment): List<Button> =
        (fragmentField(fragment, "pipeButtons") as Array<*>).map { it as Button }

    private fun clickStartOrNext(scenario: ActivityScenario<DynamicGameActivity>, level: Int) {
        scenario.onActivity { activity ->
            val button = fragmentField(gameFragment(activity), "btnStart") as Button
            assertButtonGeometry(button)
            assertTrue(button.isEnabled && button.isClickable)
            assertTrue("Use the real start/next-level control",
                button.text.toString().contains(if (level == 1) "开始" else "下一关"))
            assertTrue(button.performClick())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun assertNaturalBoard(scenario: ActivityScenario<DynamicGameActivity>, level: Int, size: Int): BoardState {
        lateinit var result: BoardState
        scenario.onActivity { activity ->
            val fragment = gameFragment(activity)
            result = state(fragment)
            assertEquals(level, result.level)
            assertEquals("Progression must expose actual 4x4, 5x5 and 6x6 boards", size, result.size)
            assertEquals("Every naturally started level resets its move counter", 0, result.moves)
            assertTrue(result.active)
            fullyVisibleScreenBounds(boardView(activity))
            val hint = descendants(fragment.requireView()).filterIsInstance<TextView>().single {
                it.text.toString() == "左上起点 → 右下终点"
            }
            fullyVisibleScreenBounds(hint)
            val buttons = pipeButtons(fragment)
            assertEquals(size * size, buttons.size)
            assertTrue(buttons.first().contentDescription.toString().contains("起点"))
            assertTrue(buttons.last().contentDescription.toString().contains("终点"))
            assertTrue(result.types.first() != 0 && result.types.last() != 0)
            for (button in descendants(fragment.requireView()).filterIsInstance<Button>().filter { it.isShown }) {
                assertButtonGeometry(button)
            }
            for (index in buttons.indices) {
                assertTrue("Every grid cell must be visible", buttons[index].isShown)
                assertEquals("Displayed Buttons must match the actual engine characters",
                    result.glyphs[index], buttons[index].text.toString())
                assertEquals("Only empty cells should be disabled", result.types[index] != 0, buttons[index].isEnabled)
            }
        }
        return result
    }

    private fun assertButtonGeometry(button: Button) {
        val minimum = kotlin.math.ceil(48.0 * button.resources.displayMetrics.density).toInt()
        assertTrue("Actual Button ${button.width}x${button.height}px must be at least $minimum px (48dp)",
            button.width >= minimum && button.height >= minimum)
        fullyVisibleScreenBounds(button)
    }

    private fun findIndependentRoute(board: BoardState): Route {
        assertFalse("Search must not run on the Activity main thread", Looper.myLooper() === Looper.getMainLooper())
        val chosen = arrayOfNulls<String>(board.types.size)
        val visited = BooleanArray(board.types.size)
        val deadline = SystemClock.uptimeMillis() + 2_000L
        var nodes = 0
        fun visit(index: Int, incoming: Int): Boolean {
            nodes++
            if (nodes > 200_000 || SystemClock.uptimeMillis() > deadline) {
                throw AssertionError("Independent route search exceeded its explicit bound: level=${board.level}, nodes=$nodes")
            }
            val type = board.types[index]
            if (type == 0) return false
            if (index == board.types.lastIndex) {
                chosen[index] = SHAPES[type].firstOrNull { ports(it) and incoming == incoming }
                return chosen[index] != null
            }
            visited[index] = true
            val row = index / board.size
            val col = index % board.size
            for (direction in intArrayOf(1, 2, 3, 0)) {
                val nextRow = row + DR[direction]
                val nextCol = col + DC[direction]
                if (nextRow !in 0 until board.size || nextCol !in 0 until board.size) continue
                val next = nextRow * board.size + nextCol
                if (visited[next]) continue
                val required = incoming or (1 shl direction)
                val fitting = SHAPES[type].firstOrNull { ports(it) and required == required } ?: continue
                chosen[index] = fitting
                if (visit(next, 1 shl ((direction + 2) % 4))) return true
            }
            chosen[index] = null
            visited[index] = false
            return false
        }
        assertTrue("Naturally generated level ${board.level} must admit an independently found route", visit(0, 0))
        return Route(chosen.toList(), nodes)
    }

    private fun rotateRealButtonsToRoute(scenario: ActivityScenario<DynamicGameActivity>, initial: BoardState, route: Route) {
        scenario.onActivity { activity ->
            val fragment = gameFragment(activity)
            val game = engine(fragment)
            val buttons = pipeButtons(fragment)
            for (index in route.glyphs.indices) {
                val wanted = route.glyphs[index] ?: continue
                val button = buttons[index]
                val row = index / initial.size
                val col = index % initial.size
                assertButtonGeometry(button)
                assertTrue(button.isEnabled && button.isClickable)
                var clicks = 0
                while (glyph(game, row, col) != wanted && clicks < 4) {
                    val movesBefore = intGetter(game, "getMoveCount")
                    assertTrue("Rotate through the actual per-cell Button listener", button.performClick())
                    clicks++
                    assertEquals(movesBefore + 1, intGetter(game, "getMoveCount"))
                    assertEquals(glyph(game, row, col), button.text.toString())
                }
                assertEquals("At most four real rotations must attain the independently chosen glyph", wanted,
                    glyph(game, row, col))
                assertEquals(wanted, button.text.toString())
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun visiblyConnected(board: BoardState): Boolean {
        val visited = BooleanArray(board.glyphs.size)
        val pending = ArrayDeque<Int>()
        if (ports(board.glyphs[0]) == 0) return false
        visited[0] = true
        pending.add(0)
        while (pending.isNotEmpty()) {
            val index = pending.removeFirst()
            val mask = ports(board.glyphs[index])
            val row = index / board.size
            val col = index % board.size
            for (direction in 0..3) {
                val nextRow = row + DR[direction]
                val nextCol = col + DC[direction]
                if (nextRow !in 0 until board.size || nextCol !in 0 until board.size) continue
                val next = nextRow * board.size + nextCol
                if (!visited[next] && mask and (1 shl direction) != 0 &&
                    ports(board.glyphs[next]) and (1 shl ((direction + 2) % 4)) != 0) {
                    visited[next] = true
                    pending.add(next)
                }
            }
        }
        return visited.last()
    }

    /** Visible strokes only: U=1, R=2, D=4, L=8. Never read the module's port/answer tables. */
    private fun ports(glyph: String): Int = when (glyph) {
        "" -> 0
        "│" -> 5
        "─" -> 10
        "└" -> 3
        "┌" -> 6
        "┐" -> 12
        "┘" -> 9
        "├" -> 7
        "┬" -> 14
        "┤" -> 13
        "┴" -> 11
        "┼" -> 15
        else -> error("Unknown visible glyph: $glyph")
    }

    private fun fullyVisibleScreenBounds(view: View): Rect {
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
        assertTrue(view.getLocalVisibleRect(local))
        assertEquals("Real input target must not be clipped", Rect(0, 0, view.width, view.height), local)
        val location = IntArray(2).also(view::getLocationOnScreen)
        val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val window = Rect().also(view::getWindowVisibleDisplayFrame)
        assertTrue("Real input target $bounds must fit in the visible window $window", window.contains(bounds))
        return bounds
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(descendants(root.getChildAt(index)))
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
                assertEquals("pipeline Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore pipeline user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed pipeline package",
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
            assertTrue("Shipped pipeline must install through the production transaction",
                ModuleManager.applyExternalUpdate(context, MODULE_ID, apk, manifest.versionCode))
            TransactionInstaller.getCurrentFile(context, manifest)
        }
        assertEquals(manifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        verifyPackage(manifest, current)
        assertEquals("Current must contain exactly the shipped APK bytes", sha256(apk), sha256(current))
        Log.i(TAG, "Verified current ${current.name} v${manifest.versionCode} sha256=${manifest.sha256}")
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
            scenario.onActivity { activity ->
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
            scenario.onActivity { activity ->
                assertTrue("The same screenshot target must still be attached and visible: $label",
                    target?.let { it.isAttachedToWindow && it.isShown && !it.isLayoutRequested
                        && it.rootView === root } == true)
                fullyVisibleScreenBounds(requireNotNull(target))
                fullyVisibleScreenBounds(fragmentField(gameFragment(activity), "tvStats") as TextView)
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
            scenario.onActivity { activity ->
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

    private fun withShippedPackage(action: (ModuleManifest, File) -> Unit) {
        // Instrumentation enters directly, without Splash's context-aware catalog bootstrap.
        // Do this before any context-free manifest/version lookup can seed a VPN-only cache.
        ModuleManager.registerLocalFallbackIfNeeded(context)
        val modules = context.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("modules")
        }
        val records = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { it.optString("id") == MODULE_ID }
        assertEquals("The shipped catalog must identify pipeline exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals("com.gamecenter.app.pipeline.PipelineModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped pipeline must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped pipeline must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-pipeline-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("pipeline APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("pipeline APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("pipeline APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
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
            throw AssertionError("Failed to restore pipeline preferences after device verification").apply {
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
        assertTrue("Restore Pipeline usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Pipeline usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "pipeline"
        private const val GAME_ID = "pipeline"
        private const val TAG = "ShippedPipelineTest"
        private val DR = intArrayOf(-1, 0, 1, 0)
        private val DC = intArrayOf(0, 1, 0, -1)
        // Independent visual possibilities for each stable pipe-type ID (not production data).
        private val SHAPES = listOf(
            listOf(""), listOf("│", "─"), listOf("└", "┌", "┐", "┘"),
            listOf("├", "┬", "┤", "┴"), listOf("┼")
        )
    }

}

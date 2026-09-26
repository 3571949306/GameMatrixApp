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
 * Real installed-host evidence: pinned shipped APK, external dynamic Fragment, touchscreen
 * flag/restart/first-reveal behavior, and actual window pixels. No board mutation or listener
 * replacement is used. Reflection only reads the loaded module's state and view geometry.
 * Flat/scoped usage preferences and only the minesweeper Room row are restored in finally.
 * Same-SHA current bytes are reused without rotating last_good. Device selection belongs
 * to the caller; this class does not choose or connect to an emulator.
 */
@RunWith(AndroidJUnit4::class)
class ShippedMinesweeperModuleTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedMinesweeperPackagePassesIntegrityAndPinnedSignature() {
        withShippedPackage { manifest, apk -> verifyPackage(manifest, apk) }
    }

    @Test
    fun shippedMinesweeperRealLongPressUpdatesAllDifficultyCountersAndRestart() {
        withInstalledGame { scenario ->
            val difficulties = listOf(
                Difficulty(1, R.string.game_diff_easy, 10, 9, 9),
                Difficulty(2, R.string.game_diff_normal, 40, 16, 16),
                Difficulty(3, R.string.game_diff_hard, 99, 16, 30)
            )
            for (difficulty in difficulties) {
                touchButton(scenario, difficulty.buttonId)
                captureScreen("minesweeper-d${difficulty.level}-initial", scenario, ::boardView)
                assertFreshBoard(scenario, difficulty)

                longPressFirstCell(scenario)
                assertCounter(scenario, difficulty.mines, 1)
                assertEquals("Flagging must not reveal a cell", 0, readState(scenario).revealed)
                captureScreen("minesweeper-d${difficulty.level}-flagged", scenario, ::boardView)

                longPressFirstCell(scenario)
                assertCounter(scenario, difficulty.mines, 0)
                assertEquals("Unflagging must not reveal a cell", 0, readState(scenario).revealed)

                // Restart while a flag is present: this must reset both board and header.
                longPressFirstCell(scenario)
                assertCounter(scenario, difficulty.mines, 1)
                touchButton(scenario, R.string.game_btn_restart)
                captureScreen("minesweeper-d${difficulty.level}-restarted", scenario, ::boardView)
                assertFreshBoard(scenario, difficulty)

                tapFirstCell(scenario)
                scenario.onActivity { activity ->
                    val board = boardView(activity)
                    val actual = state(board)
                    assertTrue("A real first tap must reveal safe cells", actual.revealed > 0)
                    assertFalse("The first tap must initialize the real mine layout", actual.firstClick)
                    assertTrue("A safe first click may win but must never lose", !actual.gameOver || actual.gameWon)
                    val revealed = boardField(board, "revealed") as Array<*>
                    val mines = boardField(board, "mines") as Array<*>
                    assertTrue("The touched top-left cell must actually be revealed", (revealed[0] as BooleanArray)[0])
                    for (row in 0..1) for (col in 0..1) {
                        assertFalse("The first cell and its valid neighbours must be mine-free",
                            (mines[row] as BooleanArray)[col])
                    }
                    assertEquals("First-click generation must keep the selected mine total",
                        difficulty.mines, mines.sumOf { (it as BooleanArray).count { mine -> mine } })
                }
                assertCounter(scenario, difficulty.mines, 0)
                captureScreen("minesweeper-d${difficulty.level}-safe-first-reveal", scenario, ::boardView)
            }
        }
    }

    @Test
    fun shippedMinesweeperOutsideReleaseDoesNotInsertDelayedFlag() {
        assertInterruptedGestureDoesNotFlag(MotionEvent.ACTION_UP, "outside-release")
    }

    @Test
    fun shippedMinesweeperOutsideCancelDoesNotInsertDelayedFlag() {
        assertInterruptedGestureDoesNotFlag(MotionEvent.ACTION_CANCEL, "outside-cancel")
    }

    private data class Difficulty(val level: Int, val buttonId: Int, val mines: Int, val rows: Int, val cols: Int)
    private data class BoardState(
        val difficulty: Int, val rows: Int, val cols: Int, val mines: Int, val flags: Int,
        val revealed: Int, val firstClick: Boolean, val started: Boolean, val gameOver: Boolean, val gameWon: Boolean
    )
    private data class TouchPoint(val x: Float, val y: Float)

    private fun withInstalledGame(action: (ActivityScenario<DynamicGameActivity>) -> Unit) {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // GameUsageStore uses application-context preferences; keep the module namespace
            // too. Minesweeper currently has no SaveManager or separate progress store.
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
                captureScreen("minesweeper-opened", launched, ::boardView)
                action(launched)
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

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("DynamicGameActivity must attach the real external Minesweeper Fragment", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.minesweeper.MinesweeperModuleFragment", it.javaClass.name)
            assertFalse("Minesweeper must not come from host classes", it.javaClass.classLoader === context.classLoader)
            assertSame("Minesweeper must use the verified module ClassLoader",
                ModuleLoader.getClassLoader(MODULE_ID), it.javaClass.classLoader)
            assertTrue(it.requireView().isShown)
        }
    }

    private fun fragmentField(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun boardView(activity: DynamicGameActivity): View {
        val fragment = gameFragment(activity)
        return (fragmentField(fragment, "minesweeperView") as View).also {
            assertEquals("com.gamecenter.app.minesweeper.MinesweeperView", it.javaClass.name)
            assertSame(fragment.javaClass.classLoader, it.javaClass.classLoader)
        }
    }

    private fun boardField(board: View, name: String): Any? =
        board.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(board)

    private fun state(board: View) = BoardState(
        boardField(board, "difficulty") as Int, boardField(board, "rows") as Int,
        boardField(board, "cols") as Int, boardField(board, "mineCount") as Int,
        boardField(board, "flaggedCount") as Int, boardField(board, "revealedCount") as Int,
        boardField(board, "firstClick") as Boolean, boardField(board, "gameStarted") as Boolean,
        boardField(board, "gameOver") as Boolean, boardField(board, "gameWon") as Boolean
    )

    private fun readState(scenario: ActivityScenario<DynamicGameActivity>): BoardState {
        lateinit var result: BoardState
        scenario.onActivity { result = state(boardView(it)) }
        return result
    }

    private fun assertCounter(scenario: ActivityScenario<DynamicGameActivity>, mines: Int, flags: Int) {
        scenario.onActivity { activity ->
            val fragment = gameFragment(activity)
            val actual = state(boardView(activity))
            assertEquals(mines, actual.mines)
            assertEquals("The actual board must change its flag count", flags, actual.flags)
            val counter = fragmentField(fragment, "tvMines") as TextView
            fullyVisibleScreenBounds(counter)
            assertEquals("Visible remaining mines must follow real flag changes immediately",
                activity.getString(R.string.game_mines_remaining_format, mines - flags), counter.text.toString())
        }
    }

    private fun assertFreshBoard(scenario: ActivityScenario<DynamicGameActivity>, expected: Difficulty) {
        assertEquals("The real difficulty/restart Button must create an untouched selected board",
            BoardState(expected.level, expected.rows, expected.cols, expected.mines, 0, 0, true, true, false, false),
            readState(scenario))
        assertCounter(scenario, expected.mines, 0)
    }

    private fun firstCellPoint(scenario: ActivityScenario<DynamicGameActivity>): TouchPoint {
        lateinit var result: TouchPoint
        scenario.onActivity { activity ->
            val board = boardView(activity)
            val bounds = fullyVisibleScreenBounds(board)
            val actual = state(board)
            val cell = minOf(board.width.toFloat() / actual.cols, board.height.toFloat() / actual.rows)
            assertTrue("A real board cell needs a non-empty touch interior", cell > 2f)
            result = TouchPoint(bounds.left + (board.width - cell * actual.cols) / 2f + cell / 2f,
                bounds.top + (board.height - cell * actual.rows) / 2f + cell / 2f)
            assertTrue(bounds.contains(result.x.toInt(), result.y.toInt()))
        }
        return result
    }

    private fun longPressFirstCell(scenario: ActivityScenario<DynamicGameActivity>) {
        val point = firstCellPoint(scenario)
        gesture(point) { downTime ->
            // This is the intentional duration of a real held pointer, not a rendering wait.
            SystemClock.sleep(LONG_PRESS_OBSERVATION_MS)
            injectTouch(downTime, MotionEvent.ACTION_UP, point)
        }
    }

    private fun tapFirstCell(scenario: ActivityScenario<DynamicGameActivity>) {
        val point = firstCellPoint(scenario)
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
    }

    private fun assertInterruptedGestureDoesNotFlag(action: Int, label: String) {
        withInstalledGame { scenario ->
            val hard = Difficulty(3, R.string.game_diff_hard, 99, 16, 30)
            touchButton(scenario, hard.buttonId)
            captureScreen("minesweeper-$label-before", scenario, ::boardView)
            assertFreshBoard(scenario, hard)
            val firstCell = firstCellPoint(scenario)
            lateinit var outside: TouchPoint
            scenario.onActivity { activity ->
                val board = boardView(activity)
                val bounds = fullyVisibleScreenBounds(board)
                val actual = state(board)
                val cell = minOf(board.width.toFloat() / actual.cols, board.height.toFloat() / actual.rows)
                val gridBottom = bounds.top + (board.height + cell * actual.rows) / 2f
                val gridRight = bounds.left + (board.width + cell * actual.cols) / 2f
                // Stay inside the View's visible padding, but beyond the positive grid edge.
                // This avoids negative-index truncation and never resizes a pending gesture.
                outside = when {
                    bounds.bottom - gridBottom > 4f -> TouchPoint(bounds.exactCenterX(), (gridBottom + bounds.bottom) / 2f)
                    bounds.right - gridRight > 4f -> TouchPoint((gridRight + bounds.right) / 2f, bounds.exactCenterY())
                    else -> error("HARD board needs visible grid padding for the outside-input fixture")
                }
                assertTrue("The outside-grid endpoint must remain in the visible board View",
                    bounds.contains(outside.x.toInt(), outside.y.toInt()))
            }
            gesture(firstCell) { downTime ->
                // End immediately, before the production 500 ms callback can legally fire.
                assertTrue("Input delivery must finish before the long-press threshold",
                    SystemClock.uptimeMillis() - downTime < 450L)
                injectTouch(downTime, action, outside)
            }
            // An idle queue can still contain a delayed callback: observe beyond its due time.
            SystemClock.sleep(LONG_PRESS_OBSERVATION_MS)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreen("minesweeper-$label-after", scenario, ::boardView)
            assertFreshBoard(scenario, hard)
        }
    }

    private fun touchButton(scenario: ActivityScenario<DynamicGameActivity>, resourceId: Int) {
        lateinit var point: TouchPoint
        scenario.onActivity { activity ->
            val label = activity.getString(resourceId)
            val buttons = descendants(gameFragment(activity).requireView()).filterIsInstance<Button>()
                .filter { it.text.toString() == label && it.isShown }.toList()
            assertEquals("Exactly one real Button must be visible: $label", 1, buttons.size)
            val button = buttons.single()
            assertTrue(button.isEnabled && button.isClickable)
            val minimum = kotlin.math.ceil(48.0 * button.resources.displayMetrics.density).toInt()
            assertTrue("$label touch target ${button.width}x${button.height}px must be at least ${minimum}px (48dp)",
                button.width >= minimum && button.height >= minimum)
            val bounds = fullyVisibleScreenBounds(button)
            point = TouchPoint(bounds.exactCenterX(), bounds.exactCenterY())
        }
        gesture(point) { downTime -> injectTouch(downTime, MotionEvent.ACTION_UP, point) }
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
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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
        assertTrue("Real input target must fit in the visible window", window.contains(bounds))
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
                assertEquals("minesweeper Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore minesweeper user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed minesweeper package",
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
            assertTrue("Shipped minesweeper must install through the production transaction",
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
                fullyVisibleScreenBounds(fragmentField(gameFragment(activity), "tvMines") as TextView)
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
        assertEquals("The shipped catalog must identify minesweeper exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals("com.gamecenter.app.minesweeper.MinesweeperModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped minesweeper must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped minesweeper must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-minesweeper-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("minesweeper APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("minesweeper APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("minesweeper APK must pass production publisher-certificate pinning",
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
            throw AssertionError("Failed to restore minesweeper preferences after device verification").apply {
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
        assertTrue("Restore Minesweeper usage preferences: ${snapshot.name}", editor.commit())
        assertEquals("Minesweeper usage preferences must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "minesweeper"
        private const val GAME_ID = "minesweeper"
        private const val TAG = "ShippedMinesweeperTest"
        private const val LONG_PRESS_OBSERVATION_MS = 650L
    }
}

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
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamecenter.app.core.common.ModuleManifest
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Sokoban APK actually embedded in the installed host. Game actions use attached
 * direction/undo/reset/menu Buttons; reflection only reads the dynamically loaded board state.
 * No direct engine moves, map replacement, progress fixture, network, or synthetic screenshot.
 * The shipped APK may be installed once when necessary, but a newer installed APK is never
 * downgraded and identical bytes never consume last_good through a redundant update.
 *
 * Sokoban currently has no persistent level save: its Fragment creates an in-memory game.
 * GameUsageStore writes the host's game_usage preferences (daily time) and the Room game_usage
 * row (wins/time). Both are snapshotted before launch and restored after Activity teardown.
 */
@RunWith(AndroidJUnit4::class)
class ShippedSokobanModuleTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedSokobanPackagePassesIntegrityAndPinnedSignature() {
        withShippedPackage { manifest, apk -> verifyPackage(manifest, apk) }
    }

    @Test
    fun shippedSokobanDirectionButtonsUndoResetAndWinFirstAndThirdLevels() {
        withShippedPackage { manifest, apk ->
            withInstalledSokobanUi(manifest, apk) { scenario ->
                lateinit var firstInitial: BoardState
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    val fragment = sokobanFragment(activity)
                    assertFalse("Sokoban must come from the external APK class loader",
                        fragment.javaClass.classLoader === context.classLoader)
                    assertTrue("Fragment must share the verified module loader",
                        fragment.javaClass.classLoader === ModuleLoader.getClassLoader(MODULE_ID))
                    clickButton(fragment.requireView(), activity.getString(R.string.game_level_format, 1))
                    firstInitial = boardState(fragment)
                    assertEquals(1, firstInitial.level)
                    assertEquals(0, firstInitial.moves)
                    assertEquals(0, firstInitial.pushes)
                    assertTrue(firstInitial.running)
                    assertMoveDisplay(activity, fragment)
                }
                captureScreen("sokoban-level-1-initial", scenario, ::boardView)
                scenario.onActivity { activity ->
                    val fragment = sokobanFragment(activity)
                    val view = fragment.requireView()
                    clickDirection(fragment, 'D')
                    assertEquals(1, gameProperty(fragment, "getMoveCount"))
                    assertTrue("The first real DOWN move must push a box, not merely walk",
                        (gameProperty(fragment, "getPushCount") as Int) > 0)
                    clickButton(view, activity.getString(R.string.game_btn_undo))
                    assertEquals("Undo must restore the entire actual map, player and counters",
                        firstInitial, boardState(fragment))
                    assertMoveDisplay(activity, fragment)

                    clickDirection(fragment, 'D')
                    clickDirection(fragment, 'D')
                    assertEquals(2, gameProperty(fragment, "getMoveCount"))
                    assertTrue(boardState(fragment).map != firstInitial.map)
                    clickButton(view, activity.getString(R.string.game_btn_reset))
                    assertEquals("Reset must restore the real initial level without replacing its data",
                        firstInitial, boardState(fragment))
                    assertMoveDisplay(activity, fragment)
                }
                playSolution(scenario, FIRST_SOLUTION)
                scenario.onActivity { activity -> assertWon(activity, level = 1, moves = FIRST_SOLUTION.length) }
                captureScreen("sokoban-level-1-win", scenario, ::boardView)

                scenario.onActivity { activity ->
                    val fragment = sokobanFragment(activity)
                    val view = fragment.requireView()
                    clickButton(view, activity.getString(R.string.game_btn_back_menu))
                    assertTrue("The real level menu must be visible",
                        (fragmentField(fragment, "menuPanel") as View).isShown)
                    clickButton(view, activity.getString(R.string.game_level_format, 3))
                    assertEquals(3, gameProperty(fragment, "getCurrentLevel"))
                    assertEquals(0, gameProperty(fragment, "getMoveCount"))
                    assertEquals(0, gameProperty(fragment, "getPushCount"))
                    assertTrue(gameProperty(fragment, "isRunning") as Boolean)
                    assertMoveDisplay(activity, fragment)
                }
                captureScreen("sokoban-level-3-initial", scenario, ::boardView)
                playSolution(scenario, THIRD_SOLUTION)
                scenario.onActivity { activity -> assertWon(activity, level = 3, moves = THIRD_SOLUTION.length) }
                captureScreen("sokoban-level-3-win", scenario, ::boardView)
                Log.i(TAG, "Real dynamic Sokoban Buttons completed level 1=$FIRST_SOLUTION and " +
                    "level 3=$THIRD_SOLUTION, including real first-level push undo and reset")
            }
        }
    }

    @Test
    fun shippedFiveNewLevelsCanBeSelectedAndWonThroughRealButtons() {
        val levels = listOf(
            Triple(11, 10, "RRUULURULLDRDRDDLLLLUURRRURDLLDDLURULDLUU"),
            Triple(12, 15, "ULUURRRLLLDDDRRRRUUUDDDLLLUULURRRRDRUDDDLLLUULURRR"),
            Triple(13, 9, "UULUURDDURRURRDLLDDRUULUR"),
            Triple(14, 13, "LLULLUURRLLDDRRUUDRUDRRDRULLLLDLLDRRRRR"),
            Triple(15, 13, "ULULUUULURDDDDRDRUUUUDDDLLUURLDDRRUURLDDRRUUURUL")
        )
        withShippedPackage { manifest, apk ->
            withInstalledSokobanUi(manifest, apk, expectedWins = levels.size) { scenario ->
                for ((level, pushes, solution) in levels) {
                    // The real menu is scrollable. Wait for its layout before scrolling,
                    // then require the selected level's entire button to be touchable.
                    captureScreen("sokoban-menu-before-$level", scenario, ::menuView, menuOnly = true)
                    scenario.onActivity { activity ->
                        val menu = menuView(activity) as ScrollView
                        val animated = menu.isSmoothScrollingEnabled
                        try {
                            // Wait for a frame at the final position, not the first frame
                            // of ScrollView's default smooth-scroll animation.
                            menu.isSmoothScrollingEnabled = false
                            menu.fullScroll(View.FOCUS_DOWN)
                        } finally {
                            menu.isSmoothScrollingEnabled = animated
                        }
                    }
                    captureScreen("sokoban-menu-scrolled-$level", scenario, ::menuView, menuOnly = true)
                    scenario.onActivity { activity ->
                        val fragment = sokobanFragment(activity)
                        val label = activity.getString(R.string.game_level_format, level)
                        val matches = descendants(fragment.requireView()).filterIsInstance<Button>()
                            .filter { it.text.toString() == label }.toList()
                        assertEquals("The actual shipped level menu must expose level $level", 1, matches.size)
                        assertButtonFullyTouchable(matches.single())
                        clickButton(fragment.requireView(), label)
                        assertEquals(level, gameProperty(fragment, "getCurrentLevel"))
                        assertEquals(0, gameProperty(fragment, "getMoveCount"))
                        assertEquals(0, gameProperty(fragment, "getPushCount"))
                    }
                    captureScreen("sokoban-level-$level-initial", scenario, ::boardView)
                    scenario.onActivity { activity ->
                        val fragment = sokobanFragment(activity)
                        assertNewBoardReadable(activity, level)
                        val initial = boardState(fragment)
                        var beforePush: BoardState? = null
                        for (direction in solution) {
                            val before = boardState(fragment)
                            clickDirection(fragment, direction)
                            if ((gameProperty(fragment, "getPushCount") as Int) > before.pushes) {
                                beforePush = before
                                break
                            }
                        }
                        assertNotNull("The new level must exercise a real push before undo", beforePush)
                        clickButton(fragment.requireView(), activity.getString(R.string.game_btn_undo))
                        assertEquals("Undo restores the new level's actual pre-push board and counters",
                            beforePush, boardState(fragment))
                        clickButton(fragment.requireView(), activity.getString(R.string.game_btn_reset))
                        assertEquals("Reset restores the exact new level selected from the menu",
                            initial, boardState(fragment))
                    }
                    playSolution(scenario, solution)
                    scenario.onActivity { activity ->
                        assertWon(activity, level, solution.length)
                        assertEquals(pushes, gameProperty(sokobanFragment(activity), "getPushCount"))
                    }
                    captureScreen("sokoban-level-$level-win", scenario, ::boardView)
                    scenario.onActivity { activity ->
                        assertNewBoardReadable(activity, level)
                        clickButton(sokobanFragment(activity).requireView(),
                            activity.getString(R.string.game_btn_back_menu))
                    }
                }
                Log.i(TAG, "All five added levels 11-15 completed through the actual menu and direction Buttons; " +
                    "legal replay witnesses are not shortest-solution claims")
            }
        }
    }

    private fun menuView(activity: DynamicGameActivity): View =
        requireNotNull(fragmentField(sokobanFragment(activity), "menuScroll") as? ScrollView)

    private fun assertNewBoardReadable(activity: DynamicGameActivity, level: Int) {
        val board = boardView(activity)
        val cellPixels = board.javaClass.getDeclaredField("cellSize")
            .apply { isAccessible = true }.getFloat(board)
        val cellDp = cellPixels / board.resources.displayMetrics.density
        assertTrue("New level $level cells must remain readable on this device: ${cellDp}dp",
            cellDp.isFinite() && cellDp >= 32f)
        Log.i(TAG, "New level $level actual board ${board.width}x${board.height}, cell=${cellDp}dp")
    }

    private fun withInstalledSokobanUi(
        manifest: ModuleManifest,
        apk: File,
        expectedWins: Int = 2,
        action: (ActivityScenario<DynamicGameActivity>) -> Unit
    ) {
        // withShippedPackage has already completed context-aware catalog registration.
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val database = AppDatabase.getDatabase(context.applicationContext)
        val usageBefore = database.gameUsageDao().getByIdSync(MODULE_ID)?.copy()
        val prefsBefore = capturePreferences("game_usage")
        var scenario: ActivityScenario<DynamicGameActivity>? = null
        try {
            ensureShipped(manifest, apk)
            val launched = ActivityScenario.launch<DynamicGameActivity>(
                Intent(context, DynamicGameActivity::class.java)
                    .putExtra(DynamicGameActivity.EXTRA_GAME_ID, MODULE_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            scenario = launched
            action(launched)
            val recorded = requireNotNull(database.gameUsageDao().getByIdSync(MODULE_ID))
            assertEquals("Every UI win must reach the actual host usage store exactly once",
                (usageBefore?.wins ?: 0) + expectedWins, recorded.wins)
            assertTrue("Recorded play time must not decrease",
                recorded.totalPlayTimeMs >= (usageBefore?.totalPlayTimeMs ?: 0L))
        } finally {
            try {
                scenario?.close()
            } finally {
                try {
                    ModuleManager.unloadModule(context, MODULE_ID)
                } finally {
                    try {
                        restoreGameUsage(database, usageBefore, prefsBefore)
                    } finally {
                        if (previousCatalog != null) {
                            ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                        }
                    }
                }
            }
        }
    }

    private fun restoreGameUsage(
        database: AppDatabase, rowBefore: GameUsageEntity?, prefsBefore: PreferenceSnapshot
    ) {
        // Attempt both restorations even if one fails. Only Sokoban's row is touched;
        // unrelated games in this database are neither cleared nor copied back wholesale.
        // GameUsageStore's Room writes use *Sync DAO calls in the Button callback, so
        // ActivityScenario.onActivity already awaits them. Drain remaining UI work after
        // teardown; preference commit below also waits for earlier apply disk writes.
        val failures = listOf(
            runCatching { InstrumentationRegistry.getInstrumentation().waitForIdleSync() }.exceptionOrNull(),
            runCatching { restoreAllPreferences(listOf(prefsBefore)) }.exceptionOrNull(),
            runCatching {
                if (rowBefore != null) {
                    database.gameUsageDao().upsertSync(rowBefore)
                } else {
                    // Existing DAO deletes exactly WHERE gameId = :gameId. Await its
                    // Room dispatcher on the instrumentation thread before readback.
                    runBlocking { database.gameUsageDao().delete(MODULE_ID) }
                }
                assertEquals("Sokoban Room usage must match its exact pre-test state",
                    rowBefore, database.gameUsageDao().getByIdSync(MODULE_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore Sokoban usage after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun playSolution(scenario: ActivityScenario<DynamicGameActivity>, solution: String) {
        solution.forEachIndexed { index, direction ->
            scenario.onActivity { activity ->
                val fragment = sokobanFragment(activity)
                assertTrue("Level must still be running before direction ${index + 1}",
                    gameProperty(fragment, "isRunning") as Boolean)
                val before = gameProperty(fragment, "getMoveCount") as Int
                clickDirection(fragment, direction)
                assertEquals("Real direction $direction at step ${index + 1} must move the player",
                    before + 1, gameProperty(fragment, "getMoveCount"))
                assertMoveDisplay(activity, fragment)
            }
        }
    }

    private fun clickDirection(fragment: Fragment, direction: Char) {
        val label = when (direction) {
            'U' -> "↑"
            'D' -> "↓"
            'L' -> "←"
            'R' -> "→"
            else -> error("Invalid test direction $direction")
        }
        clickButton(fragment.requireView(), label)
    }

    private fun assertMoveDisplay(activity: DynamicGameActivity, fragment: Fragment) {
        val moves = gameProperty(fragment, "getMoveCount") as Int
        val pushes = gameProperty(fragment, "getPushCount") as Int
        val display = fragmentField(fragment, "tvMoves") as TextView
        assertTrue("Actual move counter must be visible", display.isShown)
        assertEquals(activity.getString(R.string.game_sokoban_moves_format, moves, pushes),
            display.text.toString())
    }

    private fun assertWon(activity: DynamicGameActivity, level: Int, moves: Int) {
        val fragment = sokobanFragment(activity)
        assertEquals(level, gameProperty(fragment, "getCurrentLevel"))
        assertEquals(moves, gameProperty(fragment, "getMoveCount"))
        assertTrue("All goals must actually be covered", gameProperty(fragment, "isLevelComplete") as Boolean)
        assertFalse("The real win handler must stop input", gameProperty(fragment, "isRunning") as Boolean)
        assertTrue((gameProperty(fragment, "getLevelsCleared") as Int) >= level)
        val status = fragmentField(fragment, "tvStatus") as TextView
        assertTrue("The visible status must use the shipped game's victory text",
            status.isShown && status.text.toString().startsWith(
                activity.getString(R.string.game_sokoban_win_format, moves)))
        assertMoveDisplay(activity, fragment)
    }

    private data class BoardState(
        val map: List<List<Int>>, val row: Int, val col: Int, val moves: Int,
        val pushes: Int, val level: Int, val running: Boolean
    )

    private fun boardState(fragment: Fragment) = BoardState(
        (gameProperty(fragment, "getMap") as Array<*>).map { (it as IntArray).toList() },
        gameProperty(fragment, "getPlayerRow") as Int,
        gameProperty(fragment, "getPlayerCol") as Int,
        gameProperty(fragment, "getMoveCount") as Int,
        gameProperty(fragment, "getPushCount") as Int,
        gameProperty(fragment, "getCurrentLevel") as Int,
        gameProperty(fragment, "isRunning") as Boolean
    )

    private fun sokobanFragment(activity: DynamicGameActivity): Fragment {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("DynamicGameActivity must attach the actual Sokoban Fragment", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.sokoban.SokobanModuleFragment", it.javaClass.name)
            assertTrue("The dynamic Fragment must have a visible view", it.requireView().isShown)
        }
    }

    private fun boardView(activity: DynamicGameActivity): View =
        requireNotNull(fragmentField(sokobanFragment(activity), "sokobanView") as? View).also {
            assertEquals("com.gamecenter.app.sokoban.SokobanView", it.javaClass.name)
        }

    private fun clickButton(root: View, label: String) {
        val controls = descendants(root).filterIsInstance<Button>()
            .filter { it.text.toString() == label && it.isAttachedToWindow && it.isShown && it.isEnabled }.toList()
        assertEquals("Expected one attached visible enabled Sokoban Button: $label", 1, controls.size)
        if (label in gameControlLabels(root)) {
            // performClick can invoke a listener even when the real touch target is clipped.
            // Check every game control, not just the one exercised by this solution.
            assertGameControlsTouchable(root)
        }
        assertTrue("The actual control must execute its click listener: $label", controls.single().performClick())
    }

    private fun gameControlLabels(root: View) = listOf(
        "↑", "←", "↓", "→",
        root.context.getString(R.string.game_btn_undo),
        root.context.getString(R.string.game_btn_reset),
        root.context.getString(R.string.game_btn_back_menu)
    )

    private fun assertGameControlsTouchable(root: View) {
        val buttons = descendants(root).filterIsInstance<Button>().toList()
        gameControlLabels(root).forEach { label ->
            val matches = buttons.filter { it.text.toString() == label }
            assertEquals("Expected exactly one actual game control: $label", 1, matches.size)
            assertButtonFullyTouchable(matches.single())
        }
    }

    private fun assertButtonFullyTouchable(button: Button) {
        val label = button.text.toString()
        val density = button.resources.displayMetrics.density
        val minimumPx = kotlin.math.ceil(48.0 * density).toInt()
        assertTrue("$label must have a laid-out, enabled, clickable window target",
            button.isAttachedToWindow && button.isLaidOut && button.isEnabled && button.isClickable
                && button.windowVisibility == View.VISIBLE)
        assertTrue("$label touch height ${button.height}px (${button.height / density}dp) is below 48dp",
            button.height >= minimumPx)
        assertTrue("$label touch width ${button.width}px is below 48dp", button.width >= minimumPx)

        val windowRoot = button.rootView
        var ancestor: View? = button
        while (ancestor != null) {
            val current = ancestor
            assertTrue("$label has an invisible, transparent or detached hierarchy node: ${current.javaClass.name}",
                current.visibility == View.VISIBLE && current.alpha > 0f && current.isAttachedToWindow)
            ancestor = current.parent as? View
        }

        val local = Rect()
        assertTrue("$label has no locally visible pixels", button.getLocalVisibleRect(local))
        assertEquals("$label is clipped locally", Rect(0, 0, button.width, button.height), local)

        val buttonScreen = IntArray(2).also(button::getLocationOnScreen)
        val rootScreen = IntArray(2).also(windowRoot::getLocationOnScreen)
        val expectedGlobal = Rect(buttonScreen[0] - rootScreen[0], buttonScreen[1] - rootScreen[1],
            buttonScreen[0] - rootScreen[0] + button.width, buttonScreen[1] - rootScreen[1] + button.height)
        val global = Rect()
        assertTrue("$label has no globally visible pixels", button.getGlobalVisibleRect(global))
        assertEquals("$label is clipped by the actual view hierarchy", expectedGlobal, global)

        val screenBounds = Rect(buttonScreen[0], buttonScreen[1],
            buttonScreen[0] + button.width, buttonScreen[1] + button.height)
        val windowBounds = Rect(rootScreen[0], rootScreen[1],
            rootScreen[0] + windowRoot.width, rootScreen[1] + windowRoot.height)
        val visibleWindow = Rect().also(button::getWindowVisibleDisplayFrame)
        assertTrue("$label lies outside the window: button=$screenBounds window=$windowBounds",
            windowBounds.contains(screenBounds))
        assertTrue("$label is outside the unobscured window: button=$screenBounds visible=$visibleWindow",
            !visibleWindow.isEmpty && visibleWindow.contains(screenBounds))

        // Check clipping ancestors explicitly as well as Android's local/global visible rects.
        ancestor = button.parent as? View
        while (ancestor != null) {
            val parent = ancestor
            if (parent is ViewGroup && parent.clipChildren) {
                val location = IntArray(2).also(parent::getLocationOnScreen)
                val paddingLeft = if (parent.clipToPadding) parent.paddingLeft else 0
                val paddingTop = if (parent.clipToPadding) parent.paddingTop else 0
                val paddingRight = if (parent.clipToPadding) parent.paddingRight else 0
                val paddingBottom = if (parent.clipToPadding) parent.paddingBottom else 0
                val bounds = Rect(location[0] + paddingLeft, location[1] + paddingTop,
                    location[0] + parent.width - paddingRight, location[1] + parent.height - paddingBottom)
                assertTrue("$label is clipped by ${parent.javaClass.simpleName}: button=$screenBounds parent=$bounds",
                    bounds.contains(screenBounds))
            }
            ancestor = parent.parent as? View
        }
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(descendants(root.getChildAt(index)))
        }
    }

    // Dynamic game classes are deliberately absent from the host/test APK compilation.
    // Reflection only reads state; every mutation above enters a production Button listener.
    private fun fragmentField(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun gameProperty(fragment: Fragment, name: String): Any {
        val game = requireNotNull(fragmentField(fragment, "game")) { "Sokoban engine was not created" }
        return requireNotNull(game.javaClass.getMethod(name).invoke(game))
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed Sokoban package",
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
            assertTrue("Shipped Sokoban must install through the production transaction",
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
        targetView: (DynamicGameActivity) -> View,
        menuOnly: Boolean = false
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
                if (menuOnly) {
                    assertTrue("Menu capture must target the actual menu ScrollView", target === menuView(activity))
                    assertFalse("Menu capture cannot skip checks for a visible game panel",
                        (fragmentField(sokobanFragment(activity), "gamePanel") as View).isShown)
                } else {
                    assertGameControlsTouchable(sokobanFragment(activity).requireView())
                }
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
        assertEquals("The shipped catalog must identify Sokoban exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals("com.gamecenter.app.sokoban.SokobanModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped Sokoban must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped Sokoban must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-sokoban-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("Sokoban APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("Sokoban APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("Sokoban APK must pass production publisher-certificate pinning",
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
            throw AssertionError("Failed to restore Sokoban preferences after device verification").apply {
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
        assertTrue("Restore Sokoban save preferences: ${snapshot.name}", editor.commit())
        assertEquals("Sokoban user saves must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "sokoban"
        private const val TAG = "ShippedSokobanModuleTest"
        private const val FIRST_SOLUTION = "DDLU"
        private const val THIRD_SOLUTION = "URRDLULDDURDURDLLDURDURD"
    }
}

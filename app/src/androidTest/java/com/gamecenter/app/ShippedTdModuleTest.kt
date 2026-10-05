package com.gamecenter.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only verification of the APK shipped inside the installed host.
 *
 * Runs through the real signature verifier, transaction installer, external class loader,
 * and non-exported DynamicGameActivity. UI actions invoke attached semantic controls on
 * the main thread; there are no screen coordinates, fixed sleeps, downloads, or test
 * replacements for module verification. Canvas placement uses the laid-out board's own
 * geometry and attached touch dispatcher, never a direct engine mutation.
 *
 * The installed TD package is intentionally reconciled with the shipped package. TD save
 * preferences are captured before entering its UI and restored after the Activity closes.
 */
@RunWith(AndroidJUnit4::class)
class ShippedTdModuleTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shippedTdPackagePassesIntegrityAndPinnedSignature() {
        withShippedPackage { manifest, apk ->
            verifyPackage(manifest, apk)
        }
    }

    @Test
    fun shippedTdInstallsAndLaunchesFirstLevelWithStory() {
        withShippedPackage { manifest, apk ->
            val expectedStories = levelStories(apk, "chapter_main_01", "main_001")
            withInstalledTdUi(manifest, apk) { scenario ->
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    val fragment = tdFragment(activity)
                    assertFalse("Dynamic TD must be loaded from its external module loader",
                        fragment.javaClass.classLoader === context.classLoader)
                    assertTrue("Fragment and the verified module must share a class loader",
                        fragment.javaClass.classLoader === ModuleLoader.getClassLoader(MODULE_ID))
                    val view = fragment.requireView()
                    val firstCard = view.findViewWithTag<View>("td_level_card:main_001")
                    assertNotNull("The shipped first chapter must expose the first level", firstCard)
                    clickButton(firstCard, activity.getString(R.string.game_td_btn_play))
                    clickButton(view, activity.getString(R.string.game_td_difficulty_normal))
                    assertBriefing(activity, view, coin = 240, hp = 5, routes = 1, waves = 6)
                    assertEquals("Preview must not create a live game", null, fragmentField(fragment, "game"))
                }
                captureScreen("main_001-deck", scenario) { activity ->
                    requireNotNull(tdFragment(activity).requireView().findViewWithTag<View>("td_deck_scroll"))
                }
                scenario.onActivity { activity ->
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    clickButton(view, activity.getString(R.string.game_td_btn_start_level))
                    assertStory(view, expectedStories)
                    assertPreparedGame(fragment, coin = 240, hp = 5, routes = 1, waves = 6)
                }
                // A second main-thread dispatch checks the story after the start handler
                // and its queued tick have returned; an immediately-cleared overlay fails.
                scenario.onActivity { activity ->
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    assertStory(view, expectedStories)
                    clickButton(view, activity.getString(R.string.game_td_story_start))
                    assertEquals("Story dismissal must remove the overlay", null,
                        view.findViewWithTag<View>("td_story_intro"))
                    assertEquals("Story dismissal must leave the defense preparation phase",
                        "PREPARING", gameProperty(fragment, "getState").toString())
                    clickButton(view, activity.getString(R.string.game_td_btn_tower))
                    assertNotNull("The preparation controls must allow choosing a tower",
                        fragmentField(fragment, "selectedType"))
                    clickButton(view, activity.getString(R.string.game_td_btn_fight))
                    assertEquals("The real fight control must start the engine",
                        "RUNNING", gameProperty(fragment, "getState").toString())
                    Log.i(TAG, "Verified TD fragment, first-level NORMAL story, preparation controls and wave start")
                }
            }
        }
    }

    @Test
    fun shippedSixthLevelOffersTwoRoutesAndRealPreparationUi() {
        withShippedPackage { manifest, apk ->
            val expectedStories = levelStories(apk, "chapter_main_02", "main_006")
            withInstalledTdUi(manifest, apk, minimumUnlocked = 6) { scenario ->
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    val sixthCard = view.findViewWithTag<View>("td_level_card:main_006")
                    assertNotNull("The temporary progression fixture must expose level six", sixthCard)
                    clickButton(sixthCard, activity.getString(R.string.game_td_btn_play))
                    clickButton(view, activity.getString(R.string.game_td_difficulty_normal))
                    assertBriefing(activity, view, coin = 250, hp = 6, routes = 2, waves = 6)
                    assertEquals("Preview must not create a live game", null, fragmentField(fragment, "game"))
                }
                captureScreen("main_006-deck", scenario) { activity ->
                    requireNotNull(tdFragment(activity).requireView().findViewWithTag<View>("td_deck_scroll"))
                }
                scenario.onActivity { activity ->
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    clickButton(view, activity.getString(R.string.game_td_btn_start_level))
                    assertStory(view, expectedStories)
                    assertPreparedGame(fragment, coin = 250, hp = 6, routes = 2, waves = 6)
                    val paths = gamePaths(fragment)
                    assertEquals("Both routes must have different attack entrances", 2,
                        paths.map { it.first() }.toSet().size)
                    val egg = listOf(gameProperty(fragment, "getEggRow") as Int,
                        gameProperty(fragment, "getEggCol") as Int)
                    paths.forEachIndexed { index, route ->
                        assertEquals("Route $index must reach the actual defended egg", egg, route.last())
                    }
                }
                scenario.onActivity { activity ->
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    assertStory(view, expectedStories)
                    clickButton(view, activity.getString(R.string.game_td_story_start))
                    assertEquals("Story dismissal must expose the board", null,
                        view.findViewWithTag<View>("td_story_intro"))
                    clickButton(view, activity.getString(R.string.game_td_btn_tower))
                    assertNotNull("Level six must allow tower selection during preparation",
                        fragmentField(fragment, "selectedType"))
                    assertEquals("PREPARING", gameProperty(fragment, "getState").toString())
                    placeSelectedTowerByBoardTouch(fragment, row = 3, col = 1)
                }
                captureScreen("main_006-two-route-board", scenario) { activity ->
                    requireNotNull(fragmentField(tdFragment(activity), "tdView") as? View)
                }
                Log.i(TAG, "Verified main_006 two-route briefing, distinct entrances, shared egg, story and touch placement; " +
                    "level access used the temporary unlock fixture, not natural progression")
            }
        }
    }

    @Test
    fun shippedSniperShowsFixedStrongTargetWhileOrdinaryTowerStillCycles() {
        withShippedPackage { manifest, apk ->
            withInstalledTdUi(manifest, apk, minimumUnlocked = 4) { scenario ->
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    val card = requireNotNull(view.findViewWithTag<View>("td_level_card:main_004"))
                    clickButton(card, activity.getString(R.string.game_td_btn_play))
                    clickButton(view, activity.getString(R.string.game_td_difficulty_normal))
                    val description = activity.getString(R.string.game_td_cd_unselected,
                        activity.getString(R.string.game_td_tower_sniper))
                    val sniperCard = descendants(view).filter {
                        it.contentDescription?.toString() == description
                    }.toList()
                    assertEquals("The real unlocked deck must offer a sniper card", 1, sniperCard.size)
                    assertTrue(sniperCard.single().isEnabled)
                    assertTrue(sniperCard.single().performClick())
                    clickButton(view, activity.getString(R.string.game_td_btn_start_level))
                    clickButton(view, activity.getString(R.string.game_td_story_start))
                }
                captureScreen("main_004-preparation", scenario) { activity ->
                    requireNotNull(fragmentField(tdFragment(activity), "tdView") as? View)
                }
                scenario.onActivity { activity ->
                    val fragment = tdFragment(activity)
                    val view = fragment.requireView()
                    val game = requireNotNull(fragmentField(fragment, "game"))
                    val paths = gamePaths(fragment).flatten().toSet()
                    val egg = listOf(gameProperty(fragment, "getEggRow") as Int,
                        gameProperty(fragment, "getEggCol") as Int)
                    val cells = (0 until (gameProperty(fragment, "getRows") as Int)).flatMap { row ->
                        (0 until (gameProperty(fragment, "getCols") as Int)).map { col -> listOf(row, col) }
                    }.filter { it !in paths && it != egg }.take(2)
                    assertEquals(2, cells.size)
                    val coinBefore = gameProperty(fragment, "getCoin") as Int
                    var cost = 0
                    val built = mutableListOf<Any>()
                    listOf("SNIPER", "BOTTLE").forEachIndexed { index, typeName ->
                        val palette = descendants(view).filter {
                            it.tag?.javaClass?.name == "com.gamecenter.app.td.engine.TowerType" &&
                                it.tag.toString() == typeName && it.isShown
                        }.toList()
                        assertEquals("The selected real deck must expose $typeName", 1, palette.size)
                        assertTrue(palette.single().performClick())
                        val type = requireNotNull(fragmentField(fragment, "selectedType"))
                        assertEquals(typeName, type.toString())
                        cost += type.javaClass.getField("baseCost").getInt(type)
                        tapBoardCell(fragment, cells[index][0], cells[index][1])
                        val tower = requireNotNull(game.javaClass.getMethod("getTowerAt",
                            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            .invoke(game, cells[index][0], cells[index][1]))
                        assertEquals(typeName, tower.javaClass.getField("type").get(tower).toString())
                        built.add(tower)
                    }
                    assertEquals(coinBefore - cost, gameProperty(fragment, "getCoin"))
                    tapBoardCell(fragment, cells[0][0], cells[0][1])
                    val target = fragmentField(fragment, "btnTarget") as Button
                    assertTrue(target.isShown)
                    assertFalse("A fixed strong-target sniper must not offer a mode switch", target.isEnabled)
                    assertEquals(activity.getString(R.string.game_td_btn_target_mode,
                        activity.getString(R.string.game_td_target_strong)), target.text.toString())
                }
                captureScreen("main_004-sniper-fixed-strong", scenario) { activity ->
                    requireNotNull(fragmentField(tdFragment(activity), "towerOpsBar") as? View)
                }
                scenario.onActivity { activity ->
                    val fragment = tdFragment(activity)
                    val target = fragmentField(fragment, "btnTarget") as Button
                    val built = (gameProperty(fragment, "getTowers") as List<*>).map { requireNotNull(it) }
                    assertEquals(listOf("SNIPER", "BOTTLE"),
                        built.map { it.javaClass.getField("type").get(it).toString() })
                    val coinsBeforeCycling = gameProperty(fragment, "getCoin")
                    fun cell(tower: Any, field: String) = tower.javaClass.getField(field).getInt(tower)
                    val modeBefore = built[0].javaClass.getField("targetMode").get(built[0])
                    tapLocalView(target, target.width / 2f, target.height / 2f)
                    assertEquals(modeBefore, built[0].javaClass.getField("targetMode").get(built[0]))
                    tapBoardCell(fragment, cell(built[1], "row"), cell(built[1], "col"))
                    assertTrue("Ordinary tower must restore target switching", target.isEnabled)
                    listOf("STRONG" to R.string.game_td_target_strong,
                        "WEAK" to R.string.game_td_target_weak,
                        "FIRST" to R.string.game_td_target_first).forEach { (mode, name) ->
                        assertTrue(target.performClick())
                        assertEquals(mode, built[1].javaClass.getField("targetMode").get(built[1]).toString())
                        assertEquals(activity.getString(R.string.game_td_btn_target_mode,
                            activity.getString(name)), target.text.toString())
                    }
                    assertEquals(2, (gameProperty(fragment, "getTowers") as List<*>).size)
                    assertEquals(coinsBeforeCycling, gameProperty(fragment, "getCoin"))
                }
                Log.i(TAG, "Verified sniper fixed STRONG UI and ordinary tower mode cycling via real deck/board; " +
                    "level access used a temporary unlock fixture")
            }
        }
    }

    private fun tapBoardCell(fragment: Fragment, row: Int, col: Int) {
        val board = requireNotNull(fragmentField(fragment, "tdView") as? View)
        fun geometry(name: String) = board.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.getFloat(board)
        val cell = geometry("cellSize")
        assertTrue(cell.isFinite() && cell > 0f)
        tapLocalView(board, geometry("originX") + (col + 0.5f) * cell,
            geometry("originY") + (row + 0.5f) * cell)
    }

    private fun tapLocalView(view: View, x: Float, y: Float) {
        assertTrue("Touch target must be attached, visible and laid out",
            view.isAttachedToWindow && view.isShown && view.isLaidOut)
        assertTrue("Touch must stay within the actual view", x >= 0f && x < view.width && y >= 0f && y < view.height)
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(time, time + 16L, MotionEvent.ACTION_UP, x, y, 0)
        try {
            down.source = InputDevice.SOURCE_TOUCHSCREEN
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun withInstalledTdUi(
        manifest: ModuleManifest,
        apk: File,
        minimumUnlocked: Int? = null,
        action: (ActivityScenario<DynamicGameActivity>) -> Unit
    ) {
        val saveSnapshots = listOf(
            capturePreferences("td_save"),
            capturePreferences(ModuleScopedPreferences.scopedName(MODULE_ID, "td_save"))
        )
        // Direct instrumentation skips Splash's catalog bootstrap. Match the real
        // DynamicGameActivity entry before any manifest/version query: the context-free
        // lookup otherwise seeds only VPN and can initialize an incomplete version cache.
        // This registers bundled metadata; it does not install or replace any package.
        ModuleManager.registerLocalFallbackIfNeeded(context)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        var scenario: ActivityScenario<DynamicGameActivity>? = null
        try {
            ensureShipped(manifest, apk)
            if (minimumUnlocked != null) {
                // TdSaveManager.KEY_UNLOCKED is private. This exact persisted key is a test
                // fixture only; both legacy and scoped stores were captured above so a
                // pending production migration cannot overwrite the temporary unlock value.
                saveSnapshots.forEach { snapshot ->
                    val prefs = context.getSharedPreferences(snapshot.name, Context.MODE_PRIVATE)
                    val before = prefs.getInt(UNLOCKED_LEVELS_KEY, 1)
                    val fixture = maxOf(before, minimumUnlocked)
                    assertTrue("Commit temporary TD unlock fixture: ${snapshot.name}",
                        prefs.edit().putInt(UNLOCKED_LEVELS_KEY, fixture).commit())
                    Log.i(TAG, "Temporary unlock fixture ${snapshot.name}: $before -> $fixture")
                }
            }
            val launched = ActivityScenario.launch<DynamicGameActivity>(
                Intent(context, DynamicGameActivity::class.java)
                    .putExtra(DynamicGameActivity.EXTRA_GAME_ID, MODULE_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            scenario = launched
            action(launched)
        } finally {
            try {
                scenario?.close()
            } finally {
                try {
                    ModuleManager.unloadModule(context, MODULE_ID)
                } finally {
                    try {
                        restoreAllPreferences(saveSnapshots)
                    } finally {
                        if (previousCatalog != null) {
                            ModuleManager.registerAvailableManifests(listOf(previousCatalog))
                        }
                    }
                }
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed TD package",
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
            assertTrue("Shipped TD must install through the production transaction",
                ModuleManager.applyExternalUpdate(context, MODULE_ID, apk, manifest.versionCode))
            TransactionInstaller.getCurrentFile(context, manifest)
        }
        assertEquals(manifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        verifyPackage(manifest, current)
        assertEquals("Current must contain exactly the shipped APK bytes", sha256(apk), sha256(current))
        Log.i(TAG, "Verified current ${current.name} v${manifest.versionCode} sha256=${manifest.sha256}")
    }

    private fun assertBriefing(
        activity: DynamicGameActivity, root: View, coin: Int, hp: Int, routes: Int, waves: Int
    ) {
        val summary = root.findViewWithTag<TextView>("td_battle_brief_summary")
        assertNotNull("The actual deck UI must contain the battle briefing", summary)
        assertTrue("The briefing must be attached and visible", summary.isShown)
        assertTrue("Briefing resources must match this NORMAL level",
            summary.text.toString().contains(activity.getString(R.string.game_td_brief_resources, coin, hp, routes)))
        assertTrue("Campaign briefing must state the actual wave count",
            summary.text.toString().contains(activity.getString(R.string.game_td_brief_campaign, waves)))
        val enemies = root.findViewWithTag<TextView>("td_battle_brief_enemies")
        assertNotNull("The actual deck UI must contain enemy intelligence", enemies)
        assertTrue("Enemy intelligence must be populated", enemies.text.isNotBlank())
    }

    private fun assertPreparedGame(fragment: Fragment, coin: Int, hp: Int, routes: Int, waves: Int) {
        assertEquals("NORMAL", gameProperty(fragment, "getDifficulty").toString())
        assertEquals("CAMPAIGN", gameProperty(fragment, "getMode").toString())
        assertEquals("PREPARING", gameProperty(fragment, "getState").toString())
        assertEquals(coin, gameProperty(fragment, "getCoin"))
        assertEquals(hp, gameProperty(fragment, "getMascotHp"))
        assertEquals(routes, gamePaths(fragment).size)
        assertEquals(waves, gameProperty(fragment, "getTotalWaves"))
    }

    private fun gamePaths(fragment: Fragment): List<List<List<Int>>> =
        (gameProperty(fragment, "getPaths") as Array<*>).map { route ->
            (route as Array<*>).map { point -> (point as IntArray).toList() }
        }

    /** Called on the main thread after the real select-tower control has selected a card. */
    private fun placeSelectedTowerByBoardTouch(fragment: Fragment, row: Int, col: Int) {
        val board = requireNotNull(fragmentField(fragment, "tdView") as? View)
        assertEquals("The touch target must be the real dynamic board", "com.gamecenter.app.td.TdView",
            board.javaClass.name)
        assertTrue("The board must be attached and visible", board.isAttachedToWindow && board.isShown)
        assertTrue("Touch placement requires a laid-out board", board.isLaidOut && board.width > 0 && board.height > 0)
        val selected = requireNotNull(fragmentField(fragment, "selectedType"))
        assertEquals("The real select-tower button should choose the initial basic tower", "BOTTLE", selected.toString())
        val baseCost = selected.javaClass.getField("baseCost").getInt(selected)
        val coinBefore = gameProperty(fragment, "getCoin") as Int
        assertTrue("The chosen basic tower must be affordable", baseCost > 0 && coinBefore >= baseCost)
        assertEquals("The new preparation board must start without towers", 0,
            (gameProperty(fragment, "getTowers") as List<*>).size)
        assertFalse("The approved placement cell must not occupy either route",
            gamePaths(fragment).any { listOf(row, col) in it })

        // Reading geometry is the only canvas-specific seam. Input still enters production
        // dispatchTouchEvent -> onTouchEvent -> the Fragment listener -> the game engine.
        fun geometry(name: String): Float = board.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.getFloat(board)
        val cellSize = geometry("cellSize")
        assertTrue("The drawn board must have a finite positive cell size", cellSize.isFinite() && cellSize > 0f)
        val x = geometry("originX") + (col + 0.5f) * cellSize
        val y = geometry("originY") + (row + 0.5f) * cellSize
        assertTrue("The cell center must fall inside the attached board",
            x.isFinite() && y.isFinite() && x >= 0f && x < board.width && y >= 0f && y < board.height)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        try {
            down.source = InputDevice.SOURCE_TOUCHSCREEN
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("The board must consume the real DOWN event", board.dispatchTouchEvent(down))
            assertEquals("Pressing down alone must not place a tower", 0,
                (gameProperty(fragment, "getTowers") as List<*>).size)
            assertTrue("The board must consume the real UP event", board.dispatchTouchEvent(up))
        } finally {
            down.recycle()
            up.recycle()
        }

        val game = requireNotNull(fragmentField(fragment, "game"))
        val tower = game.javaClass.getMethod("getTowerAt", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).invoke(game, row, col)
        assertNotNull("The production touch chain must place a tower at ($row,$col)", tower)
        val placed = requireNotNull(tower)
        val towers = gameProperty(fragment, "getTowers") as List<*>
        assertEquals("A single tap must place exactly one tower", 1, towers.size)
        assertTrue("The listed tower must be the one at the approved cell", towers.single() === placed)
        assertEquals("The placed tower must use the selected card", selected,
            placed.javaClass.getField("type").get(placed))
        assertEquals("Placement must deduct exactly the selected tower's base cost", coinBefore - baseCost,
            gameProperty(fragment, "getCoin"))
        assertEquals("Touch placement must leave the game in preparation", "PREPARING",
            gameProperty(fragment, "getState").toString())
        Log.i(TAG, "Touch placed ${selected} at ($row,$col), local center=($x,$y), " +
            "cellSize=$cellSize, coins=$coinBefore->${coinBefore - baseCost}")
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
            scenario.onActivity {
                assertTrue("The same screenshot target must still be attached and visible: $label",
                    target?.let { it.isAttachedToWindow && it.isShown && !it.isLayoutRequested
                        && it.rootView === root } == true)
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

    private fun tdFragment(activity: DynamicGameActivity): Fragment {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("DynamicGameActivity must attach the real TD Fragment", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.td.TdModuleFragment", it.javaClass.name)
            assertTrue("The dynamic Fragment view must be visible", it.requireView().isShown)
        }
    }

    private fun clickButton(root: View, label: String) {
        val buttons = descendants(root).filterIsInstance<Button>()
            .filter { it.text.toString() == label && it.isShown && it.isEnabled }.toList()
        assertEquals("Expected one visible enabled control: $label", 1, buttons.size)
        assertTrue("The control must have a real click listener: $label", buttons.single().performClick())
    }

    private fun assertStory(root: View, expectedStories: Set<String>) {
        val story = root.findViewWithTag<TextView>("td_story_intro")
        assertNotNull("Starting the selected deck must retain the selected level's story overlay", story)
        assertTrue("The story overlay must be visible", story.isShown)
        assertTrue("The story must come from the verified shipped TD content",
            story.text.toString() in expectedStories)
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(descendants(root.getChildAt(index)))
        }
    }

    // Read-only reflection is needed because TD classes intentionally are not compiled
    // into the host/test APK. User actions above still go through the actual UI listeners.
    private fun fragmentField(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun gameProperty(fragment: Fragment, name: String): Any {
        val game = requireNotNull(fragmentField(fragment, "game")) { "TD engine was not created" }
        return requireNotNull(game.javaClass.getMethod(name).invoke(game))
    }

    private fun withShippedPackage(action: (ModuleManifest, File) -> Unit) {
        val modules = context.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("modules")
        }
        val records = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { it.optString("id") == MODULE_ID }
        assertEquals("The shipped catalog must identify TD exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals("com.gamecenter.app.td.TdModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped TD must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped TD must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-td-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("TD APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("TD APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("TD APK must pass production publisher-certificate pinning",
            ModuleSignatureVerifier.verify(apk, context) is ModuleSignatureVerifier.Result.Success)
    }

    private fun sha256(file: File): String = com.gamecenter.app.modules.ModuleVerifier.computeSha256(file)

    private fun levelStories(apk: File, chapterId: String, levelId: String): Set<String> = ZipFile(apk).use { zip ->
        val entry = requireNotNull(zip.getEntry("assets/td/chapters/$chapterId.json"))
        val levels = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("levels")
        }
        val level = (0 until levels.length()).map { levels.getJSONObject(it) }
            .single { it.getString("id") == levelId }
        setOf(level.getString("story_intro"), level.getString("story_intro_en")).also {
            assertTrue("Shipped level $levelId must contain non-empty stories", it.all(String::isNotBlank))
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
            throw AssertionError("Failed to restore TD preferences after device verification").apply {
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
        assertTrue("Restore TD save preferences: ${snapshot.name}", editor.commit())
        assertEquals("TD user saves must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "td"
        private const val TAG = "ShippedTdModuleTest"
        private const val UNLOCKED_LEVELS_KEY = "td_unlocked_levels"
    }
}

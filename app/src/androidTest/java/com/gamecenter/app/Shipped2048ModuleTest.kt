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
 * Verifies the real APK embedded in the installed host, using module ID game_2048 and
 * game/save/usage ID 2048. The near-terminal board is an explicit SaveManager fixture;
 * this test does NOT claim to have naturally played from a new game to this position.
 * Thereafter only real UiAutomation touchscreen input changes gameplay. Reflection only
 * reads the actual external Fragment/engine; no direct move/reset/restore engine calls.
 * Original save/progress preferences and the exact 2048 Room usage row are restored.
 */
@RunWith(AndroidJUnit4::class)
class Shipped2048ModuleTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shipped2048PackagePassesIntegrityAndPinnedSignature() {
        withShippedPackage { manifest, apk -> verifyPackage(manifest, apk) }
    }

    @Test
    fun shipped2048RealSwipePersistsTerminalAndRestartAcrossRelaunch() {
        withShippedPackage { manifest, apk ->
            val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // SaveManager and GameUsageStore currently use application-context flat prefs.
            // Include the module namespace too, preserving any existing scoped/legacy state.
            val prefsBefore = listOf(
                capturePreferences("GameMatrix_saves"),
                capturePreferences(ModuleScopedPreferences.scopedName(MODULE_ID, "GameMatrix_saves")),
                capturePreferences("game_usage"),
                capturePreferences(ModuleScopedPreferences.scopedName(MODULE_ID, "game_usage"))
            )
            val database = AppDatabase.getDatabase(context.applicationContext)
            val usageBefore = database.gameUsageDao().getByIdSync(GAME_ID)?.copy()
            var scenario: ActivityScenario<DynamicGameActivity>? = null
            try {
                ensureShipped(manifest, apk)
                val saveManager = SaveManager.getInstance(context)
                val fixture = JSONObject().put("board", FIXTURE_BOARD)
                    .put("score", 100).put("gameOver", false).toString()
                saveManager.save(GAME_ID, "auto", fixture)
                assertEquals("The only gameplay fixture must enter through public SaveManager",
                    fixture, saveManager.load(GAME_ID, "auto"))
                Log.i(TAG, "Explicit near-terminal SaveManager fixture (not natural play): $fixture")

                val first = launchGame()
                scenario = first
                first.onActivity { activity ->
                    val fragment = gameFragment(activity)
                    assertSame("The real module must read the host SaveManager fixture",
                        saveManager, fragmentField(fragment, "saveManager"))
                    assertEquals(GameState(FIXTURE_BOARD.split(',').map(String::toInt), 100, false),
                        state(fragment))
                    assertScoreDisplay(activity, fragment)
                }
                captureScreen("game2048-fixture-before-swipe", first, ::boardView)
                swipeBoardRight(first)
                val terminal = readState(first)
                assertTrue("The actual right swipe must reach terminal state", terminal.gameOver)
                assertEquals("This non-merging swipe cannot change score", 100, terminal.score)
                assertEquals("The right swipe cannot alter the first three full rows",
                    FIXTURE_BOARD.split(',').take(12).map(String::toInt), terminal.tiles.take(12))
                assertEquals(listOf(8, 16, 32), terminal.tiles.takeLast(3))
                assertTrue("The sole empty cell must receive a real random 2 or 4",
                    terminal.tiles[12] == 2 || terminal.tiles[12] == 4)
                assertTrue("The resulting board must be full", terminal.tiles.all { it > 0 })
                captureScreen("game2048-terminal", first, ::boardView)
                assertTrue("The real terminal handler must record the score in host Room",
                    requireNotNull(database.gameUsageDao().getByIdSync(GAME_ID)).highScore >= 100L)
                first.close()
                scenario = null

                val reopened = launchGame()
                scenario = reopened
                // Capture the actual reopened frame even on the old package's expected failure.
                captureScreen("game2048-terminal-reopened", reopened, ::boardView)
                assertEquals("Closing and reopening must preserve the full terminal board and flag",
                    terminal, readState(reopened))
                touchRestartButton(reopened)
                val restarted = readState(reopened)
                assertEquals("A real restart must reset the visible score", 0, restarted.score)
                assertFalse("The restarted game must accept moves", restarted.gameOver)
                assertEquals("A real restart must produce exactly two initial tiles", 2,
                    restarted.tiles.count { it != 0 })
                assertTrue(restarted.tiles.filter { it != 0 }.all { it == 2 || it == 4 })
                captureScreen("game2048-restarted", reopened, ::boardView)
                reopened.close()
                scenario = null

                val restartedAgain = launchGame()
                scenario = restartedAgain
                assertEquals("Reopening must preserve the newly restarted board, not the old terminal save",
                    restarted, readState(restartedAgain))
                captureScreen("game2048-restarted-reopened", restartedAgain, ::boardView)
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

    private fun launchGame(): ActivityScenario<DynamicGameActivity> = ActivityScenario.launch(
        Intent(context, DynamicGameActivity::class.java)
            .putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    private data class GameState(val tiles: List<Int>, val score: Int, val gameOver: Boolean)

    private fun state(fragment: Fragment): GameState {
        val game = requireNotNull(fragmentField(fragment, "game"))
        assertEquals("com.gamecenter.app.game2048.Game2048Game", game.javaClass.name)
        assertSame(fragment.javaClass.classLoader, game.javaClass.classLoader)
        val board = game.javaClass.getMethod("getBoardSnapshot").invoke(game) as Array<*>
        assertEquals(4, board.size)
        val tiles = board.flatMap { row ->
            val cells = row as IntArray
            assertEquals(4, cells.size)
            cells.toList()
        }
        return GameState(tiles, game.javaClass.getMethod("getScore").invoke(game) as Int,
            game.javaClass.getMethod("isGameOver").invoke(game) as Boolean)
    }

    private fun readState(scenario: ActivityScenario<DynamicGameActivity>): GameState {
        lateinit var result: GameState
        scenario.onActivity { activity ->
            val fragment = gameFragment(activity)
            result = state(fragment)
            assertScoreDisplay(activity, fragment)
        }
        return result
    }

    private fun assertScoreDisplay(activity: DynamicGameActivity, fragment: Fragment) {
        val display = fragmentField(fragment, "tvScore") as TextView
        assertTrue("The real score view must be visible", display.isShown)
        assertEquals(activity.getString(R.string.game_score_alt_format, state(fragment).score),
            display.text.toString())
    }

    private fun gameFragment(activity: DynamicGameActivity): Fragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        assertNotNull("DynamicGameActivity must attach the actual external 2048 Fragment", fragment)
        return requireNotNull(fragment).also {
            assertEquals("com.gamecenter.app.game2048.Game2048Fragment", it.javaClass.name)
            assertFalse("2048 must not come from host classes", it.javaClass.classLoader === context.classLoader)
            assertTrue("2048 must use the verified module ClassLoader",
                it.javaClass.classLoader === ModuleLoader.getClassLoader(MODULE_ID))
            assertTrue(it.requireView().isShown)
        }
    }

    private fun boardView(activity: DynamicGameActivity): View =
        (fragmentField(gameFragment(activity), "game2048View") as View).also {
            assertEquals("com.gamecenter.app.game2048.Game2048View", it.javaClass.name)
        }

    private fun fragmentField(fragment: Fragment, name: String): Any? =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    private fun swipeBoardRight(scenario: ActivityScenario<DynamicGameActivity>) {
        assertFalse("Input injection must never block the Activity main thread",
            Looper.myLooper() === Looper.getMainLooper())
        lateinit var bounds: Rect
        scenario.onActivity { bounds = fullyVisibleScreenBounds(boardView(it)) }
        assertTrue("The real board must have room for an unambiguous horizontal fling", bounds.width() > 180)
        val startX = bounds.left + bounds.width() * 0.2f
        val endX = bounds.left + bounds.width() * 0.8f
        val y = bounds.exactCenterY()
        val downTime = SystemClock.uptimeMillis()
        injectTouch(downTime, MotionEvent.ACTION_DOWN, startX, y)
        for (step in 1..8) {
            SystemClock.sleep(16L)
            injectTouch(downTime, MotionEvent.ACTION_MOVE, startX + (endX - startX) * step / 8f, y)
        }
        SystemClock.sleep(16L)
        injectTouch(downTime, MotionEvent.ACTION_UP, endX, y)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun touchRestartButton(scenario: ActivityScenario<DynamicGameActivity>) {
        assertFalse("Input injection must never block the Activity main thread",
            Looper.myLooper() === Looper.getMainLooper())
        lateinit var bounds: Rect
        scenario.onActivity { activity ->
            val label = activity.getString(R.string.game_btn_restart)
            val buttons = descendants(gameFragment(activity).requireView()).filterIsInstance<Button>()
                .filter { it.text.toString() == label && it.isShown }.toList()
            assertEquals("Exactly one real restart Button must be visible", 1, buttons.size)
            val button = buttons.single()
            assertTrue(button.isEnabled && button.isClickable)
            assertTrue("Restart must have a full 48dp touch target",
                button.height >= kotlin.math.ceil(48.0 * button.resources.displayMetrics.density).toInt())
            bounds = fullyVisibleScreenBounds(button)
        }
        val downTime = SystemClock.uptimeMillis()
        injectTouch(downTime, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        SystemClock.sleep(64L)
        injectTouch(downTime, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun injectTouch(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
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
                assertEquals("2048 Room usage must match the exact original row or absence",
                    rowBefore, database.gameUsageDao().getByIdSync(GAME_ID))
            }.exceptionOrNull()
        ).filterNotNull()
        if (failures.isNotEmpty()) {
            throw AssertionError("Failed to restore 2048 user data after device verification").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }

    private fun ensureShipped(manifest: ModuleManifest, apk: File) {
        verifyPackage(manifest, apk)
        val previousCatalog = ModuleManager.getModuleManifest(MODULE_ID)
        val installedVersion = ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        assertTrue("This test must not downgrade a newer installed 2048 package",
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
            assertTrue("Shipped 2048 must install through the production transaction",
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

    private fun withShippedPackage(action: (ModuleManifest, File) -> Unit) {
        // Instrumentation enters directly, without Splash's context-aware catalog bootstrap.
        // Do this before any context-free manifest/version lookup can seed a VPN-only cache.
        ModuleManager.registerLocalFallbackIfNeeded(context)
        val modules = context.assets.open("modules.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText()).getJSONArray("modules")
        }
        val records = (0 until modules.length()).map { modules.getJSONObject(it) }
            .filter { it.optString("id") == MODULE_ID }
        assertEquals("The shipped catalog must identify 2048 exactly once", 1, records.size)
        val manifest = ModuleManifest.fromJson(records.single())
        assertEquals("com.gamecenter.app.game2048.Game2048ModuleEntryPoint", manifest.entryClass)
        assertTrue("Shipped 2048 must remain an external APK", manifest.fileName.endsWith(".apk"))
        assertEquals(File(manifest.fileName).name, manifest.fileName)
        assertTrue("Shipped 2048 must specify SHA-256 and a positive size",
            manifest.sha256.length == 64 && manifest.fileSize > 0L)
        val apk = File.createTempFile("shipped-2048-instrumentation-", ".apk", context.cacheDir)
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
        assertEquals("2048 APK size must match its shipped manifest", manifest.fileSize, apk.length())
        assertTrue("2048 APK must pass the production SHA/size verifier",
            ModuleVerifier.verify(apk, manifest.sha256, manifest.fileSize).isSuccess)
        assertTrue("2048 APK must pass production publisher-certificate pinning",
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
            throw AssertionError("Failed to restore 2048 preferences after device verification").apply {
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
        assertTrue("Restore 2048 save preferences: ${snapshot.name}", editor.commit())
        assertEquals("2048 user saves must match their pre-test state: ${snapshot.name}",
            snapshot.values, preferenceValues(prefs))
    }

    companion object {
        private const val MODULE_ID = "game_2048"
        private const val GAME_ID = "2048"
        private const val TAG = "Shipped2048ModuleTest"
        private const val FIXTURE_BOARD = "2,4,8,16,32,64,128,256,512,1024,2,4,8,16,32,0"
    }
}

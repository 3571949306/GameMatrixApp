package com.gamecenter.app.modules.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.modules.ModuleDownloader
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.CatalogPackage
import com.gamecenter.app.modules.catalog.CatalogV2
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.runtime.SecureArchiveInstaller
import com.gamecenter.app.modules.runtime.WebRuntimeHandler
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

/** Exercises the public facade retry and its real worker -> main-handler completion chain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ModuleCoreFacadeArchiveRecoveryTest {
    private lateinit var context: Context
    private lateinit var facade: ModuleCoreFacade
    private val events = CopyOnWriteArrayList<ModuleCoreEvent>()
    private val observer: (ModuleCoreEvent) -> Unit = { event ->
        if (event.moduleId == MODULE_ID) events.add(event)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearOwnState()
        // Isolate this test's worker/catalog state; do not replace the application singleton.
        facade = ModuleCoreFacade::class.java.getDeclaredConstructor(Context::class.java).apply {
            isAccessible = true
        }.newInstance(context)
        ModuleEventBus.addObserver(observer)
    }

    @After
    fun tearDown() {
        ModuleEventBus.removeObserver(observer)
        if (::facade.isInitialized) {
            runtimeExecutor().shutdownNow()
            runtimeExecutor().awaitTermination(10, TimeUnit.SECONDS)
        }
        ShadowLooper.idleMainLooper()
        clearOwnState()
    }

    @Test
    fun `failed same package retry preserves both outer and runtime generations`() {
        val versionOne = installFixtureVersion(1, CONTENT_V1)
        val versionTwo = installFixtureVersion(2, CONTENT_V2)
        val oldManifest = versionOne.legacyManifest!!
        val currentManifest = versionTwo.legacyManifest!!
        val outerCurrent = TransactionInstaller.getCurrentFile(context, currentManifest)
        val outerLastGood = TransactionInstaller.getLastGoodFile(context, oldManifest)
        assertEquals(currentManifest.sha256, sha256(outerCurrent))
        assertEquals(oldManifest.sha256, sha256(outerLastGood))
        assertEquals(2, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertTrue(ModuleManager.hasRollback(context, MODULE_ID))

        // Only catalog input is seeded. The action below uses public installModule, not a
        // reflected facade callback, mocked handler, or direct recovery-helper invocation.
        ModuleCoreFacade::class.java.getDeclaredMethod("rememberCatalog", CatalogV2::class.java).apply {
            isAccessible = true
        }.invoke(facade, CatalogV2(
            catalogVersion = 2, source = "test-local-fixture", offline = true,
            modules = listOf(versionTwo)
        ))

        val root = File(context.filesDir, "modules/runtime/$MODULE_ID")
        val runtimeCurrent = File(root, "current")
        val runtimeLastGood = File(root, "last_good")
        assertEquals(CONTENT_V2, File(runtimeCurrent, ENTRY).readText())
        assertEquals(CONTENT_V1, File(runtimeLastGood, ENTRY).readText())

        // Keep current healthy. An extra backup entry requires recalibration from the
        // verified v1 ZIP; a file at quarantine blocks activation before any runtime move.
        // Windows does not promise a true chmod-style return for directories. The
        // following real write, and its tree digest, establish the fixture instead.
        runtimeLastGood.setWritable(true, false)
        File(runtimeLastGood, "unexpected-backup-entry.txt").writeText("requires-backup-repair")
        runtimeLastGood.setReadOnly()
        val quarantine = File(root, "quarantine")
        quarantine.walkBottomUp().forEach { it.setWritable(true, false) }
        assertTrue("Only this test module's old runtime quarantine is removed", quarantine.deleteRecursively())
        quarantine.writeText("a file cannot serve as an activation directory")
        val currentTreeBefore = treeDigest(runtimeCurrent)
        val lastGoodTreeBefore = treeDigest(runtimeLastGood)
        val currentSnapshotBefore = snapshot("module_current_manifest_")
        val lastGoodSnapshotBefore = snapshot("module_last_good_manifest_")
        assertNotNull(currentSnapshotBefore)
        assertNotNull(lastGoodSnapshotBefore)

        val accepted = facade.installModule(MODULE_ID)
        assertTrue("The public facade should accept the asynchronous retry", accepted.success)
        // Same-SHA downloadModule completes inline, queues real runtime work, then returns.
        // A FIFO barrier waits for that work; main-looper drain delivers its actual event.
        runtimeExecutor().submit {}.get(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        val terminal = events.filter { it.eventType in setOf("InstallFailed", "InstallCompleted") }
        assertEquals("Exactly one terminal event must arrive from the real callback chain", 1, terminal.size)
        assertEquals("InstallFailed", terminal.single().eventType)
        assertEquals("The deterministic activation blocker must be the failure cause",
            "atomic_switch_failed", terminal.single().error?.errorCode)
        assertEquals(ModuleState.FAILED, facade.progress(MODULE_ID).state)
        assertFalse("The verified fast path must not download another outer package",
            events.any { it.eventType == "DownloadProgress" || it.eventType == "DownloadStarted" })
        assertEquals("A failed repair must leave the healthy current tree unchanged",
            currentTreeBefore, treeDigest(runtimeCurrent))
        assertEquals("A failed repair must preserve the pre-request backup tree",
            lastGoodTreeBefore, treeDigest(runtimeLastGood))

        // The old failure callback rolls outer v2 back to v1 here, although no new outer
        // transaction occurred and runtime current is still healthy v2.
        assertTrue("Failed same-package retry must not roll healthy outer v2 back to v1", outerCurrent.isFile)
        assertEquals(currentManifest.sha256, sha256(outerCurrent))
        assertEquals(oldManifest.sha256, sha256(outerLastGood))
        assertEquals(2, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertEquals(currentSnapshotBefore, snapshot("module_current_manifest_"))
        assertEquals(lastGoodSnapshotBefore, snapshot("module_last_good_manifest_"))
        assertTrue("Retry failure must retain the original rollback capability", ModuleManager.hasRollback(context, MODULE_ID))
    }

    @Test
    fun `new outer commit still rolls back when runtime activation fails`() {
        val original = installFixtureVersion(1, CONTENT_V1)
        val (replacement, staged) = stageVersion(2, CONTENT_V2)
        val root = File(context.filesDir, "modules/runtime/$MODULE_ID")
        File(root, "quarantine").writeText("block activation before moving the healthy v1 runtime")
        val originalTree = treeDigest(File(root, "current"))
        // Omit network transfer only. The production post-download transaction itself
        // delivers installing -> complete to the real facade callback on the main Handler.
        val callback = ModuleCoreFacade::class.java.getDeclaredMethod(
            "createDownloadCallback", CatalogModule::class.java
        ).apply { isAccessible = true }.invoke(facade, replacement) as ModuleDownloader.Callback
        @Suppress("UNCHECKED_CAST")
        val callbacks = ModuleManager::class.java.getDeclaredField("downloadCallbacks").apply {
            isAccessible = true
        }.get(ModuleManager) as MutableMap<String, ModuleDownloader.Callback>
        callbacks[MODULE_ID] = callback
        commitOuter(replacement, staged)
        ShadowLooper.idleMainLooper()
        runtimeExecutor().submit {}.get(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        assertEquals("atomic_switch_failed", events.single { it.eventType == "InstallFailed" }.error?.errorCode)
        assertTrue("The real transaction must emit its installing phase",
            events.any { it.eventType == "InstallStarted" })
        assertEquals(originalTree, treeDigest(File(root, "current")))
        assertEquals(1, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertEquals(original.legacyManifest!!.sha256,
            sha256(TransactionInstaller.getCurrentFile(context, original.legacyManifest!!)))
        assertFalse(TransactionInstaller.getCurrentFile(context, replacement.legacyManifest!!).exists())
        assertFalse(ModuleManager.hasRollback(context, MODULE_ID))
    }

    @Test
    fun `same package fast path still rejects an archive with no entry`() {
        val original = installFixtureVersion(1, CONTENT_V1)
        val (invalid, staged) = stageVersion(2, CONTENT_V2, "not-the-entry.txt")
        commitOuter(invalid, staged)
        // Simulate interruption after outer commit but before runtime validation.
        ModuleCoreFacade::class.java.getDeclaredMethod("rememberCatalog", CatalogV2::class.java).apply {
            isAccessible = true
        }.invoke(facade, CatalogV2(catalogVersion = 2, source = "test-local-fixture",
            offline = true, modules = listOf(invalid)))
        val current = File(context.filesDir, "modules/runtime/$MODULE_ID/current")
        val originalTree = treeDigest(current)
        assertTrue(facade.installModule(MODULE_ID).success)
        runtimeExecutor().submit {}.get(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()

        assertEquals("entry_missing", events.single { it.eventType == "InstallFailed" }.error?.errorCode)
        assertEquals(originalTree, treeDigest(current))
        assertEquals(1, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertEquals(original.legacyManifest!!.sha256,
            sha256(TransactionInstaller.getCurrentFile(context, original.legacyManifest!!)))
        assertFalse(TransactionInstaller.getCurrentFile(context, invalid.legacyManifest!!).exists())
    }

    /** Real production outer transaction/snapshot setup; only network transfer is omitted. */
    private fun installFixtureVersion(version: Int, content: String): CatalogModule {
        val (module, staged) = stageVersion(version, content)
        commitOuter(module, staged)
        val installed = WebRuntimeHandler().install(context, module)
        assertTrue("The real runtime fixture must install: $installed", installed.success)
        return module
    }

    private fun stageVersion(version: Int, content: String, entry: String = ENTRY): Pair<CatalogModule, File> {
        val initial = ModuleManifest(
            id = MODULE_ID, name = "Facade archive retry regression",
            versionCode = version, versionName = "1.$version.0",
            fileName = "${MODULE_ID}_v$version.zip", kind = "web-zip", rollbackAllowed = true
        ).let { ModuleManifest.fromJson(it.toJson()) }
        val staging = TransactionInstaller.getStagingFile(context, initial)
        ZipOutputStream(staging.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entry).apply { time = 0L })
            output.write(content.toByteArray(Charsets.UTF_8))
            output.closeEntry()
        }
        val manifest = initial.copy(fileSize = staging.length(), sha256 = sha256(staging))
        val module = CatalogModule(
            id = MODULE_ID, name = manifest.name, versionCode = version,
            versionName = manifest.versionName, runtimeType = RuntimeType.WEB,
            deliveryType = DeliveryType.ZIP, entry = ENTRY,
            packageInfo = CatalogPackage(
                fileName = manifest.fileName, fileSize = manifest.fileSize, sha256 = manifest.sha256
            ),
            legacyManifest = manifest
        )
        return module to staging
    }

    private fun commitOuter(module: CatalogModule, staging: File) {
        val manifest = module.legacyManifest!!
        ModuleManager.registerAvailableManifests(listOf(manifest))
        ModuleManager::class.java.getDeclaredMethod(
            "installDownloadedModule", Context::class.java, String::class.java,
            ModuleManifest::class.java, File::class.java
        ).apply { isAccessible = true }.invoke(ModuleManager, context, MODULE_ID, manifest, staging)
        assertFalse("Production outer install must consume staging", staging.exists())
        assertEquals(manifest.sha256, sha256(TransactionInstaller.getCurrentFile(context, manifest)))
    }

    private fun runtimeExecutor(): ExecutorService =
        ModuleCoreFacade::class.java.getDeclaredField("runtimeExecutor").apply {
            isAccessible = true
        }.get(facade) as ExecutorService

    private fun snapshot(prefix: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefix + MODULE_ID, null)

    private fun treeDigest(root: File): Map<String, String> = root.walkTopDown().associate { file ->
        file.relativeTo(root).invariantSeparatorsPath to if (file.isDirectory) "directory" else sha256(file)
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun clearOwnState() {
        SecureArchiveInstaller.uninstall(context, MODULE_ID)
        listOf(
            TransactionInstaller.getCurrentDir(context), TransactionInstaller.getLastGoodDir(context),
            TransactionInstaller.getStagingDir(context), TransactionInstaller.getQuarantineDir(context)
        ).forEach { directory ->
            directory.listFiles().orEmpty().filter { it.name.startsWith("${MODULE_ID}_") }.forEach { file ->
                file.walkBottomUp().forEach { it.setWritable(true, false) }
                file.deleteRecursively()
            }
        }
        ModuleManager.removeInstalledModulePublic(context, MODULE_ID)
        @Suppress("UNCHECKED_CAST")
        val manifests = ModuleManager::class.java.getDeclaredField("manifests").apply {
            isAccessible = true
        }.get(ModuleManager) as MutableMap<String, ModuleManifest>
        manifests.remove(MODULE_ID)
        ModuleManager.invalidateInstalledCache()
    }

    companion object {
        private const val MODULE_ID = "facade_archive_retry_regression"
        private const val PREFS = "module_manager_prefs"
        private const val ENTRY = "index.html"
        private const val CONTENT_V1 = "<html>healthy-previous-v1</html>"
        private const val CONTENT_V2 = "<html>healthy-current-v2</html>"
    }
}

package com.gamecenter.app.modules.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.CatalogPackage
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reusing a verified outer package must not consume its matching runtime rollback version. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureArchiveInstallerIdempotencyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private val handler = WebRuntimeHandler()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearOwnState()
    }

    @After
    fun tearDown() {
        clearOwnState()
    }

    @Test
    fun `reinstalling current web package preserves matching outer and runtime rollback`() {
        val versionOne = installVersion(1, CONTENT_V1)
        val versionTwo = installVersion(2, CONTENT_V2)
        val oldManifest = versionOne.legacyManifest!!
        val newManifest = versionTwo.legacyManifest!!
        assertRuntimeVersions(CONTENT_V2, CONTENT_V1)
        assertOuterCurrent(newManifest)
        assertOuterLastGood(oldManifest)
        assertTrue("The real outer installation must provide rollback", ModuleManager.hasRollback(context, MODULE_ID))
        val quarantine = File(context.filesDir, "modules/runtime/$MODULE_ID/quarantine")
        val quarantineBefore = quarantine.listFiles().orEmpty().map { it.name }.toSet()

        // This is the runtime call performed by ModuleCoreFacade after ModuleManager's
        // already-installed fast path. The outer transaction is intentionally not repeated.
        repeat(2) {
            val repeated = handler.install(context, versionTwo)
            assertTrue("Reinstalling the same valid package should succeed: $repeated", repeated.success)
            assertEquals("Healthy reinstall must not accumulate copies in quarantine",
                quarantineBefore, quarantine.listFiles().orEmpty().map { it.name }.toSet())
            assertRuntimeVersions(CONTENT_V2, CONTENT_V1)
        }
        assertOuterCurrent(newManifest)
        assertOuterLastGood(oldManifest)

        // Exercise the real WebRuntimeHandler -> runtime rollback -> ModuleManager rollback
        // callback, with SHA-verified v1/v2 ZIPs and snapshots produced by actual installs.
        val rollback = handler.rollback(context, versionTwo)
        assertTrue("The real two-layer rollback should succeed: $rollback", rollback.success)
        assertOuterCurrent(oldManifest)
        assertFalse("The v2 filename must no longer be current",
            TransactionInstaller.getCurrentFile(context, newManifest).exists())
        assertFalse("A successful rollback consumes its metadata", ModuleManager.hasRollback(context, MODULE_ID))
        assertEquals("Runtime content must agree with the restored v1 ZIP and manifest",
            CONTENT_V1, runtimeEntry("current").readText())
    }

    @Test
    fun `reinstalling same package repairs corrupted runtime without replacing last good`() {
        val versionOne = installVersion(1, CONTENT_V1)
        val versionTwo = installVersion(2, CONTENT_V2)
        val oldManifest = versionOne.legacyManifest!!
        val newManifest = versionTwo.legacyManifest!!
        assertRuntimeVersions(CONTENT_V2, CONTENT_V1)
        assertOuterLastGood(oldManifest)

        val currentEntry = runtimeEntry("current")
        val originalBytes = currentEntry.readBytes()
        val damagedBytes = originalBytes.copyOf().apply { this[0] = '!'.code.toByte() }
        assertTrue("Fixture must make the installed content writable", currentEntry.setWritable(true, false))
        currentEntry.writeBytes(damagedBytes)
        assertEquals("Corruption keeps the file size unchanged", originalBytes.size.toLong(), currentEntry.length())
        assertFalse("Fixture must actually change runtime content", originalBytes.contentEquals(currentEntry.readBytes()))
        assertOuterCurrent(newManifest) // ZIP size and SHA are still the original valid v2.

        val repaired = handler.install(context, versionTwo)

        assertTrue("An intact package should repair its damaged extracted content: $repaired", repaired.success)
        assertEquals("A package identity match alone must not skip repairing current",
            CONTENT_V2, runtimeEntry("current").readText())
        assertOuterCurrent(newManifest)
        assertOuterLastGood(oldManifest)
        assertEquals("Repair must preserve v1, not promote the damaged v2 to last_good",
            CONTENT_V1, runtimeEntry("last_good").readText())
        assertTrue("Repair must retain the real outer rollback state", ModuleManager.hasRollback(context, MODULE_ID))
    }

    @Test
    fun `same version with different package SHA still installs and rolls back matching content`() {
        val original = installVersion(1, CONTENT_V1)
        val replacement = installVersion(1, CONTENT_V2)
        assertFalse("Fixture must change the package identity without changing versionCode",
            original.legacyManifest!!.sha256 == replacement.legacyManifest!!.sha256)
        assertOuterCurrent(replacement.legacyManifest!!)
        assertOuterLastGood(original.legacyManifest!!)
        assertRuntimeVersions(CONTENT_V2, CONTENT_V1)

        val rollback = handler.rollback(context, replacement)
        assertTrue("Same-version content replacement must retain real rollback: $rollback", rollback.success)
        assertOuterCurrent(original.legacyManifest!!)
        assertEquals(CONTENT_V1, runtimeEntry("current").readText())
    }

    @Test
    fun `each activation rename failure restores both original runtime generations`() {
        for (failedMove in 1..4) {
            val root = temporaryFolder.newFolder("activation-failure-$failedMove")
            val current = File(root, "current").apply { mkdirs() }
            val lastGood = File(root, "last_good").apply { mkdirs() }
            val staging = File(root, "staging").apply { mkdirs() }
            val preparedLastGood = File(root, "last-good-staging").apply { mkdirs() }
            File(current, ENTRY).writeText("original-current")
            File(lastGood, ENTRY).writeText("original-last-good")
            File(staging, ENTRY).writeText("prepared-new-current")
            File(preparedLastGood, ENTRY).writeText("prepared-verified-backup")
            var moves = 0
            try {
                val activated = SecureArchiveInstaller.activatePreparedRuntime(
                    root, staging, preparedLastGood
                ) { source, target ->
                    moves++
                    if (moves == failedMove) false else source.renameTo(target)
                }

                assertFalse("Failure at rename $failedMove cannot report successful activation", activated)
                assertTrue("The requested failure point must be reached", moves >= failedMove)
                assertEquals("rename $failedMove must restore the original current",
                    "original-current", File(current, ENTRY).readText())
                assertEquals("rename $failedMove must restore the original last_good",
                    "original-last-good", File(lastGood, ENTRY).readText())
                assertEquals("Prepared new content must remain available after compensation",
                    "prepared-new-current", File(staging, ENTRY).readText())
                assertEquals("Prepared backup must remain available after compensation",
                    "prepared-verified-backup", File(preparedLastGood, ENTRY).readText())
                assertFalse("Restored current must not also remain in quarantine",
                    File(root, "quarantine").walkTopDown().any { it.isFile })
                assertFalse("Restored last_good must not leave an abandoned temporary generation",
                    root.listFiles().orEmpty().any { it.name.startsWith(".last-good-previous-") })
            } finally {
                root.walkBottomUp().forEach { it.setWritable(true, false) }
            }
        }
    }

    /**
     * Runs the production post-download entry, including TransactionInstaller and snapshot
     * commits. Only the network transfer is replaced with a locally created, genuine ZIP.
     */
    private fun installVersion(version: Int, content: String): CatalogModule {
        val initialManifest = ModuleManifest(
            id = MODULE_ID,
            name = "Archive idempotency test",
            versionName = "1.$version.0",
            versionCode = version,
            fileName = "${MODULE_ID}_v$version.zip",
            kind = "web-zip",
            rollbackAllowed = true
        ).let { ModuleManifest.fromJson(it.toJson()) }
        val staged = TransactionInstaller.getStagingFile(context, initialManifest)
        staged.parentFile?.mkdirs()
        ZipOutputStream(staged.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(ENTRY).apply { time = 0L })
            output.write(content.toByteArray(Charsets.UTF_8))
            output.closeEntry()
        }
        val manifest = initialManifest.copy(fileSize = staged.length(), sha256 = sha256(staged))
        ModuleManager.registerAvailableManifests(listOf(manifest))
        ModuleManager::class.java.getDeclaredMethod(
            "installDownloadedModule",
            Context::class.java,
            String::class.java,
            ModuleManifest::class.java,
            File::class.java
        ).apply {
            isAccessible = true
            invoke(ModuleManager, context, MODULE_ID, manifest, staged)
        }
        assertFalse("The outer transaction must consume staging", staged.exists())
        assertOuterCurrent(manifest)
        val module = CatalogModule(
            id = MODULE_ID,
            name = manifest.name,
            versionName = manifest.versionName,
            versionCode = version,
            runtimeType = RuntimeType.WEB,
            deliveryType = DeliveryType.ZIP,
            entry = ENTRY,
            packageInfo = CatalogPackage(
                fileName = manifest.fileName,
                fileSize = manifest.fileSize,
                sha256 = manifest.sha256
            ),
            legacyManifest = manifest
        )
        val installed = handler.install(context, module)
        assertTrue("Runtime fixture install must succeed: $installed", installed.success)
        assertEquals(content, runtimeEntry("current").readText())
        return module
    }

    private fun assertOuterCurrent(manifest: ModuleManifest) {
        val current = TransactionInstaller.getCurrentFile(context, manifest)
        assertTrue("The current outer ZIP must exist", current.isFile)
        assertEquals(manifest.sha256, sha256(current))
        assertEquals(manifest.fileSize, current.length())
        assertTrue("Outer installation must be registered", ModuleManager.isModuleInstalled(context, MODULE_ID))
        assertEquals(manifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertEquals(manifest, ModuleManager.getModuleManifest(MODULE_ID))
        assertEquals(manifest, snapshot("module_current_manifest_"))
    }

    private fun assertOuterLastGood(manifest: ModuleManifest) {
        val archive = TransactionInstaller.getLastGoodFile(context, manifest)
        assertTrue("The real outer transaction must retain the old ZIP", archive.isFile)
        assertEquals(manifest.sha256, sha256(archive))
        assertEquals(manifest.fileSize, archive.length())
        assertEquals(manifest, snapshot("module_last_good_manifest_"))
    }

    private fun snapshot(prefix: String): ModuleManifest {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(prefix + MODULE_ID, null)
        assertNotNull("Production install must write $prefix metadata", stored)
        return ModuleManifest.fromJson(JSONObject(stored!!))
    }

    private fun assertRuntimeVersions(current: String, lastGood: String) {
        assertEquals(current, runtimeEntry("current").readText())
        assertEquals(lastGood, runtimeEntry("last_good").readText())
    }

    private fun runtimeEntry(slot: String): File =
        File(context.filesDir, "modules/runtime/$MODULE_ID/$slot/$ENTRY")

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun clearOwnState() {
        SecureArchiveInstaller.uninstall(context, MODULE_ID)
        listOf(
            TransactionInstaller.getCurrentDir(context),
            TransactionInstaller.getLastGoodDir(context),
            TransactionInstaller.getStagingDir(context),
            TransactionInstaller.getQuarantineDir(context)
        ).forEach { directory ->
            directory.listFiles().orEmpty().filter { it.name.startsWith("${MODULE_ID}_") }
                .forEach { file ->
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
        private const val MODULE_ID = "archive_idempotency_regression"
        private const val PREFS = "module_manager_prefs"
        private const val ENTRY = "index.html"
        private const val CONTENT_V1 = "<html>runtime-version-one</html>"
        private const val CONTENT_V2 = "<html>runtime-version-two</html>"
    }
}

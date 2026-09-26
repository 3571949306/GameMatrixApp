package com.gamecenter.app.modules.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.ModuleManifest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Regression coverage for update failures that must not downgrade a healthy current. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleUpdateManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun downloadFailureDoesNotRollbackHealthyCurrent() {
        val oldBytes = "healthy-v1".toByteArray()
        val currentBytes = "healthy-v2".toByteArray()
        val failedUpdateBytes = "unused-v3".toByteArray()
        val oldManifest = manifest(1, oldBytes)
        val currentManifest = manifest(2, currentBytes)
        val failedUpdateManifest = manifest(3, failedUpdateBytes).copy(downloadUrl = "")

        TransactionInstaller.getCurrentFile(context, currentManifest).apply {
            parentFile?.mkdirs()
            writeBytes(currentBytes)
        }
        TransactionInstaller.getLastGoodFile(context, currentManifest).apply {
            parentFile?.mkdirs()
            writeBytes(oldBytes)
        }
        prefs.edit()
            .putStringSet("installed_modules", setOf(MODULE_ID))
            .putInt("module_version_$MODULE_ID", currentManifest.versionCode)
            .putInt("module_last_good_version_$MODULE_ID", oldManifest.versionCode)
            .putString("module_current_manifest_$MODULE_ID", currentManifest.toJson().toString())
            .putString("module_last_good_manifest_$MODULE_ID", oldManifest.toJson().toString())
            .commit()
        ModuleManager.registerAvailableManifests(listOf(failedUpdateManifest))
        ModuleManager.invalidateInstalledCache()

        val rollbackResult = AtomicReference<Boolean?>()
        val failure = AtomicReference<String?>()
        val updateResult = AtomicReference<ModuleUpdateManager.BatchUpdateResult?>()
        val worker = Thread {
            updateResult.set(
                ModuleUpdateManager.performBatchUpdate(
                    context,
                    listOf(
                        ModuleUpdateManager.UpdateCandidate(
                            moduleId = MODULE_ID,
                            installedVersion = currentManifest.versionCode,
                            availableVersion = failedUpdateManifest.versionCode,
                            manifest = failedUpdateManifest
                        )
                    ),
                    object : ModuleUpdateManager.UpdateCallback {
                        override fun onRollback(moduleId: String, success: Boolean) {
                            rollbackResult.set(success)
                        }

                        override fun onFailed(moduleId: String, reason: String) {
                            failure.set(reason)
                        }
                    }
                )
            )
        }
        worker.start()

        repeat(MAX_MAIN_LOOP_DRAINS) {
            ShadowLooper.idleMainLooper()
            if (!worker.isAlive) return@repeat
            Thread.sleep(MAIN_LOOP_SLEEP_MS)
        }
        worker.join(WORKER_JOIN_TIMEOUT_MS)

        assertFalse("更新线程应结束", worker.isAlive)
        assertEquals(listOf(MODULE_ID), updateResult.get()?.failed)
        assertEquals("下载失败时不应执行或上报回滚", null, rollbackResult.get())
        assertTrue("应报告更新失败", !failure.get().isNullOrBlank())
        assertEquals(
            String(currentBytes),
            TransactionInstaller.getCurrentFile(context, currentManifest).readText()
        )
        assertEquals(
            String(oldBytes),
            TransactionInstaller.getLastGoodFile(context, currentManifest).readText()
        )
        assertEquals(
            currentManifest.versionCode,
            prefs.getInt("module_version_$MODULE_ID", 0)
        )
    }

    private fun manifest(versionCode: Int, bytes: ByteArray) = ModuleManifest(
        id = MODULE_ID,
        name = "Update failure test module",
        versionCode = versionCode,
        fileName = "$MODULE_ID.zip",
        fileSize = bytes.size.toLong(),
        sha256 = sha256(bytes),
        rollbackAllowed = true
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun clearState() {
        listOf(
            TransactionInstaller.getCurrentDir(context),
            TransactionInstaller.getLastGoodDir(context),
            TransactionInstaller.getStagingDir(context),
            TransactionInstaller.getQuarantineDir(context)
        ).forEach { directory ->
            directory.listFiles().orEmpty()
                .filter { it.name.contains(MODULE_ID) }
                .forEach { file ->
                    file.walkBottomUp().forEach { it.setWritable(true, false) }
                    file.deleteRecursively()
                }
        }
        val installed = prefs.getStringSet("installed_modules", emptySet()).orEmpty().toMutableSet()
        installed.remove(MODULE_ID)
        prefs.edit()
            .putStringSet("installed_modules", installed)
            .remove("module_version_$MODULE_ID")
            .remove("module_last_good_version_$MODULE_ID")
            .remove("module_current_manifest_$MODULE_ID")
            .remove("module_last_good_manifest_$MODULE_ID")
            .commit()
        ModuleManager.invalidateInstalledCache()
    }

    companion object {
        private const val MODULE_ID = "update_failure_protocol_mod"
        private const val MAX_MAIN_LOOP_DRAINS = 100
        private const val MAIN_LOOP_SLEEP_MS = 10L
        private const val WORKER_JOIN_TIMEOUT_MS = 1000L
    }
}

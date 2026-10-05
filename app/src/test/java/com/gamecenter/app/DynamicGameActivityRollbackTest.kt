package com.gamecenter.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.core.modulehost.ModuleLoader as CoreModuleLoader
import com.gamecenter.app.core.security.ModuleVerifier
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Exercises the real activity entry after rollback and a cold catalog reload.
 *
 * The fixture APK deliberately has no valid signature. The production core loader must
 * reject it normally; its existing verification-failure callback observes which manifest
 * and file the activity selected, before any Dalvik execution is needed. No fake loader,
 * loaded-instance cache, host fallback, or signature bypass is installed by this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DynamicGameActivityRollbackTest {
    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private var activityController: ActivityController<DynamicGameActivity>? = null
    private val verificationAttempts = mutableListOf<VerificationAttempt>()
    private val loadFailures = mutableListOf<ModuleManifest>()
    private val createdFiles = mutableListOf<File>()
    private var originalVerifyFailure: ((ModuleManifest, File) -> Unit)? = null
    private var originalLoadFailure: ((ModuleManifest) -> Unit)? = null
    private var originalCatalogManifest: ModuleManifest? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        prefs = context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
        originalVerifyFailure = CoreModuleLoader.onVerifyFailure
        originalLoadFailure = CoreModuleLoader.onLoadFailureRollback
        originalCatalogManifest = manifestIndex()[MODULE_ID]
        CoreModuleLoader.unloadModule(MODULE_ID)
        CoreModuleLoader.onVerifyFailure = { manifest, file ->
            if (manifest.id == MODULE_ID) verificationAttempts.add(VerificationAttempt(manifest, file))
        }
        CoreModuleLoader.onLoadFailureRollback = { manifest ->
            if (manifest.id == MODULE_ID) loadFailures.add(manifest)
        }
        ModuleManager.invalidateInstalledCache()
    }

    @After
    fun tearDown() {
        activityController?.destroy()
        CoreModuleLoader.unloadModule(MODULE_ID)
        CoreModuleLoader.onVerifyFailure = originalVerifyFailure
        CoreModuleLoader.onLoadFailureRollback = originalLoadFailure
        createdFiles.forEach { file ->
            file.setWritable(true, false)
            file.delete()
        }
        ModuleManager.removeInstalledModulePublic(context, MODULE_ID)
        originalCatalogManifest?.let { manifestIndex()[MODULE_ID] = it }
            ?: manifestIndex().remove(MODULE_ID)
        ModuleManager.invalidateInstalledCache()
    }

    @Test
    fun `same filename game entry selects installed v1 after catalog reloads v2`() {
        assertInstalledManifestSelected("${MODULE_ID}.apk", "${MODULE_ID}.apk")
    }

    @Test
    fun `changed filename game entry selects installed v1 after catalog reloads v2`() {
        assertInstalledManifestSelected("${MODULE_ID}_v1.apk", "${MODULE_ID}_v2.apk")
    }

    private fun assertInstalledManifestSelected(installedName: String, catalogName: String) {
        val installedBytes = "unsigned-activity-fixture-v1".toByteArray()
        val catalogBytes = "unsigned-activity-fixture-v2-different-size".toByteArray()
        val installed = manifest(1, installedName, installedBytes, "fixture.game.v1.Entry")
        val available = manifest(2, catalogName, catalogBytes, "fixture.game.v2.Entry")
        val installedFile = TransactionInstaller.getCurrentFile(context, installed).apply {
            writeBytes(installedBytes)
        }
        createdFiles.add(installedFile)

        // Persist exactly the post-rollback state: current v1 and no remaining last_good.
        // Then rebuild the in-memory catalog from v2, as a cold start can legitimately do.
        val installedIds = prefs.getStringSet("installed_modules", emptySet()).orEmpty().toMutableSet()
        installedIds.add(MODULE_ID)
        prefs.edit()
            .putStringSet("installed_modules", installedIds)
            .putInt("module_version_$MODULE_ID", installed.versionCode)
            .putString("module_current_manifest_$MODULE_ID", installed.toJson().toString())
            .remove("module_last_good_manifest_$MODULE_ID")
            .remove("module_last_good_version_$MODULE_ID")
            .commit()
        manifestIndex().remove(MODULE_ID)
        ModuleManager.invalidateInstalledCache()
        ModuleManager.registerAvailableManifests(listOf(available))
        assertEquals(available.versionCode, ModuleManager.getModuleManifest(MODULE_ID)?.versionCode)
        assertTrue(ModuleManager.isModuleInstalled(context, MODULE_ID))
        assertEquals(installed.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertFalse("冷启动不能借用已加载模块缓存", CoreModuleLoader.isModuleLoaded(MODULE_ID))
        assertTrue(ModuleVerifier.verify(installedFile, installed.sha256, installed.fileSize).isSuccess)
        assertFalse(ModuleVerifier.verify(installedFile, available.sha256, available.fileSize).isSuccess)

        val intent = Intent(context, DynamicGameActivity::class.java)
            .putExtra(DynamicGameActivity.EXTRA_GAME_ID, GAME_ID)
        activityController = Robolectric.buildActivity(DynamicGameActivity::class.java, intent)
        activityController!!.create()

        assertEquals("真实 Activity 入口必须将已安装文件交给核心加载器校验", 1, verificationAttempts.size)
        val attempt = verificationAttempts.single()
        assertEquals("入口必须使用已安装模块身份", installed.id, attempt.manifest.id)
        assertEquals("入口不能使用目录待更新版本", installed.versionCode, attempt.manifest.versionCode)
        assertEquals(installed.fileName, attempt.manifest.fileName)
        assertEquals(installed.sha256, attempt.manifest.sha256)
        assertEquals(installed.fileSize, attempt.manifest.fileSize)
        assertEquals(installed.entryClass, attempt.manifest.entryClass)
        assertEquals(installedFile.canonicalFile, attempt.file.canonicalFile)
        assertEquals(String(installedBytes), installedFile.readText())
        assertTrue("所选身份必须与实际文件完整性匹配", ModuleVerifier.verify(
            attempt.file, attempt.manifest.sha256, attempt.manifest.fileSize
        ).isSuccess)
        assertTrue("无签名 fixture 应停在验签边界", loadFailures.isEmpty())
        assertFalse("fixture 必须被真实签名校验拒绝而非装载成功", CoreModuleLoader.isModuleLoaded(MODULE_ID))
        assertEquals(installed.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
    }

    private fun manifest(version: Int, fileName: String, bytes: ByteArray, entryClass: String) = ModuleManifest(
        id = MODULE_ID,
        name = "Activity rollback fixture",
        versionCode = version,
        versionName = "1.$version.0",
        type = "game",
        gameId = GAME_ID,
        builtIn = false,
        entryClass = entryClass,
        fileName = fileName,
        fileSize = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        rollbackAllowed = true
    )

    @Suppress("UNCHECKED_CAST")
    private fun manifestIndex(): MutableMap<String, ModuleManifest> =
        ModuleManager::class.java.getDeclaredField("manifests").apply {
            isAccessible = true
        }.get(ModuleManager) as MutableMap<String, ModuleManifest>

    private data class VerificationAttempt(val manifest: ModuleManifest, val file: File)

    companion object {
        private const val GAME_ID = "activity_rollback_fixture"
        private const val MODULE_ID = "game_$GAME_ID"
    }
}

package com.gamecenter.app.modules.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureArchiveInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `extracts ordinary archive inside destination`() {
        val archive = temporaryFolder.newFile("valid.zip")
        writeZip(archive, "nested/index.html", "safe")
        val destination = temporaryFolder.newFolder("valid-output")

        val result = SecureArchiveInstaller.extractSafely(archive, destination)

        assertTrue(result.success)
        assertEquals("safe", File(destination, "nested/index.html").readText())
    }

    @Test
    fun `rejects path traversal without writing outside destination`() {
        val archive = temporaryFolder.newFile("traversal.zip")
        writeZip(archive, "../escaped.txt", "unsafe")
        val destination = File(temporaryFolder.root, "traversal-output")
        destination.mkdirs()
        val escaped = File(temporaryFolder.root, "escaped.txt")

        val result = SecureArchiveInstaller.extractSafely(archive, destination)

        assertFalse(result.success)
        assertEquals("unsafe_archive", result.code)
        assertFalse(escaped.exists())
    }

    @Test
    fun `web archive completes install update rollback and uninstall lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = webModule("runtime_web_lifecycle")
        val archive = TransactionInstaller.getCurrentFile(context, module.legacyManifest!!)
        archive.parentFile?.mkdirs()
        writeZip(archive, "index.html", "version-one")

        assertTrue(SecureArchiveInstaller.install(context, module).success)
        assertEquals(
            "version-one",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "index.html").readText()
        )

        writeZip(archive, "index.html", "version-two")
        assertTrue(SecureArchiveInstaller.install(context, module).success)
        assertEquals(
            "version-two",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "index.html").readText()
        )

        assertTrue(SecureArchiveInstaller.rollback(context, module.id).success)
        assertEquals(
            "version-one",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "index.html").readText()
        )

        assertTrue(SecureArchiveInstaller.uninstall(context, module.id).success)
        assertFalse(SecureArchiveInstaller.currentDirectory(context, module.id).exists())
    }

    @Test
    fun `rejected web update preserves last good runtime and quarantines package`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = webModule("runtime_web_rejected_update")
        val manifest = module.legacyManifest!!
        val archive = TransactionInstaller.getCurrentFile(context, manifest)
        archive.parentFile?.mkdirs()
        writeZip(archive, "index.html", "last-good")
        assertTrue(SecureArchiveInstaller.install(context, module).success)

        writeZip(archive, "wrong.html", "rejected")
        val result = SecureArchiveInstaller.install(context, module)

        assertFalse(result.success)
        assertEquals("entry_missing", result.code)
        assertEquals(
            "last-good",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "index.html").readText()
        )
        assertFalse(archive.exists())
        assertTrue(quarantinedArchiveExists(context, manifest))
    }

    @Test
    fun `rejected asset update leaves runtime current untouched and quarantines outer archive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = contentModule("runtime_asset_rejected_update", RuntimeType.ASSET, DeliveryType.ZIP)
        val manifest = module.legacyManifest!!
        val archive = TransactionInstaller.getCurrentFile(context, manifest)
        archive.parentFile?.mkdirs()
        writeZip(
            archive,
            linkedMapOf(
                "asset-manifest.json" to contentManifest(module, "payload/data.txt"),
                "payload/data.txt" to "asset-last-good"
            )
        )
        assertTrue(AssetRuntimeHandler().install(context, module).success)

        // 建立一个与外层 archive 共用路径的 APK rollback 状态，守卫 rejectPackage
        // 不得误走 ModuleManager.rollbackModule：修复前该调用会把好 archive
        // 恢复回 current，导致下面的 archive.exists 断言失败。
        val goodArchiveBytes = archive.readBytes()
        val apkManifest = manifest.copy(
            fileSize = goodArchiveBytes.size.toLong(),
            sha256 = sha256(goodArchiveBytes)
        )
        ModuleManager.registerAvailableManifests(listOf(apkManifest))
        invokePrivateManifestMethod("markModuleInstalled", context, apkManifest)
        TransactionInstaller.getLastGoodFile(context, apkManifest).writeBytes(goodArchiveBytes)
        context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE).edit()
            .putString("module_last_good_manifest_${module.id}", apkManifest.toJson().toString())
            .putInt("module_last_good_version_${module.id}", apkManifest.versionCode)
            .commit()
        assertTrue(ModuleManager.hasRollback(context, module.id))

        writeZip(archive, "payload/data.txt", "asset-rejected")
        val rejected = AssetRuntimeHandler().install(context, module)

        assertFalse(rejected.success)
        assertEquals("asset_manifest_invalid", rejected.code)
        assertEquals(
            "asset-last-good",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "payload/data.txt").readText()
        )
        assertFalse(archive.exists())
        assertTrue(quarantinedArchiveExists(context, manifest))
    }

    @Test
    fun `rejected unity content update leaves runtime current untouched and quarantines outer archive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = contentModule(
            "runtime_unity_rejected_update",
            RuntimeType.UNITY,
            DeliveryType.CONTENT,
            versionCode = 1,
            launcherId = "test-launcher"
        )
        val manifest = module.legacyManifest!!
        val archive = TransactionInstaller.getCurrentFile(context, manifest)
        archive.parentFile?.mkdirs()
        writeZip(
            archive,
            linkedMapOf(
                "unity-manifest.json" to contentManifest(module, "content/level.bin"),
                "content/level.bin" to "unity-last-good"
            )
        )
        val handler = UnityRuntimeHandler()
        assertTrue(handler.install(context, module).success)

        val rejectedModule = module.copy(versionCode = 2)
        writeZip(
            archive,
            linkedMapOf(
                "unity-manifest.json" to contentManifest(rejectedModule, "content/level.bin")
                    .replace("\"versionCode\": 2", "\"versionCode\": 999"),
                "content/level.bin" to "unity-rejected"
            )
        )
        val rejected = handler.install(context, rejectedModule)

        assertFalse(rejected.success)
        assertEquals("unity_manifest_invalid", rejected.code)
        assertEquals(
            "unity-last-good",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "content/level.bin").readText()
        )
        assertFalse(archive.exists())
        assertTrue(quarantinedArchiveExists(context, manifest))
    }

    @Test
    fun `runtime rollback restores its original layout when outer state commit fails`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moduleId = "runtime_rollback_state_sync"
        val root = File(context.filesDir, "modules/runtime/$moduleId")
        val current = File(root, "current").apply { mkdirs() }
        val lastGood = File(root, "last_good").apply { mkdirs() }
        File(current, "version.txt").writeText("current-v2")
        File(lastGood, "version.txt").writeText("last-good-v1")

        val result = SecureArchiveInstaller.rollback(context, moduleId) { false }

        assertFalse(result.success)
        assertEquals("state_sync_failed", result.code)
        assertEquals("current-v2", File(current, "version.txt").readText())
        assertEquals("last-good-v1", File(lastGood, "version.txt").readText())
        assertFalse(File(root, "quarantine").listFiles().orEmpty().any { it.isDirectory })
    }

    @Test
    fun `rejects high compression ratio archive`() {
        val archive = temporaryFolder.newFile("compression-bomb.zip")
        writeZip(archive, "large.txt", "A".repeat(1_000_000))
        val destination = temporaryFolder.newFolder("compression-output")

        val result = SecureArchiveInstaller.extractSafely(archive, destination)

        assertFalse(result.success)
        assertEquals("unsafe_archive", result.code)
        assertFalse(destination.exists())
    }

    @Test
    fun `asset package requires matching manifest and completes lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = contentModule("runtime_asset_lifecycle", RuntimeType.ASSET, DeliveryType.ZIP)
        val archive = TransactionInstaller.getCurrentFile(context, module.legacyManifest!!)
        archive.parentFile?.mkdirs()
        writeZip(archive, "payload/data.txt", "missing-manifest")

        val rejected = AssetRuntimeHandler().install(context, module)
        assertFalse(rejected.success)
        assertEquals("asset_manifest_invalid", rejected.code)

        writeZip(
            archive,
            linkedMapOf(
                "asset-manifest.json" to contentManifest(module, "payload/data.txt"),
                "payload/data.txt" to "asset-version-one"
            )
        )
        assertTrue(AssetRuntimeHandler().install(context, module).success)
        assertEquals(
            "asset-version-one",
            File(SecureArchiveInstaller.currentDirectory(context, module.id), "payload/data.txt").readText()
        )
        assertTrue(AssetRuntimeHandler().uninstall(context, module).success)
        assertFalse(SecureArchiveInstaller.currentDirectory(context, module.id).exists())
    }

    @Test
    fun `unity content completes install update rollback and uninstall lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val versionOne = contentModule(
            "runtime_unity_lifecycle",
            RuntimeType.UNITY,
            DeliveryType.CONTENT,
            versionCode = 1,
            launcherId = "test-launcher"
        )
        val archive = TransactionInstaller.getCurrentFile(context, versionOne.legacyManifest!!)
        archive.parentFile?.mkdirs()
        writeZip(
            archive,
            linkedMapOf(
                "unity-manifest.json" to contentManifest(versionOne, "content/level.bin"),
                "content/level.bin" to "level-one"
            )
        )
        val handler = UnityRuntimeHandler()
        assertTrue(handler.install(context, versionOne).success)

        val versionTwo = versionOne.copy(versionCode = 2)
        writeZip(
            archive,
            linkedMapOf(
                "unity-manifest.json" to contentManifest(versionTwo, "content/level.bin"),
                "content/level.bin" to "level-two"
            )
        )
        assertTrue(handler.install(context, versionTwo).success)
        assertEquals(
            "level-two",
            File(SecureArchiveInstaller.currentDirectory(context, versionOne.id), "content/level.bin").readText()
        )

        assertTrue(handler.rollback(context, versionTwo).success)
        assertEquals(
            "level-one",
            File(SecureArchiveInstaller.currentDirectory(context, versionOne.id), "content/level.bin").readText()
        )
        assertTrue(handler.uninstall(context, versionTwo).success)
        assertFalse(SecureArchiveInstaller.currentDirectory(context, versionOne.id).exists())
    }

    @Test
    fun `unity content uninstall removes runtime outer packages and installed state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val moduleId = "runtime_unity_complete_uninstall"
        val prefs = context.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
        val handler = UnityRuntimeHandler()
        val versionOne = contentModule(
            moduleId,
            RuntimeType.UNITY,
            DeliveryType.CONTENT,
            versionCode = 1,
            launcherId = "test-launcher"
        )
        val initialManifest = versionOne.legacyManifest!!.copy(fileName = "${moduleId}_v1.zip")
        val oldCurrent = TransactionInstaller.getCurrentFile(context, initialManifest)
        val lastGood = TransactionInstaller.getLastGoodFile(context, initialManifest)
        val newFileName = "${moduleId}_v2.zip"
        val newCurrent = File(TransactionInstaller.getCurrentDir(context), newFileName)
        val staged = File(TransactionInstaller.getStagingDir(context), newFileName)
        val unrelatedLastGood = File(TransactionInstaller.getLastGoodDir(context), "${moduleId}_neighbor.zip")
        val runtimeRoot = File(context.filesDir, "modules/runtime/$moduleId")
        try {
            writeZip(
                oldCurrent,
                linkedMapOf(
                    "unity-manifest.json" to contentManifest(versionOne, "content/level.bin"),
                    "content/level.bin" to "level-one"
                )
            )
            val oldManifest = initialManifest.copy(
                sha256 = sha256(oldCurrent.readBytes()),
                fileSize = oldCurrent.length()
            )
            invokePrivateManifestMethod("markModuleInstalled", context, oldManifest)
            assertTrue(handler.install(context, versionOne.copy(legacyManifest = oldManifest)).success)

            val versionTwo = versionOne.copy(versionCode = 2)
            writeZip(
                staged,
                linkedMapOf(
                    "unity-manifest.json" to contentManifest(versionTwo, "content/level.bin"),
                    "content/level.bin" to "level-two"
                )
            )
            val newManifest = oldManifest.copy(
                versionCode = 2,
                fileName = newFileName,
                sha256 = sha256(staged.readBytes()),
                fileSize = staged.length()
            )
            assertTrue(TransactionInstaller.install(context, newManifest, staged, oldManifest).isSuccess)
            prefs.edit()
                .putString("module_last_good_manifest_$moduleId", oldManifest.toJson().toString())
                .putInt("module_last_good_version_$moduleId", oldManifest.versionCode)
                .commit()
            invokePrivateManifestMethod("markModuleInstalled", context, newManifest)
            ModuleManager.registerAvailableManifests(listOf(newManifest))
            val installedModule = versionTwo.copy(legacyManifest = newManifest)
            assertTrue(handler.install(context, installedModule).success)
            unrelatedLastGood.writeText("other-module-backup")
            ModuleManager.invalidateInstalledCache()
            assertTrue(ModuleManager.isModuleInstalled(context, moduleId))
            assertEquals(2, ModuleManager.getInstalledVersionCode(context, moduleId))
            assertTrue(ModuleManager.hasRollback(context, moduleId))

            assertTrue(handler.uninstall(context, installedModule).success)

            assertFalse("卸载应删除完整 runtime 目录", runtimeRoot.exists())
            assertFalse("卸载应删除外层 current ZIP", newCurrent.exists())
            assertFalse("卸载应删除旧文件名对应的外层 last_good ZIP", lastGood.exists())
            assertEquals("other-module-backup", unrelatedLastGood.readText())
            assertFalse(prefs.getStringSet("installed_modules", emptySet()).orEmpty().contains(moduleId))
            listOf(
                "module_version_",
                "module_current_manifest_",
                "module_last_good_version_",
                "module_last_good_manifest_"
            ).forEach { prefix ->
                assertFalse("卸载应移除 $prefix 状态", prefs.contains(prefix + moduleId))
            }
            assertFalse("内存安装缓存应立即失效", ModuleManager.isModuleInstalled(context, moduleId))
            assertEquals(0, ModuleManager.getInstalledVersionCode(context, moduleId))
            assertFalse(ModuleManager.hasRollback(context, moduleId))
            ModuleManager.invalidateInstalledCache()
            assertFalse("重建安装缓存后也不能复活已卸载模块", ModuleManager.isModuleInstalled(context, moduleId))
        } finally {
            SecureArchiveInstaller.uninstall(context, moduleId)
            listOf(oldCurrent, newCurrent, lastGood, staged, unrelatedLastGood).forEach {
                it.setWritable(true, false)
                it.delete()
            }
            ModuleManager.removeInstalledModulePublic(context, moduleId)
            ModuleManager.invalidateInstalledCache()
        }
    }

    private fun webModule(id: String): CatalogModule {
        val manifest = ModuleManifest(
            id = id,
            name = id,
            fileName = "$id.zip",
            kind = "web-zip"
        )
        return CatalogModule(
            id = id,
            name = id,
            runtimeType = RuntimeType.WEB,
            deliveryType = DeliveryType.ZIP,
            entry = "index.html",
            legacyManifest = manifest
        )
    }

    private fun contentModule(
        id: String,
        runtimeType: RuntimeType,
        deliveryType: DeliveryType,
        versionCode: Int = 1,
        launcherId: String = ""
    ): CatalogModule {
        val manifest = ModuleManifest(
            id = id,
            name = id,
            versionCode = versionCode,
            fileName = "$id.zip",
            kind = if (runtimeType == RuntimeType.UNITY) "unity-content" else "asset-zip"
        )
        return CatalogModule(
            id = id,
            name = id,
            versionCode = versionCode,
            runtimeType = runtimeType,
            deliveryType = deliveryType,
            launcherId = launcherId,
            legacyManifest = manifest
        )
    }

    private fun contentManifest(module: CatalogModule, file: String): String = """
        {
          "schemaVersion": 1,
          "moduleId": "${module.id}",
          "versionCode": ${module.versionCode},
          "launcherId": "${module.launcherId}",
          "files": ["$file"]
        }
    """.trimIndent()

    private fun writeZip(file: File, name: String, content: String) {
        writeZip(file, linkedMapOf(name to content))
    }

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
    }

    private fun quarantinedArchiveExists(context: Context, manifest: ModuleManifest): Boolean =
        TransactionInstaller.getQuarantineDir(context).listFiles().orEmpty()
            .any { it.name.startsWith("${manifest.id}_") && it.name.endsWith(manifest.fileName) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun invokePrivateManifestMethod(name: String, context: Context, manifest: ModuleManifest) {
        val instance = ModuleManager::class.java.getDeclaredField("INSTANCE").get(null)
        ModuleManager::class.java.getDeclaredMethod(
            name,
            Context::class.java,
            ModuleManifest::class.java
        ).apply {
            isAccessible = true
            invoke(instance, context, manifest)
        }
    }
}

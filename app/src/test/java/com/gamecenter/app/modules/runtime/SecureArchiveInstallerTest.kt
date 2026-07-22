package com.gamecenter.app.modules.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
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
        assertTrue(TransactionInstaller.getQuarantineDir(context).listFiles().orEmpty().isNotEmpty())
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
}

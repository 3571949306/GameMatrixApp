package com.gamecenter.app.modules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.modules.store.TransactionInstaller
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
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

/** Regression coverage for the installed-manifest rollback protocol. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleManagerRollbackProtocolTest {

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
        com.gamecenter.app.modules.ModuleLoader.attachHostCleanup(null, null)
        clearState()
    }

    @Test
    fun `rollback restores old file and its complete manifest when file names differ`() {
        val oldBytes = "module-payload-v1".toByteArray()
        val newBytes = "module-payload-v2-with-different-size".toByteArray()
        val oldManifest = manifest(1, "module_v1.zip", oldBytes, "entry.v1.Entry")
        val newManifest = manifest(2, "module_v2.zip", newBytes, "entry.v2.Entry")
        seedInstalledState(oldManifest, newManifest, oldBytes, newBytes)
        assertEquals(
            "回滚前先初始化安装版本缓存",
            newManifest.versionCode,
            ModuleManager.getInstalledVersionCode(context, MODULE_ID)
        )

        assertTrue("回滚应成功", ModuleManager.rollbackModule(context, MODULE_ID))

        val restored = TransactionInstaller.getCurrentFile(context, oldManifest)
        assertTrue("旧文件应按旧 fileName 恢复", restored.isFile)
        assertEquals(String(oldBytes), restored.readText())
        assertFalse("新 fileName 不应继续作为 current", TransactionInstaller.getCurrentFile(context, newManifest).exists())
        assertEquals(oldManifest.sha256, ModuleVerifier.computeSha256(restored))
        assertTrue(
            "恢复文件必须通过真实 SHA/size 校验",
            com.gamecenter.app.core.security.ModuleVerifier
                .verify(restored, oldManifest.sha256, oldManifest.fileSize).isSuccess
        )

        val active = ModuleManager.getModuleManifest(MODULE_ID)
        assertEquals(oldManifest.fileName, active?.fileName)
        assertEquals(oldManifest.versionCode, active?.versionCode)
        assertEquals(oldManifest.sha256, active?.sha256)
        assertEquals(oldManifest.fileSize, active?.fileSize)
        assertEquals(oldManifest.entryClass, active?.entryClass)
        assertEquals(oldManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertTrue("恢复后安装缓存仍应保留模块", ModuleManager.getInstalledModuleIds(context).contains(MODULE_ID))
        assertTrue(prefs.getStringSet("installed_modules", emptySet()).orEmpty().contains(MODULE_ID))
        val currentSnapshot = JSONObject(
            prefs.getString("module_current_manifest_$MODULE_ID", null)
                ?: error("current manifest snapshot missing")
        )
        assertEquals(oldManifest.fileName, currentSnapshot.getString("fileName"))
        assertEquals(oldManifest.versionCode, currentSnapshot.getInt("versionCode"))
        assertEquals(oldManifest.sha256, currentSnapshot.getString("sha256"))
        assertEquals(oldManifest.fileSize, currentSnapshot.getLong("fileSize"))
        assertEquals(oldManifest.entryClass, currentSnapshot.getString("entryClass"))
        assertFalse("成功回滚后不应继续报告同一版本可回滚", ModuleManager.hasRollback(context, MODULE_ID))
        assertFalse("成功回滚后必须清除 last-good version metadata", prefs.contains("module_last_good_version_$MODULE_ID"))
        assertFalse("成功回滚后必须清除 last-good manifest metadata", prefs.contains("module_last_good_manifest_$MODULE_ID"))
        assertTrue("last_good 文件可保留供下一次更新覆盖", TransactionInstaller.getLastGoodFile(context, oldManifest).isFile)

        // 模拟目录冷启动：远程清单仍可展示 v2，但已安装文件定位/版本读取必须依赖
        // 持久化的 v1 current 快照，而不是按 v2 的 fileName 清理掉安装状态。
        clearManifestIndex()
        ModuleManager.invalidateInstalledCache()
        ModuleManager.registerAvailableManifests(listOf(newManifest))
        assertTrue(ModuleManager.isModuleInstalled(context, MODULE_ID))
        assertEquals(oldManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))

        // The next update must establish a fresh last-good record from the
        // recovered current snapshot; rollback itself must not pre-seed it.
        val nextBytes = "module-payload-v3".toByteArray()
        val nextManifest = manifest(3, "module_v3.zip", nextBytes, "entry.v3.Entry")
        ModuleManager.registerAvailableManifests(listOf(nextManifest))
        invokePrivateManifestMethod("prepareInstalledManifest", nextManifest)
        assertFalse("安装准备阶段不能预写 last-good", prefs.contains("module_last_good_manifest_$MODULE_ID"))
        val staged = TransactionInstaller.getStagingFile(context, nextManifest).apply {
            parentFile?.mkdirs()
            writeBytes(nextBytes)
        }
        invokeDownloadedInstall(nextManifest, staged)
        assertEquals(oldManifest.versionCode, prefs.getInt("module_last_good_version_$MODULE_ID", 0))
        assertTrue(prefs.contains("module_last_good_manifest_$MODULE_ID"))
        assertEquals(nextManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertTrue("新版本安装后应重新具备回滚能力", ModuleManager.hasRollback(context, MODULE_ID))
    }

    @Test
    fun `missing or corrupt last-good manifest never reports rollback available`() {
        val oldBytes = "module-payload-v1".toByteArray()
        val newBytes = "module-payload-v2".toByteArray()
        // 使用同一 fileName，确保该用例由 manifest 快照守卫触发，而不是由路径不匹配
        // 偶然返回 false；修复前 TransactionInstaller 会在这里错误地报告成功。
        val oldManifest = manifest(1, "module_same.zip", oldBytes, "entry.v1.Entry")
        val newManifest = manifest(2, "module_same.zip", newBytes, "entry.v2.Entry")

        for (snapshot in listOf<String?>(null, "not-json")) {
            clearState()
            seedInstalledState(oldManifest, newManifest, oldBytes, newBytes, snapshot)

            assertFalse("snapshot=$snapshot 不得宣称可回滚", ModuleManager.hasRollback(context, MODULE_ID))
            assertFalse("snapshot=$snapshot 不得返回回滚成功", ModuleManager.rollbackModule(context, MODULE_ID))
            assertEquals(
                "snapshot=$snapshot 损坏时不能遗留旧 last-good version 数字",
                0,
                prefs.getInt("module_last_good_version_$MODULE_ID", 0)
            )
            assertEquals(newManifest, ModuleManager.getModuleManifest(MODULE_ID))
            assertEquals(String(newBytes), TransactionInstaller.getCurrentFile(context, newManifest).readText())
        }
    }

    @Test
    fun `downloaded update rejected before backup preserves existing rollback and reports failure`() {
        val oldBytes = "rollback-preflight-v1".toByteArray()
        val currentBytes = "rollback-preflight-v2".toByteArray()
        val updateBytes = "rollback-preflight-v3".toByteArray()
        val oldManifest = manifest(1, "module_same.zip", oldBytes, "entry.v1.Entry")
        val currentManifest = manifest(2, "module_same.zip", currentBytes, "entry.v2.Entry")
        val updateManifest = manifest(3, "${MODULE_ID}_blocked_v3.zip", updateBytes, "entry.v3.Entry")
        seedInstalledState(oldManifest, currentManifest, oldBytes, currentBytes)
        ModuleManager.registerAvailableManifests(listOf(updateManifest))
        assertTrue("更新前必须已具备 v1 回滚能力", ModuleManager.hasRollback(context, MODULE_ID))

        // The package passes transport SHA/size verification. An occupied new filename
        // then rejects the real transaction before it has copied current v2 to last_good.
        val conflictingCurrent = TransactionInstaller.getCurrentFile(context, updateManifest).apply {
            writeText("unrelated-current-file")
        }
        val staged = TransactionInstaller.getStagingFile(context, updateManifest).apply {
            writeBytes(updateBytes)
        }
        val oldSnapshot = prefs.getString("module_last_good_manifest_$MODULE_ID", null)
        val callbacks = mutableListOf<String>()
        val instance = ModuleManager::class.java.getDeclaredField("INSTANCE").get(null)
        @Suppress("UNCHECKED_CAST")
        val downloadCallbacks = ModuleManager::class.java.getDeclaredField("downloadCallbacks").apply {
            isAccessible = true
        }.get(instance) as MutableMap<String, ModuleDownloader.Callback>
        downloadCallbacks[MODULE_ID] = object : ModuleDownloader.Callback {
            override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) = Unit
            override fun onComplete(moduleId: String, file: File) {
                callbacks.add("complete")
            }
            override fun onError(moduleId: String, message: String) {
                callbacks.add("error:$message")
            }
            override fun onStateChanged(moduleId: String, state: String) {
                callbacks.add("state:$state")
            }
            override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) = Unit
        }
        try {
            invokeDownloadedInstall(updateManifest, staged)
            ShadowLooper.idleMainLooper()

            assertEquals(
                listOf("state:installing", "error:安装失败: 新 current 文件归属无法确认"),
                callbacks
            )
            assertFalse("失败后必须移除本次下载回调", downloadCallbacks.containsKey(MODULE_ID))
            assertEquals(String(currentBytes), TransactionInstaller.getCurrentFile(context, currentManifest).readText())
            assertEquals(String(oldBytes), TransactionInstaller.getLastGoodFile(context, oldManifest).readText())
            assertEquals("unrelated-current-file", conflictingCurrent.readText())
            assertEquals(currentManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
            assertEquals(
                "事务尚未备份 current 时不得覆盖既有 last-good 快照",
                oldSnapshot,
                prefs.getString("module_last_good_manifest_$MODULE_ID", null)
            )
            assertEquals(oldManifest.versionCode, prefs.getInt("module_last_good_version_$MODULE_ID", 0))
            assertTrue("被拒绝的更新不能消耗原有 v1 回滚能力", ModuleManager.hasRollback(context, MODULE_ID))
        } finally {
            downloadCallbacks.remove(MODULE_ID)
        }
    }

    @Test
    fun `failed current move records the backup actually written by the transaction`() {
        val oldBytes = "move-failure-v1".toByteArray()
        val currentBytes = "move-failure-v2".toByteArray()
        val updateBytes = "move-failure-v3".toByteArray()
        val oldManifest = manifest(1, "module_same.zip", oldBytes, "entry.v1.Entry")
        val currentManifest = manifest(2, "module_same.zip", currentBytes, "entry.v2.Entry")
        val updateManifest = manifest(3, "module_same.zip", updateBytes, "entry.v3.Entry")
        seedInstalledState(oldManifest, currentManifest, oldBytes, currentBytes)
        ModuleManager.registerAvailableManifests(listOf(updateManifest))
        val stagedPath = TransactionInstaller.getStagingFile(context, updateManifest).apply {
            writeBytes(updateBytes)
        }
        // Real SHA/size verification and backup succeed; only the final rename fails.
        // TransactionInstaller restores current v2, but last_good has already become v2.
        val renameFailure = object : File(stagedPath.path) {
            override fun renameTo(dest: File): Boolean = false
        }

        invokeDownloadedInstall(updateManifest, renameFailure)

        assertEquals(String(currentBytes), TransactionInstaller.getCurrentFile(context, currentManifest).readText())
        assertEquals(String(currentBytes), TransactionInstaller.getLastGoodFile(context, currentManifest).readText())
        assertEquals(currentManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        val snapshot = ModuleManifest.fromJson(
            JSONObject(prefs.getString("module_last_good_manifest_$MODULE_ID", null) ?: error("last-good snapshot missing"))
        )
        // Stored manifests pass through fromJson, which normalizes absent details/privacy
        // to empty objects. Compare the entire normalized record, including version,
        // fileName, size, SHA and entryClass, rather than unrelated nullable defaults.
        val expectedSnapshot = ModuleManifest.fromJson(currentManifest.toJson())
        assertEquals("失败移动后元数据必须描述实际写入的 v2 备份", expectedSnapshot, snapshot)
        assertEquals(currentManifest.versionCode, prefs.getInt("module_last_good_version_$MODULE_ID", 0))
        assertTrue(ModuleManager.hasRollback(context, MODULE_ID))
    }

    @Test
    fun `external package verification failure preserves the previous rollback snapshot`() {
        val oldBytes = "external-preflight-v1".toByteArray()
        val currentBytes = "external-preflight-v2".toByteArray()
        val oldManifest = manifest(1, "module_same.zip", oldBytes, "entry.v1.Entry")
        val currentManifest = manifest(2, "module_same.zip", currentBytes, "entry.v2.Entry")
        val updateManifest = manifest(3, "module_v3.zip", "verified-v3".toByteArray(), "entry.v3.Entry")
        seedInstalledState(oldManifest, currentManifest, oldBytes, currentBytes)
        ModuleManager.registerAvailableManifests(listOf(updateManifest))
        val oldSnapshot = prefs.getString("module_last_good_manifest_$MODULE_ID", null)
        val invalidPackage = File(context.cacheDir, "apply_external_bad.apk").apply {
            writeText("tampered-external-package")
        }

        assertFalse(ModuleManager.applyExternalUpdate(context, MODULE_ID, invalidPackage, updateManifest.versionCode))

        assertEquals(String(currentBytes), TransactionInstaller.getCurrentFile(context, currentManifest).readText())
        assertEquals(String(oldBytes), TransactionInstaller.getLastGoodFile(context, oldManifest).readText())
        assertEquals(currentManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertEquals(oldSnapshot, prefs.getString("module_last_good_manifest_$MODULE_ID", null))
        assertEquals(oldManifest.versionCode, prefs.getInt("module_last_good_version_$MODULE_ID", 0))
        assertTrue(ModuleManager.hasRollback(context, MODULE_ID))
        assertTrue("拒绝事务不能删除调用方源文件", invalidPackage.isFile)
    }

    @Test
    fun `external update verifies before changing current and preserves v1 state on failure`() {
        val oldBytes = "external-module-v1".toByteArray()
        val expectedNewBytes = "external-module-v2".toByteArray()
        val oldManifest = manifest(1, "apply_v1.apk", oldBytes, "entry.v1.Entry")
        val newManifest = manifest(2, "apply_v2.apk", expectedNewBytes, "entry.v2.Entry")
        val currentV1 = TransactionInstaller.getCurrentFile(context, oldManifest).apply {
            parentFile?.mkdirs()
            writeBytes(oldBytes)
        }

        // Record the real installed v1 state. A rejected update must not create
        // rollback metadata for a backup that was never written.
        invokePrivateManifestMethod("markModuleInstalled", oldManifest)
        ModuleManager.registerAvailableManifests(listOf(newManifest))

        val badExternalApk = File(context.cacheDir, "apply_external_bad.apk").apply {
            writeBytes("tampered-v2".toByteArray())
        }
        assertFalse(
            "SHA 失败时 applyExternalUpdate 不得提交 v2",
            ModuleManager.applyExternalUpdate(context, MODULE_ID, badExternalApk, newManifest.versionCode)
        )

        assertTrue(currentV1.isFile)
        assertEquals(String(oldBytes), currentV1.readText())
        assertFalse(TransactionInstaller.getCurrentFile(context, newManifest).exists())
        assertEquals(oldManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        val currentSnapshot = JSONObject(
            prefs.getString("module_current_manifest_$MODULE_ID", null)
                ?: error("current snapshot must remain v1")
        )
        assertEquals(oldManifest.fileName, currentSnapshot.getString("fileName"))
        assertEquals(oldManifest.versionCode, currentSnapshot.getInt("versionCode"))
        assertEquals(oldManifest.sha256, currentSnapshot.getString("sha256"))
        assertFalse(prefs.contains("module_last_good_version_$MODULE_ID"))
        assertFalse(prefs.contains("module_last_good_manifest_$MODULE_ID"))
    }

    @Test
    fun `historical install without current snapshot rejects rollback and preserves current on failed update`() {
        val oldBytes = "historical-v1".toByteArray()
        val currentBytes = "historical-v2".toByteArray()
        val oldManifest = manifest(1, "historical_v1.zip", oldBytes, "entry.v1.Entry")
        val currentManifest = manifest(2, "historical_v2.zip", currentBytes, "entry.v2.Entry")
        seedInstalledState(
            oldManifest,
            currentManifest,
            oldBytes,
            currentBytes,
            currentSnapshot = null
        )
        val current = TransactionInstaller.getCurrentFile(context, currentManifest)
        val before = current.readText()

        assertFalse("缺少 current manifest 时不得宣称可回滚", ModuleManager.hasRollback(context, MODULE_ID))
        assertFalse("缺少 current manifest 时必须保守拒绝回滚", ModuleManager.rollbackModule(context, MODULE_ID))
        assertEquals("回滚拒绝不得破坏 current", before, current.readText())

        val externalApk = File(context.cacheDir, "historical_external.apk").apply {
            writeBytes("external-update".toByteArray())
        }
        assertFalse(
            "坏外部更新不得提交或破坏旧 current",
            ModuleManager.applyExternalUpdate(context, MODULE_ID, externalApk, currentManifest.versionCode)
        )
        assertEquals("失败外部更新不得破坏 current", before, current.readText())
    }

    @Test
    fun `legacy external update quarantines unverifiable files when loading fails`() {
        val currentBytes = "legacy-external-v2".toByteArray()
        val invalidUpdatedBytes = "not-a-zip-or-dex-v3".toByteArray()
        val currentManifest = manifest(2, "${MODULE_ID}_legacy_external_v1.zip", currentBytes, "entry.v2.Entry")
        val updateManifest = manifest(3, "${MODULE_ID}_legacy_external_v2.zip", invalidUpdatedBytes, "entry.v3.Entry")
        val legacyFile = File(context.filesDir, "modules/${currentManifest.fileName}").apply {
            parentFile?.mkdirs()
            writeBytes(currentBytes)
        }
        prefs.edit()
            .putStringSet("installed_modules", setOf(MODULE_ID))
            .putInt("module_version_$MODULE_ID", currentManifest.versionCode)
            .remove("module_current_manifest_$MODULE_ID")
            .remove("module_last_good_manifest_$MODULE_ID")
            .remove("module_last_good_version_$MODULE_ID")
            .commit()
        ModuleManager.registerAvailableManifests(listOf(updateManifest))
        ModuleManager.invalidateInstalledCache()
        var callbackRollback = false
        com.gamecenter.app.modules.ModuleLoader.attachHostCleanup(null) { failedManifest ->
            callbackRollback = ModuleManager.recoverFailedModuleLoad(context, failedManifest.id)
        }

        val externalApk = File(context.cacheDir, "legacy_external_source.bin").apply {
            writeBytes(invalidUpdatedBytes)
        }
        assertFalse(
            "加载失败时外部更新必须报告失败",
            ModuleManager.applyExternalUpdate(
                context,
                MODULE_ID,
                externalApk,
                updateManifest.versionCode
            )
        )

        assertTrue("legacy 隔离 token 必须能被统一加载回调发现", callbackRollback)
        assertFalse("无法验证的旧包不得恢复为 current", legacyFile.exists())
        assertFalse("失败的新包不得继续作为 current", TransactionInstaller.getCurrentFile(context, updateManifest).exists())
        assertTrue("无法验证的旧包必须保留在 quarantine 供诊断或清理",
            TransactionInstaller.getQuarantineDir(context).listFiles().orEmpty()
                .any { it.readBytes().contentEquals(currentBytes) })
        assertEquals("legacy 回滚不得继续伪造已安装版本", 0,
            ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertFalse("legacy 失败后必须清除安装状态", ModuleManager.isModuleInstalled(context, MODULE_ID))
        assertFalse("legacy 恢复不得伪造 current snapshot", prefs.contains("module_current_manifest_$MODULE_ID"))
        assertFalse("legacy 恢复不得伪造 last-good snapshot", prefs.contains("module_last_good_manifest_$MODULE_ID"))
        assertFalse("legacy 恢复完成后必须消费隔离 token", prefs.contains("module_legacy_quarantine_$MODULE_ID"))
        assertFalse("恢复后不应报告可回滚", ModuleManager.hasRollback(context, MODULE_ID))
        assertFalse("无可信 legacy 清单时公开回滚接口必须保持不可用",
            ModuleManager.rollbackModule(context, MODULE_ID))
    }

    @Test
    fun `legacy flat directory file with changed filename is located safely`() {
        val oldBytes = "legacy-flat-v1".toByteArray()
        val oldManifest = manifest(1, "${MODULE_ID}_legacy_v1.zip", oldBytes, "entry.v1.Entry")
        val newManifest = manifest(2, "${MODULE_ID}_legacy_v2.zip", "new".toByteArray(), "entry.v2.Entry")
        val legacyFile = File(context.filesDir, "modules/${oldManifest.fileName}").apply {
            parentFile?.mkdirs()
            writeBytes(oldBytes)
        }

        assertEquals("兼容定位必须识别 fileName 已变化的唯一 legacy 文件",
            legacyFile,
            com.gamecenter.app.modules.ModuleDownloader.getModuleFileCompat(context, newManifest))
    }

    @Test
    fun `legacy update with an unrecognized old filename does not claim failed install`() {
        val orphanBytes = "legacy-orphan-v1".toByteArray()
        val invalidUpdatedBytes = "legacy-orphan-invalid-v2".toByteArray()
        val oldManifest = manifest(1, "${MODULE_ID}_legacy_v1.zip", orphanBytes, "entry.v1.Entry")
        val updateManifest = manifest(2, "${MODULE_ID}_legacy_v2.zip", invalidUpdatedBytes, "entry.v2.Entry")
        TransactionInstaller.getCurrentFile(context, oldManifest).apply {
            parentFile?.mkdirs()
            writeBytes(orphanBytes)
        }
        prefs.edit()
            .putStringSet("installed_modules", setOf(MODULE_ID))
            .putInt("module_version_$MODULE_ID", oldManifest.versionCode)
            .remove("module_current_manifest_$MODULE_ID")
            .remove("module_last_good_manifest_$MODULE_ID")
            .remove("module_last_good_version_$MODULE_ID")
            .commit()
        ModuleManager.registerAvailableManifests(listOf(updateManifest))
        com.gamecenter.app.modules.ModuleLoader.attachHostCleanup(null, null)

        val externalApk = File(context.cacheDir, "legacy_orphan_source.bin").apply {
            writeBytes(invalidUpdatedBytes)
        }
        assertFalse(
            ModuleManager.applyExternalUpdate(
                context,
                MODULE_ID,
                externalApk,
                updateManifest.versionCode
            )
        )

        assertEquals(String(orphanBytes), TransactionInstaller.getCurrentFile(context, oldManifest).readText())
        assertFalse(TransactionInstaller.getCurrentFile(context, updateManifest).exists())
        assertFalse("无法识别旧文件时不得伪造已安装旧版本", ModuleManager.isModuleInstalled(context, MODULE_ID))
        assertFalse(prefs.contains("module_current_manifest_$MODULE_ID"))
    }

    @Test
    fun `loader failure callback rolls back verified current before clearing it`() {
        val oldBytes = "loadable-old-payload".toByteArray()
        val brokenBytes = "not-a-zip-or-dex-payload".toByteArray()
        val oldManifest = manifest(1, "loader_v1.zip", oldBytes, "entry.v1.Entry")
        val currentManifest = manifest(2, "loader_v2.zip", brokenBytes, "entry.v2.Entry")
        seedInstalledState(oldManifest, currentManifest, oldBytes, brokenBytes)
        ModuleManager.registerAvailableManifests(listOf(currentManifest))
        com.gamecenter.app.modules.ModuleLoader.attachHostCleanup(null) { failedManifest ->
            ModuleManager.rollbackModule(context, failedManifest.id)
        }

        assertEquals(null, ModuleManager.loadModule(context, MODULE_ID))
        assertEquals(String(oldBytes), TransactionInstaller.getCurrentFile(context, oldManifest).readText())
        assertFalse("失败加载后坏版本路径不应继续存在", TransactionInstaller.getCurrentFile(context, currentManifest).exists())
        assertEquals(oldManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertFalse("加载失败回滚后不应重复报告 last-good", ModuleManager.hasRollback(context, MODULE_ID))
    }

    @Test
    fun `missing current uses rollback before removing installed state`() {
        val oldBytes = "missing-current-old".toByteArray()
        val currentBytes = "missing-current-new".toByteArray()
        val oldManifest = manifest(1, "missing_v1.zip", oldBytes, "entry.v1.Entry")
        val currentManifest = manifest(2, "missing_v2.zip", currentBytes, "entry.v2.Entry")
        seedInstalledState(oldManifest, currentManifest, oldBytes, currentBytes)
        TransactionInstaller.getCurrentFile(context, currentManifest).delete()
        ModuleManager.registerAvailableManifests(listOf(currentManifest))
        com.gamecenter.app.modules.ModuleLoader.attachHostCleanup(null, null)
        ModuleManager.invalidateInstalledCache()
        ModuleManager.ensureInstalledCache(context)
        assertTrue("缓存初始化不能抢先清掉可回滚安装状态", ModuleManager.isModuleInstalled(context, MODULE_ID))

        assertEquals(null, ModuleManager.loadModule(context, MODULE_ID))
        assertEquals(String(oldBytes), TransactionInstaller.getCurrentFile(context, oldManifest).readText())
        assertEquals(oldManifest.versionCode, ModuleManager.getInstalledVersionCode(context, MODULE_ID))
        assertTrue("回滚后模块仍应保留安装标记", ModuleManager.isModuleInstalled(context, MODULE_ID))
    }

    @Test
    fun `hasRollback rejects a last-good file whose size or sha no longer matches`() {
        val oldBytes = "module-payload-v1".toByteArray()
        val newBytes = "module-payload-v2".toByteArray()
        val oldManifest = manifest(1, "module_v1_corrupt.zip", oldBytes, "entry.v1.Entry")
        val newManifest = manifest(2, "module_v2_corrupt.zip", newBytes, "entry.v2.Entry")
        seedInstalledState(oldManifest, newManifest, oldBytes, newBytes)

        TransactionInstaller.getLastGoodFile(context, oldManifest).writeBytes("tampered".toByteArray())

        assertFalse("损坏的 last_good 不得宣称可回滚", ModuleManager.hasRollback(context, MODULE_ID))
        assertEquals(0, prefs.getInt("module_last_good_version_$MODULE_ID", 0))
        assertFalse("损坏的 last_good 不得执行回滚", ModuleManager.rollbackModule(context, MODULE_ID))
        assertEquals(String(newBytes), TransactionInstaller.getCurrentFile(context, newManifest).readText())
    }

    private fun invokeDownloadedInstall(manifest: ModuleManifest, file: File) {
        val instance = ModuleManager::class.java.getDeclaredField("INSTANCE").get(null)
        ModuleManager::class.java.getDeclaredMethod(
            "installDownloadedModule",
            Context::class.java,
            String::class.java,
            ModuleManifest::class.java,
            File::class.java
        ).apply {
            isAccessible = true
            invoke(instance, context, MODULE_ID, manifest, file)
        }
    }

    private fun invokePrivateManifestMethod(name: String, manifest: ModuleManifest) {
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

    private fun seedInstalledState(
        oldManifest: ModuleManifest,
        newManifest: ModuleManifest,
        oldBytes: ByteArray,
        newBytes: ByteArray,
        lastGoodSnapshot: String? = oldManifest.toJson().toString(),
        currentSnapshot: String? = newManifest.toJson().toString()
    ) {
        TransactionInstaller.getLastGoodFile(context, oldManifest).apply {
            parentFile?.mkdirs()
            writeBytes(oldBytes)
        }
        TransactionInstaller.getCurrentFile(context, newManifest).apply {
            parentFile?.mkdirs()
            writeBytes(newBytes)
        }
        val installed = prefs.getStringSet("installed_modules", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        installed.add(MODULE_ID)
        prefs.edit()
            .putStringSet("installed_modules", installed)
            .putInt("module_version_$MODULE_ID", newManifest.versionCode)
            .putInt("module_last_good_version_$MODULE_ID", oldManifest.versionCode)
            .apply {
                if (lastGoodSnapshot == null) {
                    remove("module_last_good_manifest_$MODULE_ID")
                } else {
                    putString("module_last_good_manifest_$MODULE_ID", lastGoodSnapshot)
                }
                if (currentSnapshot == null) {
                    remove("module_current_manifest_$MODULE_ID")
                } else {
                    putString("module_current_manifest_$MODULE_ID", currentSnapshot)
                }
            }
            .commit()
        ModuleManager.registerAvailableManifests(listOf(newManifest))
        ModuleManager.invalidateInstalledCache()
    }

    private fun manifest(
        versionCode: Int,
        fileName: String,
        bytes: ByteArray,
        entryClass: String
    ) = ModuleManifest(
        id = MODULE_ID,
        name = "Rollback test module",
        versionName = "1.$versionCode.0",
        versionCode = versionCode,
        entryClass = entryClass,
        fileName = fileName,
        fileSize = bytes.size.toLong(),
        sha256 = sha256(bytes),
        downloadUrl = "https://example.test/$fileName",
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
                .filter { it.name == "module_same.zip" || it.name.startsWith("module_v") ||
                    it.name == "apply_v1.apk" ||
                    it.name == "apply_v2.apk" || it.name == "legacy_external.zip" ||
                    it.name.startsWith(MODULE_ID) }
                .forEach { file ->
                    file.walkBottomUp().forEach { it.setWritable(true, false) }
                    file.deleteRecursively()
                }
        }
        File(context.filesDir, "modules").listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(MODULE_ID) }
            .forEach { file ->
                file.setWritable(true, false)
                file.delete()
            }
        val installed = prefs.getStringSet("installed_modules", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        installed.remove(MODULE_ID)
        prefs.edit()
            .putStringSet("installed_modules", installed)
            .remove("module_version_$MODULE_ID")
            .remove("module_last_good_version_$MODULE_ID")
            .remove("module_current_manifest_$MODULE_ID")
            .remove("module_last_good_manifest_$MODULE_ID")
            .remove("module_legacy_quarantine_$MODULE_ID")
            .remove("module_legacy_version_$MODULE_ID")
            .commit()
        listOf(
            "apply_external_bad.apk",
            "historical_external.apk",
            "legacy_external_source.bin",
            "legacy_orphan_source.bin"
        ).forEach {
            File(context.cacheDir, it).delete()
        }
        clearManifestIndex()
        ModuleManager.invalidateInstalledCache()
    }

    private fun clearManifestIndex() {
        val instance = ModuleManager::class.java.getDeclaredField("INSTANCE").get(null)
        @Suppress("UNCHECKED_CAST")
        val manifests = ModuleManager::class.java.getDeclaredField("manifests").apply {
            isAccessible = true
        }.get(instance) as MutableMap<String, ModuleManifest>
        manifests.clear()
    }

    companion object {
        private const val MODULE_ID = "rollback_protocol_mod"
    }
}

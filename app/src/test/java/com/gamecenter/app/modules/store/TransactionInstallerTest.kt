package com.gamecenter.app.modules.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 事务安装/回滚回归（P3 补强）。
 *
 * 覆盖：
 * - last_good 存在时回滚恢复 current；
 * - 无 last_good 时回滚失败（不产生假成功）；
 * - 清单缺 SHA-256 时 install 直接被拒绝（P1 强制哈希语义在安装链路生效）。
 */
@RunWith(RobolectricTestRunner::class)
class TransactionInstallerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun manifest(fileName: String = "m1.apk", sha256: String = "a".repeat(64)) = ModuleManifest(
        id = "m1",
        name = "m1",
        versionName = "1.0.0",
        versionCode = 2,
        fileName = fileName,
        fileSize = 0,
        sha256 = sha256,
        downloadUrl = "https://example.test/$fileName"
    )

    @After
    fun tearDown() {
        // 清理测试产生的模块目录
        TransactionInstaller.getCurrentDir(context).let { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
        TransactionInstaller.getLastGoodDir(context).let { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
        TransactionInstaller.getStagingDir(context).let { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
        TransactionInstaller.getQuarantineDir(context).let { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
    }

    @Test
    fun `rollback restores last good into current when available`() {
        val m = manifest()
        TransactionInstaller.getLastGoodFile(context, m).apply { writeText("GOOD") }
        TransactionInstaller.getCurrentFile(context, m).apply { writeText("BAD") }

        assertTrue(TransactionInstaller.rollback(context, m))
        assertEquals("GOOD", TransactionInstaller.getCurrentFile(context, m).readText())
    }

    @Test
    fun `rollback fails when no last good snapshot exists`() {
        val m = manifest()
        TransactionInstaller.getCurrentFile(context, m).apply { writeText("BAD") }
        assertFalse(TransactionInstaller.rollback(context, m))
    }

    @Test
    fun `install rejects manifest without sha256`() {
        val m = manifest(sha256 = "")
        val staged = TransactionInstaller.getStagingFile(context, m).apply { writeText("whatever") }

        val result = TransactionInstaller.install(context, m, staged)

        assertTrue(!result.isSuccess)
    }
}
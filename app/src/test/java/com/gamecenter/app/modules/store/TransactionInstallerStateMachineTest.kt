package com.gamecenter.app.modules.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

/**
 * 事务安装状态机契约测试（质量提升计划 §六 ③，staging→current→last_good→quarantine）。
 *
 * 用 .zip 归档名绕开 APK 签名强校验（其信任在下载路径断言），聚焦状态转移本身；
 * SHA 用真实计算值。回滚用例手动放置文件（不经 install 的 setReadOnly 路径），
 * 避免 Windows 只读文件删除语义造成假失败。
 */
@RunWith(RobolectricTestRunner::class)
class TransactionInstallerStateMachineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun sha256(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it) }

    private fun manifest(sha: String, builtIn: Boolean = false) = ModuleManifest(
        id = "testmod",
        name = "Test Module",
        fileName = "testmod_v1.zip",
        sha256 = sha,
        builtIn = builtIn,
    )

    private fun stagingFile(content: ByteArray): File {
        val dir = TransactionInstaller.getStagingDir(context)
        dir.mkdirs()
        return File(dir, "testmod_v1.zip").apply { writeBytes(content) }
    }

    @Test
    fun installMovesStagingToCurrentAndSucceeds() {
        val content = "module-payload-v1".toByteArray()
        val m = manifest(sha256(content))
        val staged = stagingFile(content)

        val result = TransactionInstaller.install(context, m, staged)

        assertTrue("应安装成功: $result", result.isSuccess)
        val current = TransactionInstaller.getCurrentFile(context, m)
        assertTrue("current 应存在", current.exists())
        assertContentEquals(content, current)
        assertFalse("staging 源应被移走", staged.exists())
    }

    @Test
    fun installRejectsShaMismatchAndDeletesFile() {
        val content = "module-payload-v1".toByteArray()
        val m = manifest(sha256("tampered-content".toByteArray()))
        val staged = stagingFile(content)

        val result = TransactionInstaller.install(context, m, staged)

        assertTrue("应失败", result is TransactionInstaller.InstallResult.Failure)
        assertFalse("坏文件应被删除", staged.exists())
        assertFalse("current 不应被写入", TransactionInstaller.getCurrentFile(context, m).exists())
    }

    @Test
    fun installRejectsEmptyShaForNonBuiltIn() {
        val content = "module-payload".toByteArray()
        val m = manifest("")
        val staged = stagingFile(content)

        val result = TransactionInstaller.install(context, m, staged)

        assertTrue("非内置模块空 SHA 必须拒绝", result is TransactionInstaller.InstallResult.Failure)
    }

    @Test
    fun installAllowsEmptyShaForBuiltIn() {
        val content = "builtin-payload".toByteArray()
        val m = manifest("", builtIn = true)
        val staged = stagingFile(content)

        val result = TransactionInstaller.install(context, m, staged)

        assertTrue("内置模块空 SHA 应放行: $result", result.isSuccess)
    }

    @Test
    fun installBacksUpExistingCurrentToLastGood() {
        val oldContent = "module-payload-old".toByteArray()
        val newContent = "module-payload-new".toByteArray()
        val m = manifest(sha256(newContent))

        val current = TransactionInstaller.getCurrentFile(context, m)
        current.parentFile?.mkdirs()
        current.writeBytes(oldContent)

        val result = TransactionInstaller.install(context, m, stagingFile(newContent))

        assertTrue("应安装成功: $result", result.isSuccess)
        assertContentEquals(oldContent, TransactionInstaller.getLastGoodFile(context, m))
        assertContentEquals(newContent, current)
    }

    @Test
    fun rollbackRestoresLastGoodAndQuarantinesCurrent() {
        val goodContent = "good-v1".toByteArray()
        val badContent = "bad-v2".toByteArray()
        val m = manifest(sha256(badContent))

        val current = TransactionInstaller.getCurrentFile(context, m)
        val lastGood = TransactionInstaller.getLastGoodFile(context, m)
        current.parentFile?.mkdirs()
        lastGood.parentFile?.mkdirs()
        current.writeBytes(badContent)
        lastGood.writeBytes(goodContent)

        assertTrue("回滚应成功", TransactionInstaller.rollback(context, m))
        assertContentEquals(goodContent, current)
        // quarantine 文件名含时间戳（id_ts_fileName），按目录内容断言而非重建路径
        val quarantined = TransactionInstaller.getQuarantineDir(context).listFiles()!!
        assertTrue("问题版本应进 quarantine",
            quarantined.any { it.name.startsWith("testmod_") && it.name.endsWith("testmod_v1.zip") })
    }

    @Test
    fun rollbackFailsWithoutLastGood() {
        val m = manifest(sha256("x".toByteArray()))
        assertFalse("无 last_good 应返回 false", TransactionInstaller.rollback(context, m))
    }

    @Test
    fun cleanStagingEmptiesStagingDir() {
        stagingFile("leftover".toByteArray())
        assertTrue(TransactionInstaller.getStagingDir(context).listFiles()!!.isNotEmpty())

        TransactionInstaller.cleanStaging(context)

        assertTrue("staging 应清空",
            TransactionInstaller.getStagingDir(context).listFiles()!!.isEmpty())
    }

    private fun assertContentEquals(expected: ByteArray, file: File) {
        assertTrue("文件应存在: ${file.absolutePath}", file.exists())
        file.setWritable(true) // install/rollback 会 setReadOnly，Windows 下读取前先解锁保险
        assertEquals(String(expected), file.readText())
    }
}

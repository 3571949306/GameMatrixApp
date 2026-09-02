package com.gamecenter.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 预装提取跳过/重提判定标准的守卫测试（BL：预装 APK 每次冷启重复提取）。
 *
 * App.extractPreinstalledModules 的"已存在则跳过"判定 = 文件存在且 validatePreinstalledApk 通过。
 * 此前判定用 AssetInputStream.available() 与解压产物长度比对——压缩 asset 下恒失配，
 * 导致 31 个预装 APK 每次冷启全部重复提取。本测试锁定新的判定标准：
 * ZIP 可读且含 AndroidManifest.xml 即视为已就位。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreinstallApkValidationTest {

    private fun newTempFile(name: String): File =
        File.createTempFile(name, ".apk", System.getProperty("java.io.tmpdir")?.let { File(it) })

    private fun writeValidApk(target: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("<manifest/>".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(64))
            zos.closeEntry()
        }
    }

    @Test
    fun `valid zip with manifest is accepted`() {
        val f = newTempFile("valid").apply { writeValidApk(this) }
        assertTrue(validatePreinstalledApk(f))
        f.delete()
    }

    @Test
    fun `zip without manifest entry is rejected`() {
        val f = newTempFile("nomanifest")
        ZipOutputStream(f.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(ByteArray(32))
            zos.closeEntry()
        }
        assertFalse(validatePreinstalledApk(f))
        f.delete()
    }

    @Test
    fun `empty file is rejected`() {
        val f = newTempFile("empty").apply { createNewFile() }
        assertFalse(validatePreinstalledApk(f))
        f.delete()
    }

    @Test
    fun `missing file is rejected`() {
        val f = newTempFile("missing").apply { delete() }
        assertFalse(validatePreinstalledApk(f))
    }

    @Test
    fun `corrupted non-zip bytes are rejected`() {
        val f = newTempFile("corrupt").apply { writeBytes(ByteArray(256) { it.toByte() }) }
        assertFalse(validatePreinstalledApk(f))
        f.delete()
    }
}

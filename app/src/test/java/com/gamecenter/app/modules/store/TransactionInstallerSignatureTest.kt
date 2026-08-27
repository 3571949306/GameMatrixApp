package com.gamecenter.app.modules.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gamecenter.app.core.common.ModuleManifest
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #6 回归：事务安装必须执行 APK 发布证书钉扎（签名）校验，不得只做 SHA-256。
 *
 * 修复前：install() 仅校验 SHA-256，伪造/未签名 APK 也会返回 Success；
 * 修复后：与 ModuleDownloader 下载路径一致，未签名 APK 一律 Failure。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionInstallerSignatureTest {

    @Test
    fun `install rejects unsigned apk in transaction path`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 随机伪造的 .apk 内容（非合法签名 APK），但 SHA-256 与其自身匹配
        val junk = ByteArray(1024) { (it % 251).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(junk)
            .joinToString("") { "%02x".format(it) }
        val apk = File(context.cacheDir, "unsigned-test.apk")
        apk.writeBytes(junk)

        val manifest = ModuleManifest(
            id = "sig-test",
            name = "sig-test",
            fileName = "unsigned-test.apk",
            sha256 = sha,
            versionCode = 1,
            builtIn = false,
        )

        val result = TransactionInstaller.install(context, manifest, apk)

        assertTrue(
            "未签名 APK 必须被事务安装拒绝（sha256 匹配也不得放行）",
            result is TransactionInstaller.InstallResult.Failure
        )
    }
}
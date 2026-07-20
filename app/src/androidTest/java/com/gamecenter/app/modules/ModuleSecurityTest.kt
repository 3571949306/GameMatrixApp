package com.gamecenter.app.modules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 动态装载签名防御自动化测试类。
 */
@RunWith(AndroidJUnit4::class)
class ModuleSecurityTest {

    @Test
    fun testUnsignedApkRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyApk = File(context.cacheDir, "unsigned_test.apk")
        dummyApk.writeText("This is an invalid dummy unsigned APK content")

        val manifest = ModuleManifest(
            id = "security_test",
            name = "Security test",
            description = "Unsigned APK rejection test",
            versionName = "1.0.0",
            versionCode = 1,
            entryClass = "com.gamecenter.app.security_test.TestModule",
            fileName = "unsigned_test.apk",
            fileSize = dummyApk.length(),
            sha256 = "",
            downloadUrl = ""
        )

        val moduleFile = ModuleDownloader.getModuleFile(context, manifest)
        moduleFile.parentFile?.mkdirs()
        dummyApk.copyTo(moduleFile, overwrite = true)

        val result = ModuleLoader.loadModule(context, manifest)
        assertNull(result)

        if (dummyApk.exists()) {
            dummyApk.delete()
        }
        if (moduleFile.exists()) {
            moduleFile.delete()
        }
    }

    @Test
    fun testVerifyApkSignatureDirectlyViaReflection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyApk = File(context.cacheDir, "unsigned_test_2.apk")
        dummyApk.writeText("Another dummy content")

        val method = ModuleLoader::class.java.getDeclaredMethod(
            "verifyApkSignature",
            Context::class.java,
            File::class.java
        )
        method.isAccessible = true

        val isValid = method.invoke(ModuleLoader, context, dummyApk) as Boolean
        assertFalse(isValid)

        if (dummyApk.exists()) {
            dummyApk.delete()
        }
    }
}

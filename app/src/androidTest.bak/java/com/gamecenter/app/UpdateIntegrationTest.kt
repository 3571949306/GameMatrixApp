package com.gamecenter.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamecenter.app.update.UpdateInfo
import com.gamecenter.app.update.UpdateManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateIntegrationTest {

    @Test
    fun testAppContextIsAvailable() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.gamecenter.app", appContext.packageName)
    }

    @Test
    fun testUpdateInfoParsing() {
        val testJson = """
            {
                "versionCode": 100,
                "versionName": "1.0.0",
                "channel": "beta",
                "isBeta": true,
                "lastStableVersionCode": 90,
                "lastStableVersionName": "0.9.0",
                "betaNoticeVersionGap": 5,
                "apkName": "app-beta.apk",
                "changelog": "Test changelog"
            }
        """.trimIndent()

        val updateInfo = UpdateInfo.fromJson(testJson)
        assertNotNull(updateInfo)
        assertEquals(100, updateInfo?.versionCode)
        assertEquals("1.0.0", updateInfo?.versionName)
    }
}

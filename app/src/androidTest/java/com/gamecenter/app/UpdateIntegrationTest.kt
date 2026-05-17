package com.gamecenter.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gamecenter.app.update.UpdateInfo
import org.json.JSONObject
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
        val json = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("channel", "beta")
            put("isBeta", true)
            put("lastStableVersionCode", 90)
            put("lastStableVersionName", "0.9.0")
            put("betaNoticeVersionGap", 5)
            put("apkName", "app-beta.apk")
            put("changelog", "Test changelog")
        }

        val updateInfo = UpdateInfo.fromJson(json)
        assertNotNull(updateInfo)
        assertEquals(100, updateInfo?.versionCode)
        assertEquals("1.0.0", updateInfo?.versionName)
    }
}

package com.gamecenter.app.update

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateInfoTest {
    
    @Test
    fun `fromJson parses complete json`() {
        val json = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("channel", "stable")
            put("downloadUrl", "https://example.com/app.apk")
            put("fileSize", 1000000L)
            put("md5", "abc123")
            put("sha256", "a".repeat(64))
            put("githubReleaseTag", "v1.0.0-vc100")
            put("changelog", "Bug fixes")
            put("lastStableVersionCode", 99)
            put("lastStableVersionName", "0.9.0")
            put("betaNoticeVersionGap", 3)
        }
        
        val info = UpdateInfo.fromJson(json)
        
        assertEquals(100, info.getVersionCode())
        assertEquals("1.0.0", info.getVersionName())
        assertEquals("stable", info.getChannel())
        assertEquals("https://example.com/app.apk", info.getDownloadUrl())
        assertEquals(1000000L, info.getFileSize())
        assertEquals("abc123", info.getMd5())
        assertEquals("a".repeat(64), info.getSha256())
        assertEquals("v1.0.0-vc100", info.getReleaseTag())
        assertEquals("Bug fixes", info.getChangelog())
        assertEquals(99, info.getLastStableVersionCode())
        assertEquals("0.9.0", info.getLastStableVersionName())
        assertEquals(3, info.getBetaNoticeVersionGap())
    }
    
    @Test
    fun `fromJson handles missing optional fields`() {
        val json = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
        }
        
        val info = UpdateInfo.fromJson(json)
        
        assertEquals(100, info.getVersionCode())
        assertEquals("1.0.0", info.getVersionName())
        assertEquals("", info.getDownloadUrl())
        assertEquals(0L, info.getFileSize())
        assertEquals("", info.getMd5())
        assertEquals("", info.getSha256())
        assertEquals("", info.getReleaseTag())
    }
    
    @Test
    fun `isBetaRelease returns true for beta channel`() {
        val json = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("channel", "beta")
        }
        
        val info = UpdateInfo.fromJson(json)
        assertTrue(info.isBetaRelease())
    }
    
    @Test
    fun `isBetaRelease returns false for stable channel`() {
        val json = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("channel", "stable")
        }
        
        val info = UpdateInfo.fromJson(json)
        assertFalse(info.isBetaRelease())
    }
    
    @Test
    fun `getFileSizeFormatted formats bytes correctly`() {
        val json = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("fileSize", 1536L)
        }
        
        val info = UpdateInfo.fromJson(json)
        val formatted = info.getFileSizeFormatted()
        assertTrue(formatted.contains("KB"))
    }
    
    @Test
    fun `getChannelLabel returns correct label`() {
        val betaJson = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("channel", "beta")
        }
        
        val stableJson = JSONObject().apply {
            put("versionCode", 100)
            put("versionName", "1.0.0")
            put("channel", "stable")
        }
        
        val betaInfo = UpdateInfo.fromJson(betaJson)
        val stableInfo = UpdateInfo.fromJson(stableJson)
        
        assertEquals("测试版", betaInfo.getChannelLabel())
        assertEquals("正式版", stableInfo.getChannelLabel())
    }
}

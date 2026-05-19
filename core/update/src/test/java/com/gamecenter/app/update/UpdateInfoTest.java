package com.gamecenter.app.update;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class UpdateInfoTest {

    private JSONObject baseJson() throws Exception {
        return new JSONObject()
                .put("hasUpdate", true)
                .put("versionCode", 300)
                .put("versionName", "1.4.0")
                .put("downloadUrl", "https://example.com/app.apk")
                .put("changelog", "- Bug fixes")
                .put("forceUpdate", false)
                .put("fileSize", 10485760L)
                .put("md5", "abc123")
                .put("channel", "stable");
    }

    @Test
    public void fromJson_parsesAllFields() throws Exception {
        UpdateInfo info = UpdateInfo.fromJson(baseJson());

        assertTrue(info.hasUpdate());
        assertEquals(300, info.getVersionCode());
        assertEquals("1.4.0", info.getVersionName());
        assertEquals("https://example.com/app.apk", info.getDownloadUrl());
        assertEquals("- Bug fixes", info.getChangelog());
        assertFalse(info.isForceUpdate());
        assertEquals(10485760L, info.getFileSize());
        assertEquals("abc123", info.getMd5());
        assertEquals("stable", info.getChannel());
        assertFalse(info.isBetaRelease());
    }

    @Test
    public void fromJson_betaChannel() throws Exception {
        JSONObject json = baseJson().put("channel", "beta");
        UpdateInfo info = UpdateInfo.fromJson(json);

        assertEquals("beta", info.getChannel());
        assertTrue(info.isBetaRelease());
    }

    @Test
    public void fromJson_releaseChannelFallback() throws Exception {
        JSONObject json = baseJson().remove("channel").put("releaseChannel", "beta");
        UpdateInfo info = UpdateInfo.fromJson(json);

        assertEquals("beta", info.getChannel());
        assertTrue(info.isBetaRelease());
    }

    @Test
    public void fromJson_isBetaBooleanFallback() throws Exception {
        JSONObject json = baseJson().remove("channel").put("isBeta", true);
        UpdateInfo info = UpdateInfo.fromJson(json);

        assertEquals("beta", info.getChannel());
        assertTrue(info.isBetaRelease());
    }

    @Test
    public void fromJson_versionNameBetaFallback() throws Exception {
        JSONObject json = baseJson().remove("channel").put("versionName", "1.4.0-beta1");
        UpdateInfo info = UpdateInfo.fromJson(json);

        assertEquals("beta", info.getChannel());
        assertTrue(info.isBetaRelease());
    }

    @Test
    public void fromJson_defaultChannelIsStable() throws Exception {
        JSONObject json = baseJson().remove("channel");
        UpdateInfo info = UpdateInfo.fromJson(json);

        assertEquals("stable", info.getChannel());
        assertFalse(info.isBetaRelease());
    }

    @Test
    public void fromJson_downloadUrlFallbacks() throws Exception {
        JSONObject json = baseJson().remove("downloadUrl").put("apkUrl", "https://alt.com/app.apk");
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals("https://alt.com/app.apk", info.getDownloadUrl());

        json = baseJson().remove("downloadUrl").remove("apkUrl").put("url", "https://third.com/app.apk");
        info = UpdateInfo.fromJson(json);
        assertEquals("https://third.com/app.apk", info.getDownloadUrl());
    }

    @Test
    public void fromJson_stableVersionCodeFallbacks() throws Exception {
        JSONObject json = baseJson()
                .put("lastStableVersionCode", 280)
                .put("stableVersionCode", 270)
                .put("releaseVersionCode", 260);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals(280, info.getLastStableVersionCode());

        json = baseJson().remove("lastStableVersionCode").put("stableVersionCode", 270);
        info = UpdateInfo.fromJson(json);
        assertEquals(270, info.getLastStableVersionCode());

        json = baseJson().remove("lastStableVersionCode").remove("stableVersionCode").put("releaseVersionCode", 260);
        info = UpdateInfo.fromJson(json);
        assertEquals(260, info.getLastStableVersionCode());
    }

    @Test
    public void fromJson_betaNoticeVersionGap_defaultIs3() throws Exception {
        JSONObject json = baseJson();
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals(3, info.getBetaNoticeVersionGap());
    }

    @Test
    public void fromJson_betaNoticeVersionGap_customValue() throws Exception {
        JSONObject json = baseJson().put("betaNoticeVersionGap", 5);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals(5, info.getBetaNoticeVersionGap());
    }

    @Test
    public void fromJson_betaNoticeVersionGap_invalidValue_usesDefault() throws Exception {
        JSONObject json = baseJson().put("betaNoticeVersionGap", 0);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals(3, info.getBetaNoticeVersionGap());

        json = baseJson().put("betaNoticeVersionGap", -1);
        info = UpdateInfo.fromJson(json);
        assertEquals(3, info.getBetaNoticeVersionGap());
    }

    @Test
    public void getFileSizeFormatted_unknownSize() throws Exception {
        JSONObject json = baseJson().put("fileSize", 0);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals("未知", info.getFileSizeFormatted());
    }

    @Test
    public void getFileSizeFormatted_bytes() throws Exception {
        JSONObject json = baseJson().put("fileSize", 512);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals("512 B", info.getFileSizeFormatted());
    }

    @Test
    public void getFileSizeFormatted_kilobytes() throws Exception {
        JSONObject json = baseJson().put("fileSize", 1536);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals("1.5 KB", info.getFileSizeFormatted());
    }

    @Test
    public void getFileSizeFormatted_megabytes() throws Exception {
        JSONObject json = baseJson().put("fileSize", 10485760L);
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals("10.0 MB", info.getFileSizeFormatted());
    }

    @Test
    public void fromJson_testChannel_treatedAsBeta() throws Exception {
        JSONObject json = baseJson().put("channel", "testing");
        UpdateInfo info = UpdateInfo.fromJson(json);
        assertEquals("beta", info.getChannel());
        assertTrue(info.isBetaRelease());
    }

    @Test
    public void fromJson_missingOptionalFields_defaults() throws Exception {
        JSONObject json = new JSONObject().put("versionCode", 100);
        UpdateInfo info = UpdateInfo.fromJson(json);

        assertFalse(info.hasUpdate());
        assertEquals(100, info.getVersionCode());
        assertEquals("", info.getVersionName());
        assertEquals("", info.getDownloadUrl());
        assertEquals("", info.getChangelog());
        assertFalse(info.isForceUpdate());
        assertEquals(0L, info.getFileSize());
        assertEquals("", info.getMd5());
        assertEquals("stable", info.getChannel());
        assertEquals(0, info.getLastStableVersionCode());
        assertEquals("", info.getLastStableVersionName());
    }

    @Test
    public void setLocalVersion_storesValues() throws Exception {
        UpdateInfo info = UpdateInfo.fromJson(baseJson());
        info.setLocalVersion(250, "1.3.25");
        assertEquals(250, info.getLocalVersionCode());
        assertEquals("1.3.25", info.getLocalVersionName());
    }

    @Test
    public void setBetaUpdateBlocked_and_outdated() throws Exception {
        UpdateInfo info = UpdateInfo.fromJson(baseJson());
        info.setBetaUpdateBlocked(true);
        info.setBetaUpdateOutdated(true);
        assertTrue(info.isBetaUpdateBlocked());
        assertTrue(info.isBetaUpdateOutdated());
    }
}

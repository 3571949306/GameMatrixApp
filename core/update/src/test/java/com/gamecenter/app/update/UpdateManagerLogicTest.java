package com.gamecenter.app.update;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateManagerLogicTest {

    @Test
    public void trimTrailingSlash_removesAllTrailingSlashes() {
        assertEquals("https://example.com/api", UpdateManager.trimTrailingSlash("https://example.com/api///"));
        assertEquals("", UpdateManager.trimTrailingSlash("/"));
        assertEquals("", UpdateManager.trimTrailingSlash(null));
    }

    @Test
    public void buildVersionJsonUrl_usesChannelFiles() {
        UpdateChecker checker = new UpdateChecker();

        assertEquals("https://example.com/version-beta.json",
                checker.buildVersionJsonUrl("https://example.com/", true));
        assertEquals("https://example.com/version-release.json",
                checker.buildVersionJsonUrl("https://example.com", false));
        assertEquals("https://example.com/custom.json",
                checker.buildVersionJsonUrl("https://example.com/custom.json", true));
    }

    @Test
    public void resolveRelativeUrl_resolvesAgainstVersionJsonUrl() {
        UpdateChecker checker = new UpdateChecker();

        assertEquals("https://example.com/update/app-beta.apk",
                checker.resolveRelativeUrl("https://example.com/update/version-beta.json", "app-beta.apk"));
        assertEquals("https://example.com/app/app-beta.apk",
                checker.resolveRelativeUrl("https://example.com/update/version-beta.json", "/app/app-beta.apk"));
        assertEquals("https://cdn.example.com/app.apk",
                checker.resolveRelativeUrl("https://example.com/version-beta.json", "https://cdn.example.com/app.apk"));
    }

    @Test
    public void shouldOfferUpdate_respectsVersionAndBetaSetting() throws Exception {
        UpdateChecker checker = new UpdateChecker();
        UpdateChecker.LocalVersion local = new UpdateChecker.LocalVersion();
        local.versionCode = 100;
        local.versionName = "1.0.0";

        UpdateInfo stable = UpdateInfo.fromJson(new JSONObject()
                .put("versionCode", 101)
                .put("versionName", "1.0.1")
                .put("channel", "stable"));
        UpdateInfo beta = UpdateInfo.fromJson(new JSONObject()
                .put("versionCode", 102)
                .put("versionName", "1.0.2-beta")
                .put("channel", "beta"));
        UpdateInfo old = UpdateInfo.fromJson(new JSONObject()
                .put("versionCode", 99)
                .put("versionName", "0.9.9")
                .put("channel", "stable"));

        assertTrue(checker.shouldOfferUpdate(stable, local, false));
        assertTrue(checker.shouldOfferUpdate(beta, local, true));
        assertFalse(checker.shouldOfferUpdate(beta, local, false));
        assertFalse(checker.shouldOfferUpdate(old, local, true));
    }

    @Test
    public void applyUpdatePolicy_marksBlockedBetaAsNoUpdate() throws Exception {
        UpdateChecker checker = new UpdateChecker();
        UpdateChecker.LocalVersion local = new UpdateChecker.LocalVersion();
        local.versionCode = 100;
        local.versionName = "1.0.0";

        UpdateInfo beta = UpdateInfo.fromJson(new JSONObject()
                .put("versionCode", 110)
                .put("versionName", "1.1.0-beta")
                .put("channel", "beta")
                .put("lastStableVersionCode", 105)
                .put("betaNoticeVersionGap", 3));

        checker.applyUpdatePolicy(beta, local, false);

        assertFalse(beta.hasUpdate());
        assertTrue(beta.isBetaUpdateBlocked());
        assertTrue(beta.isBetaUpdateOutdated());
    }

    @Test
    public void updateInfo_parsesAliasesAndFormatsFileSize() throws Exception {
        UpdateInfo info = UpdateInfo.fromJson(new JSONObject()
                .put("versionCode", 200)
                .put("versionName", "2.0.0-beta")
                .put("apkUrl", "app.apk")
                .put("fileSize", 1536L)
                .put("isBeta", true));

        assertEquals(200, info.getVersionCode());
        assertEquals("app.apk", info.getDownloadUrl());
        assertTrue(info.isBetaRelease());
        assertEquals("测试版", info.getChannelLabel());
        assertEquals("1.5 KB", info.getFileSizeFormatted());
    }
}

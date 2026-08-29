package com.gamecenter.app.update;

import org.junit.Test;

import java.net.URL;

import org.json.JSONObject;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UpdateUrlValidatorTest {

    @Test
    public void acceptsHttpsAndRejectsCleartextOrAmbiguousUrls() throws Exception {
        assertTrue(UpdateUrlValidator.isValidHttpsUrl("https://updates.example.test/version.json"));
        assertTrue(UpdateUrlValidator.isValidHttpsUrl("https://updates.example.test:8443/a?token=x"));

        assertFalse(UpdateUrlValidator.isValidHttpsUrl("http://updates.example.test/version.json"));
        assertFalse(UpdateUrlValidator.isValidHttpsUrl("https://user:pass@updates.example.test/version.json"));
        assertFalse(UpdateUrlValidator.isValidHttpsUrl("https://updates.example.test/version.json#fragment"));
        assertFalse(UpdateUrlValidator.isValidHttpsUrl("https://updates.example.test:99999/version.json"));
        assertFalse(UpdateUrlValidator.isValidHttpsUrl("https://updates.example.test:0/version.json"));
        assertFalse(UpdateUrlValidator.isValidHttpsUrl(" https://updates.example.test/version.json"));
    }

    @Test
    public void validatesRedirectTargetsAndChecksums() throws Exception {
        UpdateUrlValidator.requireHttpsUrl(new URL("https://github.com/example/release"));
        assertTrue(UpdateUrlValidator.resolveHttpsRedirect(
                new URL("https://updates.example.test/releases/version.json"),
                "../v2/version.json").endsWith("/v2/version.json"));
        try {
            UpdateUrlValidator.resolveHttpsRedirect(
                    new URL("https://updates.example.test/releases/version.json"),
                    "http://updates.example.test/v2/version.json");
            fail("Expected cleartext redirect rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        UpdateUrlValidator.requireSha256("a".repeat(64));
        UpdateUrlValidator.requireMd5("b".repeat(32));

        assertFalse(UpdateUrlValidator.isValidSha256("a".repeat(63)));
        assertFalse(UpdateUrlValidator.isValidSha256("g".repeat(64)));
        assertFalse(UpdateUrlValidator.isValidMd5("b".repeat(31)));
        assertFalse(UpdateUrlValidator.isValidMd5("g".repeat(32)));
    }

    @Test
    public void keepsRedirectsAndMetadataDownloadsOnApprovedOrigins() throws Exception {
        String source = "https://updates.example.test:8443/releases/version.json";
        assertTrue(UpdateUrlValidator.isAllowedHttpsUrl(
                "https://updates.example.test:8443/releases/app.apk", source));
        assertTrue(UpdateUrlValidator.isAllowedHttpsUrl(
                "https://release-assets.githubusercontent.com/github-production-release-asset/app.apk",
                source));
        assertFalse(UpdateUrlValidator.isAllowedHttpsUrl(
                "https://unrelated.example.test/releases/app.apk", source));

        try {
            UpdateUrlValidator.resolveHttpsRedirect(
                    new URL(source), "https://unrelated.example.test/app.apk", source);
            fail("Expected off-origin redirect rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }

    }

    @Test
    public void rejectsInvalidChecksumWithAnActionableException() {
        try {
            UpdateUrlValidator.requireSha256("missing");
            fail("Expected checksum rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("64"));
        }
    }

    @Test
    public void downloaderRejectsUnsafeTransportAndUnsignedMetadataBeforeOpeningFiles() throws Exception {
        UpdateDownloader downloader = new UpdateDownloader(null);
        UpdateInfo unsigned = UpdateInfo.fromJson(new JSONObject()
                .put("versionCode", 1)
                .put("versionName", "1.0")
                .put("fileSize", 1));

        try {
            downloader.downloadFromUrl(null, unsigned,
                    "http://updates.example.test/app-release.apk", null);
            fail("Expected cleartext URL rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            downloader.downloadFromUrl(null, unsigned,
                    "https://github.com/3571949306/GameMatrixApp/releases/latest/download/app-release.apk", null);
            fail("Expected unsigned metadata rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("checksum"));
        }
    }
}

package com.gamecenter.app.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.core.security.DownloadSecurityValidator;

import org.junit.Test;

/** Phase A-2 / B 危险扩展名与安全落盘策略单测。 */
public class SecurityPolicyTest {

    @Test
    public void dangerousExtensionsBlockedCaseInsensitively() {
        assertTrue(DownloadSecurityValidator.isDangerousExtension("evil.exe"));
        assertTrue(DownloadSecurityValidator.isDangerousExtension("x.MSI"));
        assertFalse(DownloadSecurityValidator.isDangerousExtension("movie.mp4"));
        assertFalse(DownloadSecurityValidator.isDangerousExtension("noext"));
    }

    @Test
    public void policyRoutesDangerousToPrivateAppDir() {
        assertEquals(DownloadSecurityValidator.TargetPolicy.PRIVATE_APP_DIR,
                DownloadSecurityValidator.policyFor("a.apk"));
        assertEquals(DownloadSecurityValidator.TargetPolicy.PUBLIC,
                DownloadSecurityValidator.policyFor("a.mp4"));
    }

    @Test
    public void dangerousMimeTypeRoutesAFileWithoutExecutableExtensionToPrivateAppDir() {
        assertTrue(DownloadSecurityValidator.isDangerousMimeType(
                "application/vnd.android.package-archive; charset=binary"));
        assertEquals(DownloadSecurityValidator.TargetPolicy.PRIVATE_APP_DIR,
                DownloadSecurityValidator.policyFor("download", "application/x-msdownload"));
        assertEquals(DownloadSecurityValidator.TargetPolicy.PUBLIC,
                DownloadSecurityValidator.policyFor("download", "application/octet-stream"));
    }

    @Test
    public void quarantineAppendsSuffix() {
        assertEquals("a.apk.gmdownload", DownloadSecurityValidator.quarantineName("a.apk"));
        assertEquals("a.mp4", DownloadSecurityValidator.quarantineName("a.mp4"));
    }
}

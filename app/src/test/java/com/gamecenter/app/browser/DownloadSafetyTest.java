package com.gamecenter.app.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.core.security.DownloadSecurityValidator;
import com.gamecenter.app.browser.core.security.FileNameSanitizer;

import org.junit.Test;

/** Phase B 下载安全单测：路径穿越净化 + 危险文件落盘策略。 */
public class DownloadSafetyTest {

    @Test
    public void sanitizeRemovesPathSeparators() {
        // '/' 被替换为 '_'，不会残留目录穿越
        String safe = FileNameSanitizer.sanitize("a/../../b");
        assertFalse(safe.contains("/"));
    }

    @Test
    public void dangerousDownloadRoutedToPrivateDirAndQuarantined() {
        assertEquals(DownloadSecurityValidator.TargetPolicy.PRIVATE_APP_DIR,
                DownloadSecurityValidator.policyFor("setup.exe"));
        assertTrue(DownloadSecurityValidator.quarantineName("setup.exe").endsWith(".gmdownload"));
    }

    @Test
    public void safeDownloadGoesPublic() {
        assertEquals(DownloadSecurityValidator.TargetPolicy.PUBLIC,
                DownloadSecurityValidator.policyFor("report.pdf"));
    }
}

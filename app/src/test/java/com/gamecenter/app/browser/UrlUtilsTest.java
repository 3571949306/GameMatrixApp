package com.gamecenter.app.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.util.UrlUtils;

import org.junit.Test;

/** Phase A-2 基础工具单测：危险协议拦截、host 提取、输入规整。 */
public class UrlUtilsTest {

    @Test
    public void isDangerousScheme_detectsBlockedSchemes() {
        assertTrue(UrlUtils.isDangerousScheme("javascript:alert(1)"));
        assertTrue(UrlUtils.isDangerousScheme("  data:text/html,<b>"));
        assertTrue(UrlUtils.isDangerousScheme("intent://scan/#Intent;scheme=..."));
    }

    @Test
    public void isDangerousScheme_allowsSafe() {
        assertFalse(UrlUtils.isDangerousScheme("https://example.com"));
        assertFalse(UrlUtils.isDangerousScheme("http://example.com"));
        assertFalse(UrlUtils.isDangerousScheme(null));
    }

    @Test
    public void getHost_extractsDomain() {
        assertEquals("a.example.com", UrlUtils.getHost("https://a.example.com/path?x=1"));
        assertEquals("example.com", UrlUtils.getHost("http://example.com:8080/"));
        assertEquals("", UrlUtils.getHost(null));
    }

    @Test
    public void processInput_rejectsDangerousScheme() {
        assertNull(UrlUtils.processInput("javascript:alert(1)"));
        assertNull(UrlUtils.processInput("file:///etc/passwd"));
    }

    @Test
    public void processInput_wrapsBareDomainWithHttps() {
        String out = UrlUtils.processInput("example.com");
        assertTrue(out != null && out.startsWith("https://"));
    }

    @Test
    public void normalizeWebUrl_rejectsIncompleteOrInvalidNetworkTargets() {
        assertNull(UrlUtils.normalizeWebUrl("https://"));
        assertNull(UrlUtils.normalizeWebUrl("http://example.com:99999"));
        assertNull(UrlUtils.normalizeWebUrl("999.1.1.1"));
        assertNull(UrlUtils.normalizeWebUrl("https://example.com/has space"));
        assertNull(UrlUtils.normalizeWebUrl("https://user:pass@example.com"));
    }

    @Test
    public void normalizeWebUrl_acceptsLocalhostAndValidIpv4() {
        assertEquals("https://localhost:8080/path",
                UrlUtils.normalizeWebUrl("localhost:8080/path"));
        assertEquals("https://192.168.1.10:8443/path",
                UrlUtils.normalizeWebUrl("192.168.1.10:8443/path"));
    }

    @Test
    public void processInput_searchesInvalidNetworkLookingTextRatherThanReturningIt() {
        String invalidPort = UrlUtils.processInput("https://example.com:99999");
        assertTrue(invalidPort != null && invalidPort.startsWith(UrlUtils.SEARCH_ENGINE_URL));
    }
}

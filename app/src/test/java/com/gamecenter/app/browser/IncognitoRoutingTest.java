package com.gamecenter.app.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.core.BrowserTabManager;
import com.gamecenter.app.browser.core.incognito.IncognitoProfileManager;

import org.junit.Test;

/** Phase C 无痕路由单测：isIncognito 是否正确下沉到 Tab 维度。 */
public class IncognitoRoutingTest {

    @Test
    public void isIncognitoDelegatesToTab() {
        BrowserTabManager.Tab normal = new BrowserTabManager.Tab("t1", "T", "https://x.com");
        BrowserTabManager.Tab inc = new BrowserTabManager.Tab("t2", "I", "https://x.com");
        inc.setIncognito(true);

        assertFalse(IncognitoProfileManager.isIncognito(normal));
        assertTrue(IncognitoProfileManager.isIncognito(inc));
    }

    @Test
    public void applyProfileDoesNotClaimStrongIsolationWithoutAnIndependentProfile() {
        BrowserTabManager.Tab inc = new BrowserTabManager.Tab("t3", "I", "https://x.com");
        inc.setIncognito(true);
        // 真实 WebView 会启用弱隐私配置；null 输入用于证明该 API 不会
        // 以全局 Cookie 清理来伪造强隔离，返回值仍严格表示强隔离状态。
        assertFalse(IncognitoProfileManager.applyProfile(null, inc));
    }
}

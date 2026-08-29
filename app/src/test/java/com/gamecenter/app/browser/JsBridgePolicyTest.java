package com.gamecenter.app.browser;

import static org.junit.Assert.assertNotNull;

import com.gamecenter.app.browser.bridge.BrowserJsBridge;

import org.junit.Test;

/**
 * Phase A-2 JS Bridge 域白名单策略单测（框架占位）。
 *
 * <p>真实断言（允许调用的域清单、被拒域日志 {@code BROWSER_JSBRIDGE_REJECTED}）待补；
 * 当前仅校验桥类可加载。域白名单的静态契约另由 {@code scripts/verify_browser.py} 把关。
 */
public class JsBridgePolicyTest {

    @Test
    public void bridgeClassLoads() {
        assertNotNull(BrowserJsBridge.class);
    }
}

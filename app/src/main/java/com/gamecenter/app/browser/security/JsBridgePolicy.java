package com.gamecenter.app.browser.security;

import com.gamecenter.app.browser.util.UrlUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * JSBridge 安全策略 - 域名白名单控制。
 */
public class JsBridgePolicy {

    private static volatile JsBridgePolicy instance;
    private final Set<String> trustedDomains = new HashSet<>(Arrays.asList(
        "gamecenter.app", "www.gamecenter.app"
    ));

    private JsBridgePolicy() {}

    public static JsBridgePolicy getInstance() {
        if (instance == null) { synchronized (JsBridgePolicy.class) { if (instance == null) instance = new JsBridgePolicy(); } }
        return instance;
    }

    public boolean canUseJsBridge(String url) {
        if (url == null || !UrlUtils.isSecure(url)) return false;
        return isTrustedDomain(url);
    }

    public boolean isTrustedDomain(String url) {
        String host = UrlUtils.getHost(url).toLowerCase();
        for (String domain : trustedDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    public void addTrustedDomain(String domain) { trustedDomains.add(domain.toLowerCase()); }
    public void removeTrustedDomain(String domain) { trustedDomains.remove(domain.toLowerCase()); }
}

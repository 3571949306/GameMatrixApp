package com.gamecenter.app.browser.security;

import com.gamecenter.app.browser.util.UrlUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 域名信任管理 - 三级分类。
 */
public class DomainTrustManager {

    public enum DomainLevel { TRUSTED, NORMAL, BLOCKED }

    private static volatile DomainTrustManager instance;
    private final Set<String> trustedDomains = new HashSet<>();
    private final Set<String> blockedDomains = new HashSet<>();

    private DomainTrustManager() {}

    public static DomainTrustManager getInstance() {
        if (instance == null) { synchronized (DomainTrustManager.class) { if (instance == null) instance = new DomainTrustManager(); } }
        return instance;
    }

    public DomainLevel getDomainLevel(String url) {
        String host = UrlUtils.getHost(url).toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return DomainLevel.NORMAL;
        for (String d : blockedDomains) { if (host.equals(d) || host.endsWith("." + d)) return DomainLevel.BLOCKED; }
        for (String d : trustedDomains) { if (host.equals(d) || host.endsWith("." + d)) return DomainLevel.TRUSTED; }
        return DomainLevel.NORMAL;
    }

    public void addTrustedDomain(String domain) { trustedDomains.add(domain.toLowerCase(Locale.ROOT)); }
    public void addBlockedDomain(String domain) { blockedDomains.add(domain.toLowerCase(Locale.ROOT)); }
    public void removeTrustedDomain(String domain) { trustedDomains.remove(domain.toLowerCase(Locale.ROOT)); }
    public void removeBlockedDomain(String domain) { blockedDomains.remove(domain.toLowerCase(Locale.ROOT)); }
}

package com.gamecenter.app.browser.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ad blocker with domain blacklist and whitelist support.
 */
public class AdBlocker {

    private static volatile AdBlocker instance;
    private final Set<String> blockedDomains = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "admaster.com.cn", "cpro.baidu.com", "pos.baidu.com", "ad.xiaomi.com",
        "ads.yahoo.com", "advertising.com", "adcolony.com", "unityads.unity3d.com",
        "facebook.com/audience_network", "adservice.google.com"
    ));
    private final Set<String> whitelistDomains = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger blockedCount = new AtomicInteger(0);
    private boolean enabled = true;

    private AdBlocker() {}

    public static AdBlocker getInstance() {
        if (instance == null) { synchronized (AdBlocker.class) { if (instance == null) instance = new AdBlocker(); } }
        return instance;
    }

    public boolean shouldBlock(String url) {
        if (!enabled || url == null) return false;
        String host = extractHost(url).toLowerCase();
        if (host.isEmpty()) return false;

        synchronized (whitelistDomains) {
            for (String domain : whitelistDomains) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return false;
                }
            }
        }

        for (String domain : blockedDomains) {
            if (host.equals(domain) || host.endsWith("." + domain)) { blockedCount.incrementAndGet(); return true; }
        }
        return false;
    }

    public int getBlockedCount() { return blockedCount.get(); }
    public void resetBlockedCount() { blockedCount.set(0); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void addWhitelistDomain(String domain) {
        if (domain != null && !domain.isEmpty()) { whitelistDomains.add(domain.toLowerCase()); }
    }

    public void removeWhitelistDomain(String domain) {
        if (domain != null) { whitelistDomains.remove(domain.toLowerCase()); }
    }

    public Set<String> getWhitelistDomains() { return new HashSet<>(whitelistDomains); }

    public boolean isWhitelisted(String url) {
        String host = extractHost(url).toLowerCase();
        synchronized (whitelistDomains) {
            for (String domain : whitelistDomains) {
                if (host.equals(domain) || host.endsWith("." + domain)) { return true; }
            }
        }
        return false;
    }

    private String extractHost(String url) {
        try { java.net.URI uri = java.net.URI.create(url); return uri.getHost() != null ? uri.getHost() : ""; } catch (Exception e) { return ""; }
    }
}

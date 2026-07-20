package com.gamecenter.app.browser.security;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 追踪保护拦截器（P1-2）。
 * <p>专注于拦截第三方追踪器（analytics / tracking pixels / fingerprinting），
 * 与 {@link AdBlocker} 互不干扰；统计由 {@link BrowserTrackerStats} 负责。</p>
 */
public class BrowserTrackerBlocker {

    private static volatile BrowserTrackerBlocker instance;

    /** 内置追踪器域名黑名单（含国际 + 国内常见） */
    private static final Set<String> TRACKER_DOMAINS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Google Analytics / Ads
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "adservice.google.com",
            "doubleclick.net",
            // Facebook / Meta Pixel
            "connect.facebook.net",
            "facebook.com/tr",
            "facebook.net",
            // Twitter
            "analytics.twitter.com",
            "ads-twitter.com",
            // Microsoft / LinkedIn
            "bat.bing.com",
            "clarity.ms",
            "px.ads.linkedin.com",
            // Amazon
            "amazon-adsystem.com",
            // 国内：百度统计 / 51la / 友盟 / 神策 / growingio / 诸葛IO
            "hm.baidu.com",
            "pos.baidu.com",
            "cpro.baidu.com",
            "google.dg.baidu.com",
            "51.la",
            "umeng.com",
            "umeng.co",
            "umengcloud.com",
            "sensorsdata.cn",
            "api.sensorsdata.cn",
            "growingio.com",
            "v1.growingio.com",
            "zhugeio.com",
            "talkingdata.com",
            // 国内：腾讯 / 京东 / 阿里
            "beacon.qq.com",
            "mta.qq.com",
            "jd.com/log",
            "acs.jd.com",
            "retcode.taobao.com",
            "arms-retcode.aliyuncs.com",
            // Adobe Analytics
            "omtrdc.net",
            "demdex.net",
            // Comscore / Chartbeat / Hotjar / Mixpanel / Amplitude
            "scorecardresearch.com",
            "chartbeat.com",
            "chartbeat.net",
            "hotjar.com",
            "mixpanel.com",
            "amplitude.com",
            // Criteo / Taboola / Outbrain
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            // Yandex / Mail.ru
            "mc.yandex.ru",
            "mc.webvisor.ru",
            "top.mail.ru",
            // 其他
            "segment.io",
            "segment.com",
            "branch.io",
            "appsflyer.com",
            "adjust.com",
            "adjust.io",
            "singular.net",
            "kochava.com"
    )));

    /** 拦截 URL 路径模式（用于捕获带追踪参数的请求） */
    private static final Set<String> TRACKER_PATH_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/track", "/tracking", "/pixel", "/beacon", "/analytics", "/collect",
            "/log", "/stats", "/telemetry", "/metrics"
    )));

    private final Set<String> whitelistDomains = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean enabled = true;

    private BrowserTrackerBlocker() {}

    public static BrowserTrackerBlocker getInstance() {
        if (instance == null) {
            synchronized (BrowserTrackerBlocker.class) {
                if (instance == null) instance = new BrowserTrackerBlocker();
            }
        }
        return instance;
    }

    /**
     * 判断是否拦截指定 URL。
     * <p>命中规则：</p>
     * <ol>
     *   <li>域名在黑名单中</li>
     *   <li>路径包含追踪关键词（仅当域名不是顶级站点时）</li>
     * </ol>
     */
    public boolean shouldBlock(@NonNull String url) {
        if (!enabled || url == null || url.isEmpty()) return false;
        String host = extractHost(url).toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return false;

        // 白名单优先
        synchronized (whitelistDomains) {
            for (String domain : whitelistDomains) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return false;
                }
            }
        }

        // 域名黑名单
        for (String domain : TRACKER_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }

        // 路径关键词（避免误伤顶级站点如 baidu.com/track?xxx）
        // 仅当 host 不是当前页面顶级域名时才检查路径关键词
        String path = extractPath(url).toLowerCase(Locale.ROOT);
        if (!path.isEmpty()) {
            for (String keyword : TRACKER_PATH_KEYWORDS) {
                if (path.contains(keyword)) {
                    // 二次校验：顶级域名（如 baidu.com / bing.com / google.com）的 path 不拦截
                    if (!isFirstPartyDomain(host)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 判断 host 是否为顶级站点（避免误伤顶级站点的 /track 路径） */
    private boolean isFirstPartyDomain(@NonNull String host) {
        // 简化实现：检查顶级域是否在常见顶级站点列表中
        String[] firstParty = {"baidu.com", "bing.com", "google.com", "duckduckgo.com",
                "github.com", "stackoverflow.com", "zhihu.com", "weibo.com",
                "taobao.com", "tmall.com", "jd.com", "pinduoduo.com",
                "bilibili.com", "youtube.com", "qq.com", "163.com"};
        for (String d : firstParty) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    private String extractHost(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getHost() != null ? uri.getHost() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private String extractPath(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getPath() != null ? uri.getPath() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void addWhitelistDomain(String domain) {
        if (domain != null && !domain.isEmpty()) {
            whitelistDomains.add(domain.toLowerCase(Locale.ROOT));
        }
    }

    public void removeWhitelistDomain(String domain) {
        if (domain != null) whitelistDomains.remove(domain.toLowerCase(Locale.ROOT));
    }

    public Set<String> getWhitelistDomains() { return new HashSet<>(whitelistDomains); }

    /** 获取内置黑名单总数（用于 UI 展示） */
    public int getBuiltinRuleCount() { return TRACKER_DOMAINS.size(); }
}

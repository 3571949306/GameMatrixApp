package com.gamecenter.app.browser.security;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.BuildConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 浏览器安全策略：控制非 http/https 链接的处理方式。
 *
 * <p>安全基线：
 * <ul>
 *   <li>file:// / content:// / javascript: / data: 默认禁止</li>
 *   <li>intent:// / market:// / tel:// / sms:// / weixin:// / alipays:// 等需要用户确认</li>
 *   <li>只有可信应用能处理的外部协议才允许跳转</li>
 * </ul>
 */
public class BrowserSecurityPolicy {

    /**
     * URL 安全判定结果。
     */
    public enum UrlPolicy {
        /** 允许在当前 WebView 加载（http/https） */
        ALLOW_INTERNAL,
        /** 需要用户确认后交给系统处理 */
        CONFIRM_EXTERNAL,
        /** 直接拦截 */
        BLOCK
    }

    /** 默认直接拦截的危险协议 */
    private static final Set<String> BLOCKED_SCHEMES = new HashSet<>(Arrays.asList(
            "file", "content", "javascript", "data", "about"
    ));

    /** 需要弹窗确认的外部协议白名单 */
    private static final Set<String> CONFIRM_SCHEMES = new HashSet<>(Arrays.asList(
            "intent", "market", "tel", "sms", "mailto", "weixin", "alipays",
            "alipay", "mqq", "mqqapi", "mqqwpa", "qqmap", "baidumap", "amap"
    ));

    private static volatile BrowserSecurityPolicy instance;

    private BrowserSecurityPolicy() {}

    public static BrowserSecurityPolicy getInstance() {
        if (instance == null) {
            synchronized (BrowserSecurityPolicy.class) {
                if (instance == null) instance = new BrowserSecurityPolicy();
            }
        }
        return instance;
    }

    /**
     * 判断 URL 应该被如何处理。
     * S4: 即使 BROWSER_SECURITY_POLICY=false，也始终拦截 BLOCKED_SCHEMES（file/javascript 等）。
     */
    @NonNull
    public UrlPolicy checkUrlPolicy(@Nullable String url) {
        if (url == null || url.isEmpty()) return UrlPolicy.BLOCK;
        String scheme = extractScheme(url);
        if (scheme == null || scheme.isEmpty()) return UrlPolicy.BLOCK;

        String lowerScheme = scheme.toLowerCase(Locale.ROOT);

        // S4: 始终拦截危险协议，不受 BROWSER_SECURITY_POLICY 开关影响
        if (BLOCKED_SCHEMES.contains(lowerScheme)) {
            return UrlPolicy.BLOCK;
        }

        if (!BuildConfig.BROWSER_SECURITY_POLICY) {
            // S4: 策略关闭时，除 BLOCKED_SCHEMES 外的协议放行
            return UrlPolicy.ALLOW_INTERNAL;
        }

        if ("http".equals(scheme) || "https".equals(scheme)) {
            return UrlPolicy.ALLOW_INTERNAL;
        }

        if (CONFIRM_SCHEMES.contains(lowerScheme)) {
            return UrlPolicy.CONFIRM_EXTERNAL;
        }
        // 未知协议默认拦截
        return UrlPolicy.BLOCK;
    }

    /**
     * 检查是否有外部应用可以处理该 URL。
     */
    public boolean canExternalAppHandle(@NonNull Context context, @NonNull String url) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            return intent.resolveActivity(pm) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private String extractScheme(@NonNull String url) {
        int idx = url.indexOf(":");
        if (idx <= 0) return null;
        // intent://host#Intent;...;end 中冒号前仍是 intent，保持协议提取一致
        String scheme = url.substring(0, idx).toLowerCase(Locale.ROOT);
        // 特殊处理 intent scheme：即使后面结构复杂也返回 intent
        if (scheme.startsWith("intent")) return "intent";
        return scheme;
    }
}

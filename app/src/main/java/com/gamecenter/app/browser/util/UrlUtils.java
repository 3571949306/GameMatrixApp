package com.gamecenter.app.browser.util;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * URL 工具类，处理地址栏输入。
 */
public class UrlUtils {

    public static final String SEARCH_ENGINE_URL = "https://www.baidu.com/s?wd=";

    private static final Pattern IP_PATTERN =
            Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?(/.*)?$");
    private static final Pattern LOCALHOST_PATTERN =
            Pattern.compile("^localhost(:\\d{1,5})?(/.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/.*)?$");

    /**
     * 处理地址栏输入。
     * @return 处理后的 URL，若输入不安全返回 null
     */
    @Nullable
    public static String processInput(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return "https://www.baidu.com";

        String lower = s.toLowerCase();
        // 拦截危险协议
        if (lower.startsWith("file://") || lower.startsWith("content://")
                || lower.startsWith("javascript:") || lower.startsWith("intent://")
                || lower.startsWith("about:") || lower.startsWith("data:")) {
            return null;
        }

        // 已有 http/https 协议
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return s;
        }

        // 域名/IP/localhost 判断
        if (IP_PATTERN.matcher(s).matches()
                || LOCALHOST_PATTERN.matcher(s).matches()
                || DOMAIN_PATTERN.matcher(s).matches()) {
            return "https://" + s;
        }

        // 默认作为搜索关键词（URI 编码）
        return SEARCH_ENGINE_URL + encodeKeyword(s);
    }

    /**
     * URL 编码搜索关键词。
     */
    @NonNull
    public static String encodeKeyword(@NonNull String keyword) {
        try {
            return java.net.URLEncoder.encode(keyword, "UTF-8");
        } catch (Exception e) {
            return Uri.encode(keyword);
        }
    }

    /**
     * 判断是否危险协议。
     */
    public static boolean isDangerousScheme(@Nullable String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase();
        return lower.startsWith("file://") || lower.startsWith("content://")
                || lower.startsWith("javascript:") || lower.startsWith("intent://")
                || lower.startsWith("about:") || lower.startsWith("data:");
    }

    @NonNull
    public static String getHost(@Nullable String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String host = Uri.parse(url).getHost();
            return host != null ? host : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isSecure(@Nullable String url) {
        return url != null && url.startsWith("https://");
    }
}

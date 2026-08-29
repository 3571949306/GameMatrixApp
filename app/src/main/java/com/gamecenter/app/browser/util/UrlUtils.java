package com.gamecenter.app.browser.util;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
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
     * Returns a valid http(s) navigation target, adding https for a bare host.
     * It deliberately does not turn search terms or custom schemes into URLs.
     */
    @Nullable
    public static String normalizeWebUrl(@Nullable String input) {
        if (input == null) return null;
        String candidate = input.trim();
        if (candidate.isEmpty() || containsWhitespaceOrControl(candidate)
                || isDangerousScheme(candidate)) {
            return null;
        }

        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return isValidHttpUrl(candidate) ? candidate : null;
        }

        if (isBareHost(candidate)) {
            String url = "https://" + candidate;
            return isValidHttpUrl(url) ? url : null;
        }
        return null;
    }

    /**
     * 处理地址栏输入。
     * @return 处理后的 URL，若输入不安全返回 null
     */
    @Nullable
    public static String processInput(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return "https://www.baidu.com";

        if (isDangerousScheme(s)) return null;

        String webUrl = normalizeWebUrl(s);
        if (webUrl != null) return webUrl;

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
            android.util.Log.w("UrlUtils", "encodeKeyword failed, falling back to Uri.encode", e);
            return Uri.encode(keyword);
        }
    }

    /**
     * 判断是否危险协议。
     * S3: 更全面的危险协议检测。
     */
    public static boolean isDangerousScheme(@Nullable String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase(Locale.ROOT);
        // S3: 扩展危险协议列表
        return lower.startsWith("file://") || lower.startsWith("content://")
                || lower.startsWith("javascript:") || lower.startsWith("intent://")
                || lower.startsWith("about:") || lower.startsWith("data:")
                || lower.startsWith("vbscript:") || lower.startsWith("jar:")
                || lower.startsWith("blob:") || lower.startsWith("filesystem:");
    }

    /** True only for an http(s) URL that has a host and a valid port. */
    public static boolean isValidHttpUrl(@Nullable String url) {
        if (url == null || containsWhitespaceOrControl(url)) return false;
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))) || uri.getHost() == null) {
                return false;
            }
            // Credentials in an address-bar URL are both surprising and easy to
            // mistake for a trusted host (for example, https://trusted@attacker).
            // Keep navigation and downloads on an origin-only authority.
            if (uri.getRawUserInfo() != null || uri.getRawAuthority() == null) {
                return false;
            }
            int port = uri.getPort();
            return port >= -1 && port <= 65535;
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isBareHost(@NonNull String input) {
        if (LOCALHOST_PATTERN.matcher(input).matches()) {
            return isValidHttpUrl("https://" + input);
        }
        if (IP_PATTERN.matcher(input).matches()) {
            String host = parseHost("https://" + input);
            return host != null && isValidIpv4(host) && isValidHttpUrl("https://" + input);
        }
        return DOMAIN_PATTERN.matcher(input).matches() && isValidHttpUrl("https://" + input);
    }

    private static boolean isValidIpv4(@NonNull String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            try {
                if (octet.isEmpty() || (octet.length() > 1 && octet.startsWith("0"))) return false;
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsWhitespaceOrControl(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return true;
        }
        return false;
    }

    @NonNull
    public static String getHost(@Nullable String url) {
        if (url == null || url.isEmpty()) return "";
        String host = parseHost(url);
        return host != null ? host : "";
    }

    @Nullable
    private static String parseHost(@NonNull String url) {
        try {
            return new URI(url.trim()).getHost();
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isSecure(@Nullable String url) {
        return url != null && url.startsWith("https://");
    }
}

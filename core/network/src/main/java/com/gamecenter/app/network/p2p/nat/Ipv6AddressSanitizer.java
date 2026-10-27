package com.gamecenter.app.network.p2p.nat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * IPv6 地址清洗与中国三大运营商前缀优选器 (IPv6 Sanitizer & Prefix Ranker)。
 * <p>
 * 规则：
 * <ol>
 *   <li>剔除链路本地地址 (fe80::/10)、内网地址 (fc00::/7, fd00::/7)、回环地址 (::1)</li>
 *   <li>高优先级排序国内三大运营商全球单播前缀：
 *       中国电信 (240e::/16)、中国联通 (2408::/16)、中国移动 (2409::/16)、教育网 (2001:da8::/32)</li>
 *   <li>优先选用 RFC 8981 临时隐私地址 (过滤包含 EUI-64 硬件 MAC 特征 ff:fe 的静态地址)</li>
 * </ol>
 */
public class Ipv6AddressSanitizer {

    /** 中国电信公网 IPv6 前缀 */
    public static final String PREFIX_TELECOM = "240e";
    /** 中国联通公网 IPv6 前缀 */
    public static final String PREFIX_UNICOM = "2408";
    /** 中国移动公网 IPv6 前缀 */
    public static final String PREFIX_MOBILE = "2409";
    /** 教育网公网 IPv6 前缀 */
    public static final String PREFIX_CERNET = "2001:da8";

    /**
     * 校验并清洗 IPv6 地址。
     *
     * @param addr 待检测的 Inet6Address
     * @return 若是合法且高穿透价值的公网 IPv6 则返回标准字符串 (无 %scopeId)，否则返回 null
     */
    @Nullable
    public static String sanitize(@NonNull Inet6Address addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return null;
        }

        String ip = addr.getHostAddress();
        if (ip == null) return null;

        // 去除 %wlan0 等 scopeId
        int percentIndex = ip.indexOf('%');
        if (percentIndex >= 0) {
            ip = ip.substring(0, percentIndex);
        }

        String lower = ip.toLowerCase();

        // 排除 ULA 唯一本地内网地址 (fc00::/7 包括 fc00:: 和 fd00::)
        if (lower.startsWith("fc") || lower.startsWith("fd")) {
            return null;
        }

        // 排除 IPv4 映射 IPv6 地址 (::ffff:192.168.x.x)
        if (lower.startsWith("::ffff:") || lower.contains(".")) {
            return null;
        }

        return ip;
    }

    /**
     * 计算 IPv6 地址优先级评分 (得分越高穿透越优先)。
     */
    public static int calculatePriorityScore(@NonNull String ipv6) {
        String lower = ipv6.toLowerCase();
        int score = 50;

        // 运营商匹配加分
        if (lower.startsWith(PREFIX_TELECOM)) {
            score += 40; // 电信公网质量最高
        } else if (lower.startsWith(PREFIX_UNICOM)) {
            score += 38; // 联通公网互联优良
        } else if (lower.startsWith(PREFIX_MOBILE)) {
            score += 35; // 移动家宽/5G 原生 IPv6
        } else if (lower.startsWith(PREFIX_CERNET)) {
            score += 30; // 教育网
        } else if (lower.startsWith("2")) {
            score += 20; // 其它全球单播地址 (2000::/3)
        }

        // RFC 8981 临时隐私地址优选 (不含 EUI-64 的 "ff:fe")
        if (!lower.contains("ff:fe")) {
            score += 10;
        }

        return score;
    }

    /**
     * 对候选 IPv6 列表按国内运营商网络质量由优到劣排序。
     */
    @NonNull
    public static List<String> rankAddresses(@NonNull List<String> ipv6Addresses) {
        List<String> list = new ArrayList<>(ipv6Addresses);
        Collections.sort(list, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(calculatePriorityScore(b), calculatePriorityScore(a));
            }
        });
        return list;
    }
}

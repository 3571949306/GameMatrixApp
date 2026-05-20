package com.gamecenter.app.tools;

import android.util.Log;

/**
 * IP 运营商分类器 — 根据 IPv4 地址段判断其所属运营商或网络类型。
 * <p>
 * 基于中国三大运营商（电信、联通、移动）的 IP 地址段分配表进行匹配，
 * 同时识别内网地址、CGNAT 地址、CN2 线路和国际运营商等特殊类型。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>匹配顺序很重要：先匹配特殊地址（内网/CGNAT/CN2），再匹配具体运营商段，
 *       最后匹配国际运营商和保留地址</li>
 *   <li>移动运营商的 IP 段分布在代码中有两处（CMI 专线段和普通段），这是由于
 *       CMI（China Mobile International）与国内移动段属于不同地址池</li>
 *   <li>IP 段数据来源于 APNIC 和各运营商公开的地址分配信息，可能随时间变化需要更新</li>
 * </ul>
 * </p>
 */
public final class IpClassifier {

    private static final String TAG = "IpClassifier";

    private IpClassifier() {
    }

    /**
     * 根据 IPv4 地址判断其所属运营商或网络类型。
     * <p>
     * 分类优先级（从高到低）：
     * <ol>
     *   <li>内网地址（10.x、172.16-31.x、192.168.x）</li>
     *   <li>本地回环地址（127.x）</li>
     *   <li>CGNAT 地址（100.64-127.x，运营商级 NAT）</li>
     *   <li>CN2 专线（电信精品网）</li>
     *   <li>CMI 中国移动国际专线</li>
     *   <li>国内三大运营商（移动、电信、联通）</li>
     *   <li>国际运营商（Level3、AT&T、Cogent、Telia、NTT 等）</li>
     *   <li>组播/保留地址（224.x 及以上）</li>
     *   <li>其他未识别地址归为 "国际"</li>
     * </ol>
     * </p>
     *
     * @param ip IPv4 地址字符串，格式为 "a.b.c.d"
     * @return 运营商或网络类型名称（如 "电信"、"联通"、"移动"、"内网"、"CGNAT" 等）；
     *         输入无效时返回空字符串
     */
    public static String classifyIpCarrier(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return "";
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            int c = Integer.parseInt(parts[2]);

            // ===== 特殊地址段 =====

            // RFC 1918 私有地址：10.0.0.0/8
            if (a == 10) return "内网";
            // RFC 1918 私有地址：172.16.0.0/12
            if (a == 172 && b >= 16 && b <= 31) return "内网";
            // RFC 1918 私有地址：192.168.0.0/16
            if (a == 192 && b == 168) return "内网";
            // 本地回环地址：127.0.0.0/8
            if (a == 127) return "本地";
            // CGNAT 运营商级 NAT 地址：100.64.0.0/10 (RFC 6598)
            if (a == 100 && b >= 64 && b <= 127) return "CGNAT";

            // ===== CN2 电信精品网 =====

            // 电信 CN2 骨干网段，低延迟高品质线路
            if (a == 59 && b == 43) return "CN2(电信)";
            if (a == 202 && b == 97) return "CN2(电信)";

            // ===== CMI 中国移动国际 =====

            // CMI（China Mobile International）国际专线段
            if (a == 223 && (b == 118 || b == 119 || b == 120 || b == 121 || b == 122)) return "CMI(移动)";

            // ===== 中国移动 =====

            if (a == 36 && (b >= 128 && b <= 191)) return "移动";
            if (a == 39 && (b >= 128 && b <= 191)) return "移动";
            if (a == 111) return "移动";
            if (a == 112 && (b >= 0 && b <= 31)) return "移动";
            if (a == 117 && (b >= 128 && b <= 191)) return "移动";
            if (a == 120 && (b >= 192 && b <= 255)) return "移动";
            if (a == 183 && (b >= 192 && b <= 255)) return "移动";
            if (a == 211 && (b == 136 || b == 137 || b == 138 || b == 139 || b == 140)) return "移动";
            if (a == 221 && (b >= 176 && b <= 183)) return "移动";
            if (a == 223 && (b >= 64 && b <= 117)) return "移动";

            // ===== 中国电信 =====

            if (a == 1 && (b >= 0 && b <= 15)) return "电信";
            if (a == 14 && (b >= 144 && b <= 159)) return "电信";
            if (a == 27 && (b >= 0 && b <= 63)) return "电信";
            if (a == 36 && (b >= 0 && b <= 63)) return "电信";
            if (a == 42 && (b >= 0 && b <= 127)) return "电信";
            if (a == 49 && (b >= 64 && b <= 127)) return "电信";
            // 58.16-63 段属于电信，但排除 58.43（已归入 CN2）
            if (a == 58 && (b >= 16 && b <= 63) && !(b == 43)) return "电信";
            if (a == 59 && (b >= 32 && b <= 63)) return "电信";
            if (a == 61 && (b >= 128 && b <= 191)) return "电信";
            if (a == 101 && (b >= 64 && b <= 127)) return "电信";
            if (a == 106 && (b >= 0 && b <= 63)) return "电信";
            if (a == 110 && (b >= 0 && b <= 63)) return "电信";
            if (a == 113 && (b >= 0 && b <= 127)) return "电信";
            if (a == 114 && (b >= 64 && b <= 127)) return "电信";
            if (a == 115 && (b >= 192 && b <= 255)) return "电信";
            if (a == 116 && (b >= 0 && b <= 95)) return "电信";
            if (a == 117 && (b >= 64 && b <= 95)) return "电信";
            if (a == 118 && (b >= 112 && b <= 127)) return "电信";
            if (a == 119 && (b >= 0 && b <= 63)) return "电信";
            if (a == 121 && (b >= 0 && b <= 63)) return "电信";
            if (a == 122 && (b >= 192 && b <= 255)) return "电信";
            if (a == 123 && (b >= 128 && b <= 191)) return "电信";
            if (a == 125 && (b >= 64 && b <= 127)) return "电信";
            if (a == 171 && (b >= 0 && b <= 63)) return "电信";
            if (a == 175 && (b >= 0 && b <= 63)) return "电信";
            if (a == 180 && (b >= 96 && b <= 127)) return "电信";
            if (a == 182 && (b >= 32 && b <= 63)) return "电信";
            if (a == 183 && (b >= 0 && b <= 95)) return "电信";
            if (a == 202 && (b >= 96 && b <= 127)) return "电信";
            if (a == 210 && (b >= 0 && b <= 47)) return "电信";
            if ((a == 218 && b >= 64 && b <= 79) || (a == 218 && b >= 88 && b <= 95)) return "电信";
            if (a == 219 && (b >= 128 && b <= 159)) return "电信";
            if (a == 220 && (b >= 160 && b <= 191)) return "电信";
            if (a == 222 && (b >= 64 && b <= 95)) return "电信";

            // ===== 中国联通 =====

            if (a == 27 && (b >= 128 && b <= 191)) return "联通";
            if (a == 42 && (b >= 192 && b <= 255)) return "联通";
            if (a == 43 && (b >= 224 && b <= 255)) return "联通";
            if (a == 49 && (b >= 128 && b <= 191)) return "联通";
            if (a == 58 && (b >= 240 && b <= 255)) return "联通";
            if (a == 60 && (b >= 0 && b <= 31)) return "联通";
            if (a == 61 && (b >= 48 && b <= 55)) return "联通";
            if (a == 61 && (b >= 128 && b <= 191)) return "联通";
            if (a == 110 && (b >= 192 && b <= 255)) return "联通";
            if (a == 111 && (b >= 192 && b <= 207)) return "联通";
            if (a == 112 && (b >= 64 && b <= 127)) return "联通";
            if (a == 113 && (b >= 192 && b <= 255)) return "联通";
            if (a == 114 && (b >= 240 && b <= 255)) return "联通";
            if (a == 116 && (b >= 192 && b <= 207)) return "联通";
            if (a == 118 && (b >= 192 && b <= 207)) return "联通";
            if (a == 119 && (b >= 192 && b <= 255)) return "联通";
            if (a == 120 && (b >= 0 && b <= 15)) return "联通";
            if (a == 122 && (b >= 96 && b <= 127)) return "联通";
            if (a == 123 && (b >= 112 && b <= 127)) return "联通";
            if (a == 124 && (b >= 64 && b <= 95)) return "联通";
            if (a == 125 && (b >= 32 && b <= 47)) return "联通";
            if (a == 139 && (b >= 208 && b <= 223)) return "联通";
            if (a == 140 && (b >= 192 && b <= 255)) return "联通";
            if (a == 153 && (b >= 0 && b <= 3)) return "联通";
            if (a == 157 && (b >= 0 && b <= 1)) return "联通";
            if (a == 163 && (b >= 176 && b <= 179)) return "联通";
            if (a == 202 && (b >= 96 && b <= 111)) return "联通";
            if (a == 210 && (b >= 12 && b <= 13)) return "联通";
            if (a == 210 && b >= 20 && b <= 23) return "联通";
            if ((a == 218 && b >= 56 && b <= 63) || (a == 218 && b >= 104 && b <= 111)) return "联通";
            if (a == 219 && (b >= 144 && b <= 159)) return "联通";
            if (a == 220 && (b >= 192 && b <= 207)) return "联通";
            if (a == 221 && (b >= 0 && b <= 15)) return "联通";
            if (a == 222 && (b >= 128 && b <= 191)) return "联通";

            // ===== 移动补充段（与上方移动段不重叠的额外分配） =====

            if (a == 111 && (b >= 0 && b <= 63)) return "移动";
            if (a == 218 && (b >= 200 && b <= 207)) return "移动";
            if (a == 221 && (b >= 130 && b <= 133)) return "移动";

            // ===== 国际运营商 =====

            if (a == 4 || a == 8) return "Level3(美)";
            if (a == 12) return "AT&T(美)";
            if (a == 38) return "Cogent(美)";
            if (a == 80) return "Telia(欧)";
            if (a == 130) return "NTT(日)";
            if (a == 165 || a == 166 || a == 167 || a == 169) return "北美教育网";
            // D类组播地址（224.0.0.0/4）和 E类保留地址（240.0.0.0/4）
            if (a >= 224) return "组播/保留";

            // 未匹配的公网地址统一归为国际
            return "国际";
        } catch (Exception e) {
            return "";
        }
    }
}

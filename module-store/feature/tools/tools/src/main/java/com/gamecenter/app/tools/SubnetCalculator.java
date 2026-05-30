package com.gamecenter.app.tools;

/**
 * 子网计算器 — 根据 IP 地址和 CIDR 前缀长度计算子网的详细信息。
 * <p>
 * 输入格式为 "IP/CIDR"（如 "192.168.1.1/24"），输出包含：
 * <ul>
 *   <li>子网掩码</li>
 *   <li>网络地址</li>
 *   <li>广播地址</li>
 *   <li>可用 IP 范围</li>
 *   <li>可用主机数</li>
 * </ul>
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>使用 long 类型存储 IP 地址，避免 int 的符号位问题（IPv4 地址为无符号 32 位）</li>
 *   <li>对 /31 和 /32 特殊前缀做了边界处理：/31 为点对点链路（2个地址均可用），
 *       /32 为单主机路由（仅1个地址）</li>
 * </ul>
 * </p>
 */
public final class SubnetCalculator {

    private SubnetCalculator() {
    }

    /**
     * 根据输入的 IP/CIDR 字符串计算子网信息。
     * <p>
     * 计算步骤：
     * <ol>
     *   <li>解析 IP 地址的四个八位组和 CIDR 前缀长度</li>
     *   <li>将 IP 地址转换为 32 位无符号 long 值</li>
     *   <li>根据前缀长度生成子网掩码</li>
     *   <li>通过网络地址与掩码的按位与运算得到网络地址</li>
     *   <li>通过网络地址与反掩码的按位或运算得到广播地址</li>
     *   <li>根据前缀长度确定可用主机范围和数量</li>
     * </ol>
     * </p>
     *
     * @param input CIDR 格式的输入字符串，如 "192.168.1.1/24"
     * @return 子网计算结果的多行文本；格式错误或计算失败时返回错误提示信息
     */
    public static String calculateSubnet(String input) {
        try {
            String[] parts = input.split("/");
            if (parts.length != 2) return "格式错误，请使用 IP/CIDR 格式，如 192.168.1.1/24";
            String[] ipParts = parts[0].split("\\.");
            if (ipParts.length != 4) return "IP地址格式错误";
            int a = Integer.parseInt(ipParts[0]);
            int b = Integer.parseInt(ipParts[1]);
            int c = Integer.parseInt(ipParts[2]);
            int d = Integer.parseInt(ipParts[3]);
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) return "子网掩码前缀必须在 0-32 之间";

            // 将四个八位组组合为 32 位无符号 long 值（使用 long 避免 int 符号位问题）
            long ip = ((long) a << 24) | ((long) b << 16) | ((long) c << 8) | d;
            // 根据前缀长度生成子网掩码：prefix=0 时掩码为 0，否则左移 (32-prefix) 位
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix));
            // 网络地址 = IP 与掩码按位与
            long network = ip & mask;
            // 广播地址 = 网络地址 与反掩码按位或
            long broadcast = network | (~mask & 0xFFFFFFFFL);
            // /31 和 /32 是特殊前缀：/31 为点对点链路（无网络/广播地址概念），/32 为单主机
            long firstHost = (prefix >= 31) ? network : network + 1;
            long lastHost = (prefix >= 31) ? broadcast : broadcast - 1;
            // 可用主机数：/32 仅1个地址，/31 有2个地址，其余为 2^(32-prefix) - 2（减去网络和广播地址）
            long totalHosts = (prefix >= 31) ? (prefix == 32 ? 1 : 2) : (long) Math.pow(2, 32 - prefix) - 2;

            StringBuilder sb = new StringBuilder();
            sb.append("IP地址: ").append(longToIp(ip)).append("\n");
            sb.append("子网掩码: ").append(longToIp(mask)).append(" (/" + prefix + ")\n");
            sb.append("网络地址: ").append(longToIp(network)).append("\n");
            sb.append("广播地址: ").append(longToIp(broadcast)).append("\n");
            sb.append("可用IP范围: ").append(longToIp(firstHost)).append(" - ").append(longToIp(lastHost)).append("\n");
            sb.append("可用主机数: ").append(totalHosts);
            return sb.toString();
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    /**
     * 将 32 位无符号 long 值转换为点分十进制格式的 IP 地址字符串。
     *
     * @param ip 32 位无符号 IP 地址值（存储在 long 中）
     * @return 点分十进制格式的 IP 地址字符串，如 "192.168.1.1"
     */
    public static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }
}

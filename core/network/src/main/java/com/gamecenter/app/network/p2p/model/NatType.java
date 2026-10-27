package com.gamecenter.app.network.p2p.model;

/**
 * NAT 拓扑与行为分类枚举（基于 RFC 3489 / RFC 5389）。
 * <p>
 * 重点针对中国境内运营商网络（中国移动 CGNAT、电信/联通宽带、4G/5G 移动基站）的分类。
 */
public enum NatType {
    /** 拥有公网 IP 且无防火墙阻断 UDP */
    OPEN_INTERNET("公网裸机", true, 100),

    /** 全锥型 NAT (NAT 1)：打洞最容易，任何外部主机均可连入已映射端口 */
    FULL_CONE("全锥型 (Full Cone / NAT1)", true, 95),

    /** IP 受限锥型 NAT (NAT 2)：仅允许向其发过包的外部 IP 连入，端口不限 */
    RESTRICTED_CONE("受限锥型 (Address-Restricted / NAT2)", true, 85),

    /** 端口受限锥型 NAT (NAT 3)：中国大部分家庭宽带的典型形态，需双向对发 UDP 激活状态表 */
    PORT_RESTRICTED_CONE("端口受限锥型 (Port-Restricted / NAT3)", true, 75),

    /** 对称型 NAT (NAT 4)：中国 4G/5G 蜂窝网及部分多层 CGNAT 常见形态，对不同对端分配不同端口 */
    SYMMETRIC("对称型 (Symmetric / NAT4)", false, 30),

    /** 对称型 UDP 防火墙：有公网 IP 但严格阻断未经请求的入站 UDP */
    SYMMETRIC_FIREWALL("对称型防火墙", false, 40),

    /** UDP 被运营商或本地网络防火墙完全封锁 (如部分企业网/校园网) */
    UDP_BLOCKED("UDP 被封锁", false, 0),

    /** 未检测或检测超时 */
    UNKNOWN("未知网络类型", false, 10);

    private final String description;
    private final boolean easyToPunch;
    private final int successScore;

    NatType(String description, boolean easyToPunch, int successScore) {
        this.description = description;
        this.easyToPunch = easyToPunch;
        this.successScore = successScore;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEasyToPunch() {
        return easyToPunch;
    }

    public int getSuccessScore() {
        return successScore;
    }
}

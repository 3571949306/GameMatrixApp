package com.gamecenter.app.network.p2p.model;

/**
 * P2P 候选端点类型枚举。
 */
public enum CandidateType {
    /** 局域网 IPv4 地址 (如 192.168.1.5) */
    HOST_IPV4(90),

    /** 全局单播公网 IPv6 地址 (如 2408:8207:...) - 国内穿透第一优先级 */
    HOST_IPV6(100),

    /** STUN 服务器反射公网 IPv4 地址 (经过 NAT 映射后的公网端点) */
    REFLEXIVE_IPV4(80),

    /** 本地路由器 UPnP / PCP / NAT-PMP 动态映射的端口 */
    UPNP_MAPPED(85),

    /** 国内 BGP 低延迟中继服务器端点 (兜底保障) */
    RELAY(20);

    private final int defaultPriority;

    CandidateType(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }
}

package com.gamecenter.app.network.p2p.model;

/**
 * P2P 连接生命周期状态枚举。
 */
public enum P2pConnectionState {
    /** 初始空闲状态 */
    IDLE,

    /** 正在探测本地网络 NAT 类型及收集候选地址 (Gathering) */
    GATHERING,

    /** 正在通过信令服务器进行房间匹配与 Candidate 交换 (Signaling) */
    SIGNALING,

    /** 正在执行多路打洞与竞速测试 (Punching & Racing) */
    PUNCHING,

    /** 打洞成功，P2P 直连已建立 (Connected via Direct P2P) */
    CONNECTED_P2P,

    /** P2P 失败或闪断，已降级至 BGP 中继服务器传输 (Connected via Relay) */
    CONNECTED_RELAY,

    /** 网络波动，正在后台执行平滑重连与自动打洞恢复 (Reconnecting / Hot-Swapping) */
    RECONNECTING,

    /** 发生致命错误，连接已中断 */
    FAILED,

    /** 会话已正常关闭 */
    CLOSED
}

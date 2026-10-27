package com.gamecenter.app.network.p2p;

import androidx.annotation.NonNull;

import com.gamecenter.app.network.p2p.model.CandidateType;
import com.gamecenter.app.network.p2p.model.P2pConnectionState;

/**
 * P2P 对战引擎连接生命周期与数据监听器（供五子棋、象棋等游戏业务接入）。
 */
public interface P2pConnectionListener {
    /** 连接状态发生变化（打洞中、已直连、已降级中继等） */
    void onStateChanged(@NonNull P2pConnectionState state);

    /** 链路打通就绪，通知当前选中的通道类型（IPv6/TCP/QUIC/UDP）与网络延迟 RTT */
    void onConnected(@NonNull CandidateType activePath, long rttMs);

    /** 收到对端发送的可靠对战数据包（如落子、悔棋、确认胜负） */
    void onDataReceived(@NonNull byte[] payload);

    /** 发生网络切换并平滑迁移（如由 WiFi 切换到 4G/5G） */
    void onPathMigrated(@NonNull CandidateType newPath, long currentRttMs);

    /** 连接中断或致命错误 */
    void onError(int errorCode, @NonNull String errorMessage);
}

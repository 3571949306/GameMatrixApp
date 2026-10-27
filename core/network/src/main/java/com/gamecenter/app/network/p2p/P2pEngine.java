package com.gamecenter.app.network.p2p;

import androidx.annotation.NonNull;

import com.gamecenter.app.network.p2p.model.NatType;

/**
 * P2P 内网穿透引擎全局门面 (Singleton Entrance)。
 */
public class P2pEngine {

    private static volatile P2pEngine instance;

    private volatile NatType cachedNatType = NatType.UNKNOWN;
    private volatile long lastNatDetectionTime = 0;

    private P2pEngine() {
    }

    @NonNull
    public static P2pEngine getInstance() {
        if (instance == null) {
            synchronized (P2pEngine.class) {
                if (instance == null) {
                    instance = new P2pEngine();
                }
            }
        }
        return instance;
    }

    /**
     * 创建房主会话 (Host / Server Role)。
     *
     * @param roomCode 6位字母数字房间码
     */
    @NonNull
    public P2pRoomSession createHostSession(@NonNull String roomCode) {
        return new P2pRoomSession(roomCode, true);
    }

    /**
     * 创建访客会话 (Guest / Client Role)。
     *
     * @param roomCode 6位字母数字房间码
     */
    @NonNull
    public P2pRoomSession createGuestSession(@NonNull String roomCode) {
        return new P2pRoomSession(roomCode, false);
    }

    /**
     * 创建指定传输模式的会话。
     */
    @NonNull
    public P2pRoomSession createSession(@NonNull String roomCode, boolean isHost, @NonNull P2pRoomSession.TransportMode mode) {
        return new P2pRoomSession(roomCode, isHost, mode);
    }

    public synchronized void updateCachedNatType(@NonNull NatType natType) {
        this.cachedNatType = natType;
        this.lastNatDetectionTime = System.currentTimeMillis();
    }

    @NonNull
    public NatType getCachedNatType() {
        // 缓存有效期 5 分钟
        if (System.currentTimeMillis() - lastNatDetectionTime > 5 * 60 * 1000) {
            return NatType.UNKNOWN;
        }
        return cachedNatType;
    }
}

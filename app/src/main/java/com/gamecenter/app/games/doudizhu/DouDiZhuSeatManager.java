package com.gamecenter.app.games.doudizhu;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 斗地主联机座位管理。
 * 负责座位分配、重连恢复、座位类型管理、Peer Token 管理。
 */
public class DouDiZhuSeatManager {

    public static final int SEAT_TYPE_HOST = 0;
    public static final int SEAT_TYPE_REMOTE = 1;
    public static final int SEAT_TYPE_AI = 2;
    public static final int TOTAL_SEATS = 3;

    // ============ 座位状态 ============

    private final int[] seatTypes = new int[]{SEAT_TYPE_HOST, SEAT_TYPE_AI, SEAT_TYPE_AI};
    private final int[] seatClientIds = new int[]{-1, -1, -1};
    private final String[] seatClientIps = new String[]{"", "", ""};
    private final String[] seatPeerTokens = new String[]{"", "", ""};
    private final Map<Integer, String> pendingClientIps = new HashMap<>();
    private final long[] lastProcessedActionIds = new long[]{0L, 0L, 0L};

    private String remoteHostInfoText = "";
    private String remoteInviteAddress = "";
    private String remoteRoomCode = "";

    // ============ P2P Token ============

    private static final String P2P_PREFS = "doudizhu_p2p";
    private static final String KEY_PEER_TOKEN = "peer_token";
    private String localPeerToken = "";
    private Context context;

    public DouDiZhuSeatManager() {}

    public void setContext(Context context) {
        this.context = context;
    }

    public String getLocalPeerToken() {
        if (localPeerToken.isEmpty() && context != null) {
            localPeerToken = getOrCreatePeerToken();
        }
        return localPeerToken;
    }

    // ============ 座位分配 ============

    /**
     * 分配座位给新连接的客户端。
     * @return 分配的座位索引（1 或 2），-1 表示无法分配
     */
    public int assignSeatToClient(int clientId, String ip, String peerToken, int gameState, int currentTurn) {
        // 检查是否已经分配过
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatClientIds[i] == clientId) {
                return i;
            }
        }

        // 通过 Peer Token 匹配
        if (peerToken != null && !peerToken.trim().isEmpty()) {
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (peerToken.equals(seatPeerTokens[i])) {
                    return i;
                }
            }
        }

        // 游戏中按 IP 恢复座位
        if (gameState == 1 || gameState == 2) { // STATE_BIDDING=1, STATE_PLAYING=2
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (seatTypes[i] == SEAT_TYPE_REMOTE
                        && ip != null
                        && ip.equals(seatClientIps[i])) {
                    return i;
                }
            }
            // 优先分配到当前轮到行动的座位
            if (currentTurn > 0 && currentTurn < TOTAL_SEATS
                    && seatTypes[currentTurn] == SEAT_TYPE_REMOTE
                    && seatClientIds[currentTurn] == -1) {
                return currentTurn;
            }
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] == -1) {
                    return i;
                }
            }
        }

        // 简单分配
        if (seatClientIds[1] == -1) return 1;
        if (seatClientIds[2] == -1) return 2;
        return -1;
    }

    public void updateSeat(int seatIndex, int clientId, String ip, String peerToken, int seatType) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return;
        seatClientIds[seatIndex] = clientId;
        seatClientIps[seatIndex] = ip != null ? ip : "";
        seatPeerTokens[seatIndex] = peerToken != null ? peerToken : "";
        seatTypes[seatIndex] = seatType;
    }

    public void resetAllSeats() {
        for (int i = 0; i < TOTAL_SEATS; i++) {
            seatTypes[i] = (i == 0) ? SEAT_TYPE_HOST : SEAT_TYPE_AI;
            seatClientIds[i] = -1;
            seatClientIps[i] = "";
            seatPeerTokens[i] = "";
            lastProcessedActionIds[i] = 0L;
        }
        pendingClientIps.clear();
        remoteHostInfoText = "";
        remoteInviteAddress = "";
        remoteRoomCode = "";
    }

    public void clearPendingIps() {
        pendingClientIps.clear();
    }

    // ============ 客户端断开处理 ============

    /**
     * 处理客户端断开连接。
     * @return 断开的座位索引，-1 表示未找到
     */
    public int handleClientDisconnect(int clientId, boolean remoteP2PMode, int gameState,
                                      AICallback aiCallback) {
        int disconnectedSeat = -1;
        for (int i = 0; i < TOTAL_SEATS; i++) {
            if (seatClientIds[i] == clientId) {
                seatClientIds[i] = -1;
                disconnectedSeat = i;

                if (remoteP2PMode) {
                    seatTypes[i] = SEAT_TYPE_REMOTE;
                    if (aiCallback != null) {
                        aiCallback.showSeatToast(i, " 断线，保留座位等待重连");
                    }
                } else if (gameState == 0 || gameState == 3) { // LOBBY=0, GAME_OVER=3
                    seatTypes[i] = SEAT_TYPE_AI;
                    if (aiCallback != null) {
                        aiCallback.initAIForSeat(i);
                        aiCallback.showSeatToast(i, " 离开，已替换为 AI");
                    }
                } else {
                    seatTypes[i] = SEAT_TYPE_REMOTE;
                    if (aiCallback != null) {
                        aiCallback.showSeatToast(i, " 掉线，等待重连");
                    }
                }
                break;
            }
        }
        return disconnectedSeat;
    }

    public void removePendingClientIp(int clientId) {
        pendingClientIps.remove(clientId);
    }

    // ============ 房间信息 ============

    public void setRemoteRoomInfo(String roomCode, String inviteAddress, String hostInfoText) {
        remoteRoomCode = roomCode;
        remoteInviteAddress = inviteAddress;
        remoteHostInfoText = hostInfoText;
    }

    public String getRemoteRoomCode() { return remoteRoomCode; }
    public String getRemoteInviteAddress() { return remoteInviteAddress; }
    public String getRemoteHostInfoText() { return remoteHostInfoText; }

    // ============ 获取器 ============

    public int getSeatType(int index) { return seatTypes[index]; }
    public int[] getSeatTypes() { return seatTypes.clone(); }
    public int getClientId(int index) { return seatClientIds[index]; }
    public String getClientIp(int index) { return seatClientIps[index]; }
    public String getPeerToken(int index) { return seatPeerTokens[index]; }
    public long getLastProcessedActionId(int index) { return lastProcessedActionIds[index]; }
    public void setLastProcessedActionId(int index, long actionId) { lastProcessedActionIds[index] = actionId; }
    public String getPendingClientIp(int clientId) { return pendingClientIps.getOrDefault(clientId, ""); }
    public void putPendingClientIp(int clientId, String ip) { pendingClientIps.put(clientId, ip != null ? ip : ""); }

    public boolean hasSeatTypeRemote(int from, int to) {
        for (int i = from; i < to; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE) return true;
        }
        return false;
    }

    public boolean hasDisconnectedRemoteSeat() {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] < 0) {
                return true;
            }
        }
        return false;
    }

    // ============ Peer Token ============

    private String getOrCreatePeerToken() {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(P2P_PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_PEER_TOKEN, "");
        if (token == null || token.trim().isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.US);
            prefs.edit().putString(KEY_PEER_TOKEN, token).apply();
        }
        return token;
    }

    // ============ AI 回调接口 ============

    public interface AICallback {
        void initAIForSeat(int seatIndex);
        void showSeatToast(int seatIndex, String message);
    }
}

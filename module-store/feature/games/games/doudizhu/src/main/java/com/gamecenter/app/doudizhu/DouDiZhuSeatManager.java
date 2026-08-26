package com.gamecenter.app.doudizhu;

import android.content.Context;
import android.content.SharedPreferences;
import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 斗地主联机座位管理器。
 *
 * <p>负责联机模式下三个座位的分配、状态维护、断线重连恢复以及 Peer Token 管理。</p>
 *
 * <p>你可以把这个类想象成"棋牌室的服务员"——它负责安排谁坐哪个位置，
 * 有人离开了要处理（换AI或保留座位），有人回来了要让他坐回原来的位置。</p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>座位分配：为新连接的客户端分配座位（优先通过 Peer Token 匹配，其次按 IP 恢复）</li>
 *   <li>座位状态管理：维护每个座位的类型（房主/远程/AI）、客户端ID、IP、Peer Token</li>
 *   <li>断线处理：根据游戏状态和模式决定断线后是保留座位等待重连还是替换为 AI</li>
 *   <li>Peer Token 管理：为每个设备生成唯一标识并持久化，用于断线重连时的身份识别
 *       （就像给每个玩家发一张"会员卡"，下次来凭卡找回座位）</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>座位索引 0 固定为房主（HOST），1 和 2 为其他玩家</li>
 *   <li>默认座位配置为 [HOST, AI, AI]，联机时将 AI 替换为 REMOTE</li>
 *   <li>Peer Token 通过 SharedPreferences 持久化，确保应用重启后设备身份不变</li>
 *   <li>远程P2P模式下断线保留座位，局域网模式下游戏中断线也保留座位，大厅中断线替换为AI</li>
 * </ul>
 */
public class DouDiZhuSeatManager {

    /** 座位类型：房主（本地玩家） */
    public static final int SEAT_TYPE_HOST = 0;
    /** 座位类型：远程玩家（通过网络连接） */
    public static final int SEAT_TYPE_REMOTE = 1;
    /** 座位类型：AI 机器人 */
    public static final int SEAT_TYPE_AI = 2;
    /** 斗地主总座位数 */
    public static final int TOTAL_SEATS = 3;

    // ============ 座位状态 ============

    /** 各座位的类型，默认 [HOST, AI, AI] */
    private final int[] seatTypes = new int[]{SEAT_TYPE_HOST, SEAT_TYPE_AI, SEAT_TYPE_AI};
    /** 各座位绑定的客户端ID，-1 表示未绑定 */
    private final int[] seatClientIds = new int[]{-1, -1, -1};
    /** 各座位客户端的 IP 地址 */
    private final String[] seatClientIps = new String[]{"", "", ""};
    /** 各座位的 Peer Token（用于断线重连识别） */
    private final String[] seatPeerTokens = new String[]{"", "", ""};
    /** 等待分配座位的客户端 IP 缓存（clientId -> IP） */
    private final Map<Integer, String> pendingClientIps = new HashMap<>();
    /** 各座位最后处理的动作ID，用于防止重复处理同一消息 */
    private final long[] lastProcessedActionIds = new long[]{0L, 0L, 0L};

    /** 远程房主信息文本 */
    private String remoteHostInfoText = "";
    /** 远程邀请地址 */
    private String remoteInviteAddress = "";
    /** 远程房间号 */
    private String remoteRoomCode = "";

    // ============ P2P Token ============

    /** SharedPreferences 文件名，用于存储 Peer Token */
    private static final String P2P_PREFS = "doudizhu_p2p";
    /** 模块作用域 ID（必须与 catalog.json 中 doudizhu 模块 id 一致） */
    private static final String MODULE_ID = "doudizhu";
    /** Peer Token 的存储键 */
    private static final String KEY_PEER_TOKEN = "peer_token";
    /** 本设备的 Peer Token */
    private String localPeerToken = "";
    /** 应用上下文，用于访问 SharedPreferences */
    private Context context;

    /** 默认构造方法 */
    public DouDiZhuSeatManager() {}

    /**
     * 设置应用上下文（用于 Peer Token 的持久化存储）。
     *
     * @param context 应用上下文
     */
    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * 获取本设备的 Peer Token。
     *
     * <p>如果尚未生成，会自动创建并持久化到 SharedPreferences。
     * Peer Token 用于断线重连时识别设备身份。</p>
     *
     * @return 本设备的 Peer Token 字符串
     */
    public String getLocalPeerToken() {
        if (localPeerToken.isEmpty() && context != null) {
            localPeerToken = getOrCreatePeerToken();
        }
        return localPeerToken;
    }

    // ============ 座位分配 ============

    /**
     * 为新连接的客户端分配座位。
     *
     * <p>分配优先级：</p>
     * <ol>
     *   <li><b>客户端ID匹配</b>：如果该 clientId 已分配过座位，直接返回原座位</li>
     *   <li><b>Peer Token 匹配</b>：如果客户端携带的 Token 与某座位记录一致，恢复该座位</li>
     *   <li><b>IP 恢复</b>（仅游戏中）：如果客户端 IP 与某远程座位 IP 一致，恢复该座位</li>
     *   <li><b>当前行动优先</b>（仅游戏中）：优先分配到当前轮到行动的空远程座位</li>
     *   <li><b>简单分配</b>：按顺序分配第一个空座位</li>
     * </ol>
     *
     * @param clientId   客户端唯一标识
     * @param ip         客户端 IP 地址
     * @param peerToken  客户端的 Peer Token（用于断线重连识别）
     * @param gameState  当前游戏状态（0=大厅, 1=叫地主, 2=出牌, 3=游戏结束）
     * @param currentTurn 当前轮到行动的座位索引
     * @return 分配的座位索引（1 或 2），-1 表示无法分配（座位已满）
     */
    public int assignSeatToClient(int clientId, String ip, String peerToken, int gameState, int currentTurn) {
        // 优先级1：检查是否已经分配过（同一 clientId 重复连接）
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatClientIds[i] == clientId) {
                return i;
            }
        }

        // 优先级2：通过 Peer Token 匹配（断线重连场景）
        if (peerToken != null && !peerToken.trim().isEmpty()) {
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (peerToken.equals(seatPeerTokens[i])) {
                    return i;
                }
            }
        }

        // 优先级3-5：仅在游戏进行中（叫地主或出牌阶段）执行
        if (gameState == 1 || gameState == 2) { // STATE_BIDDING=1, STATE_PLAYING=2
            // 优先级3：按 IP 恢复座位（断线重连场景）
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (seatTypes[i] == SEAT_TYPE_REMOTE
                        && ip != null
                        && ip.equals(seatClientIps[i])) {
                    return i;
                }
            }
            // 优先级4：优先分配到当前轮到行动的座位（减少等待）
            if (currentTurn > 0 && currentTurn < TOTAL_SEATS
                    && seatTypes[currentTurn] == SEAT_TYPE_REMOTE
                    && seatClientIds[currentTurn] == -1) {
                return currentTurn;
            }
            // 优先级5：分配第一个空的远程座位
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] == -1) {
                    return i;
                }
            }
        }

        // 简单分配：按顺序分配第一个未绑定的座位
        if (seatClientIds[1] == -1) return 1;
        if (seatClientIds[2] == -1) return 2;
        return -1;
    }

    /**
     * 更新指定座位的绑定信息。
     *
     * @param seatIndex 座位索引（0-2）
     * @param clientId  客户端ID
     * @param ip        客户端 IP
     * @param peerToken 客户端 Peer Token
     * @param seatType  座位类型（HOST/REMOTE/AI）
     */
    public void updateSeat(int seatIndex, int clientId, String ip, String peerToken, int seatType) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return;
        seatClientIds[seatIndex] = clientId;
        seatClientIps[seatIndex] = ip != null ? ip : "";
        seatPeerTokens[seatIndex] = peerToken != null ? peerToken : "";
        seatTypes[seatIndex] = seatType;
    }

    /**
     * 重置所有座位到初始状态。
     *
     * <p>座位0恢复为 HOST，座位1和2恢复为 AI。
     * 清空所有客户端绑定、IP、Token 和房间信息。</p>
     */
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

    /**
     * 清空等待分配的客户端 IP 缓存。
     */
    public void clearPendingIps() {
        pendingClientIps.clear();
    }

    // ============ 客户端断开处理 ============

    /**
     * 处理客户端断开连接。
     *
     * <p>根据游戏模式和状态决定断线后的处理策略：</p>
     * <ul>
     *   <li><b>远程P2P模式</b>：保留座位类型为 REMOTE，等待重连</li>
     *   <li><b>大厅/游戏结束</b>：将座位替换为 AI</li>
     *   <li><b>游戏中（非P2P）</b>：保留座位类型为 REMOTE，等待重连</li>
     * </ul>
     *
     * @param clientId      断开连接的客户端ID
     * @param remoteP2PMode 是否为远程P2P模式
     * @param gameState     当前游戏状态（0=大厅, 1=叫地主, 2=出牌, 3=游戏结束）
     * @param aiCallback    AI 回调接口（用于初始化AI替代和显示提示）
     * @return 断开连接的座位索引，-1 表示未找到对应座位
     */
    public int handleClientDisconnect(int clientId, boolean remoteP2PMode, int gameState,
                                      AICallback aiCallback) {
        int disconnectedSeat = -1;
        for (int i = 0; i < TOTAL_SEATS; i++) {
            if (seatClientIds[i] == clientId) {
                seatClientIds[i] = -1;
                disconnectedSeat = i;

                if (remoteP2PMode) {
                    // 远程P2P模式：保留座位等待重连
                    seatTypes[i] = SEAT_TYPE_REMOTE;
                    if (aiCallback != null) {
                        aiCallback.showSeatToast(i, " 断线，保留座位等待重连");
                    }
                } else if (gameState == 0 || gameState == 3) { // LOBBY=0, GAME_OVER=3
                    // 大厅或游戏结束：替换为AI
                    seatTypes[i] = SEAT_TYPE_AI;
                    if (aiCallback != null) {
                        aiCallback.initAIForSeat(i);
                        aiCallback.showSeatToast(i, " 离开，已替换为 AI");
                    }
                } else {
                    // 游戏进行中：保留座位等待重连
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

    /**
     * 从等待列表中移除指定客户端的 IP 记录。
     *
     * @param clientId 客户端ID
     */
    public void removePendingClientIp(int clientId) {
        pendingClientIps.remove(clientId);
    }

    // ============ 房间信息 ============

    /**
     * 设置远程房间的信息。
     *
     * @param roomCode      房间号
     * @param inviteAddress 邀请地址
     * @param hostInfoText  房主信息文本
     */
    public void setRemoteRoomInfo(String roomCode, String inviteAddress, String hostInfoText) {
        remoteRoomCode = roomCode;
        remoteInviteAddress = inviteAddress;
        remoteHostInfoText = hostInfoText;
    }

    /** 获取远程房间号 */
    public String getRemoteRoomCode() { return remoteRoomCode; }
    /** 获取远程邀请地址 */
    public String getRemoteInviteAddress() { return remoteInviteAddress; }
    /** 获取远程房主信息文本 */
    public String getRemoteHostInfoText() { return remoteHostInfoText; }

    // ============ 获取器 ============

    /**
     * 获取指定座位的类型。
     *
     * @param index 座位索引（0-2）
     * @return 座位类型（HOST/REMOTE/AI）
     */
    public int getSeatType(int index) { return seatTypes[index]; }

    /**
     * 获取所有座位类型的副本。
     *
     * @return 座位类型数组的克隆
     */
    public int[] getSeatTypes() { return seatTypes.clone(); }

    /**
     * 获取指定座位绑定的客户端ID。
     *
     * @param index 座位索引
     * @return 客户端ID，-1 表示未绑定
     */
    public int getClientId(int index) { return seatClientIds[index]; }

    /**
     * 获取指定座位客户端的 IP 地址。
     *
     * @param index 座位索引
     * @return IP 地址字符串
     */
    public String getClientIp(int index) { return seatClientIps[index]; }

    /**
     * 获取指定座位的 Peer Token。
     *
     * @param index 座位索引
     * @return Peer Token 字符串
     */
    public String getPeerToken(int index) { return seatPeerTokens[index]; }

    /**
     * 获取指定座位最后处理的动作ID。
     *
     * @param index 座位索引
     * @return 最后处理的动作ID
     */
    public long getLastProcessedActionId(int index) { return lastProcessedActionIds[index]; }

    /**
     * 设置指定座位最后处理的动作ID（用于防止重复处理消息）。
     *
     * @param index    座位索引
     * @param actionId 动作ID
     */
    public void setLastProcessedActionId(int index, long actionId) { lastProcessedActionIds[index] = actionId; }

    /**
     * 获取等待分配座位的客户端 IP。
     *
     * @param clientId 客户端ID
     * @return IP 地址，未找到返回空字符串
     */
    public String getPendingClientIp(int clientId) { return pendingClientIps.getOrDefault(clientId, ""); }

    /**
     * 缓存等待分配座位的客户端 IP。
     *
     * @param clientId 客户端ID
     * @param ip       IP 地址
     */
    public void putPendingClientIp(int clientId, String ip) { pendingClientIps.put(clientId, ip != null ? ip : ""); }

    /**
     * 检查指定范围内是否存在远程玩家座位。
     *
     * @param from 起始座位索引（含）
     * @param to   结束座位索引（不含）
     * @return true 表示存在至少一个远程座位
     */
    public boolean hasSeatTypeRemote(int from, int to) {
        for (int i = from; i < to; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE) return true;
        }
        return false;
    }

    /**
     * 检查是否存在已断开连接的远程座位（座位类型为 REMOTE 但 clientId 为 -1）。
     *
     * @return true 表示存在断线的远程座位
     */
    public boolean hasDisconnectedRemoteSeat() {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] < 0) {
                return true;
            }
        }
        return false;
    }

    // ============ Peer Token ============

    /**
     * 获取或创建本设备的 Peer Token。
     *
     * <p>如果 SharedPreferences 中已有 Token 则直接返回，
     * 否则生成一个新的 UUID Token 并持久化存储。
     * Token 格式为去掉连字符的小写 UUID。</p>
     *
     * @return Peer Token 字符串
     */
    private String getOrCreatePeerToken() {
        if (context == null) return "";
        // Phase 3 数据隔离：迁移旧扁平 SP 并使用作用域 SP（mod_doudizhu__doudizhu_p2p）
        ModuleScopedPreferences.migrateFrom(context, MODULE_ID, P2P_PREFS);
        SharedPreferences prefs = ModuleScopedPreferences.get(context, MODULE_ID, P2P_PREFS);
        String token = prefs.getString(KEY_PEER_TOKEN, "");
        if (token == null || token.trim().isEmpty()) {
            // 首次使用，生成新的 UUID Token 并持久化
            token = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.US);
            prefs.edit().putString(KEY_PEER_TOKEN, token).apply();
        }
        return token;
    }

    // ============ AI 回调接口 ============

    /**
     * AI 替代回调接口。
     *
     * <p>当客户端断开连接需要 AI 替代时，通过此接口通知上层初始化 AI 并显示提示。</p>
     */
    public interface AICallback {
        /**
         * 为指定座位初始化 AI 机器人。
         *
         * @param seatIndex 座位索引
         */
        void initAIForSeat(int seatIndex);

        /**
         * 显示座位相关的提示信息。
         *
         * @param seatIndex 座位索引
         * @param message   提示消息
         */
        void showSeatToast(int seatIndex, String message);
    }
}

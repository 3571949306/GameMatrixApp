package com.gamecenter.app.games.doudizhu;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.network.GameSocketServer;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

/**
 * 斗地主联机状态同步与广播管理器。
 *
 * <p>负责状态版本管理、SYNC_STATE 广播、ACK 处理、座位信息同步。
 * 作为房主端（Host）的核心同步组件，确保所有远程客户端的状态与房主一致。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用递增版本号（stateVersion）机制，客户端通过版本号判断状态是否过期</li>
 *   <li>SYNC_STATE 广播采用"立即发送+两次延迟重发"策略（0ms/180ms/600ms），提高弱网可靠性</li>
 *   <li>客户端动作通过 actionId 去重，防止网络重传导致重复处理</li>
 *   <li>每个远程座位收到的 SYNC_STATE 包含该座位专属的手牌和记牌器数据，避免信息泄露</li>
 * </ul>
 */
public class DouDiZhuSyncManager {

    private static final String TAG = "DouDiZhuSyncMgr";

    /** 房主端的状态版本号，每次状态变更递增 */
    private long hostStateVersion = 0L;
    /** 客户端最近收到的状态版本号，用于过滤过期消息 */
    private long clientLastStateVersion = -1L;
    /** 客户端动作 ID 计数器，用于为每个客户端操作分配唯一 ID */
    private long nextClientActionId = 1L;

    /** 座位管理器，管理各座位的类型、客户端 ID 等信息 */
    private final DouDiZhuSeatManager seatManager;
    /** 游戏服务器实例，用于向客户端发送消息 */
    private GameSocketServer server;
    /** 主线程 Handler，用于延迟调度重发任务 */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * 游戏状态提供者接口。
     *
     * <p>由 Activity 实现，提供当前游戏的完整状态数据，
     * 用于构建 SYNC_STATE 消息发送给远程客户端。</p>
     */
    public interface GameStateProvider {
        /** 获取当前游戏阶段 */
        int getGameState();
        /** 获取当前轮到的座位索引 */
        int getCurrentTurn();
        /** 获取地主座位索引 */
        int getLandlordIndex();
        /** 获取赢家座位索引 */
        int getWinnerIndex();
        /** 获取最后出牌的玩家座位索引 */
        int getLastPlayerWhoPlayed();
        /** 获取座位 0 的手牌 */
        List<Card> getPlayerHandCards();
        /** 获取座位 1 的手牌 */
        List<Card> getSeat1Cards();
        /** 获取座位 2 的手牌 */
        List<Card> getSeat2Cards();
        /** 获取底牌 */
        List<Card> getBottomCards();
        /** 获取座位 0 已出的牌 */
        List<Card> getPlayerPlayedCards();
        /** 获取座位 1 已出的牌 */
        List<Card> getSeat1PlayedCards();
        /** 获取座位 2 已出的牌 */
        List<Card> getSeat2PlayedCards();
        /** 获取各座位是否"不出"的标记数组 */
        boolean[] getPlayerPassed();
        /** 获取各座位的类型数组 */
        int[] getSeatTypes();
        /** 获取玩家显示座位索引（客户端模式下用于视角计算） */
        int getPlayerDisplaySeat();
        /** 获取指定座位的剩余手牌数 */
        int getSeatCardCount(int seatIndex);
    }

    /**
     * 构造同步管理器。
     *
     * @param seatManager 座位管理器
     * @param server 游戏服务器实例，可为 null（客户端模式）
     */
    public DouDiZhuSyncManager(DouDiZhuSeatManager seatManager, GameSocketServer server) {
        this.seatManager = seatManager;
        this.server = server;
    }

    /**
     * 设置游戏服务器实例。
     *
     * @param server 游戏服务器实例
     */
    public void setServer(GameSocketServer server) {
        this.server = server;
    }

    /**
     * 递增并返回下一个状态版本号。
     *
     * <p>每次房主端状态变更时调用，确保客户端能识别最新状态。</p>
     *
     * @return 新的状态版本号
     */
    public long nextStateVersion() {
        return ++hostStateVersion;
    }

    /**
     * 获取当前状态版本号。
     *
     * <p>如果版本号未初始化（≤0），自动设为 1。</p>
     *
     * @return 当前状态版本号
     */
    public long getCurrentStateVersion() {
        if (hostStateVersion <= 0L) {
            hostStateVersion = 1L;
        }
        return hostStateVersion;
    }

    /**
     * 获取客户端最近收到的状态版本号。
     *
     * @return 版本号，-1 表示尚未收到过状态同步
     */
    public long getClientLastStateVersion() {
        return clientLastStateVersion;
    }

    /**
     * 设置客户端最近收到的状态版本号。
     *
     * @param version 版本号
     */
    public void setClientLastStateVersion(long version) {
        clientLastStateVersion = version;
    }

    /**
     * 获取并递增客户端动作 ID。
     *
     * <p>每个客户端操作（出牌/不出/叫地主）分配唯一的 actionId，
     * 用于房主端去重和确认。</p>
     *
     * @return 唯一的动作 ID
     */
    public long getNextClientActionId() {
        return nextClientActionId++;
    }

    /**
     * 广播状态同步消息到所有远程客户端。
     *
     * <p>采用"立即发送+两次延迟重发"策略（0ms/180ms/600ms），
     * 提高弱网环境下的状态同步可靠性。每个远程座位收到的消息
     * 包含该座位专属的手牌和记牌器数据。</p>
     *
     * @param provider 游戏状态提供者
     * @return 本次广播的状态版本号
     */
    public long broadcastSyncState(GameStateProvider provider) {
        if (server == null) {
            Log.w(TAG, "broadcastSyncState: server is null");
            return getCurrentStateVersion();
        }
        long version = nextStateVersion();
        Log.d(TAG, "broadcastSyncState version=" + version);
        // 立即发送
        sendSyncStateNow(version, provider);
        // 180ms 后重发，确保短暂丢包时仍能收到
        handler.postDelayed(() -> sendSyncStateNow(version, provider), 180);
        // 600ms 后再次重发，覆盖更长的网络波动
        handler.postDelayed(() -> sendSyncStateNow(version, provider), 600);
        return version;
    }

    /**
     * 向指定座位发送状态同步消息。
     *
     * <p>用于新客户端加入或重连时，发送当前完整游戏状态。</p>
     *
     * @param seatIndex 目标座位索引
     * @param provider 游戏状态提供者
     */
    public void sendSyncStateToSeat(int seatIndex, GameStateProvider provider) {
        if (server == null) return;
        if (seatIndex < 0 || seatIndex >= DouDiZhuSeatManager.TOTAL_SEATS) return;
        int clientId = seatManager.getClientId(seatIndex);
        if (clientId < 0) return;
        server.sendTo(clientId, createSyncStateMessage(seatIndex, getCurrentStateVersion(), provider));
    }

    /**
     * 立即向所有远程座位发送指定版本的状态同步消息。
     *
     * @param version 状态版本号
     * @param provider 游戏状态提供者
     */
    private void sendSyncStateNow(long version, GameStateProvider provider) {
        if (server == null) return;
        int sentCount = 0;
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            int type = seatManager.getSeatType(i);
            int clientId = seatManager.getClientId(i);
            // 仅向已连接的远程座位发送
            if (type != DouDiZhuSeatManager.SEAT_TYPE_REMOTE || clientId < 0) continue;
            server.sendTo(clientId, createSyncStateMessage(i, version, provider));
            sentCount++;
        }
        Log.d(TAG, "sendSyncStateNow sent=" + sentCount + " version=" + version);
    }

    /**
     * 构建指定座位的状态同步消息。
     *
     * <p>每个座位收到的消息包含该座位专属的手牌（myCards）和记牌器数据，
     * 避免向其他座位泄露手牌信息。</p>
     *
     * @param seatIndex 目标座位索引
     * @param version 状态版本号
     * @param provider 游戏状态提供者
     * @return 构建好的 JSON 消息
     */
    private org.json.JSONObject createSyncStateMessage(int seatIndex, long version, GameStateProvider provider) {
        String bottomCardsJson = DouDiZhuProtocol.cardsToJson(provider.getBottomCards());
        String played0Json = DouDiZhuProtocol.cardsToJson(provider.getPlayerPlayedCards());
        String played1Json = DouDiZhuProtocol.cardsToJson(provider.getSeat1PlayedCards());
        String played2Json = DouDiZhuProtocol.cardsToJson(provider.getSeat2PlayedCards());

        int[] seatTypes = provider.getSeatTypes();
        JSONArray handCounts = createHandCountsForAllSeats(provider);
        // 为该座位生成专属的记牌器数据（排除该座位的手牌）
        int[] cardCounter = createCardCounterForSeat(seatIndex, provider);

        // 根据座位索引决定发送哪份手牌（仅发送该座位自己的手牌）
        List<Card> myCards;
        if (seatIndex == 0) {
            myCards = provider.getPlayerHandCards();
        } else if (seatIndex == 1) {
            myCards = provider.getSeat1Cards();
        } else {
            myCards = provider.getSeat2Cards();
        }
        String myCardsJson = DouDiZhuProtocol.cardsToJson(myCards);

        return DouDiZhuProtocol.createSyncStateMsg(
                seatIndex, version,
                provider.getGameState(), provider.getCurrentTurn(), provider.getLandlordIndex(),
                provider.getWinnerIndex(), provider.getLastPlayerWhoPlayed(),
                seatTypes, provider.getPlayerPassed(), handCounts,
                cardCounter, myCardsJson, bottomCardsJson,
                played0Json, played1Json, played2Json
        );
    }

    /**
     * 向客户端发送操作确认消息（ACK）。
     *
     * <p>房主端处理完客户端操作后，发送 ACK 告知客户端操作是否被接受。</p>
     *
     * @param clientId 目标客户端 ID
     * @param ackType 确认的操作类型
     * @param sourceMsg 原始请求消息（用于提取 actionId）
     * @param accepted 操作是否被接受
     * @param reason 拒绝原因，空字符串表示无拒绝
     * @param stateVersion 当前状态版本号
     */
    public void sendAck(int clientId, String ackType, org.json.JSONObject sourceMsg, boolean accepted, String reason, long stateVersion) {
        if (server == null) return;
        long actionId = sourceMsg != null ? sourceMsg.optLong("actionId", -1L) : -1L;
        server.sendTo(clientId, DouDiZhuProtocol.createAckMsg(ackType, actionId, stateVersion, accepted, reason));
    }

    /**
     * 向房主发送状态确认消息（STATE_ACK）。
     *
     * <p>客户端收到 SYNC_STATE 后，向房主发送确认，表示已成功应用该状态。</p>
     *
     * @param clientId 房主的客户端 ID（未使用，保留接口一致性）
     * @param stateVersion 所确认的状态版本号
     * @param mySeatIndex 客户端的座位索引
     */
    public void sendStateAck(int clientId, long stateVersion, int mySeatIndex) {
        if (server == null || stateVersion < 0L) return;
        org.json.JSONObject ack = new org.json.JSONObject();
        try {
            ack.put("type", DouDiZhuProtocol.TYPE_STATE_ACK);
            ack.put("seatIndex", mySeatIndex);
            ack.put("stateVersion", stateVersion);
            ack.put("time", System.currentTimeMillis());
        } catch (JSONException e) {
            // 忽略 JSON 构建异常
            Log.w(TAG, "JSON构建异常", e);
        }
        server.sendTo(clientId, ack);
    }

    /**
     * 广播座位更新消息到所有客户端。
     *
     * <p>当有玩家加入或离开时，通知所有客户端更新座位信息。</p>
     *
     * @param landlordIndex 当前地主座位索引
     */
    public void broadcastSeatUpdate(int landlordIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createSeatUpdateMsg(seatManager.getSeatTypes(), landlordIndex);
        try {
            server.broadcast(msg);
        } catch (Exception e) {
            Log.e(TAG, "broadcastSeatUpdate error: " + e.getMessage());
        }
    }

    /**
     * 向所有远程座位广播手牌分发消息。
     *
     * <p>游戏开始时，向每个远程座位发送其专属的手牌和底牌信息。</p>
     */
    public void broadcastHandCards() {
        if (server == null) return;
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            if (seatManager.getSeatType(i) == DouDiZhuSeatManager.SEAT_TYPE_REMOTE && seatManager.getClientId(i) >= 0) {
                List<Card> handCards;
                switch (i) {
                    case 0: handCards = getPlayerHandCardsFromProvider(null); break;
                    case 1: handCards = getSeat1CardsFromProvider(null); break;
                    case 2: handCards = getSeat2CardsFromProvider(null); break;
                    default: handCards = java.util.Collections.emptyList(); break;
                }
                String cardsJson = DouDiZhuProtocol.cardsToJson(handCards);
                String bottomJson = DouDiZhuProtocol.cardsToJson(getBottomCardsFromProvider(null));
                org.json.JSONObject msg = DouDiZhuProtocol.createHandCardsMsg(cardsJson, bottomJson, i);
                server.sendTo(seatManager.getClientId(i), msg);
            }
        }
    }

    /**
     * 广播游戏开始消息到所有远程座位。
     *
     * @param currentTurn 当前轮到的座位索引
     * @param landlordIndex 地主座位索引
     * @param bottomCardsJson 底牌的 JSON 字符串
     */
    public void broadcastGameStart(int currentTurn, int landlordIndex, String bottomCardsJson) {
        if (server == null) return;
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            if (seatManager.getSeatType(i) == DouDiZhuSeatManager.SEAT_TYPE_REMOTE && seatManager.getClientId(i) >= 0) {
                org.json.JSONObject msg = DouDiZhuProtocol.createGameStartMsg(i, currentTurn, landlordIndex, seatManager.getSeatTypes(), bottomCardsJson);
                server.sendTo(seatManager.getClientId(i), msg);
            }
        }
    }

    /**
     * 广播出牌动作到所有客户端。
     *
     * @param seatIndex 出牌的座位索引
     * @param cards 出的牌列表
     * @param currentTurn 出牌后的当前回合
     * @param landlordIndex 地主座位索引
     */
    public void broadcastPlayAction(int seatIndex, List<Card> cards, int currentTurn, int landlordIndex) {
        if (server == null) return;
        String cardTypeName = GameRuleUtil.getCardType(cards).name();
        org.json.JSONObject msg = DouDiZhuProtocol.createBroadcastPlayMsg(
                seatIndex, DouDiZhuProtocol.cardsToJson(cards), cardTypeName,
                currentTurn, landlordIndex, seatManager.getSeatTypes()
        );
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastPlayAction error", e); }
    }

    /**
     * 广播不出动作到所有客户端。
     *
     * @param seatIndex 不出的座位索引
     * @param currentTurn 不出后的当前回合
     * @param landlordIndex 地主座位索引
     */
    public void broadcastPassAction(int seatIndex, int currentTurn, int landlordIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createBroadcastPassMsg(
                seatIndex, currentTurn, landlordIndex, seatManager.getSeatTypes()
        );
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastPassAction error", e); }
    }

    /**
     * 广播叫地主结果到所有客户端。
     *
     * @param seatIndex 叫地主的座位索引
     * @param call true 表示叫地主，false 表示不叫
     * @param currentTurn 当前回合
     * @param landlordIndex 地主座位索引
     */
    public void broadcastBidResult(int seatIndex, boolean call, int currentTurn, int landlordIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createBidResultMsg(
                seatIndex, call, currentTurn, landlordIndex, seatManager.getSeatTypes()
        );
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastBidResult error", e); }
    }

    /**
     * 广播游戏结束消息到所有客户端。
     *
     * @param winnerIndex 赢家的座位索引
     */
    public void broadcastGameOver(int winnerIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createGameOverMsg(winnerIndex);
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastGameOver error", e); }
    }

    /**
     * 判断是否应该处理客户端动作（去重校验）。
     *
     * <p>通过 actionId 去重机制，防止同一操作被重复处理：
     * <ul>
     *   <li>座位索引无效（≤0 或越界）则拒绝</li>
     *   <li>actionId ≤ 0 表示旧版客户端，放行处理</li>
     *   <li>actionId ≤ 该座位已处理的最新 actionId，则为重复消息，拒绝</li>
     *   <li>通过校验后更新该座位的最新 actionId</li>
     * </ul>
     * </p>
     *
     * @param seatIndex 客户端所在座位索引
     * @param clientId 客户端 ID
     * @param msg 原始消息
     * @param type 消息类型
     * @return true 表示应该处理，false 表示应跳过
     */
    public boolean shouldProcessClientAction(int seatIndex, int clientId, org.json.JSONObject msg, String type) {
        // 座位 0 是房主，不允许远程客户端占据
        if (seatIndex <= 0 || seatIndex >= DouDiZhuSeatManager.TOTAL_SEATS) {
            sendAck(clientId, type, msg, false, "invalid seat", getCurrentStateVersion());
            return false;
        }
        long actionId = msg.optLong("actionId", -1L);
        // 旧版客户端不带 actionId，放行处理
        if (actionId <= 0L) {
            return true;
        }
        // 重复消息检测：actionId 不大于已处理的最新 ID 则为重复
        if (actionId <= seatManager.getLastProcessedActionId(seatIndex)) {
            sendAck(clientId, type, msg, false, "duplicate", getCurrentStateVersion());
            // 重复消息时补发一次状态同步，确保客户端状态正确
            sendSyncStateToSeat(seatIndex, null);
            return false;
        }
        // 记录已处理的 actionId，用于后续去重
        seatManager.setLastProcessedActionId(seatIndex, actionId);
        return true;
    }

    /**
     * 为指定座位创建记牌器数据。
     *
     * <p>记牌器显示的是"该座位不知道的牌"的数量统计。
     * 从完整牌堆中减去该座位的手牌和所有已出的牌，
     * 剩余的就是该座位无法确定的牌（即对手可能持有的牌）。</p>
     *
     * @param seatIndex 目标座位索引
     * @param provider 游戏状态提供者
     * @return 记牌器计数数组
     */
    private int[] createCardCounterForSeat(int seatIndex, GameStateProvider provider) {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        if (provider == null) return counts;

        // 获取该座位的手牌
        List<Card> handCards;
        if (seatIndex == 0) {
            handCards = provider.getPlayerHandCards();
        } else if (seatIndex == 1) {
            handCards = provider.getSeat1Cards();
        } else {
            handCards = provider.getSeat2Cards();
        }

        // 从完整牌堆中减去该座位的手牌和所有已出的牌
        DouDiZhuProtocol.subtractCardsFromCounter(counts, handCards);
        DouDiZhuProtocol.subtractCardsFromCounter(counts, provider.getPlayerPlayedCards());
        DouDiZhuProtocol.subtractCardsFromCounter(counts, provider.getSeat1PlayedCards());
        DouDiZhuProtocol.subtractCardsFromCounter(counts, provider.getSeat2PlayedCards());
        return counts;
    }

    /**
     * 创建所有座位的手牌数 JSON 数组。
     *
     * @param provider 游戏状态提供者
     * @return 包含三个座位手牌数的 JSON 数组
     */
    private JSONArray createHandCountsForAllSeats(GameStateProvider provider) {
        JSONArray array = new JSONArray();
        if (provider != null) {
            array.put(provider.getPlayerHandCards().size());
            array.put(provider.getSeat1Cards().size());
            array.put(provider.getSeat2Cards().size());
        } else {
            // provider 为空时返回默认值
            array.put(0); array.put(0); array.put(0);
        }
        return array;
    }

    /**
     * 从 GameStateProvider 获取座位 0 手牌（空安全）。
     *
     * @param provider 游戏状态提供者，可为 null
     * @return 手牌列表，provider 为 null 时返回空列表
     */
    private List<Card> getPlayerHandCardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getPlayerHandCards() : java.util.Collections.emptyList();
    }

    /**
     * 从 GameStateProvider 获取座位 1 手牌（空安全）。
     *
     * @param provider 游戏状态提供者，可为 null
     * @return 手牌列表，provider 为 null 时返回空列表
     */
    private List<Card> getSeat1CardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getSeat1Cards() : java.util.Collections.emptyList();
    }

    /**
     * 从 GameStateProvider 获取座位 2 手牌（空安全）。
     *
     * @param provider 游戏状态提供者，可为 null
     * @return 手牌列表，provider 为 null 时返回空列表
     */
    private List<Card> getSeat2CardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getSeat2Cards() : java.util.Collections.emptyList();
    }

    /**
     * 从 GameStateProvider 获取底牌（空安全）。
     *
     * @param provider 游戏状态提供者，可为 null
     * @return 底牌列表，provider 为 null 时返回空列表
     */
    private List<Card> getBottomCardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getBottomCards() : java.util.Collections.emptyList();
    }

    /**
     * 重置房主端状态版本号。
     *
     * <p>在新游戏开始或回到大厅时调用。</p>
     */
    public void resetHostStateVersion() {
        hostStateVersion = 0L;
    }

    /**
     * 重置客户端状态。
     *
     * <p>在客户端重新加入房间时调用，重置版本号和动作 ID。</p>
     */
    public void resetClientState() {
        clientLastStateVersion = -1L;
        nextClientActionId = 1L;
    }
}

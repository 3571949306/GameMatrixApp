package com.gamecenter.app.games.doudizhu;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.network.GameSocketServer;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

/**
 * 斗地主联机状态同步与广播管理器。
 * 负责状态版本管理、SYNC_STATE 广播、ACK 处理、座位信息同步。
 */
public class DouDiZhuSyncManager {

    private static final String TAG = "DouDiZhuSyncMgr";

    private long hostStateVersion = 0L;
    private long clientLastStateVersion = -1L;
    private long nextClientActionId = 1L;

    private final DouDiZhuSeatManager seatManager;
    private GameSocketServer server;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ============ 游戏状态回调 ============

    public interface GameStateProvider {
        int getGameState();
        int getCurrentTurn();
        int getLandlordIndex();
        int getWinnerIndex();
        int getLastPlayerWhoPlayed();
        List<Card> getPlayerHandCards();
        List<Card> getSeat1Cards();
        List<Card> getSeat2Cards();
        List<Card> getBottomCards();
        List<Card> getPlayerPlayedCards();
        List<Card> getSeat1PlayedCards();
        List<Card> getSeat2PlayedCards();
        boolean[] getPlayerPassed();
        int[] getSeatTypes();
        int getPlayerDisplaySeat();
        int getSeatCardCount(int seatIndex);
    }

    public DouDiZhuSyncManager(DouDiZhuSeatManager seatManager, GameSocketServer server) {
        this.seatManager = seatManager;
        this.server = server;
    }

    public void setServer(GameSocketServer server) {
        this.server = server;
    }

    // ============ 状态版本管理 ============

    public long nextStateVersion() {
        return ++hostStateVersion;
    }

    public long getCurrentStateVersion() {
        if (hostStateVersion <= 0L) {
            hostStateVersion = 1L;
        }
        return hostStateVersion;
    }

    public long getClientLastStateVersion() {
        return clientLastStateVersion;
    }

    public void setClientLastStateVersion(long version) {
        clientLastStateVersion = version;
    }

    public long getNextClientActionId() {
        return nextClientActionId++;
    }

    // ============ 状态同步广播 ============

    public long broadcastSyncState(GameStateProvider provider) {
        if (server == null) {
            Log.w(TAG, "broadcastSyncState: server is null");
            return getCurrentStateVersion();
        }
        long version = nextStateVersion();
        Log.d(TAG, "broadcastSyncState version=" + version);
        sendSyncStateNow(version, provider);
        handler.postDelayed(() -> sendSyncStateNow(version, provider), 180);
        handler.postDelayed(() -> sendSyncStateNow(version, provider), 600);
        return version;
    }

    public void sendSyncStateToSeat(int seatIndex, GameStateProvider provider) {
        if (server == null) return;
        if (seatIndex < 0 || seatIndex >= DouDiZhuSeatManager.TOTAL_SEATS) return;
        int clientId = seatManager.getClientId(seatIndex);
        if (clientId < 0) return;
        server.sendTo(clientId, createSyncStateMessage(seatIndex, getCurrentStateVersion(), provider));
    }

    private void sendSyncStateNow(long version, GameStateProvider provider) {
        if (server == null) return;
        int sentCount = 0;
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            int type = seatManager.getSeatType(i);
            int clientId = seatManager.getClientId(i);
            if (type != DouDiZhuSeatManager.SEAT_TYPE_REMOTE || clientId < 0) continue;
            server.sendTo(clientId, createSyncStateMessage(i, version, provider));
            sentCount++;
        }
        Log.d(TAG, "sendSyncStateNow sent=" + sentCount + " version=" + version);
    }

    private org.json.JSONObject createSyncStateMessage(int seatIndex, long version, GameStateProvider provider) {
        String bottomCardsJson = DouDiZhuProtocol.cardsToJson(provider.getBottomCards());
        String played0Json = DouDiZhuProtocol.cardsToJson(provider.getPlayerPlayedCards());
        String played1Json = DouDiZhuProtocol.cardsToJson(provider.getSeat1PlayedCards());
        String played2Json = DouDiZhuProtocol.cardsToJson(provider.getSeat2PlayedCards());

        int[] seatTypes = provider.getSeatTypes();
        JSONArray handCounts = createHandCountsForAllSeats(provider);
        int[] cardCounter = createCardCounterForSeat(seatIndex, provider);

        // 根据座位索引决定发送哪份手牌
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

    // ============ ACK 处理 ============

    public void sendAck(int clientId, String ackType, org.json.JSONObject sourceMsg, boolean accepted, String reason, long stateVersion) {
        if (server == null) return;
        long actionId = sourceMsg != null ? sourceMsg.optLong("actionId", -1L) : -1L;
        server.sendTo(clientId, DouDiZhuProtocol.createAckMsg(ackType, actionId, stateVersion, accepted, reason));
    }

    public void sendStateAck(int clientId, long stateVersion, int mySeatIndex) {
        if (server == null || stateVersion < 0L) return;
        org.json.JSONObject ack = new org.json.JSONObject();
        try {
            ack.put("type", DouDiZhuProtocol.TYPE_STATE_ACK);
            ack.put("seatIndex", mySeatIndex);
            ack.put("stateVersion", stateVersion);
            ack.put("time", System.currentTimeMillis());
        } catch (JSONException e) {
            // ignore
        }
        server.sendTo(clientId, ack);
    }

    // ============ 广播辅助 ============

    public void broadcastSeatUpdate(int landlordIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createSeatUpdateMsg(seatManager.getSeatTypes(), landlordIndex);
        try {
            server.broadcast(msg);
        } catch (Exception e) {
            Log.e(TAG, "broadcastSeatUpdate error: " + e.getMessage());
        }
    }

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

    public void broadcastGameStart(int currentTurn, int landlordIndex, String bottomCardsJson) {
        if (server == null) return;
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            if (seatManager.getSeatType(i) == DouDiZhuSeatManager.SEAT_TYPE_REMOTE && seatManager.getClientId(i) >= 0) {
                org.json.JSONObject msg = DouDiZhuProtocol.createGameStartMsg(i, currentTurn, landlordIndex, seatManager.getSeatTypes(), bottomCardsJson);
                server.sendTo(seatManager.getClientId(i), msg);
            }
        }
    }

    public void broadcastPlayAction(int seatIndex, List<Card> cards, int currentTurn, int landlordIndex) {
        if (server == null) return;
        String cardTypeName = GameRuleUtil.getCardType(cards).name();
        org.json.JSONObject msg = DouDiZhuProtocol.createBroadcastPlayMsg(
                seatIndex, DouDiZhuProtocol.cardsToJson(cards), cardTypeName,
                currentTurn, landlordIndex, seatManager.getSeatTypes()
        );
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastPlayAction error", e); }
    }

    public void broadcastPassAction(int seatIndex, int currentTurn, int landlordIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createBroadcastPassMsg(
                seatIndex, currentTurn, landlordIndex, seatManager.getSeatTypes()
        );
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastPassAction error", e); }
    }

    public void broadcastBidResult(int seatIndex, boolean call, int currentTurn, int landlordIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createBidResultMsg(
                seatIndex, call, currentTurn, landlordIndex, seatManager.getSeatTypes()
        );
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastBidResult error", e); }
    }

    public void broadcastGameOver(int winnerIndex) {
        if (server == null) return;
        org.json.JSONObject msg = DouDiZhuProtocol.createGameOverMsg(winnerIndex);
        try { server.broadcast(msg); } catch (Exception e) { Log.e(TAG, "broadcastGameOver error", e); }
    }

    // ============ 客户端动作校验 ============

    public boolean shouldProcessClientAction(int seatIndex, int clientId, org.json.JSONObject msg, String type) {
        if (seatIndex <= 0 || seatIndex >= DouDiZhuSeatManager.TOTAL_SEATS) {
            sendAck(clientId, type, msg, false, "invalid seat", getCurrentStateVersion());
            return false;
        }
        long actionId = msg.optLong("actionId", -1L);
        if (actionId <= 0L) {
            return true;
        }
        if (actionId <= seatManager.getLastProcessedActionId(seatIndex)) {
            sendAck(clientId, type, msg, false, "duplicate", getCurrentStateVersion());
            sendSyncStateToSeat(seatIndex, null);
            return false;
        }
        seatManager.setLastProcessedActionId(seatIndex, actionId);
        return true;
    }

    // ============ 卡牌计数器 ============

    private int[] createCardCounterForSeat(int seatIndex, GameStateProvider provider) {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        if (provider == null) return counts;

        List<Card> handCards;
        if (seatIndex == 0) {
            handCards = provider.getPlayerHandCards();
        } else if (seatIndex == 1) {
            handCards = provider.getSeat1Cards();
        } else {
            handCards = provider.getSeat2Cards();
        }

        DouDiZhuProtocol.subtractCardsFromCounter(counts, handCards);
        DouDiZhuProtocol.subtractCardsFromCounter(counts, provider.getPlayerPlayedCards());
        DouDiZhuProtocol.subtractCardsFromCounter(counts, provider.getSeat1PlayedCards());
        DouDiZhuProtocol.subtractCardsFromCounter(counts, provider.getSeat2PlayedCards());
        return counts;
    }

    private JSONArray createHandCountsForAllSeats(GameStateProvider provider) {
        JSONArray array = new JSONArray();
        if (provider != null) {
            array.put(provider.getPlayerHandCards().size());
            array.put(provider.getSeat1Cards().size());
            array.put(provider.getSeat2Cards().size());
        } else {
            array.put(0); array.put(0); array.put(0);
        }
        return array;
    }

    // ============ Provider 空安全方法 ============

    private List<Card> getPlayerHandCardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getPlayerHandCards() : java.util.Collections.emptyList();
    }
    private List<Card> getSeat1CardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getSeat1Cards() : java.util.Collections.emptyList();
    }
    private List<Card> getSeat2CardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getSeat2Cards() : java.util.Collections.emptyList();
    }
    private List<Card> getBottomCardsFromProvider(GameStateProvider provider) {
        return provider != null ? provider.getBottomCards() : java.util.Collections.emptyList();
    }

    // ============ 重置 ============

    public void resetHostStateVersion() {
        hostStateVersion = 0L;
    }

    public void resetClientState() {
        clientLastStateVersion = -1L;
        nextClientActionId = 1L;
    }
}

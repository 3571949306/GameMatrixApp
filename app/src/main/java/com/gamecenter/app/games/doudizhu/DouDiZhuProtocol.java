package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.Rank;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主联机消息协议定义与序列化工具。
 * 集中管理所有消息类型、卡牌序列化、JSON 辅助方法。
 */
public class DouDiZhuProtocol {

    // ============ 消息类型常量 ============

    public static final String TYPE_JOIN = "JOIN";
    public static final String TYPE_SEAT_ASSIGNED = "SEAT_ASSIGNED";
    public static final String TYPE_SEAT_UPDATE = "SEAT_UPDATE";
    public static final String TYPE_HAND_CARDS = "HAND_CARDS";
    public static final String TYPE_BID_REQUEST = "BID_REQUEST";
    public static final String TYPE_BID_RESPONSE = "BID_RESPONSE";
    public static final String TYPE_BID_RESULT = "BID_RESULT";
    public static final String TYPE_GAME_START = "GAME_START";
    public static final String TYPE_REQUEST_PLAY = "REQUEST_PLAY";
    public static final String TYPE_PASS = "PASS";
    public static final String TYPE_SYNC_STATE = "SYNC_STATE";
    public static final String TYPE_STATE_ACK = "STATE_ACK";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_GAME_OVER = "GAME_OVER";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_CHAT_HISTORY = "CHAT_HISTORY";
    public static final String TYPE_BROADCAST_ACTION = "BROADCAST_ACTION";
    public static final String TYPE_PASS_ACTION = "PASS_ACTION";
    public static final String TYPE_ROOM_STATE = "ROOM_STATE";

    // ============ 卡牌序列化 ============

    public static String cardsToJson(List<Card> cards) {
        JSONArray array = new JSONArray();
        if (cards == null) return array.toString();
        for (Card card : cards) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("suit", card.getSuit().name());
                obj.put("rank", card.getRank().name());
                array.put(obj);
            } catch (JSONException e) {
                // ignore
            }
        }
        return array.toString();
    }

    public static List<Card> parseCardsFromJson(String json) {
        List<Card> cards = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) return cards;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String suitName = obj.getString("suit");
                String rankName = obj.getString("rank");
                cards.add(Card.create(
                        com.gamecenter.app.games.doudizhu.model.Suit.valueOf(suitName),
                        com.gamecenter.app.games.doudizhu.model.Rank.valueOf(rankName)
                ));
            }
        } catch (JSONException e) {
            // ignore
        }
        return cards;
    }

    // ============ JSON 辅助方法 ============

    public static JSONArray seatTypesToJson(int[] seatTypes) {
        JSONArray array = new JSONArray();
        for (int type : seatTypes) {
            array.put(type);
        }
        return array;
    }

    public static JSONArray booleanArrayToJson(boolean[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (boolean value : values) {
                array.put(value);
            }
        }
        return array;
    }

    public static JSONArray handCountsToJson(List<Card> playerHandCards, List<Card> seat1Cards, List<Card> seat2Cards) {
        JSONArray array = new JSONArray();
        array.put(playerHandCards.size());
        array.put(seat1Cards.size());
        array.put(seat2Cards.size());
        return array;
    }

    public static JSONArray intArrayToJson(int[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (int value : values) {
                array.put(value);
            }
        }
        return array;
    }

    public static int[] jsonToCounterArray(JSONArray array) {
        int[] counts = createFullDeckCounter();
        if (array == null) return counts;
        for (int i = 0; i < counts.length && i < array.length(); i++) {
            counts[i] = Math.max(0, array.optInt(i, counts[i]));
        }
        return counts;
    }

    public static int[] createFullDeckCounter() {
        int[] counts = new int[15];
        for (int i = 0; i < 13; i++) {
            counts[i] = 4;
        }
        counts[13] = 1;
        counts[14] = 1;
        return counts;
    }

    public static int rankCounterIndex(Card card) {
        if (card == null) return -1;
        int weight = card.getWeight();
        if (weight >= Rank.THREE.getWeight() && weight <= Rank.BIG_JOKER.getWeight()) {
            return weight - Rank.THREE.getWeight();
        }
        return -1;
    }

    public static void subtractCardsFromCounter(int[] counts, List<Card> cards) {
        if (counts == null || cards == null) return;
        for (Card card : cards) {
            int index = rankCounterIndex(card);
            if (index >= 0 && index < counts.length) {
                counts[index] = Math.max(0, counts[index] - 1);
            }
        }
    }

    // ============ 消息工厂方法 ============

    public static JSONObject createSeatAssignedMsg(int seatIndex, int[] seatTypes, String seatName,
                                                   boolean remoteP2P, boolean reconnected) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_SEAT_ASSIGNED);
            msg.put("seatIndex", seatIndex);
            msg.put("seatName", seatName);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
            msg.put("remoteP2P", remoteP2P);
            msg.put("reconnected", reconnected);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createSeatUpdateMsg(int[] seatTypes, int landlordIndex) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_SEAT_UPDATE);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
            msg.put("landlordIndex", landlordIndex);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createHandCardsMsg(String cardsJson, String bottomJson, int seatIndex) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_HAND_CARDS);
            msg.put("cards", cardsJson);
            msg.put("bottomCards", bottomJson);
            msg.put("seatIndex", seatIndex);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createBidRequestMsg(long stateVersion, int seatIndex, int currentTurn) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_BID_REQUEST);
            msg.put("stateVersion", stateVersion);
            msg.put("seatIndex", seatIndex);
            msg.put("currentTurn", currentTurn);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createGameStartMsg(int seatIndex, int currentTurn, int landlordIndex,
                                                int[] seatTypes, String bottomCardsJson) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_GAME_START);
            msg.put("seatIndex", seatIndex);
            msg.put("currentTurn", currentTurn);
            msg.put("landlordIndex", landlordIndex);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
            msg.put("bottomCards", bottomCardsJson);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createSyncStateMsg(int seatIndex, long version, int gameState,
                                                 int currentTurn, int landlordIndex, int winnerIndex,
                                                 int lastPlayerWhoPlayed, int[] seatTypes,
                                                 boolean[] playerPassed, JSONArray handCounts,
                                                 int[] cardCounter, String myCardsJson,
                                                 String bottomCardsJson, String played0Json,
                                                 String played1Json, String played2Json) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_SYNC_STATE);
            msg.put("stateVersion", version);
            msg.put("seatIndex", seatIndex);
            msg.put("gameState", gameState);
            msg.put("currentTurn", currentTurn);
            msg.put("landlordIndex", landlordIndex);
            msg.put("winnerIndex", winnerIndex);
            msg.put("lastPlayerWhoPlayed", lastPlayerWhoPlayed);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
            msg.put("playerPassed", booleanArrayToJson(playerPassed));
            msg.put("handCounts", handCounts);
            msg.put("cardCounter", intArrayToJson(cardCounter));
            msg.put("myCards", myCardsJson);
            msg.put("bottomCards", bottomCardsJson);
            msg.put("played0", played0Json);
            msg.put("played1", played1Json);
            msg.put("played2", played2Json);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createAckMsg(String ackType, long actionId, long stateVersion, boolean accepted, String reason) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_ACK);
            msg.put("ackType", ackType);
            msg.put("actionId", actionId);
            msg.put("stateVersion", stateVersion);
            msg.put("accepted", accepted);
            if (reason != null && !reason.isEmpty()) {
                msg.put("reason", reason);
            }
            msg.put("time", System.currentTimeMillis());
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createErrorMsg(String error) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_ERROR);
            msg.put("message", error);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createBroadcastPlayMsg(int seatIndex, String cardsJson, String cardTypeName,
                                                    int currentTurn, int landlordIndex, int[] seatTypes) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_BROADCAST_ACTION);
            msg.put("playerIndex", seatIndex);
            msg.put("cards", cardsJson);
            msg.put("cardType", cardTypeName);
            msg.put("currentTurn", currentTurn);
            msg.put("landlordIndex", landlordIndex);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createBroadcastPassMsg(int seatIndex, int currentTurn, int landlordIndex, int[] seatTypes) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_PASS_ACTION);
            msg.put("playerIndex", seatIndex);
            msg.put("currentTurn", currentTurn);
            msg.put("landlordIndex", landlordIndex);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createBidResultMsg(int seatIndex, boolean call, int currentTurn,
                                                int landlordIndex, int[] seatTypes) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_BID_RESULT);
            msg.put("seatIndex", seatIndex);
            msg.put("call", call);
            msg.put("currentTurn", currentTurn);
            msg.put("landlordIndex", landlordIndex);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }

    public static JSONObject createGameOverMsg(int winnerIndex) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_GAME_OVER);
            msg.put("winnerIndex", winnerIndex);
        } catch (JSONException e) {
            // ignore
        }
        return msg;
    }
}

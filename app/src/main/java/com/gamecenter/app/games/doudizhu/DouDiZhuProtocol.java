package com.gamecenter.app.games.doudizhu;

import android.util.Log;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.Rank;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主联机消息协议定义与序列化工具。
 *
 * <p>集中管理联机对战中所有消息类型常量、卡牌序列化/反序列化方法、
 * JSON 辅助方法以及各类消息的工厂方法。</p>
 *
 * <p>你可以把这个类想象成"翻译官"——它负责把游戏中的各种数据
 * （牌、座位、状态等）翻译成网络能传输的JSON格式，也能把收到的JSON翻译回游戏数据。</p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>定义所有网络消息的类型常量（JOIN、SEAT_ASSIGNED、BID_REQUEST 等）
 *       ——就像定义"电报代码本"，每种消息有一个代号</li>
 *   <li>提供卡牌列表与 JSON 之间的双向序列化（牌 ↔ 字符串）</li>
 *   <li>提供座位类型、布尔数组、手牌数量等数据的 JSON 序列化辅助方法</li>
 *   <li>提供记牌器计数器的序列化与反序列化</li>
 *   <li>提供各类游戏消息的工厂方法，统一消息格式</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>所有方法均为 static，作为纯工具类使用，无需实例化
 *       （就像一个工具箱，拿来就用，不需要先"制造"一个工具箱）</li>
 *   <li>消息格式统一为 JSON，type 字段标识消息类型</li>
 *   <li>卡牌序列化使用 suit + rank 的枚举名称，保证跨平台兼容</li>
 *   <li>记牌器数组长度固定为 15（3~K 各4张 + 小王1张 + 大王1张）</li>
 * </ul>
 */
public class DouDiZhuProtocol {

    private static final String TAG = "DouDiZhuProtocol";

    // ============ 消息类型常量 ============

    /** 客户端加入房间 */
    public static final String TYPE_JOIN = "JOIN";
    /** 服务端分配座位 */
    public static final String TYPE_SEAT_ASSIGNED = "SEAT_ASSIGNED";
    /** 座位状态更新（有人加入/离开） */
    public static final String TYPE_SEAT_UPDATE = "SEAT_UPDATE";
    /** 服务端发送手牌给客户端 */
    public static final String TYPE_HAND_CARDS = "HAND_CARDS";
    /** 服务端请求客户端叫地主 */
    public static final String TYPE_BID_REQUEST = "BID_REQUEST";
    /** 客户端回复叫地主决策 */
    public static final String TYPE_BID_RESPONSE = "BID_RESPONSE";
    /** 服务端广播叫地主结果 */
    public static final String TYPE_BID_RESULT = "BID_RESULT";
    /** 游戏开始通知 */
    public static final String TYPE_GAME_START = "GAME_START";
    /** 服务端请求客户端出牌 */
    public static final String TYPE_REQUEST_PLAY = "REQUEST_PLAY";
    /** 客户端选择不出 */
    public static final String TYPE_PASS = "PASS";
    /** 服务端同步完整游戏状态（用于断线重连） */
    public static final String TYPE_SYNC_STATE = "SYNC_STATE";
    /** 客户端确认状态同步 */
    public static final String TYPE_STATE_ACK = "STATE_ACK";
    /** 通用确认/应答消息 */
    public static final String TYPE_ACK = "ACK";
    /** 游戏结束通知 */
    public static final String TYPE_GAME_OVER = "GAME_OVER";
    /** 错误消息 */
    public static final String TYPE_ERROR = "ERROR";
    /** 聊天消息 */
    public static final String TYPE_CHAT = "CHAT";
    /** 聊天历史记录 */
    public static final String TYPE_CHAT_HISTORY = "CHAT_HISTORY";
    /** 广播出牌动作（某人出了什么牌） */
    public static final String TYPE_BROADCAST_ACTION = "BROADCAST_ACTION";
    /** 广播不出动作（某人选择不出） */
    public static final String TYPE_PASS_ACTION = "PASS_ACTION";
    /** 房间状态同步 */
    public static final String TYPE_ROOM_STATE = "ROOM_STATE";

    // ============ 卡牌序列化 ============

    /**
     * 将卡牌列表序列化为 JSON 字符串。
     *
     * <p>每张卡牌序列化为 {"suit": "HEART", "rank": "ACE"} 格式的 JSON 对象，
     * 整体封装为 JSON 数组。</p>
     *
     * @param cards 卡牌列表，null 时返回空数组字符串 "[]"
     * @return JSON 数组字符串
     */
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
                // 序列化失败时跳过该卡牌
                Log.w(TAG, "卡牌序列化失败，跳过", e);
            }
        }
        return array.toString();
    }

    /**
     * 从 JSON 字符串反序列化卡牌列表。
     *
     * <p>解析格式为 [{"suit": "HEART", "rank": "ACE"}, ...] 的 JSON 数组。
     * 解析失败时返回空列表而非 null。</p>
     *
     * @param json JSON 数组字符串
     * @return 卡牌列表，解析失败返回空列表
     */
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
            // 反序列化失败，返回已解析的部分
            Log.w(TAG, "反序列化失败，返回已解析部分", e);
        }
        return cards;
    }

    // ============ JSON 辅助方法 ============

    /**
     * 将座位类型数组序列化为 JSON 数组。
     *
     * @param seatTypes 座位类型数组（HOST=0, REMOTE=1, AI=2）
     * @return JSON 数组
     */
    public static JSONArray seatTypesToJson(int[] seatTypes) {
        JSONArray array = new JSONArray();
        for (int type : seatTypes) {
            array.put(type);
        }
        return array;
    }

    /**
     * 将布尔数组序列化为 JSON 数组。
     *
     * @param values 布尔数组（如 playerPassed）
     * @return JSON 数组，null 输入返回空数组
     */
    public static JSONArray booleanArrayToJson(boolean[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (boolean value : values) {
                array.put(value);
            }
        }
        return array;
    }

    /**
     * 将三个玩家的手牌数量序列化为 JSON 数组。
     *
     * @param playerHandCards 玩家0的手牌
     * @param seat1Cards      座位1的手牌
     * @param seat2Cards      座位2的手牌
     * @return 包含三个手牌数量的 JSON 数组
     */
    public static JSONArray handCountsToJson(List<Card> playerHandCards, List<Card> seat1Cards, List<Card> seat2Cards) {
        JSONArray array = new JSONArray();
        array.put(playerHandCards.size());
        array.put(seat1Cards.size());
        array.put(seat2Cards.size());
        return array;
    }

    /**
     * 将整型数组序列化为 JSON 数组。
     *
     * @param values 整型数组
     * @return JSON 数组，null 输入返回空数组
     */
    public static JSONArray intArrayToJson(int[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (int value : values) {
                array.put(value);
            }
        }
        return array;
    }

    /**
     * 从 JSON 数组反序列化记牌器计数数组。
     *
     * <p>数组长度固定为 15（3~K 各4张 + 小王1张 + 大王1张）。
     * 如果 JSON 数组长度不足，缺失部分使用默认值填充。
     * 数值不会小于 0。</p>
     *
     * @param array JSON 数组
     * @return 记牌器计数数组（长度15）
     */
    public static int[] jsonToCounterArray(JSONArray array) {
        int[] counts = createFullDeckCounter();
        if (array == null) return counts;
        for (int i = 0; i < counts.length && i < array.length(); i++) {
            // optInt 在解析失败时返回默认值（即当前计数值）
            counts[i] = Math.max(0, array.optInt(i, counts[i]));
        }
        return counts;
    }

    /**
     * 创建一副完整牌的记牌器初始计数数组。
     *
     * <p>索引 0-12 对应 3~K（各4张），索引 13 对应小王（1张），索引 14 对应大王（1张）。</p>
     *
     * @return 初始计数数组 [4,4,4,...,4,1,1]，长度15
     */
    public static int[] createFullDeckCounter() {
        int[] counts = new int[15];
        for (int i = 0; i < 13; i++) {
            counts[i] = 4;
        }
        counts[13] = 1;
        counts[14] = 1;
        return counts;
    }

    /**
     * 计算卡牌在记牌器数组中的索引。
     *
     * <p>索引 = card.weight - Rank.THREE.weight，范围 0~14。
     * 超出范围返回 -1。</p>
     *
     * @param card 卡牌
     * @return 记牌器数组索引，无效卡牌返回 -1
     */
    public static int rankCounterIndex(Card card) {
        if (card == null) return -1;
        int weight = card.getWeight();
        if (weight >= Rank.THREE.getWeight() && weight <= Rank.BIG_JOKER.getWeight()) {
            return weight - Rank.THREE.getWeight();
        }
        return -1;
    }

    /**
     * 从记牌器计数数组中减去指定卡牌列表的计数。
     *
     * <p>每张卡牌使对应索引的计数减 1，计数不会低于 0。</p>
     *
     * @param counts 记牌器计数数组（会被原地修改）
     * @param cards  要减去的卡牌列表
     */
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

    /**
     * 创建座位分配消息。
     *
     * <p>服务端在为新客户端分配座位后发送，告知客户端其座位信息和房间状态。</p>
     *
     * @param seatIndex   分配的座位索引
     * @param seatTypes   当前所有座位类型
     * @param seatName    座位名称
     * @param remoteP2P   是否为远程P2P模式
     * @param reconnected 是否为断线重连
     * @return 座位分配消息的 JSONObject
     */
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
            // JSON 构建失败，返回部分构建的消息
            Log.w(TAG, "JSON构建失败，返回部分消息", e);
        }
        return msg;
    }

    /**
     * 创建座位状态更新消息。
     *
     * <p>当有人加入或离开房间时广播给所有客户端。</p>
     *
     * @param seatTypes      当前所有座位类型
     * @param landlordIndex  地主座位索引（-1 表示未确定）
     * @return 座位更新消息的 JSONObject
     */
    public static JSONObject createSeatUpdateMsg(int[] seatTypes, int landlordIndex) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_SEAT_UPDATE);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
            msg.put("landlordIndex", landlordIndex);
        } catch (JSONException e) {
            // JSON 构建失败
            Log.w(TAG, "JSON构建失败", e);
        }
        return msg;
    }

    /**
     * 创建手牌分发消息。
     *
     * <p>服务端在发牌后发送给各客户端，包含该客户端的手牌和底牌。</p>
     *
     * @param cardsJson    手牌的 JSON 字符串
     * @param bottomJson   底牌的 JSON 字符串
     * @param seatIndex    接收者的座位索引
     * @return 手牌消息的 JSONObject
     */
    public static JSONObject createHandCardsMsg(String cardsJson, String bottomJson, int seatIndex) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_HAND_CARDS);
            msg.put("cards", cardsJson);
            msg.put("bottomCards", bottomJson);
            msg.put("seatIndex", seatIndex);
        } catch (JSONException e) {
            // JSON 构建失败
            Log.w(TAG, "JSON构建失败", e);
        }
        return msg;
    }

    /**
     * 创建叫地主请求消息。
     *
     * <p>服务端通知客户端轮到其叫地主。</p>
     *
     * @param stateVersion 当前状态版本号（用于乐观并发控制）
     * @param seatIndex    被请求的座位索引
     * @param currentTurn  当前轮到的座位
     * @return 叫地主请求消息的 JSONObject
     */
    public static JSONObject createBidRequestMsg(long stateVersion, int seatIndex, int currentTurn) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_BID_REQUEST);
            msg.put("stateVersion", stateVersion);
            msg.put("seatIndex", seatIndex);
            msg.put("currentTurn", currentTurn);
        } catch (JSONException e) {
            // JSON 构建失败
            Log.w(TAG, "JSON构建失败", e);
        }
        return msg;
    }

    /**
     * 创建游戏开始消息。
     *
     * <p>叫地主阶段结束后广播，通知所有客户端游戏正式开始。</p>
     *
     * @param seatIndex      接收者的座位索引
     * @param currentTurn    当前轮到的座位
     * @param landlordIndex  地主座位索引
     * @param seatTypes      当前所有座位类型
     * @param bottomCardsJson 底牌的 JSON 字符串
     * @return 游戏开始消息的 JSONObject
     */
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
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建完整游戏状态同步消息。
     *
     * <p>用于断线重连场景，将当前游戏的完整状态一次性发送给客户端，
     * 包括游戏状态、各玩家手牌数、出牌记录、记牌器等。</p>
     *
     * @param seatIndex          接收者的座位索引
     * @param version            状态版本号
     * @param gameState          游戏状态
     * @param currentTurn        当前轮到的座位
     * @param landlordIndex      地主座位索引
     * @param winnerIndex        获胜者索引（-1 表示未结束）
     * @param lastPlayerWhoPlayed 最后出牌的玩家索引
     * @param seatTypes          座位类型数组
     * @param playerPassed       各玩家是否选择不出
     * @param handCounts         各玩家手牌数量
     * @param cardCounter        记牌器计数数组
     * @param myCardsJson        接收者自己的手牌 JSON
     * @param bottomCardsJson    底牌 JSON
     * @param played0Json        玩家0出牌 JSON
     * @param played1Json        玩家1出牌 JSON
     * @param played2Json        玩家2出牌 JSON
     * @return 状态同步消息的 JSONObject
     */
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
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建通用确认/应答消息。
     *
     * <p>用于客户端对服务端请求的应答，包含是否接受及原因。</p>
     *
     * @param ackType      确认类型（如 "BID", "PLAY"）
     * @param actionId     动作ID
     * @param stateVersion 当前状态版本号
     * @param accepted     是否接受
     * @param reason       拒绝原因（接受时可为 null）
     * @return 确认消息的 JSONObject
     */
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
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建错误消息。
     *
     * @param error 错误描述
     * @return 错误消息的 JSONObject
     */
    public static JSONObject createErrorMsg(String error) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_ERROR);
            msg.put("message", error);
        } catch (JSONException e) {
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建出牌广播消息。
     *
     * <p>当某位玩家出牌后，服务端向所有客户端广播该动作。</p>
     *
     * @param seatIndex    出牌玩家的座位索引
     * @param cardsJson    出的牌的 JSON 字符串
     * @param cardTypeName 牌型名称
     * @param currentTurn  出牌后的下一个轮次
     * @param landlordIndex 地主座位索引
     * @param seatTypes    座位类型数组
     * @return 出牌广播消息的 JSONObject
     */
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
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建"不出"广播消息。
     *
     * <p>当某位玩家选择不出时，服务端向所有客户端广播该动作。</p>
     *
     * @param seatIndex    选择不出的玩家座位索引
     * @param currentTurn  不出后的下一个轮次
     * @param landlordIndex 地主座位索引
     * @param seatTypes    座位类型数组
     * @return "不出"广播消息的 JSONObject
     */
    public static JSONObject createBroadcastPassMsg(int seatIndex, int currentTurn, int landlordIndex, int[] seatTypes) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_PASS_ACTION);
            msg.put("playerIndex", seatIndex);
            msg.put("currentTurn", currentTurn);
            msg.put("landlordIndex", landlordIndex);
            msg.put("seatTypes", seatTypesToJson(seatTypes));
        } catch (JSONException e) {
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建叫地主结果广播消息。
     *
     * <p>当某位玩家做出叫地主决策后，服务端向所有客户端广播结果。</p>
     *
     * @param seatIndex    做出决策的玩家座位索引
     * @param call         true 表示叫地主，false 表示不叫
     * @param currentTurn  决策后的下一个轮次
     * @param landlordIndex 地主座位索引（已确定时为具体索引，否则为 -1）
     * @param seatTypes    座位类型数组
     * @return 叫地主结果消息的 JSONObject
     */
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
            // JSON 构建失败
        }
        return msg;
    }

    /**
     * 创建游戏结束消息。
     *
     * @param winnerIndex 获胜者的座位索引
     * @return 游戏结束消息的 JSONObject
     */
    public static JSONObject createGameOverMsg(int winnerIndex) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", TYPE_GAME_OVER);
            msg.put("winnerIndex", winnerIndex);
        } catch (JSONException e) {
            // JSON 构建失败
        }
        return msg;
    }
}

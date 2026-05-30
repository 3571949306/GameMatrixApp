package com.gamecenter.core.online;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 消息协议工具类。
 *
 * <p>定义联机通信的消息格式和工厂方法，所有 Relay 消息遵循统一的 JSON 协议：
 * <pre>
 *   {
 *     "type": "消息类型",
 *     "roomCode": "房间码",
 *     "timestamp": 时间戳（毫秒）,
 *     "data": { ... }
 *   }
 * </pre>
 *
 * <p>消息类型常量：
 * <ul>
 *   <li>{@link #TYPE_CREATE_ROOM} - 创建房间请求</li>
 *   <li>{@link #TYPE_ROOM_CREATED} - 房间创建成功（服务器响应）</li>
 *   <li>{@link #TYPE_JOIN_ROOM} - 加入房间请求</li>
 *   <li>{@link #TYPE_PLAYER_JOINED} - 玩家加入通知</li>
 *   <li>{@link #TYPE_LEAVE_ROOM} - 离开房间请求</li>
 *   <li>{@link #TYPE_PLAYER_LEFT} - 玩家离开通知</li>
 *   <li>{@link #TYPE_GAME_MESSAGE} - 游戏消息</li>
 *   <li>{@link #TYPE_HEARTBEAT} - 心跳保活</li>
 *   <li>{@link #TYPE_ERROR} - 错误消息</li>
 * </ul>
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
public final class MessageProtocol {

    // ========== 消息类型常量 ==========

    /** 创建房间请求 */
    public static final String TYPE_CREATE_ROOM = "create_room";

    /** 房间创建成功（服务器响应） */
    public static final String TYPE_ROOM_CREATED = "room_created";

    /** 加入房间请求 */
    public static final String TYPE_JOIN_ROOM = "join_room";

    /** 玩家加入通知 */
    public static final String TYPE_PLAYER_JOINED = "player_joined";

    /** 离开房间请求 */
    public static final String TYPE_LEAVE_ROOM = "leave_room";

    /** 玩家离开通知 */
    public static final String TYPE_PLAYER_LEFT = "player_left";

    /** 游戏消息 */
    public static final String TYPE_GAME_MESSAGE = "game_message";

    /** 心跳保活 */
    public static final String TYPE_HEARTBEAT = "heartbeat";

    /** 错误消息 */
    public static final String TYPE_ERROR = "error";

    // ========== JSON 字段名常量 ==========

    /** 消息类型字段 */
    public static final String FIELD_TYPE = "type";

    /** 房间码字段 */
    public static final String FIELD_ROOM_CODE = "roomCode";

    /** 时间戳字段 */
    public static final String FIELD_TIMESTAMP = "timestamp";

    /** 数据字段 */
    public static final String FIELD_DATA = "data";

    /** 游戏类型字段 */
    public static final String FIELD_GAME_TYPE = "gameType";

    /** 玩家 ID 字段 */
    public static final String FIELD_PLAYER_ID = "playerId";

    /** 错误码字段 */
    public static final String FIELD_ERROR_CODE = "errorCode";

    /** 错误消息字段 */
    public static final String FIELD_ERROR_MESSAGE = "errorMessage";

    /** 协议版本号 */
    private static final int PROTOCOL_VERSION = 1;

    /** 私有构造（工具类禁止实例化） */
    private MessageProtocol() {
        throw new UnsupportedOperationException("MessageProtocol 是工具类，不允许实例化");
    }

    /**
     * 创建创建房间请求消息。
     *
     * <p>消息格式：
     * <pre>
     *   {
     *     "type": "create_room",
     *     "gameType": "DDZ",
     *     "timestamp": 1700000000000
     *   }
     * </pre>
     *
     * @param gameType 游戏类型（如 "DDZ" 斗地主、"GOMOKU" 五子棋）
     * @return 创建房间请求消息
     */
    @NonNull
    public static JSONObject createRoomRequest(@NonNull String gameType) {
        JSONObject msg = new JSONObject();
        try {
            msg.put(FIELD_TYPE, TYPE_CREATE_ROOM);
            msg.put(FIELD_GAME_TYPE, gameType != null ? gameType : "");
            msg.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        } catch (JSONException e) {
            // JSONObject.put 不应抛出异常（key 非 null），但做防御性处理
            throw new RuntimeException("创建消息失败", e);
        }
        return msg;
    }

    /**
     * 创建加入房间请求消息。
     *
     * <p>消息格式：
     * <pre>
     *   {
     *     "type": "join_room",
     *     "roomCode": "A3K9M7",
     *     "timestamp": 1700000000000
     *   }
     * </pre>
     *
     * @param roomCode 房间码
     * @return 加入房间请求消息
     */
    @NonNull
    public static JSONObject joinRoomRequest(@NonNull String roomCode) {
        JSONObject msg = new JSONObject();
        try {
            msg.put(FIELD_TYPE, TYPE_JOIN_ROOM);
            msg.put(FIELD_ROOM_CODE, roomCode != null ? roomCode : "");
            msg.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException("创建消息失败", e);
        }
        return msg;
    }

    /**
     * 创建游戏消息。
     *
     * <p>消息格式：
     * <pre>
     *   {
     *     "type": "game_message",
     *     "roomCode": "A3K9M7",
     *     "timestamp": 1700000000000,
     *     "data": { ... }
     *   }
     * </pre>
     *
     * @param roomCode 房间码
     * @param gameData 游戏数据
     * @return 游戏消息
     */
    @NonNull
    public static JSONObject gameMessage(@Nullable String roomCode,
                                         @NonNull JSONObject gameData) {
        JSONObject msg = new JSONObject();
        try {
            msg.put(FIELD_TYPE, TYPE_GAME_MESSAGE);
            msg.put(FIELD_ROOM_CODE, roomCode != null ? roomCode : "");
            msg.put(FIELD_TIMESTAMP, System.currentTimeMillis());
            msg.put(FIELD_DATA, gameData);
        } catch (JSONException e) {
            throw new RuntimeException("创建消息失败", e);
        }
        return msg;
    }

    /**
     * 创建离开房间请求消息。
     *
     * <p>消息格式：
     * <pre>
     *   {
     *     "type": "leave_room",
     *     "roomCode": "A3K9M7",
     *     "timestamp": 1700000000000
     *   }
     * </pre>
     *
     * @param roomCode 房间码
     * @return 离开房间请求消息
     */
    @NonNull
    public static JSONObject leaveRoomRequest(@NonNull String roomCode) {
        JSONObject msg = new JSONObject();
        try {
            msg.put(FIELD_TYPE, TYPE_LEAVE_ROOM);
            msg.put(FIELD_ROOM_CODE, roomCode != null ? roomCode : "");
            msg.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException("创建消息失败", e);
        }
        return msg;
    }

    /**
     * 创建心跳消息。
     *
     * <p>消息格式：
     * <pre>
     *   {
     *     "type": "heartbeat",
     *     "timestamp": 1700000000000
     *   }
     * </pre>
     *
     * @return 心跳消息
     */
    @NonNull
    public static JSONObject heartbeat() {
        JSONObject msg = new JSONObject();
        try {
            msg.put(FIELD_TYPE, TYPE_HEARTBEAT);
            msg.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException("创建消息失败", e);
        }
        return msg;
    }

    /**
     * 创建错误消息。
     *
     * <p>消息格式：
     * <pre>
     *   {
     *     "type": "error",
     *     "errorCode": 1001,
     *     "errorMessage": "房间不存在",
     *     "timestamp": 1700000000000
     *   }
     * </pre>
     *
     * @param errorCode    错误码
     * @param errorMessage 错误描述
     * @return 错误消息
     */
    @NonNull
    public static JSONObject errorMessage(int errorCode, @NonNull String errorMessage) {
        JSONObject msg = new JSONObject();
        try {
            msg.put(FIELD_TYPE, TYPE_ERROR);
            msg.put(FIELD_ERROR_CODE, errorCode);
            msg.put(FIELD_ERROR_MESSAGE, errorMessage != null ? errorMessage : "");
            msg.put(FIELD_TIMESTAMP, System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException("创建消息失败", e);
        }
        return msg;
    }

    // ========== 解析方法 ==========

    /**
     * 从 JSON 字符串解析消息类型。
     *
     * @param jsonStr JSON 字符串
     * @return 消息类型，解析失败返回空字符串
     */
    @NonNull
    public static String parseType(@NonNull String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return "";
        }
        try {
            JSONObject msg = new JSONObject(jsonStr);
            return msg.optString(FIELD_TYPE, "");
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * 从 JSONObject 解析消息类型。
     *
     * @param msg 消息对象
     * @return 消息类型，解析失败返回空字符串
     */
    @NonNull
    public static String parseType(@NonNull JSONObject msg) {
        if (msg == null) {
            return "";
        }
        return msg.optString(FIELD_TYPE, "");
    }

    /**
     * 从消息中提取房间码。
     *
     * @param msg 消息对象
     * @return 房间码，不存在返回空字符串
     */
    @NonNull
    public static String parseRoomCode(@NonNull JSONObject msg) {
        if (msg == null) {
            return "";
        }
        return msg.optString(FIELD_ROOM_CODE, "");
    }

    /**
     * 从消息中提取游戏数据。
     *
     * @param msg 消息对象
     * @return 游戏数据，不存在返回 null
     */
    @Nullable
    public static JSONObject parseGameData(@NonNull JSONObject msg) {
        if (msg == null) {
            return null;
        }
        return msg.optJSONObject(FIELD_DATA);
    }

    /**
     * 验证消息格式是否合法。
     *
     * <p>合法消息必须包含 {@code type} 字段。
     *
     * @param msg 消息对象
     * @return 合法返回 true，否则返回 false
     */
    public static boolean isValidMessage(@Nullable JSONObject msg) {
        if (msg == null) {
            return false;
        }
        String type = msg.optString(FIELD_TYPE, "");
        return !type.isEmpty();
    }
}

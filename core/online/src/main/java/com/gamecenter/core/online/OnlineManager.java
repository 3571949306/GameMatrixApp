package com.gamecenter.core.online;

// [DEAD-ONLINE] 联机功能已通过 OnlinePlayGate 统一下线。
// 本类属于完全死模块：运行时入口全部被闸门挡住，全仓库零外部调用。
// 清理时连同所属 Gradle 模块整体删除。

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONObject;

/**
 * 联机管理器（公共接口）。
 *
 * <p>所有游戏模块通过 {@code implementation project(":core:online")} 依赖此库，
 * 实现跨游戏的联机功能调用。
 *
 * <p>核心功能：
 * <ul>
 *   <li>创建/加入房间</li>
 *   <li>发送/接收消息</li>
 *   <li>管理联机连接生命周期</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   OnlineManager manager = OnlineManager.getInstance(context);
 *   manager.initialize(context);
 *   String roomCode = manager.createRoom("DDZ");
 *   manager.sendMessage(jsonObject);
 *   manager.disconnect();
 * </pre>
 *
 * <p>架构说明：此类位于 {@code :core:online} AAR 库，
 * 可被任何 App 通过 {@code implementation} 依赖调用。
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
public class OnlineManager {

    private static final String TAG = "OnlineManager";

    /** 单例实例 */
    private static volatile OnlineManager instance;

    /** Relay 服务器 URL（默认占位，通过 setRelayUrl() 设置实际地址） */
    private static final String DEFAULT_RELAY_URL = "wss://your-server.example.com/ws/relay";

    /** WebSocket Relay 客户端 */
    @Nullable
    private RelayClient relayClient;

    /** 房间管理器 */
    @Nullable
    private RoomManager roomManager;

    /** 当前房间码 */
    @Nullable
    private String currentRoomCode;

    /** 当前游戏类型 */
    @Nullable
    private String currentGameType;

    /** 是否已连接 */
    private boolean connected = false;

    /** 消息监听器 */
    @Nullable
    private OnMessageListener messageListener;

    /** 连接状态监听器 */
    @Nullable
    private OnConnectionStateListener connectionStateListener;

    /** Relay 服务器 URL */
    private String relayUrl;

    /**
     * 消息监听器接口。
     */
    public interface OnMessageListener {
        /**
         * 收到消息时回调。
         *
         * @param message 消息内容（JSON 格式）
         */
        void onMessage(@NonNull JSONObject message);
    }

    /**
     * 连接状态监听器接口。
     */
    public interface OnConnectionStateListener {
        /** 连接成功 */
        void onConnected();

        /** 连接断开 */
        void onDisconnected(@Nullable String reason);

        /** 连接错误 */
        void onError(@NonNull String errorMessage);
    }

    /**
     * 获取单例实例。
     *
     * @param context Android Context
     * @return OnlineManager 单例
     */
    @NonNull
    public static OnlineManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (OnlineManager.class) {
                if (instance == null) {
                    instance = new OnlineManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造函数。
     */
    private OnlineManager(@NonNull Context context) {
        this.relayUrl = DEFAULT_RELAY_URL;
        this.roomManager = new RoomManager(context);
    }

    /**
     * 初始化联机管理器。
     *
     * <p>创建 WebSocket 客户端实例，但不立即连接。
     * 实际连接在 {@link #createRoom(String)} 或 {@link #joinRoom(String)} 时建立。
     *
     * @param context Android Context
     */
    public void initialize(@NonNull Context context) {
        Log.i(TAG, "OnlineManager 初始化");
        this.relayClient = new RelayClient(relayUrl);
        this.roomManager = new RoomManager(context);

        // 设置消息回调
        relayClient.setMessageListener(messageStr -> {
            try {
                JSONObject parsed = new JSONObject(messageStr);
                handleMessage(parsed);
            } catch (Exception e) {
                Log.e(TAG, "解析消息失败: " + messageStr, e);
            }
        });

        relayClient.setConnectionStateListener(new RelayClient.OnConnectionStateListener() {
            @Override
            public void onConnected() {
                connected = true;
                if (connectionStateListener != null) {
                    connectionStateListener.onConnected();
                }
            }

            @Override
            public void onDisconnected(String reason) {
                connected = false;
                if (connectionStateListener != null) {
                    connectionStateListener.onDisconnected(reason);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (connectionStateListener != null) {
                    connectionStateListener.onError(errorMessage);
                }
            }
        });

        Log.d(TAG, "OnlineManager 初始化完成");
    }

    /**
     * 创建房间。
     *
     * <p>连接到 Relay 服务器并发送创建房间请求。
     * 成功后返回房间码，其他玩家可通过房间码加入。
     *
     * @param gameType 游戏类型（如 "DDZ" 斗地主、"GOMOKU" 五子棋）
     * @return 房间码，创建失败返回 null
     */
    @Nullable
    public String createRoom(@NonNull String gameType) {
        if (relayClient == null) {
            Log.e(TAG, "OnlineManager 未初始化");
            return null;
        }

        this.currentGameType = gameType;

        // 连接服务器（如果未连接）
        if (!connected) {
            boolean connectSuccess = relayClient.connect();
            if (!connectSuccess) {
                Log.e(TAG, "连接 Relay 服务器失败");
                return null;
            }

            // 等待连接建立（简化实现，实际应异步等待）
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 发送创建房间消息
        JSONObject createMsg = MessageProtocol.createRoomRequest(gameType);
        relayClient.send(createMsg.toString());

        // 简化实现：生成房间码
        // 实际应等待服务器返回房间码
        currentRoomCode = RoomManager.generateRoomCode();
        if (roomManager != null) {
            roomManager.setCurrentRoom(currentRoomCode, gameType, true);
        }

        Log.i(TAG, "创建房间: " + currentRoomCode + " (游戏: " + gameType + ")");
        return currentRoomCode;
    }

    /**
     * 加入房间。
     *
     * @param roomCode 房间码
     * @return 加入成功返回 true，否则返回 false
     */
    public boolean joinRoom(@NonNull String roomCode) {
        if (relayClient == null) {
            Log.e(TAG, "OnlineManager 未初始化");
            return false;
        }

        this.currentRoomCode = roomCode;

        // 连接服务器（如果未连接）
        if (!connected) {
            boolean connectSuccess = relayClient.connect();
            if (!connectSuccess) {
                Log.e(TAG, "连接 Relay 服务器失败");
                return false;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 发送加入房间消息
        JSONObject joinMsg = MessageProtocol.joinRoomRequest(roomCode);
        relayClient.send(joinMsg.toString());

        if (roomManager != null) {
            roomManager.setCurrentRoom(roomCode, currentGameType, false);
        }

        Log.i(TAG, "加入房间: " + roomCode);
        return true;
    }

    /**
     * 发送消息到当前房间。
     *
     * @param message 消息内容（JSON 格式）
     */
    public void sendMessage(@NonNull JSONObject message) {
        if (relayClient == null || !connected) {
            Log.w(TAG, "未连接，无法发送消息");
            return;
        }

        JSONObject wrappedMsg = MessageProtocol.gameMessage(
                currentRoomCode, message);
        relayClient.send(wrappedMsg.toString());

        Log.d(TAG, "消息已发送到房间: " + currentRoomCode);
    }

    /**
     * 断开联机连接。
     */
    public void disconnect() {
        if (relayClient != null && connected) {
            // 发送离开房间消息
            if (currentRoomCode != null) {
                JSONObject leaveMsg = MessageProtocol.leaveRoomRequest(currentRoomCode);
                relayClient.send(leaveMsg.toString());
            }

            relayClient.disconnect();
            connected = false;
        }

        currentRoomCode = null;
        currentGameType = null;

        if (roomManager != null) {
            roomManager.clearCurrentRoom();
        }

        Log.i(TAG, "已断开联机连接");
    }

    /**
     * 处理收到的消息。
     */
    private void handleMessage(@NonNull JSONObject message) {
        try {
            String type = message.optString("type", "");

            switch (type) {
                case MessageProtocol.TYPE_ROOM_CREATED:
                    handleRoomCreated(message);
                    break;
                case MessageProtocol.TYPE_PLAYER_JOINED:
                    handlePlayerJoined(message);
                    break;
                case MessageProtocol.TYPE_PLAYER_LEFT:
                    handlePlayerLeft(message);
                    break;
                case MessageProtocol.TYPE_GAME_MESSAGE:
                    // 转发给上层监听器
                    if (messageListener != null) {
                        JSONObject gameData = message.optJSONObject("data");
                        if (gameData != null) {
                            messageListener.onMessage(gameData);
                        }
                    }
                    break;
                default:
                    Log.d(TAG, "未知消息类型: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理消息失败", e);
        }
    }

    private void handleRoomCreated(@NonNull JSONObject message) {
        String roomCode = message.optString("roomCode", "");
        if (!roomCode.isEmpty()) {
            currentRoomCode = roomCode;
            Log.i(TAG, "房间创建成功: " + roomCode);
        }
    }

    private void handlePlayerJoined(@NonNull JSONObject message) {
        String playerId = message.optString("playerId", "");
        Log.i(TAG, "玩家加入房间: " + playerId);
    }

    private void handlePlayerLeft(@NonNull JSONObject message) {
        String playerId = message.optString("playerId", "");
        Log.i(TAG, "玩家离开房间: " + playerId);
    }

    // ========== Getter/Setter ==========

    @Nullable
    public String getCurrentRoomCode() {
        return currentRoomCode;
    }

    @Nullable
    public String getCurrentGameType() {
        return currentGameType;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isModuleLoaded() {
        return relayClient != null;
    }

    public void setMessageListener(@Nullable OnMessageListener listener) {
        this.messageListener = listener;
    }

    public void setConnectionStateListener(@Nullable OnConnectionStateListener listener) {
        this.connectionStateListener = listener;
    }

    public void setRelayUrl(@NonNull String url) {
        if (url != null && !url.isEmpty()) {
            this.relayUrl = url;
        }
    }

    @Nullable
    public RoomManager getRoomManager() {
        return roomManager;
    }
}

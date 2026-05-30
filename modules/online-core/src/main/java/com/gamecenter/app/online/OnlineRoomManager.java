package com.gamecenter.app.online;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.gamecenter.app.network.OnlineChatHelper;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * 联机房间管理器。
 * 
 * 封装房间创建/加入/离开、连接回调、聊天等联机通用逻辑。
 * 各游戏通过组合方式复用此类，而不必继承特定基类。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0 (从 BaseOnlineActivity 提取)
 * @since 2026-05-26
 */
public class OnlineRoomManager {
    
    private static final String TAG = "OnlineRoomManager";
    
    // ==================== 基础配置字段 ====================
    
    /** 应用级 Context，避免持有 Activity 导致内存泄漏 */
    private final Context context;
    
    /** 偏好设置文件名，不同游戏使用不同文件名避免数据混淆 */
    private final String prefsName;
    
    /** 游戏名称，用于大厅标题显示 */
    private final String gameName;
    
    /** 主线程 Handler，用于将回调投递到 UI 线程 */
    private final android.os.Handler mainHandler;
    
    // ==================== 核心网络组件 ====================
    
    /** 偏好设置，用于持久化存储令牌等信息 */
    private android.content.SharedPreferences prefs;
    
    /** 主机端服务器实例，仅在房主端使用 */
    private GameSocketServer server;
    
    /** 客户端连接实例，仅在加入方使用 */
    private GameSocketClient client;
    
    /** 聊天辅助类，处理聊天消息的收发与显示 */
    private OnlineChatHelper chatHelper;
    
    // ==================== 游戏状态变量 ====================
    
    /** 是否为主机端（房主） */
    private volatile boolean isHost = false;
    
    /** 是否正在游戏中 */
    private volatile boolean isPlaying = false;
    
    /** 本方玩家 ID（房主=1，加入方=2） */
    private int myPlayerId = -1;
    
    /** 对手玩家 ID */
    private int opponentPlayerId = -1;
    
    /** 当前房间码 */
    private String roomCode = "";
    
    /** 连接状态监听器列表 */
    private final List<ConnectionListener> connectionListeners;
    
    /** 游戏事件监听器 */
    private GameEventListener gameEventListener;
    
    // ==================== 构造函数 ====================
    
    /**
     * 构造联机房间管理器。
     * 
     * @param context   上下文，内部会转为 ApplicationContext 避免内存泄漏
     * @param prefsName 偏好设置文件名
     * @param gameName  游戏名称
     */
    public OnlineRoomManager(@NonNull Context context, 
                           @NonNull String prefsName, 
                           @NonNull String gameName) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.prefsName = prefsName != null ? prefsName : "default";
        this.gameName = gameName != null ? gameName : "游戏";
        this.mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        this.connectionListeners = new CopyOnWriteArrayList<>();
        this.prefs = this.context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        
        // 初始化聊天辅助类
        this.chatHelper = new OnlineChatHelper(this.context);
        
        Log.d(TAG, "OnlineRoomManager 已初始化: " + gameName);
    }
    
    // ==================== 房间管理方法 ====================
    
    /**
     * 初始化服务器（房主端调用）。
     * 创建 GameSocketServer 实例并启动。
     */
    public void initServer() {
        if (server != null) {
            Log.w(TAG, "服务器已初始化");
            return;
        }
        
        server = new GameSocketServer(context);
        isHost = true;
        
        Log.i(TAG, "服务器已初始化（房主端）");
    }
    
    /**
     * 初始化客户端（加入方调用）。
     * 创建 GameSocketClient 实例。
     */
    public void initClient() {
        if (client != null) {
            Log.w(TAG, "客户端已初始化");
            return;
        }
        
        client = new GameSocketClient(context);
        isHost = false;
        
        Log.i(TAG, "客户端已初始化（加入方）");
    }
    
    /**
     * 创建房间（房主端）。
     * 
     * @param config 房间配置
     * @return 创建成功返回 true，否则返回 false
     */
    public boolean createRoom(@NonNull RoomConfig config) {
        if (!isHost || server == null) {
            Log.e(TAG, "不是房主或未初始化服务器");
            return false;
        }
        
        // 简化实现：启动服务器
        boolean success = server.start(config.getPort());
        if (success) {
            room = config.getRoomCode();
            Log.i(TAG, "房间已创建: " + roomCode);
            
            // 回调监听器
            notifyRoomCreated(config);
        }
        
        return success;
    }
    
    /**
     * 加入房间（加入方）。
     * 
     * @param roomCode 房间码
     * @param serverIp  服务器 IP 地址
     * @param port     端口
     * @return 加入请求已发送返回 true，否则返回 false
     */
    public boolean joinRoom(@NonNull String roomCode, 
                            @NonNull String serverIp, 
                            int port) {
        if (isHost || client == null) {
            Log.e(TAG, "是房主或未初始化客户端");
            return false;
        }
        
        this.roomCode = roomCode;
        
        // 简化实现：连接到服务器
        String url = "ws://" + serverIp + ":" + port + "/";
        boolean success = client.connect(url, new GameSocketClient.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "已连接到房间: " + roomCode);
                notifyConnected();
            }
            
            @Override
            public void onMessage(@NonNull String message) {
                handleGameMessage(message);
            }
            
            @Override
            public void onDisconnected() {
                Log.i(TAG, "已断开连接: " + roomCode);
                notifyDisconnected();
            }
            
            @Override
            public void onError(@NonNull String errorMessage) {
                Log.e(TAG, "连接错误: " + errorMessage);
                notifyError(errorMessage);
            }
        });
        
        return success;
    }
    
    /**
     * 离开房间。
     */
    public void leaveRoom() {
        if (server != null) {
            server.stop();
            server = null;
        }
        
        if (client != null) {
            client.disconnect();
            client = null;
        }
        
        room = "";
        isPlaying = false;
        
        Log.i(TAG, "已离开房间");
    }
    
    /**
     * 发送游戏消息。
     * 
     * @param message 消息内容（JSON 格式）
     * @return 发送成功返回 true，否则返回 false
     */
    public boolean sendMessage(@NonNull String message) {
        if (client != null && client.isConnected()) {
            return client.sendMessage(message);
        }
        
        if (server != null && server.isRunning()) {
            server.broadcast(message);
            return true;
        }
        
        Log.w(TAG, "未连接到任何房间，无法发送消息");
        return false;
    }
    
    // ==================== 监听器接口 ====================
    
    /**
     * 连接状态监听器。
     */
    public interface ConnectionListener {
        /**
         * 连接到房间。
         */
        void onConnected();
        
        /**
         * 收到消息。
         * 
         * @param message 消息内容
         */
        void onMessage(@NonNull String message);
        
        /**
         * 断开连接。
         */
        void onDisconnected();
        
        /**
         * 发生错误。
         * 
         * @param errorMessage 错误信息
         */
        void onError(@NonNull String errorMessage);
    }
    
    /**
     * 游戏事件监听器。
     */
    public interface GameEventListener {
        /**
         * 游戏开始。
         */
        void onGameStarted();
        
        /**
         * 收到游戏消息。
         * 
         * @param message 消息内容（JSON 对象）
         */
        void onGameMessageReceived(@NonNull String message);
        
        /**
         * 游戏重置。
         */
        void onGameReset();
    }
    
    /**
     * 房间配置。
     */
    public static class RoomConfig {
        private String roomCode;
        private int port;
        private int maxPlayers;
        
        public RoomConfig(@NonNull String roomCode, int port) {
            this.roomCode = roomCode;
            this.port = port;
            this.maxPlayers = 2;
        }
        
        @NonNull
        public String getRoomCode() { return roomCode; }
        
        public int getPort() { return port; }
        
        public int getMaxPlayers() { return maxPlayers; }
        
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    }
    
    // ==================== 监听器管理 ====================
    
    /**
     * 添加连接状态监听器。
     * 
     * @param listener 监听器
     */
    public void addConnectionListener(@NonNull ConnectionListener listener) {
        connectionListeners.add(listener);
    }
    
    /**
     * 移除连接状态监听器。
     * 
     * @param listener 监听器
     */
    public void removeConnectionListener(@NonNull ConnectionListener listener) {
        connectionListeners.remove(listener);
    }
    
    /**
     * 设置游戏事件监听器。
     * 
     * @param listener 监听器
     */
    public void setGameEventListener(GameEventListener listener) {
        this.gameEventListener = listener;
    }
    
    // ==================== 通知方法 ====================
    
    /**
     * 通知房间已创建。
     */
    private void notifyRoomCreated(@NonNull RoomConfig config) {
        mainHandler.post(() -> {
            for (ConnectionListener listener : connectionListeners) {
                listener.onConnected(); // 简化：创建成功后回调 onConnected
            }
        });
    }
    
    /**
     * 通知已连接。
     */
    private void notifyConnected() {
        mainHandler.post(() -> {
            for (ConnectionListener listener : connectionListeners) {
                listener.onConnected();
            }
        });
    }
    
    /**
     * 通知已断开连接。
     */
    private void notifyDisconnected() {
        mainHandler.post(() -> {
            for (ConnectionListener listener : connectionListeners) {
                listener.onDisconnected();
            }
        });
    }
    
    /**
     * 通知发生错误。
     */
    private void notifyError(@NonNull String errorMessage) {
        mainHandler.post(() -> {
            for (ConnectionListener listener : connectionListeners) {
                listener.onError(errorMessage);
            }
        });
    }
    
    /**
     * 处理游戏消息。
     * 
     * @param message 消息内容
     */
    private void handleGameMessage(@NonNull String message) {
        mainHandler.post(() -> {
            // 回调消息监听器
            for (ConnectionListener listener : connectionListeners) {
                listener.onMessage(message);
            }
            
            // 回调游戏事件监听器
            if (gameEventListener != null) {
                gameEventListener.onGameMessageReceived(message);
            }
        });
    }
    
    /**
     * 获取聊天辅助类实例。
     * 
     * @return OnlineChatHelper 实例
     */
    @NonNull
    public OnlineChatHelper getChatHelper() {
        return chatHelper;
    }
    
    /**
     * 显示聊天对话框（弹窗模式）。
     */
    public void showChatDialog() {
        if (chatHelper != null) {
            chatHelper.showChatDialog();
        }
    }
    
    /**
     * 设置聊天消息发送监听器。
     * 
     * @param listener 消息发送监听器
     */
    public void setChatMessageSendListener(OnlineChatHelper.OnChatMessageSendListener listener) {
        if (chatHelper != null) {
            chatHelper.setOnChatMessageSendListener(listener);
        }
    }
    
    /**
     * 处理收到的聊天消息（从网络层接收）。
     * 
     * @param message JSON 格式的聊天消息
     */
    public void handleIncomingChatMessage(@NonNull String message) {
        if (chatHelper != null) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(message);
                if (chatHelper.isChatMessage(json)) {
                    chatHelper.handleIncomingChat(json);
                }
            } catch (org.json.JSONException e) {
                Log.e(TAG, "解析聊天消息失败: " + message, e);
            }
        }
    }
    
    /**
     * 发送聊天消息（通过聊天辅助类）。
     * 
     * @param text 消息内容
     */
    public void sendChatMessage(@NonNull String text) {
        if (chatHelper != null) {
            chatHelper.sendChat(text);
        }
    }
    
    // ==================== Getter/Setter ====================
    
    /**
     * 获取游戏名称。
     */
    @NonNull
    public String getGameName() {
        return gameName;
    }
    
    /**
     * 获取当前房间码。
     */
    @NonNull
    public String getRoomCode() {
        return roomCode != null ? roomCode : "";
    }
    
    /**
     * 检查是否为主机端。
     */
    public boolean isHost() {
        return isHost;
    }
    
    /**
     * 检查是否正在游戏中。
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    /**
     * 设置游戏状态。
     */
    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }
    
    /**
     * 获取本方玩家 ID。
     */
    public int getMyPlayerId() {
        return myPlayerId;
    }
    
    /**
     * 设置本方玩家 ID。
     */
    public void setMyPlayerId(int playerId) {
        this.myPlayerId = playerId;
    }
    
    /**
     * 获取对手玩家 ID。
     */
    public int getOpponentPlayerId() {
        return opponentPlayerId;
    }
    
    /**
     * 设置对手玩家 ID。
     */
    public void setOpponentPlayerId(int playerId) {
        this.opponentPlayerId = playerId;
    }
    
    /**
     * 释放所有资源（退出房间时调用）。
     */
    public void release() {
        leaveRoom();
        connectionListeners.clear();
        gameEventListener = null;
        
        // 清理聊天辅助类资源
        if (chatHelper != null) {
            chatHelper.cleanup();
            chatHelper = null;
        }
        
        Log.d(TAG, "OnlineRoomManager 资源已释放");
    }
}

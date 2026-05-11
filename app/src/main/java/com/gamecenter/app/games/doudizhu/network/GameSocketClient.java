package com.gamecenter.app.games.doudizhu.network;

import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.content.Context;

import com.gamecenter.app.network.OkHttpClientProvider;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 斗地主游戏客户端（Game Socket Client）
 * 支持三种连接模式：
 * 1. TCP Socket 直连（局域网）
 * 2. HTTP Relay 轮询（云联机 fallback）
 * 3. WebSocket 长连接（云联机主方案，跨境网络优化）
 */
public class GameSocketClient {

    // 日志标签
    private static final String TAG = "GameSocketClient";

    // ============ 调试日志开关 ============
    private static final boolean DEBUG_NETWORK = true;
    private static final String LOG_PREFIX = "[DDZ-WSS]";

    private static void logEvent(String event, String roomCode, int playerId, String messageType) {
        if (!DEBUG_NETWORK) return;
        Log.d(TAG, LOG_PREFIX + " [" + event + "] room=" + (roomCode != null ? roomCode : "-")
                + " player=" + playerId + " type=" + (messageType != null ? messageType : "-")
                + " t=" + System.currentTimeMillis());
    }

    private static void logWs(String event, String wsUrl, String detail) {
        if (!DEBUG_NETWORK) return;
        Log.d(TAG, LOG_PREFIX + " [WS_" + event + "] url=" + (wsUrl != null ? wsUrl.replaceAll("\\?.*", "?...") : "-")
                + " " + (detail != null ? detail : ""));
    }

    private static void logError(String event, String detail) {
        if (!DEBUG_NETWORK) return;
        Log.e(TAG, LOG_PREFIX + " [ERR_" + event + "] " + (detail != null ? detail : ""));
    }

    // 服务器连接超时（毫秒）
    private static final int CONNECT_TIMEOUT = 5000;

    // 心跳间隔（毫秒）
    private static final long HEARTBEAT_INTERVAL = 3000L;

    // 心跳超时时间（毫秒）
    private static final long HEARTBEAT_TIMEOUT = 30000L;

    // 最大重连次数
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 3;

    // 重连间隔（毫秒）
    private static final long DEFAULT_RECONNECT_INTERVAL = 2000L;

    private static final long DEFAULT_MAX_RECONNECT_INTERVAL = 15000L;

    // ============ WebSocket 模式常量（跨境网络优化） ============

    // WebSocket 心跳间隔 10 秒（不要太频繁，减少跨境流量）
    private static final long WS_HEARTBEAT_INTERVAL = 10000L;

    // WebSocket 心跳超时 45 秒（容忍美国服务器高延迟）
    private static final long WS_HEARTBEAT_TIMEOUT = 45000L;

    // 连续 2 次无响应才判定断线（避免偶发抖动误杀）
    private static final int WS_MAX_MISSED_PONGS = 2;

    // 消息缓冲队列最大长度（防止内存泄漏）
    private static final int MAX_PENDING_MESSAGES = 32;

    // ============ 连接状态 ============

    // 连接状态枚举
    public enum ConnectionState {
        DISCONNECTED,     // 未连接
        CONNECTING,       // 连接中
        CONNECTED,        // 已连接
        AUTHENTICATED,    // 已认证（完成 JOIN）
        RECONNECTING      // 重新连接中
    }

    // 当前连接状态
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    // 服务器信息
    private String serverHost;
    private int serverPort;

    // Socket 连接
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    // 读取线程
    private Thread readThread;

    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SocketWriter");
        thread.setDaemon(true);
        return thread;
    });

    // 心跳调度器
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatTask;

    // 重连调度器
    private ScheduledExecutorService reconnectScheduler;
    private ScheduledFuture<?> reconnectTask;

    // 重连计数
    private int reconnectAttempts = 0;
    private int maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS;
    private long reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    private long maxReconnectInterval = DEFAULT_MAX_RECONNECT_INTERVAL;

    private volatile boolean manualDisconnect = false;
    private volatile long lastServerMessageTime = 0L;
    private volatile boolean suppressNextDisconnectNotice = false;
    private String peerToken = "";
    private int protocolVersion = 2;
    private volatile boolean relayMode = false;
    private volatile boolean relayPolling = false;
    private String relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL;
    private String relayRoomCode = "";
    private String relayClientToken = "";
    private Thread relayPollThread;

    // ============ WebSocket 模式字段 ============

    // 是否处于 WebSocket 模式（区分 TCP / HTTP / WS 三种模式）
    private volatile boolean webSocketMode = false;

    // 防重复断线处理标志（避免 onFailure / onClosed 重复触发 handleDisconnection）
    private volatile boolean isHandlingDisconnection = false;

    // OkHttp WebSocket 连接实例
    private WebSocket webSocket;

    // OkHttp 客户端实例（复用，避免重复创建）
    private OkHttpClient okHttpClient;

    // WebSocket 连接 URL（重连时需要）
    private String wsUrl;

    // 消息缓冲队列（连接未建立时暂存消息，连接成功后自动发送）
    private final ConcurrentLinkedQueue<JSONObject> pendingMessages = new ConcurrentLinkedQueue<>();

    // 连续未收到响应的次数（用于温和判定断线）
    private int consecutiveMissedPongs = 0;

    // Handler 用于主线程回调
    private Handler mainHandler;

    // 回调接口
    private OnConnectedListener connectedListener;
    private OnDisconnectedListener disconnectedListener;
    private OnMessageReceivedListener messageListener;
    private OnErrorListener errorListener;
    private OnStateChangedListener stateChangedListener;

    // 玩家信息
    private String playerName = "Player";
    private int clientId = -1;

    // ============ 构造函数 ============

    private final Context context;

    public GameSocketClient() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = null;
    }

    public GameSocketClient(Context context) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = context != null ? context.getApplicationContext() : null;
    }

    // ============ 连接管理 ============

    /**
     * 连接到服务器（通过 NsdServiceInfo）
     */
    public void connect(NsdServiceInfo serviceInfo) {
        if (serviceInfo.getHost() != null && serviceInfo.getPort() > 0) {
            connect(serviceInfo.getHost().getHostAddress(), serviceInfo.getPort());
        } else {
            postError("服务信息无效");
        }
    }

    /**
     * 连接到服务器（通过 IP 和端口）
     * @param host 服务器 IP 地址
     * @param port 服务器端口
     */
    public void connect(String host, int port) {
        if (state == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting");
            return;
        }

        if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED
                || state == ConnectionState.RECONNECTING) {
            Log.d(TAG, "Force disconnecting previous connection");
            forceDisconnect();
        } else if (relayMode) {
            stopRelay();
        } else if (webSocketMode) {
            closeWebSocketQuietly();
        }

        setState(ConnectionState.CONNECTING);
        manualDisconnect = false;
        clientId = -1;
        lastServerMessageTime = System.currentTimeMillis();
        this.serverHost = host;
        this.serverPort = port;

        new Thread(() -> {
            try {
                doConnect();
            } catch (IOException e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
                handleConnectionFailure(e.getMessage());
            }
        }).start();
    }

    /**
     * 通过 WebSocket 连接到服务器（跨境网络优化主方案）
     * @param wsUrl WebSocket URL，例如 wss://relay.example.com/ws?room=ABC123&role=client&token=xxx
     */
    public void connectWebSocket(String wsUrl) {
        logWs("CONNECTING", wsUrl, "client connecting");
        if (state == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting");
            return;
        }

        if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED
                || state == ConnectionState.RECONNECTING) {
            Log.d(TAG, "Force disconnecting previous connection");
            forceDisconnect();
        } else if (relayMode) {
            stopRelay();
        } else if (webSocketMode) {
            closeWebSocketQuietly();
        }

        this.wsUrl = wsUrl;
        webSocketMode = true;
        manualDisconnect = false;
        clientId = -1;
        lastServerMessageTime = System.currentTimeMillis();
        consecutiveMissedPongs = 0;
        setState(ConnectionState.CONNECTING);

        // 从 wsUrl 中提取 roomCode
        try {
            int roomStart = wsUrl.indexOf("?room=");
            if (roomStart > 0) {
                roomStart += 6;
                int roomEnd = wsUrl.indexOf("&", roomStart);
                relayRoomCode = roomEnd > 0
                        ? wsUrl.substring(roomStart, roomEnd)
                        : wsUrl.substring(roomStart);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse roomCode from wsUrl: " + e.getMessage());
        }

        new Thread(() -> {
            try {
                doWebSocketConnect();
            } catch (Exception e) {
                Log.e(TAG, "WebSocket connection error: " + e.getMessage());
                handleConnectionFailure(e.getMessage());
            }
        }, "DdzWebSocketConnect").start();
    }

    public void connectRelay(String roomCode, String baseUrl) {
        String code = RemoteP2PUtil.normalizeRoomCode(roomCode);
        if (code.isEmpty()) {
            postError("房间码无效");
            return;
        }
        if (state == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting");
            return;
        }

        if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED
                || state == ConnectionState.RECONNECTING) {
            Log.d(TAG, "Force disconnecting previous connection");
            forceDisconnect();
        } else if (webSocketMode) {
            closeWebSocketQuietly();
        } else if (relayMode) {
            stopRelay();
        }

        relayMode = true;
        relayPolling = false;
        relayRoomCode = code;
        relayBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty()
                ? baseUrl.trim()
                : RelayHttpClient.DEFAULT_BASE_URL;
        relayClientToken = "";
        serverHost = "";
        serverPort = 0;
        manualDisconnect = false;
        clientId = -1;
        lastServerMessageTime = System.currentTimeMillis();
        setState(ConnectionState.CONNECTING);

        new Thread(() -> {
            try {
                doRelayJoin();
            } catch (Exception e) {
                Log.e(TAG, "Relay connection error: " + e.getMessage(), e);
                handleConnectionFailure(e.getMessage());
            }
        }, "DdzRelayClientJoin").start();
    }

    private void forceDisconnect() {
        stopHeartbeat();
        stopReconnect();
        stopRelay();
        closeWebSocketQuietly();
        state = ConnectionState.DISCONNECTED;
        closeTransportQuietly();
    }

    private void closeTransportQuietly() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket: " + e.getMessage());
        }
        reader = null;
        writer = null;
        socket = null;
    }

    /**
     * 安静关闭 WebSocket 连接（不触发回调）
     */
    private void closeWebSocketQuietly() {
        webSocketMode = false;
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Disconnect");
            } catch (Exception e) {
                Log.w(TAG, "Error closing WebSocket: " + e.getMessage());
            }
            webSocket = null;
        }
    }

    private void doRelayJoin() throws Exception {
        Log.d(TAG, "Joining relay room " + relayRoomCode);
        JSONObject body = new JSONObject();
        body.put("roomCode", relayRoomCode);
        body.put("playerName", playerName);
        body.put("peerToken", peerToken);
        body.put("protocolVersion", protocolVersion);
        body.put("lastClientId", clientId);
        body.put("androidSdk", android.os.Build.VERSION.SDK_INT);
        JSONObject response = RelayHttpClient.post(relayBaseUrl, "/join", body, 12000);
        clientId = response.getInt("clientId");
        relayClientToken = response.getString("clientToken");
        lastServerMessageTime = System.currentTimeMillis();
        setState(ConnectionState.CONNECTED);
        setState(ConnectionState.AUTHENTICATED);
        startRelayPolling();
        startHeartbeat();
        sendJoin(playerName);
        postConnected();
        reconnectAttempts = 0;
        Log.d(TAG, "Relay joined as client " + clientId);
    }

    private void startRelayPolling() {
        relayPolling = true;
        if (relayPollThread != null) {
            relayPollThread.interrupt();
        }
        relayPollThread = new Thread(() -> {
            while (relayPolling && !manualDisconnect) {
                try {
                    JSONObject response = RelayHttpClient.post(relayBaseUrl, "/poll", relayBaseBody(), 35000);
                    org.json.JSONArray messages = response.optJSONArray("messages");
                    if (messages != null) {
                        for (int i = 0; i < messages.length(); i++) {
                            JSONObject item = messages.optJSONObject(i);
                            if (item == null) continue;
                            JSONObject payload = item.optJSONObject("payload");
                            if (payload != null) {
                                handleMessage(payload.toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    if (relayPolling && !manualDisconnect) {
                        Log.w(TAG, "Relay poll failed: " + e.getMessage());
                        suppressNextDisconnectNotice = true;
                        handleDisconnection("等待云联机重连: " + e.getMessage());
                        return;
                    }
                }
            }
        }, "DdzRelayClientPoll");
        relayPollThread.setDaemon(true);
        relayPollThread.start();
    }

    private JSONObject relayBaseBody() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("roomCode", relayRoomCode);
        body.put("role", "client");
        body.put("clientId", clientId);
        body.put("token", relayClientToken);
        return body;
    }

    private void stopRelay() {
        relayPolling = false;
        if (relayPollThread != null) {
            relayPollThread.interrupt();
            relayPollThread = null;
        }
        final boolean shouldNotify = relayMode && clientId > 0
                && relayClientToken != null && !relayClientToken.isEmpty();
        final String baseUrl = relayBaseUrl;
        final String roomCode = relayRoomCode;
        final int id = clientId;
        final String token = relayClientToken;
        relayMode = false;
        relayClientToken = "";
        if (shouldNotify) {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("roomCode", roomCode);
                    body.put("role", "client");
                    body.put("clientId", id);
                    body.put("token", token);
                    RelayHttpClient.post(baseUrl, "/disconnect", body, 5000);
                } catch (Exception e) {
                    Log.w(TAG, "Relay disconnect failed: " + e.getMessage());
                }
            }, "DdzRelayClientDisconnect").start();
        }
    }

    /**
     * 执行 WebSocket 连接
     */
    private void doWebSocketConnect() {
        Log.d(TAG, "Connecting WebSocket to " + wsUrl);

        if (okHttpClient == null) {
            if (context != null) {
                okHttpClient = OkHttpClientProvider.getInstance(context).getWebSocketClient();
            } else {
                okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS)
                        .build();
            }
        }

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logWs("OPEN", wsUrl, "client websocket opened");
                lastServerMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                setState(ConnectionState.CONNECTED);
                setState(ConnectionState.AUTHENTICATED);
                startWebSocketHeartbeat();
                sendJoin(playerName);
                postConnected();
                reconnectAttempts = 0;
                flushPendingMessages();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                lastServerMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                logEvent("MESSAGE", relayRoomCode, clientId, "incoming");
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logWs("CLOSE", wsUrl, "code=" + code + " reason=" + reason);
                webSocket.close(1000, "Client closing");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logWs("CLOSED", wsUrl, "code=" + code);
                if (!manualDisconnect && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED)) {
                    handleDisconnection("WebSocket closed");
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logError("FAILURE", "client websocket failure: " + t.getMessage());
                if (!manualDisconnect && state != ConnectionState.DISCONNECTED && state != ConnectionState.RECONNECTING) {
                    postError("WebSocket error: " + t.getMessage());
                    handleDisconnection("WebSocket failure");
                }
            }
        });
    }

    /**
     * 启动 WebSocket 应用层心跳（单心跳，跨境网络优化）
     */
    private void startWebSocketHeartbeat() {
        logWs("HEARTBEAT_START", wsUrl, "interval=" + WS_HEARTBEAT_INTERVAL);
        stopHeartbeat();

        heartbeatScheduler = Executors.newScheduledThreadPool(1);

        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!webSocketMode || webSocket == null) {
                return;
            }
            if (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED) {
                return;
            }

            long now = System.currentTimeMillis();
            long elapsed = now - lastServerMessageTime;

            // 如果近期收到过消息，重置计数器
            if (elapsed < WS_HEARTBEAT_INTERVAL) {
                consecutiveMissedPongs = 0;
                return;
            }

            consecutiveMissedPongs++;

            // 温和判定：连续多次无响应才断开，避免网络抖动误杀
            if (consecutiveMissedPongs >= WS_MAX_MISSED_PONGS && elapsed > WS_HEARTBEAT_TIMEOUT) {
                Log.w(TAG, "WebSocket heartbeat timeout after " + consecutiveMissedPongs + " missed checks");
                consecutiveMissedPongs = 0;
                if (webSocket != null) {
                    webSocket.close(1001, "Heartbeat timeout");
                }
                return;
            }

            // 发送应用层 PING
            sendPing();

        }, WS_HEARTBEAT_INTERVAL, WS_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        Log.d(TAG, "WebSocket heartbeat started");
    }

    /**
     * 连接建立后，flush 缓冲队列中的消息
     */
    private void flushPendingMessages() {
        if (webSocket == null || pendingMessages.isEmpty()) {
            return;
        }
        logEvent("FLUSH", relayRoomCode, clientId, "pending=" + pendingMessages.size());
        Log.d(TAG, "Flushing " + pendingMessages.size() + " pending messages");
        JSONObject msg;
        while ((msg = pendingMessages.poll()) != null) {
            if (webSocket != null) {
                webSocket.send(msg.toString());
            }
        }
    }

    /**
     * 执行 TCP 连接
     */
    private void doConnect() throws IOException {
        Log.d(TAG, "Connecting to " + serverHost + ":" + serverPort);

        lastServerMessageTime = System.currentTimeMillis();
        socket = new Socket();
        java.net.InetAddress address = java.net.InetAddress.getByName(serverHost);
        socket.connect(new java.net.InetSocketAddress(address, serverPort), CONNECT_TIMEOUT);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout((int) HEARTBEAT_TIMEOUT + 5000);

        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        setState(ConnectionState.CONNECTED);

        startHeartbeat();

        startReadThread();

        sendJoin(playerName);

        postConnected();

        reconnectAttempts = 0;

        Log.d(TAG, "Connected successfully");
    }

    /**
     * 启动读取线程
     */
    private void startReadThread() {
        readThread = new Thread(() -> {
            String line;
            try {
                while (socket != null && !socket.isClosed()) {
                    line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    handleMessage(line);
                }
            } catch (IOException e) {
                if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED) {
                    Log.e(TAG, "Read error: " + e.getMessage());
                    suppressNextDisconnectNotice = true;
                    handleDisconnection("等待重连: " + e.getMessage());
                }
            } finally {
                if (!manualDisconnect
                        && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED)) {
                    handleDisconnection(suppressNextDisconnectNotice ? "等待重连" : "连接已断开");
                }
            }
        });
        readThread.setName("SocketReader");
        readThread.start();
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");
            lastServerMessageTime = System.currentTimeMillis();

            Log.d(TAG, LOG_PREFIX + " [CLIENT_HANDLE_MSG] type=" + type + " len=" + message.length());

            switch (type) {
                case "WELCOME":
                    clientId = json.optInt("clientId", -1);
                    Log.d(TAG, LOG_PREFIX + " [CLIENT_HANDLE_MSG] WELCOME: clientId=" + clientId);
                    setState(ConnectionState.AUTHENTICATED);
                    logEvent("JOIN_ROOM", relayRoomCode, clientId, "WELCOME");
                    postMessageReceived(json);
                    break;

                case "PONG":
                    logEvent("HEARTBEAT", relayRoomCode, clientId, "PONG");
                    break;

                default:
                    Log.d(TAG, LOG_PREFIX + " [CLIENT_HANDLE_MSG] forwarding to listener: type=" + type);
                    postMessageReceived(json);
                    break;
            }

        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON: " + message);
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        logEvent("LEAVE_ROOM", relayRoomCode, clientId, "DISCONNECT");
        Log.d(TAG, "Disconnecting...");
        manualDisconnect = true;

        // 停止心跳
        stopHeartbeat();

        // 停止重连
        stopReconnect();

        // 更新状态
        setState(ConnectionState.DISCONNECTED);
        stopRelay();
        closeWebSocketQuietly();

        // 关闭连接
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket: " + e.getMessage());
        }

        reader = null;
        writer = null;
        socket = null;

        // 断开回调
        postDisconnected("用户主动断开");
    }

    // ============ 心跳机制 ============

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }

        heartbeatScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);

        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED) {
                long now = System.currentTimeMillis();
                if (lastServerMessageTime > 0 && now - lastServerMessageTime > HEARTBEAT_TIMEOUT) {
                    Log.w(TAG, "Heartbeat delayed, keep socket alive");
                }
                sendPing();
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        Log.d(TAG, "Heartbeat started");
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        logWs("HEARTBEAT_STOP", wsUrl, null);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            heartbeatScheduler = null;
        }
        Log.d(TAG, "Heartbeat stopped");
    }

    /**
     * 发送心跳
     */
    private void sendPing() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "PING");
            send(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending ping: " + e.getMessage());
        }
    }

    // ============ 断线重连 ============

    /**
     * 处理断开连接（防重入保护）
     */
    private void handleDisconnection(String reason) {
        if (manualDisconnect || state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) {
            return;
        }

        // 原子性防重入（避免 onFailure / onClosed 重复触发）
        synchronized (this) {
            if (isHandlingDisconnection || state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) {
                return;
            }
            isHandlingDisconnection = true;
        }

        try {
            // 停止心跳
            stopHeartbeat();
            if (relayMode) {
                relayPolling = false;
            }
            if (webSocketMode) {
                closeWebSocketQuietly();
            }

            // 更新状态
            setState(ConnectionState.DISCONNECTED);
            closeTransportQuietly();

            // 通知 UI 层
            if (!suppressNextDisconnectNotice) {
                postDisconnected(reason);
            }
            suppressNextDisconnectNotice = false;

            // 尝试重连
            if (reconnectAttempts < maxReconnectAttempts) {
                scheduleReconnect();
            }
        } finally {
            isHandlingDisconnection = false;
        }
    }

    /**
     * 处理连接失败
     */
    private void handleConnectionFailure(String reason) {
        if (manualDisconnect) {
            return;
        }
        setState(ConnectionState.DISCONNECTED);
        postError("连接失败: " + reason);

        // 尝试重连
        if (reconnectAttempts < maxReconnectAttempts) {
            scheduleReconnect();
        }
    }

    /**
     * 安排重连（指数退避，跨境网络优化）
     */
    private void scheduleReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
        }

        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            try {
                if (!reconnectScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Reconnect scheduler did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reconnectScheduler = null;
        }

        reconnectScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
        reconnectAttempts++;

        setState(ConnectionState.RECONNECTING);

        // 指数退避：base * 2^(attempts-1)，不超过最大值
        long exponentialDelay = (long) (reconnectInterval * Math.pow(2, reconnectAttempts - 1));
        long delay = Math.min(exponentialDelay, maxReconnectInterval);

        logWs("RECONNECT", wsUrl, "attempt=" + reconnectAttempts + " delay=" + delay);
        Log.d(TAG, "Scheduling reconnect #" + reconnectAttempts + " in " + delay + "ms");

        reconnectTask = reconnectScheduler.schedule(() -> {
            if (manualDisconnect) {
                return;
            }
            Log.d(TAG, "Attempting reconnect #" + reconnectAttempts);
            try {
                if (webSocketMode) {
                    doWebSocketConnect();
                } else if (relayMode) {
                    doRelayJoin();
                } else {
                    doConnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Reconnect failed: " + e.getMessage());
                handleConnectionFailure(e.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止重连（强制清理线程池）
     */
    private void stopReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
            reconnectTask = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            try {
                if (!reconnectScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Reconnect scheduler did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reconnectScheduler = null;
        }
    }

    public void reconnectNow() {
        logWs("RECONNECT_NOW", wsUrl, "immediate reconnect");
        if (!webSocketMode && !relayMode && (serverHost == null || serverHost.trim().isEmpty() || serverPort <= 0)) {
            postError("没有可重连的主机地址");
            return;
        }
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED
                || state == ConnectionState.AUTHENTICATED) {
            return;
        }
        stopReconnect();
        manualDisconnect = false;
        lastServerMessageTime = System.currentTimeMillis();
        consecutiveMissedPongs = 0;
        setState(ConnectionState.CONNECTING);
        new Thread(() -> {
            try {
                if (webSocketMode) {
                    doWebSocketConnect();
                } else if (relayMode) {
                    doRelayJoin();
                } else {
                    doConnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Reconnect now failed: " + e.getMessage());
                handleConnectionFailure(e.getMessage());
            }
        }).start();
    }

    // ============ 消息发送 ============

    /**
     * 发送 JSON 消息（自动分发到 TCP / HTTP / WebSocket）
     */
    public boolean send(JSONObject json) {
        if (json == null) {
            return false;
        }
        if (relayMode) {
            return sendRelay(json);
        }
        if (webSocketMode) {
            return sendWebSocket(json);
        }
        final String message = json.toString();
        if (!isTransportWritable()) {
            Log.w(TAG, "Cannot send, not connected");
            return false;
        }
        try {
            sendExecutor.execute(() -> sendMessageNow(message));
            return true;
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Send warning: writer executor stopped", e);
            return false;
        }
    }

    /**
     * 通过 WebSocket 发送消息，未连接时加入缓冲队列
     */
    private boolean sendWebSocket(JSONObject json) {
        if (webSocket == null || (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED)) {
            // 连接未建立，加入缓冲队列（有上限防止内存泄漏）
            if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
                pendingMessages.poll();
                Log.w(TAG, "Pending message queue full, dropped oldest");
            }
            pendingMessages.offer(json);
            logEvent("BUFFER", relayRoomCode, clientId, json.optString("type", "-"));
            Log.d(TAG, "Message buffered, queue size: " + pendingMessages.size());
            return true;
        }
        String text = json.toString();
        boolean sent = webSocket.send(text);
        if (sent) {
            logEvent("SEND", relayRoomCode, clientId, json.optString("type", "-"));
        }
        if (!sent) {
            // 发送失败（例如连接正在关闭），加入缓冲队列，重连后发送
            if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
                pendingMessages.poll();
            }
            pendingMessages.offer(json);
            Log.w(TAG, "WebSocket send failed, message buffered");
        }
        return true;
    }

    private boolean sendRelay(JSONObject json) {
        if (clientId <= 0 || relayClientToken == null || relayClientToken.isEmpty()) {
            Log.w(TAG, "Cannot send relay message, not joined");
            return false;
        }
        try {
            sendExecutor.execute(() -> {
                try {
                    JSONObject body = relayBaseBody();
                    body.put("to", "host");
                    body.put("payload", json);
                    RelayHttpClient.post(relayBaseUrl, "/send", body, 10000);
                } catch (Exception e) {
                    Log.e(TAG, "Relay send warning: " + e.getMessage(), e);
                    postError("云联机发送失败: " + e.getMessage());
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Relay send warning: writer executor stopped", e);
            return false;
        }
    }

    private boolean isTransportWritable() {
        if (relayMode) {
            return clientId > 0 && relayClientToken != null && !relayClientToken.isEmpty();
        }
        if (webSocketMode) {
            return webSocket != null && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED);
        }
        Socket currentSocket = socket;
        return writer != null && currentSocket != null && !currentSocket.isClosed();
    }

    private void sendMessageNow(String message) {
        try {
            if (isTransportWritable()) {
                synchronized (this) {
                    writer.println(message);
                    if (writer.checkError()) {
                        Log.w(TAG, "Send warning: writer reported an error");
                    }
                }
            } else {
                Log.w(TAG, "Cannot send, not connected");
            }
        } catch (Exception e) {
            Log.e(TAG, "Send warning: " + e.getMessage(), e);
        }
    }

    /**
     * 发送加入游戏请求
     */
    public void sendJoin(String playerName) {
        logEvent("JOIN_ROOM", relayRoomCode, clientId, "JOIN");
        this.playerName = playerName;
        JSONObject json = new JSONObject();
        try {
            json.put("type", "JOIN");
            json.put("playerName", playerName);
            json.put("peerToken", peerToken);
            json.put("protocolVersion", protocolVersion);
            json.put("reconnect", clientId > 0);
            json.put("lastClientId", clientId);
            json.put("androidSdk", android.os.Build.VERSION.SDK_INT);
            send(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending join: " + e.getMessage());
        }
    }

    /**
     * 发送出牌请求
     */
    public void sendPlayRequest(String cardsJson, String cardType) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "REQUEST_PLAY");
            json.put("cards", cardsJson);
            json.put("cardType", cardType);
            send(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending play request: " + e.getMessage());
        }
    }

    /**
     * 发送不出请求
     */
    public void sendPass() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "PASS");
            send(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending pass: " + e.getMessage());
        }
    }

    // ============ 状态管理 ============

    /**
     * 设置连接状态
     */
    private void setState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            postStateChanged(newState);
        }
    }

    // ============ 回调方法（主线程执行）============

    private void postConnected() {
        mainHandler.post(() -> {
            if (connectedListener != null) {
                connectedListener.onConnected(clientId);
            }
        });
    }

    private void postDisconnected(final String reason) {
        mainHandler.post(() -> {
            if (disconnectedListener != null) {
                disconnectedListener.onDisconnected(reason);
            }
        });
    }

    private void postMessageReceived(final JSONObject message) {
        mainHandler.post(() -> {
            if (messageListener != null) {
                messageListener.onMessageReceived(message);
            }
        });
    }

    private void postError(final String message) {
        mainHandler.post(() -> {
            if (errorListener != null) {
                errorListener.onError(message);
            }
        });
    }

    private void postStateChanged(final ConnectionState state) {
        mainHandler.post(() -> {
            if (stateChangedListener != null) {
                stateChangedListener.onStateChanged(state);
            }
        });
    }

    // ============ 回调接口定义 ============

    public interface OnConnectedListener {
        void onConnected(int clientId);
    }

    public interface OnDisconnectedListener {
        void onDisconnected(String reason);
    }

    public interface OnMessageReceivedListener {
        void onMessageReceived(JSONObject message);
    }

    public interface OnErrorListener {
        void onError(String message);
    }

    public interface OnStateChangedListener {
        void onStateChanged(ConnectionState state);
    }

    // ============ 回调设置 ============

    public void setOnConnectedListener(OnConnectedListener listener) {
        this.connectedListener = listener;
    }

    public void setOnDisconnectedListener(OnDisconnectedListener listener) {
        this.disconnectedListener = listener;
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        this.stateChangedListener = listener;
    }

    // ============ 状态查询 ============

    public ConnectionState getState() {
        return state;
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED;
    }

    public boolean isAuthenticated() {
        return state == ConnectionState.AUTHENTICATED;
    }

    public int getClientId() {
        return clientId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String name) {
        this.playerName = name;
    }

    public void setPeerToken(String token) {
        this.peerToken = token != null ? token : "";
    }

    public String getPeerToken() {
        return peerToken;
    }

    public void setProtocolVersion(int version) {
        this.protocolVersion = version;
    }

    public void setReconnectPolicy(int maxAttempts, long baseIntervalMs, long maxIntervalMs) {
        this.maxReconnectAttempts = Math.max(0, maxAttempts);
        this.reconnectInterval = Math.max(500L, baseIntervalMs);
        this.maxReconnectInterval = Math.max(this.reconnectInterval, maxIntervalMs);
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    // ============ 清理资源 ============

    /**
     * 释放所有资源
     */
    public void release() {
        disconnect();
        sendExecutor.shutdownNow();
        instance = null;
    }

    // 单例实例引用
    private static GameSocketClient instance;

    public static synchronized GameSocketClient getInstance() {
        if (instance == null) {
            instance = new GameSocketClient();
        }
        return instance;
    }

    public static synchronized GameSocketClient getInstance(Context context) {
        if (instance == null) {
            instance = new GameSocketClient(context);
        }
        return instance;
    }
}

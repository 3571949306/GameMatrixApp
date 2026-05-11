package com.gamecenter.app.games.doudizhu.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
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
 * 斗地主游戏服务器（权威主机端）(Game Socket Server)
 * 支持三种模式：
 * 1. TCP ServerSocket（局域网）
 * 2. HTTP Relay 轮询（云联机 fallback）
 * 3. WebSocket 客户端（云联机主方案，连接 Relay 服务器）
 */
public class GameSocketServer {

    // 日志标签
    private static final String TAG = "GameSocketServer";

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

    // 最大客户端数量（斗地主需要 2 个客户端）
    private static final int MAX_CLIENTS = 4;

    // 心跳超时时间（毫秒）
    private static final long HEARTBEAT_TIMEOUT = 30000L;

    // 心跳检测间隔（毫秒）
    private static final long HEARTBEAT_CHECK_INTERVAL = 2000L;

    // ============ WebSocket 模式常量（跨境网络优化） ============

    // WebSocket 心跳间隔 10 秒
    private static final long WS_HEARTBEAT_INTERVAL = 10000L;

    // WebSocket 心跳超时 45 秒（容忍美国服务器高延迟）
    private static final long WS_HEARTBEAT_TIMEOUT = 45000L;

    // 连续 2 次无响应才判定断线
    private static final int WS_MAX_MISSED_PONGS = 2;

    // ============ 服务器状态 ============

    private ServerSocket serverSocket;
    private ExecutorService clientThreadPool;
    private ExecutorService sendExecutor;
    private ScheduledExecutorService heartbeatScheduler;

    // 客户端连接映射 <客户端ID, ClientConnection>
    private final ConcurrentHashMap<Integer, ClientConnection> clients = new ConcurrentHashMap<>();

    // 当前客户端 ID 计数器
    private int nextClientId = 1;

    /**
     * 生成临时 clientId（用于 WebSocket 客户端尚未分配 ID 时）
     */
    private synchronized int generateTempClientId() {
        int id = nextClientId++;
        while (relayKnownClients.containsKey(id)) {
            id = nextClientId++;
        }
        return id;
    }

    // 服务器端口
    private int serverPort;

    // 服务器是否运行中
    private volatile boolean isRunning = false;

    // Handler 用于主线程回调
    private final Handler mainHandler;

    // 回调接口
    private OnClientConnectedListener connectedListener;
    private OnClientDisconnectedListener disconnectedListener;
    private OnMessageReceivedListener messageListener;
    private OnErrorListener errorListener;

    // 心跳检测任务
    private ScheduledFuture<?> heartbeatCheckTask;
    private volatile boolean relayMode = false;
    private volatile boolean relayPolling = false;
    private String relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL;
    private String relayRoomCode = "";
    private String relayHostToken = "";
    private Thread relayPollThread;
    private final ConcurrentHashMap<Integer, Boolean> relayKnownClients = new ConcurrentHashMap<>();

    // ============ WebSocket 模式字段 ============

    // 是否处于 WebSocket 模式（房主作为 WebSocket 客户端连接 Relay）
    private volatile boolean webSocketMode = false;

    // OkHttp WebSocket 连接实例
    private WebSocket webSocket;

    // OkHttp 客户端实例
    private OkHttpClient okHttpClient;

    // WebSocket 连接 URL
    private String wsUrl;

    // 连续未收到响应的次数
    private int consecutiveMissedPongs = 0;

    // 最后收到消息时间
    private volatile long lastWsMessageTime = 0L;

    // WebSocket 心跳调度器
    private ScheduledExecutorService wsHeartbeatScheduler;
    private ScheduledFuture<?> wsHeartbeatTask;

    // ============ 客户端连接包装类 ============

    /**
     * 客户端连接封装类
     */
    private class ClientConnection {
        final int clientId;
        final Socket socket;
        final PrintWriter writer;
        final BufferedReader reader;
        final Thread readThread;

        // 最后心跳时间
        volatile long lastHeartbeat;

        // 是否已认证（完成 JOIN）
        volatile boolean authenticated = false;

        // 玩家名称
        String playerName = "";

        String peerToken = "";

        // 是否已经关闭，防止读线程和心跳线程重复通知断线
        volatile boolean closed = false;

        ClientConnection(int clientId, Socket socket) throws IOException {
            this.clientId = clientId;
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.lastHeartbeat = System.currentTimeMillis();
            this.socket.setKeepAlive(true);
            this.socket.setTcpNoDelay(true);

            // 启动读取线程
            this.readThread = new Thread(() -> readMessages());
            this.readThread.setName("ClientReader-" + clientId);
            this.readThread.start();
        }

        /**
         * 发送消息
         */
        boolean send(String message) {
            if (!closed && !socket.isClosed()) {
                ExecutorService executor = sendExecutor;
                if (executor == null || executor.isShutdown()) {
                    Log.w(TAG, "Cannot send to client " + clientId + ", writer executor stopped");
                    return false;
                }
                try {
                    executor.execute(() -> sendNow(message));
                } catch (RejectedExecutionException e) {
                    Log.w(TAG, "Cannot send to client " + clientId + ", writer executor rejected task");
                    return false;
                }
                return true;
            }
            Log.w(TAG, "Cannot send to client " + clientId + ", connection closed");
            return false;
        }

        private void sendNow(String message) {
            try {
                if (!closed && !socket.isClosed()) {
                    synchronized (this) {
                        writer.println(message);
                        if (writer.checkError()) {
                            Log.w(TAG, "Send warning to client " + clientId + ": writer reported an error");
                        }
                    }
                } else {
                    Log.w(TAG, "Cannot send to client " + clientId + ", connection closed");
                }
            } catch (Exception e) {
                Log.w(TAG, "Send warning to client " + clientId + ": " + e.getMessage());
            }
        }

        /**
         * 发送 JSON 对象
         */
        boolean sendJSON(JSONObject json) {
            return send(json.toString());
        }

        /**
         * 读取消息循环
         */
        private void readMessages() {
            String line;
            try {
                while ((line = reader.readLine()) != null && isRunning) {
                    lastHeartbeat = System.currentTimeMillis();
                    handleMessage(this, line);
                }
            } catch (IOException e) {
                Log.d(TAG, "Client " + clientId + " read error: " + e.getMessage());
            } finally {
                closeConnection(this);
            }
        }

        /**
         * 关闭连接
         */
        void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                reader.close();
                writer.close();
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client " + clientId + " connection");
            }
        }
    }

    // ============ 构造函数 ============

    private final Context context;

    public GameSocketServer() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = null;
    }

    public GameSocketServer(Context context) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = context != null ? context.getApplicationContext() : null;
    }

    // ============ 服务器控制 ============

    /**
     * 启动服务器（TCP Socket 局域网模式）
     * @param port 监听端口
     * @return 是否启动成功
     */
    public boolean start(int port) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }

        try {
            serverSocket = new ServerSocket(port);
            serverPort = port;
            isRunning = true;

            // 创建线程池处理客户端
            clientThreadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
            sendExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ServerSocketWriter");
                thread.setDaemon(true);
                return thread;
            });

            // 创建心跳检测调度器
            heartbeatScheduler = Executors.newScheduledThreadPool(1);
            startHeartbeatCheck();

            // 启动接受连接线程
            new Thread(this::acceptConnections).start();

            Log.d(TAG, "Server started on port " + port);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动 HTTP Relay 轮询模式（云联机 fallback）
     */
    public boolean startRelay(String baseUrl) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }
        try {
            relayMode = true;
            relayBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty()
                    ? baseUrl.trim()
                    : RelayHttpClient.DEFAULT_BASE_URL;
            JSONObject body = new JSONObject();
            body.put("app", "GameCenterApp");
            body.put("game", "doudizhu");
            JSONObject response = RelayHttpClient.post(relayBaseUrl, "/create", body, 10000);
            relayRoomCode = response.getString("roomCode");
            relayHostToken = response.getString("hostToken");
            isRunning = true;
            relayPolling = true;
            relayKnownClients.clear();
            sendExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "RelayHostWriter");
                thread.setDaemon(true);
                return thread;
            });
            startRelayPolling();
            Log.d(TAG, "Relay room created: " + relayRoomCode);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start relay room: " + e.getMessage(), e);
            relayMode = false;
            isRunning = false;
            postError("云房间创建失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动 WebSocket 模式（云联机主方案，连接 Relay 服务器）
     * @param wsUrl WebSocket URL，例如 wss://hk-ws.<YOUR_DOMAIN>/ddz-ws?room=ABC123&role=host&token=xxx
     */
    public boolean startWebSocket(String wsUrl) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }

        logWs("CONNECTING", wsUrl, "host starting websocket");
        this.wsUrl = wsUrl;
        webSocketMode = true;
        isRunning = true;
        consecutiveMissedPongs = 0;
        lastWsMessageTime = System.currentTimeMillis();
        relayKnownClients.clear();

        sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WebSocketHostWriter");
            thread.setDaemon(true);
            return thread;
        });

        new Thread(() -> {
            try {
                doWebSocketConnect();
            } catch (Exception e) {
                Log.e(TAG, "WebSocket connection error: " + e.getMessage());
                postError("WebSocket 连接失败: " + e.getMessage());
                stop();
            }
        }, "DdzWebSocketHostConnect").start();

        return true;
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
                Log.d(TAG, "WebSocket opened");
                logWs("OPEN", wsUrl, "host websocket opened");
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                startWebSocketHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                Log.d(TAG, LOG_PREFIX + " [WS_RAW_MSG] TEXT len=" + text.length() + " first100=" + (text.length() > 100 ? text.substring(0, 100) : text));
                logEvent("MESSAGE", wsUrl != null ? "ws" : "-", -1, "incoming-text");
                handleWebSocketMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                String text = bytes.utf8();
                Log.d(TAG, LOG_PREFIX + " [WS_RAW_MSG] BINARY len=" + bytes.size() + " first100=" + (text.length() > 100 ? text.substring(0, 100) : text));
                logEvent("MESSAGE", wsUrl != null ? "ws" : "-", -1, "incoming-binary");
                handleWebSocketMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                logWs("CLOSE", wsUrl, "code=" + code + " reason=" + reason);
                webSocket.close(1000, "Host closing");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " " + reason);
                if (isRunning) {
                    postError("WebSocket 连接已关闭: " + reason);
                    stop();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                logError("FAILURE", "host websocket failure: " + t.getMessage());
                if (isRunning) {
                    postError("WebSocket 连接失败: " + t.getMessage());
                    stop();
                }
            }
        });
    }

    /**
     * 处理 WebSocket 收到的消息
     */
    private void handleWebSocketMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String type = json.optString("type", "");
            Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] type=" + type + " rawLen=" + text.length());

            if ("PONG".equals(type)) {
                return;
            }

            if ("JOIN".equals(type)) {
                int clientId = json.optInt("clientId", -1);
                Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] JOIN: json.clientId=" + clientId);
                logEvent("JOIN_ROOM", wsUrl != null ? "ws" : "-", clientId, "JOIN");
                if (clientId <= 0) {
                    clientId = generateTempClientId();
                    Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] JOIN: generated temp clientId=" + clientId);
                }
                if (!relayKnownClients.containsKey(clientId)) {
                    relayKnownClients.put(clientId, true);
                    Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] JOIN: calling postClientConnected with clientId=" + clientId);
                    postClientConnected(clientId, "websocket");
                }
            }

            if ("DISCONNECT".equals(type) || "CLIENT_DISCONNECTED".equals(type)) {
                int clientId = json.optInt("clientId", -1);
                logEvent("LEAVE_ROOM", wsUrl != null ? "ws" : "-", clientId, "DISCONNECT");
                if (clientId > 0) {
                    relayKnownClients.remove(clientId);
                    postClientDisconnected(clientId, json.optString("reason", "连接关闭"));
                }
                return;
            }

            int clientId = json.optInt("clientId", -1);
            if (clientId <= 0) {
                clientId = json.optInt("_clientId", -1);
            }
            if (clientId <= 0) {
                clientId = 1;
            }
            Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] resolved clientId=" + clientId + " for type=" + type);

            if (!relayKnownClients.containsKey(clientId)) {
                relayKnownClients.put(clientId, true);
                Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] new client, calling postClientConnected with clientId=" + clientId);
                postClientConnected(clientId, "websocket");
            }

            json.put("_remoteIp", "websocket");
            json.put("_clientId", clientId);
            Log.d(TAG, LOG_PREFIX + " [WS_MSG_RECV] calling postMessageReceived clientId=" + clientId + " type=" + type);
            postMessageReceived(clientId, json);

        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON from WebSocket: " + text);
        }
    }

    /**
     * 启动 WebSocket 应用层心跳
     */
    private void startWebSocketHeartbeat() {
        logWs("HEARTBEAT_START", wsUrl, "interval=" + WS_HEARTBEAT_INTERVAL);
        if (wsHeartbeatScheduler != null) {
            wsHeartbeatScheduler.shutdownNow();
        }

        wsHeartbeatScheduler = Executors.newScheduledThreadPool(1);

        wsHeartbeatTask = wsHeartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!webSocketMode || webSocket == null || !isRunning) {
                return;
            }

            long now = System.currentTimeMillis();
            long elapsed = now - lastWsMessageTime;

            // 如果近期收到过消息，重置计数器
            if (elapsed < WS_HEARTBEAT_INTERVAL) {
                consecutiveMissedPongs = 0;
                return;
            }

            consecutiveMissedPongs++;

            // 温和判定：连续多次无响应才断开
            if (consecutiveMissedPongs >= WS_MAX_MISSED_PONGS && elapsed > WS_HEARTBEAT_TIMEOUT) {
                Log.w(TAG, "WebSocket heartbeat timeout after " + consecutiveMissedPongs + " missed checks");
                consecutiveMissedPongs = 0;
                if (webSocket != null) {
                    webSocket.close(1001, "Heartbeat timeout");
                }
                return;
            }

            // 发送应用层 PING
            try {
                JSONObject ping = new JSONObject();
                ping.put("type", "PING");
                webSocket.send(ping.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Error sending ping: " + e.getMessage());
            }

        }, WS_HEARTBEAT_INTERVAL, WS_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        Log.d(TAG, "WebSocket heartbeat started");
    }

    /**
     * 停止 WebSocket 心跳
     */
    private void stopWebSocketHeartbeat() {
        logWs("HEARTBEAT_STOP", wsUrl, null);
        if (wsHeartbeatTask != null) {
            wsHeartbeatTask.cancel(true);
            wsHeartbeatTask = null;
        }
        if (wsHeartbeatScheduler != null) {
            wsHeartbeatScheduler.shutdownNow();
            wsHeartbeatScheduler = null;
        }
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        relayPolling = false;

        // 停止 WebSocket
        if (webSocketMode) {
            stopWebSocketHeartbeat();
            webSocketMode = false;
            if (webSocket != null) {
                try {
                    webSocket.close(1000, "Host stopped");
                } catch (Exception e) {
                    Log.w(TAG, "Error closing WebSocket: " + e.getMessage());
                }
                webSocket = null;
            }
        }

        if (relayMode) {
            final String baseUrl = relayBaseUrl;
            final String roomCode = relayRoomCode;
            final String hostToken = relayHostToken;
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("roomCode", roomCode);
                    body.put("role", "host");
                    body.put("token", hostToken);
                    RelayHttpClient.post(baseUrl, "/close", body, 5000);
                } catch (Exception e) {
                    Log.w(TAG, "Relay close failed: " + e.getMessage());
                }
            }, "DdzRelayHostClose").start();
            if (relayPollThread != null) {
                relayPollThread.interrupt();
                relayPollThread = null;
            }
            relayKnownClients.clear();
            relayMode = false;
            relayRoomCode = "";
            relayHostToken = "";
        }

        // 停止心跳检测
        if (heartbeatCheckTask != null) {
            heartbeatCheckTask.cancel(true);
        }
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }

        // 关闭所有客户端连接
        for (ClientConnection client : clients.values()) {
            client.close();
        }
        clients.clear();

        // 关闭线程池
        if (clientThreadPool != null) {
            clientThreadPool.shutdown();
        }
        if (sendExecutor != null) {
            sendExecutor.shutdownNow();
            sendExecutor = null;
        }

        // 关闭服务器套接字
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket");
        }

        Log.d(TAG, "Server stopped");
    }

    private void startRelayPolling() {
        relayPollThread = new Thread(() -> {
            while (relayPolling && isRunning) {
                try {
                    JSONObject response = RelayHttpClient.post(relayBaseUrl, "/poll", relayBaseBody(), 35000);
                    JSONArray messages = response.optJSONArray("messages");
                    if (messages != null) {
                        for (int i = 0; i < messages.length(); i++) {
                            JSONObject item = messages.optJSONObject(i);
                            if (item == null) continue;
                            int clientId = item.optInt("clientId", -1);
                            JSONObject payload = item.optJSONObject("payload");
                            if (clientId <= 0 || payload == null) continue;
                            handleRelayMessage(clientId, payload);
                        }
                    }
                } catch (Exception e) {
                    if (relayPolling && isRunning) {
                        Log.w(TAG, "Relay poll failed: " + e.getMessage());
                        postError("云联机连接不稳定，正在重试");
                        try {
                            Thread.sleep(1500L);
                        } catch (InterruptedException ignored) {
                            return;
                        }
                    }
                }
            }
        }, "DdzRelayHostPoll");
        relayPollThread.setDaemon(true);
        relayPollThread.start();
    }

    private JSONObject relayBaseBody() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("roomCode", relayRoomCode);
        body.put("role", "host");
        body.put("token", relayHostToken);
        return body;
    }

    private void handleRelayMessage(int clientId, JSONObject json) {
        if ("DISCONNECT".equals(json.optString("type", ""))) {
            relayKnownClients.remove(clientId);
            postClientDisconnected(clientId, json.optString("reason", "连接关闭"));
            return;
        }
        if (!relayKnownClients.containsKey(clientId)) {
            relayKnownClients.put(clientId, true);
            postClientConnected(clientId, "云中转");
        }
        try {
            json.put("_remoteIp", "relay");
            json.put("_clientId", clientId);
        } catch (JSONException ignored) {
        }
        handleIncomingJson(clientId, json);
    }

    /**
     * 接受客户端连接
     */
    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewClient(clientSocket);
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Accept error: " + e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * 处理新客户端连接
     */
    private void handleNewClient(Socket socket) {
        if (clients.size() >= MAX_CLIENTS) {
            Log.w(TAG, "Max clients reached, rejecting connection");
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
            return;
        }

        try {
            int clientId = nextClientId++;
            ClientConnection client = new ClientConnection(clientId, socket);
            clients.put(clientId, client);
            Log.d(TAG, "Client " + clientId + " connected from " + socket.getInetAddress());

            // 发送欢迎消息
            JSONObject welcome = new JSONObject();
            welcome.put("type", "WELCOME");
            welcome.put("clientId", clientId);
            welcome.put("maxClients", MAX_CLIENTS);
            client.sendJSON(welcome);

            // 通知监听器
            postClientConnected(clientId, socket.getInetAddress().getHostAddress());

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to setup client: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    /**
     * 处理客户端消息
     */
    private void handleMessage(ClientConnection client, String message) {
        try {
            JSONObject json = new JSONObject(message);
            handleIncomingJson(client.clientId, json);

        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON from client " + client.clientId + ": " + message);
        }
    }

    private void handleIncomingJson(int clientId, JSONObject json) {
        String type = json.optString("type", "");
        Log.d(TAG, "Received from client " + clientId + ": " + type);

        try {
            switch (type) {
                case "PING":
                    ClientConnection client = clients.get(clientId);
                    if (client != null) {
                        client.lastHeartbeat = System.currentTimeMillis();
                    }
                    JSONObject pong = new JSONObject();
                    pong.put("type", "PONG");
                    sendTo(clientId, pong);
                    break;

                case "JOIN":
                    ClientConnection joinClient = clients.get(clientId);
                    if (joinClient != null) {
                        joinClient.playerName = json.optString("playerName", "Player" + clientId);
                        joinClient.peerToken = json.optString("peerToken", "");
                        joinClient.authenticated = true;
                        if (joinClient.socket != null && joinClient.socket.getInetAddress() != null) {
                            json.put("_remoteIp", joinClient.socket.getInetAddress().getHostAddress());
                        }
                    }
                    json.put("_clientId", clientId);
                    postMessageReceived(clientId, json);
                    break;

                default:
                    postMessageReceived(clientId, json);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleIncomingJson error: " + e.getMessage());
        }
    }

    /**
     * 启动心跳检测
     */
    private void startHeartbeatCheck() {
        heartbeatCheckTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();

            for (ClientConnection client : clients.values()) {
                if (currentTime - client.lastHeartbeat > HEARTBEAT_TIMEOUT) {
                    Log.w(TAG, "Client " + client.clientId + " heartbeat delayed, keep connection alive");
                }
            }
        }, HEARTBEAT_CHECK_INTERVAL, HEARTBEAT_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭客户端连接
     */
    private void closeConnection(ClientConnection client) {
        closeConnection(client, "连接关闭");
    }

    private void closeConnection(ClientConnection client, String reason) {
        if (client == null) return;
        boolean removed = clients.remove(client.clientId, client);
        client.close();
        if (removed && isRunning) {
            postClientDisconnected(client.clientId, reason);
        }
    }

    // ============ 消息广播 ============

    /**
     * 广播消息给所有客户端
     * @param json 要广播的 JSON 消息
     */
    public void broadcast(JSONObject json) {
        if (json == null) return;
        if (webSocketMode) {
            logEvent("BROADCAST", wsUrl != null ? "ws" : "-", -1, json.optString("type", "-"));
            webSocketBroadcast(json);
            return;
        }
        if (relayMode) {
            relaySendAll(json);
            return;
        }
        String message = json.toString();
        try {
            for (ClientConnection client : clients.values()) {
                if (client != null) {
                    client.send(message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "broadcast error: " + e.getMessage());
        }
    }

    /**
     * 通过 WebSocket 广播消息
     */
    private void webSocketBroadcast(JSONObject json) {
        if (webSocket == null) return;
        try {
            json.put("broadcast", true);
            webSocket.send(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "WebSocket broadcast error: " + e.getMessage());
        }
    }

    public void broadcastExcept(int excludeClientId, JSONObject json) {
        if (json == null) return;
        if (webSocketMode) {
            // WebSocket 模式下由 Relay 服务器处理排除逻辑
            webSocketBroadcast(json);
            return;
        }
        if (relayMode) {
            for (Integer clientId : relayKnownClients.keySet()) {
                if (clientId != null && clientId != excludeClientId) {
                    relaySendTo(clientId, json);
                }
            }
            return;
        }
        String message = json.toString();
        try {
            for (ClientConnection client : clients.values()) {
                if (client != null && client.clientId != excludeClientId) {
                    client.send(message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "broadcastExcept error: " + e.getMessage());
        }
    }

    /**
     * 发送消息给指定客户端
     */
    public void sendTo(int clientId, JSONObject json) {
        try {
            if (webSocketMode) {
                logEvent("SEND_TO", wsUrl != null ? "ws" : "-", clientId, json.optString("type", "-"));
                webSocketSendTo(clientId, json);
                return;
            }
            if (relayMode) {
                relaySendTo(clientId, json);
                return;
            }
            ClientConnection client = clients.get(clientId);
            if (client != null) {
                client.sendJSON(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "sendTo error: " + e.getMessage());
        }
    }

    /**
     * 通过 WebSocket 发送消息给指定客户端
     */
    private void webSocketSendTo(int clientId, JSONObject json) {
        if (webSocket == null) {
            Log.e(TAG, LOG_PREFIX + " [WS_SEND_TO] webSocket is NULL! Cannot send to clientId=" + clientId);
            return;
        }
        try {
            json.put("targetClientId", clientId);
            String msgType = json.optString("type", "?");
            Log.d(TAG, LOG_PREFIX + " [WS_SEND_TO] clientId=" + clientId + " type=" + msgType + " msgLen=" + json.toString().length());
            webSocket.send(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "WebSocket sendTo error: " + e.getMessage());
        }
    }

    private void relaySendAll(JSONObject json) {
        relaySend("all", json);
    }

    private void relaySendTo(int clientId, JSONObject json) {
        if (clientId <= 0) return;
        relaySend(String.valueOf(clientId), json);
    }

    private void relaySend(String to, JSONObject json) {
        ExecutorService executor = sendExecutor;
        if (executor == null || executor.isShutdown()) {
            Log.w(TAG, "Cannot send relay message, writer executor stopped");
            return;
        }
        try {
            executor.execute(() -> relaySendNow(to, json));
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "relay writer rejected task", e);
        }
    }

    private void relaySendNow(String to, JSONObject json) {
        try {
            JSONObject body = relayBaseBody();
            body.put("to", to);
            body.put("payload", json);
            RelayHttpClient.post(relayBaseUrl, "/send", body, 10000);
        } catch (Exception e) {
            Log.e(TAG, "relay send error: " + e.getMessage());
            postError("云联机发送失败: " + e.getMessage());
        }
    }

    /**
     * 广播游戏状态
     */
    public void broadcastGameState(int[] handCounts, int currentTurn, int landlordIndex) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "SYNC_STATE");
            json.put("handCounts", new org.json.JSONArray(handCounts));
            json.put("currentTurn", currentTurn);
            json.put("landlordIndex", landlordIndex);
            broadcast(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error broadcasting game state: " + e.getMessage());
        }
    }

    /**
     * 广播出牌动作
     */
    public void broadcastPlayAction(int playerIndex, String cardsJson, String cardType) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "BROADCAST_ACTION");
            json.put("playerIndex", playerIndex);
            json.put("cards", cardsJson);
            json.put("cardType", cardType);
            broadcast(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error broadcasting play action: " + e.getMessage());
        }
    }

    /**
     * 广播游戏结束
     */
    public void broadcastGameOver(int winnerIndex) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "GAME_OVER");
            json.put("winnerIndex", winnerIndex);
            broadcast(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error broadcasting game over: " + e.getMessage());
        }
    }

    // ============ 回调方法（主线程执行）============

    private void postClientConnected(final int clientId, final String ip) {
        mainHandler.post(() -> {
            if (connectedListener != null) {
                connectedListener.onClientConnected(clientId, ip);
            }
        });
    }

    private void postClientDisconnected(final int clientId, final String reason) {
        mainHandler.post(() -> {
            if (disconnectedListener != null) {
                disconnectedListener.onClientDisconnected(clientId, reason);
            }
        });
    }

    private void postMessageReceived(final int clientId, final JSONObject message) {
        mainHandler.post(() -> {
            if (messageListener != null) {
                messageListener.onMessageReceived(clientId, message);
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

    // ============ 回调接口定义 ============

    public interface OnClientConnectedListener {
        void onClientConnected(int clientId, String ip);
    }

    public interface OnClientDisconnectedListener {
        void onClientDisconnected(int clientId, String reason);
    }

    public interface OnMessageReceivedListener {
        void onMessageReceived(int clientId, JSONObject message);
    }

    public interface OnErrorListener {
        void onError(String message);
    }

    // ============ 回调设置 ============

    public void setOnClientConnectedListener(OnClientConnectedListener listener) {
        this.connectedListener = listener;
    }

    public void setOnClientDisconnectedListener(OnClientDisconnectedListener listener) {
        this.disconnectedListener = listener;
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }

    // ============ 状态查询 ============

    public boolean isRunning() {
        return isRunning;
    }

    public int getServerPort() {
        return serverPort;
    }

    public boolean isRelayMode() {
        return relayMode;
    }

    public boolean isWebSocketMode() {
        return webSocketMode;
    }

    public String getRelayRoomCode() {
        return relayRoomCode;
    }

    public int getConnectedClientCount() {
        if (webSocketMode || relayMode) {
            return relayKnownClients.size();
        }
        return clients.size();
    }

    public int getAuthenticatedClientCount() {
        if (webSocketMode || relayMode) {
            return relayKnownClients.size();
        }
        int count = 0;
        for (ClientConnection client : clients.values()) {
            if (client != null && client.authenticated) {
                count++;
            }
        }
        return count;
    }

    public String getClientIp(int clientId) {
        ClientConnection client = clients.get(clientId);
        if (client == null || client.socket == null || client.socket.getInetAddress() == null) {
            return "";
        }
        return client.socket.getInetAddress().getHostAddress();
    }

    public String getClientPeerToken(int clientId) {
        ClientConnection client = clients.get(clientId);
        return client != null ? client.peerToken : "";
    }

    public void disconnectClient(int clientId, String reason) {
        if (webSocketMode) {
            relayKnownClients.remove(clientId);
            postClientDisconnected(clientId, reason != null ? reason : "连接关闭");
            return;
        }
        if (relayMode) {
            try {
                JSONObject error = new JSONObject();
                error.put("type", "ERROR");
                error.put("message", reason != null ? reason : "连接关闭");
                relaySendTo(clientId, error);
            } catch (JSONException ignored) {
            }
            relayKnownClients.remove(clientId);
            postClientDisconnected(clientId, reason != null ? reason : "连接关闭");
            return;
        }
        ClientConnection client = clients.get(clientId);
        if (client != null) {
            closeConnection(client, reason != null ? reason : "连接关闭");
        }
    }

    public int getMaxClients() {
        return MAX_CLIENTS;
    }

    public boolean isFull() {
        if (webSocketMode || relayMode) {
            return relayKnownClients.size() >= MAX_CLIENTS;
        }
        return clients.size() >= MAX_CLIENTS;
    }
}

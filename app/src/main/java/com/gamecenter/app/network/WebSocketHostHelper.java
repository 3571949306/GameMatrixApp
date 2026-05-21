package com.gamecenter.app.network;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import okio.ByteString;

/**
 * WebSocket主机端辅助类，封装主机端通过WebSocket与服务器通信的逻辑。
 *
 * <p>打个比方：这个类就像主机端的"电话总机"，通过WebSocket连接到中转服务器，
 * 然后负责接听来电（客户端连接）、转接电话（消息转发）、挂断电话（断开连接），
 * 以及定期检查线路是否畅通（心跳检测）。</p>
 *
 * <p>在网络模块中的角色：这是WebSocket模式主机端的"通信枢纽"，
 * 与 {@link WebSocketClientHelper}（客户端侧的辅助工具）互为对偶。
 * WebSocket模式比云中转（Relay）模式更高效，因为它保持持久连接，
 * 不需要反复发送HTTP请求来拉取消息，就像打电话比写信更即时。</p>
 * <p>
 * 职责：
 * <ul>
 *   <li>建立WebSocket连接到中转/信令服务器</li>
 *   <li>处理来自WebSocket服务器的消息，识别客户端连接/断开/业务消息</li>
 *   <li>通过WebSocket向服务器发送广播和定向消息</li>
 *   <li>管理WebSocket心跳，检测连接是否存活</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 共享客户端列表（与Relay模式共用），实现模式切换时的无缝衔接</li>
 *   <li>心跳机制：连续 {@link #WS_MAX_MISSED_PONGS} 次未收到PONG且总超时超过 {@link #WS_HEARTBEAT_TIMEOUT} 时判定断开。
 *       就像打电话时连续几次问"你还在吗？"都没人回答，就判定电话断了。</li>
 *   <li>URL中的token参数自动提取并添加到Authorization请求头</li>
 *   <li>客户端ID生成时避免与已有ID冲突</li>
 * </ul>
 * <p>
 * 此类为包级私有，仅供 {@link GameSocketServer} 内部使用。
 */
class WebSocketHostHelper {

    private static final String TAG = "WebSocketHostHelper";

    /** WebSocket心跳发送间隔（毫秒），每10秒发一次"你还在吗？" */
    static final long WS_HEARTBEAT_INTERVAL = 10000L;

    /** WebSocket心跳超时阈值（毫秒），45秒没收到回复就认为连接断了 */
    static final long WS_HEARTBEAT_TIMEOUT = 45000L;

    /** 连续未收到PONG的最大次数，超过则判定连接断开。
     *  就像连续2次问"你还在吗？"都没人回答，就认为电话断了。 */
    static final int WS_MAX_MISSED_PONGS = 2;

    /** 回调接口，用于通知上层客户端连接/断开/消息/错误事件及请求停止 */
    private final WsHostCallback callback;
    /** Android上下文，用于获取OkHttpClient实例 */
    private final Context context;
    /** 已知客户端集合（与Relay模式共用），key为clientId，value为Boolean占位。
     *  使用ConcurrentHashMap保证多线程安全，就像一个"签到簿"，多个线程可以同时读写。 */
    private final ConcurrentHashMap<Integer, Boolean> relayKnownClients;
    /** 最大客户端连接数 */
    private final int maxClients;

    /** 是否处于WebSocket模式 */
    private volatile boolean webSocketMode = false;
    private WebSocket webSocket;
    private OkHttpClient okHttpClient;
    /** WebSocket服务器URL */
    private String wsUrl;
    /** 连续未收到PONG的次数 */
    private int consecutiveMissedPongs = 0;
    /** 最后一次收到WebSocket消息的时间戳 */
    private volatile long lastWsMessageTime = 0L;
    /** WebSocket心跳调度器 */
    private ScheduledExecutorService wsHeartbeatScheduler;
    private ScheduledFuture<?> wsHeartbeatTask;
    /** 发送消息的独立线程池 */
    private ExecutorService sendExecutor;
    /** WebSocket客户端ID自增计数器 */
    private int nextWsClientId = 1;
    /** 是否处于活跃状态 */
    private volatile boolean active = false;

    /**
     * WebSocket主机端回调接口。
     * <p>
     * 所有回调方法可能在WebSocket线程中调用，上层需要注意线程安全。
     * {@link #onRequestStop()} 用于通知上层需要停止服务端（如连接失败时）。
     */
    interface WsHostCallback {
        /** 客户端连接时回调 */
        void onClientConnected(int clientId, String ip);
        /** 客户端断开时回调 */
        void onClientDisconnected(int clientId, String reason);
        /** 收到客户端消息时回调 */
        void onMessageReceived(int clientId, JSONObject message);
        /** 发生错误时回调 */
        void onError(String message);
        /** 请求停止服务端时回调（如WebSocket连接失败或断开） */
        void onRequestStop();
    }

    /**
     * 构造WebSocketHostHelper。
     *
     * @param callback          事件回调接口
     * @param context           Android上下文，用于获取OkHttpClient实例，可为null
     * @param relayKnownClients 已知客户端集合（与Relay模式共用）
     * @param maxClients        最大客户端连接数
     */
    WebSocketHostHelper(WsHostCallback callback, Context context,
                        ConcurrentHashMap<Integer, Boolean> relayKnownClients, int maxClients) {
        this.callback = callback;
        this.context = context != null ? context.getApplicationContext() : null;
        this.relayKnownClients = relayKnownClients;
        this.maxClients = maxClients;
    }

    /**
     * 启动WebSocket模式，建立到WebSocket服务器的连接。
     * <p>
     * 连接过程在独立线程中执行。如果连接失败，会通过回调通知错误并请求停止服务端。
     *
     * @param wsUrl WebSocket服务器的URL
     * @return true表示已开始连接（异步），false表示已在运行
     */
    boolean startWebSocket(String wsUrl) {
        if (active) {
            Log.w(TAG, "WebSocket is already running");
            return false;
        }
        NetworkLogger.logWs(TAG, "CONNECTING", wsUrl, "host starting websocket");
        this.wsUrl = wsUrl;
        webSocketMode = true;
        active = true;
        consecutiveMissedPongs = 0;
        lastWsMessageTime = System.currentTimeMillis();
        relayKnownClients.clear();
        nextWsClientId = 1;

        sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WebSocketHostWriter");
            thread.setDaemon(true);
            return thread;
        });

        // 在独立线程中执行WebSocket连接，避免阻塞调用线程
        new Thread(() -> {
            try {
                doWebSocketConnect();
            } catch (Exception e) {
                Log.e(TAG, "WebSocket connection error: " + e.getMessage());
                callback.onError("WebSocket 连接失败: " + e.getMessage());
                callback.onRequestStop();
            }
        }, "GameWebSocketHostConnect").start();

        return true;
    }

    /**
     * 执行WebSocket连接操作。
     * <p>
     * 使用OkHttp建立WebSocket连接，如果URL中包含token参数，
     * 会自动添加到Authorization请求头进行身份验证。
     * 连接成功后启动心跳定时任务。
     */
    private void doWebSocketConnect() {
        Log.d(TAG, "Connecting WebSocket to " + wsUrl);
        if (okHttpClient == null) {
            // 优先使用全局共享的OkHttpClient实例
            if (context != null) {
                okHttpClient = OkHttpClientProvider.getInstance(context).getWebSocketClient();
            } else {
                okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        // readTimeout设为0表示不超时，WebSocket需要长连接
                        .readTimeout(0, TimeUnit.SECONDS)
                        .build();
            }
        }
        Request.Builder requestBuilder = new Request.Builder().url(wsUrl);
        String tokenParam = extractTokenFromUrl(wsUrl);
        if (tokenParam != null && !tokenParam.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + tokenParam);
        }
        Request request = requestBuilder.build();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket opened");
                NetworkLogger.logWs(TAG, "OPEN", wsUrl, "host websocket opened");
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                startWebSocketHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                NetworkLogger.logEvent(TAG, "MESSAGE", wsUrl != null ? "ws" : "-", -1, "incoming-text");
                handleWebSocketMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                NetworkLogger.logEvent(TAG, "MESSAGE", wsUrl != null ? "ws" : "-", -1, "incoming-binary");
                // 二进制消息按UTF-8解码后处理
                handleWebSocketMessage(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                webSocket.close(1000, "Host closing");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " " + reason);
                if (active) {
                    callback.onError("WebSocket 连接已关闭: " + reason);
                    callback.onRequestStop();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                if (active) {
                    callback.onError("WebSocket 连接失败: " + t.getMessage());
                    callback.onRequestStop();
                }
            }
        });
    }

    /**
     * 生成不与已有客户端ID冲突的临时客户端ID。
     * <p>
     * 使用synchronized保证线程安全，自增ID直到找到未被占用的值。
     *
     * @return 唯一的客户端ID
     */
    private synchronized int generateTempClientId() {
        int id = nextWsClientId++;
        // 跳过已被占用的ID（可能来自Relay模式残留）
        while (relayKnownClients.containsKey(id)) {
            id = nextWsClientId++;
        }
        return id;
    }

    /**
     * 处理从WebSocket收到的消息。
     * <p>
     * 消息处理逻辑：
     * <ol>
     *   <li>忽略协议控制消息：PONG、WELCOME、ROOM_CREATED、ROOM_STATE、PING</li>
     *   <li>JOIN消息：识别新客户端连接，分配或使用消息中的clientId</li>
     *   <li>DISCONNECT/CLIENT_DISCONNECTED消息：识别客户端断开</li>
     *   <li>其他业务消息：附加元数据后通知上层</li>
     * </ol>
     * <p>
     * 对于无法识别clientId的消息，默认使用clientId=1并自动注册为新客户端。
     *
     * @param text WebSocket消息文本
     */
    private void handleWebSocketMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String type = json.optString("type", "");

            // 忽略协议控制消息，不需要转发给上层
            if ("PONG".equals(type)) return;
            if ("WELCOME".equals(type)) return;
            if ("ROOM_CREATED".equals(type)) return;
            if ("ROOM_STATE".equals(type)) return;
            if ("PING".equals(type)) return;

            if ("JOIN".equals(type)) {
                int clientId = json.optInt("clientId", -1);
                // 如果消息中没有clientId，生成一个临时ID
                if (clientId <= 0) clientId = generateTempClientId();
                if (!relayKnownClients.containsKey(clientId)) {
                    relayKnownClients.put(clientId, true);
                    callback.onClientConnected(clientId, "websocket");
                }
            }

            if ("DISCONNECT".equals(type) || "CLIENT_DISCONNECTED".equals(type)) {
                int clientId = json.optInt("clientId", -1);
                if (clientId > 0) {
                    relayKnownClients.remove(clientId);
                    callback.onClientDisconnected(clientId, json.optString("reason", "连接关闭"));
                }
                return;
            }

            // 尝试从消息中提取clientId，优先使用"clientId"字段，其次使用"_clientId"字段
            int clientId = json.optInt("clientId", -1);
            if (clientId <= 0) clientId = json.optInt("_clientId", -1);
            // 如果仍然无法识别clientId，默认使用1（兼容某些服务器实现）
            if (clientId <= 0) clientId = 1;

            // 如果clientId不在已知客户端中，自动注册为新客户端
            if (!relayKnownClients.containsKey(clientId)) {
                relayKnownClients.put(clientId, true);
                callback.onClientConnected(clientId, "websocket");
            }

            // 附加元数据，标识消息来源为WebSocket模式
            json.put("_remoteIp", "websocket");
            json.put("_clientId", clientId);
            callback.onMessageReceived(clientId, json);
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON from WebSocket: " + text);
        }
    }

    /**
     * 从URL中提取token参数值。
     * <p>
     * 查找URL中"token="参数，支持URL编码的token值。
     *
     * @param url 包含token参数的URL
     * @return token字符串，如果URL为空或不含token则返回null
     */
    private static String extractTokenFromUrl(String url) {
        if (url == null) return null;
        int tokenIdx = url.indexOf("token=");
        if (tokenIdx < 0) return null;
        int start = tokenIdx + 6;
        int end = url.indexOf("&", start);
        String token = end > 0 ? url.substring(start, end) : url.substring(start);
        try {
            // 对URL编码的token进行解码
            return java.net.URLDecoder.decode(token, "UTF-8");
        } catch (Exception e) {
            return token;
        }
    }

    /**
     * 启动WebSocket心跳定时任务。
     * <p>
     * 心跳机制：每隔 {@link #WS_HEARTBEAT_INTERVAL} 检查一次，
     * 如果距上次收到消息已超过心跳间隔，则发送PING并增加连续未响应计数。
     * 当连续未响应次数达到 {@link #WS_MAX_MISSED_PONGS} 且总超时超过
     * {@link #WS_HEARTBEAT_TIMEOUT} 时，关闭WebSocket连接。
     */
    private void startWebSocketHeartbeat() {
        NetworkLogger.logWs(TAG, "HEARTBEAT_START", wsUrl, "interval=" + WS_HEARTBEAT_INTERVAL);
        if (wsHeartbeatScheduler != null) wsHeartbeatScheduler.shutdownNow();
        wsHeartbeatScheduler = Executors.newScheduledThreadPool(1);

        wsHeartbeatTask = wsHeartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!webSocketMode || webSocket == null || !active) return;
            long now = System.currentTimeMillis();
            long elapsed = now - lastWsMessageTime;
            // 如果最近收到过消息，重置连续未响应计数
            if (elapsed < WS_HEARTBEAT_INTERVAL) {
                consecutiveMissedPongs = 0;
                return;
            }
            consecutiveMissedPongs++;
            // 连续多次未响应且总超时，判定连接已断开
            if (consecutiveMissedPongs >= WS_MAX_MISSED_PONGS && elapsed > WS_HEARTBEAT_TIMEOUT) {
                Log.w(TAG, "WebSocket heartbeat timeout");
                consecutiveMissedPongs = 0;
                if (webSocket != null) webSocket.close(1001, "Heartbeat timeout");
                return;
            }
            try {
                JSONObject ping = new JSONObject();
                ping.put("type", "PING");
                webSocket.send(ping.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Error sending ping: " + e.getMessage());
            }
        }, WS_HEARTBEAT_INTERVAL, WS_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /** 停止WebSocket心跳定时任务并释放调度器资源 */
    private void stopWebSocketHeartbeat() {
        NetworkLogger.logWs(TAG, "HEARTBEAT_STOP", wsUrl, null);
        if (wsHeartbeatTask != null) { wsHeartbeatTask.cancel(true); wsHeartbeatTask = null; }
        if (wsHeartbeatScheduler != null) { wsHeartbeatScheduler.shutdownNow(); wsHeartbeatScheduler = null; }
    }

    /**
     * 停止WebSocket模式，释放所有资源。
     * <p>
     * 停止心跳、关闭WebSocket连接、关闭发送线程池。
     */
    void stop() {
        stopWebSocketHeartbeat();
        webSocketMode = false;
        active = false;
        if (webSocket != null) {
            try { webSocket.close(1000, "Host stopped"); } catch (Exception e) { Log.w(TAG, "close websocket: " + e.getMessage()); }
            webSocket = null;
        }
        if (sendExecutor != null) { sendExecutor.shutdownNow(); sendExecutor = null; }
    }

    /**
     * 通过WebSocket广播消息到所有客户端。
     * <p>
     * 在消息中添加"broadcast"标志，由服务器识别并转发给所有客户端。
     *
     * @param json 要广播的JSON消息
     */
    void webSocketBroadcast(JSONObject json) {
        if (webSocket == null) return;
        try { json.put("broadcast", true); webSocket.send(json.toString()); }
        catch (JSONException e) { Log.e(TAG, "WebSocket broadcast error: " + e.getMessage()); }
    }

    /**
     * 通过WebSocket向指定客户端发送消息。
     * <p>
     * 在消息中添加"targetClientId"字段，由服务器识别并转发给目标客户端。
     *
     * @param clientId 目标客户端ID
     * @param json     要发送的JSON消息
     */
    void webSocketSendTo(int clientId, JSONObject json) {
        if (webSocket == null) return;
        try {
            json.put("targetClientId", clientId);
            webSocket.send(json.toString());
        } catch (JSONException e) { Log.e(TAG, "WebSocket sendTo error: " + e.getMessage()); }
    }

    /**
     * 断开指定客户端的连接。
     * <p>
     * 从已知客户端中移除并通知上层。WebSocket模式下实际的断开
     * 由服务器端处理，此处仅更新本地状态。
     *
     * @param clientId 要断开的客户端ID
     * @param reason   断开原因
     */
    void disconnectClient(int clientId, String reason) {
        relayKnownClients.remove(clientId);
        callback.onClientDisconnected(clientId, reason != null ? reason : "连接关闭");
    }

    /** 是否处于WebSocket模式 */
    boolean isWebSocketMode() { return webSocketMode; }

    /** 获取已连接的客户端数量 */
    int getConnectedClientCount() { return relayKnownClients.size(); }

    /** 客户端数量是否已达上限 */
    boolean isFull() { return relayKnownClients.size() >= maxClients; }
}

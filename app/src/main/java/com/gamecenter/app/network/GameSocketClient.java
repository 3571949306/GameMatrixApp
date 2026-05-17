package com.gamecenter.app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.net.nsd.NsdServiceInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.content.Context;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 游戏Socket客户端，负责管理多人游戏中的客户端网络通信。
 * <p>
 * 支持三种连接模式：
 * <ul>
 *   <li>局域网直连模式：通过TCP Socket直接连接主机端</li>
 *   <li>云中转（Relay）模式：通过HTTP轮询与中转服务器通信</li>
 *   <li>WebSocket模式：通过OkHttp WebSocket连接到WebSocket服务器</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link WeakReference} 持有监听器，避免Activity/Fragment销毁后内存泄漏</li>
 *   <li>所有回调通过 {@link Handler} 投递到主线程，确保UI操作线程安全</li>
 *   <li>内置自动重连机制，支持指数退避策略，默认最多重连3次</li>
 *   <li>使用单例模式管理客户端实例，确保全局只有一个活跃连接</li>
 *   <li>WebSocket模式下支持消息缓存，连接建立后自动发送缓存消息</li>
 * </ul>
 */
public class GameSocketClient {

    private static final String TAG = "GameSocketClient";

    /** Socket连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT = 5000;

    /** TCP/Relay模式心跳发送间隔（毫秒） */
    private static final long HEARTBEAT_INTERVAL = 3000L;

    /** TCP/Relay模式心跳超时阈值（毫秒） */
    private static final long HEARTBEAT_TIMEOUT = 30000L;

    /** 默认最大重连尝试次数 */
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 3;

    /** 默认重连基础间隔（毫秒） */
    private static final long DEFAULT_RECONNECT_INTERVAL = 2000L;

    /** 默认重连最大间隔（毫秒），指数退避的上限 */
    private static final long DEFAULT_MAX_RECONNECT_INTERVAL = 15000L;

    /** WebSocket模式心跳发送间隔（毫秒），比TCP模式更长以减少开销 */
    private static final long WS_HEARTBEAT_INTERVAL = 10000L;

    /** WebSocket模式心跳超时阈值（毫秒） */
    private static final long WS_HEARTBEAT_TIMEOUT = 45000L;

    /** WebSocket模式连续未收到PONG的最大次数，超过则判定连接断开 */
    private static final int WS_MAX_MISSED_PONGS = 2;

    /** WebSocket模式下待发送消息的最大缓存数量 */
    private static final int MAX_PENDING_MESSAGES = 32;

    /**
     * 连接状态枚举，描述客户端的生命周期状态。
     * <ul>
     *   <li>DISCONNECTED：已断开</li>
     *   <li>CONNECTING：正在连接中</li>
     *   <li>CONNECTED：已连接（未认证）</li>
     *   <li>AUTHENTICATED：已认证（收到WELCOME后）</li>
     *   <li>RECONNECTING：正在重连中</li>
     * </ul>
     */
    public enum ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATED, RECONNECTING
    }

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private String serverHost;
    private int serverPort;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread readThread;

    /** 发送消息的独立线程池，单线程执行以保证消息顺序 */
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SocketWriter");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledExecutorService reconnectScheduler;
    private ScheduledFuture<?> reconnectTask;

    /** 当前重连尝试次数 */
    private int reconnectAttempts = 0;
    private int maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS;
    /** 重连基础间隔，指数退避的底数 */
    private long reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    /** 重连最大间隔，指数退避的上限 */
    private long maxReconnectInterval = DEFAULT_MAX_RECONNECT_INTERVAL;

    /** 是否为用户主动断开，主动断开时不触发自动重连 */
    private volatile boolean manualDisconnect = false;
    /** 最后一次收到服务器消息的时间戳，用于心跳超时检测 */
    private volatile long lastServerMessageTime = 0L;
    /** 是否抑制下一次断开通知，用于重连场景避免重复提示 */
    private volatile boolean suppressNextDisconnectNotice = false;
    /** P2P令牌，用于重连时服务器识别同一客户端 */
    private String peerToken = "";
    /** 通信协议版本号，用于兼容性协商 */
    private int protocolVersion = 2;

    /** 是否处于云中转模式 */
    private volatile boolean relayMode = false;
    /** 是否正在进行云中转轮询 */
    private volatile boolean relayPolling = false;
    private String relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL;
    /** 云中转房间码 */
    private String relayRoomCode = "";
    /** 云中转客户端令牌，用于身份验证 */
    private String relayClientToken = "";
    /** 云中转轮询线程 */
    private Thread relayPollThread;

    /** 是否处于WebSocket模式 */
    private volatile boolean webSocketMode = false;
    /** 防止并发处理断开事件的标志 */
    private volatile boolean isHandlingDisconnection = false;
    private WebSocket webSocket;
    private OkHttpClient okHttpClient;
    private String wsUrl;
    /** WebSocket模式下待发送的消息队列，连接建立后自动发送 */
    private final ConcurrentLinkedQueue<JSONObject> pendingMessages = new ConcurrentLinkedQueue<>();
    /** WebSocket模式连续未收到PONG的次数 */
    private int consecutiveMissedPongs = 0;

    /** 主线程Handler，用于将回调投递到主线程 */
    private Handler mainHandler;
    private WeakReference<OnConnectedListener> connectedListenerRef;
    private WeakReference<OnDisconnectedListener> disconnectedListenerRef;
    private WeakReference<OnMessageReceivedListener> messageListenerRef;
    private WeakReference<OnErrorListener> errorListenerRef;
    private WeakReference<OnStateChangedListener> stateChangedListenerRef;

    private String playerName = "Player";
    /** 服务器分配的客户端ID，-1表示未分配 */
    private int clientId = -1;

    public GameSocketClient() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 通过NSD服务信息连接到主机端。
     * <p>
     * 从 {@link NsdServiceInfo} 中提取主机地址和端口号进行连接。
     *
     * @param serviceInfo NSD发现的服务信息
     */
    public void connect(NsdServiceInfo serviceInfo) {
        if (serviceInfo != null && serviceInfo.getHost() != null && serviceInfo.getPort() > 0) {
            connect(serviceInfo.getHost().getHostAddress(), serviceInfo.getPort());
        } else {
            postError("服务信息无效");
        }
    }

    /**
     * 通过主机地址和端口连接到主机端（局域网直连模式）。
     * <p>
     * 如果当前已有连接，会先强制断开再建立新连接。
     * 连接过程在独立线程中执行，避免阻塞调用线程。
     *
     * @param host 主机端IP地址
     * @param port 主机端端口号
     */
    public void connect(String host, int port) {
        if (state == ConnectionState.CONNECTING) return;
        // 如果当前已有连接，先断开
        if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED || state == ConnectionState.RECONNECTING) {
            forceDisconnect();
        } else if (relayMode) {
            stopRelay();
        } else if (webSocketMode) {
            closeWebSocketQuietly();
        }
        serverHost = host;
        serverPort = port;
        manualDisconnect = false;
        clientId = -1;
        lastServerMessageTime = System.currentTimeMillis();
        setState(ConnectionState.CONNECTING);
        new Thread(() -> {
            try {
                doConnect();
            } catch (IOException e) {
                handleConnectionFailure(e.getMessage());
            }
        }, "GameSocketConnect").start();
    }

    /**
     * 通过WebSocket URL连接到服务器（WebSocket模式）。
     * <p>
     * 会从URL中解析房间码（?room=参数），并使用OkHttp建立WebSocket连接。
     * 如果URL中包含token参数，会自动添加到Authorization请求头。
     *
     * @param wsUrl WebSocket服务器的完整URL
     */
    public void connectWebSocket(String wsUrl) {
        NetworkLogger.logWs(TAG, "CONNECTING", wsUrl, "client connecting");
        if (state == ConnectionState.CONNECTING) return;
        if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED || state == ConnectionState.RECONNECTING) {
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
        // 从URL中解析房间码
        try {
            int roomStart = wsUrl.indexOf("?room=");
            if (roomStart > 0) {
                roomStart += 6;
                int roomEnd = wsUrl.indexOf("&", roomStart);
                relayRoomCode = roomEnd > 0 ? wsUrl.substring(roomStart, roomEnd) : wsUrl.substring(roomStart);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse roomCode from wsUrl: " + e.getMessage());
        }
        new Thread(() -> {
            try { doWebSocketConnect(); } catch (Exception e) { handleConnectionFailure(e.getMessage()); }
        }, "GameWebSocketConnect").start();
    }

    /**
     * 通过云中转连接到主机端（云中转模式）。
     * <p>
     * 向中转服务器发送加入房间请求，获取客户端ID和令牌后，
     * 启动轮询线程持续拉取来自主机端的消息。
     *
     * @param roomCode 房间码
     * @param baseUrl  中转服务器的基础URL，为空则使用默认地址
     */
    public void connectRelay(String roomCode, String baseUrl) {
        String code = RemoteP2PUtil.normalizeRoomCode(roomCode);
        if (code.isEmpty()) { postError("房间码无效"); return; }
        if (state == ConnectionState.CONNECTING) return;
        if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED || state == ConnectionState.RECONNECTING) {
            forceDisconnect();
        } else if (webSocketMode) {
            closeWebSocketQuietly();
        } else if (relayMode) {
            stopRelay();
        }
        relayMode = true;
        relayPolling = false;
        relayRoomCode = code;
        relayBaseUrl = RelayClientHelper.resolveBaseUrl(baseUrl);
        relayClientToken = "";
        serverHost = "";
        serverPort = 0;
        manualDisconnect = false;
        clientId = -1;
        lastServerMessageTime = System.currentTimeMillis();
        setState(ConnectionState.CONNECTING);
        new Thread(() -> {
            try { doRelayJoin(); } catch (Exception e) { handleConnectionFailure(e.getMessage()); }
        }, "GameRelayClientJoin").start();
    }

    /**
     * 强制断开所有连接，不触发重连和断开通知。
     * <p>
     * 用于切换连接模式时清理旧连接状态。
     */
    private void forceDisconnect() {
        stopHeartbeat();
        stopReconnect();
        stopRelay();
        closeWebSocketQuietly();
        state = ConnectionState.DISCONNECTED;
        closeTransportQuietly();
    }

    /** 安静地关闭TCP传输层（Socket、Reader、Writer），不触发任何回调 */
    private void closeTransportQuietly() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) { Log.w(TAG, "close transport: " + e.getMessage()); }
        reader = null; writer = null; socket = null;
    }

    /** 安静地关闭WebSocket连接，不触发任何回调 */
    private void closeWebSocketQuietly() {
        webSocketMode = false;
        if (webSocket != null) {
            try { webSocket.close(1000, "Disconnect"); } catch (Exception e) { Log.w(TAG, "close websocket: " + e.getMessage()); }
            webSocket = null;
        }
    }

    /**
     * 执行TCP Socket连接（局域网直连模式）。
     * <p>
     * 连接成功后设置Socket选项、启动心跳、启动读取线程并发送JOIN消息。
     *
     * @throws IOException 如果连接失败
     */
    private void doConnect() throws IOException {
        lastServerMessageTime = System.currentTimeMillis();
        socket = new Socket();
        InetAddress address = InetAddress.getByName(serverHost);
        socket.connect(new InetSocketAddress(address, serverPort), CONNECT_TIMEOUT);
        // 启用TCP KeepAlive，让操作系统层面检测死连接
        socket.setKeepAlive(true);
        // 禁用Nagle算法，减少小包延迟
        socket.setTcpNoDelay(true);
        // 设置读取超时，避免readLine永久阻塞
        socket.setSoTimeout((int) HEARTBEAT_TIMEOUT + 5000);
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        setState(ConnectionState.CONNECTED);
        startHeartbeat();
        startReadThread();
        sendJoin(playerName);
        postConnected();
        reconnectAttempts = 0;
    }

    /**
     * 启动TCP消息读取线程。
     * <p>
     * 读取线程持续从Socket输入流读取消息。
     * 读取异常时触发重连流程，正常结束且非主动断开时也触发重连。
     */
    private void startReadThread() {
        if (readThread != null) readThread.interrupt();
        readThread = new Thread(() -> {
            String line;
            try {
                while (socket != null && !socket.isClosed() && (line = reader.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                // 读取异常时，如果当前已连接，则抑制断开通知并触发重连
                if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED) {
                    suppressNextDisconnectNotice = true;
                    handleDisconnection("等待重连: " + e.getMessage());
                }
            } finally {
                // 读取结束且非主动断开时，触发重连
                if (!manualDisconnect && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED)) {
                    handleDisconnection(suppressNextDisconnectNotice ? "等待重连" : "连接已断开");
                }
            }
        }, "SocketReader");
        readThread.setDaemon(true);
        readThread.start();
    }

    /**
     * 执行云中转加入房间操作。
     * <p>
     * 向中转服务器发送加入请求，携带玩家名称、令牌和协议版本等信息。
     * 加入成功后启动轮询线程和心跳。
     *
     * @throws Exception 如果加入房间失败
     */
    private void doRelayJoin() throws Exception {
        Log.d(TAG, "Joining relay room " + relayRoomCode);
        JSONObject body = new JSONObject();
        body.put("roomCode", relayRoomCode);
        body.put("playerName", playerName);
        body.put("peerToken", peerToken);
        body.put("protocolVersion", protocolVersion);
        // 携带上次的clientId，支持断线重连时恢复身份
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

    /**
     * 启动云中转轮询线程。
     * <p>
     * 轮询线程以长轮询方式（超时35秒）持续从中转服务器拉取消息。
     * 轮询失败时触发重连流程。
     */
    private void startRelayPolling() {
        relayPolling = true;
        if (relayPollThread != null) relayPollThread.interrupt();
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
                            if (payload != null) handleMessage(payload.toString());
                        }
                    }
                } catch (Exception e) {
                    if (relayPolling && !manualDisconnect) {
                        // 轮询失败时抑制断开通知，直接进入重连流程
                        suppressNextDisconnectNotice = true;
                        handleDisconnection("等待云联机重连: " + e.getMessage());
                        return;
                    }
                }
            }
        }, "GameRelayClientPoll");
        relayPollThread.setDaemon(true);
        relayPollThread.start();
    }

    /**
     * 构建云中转请求的基础请求体，委托给 {@link RelayClientHelper#baseBody}。
     *
     * @return 包含认证信息的JSONObject
     * @throws JSONException 如果构建JSON失败
     */
    private JSONObject relayBaseBody() throws JSONException {
        return RelayClientHelper.baseBody(relayRoomCode, clientId, relayClientToken);
    }

    /**
     * 停止云中转模式。
     * <p>
     * 停止轮询线程，并异步通知中转服务器客户端已断开。
     * 只有在已成功加入房间后才发送断开通知。
     */
    private void stopRelay() {
        relayPolling = false;
        if (relayPollThread != null) { relayPollThread.interrupt(); relayPollThread = null; }
        // 判断是否需要通知服务器：只有已成功加入房间（有有效token）才通知
        final boolean shouldNotify = relayMode && clientId > 0 && relayClientToken != null && !relayClientToken.isEmpty();
        final String baseUrl = relayBaseUrl;
        final String roomCode = relayRoomCode;
        final int id = clientId;
        final String token = relayClientToken;
        relayMode = false;
        relayClientToken = "";
        if (shouldNotify) {
            // 异步发送断开通知，避免阻塞当前线程
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("roomCode", roomCode);
                    body.put("role", "client");
                    body.put("clientId", id);
                    body.put("token", token);
                    RelayHttpClient.post(baseUrl, "/disconnect", body, 5000);
                } catch (Exception e) { Log.w(TAG, "relay disconnect: " + e.getMessage()); }
            }, "GameRelayClientDisconnect").start();
        }
    }

    /**
     * 从URL中提取token参数，委托给 {@link WebSocketClientHelper#extractTokenFromUrl}。
     *
     * @param url 包含token参数的URL
     * @return token字符串，如果URL为空或不含token则返回null
     */
    static String extractTokenFromUrl(String url) {
        if (url == null) return null;
        return WebSocketClientHelper.extractTokenFromUrl(url);
    }

    /**
     * 执行WebSocket连接操作。
     * <p>
     * 使用OkHttp建立WebSocket连接，如果URL中包含token参数，
     * 会自动添加到Authorization请求头进行身份验证。
     * 连接成功后启动心跳并发送JOIN消息。
     */
    private void doWebSocketConnect() {
        Log.d(TAG, "Connecting WebSocket to " + wsUrl);
        if (okHttpClient == null) {
            // 优先使用全局共享的OkHttpClient实例
            if (appContext != null) {
                okHttpClient = OkHttpClientProvider.getInstance(appContext).getWebSocketClient();
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
                NetworkLogger.logWs(TAG, "OPEN", wsUrl, "client websocket opened");
                lastServerMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                setState(ConnectionState.CONNECTED);
                setState(ConnectionState.AUTHENTICATED);
                startWebSocketHeartbeat();
                sendJoin(playerName);
                postConnected();
                reconnectAttempts = 0;
                // 连接建立后发送缓存的消息
                flushPendingMessages();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                lastServerMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                NetworkLogger.logEvent(TAG, "MESSAGE", relayRoomCode, clientId, "incoming");
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                NetworkLogger.logWs(TAG, "CLOSE", wsUrl, "code=" + code + " reason=" + reason);
                webSocket.close(1000, "Client closing");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                NetworkLogger.logWs(TAG, "CLOSED", wsUrl, "code=" + code);
                if (!manualDisconnect && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED)) {
                    handleDisconnection("WebSocket closed");
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                NetworkLogger.logEvent(TAG, "FAILURE", relayRoomCode, clientId, "websocket-failure");
                if (!manualDisconnect && state != ConnectionState.DISCONNECTED && state != ConnectionState.RECONNECTING) {
                    postError("WebSocket error: " + t.getMessage());
                    handleDisconnection("WebSocket failure");
                }
            }
        });
    }

    /**
     * 启动WebSocket模式的心跳定时任务。
     * <p>
     * 心跳机制：每隔 {@link #WS_HEARTBEAT_INTERVAL} 检查一次，
     * 如果距上次收到消息已超过心跳间隔，则发送PING并增加连续未响应计数。
     * 当连续未响应次数达到 {@link #WS_MAX_MISSED_PONGS} 且总超时超过
     * {@link #WS_HEARTBEAT_TIMEOUT} 时，关闭WebSocket触发重连。
     */
    private void startWebSocketHeartbeat() {
        NetworkLogger.logWs(TAG, "HEARTBEAT_START", wsUrl, "interval=" + WS_HEARTBEAT_INTERVAL);
        stopHeartbeat();
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!webSocketMode || webSocket == null) return;
            if (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED) return;
            long now = System.currentTimeMillis();
            long elapsed = now - lastServerMessageTime;
            // 如果最近收到过消息，重置连续未响应计数
            if (elapsed < WS_HEARTBEAT_INTERVAL) { consecutiveMissedPongs = 0; return; }
            consecutiveMissedPongs++;
            // 连续多次未响应且总超时，判定连接已断开
            if (consecutiveMissedPongs >= WS_MAX_MISSED_PONGS && elapsed > WS_HEARTBEAT_TIMEOUT) {
                consecutiveMissedPongs = 0;
                if (webSocket != null) webSocket.close(1001, "Heartbeat timeout");
                return;
            }
            sendPing();
        }, WS_HEARTBEAT_INTERVAL, WS_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * 发送WebSocket模式下缓存的消息。
     * <p>
     * 在WebSocket连接建立后调用，将连接断开期间缓存的消息全部发送出去。
     */
    private void flushPendingMessages() {
        if (webSocket == null || pendingMessages.isEmpty()) return;
        Log.d(TAG, "Flushing " + pendingMessages.size() + " pending messages");
        JSONObject msg;
        while ((msg = pendingMessages.poll()) != null) {
            if (webSocket != null) webSocket.send(msg.toString());
        }
    }

    /**
     * 启动TCP/Relay模式的心跳定时任务。
     * <p>
     * 定期发送PING消息，如果超过 {@link #HEARTBEAT_TIMEOUT} 未收到服务器消息则记录警告。
     */
    private void startHeartbeat() {
        if (heartbeatScheduler != null) heartbeatScheduler.shutdown();
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED) {
                long now = System.currentTimeMillis();
                if (lastServerMessageTime > 0 && now - lastServerMessageTime > HEARTBEAT_TIMEOUT) {
                    Log.w(TAG, "Heartbeat delayed, keep socket alive");
                }
                sendPing();
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /** 停止心跳定时任务并释放调度器资源 */
    private void stopHeartbeat() {
        NetworkLogger.logWs(TAG, "HEARTBEAT_STOP", wsUrl, null);
        if (heartbeatTask != null) { heartbeatTask.cancel(true); heartbeatTask = null; }
        if (heartbeatScheduler != null) { heartbeatScheduler.shutdown(); heartbeatScheduler = null; }
    }

    /** 发送PING消息，用于保持连接活跃 */
    private void sendPing() {
        JSONObject json = new JSONObject();
        try { json.put("type", "PING"); send(json); } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /**
     * 处理从服务器收到的消息。
     * <p>
     * 根据消息类型执行不同逻辑：
     * <ul>
     *   <li>WELCOME：记录服务器分配的clientId，切换到AUTHENTICATED状态</li>
     *   <li>PONG：心跳响应，仅更新消息时间戳</li>
     *   其他类型：转发给消息监听器
     * </ul>
     *
     * @param message 原始字符串消息
     */
    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");
            lastServerMessageTime = System.currentTimeMillis();
            switch (type) {
                case "WELCOME":
                    clientId = json.optInt("clientId", -1);
                    setState(ConnectionState.AUTHENTICATED);
                    postMessageReceived(json);
                    break;
                case "PONG":
                    // PONG是心跳响应，无需特殊处理
                    break;
                default:
                    postMessageReceived(json);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON: " + message);
        }
    }

    /**
     * 主动断开连接。
     * <p>
     * 设置手动断开标志，停止心跳和重连，关闭所有连接资源，
     * 并通知断开监听器。主动断开不会触发自动重连。
     */
    public void disconnect() {
        NetworkLogger.logEvent(TAG, "LEAVE_ROOM", relayRoomCode, clientId, "DISCONNECT");
        manualDisconnect = true;
        stopHeartbeat();
        stopReconnect();
        setState(ConnectionState.DISCONNECTED);
        stopRelay();
        closeWebSocketQuietly();
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) { Log.w(TAG, "close socket: " + e.getMessage()); }
        reader = null; writer = null; socket = null;
        postDisconnected("用户主动断开");
    }

    /**
     * 处理连接断开事件。
     * <p>
     * 使用synchronized和 {@link #isHandlingDisconnection} 标志防止并发处理。
     * 断开后根据重连策略决定是否自动重连。
     *
     * @param reason 断开原因
     */
    private void handleDisconnection(String reason) {
        // 防止重复处理：手动断开、已断开、正在重连时跳过
        if (manualDisconnect || state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) return;
        synchronized (this) {
            // 双重检查，防止并发进入
            if (isHandlingDisconnection || state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) return;
            isHandlingDisconnection = true;
        }
        try {
            stopHeartbeat();
            if (relayMode) relayPolling = false;
            if (webSocketMode) closeWebSocketQuietly();
            setState(ConnectionState.DISCONNECTED);
            closeTransportQuietly();
            // 根据suppressNextDisconnectNotice决定是否通知断开
            if (!suppressNextDisconnectNotice) postDisconnected(reason);
            suppressNextDisconnectNotice = false;
            // 未超过最大重连次数时，自动调度重连
            if (reconnectAttempts < maxReconnectAttempts) scheduleReconnect();
        } finally { isHandlingDisconnection = false; }
    }

    /**
     * 处理连接失败事件。
     * <p>
     * 连接失败时切换到DISCONNECTED状态，通知错误监听器，
     * 并根据重连策略决定是否自动重连。
     *
     * @param reason 失败原因
     */
    private void handleConnectionFailure(String reason) {
        if (manualDisconnect) return;
        setState(ConnectionState.DISCONNECTED);
        postError("连接失败: " + reason);
        if (reconnectAttempts < maxReconnectAttempts) scheduleReconnect();
    }

    /**
     * 调度重连任务。
     * <p>
     * 使用指数退避策略计算重连延迟：delay = baseInterval * 2^(attempt-1)，
     * 最大不超过 {@link #maxReconnectInterval}。
     * 例如：base=2000ms时，第1次重连延迟2秒，第2次4秒，第3次8秒。
     */
    private void scheduleReconnect() {
        if (reconnectTask != null) reconnectTask.cancel(true);
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            try { if (!reconnectScheduler.awaitTermination(1, TimeUnit.SECONDS)) {} } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            reconnectScheduler = null;
        }
        reconnectScheduler = Executors.newScheduledThreadPool(1);
        reconnectAttempts++;
        setState(ConnectionState.RECONNECTING);
        // 指数退避：每次重连延迟翻倍，但不超过最大间隔
        long exponentialDelay = (long) (reconnectInterval * Math.pow(2, reconnectAttempts - 1));
        long delay = Math.min(exponentialDelay, maxReconnectInterval);
        NetworkLogger.logWs(TAG, "RECONNECT", wsUrl, "attempt=" + reconnectAttempts + " delay=" + delay);
        reconnectTask = reconnectScheduler.schedule(() -> {
            if (manualDisconnect) return;
            try {
                if (webSocketMode) doWebSocketConnect();
                else if (relayMode) doRelayJoin();
                else doConnect();
            } catch (Exception e) { handleConnectionFailure(e.getMessage()); }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** 停止重连调度器，取消待执行的重连任务 */
    private void stopReconnect() {
        if (reconnectTask != null) { reconnectTask.cancel(true); reconnectTask = null; }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            try { if (!reconnectScheduler.awaitTermination(1, TimeUnit.SECONDS)) {} } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            reconnectScheduler = null;
        }
    }

    /**
     * 立即重连，不等待退避延迟。
     * <p>
     * 如果当前已有活跃连接或正在连接中，则不执行重连。
     * 重连时会重置心跳和未响应计数。
     */
    public void reconnectNow() {
        NetworkLogger.logWs(TAG, "RECONNECT_NOW", wsUrl, "immediate reconnect");
        if (!webSocketMode && !relayMode && (serverHost == null || serverHost.trim().isEmpty() || serverPort <= 0)) {
            postError("没有可重连的主机地址");
            return;
        }
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED) return;
        stopReconnect();
        manualDisconnect = false;
        lastServerMessageTime = System.currentTimeMillis();
        consecutiveMissedPongs = 0;
        setState(ConnectionState.CONNECTING);
        new Thread(() -> {
            try {
                if (webSocketMode) doWebSocketConnect();
                else if (relayMode) doRelayJoin();
                else doConnect();
            } catch (Exception e) { handleConnectionFailure(e.getMessage()); }
        }).start();
    }

    /**
     * 发送JSON消息到服务器。
     * <p>
     * 根据当前连接模式选择不同的发送方式：
     * <ul>
     *   <li>云中转模式：通过HTTP请求发送到中转服务器</li>
     *   <li>WebSocket模式：通过WebSocket发送，连接未就绪时缓存消息</li>
     *   <li>直连模式：通过Socket输出流发送</li>
     * </ul>
     *
     * @param json 要发送的JSONObject
     * @return true表示消息已发送或已缓存，false表示发送失败
     */
    public boolean send(JSONObject json) {
        if (json == null) return false;
        if (relayMode) return sendRelay(json);
        if (webSocketMode) return sendWebSocket(json);
        final String message = json.toString();
        if (!isTransportWritable()) return false;
        try {
            sendExecutor.execute(() -> sendMessageNow(message));
            return true;
        } catch (RejectedExecutionException e) { return false; }
    }

    /**
     * 通过WebSocket发送消息。
     * <p>
     * 如果WebSocket未连接或连接未就绪，消息会被缓存到 {@link #pendingMessages}，
     * 等待连接建立后自动发送。如果发送失败，消息也会被缓存。
     *
     * @param json 要发送的JSONObject
     * @return true（消息已发送或已缓存）
     */
    private boolean sendWebSocket(JSONObject json) {
        if (webSocket == null || (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED)) {
            // WebSocket未就绪，缓存消息等待连接建立后发送
            WebSocketClientHelper.offerPendingMessage(pendingMessages, json, MAX_PENDING_MESSAGES);
            return true;
        }
        boolean sent = webSocket.send(json.toString());
        if (!sent) {
            // 发送失败，缓存消息
            WebSocketClientHelper.offerPendingMessage(pendingMessages, json, MAX_PENDING_MESSAGES);
        }
        return true;
    }

    /**
     * 通过云中转发送消息到主机端。
     * <p>
     * 消息通过发送线程池异步发送，目标固定为"host"。
     *
     * @param json 要发送的JSONObject
     * @return true表示消息已提交到发送队列，false表示发送失败
     */
    private boolean sendRelay(JSONObject json) {
        if (clientId <= 0 || relayClientToken == null || relayClientToken.isEmpty()) return false;
        try {
            sendExecutor.execute(() -> {
                try {
                    JSONObject body = relayBaseBody();
                    body.put("to", "host");
                    body.put("payload", json);
                    RelayHttpClient.post(relayBaseUrl, "/send", body, 10000);
                } catch (Exception e) { postError("云联机发送失败: " + e.getMessage()); }
            });
            return true;
        } catch (RejectedExecutionException e) { return false; }
    }

    /**
     * 检查当前传输层是否可写。
     * <p>
     * 根据连接模式检查不同的条件：
     * <ul>
     *   <li>云中转模式：检查clientId和token是否有效</li>
     *   <li>WebSocket模式：检查WebSocket实例和连接状态</li>
     *   <li>直连模式：检查Writer和Socket是否可用</li>
     * </ul>
     *
     * @return true表示传输层可写
     */
    private boolean isTransportWritable() {
        if (relayMode) return clientId > 0 && relayClientToken != null && !relayClientToken.isEmpty();
        if (webSocketMode) return webSocket != null && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED);
        return writer != null && socket != null && !socket.isClosed();
    }

    /**
     * 同步发送消息（在发送线程池中执行）。
     * <p>
     * 使用synchronized保证消息发送顺序，避免多线程并发写入导致数据错乱。
     *
     * @param message 要发送的字符串消息
     */
    private void sendMessageNow(String message) {
        try {
            if (isTransportWritable()) {
                synchronized (this) {
                    writer.println(message);
                    if (writer.checkError()) {}
                }
            }
        } catch (Exception e) { Log.w(TAG, "send message: " + e.getMessage()); }
    }

    /**
     * 发送JOIN消息到服务器，携带玩家名称和认证信息。
     * <p>
     * 如果clientId大于0，表示这是重连，会设置reconnect标志和lastClientId，
     * 服务器可据此恢复之前的游戏状态。
     *
     * @param playerName 玩家名称
     */
    public void sendJoin(String playerName) {
        NetworkLogger.logEvent(TAG, "JOIN_ROOM", relayRoomCode, clientId, "JOIN");
        this.playerName = playerName;
        JSONObject json = new JSONObject();
        try {
            json.put("type", "JOIN");
            json.put("playerName", playerName);
            json.put("peerToken", peerToken);
            json.put("protocolVersion", protocolVersion);
            // clientId > 0 表示重连，告知服务器恢复之前的身份
            json.put("reconnect", clientId > 0);
            json.put("lastClientId", clientId);
            json.put("androidSdk", android.os.Build.VERSION.SDK_INT);
            send(json);
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /**
     * 发送出牌请求消息。
     *
     * @param cardsJson 出牌的JSON字符串，描述所出的牌
     * @param cardType  牌型标识
     */
    public void sendPlayRequest(String cardsJson, String cardType) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "REQUEST_PLAY");
            json.put("cards", cardsJson);
            json.put("cardType", cardType);
            send(json);
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /** 发送过牌（PASS）消息 */
    public void sendPass() {
        JSONObject json = new JSONObject();
        try { json.put("type", "PASS"); send(json); }
        catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /**
     * 更新连接状态并通知状态变化监听器。
     * <p>
     * 只有状态实际发生变化时才会通知。
     *
     * @param newState 新的连接状态
     */
    private void setState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            postStateChanged(newState);
        }
    }

    /** 通过主线程Handler投递连接成功事件到监听器 */
    private void postConnected() {
        mainHandler.post(() -> {
            OnConnectedListener l = connectedListenerRef != null ? connectedListenerRef.get() : null;
            if (l != null) l.onConnected(clientId);
        });
    }

    /** 通过主线程Handler投递断开连接事件到监听器 */
    private void postDisconnected(final String reason) {
        mainHandler.post(() -> {
            OnDisconnectedListener l = disconnectedListenerRef != null ? disconnectedListenerRef.get() : null;
            if (l != null) l.onDisconnected(reason);
        });
    }

    /** 通过主线程Handler投递消息接收事件到监听器 */
    private void postMessageReceived(final JSONObject message) {
        mainHandler.post(() -> {
            OnMessageReceivedListener l = messageListenerRef != null ? messageListenerRef.get() : null;
            if (l != null) l.onMessageReceived(message);
        });
    }

    /** 通过主线程Handler投递错误事件到监听器 */
    private void postError(final String message) {
        mainHandler.post(() -> {
            OnErrorListener l = errorListenerRef != null ? errorListenerRef.get() : null;
            if (l != null) l.onError(message);
        });
    }

    /** 通过主线程Handler投递状态变化事件到监听器 */
    private void postStateChanged(final ConnectionState state) {
        mainHandler.post(() -> {
            OnStateChangedListener l = stateChangedListenerRef != null ? stateChangedListenerRef.get() : null;
            if (l != null) l.onStateChanged(state);
        });
    }

    /** 连接成功监听器接口 */
    public interface OnConnectedListener { void onConnected(int clientId); }
    /** 断开连接监听器接口 */
    public interface OnDisconnectedListener { void onDisconnected(String reason); }
    /** 消息接收监听器接口 */
    public interface OnMessageReceivedListener { void onMessageReceived(JSONObject message); }
    /** 错误监听器接口 */
    public interface OnErrorListener { void onError(String message); }
    /** 连接状态变化监听器接口 */
    public interface OnStateChangedListener { void onStateChanged(ConnectionState state); }

    /** 设置连接成功监听器，使用WeakReference持有 */
    public void setOnConnectedListener(OnConnectedListener listener) { this.connectedListenerRef = new WeakReference<>(listener); }
    /** 设置断开连接监听器，使用WeakReference持有 */
    public void setOnDisconnectedListener(OnDisconnectedListener listener) { this.disconnectedListenerRef = new WeakReference<>(listener); }
    /** 设置消息接收监听器，使用WeakReference持有 */
    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) { this.messageListenerRef = new WeakReference<>(listener); }
    /** 设置错误监听器，使用WeakReference持有 */
    public void setOnErrorListener(OnErrorListener listener) { this.errorListenerRef = new WeakReference<>(listener); }
    /** 设置连接状态变化监听器，使用WeakReference持有 */
    public void setOnStateChangedListener(OnStateChangedListener listener) { this.stateChangedListenerRef = new WeakReference<>(listener); }

    /** 获取当前连接状态 */
    public ConnectionState getState() { return state; }
    /** 是否已连接（包含CONNECTED和AUTHENTICATED状态） */
    public boolean isConnected() { return state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED; }
    /** 是否已认证（收到WELCOME消息后） */
    public boolean isAuthenticated() { return state == ConnectionState.AUTHENTICATED; }
    /** 获取服务器分配的客户端ID */
    public int getClientId() { return clientId; }
    /** 获取玩家名称 */
    public String getPlayerName() { return playerName; }
    /** 获取主机端IP地址 */
    public String getServerHost() { return serverHost; }
    /** 获取主机端端口号 */
    public int getServerPort() { return serverPort; }
    /** 设置玩家名称 */
    public void setPlayerName(String name) { this.playerName = name; }
    /** 设置P2P令牌，用于重连时识别同一客户端 */
    public void setPeerToken(String token) { this.peerToken = token != null ? token : ""; }
    /** 获取P2P令牌 */
    public String getPeerToken() { return peerToken; }
    /** 设置通信协议版本号 */
    public void setProtocolVersion(int version) { this.protocolVersion = version; }

    /**
     * 设置重连策略参数。
     *
     * @param maxAttempts   最大重连尝试次数，最小为0
     * @param baseIntervalMs 重连基础间隔（毫秒），最小为500ms
     * @param maxIntervalMs  重连最大间隔（毫秒），不小于baseIntervalMs
     */
    public void setReconnectPolicy(int maxAttempts, long baseIntervalMs, long maxIntervalMs) {
        this.maxReconnectAttempts = Math.max(0, maxAttempts);
        this.reconnectInterval = Math.max(500L, baseIntervalMs);
        this.maxReconnectInterval = Math.max(this.reconnectInterval, maxIntervalMs);
    }

    /**
     * 释放客户端资源，断开连接并关闭发送线程池。
     * <p>
     * 调用后单例实例会被清空，需要重新获取。
     */
    public void release() {
        disconnect();
        sendExecutor.shutdownNow();
        instance = null;
        appContext = null;
    }

    /** 单例实例 */
    private static GameSocketClient instance;
    /** 应用级Context，用于获取OkHttpClient等全局资源 */
    private static Context appContext;

    /**
     * 获取客户端单例实例（无Context）。
     *
     * @return GameSocketClient单例
     */
    public static synchronized GameSocketClient getInstance() {
        if (instance == null) instance = new GameSocketClient();
        return instance;
    }

    /**
     * 获取客户端单例实例（带Context）。
     * <p>
     * 首次调用时会保存Context引用，用于WebSocket模式下的OkHttpClientProvider。
     *
     * @param context Android上下文
     * @return GameSocketClient单例
     */
    public static synchronized GameSocketClient getInstance(Context context) {
        if (instance == null) {
            instance = new GameSocketClient();
            if (context != null) {
                appContext = context.getApplicationContext();
            }
        }
        return instance;
    }
}

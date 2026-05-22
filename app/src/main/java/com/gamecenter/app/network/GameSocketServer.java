package com.gamecenter.app.network;

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
import java.lang.ref.WeakReference;
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

/**
 * 游戏Socket服务端，负责管理多人游戏中的主机端网络通信。
 *
 * <p>打个比方：这个类就像一个"会议室管理员"，负责开门迎客（接受客户端连接）、
 * 维持秩序（心跳检测）、传达消息（广播和转发）、以及关门送客（断开连接）。
 * 会议室最多容纳4个人（MAX_CLIENTS），管理员会确保每个人都有座位号（clientId）。</p>
 *
 * <p>在网络模块中的角色：这是创建房间一方的"网络管家"，与 {@link GameSocketClient} 互为对偶。
 * GameSocketClient 是"打电话的人"，而 GameSocketServer 是"接电话的人"。
 * 服务端负责管理所有客户端的连接，并将消息在客户端之间转发。</p>
 * <p>
 * 支持三种连接模式：
 * <ul>
 *   <li>局域网直连模式：通过 {@link ServerSocket} 监听端口，客户端直接TCP连接</li>
 *   <li>云中转（Relay）模式：通过HTTP轮询与中转服务器通信，适用于无法直连的场景</li>
 *   <li>WebSocket模式：通过 {@link WebSocketHostHelper} 与WebSocket服务器通信</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link WeakReference} 持有监听器，避免Activity/Fragment销毁后内存泄漏。
 *       WeakReference就像"弱绳子"，当Activity被销毁时绳子会自动断开，不会阻止回收。</li>
 *   <li>所有回调通过 {@link Handler} 投递到主线程，确保UI操作线程安全</li>
 *   <li>发送操作使用独立线程池，避免阻塞读取线程</li>
 *   <li>客户端连接数上限为 {@link #MAX_CLIENTS}（4人）</li>
 * </ul>
 */
public class GameSocketServer implements WebSocketHostHelper.WsHostCallback {

    private static final String TAG = "GameSocketServer";

    /** 最大客户端连接数，即游戏房间人数上限。
     *  就像会议室最多能坐4个人，超过就不让进了。 */
    private static final int MAX_CLIENTS = 4;

    /** 心跳超时阈值（毫秒），超过此时间未收到客户端消息则判定超时。
     *  就像30秒内没听到对方说话，就怀疑电话可能断了。 */
    private static final long HEARTBEAT_TIMEOUT = 30000L;

    /** 心跳检查间隔（毫秒），定期扫描所有客户端的最后心跳时间。
     *  每2秒检查一次，看看有没有人"失联"。 */
    private static final long HEARTBEAT_CHECK_INTERVAL = 2000L;

    private ServerSocket serverSocket;
    private ExecutorService clientThreadPool;
    private ExecutorService sendExecutor;
    private ScheduledExecutorService heartbeatScheduler;
    /** 客户端连接映射表，key为clientId，线程安全。
     *  就像一本"签到簿"，记录每个座位号对应的客户端连接。 */
    private final ConcurrentHashMap<Integer, ClientConnection> clients = new ConcurrentHashMap<>();
    /** 自增的客户端ID计数器，每来一个新客户端就+1 */
    private int nextClientId = 1;

    private int serverPort;
    private volatile boolean isRunning = false;
    /** 主线程Handler，用于将回调投递到主线程 */
    private final Handler mainHandler;
    private final Context context;

    /** 使用WeakReference持有监听器，防止Activity销毁后内存泄漏 */
    private WeakReference<OnClientConnectedListener> connectedListenerRef;
    private WeakReference<OnClientDisconnectedListener> disconnectedListenerRef;
    private WeakReference<OnMessageReceivedListener> messageListenerRef;
    private WeakReference<OnErrorListener> errorListenerRef;

    private ScheduledFuture<?> heartbeatCheckTask;

    /** 是否处于云中转模式 */
    private volatile boolean relayMode = false;
    /** 是否正在进行云中转轮询 */
    private volatile boolean relayPolling = false;
    private String relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL;
    /** 云中转房间码 */
    private String relayRoomCode = "";
    /** 云中转主机令牌，用于身份验证 */
    private String relayHostToken = "";
    /** 云中转轮询线程 */
    private Thread relayPollThread;
    /** 已知的云中转客户端集合，key为clientId，value为Boolean占位 */
    private final ConcurrentHashMap<Integer, Boolean> relayKnownClients = new ConcurrentHashMap<>();

    /** WebSocket主机端辅助类，封装WebSocket模式下的通信逻辑 */
    private final WebSocketHostHelper wsHelper;

    /**
     * 客户端连接封装类，管理单个客户端的Socket连接、读写线程和心跳状态。
     *
     * <p>打个比方：每个ClientConnection就像一个"专属客服"，专门为一个客户端服务，
     * 负责听客户说话（读取消息）、回答客户问题（发送消息）、记录客户最后活跃时间（心跳）。</p>
     * <p>
     * 每个客户端连接创建时会自动启动读取线程，持续监听客户端发来的消息。
     * 发送操作通过独立的 {@link #sendExecutor} 异步执行，避免阻塞读取线程。
     */
    private class ClientConnection {
        final int clientId;
        final Socket socket;
        final PrintWriter writer;
        final BufferedReader reader;
        /** 读取线程，持续从Socket输入流读取消息 */
        final Thread readThread;
        /** 最后一次收到心跳/消息的时间戳，用于超时检测 */
        volatile long lastHeartbeat;
        /** 客户端是否已完成JOIN认证 */
        volatile boolean authenticated = false;
        /** 客户端玩家名称 */
        String playerName = "";
        /** 客户端的P2P令牌，用于重连识别 */
        String peerToken = "";
        /** 连接是否已关闭的标志 */
        volatile boolean closed = false;

        /**
         * 创建客户端连接，初始化输入输出流并启动读取线程。
         *
         * @param clientId 分配的客户端ID
         * @param socket   客户端的Socket连接
         * @throws IOException 如果获取输入输出流失败
         */
        ClientConnection(int clientId, Socket socket) throws IOException {
            this.clientId = clientId;
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.lastHeartbeat = System.currentTimeMillis();
            // 启用TCP KeepAlive，让操作系统层面检测死连接
            this.socket.setKeepAlive(true);
            // 禁用Nagle算法，减少小包延迟
            this.socket.setTcpNoDelay(true);
            this.readThread = new Thread(() -> readMessages());
            this.readThread.setName("ClientReader-" + clientId);
            this.readThread.start();
        }

        /**
         * 异步发送消息到客户端。
         * <p>
         * 消息通过 {@link #sendExecutor} 线程池异步发送，避免阻塞调用线程。
         * 如果连接已关闭或线程池已关闭，则返回false。
         *
         * @param message 要发送的字符串消息
         * @return true表示消息已提交到发送队列，false表示发送失败
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

        /**
         * 同步发送消息到客户端（在发送线程池中执行）。
         * <p>
         * 使用synchronized保证同一客户端的消息发送顺序，避免多线程并发写入导致数据错乱。
         *
         * @param message 要发送的字符串消息
         */
        private void sendNow(String message) {
            try {
                if (!closed && !socket.isClosed()) {
                    synchronized (this) {
                        writer.println(message);
                        if (writer.checkError()) {
                            Log.w(TAG, "Send warning to client " + clientId + ": writer reported an error");
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Send warning to client " + clientId + ": " + e.getMessage());
            }
        }

        /**
         * 发送JSON格式的消息到客户端。
         *
         * @param json 要发送的JSONObject
         * @return true表示消息已提交到发送队列，false表示发送失败
         */
        boolean sendJSON(JSONObject json) {
            return send(json.toString());
        }

        /**
         * 持续读取客户端消息的循环方法，在独立线程中运行。
         * <p>
         * 每收到一行消息即更新心跳时间并交给 {@link #handleMessage} 处理。
         * 读取结束或异常时自动关闭连接。
         */
        private void readMessages() {
            String line;
            try {
                while ((line = reader.readLine()) != null && isRunning) {
                    // 收到任何消息都视为客户端存活，更新心跳时间
                    lastHeartbeat = System.currentTimeMillis();
                    handleMessage(this, line);
                }
            } catch (IOException e) {
                Log.d(TAG, "Client " + clientId + " read error: " + e.getMessage());
            } finally {
                // 读取结束（连接断开或异常），关闭连接
                closeConnection(this);
            }
        }

        /** 关闭客户端连接，释放Reader、Writer和Socket资源 */
        void close() {
            if (closed) return;
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

    /** 无Context的构造方法，WebSocket模式无法使用OkHttpClientProvider */
    public GameSocketServer() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = null;
        this.wsHelper = new WebSocketHostHelper(this, null, relayKnownClients, MAX_CLIENTS);
    }

    /**
     * 带Context的构造方法，推荐使用此构造方法以支持WebSocket模式下的OkHttpClientProvider。
     *
     * @param context Android上下文，用于获取OkHttpClient实例
     */
    public GameSocketServer(Context context) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = context != null ? context.getApplicationContext() : null;
        this.wsHelper = new WebSocketHostHelper(this, this.context, relayKnownClients, MAX_CLIENTS);
    }

    /** WebSocket客户端连接回调，委托给主线程投递方法 */
    @Override
    public void onClientConnected(int clientId, String ip) {
        postClientConnected(clientId, ip);
    }

    /** WebSocket客户端断开回调 */
    @Override
    public void onClientDisconnected(int clientId, String reason) {
        postClientDisconnected(clientId, reason);
    }

    /** WebSocket消息接收回调 */
    @Override
    public void onMessageReceived(int clientId, JSONObject message) {
        postMessageReceived(clientId, message);
    }

    /** WebSocket错误回调 */
    @Override
    public void onError(String message) {
        postError(message);
    }

    /** WebSocket请求停止回调，触发服务端停止 */
    @Override
    public void onRequestStop() {
        stop();
    }

    /**
     * 启动局域网直连模式的Socket服务端。
     * <p>
     * 在指定端口创建 {@link ServerSocket}，初始化线程池和心跳检查，
     * 并启动连接接受线程等待客户端连接。
     *
     * @param port 监听端口号
     * @return true表示启动成功，false表示启动失败或服务端已在运行
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
            clientThreadPool = Executors.newFixedThreadPool(MAX_CLIENTS, r -> {
                Thread t = new Thread(r, "GC-Network-Client");
                t.setDaemon(true);
                return t;
            });
            sendExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "GC-Network-Send");
                thread.setDaemon(true);
                return thread;
            });
            heartbeatScheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "GC-Network-Heartbeat");
                t.setDaemon(true);
                return t;
            });
            startHeartbeatCheck();
            new Thread(this::acceptConnections, "GC-Network-Accept").start();
            Log.d(TAG, "Server started on port " + port);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动云中转（Relay）模式。
     * <p>
     * 向中转服务器发送创建房间请求，获取房间码和主机令牌后，
     * 启动轮询线程持续拉取来自客户端的消息。
     *
     * @param baseUrl 中转服务器的基础URL，为空则使用默认地址
     * @return true表示房间创建成功，false表示创建失败
     */
    public boolean startRelay(String baseUrl) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }
        try {
            relayMode = true;
            relayBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl.trim() : RelayHttpClient.DEFAULT_BASE_URL;
            // 向中转服务器发送创建房间请求
            JSONObject body = new JSONObject();
            body.put("app", "GameMatrixApp");
            body.put("game", "GameMatrixApp");
            JSONObject response = RelayHttpClient.post(relayBaseUrl, "/create", body, 10000);
            relayRoomCode = response.getString("roomCode");
            relayHostToken = response.getString("hostToken");
            isRunning = true;
            relayPolling = true;
            relayKnownClients.clear();
            sendExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "GC-Network-RelaySend");
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
     * 启动WebSocket模式。
     * <p>
     * 委托给 {@link WebSocketHostHelper} 处理WebSocket连接的建立和管理。
     *
     * @param wsUrl WebSocket服务器的URL
     * @return true表示启动成功，false表示启动失败
     */
    public boolean startWebSocket(String wsUrl) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }
        boolean started = wsHelper.startWebSocket(wsUrl);
        if (started) {
            isRunning = true;
        }
        return started;
    }

    /**
     * 停止服务端，释放所有资源。
     * <p>
     * 根据当前运行模式执行不同的清理逻辑：
     * <ul>
     *   <li>WebSocket模式：停止WebSocket辅助类</li>
     *   <li>云中转模式：通知中转服务器关闭房间，停止轮询线程</li>
     *   <li>直连模式：关闭所有客户端连接和ServerSocket</li>
     * </ul>
     * 所有模式的公共资源（心跳调度器、线程池）都会被清理。
     */
    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        relayPolling = false;

        if (wsHelper.isWebSocketMode()) {
            wsHelper.stop();
        }

        if (relayMode) {
            // 异步通知中转服务器关闭房间，避免阻塞主线程
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
                } catch (Exception e) { Log.w(TAG, "close relay room: " + e.getMessage()); }
            }, "GameRelayHostClose").start();
            if (relayPollThread != null) { relayPollThread.interrupt(); relayPollThread = null; }
            relayKnownClients.clear();
            relayMode = false;
            relayRoomCode = "";
            relayHostToken = "";
        }

        // 清理公共资源
        if (heartbeatCheckTask != null) heartbeatCheckTask.cancel(true);
        if (heartbeatScheduler != null) heartbeatScheduler.shutdown();
        for (ClientConnection client : clients.values()) client.close();
        clients.clear();
        if (clientThreadPool != null) clientThreadPool.shutdown();
        // 发送线程池使用shutdownNow，因为可能还有阻塞的写入操作
        if (sendExecutor != null) { sendExecutor.shutdownNow(); sendExecutor = null; }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) { Log.w(TAG, "close server socket: " + e.getMessage()); }
        Log.d(TAG, "Server stopped");
    }

    /**
     * 启动云中转轮询线程。
     * <p>
     * 轮询线程以长轮询方式（超时35秒）持续从中转服务器拉取消息。
     * 拉取失败时等待1.5秒后重试，避免频繁请求。
     */
    private void startRelayPolling() {
        relayPollThread = new Thread(() -> {
            while (relayPolling && isRunning) {
                try {
                    // 使用长轮询，超时35秒，减少无效请求
                    JSONObject response = RelayHttpClient.post(relayBaseUrl, "/poll", relayBaseBody(), 35000);
                    JSONArray messages = response.optJSONArray("messages");
                    if (messages != null) {
                        for (int i = 0; i < messages.length(); i++) {
                            JSONObject item = messages.optJSONObject(i);
                            if (item == null) continue;
                            int clientId = item.optInt("clientId", -1);
                            JSONObject payload = item.optJSONObject("payload");
                            // 跳过无效的消息（clientId不合法或payload为空）
                            if (clientId <= 0 || payload == null) continue;
                            handleRelayMessage(clientId, payload);
                        }
                    }
                } catch (Exception e) {
                    if (relayPolling && isRunning) {
                        Log.w(TAG, "Relay poll failed: " + e.getMessage());
                        // 轮询失败后等待1.5秒再重试，避免连续失败时频繁请求
                        try { Thread.sleep(1500L); } catch (InterruptedException ignored) { return; }
                    }
                }
            }
        }, "GameRelayHostPoll");
        relayPollThread.setDaemon(true);
        relayPollThread.start();
    }

    /**
     * 构建云中转请求的基础请求体，包含房间码、角色和令牌。
     *
     * @return 包含认证信息的JSONObject
     * @throws JSONException 如果构建JSON失败
     */
    private JSONObject relayBaseBody() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("roomCode", relayRoomCode);
        body.put("role", "host");
        body.put("token", relayHostToken);
        return body;
    }

    /**
     * 处理从云中转服务器收到的客户端消息。
     * <p>
     * 如果是DISCONNECT类型，从已知客户端中移除并通知断开。
     * 如果是新客户端，添加到已知客户端集合并通知连接。
     * 所有消息都会附加远程IP和clientId信息后交给消息处理逻辑。
     *
     * @param clientId 客户端ID
     * @param json     消息内容
     */
    private void handleRelayMessage(int clientId, JSONObject json) {
        if ("DISCONNECT".equals(json.optString("type", ""))) {
            relayKnownClients.remove(clientId);
            postClientDisconnected(clientId, json.optString("reason", "连接关闭"));
            return;
        }
        // 新客户端首次发消息时，添加到已知客户端集合
        if (!relayKnownClients.containsKey(clientId)) {
            relayKnownClients.put(clientId, true);
            postClientConnected(clientId, "云中转");
        }
        try {
            // 附加元数据，标识消息来源为中转模式
            json.put("_remoteIp", "relay");
            json.put("_clientId", clientId);
        } catch (JSONException ignored) { Log.w(TAG, "JSON error: " + ignored.getMessage()); }
        handleIncomingJson(clientId, json);
    }

    /**
     * 接受客户端连接的循环方法，在独立线程中运行。
     * <p>
     * 持续调用 {@link ServerSocket#accept()} 等待客户端连接，
     * 服务端停止或accept异常时退出循环。
     */
    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewClient(clientSocket);
            } catch (IOException e) {
                if (isRunning) Log.e(TAG, "Accept error: " + e.getMessage());
                break;
            }
        }
    }

    /**
     * 处理新客户端连接。
     * <p>
     * 检查客户端数量是否已达上限，未达上限则为客户端分配ID、创建连接对象、
     * 发送WELCOME消息并通知连接监听器。
     *
     * @param socket 客户端的Socket连接
     */
    private void handleNewClient(Socket socket) {
        if (clients.size() >= MAX_CLIENTS) {
            // 客户端数量已达上限，直接关闭新连接
            try { socket.close(); } catch (IOException e) { Log.w(TAG, "close socket: " + e.getMessage()); }
            return;
        }
        try {
            int clientId = nextClientId++;
            ClientConnection client = new ClientConnection(clientId, socket);
            clients.put(clientId, client);
            // 发送欢迎消息，告知客户端其ID和房间人数上限
            JSONObject welcome = new JSONObject();
            welcome.put("type", "WELCOME");
            welcome.put("clientId", clientId);
            welcome.put("maxClients", MAX_CLIENTS);
            client.sendJSON(welcome);
            postClientConnected(clientId, socket.getInetAddress().getHostAddress());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to setup client: " + e.getMessage());
            try { socket.close(); } catch (IOException ex) { Log.w(TAG, "close socket: " + ex.getMessage()); }
        }
    }

    /**
     * 解析客户端发来的原始字符串消息为JSON并处理。
     *
     * @param client  发送消息的客户端连接
     * @param message 原始字符串消息
     */
    private void handleMessage(ClientConnection client, String message) {
        try {
            JSONObject json = new JSONObject(message);
            handleIncomingJson(client.clientId, json);
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON from client " + client.clientId + ": " + message);
        }
    }

    /**
     * 处理客户端发来的JSON消息，根据消息类型执行不同逻辑。
     * <p>
     * 支持的消息类型：
     * <ul>
     *   <li>PING：更新心跳时间并回复PONG</li>
     *   <li>JOIN：记录玩家名称和令牌，标记为已认证，附加远程IP后转发给监听器</li>
     *   其他类型：直接转发给消息监听器
     * </ul>
     *
     * @param clientId 客户端ID
     * @param json     消息内容
     */
    private void handleIncomingJson(int clientId, JSONObject json) {
        String type = json.optString("type", "");
        try {
            switch (type) {
                case "PING":
                    ClientConnection client = clients.get(clientId);
                    if (client != null) client.lastHeartbeat = System.currentTimeMillis();
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
                        // 附加客户端的远程IP地址，供上层业务使用
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
     * 启动心跳检查定时任务。
     * <p>
     * 定期扫描所有客户端的最后心跳时间，如果超过 {@link #HEARTBEAT_TIMEOUT}
     * 则记录警告日志。当前实现仅记录日志，不主动断开客户端。
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

    /** 关闭客户端连接，使用默认原因"连接关闭" */
    private void closeConnection(ClientConnection client) { closeConnection(client, "连接关闭"); }

    /**
     * 关闭客户端连接并通知断开监听器。
     * <p>
     * 使用 {@link ConcurrentHashMap#remove} 的原子操作确保同一客户端不会被重复移除。
     * 只有在客户端确实从映射表中移除且服务端仍在运行时，才通知断开监听器。
     *
     * @param client 要关闭的客户端连接
     * @param reason 断开原因
     */
    private void closeConnection(ClientConnection client, String reason) {
        if (client == null) return;
        boolean removed = clients.remove(client.clientId, client);
        client.close();
        if (removed && isRunning) postClientDisconnected(client.clientId, reason);
    }

    /**
     * 向所有已连接的客户端广播消息。
     * <p>
     * 根据当前运行模式选择不同的广播方式：
     * <ul>
     *   <li>WebSocket模式：通过WebSocket服务器广播</li>
     *   <li>云中转模式：通过中转服务器广播</li>
     *   <li>直连模式：遍历所有客户端逐个发送</li>
     * </ul>
     *
     * @param json 要广播的JSON消息
     */
    public void broadcast(JSONObject json) {
        if (json == null) return;
        if (wsHelper.isWebSocketMode()) { wsHelper.webSocketBroadcast(json); return; }
        if (relayMode) { relaySendAll(json); return; }
        String message = json.toString();
        try { for (ClientConnection client : clients.values()) if (client != null) client.send(message); }
        catch (Exception e) { Log.e(TAG, "broadcast error: " + e.getMessage()); }
    }

    /**
     * 向指定客户端发送消息。
     * <p>
     * 根据当前运行模式选择不同的发送方式。
     *
     * @param clientId 目标客户端ID
     * @param json     要发送的JSON消息
     */
    public void sendTo(int clientId, JSONObject json) {
        try {
            if (wsHelper.isWebSocketMode()) { wsHelper.webSocketSendTo(clientId, json); return; }
            if (relayMode) { relaySendTo(clientId, json); return; }
            ClientConnection client = clients.get(clientId);
            if (client != null) client.sendJSON(json);
        } catch (Exception e) { Log.e(TAG, "sendTo error: " + e.getMessage()); }
    }

    /** 通过云中转向所有客户端广播消息 */
    private void relaySendAll(JSONObject json) { relaySend("all", json); }

    /**
     * 通过云中转向指定客户端发送消息。
     *
     * @param clientId 目标客户端ID，必须大于0
     * @param json     要发送的JSON消息
     */
    private void relaySendTo(int clientId, JSONObject json) {
        if (clientId <= 0) return;
        relaySend(String.valueOf(clientId), json);
    }

    /**
     * 通过云中转发送消息的内部方法。
     * <p>
     * 通过发送线程池异步执行，避免阻塞调用线程。
     *
     * @param to   目标标识，"all"表示广播，数字字符串表示指定客户端
     * @param json 要发送的JSON消息
     */
    private void relaySend(String to, JSONObject json) {
        ExecutorService executor = sendExecutor;
        if (executor == null || executor.isShutdown()) return;
        try { executor.execute(() -> relaySendNow(to, json)); }
        catch (RejectedExecutionException e) { Log.e(TAG, "relay writer rejected task", e); }
    }

    /**
     * 同步执行云中转消息发送（在发送线程池中调用）。
     *
     * @param to   目标标识
     * @param json 要发送的JSON消息
     */
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
     * 向所有客户端广播游戏结束消息。
     *
     * @param winnerIndex 获胜玩家的索引
     */
    public void broadcastGameOver(int winnerIndex) {
        JSONObject json = new JSONObject();
        try { json.put("type", "GAME_OVER"); json.put("winnerIndex", winnerIndex); broadcast(json); }
        catch (JSONException e) { Log.e(TAG, "Error broadcasting game over: " + e.getMessage()); }
    }

    /** 通过主线程Handler投递客户端连接事件到监听器 */
    private void postClientConnected(final int clientId, final String ip) {
        mainHandler.post(() -> {
            OnClientConnectedListener l = connectedListenerRef != null ? connectedListenerRef.get() : null;
            if (l != null) l.onClientConnected(clientId, ip);
        });
    }

    /** 通过主线程Handler投递客户端断开事件到监听器 */
    private void postClientDisconnected(final int clientId, final String reason) {
        mainHandler.post(() -> {
            OnClientDisconnectedListener l = disconnectedListenerRef != null ? disconnectedListenerRef.get() : null;
            if (l != null) l.onClientDisconnected(clientId, reason);
        });
    }

    /** 通过主线程Handler投递消息接收事件到监听器 */
    private void postMessageReceived(final int clientId, final JSONObject message) {
        mainHandler.post(() -> {
            OnMessageReceivedListener l = messageListenerRef != null ? messageListenerRef.get() : null;
            if (l != null) l.onMessageReceived(clientId, message);
        });
    }

    /** 通过主线程Handler投递错误事件到监听器 */
    private void postError(final String message) {
        mainHandler.post(() -> {
            OnErrorListener l = errorListenerRef != null ? errorListenerRef.get() : null;
            if (l != null) l.onError(message);
        });
    }

    /** 客户端连接监听器接口 */
    public interface OnClientConnectedListener { void onClientConnected(int clientId, String ip); }
    /** 客户端断开监听器接口 */
    public interface OnClientDisconnectedListener { void onClientDisconnected(int clientId, String reason); }
    /** 消息接收监听器接口 */
    public interface OnMessageReceivedListener { void onMessageReceived(int clientId, JSONObject message); }
    /** 错误监听器接口 */
    public interface OnErrorListener { void onError(String message); }

    /** 设置客户端连接监听器，使用WeakReference持有 */
    public void setOnClientConnectedListener(OnClientConnectedListener listener) { this.connectedListenerRef = new WeakReference<>(listener); }
    /** 设置客户端断开监听器，使用WeakReference持有 */
    public void setOnClientDisconnectedListener(OnClientDisconnectedListener listener) { this.disconnectedListenerRef = new WeakReference<>(listener); }
    /** 设置消息接收监听器，使用WeakReference持有 */
    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) { this.messageListenerRef = new WeakReference<>(listener); }
    /** 设置错误监听器，使用WeakReference持有 */
    public void setOnErrorListener(OnErrorListener listener) { this.errorListenerRef = new WeakReference<>(listener); }

    /** 服务端是否正在运行 */
    public boolean isRunning() { return isRunning; }
    /** 获取服务端监听端口号 */
    public int getServerPort() { return serverPort; }
    /** 是否处于云中转模式 */
    public boolean isRelayMode() { return relayMode; }
    /** 是否处于WebSocket模式 */
    public boolean isWebSocketMode() { return wsHelper.isWebSocketMode(); }
    /** 获取云中转房间码 */
    public String getRelayRoomCode() { return relayRoomCode; }
    /** 获取已连接的客户端数量，根据模式从不同数据源获取 */
    public int getConnectedClientCount() { return wsHelper.isWebSocketMode() || relayMode ? relayKnownClients.size() : clients.size(); }

    /**
     * 断开指定客户端的连接。
     * <p>
     * 根据当前运行模式执行不同的断开逻辑：
     * <ul>
     *   <li>WebSocket模式：委托给WebSocket辅助类</li>
     *   <li>云中转模式：发送ERROR消息后移除客户端</li>
     *   <li>直连模式：关闭Socket连接</li>
     * </ul>
     *
     * @param clientId 要断开的客户端ID
     * @param reason   断开原因
     */
    public void disconnectClient(int clientId, String reason) {
        if (wsHelper.isWebSocketMode()) { wsHelper.disconnectClient(clientId, reason); return; }
        if (relayMode) {
            // 向客户端发送ERROR消息通知其被断开
            try { JSONObject error = new JSONObject(); error.put("type", "ERROR"); error.put("message", reason != null ? reason : "连接关闭"); relaySendTo(clientId, error); }
            catch (JSONException ignored) { Log.w(TAG, "JSON error: " + ignored.getMessage()); }
            relayKnownClients.remove(clientId);
            postClientDisconnected(clientId, reason != null ? reason : "连接关闭");
            return;
        }
        ClientConnection client = clients.get(clientId);
        if (client != null) closeConnection(client, reason != null ? reason : "连接关闭");
    }

    /** 获取最大客户端连接数 */
    public int getMaxClients() { return MAX_CLIENTS; }
    /** 客户端数量是否已达上限 */
    public boolean isFull() { return wsHelper.isWebSocketMode() || relayMode ? relayKnownClients.size() >= MAX_CLIENTS : clients.size() >= MAX_CLIENTS; }
}

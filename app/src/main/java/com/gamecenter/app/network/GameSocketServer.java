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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class GameSocketServer {

    private static final String TAG = "GameSocketServer";

    private static final boolean DEBUG_NETWORK = true;
    private static final String LOG_PREFIX = "[GAME-WS]";

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

    private static final int MAX_CLIENTS = 4;
    private static final long HEARTBEAT_TIMEOUT = 30000L;
    private static final long HEARTBEAT_CHECK_INTERVAL = 2000L;
    private static final long WS_HEARTBEAT_INTERVAL = 10000L;
    private static final long WS_HEARTBEAT_TIMEOUT = 45000L;
    private static final int WS_MAX_MISSED_PONGS = 2;

    private ServerSocket serverSocket;
    private ExecutorService clientThreadPool;
    private ExecutorService sendExecutor;
    private ScheduledExecutorService heartbeatScheduler;
    private final ConcurrentHashMap<Integer, ClientConnection> clients = new ConcurrentHashMap<>();
    private int nextClientId = 1;

    private synchronized int generateTempClientId() {
        int id = nextClientId++;
        while (relayKnownClients.containsKey(id)) {
            id = nextClientId++;
        }
        return id;
    }

    private int serverPort;
    private volatile boolean isRunning = false;
    private final Handler mainHandler;
    private final Context context;

    private OnClientConnectedListener connectedListener;
    private OnClientDisconnectedListener disconnectedListener;
    private OnMessageReceivedListener messageListener;
    private OnErrorListener errorListener;

    private ScheduledFuture<?> heartbeatCheckTask;
    private volatile boolean relayMode = false;
    private volatile boolean relayPolling = false;
    private String relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL;
    private String relayRoomCode = "";
    private String relayHostToken = "";
    private Thread relayPollThread;
    private final ConcurrentHashMap<Integer, Boolean> relayKnownClients = new ConcurrentHashMap<>();

    private volatile boolean webSocketMode = false;
    private WebSocket webSocket;
    private OkHttpClient okHttpClient;
    private String wsUrl;
    private int consecutiveMissedPongs = 0;
    private volatile long lastWsMessageTime = 0L;
    private ScheduledExecutorService wsHeartbeatScheduler;
    private ScheduledFuture<?> wsHeartbeatTask;

    private class ClientConnection {
        final int clientId;
        final Socket socket;
        final PrintWriter writer;
        final BufferedReader reader;
        final Thread readThread;
        volatile long lastHeartbeat;
        volatile boolean authenticated = false;
        String playerName = "";
        String peerToken = "";
        volatile boolean closed = false;

        ClientConnection(int clientId, Socket socket) throws IOException {
            this.clientId = clientId;
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.lastHeartbeat = System.currentTimeMillis();
            this.socket.setKeepAlive(true);
            this.socket.setTcpNoDelay(true);
            this.readThread = new Thread(() -> readMessages());
            this.readThread.setName("ClientReader-" + clientId);
            this.readThread.start();
        }

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
                }
            } catch (Exception e) {
                Log.w(TAG, "Send warning to client " + clientId + ": " + e.getMessage());
            }
        }

        boolean sendJSON(JSONObject json) {
            return send(json.toString());
        }

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

    public GameSocketServer() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = null;
    }

    public GameSocketServer(Context context) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public boolean start(int port) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }
        try {
            serverSocket = new ServerSocket(port);
            serverPort = port;
            isRunning = true;
            clientThreadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
            sendExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ServerSocketWriter");
                thread.setDaemon(true);
                return thread;
            });
            heartbeatScheduler = Executors.newScheduledThreadPool(1);
            startHeartbeatCheck();
            new Thread(this::acceptConnections).start();
            Log.d(TAG, "Server started on port " + port);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server: " + e.getMessage());
            return false;
        }
    }

    public boolean startRelay(String baseUrl) {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return false;
        }
        try {
            relayMode = true;
            relayBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl.trim() : RelayHttpClient.DEFAULT_BASE_URL;
            JSONObject body = new JSONObject();
            body.put("app", "GameCenterApp");
            body.put("game", "GameCenterApp");
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
        }, "GameWebSocketHostConnect").start();

        return true;
    }

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
        Request request = new Request.Builder().url(wsUrl).build();
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
                logEvent("MESSAGE", wsUrl != null ? "ws" : "-", -1, "incoming-text");
                handleWebSocketMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                lastWsMessageTime = System.currentTimeMillis();
                consecutiveMissedPongs = 0;
                logEvent("MESSAGE", wsUrl != null ? "ws" : "-", -1, "incoming-binary");
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
                if (isRunning) {
                    postError("WebSocket 连接已关闭: " + reason);
                    stop();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                if (isRunning) {
                    postError("WebSocket 连接失败: " + t.getMessage());
                    stop();
                }
            }
        });
    }

    private void handleWebSocketMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String type = json.optString("type", "");

            if ("PONG".equals(type)) return;
            if ("WELCOME".equals(type)) return;
            if ("ROOM_CREATED".equals(type)) return;
            if ("ROOM_STATE".equals(type)) return;
            if ("PING".equals(type)) return;

            if ("JOIN".equals(type)) {
                int clientId = json.optInt("clientId", -1);
                if (clientId <= 0) clientId = generateTempClientId();
                if (!relayKnownClients.containsKey(clientId)) {
                    relayKnownClients.put(clientId, true);
                    postClientConnected(clientId, "websocket");
                }
            }

            if ("DISCONNECT".equals(type) || "CLIENT_DISCONNECTED".equals(type)) {
                int clientId = json.optInt("clientId", -1);
                if (clientId > 0) {
                    relayKnownClients.remove(clientId);
                    postClientDisconnected(clientId, json.optString("reason", "连接关闭"));
                }
                return;
            }

            int clientId = json.optInt("clientId", -1);
            if (clientId <= 0) clientId = json.optInt("_clientId", -1);
            if (clientId <= 0) clientId = 1;

            if (!relayKnownClients.containsKey(clientId)) {
                relayKnownClients.put(clientId, true);
                postClientConnected(clientId, "websocket");
            }

            json.put("_remoteIp", "websocket");
            json.put("_clientId", clientId);
            postMessageReceived(clientId, json);
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON from WebSocket: " + text);
        }
    }

    private void startWebSocketHeartbeat() {
        logWs("HEARTBEAT_START", wsUrl, "interval=" + WS_HEARTBEAT_INTERVAL);
        if (wsHeartbeatScheduler != null) wsHeartbeatScheduler.shutdownNow();
        wsHeartbeatScheduler = Executors.newScheduledThreadPool(1);

        wsHeartbeatTask = wsHeartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!webSocketMode || webSocket == null || !isRunning) return;
            long now = System.currentTimeMillis();
            long elapsed = now - lastWsMessageTime;
            if (elapsed < WS_HEARTBEAT_INTERVAL) {
                consecutiveMissedPongs = 0;
                return;
            }
            consecutiveMissedPongs++;
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

    private void stopWebSocketHeartbeat() {
        logWs("HEARTBEAT_STOP", wsUrl, null);
        if (wsHeartbeatTask != null) { wsHeartbeatTask.cancel(true); wsHeartbeatTask = null; }
        if (wsHeartbeatScheduler != null) { wsHeartbeatScheduler.shutdownNow(); wsHeartbeatScheduler = null; }
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        relayPolling = false;

        if (webSocketMode) {
            stopWebSocketHeartbeat();
            webSocketMode = false;
            if (webSocket != null) {
                try { webSocket.close(1000, "Host stopped"); } catch (Exception e) {}
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
                } catch (Exception e) {}
            }, "GameRelayHostClose").start();
            if (relayPollThread != null) { relayPollThread.interrupt(); relayPollThread = null; }
            relayKnownClients.clear();
            relayMode = false;
            relayRoomCode = "";
            relayHostToken = "";
        }

        if (heartbeatCheckTask != null) heartbeatCheckTask.cancel(true);
        if (heartbeatScheduler != null) heartbeatScheduler.shutdown();
        for (ClientConnection client : clients.values()) client.close();
        clients.clear();
        if (clientThreadPool != null) clientThreadPool.shutdown();
        if (sendExecutor != null) { sendExecutor.shutdownNow(); sendExecutor = null; }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
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
                        try { Thread.sleep(1500L); } catch (InterruptedException ignored) { return; }
                    }
                }
            }
        }, "GameRelayHostPoll");
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
        } catch (JSONException ignored) {}
        handleIncomingJson(clientId, json);
    }

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

    private void handleNewClient(Socket socket) {
        if (clients.size() >= MAX_CLIENTS) {
            try { socket.close(); } catch (IOException e) {}
            return;
        }
        try {
            int clientId = nextClientId++;
            ClientConnection client = new ClientConnection(clientId, socket);
            clients.put(clientId, client);
            JSONObject welcome = new JSONObject();
            welcome.put("type", "WELCOME");
            welcome.put("clientId", clientId);
            welcome.put("maxClients", MAX_CLIENTS);
            client.sendJSON(welcome);
            postClientConnected(clientId, socket.getInetAddress().getHostAddress());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to setup client: " + e.getMessage());
            try { socket.close(); } catch (IOException ex) {}
        }
    }

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

    private void closeConnection(ClientConnection client) { closeConnection(client, "连接关闭"); }

    private void closeConnection(ClientConnection client, String reason) {
        if (client == null) return;
        boolean removed = clients.remove(client.clientId, client);
        client.close();
        if (removed && isRunning) postClientDisconnected(client.clientId, reason);
    }

    public void broadcast(JSONObject json) {
        if (json == null) return;
        if (webSocketMode) { webSocketBroadcast(json); return; }
        if (relayMode) { relaySendAll(json); return; }
        String message = json.toString();
        try { for (ClientConnection client : clients.values()) if (client != null) client.send(message); }
        catch (Exception e) { Log.e(TAG, "broadcast error: " + e.getMessage()); }
    }

    private void webSocketBroadcast(JSONObject json) {
        if (webSocket == null) return;
        try { json.put("broadcast", true); webSocket.send(json.toString()); }
        catch (JSONException e) { Log.e(TAG, "WebSocket broadcast error: " + e.getMessage()); }
    }

    public void sendTo(int clientId, JSONObject json) {
        try {
            if (webSocketMode) { webSocketSendTo(clientId, json); return; }
            if (relayMode) { relaySendTo(clientId, json); return; }
            ClientConnection client = clients.get(clientId);
            if (client != null) client.sendJSON(json);
        } catch (Exception e) { Log.e(TAG, "sendTo error: " + e.getMessage()); }
    }

    private void webSocketSendTo(int clientId, JSONObject json) {
        if (webSocket == null) return;
        try {
            json.put("targetClientId", clientId);
            webSocket.send(json.toString());
        } catch (JSONException e) { Log.e(TAG, "WebSocket sendTo error: " + e.getMessage()); }
    }

    private void relaySendAll(JSONObject json) { relaySend("all", json); }

    private void relaySendTo(int clientId, JSONObject json) {
        if (clientId <= 0) return;
        relaySend(String.valueOf(clientId), json);
    }

    private void relaySend(String to, JSONObject json) {
        ExecutorService executor = sendExecutor;
        if (executor == null || executor.isShutdown()) return;
        try { executor.execute(() -> relaySendNow(to, json)); }
        catch (RejectedExecutionException e) { Log.e(TAG, "relay writer rejected task", e); }
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

    public void broadcastGameOver(int winnerIndex) {
        JSONObject json = new JSONObject();
        try { json.put("type", "GAME_OVER"); json.put("winnerIndex", winnerIndex); broadcast(json); }
        catch (JSONException e) { Log.e(TAG, "Error broadcasting game over: " + e.getMessage()); }
    }

    private void postClientConnected(final int clientId, final String ip) {
        mainHandler.post(() -> { if (connectedListener != null) connectedListener.onClientConnected(clientId, ip); });
    }

    private void postClientDisconnected(final int clientId, final String reason) {
        mainHandler.post(() -> { if (disconnectedListener != null) disconnectedListener.onClientDisconnected(clientId, reason); });
    }

    private void postMessageReceived(final int clientId, final JSONObject message) {
        mainHandler.post(() -> { if (messageListener != null) messageListener.onMessageReceived(clientId, message); });
    }

    private void postError(final String message) {
        mainHandler.post(() -> { if (errorListener != null) errorListener.onError(message); });
    }

    public interface OnClientConnectedListener { void onClientConnected(int clientId, String ip); }
    public interface OnClientDisconnectedListener { void onClientDisconnected(int clientId, String reason); }
    public interface OnMessageReceivedListener { void onMessageReceived(int clientId, JSONObject message); }
    public interface OnErrorListener { void onError(String message); }

    public void setOnClientConnectedListener(OnClientConnectedListener listener) { this.connectedListener = listener; }
    public void setOnClientDisconnectedListener(OnClientDisconnectedListener listener) { this.disconnectedListener = listener; }
    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) { this.messageListener = listener; }
    public void setOnErrorListener(OnErrorListener listener) { this.errorListener = listener; }

    public boolean isRunning() { return isRunning; }
    public int getServerPort() { return serverPort; }
    public boolean isRelayMode() { return relayMode; }
    public boolean isWebSocketMode() { return webSocketMode; }
    public String getRelayRoomCode() { return relayRoomCode; }
    public int getConnectedClientCount() { return webSocketMode || relayMode ? relayKnownClients.size() : clients.size(); }

    public void disconnectClient(int clientId, String reason) {
        if (webSocketMode) { relayKnownClients.remove(clientId); postClientDisconnected(clientId, reason != null ? reason : "连接关闭"); return; }
        if (relayMode) {
            try { JSONObject error = new JSONObject(); error.put("type", "ERROR"); error.put("message", reason != null ? reason : "连接关闭"); relaySendTo(clientId, error); }
            catch (JSONException ignored) {}
            relayKnownClients.remove(clientId);
            postClientDisconnected(clientId, reason != null ? reason : "连接关闭");
            return;
        }
        ClientConnection client = clients.get(clientId);
        if (client != null) closeConnection(client, reason != null ? reason : "连接关闭");
    }

    public int getMaxClients() { return MAX_CLIENTS; }
    public boolean isFull() { return webSocketMode || relayMode ? relayKnownClients.size() >= MAX_CLIENTS : clients.size() >= MAX_CLIENTS; }
}

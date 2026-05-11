package com.gamecenter.app.network;

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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class GameSocketClient {

    private static final String TAG = "GameSocketClient";

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

    private static final int CONNECT_TIMEOUT = 5000;
    private static final long HEARTBEAT_INTERVAL = 3000L;
    private static final long HEARTBEAT_TIMEOUT = 30000L;
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 3;
    private static final long DEFAULT_RECONNECT_INTERVAL = 2000L;
    private static final long DEFAULT_MAX_RECONNECT_INTERVAL = 15000L;
    private static final long WS_HEARTBEAT_INTERVAL = 10000L;
    private static final long WS_HEARTBEAT_TIMEOUT = 45000L;
    private static final int WS_MAX_MISSED_PONGS = 2;
    private static final int MAX_PENDING_MESSAGES = 32;

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

    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SocketWriter");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledExecutorService reconnectScheduler;
    private ScheduledFuture<?> reconnectTask;

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

    private volatile boolean webSocketMode = false;
    private volatile boolean isHandlingDisconnection = false;
    private WebSocket webSocket;
    private OkHttpClient okHttpClient;
    private String wsUrl;
    private final ConcurrentLinkedQueue<JSONObject> pendingMessages = new ConcurrentLinkedQueue<>();
    private int consecutiveMissedPongs = 0;

    private Handler mainHandler;
    private OnConnectedListener connectedListener;
    private OnDisconnectedListener disconnectedListener;
    private OnMessageReceivedListener messageListener;
    private OnErrorListener errorListener;
    private OnStateChangedListener stateChangedListener;

    private String playerName = "Player";
    private int clientId = -1;

    public GameSocketClient() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void connectWebSocket(String wsUrl) {
        logWs("CONNECTING", wsUrl, "client connecting");
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
        relayBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl.trim() : RelayHttpClient.DEFAULT_BASE_URL;
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
        } catch (IOException e) {}
        reader = null; writer = null; socket = null;
    }

    private void closeWebSocketQuietly() {
        webSocketMode = false;
        if (webSocket != null) {
            try { webSocket.close(1000, "Disconnect"); } catch (Exception e) {}
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
        if (relayPollThread != null) { relayPollThread.interrupt(); relayPollThread = null; }
        final boolean shouldNotify = relayMode && clientId > 0 && relayClientToken != null && !relayClientToken.isEmpty();
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
                } catch (Exception e) {}
            }, "GameRelayClientDisconnect").start();
        }
    }

    private void doWebSocketConnect() {
        Log.d(TAG, "Connecting WebSocket to " + wsUrl);
        if (okHttpClient == null) {
            if (appContext != null) {
                okHttpClient = OkHttpClientProvider.getInstance(appContext).getWebSocketClient();
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
                logEvent("FAILURE", relayRoomCode, clientId, "websocket-failure");
                if (!manualDisconnect && state != ConnectionState.DISCONNECTED && state != ConnectionState.RECONNECTING) {
                    postError("WebSocket error: " + t.getMessage());
                    handleDisconnection("WebSocket failure");
                }
            }
        });
    }

    private void startWebSocketHeartbeat() {
        logWs("HEARTBEAT_START", wsUrl, "interval=" + WS_HEARTBEAT_INTERVAL);
        stopHeartbeat();
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!webSocketMode || webSocket == null) return;
            if (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED) return;
            long now = System.currentTimeMillis();
            long elapsed = now - lastServerMessageTime;
            if (elapsed < WS_HEARTBEAT_INTERVAL) { consecutiveMissedPongs = 0; return; }
            consecutiveMissedPongs++;
            if (consecutiveMissedPongs >= WS_MAX_MISSED_PONGS && elapsed > WS_HEARTBEAT_TIMEOUT) {
                consecutiveMissedPongs = 0;
                if (webSocket != null) webSocket.close(1001, "Heartbeat timeout");
                return;
            }
            sendPing();
        }, WS_HEARTBEAT_INTERVAL, WS_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void flushPendingMessages() {
        if (webSocket == null || pendingMessages.isEmpty()) return;
        Log.d(TAG, "Flushing " + pendingMessages.size() + " pending messages");
        JSONObject msg;
        while ((msg = pendingMessages.poll()) != null) {
            if (webSocket != null) webSocket.send(msg.toString());
        }
    }

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

    private void stopHeartbeat() {
        logWs("HEARTBEAT_STOP", wsUrl, null);
        if (heartbeatTask != null) { heartbeatTask.cancel(true); heartbeatTask = null; }
        if (heartbeatScheduler != null) { heartbeatScheduler.shutdown(); heartbeatScheduler = null; }
    }

    private void sendPing() {
        JSONObject json = new JSONObject();
        try { json.put("type", "PING"); send(json); } catch (JSONException e) {}
    }

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
                    break;
                default:
                    postMessageReceived(json);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON: " + message);
        }
    }

    public void disconnect() {
        logEvent("LEAVE_ROOM", relayRoomCode, clientId, "DISCONNECT");
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
        } catch (IOException e) {}
        reader = null; writer = null; socket = null;
        postDisconnected("用户主动断开");
    }

    private void handleDisconnection(String reason) {
        if (manualDisconnect || state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) return;
        synchronized (this) {
            if (isHandlingDisconnection || state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) return;
            isHandlingDisconnection = true;
        }
        try {
            stopHeartbeat();
            if (relayMode) relayPolling = false;
            if (webSocketMode) closeWebSocketQuietly();
            setState(ConnectionState.DISCONNECTED);
            closeTransportQuietly();
            if (!suppressNextDisconnectNotice) postDisconnected(reason);
            suppressNextDisconnectNotice = false;
            if (reconnectAttempts < maxReconnectAttempts) scheduleReconnect();
        } finally { isHandlingDisconnection = false; }
    }

    private void handleConnectionFailure(String reason) {
        if (manualDisconnect) return;
        setState(ConnectionState.DISCONNECTED);
        postError("连接失败: " + reason);
        if (reconnectAttempts < maxReconnectAttempts) scheduleReconnect();
    }

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
        long exponentialDelay = (long) (reconnectInterval * Math.pow(2, reconnectAttempts - 1));
        long delay = Math.min(exponentialDelay, maxReconnectInterval);
        logWs("RECONNECT", wsUrl, "attempt=" + reconnectAttempts + " delay=" + delay);
        reconnectTask = reconnectScheduler.schedule(() -> {
            if (manualDisconnect) return;
            try {
                if (webSocketMode) doWebSocketConnect();
                else if (relayMode) doRelayJoin();
            } catch (Exception e) { handleConnectionFailure(e.getMessage()); }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void stopReconnect() {
        if (reconnectTask != null) { reconnectTask.cancel(true); reconnectTask = null; }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            try { if (!reconnectScheduler.awaitTermination(1, TimeUnit.SECONDS)) {} } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            reconnectScheduler = null;
        }
    }

    public void reconnectNow() {
        logWs("RECONNECT_NOW", wsUrl, "immediate reconnect");
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
            } catch (Exception e) { handleConnectionFailure(e.getMessage()); }
        }).start();
    }

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

    private boolean sendWebSocket(JSONObject json) {
        if (webSocket == null || (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED)) {
            if (pendingMessages.size() >= MAX_PENDING_MESSAGES) pendingMessages.poll();
            pendingMessages.offer(json);
            return true;
        }
        boolean sent = webSocket.send(json.toString());
        if (!sent) {
            if (pendingMessages.size() >= MAX_PENDING_MESSAGES) pendingMessages.poll();
            pendingMessages.offer(json);
        }
        return true;
    }

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

    private boolean isTransportWritable() {
        if (relayMode) return clientId > 0 && relayClientToken != null && !relayClientToken.isEmpty();
        if (webSocketMode) return webSocket != null && (state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED);
        return writer != null && socket != null && !socket.isClosed();
    }

    private void sendMessageNow(String message) {
        try {
            if (isTransportWritable()) {
                synchronized (this) {
                    writer.println(message);
                    if (writer.checkError()) {}
                }
            }
        } catch (Exception e) {}
    }

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
        } catch (JSONException e) {}
    }

    private void setState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            postStateChanged(newState);
        }
    }

    private void postConnected() {
        mainHandler.post(() -> { if (connectedListener != null) connectedListener.onConnected(clientId); });
    }

    private void postDisconnected(final String reason) {
        mainHandler.post(() -> { if (disconnectedListener != null) disconnectedListener.onDisconnected(reason); });
    }

    private void postMessageReceived(final JSONObject message) {
        mainHandler.post(() -> { if (messageListener != null) messageListener.onMessageReceived(message); });
    }

    private void postError(final String message) {
        mainHandler.post(() -> { if (errorListener != null) errorListener.onError(message); });
    }

    private void postStateChanged(final ConnectionState state) {
        mainHandler.post(() -> { if (stateChangedListener != null) stateChangedListener.onStateChanged(state); });
    }

    public interface OnConnectedListener { void onConnected(int clientId); }
    public interface OnDisconnectedListener { void onDisconnected(String reason); }
    public interface OnMessageReceivedListener { void onMessageReceived(JSONObject message); }
    public interface OnErrorListener { void onError(String message); }
    public interface OnStateChangedListener { void onStateChanged(ConnectionState state); }

    public void setOnConnectedListener(OnConnectedListener listener) { this.connectedListener = listener; }
    public void setOnDisconnectedListener(OnDisconnectedListener listener) { this.disconnectedListener = listener; }
    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) { this.messageListener = listener; }
    public void setOnErrorListener(OnErrorListener listener) { this.errorListener = listener; }
    public void setOnStateChangedListener(OnStateChangedListener listener) { this.stateChangedListener = listener; }

    public ConnectionState getState() { return state; }
    public boolean isConnected() { return state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED; }
    public boolean isAuthenticated() { return state == ConnectionState.AUTHENTICATED; }
    public int getClientId() { return clientId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; }
    public void setPeerToken(String token) { this.peerToken = token != null ? token : ""; }
    public String getPeerToken() { return peerToken; }
    public void setProtocolVersion(int version) { this.protocolVersion = version; }
    public void setReconnectPolicy(int maxAttempts, long baseIntervalMs, long maxIntervalMs) {
        this.maxReconnectAttempts = Math.max(0, maxAttempts);
        this.reconnectInterval = Math.max(500L, baseIntervalMs);
        this.maxReconnectInterval = Math.max(this.reconnectInterval, maxIntervalMs);
    }

    public void release() {
        disconnect();
        sendExecutor.shutdownNow();
        instance = null;
    }

    private static GameSocketClient instance;
    private static Context appContext;

    public static synchronized GameSocketClient getInstance() {
        if (instance == null) instance = new GameSocketClient();
        return instance;
    }

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

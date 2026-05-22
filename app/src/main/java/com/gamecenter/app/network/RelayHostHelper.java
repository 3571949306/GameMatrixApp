package com.gamecenter.app.network;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 云中转（Relay）主机端辅助类，封装主机端与中转服务器之间的通信逻辑。
 *
 * <p>打个比方：如果云中转服务器是一个"邮局"，那么这个类就是主机端的"邮局柜台"，
 * 负责开信箱（创建房间）、定期取信（轮询拉取客户端消息）、寄信（向客户端发送消息），
 * 以及管理"常客名单"（已知客户端列表）。</p>
 *
 * <p>在网络模块中的角色：这是云中转模式主机端的"通信桥梁"，
 * 与 {@link RelayClientHelper}（客户端侧的辅助工具）互为对偶。
 * 当两台设备无法直接通信时（比如不在同一WiFi下），就需要通过云中转服务器来转发消息，
 * 这个类就是帮主机端完成与中转服务器的所有交互。</p>
 * <p>
 * 职责：
 * <ul>
 *   <li>创建中转房间并获取房间码和主机令牌</li>
 *   <li>通过长轮询从中转服务器拉取客户端消息</li>
 *   <li>通过中转服务器向客户端发送消息（单播和广播）</li>
 *   <li>管理已知客户端的连接状态</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 跟踪已知客户端，支持并发访问</li>
 *   <li>发送操作使用独立单线程池，保证消息顺序并避免阻塞轮询线程</li>
 *   <li>轮询失败后等待1.5秒重试，避免频繁请求导致服务器压力</li>
 * </ul>
 * <p>
 * 此类为包级私有，仅供 {@link GameSocketServer} 内部使用。
 */
class RelayHostHelper {

    private static final String TAG = "RelayHostHelper";

    /** 回调接口，用于通知上层客户端连接/断开/消息/错误事件 */
    private final RelayHostCallback callback;

    /** 最大客户端连接数，即房间人数上限 */
    private final int maxClients;

    /** 发送消息的独立线程池，单线程执行以保证消息顺序 */
    private ExecutorService sendExecutor;

    /** 是否处于云中转模式 */
    volatile boolean relayMode = false;
    /** 是否正在进行轮询 */
    volatile boolean relayPolling = false;
    /** 是否处于活跃状态 */
    private volatile boolean active = false;
    /** 中转服务器基础URL */
    String relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL;
    /** 中转房间码，就像邮局的信箱号 */
    String relayRoomCode = "";
    /** 主机令牌，用于向中转服务器验证身份。
     *  就像信箱的钥匙，只有持有令牌的人才能取信和寄信。 */
    String relayHostToken = "";
    /** 轮询线程 */
    private Thread relayPollThread;
    /** 已知客户端集合，key为clientId，value为Boolean占位。
     *  就像邮局的"常客名单"，记录哪些客户端已经来过。 */
    public final ConcurrentHashMap<Integer, Boolean> relayKnownClients = new ConcurrentHashMap<>();

    /**
     * 云中转主机端回调接口。
     * <p>
     * 所有回调方法在轮询线程中调用，上层需要注意线程安全。
     */
    interface RelayHostCallback {
        /** 客户端连接时回调 */
        void onClientConnected(int clientId, String ip);
        /** 客户端断开时回调 */
        void onClientDisconnected(int clientId, String reason);
        /** 收到客户端消息时回调 */
        void onMessageReceived(int clientId, JSONObject message);
        /** 发生错误时回调 */
        void onError(String message);
    }

    /**
     * 构造RelayHostHelper。
     *
     * @param callback  事件回调接口
     * @param maxClients 最大客户端连接数
     */
    RelayHostHelper(RelayHostCallback callback, int maxClients) {
        this.callback = callback;
        this.maxClients = maxClients;
    }

    /**
     * 启动云中转模式，创建中转房间。
     * <p>
     * 向中转服务器发送创建房间请求，获取房间码和主机令牌后，
     * 启动轮询线程持续拉取来自客户端的消息。
     *
     * @param baseUrl 中转服务器的基础URL，为空则使用默认地址
     * @return true表示房间创建成功，false表示创建失败
     */
    boolean startRelay(String baseUrl) {
        try {
            relayMode = true;
            relayBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty() ? baseUrl.trim() : RelayHttpClient.DEFAULT_BASE_URL;
            // 向中转服务器发送创建房间请求
            JSONObject body = new JSONObject();
            body.put("app", "GameCenterApp");
            body.put("game", "GameCenterApp");
            JSONObject response = RelayHttpClient.post(relayBaseUrl, "/create", body, 10000);
            relayRoomCode = response.getString("roomCode");
            relayHostToken = response.getString("hostToken");
            relayPolling = true;
            active = true;
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
            active = false;
            callback.onError("云房间创建失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 停止云中转模式，释放所有资源。
     * <p>
     * 异步通知中转服务器关闭房间，停止轮询线程，清理客户端列表和发送线程池。
     */
    void stop() {
        relayPolling = false;
        active = false;
        // 异步通知中转服务器关闭房间，避免阻塞当前线程
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
        if (sendExecutor != null) { sendExecutor.shutdownNow(); sendExecutor = null; }
    }

    /**
     * 启动轮询线程，持续从中转服务器拉取消息。
     * <p>
     * 使用长轮询方式（超时35秒），拉取失败时等待1.5秒后重试。
     */
    private void startRelayPolling() {
        relayPollThread = new Thread(() -> {
            while (relayPolling && active) {
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
                    if (relayPolling && active) {
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
    JSONObject relayBaseBody() throws JSONException {
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
     * 所有消息都会附加远程IP和clientId信息后通知上层。
     *
     * @param clientId 客户端ID
     * @param json     消息内容
     */
    void handleRelayMessage(int clientId, JSONObject json) {
        if ("DISCONNECT".equals(json.optString("type", ""))) {
            relayKnownClients.remove(clientId);
            callback.onClientDisconnected(clientId, json.optString("reason", "连接关闭"));
            return;
        }
        // 新客户端首次发消息时，添加到已知客户端集合
        if (!relayKnownClients.containsKey(clientId)) {
            relayKnownClients.put(clientId, true);
            callback.onClientConnected(clientId, "云中转");
        }
        try {
            // 附加元数据，标识消息来源为中转模式
            json.put("_remoteIp", "relay");
            json.put("_clientId", clientId);
        } catch (JSONException ignored) { Log.w(TAG, "JSON error: " + ignored.getMessage()); }
        callback.onMessageReceived(clientId, json);
    }

    /** 通过云中转向所有客户端广播消息 */
    void relaySendAll(JSONObject json) { relaySend("all", json); }

    /**
     * 通过云中转向指定客户端发送消息。
     *
     * @param clientId 目标客户端ID，必须大于0
     * @param json     要发送的JSON消息
     */
    void relaySendTo(int clientId, JSONObject json) {
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
            callback.onError("云联机发送失败: " + e.getMessage());
        }
    }

    /** 是否处于云中转模式 */
    boolean isRelayMode() { return relayMode; }

    /** 获取云中转房间码 */
    String getRelayRoomCode() { return relayRoomCode; }

    /** 获取已连接的客户端数量 */
    int getConnectedClientCount() { return relayKnownClients.size(); }

    /** 客户端数量是否已达上限 */
    boolean isFull() { return relayKnownClients.size() >= maxClients; }

    /**
     * 断开指定客户端的连接。
     * <p>
     * 向客户端发送ERROR消息通知其被断开，然后从已知客户端中移除并通知上层。
     *
     * @param clientId 要断开的客户端ID
     * @param reason   断开原因
     */
    void disconnectClient(int clientId, String reason) {
        try {
            JSONObject error = new JSONObject();
            error.put("type", "ERROR");
            error.put("message", reason != null ? reason : "连接关闭");
            relaySendTo(clientId, error);
        } catch (JSONException ignored) { Log.w(TAG, "JSON error: " + ignored.getMessage()); }
        relayKnownClients.remove(clientId);
        callback.onClientDisconnected(clientId, reason != null ? reason : "连接关闭");
    }
}

package com.gamecenter.app.network;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import org.json.JSONObject;

/**
 * 联机房间管理器 — 封装房间创建/加入/离开、连接回调、聊天等联机通用逻辑。
 *
 * <p>打个比方：这个类就像一个"线上棋牌室管理员"，帮你完成开房、邀人、关门等操作，
 * 还提供对讲机（聊天功能）。每个游戏只需要告诉管理员自己的名字和偏好，
 * 管理员就能帮你搞定所有联机相关的事情。</p>
 *
 * <p>在网络模块中的角色：这是 {@link BaseOnlineActivity} 的"组合版替代品"。
 * BaseOnlineActivity 用继承方式复用联机逻辑，而 OnlineRoomManager 用组合方式复用，
 * 更灵活——你的Activity不需要继承特定基类，只需创建一个OnlineRoomManager实例即可。</p>
 *
 * <p>从 {@link BaseOnlineActivity} 中提取，使各游戏的 OnlineActivity 可以通过组合方式复用，
 * 而不必继承 BaseOnlineActivity 或重复实现联机逻辑。
 * </p>
 * <p>
 * 使用方式：
 * <pre>
 * OnlineRoomManager roomManager = new OnlineRoomManager(activity, "gomoku_p2p", "五子棋");
 * roomManager.initServer();
 * roomManager.initClient();
 * roomManager.initChatHelper();
 * roomManager.setListener(new OnlineRoomManager.Listener() { ... });
 * </pre>
 * </p>
 */
public class OnlineRoomManager {

    // ==================== 基础配置字段 ====================

    /** 应用级Context，避免持有Activity导致内存泄漏 */
    private final Context context;
    /** 偏好设置文件名，不同游戏使用不同文件名避免数据混淆 */
    private final String prefsName;
    /** 游戏名称，用于大厅标题显示 */
    private final String gameName;
    /** 主线程Handler，用于将回调投递到UI线程 */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==================== 核心网络组件 ====================

    /** 偏好设置，用于持久化存储令牌等信息 */
    private SharedPreferences prefs;
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
    /** 本方玩家ID（房主=1，加入方=2） */
    private int myPlayerId = -1;
    /** 对手玩家ID */
    private int opponentPlayerId = -1;
    /** 当前房间码 */
    private String roomCode = "";

    // ==================== UI组件 ====================

    /** 大厅布局容器 */
    private LinearLayout lobbyLayout;
    /** 游戏布局容器 */
    private LinearLayout gameLayout;
    /** 房间码显示文本 */
    private TextView roomCodeText;
    /** 连接状态显示文本 */
    private TextView connectionStatusText;
    /** 加载进度条 */
    private ProgressBar loadingBar;
    /** 聊天消息显示区域 */
    private TextView chatDisplay;
    /** 聊天区域滚动容器 */
    private ScrollView chatScroll;
    /** 聊天输入框 */
    private EditText chatInput;

    // ==================== 监听器 ====================

    /** 游戏事件监听器，由外部设置 */
    private Listener listener;

    /**
     * 游戏事件监听接口，由外部实现以响应游戏生命周期事件。
     * 就像给管理员留了三个"电话号码"，分别在游戏开始、收到消息、游戏重置时拨打。
     */
    public interface Listener {
        /** 双方连接成功，游戏开始时回调 */
        void onGameStarted();
        /** 收到非聊天的游戏消息时回调 */
        void onGameMessageReceived(JSONObject message);
        /** 游戏需要重置时回调 */
        void onGameReset();
    }

    /**
     * 构造联机房间管理器。
     *
     * @param context  上下文，内部会转为ApplicationContext避免内存泄漏
     * @param prefsName 偏好设置文件名，不同游戏应使用不同名称
     * @param gameName  游戏名称，用于大厅标题显示
     */
    public OnlineRoomManager(Context context, String prefsName, String gameName) {
        this.context = context.getApplicationContext();
        this.prefsName = prefsName;
        this.gameName = gameName;
    }

    /**
     * 设置游戏事件监听器。
     *
     * @param listener 监听器实现
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * 初始化主机端WebSocket服务器。
     * 设置客户端连接、断开、消息接收和错误的监听器。
     * 服务器在 {@link #createRoom()} 成功后才真正启动。
     */
    public void initServer() {
        server = new GameSocketServer(context);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);
    }

    /**
     * 初始化客户端WebSocket连接。
     * 获取单例客户端实例并设置连接、断开、消息接收和错误的监听器。
     * 客户端在 {@link #joinRoom(String)} 时发起连接。
     */
    public void initClient() {
        client = GameSocketClient.getInstance(context);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);
    }

    /**
     * 初始化聊天辅助类。
     * 设置聊天消息发送监听器，根据当前角色（主机/客户端）选择不同的发送方式。
     */
    public void initChatHelper() {
        chatHelper = new OnlineChatHelper(context);
        chatHelper.setOnChatMessageSendListener(text -> {
            JSONObject msg = chatHelper.createChatMessage(text);
            if (msg != null) {
                if (isHost) {
                    server.broadcast(msg);
                } else {
                    client.send(msg);
                }
            }
        });
    }

    /**
     * 设置内嵌模式的聊天显示区域。
     * 调用后聊天内容将直接显示在指定的TextView中，而非弹窗。
     *
     * @param chatDisplay 聊天内容显示的TextView
     * @param chatScroll  聊天区域的滚动容器
     */
    public void setInlineChatDisplay(TextView chatDisplay, ScrollView chatScroll) {
        this.chatDisplay = chatDisplay;
        this.chatScroll = chatScroll;
        if (chatHelper != null) {
            chatHelper.setInlineDisplay(chatDisplay, chatScroll);
        }
    }

    /**
     * 初始化大厅（房间管理）界面。
     * 包含：游戏标题、创建房间按钮、加入房间按钮、加载进度条、房间码显示、连接状态。
     *
     * @param root 根布局容器
     */
    public void initLobbyLayout(LinearLayout root) {
        lobbyLayout = new LinearLayout(context);
        lobbyLayout.setOrientation(LinearLayout.VERTICAL);
        lobbyLayout.setPadding(48, 48, 48, 48);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView titleText = new TextView(context);
        titleText.setText(gameName + " - " + context.getString(R.string.online_title));
        titleText.setTextSize(24);
        titleText.setTextColor(0xFF1E1E32);
        titleText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        titleText.setPadding(0, 0, 0, 32);

        Button createRoomBtn = new Button(context);
        createRoomBtn.setText(context.getString(R.string.online_create_room));
        createRoomBtn.setTextSize(18);
        createRoomBtn.setBackgroundColor(0xFF4CAF50);
        createRoomBtn.setTextColor(0xFFFFFFFF);
        createRoomBtn.setPadding(32, 24, 32, 24);
        createRoomBtn.setOnClickListener(v -> createRoom());

        Button joinRoomBtn = new Button(context);
        joinRoomBtn.setText(context.getString(R.string.online_join_room));
        joinRoomBtn.setTextSize(18);
        joinRoomBtn.setBackgroundColor(0xFF2196F3);
        joinRoomBtn.setTextColor(0xFFFFFFFF);
        joinRoomBtn.setPadding(32, 24, 32, 24);
        joinRoomBtn.setOnClickListener(v -> showJoinDialog());

        loadingBar = new ProgressBar(context);
        loadingBar.setVisibility(View.GONE);
        loadingBar.setPadding(0, 24, 0, 24);

        roomCodeText = new TextView(context);
        roomCodeText.setTextSize(20);
        roomCodeText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeText.setPadding(0, 16, 0, 8);
        roomCodeText.setVisibility(View.GONE);

        connectionStatusText = new TextView(context);
        connectionStatusText.setTextSize(16);
        connectionStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        connectionStatusText.setPadding(0, 8, 0, 0);

        lobbyLayout.addView(titleText);
        lobbyLayout.addView(createRoomBtn);
        lobbyLayout.addView(joinRoomBtn);
        lobbyLayout.addView(loadingBar);
        lobbyLayout.addView(roomCodeText);
        lobbyLayout.addView(connectionStatusText);

        root.addView(lobbyLayout);
    }

    /**
     * 初始化内嵌聊天视图。
     * 在游戏界面底部添加聊天区域，包含消息显示区（可滚动）和输入行（输入框+发送按钮）。
     *
     * @param gameContent 游戏内容容器
     */
    public void initChatViews(LinearLayout gameContent) {
        chatDisplay = new TextView(context);
        chatDisplay.setTextSize(12);
        chatDisplay.setBackgroundColor(0xFFF5F5F5);
        chatDisplay.setPadding(12, 8, 12, 8);
        chatDisplay.setMaxLines(4);
        chatDisplay.setGravity(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (context.getResources().getDisplayMetrics().density * 80));
        chatParams.setMargins(8, 2, 8, 2);
        chatDisplay.setLayoutParams(chatParams);

        chatScroll = new ScrollView(context);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (context.getResources().getDisplayMetrics().density * 80)));
        chatScroll.addView(chatDisplay);
        chatScroll.setSmoothScrollingEnabled(true);

        LinearLayout chatInputRow = new LinearLayout(context);
        chatInputRow.setOrientation(LinearLayout.HORIZONTAL);
        chatInputRow.setPadding(8, 0, 8, 4);

        chatInput = new EditText(context);
        chatInput.setHint(context.getString(R.string.online_input_message));
        chatInput.setSingleLine(true);
        chatInput.setTextSize(14);
        chatInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button chatSendBtn = new Button(context);
        chatSendBtn.setText(context.getString(R.string.online_send));
        chatSendBtn.setTextSize(14);
        chatSendBtn.setBackgroundColor(0xFF9C27B0);
        chatSendBtn.setTextColor(0xFFFFFFFF);
        chatSendBtn.setPadding(16, 8, 16, 8);
        chatSendBtn.setOnClickListener(v -> sendChatMessage());

        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            sendChatMessage();
            return true;
        });

        chatInputRow.addView(chatInput);
        chatInputRow.addView(chatSendBtn);

        gameContent.addView(chatScroll);
        gameContent.addView(chatInputRow);
    }

    /**
     * 创建联机房间。
     * 在后台线程中生成房间码并启动WebSocket服务器连接中转服务器。
     * 成功后设置房主标识（isHost=true, myPlayerId=1），显示房间码和等待对话框。
     */
    public void createRoom() {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText(context.getString(R.string.online_creating_cloud));

        new Thread(() -> {
            String code = generateRoomCode();
            String wsUrl = RelayHttpClient.getWebSocketUrl(RelayHttpClient.DEFAULT_BASE_URL, code, "");
            boolean success = server.startWebSocket(wsUrl);
            mainHandler.post(() -> {
                loadingBar.setVisibility(View.GONE);
                if (success) {
                    isHost = true;
                    myPlayerId = 1;
                    roomCode = code;
                    connectionStatusText.setText(context.getString(R.string.online_room_created));
                    roomCodeText.setText(context.getString(R.string.online_room_code_label) + code);
                    showWaitingDialog();
                } else {
                    Toast.makeText(context, context.getString(R.string.online_create_failed), Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /**
     * 加入指定房间。
     * 从偏好设置中读取上次保存的对等端令牌（用于身份识别），
     * 然后通过WebSocket客户端连接到中转服务器。
     *
     * @param code 房间码
     */
    public void joinRoom(String code) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText(context.getString(R.string.online_joining_room) + code + "...");
        roomCode = code;

        String token = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RelayHttpClient.DEFAULT_BASE_URL, code);

        if (token != null) {
            client.setPeerToken(token);
        }

        client.connectWebSocket(wsUrl);
    }

    /**
     * 离开当前房间。
     * 根据角色停止服务器或断开客户端连接，重置游戏状态。
     */
    public void leaveRoom() {
        if (isHost && server != null) {
            server.stop();
        }
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        isPlaying = false;
        isHost = false;
    }

    /**
     * 以主机身份广播JSON消息给所有客户端。
     * 仅在主机端有效，客户端调用此方法不会执行任何操作。
     *
     * @param json 要广播的JSON消息
     */
    public void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    /** 切换到游戏界面，隐藏大厅布局，显示游戏布局 */
    public void showGameScreen() {
        if (lobbyLayout != null) lobbyLayout.setVisibility(View.GONE);
        if (gameLayout != null) gameLayout.setVisibility(View.VISIBLE);
    }

    /** 切换回大厅界面，隐藏游戏布局，显示大厅布局 */
    public void showLobby() {
        if (gameLayout != null) gameLayout.setVisibility(View.GONE);
        if (lobbyLayout != null) lobbyLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 清理所有资源。
     * 依次清理聊天辅助类、停止服务器、释放客户端连接。
     * 应在Activity的onDestroy中调用此方法。
     */
    public void cleanup() {
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }

    /**
     * 初始化偏好设置。需要传入Activity级别的Context来访问SharedPreferences。
     *
     * @param activityContext Activity上下文
     */
    public void initPrefs(Context activityContext) {
        prefs = activityContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    /**
     * 设置游戏布局容器，用于showGameScreen/showLobby切换显示。
     *
     * @param gameLayout 游戏界面的布局容器
     */
    public void setGameLayout(LinearLayout gameLayout) {
        this.gameLayout = gameLayout;
    }

    // ==================== Getter方法 ====================

    /** 获取主机端服务器实例 */
    public GameSocketServer getServer() { return server; }
    /** 获取客户端连接实例 */
    public GameSocketClient getClient() { return client; }
    /** 获取聊天辅助类 */
    public OnlineChatHelper getChatHelper() { return chatHelper; }
    /** 是否为主机端（房主） */
    public boolean isHost() { return isHost; }
    /** 是否正在游戏中 */
    public boolean isPlaying() { return isPlaying; }
    /** 获取本方玩家ID */
    public int getMyPlayerId() { return myPlayerId; }
    /** 获取对手玩家ID */
    public int getOpponentPlayerId() { return opponentPlayerId; }
    /** 获取当前房间码 */
    public String getRoomCode() { return roomCode; }
    /** 获取连接状态文本控件 */
    public TextView getConnectionStatusText() { return connectionStatusText; }
    /** 获取加载进度条控件 */
    public ProgressBar getLoadingBar() { return loadingBar; }
    /** 获取大厅布局容器 */
    public LinearLayout getLobbyLayout() { return lobbyLayout; }
    /** 获取主线程Handler */
    public Handler getMainHandler() { return mainHandler; }

    /**
     * 生成6位随机房间码。
     * 使用排除易混淆字符的字符集（没有O、0、I、1等），确保房间码容易辨认。
     *
     * @return 6位随机房间码
     */
    private String generateRoomCode() {
        return RoomCodeHelper.generateRoomCode();
    }

    /**
     * 显示等待对手加入的对话框。
     * 对话框中显示房间码（大号加粗）、复制房间码按钮和操作提示。
     * 对话框不可取消，用户只能通过"取消"按钮离开房间。
     */
    private void showWaitingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.online_waiting_opponent));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 48, 48, 48);
        content.setGravity(View.TEXT_ALIGNMENT_CENTER);

        TextView roomCodeView = new TextView(context);
        roomCodeView.setTextSize(28);
        roomCodeView.setText(roomCode);
        roomCodeView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeView.setPadding(0, 16, 0, 16);
        roomCodeView.setTextColor(0xFF2196F3);
        roomCodeView.setTypeface(null, android.graphics.Typeface.BOLD);

        Button copyBtn = new Button(context);
        copyBtn.setText(context.getString(R.string.online_copy_room_code));
        copyBtn.setTextSize(14);
        copyBtn.setBackgroundColor(0xFF4CAF50);
        copyBtn.setTextColor(0xFFFFFFFF);
        copyBtn.setPadding(24, 8, 24, 8);
        copyBtn.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("room_code", roomCode);
            cm.setPrimaryClip(clip);
            Toast.makeText(context, context.getString(R.string.online_room_code_copied), Toast.LENGTH_SHORT).show();
        });

        TextView hintView = new TextView(context);
        hintView.setTextSize(14);
        hintView.setText(context.getString(R.string.online_share_room_code_hint));
        hintView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        hintView.setPadding(0, 8, 0, 8);

        content.addView(roomCodeView);
        content.addView(copyBtn);
        content.addView(hintView);

        builder.setView(content);
        builder.setCancelable(false);
        builder.setNegativeButton(context.getString(R.string.online_cancel), (d, w) -> leaveRoom());
        builder.create().show();
    }

    /**
     * 显示加入房间的输入对话框。
     * 弹出输入框让用户输入6位房间码，输入后调用 {@link #joinRoom(String)} 加入房间。
     */
    private void showJoinDialog() {
        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.online_input_room_code_hint));
        input.setMaxLines(1);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.online_join_room_title))
                .setView(input)
                .setPositiveButton(context.getString(R.string.online_join), (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() == 6) {
                        joinRoom(code);
                    } else {
                        Toast.makeText(context, context.getString(R.string.online_input_room_code_toast), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(context.getString(R.string.online_cancel), null)
                .show();
    }

    /**
     * 显示连接断开对话框。
     * 主机端提供"等待重连"选项，客户端提供"重新连接"选项。
     *
     * @param message    断开原因的描述信息
     * @param isHostSide 是否为主机端
     */
    private void showDisconnectDialog(String message, boolean isHostSide) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.online_disconnected_title))
                .setMessage(message)
                .setCancelable(false);

        if (isHostSide) {
            builder.setPositiveButton(context.getString(R.string.online_waiting_reconnect), (d, w) ->
                    Toast.makeText(context, context.getString(R.string.online_waiting_opponent_reconnect), Toast.LENGTH_SHORT).show());
        } else {
            builder.setPositiveButton(context.getString(R.string.online_reconnect), (d, w) -> {
                if (roomCode != null && !roomCode.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.online_reconnecting), Toast.LENGTH_SHORT).show();
                    joinRoom(roomCode);
                } else {
                    Toast.makeText(context, context.getString(R.string.online_reconnect_failed), Toast.LENGTH_SHORT).show();
                    showLobby();
                }
            });
        }

        builder.setNegativeButton(context.getString(R.string.online_leave_room), (d, w) -> leaveRoom());
        builder.show();
    }

    /** 从输入框获取文本并发送聊天消息 */
    private void sendChatMessage() {
        if (chatInput == null || chatHelper == null) return;
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    // ==================== 连接回调方法 ====================

    /** 主机端回调：有客户端连接到本机，记录对手ID并通知游戏开始 */
    private void onClientConnected(int clientId, String ip) {
        opponentPlayerId = clientId;
        mainHandler.post(() -> {
            connectionStatusText.setText(context.getString(R.string.online_opponent_joined));
            isPlaying = true;
            showGameScreen();
            if (listener != null) listener.onGameStarted();
        });
    }

    /** 主机端回调：客户端断开连接，显示断开对话框 */
    private void onClientDisconnected(int clientId, String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            showDisconnectDialog(context.getString(R.string.online_opponent_disconnected) + reason, true);
        });
    }

    /** 客户端回调：成功连接到主机，设置玩家ID为2，保存令牌并通知游戏开始 */
    private void onClientConnectedToHost(int clientId) {
        myPlayerId = 2;
        isPlaying = true;

        String token = client.getPeerToken();
        if (token != null && !token.isEmpty() && prefs != null) {
            prefs.edit().putString("last_peer_token", token).apply();
        }

        mainHandler.post(() -> {
            connectionStatusText.setText(context.getString(R.string.online_connected_to_host));
            showGameScreen();
            if (listener != null) listener.onGameStarted();
        });
    }

    /** 客户端回调：与主机的连接断开，显示断开对话框 */
    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            showDisconnectDialog(context.getString(R.string.online_connection_lost) + reason, false);
        });
    }

    /** 主机端服务器错误回调 */
    private void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(context, context.getString(R.string.online_server_error) + message, Toast.LENGTH_SHORT).show());
    }

    /** 客户端连接错误回调 */
    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(context, context.getString(R.string.online_client_error) + message, Toast.LENGTH_SHORT).show());
    }

    // ==================== 消息处理回调 ====================

    /** 主机端消息接收回调：先判断是否为聊天消息，否则转发给游戏监听器 */
    private void onHostMessageReceived(int clientId, JSONObject message) {
        if (chatHelper != null && chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        if (listener != null) listener.onGameMessageReceived(message);
    }

    /** 客户端消息接收回调：先判断是否为聊天消息，否则转发给游戏监听器 */
    private void onClientMessageReceived(JSONObject message) {
        if (chatHelper != null && chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        if (listener != null) listener.onGameMessageReceived(message);
    }
}

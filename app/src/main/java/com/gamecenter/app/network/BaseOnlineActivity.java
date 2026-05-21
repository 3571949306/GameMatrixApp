package com.gamecenter.app.network;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/**
 * 联机游戏 Activity 基类 — 封装房间管理、聊天、连接状态等联机对战通用逻辑。
 *
 * <p>打个比方：如果联机对战是一场线上聚会，那么这个类就是聚会的"总管"，
 * 负责搭建场地（创建房间）、邀请客人（加入房间）、维持秩序（管理连接状态）、
 * 以及提供聊天服务。每个具体的游戏只需要告诉"总管"自己的特殊需求即可。</p>
 *
 * <p>在网络模块中的角色：这是整个联机模块的"顶层协调者"，它把底层的网络通信
 * （GameSocketServer/GameSocketClient）和上层的游戏逻辑连接起来，
 * 让具体的游戏Activity不需要关心网络细节。</p>
 * <p>
 * 职责：
 * <ul>
 *   <li>管理联机对战的生命周期：创建房间、加入房间、离开房间</li>
 *   <li>维护主机端（{@link GameSocketServer}）和客户端（{@link GameSocketClient}）的连接与回调</li>
 *   <li>集成 {@link OnlineChatHelper} 提供实时聊天功能</li>
 *   <li>构建大厅 UI（创建/加入房间按钮、房间码显示、连接状态）和游戏 UI 的通用框架</li>
 *   <li>处理连接断开场景，提供重连和离开选项</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>采用模板方法模式：子类实现 {@link #initGameViews}、{@link #onGameStarted}、
 *       {@link #onGameMessageReceived}、{@link #onGameReset} 等抽象方法来定制游戏特有逻辑。
 *       就像填空题一样，基类出好题目框架，子类填写自己的答案。</li>
 *   <li>UI 完全通过代码动态构建，不依赖 XML 布局，便于在不同游戏中复用</li>
 *   <li>使用 volatile 修饰 {@link #isHost} 和 {@link #isPlaying}，确保多线程间的可见性。
 *       volatile 就像一块"公告板"，一个线程修改后，其他线程立刻能看到最新值。</li>
 *   <li>所有 UI 更新通过 {@link #mainHandler} 投递到主线程，保证线程安全。
 *       Android 要求UI操作只能在主线程进行，Handler就像一个"快递员"，把任务从后台线程送到主线程执行。</li>
 * </ul>
 */
public abstract class BaseOnlineActivity extends AppCompatActivity {

    /** 中转服务器基础 URL，从 {@link RelayHttpClient} 获取默认值 */
    protected static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    // ==================== 核心网络组件 ====================
    // 下面这些是联机对战的"四大金刚"，分别负责不同的网络任务

    /** 偏好设置，用于持久化存储对等端令牌等信息。
     *  就像一个小本子，把重要的信息记下来，下次打开应用时还能找到。 */
    protected SharedPreferences prefs;
    /** 主机端 WebSocket 服务器实例，仅在房主端使用。
     *  房主就像"服务器"，负责接收和转发所有玩家的消息。 */
    protected GameSocketServer server;
    /** 客户端 WebSocket 连接实例，仅在加入方使用。
     *  加入方就像"客户端"，连接到房主的服务器上。 */
    protected GameSocketClient client;
    /** 聊天辅助类，处理聊天消息的收发与显示。
     *  就像聊天软件的后台服务，负责把你说的话发出去，把对方的话显示出来。 */
    protected OnlineChatHelper chatHelper;

    // ==================== 游戏状态变量 ====================

    /** 是否为主机端（房主），volatile 保证多线程可见性 */
    protected volatile boolean isHost = false;
    /** 是否正在游戏中，volatile 保证多线程可见性 */
    protected volatile boolean isPlaying = false;
    /** 本方玩家 ID（房主=1，加入方=2）。
     *  就像游戏中的座位号，房主坐1号位，加入者坐2号位。 */
    protected int myPlayerId = -1;
    /** 对手玩家 ID，用于标识对手的客户端连接 */
    protected int opponentPlayerId = -1;
    /** 当前房间码。
     *  就像聚会的邀请码，告诉朋友这个码，他们就能找到你的房间。 */
    protected String roomCode = "";

    /** 主线程 Handler，用于将回调投递到 UI 线程 */
    protected Handler mainHandler = new Handler(Looper.getMainLooper());

    // ========== UI 组件 ==========

    /** 大厅布局容器（包含创建/加入房间按钮等） */
    protected LinearLayout lobbyLayout;
    /** 游戏布局容器（包含游戏视图和聊天区域） */
    protected LinearLayout gameLayout;
    /** 房间码显示文本 */
    protected TextView roomCodeText;
    /** 连接状态显示文本 */
    protected TextView connectionStatusText;
    /** 加载进度条 */
    protected ProgressBar loadingBar;
    /** 聊天消息显示区域 */
    protected TextView chatDisplay;
    /** 聊天区域滚动容器 */
    protected ScrollView chatScroll;
    /** 聊天输入框 */
    protected EditText chatInput;

    // ========== 子类必须实现的抽象方法 ==========

    /**
     * 获取 P2P 偏好设置文件名。
     * <p>
     * 不同游戏应使用不同的文件名，避免令牌等数据混淆。
     *
     * @return 偏好设置文件名
     */
    protected abstract String getP2pPrefsName();

    /**
     * 获取游戏名称，用于大厅标题显示。
     *
     * @return 游戏名称字符串
     */
    protected abstract String getGameName();

    /**
     * 初始化游戏特有的 UI 视图。
     * <p>
     * 子类在此方法中将游戏相关的 View 添加到 gameContent 容器中，
     * 如棋盘、手牌区域、计分板等。
     *
     * @param gameContent 游戏内容容器，子类应将自定义 View 添加到此容器
     */
    protected abstract void initGameViews(LinearLayout gameContent);

    /**
     * 游戏开始时的回调。
     * <p>
     * 当双方玩家都连接成功后调用，子类在此初始化游戏状态、
     * 发牌、设置初始回合等游戏开始逻辑。
     */
    protected abstract void onGameStarted();

    /**
     * 收到游戏消息时的回调。
     * <p>
     * 当收到非聊天类型的 JSON 消息时调用，子类在此处理游戏逻辑消息，
     * 如出牌、操作同步等。
     *
     * @param message 收到的 JSON 游戏消息
     */
    protected abstract void onGameMessageReceived(JSONObject message);

    /**
     * 游戏重置时的回调。
     * <p>
     * 当需要重新开始一局游戏时调用，子类在此清除游戏状态、
     * 重置 UI 等恢复初始状态的逻辑。
     */
    protected abstract void onGameReset();

    /**
     * Activity 创建时的初始化入口。
     * <p>
     * 初始化顺序：偏好设置 → 服务器 → 客户端 → 聊天辅助类 → UI 布局 → 后续回调。
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(getP2pPrefsName(), MODE_PRIVATE);

        initServer();
        initClient();
        initChatHelper();

        // 动态构建根布局（垂直线性布局）
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        initLobbyLayout(root);
        initGameLayout(root);

        setContentView(root);

        afterViewsCreated();
    }

    /**
     * 初始化主机端 WebSocket 服务器。
     * <p>
     * 设置客户端连接、断开、消息接收和错误的监听器。
     * 服务器在 {@link #createRoom()} 成功后才真正启动。
     */
    protected void initServer() {
        server = new GameSocketServer(this);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);
    }

    /**
     * 初始化客户端 WebSocket 连接。
     * <p>
     * 获取单例客户端实例并设置连接、断开、消息接收和错误的监听器。
     * 客户端在 {@link #joinRoom(String)} 时发起连接。
     */
    protected void initClient() {
        client = GameSocketClient.getInstance(this);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);
    }

    /**
     * 初始化聊天辅助类。
     * <p>
     * 设置聊天消息发送监听器，当用户发送聊天消息时，
     * 根据当前角色（主机/客户端）选择不同的发送方式：
     * 主机通过 server 广播，客户端通过 client 发送。
     */
    protected void initChatHelper() {
        chatHelper = new OnlineChatHelper(this);
        chatHelper.setOnChatMessageSendListener(text -> {
            JSONObject msg = chatHelper.createChatMessage(text);
            if (msg != null) {
                // 根据角色选择发送通道
                if (isHost) {
                    server.broadcast(msg);
                } else {
                    client.send(msg);
                }
            }
        });
    }

    /**
     * 视图创建后的回调，用于子类扩展。
     * <p>
     * 默认实现将聊天显示区域设置为内嵌模式。
     * 子类可重写此方法添加额外的初始化逻辑。
     */
    protected void afterViewsCreated() {
        chatHelper.setInlineDisplay(chatDisplay, chatScroll);
    }

    // ========== 大厅布局 ==========

    /**
     * 初始化大厅（房间管理）界面。
     * <p>
     * 包含：游戏标题、创建房间按钮、加入房间按钮、加载进度条、房间码显示、连接状态。
     *
     * @param root 根布局容器
     */
    protected void initLobbyLayout(LinearLayout root) {
        lobbyLayout = new LinearLayout(this);
        lobbyLayout.setOrientation(LinearLayout.VERTICAL);
        lobbyLayout.setPadding(48, 48, 48, 48);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView titleText = new TextView(this);
        titleText.setText(getGameName() + " - 联机对战");
        titleText.setTextSize(24);
        titleText.setTextColor(0xFF1E1E32);
        titleText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        titleText.setPadding(0, 0, 0, 32);

        Button createRoomBtn = new Button(this);
        createRoomBtn.setText("创建房间");
        createRoomBtn.setTextSize(18);
        createRoomBtn.setBackgroundColor(0xFF4CAF50);
        createRoomBtn.setTextColor(0xFFFFFFFF);
        createRoomBtn.setPadding(32, 24, 32, 24);
        createRoomBtn.setOnClickListener(v -> createRoom());

        Button joinRoomBtn = new Button(this);
        joinRoomBtn.setText("加入房间");
        joinRoomBtn.setTextSize(18);
        joinRoomBtn.setBackgroundColor(0xFF2196F3);
        joinRoomBtn.setTextColor(0xFFFFFFFF);
        joinRoomBtn.setPadding(32, 24, 32, 24);
        joinRoomBtn.setOnClickListener(v -> showJoinDialog());

        loadingBar = new ProgressBar(this);
        loadingBar.setVisibility(View.GONE);
        loadingBar.setPadding(0, 24, 0, 24);

        roomCodeText = new TextView(this);
        roomCodeText.setTextSize(20);
        roomCodeText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeText.setPadding(0, 16, 0, 8);
        roomCodeText.setVisibility(View.GONE);

        connectionStatusText = new TextView(this);
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

    // ========== 游戏布局 ==========

    /**
     * 初始化游戏界面。
     * <p>
     * 默认隐藏（GONE），当双方连接成功后通过 {@link #showGameScreen()} 显示。
     * 布局结构：游戏内容（子类自定义）→ 聊天区域 → 离开房间按钮。
     *
     * @param root 根布局容器
     */
    protected void initGameLayout(LinearLayout root) {
        gameLayout = new LinearLayout(this);
        gameLayout.setOrientation(LinearLayout.VERTICAL);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        // 初始隐藏，连接成功后才显示
        gameLayout.setVisibility(View.GONE);

        LinearLayout gameContent = new LinearLayout(this);
        gameContent.setOrientation(LinearLayout.VERTICAL);

        // 让子类添加游戏特有的 UI
        initGameViews(gameContent);

        // 添加聊天区域
        initChatViews(gameContent);

        // 添加离开按钮
        Button leaveBtn = new Button(this);
        leaveBtn.setText("离开房间");
        leaveBtn.setTextSize(14);
        leaveBtn.setBackgroundColor(0xFF9E9E9E);
        leaveBtn.setTextColor(0xFFFFFFFF);
        leaveBtn.setPadding(16, 8, 16, 8);
        leaveBtn.setOnClickListener(v -> leaveRoom());

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(8, 0, 8, 4);
        btnRow.addView(leaveBtn);

        gameContent.addView(btnRow);
        gameLayout.addView(gameContent);

        root.addView(gameLayout);
    }

    /**
     * 初始化内嵌聊天视图。
     * <p>
     * 在游戏界面底部添加聊天区域，包含消息显示区（可滚动）和输入行（输入框+发送按钮）。
     * 聊天显示区高度为 80dp，限制最大显示4行。
     *
     * @param gameContent 游戏内容容器
     */
    protected void initChatViews(LinearLayout gameContent) {
        chatDisplay = new TextView(this);
        chatDisplay.setTextSize(12);
        chatDisplay.setBackgroundColor(0xFFF5F5F5);
        chatDisplay.setPadding(12, 8, 12, 8);
        // 限制最大显示行数，避免聊天区域占用过多空间
        chatDisplay.setMaxLines(4);
        chatDisplay.setGravity(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().density * 80));
        chatParams.setMargins(8, 2, 8, 2);
        chatDisplay.setLayoutParams(chatParams);

        chatScroll = new ScrollView(this);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().density * 80)));
        chatScroll.addView(chatDisplay);
        chatScroll.setSmoothScrollingEnabled(true);

        LinearLayout chatInputRow = new LinearLayout(this);
        chatInputRow.setOrientation(LinearLayout.HORIZONTAL);
        chatInputRow.setPadding(8, 0, 8, 4);

        chatInput = new EditText(this);
        chatInput.setHint("输入消息...");
        chatInput.setSingleLine(true);
        chatInput.setTextSize(14);
        // weight=1.0 使输入框占据剩余空间
        chatInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button chatSendBtn = new Button(this);
        chatSendBtn.setText("发送");
        chatSendBtn.setTextSize(14);
        chatSendBtn.setBackgroundColor(0xFF9C27B0);
        chatSendBtn.setTextColor(0xFFFFFFFF);
        chatSendBtn.setPadding(16, 8, 16, 8);
        chatSendBtn.setOnClickListener(v -> sendChatMessage());

        // 键盘回车键也可发送消息
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            sendChatMessage();
            return true;
        });

        chatInputRow.addView(chatInput);
        chatInputRow.addView(chatSendBtn);

        gameContent.addView(chatScroll);
        gameContent.addView(chatInputRow);
    }

    // ========== 房间管理 ==========

    /**
     * 创建联机房间。
     * <p>
     * 在后台线程中生成房间码并启动 WebSocket 服务器连接中转服务器。
     * 成功后设置房主标识（isHost=true, myPlayerId=1），显示房间码和等待对话框。
     * 失败时显示 Toast 提示。
     */
    protected void createRoom() {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("正在创建云房间...");

        new Thread(() -> {
            String code = generateRoomCode();
            String wsUrl = RelayHttpClient.getWebSocketUrl(RELAY_BASE_URL, code, "");
            boolean success = server.startWebSocket(wsUrl);
            mainHandler.post(() -> {
                loadingBar.setVisibility(View.GONE);
                if (success) {
                    isHost = true;
                    myPlayerId = 1;
                    roomCode = code;
                    connectionStatusText.setText("房间已创建");
                    roomCodeText.setText("房间码: " + code);
                    showWaitingDialog();
                } else {
                    Toast.makeText(this, "创建房间失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /**
     * 生成6位随机房间码。
     * <p>
     * 使用与 {@link RemoteP2PUtil} 相同的字符集（排除易混淆字符），
     * 确保生成的房间码能通过 {@link RemoteP2PUtil#isValidRoomCode(String)} 校验。
     *
     * @return 6位随机房间码
     */
    protected String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 显示等待对手加入的对话框。
     * <p>
     * 对话框中显示房间码（大号加粗）、复制房间码按钮和操作提示。
     * 对话框不可取消（cancelable=false），用户只能通过"取消"按钮离开房间。
     */
    protected void showWaitingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("等待对手加入");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 48, 48, 48);
        content.setGravity(View.TEXT_ALIGNMENT_CENTER);

        // 大号加粗显示房间码，便于用户查看和分享
        TextView roomCodeView = new TextView(this);
        roomCodeView.setTextSize(28);
        roomCodeView.setText(roomCode);
        roomCodeView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeView.setPadding(0, 16, 0, 16);
        roomCodeView.setTextColor(0xFF2196F3);
        roomCodeView.setTypeface(null, android.graphics.Typeface.BOLD);

        Button copyBtn = new Button(this);
        copyBtn.setText("复制房间码");
        copyBtn.setTextSize(14);
        copyBtn.setBackgroundColor(0xFF4CAF50);
        copyBtn.setTextColor(0xFFFFFFFF);
        copyBtn.setPadding(24, 8, 24, 8);
        // 点击复制房间码到剪贴板
        copyBtn.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("room_code", roomCode);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "房间码已复制", Toast.LENGTH_SHORT).show();
        });

        TextView hintView = new TextView(this);
        hintView.setTextSize(14);
        hintView.setText("请将房间码告诉对手\n\n对手输入房间码加入后，游戏将自动开始");
        hintView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        hintView.setPadding(0, 8, 0, 8);

        content.addView(roomCodeView);
        content.addView(copyBtn);
        content.addView(hintView);

        builder.setView(content);
        // 不可通过点击外部取消，必须通过按钮操作
        builder.setCancelable(false);
        // 取消按钮直接离开房间
        builder.setNegativeButton("取消", (d, w) -> leaveRoom());
        builder.create().show();
    }

    /**
     * 显示加入房间的输入对话框。
     * <p>
     * 弹出输入框让用户输入6位房间码，输入后调用 {@link #joinRoom(String)} 加入房间。
     * 若输入长度不为6，显示错误提示。
     */
    protected void showJoinDialog() {
        EditText input = new EditText(this);
        input.setHint("请输入6位房间码");
        input.setMaxLines(1);

        new AlertDialog.Builder(this)
                .setTitle("加入房间")
                .setView(input)
                .setPositiveButton("加入", (d, w) -> {
                    String code = input.getText().toString().trim();
                    // 简单校验长度，详细校验在 joinRoom 中进行
                    if (code.length() == 6) {
                        joinRoom(code);
                    } else {
                        Toast.makeText(this, "请输入6位房间码", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 加入指定房间。
     * <p>
     * 从偏好设置中读取上次保存的对等端令牌（用于身份识别），
     * 然后通过 WebSocket 客户端连接到中转服务器。
     *
     * @param code 房间码
     */
    protected void joinRoom(String code) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("正在加入房间 " + code + "...");
        roomCode = code;

        // 读取上次保存的令牌，用于服务器识别回连的客户端
        String token = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, code);

        if (token != null) {
            client.setPeerToken(token);
        }

        client.connectWebSocket(wsUrl);
    }

    /**
     * 离开当前房间。
     * <p>
     * 根据角色停止服务器或断开客户端连接，重置游戏状态，
     * 并关闭当前 Activity。
     */
    protected void leaveRoom() {
        if (isHost && server != null) {
            server.stop();
        }
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        isPlaying = false;
        isHost = false;
        finish();
    }

    // ========== 连接回调 ==========

    /**
     * 主机端回调：有客户端连接到本机。
     * <p>
     * 记录对手 ID，标记游戏开始，切换到游戏界面并通知子类游戏开始。
     *
     * @param clientId 客户端连接 ID
     * @param ip       客户端 IP 地址
     */
    protected void onClientConnected(int clientId, String ip) {
        opponentPlayerId = clientId;
        mainHandler.post(() -> {
            connectionStatusText.setText("对手已加入!");
            isPlaying = true;
            showGameScreen();
            onGameStarted();
        });
    }

    /**
     * 主机端回调：客户端断开连接。
     * <p>
     * 标记游戏停止，显示断开连接对话框（主机端提供"等待重连"选项）。
     *
     * @param clientId 客户端连接 ID
     * @param reason   断开原因描述
     */
    protected void onClientDisconnected(int clientId, String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            // isHostSide=true 表示主机端，对话框提供"等待重连"选项
            showDisconnectDialog("对手已断开: " + reason, true);
        });
    }

    /**
     * 客户端回调：成功连接到主机。
     * <p>
     * 设置本方玩家 ID 为2，保存对等端令牌到偏好设置，
     * 切换到游戏界面并通知子类游戏开始。
     *
     * @param clientId 分配的客户端 ID
     */
    protected void onClientConnectedToHost(int clientId) {
        // 加入方的玩家 ID 固定为2
        myPlayerId = 2;
        isPlaying = true;

        // 保存服务器分配的令牌，用于断线重连时的身份识别
        String token = client.getPeerToken();
        if (token != null && !token.isEmpty()) {
            prefs.edit().putString("last_peer_token", token).apply();
        }

        mainHandler.post(() -> {
            connectionStatusText.setText("已连接到主机");
            showGameScreen();
            onGameStarted();
        });
    }

    /**
     * 客户端回调：与主机的连接断开。
     * <p>
     * 标记游戏停止，显示断开连接对话框（客户端提供"重新连接"选项）。
     *
     * @param reason 断开原因描述
     */
    protected void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            // isHostSide=false 表示客户端，对话框提供"重新连接"选项
            showDisconnectDialog("连接已断开: " + reason, false);
        });
    }

    /**
     * 显示连接断开对话框。
     * <p>
     * 根据当前角色（主机端/客户端）显示不同的操作选项：
     * <ul>
     *   <li>主机端（isHostSide=true）：显示"等待重连"按钮，保持房间开放</li>
     *   <li>客户端（isHostSide=false）：显示"重新连接"按钮，尝试重新加入房间</li>
     * </ul>
     * 两侧都有"离开房间"选项。
     * <p>
     * 对话框不可取消，防止用户误触导致状态不一致。
     *
     * @param message     断开原因的描述信息
     * @param isHostSide  是否为主机端
     */
    protected void showDisconnectDialog(String message, boolean isHostSide) {
        // 防止在 Activity 销毁后弹出对话框导致崩溃
        if (isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("连接断开")
                .setMessage(message)
                .setCancelable(false);

        if (isHostSide) {
            // 主机端：等待对手重连
            builder.setPositiveButton("等待重连", (d, w) -> {
                Toast.makeText(this, "等待对手重新连接...", Toast.LENGTH_SHORT).show();
            });
        } else {
            // 客户端：尝试重新连接
            builder.setPositiveButton("重新连接", (d, w) -> {
                if (roomCode != null && !roomCode.isEmpty()) {
                    Toast.makeText(this, "正在重新连接...", Toast.LENGTH_SHORT).show();
                    joinRoom(roomCode);
                } else {
                    // 无房间码无法重连，返回大厅
                    Toast.makeText(this, "无法重连，请重新加入", Toast.LENGTH_SHORT).show();
                    showLobby();
                }
            });
        }

        builder.setNegativeButton("离开房间", (d, w) -> leaveRoom());
        builder.show();
    }

    /**
     * 主机端服务器错误回调。
     *
     * @param message 错误信息
     */
    protected void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 客户端连接错误回调。
     *
     * @param message 错误信息
     */
    protected void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "客户端错误: " + message, Toast.LENGTH_SHORT).show());
    }

    // ========== 消息处理 ==========

    /**
     * 主机端消息接收回调。
     * <p>
     * 先判断是否为聊天消息，若是则交给 {@link OnlineChatHelper} 处理；
     * 否则交给子类的 {@link #onGameMessageReceived(JSONObject)} 处理游戏逻辑。
     *
     * @param clientId 发送消息的客户端 ID
     * @param message  收到的 JSON 消息
     */
    protected void onHostMessageReceived(int clientId, JSONObject message) {
        String type = message.optString("type", "");
        if (chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        onGameMessageReceived(message);
    }

    /**
     * 客户端消息接收回调。
     * <p>
     * 先判断是否为聊天消息，若是则交给 {@link OnlineChatHelper} 处理；
     * 否则交给子类的 {@link #onGameMessageReceived(JSONObject)} 处理游戏逻辑。
     *
     * @param message 收到的 JSON 消息
     */
    protected void onClientMessageReceived(JSONObject message) {
        String type = message.optString("type", "");
        if (chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        onGameMessageReceived(message);
    }

    // ========== UI 辅助 ==========

    /**
     * 切换到游戏界面。
     * <p>
     * 隐藏大厅布局，显示游戏布局。
     */
    protected void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 切换回大厅界面。
     * <p>
     * 隐藏游戏布局，显示大厅布局。
     */
    protected void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 发送聊天消息。
     * <p>
     * 从输入框获取文本，若非空则通过 {@link OnlineChatHelper} 发送并清空输入框。
     */
    protected void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    /**
     * 以主机身份广播 JSON 消息给所有客户端。
     * <p>
     * 仅在主机端有效，客户端调用此方法不会执行任何操作。
     *
     * @param json 要广播的 JSON 消息
     */
    protected void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    // ========== 生命周期 ==========

    /**
     * Activity 销毁时清理资源。
     * <p>
     * 依次清理聊天辅助类、停止服务器、释放客户端连接，
     * 防止资源泄漏和后台线程持续运行。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }
}

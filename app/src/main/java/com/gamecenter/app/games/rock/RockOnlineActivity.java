package com.gamecenter.app.games.rock;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.network.GameSocketClient;
import com.gamecenter.app.network.GameSocketServer;
import com.gamecenter.app.network.OnlineChatHelper;
import com.gamecenter.app.network.RelayHttpClient;
import com.gamecenter.app.network.RemoteP2PUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

/**
 * 石头剪刀布联机对战 Activity
 * <p>
 * 通过 WebSocket 中继服务器实现双人实时对战，支持房间创建/加入、
 * 实时出拳、回合结果判定、在线聊天等功能。
 * <p>
 * 职责：
 * - 管理房间的创建（主机模式）和加入（客户端模式）
 * - 处理网络消息的收发和游戏状态同步
 * - 渲染游戏界面（纯代码构建，无 XML 布局）
 * - 管理在线聊天功能
 * <p>
 * 关键设计决策：
 * - 主机（Host）同时运行 GameSocketServer 和作为玩家参与游戏
 * - 客户端通过 GameSocketClient 连接到中继服务器
 * - 使用 currentStateVersion 实现乐观并发控制，防止乱序消息覆盖最新状态
 * - 双方出拳后由主机统一判定结果并广播，避免双方判定不一致
 * - 所有 UI 更新通过 mainHandler.post() 切换到主线程执行
 */
public class RockOnlineActivity extends AppCompatActivity {

    /** SharedPreferences 文件名，用于存储 P2P 令牌 */
    private static final String P2P_PREFS = "rock_p2p";
    /** 网络协议标识 */
    private static final String PROTOCOL = "ROCK";
    /** 中继服务器基础 URL */
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    private SharedPreferences prefs;
    /** 主机模式下的 WebSocket 服务器 */
    private GameSocketServer server;
    /** 客户端模式下的 WebSocket 客户端 */
    private GameSocketClient client;

    /** 是否为主机模式 */
    private volatile boolean isHost = false;
    /** 是否正在游戏中 */
    private volatile boolean isPlaying = false;
    /** 是否轮到我出拳 */
    private volatile boolean isMyTurn = false;
    /** 我的玩家编号（1=主机，2=客户端） */
    private int myPlayerId = -1;
    /** 对手的玩家编号 */
    private int opponentPlayerId = -1;
    /** 房间码 */
    private String roomCode = "";

    /** 客户端模式下的出拳选择 */
    private int clientChoice = -1;
    /** 主机模式下自己的出拳选择 */
    private int hostPlayerChoice = -1;
    /** 主机模式下对手的出拳选择（从网络接收） */
    private int hostOpponentChoice = -1;

    /** 状态版本号，用于防止旧消息覆盖新状态（乐观并发控制） */
    private volatile long currentStateVersion = 0;
    /** 是否已收到对手的出拳（主机模式） */
    private volatile int remoteChoiceReceived = -1;
    /** 在线聊天辅助类 */
    private OnlineChatHelper chatHelper;

    /** 主线程 Handler，用于从网络线程切换到 UI 线程 */
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    /** 大厅布局（创建/加入房间界面） */
    private LinearLayout lobbyLayout;
    /** 游戏布局（出拳/结果界面） */
    private FrameLayout gameLayout;
    /** 房间码显示文字 */
    private TextView roomCodeText;
    /** 连接状态文字 */
    private TextView connectionStatusText;
    /** 回合结果文字 */
    private TextView statusText;
    /** 回合结果文字 */
    private TextView resultText;
    /** 加载进度条 */
    private ProgressBar loadingBar;

    /** 玩家得分文字 */
    private TextView scorePlayerText;
    /** 对手得分文字 */
    private TextView scoreOpponentText;
    /** 回合状态文字 */
    private TextView turnStatusText;

    /** 石头按钮 */
    private Button buttonRock;
    /** 布按钮 */
    private Button buttonPaper;
    /** 剪刀按钮 */
    private Button buttonScissors;

    /** 聊天消息显示区域 */
    private TextView chatDisplay;
    /** 聊天消息滚动容器 */
    private ScrollView chatScroll;
    /** 聊天输入框 */
    private EditText chatInput;

    /**
     * Activity 创建时初始化网络组件、聊天辅助和界面
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        String savedToken = prefs.getString("last_peer_token", null);

        // 初始化主机端服务器及回调
        server = new GameSocketServer(this);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);

        // 初始化客户端及回调
        client = GameSocketClient.getInstance(this);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);

        // 初始化聊天辅助类，设置消息发送回调
        chatHelper = new OnlineChatHelper(this);
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

        initViews();

        chatHelper.setInlineDisplay(chatDisplay, chatScroll);
    }

    /**
     * 初始化所有 UI 组件（纯代码构建，无 XML 布局）
     * <p>
     * 界面分为两层：
     * - lobbyLayout：大厅界面，包含创建/加入房间按钮
     * - gameLayout：游戏界面，包含得分、出拳按钮、聊天区域
     */
    private void initViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // === 大厅布局 ===
        lobbyLayout = new LinearLayout(this);
        lobbyLayout.setOrientation(LinearLayout.VERTICAL);
        lobbyLayout.setPadding(48, 48, 48, 48);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView titleText = new TextView(this);
        titleText.setText("石头剪刀布 - 联机对战");
        titleText.setTextSize(24);
        titleText.setTextColor(0xFF1E1E32);
        titleText.setGravity(View.TEXT_ALIGNMENT_CENTER);

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

        roomCodeText = new TextView(this);
        roomCodeText.setTextSize(20);
        roomCodeText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeText.setPadding(0, 16, 0, 16);

        connectionStatusText = new TextView(this);
        connectionStatusText.setTextSize(16);
        connectionStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        connectionStatusText.setPadding(0, 8, 0, 8);

        lobbyLayout.addView(titleText);
        lobbyLayout.addView(createRoomBtn);
        lobbyLayout.addView(joinRoomBtn);
        lobbyLayout.addView(loadingBar);
        lobbyLayout.addView(roomCodeText);
        lobbyLayout.addView(connectionStatusText);

        // === 游戏布局 ===
        gameLayout = new FrameLayout(this);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f));
        gameLayout.setVisibility(View.GONE);

        LinearLayout gameContent = new LinearLayout(this);
        gameContent.setOrientation(LinearLayout.VERTICAL);

        // 得分栏：玩家 | 回合状态 | 对手
        LinearLayout scoreLayout = new LinearLayout(this);
        scoreLayout.setOrientation(LinearLayout.HORIZONTAL);
        scoreLayout.setPadding(16, 8, 16, 8);

        scorePlayerText = new TextView(this);
        scorePlayerText.setTextSize(18);
        scorePlayerText.setTextColor(0xFF4CAF50);
        scorePlayerText.setText("你: 0");
        scorePlayerText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        turnStatusText = new TextView(this);
        turnStatusText.setTextSize(16);
        turnStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        turnStatusText.setTextColor(0xFFFF9800);
        turnStatusText.setText("等待对手...");
        turnStatusText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        scoreOpponentText = new TextView(this);
        scoreOpponentText.setTextSize(18);
        scoreOpponentText.setTextColor(0xFFE53935);
        scoreOpponentText.setGravity(View.TEXT_ALIGNMENT_TEXT_END);
        scoreOpponentText.setText("对手: 0");
        scoreOpponentText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        scoreLayout.addView(scorePlayerText);
        scoreLayout.addView(turnStatusText);
        scoreLayout.addView(scoreOpponentText);

        resultText = new TextView(this);
        resultText.setTextSize(22);
        resultText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        resultText.setPadding(16, 24, 16, 24);
        resultText.setTextColor(0xFFFFD700);

        // 出拳按钮栏
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(View.TEXT_ALIGNMENT_CENTER);
        buttonLayout.setPadding(16, 16, 16, 16);

        buttonRock = createChoiceButton("石头", "✊", 0xFFE53935, v -> makeChoice(0));
        buttonPaper = createChoiceButton("布", "✋", 0xFF43A047, v -> makeChoice(2));
        buttonScissors = createChoiceButton("剪刀", "✌️", 0xFF1E88E5, v -> makeChoice(1));

        buttonLayout.addView(buttonRock);
        buttonLayout.addView(buttonPaper);
        buttonLayout.addView(buttonScissors);

        // 聊天显示区域
        chatDisplay = new TextView(this);
        chatDisplay.setTextSize(12);
        chatDisplay.setBackgroundColor(0xFFF5F5F5);
        chatDisplay.setPadding(12, 8, 12, 8);
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

        // 聊天输入栏
        LinearLayout chatInputRow = new LinearLayout(this);
        chatInputRow.setOrientation(LinearLayout.HORIZONTAL);
        chatInputRow.setPadding(8, 0, 8, 4);

        chatInput = new EditText(this);
        chatInput.setHint("输入消息...");
        chatInput.setSingleLine(true);
        chatInput.setTextSize(14);
        chatInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button chatSendBtn = new Button(this);
        chatSendBtn.setText("发送");
        chatSendBtn.setTextSize(14);
        chatSendBtn.setBackgroundColor(0xFF9C27B0);
        chatSendBtn.setTextColor(0xFFFFFFFF);
        chatSendBtn.setPadding(16, 8, 16, 8);
        chatSendBtn.setOnClickListener(v -> sendChatMessage());

        // 键盘回车键发送消息
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            sendChatMessage();
            return true;
        });

        chatInputRow.addView(chatInput);
        chatInputRow.addView(chatSendBtn);

        // 离开房间按钮
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

        gameContent.addView(scoreLayout);
        gameContent.addView(resultText);
        gameContent.addView(buttonLayout);
        gameContent.addView(chatScroll);
        gameContent.addView(chatInputRow);
        gameContent.addView(btnRow);

        gameLayout.addView(gameContent);

        root.addView(lobbyLayout);
        root.addView(gameLayout);

        setContentView(root);
    }

    /**
     * 发送聊天消息
     */
    private void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    /**
     * 创建出拳按钮（包含 Emoji 和名称）
     *
     * @param name     按钮名称（如"石头"）
     * @param emoji    Emoji 表情
     * @param color    按钮背景色
     * @param listener 点击监听器
     * @return 构建好的 Button
     */
    private Button createChoiceButton(String name, String emoji, int color, View.OnClickListener listener) {
        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        btnContainer.setGravity(View.TEXT_ALIGNMENT_CENTER);
        btnContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button btn = new Button(this);
        btn.setText(emoji + "\n" + name);
        btn.setTextSize(18);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setPadding(16, 24, 16, 24);
        btn.setOnClickListener(listener);

        btnContainer.addView(btn);
        return btn;
    }

    /**
     * 创建房间（主机模式）
     * <p>
     * 生成 6 位房间码，启动 WebSocket 服务器连接到中继服务器，
     * 成功后显示等待对话框。
     */
    private void createRoom() {
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
     * 生成 6 位随机房间码
     * <p>
     * 字符集排除容易混淆的字符（I/O/0/1），使用大写字母和数字组合。
     *
     * @return 6 位房间码字符串
     */
    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 显示等待对手加入的对话框
     * <p>
     * 包含房间码显示、复制按钮和操作提示。
     */
    private void showWaitingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("等待对手加入");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 48, 48, 48);
        content.setGravity(View.TEXT_ALIGNMENT_CENTER);

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
        copyBtn.setOnClickListener(v -> {
            // 复制房间码到剪贴板
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
        builder.setCancelable(false);
        builder.setNegativeButton("取消", (d, w) -> leaveRoom());
        builder.create().show();
    }

    /**
     * 显示加入房间的对话框
     * <p>
     * 弹出输入框让用户输入 6 位房间码。
     */
    private void showJoinDialog() {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("准备加入...");

        EditText roomCodeInput = new EditText(this);
        roomCodeInput.setHint("请输入6位房间码");
        roomCodeInput.setMaxLines(1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("加入房间");
        builder.setView(roomCodeInput);
        builder.setPositiveButton("加入", (dialog, which) -> {
            String code = roomCodeInput.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "请输入房间码", Toast.LENGTH_SHORT).show();
                return;
            }
            joinRoom(code);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 加入指定房间（客户端模式）
     * <p>
     * 使用保存的 P2P 令牌（如有）建立 WebSocket 连接。
     *
     * @param roomCode 目标房间码
     */
    private void joinRoom(String roomCode) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("正在加入房间: " + roomCode);

        String savedToken = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, roomCode);

        if (savedToken != null) {
            client.setPeerToken(savedToken);
        }

        client.connectWebSocket(wsUrl);
    }

    /**
     * 主机模式回调：客户端连接成功
     * <p>
     * 记录对手 ID，切换到游戏界面并开始第一轮。
     *
     * @param clientId 客户端 ID
     * @param ip       客户端 IP 地址
     */
    private void onClientConnected(int clientId, String ip) {
        opponentPlayerId = clientId;
        mainHandler.post(() -> {
            connectionStatusText.setText("对手已加入!");
            isPlaying = true;
            isMyTurn = true;
            showGameScreen();
            startNewRound();
        });
    }

    /**
     * 主机模式回调：客户端断开连接
     *
     * @param clientId 断开的客户端 ID
     * @param reason   断开原因
     */
    private void onClientDisconnected(int clientId, String reason) {
        if (clientId == opponentPlayerId) {
            mainHandler.post(() -> {
                Toast.makeText(this, "对手已断开: " + reason, Toast.LENGTH_SHORT).show();
                isPlaying = false;
                showLobby();
            });
        }
    }

    /**
     * 客户端模式回调：成功连接到主机
     * <p>
     * 保存 P2P 令牌用于后续重连，切换到游戏界面。
     *
     * @param clientId 分配的客户端 ID
     */
    private void onClientConnectedToHost(int clientId) {
        myPlayerId = 2;
        isPlaying = true;

        // 保存令牌以便重连时使用
        String token = client.getPeerToken();
        if (token != null && !token.isEmpty()) {
            prefs.edit().putString("last_peer_token", token).apply();
        }

        mainHandler.post(() -> {
            connectionStatusText.setText("已连接到主机");
            showGameScreen();
        });
    }

    /**
     * 客户端模式回调：与主机断开连接
     *
     * @param reason 断开原因
     */
    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            Toast.makeText(this, "连接断开: " + reason, Toast.LENGTH_SHORT).show();
            isPlaying = false;
            showLobby();
        });
    }

    /**
     * 主机模式错误回调
     *
     * @param message 错误信息
     */
    private void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 客户端模式错误回调
     *
     * @param message 错误信息
     */
    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "客户端错误: " + message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 主机模式回调：收到客户端消息
     * <p>
     * 处理消息类型：
     * - THROW：客户端出拳
     * - SYNC_STATE：状态同步
     * - RECONNECT_STATE：重连状态恢复
     * - GAME_OVER：游戏结束
     *
     * @param clientId 发送者 ID
     * @param message  JSON 消息
     */
    private void onHostMessageReceived(int clientId, JSONObject message) {
        try {
            String type = message.optString("type", "");
            if (chatHelper.isChatMessage(message)) {
                chatHelper.handleIncomingChat(message);
                return;
            }
            switch (type) {
                case "THROW":
                    handleHostThrow(clientId, message);
                    break;
                case "SYNC_STATE":
                    handleClientSyncState(message);
                    break;
                case "RECONNECT_STATE":
                    handleReconnectState(message);
                    break;
                case "GAME_OVER":
                    handleGameOver(message);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 客户端模式回调：收到主机消息
     * <p>
     * 处理消息类型：
     * - SYNC_STATE：状态同步（包含双方出拳和结果）
     * - RECONNECT_STATE：重连状态恢复
     * - GAME_OVER：回合结果
     * - START_ROUND：新回合开始
     *
     * @param message JSON 消息
     */
    private void onClientMessageReceived(JSONObject message) {
        try {
            String type = message.optString("type", "");
            if (chatHelper.isChatMessage(message)) {
                chatHelper.handleIncomingChat(message);
                return;
            }
            switch (type) {
                case "SYNC_STATE":
                    handleClientSyncState(message);
                    break;
                case "RECONNECT_STATE":
                    handleReconnectState(message);
                    break;
                case "GAME_OVER":
                    handleGameOver(message);
                    break;
                case "START_ROUND":
                    mainHandler.post(() -> {
                        isMyTurn = true;
                        turnStatusText.setText("轮到你出拳!");
                        setChoiceButtonsEnabled(true);
                    });
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 主机模式处理出拳消息
     * <p>
     * 当双方都已出拳时，由主机统一判定结果。
     * 如果主机先出拳，等待客户端出拳；反之亦然。
     *
     * @param clientId 出拳者的客户端 ID
     * @param message  包含 choice 字段的 JSON 消息
     */
    private void handleHostThrow(int clientId, JSONObject message) throws JSONException {
        int choice = message.getInt("choice");

        if (clientId == opponentPlayerId) {
            // 对手出拳
            remoteChoiceReceived = choice;

            // 如果主机也已出拳，判定结果
            if (hostPlayerChoice >= 0) {
                resolveRound();
            }
        } else {
            // 主机自己出拳
            hostPlayerChoice = choice;

            // 如果对手已出拳，判定结果
            if (remoteChoiceReceived >= 0) {
                resolveRound();
            }
        }
    }

    /**
     * 由主机执行回合结果判定
     * <p>
     * 判定规则（与单机模式一致）：
     * - 石头(0) 胜 剪刀(1)
     * - 剪刀(1) 胜 布(2)
     * - 布(2) 胜 石头(0)
     * <p>
     * result: 0=平局, 1=玩家1(主机)赢, 2=玩家2(客户端)赢
     */
    private void resolveRound() {
        int player1Choice = hostPlayerChoice;
        int player2Choice = remoteChoiceReceived;

        int result;
        if (player1Choice == player2Choice) {
            result = 0;
        } else if ((player1Choice == 0 && player2Choice == 1)
                || (player1Choice == 1 && player2Choice == 2)
                || (player1Choice == 2 && player2Choice == 0)) {
            result = 1;
        } else {
            result = 2;
        }

        // 广播同步状态和结果给所有客户端
        sendSyncState(player1Choice, player2Choice, result);
        broadcastResult(player1Choice, player2Choice, result);

        final int myChoice = player1Choice;
        final int opponentChoice = player2Choice;
        final int finalResult = result;
        mainHandler.post(() -> showRoundResult(myChoice, opponentChoice, finalResult));

        // 2 秒后开始下一轮
        scheduleNextRound();
    }

    /**
     * 发送状态同步消息给所有客户端
     * <p>
     * 包含双方出拳、结果和状态版本号，用于确保所有客户端状态一致。
     *
     * @param p1Choice 玩家1出拳
     * @param p2Choice 玩家2出拳
     * @param result   对局结果
     */
    private void sendSyncState(int p1Choice, int p2Choice, int result) {
        try {
            currentStateVersion++;
            JSONObject state = new JSONObject();
            state.put("type", "SYNC_STATE");
            state.put("stateVersion", currentStateVersion);
            state.put("p1Choice", p1Choice);
            state.put("p2Choice", p2Choice);
            state.put("result", result);
            state.put("isMyTurn", true);
            state.put("gameOver", false);
            broadcast(state);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 广播回合结果消息
     *
     * @param p1Choice 玩家1出拳
     * @param p2Choice 玩家2出拳
     * @param result   对局结果
     */
    private void broadcastResult(int p1Choice, int p2Choice, int result) {
        try {
            JSONObject resultMsg = new JSONObject();
            resultMsg.put("type", "GAME_OVER");
            resultMsg.put("p1Choice", p1Choice);
            resultMsg.put("p2Choice", p2Choice);
            resultMsg.put("result", result);
            resultMsg.put("isMyTurn", true);
            broadcast(resultMsg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 调度下一轮，2 秒延迟后重置出拳状态并开始新回合
     */
    private void scheduleNextRound() {
        mainHandler.postDelayed(() -> {
            hostPlayerChoice = -1;
            remoteChoiceReceived = -1;
            isMyTurn = true;
            startNewRound();
        }, 2000);
    }

    /**
     * 处理状态同步消息（主机和客户端共用）
     * <p>
     * 使用 currentStateVersion 实现乐观并发控制：
     * 如果收到的消息版本号不大于当前版本号，则忽略该消息（防止乱序）。
     *
     * @param message SYNC_STATE 消息
     */
    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        // 忽略旧版本消息，防止乱序覆盖
        if (version <= currentStateVersion) return;
        currentStateVersion = version;

        int p1Choice = message.optInt("p1Choice", -1);
        int p2Choice = message.optInt("p2Choice", -1);
        int result = message.optInt("result", -1);

        // 根据自己的玩家编号确定"我"和"对手"的出拳
        final int myChoice, opponentChoice;
        if (myPlayerId == 1) {
            myChoice = p1Choice;
            opponentChoice = p2Choice;
        } else {
            myChoice = p2Choice;
            opponentChoice = p1Choice;
        }

        mainHandler.post(() -> {
            clientChoice = myChoice;
            showRoundResult(myChoice, opponentChoice, result);
        });
    }

    /**
     * 处理重连状态恢复消息
     *
     * @param message RECONNECT_STATE 消息
     */
    private void handleReconnectState(JSONObject message) throws JSONException {
        currentStateVersion = message.optLong("stateVersion", 0);

        mainHandler.post(() -> {
            isPlaying = true;
            showGameScreen();
        });
    }

    /**
     * 处理游戏结束/回合结果消息
     * <p>
     * 根据自己的玩家编号映射"我"和"对手"的出拳。
     *
     * @param message GAME_OVER 消息
     */
    private void handleGameOver(JSONObject message) throws JSONException {
        int result = message.optInt("result", -1);
        int p1Choice = message.optInt("p1Choice", -1);
        int p2Choice = message.optInt("p2Choice", -1);

        final int myChoice, opponentChoice;
        if (!isHost && myPlayerId == 2) {
            myChoice = p2Choice;
            opponentChoice = p1Choice;
        } else {
            myChoice = p1Choice;
            opponentChoice = p2Choice;
        }

        mainHandler.post(() -> showRoundResult(myChoice, opponentChoice, result));
    }

    /**
     * 显示回合结果
     * <p>
     * 根据结果和玩家编号判断胜负，更新结果文字和颜色。
     * result: 0=平局, 1=玩家1赢, 2=玩家2赢
     *
     * @param myChoice       我的出拳
     * @param opponentChoice 对手的出拳
     * @param result         对局结果
     */
    private void showRoundResult(int myChoice, int opponentChoice, int result) {
        String[] names = {"石头", "剪刀", "布"};
        String[] emojis = {"✊", "✌️", "✋"};

        String resultText;
        int resultColor;

        if (result == 0) {
            resultText = "平局!";
            resultColor = 0xFFFF9800;
        } else if (myPlayerId == 1) {
            // 玩家1：result=1 表示我赢
            if (result == 1) {
                resultText = "你赢了!";
                resultColor = 0xFF4CAF50;
            } else {
                resultText = "对手赢了!";
                resultColor = 0xFFE53935;
            }
        } else {
            // 玩家2：result=2 表示我赢
            if (result == 2) {
                resultText = "你赢了!";
                resultColor = 0xFF4CAF50;
            } else {
                resultText = "对手赢了!";
                resultColor = 0xFFE53935;
            }
        }

        this.resultText.setText(resultText);
        this.resultText.setTextColor(resultColor);
        isMyTurn = false;
        setChoiceButtonsEnabled(false);
    }

    /**
     * 开始新一轮
     * <p>
     * 重置出拳状态，启用出拳按钮。
     * 主机额外广播 START_ROUND 消息通知客户端。
     */
    private void startNewRound() {
        clientChoice = -1;
        mainHandler.post(() -> {
            resultText.setText("请出拳!");
            resultText.setTextColor(0xFFFFFFFF);
            turnStatusText.setText("轮到你出拳!");
            setChoiceButtonsEnabled(true);
        });

        // 主机广播新回合开始消息
        if (isHost) {
            try {
                JSONObject startMsg = new JSONObject();
                startMsg.put("type", "START_ROUND");
                broadcast(startMsg);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 玩家做出出拳选择
     * <p>
     * 主机模式：直接调用 onHostMessageReceived 模拟收到自己的出拳消息
     * 客户端模式：通过网络发送 THROW 消息给主机
     *
     * @param choice 出拳选择（0=石头, 1=剪刀, 2=布）
     */
    private void makeChoice(int choice) {
        if (!isPlaying || !isMyTurn) return;

        clientChoice = choice;
        isMyTurn = false;
        setChoiceButtonsEnabled(false);

        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "THROW");
            msg.put("choice", choice);

            if (isHost) {
                // 主机直接在本地处理自己的出拳
                hostPlayerChoice = choice;
                onHostMessageReceived(myPlayerId, msg);
            } else {
                // 客户端发送出拳消息给主机
                client.send(msg);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 广播消息给所有连接的客户端（仅主机模式有效）
     *
     * @param json 要广播的 JSON 消息
     */
    private void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    /**
     * 设置出拳按钮的启用/禁用状态
     * <p>
     * 禁用时按钮半透明，防止重复点击。
     *
     * @param enabled true 启用，false 禁用
     */
    private void setChoiceButtonsEnabled(boolean enabled) {
        buttonRock.setEnabled(enabled);
        buttonPaper.setEnabled(enabled);
        buttonScissors.setEnabled(enabled);
        buttonRock.setAlpha(enabled ? 1.0f : 0.5f);
        buttonPaper.setAlpha(enabled ? 1.0f : 0.5f);
        buttonScissors.setEnabled(enabled);
        buttonScissors.setAlpha(enabled ? 1.0f : 0.5f);
    }

    /**
     * 切换到游戏界面，隐藏大厅
     */
    private void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
        isPlaying = true;
    }

    /**
     * 切换到大厅界面，隐藏游戏
     */
    private void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
        isPlaying = false;
    }

    /**
     * 离开房间，停止服务器/断开客户端连接，并关闭 Activity
     */
    private void leaveRoom() {
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

    /**
     * Activity 销毁时清理网络资源和聊天辅助类
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }

    /**
     * 获取出拳选择的中文名称
     *
     * @param choice 选择值（0/1/2）
     * @return 中文名称
     */
    private String getChoiceName(int choice) {
        switch (choice) {
            case 0: return "石头";
            case 1: return "剪刀";
            case 2: return "布";
            default: return "?";
        }
    }

    /**
     * 获取出拳选择的 Emoji 表情
     *
     * @param choice 选择值（0/1/2）
     * @return Emoji 字符串
     */
    private String getChoiceEmoji(int choice) {
        switch (choice) {
            case 0: return "✊";
            case 1: return "✌️";
            case 2: return "✋";
            default: return "?";
        }
    }
}

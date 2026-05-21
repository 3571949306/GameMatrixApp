package com.gamecenter.app.games.go;

import android.app.AlertDialog;
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

/**
 * 围棋联机对战Activity。
 * <p>
 * 实现基于WebSocket中继服务器的双人在线围棋对局，支持：
 * <ul>
 *   <li>创建/加入房间（6位房间码）</li>
 *   <li>主机-客户端架构：主机负责权威状态同步，客户端发送操作请求</li>
 *   <li>实时聊天功能</li>
 *   <li>虚手（Pass）操作与双方连续虚手终局</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>采用"主机权威"模型：主机维护游戏状态的唯一权威副本，每次落子后向客户端广播完整状态同步</li>
 *   <li>状态版本号（{@link #currentStateVersion}）用于防止旧消息覆盖新状态</li>
 *   <li>提子数由Activity层独立维护，因为GoGame的提子计数与联机同步逻辑存在差异</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是围棋联机对战的"网络大厅+对局室"，和五子棋的GomokuOnlineActivity结构类似。
 * 主要区别：围棋有虚手（Pass）操作，双方连续虚手则对局结束。
 * 提子数的计算比较特殊，由Activity层独立维护而不是直接用GoGame的计数。
 */
public class GoOnlineActivity extends AppCompatActivity {

    /** SharedPreferences文件名，存储P2P令牌等持久化数据 */
    private static final String P2P_PREFS = "go_p2p";

    /** 协议标识 */
    private static final String PROTOCOL = "GO";

    /** 中继服务器基础URL */
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    /** 偏好设置，用于持久化对等端令牌 */
    private SharedPreferences prefs;

    /** WebSocket服务器实例（主机模式使用） */
    private GameSocketServer server;

    /** WebSocket客户端实例（加入模式使用） */
    private GameSocketClient client;

    /** 是否为主机（创建房间者） */
    private volatile boolean isHost = false;

    /** 对局是否正在进行 */
    private volatile boolean isPlaying = false;

    /** 对手是否已加入房间 */
    private volatile boolean opponentHasJoined = false;

    /** 本机玩家ID（主机=1，客户端=2） */
    private int myPlayerId = -1;

    /** 对手玩家ID */
    private int opponentPlayerId = -1;

    /** 当前房间码 */
    private String roomCode = "";

    /** 围棋游戏逻辑对象 */
    private GoGame game;

    /** 状态版本号，用于防止旧同步消息覆盖新状态 */
    private volatile long currentStateVersion = 0;

    /** 在线聊天辅助类 */
    private OnlineChatHelper chatHelper;

    /** 本机执子颜色（黑=1，白=2） */
    private int myColor;

    /** 黑方提子数（Activity层独立维护，与GoGame内部计数分离）
     *  为什么不直接用GoGame的计数？因为联机同步时需要精确控制提子数，
     *  而GoGame在重放历史时可能会重复计算提子，所以Activity自己维护一份 */
    private int blackCaptures;

    /** 白方提子数 */
    private int whiteCaptures;

    /** 最后一手坐标 */
    private int[] lastMovePos;

    /** 对局是否结束标志 */
    private volatile boolean isGameOver = false;

    /** 主线程Handler，用于跨线程UI更新 */
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 大厅布局（创建/加入房间界面） */
    private LinearLayout lobbyLayout;

    /** 游戏布局（棋盘+控制界面） */
    private FrameLayout gameLayout;

    /** 房间码显示文本 */
    private TextView roomCodeText;

    /** 连接状态文本 */
    private TextView connectionStatusText;

    /** 回合状态文本 */
    private TextView turnStatusText;

    /** 胜负结果文本 */
    private TextView winnerText;

    /** 提子数显示文本 */
    private TextView captureText;

    /** 加载进度条 */
    private ProgressBar loadingBar;

    /** 棋盘视图 */
    private GoView boardView;

    /** 聊天消息显示区域 */
    private TextView chatDisplay;

    /** 聊天消息滚动容器 */
    private ScrollView chatScroll;

    /** 聊天输入框 */
    private EditText chatInput;

    /**
     * Activity创建时的初始化入口。
     * <p>
     * 初始化网络组件（服务器/客户端）、聊天辅助类、视图，
     * 并设置各类网络事件监听器。
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        game = new GoGame();

        initViews();

        // 初始化服务器（主机模式）
        server = new GameSocketServer(this);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);

        // 初始化客户端（加入模式）
        client = GameSocketClient.getInstance(this);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);

        // 初始化聊天辅助类
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

        chatHelper.setInlineDisplay(chatDisplay, chatScroll);
    }

    /**
     * 初始化所有视图组件，采用纯代码构建布局（无XML布局文件）。
     * <p>
     * 界面分为两层：大厅层（创建/加入房间）和游戏层（棋盘+聊天+控制），
     * 通过切换可见性实现界面切换。
     */
    private void initViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        lobbyLayout = new LinearLayout(this);
        lobbyLayout.setOrientation(LinearLayout.VERTICAL);
        lobbyLayout.setPadding(48, 48, 48, 48);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView titleText = new TextView(this);
        titleText.setText("围棋 - 联机对战");
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

        gameLayout = new FrameLayout(this);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f));
        gameLayout.setVisibility(View.GONE);

        LinearLayout gameContent = new LinearLayout(this);
        gameContent.setOrientation(LinearLayout.VERTICAL);

        turnStatusText = new TextView(this);
        turnStatusText.setTextSize(18);
        turnStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        turnStatusText.setTextColor(0xFFFF9800);
        turnStatusText.setPadding(8, 8, 8, 4);

        captureText = new TextView(this);
        captureText.setTextSize(14);
        captureText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        captureText.setTextColor(0xFF9E9E9E);
        captureText.setPadding(8, 4, 8, 4);

        winnerText = new TextView(this);
        winnerText.setTextSize(20);
        winnerText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        winnerText.setTextColor(0xFF4CAF50);
        winnerText.setPadding(8, 4, 8, 4);

        boardView = new GoView(this);
        boardView.setGame(game);
        boardView.setOnCellClickListener(this::placeStone);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f);
        boardView.setLayoutParams(boardParams);

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

        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            sendChatMessage();
            return true;
        });

        chatInputRow.addView(chatInput);
        chatInputRow.addView(chatSendBtn);

        Button passBtn = new Button(this);
        passBtn.setText("虚手");
        passBtn.setTextSize(14);
        passBtn.setBackgroundColor(0xFFFF9800);
        passBtn.setTextColor(0xFFFFFFFF);
        passBtn.setPadding(16, 8, 16, 8);
        passBtn.setOnClickListener(v -> passMove());

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
        btnRow.addView(passBtn);
        btnRow.addView(leaveBtn);

        gameContent.addView(turnStatusText);
        gameContent.addView(captureText);
        gameContent.addView(winnerText);
        gameContent.addView(boardView);
        gameContent.addView(chatScroll);
        gameContent.addView(chatInputRow);
        gameContent.addView(btnRow);

        gameLayout.addView(gameContent);

        root.addView(lobbyLayout);
        root.addView(gameLayout);

        setContentView(root);
    }

    /**
     * 发送聊天消息。
     * <p>
     * 读取输入框内容，通过聊天辅助类发送并清空输入框。
     */
    private void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    /**
     * 创建联机房间。
     * <p>
     * 在后台线程生成房间码并启动WebSocket服务器，
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
     * 生成6位随机房间码。
     * <p>
     * 使用去除易混淆字符（0/O/1/I）的字母数字字符集。
     *
     * @return 6位房间码字符串
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
     * 显示等待对手加入的对话框。
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
     * 显示加入房间的输入对话框。
     * <p>
     * 用户输入6位房间码后调用 {@link #joinRoom}。
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
     * 加入指定房间码的联机房间。
     * <p>
     * 使用WebSocket客户端连接到中继服务器，并恢复上次保存的对等端令牌。
     *
     * @param roomCode 目标房间码
     */
    private void joinRoom(String roomCode) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("正在加入房间: " + roomCode);

        // 恢复上次保存的P2P令牌，用于NAT穿透
        String savedToken = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, roomCode);

        if (savedToken != null) {
            client.setPeerToken(savedToken);
        }

        client.connectWebSocket(wsUrl);
    }

    /**
     * 主机端回调：客户端连接成功。
     * <p>
     * 记录对手ID，标记对手已加入，并启动游戏。
     *
     * @param clientId 客户端ID
     * @param ip       客户端IP地址
     */
    private void onClientConnected(int clientId, String ip) {
        opponentPlayerId = clientId;
        opponentHasJoined = true;
        mainHandler.post(() -> {
            connectionStatusText.setText("对手已加入!");
            isPlaying = true;
            startGame();
        });
    }

    /**
     * 主机端回调：客户端断开连接。
     *
     * @param clientId 断开的客户端ID
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
     * 客户端回调：成功连接到主机。
     * <p>
     * 设置本机ID为2，保存P2P令牌，并启动游戏。
     *
     * @param clientId 分配的客户端ID
     */
    private void onClientConnectedToHost(int clientId) {
        myPlayerId = 2;
        isPlaying = true;

        // 保存P2P令牌以便下次重连
        String token = client.getPeerToken();
        if (token != null && !token.isEmpty()) {
            prefs.edit().putString("last_peer_token", token).apply();
        }

        mainHandler.post(() -> {
            connectionStatusText.setText("已连接到主机");
            startGame();
        });
    }

    /**
     * 客户端回调：与主机断开连接。
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
     * 主机端错误回调。
     *
     * @param message 错误信息
     */
    private void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 客户端错误回调。
     *
     * @param message 错误信息
     */
    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "客户端错误: " + message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 启动游戏，初始化对局状态。
     * <p>
     * 主机执黑（先手），客户端执白。重置游戏并切换到游戏界面。
     */
    private void startGame() {
        game.reset();
        currentStateVersion = 0;
        myColor = myPlayerId == 1 ? GoGame.BLACK : GoGame.WHITE;
        blackCaptures = 0;
        whiteCaptures = 0;
        lastMovePos = null;
        isGameOver = false;
        showGameScreen();
        updateTurnStatus();
    }

    /**
     * 更新回合状态显示。
     * <p>
     * 根据当前执子方和本机颜色，显示"轮到你落子"或"等待对手..."。
     * 对局结束时显示结果和提子数。
     */
    private void updateTurnStatus() {
        boolean isMyTurn = game.getCurrentPlayer() == myColor;

        if (isGameOver) {
            winnerText.setText("游戏结束 - 双方虚手");
            turnStatusText.setText("游戏结束");
        } else {
            winnerText.setText("");
            turnStatusText.setText(isMyTurn ? "轮到你落子" : "等待对手...");
        }

        int myCaptures = myColor == GoGame.BLACK ? blackCaptures : whiteCaptures;
        int oppCaptures = myColor == GoGame.BLACK ? whiteCaptures : blackCaptures;
        captureText.setText("提子 - 你: " + myCaptures + " | 对手: " + oppCaptures);

        boardView.invalidate();
    }

    /**
     * 主机端消息处理回调。
     * <p>
     * 处理来自客户端的PLACE_STONE、PASS、SYNC_STATE、GAME_OVER消息，
     * 以及聊天消息。主机收到客户端落子后，执行落子并广播状态同步。
     *
     * @param clientId 发送方客户端ID
     * @param message  JSON消息对象
     */
    private void onHostMessageReceived(int clientId, JSONObject message) {
        try {
            String type = message.optString("type", "");
            if (chatHelper.isChatMessage(message)) {
                chatHelper.handleIncomingChat(message);
                return;
            }
            switch (type) {
                case "PLACE_STONE":
                    handlePlaceStone(clientId, message);
                    break;
                case "PASS":
                    handlePass(clientId);
                    break;
                case "SYNC_STATE":
                    handleClientSyncState(message);
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
     * 客户端消息处理回调。
     * <p>
     * 客户端仅接收主机广播的SYNC_STATE和GAME_OVER消息。
     *
     * @param message JSON消息对象
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
                case "GAME_OVER":
                    handleGameOver(message);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理对手落子消息（主机端）。
     * <p>
     * 验证发送方为对手后，执行落子并计算提子数，
     * 然后广播状态同步给客户端。
     *
     * @param clientId 发送方客户端ID
     * @param message  包含x、y坐标的JSON消息
     */
    private void handlePlaceStone(int clientId, JSONObject message) throws JSONException {
        if (clientId != opponentPlayerId) return;

        int x = message.getInt("x");
        int y = message.getInt("y");

        if (game.isValidMove(x, y)) {
            // 记录落子前的提子数，用于计算本手提子
            int prevBC = game.getBlackCaptures();
            int prevWC = game.getWhiteCaptures();
            game.makeMove(x, y);
            int newBC = game.getBlackCaptures();
            int newWC = game.getWhiteCaptures();
            int captured = (newBC - prevBC) + (newWC - prevWC);
            // 提子归属：当前落子方的对方（因为makeMove后currentPlayer尚未切换）
            if (game.getCurrentPlayer() == GoGame.BLACK) {
                whiteCaptures += captured;
            } else {
                blackCaptures += captured;
            }
            lastMovePos = new int[]{x, y};
            game.setLastMove(x, y);
            game.switchPlayer();

            sendSyncState(false);

            mainHandler.post(this::updateTurnStatus);
        }
    }

    /**
     * 处理对手虚手消息（主机端）。
     * <p>
     * 对手虚手后检查是否双方连续虚手（终局），
     * 若终局则广播GAME_OVER消息。
     *
     * @param clientId 发送方客户端ID
     */
    private void handlePass(int clientId) {
        if (clientId != opponentPlayerId) return;

        game.pass();
        game.switchPlayer();

        if (game.isGameOver()) {
            isGameOver = true;
            sendSyncState(true);
            broadcastGameOver();
        } else {
            sendSyncState(false);
        }

        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 构建并发送状态同步消息（主机端）。
     * <p>
     * 包含完整棋盘数据、当前回合、提子数、最后一手坐标和游戏结束标志。
     * 每次同步递增版本号，客户端仅接受版本号更大的同步消息。
     *
     * @param gameOver 对局是否已结束
     */
    private void sendSyncState(boolean gameOver) {
        try {
            currentStateVersion++;
            JSONObject state = new JSONObject();
            state.put("type", "SYNC_STATE");
            state.put("stateVersion", currentStateVersion);
            state.put("gameOver", gameOver);

            // 序列化棋盘数据为二维JSON数组
            int[][] board = game.getBoard();
            JSONArray boardArray = new JSONArray();
            for (int i = 0; i < GoGame.BOARD_SIZE; i++) {
                JSONArray row = new JSONArray();
                for (int j = 0; j < GoGame.BOARD_SIZE; j++) {
                    row.put(board[i][j]);
                }
                boardArray.put(row);
            }
            state.put("board", boardArray);
            state.put("currentTurn", game.getCurrentPlayer());
            state.put("blackCaptures", blackCaptures);
            state.put("whiteCaptures", whiteCaptures);
            if (lastMovePos != null) {
                state.put("lastX", lastMovePos[0]);
                state.put("lastY", lastMovePos[1]);
            } else {
                state.put("lastX", -1);
                state.put("lastY", -1);
            }

            broadcast(state);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 广播游戏结束消息。
     */
    private void broadcastGameOver() {
        try {
            JSONObject gameOverMsg = new JSONObject();
            gameOverMsg.put("type", "GAME_OVER");
            broadcast(gameOverMsg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理状态同步消息（客户端/主机端）。
     * <p>
     * 通过版本号过滤旧消息，反序列化棋盘数据并更新本地游戏状态，
     * 包括棋盘、提子数、当前回合、最后一手和游戏结束标志。
     *
     * @param message SYNC_STATE类型的JSON消息
     */
    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        // 忽略旧版本的状态同步消息
        if (version <= currentStateVersion) return;
        currentStateVersion = version;

        // 反序列化棋盘数据
        JSONArray boardArray = message.optJSONArray("board");
        if (boardArray != null) {
            int[][] board = new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
            for (int i = 0; i < GoGame.BOARD_SIZE; i++) {
                JSONArray row = boardArray.optJSONArray(i);
                if (row != null) {
                    for (int j = 0; j < GoGame.BOARD_SIZE; j++) {
                        board[i][j] = row.optInt(j, 0);
                    }
                }
            }
            // 将反序列化的棋盘数据复制到游戏对象中
            for (int i = 0; i < GoGame.BOARD_SIZE; i++) {
                System.arraycopy(board[i], 0, game.getBoard()[i], 0, GoGame.BOARD_SIZE);
            }
        }

        blackCaptures = message.optInt("blackCaptures", 0);
        whiteCaptures = message.optInt("whiteCaptures", 0);

        // 同步当前回合，通过switchPlayer对齐
        int currentTurn = message.optInt("currentTurn", GoGame.BLACK);
        while (game.getCurrentPlayer() != currentTurn) {
            game.switchPlayer();
        }

        // 同步最后一手标记
        int lastX = message.optInt("lastX", -1);
        int lastY = message.optInt("lastY", -1);
        if (lastX >= 0 && lastY >= 0) {
            lastMovePos = new int[]{lastX, lastY};
            game.setLastMove(lastX, lastY);
        } else {
            lastMovePos = null;
            game.clearLastMove();
        }

        if (message.optBoolean("gameOver", false)) {
            isGameOver = true;
            game.setGameOver(true);
        }

        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 处理游戏结束消息。
     *
     * @param message GAME_OVER类型的JSON消息
     */
    private void handleGameOver(JSONObject message) {
        isGameOver = true;
        game.setGameOver(true);
        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 处理本机玩家落子操作。
     * <p>
     * 验证是否为己方回合后执行落子，计算提子数，
     * 主机直接广播状态同步，客户端发送PLACE_STONE消息给主机。
     *
     * @param x 横坐标
     * @param y 纵坐标
     */
    private void placeStone(int x, int y) {
        if (!isPlaying || isGameOver) return;

        boolean isMyTurn = game.getCurrentPlayer() == myColor;
        if (!isMyTurn) return;

        if (!game.isValidMove(x, y)) return;

        // 计算本手提子数
        int prevBC = game.getBlackCaptures();
        int prevWC = game.getWhiteCaptures();
        game.makeMove(x, y);
        int newBC = game.getBlackCaptures();
        int newWC = game.getWhiteCaptures();
        int captured = (newBC - prevBC) + (newWC - prevWC);
        if (game.getCurrentPlayer() == GoGame.BLACK) {
            whiteCaptures += captured;
        } else {
            blackCaptures += captured;
        }
        lastMovePos = new int[]{x, y};
        game.setLastMove(x, y);
        game.switchPlayer();

        if (isHost) {
            // 主机直接广播状态同步
            sendSyncState(false);
        } else {
            // 客户端发送落子请求给主机
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "PLACE_STONE");
                msg.put("x", x);
                msg.put("y", y);
                client.send(msg);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 处理本机玩家虚手操作。
     * <p>
     * 主机执行虚手后检查终局并广播状态，客户端发送PASS消息给主机。
     */
    private void passMove() {
        if (!isPlaying || isGameOver) return;

        boolean isMyTurn = game.getCurrentPlayer() == myColor;
        if (!isMyTurn) return;

        game.pass();
        game.switchPlayer();

        if (game.isGameOver()) {
            isGameOver = true;
        }

        if (isHost) {
            sendSyncState(isGameOver);
            if (isGameOver) {
                broadcastGameOver();
            }
        } else {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "PASS");
                client.send(msg);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 广播JSON消息（仅主机可用）。
     *
     * @param json 要广播的JSON消息
     */
    private void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    /**
     * 切换到游戏界面。
     */
    private void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
        isPlaying = true;
    }

    /**
     * 切换回大厅界面。
     */
    private void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
        isPlaying = false;
    }

    /**
     * 离开房间，停止服务器/断开客户端连接，并关闭Activity。
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
     * Activity销毁时清理网络资源和聊天辅助类。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }

}

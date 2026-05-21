package com.gamecenter.app.games.chinesechess;

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

import java.util.List;

/**
 * 中国象棋联机对战 Activity。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理联机对战的完整生命周期：大厅 → 创建/加入房间 → 对局 → 离开</li>
 *   <li>通过 WebSocket 中继服务器实现双人实时对战</li>
 *   <li>房主（Host）负责权威状态同步，客户端发送走法请求由房主执行并广播状态</li>
 *   <li>内置实时聊天功能，通过 {@link OnlineChatHelper} 管理</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>采用房主-客户端架构：房主是游戏状态的唯一权威，客户端仅发送走法请求</li>
 *   <li>状态同步使用版本号（stateVersion）机制，客户端只接受更新版本的状态</li>
 *   <li>界面采用纯代码构建（无XML布局），以动态切换大厅/游戏界面</li>
 *   <li>房间码为6位随机字母数字，排除易混淆字符（I/O/0/1）</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是中国象棋联机对战的"网络大厅+对局室"。
 * 和其他棋类的联机Activity结构类似，主要区别：
 * - 中国象棋是"选子+走子"两步操作（先点选棋子，再点目标位置）
 * - 状态同步通过"重放走棋历史"实现（客户端收到所有走法记录后重放一遍恢复棋盘）
 * - 房主执红方（先手），客户端执黑方
 */
public class ChineseChessOnlineActivity extends AppCompatActivity {

    /** SharedPreferences 文件名，用于保存P2P令牌 */
    private static final String P2P_PREFS = "xiangqi_p2p";

    /** 通信协议标识 */
    private static final String PROTOCOL = "XQ";

    /** 中继服务器基础URL */
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    /** SharedPreferences 实例 */
    private SharedPreferences prefs;

    /** WebSocket 服务器（房主使用） */
    private GameSocketServer server;

    /** WebSocket 客户端（加入方使用） */
    private GameSocketClient client;

    /** 是否为房主 */
    private volatile boolean isHost = false;

    /** 是否正在对局中 */
    private volatile boolean isPlaying = false;

    /** 对手是否已加入房间 */
    private volatile boolean opponentHasJoined = false;

    /** 自己的玩家ID（房主=1，客户端=2） */
    private int myPlayerId = -1;

    /** 对手的玩家ID */
    private int opponentPlayerId = -1;

    /** 房间码 */
    private String roomCode = "";

    /** 游戏逻辑对象 */
    private ChineseChessGame game;

    /** 当前状态版本号，用于防止旧状态覆盖新状态 */
    private volatile long currentStateVersion = 0;

    /** 在线聊天辅助类 */
    private OnlineChatHelper chatHelper;

    /** 自己的阵营（房主=红方，客户端=黑方） */
    private ChineseChessGame.Side mySide;

    /** 主线程Handler，用于从网络回调切换到UI线程 */
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 大厅布局（创建/加入房间界面） */
    private LinearLayout lobbyLayout;

    /** 游戏布局（对局界面） */
    private FrameLayout gameLayout;

    /** 房间码显示文本 */
    private TextView roomCodeText;

    /** 连接状态文本 */
    private TextView connectionStatusText;

    /** 回合状态文本 */
    private TextView turnStatusText;

    /** 获胜方文本 */
    private TextView winnerText;

    /** 加载进度条 */
    private ProgressBar loadingBar;

    /** 棋盘视图 */
    private ChineseChessView boardView;

    /** 当前选中的棋子列坐标，-1 表示未选中 */
    private int selectedX = -1;

    /** 当前选中的棋子行坐标，-1 表示未选中 */
    private int selectedY = -1;

    /** 当前选中棋子的合法走法列表 */
    private List<int[]> selectedMoves;

    /** 聊天消息显示区域 */
    private TextView chatDisplay;

    /** 聊天消息滚动容器 */
    private ScrollView chatScroll;

    /** 聊天输入框 */
    private EditText chatInput;

    /**
     * Activity创建时的初始化入口。
     * <p>
     * 初始化游戏对象、视图、服务器/客户端、聊天功能。
     *
     * @param savedInstanceState 系统保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        game = new ChineseChessGame();

        initViews();

        // 初始化WebSocket服务器（房主模式）
        server = new GameSocketServer(this);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);

        // 初始化WebSocket客户端（加入方模式）
        client = GameSocketClient.getInstance(this);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);

        // 初始化聊天功能
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
     * 初始化所有视图组件。
     * <p>纯代码构建界面，包含大厅布局和游戏布局两个主要区域。
     * 大厅布局包含标题、创建/加入房间按钮、加载进度条和状态文本；
     * 游戏布局包含回合状态、棋盘、聊天区域和离开按钮。
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
        titleText.setText("中国象棋 - 联机对战");
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

        winnerText = new TextView(this);
        winnerText.setTextSize(20);
        winnerText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        winnerText.setTextColor(0xFF4CAF50);
        winnerText.setPadding(8, 4, 8, 4);

        boardView = new ChineseChessView(this);
        boardView.bindGame(game);
        boardView.setOnCellClickListener(this::selectPiece);
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

        gameContent.addView(turnStatusText);
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
     * <p>获取输入框文本，通过聊天辅助类发送，发送后清空输入框。
     */
    private void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    /**
     * 创建房间（房主模式）。
     * <p>在后台线程生成房间码并启动WebSocket服务器，
     * 成功后显示等待对话框等待对手加入。
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
     * <p>使用排除易混淆字符（I/O/0/1）的字母数字字符集。
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
     * <p>包含房间码显示、复制按钮和提示文字。
     * 对话框不可取消，点击"取消"会离开房间。
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
     * 显示加入房间的对话框。
     * <p>包含房间码输入框，输入后调用 {@link #joinRoom} 加入。
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
     * 加入指定房间。
     * <p>使用保存的P2P令牌（如有）连接到中继服务器的WebSocket。
     *
     * @param roomCode 要加入的房间码
     */
    private void joinRoom(String roomCode) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("正在加入房间: " + roomCode);

        // 尝试使用上次保存的P2P令牌
        String savedToken = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, roomCode);

        if (savedToken != null) {
            client.setPeerToken(savedToken);
        }

        client.connectWebSocket(wsUrl);
    }

    /**
     * 房主回调：有客户端连接到房间。
     * <p>记录对手ID，标记对手已加入，开始游戏。
     *
     * @param clientId 连接的客户端ID
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
     * 房主回调：客户端断开连接。
     * <p>若断开的是对手，提示并返回大厅。
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
     * 客户端回调：成功连接到房主。
     * <p>保存P2P令牌，设置玩家ID为2，开始游戏。
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
     * 客户端回调：与房主的连接断开。
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
     * 服务器错误回调。
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
     * 开始游戏。
     * <p>重置棋盘，根据玩家ID确定阵营（房主=红方，客户端=黑方），
     * 切换到游戏界面并更新回合状态。
     */
    private void startGame() {
        game.reset();
        currentStateVersion = 0;
        mySide = myPlayerId == 1 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
        selectedX = -1;
        selectedY = -1;
        selectedMoves = null;
        showGameScreen();
        updateTurnStatus();
    }

    /**
     * 更新回合状态显示。
     * <p>根据当前走棋方和游戏状态更新回合提示文本、锁定/解锁棋盘。
     */
    private void updateTurnStatus() {
        boolean isMyTurn = game.getCurrentSide() == mySide;

        if (game.isGameOver()) {
            ChineseChessGame.Side winner = game.getWinner();
            if (winner == mySide) {
                winnerText.setText("你赢了!");
            } else {
                winnerText.setText("对手赢了!");
            }
            turnStatusText.setText("游戏结束");
            boardView.setLocked(true);
        } else {
            winnerText.setText("");
            turnStatusText.setText(isMyTurn ? "轮到你走棋" : "等待对手...");
            boardView.setLocked(!isMyTurn);
        }

        boardView.invalidate();
    }

    /**
     * 房主消息处理回调。
     * <p>处理来自客户端的消息，包括：
     * <ul>
     *   <li>聊天消息：交给聊天辅助类处理</li>
     *   <li>MOVE：客户端的走法请求，验证后执行并同步状态</li>
     *   <li>SYNC_STATE：状态同步（通常不会由客户端发送）</li>
     *   <li>GAME_OVER：游戏结束通知</li>
     * </ul>
     *
     * @param clientId 发送消息的客户端ID
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
                case "MOVE":
                    handleMove(clientId, message);
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
     * <p>处理来自房主的消息，包括：
     * <ul>
     *   <li>聊天消息：交给聊天辅助类处理</li>
     *   <li>SYNC_STATE：房主广播的状态同步，重放走棋历史恢复棋盘</li>
     *   <li>GAME_OVER：游戏结束通知</li>
     * </ul>
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
     * 处理客户端发来的走法请求（房主专用）。
     * <p>验证走法来源为对手后，在游戏逻辑中执行走法，
     * 切换走棋方，检查游戏结束，然后同步状态给客户端。
     *
     * @param clientId 发送走法的客户端ID
     * @param message  包含 fromX, fromY, toX, toY 的JSON消息
     * @throws JSONException JSON解析异常
     */
    private void handleMove(int clientId, JSONObject message) throws JSONException {
        if (clientId != opponentPlayerId) return;

        int fromX = message.getInt("fromX");
        int fromY = message.getInt("fromY");
        int toX = message.getInt("toX");
        int toY = message.getInt("toY");

        ChineseChessGame.Piece piece = game.getBoard()[fromY][fromX];
        if (piece != null && piece.side == game.getCurrentSide()) {
            ChineseChessGame.MoveRecord record = game.makeMoveSafe(fromX, fromY, toX, toY);
            if (record != null) {
                game.switchSide();
                game.checkGameOver();

                sendSyncState();

                if (game.isGameOver()) {
                    broadcastGameOver();
                }

                mainHandler.post(this::updateTurnStatus);
            }
        }
    }

    /**
     * 构建并发送状态同步消息（房主专用）。
     * <p>包含状态版本号、当前走棋方、游戏结束标志、
     * 完整走棋历史（用于客户端重放恢复棋盘）。
     */
    private void sendSyncState() {
        try {
            currentStateVersion++;
            JSONObject state = new JSONObject();
            state.put("type", "SYNC_STATE");
            state.put("stateVersion", currentStateVersion);
            state.put("currentSide", game.getCurrentSide().ordinal());
            state.put("gameOver", game.isGameOver());
            if (game.isGameOver()) {
                state.put("winner", game.getWinner().ordinal());
            }

            // 序列化完整走棋历史，客户端通过重放恢复棋盘
            JSONArray moveHistory = new JSONArray();
            for (ChineseChessGame.MoveRecord move : game.getMoveHistory()) {
                JSONObject moveObj = new JSONObject();
                moveObj.put("fx", move.fromX);
                moveObj.put("fy", move.fromY);
                moveObj.put("tx", move.toX);
                moveObj.put("ty", move.toY);
                moveHistory.put(moveObj);
            }
            state.put("moveHistory", moveHistory);

            broadcast(state);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 广播游戏结束消息（房主专用）。
     *
     */
    private void broadcastGameOver() {
        try {
            JSONObject gameOverMsg = new JSONObject();
            gameOverMsg.put("type", "GAME_OVER");
            gameOverMsg.put("winner", game.getWinner().ordinal());
            broadcast(gameOverMsg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理状态同步消息（客户端使用）。
     * <p>通过版本号判断是否需要更新。更新时重置棋盘，
     * 重放走棋历史恢复棋盘状态，并同步走棋方和游戏结束标志。
     *
     * @param message 状态同步JSON消息
     * @throws JSONException JSON解析异常
     */
    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        // 忽略旧版本的状态，防止乱序消息覆盖
        if (version <= currentStateVersion) return;
        currentStateVersion = version;

        // 重置棋盘并通过重放走棋历史恢复状态
        game.reset();

        JSONArray moveHistoryArray = message.optJSONArray("moveHistory");
        if (moveHistoryArray != null) {
            for (int i = 0; i < moveHistoryArray.length(); i++) {
                JSONObject moveObj = moveHistoryArray.optJSONObject(i);
                if (moveObj != null) {
                    int fx = moveObj.getInt("fx");
                    int fy = moveObj.getInt("fy");
                    int tx = moveObj.getInt("tx");
                    int ty = moveObj.getInt("ty");
                    game.makeMoveSafe(fx, fy, tx, ty);
                }
            }
        }

        // 同步走棋方
        int currentSideOrdinal = message.optInt("currentSide", 0);
        while (game.getCurrentSide().ordinal() != currentSideOrdinal) {
            game.switchSide();
        }

        // 同步游戏结束状态
        if (message.optBoolean("gameOver", false)) {
            int winnerOrdinal = message.optInt("winner", 0);
            game.setGameOver(winnerOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK);
        }

        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 处理游戏结束消息。
     *
     * @param message 包含获胜方信息的JSON消息
     * @throws JSONException JSON解析异常
     */
    private void handleGameOver(JSONObject message) throws JSONException {
        int winnerOrdinal = message.optInt("winner", 0);
        ChineseChessGame.Side winnerSide = winnerOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
        game.setGameOver(winnerSide);
        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 执行走棋操作。
     * <p>仅在自己回合且游戏未结束时允许走棋。
     * 走棋后根据角色（房主/客户端）进行不同的同步操作：
     * <ul>
     *   <li>房主：直接执行走法，发送状态同步</li>
     *   <li>客户端：执行走法，发送MOVE消息给房主</li>
     * </ul>
     *
     * @param fromX 起始列
     * @param fromY 起始行
     * @param toX   目标列
     * @param toY   目标行
     */
    private void makeMove(int fromX, int fromY, int toX, int toY) {
        if (!isPlaying || game.isGameOver()) return;

        boolean isMyTurn = game.getCurrentSide() == mySide;
        if (!isMyTurn) return;

        ChineseChessGame.Piece piece = game.getBoard()[fromY][fromX];
        if (piece == null || piece.side != mySide) return;

        ChineseChessGame.MoveRecord record = game.makeMoveSafe(fromX, fromY, toX, toY);
        if (record == null) return;

        game.switchSide();
        game.checkGameOver();

        if (isHost) {
            // 房主：权威执行走法，广播状态同步
            sendSyncState();
            if (game.isGameOver()) {
                broadcastGameOver();
            }
        } else {
            // 客户端：发送走法请求给房主
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "MOVE");
                msg.put("fromX", fromX);
                msg.put("fromY", fromY);
                msg.put("toX", toX);
                msg.put("toY", toY);
                client.send(msg);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        selectedX = -1;
        selectedY = -1;
        selectedMoves = null;
        boardView.clearSelected();
        boardView.setLastMove(fromX, fromY, toX, toY);

        mainHandler.post(this::updateTurnStatus);
    }

    /**
     * 处理棋盘格子点击（选中棋子或走棋）。
     * <p>交互逻辑：
     * <ol>
     *   <li>点击己方棋子：选中并显示合法走法</li>
     *   <li>已选中棋子后点击合法目标位置：执行走棋</li>
     *   <li>点击无效位置：取消选中</li>
     * </ol>
     *
     * @param x 点击的列坐标
     * @param y 点击的行坐标
     */
    private void selectPiece(int x, int y) {
        if (!isPlaying || game.isGameOver()) return;

        boolean isMyTurn = game.getCurrentSide() == mySide;
        if (!isMyTurn) return;

        ChineseChessGame.Piece piece = game.getBoard()[y][x];

        if (piece != null && piece.side == mySide) {
            // 选中己方棋子
            selectedX = x;
            selectedY = y;
            selectedMoves = game.getLegalMoves(x, y);
            boardView.setSelected(x, y, selectedMoves);
        } else if (selectedX >= 0 && selectedMoves != null) {
            // 已选中棋子后点击目标位置
            for (int[] move : selectedMoves) {
                if (move[0] == x && move[1] == y) {
                    makeMove(selectedX, selectedY, x, y);
                    return;
                }
            }
            // 点击了无效位置，取消选中
            selectedX = -1;
            selectedY = -1;
            selectedMoves = null;
            boardView.clearSelected();
        }
    }

    /**
     * 广播消息给所有连接的客户端（房主专用）。
     *
     * @param json 要广播的JSON消息
     */
    private void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    /**
     * 切换到游戏界面，隐藏大厅。
     */
    private void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
        isPlaying = true;
    }

    /**
     * 切换到大厅界面，隐藏游戏界面。
     */
    private void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
        isPlaying = false;
    }

    /**
     * 离开房间。
     * <p>停止服务器/断开客户端连接，重置状态，关闭Activity。
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
     * Activity销毁时清理资源。
     * <p>清理聊天辅助类，停止服务器，释放客户端。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }

}

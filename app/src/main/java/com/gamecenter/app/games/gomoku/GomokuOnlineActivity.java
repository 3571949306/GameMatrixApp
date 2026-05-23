package com.gamecenter.app.games.gomoku;

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
import com.google.android.material.card.MaterialCardView;

import com.gamecenter.app.network.GameSocketClient;
import com.gamecenter.app.network.GameSocketServer;
import com.gamecenter.app.network.OnlineChatHelper;
import com.gamecenter.app.network.OnlineDialogHelper;
import com.gamecenter.app.network.RelayHttpClient;
import com.gamecenter.app.network.RemoteP2PUtil;
import com.gamecenter.app.network.RoomCodeHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 五子棋联机对战Activity。
 * <p>
 * 实现基于WebSocket中继服务器的双人在线五子棋对局，支持：
 * <ul>
 *   <li>创建/加入房间（6位房间码）</li>
 *   <li>主机-客户端架构：主机负责权威状态同步，客户端发送操作请求</li>
 *   <li>实时聊天功能</li>
 *   <li>五连胜负自动检测</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>采用"主机权威"模型：主机维护游戏状态的唯一权威副本，每次落子后向客户端广播完整状态同步</li>
 *   <li>状态版本号（{@link #currentStateVersion}）用于防止旧消息覆盖新状态</li>
 *   <li>主机执黑（先手），客户端执白</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是五子棋联机对战的"网络大厅+对局室"。
 * 联机对战的流程就像去棋牌室下棋：
 * 1. 创建房间 = 在棋牌室开一桌，拿到一个房间号
 * 2. 加入房间 = 告诉朋友房间号，朋友输入号码就能加入
 * 3. 主机（创建者）就像"裁判"，负责维护棋盘的权威状态
 * 4. 客户端（加入者）走棋时，要告诉主机"我下在这里"，由主机确认后广播给双方
 * 5. 这样可以防止作弊——因为棋盘的"真相"只保存在主机那里
 */
public class GomokuOnlineActivity extends AppCompatActivity {

    /** SharedPreferences文件名，存储P2P令牌等持久化数据 */
    private static final String P2P_PREFS = "gomoku_p2p";

    /** 协议标识 */
    private static final String PROTOCOL = "GMK";

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

    /** 对局是否已开始 */
    private volatile boolean gameStarted = false;

    /** 对手是否已加入房间 */
    private volatile boolean opponentHasJoined = false;

    /** 本机玩家ID（主机=1，客户端=2） */
    private int myPlayerId = -1;

    /** 当前房间码 */
    private String roomCode = "";

    /** 五子棋游戏逻辑对象 */
    private GomokuGame game;

    /** 状态版本号，用于防止旧同步消息覆盖新状态 */
    private volatile long currentStateVersion = 0;

    /** 在线聊天辅助类 */
    private OnlineChatHelper chatHelper;

    /** 主线程Handler，用于跨线程UI更新 */
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 大厅布局（创建/加入房间界面） */
    private LinearLayout lobbyLayout;

    /** 游戏布局（棋盘+控制界面） */
    private LinearLayout gameLayout;

    /** 房间码显示文本 */
    private TextView roomCodeText;

    /** 连接状态文本 */
    private TextView statusText;

    /** 回合状态文本 */
    private TextView turnText;

    /** 胜负结果文本 */
    private TextView winnerText;

    /** 棋盘视图 */
    private GomokuView gomokuView;

    /** 加载进度条 */
    private ProgressBar loadingBar;

    /** 创建房间按钮 */
    private Button createRoomBtn;

    /** 加入房间按钮 */
    private Button joinRoomBtn;

    /** 离开房间按钮 */
    private Button leaveBtn;

    /** 等待对手对话框 */
    private AlertDialog waitingDialog;

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
        game = new GomokuGame();

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

        initViews();

        chatHelper.setInlineDisplay(chatDisplay, chatScroll);
    }

    /**
     * 初始化所有视图组件，采用纯代码构建布局。
     * <p>
     * 界面分为两层：大厅层（创建/加入房间）和游戏层（棋盘+聊天+控制），
     * 通过切换可见性实现界面切换。棋盘使用CardView包裹以增加视觉层次。
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
        lobbyLayout.setGravity(View.TEXT_ALIGNMENT_CENTER);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView titleText = new TextView(this);
        titleText.setText("五子棋 - 联机对战");
        titleText.setTextSize(24);
        titleText.setTextColor(0xFF1E1E32);
        titleText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        titleText.setPadding(0, 0, 0, 32);

        createRoomBtn = new Button(this);
        createRoomBtn.setText("创建房间");
        createRoomBtn.setTextSize(18);
        createRoomBtn.setBackgroundColor(0xFF4CAF50);
        createRoomBtn.setTextColor(0xFFFFFFFF);
        createRoomBtn.setPadding(32, 24, 32, 24);
        createRoomBtn.setOnClickListener(v -> createRoom());

        joinRoomBtn = new Button(this);
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

        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        statusText.setPadding(0, 8, 0, 0);

        lobbyLayout.addView(titleText);
        lobbyLayout.addView(createRoomBtn);
        lobbyLayout.addView(joinRoomBtn);
        lobbyLayout.addView(loadingBar);
        lobbyLayout.addView(roomCodeText);
        lobbyLayout.addView(statusText);

        gameLayout = new LinearLayout(this);
        gameLayout.setOrientation(LinearLayout.VERTICAL);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        gameLayout.setVisibility(View.GONE);

        turnText = new TextView(this);
        turnText.setTextSize(18);
        turnText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        turnText.setPadding(0, 4, 0, 2);

        winnerText = new TextView(this);
        winnerText.setTextSize(20);
        winnerText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        winnerText.setPadding(0, 2, 0, 4);

        // 使用CardView包裹棋盘，增加圆角和阴影效果
        MaterialCardView boardCard = new MaterialCardView(this);
        boardCard.setRadius(8);
        boardCard.setPadding(4, 4, 4, 4);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f);
        boardParams.setMargins(8, 4, 8, 2);
        boardCard.setLayoutParams(boardParams);

        gomokuView = new GomokuView(this);
        gomokuView.setGame(game);
        gomokuView.setOnCellClickListener(this::onPlayerMove);
        gomokuView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        boardCard.addView(gomokuView);

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

        leaveBtn = new Button(this);
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

        gameLayout.addView(turnText);
        gameLayout.addView(winnerText);
        gameLayout.addView(boardCard);
        gameLayout.addView(chatScroll);
        gameLayout.addView(chatInputRow);
        gameLayout.addView(btnRow);

        root.addView(lobbyLayout);
        root.addView(gameLayout);

        setContentView(root);
    }

    /**
     * 发送聊天消息。
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
        createRoomBtn.setEnabled(false);
        joinRoomBtn.setEnabled(false);
        statusText.setText("正在创建云房间...");

        new Thread(() -> {
            String roomCode = generateRoomCode();
            String wsUrl = RelayHttpClient.getWebSocketUrl(RELAY_BASE_URL, roomCode, "");
            boolean success = server.startWebSocket(wsUrl);
            mainHandler.post(() -> {
                loadingBar.setVisibility(View.GONE);
                if (success) {
                    isHost = true;
                    this.roomCode = roomCode;
                    statusText.setText("房间已创建，等待对手加入...");
                    roomCodeText.setText("房间码: " + roomCode);
                    roomCodeText.setVisibility(View.VISIBLE);
                    createRoomBtn.setEnabled(false);
                    joinRoomBtn.setEnabled(true);
                    joinRoomBtn.setText("输入房间码");
                    showWaitingDialog(roomCode);
                } else {
                    Toast.makeText(this, "创建房间失败", Toast.LENGTH_SHORT).show();
                    createRoomBtn.setEnabled(true);
                    joinRoomBtn.setEnabled(true);
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
        return RoomCodeHelper.generateRoomCode();
    }

    /**
     * 显示加入房间的输入对话框。
     * <p>
     * 用户输入6位房间码后调用 {@link #joinRoom}。
     */
    private void showJoinDialog() {
        OnlineDialogHelper.showJoinDialog(this, this::joinRoom);
    }

    /**
     * 加入指定房间码的联机房间。
     * <p>
     * 使用WebSocket客户端连接到中继服务器，并恢复上次保存的对等端令牌。
     *
     * @param code 目标房间码
     */
    private void joinRoom(String code) {
        loadingBar.setVisibility(View.VISIBLE);
        createRoomBtn.setEnabled(false);
        joinRoomBtn.setEnabled(false);
        statusText.setText("正在加入房间 " + code + "...");
        roomCode = code;

        String token = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, code);

        if (token != null) {
            client.setPeerToken(token);
        }

        client.connectWebSocket(wsUrl);
    }

    /**
     * 主机端回调：客户端连接成功。
     * <p>
     * 关闭等待对话框，标记对手已加入，并启动游戏。
     *
     * @param clientId 客户端ID
     * @param ip       客户端IP地址
     */
    private void onClientConnected(int clientId, String ip) {
        mainHandler.post(() -> {
            if (waitingDialog != null && waitingDialog.isShowing()) {
                waitingDialog.dismiss();
            }
            statusText.setText("对手已加入，游戏开始!");
            game.reset();
            gameStarted = true;
            myPlayerId = 1;
            showGameScreen();
            updateGameUI();
        });
    }

    /**
     * 主机端回调：客户端断开连接。
     *
     * @param clientId 断开的客户端ID
     * @param reason   断开原因
     */
    private void onClientDisconnected(int clientId, String reason) {
        mainHandler.post(() -> {
            Toast.makeText(this, "对手已断开: " + reason, Toast.LENGTH_LONG).show();
            gameStarted = false;
            showLobby();
        });
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
        gameStarted = true;

        // 保存P2P令牌以便下次重连
        String token = client.getPeerToken();
        if (token != null && !token.isEmpty()) {
            prefs.edit().putString("last_peer_token", token).apply();
        }

        mainHandler.post(() -> {
            statusText.setText("已加入，游戏开始!");
            game.reset();
            showGameScreen();
            updateGameUI();
        });
    }

    /**
     * 客户端回调：与主机断开连接。
     *
     * @param reason 断开原因
     */
    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            Toast.makeText(this, "连接已断开: " + reason, Toast.LENGTH_LONG).show();
            gameStarted = false;
            showLobby();
        });
    }

    /**
     * 主机端错误回调。
     *
     * @param message 错误信息
     */
    private void onServerError(String message) {
        mainHandler.post(() -> {
            Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show();
            createRoomBtn.setEnabled(true);
            joinRoomBtn.setEnabled(true);
        });
    }

    /**
     * 客户端错误回调。
     *
     * @param message 错误信息
     */
    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "错误: " + message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 主机端消息处理回调。
     * <p>
     * 处理来自客户端的PLACE_STONE消息和聊天消息。
     * 主机收到客户端落子后，执行落子并广播状态同步。
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
            if ("PLACE_STONE".equals(type)) {
                int x = message.getInt("x");
                int y = message.getInt("y");

                if (game.isValidMove(x, y)) {
                    game.makeMove(x, y, game.getCurrentPlayer());
                    if (game.checkGameOver()) {
                        game.getBoard();
                    }
                    game.switchPlayer();

                    sendSyncState();

                    if (game.isGameOver()) {
                        broadcastGameOver();
                    }

                    mainHandler.post(this::updateGameUI);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 客户端消息处理回调。
     * <p>
     * 客户端接收主机广播的SYNC_STATE和GAME_OVER消息。
     * 通过版本号过滤旧消息，反序列化棋盘数据并更新本地状态。
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
            if ("SYNC_STATE".equals(type)) {
                long version = message.optLong("stateVersion", 0);
                // 忽略旧版本的状态同步消息
                if (version <= currentStateVersion) return;
                currentStateVersion = version;

                // 反序列化棋盘数据
                JSONArray boardArray = message.getJSONArray("board");
                for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
                    JSONArray row = boardArray.getJSONArray(i);
                    for (int j = 0; j < GomokuGame.BOARD_SIZE; j++) {
                        game.getBoard()[i][j] = row.getInt(j);
                    }
                }

                int currentTurn = message.optInt("currentTurn", GomokuGame.BLACK);
                game.setCurrentPlayer(currentTurn);

                if (message.optBoolean("gameOver", false)) {
                    int winner = message.optInt("winner", 0);
                    game.setGameOver(winner);
                }

                mainHandler.post(this::updateGameUI);
            } else if ("GAME_OVER".equals(type)) {
                int winner = message.optInt("winner", 0);
                game.setGameOver(winner);
                mainHandler.post(this::updateGameUI);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建并发送状态同步消息（主机端）。
     * <p>
     * 包含完整棋盘数据、当前回合、游戏结束标志和获胜方。
     * 每次同步递增版本号。
     */
    private void sendSyncState() {
        try {
            currentStateVersion++;
            JSONObject state = new JSONObject();
            state.put("type", "SYNC_STATE");
            state.put("stateVersion", currentStateVersion);
            state.put("gameOver", game.isGameOver());

            // 序列化棋盘数据为二维JSON数组
            int[][] board = game.getBoard();
            JSONArray boardArray = new JSONArray();
            for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
                JSONArray row = new JSONArray();
                for (int j = 0; j < GomokuGame.BOARD_SIZE; j++) {
                    row.put(board[i][j]);
                }
                boardArray.put(row);
            }
            state.put("board", boardArray);
            state.put("currentTurn", game.getCurrentPlayer());
            if (game.isGameOver()) {
                state.put("winner", game.getWinner() != null ? game.getWinner() : 0);
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
            gameOverMsg.put("winner", game.getWinner() != null ? game.getWinner() : 0);
            broadcast(gameOverMsg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理本机玩家落子操作。
     * <p>
     * 验证是否为己方回合后执行落子，
     * 主机直接广播状态同步，客户端发送PLACE_STONE消息给主机。
     *
     * @param x 横坐标
     * @param y 纵坐标
     */
    private void onPlayerMove(int x, int y) {
        if (!gameStarted || game.isGameOver()) return;

        int currentColor = game.getCurrentPlayer();
        // 主机执黑，客户端执白
        boolean isMyTurn = (myPlayerId == 1 && currentColor == GomokuGame.BLACK)
                || (myPlayerId == 2 && currentColor == GomokuGame.WHITE);

        if (!isMyTurn) return;

        if (!game.isValidMove(x, y)) return;

        game.makeMove(x, y, currentColor);
        if (game.checkGameOver()) {
            game.getBoard();
        }
        game.switchPlayer();

        if (isHost) {
            // 主机直接广播状态同步
            sendSyncState();
            if (game.isGameOver()) {
                broadcastGameOver();
            }
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

        mainHandler.post(this::updateGameUI);
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
     * 更新游戏界面状态显示。
     * <p>
     * 根据当前执子方和本机颜色显示回合提示，
     * 对局结束时显示胜负结果。
     */
    private void updateGameUI() {
        if (!gameStarted) return;

        int currentColor = game.getCurrentPlayer();
        boolean isMyTurn = (myPlayerId == 1 && currentColor == GomokuGame.BLACK)
                || (myPlayerId == 2 && currentColor == GomokuGame.WHITE);

        if (game.isGameOver()) {
            Integer winner = game.getWinner();
            if (winner == null) {
                winnerText.setText("平局!");
            } else if (winner == GomokuGame.BLACK) {
                winnerText.setText(myPlayerId == 1 ? "你赢了!" : "对手赢了!");
            } else {
                winnerText.setText(myPlayerId == 2 ? "你赢了!" : "对手赢了!");
            }
            turnText.setText("游戏结束");
        } else {
            winnerText.setText("");
            turnText.setText(isMyTurn ? "你的回合 - 请落子" : "等待对手落子...");
        }

        gomokuView.invalidate();
    }

    /**
     * 切换到游戏界面。
     */
    private void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 显示等待对手加入的对话框。
     * <p>
     * 包含房间码显示、复制按钮和等待指示器。
     *
     * @param roomCode 房间码
     */
    private void showWaitingDialog(String roomCode) {
        waitingDialog = OnlineDialogHelper.showWaitingDialog(this, roomCode, this::leaveRoom);
    }

    /**
     * 切换回大厅界面。
     */
    private void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
        createRoomBtn.setEnabled(true);
        joinRoomBtn.setEnabled(true);
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
        gameStarted = false;
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

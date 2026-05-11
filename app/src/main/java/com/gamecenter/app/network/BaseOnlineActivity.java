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
 * 联机游戏基类 — 封装房间管理、聊天、连接状态等通用逻辑。
 * 子类只需实现 initGameViews()、onGameStarted()、onGameMessageReceived() 等游戏特有逻辑。
 */
public abstract class BaseOnlineActivity extends AppCompatActivity {

    protected static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    protected SharedPreferences prefs;
    protected GameSocketServer server;
    protected GameSocketClient client;
    protected OnlineChatHelper chatHelper;

    protected volatile boolean isHost = false;
    protected volatile boolean isPlaying = false;
    protected int myPlayerId = -1;
    protected int opponentPlayerId = -1;
    protected String roomCode = "";

    protected Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI 组件
    protected LinearLayout lobbyLayout;
    protected LinearLayout gameLayout;
    protected TextView roomCodeText;
    protected TextView connectionStatusText;
    protected ProgressBar loadingBar;
    protected TextView chatDisplay;
    protected ScrollView chatScroll;
    protected EditText chatInput;

    // 子类必须实现的方法
    protected abstract String getP2pPrefsName();
    protected abstract String getGameName();
    protected abstract void initGameViews(LinearLayout gameContent);
    protected abstract void onGameStarted();
    protected abstract void onGameMessageReceived(JSONObject message);
    protected abstract void onGameReset();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(getP2pPrefsName(), MODE_PRIVATE);

        initServer();
        initClient();
        initChatHelper();

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

    protected void initServer() {
        server = new GameSocketServer(this);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);
    }

    protected void initClient() {
        client = GameSocketClient.getInstance(this);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);
    }

    protected void initChatHelper() {
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
    }

    protected void afterViewsCreated() {
        chatHelper.setInlineDisplay(chatDisplay, chatScroll);
    }

    // ========== 大厅布局 ==========

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

    protected void initGameLayout(LinearLayout root) {
        gameLayout = new LinearLayout(this);
        gameLayout.setOrientation(LinearLayout.VERTICAL);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
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

    protected void initChatViews(LinearLayout gameContent) {
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

        gameContent.addView(chatScroll);
        gameContent.addView(chatInputRow);
    }

    // ========== 房间管理 ==========

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

    protected String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    protected void showWaitingDialog() {
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

    protected void showJoinDialog() {
        EditText input = new EditText(this);
        input.setHint("请输入6位房间码");
        input.setMaxLines(1);

        new AlertDialog.Builder(this)
                .setTitle("加入房间")
                .setView(input)
                .setPositiveButton("加入", (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() == 6) {
                        joinRoom(code);
                    } else {
                        Toast.makeText(this, "请输入6位房间码", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    protected void joinRoom(String code) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText("正在加入房间 " + code + "...");
        roomCode = code;

        String token = prefs.getString("last_peer_token", null);
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, code);

        if (token != null) {
            client.setPeerToken(token);
        }

        client.connectWebSocket(wsUrl);
    }

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

    protected void onClientConnected(int clientId, String ip) {
        opponentPlayerId = clientId;
        mainHandler.post(() -> {
            connectionStatusText.setText("对手已加入!");
            isPlaying = true;
            showGameScreen();
            onGameStarted();
        });
    }

    protected void onClientDisconnected(int clientId, String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            showDisconnectDialog("对手已断开: " + reason, true);
        });
    }

    protected void onClientConnectedToHost(int clientId) {
        myPlayerId = 2;
        isPlaying = true;

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

    protected void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            showDisconnectDialog("连接已断开: " + reason, false);
        });
    }

    protected void showDisconnectDialog(String message, boolean isHostSide) {
        if (isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("连接断开")
                .setMessage(message)
                .setCancelable(false);

        if (isHostSide) {
            // 主机端：等待对手重连或离开
            builder.setPositiveButton("等待重连", (d, w) -> {
                Toast.makeText(this, "等待对手重新连接...", Toast.LENGTH_SHORT).show();
            });
        } else {
            // 客户端：尝试重连或离开
            builder.setPositiveButton("重新连接", (d, w) -> {
                if (roomCode != null && !roomCode.isEmpty()) {
                    Toast.makeText(this, "正在重新连接...", Toast.LENGTH_SHORT).show();
                    joinRoom(roomCode);
                } else {
                    Toast.makeText(this, "无法重连，请重新加入", Toast.LENGTH_SHORT).show();
                    showLobby();
                }
            });
        }

        builder.setNegativeButton("离开房间", (d, w) -> leaveRoom());
        builder.show();
    }

    protected void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show());
    }

    protected void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "客户端错误: " + message, Toast.LENGTH_SHORT).show());
    }

    // ========== 消息处理 ==========

    protected void onHostMessageReceived(int clientId, JSONObject message) {
        String type = message.optString("type", "");
        if (chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        onGameMessageReceived(message);
    }

    protected void onClientMessageReceived(JSONObject message) {
        String type = message.optString("type", "");
        if (chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        onGameMessageReceived(message);
    }

    // ========== UI 辅助 ==========

    protected void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
    }

    protected void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
    }

    protected void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    protected void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    // ========== 生命周期 ==========

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }
}

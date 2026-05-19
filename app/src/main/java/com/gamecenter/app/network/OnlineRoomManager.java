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
 * <p>
 * 从 {@link BaseOnlineActivity} 中提取，使各游戏的 OnlineActivity 可以通过组合方式复用，
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

    private final Context context;
    private final String prefsName;
    private final String gameName;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private GameSocketServer server;
    private GameSocketClient client;
    private OnlineChatHelper chatHelper;

    private volatile boolean isHost = false;
    private volatile boolean isPlaying = false;
    private int myPlayerId = -1;
    private int opponentPlayerId = -1;
    private String roomCode = "";

    private LinearLayout lobbyLayout;
    private LinearLayout gameLayout;
    private TextView roomCodeText;
    private TextView connectionStatusText;
    private ProgressBar loadingBar;
    private TextView chatDisplay;
    private ScrollView chatScroll;
    private EditText chatInput;

    private Listener listener;

    public interface Listener {
        void onGameStarted();
        void onGameMessageReceived(JSONObject message);
        void onGameReset();
    }

    public OnlineRoomManager(Context context, String prefsName, String gameName) {
        this.context = context.getApplicationContext();
        this.prefsName = prefsName;
        this.gameName = gameName;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void initServer() {
        server = new GameSocketServer(context);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);
    }

    public void initClient() {
        client = GameSocketClient.getInstance(context);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);
    }

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

    public void setInlineChatDisplay(TextView chatDisplay, ScrollView chatScroll) {
        this.chatDisplay = chatDisplay;
        this.chatScroll = chatScroll;
        if (chatHelper != null) {
            chatHelper.setInlineDisplay(chatDisplay, chatScroll);
        }
    }

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

    public void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    public void showGameScreen() {
        if (lobbyLayout != null) lobbyLayout.setVisibility(View.GONE);
        if (gameLayout != null) gameLayout.setVisibility(View.VISIBLE);
    }

    public void showLobby() {
        if (gameLayout != null) gameLayout.setVisibility(View.GONE);
        if (lobbyLayout != null) lobbyLayout.setVisibility(View.VISIBLE);
    }

    public void cleanup() {
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }

    public void initPrefs(Context activityContext) {
        prefs = activityContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    public void setGameLayout(LinearLayout gameLayout) {
        this.gameLayout = gameLayout;
    }

    public GameSocketServer getServer() { return server; }
    public GameSocketClient getClient() { return client; }
    public OnlineChatHelper getChatHelper() { return chatHelper; }
    public boolean isHost() { return isHost; }
    public boolean isPlaying() { return isPlaying; }
    public int getMyPlayerId() { return myPlayerId; }
    public int getOpponentPlayerId() { return opponentPlayerId; }
    public String getRoomCode() { return roomCode; }
    public TextView getConnectionStatusText() { return connectionStatusText; }
    public ProgressBar getLoadingBar() { return loadingBar; }
    public LinearLayout getLobbyLayout() { return lobbyLayout; }
    public Handler getMainHandler() { return mainHandler; }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

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

    private void sendChatMessage() {
        if (chatInput == null || chatHelper == null) return;
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

    private void onClientConnected(int clientId, String ip) {
        opponentPlayerId = clientId;
        mainHandler.post(() -> {
            connectionStatusText.setText(context.getString(R.string.online_opponent_joined));
            isPlaying = true;
            showGameScreen();
            if (listener != null) listener.onGameStarted();
        });
    }

    private void onClientDisconnected(int clientId, String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            showDisconnectDialog(context.getString(R.string.online_opponent_disconnected) + reason, true);
        });
    }

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

    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            isPlaying = false;
            showDisconnectDialog(context.getString(R.string.online_connection_lost) + reason, false);
        });
    }

    private void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(context, context.getString(R.string.online_server_error) + message, Toast.LENGTH_SHORT).show());
    }

    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(context, context.getString(R.string.online_client_error) + message, Toast.LENGTH_SHORT).show());
    }

    private void onHostMessageReceived(int clientId, JSONObject message) {
        if (chatHelper != null && chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        if (listener != null) listener.onGameMessageReceived(message);
    }

    private void onClientMessageReceived(JSONObject message) {
        if (chatHelper != null && chatHelper.isChatMessage(message)) {
            chatHelper.handleIncomingChat(message);
            return;
        }
        if (listener != null) listener.onGameMessageReceived(message);
    }
}

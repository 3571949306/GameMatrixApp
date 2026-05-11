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

public class RockOnlineActivity extends AppCompatActivity {

    private static final String P2P_PREFS = "rock_p2p";
    private static final String PROTOCOL = "ROCK";
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    private SharedPreferences prefs;
    private GameSocketServer server;
    private GameSocketClient client;

    private volatile boolean isHost = false;
    private volatile boolean isPlaying = false;
    private volatile boolean isMyTurn = false;
    private int myPlayerId = -1;
    private int opponentPlayerId = -1;
    private String roomCode = "";

    private int clientChoice = -1;
    private int hostPlayerChoice = -1;
    private int hostOpponentChoice = -1;

    private volatile long currentStateVersion = 0;
    private volatile int remoteChoiceReceived = -1;
    private OnlineChatHelper chatHelper;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    private LinearLayout lobbyLayout;
    private FrameLayout gameLayout;
    private TextView roomCodeText;
    private TextView connectionStatusText;
    private TextView statusText;
    private TextView resultText;
    private ProgressBar loadingBar;

    private TextView scorePlayerText;
    private TextView scoreOpponentText;
    private TextView turnStatusText;

    private Button buttonRock;
    private Button buttonPaper;
    private Button buttonScissors;

    private TextView chatDisplay;
    private ScrollView chatScroll;
    private EditText chatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        String savedToken = prefs.getString("last_peer_token", null);

        server = new GameSocketServer(this);
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);

        client = GameSocketClient.getInstance(this);
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);

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

        gameLayout = new FrameLayout(this);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f));
        gameLayout.setVisibility(View.GONE);

        LinearLayout gameContent = new LinearLayout(this);
        gameContent.setOrientation(LinearLayout.VERTICAL);

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

    private void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

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

    private void onClientDisconnected(int clientId, String reason) {
        if (clientId == opponentPlayerId) {
            mainHandler.post(() -> {
                Toast.makeText(this, "对手已断开: " + reason, Toast.LENGTH_SHORT).show();
                isPlaying = false;
                showLobby();
            });
        }
    }

    private void onClientConnectedToHost(int clientId) {
        myPlayerId = 2;
        isPlaying = true;

        String token = client.getPeerToken();
        if (token != null && !token.isEmpty()) {
            prefs.edit().putString("last_peer_token", token).apply();
        }

        mainHandler.post(() -> {
            connectionStatusText.setText("已连接到主机");
            showGameScreen();
        });
    }

    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            Toast.makeText(this, "连接断开: " + reason, Toast.LENGTH_SHORT).show();
            isPlaying = false;
            showLobby();
        });
    }

    private void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show());
    }

    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "客户端错误: " + message, Toast.LENGTH_SHORT).show());
    }

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

    private void handleHostThrow(int clientId, JSONObject message) throws JSONException {
        int choice = message.getInt("choice");

        if (clientId == opponentPlayerId) {
            remoteChoiceReceived = choice;

            if (hostPlayerChoice >= 0) {
                resolveRound();
            }
        } else {
            hostPlayerChoice = choice;

            if (remoteChoiceReceived >= 0) {
                resolveRound();
            }
        }
    }

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

        sendSyncState(player1Choice, player2Choice, result);
        broadcastResult(player1Choice, player2Choice, result);

        final int myChoice = player1Choice;
        final int opponentChoice = player2Choice;
        final int finalResult = result;
        mainHandler.post(() -> showRoundResult(myChoice, opponentChoice, finalResult));

        scheduleNextRound();
    }

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

    private void scheduleNextRound() {
        mainHandler.postDelayed(() -> {
            hostPlayerChoice = -1;
            remoteChoiceReceived = -1;
            isMyTurn = true;
            startNewRound();
        }, 2000);
    }

    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        if (version <= currentStateVersion) return;
        currentStateVersion = version;

        int p1Choice = message.optInt("p1Choice", -1);
        int p2Choice = message.optInt("p2Choice", -1);
        int result = message.optInt("result", -1);

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

    private void handleReconnectState(JSONObject message) throws JSONException {
        currentStateVersion = message.optLong("stateVersion", 0);

        mainHandler.post(() -> {
            isPlaying = true;
            showGameScreen();
        });
    }

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

    private void showRoundResult(int myChoice, int opponentChoice, int result) {
        String[] names = {"石头", "剪刀", "布"};
        String[] emojis = {"✊", "✌️", "✋"};

        String resultText;
        int resultColor;

        if (result == 0) {
            resultText = "平局!";
            resultColor = 0xFFFF9800;
        } else if (myPlayerId == 1) {
            if (result == 1) {
                resultText = "你赢了!";
                resultColor = 0xFF4CAF50;
            } else {
                resultText = "对手赢了!";
                resultColor = 0xFFE53935;
            }
        } else {
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

    private void startNewRound() {
        clientChoice = -1;
        mainHandler.post(() -> {
            resultText.setText("请出拳!");
            resultText.setTextColor(0xFFFFFFFF);
            turnStatusText.setText("轮到你出拳!");
            setChoiceButtonsEnabled(true);
        });

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
                hostPlayerChoice = choice;
                onHostMessageReceived(myPlayerId, msg);
            } else {
                client.send(msg);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

    private void setChoiceButtonsEnabled(boolean enabled) {
        buttonRock.setEnabled(enabled);
        buttonPaper.setEnabled(enabled);
        buttonScissors.setEnabled(enabled);
        buttonRock.setAlpha(enabled ? 1.0f : 0.5f);
        buttonPaper.setAlpha(enabled ? 1.0f : 0.5f);
        buttonScissors.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
        isPlaying = true;
    }

    private void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
        isPlaying = false;
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }

    private String getChoiceName(int choice) {
        switch (choice) {
            case 0: return "石头";
            case 1: return "剪刀";
            case 2: return "布";
            default: return "?";
        }
    }

    private String getChoiceEmoji(int choice) {
        switch (choice) {
            case 0: return "✊";
            case 1: return "✌️";
            case 2: return "✋";
            default: return "?";
        }
    }
}

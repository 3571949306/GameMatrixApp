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
import androidx.cardview.widget.CardView;

import com.gamecenter.app.network.GameSocketClient;
import com.gamecenter.app.network.GameSocketServer;
import com.gamecenter.app.network.OnlineChatHelper;
import com.gamecenter.app.network.RelayHttpClient;
import com.gamecenter.app.network.RemoteP2PUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GomokuOnlineActivity extends AppCompatActivity {

    private static final String P2P_PREFS = "gomoku_p2p";
    private static final String PROTOCOL = "GMK";
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    private SharedPreferences prefs;
    private GameSocketServer server;
    private GameSocketClient client;

    private volatile boolean isHost = false;
    private volatile boolean gameStarted = false;
    private volatile boolean opponentHasJoined = false;
    private int myPlayerId = -1;
    private String roomCode = "";

    private GomokuGame game;
    private volatile long currentStateVersion = 0;
    private OnlineChatHelper chatHelper;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout lobbyLayout;
    private LinearLayout gameLayout;
    private TextView roomCodeText;
    private TextView statusText;
    private TextView turnText;
    private TextView winnerText;
    private GomokuView gomokuView;
    private ProgressBar loadingBar;
    private Button createRoomBtn;
    private Button joinRoomBtn;
    private Button leaveBtn;
    private AlertDialog waitingDialog;
    private TextView chatDisplay;
    private ScrollView chatScroll;
    private EditText chatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        game = new GomokuGame();

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

        CardView boardCard = new CardView(this);
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

    private void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
    }

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

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void showJoinDialog() {
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

    private void onClientDisconnected(int clientId, String reason) {
        mainHandler.post(() -> {
            Toast.makeText(this, "对手已断开: " + reason, Toast.LENGTH_LONG).show();
            gameStarted = false;
            showLobby();
        });
    }

    private void onClientConnectedToHost(int clientId) {
        myPlayerId = 2;
        gameStarted = true;

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

    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            Toast.makeText(this, "连接已断开: " + reason, Toast.LENGTH_LONG).show();
            gameStarted = false;
            showLobby();
        });
    }

    private void onServerError(String message) {
        mainHandler.post(() -> {
            Toast.makeText(this, "服务器错误: " + message, Toast.LENGTH_SHORT).show();
            createRoomBtn.setEnabled(true);
            joinRoomBtn.setEnabled(true);
        });
    }

    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(this, "错误: " + message, Toast.LENGTH_SHORT).show());
    }

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

    private void onClientMessageReceived(JSONObject message) {
        try {
            String type = message.optString("type", "");
            if (chatHelper.isChatMessage(message)) {
                chatHelper.handleIncomingChat(message);
                return;
            }
            if ("SYNC_STATE".equals(type)) {
                long version = message.optLong("stateVersion", 0);
                if (version <= currentStateVersion) return;
                currentStateVersion = version;

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

    private void sendSyncState() {
        try {
            currentStateVersion++;
            JSONObject state = new JSONObject();
            state.put("type", "SYNC_STATE");
            state.put("stateVersion", currentStateVersion);
            state.put("gameOver", game.isGameOver());

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

    private void onPlayerMove(int x, int y) {
        if (!gameStarted || game.isGameOver()) return;

        int currentColor = game.getCurrentPlayer();
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
            sendSyncState();
            if (game.isGameOver()) {
                broadcastGameOver();
            }
        } else {
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

    private void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
    }

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

    private void showGameScreen() {
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
    }

    private void showWaitingDialog(String roomCode) {
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

        TextView waitingView = new TextView(this);
        waitingView.setTextSize(14);
        waitingView.setText("等待中...");
        waitingView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        waitingView.setPadding(0, 8, 0, 0);
        waitingView.setTextColor(0xFFFF9800);
        waitingView.setTag("waiting_indicator");

        content.addView(roomCodeView);
        content.addView(copyBtn);
        content.addView(hintView);
        content.addView(waitingView);

        builder.setView(content);
        builder.setCancelable(false);
        builder.setNegativeButton("取消", (d, w) -> {
            leaveRoom();
        });

        waitingDialog = builder.create();
        waitingDialog.show();
    }

    private void showLobby() {
        gameLayout.setVisibility(View.GONE);
        lobbyLayout.setVisibility(View.VISIBLE);
        createRoomBtn.setEnabled(true);
        joinRoomBtn.setEnabled(true);
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }
}

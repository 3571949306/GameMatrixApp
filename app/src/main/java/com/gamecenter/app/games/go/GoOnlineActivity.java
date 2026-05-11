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

public class GoOnlineActivity extends AppCompatActivity {

    private static final String P2P_PREFS = "go_p2p";
    private static final String PROTOCOL = "GO";
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    private SharedPreferences prefs;
    private GameSocketServer server;
    private GameSocketClient client;

    private volatile boolean isHost = false;
    private volatile boolean isPlaying = false;
    private volatile boolean opponentHasJoined = false;
    private int myPlayerId = -1;
    private int opponentPlayerId = -1;
    private String roomCode = "";

    private GoGame game;
    private volatile long currentStateVersion = 0;
    private OnlineChatHelper chatHelper;

    private int myColor;

    private int blackCaptures;
    private int whiteCaptures;
    private int[] lastMovePos;
    private volatile boolean isGameOver = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout lobbyLayout;
    private FrameLayout gameLayout;
    private TextView roomCodeText;
    private TextView connectionStatusText;
    private TextView turnStatusText;
    private TextView winnerText;
    private TextView captureText;
    private ProgressBar loadingBar;

    private GoView boardView;

    private TextView chatDisplay;
    private ScrollView chatScroll;
    private EditText chatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        game = new GoGame();

        initViews();

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

    private void sendChatMessage() {
        String text = chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            chatHelper.sendChat(text);
            chatInput.setText("");
        }
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
        opponentHasJoined = true;
        mainHandler.post(() -> {
            connectionStatusText.setText("对手已加入!");
            isPlaying = true;
            startGame();
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
            startGame();
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

    private void handlePlaceStone(int clientId, JSONObject message) throws JSONException {
        if (clientId != opponentPlayerId) return;

        int x = message.getInt("x");
        int y = message.getInt("y");

        if (game.isValidMove(x, y)) {
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

            sendSyncState(false);

            mainHandler.post(this::updateTurnStatus);
        }
    }

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

    private void sendSyncState(boolean gameOver) {
        try {
            currentStateVersion++;
            JSONObject state = new JSONObject();
            state.put("type", "SYNC_STATE");
            state.put("stateVersion", currentStateVersion);
            state.put("gameOver", gameOver);

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

    private void broadcastGameOver() {
        try {
            JSONObject gameOverMsg = new JSONObject();
            gameOverMsg.put("type", "GAME_OVER");
            broadcast(gameOverMsg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        if (version <= currentStateVersion) return;
        currentStateVersion = version;

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
            for (int i = 0; i < GoGame.BOARD_SIZE; i++) {
                System.arraycopy(board[i], 0, game.getBoard()[i], 0, GoGame.BOARD_SIZE);
            }
        }

        blackCaptures = message.optInt("blackCaptures", 0);
        whiteCaptures = message.optInt("whiteCaptures", 0);

        int currentTurn = message.optInt("currentTurn", GoGame.BLACK);
        while (game.getCurrentPlayer() != currentTurn) {
            game.switchPlayer();
        }

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

    private void handleGameOver(JSONObject message) {
        isGameOver = true;
        game.setGameOver(true);
        mainHandler.post(this::updateTurnStatus);
    }

    private void placeStone(int x, int y) {
        if (!isPlaying || isGameOver) return;

        boolean isMyTurn = game.getCurrentPlayer() == myColor;
        if (!isMyTurn) return;

        if (!game.isValidMove(x, y)) return;

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
            sendSyncState(false);
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

        mainHandler.post(this::updateTurnStatus);
    }

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

    private void broadcast(JSONObject json) {
        if (isHost && server != null) {
            server.broadcast(json);
        }
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

}

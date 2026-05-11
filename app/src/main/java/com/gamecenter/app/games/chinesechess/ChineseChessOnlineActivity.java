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

public class ChineseChessOnlineActivity extends AppCompatActivity {

    private static final String P2P_PREFS = "xiangqi_p2p";
    private static final String PROTOCOL = "XQ";
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

    private ChineseChessGame game;
    private volatile long currentStateVersion = 0;
    private OnlineChatHelper chatHelper;

    private ChineseChessGame.Side mySide;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout lobbyLayout;
    private FrameLayout gameLayout;
    private TextView roomCodeText;
    private TextView connectionStatusText;
    private TextView turnStatusText;
    private TextView winnerText;
    private ProgressBar loadingBar;

    private ChineseChessView boardView;

    private int selectedX = -1;
    private int selectedY = -1;
    private List<int[]> selectedMoves;

    private TextView chatDisplay;
    private ScrollView chatScroll;
    private EditText chatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        game = new ChineseChessGame();

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
        mySide = myPlayerId == 1 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
        selectedX = -1;
        selectedY = -1;
        selectedMoves = null;
        showGameScreen();
        updateTurnStatus();
    }

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

    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        if (version <= currentStateVersion) return;
        currentStateVersion = version;

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

        int currentSideOrdinal = message.optInt("currentSide", 0);
        while (game.getCurrentSide().ordinal() != currentSideOrdinal) {
            game.switchSide();
        }

        if (message.optBoolean("gameOver", false)) {
            int winnerOrdinal = message.optInt("winner", 0);
            game.setGameOver(winnerOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK);
        }

        mainHandler.post(this::updateTurnStatus);
    }

    private void handleGameOver(JSONObject message) throws JSONException {
        int winnerOrdinal = message.optInt("winner", 0);
        ChineseChessGame.Side winnerSide = winnerOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
        game.setGameOver(winnerSide);
        mainHandler.post(this::updateTurnStatus);
    }

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
            sendSyncState();
            if (game.isGameOver()) {
                broadcastGameOver();
            }
        } else {
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

    private void selectPiece(int x, int y) {
        if (!isPlaying || game.isGameOver()) return;

        boolean isMyTurn = game.getCurrentSide() == mySide;
        if (!isMyTurn) return;

        ChineseChessGame.Piece piece = game.getBoard()[y][x];

        if (piece != null && piece.side == mySide) {
            selectedX = x;
            selectedY = y;
            selectedMoves = game.getLegalMoves(x, y);
            boardView.setSelected(x, y, selectedMoves);
        } else if (selectedX >= 0 && selectedMoves != null) {
            for (int[] move : selectedMoves) {
                if (move[0] == x && move[1] == y) {
                    makeMove(selectedX, selectedY, x, y);
                    return;
                }
            }
            selectedX = -1;
            selectedY = -1;
            selectedMoves = null;
            boardView.clearSelected();
        }
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

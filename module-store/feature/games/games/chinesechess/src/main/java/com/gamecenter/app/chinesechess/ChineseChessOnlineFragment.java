package com.gamecenter.app.chinesechess;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.network.GameSocketClient;
import com.gamecenter.app.network.GameSocketServer;
import com.gamecenter.app.network.OnlineChatHelper;
import com.gamecenter.app.network.OnlineDialogHelper;
import com.gamecenter.app.network.RelayHttpClient;
import com.gamecenter.app.network.RoomCodeHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * 中国象棋联机对战 Fragment。
 */
public class ChineseChessOnlineFragment extends Fragment {

    private static final String P2P_PREFS = "xiangqi_p2p";
    private static final String PROTOCOL = "XQ";
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;
    private static final String LOG_TAG = "ChineseChessOnline";
    private static final int MAX_SYNC_MOVES = 1000;

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
    private AlertDialog waitingDialog;
    private ScrollView chatScroll;
    private EditText chatInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        prefs = requireContext().getSharedPreferences(P2P_PREFS, Context.MODE_PRIVATE);
        game = new ChineseChessGame();

        View rootView = initViews();

        // 初始化WebSocket服务器（房主模式）
        server = new GameSocketServer(requireContext());
        server.setOnClientConnectedListener(this::onClientConnected);
        server.setOnClientDisconnectedListener(this::onClientDisconnected);
        server.setOnMessageReceivedListener(this::onHostMessageReceived);
        server.setOnErrorListener(this::onServerError);

        // 初始化WebSocket客户端（加入方模式）
        client = GameSocketClient.getInstance(requireContext());
        client.setPlayerName("Player");
        client.setOnConnectedListener(this::onClientConnectedToHost);
        client.setOnDisconnectedListener(this::onClientDisconnectedFromHost);
        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(this::onClientError);

        // 初始化聊天功能
        chatHelper = new OnlineChatHelper(requireContext());
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

        return rootView;
    }

    private View initViews() {
        Context ctx = requireContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        lobbyLayout = new LinearLayout(ctx);
        lobbyLayout.setOrientation(LinearLayout.VERTICAL);
        lobbyLayout.setPadding(48, 48, 48, 48);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        TextView titleText = new TextView(ctx);
        titleText.setText(getString(R.string.chess_online_title));
        titleText.setTextSize(24);
        titleText.setTextColor(0xFF1E1E32);
        titleText.setGravity(View.TEXT_ALIGNMENT_CENTER);

        Button createRoomBtn = new Button(ctx);
        createRoomBtn.setText(getString(R.string.chess_online_create_room));
        createRoomBtn.setTextSize(18);
        createRoomBtn.setBackgroundColor(0xFF4CAF50);
        createRoomBtn.setTextColor(0xFFFFFFFF);
        createRoomBtn.setPadding(32, 24, 32, 24);
        createRoomBtn.setOnClickListener(v -> createRoom());

        Button joinRoomBtn = new Button(ctx);
        joinRoomBtn.setText(getString(R.string.chess_online_join_room));
        joinRoomBtn.setTextSize(18);
        joinRoomBtn.setBackgroundColor(0xFF2196F3);
        joinRoomBtn.setTextColor(0xFFFFFFFF);
        joinRoomBtn.setPadding(32, 24, 32, 24);
        joinRoomBtn.setOnClickListener(v -> showJoinDialog());

        loadingBar = new ProgressBar(ctx);
        loadingBar.setVisibility(View.GONE);

        roomCodeText = new TextView(ctx);
        roomCodeText.setTextSize(20);
        roomCodeText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeText.setPadding(0, 16, 0, 16);

        connectionStatusText = new TextView(ctx);
        connectionStatusText.setTextSize(16);
        connectionStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        connectionStatusText.setPadding(0, 8, 0, 8);

        lobbyLayout.addView(titleText);
        lobbyLayout.addView(createRoomBtn);
        lobbyLayout.addView(joinRoomBtn);
        lobbyLayout.addView(loadingBar);
        lobbyLayout.addView(roomCodeText);
        lobbyLayout.addView(connectionStatusText);

        gameLayout = new FrameLayout(ctx);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f));
        gameLayout.setVisibility(View.GONE);

        LinearLayout gameContent = new LinearLayout(ctx);
        gameContent.setOrientation(LinearLayout.VERTICAL);

        turnStatusText = new TextView(ctx);
        turnStatusText.setTextSize(18);
        turnStatusText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        turnStatusText.setTextColor(0xFFFF9800);
        turnStatusText.setPadding(8, 8, 8, 4);

        winnerText = new TextView(ctx);
        winnerText.setTextSize(20);
        winnerText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        winnerText.setTextColor(0xFF4CAF50);
        winnerText.setPadding(8, 4, 8, 4);

        boardView = new ChineseChessView(ctx);
        boardView.bindGame(game);
        boardView.setOnCellClickListener(this::selectPiece);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1.0f);
        boardView.setLayoutParams(boardParams);

        chatDisplay = new TextView(ctx);
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

        chatScroll = new ScrollView(ctx);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().density * 80)));
        chatScroll.addView(chatDisplay);
        chatScroll.setSmoothScrollingEnabled(true);

        LinearLayout chatInputRow = new LinearLayout(ctx);
        chatInputRow.setOrientation(LinearLayout.HORIZONTAL);
        chatInputRow.setPadding(8, 0, 8, 4);

        chatInput = new EditText(ctx);
        chatInput.setHint(getString(R.string.chess_online_chat_hint));
        chatInput.setSingleLine(true);
        chatInput.setTextSize(14);
        chatInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button chatSendBtn = new Button(ctx);
        chatSendBtn.setText(getString(R.string.chess_online_send));
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

        Button leaveBtn = new Button(ctx);
        leaveBtn.setText(getString(R.string.chess_online_leave));
        leaveBtn.setTextSize(14);
        leaveBtn.setBackgroundColor(0xFF9E9E9E);
        leaveBtn.setTextColor(0xFFFFFFFF);
        leaveBtn.setPadding(16, 8, 16, 8);
        leaveBtn.setOnClickListener(v -> leaveRoom());

        LinearLayout btnRow = new LinearLayout(ctx);
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

        return root;
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
        connectionStatusText.setText(getString(R.string.chess_online_creating));

        new Thread(() -> {
            String code = RoomCodeHelper.generateRoomCode();
            String wsUrl = RelayHttpClient.getWebSocketUrl(RELAY_BASE_URL, code, "");
            boolean success = server.startWebSocket(wsUrl);
            mainHandler.post(() -> {
                loadingBar.setVisibility(View.GONE);
                if (success) {
                    isHost = true;
                    myPlayerId = 1;
                    roomCode = code;
                    connectionStatusText.setText(getString(R.string.chess_online_room_created));
                    roomCodeText.setText(getString(R.string.chess_online_room_code_format, code));
                    showWaitingDialog(roomCode);
                } else {
                    Toast.makeText(requireContext(), R.string.chess_online_create_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void showWaitingDialog(String roomCode) {
        waitingDialog = OnlineDialogHelper.showWaitingDialog(requireActivity(), roomCode, this::leaveRoom);
    }

    private void showJoinDialog() {
        OnlineDialogHelper.showJoinDialog(requireActivity(), this::joinRoom);
    }

    private void joinRoom(String roomCode) {
        loadingBar.setVisibility(View.VISIBLE);
        connectionStatusText.setText(getString(R.string.chess_online_joining_format, roomCode));

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
            connectionStatusText.setText(getString(R.string.chess_online_opponent_joined));
            isPlaying = true;
            startGame();
        });
    }

    private void onClientDisconnected(int clientId, String reason) {
        if (clientId == opponentPlayerId) {
            mainHandler.post(() -> {
                Toast.makeText(requireContext(), getString(R.string.chess_online_opponent_left_format, reason), Toast.LENGTH_SHORT).show();
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
            connectionStatusText.setText(getString(R.string.chess_online_connected_host));
            startGame();
        });
    }

    private void onClientDisconnectedFromHost(String reason) {
        mainHandler.post(() -> {
            Toast.makeText(requireContext(), getString(R.string.chess_online_disconnected_format, reason), Toast.LENGTH_SHORT).show();
            isPlaying = false;
            showLobby();
        });
    }

    private void onServerError(String message) {
        mainHandler.post(() -> Toast.makeText(requireContext(), getString(R.string.chess_online_server_error_format, message), Toast.LENGTH_SHORT).show());
    }

    private void onClientError(String message) {
        mainHandler.post(() -> Toast.makeText(requireContext(), getString(R.string.chess_online_client_error_format, message), Toast.LENGTH_SHORT).show());
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
            if (winner == null) {
                winnerText.setText("和棋");
            } else if (winner == mySide) {
                winnerText.setText(getString(R.string.chess_online_you_win));
            } else {
                winnerText.setText(getString(R.string.chess_online_opponent_wins));
            }
            turnStatusText.setText(getString(R.string.chess_online_game_over));
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

        // 集中闸门落子，并拒绝对手发来的非法着法（程序缺陷或对局作弊），
        // 杜绝非法局面污染本地棋盘（即"AI 非法吃将"类问题的联机对应形态）。
        ChineseChessGame.MoveRecord record = game.commitMove(fromX, fromY, toX, toY);
        if (record == null) {
            Log.e("ChineseChessOnline", "收到对手非法着法，已拒绝: "
                    + fromX + "," + fromY + "->" + toX + "," + toY);
            return;
        }

        sendSyncState();

        if (game.isGameOver()) {
            broadcastGameOver();
        }

        mainHandler.post(this::updateTurnStatus);
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
                state.put("winner", game.getWinner() == null ? -1 : game.getWinner().ordinal());
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
            gameOverMsg.put("winner", game.getWinner() == null ? -1 : game.getWinner().ordinal());
            broadcast(gameOverMsg);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleClientSyncState(JSONObject message) throws JSONException {
        long version = message.optLong("stateVersion", 0);
        if (version <= currentStateVersion) return;

        JSONArray moveHistoryArray = message.optJSONArray("moveHistory");
        if (moveHistoryArray != null && moveHistoryArray.length() > MAX_SYNC_MOVES) {
            Log.e(LOG_TAG, "拒绝超长同步历史: " + moveHistoryArray.length());
            return;
        }

        // 在临时棋局中完整验证后再原子替换，非法/截断历史不能污染当前棋盘。
        ChineseChessGame syncedGame = new ChineseChessGame();
        if (moveHistoryArray != null) {
            for (int i = 0; i < moveHistoryArray.length(); i++) {
                JSONObject moveObj = moveHistoryArray.optJSONObject(i);
                if (moveObj == null) {
                    Log.e(LOG_TAG, "同步历史第 " + i + " 手格式错误，已拒绝");
                    return;
                }
                int fx = moveObj.getInt("fx");
                int fy = moveObj.getInt("fy");
                int tx = moveObj.getInt("tx");
                int ty = moveObj.getInt("ty");
                if (syncedGame.commitMove(fx, fy, tx, ty) == null) {
                    Log.e(LOG_TAG, "同步历史第 " + i + " 手非法，已拒绝: "
                            + fx + "," + fy + "->" + tx + "," + ty);
                    return;
                }
            }
        }

        int currentSideOrdinal = message.optInt("currentSide", -1);
        if (currentSideOrdinal < 0 || currentSideOrdinal > 1
                || syncedGame.getCurrentSide().ordinal() != currentSideOrdinal) {
            Log.e(LOG_TAG, "同步走棋方与历史不一致，已拒绝: " + currentSideOrdinal);
            return;
        }

        boolean advertisedGameOver = message.optBoolean("gameOver", false);
        if (syncedGame.isGameOver() != advertisedGameOver) {
            Log.e(LOG_TAG, "同步终局状态与合法重放结果不一致，已拒绝");
            return;
        }
        if (advertisedGameOver) {
            int winnerOrdinal = message.optInt("winner", -2);
            if (winnerOrdinal < -1 || winnerOrdinal > 1) {
                Log.e(LOG_TAG, "同步胜方编码非法，已拒绝: " + winnerOrdinal);
                return;
            }
            ChineseChessGame.Side syncedWinner = winnerOrdinal < 0 ? null
                    : (winnerOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK);
            if (syncedGame.getWinner() != syncedWinner) {
                Log.e(LOG_TAG, "同步胜方与合法重放结果不一致，已拒绝");
                return;
            }
        }

        currentStateVersion = version;
        game = syncedGame;
        mainHandler.post(() -> {
            boardView.bindGame(game);
            updateTurnStatus();
        });
    }

    private void handleGameOver(JSONObject message) throws JSONException {
        int winnerOrdinal = message.optInt("winner", -2);
        if (winnerOrdinal < -1 || winnerOrdinal > 1) {
            Log.e(LOG_TAG, "忽略非法终局胜方编码: " + winnerOrdinal);
            return;
        }
        ChineseChessGame.Side winnerSide = winnerOrdinal < 0 ? null
                : (winnerOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK);
        game.setGameOver(winnerSide);
        mainHandler.post(this::updateTurnStatus);
    }

    private void makeMove(int fromX, int fromY, int toX, int toY) {
        if (!isPlaying || game.isGameOver()) return;

        boolean isMyTurn = game.getCurrentSide() == mySide;
        if (!isMyTurn) return;

        ChineseChessGame.Piece piece = game.getBoard()[fromY][fromX];
        if (piece == null || piece.side != mySide) return;

        // 集中闸门：校验 + 落子 + 切换 + 记录 + 终局判定原子完成。
        // 玩家着法来自 getLegalMoves（已合法），再次经 isMoveLegal 防御性把关。
        ChineseChessGame.MoveRecord record = game.commitMove(fromX, fromY, toX, toY);
        if (record == null) return;

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
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatHelper != null) chatHelper.cleanup();
        if (server != null) server.stop();
        if (client != null) client.release();
    }
}

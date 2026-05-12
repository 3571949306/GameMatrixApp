package com.gamecenter.app.games.doudizhu;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;
import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.network.GameSocketClient;
import com.gamecenter.app.games.doudizhu.network.GameSocketServer;
import com.gamecenter.app.games.doudizhu.network.LANManager;
import com.gamecenter.app.games.doudizhu.network.RelayHttpClient;
import com.gamecenter.app.games.doudizhu.network.RemoteP2PUtil;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DouDiZhuOnlineActivity extends AppCompatActivity {

    private static final String TAG = "DouDiZhuOnline";
    public static final String EXTRA_REMOTE_P2P = "remote_p2p";

    private static final int STATE_LOBBY = 0;
    private static final int STATE_BIDDING = 1;
    private static final int STATE_PLAYING = 2;
    private static final int STATE_GAME_OVER = 3;

    private static final int TOTAL_SEATS = 3;
    private static final int DEFAULT_SERVER_PORT = 8765;
    private static final int[] HOST_PORT_CANDIDATES = {8765, 8766, 8767, 8768, 8769};
    private static final long AI_THINKING_DELAY = 1500L;
    private static final int P2P_PROTOCOL_VERSION = 2;
    private static final int SEAT_TYPE_HOST = DouDiZhuSeatManager.SEAT_TYPE_HOST;
    private static final int SEAT_TYPE_REMOTE = DouDiZhuSeatManager.SEAT_TYPE_REMOTE;
    private static final int SEAT_TYPE_AI = DouDiZhuSeatManager.SEAT_TYPE_AI;
    private static final int REMOTE_RECONNECT_ATTEMPTS = 120;
    private static final long REMOTE_RECONNECT_INTERVAL_MS = 2500L;
    private static final long REMOTE_RECONNECT_MAX_INTERVAL_MS = 15000L;
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    // ============ 调试日志开关 ============
    private static final boolean DEBUG_NETWORK = true;
    private static final String LOG_PREFIX = "[DDZ-WSS]";

    private void logEvent(String event, String roomCode, int playerId, String messageType) {
        if (!DEBUG_NETWORK) return;
        Log.d(TAG, LOG_PREFIX + " [" + event + "] room=" + (roomCode != null ? roomCode : "-")
                + " player=" + playerId + " type=" + (messageType != null ? messageType : "-")
                + " t=" + System.currentTimeMillis());
    }

    private void logGame(String event, int seatIndex, String detail) {
        if (!DEBUG_NETWORK) return;
        Log.d(TAG, LOG_PREFIX + " [GAME_" + event + "] seat=" + seatIndex + " " + (detail != null ? detail : ""));
    }

    private void logSeatState(String context) {
        if (!DEBUG_NETWORK) return;
        StringBuilder sb = new StringBuilder();
        sb.append(context).append(" | mode=").append(mode);
        sb.append(" gameState=").append(gameState);
        sb.append(" seats=[");
        for (int i = 0; i < TOTAL_SEATS; i++) {
            if (i > 0) sb.append(", ");
            sb.append(i).append(":type=").append(seatTypes[i])
              .append(",cid=").append(seatClientIds[i]);
        }
        sb.append("]");
        Log.d(TAG, LOG_PREFIX + " [SEAT_STATE] " + sb.toString());
    }

    private DouDiZhuTableView tableView;
    private LinearLayout lobbyContainer;
    private TextView tvServerInfo;
    private TextView tvRoomList;
    private TextView tvConnectionStatus;
    private Button btnCreateRoom;
    private Button btnJoinRoom;
    private Button btnCopyRoomAddress;
    private Button btnStartGame;
    private Button btnDisconnect;
    private LinearLayout buttonContainer;
    private LinearLayout bidButtonLayout;
    private LinearLayout playButtonLayout;
    private Button btnCallLandlord;
    private Button btnNoCall;
    private Button btnPlayCard;
    private Button btnHint;
    private Button btnPass;
    private ProgressBar progressLoading;
    private LinearLayout gameOverDialog;
    private TextView tvGameOverTitle;
    private TextView tvGameOverResult;
    private TextView tvScoreDetail;
    private Button btnPlayAgain;
    private Button btnExit;
    private LinearLayout chatContainer;
    private ScrollView chatScrollView;
    private TextView tvChatMessages;
    private EditText etChatInput;
    private Button btnSendChat;
    private LinearLayout topStatusBar;
    private TextView tvLandlordIndicator;
    private TextView tvTurnIndicator;

    private LANManager lanManager;
    private GameSocketServer server;
    private GameSocketClient client;

    // ============ 新管理器 ============
    private DouDiZhuSeatManager seatManager;
    private DouDiZhuSyncManager syncManager;

    private int mode = -1;
    private boolean remoteP2PMode = false;
    private String localPeerToken = "";

    private static final String P2P_PREFS = "doudizhu_p2p";
    private static final String KEY_PEER_TOKEN = "peer_token";

    // ============ 兼容旧代码的变量（已委托给管理器，保留为向后兼容） ============
    private int[] seatTypes = new int[]{DouDiZhuSeatManager.SEAT_TYPE_HOST, DouDiZhuSeatManager.SEAT_TYPE_AI, DouDiZhuSeatManager.SEAT_TYPE_AI};
    private int[] seatClientIds = new int[]{-1, -1, -1};
    private String[] seatClientIps = new String[]{"", "", ""};
    private String[] seatPeerTokens = new String[]{"", "", ""};
    private Map<Integer, String> pendingClientIps = new HashMap<>();
    private String remoteHostInfoText = "";
    private String remoteInviteAddress = "";
    private String remoteRoomCode = "";
    private long[] lastProcessedActionIds = new long[]{0L, 0L, 0L};

    // ============ 游戏状态 ============
    private int gameState = STATE_LOBBY;
    private int currentTurn = 0;
    private int landlordIndex = -1;
    private int winnerIndex = -1;
    private int lastPlayerWhoPlayed = -1;
    private boolean[] playerPassed = new boolean[]{false, false, false};
    private int bidTurn = 0;
    private int bidRound = 0;
    private int mySeatIndex = -1;

    private List<Card> playerHandCards = new ArrayList<>();
    private List<Card> seat1Cards = new ArrayList<>();
    private List<Card> seat2Cards = new ArrayList<>();
    private List<Card> bottomCards = new ArrayList<>();

    private List<Card> playerPlayedCards = new ArrayList<>();
    private List<Card> seat1PlayedCards = new ArrayList<>();
    private List<Card> seat2PlayedCards = new ArrayList<>();

    private List<Card>[] aiBotHands = new List[]{null, null};
    private int[] handCounts = new int[]{17, 17, 17};
    private int[] cardCounterCounts = createFullDeckCounter();

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable aiThinkingRunnable;
    private boolean isCleaningUp = false;
    private DouDiZhuSoundManager soundManager;
    private int lastTurnSoundState = -1;
    private int lastTurnSoundSeat = -1;

    private StringBuilder chatLog = new StringBuilder();
    private final List<JSONObject> hostChatHistory = new ArrayList<>();
    private JSONObject pendingClientIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        remoteP2PMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_REMOTE_P2P, false);
        seatManager = new DouDiZhuSeatManager();
        seatManager.setContext(this);
        localPeerToken = seatManager.getLocalPeerToken();
        syncManager = new DouDiZhuSyncManager(seatManager, null);
        setContentView(R.layout.activity_doudizhu_online);
        soundManager = new DouDiZhuSoundManager(this);

        handler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            initViews();
            initListeners();
            initNetwork();
            showLobby();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
        releaseSoundManager();
    }

    private void initViews() {
        tableView = findViewById(R.id.tableView);
        lobbyContainer = findViewById(R.id.lobbyContainer);
        tvServerInfo = findViewById(R.id.tvServerInfo);
        tvRoomList = findViewById(R.id.tvRoomList);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        btnCopyRoomAddress = findViewById(R.id.btnCopyRoomAddress);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        buttonContainer = findViewById(R.id.buttonContainer);
        bidButtonLayout = findViewById(R.id.bidButtonLayout);
        playButtonLayout = findViewById(R.id.playButtonLayout);
        btnCallLandlord = findViewById(R.id.btnCallLandlord);
        btnNoCall = findViewById(R.id.btnNoCall);
        btnPlayCard = findViewById(R.id.btnPlayCard);
        btnHint = findViewById(R.id.btnHint);
        btnPass = findViewById(R.id.btnPass);
        progressLoading = findViewById(R.id.progressLoading);
        gameOverDialog = findViewById(R.id.gameOverDialog);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvGameOverResult = findViewById(R.id.tvGameOverResult);
        tvScoreDetail = findViewById(R.id.tvScoreDetail);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnExit = findViewById(R.id.btnExit);
        chatContainer = findViewById(R.id.chatContainer);
        chatScrollView = findViewById(R.id.chatScrollView);
        tvChatMessages = findViewById(R.id.tvChatMessages);
        etChatInput = findViewById(R.id.etChatInput);
        btnSendChat = findViewById(R.id.btnSendChat);
        topStatusBar = findViewById(R.id.topStatusBar);
        tvLandlordIndicator = findViewById(R.id.tvLandlordIndicator);
        tvTurnIndicator = findViewById(R.id.tvTurnIndicator);
    }

    private void initListeners() {
        if (btnCreateRoom != null) btnCreateRoom.setOnClickListener(v -> startAsHost());
        if (btnJoinRoom != null) btnJoinRoom.setOnClickListener(v -> startClientDiscovery());
        if (btnCopyRoomAddress != null) btnCopyRoomAddress.setOnClickListener(v -> copyRoomAddressToClipboard());
        if (btnStartGame != null) btnStartGame.setOnClickListener(v -> onStartGame());
        if (btnDisconnect != null) btnDisconnect.setOnClickListener(v -> onDisconnect());
        if (btnCallLandlord != null) btnCallLandlord.setOnClickListener(v -> onCallLandlord());
        if (btnNoCall != null) btnNoCall.setOnClickListener(v -> onNoCall());
        if (btnPlayCard != null) btnPlayCard.setOnClickListener(v -> onPlayCard());
        if (btnHint != null) btnHint.setOnClickListener(v -> onHint());
        if (btnPass != null) btnPass.setOnClickListener(v -> onPass());
        if (btnPlayAgain != null) btnPlayAgain.setOnClickListener(v -> onPlayAgain());
        if (btnExit != null) btnExit.setOnClickListener(v -> finish());
        if (btnSendChat != null) btnSendChat.setOnClickListener(v -> onSendChat());
        if (tableView != null) tableView.setOnCardTouchListener(cards -> {});
    }

    private void initNetwork() {
        try {
            lanManager = LANManager.getInstance(this);
            lanManager.setOnServiceDiscoveredListener(info -> updateRoomList());
            lanManager.setOnServiceLostListener(info -> updateRoomList());
            lanManager.setOnErrorListener(error -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (error != null && (error.contains("注册") || error.contains("发现"))) {
                        String hint = "自动发现不可用，请使用手动IP加入";
                        Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
                        if (mode == 0 && tvRoomList != null) {
                            tvRoomList.setText(hint + "\n房主界面上方会显示可输入的IP和端口");
                        }
                    } else {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "initNetwork failed", e);
        }
    }

    private void showLobby() {
        gameState = STATE_LOBBY;
        if (lobbyContainer != null) lobbyContainer.setVisibility(View.VISIBLE);
        if (topStatusBar != null) topStatusBar.setVisibility(View.GONE);
        if (buttonContainer != null) buttonContainer.setVisibility(View.GONE);
        if (chatContainer != null) chatContainer.setVisibility(View.GONE);
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.GONE);
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        if (btnCreateRoom != null) btnCreateRoom.setVisibility(View.VISIBLE);
        if (btnCopyRoomAddress != null) btnCopyRoomAddress.setVisibility(View.GONE);
        if (btnJoinRoom != null) {
            btnJoinRoom.setVisibility(View.VISIBLE);
            btnJoinRoom.setText(remoteP2PMode ? "输入房间码" : "加入房间");
            btnJoinRoom.setOnClickListener(v -> startClientDiscovery());
        }
        if (btnStartGame != null) btnStartGame.setVisibility(View.GONE);
        if (btnCreateRoom != null) btnCreateRoom.setText(remoteP2PMode ? "云开房" : "创建房间");
        if (tvServerInfo != null) tvServerInfo.setText(remoteP2PMode ? "斗地主云联机" : "选择操作创建或加入房间");
        if (tvRoomList != null) {
            tvRoomList.setText(remoteP2PMode
                    ? "房主点“云开房”生成 6 位房间码。\n\n其他玩家点“输入房间码”，输入或粘贴房间码即可加入。\n旧版 p2p://IP:端口 地址仍可作为高级直连入口。"
                    : "点击\"加入房间\"搜索局域网房间");
        }
    }

    // ============ Host Mode ============

    private void startAsHost() {
        mode = 0;
        mySeatIndex = 0;
        seatManager.resetAllSeats();
        seatManager.updateSeat(0, -1, "", "", DouDiZhuSeatManager.SEAT_TYPE_HOST);
        syncManager.resetHostStateVersion();

        if (btnCreateRoom != null) btnCreateRoom.setVisibility(View.GONE);
        if (btnCopyRoomAddress != null) btnCopyRoomAddress.setVisibility(View.GONE);
        if (btnJoinRoom != null) {
            btnJoinRoom.setVisibility(View.GONE);
            btnJoinRoom.setText("手动输入IP");
            btnJoinRoom.setOnClickListener(v -> showManualJoinDialog());
        }
        if (progressLoading != null) progressLoading.setVisibility(View.VISIBLE);
        if (tvServerInfo != null) tvServerInfo.setText("正在创建房间...");

        new Thread(() -> {
            server = new GameSocketServer(this);
            syncManager.setServer(server);
            server.setOnClientConnectedListener(this::onClientConnected);
            server.setOnClientDisconnectedListener(this::onClientDisconnected);
            server.setOnMessageReceivedListener(this::onServerMessageReceived);
            server.setOnErrorListener(error -> handler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(DouDiZhuOnlineActivity.this, error, Toast.LENGTH_SHORT).show();
                }
            }));

            if (remoteP2PMode) {
                // 生成 6 位随机房间码，直接通过 WebSocket 连接 Relay
                String roomCode = generateRoomCode();
                String wsUrl = RelayHttpClient.getWebSocketUrl(RELAY_BASE_URL, roomCode, "");

                if (!server.startWebSocket(wsUrl)) {
                    handler.post(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, "WebSocket 连接失败，请检查网络", Toast.LENGTH_SHORT).show();
                            showLobby();
                        }
                    });
                    return;
                }

                logEvent("CREATE_ROOM", roomCode, 0, "WEBSOCKET_HOST");
                String invite = RemoteP2PUtil.formatRelayInvite(roomCode);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    remoteRoomCode = roomCode;
                    remoteInviteAddress = invite;
                    remoteHostInfoText = buildRelayHostInfo(roomCode);
                    if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                    if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
                    if (tvServerInfo != null) tvServerInfo.setText("云房间码: " + roomCode);
                    if (btnCopyRoomAddress != null) {
                        btnCopyRoomAddress.setVisibility(View.VISIBLE);
                        btnCopyRoomAddress.setText("复制房间码");
                    }
                    if (btnStartGame != null) {
                        btnStartGame.setVisibility(View.VISIBLE);
                        btnStartGame.setText("开始游戏");
                    }
                    updateLobbyStatus();
                });
                return;
            }

            int port = findAvailablePort();
            if (!server.start(port)) {
                handler.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, "创建房间失败", Toast.LENGTH_SHORT).show();
                        showLobby();
                    }
                });
                return;
            }

            String localIP = lanManager != null ? lanManager.getLocalIPv4Address() : "";
            if (!remoteP2PMode && lanManager != null) {
                lanManager.registerService("斗地主_" + android.os.Build.MODEL, port);
            }
            String hostAddress = formatHostAddress(localIP, port);
            String localInviteAddress = RemoteP2PUtil.formatInviteAddress(localIP, port);

            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
                if (tvServerInfo != null) tvServerInfo.setText((remoteP2PMode ? "远程房间地址: " : "房间地址: ") + hostAddress);
                if (remoteP2PMode) {
                    remoteInviteAddress = localInviteAddress;
                    if (btnCopyRoomAddress != null) btnCopyRoomAddress.setVisibility(View.VISIBLE);
                    remoteHostInfoText = buildRemoteHostInfo(localIP, port, null);
                    updateLobbyStatus();
                    fetchPublicAddressForHost(port, localIP);
                } else if (tvRoomList != null) {
                    tvRoomList.setText("等待玩家加入... (0/2)\n自动发现失败时，请在客机手动输入房主IP");
                }
                if (btnStartGame != null) {
                    btnStartGame.setVisibility(View.VISIBLE);
                    btnStartGame.setText("开始游戏");
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

    private int findAvailablePort() {
        for (int port : HOST_PORT_CANDIDATES) {
            try {
                java.net.ServerSocket socket = new java.net.ServerSocket(port);
                socket.close();
                return port;
            } catch (Exception e) {
                continue;
            }
        }
        return DEFAULT_SERVER_PORT;
    }

    private String formatHostAddress(String ip, int port) {
        if (ip == null || ip.trim().isEmpty()) {
            return "请查看本机IP:" + port;
        }
        return ip + ":" + port;
    }

    private String buildRelayHostInfo(String roomCode) {
        String code = RemoteP2PUtil.normalizeRoomCode(roomCode);
        if (code.isEmpty()) {
            return "云房间已开启，正在生成房间码...";
        }
        return "云房间已开启\n\n"
                + "房间码: " + code + "\n"
                + "让其他玩家进入 斗地主 → 云联机 → 输入房间码。\n"
                + "建议直接点“复制房间码”发给对方，系统会自动识别粘贴内容。";
    }

    private String buildRemoteHostInfo(String localIp, int port, String publicIp) {
        StringBuilder sb = new StringBuilder();
        sb.append("远程 P2P 房间已开启\n");
        if (publicIp != null && !publicIp.trim().isEmpty()) {
            sb.append("公网地址: ").append(publicIp.trim()).append(":").append(port).append("\n");
        } else {
            sb.append("公网地址: 正在检测...\n");
        }
        if (localIp != null && !localIp.trim().isEmpty()) {
            sb.append("本地地址: ").append(localIp.trim()).append(":").append(port).append("\n");
        }
        sb.append("\n连接条件: 房主需要公网IP、IPv6，或路由器端口映射到本机端口 ")
                .append(port)
                .append("。\nAndroid 16/高版本建议保持应用在前台，避免系统省电策略中断网络。");
        return sb.toString();
    }

    private void copyRoomAddressToClipboard() {
        if (!remoteP2PMode || mode != 0) {
            Toast.makeText(this, "只有云联机房主可以复制房间码", Toast.LENGTH_SHORT).show();
            return;
        }
        String address = remoteInviteAddress != null ? remoteInviteAddress.trim() : "";
        if (address.isEmpty()) {
            Toast.makeText(this, "房间码还在生成中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "无法访问剪切板", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("斗地主云房间码", address));
        String copiedCode = !remoteRoomCode.isEmpty() ? remoteRoomCode : RemoteP2PUtil.findRoomCode(address);
        Toast.makeText(this, "已复制房间码: " + copiedCode, Toast.LENGTH_SHORT).show();
    }

    private void fetchPublicAddressForHost(int port, String localIp) {
        new Thread(() -> {
            String publicIp = fetchPublicIp();
            handler.post(() -> {
                if (isFinishing() || isDestroyed() || mode != 0 || !remoteP2PMode) return;
                String publicInviteAddress = RemoteP2PUtil.formatInviteAddress(publicIp, port);
                if (!publicInviteAddress.isEmpty()) {
                    remoteInviteAddress = publicInviteAddress;
                }
                remoteHostInfoText = buildRemoteHostInfo(localIp, port, publicIp);
                updateLobbyStatus();
            });
        }, "DdzPublicIpLookup").start();
    }

    private String fetchPublicIp() {
        String[] endpoints = new String[]{
                "https://api.ipify.org",
                "https://ipv4.icanhazip.com"
        };
        for (String endpoint : endpoints) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "GameCenterApp-DDZ-P2P");
                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String ip = reader.readLine();
                        if (ip != null && !ip.trim().isEmpty()) {
                            return ip.trim();
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "fetchPublicIp failed: " + endpoint + " " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return "";
    }

    // ============ Client Mode ============

    private void startClientDiscovery() {
        if (remoteP2PMode) {
            showRemoteJoinDialog();
            return;
        }
        mode = 1;
        mySeatIndex = -1;
        syncManager.resetClientState();
        lanManager.startDiscovery();

        if (btnCreateRoom != null) btnCreateRoom.setVisibility(View.GONE);
        if (btnJoinRoom != null) {
            btnJoinRoom.setVisibility(View.VISIBLE);
            btnJoinRoom.setText("手动输入IP");
            btnJoinRoom.setOnClickListener(v -> showManualJoinDialog());
        }
        if (tvServerInfo != null) tvServerInfo.setText("正在搜索房间...");
        if (tvRoomList != null) tvRoomList.setText("搜索中，请稍候...\n\n如果没有找到房间，可以手动输入IP");

        Button btnManual = new Button(this);
        btnManual.setText("手动输入IP");
        btnManual.setTextSize(12);
        btnManual.setOnClickListener(v -> showManualJoinDialog());

        handler.postDelayed(() -> {
            updateRoomList();
            if (btnJoinRoom != null && btnJoinRoom.getVisibility() == View.VISIBLE) return;
            if (lobbyContainer != null && lobbyContainer instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) lobbyContainer;
                if (layout.getChildCount() > 0) {
                    LinearLayout btnRow = null;
                    for (int i = 0; i < layout.getChildCount(); i++) {
                        View child = layout.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            btnRow = (LinearLayout) child;
                            break;
                        }
                    }
                    if (btnRow != null) {
                        btnManual.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                        btnRow.addView(btnManual);
                    }
                }
            }
        }, 3000);
    }

    private void updateRoomList() {
        if (lanManager == null || tvRoomList == null) return;
        List<NsdServiceInfo> services = lanManager.getDiscoveredServices();
        if (services.isEmpty()) {
            tvRoomList.setOnClickListener(v -> showManualJoinDialog());
            tvRoomList.setText("未发现房间\n\n请确保与房主在同一WiFi网络下");
            return;
        }
        StringBuilder sb = new StringBuilder("发现房间:\n\n");
        for (int i = 0; i < services.size(); i++) {
            NsdServiceInfo info = services.get(i);
            sb.append(i + 1).append(". ").append(info.getServiceName());
            if (info.getHost() != null) {
                sb.append(" (").append(info.getHost().getHostAddress()).append(":").append(info.getPort()).append(")");
            }
            sb.append("\n");
        }
        sb.append("\n点击房间名称加入");
        tvRoomList.setText(sb.toString());

        tvRoomList.setOnClickListener(v -> {
            if (!services.isEmpty()) {
                showRoomSelectionDialog(services);
            }
        });
    }

    private void showRoomSelectionDialog(List<NsdServiceInfo> services) {
        List<NsdServiceInfo> validServices = new ArrayList<>();
        for (NsdServiceInfo info : services) {
            if (info.getHost() != null && info.getPort() > 0) {
                validServices.add(info);
            }
        }

        if (validServices.isEmpty()) {
            Toast.makeText(this, "房间正在解析中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[validServices.size()];
        for (int i = 0; i < validServices.size(); i++) {
            NsdServiceInfo info = validServices.get(i);
            names[i] = info.getServiceName();
            if (info.getHost() != null) {
                names[i] += " (" + info.getHost().getHostAddress() + ")";
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择房间")
                .setItems(names, (dialog, which) -> {
                    NsdServiceInfo info = validServices.get(which);
                    lanManager.stopDiscovery();
                    connectToServer(info.getHost().getHostAddress(), info.getPort());
                })
                .setNegativeButton("手动输入", (dialog, which) -> showManualJoinDialog())
                .show();
    }

    private void showManualJoinDialog() {
        if (remoteP2PMode) {
            showRemoteJoinDialog();
            return;
        }
        lanManager.stopDiscovery();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        TextView ipLabel = new TextView(this);
        ipLabel.setText("房主IP");
        layout.addView(ipLabel);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint("例如 192.168.1.100");
        input.setText(getSuggestedIpPrefix());
        input.setSelection(input.getText().length());
        layout.addView(input);

        TextView portLabel = new TextView(this);
        portLabel.setText("端口");
        layout.addView(portLabel);

        Spinner portSpinner = new Spinner(this);
        List<String> ports = new ArrayList<>();
        for (int port : HOST_PORT_CANDIDATES) {
            ports.add(String.valueOf(port));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ports);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        portSpinner.setAdapter(adapter);
        layout.addView(portSpinner);

        new AlertDialog.Builder(this)
                .setTitle("加入房间")
                .setView(layout)
                .setPositiveButton("连接", (dialog, which) -> {
                    String address = input.getText().toString().trim();
                    if (LANManager.isValidIPAndPort(address)) {
                        String[] parts = address.split(":");
                        connectToServer(parts[0], Integer.parseInt(parts[1]));
                    } else if (LANManager.isValidIPAddress(address)) {
                        int port = Integer.parseInt((String) portSpinner.getSelectedItem());
                        connectToServer(address, port);
                    } else {
                        Toast.makeText(this, "请输入正确的IP地址", Toast.LENGTH_SHORT).show();
                        showManualJoinDialog();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    lanManager.stopDiscovery();
                    showLobby();
                })
                .show();
    }

    private void showRemoteJoinDialog() {
        if (lanManager != null) {
            lanManager.stopDiscovery();
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        TextView hostLabel = new TextView(this);
        hostLabel.setText("房间码");
        layout.addView(hostLabel);

        EditText hostInput = new EditText(this);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hostInput.setSingleLine(true);
        hostInput.setHint("例如 AB12CD，也可以直接粘贴房主发来的整段文字");
        String clipboardText = getClipboardText();
        String clipboardCode = RemoteP2PUtil.findRoomCode(clipboardText);
        if (!clipboardCode.isEmpty()) {
            hostInput.setText(clipboardCode);
            hostInput.setSelection(hostInput.getText().length());
        }
        layout.addView(hostInput);

        TextView hint = new TextView(this);
        hint.setText("默认使用云联机中转，不需要公网 IP 或路由器端口映射。高级直连仍支持 p2p://IP:端口。");
        hint.setTextSize(12);
        hint.setTextColor(0xFFB0BEC5);
        layout.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle("加入云联机房间")
                .setView(layout)
                .setPositiveButton("加入", (dialog, which) -> {
                    String raw = hostInput.getText().toString().trim();
                    RemoteP2PUtil.Endpoint endpoint = RemoteP2PUtil.parseEndpoint(raw, DEFAULT_SERVER_PORT);
                    if (endpoint != null && (raw.startsWith("p2p://") || raw.contains(":"))) {
                        connectToServer(endpoint.host, endpoint.port);
                        return;
                    }
                    String code = RemoteP2PUtil.findRoomCode(raw);
                    if (code.isEmpty()) {
                        Toast.makeText(this, "请输入 6 位房间码，或粘贴房主发来的邀请文字", Toast.LENGTH_SHORT).show();
                        showRemoteJoinDialog();
                        return;
                    }
                    connectToRelayRoom(code);
                })
                .setNegativeButton("取消", (dialog, which) -> showLobby())
                .show();
    }

    private String getClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
                    || clipboard.getPrimaryClip().getItemCount() == 0) {
                return "";
            }
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            return text != null ? text.toString() : "";
        } catch (Exception e) {
            Log.w(TAG, "getClipboardText failed: " + e.getMessage());
            return "";
        }
    }

    private String getSuggestedIpPrefix() {
        if (lanManager == null) return "192.168.1.";
        List<String> addresses = lanManager.getAllLocalIPv4Addresses();
        for (String ip : addresses) {
            if (ip == null || !ip.contains(".")) continue;
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) {
                return ip.substring(0, lastDot + 1);
            }
        }
        return "192.168.1.";
    }

    private void prepareClientConnection() {
        mode = 1;
        mySeatIndex = -1;
        pendingClientIntent = null;
        syncManager.resetClientState();
        client = GameSocketClient.getInstance(this);
        client.setPlayerName(android.os.Build.MODEL);
        client.setPeerToken(localPeerToken);
        client.setProtocolVersion(P2P_PROTOCOL_VERSION);
        if (remoteP2PMode) {
            client.setReconnectPolicy(REMOTE_RECONNECT_ATTEMPTS,
                    REMOTE_RECONNECT_INTERVAL_MS,
                    REMOTE_RECONNECT_MAX_INTERVAL_MS);
        } else {
            client.setReconnectPolicy(3, 2000L, 15000L);
        }

        client.setOnConnectedListener(clientId -> handler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (lobbyContainer != null) lobbyContainer.setVisibility(View.GONE);
            if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
            if (progressLoading != null) progressLoading.setVisibility(View.GONE);
            if (tvConnectionStatus != null) {
                tvConnectionStatus.setText("已连接");
                tvConnectionStatus.setTextColor(0xFF4CAF50);
            }
        }));

        client.setOnDisconnectedListener(reason -> handler.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "连接断开: " + reason, Toast.LENGTH_SHORT).show();
                if (tvConnectionStatus != null) {
                    tvConnectionStatus.setText("连接断开: " + reason);
                    tvConnectionStatus.setTextColor(0xFFFF5722);
                }
                hideAllButtons();
                if (!remoteP2PMode && gameState != STATE_PLAYING && gameState != STATE_BIDDING) {
                    showLobby();
                }
            }
        }));

        client.setOnMessageReceivedListener(this::onClientMessageReceived);
        client.setOnErrorListener(error -> handler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (tvConnectionStatus != null) {
                tvConnectionStatus.setText(error);
                tvConnectionStatus.setTextColor(0xFFFF9800);
            }
            if (!remoteP2PMode) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        }));
        client.setOnStateChangedListener(state -> handler.post(() -> {
            Log.d(TAG, "Client state: " + state);
            if (tvConnectionStatus != null) {
                tvConnectionStatus.setText("连接状态: " + state.name());
                tvConnectionStatus.setTextColor(state == GameSocketClient.ConnectionState.AUTHENTICATED
                        ? 0xFF4CAF50 : 0xFFFF9800);
            }
        }));

        if (progressLoading != null) progressLoading.setVisibility(View.VISIBLE);
        if (lobbyContainer != null) lobbyContainer.setVisibility(View.GONE);
    }

    private void connectToServer(String host, int port) {
        prepareClientConnection();
        client.connect(host, port);
    }

    private void connectToRelayRoom(String roomCode) {
        logEvent("JOIN_ROOM", roomCode, -1, "WEBSOCKET_CLIENT");
        prepareClientConnection();
        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText("正在加入云房间 " + roomCode);
            tvConnectionStatus.setTextColor(0xFFFF9800);
        }
        // 生成 WebSocket URL 并通过 WebSocket 连接 Relay（客户端角色）
        String wsUrl = RelayHttpClient.getWebSocketClientUrl(RELAY_BASE_URL, roomCode);
        client.connectWebSocket(wsUrl);
    }

    // ============ Connection Handling (Host) ============

    private void onClientConnected(int clientId, String ip) {
        logEvent("SEAT_ASSIGN", remoteRoomCode, clientId, "CONNECTED");
        Log.d(TAG, LOG_PREFIX + " [ON_CLIENT_CONNECTED] clientId=" + clientId + " ip=" + ip + " remoteP2PMode=" + remoteP2PMode);
        handler.post(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;

                Log.d(TAG, LOG_PREFIX + " [ON_CLIENT_CONNECTED] processing clientId=" + clientId);
                pendingClientIps.put(clientId, ip != null ? ip : "");
                if (remoteP2PMode) {
                    Log.d(TAG, LOG_PREFIX + " [ON_CLIENT_CONNECTED] remoteP2PMode=true, returning early WITHOUT seat assignment");
                    if (tvConnectionStatus != null) {
                        tvConnectionStatus.setText("远程连接已建立，等待身份确认");
                        tvConnectionStatus.setTextColor(0xFFFF9800);
                    }
                    updateLobbyStatus();
                    logSeatState("onClientConnected(early-return)");
                    return;
                }

                int seatIndex = assignSeatToClient(clientId, ip);
                if (seatIndex == -1) {
                    Log.w(TAG, "No seat available for client " + clientId);
                    if (server != null) {
                        try { server.sendTo(clientId, createErrorMsg("房间已满")); } catch (Exception e) {}
                    }
                    return;
                }

                if (seatClientIds[seatIndex] != clientId) {
                    lastProcessedActionIds[seatIndex] = 0L;
                }
                seatClientIds[seatIndex] = clientId;
                seatClientIps[seatIndex] = ip != null ? ip : "";
                seatTypes[seatIndex] = SEAT_TYPE_REMOTE;

                JSONObject assignMsg = new JSONObject();
                try {
                    assignMsg.put("type", "SEAT_ASSIGNED");
                    assignMsg.put("seatIndex", seatIndex);
                    assignMsg.put("seatName", getFixedSeatName(seatIndex));
                    assignMsg.put("seatTypes", seatTypesToJson());
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create SEAT_ASSIGNED: " + e.getMessage());
                }
                if (server != null) {
                    try { server.sendTo(clientId, assignMsg); } catch (Exception e) {
                        Log.e(TAG, "Failed to send SEAT_ASSIGNED: " + e.getMessage());
                    }
                }
                sendChatHistoryToClient(clientId);

                broadcastSeatUpdate();
                if (gameState == STATE_BIDDING || gameState == STATE_PLAYING || gameState == STATE_GAME_OVER) {
                    sendSyncStateToSeat(seatIndex);
                }
                updateLobbyStatus();

                try {
                    Toast.makeText(DouDiZhuOnlineActivity.this, "玩家加入 " + getFixedSeatName(seatIndex), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Toast failed: " + e.getMessage());
                }

                Log.d(TAG, "Client " + clientId + " assigned to " + getFixedSeatName(seatIndex));
            } catch (Exception e) {
                Log.e(TAG, "onClientConnected error: " + e.getMessage(), e);
            }
        });
    }

    private int assignSeatToClient(int clientId, String ip) {
        return assignSeatToClient(clientId, ip, "");
    }

    private int assignSeatToClient(int clientId, String ip, String peerToken) {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatClientIds[i] == clientId) {
                return i;
            }
        }

        if (peerToken != null && !peerToken.trim().isEmpty()) {
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (peerToken.equals(seatPeerTokens[i])) {
                    return i;
                }
            }
        }

        if (gameState == STATE_BIDDING || gameState == STATE_PLAYING) {
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (seatTypes[i] == SEAT_TYPE_REMOTE
                        && ip != null
                        && ip.equals(seatClientIps[i])) {
                    return i;
                }
            }
            if (currentTurn > 0 && currentTurn < TOTAL_SEATS
                    && seatTypes[currentTurn] == SEAT_TYPE_REMOTE
                    && seatClientIds[currentTurn] == -1) {
                return currentTurn;
            }
            for (int i = 1; i < TOTAL_SEATS; i++) {
                if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] == -1) {
                    return i;
                }
            }
        }
        if (seatClientIds[1] == -1) return 1;
        if (seatClientIds[2] == -1) return 2;
        return -1;
    }

    private void handleClientJoin(int clientId, JSONObject msg) {
        Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] clientId=" + clientId + " msgType=" + msg.optString("type", "?"));
        handler.post(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;

                String peerToken = msg.optString("peerToken", "");
                String ip = msg.optString("_remoteIp", pendingClientIps.getOrDefault(clientId, ""));
                int protocolVersion = msg.optInt("protocolVersion", 1);
                Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] processing clientId=" + clientId + " peerToken=" + peerToken + " ip=" + ip + " protocolVersion=" + protocolVersion);
                if (protocolVersion > P2P_PROTOCOL_VERSION + 10) {
                    Log.w(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] protocol version too high: " + protocolVersion);
                    if (server != null) {
                        server.sendTo(clientId, createErrorMsg("客户端协议版本过高，请更新房主端"));
                    }
                    return;
                }

                int seatIndex = assignSeatToClient(clientId, ip, peerToken);
                Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] assignSeatToClient returned seatIndex=" + seatIndex);
                if (seatIndex == -1) {
                    Log.w(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] no seat available!");
                    if (server != null) {
                        server.sendTo(clientId, createErrorMsg("房间已满或无法恢复原座位"));
                    }
                    return;
                }

                int previousClientId = seatClientIds[seatIndex];
                boolean sameToken = peerToken != null && !peerToken.isEmpty()
                        && peerToken.equals(seatPeerTokens[seatIndex]);
                boolean reconnected = sameToken || (previousClientId > 0 && previousClientId != clientId);

                if (previousClientId > 0 && previousClientId != clientId && server != null) {
                    server.disconnectClient(previousClientId, "同一玩家已重连");
                }

                if (!sameToken && previousClientId != clientId) {
                    lastProcessedActionIds[seatIndex] = 0L;
                }
                seatClientIds[seatIndex] = clientId;
                seatClientIps[seatIndex] = ip != null ? ip : "";
                seatPeerTokens[seatIndex] = peerToken != null ? peerToken : "";
                seatTypes[seatIndex] = SEAT_TYPE_REMOTE;

                Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] seat assigned: seatIndex=" + seatIndex + " clientId=" + clientId + " seatType=REMOTE");
                logSeatState("handleClientJoin(after-assign)");

                JSONObject assignMsg = new JSONObject();
                assignMsg.put("type", "SEAT_ASSIGNED");
                assignMsg.put("seatIndex", seatIndex);
                assignMsg.put("seatName", getFixedSeatName(seatIndex));
                assignMsg.put("seatTypes", seatTypesToJson());
                assignMsg.put("remoteP2P", remoteP2PMode);
                assignMsg.put("reconnected", reconnected);
                if (server != null) {
                    Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] sending SEAT_ASSIGNED to clientId=" + clientId);
                    server.sendTo(clientId, assignMsg);
                } else {
                    Log.e(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] server is NULL! Cannot send SEAT_ASSIGNED");
                }
                logEvent("SEAT_ASSIGN", remoteRoomCode, clientId, "SEAT_ASSIGNED");

                sendChatHistoryToClient(clientId);
                broadcastSeatUpdate();
                Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] gameState=" + gameState + " (LOBBY=0,BIDDING=1,PLAYING=2,GAMEOVER=3)");
                if (gameState == STATE_BIDDING || gameState == STATE_PLAYING || gameState == STATE_GAME_OVER) {
                    Log.d(TAG, LOG_PREFIX + " [HANDLE_CLIENT_JOIN] game in progress, sending SYNC_STATE to seat " + seatIndex);
                    sendSyncStateToSeat(seatIndex);
                }
                updateLobbyStatus();
                String joinText = remoteP2PMode ? " 已加入远程房间" : " 已加入房间";
                broadcastSystemMessage(getFixedSeatName(seatIndex)
                        + (reconnected ? " 已恢复连接" : joinText));
            } catch (Exception e) {
                Log.e(TAG, "handleClientJoin error", e);
                if (server != null) {
                    server.sendTo(clientId, createErrorMsg("加入房间失败，请重试"));
                }
            }
        });
    }

    private void onClientDisconnected(int clientId, String reason) {
        logEvent("LEAVE_ROOM", remoteRoomCode, clientId, "DISCONNECTED");
        handler.post(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;

                Log.d(TAG, "onClientDisconnected: clientId=" + clientId + " reason=" + reason);
                pendingClientIps.remove(clientId);

                int disconnectedSeat = -1;
                for (int i = 0; i < TOTAL_SEATS; i++) {
                    if (seatClientIds[i] == clientId) {
                        seatClientIds[i] = -1;
                        disconnectedSeat = i;
                        if (remoteP2PMode) {
                            seatTypes[i] = SEAT_TYPE_REMOTE;
                            try {
                                Toast.makeText(DouDiZhuOnlineActivity.this, getFixedSeatName(i) + " 断线，保留座位等待重连", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {}
                        } else if (gameState == STATE_LOBBY || gameState == STATE_GAME_OVER) {
                            seatTypes[i] = SEAT_TYPE_AI;
                            initAIForSeat(i);
                            try {
                                Toast.makeText(DouDiZhuOnlineActivity.this, getFixedSeatName(i) + " 离开，已替换为 AI", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {}
                        } else {
                            seatTypes[i] = SEAT_TYPE_REMOTE;
                            try {
                                Toast.makeText(DouDiZhuOnlineActivity.this, getFixedSeatName(i) + " 掉线，等待重连", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {}
                        }
                        break;
                    }
                }

                if (disconnectedSeat >= 0) {
                    if (remoteP2PMode) {
                        broadcastSystemMessage(getFixedSeatName(disconnectedSeat) + " 连接断开，座位保留等待重连");
                    } else if (gameState == STATE_LOBBY || gameState == STATE_GAME_OVER) {
                        broadcastSystemMessage(getSeatActorName(disconnectedSeat) + " 连接断开，已由 AI 接管");
                    } else {
                        broadcastSystemMessage(getFixedSeatName(disconnectedSeat) + " 连接断开，等待重连");
                    }
                    broadcastSeatUpdate();
                    broadcastSyncState();
                    updateTurnIndicator();
                }
                updateLobbyStatus();
            } catch (Exception e) {
                Log.e(TAG, "onClientDisconnected error: " + e.getMessage(), e);
            }
        });
    }

    private JSONArray seatTypesToJson() {
        return DouDiZhuProtocol.seatTypesToJson(seatTypes);
    }

    private JSONArray booleanArrayToJson(boolean[] values) {
        return DouDiZhuProtocol.booleanArrayToJson(values);
    }

    private JSONArray handCountsToJson() {
        return DouDiZhuProtocol.handCountsToJson(playerHandCards, seat1Cards, seat2Cards);
    }

    private JSONArray intArrayToJson(int[] values) {
        return DouDiZhuProtocol.intArrayToJson(values);
    }

    private int[] jsonToCounterArray(JSONArray array) {
        return DouDiZhuProtocol.jsonToCounterArray(array);
    }

    private int[] createFullDeckCounter() {
        return DouDiZhuProtocol.createFullDeckCounter();
    }

    private int rankCounterIndex(Card card) {
        return DouDiZhuProtocol.rankCounterIndex(card);
    }

    private void subtractCardsFromCounter(int[] counts, List<Card> cards) {
        DouDiZhuProtocol.subtractCardsFromCounter(counts, cards);
    }

    private int[] createCardCounterForSeat(int seatIndex) {
        int[] counts = createFullDeckCounter();
        subtractCardsFromCounter(counts, getSeatHandCards(seatIndex));
        subtractCardsFromCounter(counts, playerPlayedCards);
        subtractCardsFromCounter(counts, seat1PlayedCards);
        subtractCardsFromCounter(counts, seat2PlayedCards);
        return counts;
    }

    private void onDisconnect() {
        cleanup();
        showLobby();
        resetNetworkState();
    }

    private void updateLobbyStatus() {
        if (mode == 0 && server != null && tvRoomList != null) {
            if (remoteP2PMode) {
                int connected = 0;
                int reserved = 0;
                for (int i = 1; i < TOTAL_SEATS; i++) {
                    if (seatTypes[i] == SEAT_TYPE_REMOTE) {
                        reserved++;
                        if (seatClientIds[i] >= 0) {
                            connected++;
                        }
                    }
                }
                String base = remoteHostInfoText != null && !remoteHostInfoText.isEmpty()
                        ? remoteHostInfoText
                        : "云房间已开启";
                tvRoomList.setText(base + "\n\n云联机玩家: " + connected + "/" + Math.max(2, reserved)
                        + "\n断线玩家会保留座位，重连后自动恢复。");
                return;
            }
            int connected = server.getConnectedClientCount();
            tvRoomList.setText("等待玩家加入... (" + connected + "/2)");
        }
    }

    // ============ Game Start ============

    private void onStartGame() {
        if (mode != 0) return;
        if (remoteP2PMode && hasDisconnectedRemoteSeat()) {
            Toast.makeText(this, "有远程玩家断线，等待重连后再开始", Toast.LENGTH_SHORT).show();
            updateLobbyStatus();
            return;
        }
        if (remoteP2PMode && !hasAnyRemoteSeat()) {
            Toast.makeText(this, "请等待远程玩家加入后再开始游戏", Toast.LENGTH_SHORT).show();
            return;
        }
        startGame();
    }

    private boolean hasDisconnectedRemoteSeat() {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyRemoteSeat() {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE) {
                return true;
            }
        }
        return false;
    }

    private void startGame() {
        logGame("START_GAME", 0, "dealing cards");
        Log.d(TAG, LOG_PREFIX + " [START_GAME] called | mode=" + mode + " remoteP2PMode=" + remoteP2PMode);
        logSeatState("startGame(BEFORE)");
        gameState = STATE_BIDDING;
        winnerIndex = -1;
        playerPassed = new boolean[]{false, false, false};
        bidRound = 0;
        cardCounterCounts = createFullDeckCounter();
        resetTurnSoundMarker();

        List<Card>[] dealt = GameRuleUtil.shuffleAndDeal();
        playerHandCards = dealt[0];
        seat1Cards = dealt[1];
        seat2Cards = dealt[2];
        bottomCards = dealt[3];

        aiBotHands[0] = new ArrayList<>(seat1Cards);
        aiBotHands[1] = new ArrayList<>(seat2Cards);

        playerPlayedCards = new ArrayList<>();
        seat1PlayedCards = new ArrayList<>();
        seat2PlayedCards = new ArrayList<>();
        handCounts = new int[]{playerHandCards.size(), seat1Cards.size(), seat2Cards.size()};
        if (soundManager != null) soundManager.deal();

        if (lobbyContainer != null) lobbyContainer.setVisibility(View.GONE);
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        if (topStatusBar != null) topStatusBar.setVisibility(View.VISIBLE);
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);

        updateTableView();
        updateLandlordIndicator();

        currentTurn = (int) (Math.random() * 3);
        bidTurn = currentTurn;
        updateTurnIndicator();

        refreshLocalControls();

        if (mode == 0) {
            Log.d(TAG, LOG_PREFIX + " [START_GAME] mode=0, calling broadcastSyncState()");
            logSeatState("startGame(ABOUT-TO-BROADCAST)");
            broadcastSyncState();
        } else {
            Log.w(TAG, LOG_PREFIX + " [START_GAME] mode=" + mode + ", SKIPPING broadcastSyncState!");
        }

        if (seatTypes[currentTurn] == SEAT_TYPE_AI) {
            scheduleAIBid();
        } else if (currentTurn == 0 && mode == 0) {
            // Host player's turn to bid - buttons already shown
        } else if (seatTypes[currentTurn] == SEAT_TYPE_REMOTE) {
            sendBidRequestToRemote(currentTurn);
        }
    }

    // ============ Bidding Phase ============

    private void showBidUI() {
        if (buttonContainer != null) buttonContainer.setVisibility(View.VISIBLE);
        if (bidButtonLayout != null) bidButtonLayout.setVisibility(View.VISIBLE);
        if (playButtonLayout != null) playButtonLayout.setVisibility(View.GONE);
    }

    private void showPlayUI() {
        if (buttonContainer != null) buttonContainer.setVisibility(View.VISIBLE);
        if (bidButtonLayout != null) bidButtonLayout.setVisibility(View.GONE);
        if (playButtonLayout != null) playButtonLayout.setVisibility(View.VISIBLE);
    }

    private void hideAllButtons() {
        if (buttonContainer != null) buttonContainer.setVisibility(View.GONE);
        if (bidButtonLayout != null) bidButtonLayout.setVisibility(View.GONE);
        if (playButtonLayout != null) playButtonLayout.setVisibility(View.GONE);
    }

    private void showGameChrome() {
        if (lobbyContainer != null) lobbyContainer.setVisibility(View.GONE);
        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
        if (topStatusBar != null) topStatusBar.setVisibility(View.VISIBLE);
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
    }

    private void updateConnectedStatusText() {
        if (tvConnectionStatus == null) return;
        if (mode == 0) {
            tvConnectionStatus.setText(remoteP2PMode ? "云联机房主" : "主机");
            tvConnectionStatus.setTextColor(0xFF4CAF50);
        } else if (client != null && client.isConnected()) {
            tvConnectionStatus.setText(remoteP2PMode ? "已连接云房间" : "已连接主机");
            tvConnectionStatus.setTextColor(0xFF4CAF50);
        }
    }

    private void refreshLocalControls() {
        boolean canAct = isLocalSeat(currentTurn);
        if (gameState == STATE_BIDDING) {
            if (canAct) {
                showBidUI();
                playTurnSoundIfNeeded();
            } else {
                hideAllButtons();
            }
            return;
        }

        if (gameState == STATE_PLAYING) {
            if (canAct) {
                showPlayUI();
                enablePlayerControls(true);
                playTurnSoundIfNeeded();
            } else {
                hideAllButtons();
            }
            return;
        }

        hideAllButtons();
    }

    private void playClickSound() {
        if (soundManager != null) {
            soundManager.click();
        }
    }

    private void playTurnSoundIfNeeded() {
        if (soundManager == null || !isLocalSeat(currentTurn)) return;
        if (gameState != STATE_BIDDING && gameState != STATE_PLAYING) return;
        if (lastTurnSoundState == gameState && lastTurnSoundSeat == currentTurn) return;
        lastTurnSoundState = gameState;
        lastTurnSoundSeat = currentTurn;
        soundManager.turn();
    }

    private void resetTurnSoundMarker() {
        lastTurnSoundState = -1;
        lastTurnSoundSeat = -1;
    }

    private void onCallLandlord() {
        playClickSound();
        submitBid(true);
    }

    private void onNoCall() {
        playClickSound();
        submitBid(false);
    }

    private void submitBid(boolean call) {
        try {
            if (gameState != STATE_BIDDING) return;
            if (mode == 0 && currentTurn != 0) return;
            if (mode == 1 && (mySeatIndex < 0 || currentTurn != mySeatIndex)) return;
            if (soundManager != null) soundManager.bid(call);

            if (mode == 0) {
                logGame("BID", currentTurn, "call=" + call);
                if (call) {
                    setLandlord(0);
                    broadcastSystemMessage(getSeatActorName(0) + " 叫地主");
                    startPlayingPhase();
                } else {
                    int noCallSeat = currentTurn;
                    broadcastSystemMessage(getSeatActorName(noCallSeat) + " 不叫");
                    advanceBidTurn();
                }
                broadcastSyncState();
            } else {
                logGame("BID", mySeatIndex, "call=" + call);
                JSONObject msg = new JSONObject();
                msg.put("type", "BID_RESPONSE");
                msg.put("call", call);
                msg.put("seatIndex", mySeatIndex);
                msg.put("currentTurn", currentTurn);
                if (sendClientIntent(msg)) {
                    hideAllButtons();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "submitBid error", e);
            Toast.makeText(this, "提交叫地主失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean sendClientIntent(JSONObject msg) {
        if (client == null) {
            Toast.makeText(this, "未连接主机", Toast.LENGTH_SHORT).show();
            if (tvConnectionStatus != null) tvConnectionStatus.setText("未连接主机");
            return false;
        }
        decorateClientIntent(msg);
        if (!client.isConnected()) {
            queueClientIntentForReconnect(msg);
            return true;
        }
        boolean sent = client.send(msg);
        if (!sent) {
            queueClientIntentForReconnect(msg);
        } else {
            scheduleClientIntentRepeats(msg);
        }
        return sent;
    }

    private void decorateClientIntent(JSONObject msg) {
        if (msg == null) return;
        try {
            if (!msg.has("actionId")) {
                msg.put("actionId", syncManager.getNextClientActionId());
            }
            msg.put("clientStateVersion", syncManager.getClientLastStateVersion());
            msg.put("sentAt", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "decorateClientIntent error", e);
        }
    }

    private void scheduleClientIntentRepeats(JSONObject msg) {
        String type = msg.optString("type", "");
        if (!"BID_RESPONSE".equals(type) && !"REQUEST_PLAY".equals(type) && !"PASS".equals(type)) {
            return;
        }
        JSONObject firstRetry;
        JSONObject secondRetry;
        try {
            firstRetry = new JSONObject(msg.toString());
            secondRetry = new JSONObject(msg.toString());
        } catch (JSONException e) {
            return;
        }
        handler.postDelayed(() -> resendClientIntent(firstRetry), 220);
        handler.postDelayed(() -> resendClientIntent(secondRetry), 700);
    }

    private void resendClientIntent(JSONObject msg) {
        if (client == null || !client.isConnected()) return;
        String type = msg.optString("type", "");
        if ("BID_RESPONSE".equals(type) && gameState == STATE_BIDDING && currentTurn == mySeatIndex) {
            client.send(msg);
        } else if (("REQUEST_PLAY".equals(type) || "PASS".equals(type))
                && gameState == STATE_PLAYING && currentTurn == mySeatIndex) {
            client.send(msg);
        }
    }

    private void queueClientIntentForReconnect(JSONObject msg) {
        try {
            pendingClientIntent = new JSONObject(msg.toString());
        } catch (JSONException e) {
            pendingClientIntent = msg;
        }
        Toast.makeText(this, "连接恢复后自动发送", Toast.LENGTH_SHORT).show();
        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText("正在重连，恢复后自动发送");
            tvConnectionStatus.setTextColor(0xFFFF9800);
        }
        hideAllButtons();
        if (client != null) {
            client.reconnectNow();
        }
    }

    private void flushPendingClientIntentIfReady() {
        if (pendingClientIntent == null || client == null || !client.isConnected()) return;
        String type = pendingClientIntent.optString("type", "");
        if ("BID_RESPONSE".equals(type)) {
            if (gameState != STATE_BIDDING || currentTurn != mySeatIndex) return;
        } else if ("REQUEST_PLAY".equals(type) || "PASS".equals(type)) {
            if (gameState != STATE_PLAYING || currentTurn != mySeatIndex) return;
        }
        try {
            pendingClientIntent.put("seatIndex", mySeatIndex);
            pendingClientIntent.put("currentTurn", currentTurn);
        } catch (JSONException e) {
            Log.e(TAG, "flush pending intent failed to update seat", e);
        }
        boolean sent = client.send(pendingClientIntent);
        if (sent) {
            pendingClientIntent = null;
            if (tvConnectionStatus != null) {
                tvConnectionStatus.setText("重连后已发送");
                tvConnectionStatus.setTextColor(0xFF4CAF50);
            }
            hideAllButtons();
        } else if (client != null) {
            client.reconnectNow();
        }
    }

    private void advanceBidTurn() {
        bidRound++;
        if (bidRound >= 3) {
            int forcedLandlord = (int) (Math.random() * 3);
            setLandlord(forcedLandlord);
            Toast.makeText(this, "无人叫地主，随机指定", Toast.LENGTH_SHORT).show();
            broadcastSystemMessage("无人叫地主，随机指定 " + getSeatActorName(forcedLandlord) + " 为地主");
            startPlayingPhase();
            broadcastSyncState();
            return;
        }

        currentTurn = (currentTurn + 1) % 3;
        bidTurn = currentTurn;
        updateTurnIndicator();
        refreshLocalControls();

        if (seatTypes[currentTurn] == SEAT_TYPE_AI) {
            scheduleAIBid();
        } else if (seatTypes[currentTurn] == SEAT_TYPE_REMOTE) {
            sendBidRequestToRemote(currentTurn);
        }
    }

    private void scheduleAIBid() {
        if (aiThinkingRunnable != null) handler.removeCallbacks(aiThinkingRunnable);
        aiThinkingRunnable = () -> {
            if (gameState != STATE_BIDDING) return;
            List<Card> aiHand = getSeatHandCards(currentTurn);
            boolean shouldBid = evaluateHandForBid(aiHand);
            if (soundManager != null) soundManager.bid(shouldBid);
            if (shouldBid) {
                setLandlord(currentTurn);
                broadcastSystemMessage(getSeatActorName(currentTurn) + " 叫地主");
                startPlayingPhase();
                broadcastSyncState();
            } else {
                int noCallSeat = currentTurn;
                broadcastSystemMessage(getSeatActorName(noCallSeat) + " 不叫");
                advanceBidTurn();
                broadcastSyncState();
            }
        };
        handler.postDelayed(aiThinkingRunnable, AI_THINKING_DELAY);
    }

    private void sendBidRequestToRemote(int seatIndex) {
        if (server == null) return;
        int clientId = seatManager.getClientId(seatIndex);
        if (clientId < 0) return;
        JSONObject msg = DouDiZhuProtocol.createBidRequestMsg(getCurrentStateVersion(), seatIndex, currentTurn);
        server.sendTo(clientId, msg);
    }

    private boolean evaluateHandForBid(List<Card> handCards) {
        if (handCards == null || handCards.isEmpty()) return false;
        int score = 0;
        Map<Integer, Integer> rankCountMap = new HashMap<>();
        for (Card card : handCards) {
            int weight = card.getWeight();
            rankCountMap.put(weight, rankCountMap.getOrDefault(weight, 0) + 1);
        }
        boolean hasSmallJoker = rankCountMap.containsKey(Rank.SMALL_JOKER.getWeight());
        boolean hasBigJoker = rankCountMap.containsKey(Rank.BIG_JOKER.getWeight());
        if (hasSmallJoker && hasBigJoker) score += 8;
        else {
            if (hasSmallJoker) score += 3;
            if (hasBigJoker) score += 4;
        }
        for (int count : rankCountMap.values()) {
            if (count == 4) score += 6;
        }
        score += rankCountMap.getOrDefault(Rank.TWO.getWeight(), 0) * 2;
        score += rankCountMap.getOrDefault(Rank.ACE.getWeight(), 0);
        return score >= 7;
    }

    private void setLandlord(int seatIndex) {
        landlordIndex = seatIndex;
        List<Card> hand = getSeatHandCards(seatIndex);
        hand.addAll(bottomCards);
        GameRuleUtil.sortCardsByWeightAscending(hand);
        if (seatIndex == 1 && aiBotHands[0] != null) {
            aiBotHands[0] = new ArrayList<>(seat1Cards);
        } else if (seatIndex == 2 && aiBotHands[1] != null) {
            aiBotHands[1] = new ArrayList<>(seat2Cards);
        }
        updateTableView();
        updateLandlordIndicator();
    }

    private void startPlayingPhase() {
        gameState = STATE_PLAYING;
        currentTurn = landlordIndex;
        lastPlayerWhoPlayed = -1;
        playerPassed = new boolean[]{false, false, false};

        clearAllPlayedCards();
        updateTurnIndicator();

        refreshLocalControls();
        if (mode == 0 && seatTypes[currentTurn] == SEAT_TYPE_AI) {
            scheduleAITurn();
        }
    }

    // ============ Play Handling ============

    private void onPlayCard() {
        playClickSound();
        if (gameState != STATE_PLAYING) return;
        boolean isHostPlayer = (mode == 0 && currentTurn == 0);
        boolean isClientPlayer = (mode == 1 && currentTurn == mySeatIndex);
        if (!isHostPlayer && !isClientPlayer) return;

        List<Card> selectedCards = tableView.getSelectedCards();
        if (selectedCards.isEmpty()) {
            Toast.makeText(this, "请选择要出的牌", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Card> previousCards = getLastPlayedCards();
        if (!validatePlay(selectedCards, previousCards)) {
            Toast.makeText(this, "打不过上家的牌", Toast.LENGTH_SHORT).show();
            return;
        }

        int seatIndex = (mode == 0) ? 0 : mySeatIndex;

        if (mode == 0) {
            executePlay(seatIndex, selectedCards);
            broadcastSyncState();
        } else {
            JSONObject msg = new JSONObject();
            try {
                msg.put("type", "REQUEST_PLAY");
                msg.put("seatIndex", mySeatIndex);
                msg.put("currentTurn", currentTurn);
                msg.put("cards", cardsToJson(selectedCards));
                msg.put("cardType", GameRuleUtil.getCardType(selectedCards).name());
            } catch (JSONException e) {}
            if (sendClientIntent(msg)) {
                if (soundManager != null) {
                    soundManager.cards(selectedCards, GameRuleUtil.getCardType(selectedCards));
                }
                enablePlayerControls(false);
            }
        }
    }

    private void onHint() {
        playClickSound();
        if (gameState != STATE_PLAYING) return;
        List<Card> previousCards = getLastPlayedCards();
        List<List<Card>> hints = GameRuleUtil.findPlayableCombos(playerHandCards, previousCards);
        if (hints.isEmpty()) {
            Toast.makeText(this, "没有能打过的牌", Toast.LENGTH_SHORT).show();
        } else {
            List<Card> hint = hints.get(0);
            Toast.makeText(this, "提示: " + GameRuleUtil.getCardType(hint).getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onPass() {
        playClickSound();
        if (gameState != STATE_PLAYING) return;
        boolean isHostPlayer = (mode == 0 && currentTurn == 0);
        boolean isClientPlayer = (mode == 1 && currentTurn == mySeatIndex);
        if (!isHostPlayer && !isClientPlayer) return;

        if (getLastPlayedCards() == null) {
            Toast.makeText(this, "当前你先出牌，不能不要", Toast.LENGTH_SHORT).show();
            return;
        }

        int seatIndex = (mode == 0) ? 0 : mySeatIndex;

        if (mode == 0) {
            logGame("PASS", seatIndex, "host pass");
            if (soundManager != null) soundManager.pass();
            playerPassed[seatIndex] = true;
            clearSeatPlayedCards(seatIndex);
            updateTableView();
            if (checkAndClearTable()) {
                continueFromCurrentTurn();
            } else {
                switchToNextPlayer();
            }
            broadcastSyncState();
        } else {
            logGame("PASS", mySeatIndex, "client pass");
            JSONObject msg = new JSONObject();
            try {
                msg.put("type", "PASS");
                msg.put("seatIndex", mySeatIndex);
                msg.put("currentTurn", currentTurn);
            } catch (JSONException e) {}
            if (sendClientIntent(msg)) {
                if (soundManager != null) soundManager.pass();
                enablePlayerControls(false);
            }
        }
    }

    // ============ AI Handling ============

    private boolean isAITurn() {
        return seatTypes[currentTurn] == SEAT_TYPE_AI;
    }

    private void scheduleAITurn() {
        if (aiThinkingRunnable != null) handler.removeCallbacks(aiThinkingRunnable);
        aiThinkingRunnable = () -> executeAITurn();
        handler.postDelayed(aiThinkingRunnable, AI_THINKING_DELAY);
    }

    private void executeAITurn() {
        if (gameState != STATE_PLAYING) return;
        if (!isAITurn()) return;

        int seatIndex = currentTurn;
        List<Card> aiHand = getAIHandCards(seatIndex);
        List<Card> previousCards = getLastPlayedCards();

        List<Card> playedCards = AIBot.decidePlay(aiHand, previousCards);

        if (playedCards != null && !playedCards.isEmpty()) {
            playerPassed[seatIndex] = false;
            executePlay(seatIndex, playedCards);
            if (mode == 0) {
                broadcastSyncState();
            }
        } else {
            playerPassed[seatIndex] = true;
            if (soundManager != null) soundManager.pass();
            clearSeatPlayedCards(seatIndex);
            updateTableView();
            if (checkAndClearTable()) {
                continueFromCurrentTurn();
            } else {
                switchToNextPlayer();
            }
            if (mode == 0) {
                broadcastSyncState();
            }
        }
    }

    private List<Card> getAIHandCards(int seatIndex) {
        if (seatIndex == 1) return aiBotHands[0];
        if (seatIndex == 2) return aiBotHands[1];
        return new ArrayList<>();
    }

    private void initAIForSeat(int seatIndex) {
        if (seatIndex == 1) aiBotHands[0] = new ArrayList<>(seat1Cards);
        else if (seatIndex == 2) aiBotHands[1] = new ArrayList<>(seat2Cards);
    }

    // ============ Core Game Logic ============

    private boolean validatePlay(List<Card> cards, List<Card> previousCards) {
        if (cards == null || cards.isEmpty()) return false;
        CardType type = GameRuleUtil.getCardType(cards);
        if (type == CardType.ERROR) return false;
        if (previousCards == null || previousCards.isEmpty()) return true;
        return GameRuleUtil.canPlayPass(cards, previousCards);
    }

    private void executePlay(int seatIndex, List<Card> cards) {
        logGame("PLAY_CARD", seatIndex, "cards=" + cards.size());
        if (soundManager != null) {
            soundManager.cards(cards, GameRuleUtil.getCardType(cards));
        }
        List<Card> hand = getSeatHandCards(seatIndex);
        for (Card card : cards) {
            hand.remove(card);
        }
        if (seatIndex == 1) aiBotHands[0] = hand;
        else if (seatIndex == 2) aiBotHands[1] = hand;

        playerPassed = new boolean[]{false, false, false};
        lastPlayerWhoPlayed = seatIndex;
        setSeatPlayedCards(seatIndex, new ArrayList<>(cards));
        updateTableView();

        if (hand.isEmpty()) {
            onGameOver(seatIndex);
            return;
        }

        switchToNextPlayer();
    }

    private void switchToNextPlayer() {
        currentTurn = (currentTurn + 1) % 3;
        continueFromCurrentTurn();
    }

    private void continueFromCurrentTurn() {
        updateTurnIndicator();
        refreshLocalControls();

        if (mode == 0 && isAITurn()) {
            scheduleAITurn();
        }
    }

    private boolean checkAndClearTable() {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= TOTAL_SEATS) return false;
        int passCount = 0;
        for (int i = 0; i < TOTAL_SEATS; i++) {
            if (i != lastPlayerWhoPlayed && playerPassed[i]) passCount++;
        }
        if (passCount >= TOTAL_SEATS - 1) {
            clearAllPlayedCards();
            playerPassed = new boolean[]{false, false, false};
            currentTurn = lastPlayerWhoPlayed;
            updateTableView();
            return true;
        }
        return false;
    }

    private void onGameOver(int winnerIndex) {
        gameState = STATE_GAME_OVER;
        this.winnerIndex = winnerIndex;
        hideAllButtons();

        String result;
        if (mode == 0) {
            result = (winnerIndex == 0) ? "你赢了！" : "你输了！";
        } else {
            boolean winnerIsLandlord = (winnerIndex == landlordIndex);
            boolean iAmLandlord = (mySeatIndex == landlordIndex);
            if (winnerIsLandlord == iAmLandlord) result = "你赢了！";
            else result = "你输了！";
        }
        if (soundManager != null) soundManager.win(result.contains("赢"));

        if (tvGameOverTitle != null) tvGameOverTitle.setText("游戏结束");
        if (tvGameOverResult != null) tvGameOverResult.setText(result);
        if (tvScoreDetail != null) {
            int score = (winnerIndex == landlordIndex) ? 100 : 50;
            tvScoreDetail.setText("本局得分：" + (score >= 0 ? "+" : "") + score);
        }
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.VISIBLE);

        if (mode == 0) broadcastSyncState();
        enablePlayerControls(false);
    }

    private void showGameOverDialog(String message) {
        gameState = STATE_GAME_OVER;
        hideAllButtons();
        if (tvGameOverTitle != null) tvGameOverTitle.setText("游戏结束");
        if (tvGameOverResult != null) tvGameOverResult.setText(message);
        if (tvScoreDetail != null) tvScoreDetail.setText("");
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.VISIBLE);
    }

    private void onPlayAgain() {
        if (gameOverDialog != null) gameOverDialog.setVisibility(View.GONE);
        if (mode == 0) {
            startGame();
        } else {
            // Client waits for host to start new game
            if (tvGameOverResult != null) tvGameOverResult.setText("等待房主开始新游戏...");
        }
    }

    // ============ Card Serialization ============

    private String cardsToJson(List<Card> cards) {
        return DouDiZhuProtocol.cardsToJson(cards);
    }

    private List<Card> parseCardsFromJson(String json) {
        return DouDiZhuProtocol.parseCardsFromJson(json);
    }

    private String cardsListToJsonArray(List<Card> cards) {
        return DouDiZhuProtocol.cardsToJson(cards);
    }

    // ============ Helper Methods ============

    private List<Card> getSeatHandCards(int seatIndex) {
        if (mode == 1 && seatIndex == mySeatIndex) {
            return playerHandCards;
        }
        switch (seatIndex) {
            case 0: return playerHandCards;
            case 1: return seat1Cards;
            case 2: return seat2Cards;
            default: return new ArrayList<>();
        }
    }

    private void setSeatPlayedCards(int seatIndex, List<Card> cards) {
        if (mode == 1) {
            int displaySlot = getDisplaySlotForSeat(seatIndex);
            if (displaySlot == 0) playerPlayedCards = cards;
            else if (displaySlot == 1) seat1PlayedCards = cards;
            else if (displaySlot == 2) seat2PlayedCards = cards;
            return;
        }
        switch (seatIndex) {
            case 0: playerPlayedCards = cards; break;
            case 1: seat1PlayedCards = cards; break;
            case 2: seat2PlayedCards = cards; break;
        }
    }

    private List<Card> getSeatPlayedCards(int seatIndex) {
        if (mode == 1) {
            int displaySlot = getDisplaySlotForSeat(seatIndex);
            if (displaySlot == 0) return playerPlayedCards;
            if (displaySlot == 1) return seat1PlayedCards;
            if (displaySlot == 2) return seat2PlayedCards;
            return new ArrayList<>();
        }
        switch (seatIndex) {
            case 0: return playerPlayedCards;
            case 1: return seat1PlayedCards;
            case 2: return seat2PlayedCards;
            default: return new ArrayList<>();
        }
    }

    private List<Card> getLastPlayedCards() {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= 3) return null;
        if (playerPassed[lastPlayerWhoPlayed]) return null;
        List<Card> played = getSeatPlayedCards(lastPlayerWhoPlayed);
        if (played == null || played.isEmpty()) return null;
        return played;
    }

    private void clearSeatPlayedCards(int seatIndex) {
        setSeatPlayedCards(seatIndex, new ArrayList<>());
    }

    private void clearAllPlayedCards() {
        playerPlayedCards = new ArrayList<>();
        seat1PlayedCards = new ArrayList<>();
        seat2PlayedCards = new ArrayList<>();
        if (tableView != null) tableView.clearAllPlayedCards();
    }

    private void removeCardsFromHand(List<Card> hand, List<Card> cards) {
        for (Card card : cards) {
            hand.remove(card);
        }
    }

    private int getPlayerDisplaySeat() {
        return mode == 1 && mySeatIndex >= 0 ? mySeatIndex : 0;
    }

    private int getLeftDisplaySeat() {
        return mode == 1 && mySeatIndex >= 0 ? (mySeatIndex + 1) % TOTAL_SEATS : 1;
    }

    private int getRightDisplaySeat() {
        return mode == 1 && mySeatIndex >= 0 ? (mySeatIndex + 2) % TOTAL_SEATS : 2;
    }

    private int getDisplaySlotForSeat(int seatIndex) {
        if (seatIndex == getPlayerDisplaySeat()) return 0;
        if (seatIndex == getLeftDisplaySeat()) return 1;
        if (seatIndex == getRightDisplaySeat()) return 2;
        return -1;
    }

    private int getSeatCardCount(int seatIndex) {
        if (mode == 0) {
            if (seatIndex == 0) return playerHandCards.size();
            if (seatIndex == 1) return seat1Cards.size();
            if (seatIndex == 2) return seat2Cards.size();
        }
        if (seatIndex >= 0 && seatIndex < TOTAL_SEATS) return handCounts[seatIndex];
        return 0;
    }

    private int getLandlordStatusForSeat(int seatIndex) {
        if (landlordIndex < 0) return 0;
        return landlordIndex == seatIndex ? 2 : 1;
    }

    // ============ UI Updates ============

    private void updateTableView() {
        if (tableView == null) return;
        tableView.setPlayerHandCards(playerHandCards);
        tableView.setBottomCards(bottomCards);
        tableView.setPlayerPlayedCards(playerPlayedCards);
        tableView.setLeftAIPlayedCards(seat1PlayedCards);
        tableView.setRightAIPlayedCards(seat2PlayedCards);
        int leftSeat = getLeftDisplaySeat();
        int rightSeat = getRightDisplaySeat();
        tableView.setAICardCounts(getSeatCardCount(leftSeat), getSeatCardCount(rightSeat));
        tableView.setAllLandlordStatus(new int[]{
                getLandlordStatusForSeat(getPlayerDisplaySeat()),
                getLandlordStatusForSeat(leftSeat),
                getLandlordStatusForSeat(rightSeat)
        });
        tableView.setPlayerLabels(new String[]{
                getSeatName(getPlayerDisplaySeat()),
                getSeatName(leftSeat),
                getSeatName(rightSeat)
        });
        tableView.setPassStates(playerPassed[leftSeat], playerPassed[rightSeat]);
        tableView.setCardCounterCounts(mode == 1
                ? cardCounterCounts
                : createCardCounterForSeat(getPlayerDisplaySeat()));
    }

    private void updateLandlordIndicator() {
        if (tvLandlordIndicator == null) return;
        StringBuilder sb = new StringBuilder("地主：");
        if (landlordIndex < 0) sb.append("待定");
        else sb.append(getSeatName(landlordIndex));
        tvLandlordIndicator.setText(sb.toString());
    }

    private void updateTurnIndicator() {
        if (tvTurnIndicator == null) return;
        String turnText;
        if (gameState == STATE_BIDDING) {
            turnText = getTurnSeatName(currentTurn) + "叫地主";
        } else {
            turnText = getTurnSeatName(currentTurn) + "出牌";
        }
        tvTurnIndicator.setText("轮到：" + turnText);
    }

    private void enablePlayerControls(boolean enable) {
        if (btnPlayCard != null) btnPlayCard.setEnabled(enable);
        if (btnHint != null) btnHint.setEnabled(enable);
        if (btnPass != null) btnPass.setEnabled(enable);
        if (playButtonLayout != null) {
            playButtonLayout.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    // ============ Server Message Handling (Host) ============

    private void onServerMessageReceived(int clientId, JSONObject msg) {
        try {
            String type = msg.getString("type");
            logEvent("MSG_RECV", remoteRoomCode, clientId, type);
            if ("JOIN".equals(type)) {
                handleClientJoin(clientId, msg);
                return;
            }
            int seatIndex = -1;
            for (int i = 0; i < TOTAL_SEATS; i++) {
                if (seatClientIds[i] == clientId) {
                    seatIndex = i;
                    break;
                }
            }
            if (seatIndex == -1) {
                int declaredSeat = msg.optInt("seatIndex", -1);
                if (declaredSeat > 0 && declaredSeat < TOTAL_SEATS
                        && seatTypes[declaredSeat] == SEAT_TYPE_REMOTE) {
                    seatIndex = declaredSeat;
                    seatClientIds[seatIndex] = clientId;
                } else {
                    return;
                }
            }

            if (isClientAction(type) && !shouldProcessClientAction(seatIndex, clientId, msg, type)) {
                return;
            }

            switch (type) {
                case "REQUEST_PLAY":
                    handleRemotePlayRequest(seatIndex, clientId, msg);
                    break;
                case "PASS":
                    handleRemotePass(seatIndex, clientId, msg);
                    break;
                case "BID_RESPONSE":
                    handleRemoteBidResponse(seatIndex, clientId, msg);
                    break;
                case "STATE_ACK":
                    handleStateAck(seatIndex, msg);
                    break;
                case "CHAT":
                    String chatMsg = msg.optString("message", "");
                    broadcastChat(seatIndex, chatMsg);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing server message: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error handling server message", e);
            if (server != null) {
                server.sendTo(clientId, createErrorMsg("主机处理消息失败，请重试"));
            }
        }
    }

    private boolean isClientAction(String type) {
        return "BID_RESPONSE".equals(type) || "REQUEST_PLAY".equals(type) || "PASS".equals(type);
    }

    private boolean shouldProcessClientAction(int seatIndex, int clientId, JSONObject msg, String type) {
        syncLocalToManager();
        boolean result = syncManager.shouldProcessClientAction(seatIndex, clientId, msg, type);
        syncManagerToLocal();
        return result;
    }

    private void handleStateAck(int seatIndex, JSONObject msg) {
        Log.d(TAG, "STATE_ACK seat=" + seatIndex + " version=" + msg.optLong("stateVersion", -1L));
    }

    private void handleRemotePlayRequest(int seatIndex, int clientId, JSONObject msg) {
        if (gameState != STATE_PLAYING || currentTurn != seatIndex) {
            sendAck(clientId, "REQUEST_PLAY", msg, false, "stale turn", getCurrentStateVersion());
            sendSyncStateToSeat(seatIndex);
            return;
        }
        if (msg.optInt("seatIndex", seatIndex) != seatIndex) {
            sendAck(clientId, "REQUEST_PLAY", msg, false, "seat mismatch", getCurrentStateVersion());
            if (server != null) server.sendTo(seatClientIds[seatIndex], createErrorMsg("座位校验失败"));
            return;
        }
        try {
            String cardsJson = msg.getString("cards");
            List<Card> playedCards = parseCardsFromJson(cardsJson);
            if (playedCards.isEmpty()) {
                sendAck(clientId, "REQUEST_PLAY", msg, false, "empty cards", getCurrentStateVersion());
                return;
            }

            List<Card> previousCards = getLastPlayedCards();
            if (validatePlay(playedCards, previousCards)) {
                executePlay(seatIndex, playedCards);
                long version = broadcastSyncState();
                sendAck(clientId, "REQUEST_PLAY", msg, true, "", version);
            } else {
                if (server != null) {
                    server.sendTo(seatClientIds[seatIndex], createErrorMsg("出牌不合法"));
                }
            }
        } catch (JSONException e) {}
    }

    private void handleRemotePass(int seatIndex, int clientId, JSONObject msg) {
        if (gameState != STATE_PLAYING || currentTurn != seatIndex) {
            sendAck(clientId, "PASS", msg, false, "stale turn", getCurrentStateVersion());
            sendSyncStateToSeat(seatIndex);
            return;
        }
        if (msg.optInt("seatIndex", seatIndex) != seatIndex) {
            if (server != null) server.sendTo(seatClientIds[seatIndex], createErrorMsg("座位校验失败"));
            return;
        }
        if (getLastPlayedCards() == null) {
            sendAck(clientId, "PASS", msg, false, "lead cannot pass", getCurrentStateVersion());
            sendSyncStateToSeat(seatIndex);
            return;
        }
        if (soundManager != null) soundManager.pass();
        playerPassed[seatIndex] = true;
        clearSeatPlayedCards(seatIndex);
        updateTableView();
        if (checkAndClearTable()) {
            continueFromCurrentTurn();
        } else {
            switchToNextPlayer();
        }
        long version = broadcastSyncState();
        sendAck(clientId, "PASS", msg, true, "", version);
    }

    private void handleRemoteBidResponse(int seatIndex, int clientId, JSONObject msg) {
        try {
            if (gameState != STATE_BIDDING || currentTurn != seatIndex) {
                if (server != null) {
                    sendSyncStateToSeat(seatIndex);
                }
                sendAck(clientId, "BID_RESPONSE", msg, false, "stale turn", getCurrentStateVersion());
                return;
            }
            int declaredSeat = msg.optInt("seatIndex", seatIndex);
            if (declaredSeat != seatIndex) {
                if (server != null) server.sendTo(seatClientIds[seatIndex], createErrorMsg("座位校验失败"));
                return;
            }
            boolean call = msg.optBoolean("call", false);
            if (soundManager != null) soundManager.bid(call);
            if (call) {
                setLandlord(seatIndex);
                broadcastSystemMessage(getSeatActorName(seatIndex) + " 叫地主");
                startPlayingPhase();
                long version = broadcastSyncState();
                sendAck(clientId, "BID_RESPONSE", msg, true, "", version);
            } else {
                advanceBidTurn();
                broadcastSystemMessage(getSeatActorName(seatIndex) + " 不叫");
                long version = broadcastSyncState();
                sendAck(clientId, "BID_RESPONSE", msg, true, "", version);
            }
        } catch (Exception e) {
            Log.e(TAG, "handleRemoteBidResponse error", e);
            sendAck(clientId, "BID_RESPONSE", msg, false, "exception", getCurrentStateVersion());
            if (server != null) {
                server.sendTo(seatClientIds[seatIndex], createErrorMsg("叫地主处理失败，请重试"));
                sendSyncStateToSeat(seatIndex);
            }
        }
    }

    // ============ Client Message Handling (Client) ============

    private void onClientMessageReceived(JSONObject msg) {
        if (msg == null) return;
        String type = msg.optString("type", "");
        logEvent("MSG_RECV", remoteRoomCode, mySeatIndex, type);
        Log.d(TAG, LOG_PREFIX + " [CLIENT_MSG_RECV] type=" + type + " mySeatIndex=" + mySeatIndex + " gameState=" + gameState);
        handler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                switch (type) {
                    case "SEAT_ASSIGNED":
                        handleSeatAssigned(msg);
                        break;
                    case "SEAT_UPDATE":
                        handleSeatUpdate(msg);
                        break;
                    case "HAND_CARDS":
                        handleHandCards(msg);
                        break;
                    case "BID_REQUEST":
                        handleBidRequest(msg);
                        break;
                    case "GAME_OVER":
                        handleGameOverMsg(msg);
                        break;
                    case "SYNC_STATE":
                        handleSyncState(msg);
                        break;
                    case "ACK":
                        handleAck(msg);
                        break;
                    case "CHAT_HISTORY":
                        handleChatHistory(msg);
                        break;
                    case "CHAT":
                        handleChatMessage(msg);
                        break;
                    case "ERROR":
                        String errorMsg = msg.optString("message", "未知错误");
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling client message: " + e.getMessage());
            }
        });
    }

    private void handleSeatAssigned(JSONObject msg) {
        mySeatIndex = msg.optInt("seatIndex", 0);
        try {
            JSONArray types = msg.getJSONArray("seatTypes");
            for (int i = 0; i < TOTAL_SEATS; i++) {
                seatTypes[i] = types.getInt(i);
            }
        } catch (JSONException e) {}
        if (tvServerInfo != null) tvServerInfo.setText("已分配座位 " + getFixedSeatName(mySeatIndex));
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
        if (msg.optBoolean("reconnected", false)) {
            appendChat("系统: 已恢复到原座位 " + getFixedSeatName(mySeatIndex));
        }
        updateConnectedStatusText();
    }

    private void handleSeatUpdate(JSONObject msg) {
        try {
            JSONArray types = msg.getJSONArray("seatTypes");
            for (int i = 0; i < TOTAL_SEATS; i++) {
                seatTypes[i] = types.getInt(i);
            }
            landlordIndex = msg.optInt("landlordIndex", -1);
        } catch (JSONException e) {}
    }

    private void handleHandCards(JSONObject msg) {
        try {
            mySeatIndex = msg.optInt("seatIndex", mySeatIndex);
            String cardsJson = msg.getString("cards");
            playerHandCards = parseCardsFromJson(cardsJson);
            GameRuleUtil.sortCardsByWeightAscending(playerHandCards);
            if (mySeatIndex >= 0 && mySeatIndex < TOTAL_SEATS) {
                handCounts[mySeatIndex] = playerHandCards.size();
            }
            cardCounterCounts = createCardCounterForSeat(mySeatIndex);
            String bottomJson = msg.optString("bottomCards", "[]");
            bottomCards = parseCardsFromJson(bottomJson);
            if (tableView != null) {
                tableView.setPlayerHandCards(playerHandCards);
                tableView.setBottomCards(bottomCards);
                tableView.setCardCounterCounts(cardCounterCounts);
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleHandCards error: " + e.getMessage());
        }
    }

    private void handleBidStart(JSONObject msg) {
        gameState = STATE_BIDDING;
        currentTurn = msg.optInt("currentTurn", 0);
        showGameChrome();
        updateTableView();
        updateLandlordIndicator();
        updateTurnIndicator();
        refreshLocalControls();
        updateConnectedStatusText();
        flushPendingClientIntentIfReady();
    }

    private void handleBidRequest(JSONObject msg) {
        long version = msg.optLong("stateVersion", -1L);
        if (version >= 0L && version < syncManager.getClientLastStateVersion()) {
            return;
        }
        int seatIndex = msg.optInt("seatIndex", -1);
        currentTurn = msg.optInt("currentTurn", seatIndex);
        gameState = STATE_BIDDING;
        showGameChrome();
        updateTableView();
        updateLandlordIndicator();
        updateTurnIndicator();
        refreshLocalControls();
        updateConnectedStatusText();
    }

    private void handleBidResult(JSONObject msg) {
        updateAuthorityFields(msg);
        int seatIndex = msg.optInt("seatIndex", -1);
        boolean call = msg.optBoolean("call", false);
        String name = getSeatName(seatIndex);
        String action = call ? "叫地主" : "不叫";
        appendChat(name + " " + action);
        if (call && mode == 0) {
            landlordIndex = seatIndex;
            updateLandlordIndicator();
        }
        if (mode == 1) {
            hideAllButtons();
        }
    }

    private void handleGameStart(JSONObject msg) {
        try {
            mySeatIndex = msg.optInt("seatIndex", mySeatIndex);
            gameState = STATE_PLAYING;
            currentTurn = msg.getInt("currentTurn");
            landlordIndex = msg.getInt("landlordIndex");

            if (msg.has("bottomCards")) {
                String bottomJson = msg.getString("bottomCards");
                bottomCards = parseCardsFromJson(bottomJson);
            }

            playerPassed = new boolean[]{false, false, false};
            lastPlayerWhoPlayed = msg.optInt("lastPlayerWhoPlayed", -1);

            showGameChrome();

            clearAllPlayedCards();
            updateLandlordIndicator();
            updateTurnIndicator();
            refreshLocalControls();
            updateConnectedStatusText();
            flushPendingClientIntentIfReady();
        } catch (JSONException e) {
            Log.e(TAG, "handleGameStart error: " + e.getMessage());
        }
    }

    private void handleBroadcastAction(JSONObject msg) {
        try {
            int playerIndex = msg.getInt("playerIndex");
            String cardsJson = msg.getString("cards");
            List<Card> playedCards = parseCardsFromJson(cardsJson);
            if (soundManager != null) {
                soundManager.cards(playedCards, GameRuleUtil.getCardType(playedCards));
            }

            if (playerIndex == mySeatIndex) {
                removeCardsFromHand(playerHandCards, playedCards);
                handCounts[playerIndex] = playerHandCards.size();
            } else if (playerIndex >= 0 && playerIndex < TOTAL_SEATS) {
                handCounts[playerIndex] = Math.max(0, handCounts[playerIndex] - playedCards.size());
            }

            playerPassed = new boolean[]{false, false, false};
            lastPlayerWhoPlayed = playerIndex;
            setSeatPlayedCards(playerIndex, new ArrayList<>(playedCards));
            updateTableView();

            if (mode == 1) {
                hideAllButtons();
                return;
            }

            switchToNextPlayer();
        } catch (JSONException e) {
            Log.e(TAG, "handleBroadcastAction error: " + e.getMessage());
        }
    }

    private void handlePassAction(JSONObject msg) {
        int playerIndex = msg.optInt("playerIndex", -1);
        if (playerIndex < 0) return;
        playerPassed[playerIndex] = true;
        if (soundManager != null) soundManager.pass();
        clearSeatPlayedCards(playerIndex);
        updateTableView();
        if (mode == 1) {
            hideAllButtons();
            return;
        }
        if (checkAndClearTable()) {
            continueFromCurrentTurn();
        } else {
            switchToNextPlayer();
        }
    }

    private void handleGameOverMsg(JSONObject msg) {
        int winnerIndex = msg.optInt("winnerIndex", -1);
        this.winnerIndex = winnerIndex;
        boolean winnerIsLandlord = (winnerIndex == landlordIndex);
        boolean iAmLandlord = (mySeatIndex == landlordIndex);
        String result;
        if (winnerIndex == mySeatIndex) result = "你赢了！";
        else if (winnerIsLandlord == iAmLandlord) result = "你赢了！";
        else result = "你输了！";
        if (soundManager != null) soundManager.win(result.contains("赢"));
        showGameOverDialog(result);
    }

    private void handleSyncState(JSONObject msg) {
        try {
            long incomingVersion = msg.optLong("stateVersion", -1L);
            logGame("SYNC_STATE", mySeatIndex, "incoming version=" + incomingVersion);
            Log.d(TAG, LOG_PREFIX + " [HANDLE_SYNC_STATE] incomingVersion=" + incomingVersion + " clientLastStateVersion=" + syncManager.getClientLastStateVersion());
            if (incomingVersion >= 0L && incomingVersion <= syncManager.getClientLastStateVersion()) {
                Log.d(TAG, LOG_PREFIX + " [HANDLE_SYNC_STATE] stale version, sending ACK and skipping");
                sendStateAck(incomingVersion);
                return;
            }
            if (incomingVersion >= 0L) {
                syncManager.setClientLastStateVersion(incomingVersion);
            }
            mySeatIndex = msg.optInt("seatIndex", mySeatIndex);
            gameState = msg.optInt("gameState", STATE_LOBBY);
            currentTurn = msg.optInt("currentTurn", 0);
            landlordIndex = msg.optInt("landlordIndex", -1);
            winnerIndex = msg.optInt("winnerIndex", winnerIndex);
            lastPlayerWhoPlayed = msg.optInt("lastPlayerWhoPlayed", -1);

            JSONArray seatTypeArr = msg.optJSONArray("seatTypes");
            if (seatTypeArr != null) {
                for (int i = 0; i < TOTAL_SEATS && i < seatTypeArr.length(); i++) {
                    seatTypes[i] = seatTypeArr.getInt(i);
                }
            }

            JSONArray passArr = msg.optJSONArray("playerPassed");
            if (passArr != null) {
                for (int i = 0; i < 3 && i < passArr.length(); i++) {
                    playerPassed[i] = passArr.getBoolean(i);
                }
            }

            JSONArray handCountArray = msg.optJSONArray("handCounts");
            if (handCountArray != null) {
                for (int i = 0; i < TOTAL_SEATS && i < handCountArray.length(); i++) {
                    this.handCounts[i] = handCountArray.getInt(i);
                }
            }

            JSONArray counterArray = msg.optJSONArray("cardCounter");
            if (counterArray != null) {
                cardCounterCounts = jsonToCounterArray(counterArray);
            }

            if (msg.has("myCards")) {
                String myCardsJson = msg.getString("myCards");
                playerHandCards = parseCardsFromJson(myCardsJson);
                GameRuleUtil.sortCardsByWeightAscending(playerHandCards);
                if (mySeatIndex >= 0 && mySeatIndex < TOTAL_SEATS) {
                    handCounts[mySeatIndex] = playerHandCards.size();
                }
            }

            if (msg.has("bottomCards")) {
                bottomCards = parseCardsFromJson(msg.getString("bottomCards"));
            }
            if (msg.has("played0")) {
                setSeatPlayedCards(0, parseCardsFromJson(msg.getString("played0")));
            }
            if (msg.has("played1")) {
                setSeatPlayedCards(1, parseCardsFromJson(msg.getString("played1")));
            }
            if (msg.has("played2")) {
                setSeatPlayedCards(2, parseCardsFromJson(msg.getString("played2")));
            }

            showGameChrome();
            updateTableView();
            updateLandlordIndicator();
            updateTurnIndicator();
            Log.d(TAG, LOG_PREFIX + " [HANDLE_SYNC_STATE] APPLIED: gameState=" + gameState + " currentTurn=" + currentTurn + " landlordIndex=" + landlordIndex + " myCards=" + playerHandCards.size() + " mySeatIndex=" + mySeatIndex);
            if (gameState == STATE_GAME_OVER) {
                handleGameOverMsg(msg);
                updateConnectedStatusText();
                sendStateAck(incomingVersion);
                return;
            }
            refreshLocalControls();
            updateConnectedStatusText();
            flushPendingClientIntentIfReady();
            sendStateAck(incomingVersion);
        } catch (JSONException e) {
            Log.e(TAG, "handleSyncState error: " + e.getMessage());
        }
    }

    private void sendStateAck(long stateVersion) {
        if (mode != 1 || client == null || !client.isConnected() || stateVersion < 0L) return;
        JSONObject ack = new JSONObject();
        try {
            ack.put("type", "STATE_ACK");
            ack.put("seatIndex", mySeatIndex);
            ack.put("stateVersion", stateVersion);
            ack.put("time", System.currentTimeMillis());
            client.send(ack);
        } catch (JSONException e) {}
    }

    private void handleChatMessage(JSONObject msg) {
        int seatIndex = msg.optInt("seatIndex", -1);
        String message = msg.optString("message", "");
        String name = seatIndex >= 0 ? getFixedSeatName(seatIndex) : "系统";
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
        appendChat(name + ": " + message);
    }

    private void handleAck(JSONObject msg) {
        String ackType = msg.optString("ackType", "");
        long actionId = msg.optLong("actionId", -1L);
        long stateVersion = msg.optLong("stateVersion", -1L);
        boolean accepted = msg.optBoolean("accepted", true);
        Log.d(TAG, "ACK received: " + ackType + " action=" + actionId + " accepted=" + accepted);
        if (tvConnectionStatus != null && mode == 1) {
            tvConnectionStatus.setText("已发送: " + ackType);
            tvConnectionStatus.setTextColor(0xFF4CAF50);
        }
    }

    private void handleChatHistory(JSONObject msg) {
        JSONArray messages = msg.optJSONArray("messages");
        if (messages == null) return;
        chatLog.setLength(0);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) continue;
            int seatIndex = item.optInt("seatIndex", -1);
            String message = item.optString("message", "");
            String name = seatIndex >= 0 ? getFixedSeatName(seatIndex) : "系统";
            chatLog.append(name).append(": ").append(message).append("\n");
        }
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
        if (tvChatMessages != null) tvChatMessages.setText(chatLog.toString());
        if (chatScrollView != null) chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void updateAuthorityFields(JSONObject msg) {
        if (msg == null) return;
        landlordIndex = msg.optInt("landlordIndex", landlordIndex);
        currentTurn = msg.optInt("currentTurn", currentTurn);
        JSONArray types = msg.optJSONArray("seatTypes");
        if (types != null) {
            for (int i = 0; i < TOTAL_SEATS && i < types.length(); i++) {
                seatTypes[i] = types.optInt(i, seatTypes[i]);
            }
        }
    }

    // ============ Broadcast Methods (Host) ============

    private void broadcastHandCards() {
        if (server == null) return;
        logGame("DEAL_CARDS", -1, "broadcasting to remote seats");
        syncLocalToManager();
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            if (seatManager.getSeatType(i) == DouDiZhuSeatManager.SEAT_TYPE_REMOTE && seatManager.getClientId(i) >= 0) {
                List<Card> handCards = getSeatHandCards(i);
                String cardsJson = DouDiZhuProtocol.cardsToJson(handCards);
                String bottomJson = DouDiZhuProtocol.cardsToJson(bottomCards);
                JSONObject msg = DouDiZhuProtocol.createHandCardsMsg(cardsJson, bottomJson, i);
                server.sendTo(seatManager.getClientId(i), msg);
            }
        }
    }

    private void broadcastSeatUpdate() {
        if (mode != 0 || server == null) return;
        syncLocalToManager();
        syncManager.broadcastSeatUpdate(landlordIndex);
    }

    private long broadcastSyncState() {
        if (mode != 0 || server == null) {
            Log.w(TAG, LOG_PREFIX + " [BROADCAST_SYNC] EARLY RETURN: mode=" + mode + " server=" + (server != null ? "not-null" : "NULL"));
            return syncManager.getCurrentStateVersion();
        }
        syncLocalToManager();
        long version = syncManager.broadcastSyncState(gameStateProvider);
        syncManagerToLocal();
        return version;
    }

    private void sendSyncStateNow(long version) {
        syncLocalToManager();
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            if (seatManager.getSeatType(i) == DouDiZhuSeatManager.SEAT_TYPE_REMOTE && seatManager.getClientId(i) >= 0) {
                syncManager.sendSyncStateToSeat(i, gameStateProvider);
            }
        }
    }

    private void sendSyncStateToSeat(int seatIndex) {
        syncLocalToManager();
        syncManager.sendSyncStateToSeat(seatIndex, gameStateProvider);
        syncManagerToLocal();
    }

    private long nextStateVersion() {
        return syncManager.nextStateVersion();
    }

    private long getCurrentStateVersion() {
        return syncManager.getCurrentStateVersion();
    }

    private JSONObject createSyncStateMessage(int seatIndex, long version) {
        String bottomCardsJson = DouDiZhuProtocol.cardsToJson(bottomCards);
        String played0Json = DouDiZhuProtocol.cardsToJson(playerPlayedCards);
        String played1Json = DouDiZhuProtocol.cardsToJson(seat1PlayedCards);
        String played2Json = DouDiZhuProtocol.cardsToJson(seat2PlayedCards);

        JSONArray handCounts = DouDiZhuProtocol.handCountsToJson(playerHandCards, seat1Cards, seat2Cards);
        int[] cardCounter = createCardCounterForSeat(seatIndex);

        List<Card> myCards = getSeatHandCards(seatIndex);
        String myCardsJson = DouDiZhuProtocol.cardsToJson(myCards);

        return DouDiZhuProtocol.createSyncStateMsg(
                seatIndex, version,
                gameState, currentTurn, landlordIndex,
                winnerIndex, lastPlayerWhoPlayed,
                seatTypes, playerPassed, handCounts,
                cardCounter, myCardsJson, bottomCardsJson,
                played0Json, played1Json, played2Json
        );
    }

    private void sendAck(int clientId, String ackType, JSONObject source, boolean accepted, String reason, long stateVersion) {
        syncManager.sendAck(clientId, ackType, source, accepted, reason, stateVersion);
    }

    private JSONObject createGameStartMessage(int seatIndex) {
        return DouDiZhuProtocol.createGameStartMsg(seatIndex, currentTurn, landlordIndex, seatTypes, DouDiZhuProtocol.cardsToJson(bottomCards));
    }

    private void broadcastGameStart() {
        if (server == null) return;
        syncLocalToManager();
        String bottomCardsJson = DouDiZhuProtocol.cardsToJson(bottomCards);
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            if (seatManager.getSeatType(i) == DouDiZhuSeatManager.SEAT_TYPE_REMOTE && seatManager.getClientId(i) >= 0) {
                server.sendTo(seatManager.getClientId(i), DouDiZhuProtocol.createGameStartMsg(i, currentTurn, landlordIndex, seatTypes, bottomCardsJson));
            }
        }
    }

    private void broadcastPlayAction(int seatIndex, List<Card> cards) {
        if (server == null) return;
        syncManager.broadcastPlayAction(seatIndex, cards, currentTurn, landlordIndex);
    }

    private void broadcastPassAction(int seatIndex) {
        if (server == null) return;
        syncManager.broadcastPassAction(seatIndex, currentTurn, landlordIndex);
    }

    private void broadcastBidResult(int seatIndex, boolean call) {
        if (server == null) return;
        syncManager.broadcastBidResult(seatIndex, call, currentTurn, landlordIndex);

        String name = getFixedSeatName(seatIndex);
        String action = call ? "叫地主" : "不叫";
        appendChat(name + " " + action);
    }

    private void broadcastGameOver(int winnerIndex) {
        if (server == null) return;
        syncManager.broadcastGameOver(winnerIndex);
    }

    private void broadcastChat(int seatIndex, String message) {
        rememberHostChat(seatIndex, message);
        String name = getFixedSeatName(seatIndex);
        appendChat(name + ": " + message);
        broadcastChatHistoryToAll();
    }

    private void broadcastSystemMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        rememberHostChat(-1, message);
        appendChat("系统: " + message);
        broadcastChatHistoryToAll();
    }

    private void rememberHostChat(int seatIndex, String message) {
        if (message == null) return;
        JSONObject item = new JSONObject();
        try {
            item.put("seatIndex", seatIndex);
            item.put("message", message);
            item.put("time", System.currentTimeMillis());
            hostChatHistory.add(item);
        } catch (JSONException e) {}
    }

    private void sendChatHistoryToClient(int clientId) {
        if (server == null) return;
        JSONObject msg = new JSONObject();
        JSONArray messages = new JSONArray();
        try {
            for (JSONObject item : hostChatHistory) {
                messages.put(item);
            }
            msg.put("type", "CHAT_HISTORY");
            msg.put("messages", messages);
            server.sendTo(clientId, msg);
        } catch (JSONException e) {
            Log.e(TAG, "sendChatHistoryToClient error: " + e.getMessage());
        }
    }

    private void broadcastChatHistoryToAll() {
        if (server == null) return;
        sendChatHistoryToAllNow();
        handler.postDelayed(this::sendChatHistoryToAllNow, 200);
    }

    private void sendChatHistoryToAllNow() {
        if (server == null) return;
        for (int i = 0; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] >= 0) {
                sendChatHistoryToClient(seatClientIds[i]);
            }
        }
    }

    private JSONObject createErrorMsg(String error) {
        return DouDiZhuProtocol.createErrorMsg(error);
    }

    // ============ Chat ============

    private void onSendChat() {
        playClickSound();
        if (etChatInput == null) return;
        String message = etChatInput.getText().toString().trim();
        if (message.isEmpty()) return;
        etChatInput.setText("");

        if (mode == 0) {
            broadcastChat(0, message);
        } else if (client != null) {
            JSONObject msg = new JSONObject();
            try {
                msg.put("type", "CHAT");
                msg.put("message", message);
                msg.put("seatIndex", mySeatIndex);
            } catch (JSONException e) {}
            if (!sendClientIntent(msg)) {
                etChatInput.setText(message);
            }
        }
    }

    private void appendChat(String message) {
        chatLog.append(message).append("\n");
        if (tvChatMessages != null) tvChatMessages.setText(chatLog.toString());
        if (chatScrollView != null) chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String getSeatName(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        String role = getRoleName(seatIndex);
        if (seatTypes[seatIndex] == SEAT_TYPE_AI) {
            return "人机（" + role + "）";
        }
        return "P" + (seatIndex + 1) + "（" + role + "）";
    }

    private String getFixedSeatName(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        return "P" + (seatIndex + 1);
    }

    private String getSeatActorName(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        if (seatTypes[seatIndex] == SEAT_TYPE_AI) {
            return getFixedSeatName(seatIndex) + "（人机）";
        }
        return getFixedSeatName(seatIndex);
    }

    private String getRoleName(int seatIndex) {
        if (landlordIndex < 0) return "待定";
        return landlordIndex == seatIndex ? "地主" : "农民";
    }

    private String getShortSeatName(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        if (seatTypes[seatIndex] == SEAT_TYPE_AI) return "人机";
        return "P" + (seatIndex + 1);
    }

    private boolean isLocalSeat(int seatIndex) {
        return (mode == 0 && seatIndex == 0)
                || (mode == 1 && mySeatIndex >= 0 && seatIndex == mySeatIndex);
    }

    private String getTurnSeatName(int seatIndex) {
        return isLocalSeat(seatIndex) ? "你" : getShortSeatName(seatIndex);
    }

    // ============ GameStateProvider ============

    private final DouDiZhuSyncManager.GameStateProvider gameStateProvider = new DouDiZhuSyncManager.GameStateProvider() {
        @Override public int getGameState() { return gameState; }
        @Override public int getCurrentTurn() { return currentTurn; }
        @Override public int getLandlordIndex() { return landlordIndex; }
        @Override public int getWinnerIndex() { return winnerIndex; }
        @Override public int getLastPlayerWhoPlayed() { return lastPlayerWhoPlayed; }
        @Override public List<Card> getPlayerHandCards() { return playerHandCards; }
        @Override public List<Card> getSeat1Cards() { return seat1Cards; }
        @Override public List<Card> getSeat2Cards() { return seat2Cards; }
        @Override public List<Card> getBottomCards() { return bottomCards; }
        @Override public List<Card> getPlayerPlayedCards() { return playerPlayedCards; }
        @Override public List<Card> getSeat1PlayedCards() { return seat1PlayedCards; }
        @Override public List<Card> getSeat2PlayedCards() { return seat2PlayedCards; }
        @Override public boolean[] getPlayerPassed() { return playerPassed; }
        @Override public int[] getSeatTypes() { return seatTypes; }
        @Override public int getPlayerDisplaySeat() { return mode == 1 && mySeatIndex >= 0 ? mySeatIndex : 0; }
        @Override public int getSeatCardCount(int seatIndex) {
            if (mode == 0) {
                if (seatIndex == 0) return playerHandCards.size();
                if (seatIndex == 1) return seat1Cards.size();
                if (seatIndex == 2) return seat2Cards.size();
            }
            int[] handCounts = new int[]{playerHandCards.size(), seat1Cards.size(), seat2Cards.size()};
            if (seatIndex >= 0 && seatIndex < DouDiZhuSeatManager.TOTAL_SEATS) return handCounts[seatIndex];
            return 0;
        }
    };

    // ============ Cleanup ============

    private void syncLocalToManager() {
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            seatManager.updateSeat(i, seatClientIds[i], seatClientIps[i], seatPeerTokens[i], seatTypes[i]);
            seatManager.setLastProcessedActionId(i, lastProcessedActionIds[i]);
        }
    }

    private void syncManagerToLocal() {
        int[] mgrTypes = seatManager.getSeatTypes();
        System.arraycopy(mgrTypes, 0, seatTypes, 0, mgrTypes.length);
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            seatClientIds[i] = seatManager.getClientId(i);
            seatClientIps[i] = seatManager.getClientIp(i);
            seatPeerTokens[i] = seatManager.getPeerToken(i);
            lastProcessedActionIds[i] = seatManager.getLastProcessedActionId(i);
        }
        remoteHostInfoText = seatManager.getRemoteHostInfoText();
        remoteInviteAddress = seatManager.getRemoteInviteAddress();
        remoteRoomCode = seatManager.getRemoteRoomCode();
    }

    private void releaseSoundManager() {
        if (soundManager != null) {
            soundManager.release();
            soundManager = null;
        }
    }

    private void cleanup() {
        isCleaningUp = true;
        if (handler != null && aiThinkingRunnable != null) {
            handler.removeCallbacks(aiThinkingRunnable);
        }
        if (lanManager != null) {
            lanManager.unregisterService();
            lanManager.stopDiscovery();
        }
        if (server != null) {
            server.setOnClientConnectedListener(null);
            server.setOnClientDisconnectedListener(null);
            server.setOnMessageReceivedListener(null);
            server.setOnErrorListener(null);
            server.stop();
            server = null;
        }
        if (client != null) {
            client.setOnConnectedListener(null);
            client.setOnDisconnectedListener(null);
            client.setOnMessageReceivedListener(null);
            client.setOnErrorListener(null);
            client.setOnStateChangedListener(null);
            client.disconnect();
        }
        client = null;
    }

    private void resetNetworkState() {
        isCleaningUp = false;
        mode = -1;
        mySeatIndex = -1;
        seatTypes = new int[]{DouDiZhuSeatManager.SEAT_TYPE_HOST, DouDiZhuSeatManager.SEAT_TYPE_AI, DouDiZhuSeatManager.SEAT_TYPE_AI};
        seatClientIds = new int[]{-1, -1, -1};
        seatClientIps = new String[]{"", "", ""};
        seatPeerTokens = new String[]{"", "", ""};
        pendingClientIps.clear();
        remoteHostInfoText = "";
        remoteInviteAddress = "";
        remoteRoomCode = "";
        cardCounterCounts = DouDiZhuProtocol.createFullDeckCounter();
        resetTurnSoundMarker();
        syncManager.resetHostStateVersion();
        seatManager.resetAllSeats();
    }

    private String getOrCreatePeerToken() {
        SharedPreferences prefs = getSharedPreferences(P2P_PREFS, MODE_PRIVATE);
        String token = prefs.getString(KEY_PEER_TOKEN, "");
        if (token == null || token.trim().isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.US);
            prefs.edit().putString(KEY_PEER_TOKEN, token).apply();
        }
        return token;
    }
}

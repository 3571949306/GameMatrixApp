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

import com.gamecenter.app.BuildConfig;
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
import com.gamecenter.app.network.GameSocketClient;
import com.gamecenter.app.network.GameSocketServer;
import com.gamecenter.app.network.LANManager;
import com.gamecenter.app.network.RelayHttpClient;
import com.gamecenter.app.network.RemoteP2PUtil;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 斗地主在线联机主 Activity。
 *
 * <p>作为斗地主在线模式的核心控制器，负责协调网络通信、游戏逻辑、AI 决策和 UI 展示。
 * 支持两种联机模式：局域网（LAN）和云联机（Relay/WebSocket）。</p>
 *
 * <p>你可以把这个类想象成一个"在线棋牌室管理员"——它既管理本地玩家，
 * 又通过网络和其他玩家沟通，还负责安排AI替补缺席的玩家。</p>
 *
 * <p>架构设计：
 * <ul>
 *   <li>实现 {@link DouDiZhuUIController.GameActionCallback} 接收 UI 操作回调</li>
 *   <li>实现 {@link DouDiZhuAIHelper.AICallback} 接收 AI 决策回调</li>
 *   <li>实现 {@link DouDiZhuNetworkHandler.NetworkDelegate} 接收网络消息回调</li>
 *   <li>通过委托模式将 UI/AI/网络逻辑分别交给 {@link DouDiZhuUIController}、
 *       {@link DouDiZhuAIHelper}、{@link DouDiZhuNetworkHandler} 处理
 *       （委托模式就像"分工合作"——每个人只做自己擅长的事）</li>
 * </ul>
 * </p>
 *
 * <p>运行模式：
 * <ul>
 *   <li>mode=0：房主模式，运行游戏服务器，管理游戏状态，广播同步消息
 *       （房主就像"裁判+服务器"，所有规则判定都在房主端完成）</li>
 *   <li>mode=1：客户端模式，连接房主服务器，发送操作意图，接收状态同步
 *       （客户端就像"选手"，只负责出牌，规则交给房主判定）</li>
 * </ul>
 * </p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>房主作为权威服务器（Authoritative Server），所有游戏逻辑在房主端执行，
 *       客户端仅发送操作意图，由房主校验后广播结果
 *       （这样能防止作弊——客户端不能自己改游戏结果）</li>
 *   <li>支持断线重连：远程玩家断线后保留座位，重连后自动恢复状态
 *       （就像暂时离开牌桌，回来后还能继续打）</li>
 *   <li>局域网模式下断线玩家由 AI 接管，云联机模式下保留座位等待重连</li>
 *   <li>保留旧变量（seatTypes/seatClientIds 等）用于向后兼容，
 *       通过 syncLocalToManager/syncManagerToLocal 与 SeatManager 同步
 *       （就像新旧两套账本，要经常对账保持一致）</li>
 * </ul>
 */
public class DouDiZhuOnlineActivity extends AppCompatActivity implements DouDiZhuUIController.GameActionCallback, DouDiZhuAIHelper.AICallback, DouDiZhuNetworkHandler.NetworkDelegate {

    /** 调试日志标签 */
    private static final String TAG = "DouDiZhuOnline";
    /** Intent 额外参数键：是否启用远程 P2P 模式
     *  通过这个键从菜单页传递"是否云联机"的信息 */
    public static final String EXTRA_REMOTE_P2P = "remote_p2p";

    /** 游戏状态常量：大厅/等待中（还没开始游戏，在选房间） */
    private static final int STATE_LOBBY = 0;
    /** 游戏状态常量：叫地主阶段 */
    private static final int STATE_BIDDING = 1;
    /** 游戏状态常量：出牌阶段 */
    private static final int STATE_PLAYING = 2;
    /** 游戏状态常量：游戏结束 */
    private static final int STATE_GAME_OVER = 3;

    /** 斗地主总座位数（固定3人：地主1+农民2） */
    private static final int TOTAL_SEATS = 3;
    /** 默认服务器端口（房主监听的网络端口） */
    private static final int DEFAULT_SERVER_PORT = 8765;
    /** 房主端口候选列表，按顺序尝试绑定
     *  如果8765被占用就试8766，以此类推 */
    private static final int[] HOST_PORT_CANDIDATES = {8765, 8766, 8767, 8768, 8769};
    /** AI 模拟思考的延迟时间（毫秒） */
    private static final long AI_THINKING_DELAY = 1500L;
    /** P2P 协议版本号，用于客户端与房主版本兼容性校验
     *  版本不一致可能导致消息格式不兼容 */
    private static final int P2P_PROTOCOL_VERSION = 2;
    /** 座位类型常量：房主（本地玩家） */
    private static final int SEAT_TYPE_HOST = DouDiZhuSeatManager.SEAT_TYPE_HOST;
    /** 座位类型常量：远程玩家（通过网络连接的真实玩家） */
    private static final int SEAT_TYPE_REMOTE = DouDiZhuSeatManager.SEAT_TYPE_REMOTE;
    /** 座位类型常量：AI 玩家（电脑替补） */
    private static final int SEAT_TYPE_AI = DouDiZhuSeatManager.SEAT_TYPE_AI;
    /** 远程 P2P 模式下的重连尝试次数（120次，约5分钟） */
    private static final int REMOTE_RECONNECT_ATTEMPTS = 120;
    /** 远程 P2P 模式下的重连间隔（毫秒，2.5秒） */
    private static final long REMOTE_RECONNECT_INTERVAL_MS = 2500L;
    /** 远程 P2P 模式下的最大重连间隔（毫秒，15秒，会逐渐增加） */
    private static final long REMOTE_RECONNECT_MAX_INTERVAL_MS = 15000L;
    /** Relay 服务器基础 URL（云联机中转服务器的地址） */
    private static final String RELAY_BASE_URL = RelayHttpClient.DEFAULT_BASE_URL;

    // ============ 调试日志开关 ============
    private static final boolean DEBUG_NETWORK = BuildConfig.DEBUG;
    private static final String LOG_PREFIX = "[DDZ-WSS]";

    /**
     * 记录网络事件日志（仅 DEBUG 模式）。
     *
     * @param event 事件名称
     * @param roomCode 房间码
     * @param playerId 玩家 ID
     * @param messageType 消息类型
     */
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

    private LANManager lanManager; // 局域网管理器（负责发现和注册局域网房间）
    private GameSocketServer server; // 游戏服务器（房主模式使用，监听客户端连接）
    private GameSocketClient client; // 游戏客户端（客户端模式使用，连接房主服务器）

    private DouDiZhuSeatManager seatManager; // 座位管理器（管理谁坐在哪个位置）
    private DouDiZhuSyncManager syncManager; // 状态同步管理器（确保所有玩家看到相同的游戏状态）
    private DouDiZhuUIController uiController; // UI控制器（管理界面显示和按钮）
    private DouDiZhuAIHelper aiHelper; // AI辅助类（调度AI的叫地主和出牌决策）
    private DouDiZhuNetworkHandler networkHandler; // 网络消息处理器（处理收发消息）

    private int mode = -1; // 运行模式：0=房主，1=客户端，-1=未确定
    private boolean remoteP2PMode = false; // 是否为云联机模式（true=通过中转服务器连接）
    private String localPeerToken = ""; // 本机的唯一标识令牌（断线重连时用来识别身份）

    private static final String P2P_PREFS = "doudizhu_p2p"; // SharedPreferences文件名（保存P2P配置）
    private static final String KEY_PEER_TOKEN = "peer_token"; // Peer Token的存储键名

    // ============ 兼容旧代码的变量（已委托给管理器，保留为向后兼容） ============
    // 这些变量就像"旧账本"，新代码用SeatManager管理座位，
    // 但这些旧变量还在被很多地方使用，所以需要经常同步
    private int[] seatTypes = new int[]{DouDiZhuSeatManager.SEAT_TYPE_HOST, DouDiZhuSeatManager.SEAT_TYPE_AI, DouDiZhuSeatManager.SEAT_TYPE_AI};
    private int[] seatClientIds = new int[]{-1, -1, -1}; // 每个座位对应的客户端ID，-1表示无人
    private String[] seatClientIps = new String[]{"", "", ""}; // 每个座位对应的IP地址
    private String[] seatPeerTokens = new String[]{"", "", ""}; // 每个座位的唯一标识令牌（用于断线重连识别）
    private Map<Integer, String> pendingClientIps = new HashMap<>(); // 等待分配座位的客户端IP
    private String remoteHostInfoText = ""; // 远程房主信息文本（显示在界面上）
    private String remoteInviteAddress = ""; // 远程邀请地址（发给朋友让他加入）
    private String remoteRoomCode = ""; // 云联机6位房间码
    private long[] lastProcessedActionIds = new long[]{0L, 0L, 0L}; // 每个座位最后处理的操作ID（防止重复处理）

    // ============ 游戏状态 ============
    // 这些变量记录着当前游戏进行到哪一步了
    private int gameState = STATE_LOBBY; // 当前游戏阶段
    private int currentTurn = 0; // 当前轮到谁出牌
    private int landlordIndex = -1; // 地主的座位号，-1表示还没确定
    private int winnerIndex = -1; // 赢家的座位号，-1表示游戏还没结束
    private int lastPlayerWhoPlayed = -1; // 最后出牌的人（用于判断是否该清空桌面）
    private boolean[] playerPassed = new boolean[]{false, false, false}; // 每个人是否选择了"不出"
    private int bidTurn = 0; // 当前叫地主的轮次
    private int bidRound = 0; // 叫地主已经进行了几轮
    private int mySeatIndex = -1; // 本机在哪个座位，-1表示还没分配

    private List<Card> playerHandCards = new ArrayList<>(); // 本机玩家的手牌
    private List<Card> seat1Cards = new ArrayList<>(); // 1号座位的手牌
    private List<Card> seat2Cards = new ArrayList<>(); // 2号座位的手牌
    private List<Card> bottomCards = new ArrayList<>(); // 底牌（3张，给地主）

    private List<Card> playerPlayedCards = new ArrayList<>(); // 本机玩家已出的牌
    private List<Card> seat1PlayedCards = new ArrayList<>(); // 1号座位已出的牌
    private List<Card> seat2PlayedCards = new ArrayList<>(); // 2号座位已出的牌

    private List<Card>[] aiBotHands = new List[]{null, null}; // AI的手牌引用（座位1和座位2）
    private int[] handCounts = new int[]{17, 17, 17}; // 每个座位剩余手牌数
    private int[] cardCounterCounts = createFullDeckCounter(); // 记牌器数据

    private Handler handler = new Handler(Looper.getMainLooper()); // 主线程定时器
    // aiThinkingRunnable moved to DouDiZhuAIHelper
    private boolean isCleaningUp = false; // 是否正在清理资源（防止重复清理）
    private DouDiZhuSoundManager soundManager; // 音效管理器
    private int lastTurnSoundState = -1; // 上次播放回合音效时的游戏状态
    private int lastTurnSoundSeat = -1; // 上次播放回合音效时的座位号

    private StringBuilder chatLog = new StringBuilder(); // 聊天记录缓冲区
    private final List<JSONObject> hostChatHistory = new ArrayList<>(); // 房主端保存的聊天历史（新玩家加入时发送给他）
    // pendingClientIntent moved to DouDiZhuNetworkHandler

    /**
     * Activity 创建时初始化。
     *
     * <p>初始化各管理器（SeatManager、SyncManager、UIController、AIHelper、NetworkHandler），
     * 设置布局，并在主线程中完成视图初始化和网络初始化。</p>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        remoteP2PMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_REMOTE_P2P, false);
        seatManager = new DouDiZhuSeatManager();
        seatManager.setContext(this);
        localPeerToken = seatManager.getLocalPeerToken();
        syncManager = new DouDiZhuSyncManager(seatManager, null);
        uiController = new DouDiZhuUIController(this);
        uiController.setCallback(this);
        aiHelper = new DouDiZhuAIHelper(handler, this);
        networkHandler = new DouDiZhuNetworkHandler(handler, this);
        setContentView(R.layout.activity_doudizhu_online);
        soundManager = new DouDiZhuSoundManager(this);

        handler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            uiController.initViews();
            uiController.initListeners(remoteP2PMode);
            initNetwork();
            uiController.showLobby(remoteP2PMode);
        });
    }

    /**
     * Activity 销毁时清理资源。
     *
     * <p>停止 AI 操作，关闭服务器和客户端连接，注销 NSD 服务，
     * 移除所有 Handler 回调，避免内存泄漏。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
        releaseSoundManager();
    }

    /**
     * 初始化局域网管理器，设置服务发现和错误监听。
     */
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

    /**
     * 以房主模式启动。
     *
     * <p>初始化房主座位，创建游戏服务器，根据模式（局域网/云联机）
     * 选择不同的连接方式。局域网模式通过 NSD 注册服务和 TCP 监听，
     * 云联机模式通过 WebSocket 连接 Relay 中转服务器。</p>
     */
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

    /**
     * 生成 6 位随机房间码。
     *
     * <p>字符集排除了容易混淆的字符（0/O、1/I），仅使用大写字母和数字。
     * 就像生成一个"临时密码"，让朋友输入这个码就能加入你的房间。</p>
     *
     * @return 6 位房间码字符串
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
     * 从候选端口列表中查找可用端口。
     *
     * <p>依次尝试绑定 HOST_PORT_CANDIDATES 中的端口，
     * 全部不可用则返回默认端口。</p>
     *
     * @return 可用的端口号
     */
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

    /**
     * 复制房间地址到剪贴板。
     *
     * <p>云联机模式复制房间码，局域网模式复制 p2p://IP:端口 格式的地址。</p>
     */
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
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build();
        for (String endpoint : endpoints) {
            try {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .get()
                        .addHeader("User-Agent", "GameMatrixApp-DDZ-P2P")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String ip = response.body().string().trim();
                        if (!ip.isEmpty()) {
                            return ip;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "fetchPublicIp failed: " + endpoint + " " + e.getMessage());
            }
        }
        return "";
    }

    // ============ Client Mode ============

    /**
     * 启动客户端发现流程。
     *
     * <p>云联机模式显示房间码输入对话框，局域网模式启动 NSD 服务发现。
     * 3 秒后如果未找到房间，自动添加手动输入 IP 按钮。</p>
     */
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

    /**
     * 准备客户端连接，初始化 GameSocketClient 并设置各种监听器。
     *
     * <p>根据 remoteP2PMode 设置不同的重连策略：
     * 云联机模式重连次数多、间隔长；局域网模式重连次数少、间隔短。</p>
     */
    private void prepareClientConnection() {
        mode = 1;
        mySeatIndex = -1;
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

    /**
     * 连接到指定 IP 和端口的服务器（客户端模式）。
     *
     * <p>初始化客户端连接，设置消息监听器，启动连接线程。</p>
     *
     * @param ip 服务器 IP 地址
     * @param port 服务器端口
     */
    private void connectToServer(String ip, int port) {
        prepareClientConnection();
        client.connect(ip, port);
    }

    /**
     * 通过房间码连接到云联机中转服务器（客户端模式）。
     *
     * <p>生成 WebSocket URL 并通过 WebSocket 连接 Relay 服务器，
     * 使用房间码标识目标房间。</p>
     *
     * @param roomCode 6 位房间码
     */
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

    /**
     * 房主端处理客户端连接事件。
     *
     * <p>云联机模式下不立即分配座位（等待 CLIENT_JOIN 消息中的 peerToken），
     * 局域网模式下立即分配座位并发送 SEAT_ASSIGNED 消息。
     * 如果游戏正在进行中，还会发送当前状态同步。</p>
     *
     * @param clientId 连接的客户端 ID
     * @param ip 客户端 IP 地址
     */
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
                        try { server.sendTo(clientId, createErrorMsg("房间已满")); } catch (Exception e) { Log.w(TAG, "Send room full error: " + e.getMessage()); }
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

    /**
     * 为客户端分配座位。
     *
     * <p>分配优先级（从高到低，就像医院分诊——最紧急的优先处理）：
     * <ol>
     *   <li>该 clientId 已有座位 → 返回原座位（你之前坐这，还坐这）</li>
     *   <li>peerToken 匹配已有座位 → 恢复该座位（断线重连：凭"身份证"找回座位）</li>
     *   <li>游戏进行中，IP 匹配的远程座位 → 恢复该座位（同一台设备重新连上了）</li>
     *   <li>游戏进行中，当前回合的空远程座位 → 优先分配（让当前该出牌的人先坐下）</li>
     *   <li>按顺序分配第一个空座位（1 或 2）</li>
     * </ol>
     * </p>
     *
     * @param clientId 客户端 ID
     * @param ip 客户端 IP 地址
     * @param peerToken 客户端的唯一标识令牌（断线重连的"身份证"）
     * @return 分配的座位索引，-1 表示无可用座位（房间满了）
     */
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

    /**
     * 处理客户端加入请求（房主端）。
     *
     * <p>当收到 CLIENT_JOIN 消息时调用。校验协议版本，分配座位，
     * 处理断线重连逻辑（同一 peerToken 视为重连，踢掉旧连接），
     * 发送 SEAT_ASSIGNED 消息和当前游戏状态。</p>
     *
     * @param clientId 客户端 ID
     * @param msg JOIN 消息
     */
    @Override public void handleClientJoin(int clientId, JSONObject msg) {
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

    /**
     * 房主端处理客户端断线事件。
     *
     * <p>断线处理策略（不同情况不同处理，就像有人临时离开牌桌）：
     * <ul>
     *   <li>云联机模式：保留座位类型为 REMOTE，等待重连（朋友去上厕所，留着位子等他回来）</li>
     *   <li>局域网模式 + 大厅/游戏结束：替换为 AI（还没开始打牌，直接让电脑顶上）</li>
     *   <li>局域网模式 + 游戏进行中：保留座位等待重连（打牌中途离开，先等一等）</li>
     * </ul>
     * </p>
     *
     * @param clientId 断线的客户端 ID
     * @param reason 断线原因
     */
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
                            } catch (Exception e) { Log.w(TAG, "Show disconnect toast: " + e.getMessage()); }
                        } else if (gameState == STATE_LOBBY || gameState == STATE_GAME_OVER) {
                            seatTypes[i] = SEAT_TYPE_AI;
                            initAIForSeat(i);
                            try {
                                Toast.makeText(DouDiZhuOnlineActivity.this, getFixedSeatName(i) + " 离开，已替换为 AI", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) { Log.w(TAG, "Show leave toast: " + e.getMessage()); }
                        } else {
                            seatTypes[i] = SEAT_TYPE_REMOTE;
                            try {
                                Toast.makeText(DouDiZhuOnlineActivity.this, getFixedSeatName(i) + " 掉线，等待重连", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) { Log.w(TAG, "Show offline toast: " + e.getMessage()); }
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

    /**
     * 断开连接回调。
     *
     * <p>关闭服务器和客户端连接，重置状态，回到大厅界面。</p>
     */
    @Override public void onDisconnect() {
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

    /**
     * 开始游戏回调（由 UI 的"开始游戏"按钮触发）。
     *
     * <p>校验条件：必须为房主模式，云联机模式下不能有断线远程玩家，
     * 且至少有一个远程玩家加入。</p>
     */
    @Override public void onStartGame() {
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

    /**
     * 开始一局游戏。
     *
     * <p>执行洗牌发牌，初始化游戏状态，设置当前回合，
     * 根据座位类型（AI/远程/本地）触发对应的操作。</p>
     */
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
        uiController.showBidUI();
    }

    private void showPlayUI() {
        uiController.showPlayUI();
    }

    @Override public void hideAllButtons() {
        uiController.hideAllButtons();
    }

    private void showGameChrome() {
        uiController.showGameChrome();
    }

    private void updateConnectedStatusText() {
        uiController.updateConnectedStatusText(mode == 0, client != null && client.isConnected(), remoteP2PMode);
    }

    /**
     * 刷新本地操作控件。
     *
     * <p>根据当前游戏状态和是否轮到本地玩家，显示或隐藏操作按钮。
     * 叫地主阶段显示叫/不叫按钮，出牌阶段显示出牌/提示/不出按钮。</p>
     */
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

    /**
     * 叫地主按钮回调。
     *
     * <p>提交叫地主决策（call=true）。</p>
     */
    @Override public void onCallLandlord() {
        playClickSound();
        submitBid(true);
    }

    /**
     * 不叫地主按钮回调。
     *
     * <p>提交不叫地主决策（call=false）。</p>
     */
    @Override public void onNoCall() {
        playClickSound();
        submitBid(false);
    }

    /**
     * 提交叫地主决策。
     *
     * <p>房主模式下直接处理叫地主逻辑并广播状态；
     * 客户端模式下发送 BID_RESPONSE 消息给房主。</p>
     *
     * @param call true 表示叫地主，false 表示不叫
     */
    private void submitBid(boolean call) {
        try {
            if (gameState != STATE_BIDDING) return;
            if (mode == 0 && currentTurn != 0) return;
            if (mode == 1 && (mySeatIndex < 0 || currentTurn != mySeatIndex)) return;
            if (soundManager != null) soundManager.bid(call, currentTurn);

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

    /**
     * 发送客户端意图消息给房主。
     *
     * <p>委托给 NetworkHandler 处理，包括消息装饰、重试和断线缓存。</p>
     *
     * @param msg 要发送的 JSON 消息
     * @return true 表示消息已发送
     */
    private boolean sendClientIntent(JSONObject msg) {
        return networkHandler.sendClientIntent(msg);
    }

    // decorateClientIntent moved to DouDiZhuNetworkHandler

    // scheduleClientIntentRepeats moved to DouDiZhuNetworkHandler

    // resendClientIntent moved to DouDiZhuNetworkHandler

    // queueClientIntentForReconnect moved to DouDiZhuNetworkHandler

    private void flushPendingClientIntentIfReady() {
        networkHandler.flushPendingClientIntentIfReady();
    }

    /**
     * 推进叫地主轮次。
     *
     * <p>如果三轮均无人叫地主，则随机指定一名玩家为地主。
     * 否则轮转到下一个玩家继续叫地主。</p>
     */
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

    /**
     * 调度 AI 叫地主操作。
     */
    private void scheduleAIBid() {
        aiHelper.scheduleAIBid();
    }

    /**
     * 向远程座位发送叫地主请求。
     *
     * @param seatIndex 远程座位的索引
     */
    private void sendBidRequestToRemote(int seatIndex) {
        if (server == null) return;
        int clientId = seatManager.getClientId(seatIndex);
        if (clientId < 0) return;
        JSONObject msg = DouDiZhuProtocol.createBidRequestMsg(getCurrentStateVersion(), seatIndex, currentTurn);
        server.sendTo(clientId, msg);
    }

    /**
     * 评估手牌是否应该叫地主。
     *
     * @param handCards 手牌列表
     * @return true 表示建议叫地主
     */
    private boolean evaluateHandForBid(List<Card> handCards) {
        return DouDiZhuAIHelper.shouldCallLandlord(handCards);
    }

    /**
     * 设置地主。
     *
     * <p>将底牌加入地主手牌，排序后更新 AI 手牌引用和 UI。</p>
     *
     * @param seatIndex 地主的座位索引
     */
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

    /**
     * 出牌按钮回调。
     *
     * <p>校验选中的牌是否合法，房主模式直接执行出牌并广播，
     * 客户端模式发送 REQUEST_PLAY 消息给房主。</p>
     */
    @Override public void onPlayCard() {
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
            } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
            if (sendClientIntent(msg)) {
                if (soundManager != null) {
                    soundManager.cards(selectedCards, GameRuleUtil.getCardType(selectedCards), mySeatIndex);
                }
                enablePlayerControls(false);
            }
        }
    }

    /**
     * 提示按钮回调。
     *
     * <p>使用 AIBot 为当前手牌寻找可出的牌组合，选中提示的牌并刷新牌桌。</p>
     */
    @Override public void onHint() {
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

    /**
     * 不出按钮回调。
     *
     * <p>如果当前是自由出牌（无上家出牌），则不允许不出。
     * 房主模式直接执行不出逻辑，客户端模式发送 PASS 消息给房主。</p>
     */
    @Override public void onPass() {
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
            if (soundManager != null) soundManager.pass(mySeatIndex);
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
            } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
            if (sendClientIntent(msg)) {
                if (soundManager != null) soundManager.pass(mySeatIndex);
                enablePlayerControls(false);
            }
        }
    }

    // ============ AI Handling ============

    /** 判断当前是否轮到 AI 出牌 */
    private boolean isAITurn() {
        return aiHelper.isAITurn();
    }

    /** 调度 AI 出牌操作（延迟执行） */
    private void scheduleAITurn() {
        aiHelper.scheduleAITurn();
    }

    /** 立即执行 AI 出牌逻辑 */
    private void executeAITurn() {
        aiHelper.executeAITurn();
    }

    /**
     * 获取指定座位的 AI 手牌。
     *
     * @param seatIndex 座位索引（1 或 2）
     * @return AI 手牌列表
     */
    private List<Card> getAIHandCards(int seatIndex) {
        return DouDiZhuAIHelper.getAIHandCards(seatIndex, aiBotHands[0], aiBotHands[1]);
    }

    private void initAIForSeat(int seatIndex) {
        if (seatIndex == 1) aiBotHands[0] = new ArrayList<>(seat1Cards);
        else if (seatIndex == 2) aiBotHands[1] = new ArrayList<>(seat2Cards);
    }

    // ============ AICallback Implementation ============

    @Override
    public int getGameState() { return gameState; }

    @Override
    public int getCurrentTurn() { return currentTurn; }

    @Override
    public int[] getSeatTypes() { return seatTypes; }

    /**
     * AI 出牌回调。
     *
     * <p>AI 决定出牌时，房主模式直接执行出牌逻辑，客户端模式不应触发此回调。</p>
     *
     * @param seatIndex AI 所在的座位索引
     * @param cards AI 决定出的牌
     */
    @Override public void onAIPlay(int seatIndex, List<Card> cards) {
        playerPassed[seatIndex] = false;
        executePlay(seatIndex, cards);
        if (mode == 0) {
            broadcastSyncState();
        }
    }

    /**
     * AI 不出回调。
     *
     * <p>AI 决定不出时，标记该座位为"不出"，检查桌面清理条件，
     * 然后轮转到下一个玩家。</p>
     *
     * @param seatIndex AI 所在的座位索引
     */
    @Override public void onAIPass(int seatIndex) {
        playerPassed[seatIndex] = true;
        if (soundManager != null) soundManager.pass(seatIndex);
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

    /**
     * AI 叫地主决策回调。
     *
     * <p>AI 根据手牌强度决定是否叫地主，房主模式直接执行叫地主逻辑。</p>
     *
     * @param call true 表示 AI 决定叫地主
     */
    @Override public void onAIBid(boolean call) {
        if (soundManager != null) soundManager.bid(call, currentTurn);
        if (call) {
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
    }

    // ============ NetworkDelegate Implementation ============

    @Override
    public int getMySeatIndex() { return mySeatIndex; }

    @Override
    public int getMode() { return mode; }

    @Override
    public int[] getSeatClientIds() { return seatClientIds; }

    @Override
    public void setSeatClientId(int seatIndex, int clientId) { seatClientIds[seatIndex] = clientId; }

    @Override
    public GameSocketClient getClient() { return client; }

    @Override
    public DouDiZhuSyncManager getSyncManager() { return syncManager; }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void updateConnectionStatus(String text, int color) {
        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText(text);
            tvConnectionStatus.setTextColor(color);
        }
    }

    // ============ Core Game Logic ============

    /**
     * 校验出牌是否合法。
     *
     * @param cards 当前选中的牌
     * @param previousCards 上家出的牌，null 表示自由出牌
     * @return true 表示合法
     */
    private boolean validatePlay(List<Card> cards, List<Card> previousCards) {
        return DouDiZhuRuleEngine.validatePlay(cards, previousCards);
    }

    /**
     * 执行出牌操作。
     *
     * <p>从手牌中移除已出的牌，重置所有"不出"标记，更新出牌记录。
     * 如果出牌后手牌为空，判定该玩家获胜；否则轮转到下一个玩家。</p>
     *
     * @param seatIndex 出牌的座位索引
     * @param cards 出的牌列表
     */
    private void executePlay(int seatIndex, List<Card> cards) {
        logGame("PLAY_CARD", seatIndex, "cards=" + cards.size());
        if (soundManager != null) {
            soundManager.cards(cards, GameRuleUtil.getCardType(cards), seatIndex);
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

    /**
     * 轮转到下一个玩家。
     *
     * <p>按座位顺序循环（0→1→2→0），根据新回合的座位类型
     * 触发对应操作（AI/远程/本地）。</p>
     */
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

    /**
     * 检查并清理桌面。
     *
     * <p>当除最后出牌者外的所有玩家都"不出"时，清空桌面出牌记录，
     * 将出牌权交还给最后出牌的玩家。</p>
     *
     * @return true 表示桌面已清理
     */
    private boolean checkAndClearTable() {
        if (!DouDiZhuRuleEngine.shouldClearTable(playerPassed, lastPlayerWhoPlayed, TOTAL_SEATS)) return false;
        clearAllPlayedCards();
        playerPassed = new boolean[]{false, false, false};
        currentTurn = lastPlayerWhoPlayed;
        updateTableView();
        return true;
    }

    /**
     * 游戏结束处理。
     *
     * <p>判定胜负结果：房主模式直接比较座位索引，客户端模式比较阵营（地主/农民）。
     * 地主赢 +100 分，农民赢 +50 分。</p>
     *
     * @param winnerIndex 赢家的座位索引
     */
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

        int score = (winnerIndex == landlordIndex) ? 100 : 50;
        String scoreDetail = "本局得分：" + (score >= 0 ? "+" : "") + score;
        uiController.showGameOverDialog("游戏结束", result, scoreDetail);

        if (mode == 0) broadcastSyncState();
        enablePlayerControls(false);
    }

    private void showGameOverDialog(String message) {
        gameState = STATE_GAME_OVER;
        hideAllButtons();
        uiController.showGameOverDialog("游戏结束", message, null);
    }

    /**
     * 再来一局回调。
     *
     * <p>重置游戏状态，回到大厅界面，保留网络连接。</p>
     */
    @Override public void onPlayAgain() {
        uiController.hideGameOverDialog();
        if (mode == 0) {
            startGame();
        } else {
            uiController.setGameOverResultText("等待房主开始新游戏...");
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

    /**
     * 获取指定座位的手牌。
     *
     * <p>客户端模式下，无论座位索引如何，本机座位始终返回 playerHandCards
     * （因为客户端只持有自己的手牌数据）。</p>
     *
     * @param seatIndex 座位索引
     * @return 该座位的手牌列表
     */
    @Override public List<Card> getSeatHandCards(int seatIndex) {
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

    /**
     * 获取当前桌面上最后出的牌。
     *
     * <p>用于判断下家出牌时是否需要压过上家。如果最后出牌者已"不出"
     * 或尚无人出牌，则返回 null（表示自由出牌）。</p>
     *
     * @return 最后出的牌列表，null 表示自由出牌
     */
    @Override public List<Card> getLastPlayedCards() {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= 3) return null;
        if (playerPassed[lastPlayerWhoPlayed]) return null;
        List<Card> played = getSeatPlayedCards(lastPlayerWhoPlayed);
        if (played == null || played.isEmpty()) return null;
        return played;
    }

    @Override public int getLandlordSeat() {
        return landlordIndex;
    }

    @Override public int getLastPlayerWhoPlayed() {
        return lastPlayerWhoPlayed;
    }

    @Override public int getLandlordStatusForAISeat(int seatIndex) {
        return getLandlordStatusForSeat(seatIndex);
    }

    @Override public int getSeatRemainingCardCount(int seatIndex) {
        return getSeatCardCount(seatIndex);
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

    /**
     * 获取客户端视角下的显示座位映射。
     *
     * <p>客户端模式下，本机座位始终显示在底部（slot 0），
     * 左边和右边的座位按顺序排列。房主模式下座位直接对应索引。</p>
     *
     * @param seatIndex 实际座位索引
     * @return 显示槽位（0=底部，1=左边，2=右边），-1 表示无效
     */
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

    /**
     * 更新牌桌 UI。
     *
     * <p>根据客户端/房主模式计算各座位的显示位置，收集所有牌面数据，
     * 传递给 UIController 更新牌桌视图。</p>
     */
    private void updateTableView() {
        if (uiController == null) return;
        int leftSeat = getLeftDisplaySeat();
        int rightSeat = getRightDisplaySeat();
        uiController.updateTableView(
                playerHandCards, bottomCards,
                playerPlayedCards, seat1PlayedCards, seat2PlayedCards,
                getSeatCardCount(leftSeat), getSeatCardCount(rightSeat),
                new int[]{
                        getLandlordStatusForSeat(getPlayerDisplaySeat()),
                        getLandlordStatusForSeat(leftSeat),
                        getLandlordStatusForSeat(rightSeat)
                },
                new String[]{
                        getSeatName(getPlayerDisplaySeat()),
                        getSeatName(leftSeat),
                        getSeatName(rightSeat)
                },
                playerPassed[leftSeat], playerPassed[rightSeat],
                mode == 1 ? cardCounterCounts : createCardCounterForSeat(getPlayerDisplaySeat())
        );
    }

    private void updateLandlordIndicator() {
        uiController.updateLandlordIndicator(landlordIndex < 0 ? "待定" : getSeatName(landlordIndex));
    }

    private void updateTurnIndicator() {
        String turnText;
        if (gameState == STATE_BIDDING) {
            turnText = getTurnSeatName(currentTurn) + "叫地主";
        } else {
            turnText = getTurnSeatName(currentTurn) + "出牌";
        }
        uiController.updateTurnIndicator(turnText);
    }

    /**
     * 启用或禁用玩家出牌控制按钮。
     *
     * @param enable true 启用并显示，false 禁用并隐藏
     */
    private void enablePlayerControls(boolean enable) {
        uiController.enablePlayerControls(enable);
    }

    // ============ Server Message Handling (Host) ============

    private void onServerMessageReceived(int clientId, JSONObject msg) {
        logEvent("MSG_RECV", remoteRoomCode, clientId, msg.optString("type", ""));
        networkHandler.onServerMessageReceived(clientId, msg);
    }

    // isClientAction moved to DouDiZhuNetworkHandler

    // shouldProcessClientAction moved to DouDiZhuNetworkHandler

    /**
     * 处理状态确认消息（房主端）。
     *
     * <p>更新该座位已确认的状态版本号，用于判断客户端是否已同步到最新状态。</p>
     *
     * @param seatIndex 确认的座位索引
     * @param msg STATE_ACK 消息
     */
    @Override public void handleStateAck(int seatIndex, JSONObject msg) {
        Log.d(TAG, "STATE_ACK seat=" + seatIndex + " version=" + msg.optLong("stateVersion", -1L));
    }

    /**
     * 处理远程玩家出牌请求（房主端）。
     *
     * <p>校验回合和座位后，验证出牌合法性，执行出牌并广播状态。</p>
     *
     * @param seatIndex 出牌的座位索引
     * @param clientId 客户端 ID
     * @param msg 出牌请求消息
     */
    @Override public void handleRemotePlayRequest(int seatIndex, int clientId, JSONObject msg) {
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
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /**
     * 处理远程玩家不出请求（房主端）。
     *
     * <p>校验回合和座位后，执行不出逻辑。自由出牌时不允许不出。</p>
     *
     * @param seatIndex 不出的座位索引
     * @param clientId 客户端 ID
     * @param msg 不出请求消息
     */
    @Override public void handleRemotePass(int seatIndex, int clientId, JSONObject msg) {
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
        if (soundManager != null) soundManager.pass(mySeatIndex);
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

    /**
     * 处理远程玩家叫地主响应（房主端）。
     *
     * <p>校验回合后，执行叫地主决策逻辑并广播结果。</p>
     *
     * @param seatIndex 叫地主的座位索引
     * @param clientId 客户端 ID
     * @param msg 叫地主响应消息
     */
    @Override public void handleRemoteBidResponse(int seatIndex, int clientId, JSONObject msg) {
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
            if (soundManager != null) soundManager.bid(call, seatIndex);
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
        logEvent("MSG_RECV", remoteRoomCode, mySeatIndex, msg.optString("type", ""));
        networkHandler.onClientMessageReceived(msg);
    }

    /**
     * 处理座位分配消息（客户端）。
     *
     * <p>设置本机座位索引，更新连接状态，同步座位类型到 SeatManager。</p>
     *
     * @param msg SEAT_ASSIGNED 消息
     */
    @Override public void handleSeatAssigned(JSONObject msg) {
        mySeatIndex = msg.optInt("seatIndex", 0);
        try {
            JSONArray types = msg.getJSONArray("seatTypes");
            for (int i = 0; i < TOTAL_SEATS; i++) {
                seatTypes[i] = types.getInt(i);
            }
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
        if (tvServerInfo != null) tvServerInfo.setText("已分配座位 " + getFixedSeatName(mySeatIndex));
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
        if (msg.optBoolean("reconnected", false)) {
            appendChat("系统: 已恢复到原座位 " + getFixedSeatName(mySeatIndex));
        }
        updateConnectedStatusText();
    }

    /**
     * 处理座位更新消息（客户端）。
     *
     * <p>更新本地座位类型数组并同步到 SeatManager。</p>
     *
     * @param msg SEAT_UPDATE 消息
     */
    @Override public void handleSeatUpdate(JSONObject msg) {
        try {
            JSONArray types = msg.getJSONArray("seatTypes");
            for (int i = 0; i < TOTAL_SEATS; i++) {
                seatTypes[i] = types.getInt(i);
            }
            landlordIndex = msg.optInt("landlordIndex", -1);
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    /**
     * 处理客户端收到的手牌分发消息。
     *
     * <p>解析手牌和底牌数据，设置游戏状态为叫地主阶段。</p>
     *
     * @param msg HAND_CARDS 消息
     */
    @Override public void handleHandCards(JSONObject msg) {
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

    /**
     * 处理客户端收到的叫地主请求消息。
     *
     * <p>如果轮到本机叫地主，显示叫地主按钮；否则隐藏按钮等待。</p>
     *
     * @param msg BID_REQUEST 消息
     */
    @Override public void handleBidRequest(JSONObject msg) {
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

    /**
     * 处理客户端收到的叫地主结果广播。
     *
     * <p>更新本地叫地主状态，如果有人叫地主则设置地主并进入出牌阶段，
     * 否则继续叫地主轮次。</p>
     *
     * @param msg BID_RESULT 广播消息
     */
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
                soundManager.cards(playedCards, GameRuleUtil.getCardType(playedCards), playerIndex);
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

    /**
     * 处理客户端收到的不出动作广播。
     *
     * <p>更新本地"不出"状态，检查桌面清理条件，轮转到下一个玩家。</p>
     *
     * @param msg PASS 广播消息
     */
    private void handlePassAction(JSONObject msg) {
        int playerIndex = msg.optInt("playerIndex", -1);
        if (playerIndex < 0) return;
        playerPassed[playerIndex] = true;
        if (soundManager != null) soundManager.pass(playerIndex);
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

    /**
     * 处理游戏结束消息（客户端）。
     *
     * <p>解析赢家信息，判定本机胜负，显示游戏结束对话框。</p>
     *
     * @param msg GAME_OVER 消息
     */
    @Override public void handleGameOverMsg(JSONObject msg) {
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

    /**
     * 处理客户端收到的状态同步消息。
     *
     * <p>过滤过期版本号的消息（旧消息不要重复处理），解析完整的游戏状态并应用到本地，
     * 包括座位类型、手牌、出牌、底牌、记牌器等。应用后发送 STATE_ACK 确认。
     * 这就像"对账"——房主发来最新的账本，客户端对照更新自己的记录，
     * 然后告诉房主"我已收到"。</p>
     *
     * @param msg SYNC_STATE 消息
     */
    @Override public void handleSyncState(JSONObject msg) {
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
        networkHandler.sendStateAck(stateVersion);
    }

    @Override public void handleChatMessage(JSONObject msg) {
        int seatIndex = msg.optInt("seatIndex", -1);
        String message = msg.optString("message", "");
        String name = seatIndex >= 0 ? getFixedSeatName(seatIndex) : "系统";
        if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
        appendChat(name + ": " + message);
    }

    /**
     * 处理确认应答消息（客户端）。
     *
     * <p>收到 ACK 后，如果操作被接受则更新状态版本号；
     * 如果被拒绝则显示原因并刷新控件。</p>
     *
     * @param msg ACK 消息
     */
    @Override public void handleAck(JSONObject msg) {
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

    @Override public void handleChatHistory(JSONObject msg) {
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

    /**
     * 从消息中更新权威字段（客户端模式）。
     *
     * <p>从房主广播的消息中提取 currentTurn、landlordIndex、gameState 等字段，
     * 客户端以房主数据为准，避免本地状态与房主不一致。
     * 就像"以裁判的记录为准"——客户端和房主的数据有出入时，听房主的。</p>
     *
     * @param msg 包含权威字段的 JSON 消息
     */
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

    /**
     * 广播聊天消息到所有客户端（房主端）。
     *
     * @param seatIndex 发送者的座位索引
     * @param message 聊天消息内容
     */
    @Override public void broadcastChat(int seatIndex, String message) {
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

    /**
     * 记录房主端的聊天消息到历史记录。
     *
     * @param seatIndex 发送者的座位索引
     * @param message 聊天消息内容
     */
    private void rememberHostChat(int seatIndex, String message) {
        if (message == null) return;
        JSONObject item = new JSONObject();
        try {
            item.put("seatIndex", seatIndex);
            item.put("message", message);
            item.put("time", System.currentTimeMillis());
            hostChatHistory.add(item);
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
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

    /**
     * 向所有远程座位广播聊天历史。
     *
     * <p>新客户端加入时，将已有的聊天记录发送给该客户端。</p>
     */
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

    @Override public void onSendChat(String message) {
        playClickSound();
        if (etChatInput == null) return;
        String chatText = etChatInput.getText().toString().trim();
        if (chatText.isEmpty()) return;
        etChatInput.setText("");

        if (mode == 0) {
            broadcastChat(0, chatText);
        } else if (client != null) {
            JSONObject msg = new JSONObject();
            try {
                msg.put("type", "CHAT");
                msg.put("message", chatText);
                msg.put("seatIndex", mySeatIndex);
            } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
            if (!sendClientIntent(msg)) {
                etChatInput.setText(chatText);
            }
        }
    }

    private void appendChat(String message) {
        uiController.appendChat(message);
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

    /**
     * 将本地变量同步到 SeatManager。
     *
     * <p>将 seatTypes、seatClientIds 等旧变量的值写入 SeatManager，
     * 保持两者数据一致。在需要 SeatManager 参与逻辑判断前调用。
     * 就像把"旧账本"的数据抄到"新账本"上。</p>
     */
    @Override public void syncLocalToManager() {
        for (int i = 0; i < DouDiZhuSeatManager.TOTAL_SEATS; i++) {
            seatManager.updateSeat(i, seatClientIds[i], seatClientIps[i], seatPeerTokens[i], seatTypes[i]);
            seatManager.setLastProcessedActionId(i, lastProcessedActionIds[i]);
        }
    }

    /**
     * 将 SeatManager 的数据同步回本地变量。
     *
     * <p>从 SeatManager 读取 seatTypes、seatClientIds 等数据到旧变量，
     * 保持两者数据一致。在 SeatManager 执行逻辑操作后调用。
     * 就像把"新账本"的更新结果抄回"旧账本"。</p>
     */
    @Override public void syncManagerToLocal() {
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

    /**
     * 清理所有网络和游戏资源。
     *
     * <p>关闭服务器、客户端连接，注销 NSD 服务，取消 AI 操作，
     * 重置游戏状态。在断开连接和 Activity 销毁时调用。</p>
     */
    private void cleanup() {
        isCleaningUp = true;
        if (aiHelper != null) {
            aiHelper.cancelPending();
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

    @Override public void onCreateRoom() { startAsHost(); }
    @Override public void onJoinRoom() { startClientDiscovery(); }
    @Override public void onCopyRoomAddress() { copyRoomAddressToClipboard(); }
    /**
     * 退出回调。
     *
     * <p>关闭连接，结束 Activity。</p>
     */
    @Override public void onExit() { finish(); }
    @Override public void onManualJoin(String ip, int port) { connectToServer(ip, port); }
    @Override public void onRemoteJoin(String roomCode) { connectToRelayRoom(roomCode); }
}

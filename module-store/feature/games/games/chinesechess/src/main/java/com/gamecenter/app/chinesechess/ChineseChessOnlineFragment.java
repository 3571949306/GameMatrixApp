package com.gamecenter.app.chinesechess;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * 局域网双机对战 Fragment（LOCAL-P2P，无服务器、local-first）。
 *
 * <p>玩法：一台设备「创建房间」（ServerSocket 随机端口 + NSD 广播），另一台在同一
 * 局域网「加入房间」（NSD 发现列表或手动 IP:PORT 直连），双端经 LanChessSession
 * 行协议实时同步着法。NSD 组播不可用的网络下降级为纯直连。</p>
 *
 * <p>对局承载：本 Fragment 自带棋盘渲染（ChineseChessView）与对局逻辑（ChineseChessGame），
 * 与人机主界面（ChineseChessModuleFragment）平行的独立对局页。不把 Session 回传主界面
 * 是有意为之：连接生命周期与页面生命周期天然一致（退出页面即 session.close 成对释放），
 * 也避免主界面为联机注入 AI 回调/代次保护之外的又一状态源。</p>
 *
 * <p>线程约定：session 回调来自 Socket 读线程，一律经 mainHandler 切主线程后再
 * 操作视图与棋局；网络 connect/建房的阻塞操作放后台线程。</p>
 */
public class ChineseChessOnlineFragment extends Fragment {

    private static final String TAG = "ChineseChessOnline";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final String ROOM_NAME_PREFIX = "GameMatrix-象棋-";
    private static final char[] ROOM_SUFFIX_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private ChineseChessGame game;
    private ChineseChessView boardView;

    private LanChessSession session;
    private LanChessServer server;
    private LanChessClient client;

    // ---- 大厅视图（建房/加入） ----
    private LinearLayout lobbyLayout;
    private LinearLayout roomListLayout;
    private TextView lobbyStatusText;
    private ProgressBar lobbyProgress;
    private EditText ipInput;
    private EditText portInput;

    // ---- 对局视图 ----
    private LinearLayout gameLayout;
    private TextView turnStatusText;
    private TextView winnerText;

    private volatile boolean connecting;
    private volatile boolean isPlaying;
    private boolean matchResultShown;
    private boolean iAmRed;

    private int selectedX = -1;
    private int selectedY = -1;
    private List<int[]> selectedMoves;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        game = new ChineseChessGame();
        View root = buildViews();
        return root;
    }

    private View buildViews() {
        Context ctx = requireContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(buildLobby(ctx));
        root.addView(buildGameScreen(ctx));
        return root;
    }

    // ==================== 大厅 ====================

    private View buildLobby(Context ctx) {
        lobbyLayout = new LinearLayout(ctx);
        lobbyLayout.setOrientation(LinearLayout.VERTICAL);
        lobbyLayout.setPadding(48, 48, 48, 48);
        lobbyLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(ctx);
        title.setText(getString(R.string.chess_online_title));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 12);

        TextView subtitle = new TextView(ctx);
        subtitle.setText("局域网双机对战：两台设备连同一 Wi-Fi 热点即可");
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 16);

        Button createRoomBtn = buildButton(ctx, getString(R.string.chess_online_create_room), 18);
        createRoomBtn.setOnClickListener(v -> hostRoom());

        Button joinRoomBtn = buildButton(ctx, getString(R.string.chess_online_join_room), 18);
        joinRoomBtn.setOnClickListener(v -> startRoomDiscovery());

        lobbyProgress = new ProgressBar(ctx);
        lobbyProgress.setVisibility(View.GONE);

        lobbyStatusText = new TextView(ctx);
        lobbyStatusText.setTextSize(15);
        lobbyStatusText.setGravity(Gravity.CENTER);
        lobbyStatusText.setPadding(0, 16, 0, 8);

        // NSD 发现的房间列表（点击即连；滚动容器防止房间过多撑爆大厅）。
        ScrollView roomScroll = new ScrollView(ctx);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 150));
        roomScroll.setLayoutParams(scrollParams);
        roomListLayout = new LinearLayout(ctx);
        roomListLayout.setOrientation(LinearLayout.VERTICAL);
        roomScroll.addView(roomListLayout);

        TextView manualTitle = new TextView(ctx);
        manualTitle.setText("自动发现不可用？手动输入房主 IP 直连：");
        manualTitle.setTextSize(13);
        manualTitle.setPadding(0, 8, 0, 4);

        LinearLayout manualRow = new LinearLayout(ctx);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        ipInput = new EditText(ctx);
        ipInput.setHint("如 192.168.1.10");
        ipInput.setSingleLine(true);
        ipInput.setTextSize(14);
        ipInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f));
        portInput = new EditText(ctx);
        portInput.setHint("端口");
        portInput.setSingleLine(true);
        portInput.setTextSize(14);
        portInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
        Button connectBtn = buildButton(ctx, "直连加入", 14);
        connectBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        connectBtn.setOnClickListener(v -> connectFromManualInput());
        manualRow.addView(ipInput);
        manualRow.addView(portInput);
        manualRow.addView(connectBtn);

        lobbyLayout.addView(title);
        lobbyLayout.addView(subtitle);
        lobbyLayout.addView(createRoomBtn);
        lobbyLayout.addView(joinRoomBtn);
        lobbyLayout.addView(lobbyProgress);
        lobbyLayout.addView(lobbyStatusText);
        lobbyLayout.addView(roomScroll);
        lobbyLayout.addView(manualTitle);
        lobbyLayout.addView(manualRow);
        return lobbyLayout;
    }

    /** 统一构建程序化 Button：动态模块资源上下文必须显式关闭宿主主题 stateListAnimator。 */
    private Button buildButton(Context ctx, String text, float sizeSp) {
        Button button = new Button(ctx);
        button.setText(text);
        button.setTextSize(sizeSp);
        button.setStateListAnimator(null);
        return button;
    }

    private int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 建房：随机端口 + NSD 广播（失败仅告警），大厅展示本机 IP:PORT 等对手直连。 */
    private void hostRoom() {
        if (connecting || isPlaying || server != null) return;
        lobbyProgress.setVisibility(View.VISIBLE);
        lobbyStatusText.setText("正在创建房间…");
        server = new LanChessServer(requireContext());
        server.setOnClientConnectedListener(this::onOpponentConnected);
        new Thread(() -> {
            try {
                final int boundPort = server.start(ROOM_NAME_PREFIX + randomRoomSuffix());
                final String localIp = LanChessServer.getLocalIpv4();
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    lobbyProgress.setVisibility(View.GONE);
                    lobbyStatusText.setText("房间已创建："
                            + (localIp != null ? localIp + ":" + boundPort : "端口 " + boundPort)
                            + "\n等待对手加入…（对方可自动发现或直连）");
                });
            } catch (IOException e) {
                Log.w(TAG, "创建房间失败", e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    lobbyProgress.setVisibility(View.GONE);
                    lobbyStatusText.setText("");
                    Toast.makeText(requireContext(),
                            R.string.chess_online_create_failed, Toast.LENGTH_SHORT).show();
                    closeServerQuietly();
                });
            }
        }, "LanChessHostRoom").start();
    }

    private void onOpponentConnected(LanChessSession.Transport transport, String peerIp) {
        // accept 线程回调 → 切主线程
        mainHandler.post(() -> {
            if (!isAdded() || isPlaying) {
                transport.close(); // 页面已销毁或已进入其他对局：成对释放，防泄漏
                return;
            }
            server.stopAccepting(); // 已有对手：停 accept + 注销 NSD，连接交给会话管理
            startMatch(LanChessSession.Role.HOST, transport);
        });
    }

    /** NSD 发现列表（自动发现 + 手动直连共用）。 */
    private void startRoomDiscovery() {
        if (connecting || isPlaying) return;
        ensureClient();
        roomListLayout.removeAllViews();
        lobbyProgress.setVisibility(View.VISIBLE);
        lobbyStatusText.setText("正在搜索局域网房间…（搜不到可直接输入 IP）");
        client.startDiscovery(new LanChessClient.RoomListener() {
            @Override
            public void onRoomFound(String roomName, String host, int port) {
                // NsdManager 回调已在主线程，仍统一 post 保证视图操作收口一处。
                mainHandler.post(() -> addRoomToList(roomName, host, port));
            }

            @Override
            public void onDiscoveryStopped(String reason) {
                mainHandler.post(() -> {
                    if (isAdded()) lobbyProgress.setVisibility(View.GONE);
                });
            }

            @Override
            public void onDiscoveryStartFailed(String reason) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    lobbyProgress.setVisibility(View.GONE);
                    lobbyStatusText.setText("自动发现不可用（" + reason
                            + "），请让房主查看 IP 后直连");
                });
            }
        });
    }

    private void addRoomToList(String roomName, String host, int port) {
        if (!isAdded() || isPlaying) return;
        // 房主自己也会收到本机房间广播：跳过自己，避免连到自己。
        if (server != null && server.getPort() == port
                && host != null && host.equals(LanChessServer.getLocalIpv4())) {
            return;
        }
        Button roomBtn = buildButton(requireContext(),
                roomName + "\n" + host + ":" + port, 14);
        roomBtn.setPadding(12, 8, 12, 8);
        roomBtn.setOnClickListener(v -> connectToRoom(host, port));
        roomListLayout.addView(roomBtn);
        lobbyProgress.setVisibility(View.GONE);
        lobbyStatusText.setText("发现房间，点击加入（列表持续刷新…）");
    }

    private void connectFromManualInput() {
        String ip = ipInput.getText().toString().trim();
        String portText = portInput.getText().toString().trim();
        if (ip.isEmpty()) {
            Toast.makeText(requireContext(), "请输入房主 IP", Toast.LENGTH_SHORT).show();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "端口无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (port < 1 || port > 65535) {
            Toast.makeText(requireContext(), "端口须在 1~65535", Toast.LENGTH_SHORT).show();
            return;
        }
        connectToRoom(ip, port);
    }

    private void connectToRoom(String host, int port) {
        if (connecting || isPlaying || !isAdded()) return;
        connecting = true;
        lobbyProgress.setVisibility(View.VISIBLE);
        lobbyStatusText.setText("正在连接 " + host + ":" + port + "…");
        ensureClient();
        client.stopDiscovery();
        new Thread(() -> {
            try {
                LanChessSession.Transport transport =
                        client.connect(host, port, CONNECT_TIMEOUT_MS);
                mainHandler.post(() -> {
                    if (!isAdded()) {
                        transport.close(); // 页面已销毁：立即成对释放，防泄漏
                        return;
                    }
                    connecting = false;
                    startMatch(LanChessSession.Role.GUEST, transport);
                });
            } catch (IOException e) {
                Log.w(TAG, "直连失败 " + host + ":" + port, e);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    connecting = false;
                    lobbyProgress.setVisibility(View.GONE);
                    lobbyStatusText.setText("连接失败，请检查 IP/端口与同一 Wi-Fi");
                });
            }
        }, "LanChessConnect").start();
    }

    private void ensureClient() {
        if (client == null) {
            client = new LanChessClient(requireContext());
        }
    }

    private String randomRoomSuffix() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ROOM_SUFFIX_ALPHABET[random.nextInt(ROOM_SUFFIX_ALPHABET.length)]);
        }
        return sb.toString();
    }

    // ==================== 对局 ====================

    private View buildGameScreen(Context ctx) {
        gameLayout = new LinearLayout(ctx);
        gameLayout.setOrientation(LinearLayout.VERTICAL);
        gameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        gameLayout.setVisibility(View.GONE);

        turnStatusText = new TextView(ctx);
        turnStatusText.setTextSize(18);
        turnStatusText.setGravity(Gravity.CENTER);
        turnStatusText.setPadding(8, 12, 8, 4);

        winnerText = new TextView(ctx);
        winnerText.setTextSize(20);
        winnerText.setGravity(Gravity.CENTER);
        winnerText.setPadding(8, 4, 8, 4);

        boardView = new ChineseChessView(ctx);
        boardView.bindGame(game);
        boardView.setSimpleMode(ChineseChessUiPreferences.isSimpleMode(ctx));
        boardView.setLocked(true);
        boardView.setOnCellClickListener(this::onCellTap);
        boardView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        Button resignBtn = buildButton(ctx, "认输", 14);
        resignBtn.setOnClickListener(v -> confirmResign());
        Button leaveBtn = buildButton(ctx, getString(R.string.chess_online_leave), 14);
        leaveBtn.setOnClickListener(v -> leaveMatch());

        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(8, 4, 8, 8);
        buttonRow.addView(resignBtn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        buttonRow.addView(leaveBtn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        gameLayout.addView(turnStatusText);
        gameLayout.addView(winnerText);
        gameLayout.addView(boardView);
        gameLayout.addView(buttonRow);
        return gameLayout;
    }

    /** 建立对局：组装会话（规则校验注入 + 回调监听），握手后进入棋盘页。 */
    private void startMatch(LanChessSession.Role role, LanChessSession.Transport transport) {
        if (isPlaying) {
            transport.close(); // 防御：已有进行中对局，拒绝重复进入
            return;
        }
        if (session != null) {
            session.close(); // 理论不可达（结束局必经「返回」离开）：防御性收口旧会话
        }
        game.reset();
        matchResultShown = false;
        selectedX = -1;
        selectedY = -1;
        selectedMoves = null;

        session = new LanChessSession(role, transport, playerName(),
                this::isOpponentMoveLegal, sessionListener);
        session.start(); // GUEST 发送 HELLO；HOST 等待对端 HELLO 后回 WELCOME

        isPlaying = true;
        lobbyLayout.setVisibility(View.GONE);
        gameLayout.setVisibility(View.VISIBLE);
        boardView.bindGame(game);
        boardView.clearSelected();
        boardView.clearLastMove();
        boardView.setLocked(true);
        turnStatusText.setText("握手确认中…");
        winnerText.setText("");
    }

    private String playerName() {
        String model = Build.MODEL;
        return model == null || model.isEmpty() ? "GameMatrix玩家" : model;
    }

    /**
     * 会话在读线程上的对方着法预检。轮次排他保证：只有轮到对方时才会调用，
     * 主线程此时不会对 game 做写操作；主线程收到回调后仍会经 syncedGame 终检。
     */
    private boolean isOpponentMoveLegal(int fromX, int fromY, int toX, int toY) {
        return game.isMoveLegal(fromX, fromY, toX, toY);
    }

    private final LanChessSession.SessionListener sessionListener = new LanChessSession.SessionListener() {
        @Override
        public void onReady(boolean red, String opponentName) {
            mainHandler.post(() -> {
                if (!isAdded() || !isPlaying) return;
                iAmRed = red;
                updateTurnStatus("你执" + (red ? "红" : "黑")
                        + "，对手：" + opponentName);
            });
        }

        @Override
        public void onOpponentMove(int fromX, int fromY, int toX, int toY) {
            mainHandler.post(() -> applyOpponentMove(fromX, fromY, toX, toY));
        }

        @Override
        public void onOpponentResigned() {
            mainHandler.post(() -> showMatchResult(
                    getString(R.string.chess_online_you_win) + "（对方认输）"));
        }

        @Override
        public void onOpponentLeft() {
            mainHandler.post(() -> showMatchResult("对手已离开对局"));
        }

        @Override
        public void onDisconnected(String reason) {
            mainHandler.post(() -> showMatchResult("连接断开："
                    + (reason == null || reason.isEmpty() ? "未知原因" : reason)));
        }
    };

    private void updateTurnStatus(String prefix) {
        if (!isPlaying) return;
        if (game.isGameOver()) {
            turnStatusText.setText(R.string.chess_online_game_over);
            return;
        }
        boolean myTurn = session != null && session.isMyTurn();
        if (prefix != null && !prefix.isEmpty()) {
            turnStatusText.setText(prefix + " · " + (myTurn ? "轮到你走棋" : "等待对手…"));
        } else {
            turnStatusText.setText(myTurn ? "轮到你走棋" : "等待对手…");
        }
        boardView.setLocked(!myTurn);
    }

    /** 对方着法已过会话四重校验；主线程经重放副本终检后由集中闸门落子（双保险）。 */
    private void applyOpponentMove(int fromX, int fromY, int toX, int toY) {
        if (!isAdded() || !isPlaying || game.isGameOver()) return;
        // 联机消息一律按不可信输入处理：先在重放副本上过 commitMove 闸门终检，
        // 非法数据绝不能污染本地棋盘（与中继联机旧实现的防线语义一致）。
        ChineseChessGame syncedGame = game.deepCopy();
        if (syncedGame.commitMove(fromX, fromY, toX, toY) == null
                || game.commitMove(fromX, fromY, toX, toY) == null) {
            Log.e(TAG, "LAN_OPPONENT_MOVE_REJECTED " + fromX + "," + fromY
                    + "->" + toX + "," + toY + "，断开对局");
            if (session != null) session.close();
            showMatchResult("对方着法非法，对局已断开");
            return;
        }
        selectedX = -1;
        selectedY = -1;
        selectedMoves = null;
        boardView.clearSelected();
        boardView.setLastMove(fromX, fromY, toX, toY);
        boardView.invalidate();
        if (game.isGameOver()) {
            showMatchResult(localResultMessage());
            return;
        }
        updateTurnStatus(null);
    }

    private void onCellTap(int x, int y) {
        if (!isPlaying || game.isGameOver() || session == null || !session.isMyTurn()) return;
        ChineseChessGame.Side mySide = iAmRed
                ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
        if (game.getCurrentSide() != mySide) return;

        ChineseChessGame.Piece target = game.getBoard()[y][x];
        if (selectedX >= 0 && selectedMoves != null) {
            for (int[] move : selectedMoves) {
                if (move[0] == x && move[1] == y) {
                    performMyMove(selectedX, selectedY, x, y);
                    return;
                }
            }
        }
        if (target != null && target.side == mySide) {
            selectedX = x;
            selectedY = y;
            selectedMoves = game.getLegalMoves(x, y);
            boardView.setSelected(x, y, selectedMoves);
        } else {
            selectedX = -1;
            selectedY = -1;
            selectedMoves = null;
            boardView.clearSelected();
        }
    }

    /** 本方走子：会话完成轮次校验并发出 MOVE（返回 true 即本地确认），随后集中闸门落子。 */
    private void performMyMove(int fromX, int fromY, int toX, int toY) {
        if (!isPlaying || game.isGameOver() || session == null) return;
        selectedX = -1;
        selectedY = -1;
        selectedMoves = null;
        boardView.clearSelected();
        if (!session.sendMove(fromX, fromY, toX, toY)) return;

        // 集中闸门：着法来自 getLegalMoves（已合法），commitMove 再防御性把关。
        ChineseChessGame.MoveRecord record = game.commitMove(fromX, fromY, toX, toY);
        if (record == null) {
            // 理论不可达（会话轮次校验 + UI 合法着法集双保险）；按异常终止处理。
            Log.e(TAG, "LAN_LOCAL_MOVE_REJECTED " + fromX + "," + fromY
                    + "->" + toX + "," + toY);
            session.close();
            showMatchResult("本地着法校验失败，对局已终止");
            return;
        }
        boardView.setLastMove(fromX, fromY, toX, toY);
        boardView.invalidate();
        if (game.isGameOver()) {
            showMatchResult(localResultMessage());
            return;
        }
        updateTurnStatus(null);
    }

    private String localResultMessage() {
        ChineseChessGame.Side winner = game.getWinner();
        ChineseChessGame.Side mySide = iAmRed
                ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
        if (winner == null) return "和棋！";
        return winner == mySide
                ? getString(R.string.chess_online_you_win)
                : getString(R.string.chess_online_opponent_wins);
    }

    private void confirmResign() {
        if (!isPlaying || session == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("认输")
                .setMessage("确定向对方认输吗？")
                .setPositiveButton("认输", (dialog, which) -> {
                    if (session != null && session.sendResign()) {
                        showMatchResult("你已认输，" + getString(R.string.chess_online_opponent_wins));
                    }
                })
                .setNegativeButton("继续对局", null)
                .show();
    }

    /** 对局结束统一收口：终止本地交互 + 结果提示 + 返回（连接资源在 onDestroy 成对释放）。 */
    private void showMatchResult(String message) {
        if (!isAdded() || matchResultShown) return;
        matchResultShown = true;
        isPlaying = false;
        boardView.setLocked(true);
        turnStatusText.setText(R.string.chess_online_game_over);
        winnerText.setText(message);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.chess_online_game_over)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("返回", (dialog, which) -> leaveMatch())
                .show();
    }

    private void leaveMatch() {
        if (session != null) {
            session.sendLeave(); // 尽力通知对方后进入终态；其余释放由 onDestroy 兜底
        }
        getParentFragmentManager().popBackStack();
    }

    // ==================== 生命周期与释放 ====================

    private void closeServerQuietly() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Override
    public void onDestroy() {
        // 成对释放：会话（含传输连接）/ 建房监听 / 发现监听，全部幂等。
        if (session != null) {
            session.close();
            session = null;
        }
        closeServerQuietly();
        if (client != null) {
            client.close();
            client = null;
        }
        connecting = false;
        super.onDestroy();
    }
}

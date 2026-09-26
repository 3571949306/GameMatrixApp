package com.gamecenter.app.chinesechess;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 局域网加入端（Android 层薄封装，无业务规则）。
 *
 * <p>能力：NSD 房间发现（回调房间名+host+port）与手动 IP:PORT 直连。
 * NSD 不可用（无 NsdManager/组播被路由器屏蔽）时仅影响自动发现，
 * 直连模式始终可用。</p>
 *
 * <p>线程约定：NsdManager 回调默认经主线程 executor 派发；{@link #connect}
 * 是阻塞式 TCP 连接，调用方必须放在后台线程。</p>
 *
 * <p>生命周期：{@link #stopDiscovery}/{@link #close} 停止发现并注销监听，幂等成对。</p>
 */
public class LanChessClient {

    private static final String TAG = "LanChessClient";

    /** 房间发现回调（主线程触发，UI 可直接消费；仍建议统一走主 Handler）。 */
    public interface RoomListener {
        /** 发现一个房间（同一房间只会回调一次）。 */
        void onRoomFound(String roomName, String host, int port);

        /** 发现过程停止（主动停止或系统终止）。 */
        void onDiscoveryStopped(String reason);

        /** 发现启动失败（NSD 不可用等），应提示用户改用手动直连。 */
        void onDiscoveryStartFailed(String reason);
    }

    private final Context appContext;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private final Deque<NsdServiceInfo> pendingResolve = new ArrayDeque<>();
    private final Set<String> seenRooms = new HashSet<>();
    private volatile boolean discovering;
    private volatile boolean resolving;
    private volatile RoomListener roomListener;

    public LanChessClient(Context context) {
        appContext = context.getApplicationContext();
    }

    /** 开始 NSD 发现（重复调用自动忽略）。回调主线程派发。 */
    public void startDiscovery(RoomListener listener) {
        roomListener = listener;
        if (discovering) return;
        nsdManager = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            listener.onDiscoveryStartFailed("NsdManager 不可用");
            return;
        }
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                discovering = false;
                Log.w(TAG, "NSD 发现启动失败 errorCode=" + errorCode + "（可手动直连）");
                roomListener.onDiscoveryStartFailed("NSD 启动失败(" + errorCode + ")");
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                discovering = true;
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                // resolveService 同一时刻只允许一个：入队串行解析，避免内部错误丢房间。
                synchronized (pendingResolve) {
                    pendingResolve.addLast(serviceInfo);
                }
                pumpResolve();
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                // 房间下线不专门回调 UI：房间列表点击时会连接失败并提示。
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                discovering = false;
                Log.w(TAG, "NSD 停止发现失败 errorCode=" + errorCode);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                discovering = false;
                RoomListener l = roomListener;
                if (l != null) l.onDiscoveryStopped("已停止搜索");
            }
        };
        try {
            nsdManager.discoverServices(LanChessServer.NSD_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "NSD 发现异常（可手动直连）", e);
            discoveryListener = null;
            listener.onDiscoveryStartFailed("NSD 异常");
        }
    }

    /** 逐个解析已发现的房间（Android 的 resolveService 并发限制要求串行）。 */
    private void pumpResolve() {
        if (resolving) return;
        NsdServiceInfo info;
        synchronized (pendingResolve) {
            info = pendingResolve.pollFirst();
        }
        if (info == null) return;
        NsdManager manager = nsdManager;
        if (manager == null) return;
        resolving = true;
        try {
            manager.resolveService(info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "NSD 解析房间失败 errorCode=" + errorCode);
                    finishResolveAndPump();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    RoomListener l = roomListener;
                    if (l != null && serviceInfo.getHost() != null) {
                        String key = serviceInfo.getServiceName() + "@"
                                + serviceInfo.getHost().getHostAddress() + ":" + serviceInfo.getPort();
                        if (seenRooms.add(key)) {
                            l.onRoomFound(serviceInfo.getServiceName(),
                                    serviceInfo.getHost().getHostAddress(),
                                    serviceInfo.getPort());
                        }
                    }
                    finishResolveAndPump();
                }

                private void finishResolveAndPump() {
                    resolving = false;
                    pumpResolve();
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "NSD resolveService 异常", e);
            resolving = false;
        }
    }

    /** 停止发现（幂等）。进入对局或退出页面时调用，与 startDiscovery 成对。 */
    public synchronized void stopDiscovery() {
        NsdManager.DiscoveryListener listener = discoveryListener;
        discoveryListener = null;
        // 不依赖 discovering 标志：发现可能仍处于启动回调间隙，必须保证注销成对。
        if (listener == null || nsdManager == null) return;
        try {
            nsdManager.stopServiceDiscovery(listener);
        } catch (RuntimeException e) {
            Log.w(TAG, "NSD 停止发现异常", e);
        }
        discovering = false;
    }

    /**
     * 手动直连（阻塞式，调用方须放后台线程）。
     *
     * @return 已连接的 Transport 适配器（由会话负责后续关闭）
     * @throws IOException 连接失败/超时
     */
    public LanChessSession.Transport connect(String ip, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
        try {
            return new SocketTransport(socket, TAG);
        } catch (IOException e) {
            Log.w(TAG, "连接流打开失败 " + ip + ":" + port, e);
            closeQuietly(socket);
            throw e;
        }
    }

    /** 全量释放：等价 stopDiscovery（传输连接由会话生命周期管理）。幂等。 */
    public void close() {
        stopDiscovery();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(TAG, "关闭 Socket 失败", e);
        }
    }
}

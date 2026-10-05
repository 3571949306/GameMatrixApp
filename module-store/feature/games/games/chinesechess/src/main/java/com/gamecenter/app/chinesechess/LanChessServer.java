package com.gamecenter.app.chinesechess;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 局域网建房端（Android 层薄封装，无业务规则）。
 *
 * <p>生命周期：{@link #start} 打开 ServerSocket(0) 随机端口并注册 NSD 服务
 * （"_gamematrix_chess._tcp."），accept 线程等待首个对手接入；{@link #stopAccepting}
 * 停止等待（关 ServerSocket + 注销 NSD，保留已建连接）；{@link #close} 全量释放
 * （含活跃连接），全部幂等、成对释放，防泄漏。</p>
 *
 * <p>降级路径：NSD 组播在部分路由器/模拟器上不可用——注册失败仅 Log.w 不阻断，
 * 房主页会同时展示「本机 IP:端口」供对手手动直连。</p>
 *
 * <p>线程约定：accept 回调 {@link OnClientConnectedListener} 在 accept 线程触发，
 * UI 需自行切主线程。</p>
 */
public class LanChessServer {

    /** NSD 服务类型（Client 发现端必须使用同一常量）。 */
    public static final String NSD_SERVICE_TYPE = "_gamematrix_chess._tcp.";
    private static final String TAG = "LanChessServer";

    /** 首个客户端接入回调（accept 线程；第二个及以后的连接会被直接拒绝）。 */
    public interface OnClientConnectedListener {
        void onClientConnected(LanChessSession.Transport transport, String peerIp);
    }

    private final Context appContext;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private volatile boolean closed;
    private ServerSocket serverSocket;
    private int port = -1;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener nsdRegistration;
    private volatile boolean nsdRegistered;
    private volatile boolean nsdUnregisterRequested;
    private Thread acceptThread;
    private volatile SocketTransport activeTransport;
    private volatile OnClientConnectedListener clientListener;

    public LanChessServer(Context context) {
        appContext = context.getApplicationContext();
    }

    public void setOnClientConnectedListener(OnClientConnectedListener listener) {
        clientListener = listener;
    }

    public int getPort() {
        return port;
    }

    /**
     * 开始建房：绑定随机端口 + 注册 NSD + 启动 accept 线程。
     *
     * @param roomName NSD 展示名（如 "GameMatrix-象棋-XXXX"）
     * @return 实际监听端口
     * @throws IOException ServerSocket 绑定失败（内存不足/无可用端口等）
     */
    public int start(String roomName) throws IOException {
        if (closed) throw new IllegalStateException("server closed");
        if (listening.get()) return port;
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
        listening.set(true);
        registerNsd(roomName, port);
        acceptThread = new Thread(this::acceptLoop, "LanChessServerAccept");
        acceptThread.start();
        return port;
    }

    /** 已有对手接入后调用：不再接受新连接并注销 NSD 广播，但保留现有对局连接。 */
    public void stopAccepting() {
        if (!listening.compareAndSet(true, false)) {
            unregisterNsd();
            return;
        }
        closeServerSocket();
        unregisterNsd();
    }

    /** 全量释放：停止等待 + 关闭活跃连接。幂等，退出对局/onDestroy 路径必经。 */
    public void close() {
        if (closed) return;
        closed = true;
        stopAccepting();
        SocketTransport transport = activeTransport;
        if (transport != null) {
            transport.close("local_close");
        }
    }

    private void acceptLoop() {
        // 持本地引用：stopAccepting 会置空字段并关闭 Socket，避免循环内 NPE/二次关闭竞态。
        ServerSocket socket = serverSocket;
        while (listening.get() && !closed && socket != null) {
            try {
                Socket client = socket.accept();
                handleAccepted(client);
            } catch (IOException e) {
                if (!listening.get() || closed) break; // 正常关闭路径：ServerSocket 被主动关闭
                Log.w(TAG, "accept 异常，停止等待新连接", e);
                break;
            }
        }
    }

    private void handleAccepted(Socket socket) {
        if (activeTransport != null) {
            // 已有对手：拒绝第二个连接（单房间单对局），保持原有对局不受影响。
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "关闭多余连接失败", e);
            }
            return;
        }
        SocketTransport transport;
        try {
            transport = new SocketTransport(socket, TAG);
        } catch (IOException e) {
            Log.w(TAG, "接入连接流打开失败，关闭该连接", e);
            closeQuietly(socket);
            return;
        }
        activeTransport = transport;
        OnClientConnectedListener listener = clientListener;
        if (listener != null) {
            InetAddress address = socket.getInetAddress();
            listener.onClientConnected(transport,
                    address == null ? "unknown" : address.getHostAddress());
        }
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(TAG, "关闭 ServerSocket 失败", e);
        }
    }

    // ==================== NSD 注册/注销（失败仅告警，不阻断直连） ====================

    private void registerNsd(String roomName, int servicePort) {
        try {
            nsdManager = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) {
                Log.w(TAG, "NsdManager 不可用，跳过广播（直连模式仍可用）");
                return;
            }
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(roomName);
            info.setServiceType(NSD_SERVICE_TYPE);
            info.setPort(servicePort);
            nsdRegistration = new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                    nsdRegistered = true;
                    if (nsdUnregisterRequested) {
                        doUnregisterNsd();
                    }
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "NSD 注册失败 errorCode=" + errorCode + "（直连模式仍可用）");
                    nsdRegistration = null;
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                    nsdRegistered = false;
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "NSD 注销失败 errorCode=" + errorCode);
                }
            };
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdRegistration);
        } catch (RuntimeException e) {
            Log.w(TAG, "NSD 注册异常（直连模式仍可用）", e);
            nsdRegistration = null;
        }
    }

    private void unregisterNsd() {
        if (nsdRegistered) {
            doUnregisterNsd();
        } else {
            // 注册回调未到达：标记注销意图，onServiceRegistered 内补注销，防泄漏。
            nsdUnregisterRequested = true;
        }
    }

    private void doUnregisterNsd() {
        NsdManager.RegistrationListener registration = nsdRegistration;
        nsdRegistration = null;
        nsdUnregisterRequested = false;
        if (registration == null || nsdManager == null) return;
        try {
            nsdManager.unregisterService(registration);
        } catch (RuntimeException e) {
            Log.w(TAG, "NSD 注销异常", e);
        }
    }

    /** 枚举本机局域网 IPv4（优先站点本地地址），失败返回 null；建房页展示给对手直连用。 */
    public static String getLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            String fallback = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || !(address instanceof Inet4Address)) continue;
                    if (address.isSiteLocalAddress()) return address.getHostAddress();
                    if (fallback == null) fallback = address.getHostAddress();
                }
            }
            return fallback;
        } catch (SocketException e) {
            Log.w(TAG, "枚举本机网卡失败", e);
            return null;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(TAG, "关闭 Socket 失败", e);
        }
    }
}

/**
 * Socket → {@link LanChessSession.Transport} 适配器：UTF-8 行协议读写，
 * LanChessServer 与 LanChessClient 共用（同一文件内包私有，避免第四份拷贝）。
 *
 * <p>线程模型：send 可在任意线程调用（写入同步加锁）；setListener 启动独立守护读线程，
 * 读到 EOF/IO 异常即回调 onClosed 并关闭 Socket——close 幂等，onClosed 至多通知一次。</p>
 */
final class SocketTransport implements LanChessSession.Transport {

    private final Socket socket;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final String logTag;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile LanChessSession.TransportListener listener;

    SocketTransport(Socket socket, String logTag) throws IOException {
        this.socket = socket;
        this.logTag = logTag;
        writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void send(String line) {
        if (closed.get()) throw new IllegalStateException("transport closed");
        try {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            Log.w(logTag, "发送失败，关闭连接", e);
            close("send_failed");
            throw new IllegalStateException("send failed", e);
        }
    }

    @Override
    public void setListener(LanChessSession.TransportListener transportListener) {
        listener = transportListener;
        Thread readerThread = new Thread(this::readLoop, "LanChessSocketReader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                LanChessSession.TransportListener l = listener;
                if (l != null) l.onLine(line);
            }
            close("peer_closed"); // 对端正常关闭（读到 EOF）
        } catch (IOException e) {
            if (!closed.get()) Log.w(logTag, "读取失败", e);
            close("io_error");
        }
    }

    @Override
    public void close() {
        close("local_close");
    }

    void close(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(logTag, "关闭 Socket 失败", e);
        }
        LanChessSession.TransportListener l = listener;
        if (l != null) l.onClosed(reason);
    }
}

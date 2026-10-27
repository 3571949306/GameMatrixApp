package com.gamecenter.app.network.p2p.transport.tcp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.network.p2p.model.Candidate;
import com.gamecenter.app.network.p2p.model.CandidateType;
import com.gamecenter.app.network.p2p.utils.P2pCallbackDispatcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP 玩家服务端/客户端通道 (TCP Host-Server & TCP Simultaneous Open)。
 * <p>
 * 对应「一个玩家当服务器，一个玩家当用户主动出击」以及 RFC 5382「TCP 双向 SYN 同时打开 (Simultaneous Open)」：
 * <ol>
 *   <li><b>房主服务端模式 (Host as IPv6 / LAN / UPnP Server)</b>：
 *       房主开启 ServerSocket 监听本地与公网 IPv6，访客像访问 B 站一样主动建立 TCP 握手。</li>
 *   <li><b>TCP 状态化打洞模式 (TCP Simultaneous Open)</b>：
 *       双端同时通过 SO_REUSEADDR 绑定相同本地端口，向对方外网映射端口同时发起 connect()，
 *       促使两端 NAT 路由器记录出站 SYN 并放行回信，完成 TCP 三次握手。</li>
 * </ol>
 */
public class TcpPeerChannel {

    private static final String TAG = "TcpPeerChannel";

    public interface TcpListener {
        /** TCP 链路完全打通就绪 */
        void onTcpConnected(@NonNull Socket socket, boolean isServerRole);

        /** 收到对方经 TCP 可靠通道发送的完整报文 */
        void onTcpMessageReceived(@NonNull byte[] message);

        /** TCP 发生错误或异常断开 */
        void onTcpDisconnected(@NonNull String reason);
    }

    private final boolean isHost;
    private final int localPort;
    private final TcpListener listener;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private Socket activeSocket;
    private DataOutputStream dataOut;
    private DataInputStream dataIn;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    public TcpPeerChannel(boolean isHost, int localPort, @NonNull TcpListener listener) {
        this.isHost = isHost;
        this.localPort = localPort;
        this.listener = listener;
    }

    /**
     * 作为服务端（房主）启动监听，准备迎接访客的主动连接。
     */
    public synchronized void startServer() {
        if (isRunning.getAndSet(true)) return;

        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                // 绑定到 0.0.0.0 / ::，同时支持 IPv4 和公网 IPv6
                serverSocket.bind(new InetSocketAddress(localPort));
                Log.i(TAG, "TCP Server listening on port " + localPort + " (Waiting for Client to connect...)");

                while (isRunning.get() && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    Log.i(TAG, "Accepted TCP connection from: " + client.getRemoteSocketAddress());
                    if (onSocketEstablished(client, true)) {
                        break;
                    }
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    Log.w(TAG, "TCP Server error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 作为客户端（访客）主动连接房主服务端。
     * 支持尝试候选列表中的 IPv6、局域网 IP 与公网 IP。
     *
     * @param candidates 房主的候选端点列表
     * @param timeoutMs  单端点连接超时 (推荐 1500ms)
     */
    public synchronized void connectAsClient(@NonNull List<Candidate> candidates, int timeoutMs) {
        if (isRunning.getAndSet(true)) return;

        executor.execute(() -> {
            for (Candidate c : candidates) {
                if (!isRunning.get() || isConnected.get()) break;
                if (c.getType() == CandidateType.RELAY) continue;

                Log.i(TAG, "Client actively connecting to Server: " + c.getIp() + ":" + c.getPort());
                Socket socket = new Socket();
                try {
                    socket.setReuseAddress(true);
                    socket.setTcpNoDelay(true); // 禁用 Nagle 算法，实现极低延迟落子
                    socket.setKeepAlive(true);

                    socket.connect(new InetSocketAddress(c.getIp(), c.getPort()), timeoutMs);

                    if (onSocketEstablished(socket, false)) {
                        return;
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Connect to " + c.getIp() + ":" + c.getPort() + " failed: " + e.getMessage());
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (!isConnected.get() && isRunning.get()) {
                P2pCallbackDispatcher.dispatch(() -> listener.onTcpDisconnected("All TCP candidate endpoints unreachable"));
            }
        });
    }

    /**
     * 执行 TCP Simultaneous Open (TCP 双向 SYN 打洞)。
     * 双端同时在相同的 localPort 上绑定，并在协同约定的同一毫秒向对方发起 connect()。
     */
    public synchronized void startSimultaneousOpen(@NonNull String remoteIp, int remotePort, int timeoutMs) {
        if (isRunning.getAndSet(true)) return;

        executor.execute(() -> {
            Log.i(TAG, "Initiating TCP Simultaneous Open to " + remoteIp + ":" + remotePort + " from localPort=" + localPort);
            Socket socket = new Socket();
            try {
                socket.setReuseAddress(true);
                socket.setTcpNoDelay(true);
                socket.bind(new InetSocketAddress(localPort));

                socket.connect(new InetSocketAddress(remoteIp, remotePort), timeoutMs);
                onSocketEstablished(socket, isHost);
            } catch (IOException e) {
                Log.w(TAG, "TCP Simultaneous Open failed: " + e.getMessage());
                if (!isConnected.get() && isRunning.get()) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onTcpDisconnected("TCP Simultaneous Open failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * 发送可靠 TCP 报文（自动带 4 字节大端长度前缀，防止粘包/半包）。
     */
    public boolean sendReliable(@NonNull byte[] data) {
        DataOutputStream out = dataOut;
        if (out == null || !isConnected.get()) return false;

        try {
            synchronized (out) {
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed sending TCP packet: " + e.getMessage());
            close();
            return false;
        }
    }

    public synchronized void close() {
        isRunning.set(false);
        isConnected.set(false);
        try {
            if (activeSocket != null) activeSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
    }

    private synchronized boolean onSocketEstablished(Socket socket, boolean isServerRole) {
        if (isConnected.getAndSet(true)) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            return false;
        }

        try {
            socket.setTcpNoDelay(true);
            activeSocket = socket;
            dataOut = new DataOutputStream(socket.getOutputStream());
            dataIn = new DataInputStream(socket.getInputStream());

            P2pCallbackDispatcher.dispatch(() -> listener.onTcpConnected(socket, isServerRole));

            // 启动数据接收线程（处理粘包与分包）
            executor.execute(this::readLoop);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize streams on established socket: " + e.getMessage());
            return false;
        }
    }

    private void readLoop() {
        try {
            while (isRunning.get() && !activeSocket.isClosed()) {
                int length = dataIn.readInt();
                if (length < 0 || length > 1024 * 1024) { // 安全保护：单包最大 1MB
                    throw new IOException("Invalid packet length: " + length);
                }

                byte[] buffer = new byte[length];
                dataIn.readFully(buffer);

                P2pCallbackDispatcher.dispatch(() -> listener.onTcpMessageReceived(buffer));
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.i(TAG, "TCP connection closed: " + e.getMessage());
                P2pCallbackDispatcher.dispatch(() -> listener.onTcpDisconnected("TCP stream closed: " + e.getMessage()));
            }
        } finally {
            close();
        }
    }
}

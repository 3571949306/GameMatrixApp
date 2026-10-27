package com.gamecenter.app.network.p2p;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.network.RemoteP2PUtil;
import com.gamecenter.app.network.p2p.model.Candidate;
import com.gamecenter.app.network.p2p.model.CandidateType;
import com.gamecenter.app.network.p2p.model.NatType;
import com.gamecenter.app.network.p2p.model.P2pConnectionState;
import com.gamecenter.app.network.p2p.nat.CandidateGatherer;
import com.gamecenter.app.network.p2p.punch.HolePuncher;
import com.gamecenter.app.network.p2p.transport.quic.QuicKcpChannel;
import com.gamecenter.app.network.p2p.transport.tcp.TcpPeerChannel;
import com.gamecenter.app.network.p2p.utils.P2pCallbackDispatcher;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P2P 房间会话控制器 (Room Session Controller)。
 * <p>
 * 封装完整的房间连接生命周期：
 * <ul>
 *   <li>房主 (Host) / 访客 (Client) 角色双态切换</li>
 *   <li>本地 NAT 探测与 IPv6/IPv4 候选收集 (Candidate Gathering)</li>
 *   <li>并发穿透与 QUIC / TCP / UDP 打洞多路并行竞速</li>
 *   <li>提供透明的 sendMoveData(byte[]) 游戏接口</li>
 * </ul>
 */
public class P2pRoomSession {

    private static final String TAG = "P2pRoomSession";

    public enum TransportMode {
        AUTO_RACING, // 自动竞速选路 (QUIC / UDP / TCP / IPv6 并发谁快选谁)
        QUIC_ONLY,   // 强制类 QUIC 模式
        TCP_ONLY     // 强制 TCP Server-Client 模式
    }

    private final String roomCode;
    private final boolean isHost;
    private final int sessionHash;
    private final TransportMode transportMode;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private P2pConnectionListener listener;
    private P2pConnectionState currentState = P2pConnectionState.IDLE;

    private DatagramSocket udpSocket;
    private List<Candidate> localCandidates = new ArrayList<>();
    private NatType localNatType = NatType.UNKNOWN;

    // 传输通道实例
    private HolePuncher holePuncher;
    private QuicKcpChannel quicChannel;
    private TcpPeerChannel tcpChannel;

    private CandidateType activePath = null;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    public P2pRoomSession(@NonNull String rawRoomCode, boolean isHost) {
        this(rawRoomCode, isHost, TransportMode.AUTO_RACING);
    }

    public P2pRoomSession(@NonNull String rawRoomCode, boolean isHost, TransportMode mode) {
        this.roomCode = RemoteP2PUtil.normalizeRoomCode(rawRoomCode);
        this.isHost = isHost;
        this.sessionHash = roomCode.hashCode();
        this.transportMode = mode != null ? mode : TransportMode.AUTO_RACING;
        try {
            this.udpSocket = new DatagramSocket(0);
        } catch (Exception ignored) {
        }
    }

    public int getLocalPort() {
        return udpSocket != null ? udpSocket.getLocalPort() : 0;
    }

    public void setListener(@Nullable P2pConnectionListener listener) {
        this.listener = listener;
    }

    @NonNull
    public String getRoomCode() {
        return roomCode;
    }

    public boolean isHost() {
        return isHost;
    }

    @NonNull
    public P2pConnectionState getState() {
        return currentState;
    }

    /**
     * 第一阶段：启动网络侦测与候选端点收集 (Gathering)。
     * 会在后台线程执行 STUN 与 IPv6/IPv4 收集，完成后触发 onCandidatesGathered 回调。
     */
    public void startGathering(@NonNull OnCandidatesGatheredCallback callback) {
        updateState(P2pConnectionState.GATHERING);

        executor.execute(() -> {
            try {
                CandidateGatherer.GatherResult result = CandidateGatherer.gather(udpSocket);
                this.udpSocket = result.getSocket();
                this.localCandidates = result.getCandidates();
                this.localNatType = result.getNatType();

                Log.i(TAG, "Gathering complete: " + localCandidates.size() + " candidates, NatType=" + localNatType);

                P2pCallbackDispatcher.dispatch(() -> callback.onGathered(localCandidates, localNatType));
            } catch (Exception e) {
                Log.e(TAG, "Gathering failed: " + e.getMessage());
                updateState(P2pConnectionState.FAILED);
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onError(1001, "Candidate gathering failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * 第二阶段：接收到对端的候选地址列表后，启动并发打洞与连接竞速。
     *
     * @param remoteCandidates 从信令服务器获取到的对端候选端点
     */
    public void startConnectingWithCandidates(@NonNull List<Candidate> remoteCandidates) {
        updateState(P2pConnectionState.PUNCHING);

        int localPort = udpSocket != null ? udpSocket.getLocalPort() : 18888;

        // 根据模式选择启动通道
        if (transportMode == TransportMode.TCP_ONLY) {
            startTcpFlow(remoteCandidates, localPort);
        } else if (transportMode == TransportMode.QUIC_ONLY) {
            startQuicFlow(remoteCandidates);
        } else {
            // AUTO_RACING 竞速：同时启动 QUIC 与 UDP 刺穿打洞
            startUdpHolePunching(remoteCandidates);
            startQuicFlow(remoteCandidates);
        }
    }

    private void startTcpFlow(List<Candidate> remoteCandidates, int localPort) {
        tcpChannel = new TcpPeerChannel(isHost, localPort, new TcpPeerChannel.TcpListener() {
            @Override
            public void onTcpConnected(@NonNull Socket socket, boolean isServerRole) {
                if (isConnected.getAndSet(true)) return;
                activePath = CandidateType.HOST_IPV4;
                updateState(P2pConnectionState.CONNECTED_P2P);
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onConnected(activePath, 15));
                }
            }

            @Override
            public void onTcpMessageReceived(@NonNull byte[] message) {
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onDataReceived(message));
                }
            }

            @Override
            public void onTcpDisconnected(@NonNull String reason) {
                Log.w(TAG, "TCP Disconnected: " + reason);
                handleConnectionFailure(reason);
            }
        });

        if (isHost) {
            tcpChannel.startServer();
        } else {
            tcpChannel.connectAsClient(remoteCandidates, 2000);
        }
    }

    private void startQuicFlow(List<Candidate> remoteCandidates) {
        if (udpSocket == null) return;

        quicChannel = new QuicKcpChannel(isHost, udpSocket, new QuicKcpChannel.QuicListener() {
            @Override
            public void onQuicConnected(long cid, @NonNull InetAddress remoteAddress, int remotePort, long rttMs) {
                if (isConnected.getAndSet(true)) return;
                activePath = remoteAddress.getHostAddress().contains(":") ? CandidateType.HOST_IPV6 : CandidateType.REFLEXIVE_IPV4;
                updateState(P2pConnectionState.CONNECTED_P2P);
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onConnected(activePath, rttMs));
                }
            }

            @Override
            public void onStreamDataReceived(int streamId, @NonNull byte[] payload) {
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onDataReceived(payload));
                }
            }

            @Override
            public void onConnectionMigrated(@NonNull InetAddress newAddress, int newPort) {
                Log.i(TAG, "QUIC Path migrated to: " + newAddress + ":" + newPort);
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onPathMigrated(activePath, 20));
                }
            }

            @Override
            public void onQuicClosed(@NonNull String reason) {
                handleConnectionFailure(reason);
            }
        });

        if (isHost) {
            quicChannel.startServer();
        } else {
            // 客户端主动寻找最佳远端候选（优先 IPv6 / LAN IP）
            Candidate target = null;
            for (Candidate c : remoteCandidates) {
                if (c.getType() == CandidateType.HOST_IPV6 || c.getType() == CandidateType.HOST_IPV4) {
                    target = c;
                    break;
                }
            }
            if (target == null && !remoteCandidates.isEmpty()) {
                target = remoteCandidates.get(0);
            }

            if (target != null) {
                try {
                    quicChannel.connectAsClient(InetAddress.getByName(target.getIp()), target.getPort());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initiate QUIC connect: " + e.getMessage());
                }
            }
        }
    }

    private void startUdpHolePunching(List<Candidate> remoteCandidates) {
        if (udpSocket == null) return;

        holePuncher = new HolePuncher(udpSocket, sessionHash, remoteCandidates, new HolePuncher.PunchCallback() {
            @Override
            public void onPunchSuccess(@NonNull Candidate candidate, @NonNull InetAddress targetAddress, int targetPort, long rttMs) {
                if (isConnected.getAndSet(true)) return;
                activePath = candidate.getType();
                updateState(P2pConnectionState.CONNECTED_P2P);
                Log.i(TAG, "UDP Punch Succeeded via path: " + activePath + " RTT=" + rttMs + "ms");
                if (listener != null) {
                    P2pCallbackDispatcher.dispatch(() -> listener.onConnected(activePath, rttMs));
                }
            }

            @Override
            public void onPunchFailed(@NonNull String reason) {
                Log.w(TAG, "UDP Punch Failed: " + reason);
                if (!isConnected.get()) {
                    handleConnectionFailure("UDP Punch failed: " + reason);
                }
            }
        });
        holePuncher.start(2500);
    }

    /**
     * 发送可靠对战数据包（落子坐标等）。
     */
    public boolean sendData(@NonNull byte[] data) {
        if (quicChannel != null && isConnected.get()) {
            return quicChannel.sendStreamData(0, data);
        }
        if (tcpChannel != null && isConnected.get()) {
            return tcpChannel.sendReliable(data);
        }
        return false;
    }

    public synchronized void close() {
        updateState(P2pConnectionState.CLOSED);
        isConnected.set(false);
        if (holePuncher != null) holePuncher.stop();
        if (quicChannel != null) quicChannel.close();
        if (tcpChannel != null) tcpChannel.close();
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        executor.shutdownNow();
    }

    private void updateState(P2pConnectionState newState) {
        this.currentState = newState;
        if (listener != null) {
            P2pCallbackDispatcher.dispatch(() -> listener.onStateChanged(newState));
        }
    }

    private void handleConnectionFailure(String reason) {
        if (isConnected.get()) return;
        updateState(P2pConnectionState.FAILED);
        if (listener != null) {
            P2pCallbackDispatcher.dispatch(() -> listener.onError(1002, reason));
        }
    }

    public interface OnCandidatesGatheredCallback {
        void onGathered(@NonNull List<Candidate> candidates, @NonNull NatType natType);
    }
}

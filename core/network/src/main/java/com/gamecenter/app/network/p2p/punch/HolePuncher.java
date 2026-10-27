package com.gamecenter.app.network.p2p.punch;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.network.p2p.model.Candidate;
import com.gamecenter.app.network.p2p.model.CandidateType;
import com.gamecenter.app.network.p2p.model.NatType;
import com.gamecenter.app.network.p2p.utils.P2pCallbackDispatcher;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 核心打洞穿透协调器 (Hole Puncher & Knocking Coordinator)。
 * <p>
 * 整合双向协同刺穿、IPv6 光猫状态防火墙穿透、Symmetric NAT 端口预测突发与多路并发竞速：
 * <ol>
 *   <li><b>IPv6 状态防火墙刺穿</b>：主动向对端 IPv6 发包，瞬间在光猫/路由器生成出站状态白名单</li>
 *   <li><b>局域网零延迟直通</b>：同 WiFi 下内网包优先抢占通道 (RTT < 2ms)</li>
 *   <li><b>对称型 NAT 预测突发</b>：对移动 4G/5G 等 NAT444 节点执行动态步长端口区间连环打击</li>
 *   <li><b>竞速绑定与锁存</b>：一旦首个 PONG 回包响应，立即锁存该链路为工作通道并完成 BIND_ACK</li>
 * </ol>
 */
public class HolePuncher {

    private static final String TAG = "HolePuncher";

    public interface PunchCallback {
        /** 打洞成功，返回选中的最佳候选通道、最终打通的目标端点与初始 RTT */
        void onPunchSuccess(@NonNull Candidate candidate, @NonNull InetAddress targetAddress, int targetPort, long rttMs);

        /** 所有候选路径打洞均超时或失败，需降级至中继 */
        void onPunchFailed(@NonNull String reason);
    }

    private final DatagramSocket socket;
    private final int sessionHash;
    private final List<Candidate> remoteCandidates;
    private final PunchCallback callback;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isSuccess = new AtomicBoolean(false);
    private final AtomicInteger sequenceGenerator = new AtomicInteger(1);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Thread receiveThread;

    public HolePuncher(@NonNull DatagramSocket socket,
                       int sessionHash,
                       @NonNull List<Candidate> remoteCandidates,
                       @NonNull PunchCallback callback) {
        this.socket = socket;
        this.sessionHash = sessionHash;
        this.remoteCandidates = new ArrayList<>(remoteCandidates);
        this.callback = callback;
    }

    /**
     * 启动打洞流程。
     *
     * @param timeoutMs 总打洞允许最大超时时间 (通常推荐 2500 ~ 3000ms)
     */
    public synchronized void start(int timeoutMs) {
        if (isRunning.getAndSet(true)) return;

        Log.i(TAG, "Starting hole punching with " + remoteCandidates.size() + " remote candidates, sessionHash=" + sessionHash);

        // 1. 启动监听线程接收 PING / PONG / BIND_ACK
        startReceiveLoop();

        // 2. 启动打洞并发发包任务
        executor.execute(() -> executePunchKnocks());

        // 3. 设定超时监控定时器
        executor.execute(() -> {
            try {
                Thread.sleep(timeoutMs);
                if (isRunning.get() && !isSuccess.get()) {
                    Log.w(TAG, "Hole punching timeout after " + timeoutMs + "ms");
                    stop();
                    P2pCallbackDispatcher.dispatch(() -> callback.onPunchFailed("Timeout after " + timeoutMs + "ms"));
                }
            } catch (InterruptedException ignored) {
            }
        });
    }

    /**
     * 停止打洞器。
     */
    public synchronized void stop() {
        isRunning.set(false);
        executor.shutdownNow();
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
    }

    private void startReceiveLoop() {
        receiveThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    socket.receive(packet);
                    KnockPacket kp = KnockPacket.decode(packet.getData(), packet.getLength());
                    if (kp == null) continue;

                    // 校验会话哈希，杜绝串网
                    if (kp.getSessionHash() != sessionHash) continue;

                    handleIncomingKnockPacket(kp, packet.getAddress(), packet.getPort());

                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    if (!isRunning.get()) break;
                    Log.w(TAG, "Socket receive error: " + e.getMessage());
                }
            }
        }, "HolePuncher-Receiver");
        receiveThread.start();
    }

    private void handleIncomingKnockPacket(KnockPacket kp, InetAddress remoteAddr, int remotePort) {
        long now = System.currentTimeMillis();

        if (kp.getType() == KnockPacket.TYPE_KNOCK_PING) {
            // 收到对方发来的穿透探测包！说明对方打通了我们的入站状态表
            // 立即向对方实际来源 IP 和端口回复 PONG
            sendKnockPacket(KnockPacket.TYPE_KNOCK_PONG, kp.getSequence(), kp.getTimestamp(), remoteAddr, remotePort);

        } else if (kp.getType() == KnockPacket.TYPE_KNOCK_PONG) {
            // 收到对方对我方探测包的回应！说明双向连通完全确立
            long rtt = Math.max(1, now - kp.getTimestamp());
            Log.i(TAG, "Received KNOCK_PONG from " + remoteAddr + ":" + remotePort + " with RTT=" + rtt + "ms");

            // 回复 BIND_ACK 确认锁存
            sendKnockPacket(KnockPacket.TYPE_KNOCK_BIND_ACK, kp.getSequence(), now, remoteAddr, remotePort);

            if (!isSuccess.getAndSet(true)) {
                // 寻找匹配的候选类型
                Candidate matchedCandidate = findMatchedCandidate(remoteAddr.getHostAddress(), remotePort);
                P2pCallbackDispatcher.dispatch(() -> callback.onPunchSuccess(matchedCandidate, remoteAddr, remotePort, rtt));
            }

        } else if (kp.getType() == KnockPacket.TYPE_KNOCK_BIND_ACK) {
            // 对方收到了 PONG 并确立了通道
            Log.i(TAG, "Received KNOCK_BIND_ACK from " + remoteAddr + ":" + remotePort);
            if (!isSuccess.getAndSet(true)) {
                Candidate matchedCandidate = findMatchedCandidate(remoteAddr.getHostAddress(), remotePort);
                P2pCallbackDispatcher.dispatch(() -> callback.onPunchSuccess(matchedCandidate, remoteAddr, remotePort, 20));
            }
        }
    }

    private void executePunchKnocks() {
        // 按优先级降序排序候选地址 (IPv6 > LAN IPv4 > UPnP > STUN IPv4)
        Collections.sort(remoteCandidates);

        // 连续发送 3 轮突发探测，每轮间隔 60ms
        for (int round = 0; round < 3 && isRunning.get() && !isSuccess.get(); round++) {
            for (Candidate c : remoteCandidates) {
                if (c.getType() == CandidateType.RELAY) continue;

                try {
                    InetAddress targetAddr = InetAddress.getByName(c.getIp());

                    if (c.getNatType() == NatType.SYMMETRIC) {
                        // 目标端是对称型 NAT：执行端口增量预测连发突发
                        List<Integer> predictedPorts = PortPredictor.predictTargetPorts(
                                Collections.singletonList(c.getPort()), 12);
                        for (int port : predictedPorts) {
                            sendKnockPacket(KnockPacket.TYPE_KNOCK_PING, sequenceGenerator.getAndIncrement(),
                                    System.currentTimeMillis(), targetAddr, port);
                            Thread.sleep(2); // 微突发间隔
                        }
                    } else {
                        // 锥型 NAT 或 IPv6：直接突发 3 个 Ping 包刺穿防火墙
                        for (int i = 0; i < 3; i++) {
                            sendKnockPacket(KnockPacket.TYPE_KNOCK_PING, sequenceGenerator.getAndIncrement(),
                                    System.currentTimeMillis(), targetAddr, c.getPort());
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed knocking candidate " + c + ": " + e.getMessage());
                }
            }

            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void sendKnockPacket(byte type, int seq, long timestamp, InetAddress addr, int port) {
        try {
            KnockPacket packet = new KnockPacket(type, sessionHash, seq, timestamp, null);
            byte[] data = packet.encode();
            DatagramPacket dp = new DatagramPacket(data, data.length, addr, port);
            socket.send(dp);
        } catch (IOException e) {
            Log.d(TAG, "Error sending knock packet: " + e.getMessage());
        }
    }

    @NonNull
    private Candidate findMatchedCandidate(String ip, int port) {
        for (Candidate c : remoteCandidates) {
            if (c.getIp().equalsIgnoreCase(ip) && c.getPort() == port) {
                return c;
            }
        }
        // 若端口经 NAT 改变（Symmetric NAT 端口预测命中），以 IP 匹配
        for (Candidate c : remoteCandidates) {
            if (c.getIp().equalsIgnoreCase(ip)) {
                return new Candidate(c.getType(), ip, port, c.getPriority(), c.getNatType(), "symmetric-predicted");
            }
        }
        // 兜底推测
        CandidateType type = ip.contains(":") ? CandidateType.HOST_IPV6 : CandidateType.REFLEXIVE_IPV4;
        return new Candidate(type, ip, port, type.getDefaultPriority(), NatType.UNKNOWN, "discovered");
    }
}

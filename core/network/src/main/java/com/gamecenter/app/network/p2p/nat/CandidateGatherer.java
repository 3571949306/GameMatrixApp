package com.gamecenter.app.network.p2p.nat;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.network.p2p.model.Candidate;
import com.gamecenter.app.network.p2p.model.CandidateType;
import com.gamecenter.app.network.p2p.model.NatType;
import com.gamecenter.app.network.p2p.stun.StunAttribute;
import com.gamecenter.app.network.p2p.stun.StunClient;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 候选端点收集器 (Candidate Gatherer)。
 * <p>
 * 负责并发探查本机网络环境并生成有序候选地址集：
 * <ol>
 *   <li>公网全局单播 IPv6 (最高穿透优先级，规避所有 IPv4 CGNAT)</li>
 *   <li>局域网 IPv4 (支持同局域网/同 WiFi 零延迟直连)</li>
 *   <li>STUN 反射公网 IPv4 (针对各类锥型 NAT 打洞)</li>
 *   <li>UPnP 路由器映射端口</li>
 * </ol>
 */
public class CandidateGatherer {

    private static final String TAG = "CandidateGatherer";

    public static class GatherResult {
        private final List<Candidate> candidates;
        private final NatType natType;
        @Nullable
        private final DatagramSocket socket;

        public GatherResult(@NonNull List<Candidate> candidates, @NonNull NatType natType, @Nullable DatagramSocket socket) {
            this.candidates = candidates;
            this.natType = natType;
            this.socket = socket;
        }

        @NonNull
        public List<Candidate> getCandidates() {
            return candidates;
        }

        @NonNull
        public NatType getNatType() {
            return natType;
        }

        @Nullable
        public DatagramSocket getSocket() {
            return socket;
        }
    }

    /**
     * 执行完整的候选收集与 NAT 探测（阻塞式执行，需在工作线程调用）。
     *
     * @param existingSocket 可复用的本地 Socket，若为 null 则自动创建并绑定随机可用端口
     * @return 包含所有可用 Candidate 和 NAT 类型的完整结果
     */
    @NonNull
    public static GatherResult gather(@Nullable DatagramSocket existingSocket) {
        DatagramSocket socket = existingSocket;
        if (socket == null) {
            try {
                socket = new DatagramSocket(0); // 绑定随机可用端口
            } catch (Exception e) {
                Log.e(TAG, "Failed to create DatagramSocket: " + e.getMessage());
                return new GatherResult(Collections.emptyList(), NatType.UNKNOWN, null);
            }
        }

        int localPort = socket.getLocalPort();
        List<Candidate> candidateList = new ArrayList<>();

        // 1. 探测 NAT 类型与 STUN 反射 IPv4
        NatType natType = NatDetector.detect(socket);
        StunAttribute.StunAddress reflexiveAddr = StunClient.queryReflexiveAddress(socket);

        if (reflexiveAddr != null) {
            candidateList.add(new Candidate(
                    CandidateType.REFLEXIVE_IPV4,
                    reflexiveAddr.getIp(),
                    reflexiveAddr.getPort(),
                    CandidateType.REFLEXIVE_IPV4.getDefaultPriority(),
                    natType,
                    "stun"
            ));
        }

        // 2. 收集物理网卡 IP (包括公网 IPv6 与局域网 IPv4)
        gatherLocalInterfaceCandidates(localPort, natType, candidateList);

        // 3. 尝试探测 UPnP 映射
        int upnpPort = UpnpPortMapper.tryRequestPortMapping(localPort);
        if (upnpPort > 0 && reflexiveAddr != null) {
            candidateList.add(new Candidate(
                    CandidateType.UPNP_MAPPED,
                    reflexiveAddr.getIp(),
                    upnpPort,
                    CandidateType.UPNP_MAPPED.getDefaultPriority(),
                    natType,
                    "upnp"
            ));
        }

        // 按优先级排序 (IPv6 > LAN IPv4 > UPnP > STUN IPv4)
        Collections.sort(candidateList);

        Log.i(TAG, "Gathered " + candidateList.size() + " candidates, NatType: " + natType);
        for (Candidate c : candidateList) {
            Log.d(TAG, " -> " + c);
        }

        return new GatherResult(candidateList, natType, socket);
    }

    private static void gatherLocalInterfaceCandidates(int localPort, NatType natType, List<Candidate> candidates) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // 忽略回环网卡和未启用的虚拟网卡
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet6Address) {
                        Inet6Address v6 = (Inet6Address) addr;
                        String sanitizedIp = Ipv6AddressSanitizer.sanitize(v6);
                        if (sanitizedIp != null) {
                            int priority = Ipv6AddressSanitizer.calculatePriorityScore(sanitizedIp);
                            candidates.add(new Candidate(
                                    CandidateType.HOST_IPV6,
                                    sanitizedIp,
                                    localPort,
                                    priority,
                                    natType,
                                    iface.getName()
                            ));
                        }
                    } else if (addr instanceof Inet4Address) {
                        Inet4Address v4 = (Inet4Address) addr;
                        if (!v4.isLoopbackAddress()) {
                            candidates.add(new Candidate(
                                    CandidateType.HOST_IPV4,
                                    v4.getHostAddress(),
                                    localPort,
                                    CandidateType.HOST_IPV4.getDefaultPriority(),
                                    natType,
                                    iface.getName()
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to enumerate network interfaces: " + e.getMessage());
        }
    }
}

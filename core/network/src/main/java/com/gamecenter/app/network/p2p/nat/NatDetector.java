package com.gamecenter.app.network.p2p.nat;

import android.util.Log;

import androidx.annotation.NonNull;

import com.gamecenter.app.network.p2p.model.NatType;
import com.gamecenter.app.network.p2p.stun.StunAttribute;
import com.gamecenter.app.network.p2p.stun.StunClient;
import com.gamecenter.app.network.p2p.stun.StunMessage;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * NAT 行为特征判定器。
 * <p>
 * 结合 RFC 3489 标准状态机与现代化多 STUN 节点启发式比对（Heuristic Dual-Server Mapping Check），
 * 精准检测当前网络是 Full Cone (NAT1)、Port-Restricted Cone (NAT3)、还是 Symmetric (NAT4)。
 */
public class NatDetector {

    private static final String TAG = "NatDetector";

    /**
     * 探测当前套接字所在网络的 NAT 类型。
     *
     * @param socket 已绑定的本地 UDP 套接字
     * @return 确诊的 NAT 类型
     */
    @NonNull
    public static NatType detect(@NonNull DatagramSocket socket) {
        String server1 = StunClient.DEFAULT_DOMESTIC_STUN_SERVERS[0]; // stun.qq.com
        String server2 = StunClient.DEFAULT_DOMESTIC_STUN_SERVERS[1]; // stun.miwifi.com

        try {
            // Test 1: 向 Server 1 发送标准 Binding Request
            StunMessage req1 = StunMessage.createBindingRequest();
            StunMessage res1 = StunClient.sendRequest(socket, server1, StunClient.DEFAULT_STUN_PORT, req1, 1000);
            if (res1 == null) {
                // 重试备用服务器
                res1 = StunClient.sendRequest(socket, server2, StunClient.DEFAULT_STUN_PORT, req1, 1000);
                if (res1 == null) {
                    Log.w(TAG, "STUN Test 1 failed, UDP might be blocked");
                    return NatType.UDP_BLOCKED;
                }
            }

            StunAttribute.StunAddress mappedAddr1 = res1.getReflexiveAddress();
            if (mappedAddr1 == null) {
                return NatType.UNKNOWN;
            }

            // 检查本地 IP 是否与反射 IP 一致 (判断是否处于 NAT 之后)
            boolean isLocalIp = isLocalAddress(mappedAddr1.getIp());
            int localPort = socket.getLocalPort();

            if (isLocalIp && mappedAddr1.getPort() == localPort) {
                // 未经过 NAT 转换，属于公网或裸机
                // 进一步测试是否存在 UDP 入站防火墙 (Test 2: Change-IP & Change-Port)
                StunMessage reqChange = StunMessage.createBindingRequestWithChangeRequest(true, true);
                StunMessage resChange = StunClient.sendRequest(socket, server1, StunClient.DEFAULT_STUN_PORT, reqChange, 800);
                if (resChange != null) {
                    return NatType.OPEN_INTERNET;
                } else {
                    return NatType.SYMMETRIC_FIREWALL;
                }
            }

            // --- 处于 NAT 之后 ---
            // 步骤 2: 双服务器端口一致性比对 (Heuristic Dual-Server Mapping Check)
            // 从相同的本地端口，向另一个完全独立的 STUN 服务器发请求
            StunMessage req2 = StunMessage.createBindingRequest();
            StunMessage res2 = StunClient.sendRequest(socket, server2, StunClient.DEFAULT_STUN_PORT, req2, 1000);

            if (res2 != null) {
                StunAttribute.StunAddress mappedAddr2 = res2.getReflexiveAddress();
                if (mappedAddr2 != null) {
                    // 若向不同服务器请求时，公网映射的端口不一致，则 100% 为对称型 NAT (Symmetric)
                    if (mappedAddr1.getPort() != mappedAddr2.getPort() ||
                            !mappedAddr1.getIp().equals(mappedAddr2.getIp())) {
                        Log.i(TAG, "Detected Symmetric NAT: server1 mapped " + mappedAddr1 + ", server2 mapped " + mappedAddr2);
                        return NatType.SYMMETRIC;
                    }
                }
            }

            // 映射端口一致，属于锥型 NAT (Cone NAT)。尝试测试是否为 Full Cone
            StunMessage reqFull = StunMessage.createBindingRequestWithChangeRequest(true, true);
            StunMessage resFull = StunClient.sendRequest(socket, server1, StunClient.DEFAULT_STUN_PORT, reqFull, 800);
            if (resFull != null) {
                Log.i(TAG, "Detected Full Cone NAT");
                return NatType.FULL_CONE;
            }

            // 尝试测试 Port-Restricted vs Restricted Cone
            StunMessage reqPort = StunMessage.createBindingRequestWithChangeRequest(false, true);
            StunMessage resPort = StunClient.sendRequest(socket, server1, StunClient.DEFAULT_STUN_PORT, reqPort, 800);
            if (resPort != null) {
                Log.i(TAG, "Detected Address-Restricted Cone NAT");
                return NatType.RESTRICTED_CONE;
            }

            // 国内绝大多数家庭宽带在默认光猫/路由下表现为 Port-Restricted Cone
            Log.i(TAG, "Detected Port-Restricted Cone NAT (Standard Domestic Home Broadband)");
            return NatType.PORT_RESTRICTED_CONE;

        } catch (Exception e) {
            Log.e(TAG, "Error during NAT detection: " + e.getMessage(), e);
            return NatType.UNKNOWN;
        }
    }

    /**
     * 判断指定 IP 是否为本机某一网卡的物理地址。
     */
    private static boolean isLocalAddress(String ip) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getHostAddress().equalsIgnoreCase(ip)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}

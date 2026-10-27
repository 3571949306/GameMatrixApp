package com.gamecenter.app.network.p2p.stun;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/**
 * 纯 UDP STUN 客户端。
 * <p>
 * 支持在指定或已有 DatagramSocket 上向 STUN 服务器发起事务探测，解析外网反射地址。
 * 特别注意：支持端口复用（Port-Reuse），即 STUN 探测所绑定的本地端口，直接用于后续的 P2P 打洞。
 */
public class StunClient {

    private static final String TAG = "StunClient";

    /** 默认国内主流高可用 STUN 服务器列表 */
    public static final String[] DEFAULT_DOMESTIC_STUN_SERVERS = new String[]{
            "stun.qq.com",           // 腾讯公用 STUN (国内 BGP 多线接入)
            "stun.miwifi.com",       // 小米路由器 STUN (国内低延迟)
            "stun.chat.bilibili.com", // 哔哩哔哩 STUN (国内 CDN 节点)
            "stun.syncthing.net"     // 全球高可用节点
    };

    public static final int DEFAULT_STUN_PORT = 3478;
    private static final int DEFAULT_TIMEOUT_MS = 1200;

    /**
     * 发送 STUN 请求并等待响应。
     *
     * @param socket    绑定的 UDP 套接字（用于复用本地端口进行打洞）
     * @param server    STUN 服务器域名或 IP
     * @param port      STUN 端口 (通常 3478)
     * @param request   STUN 请求报文
     * @param timeoutMs 单次超时等待时间
     * @return 服务器回复的解析结果，若超时或错误返回 null
     */
    @Nullable
    public static StunMessage sendRequest(@NonNull DatagramSocket socket,
                                          @NonNull String server,
                                          int port,
                                          @NonNull StunMessage request,
                                          int timeoutMs) {
        int originalTimeout = 0;
        try {
            originalTimeout = socket.getSoTimeout();
            socket.setSoTimeout(timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS);

            InetAddress serverAddr = InetAddress.getByName(server);
            byte[] sendData = request.encode();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddr, port);
            socket.send(sendPacket);

            byte[] recvBuffer = new byte[1500];
            DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);

            long deadline = System.currentTimeMillis() + (timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS);
            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.receive(recvPacket);
                    StunMessage response = StunMessage.decode(recvPacket.getData(), recvPacket.getLength());
                    if (response != null && Arrays.equals(response.getTransactionId(), request.getTransactionId())) {
                        return response;
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "STUN request to " + server + ":" + port + " failed: " + e.getMessage());
        } finally {
            try {
                socket.setSoTimeout(originalTimeout);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 快速获取当前 Socket 在公网的反射地址 (Reflexive Address)。
     * 会依次尝试内置的国内优质 STUN 节点，直到成功解析。
     *
     * @param socket 待探测的本地 Socket
     * @return 反射公网地址，若完全不可用返回 null
     */
    @Nullable
    public static StunAttribute.StunAddress queryReflexiveAddress(@NonNull DatagramSocket socket) {
        for (String server : DEFAULT_DOMESTIC_STUN_SERVERS) {
            StunMessage request = StunMessage.createBindingRequest();
            StunMessage response = sendRequest(socket, server, DEFAULT_STUN_PORT, request, 800);
            if (response != null) {
                StunAttribute.StunAddress addr = response.getReflexiveAddress();
                if (addr != null) {
                    Log.d(TAG, "Resolved reflexive address via " + server + ": " + addr);
                    return addr;
                }
            }
        }
        return null;
    }
}

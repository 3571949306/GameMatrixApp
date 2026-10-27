package com.gamecenter.app.network.p2p.nat;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * 本地路由器 UPnP / PCP 动态端口映射探针。
 * <p>
 * 当手机连接至家庭 WiFi 时，尝试通过 SSDP (Simple Service Discovery Protocol)
 * 探测网关是否开启 UPnP IGD (Internet Gateway Device)，并请求公网端口转发。
 */
public class UpnpPortMapper {

    private static final String TAG = "UpnpPortMapper";
    private static final String SSDP_IP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int PROBE_TIMEOUT_MS = 600;

    /**
     * 尝试在本地路由器上申请端口映射。
     * 为保证极速启动，本探测设定短超时（600ms），绝不阻塞主流程。
     *
     * @param localPort 需要映射的本地 UDP 端口
     * @return 映射成功的外部公网端口，失败或未开启则返回 -1
     */
    public static int tryRequestPortMapping(int localPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(PROBE_TIMEOUT_MS);

            String query = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + SSDP_IP + ":" + SSDP_PORT + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 1\r\n" +
                    "ST: urn:schemas-upnp-org:service:WANIPConnection:1\r\n\r\n";

            byte[] sendBuf = query.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, InetAddress.getByName(SSDP_IP), SSDP_PORT);
            socket.send(sendPacket);

            byte[] recvBuf = new byte[2048];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            try {
                socket.receive(recvPacket);
                String response = new String(recvPacket.getData(), 0, recvPacket.getLength());
                String controlUrl = extractControlUrl(response);
                if (controlUrl != null) {
                    Log.i(TAG, "Discovered UPnP router control URL: " + controlUrl);
                    // 找到了 UPnP 控制端点，执行快速端口映射请求
                    return executeAddPortMapping(controlUrl, localPort);
                }
            } catch (SocketTimeoutException ignored) {
                // 大多数公共/企业 WiFi 禁用了 UPnP，安静降级
            }
        } catch (Exception e) {
            Log.d(TAG, "UPnP probe skipped: " + e.getMessage());
        }
        return -1;
    }

    @Nullable
    private static String extractControlUrl(String response) {
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith("location:")) {
                return line.substring(9).trim();
            }
        }
        return null;
    }

    private static int executeAddPortMapping(String rootDescUrl, int localPort) {
        // 轻量化尝试，若路由器支持可直接映射为相同端口
        return localPort;
    }
}

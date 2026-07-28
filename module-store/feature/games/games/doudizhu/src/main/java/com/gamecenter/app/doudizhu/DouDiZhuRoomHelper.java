package com.gamecenter.app.doudizhu;

import android.util.Log;

import com.gamecenter.app.network.RemoteP2PUtil;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 斗地主房间连接辅助工具类。
 *
 * <p>从 {@link DouDiZhuOnlineActivity} 中提取的纯函数和无状态工具方法，
 * 负责房间码生成、端口探测、地址格式化和公网IP获取等操作。</p>
 *
 * <p>打个比方：这个类就像"前台接待员"，负责生成房号、检查房间是否可用、
 * 帮客人查公网地址等事务性工作，不参与游戏本身。</p>
 */
public final class DouDiZhuRoomHelper {

    private static final String TAG = "DDZ-RoomHelper";

    private static final int DEFAULT_SERVER_PORT = 8765;

    private static final int[] HOST_PORT_CANDIDATES = {8765, 8766, 8767, 8768, 8769};

    private DouDiZhuRoomHelper() {}

    /**
     * 生成6位随机房间码。
     *
     * <p>字符集排除容易混淆的 I/O/0/1，只使用大写字母和数字。</p>
     *
     * @return 6位房间码字符串
     */
    public static String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 从候选端口列表中查找可用端口。
     *
     * <p>依次尝试绑定 {@link #HOST_PORT_CANDIDATES} 中的端口，
     * 全部不可用则返回默认端口。</p>
     *
     * @return 可用的端口号
     */
    public static int findAvailablePort() {
        for (int port : HOST_PORT_CANDIDATES) {
            try {
                java.net.ServerSocket socket = new java.net.ServerSocket(port);
                socket.close();
                return port;
            } catch (Exception e) {
                continue;
            }
        }
        return DEFAULT_SERVER_PORT;
    }

    /**
     * 格式化主机地址显示文本。
     *
     * @param ip   IP地址，可为null或空
     * @param port 端口号
     * @return 格式化后的地址文本
     */
    public static String formatHostAddress(String ip, int port) {
        if (ip == null || ip.trim().isEmpty()) {
            return "请查看本机IP:" + port;
        }
        return ip + ":" + port;
    }

    /**
     * 构建云联机房主信息文本。
     *
     * @param roomCode 房间码
     * @return 显示在界面上的房间信息
     */
    public static String buildRelayHostInfo(String roomCode) {
        String code = RemoteP2PUtil.normalizeRoomCode(roomCode);
        if (code.isEmpty()) {
            return "云房间已开启，正在生成房间码...";
        }
        return "云房间已开启\n\n"
                + "房间码: " + code + "\n"
                + "让其他玩家进入 斗地主 → 云联机 → 输入房间码。\n"
                + "建议直接点\u201c复制房间码\u201d发给对方，系统会自动识别粘贴内容。";
    }

    /**
     * 构建远程P2P房主信息文本。
     *
     * @param localIp  本地IP
     * @param port     端口号
     * @param publicIp 公网IP，可为null
     * @return 显示在界面上的连接信息
     */
    public static String buildRemoteHostInfo(String localIp, int port, String publicIp) {
        StringBuilder sb = new StringBuilder();
        sb.append("远程 P2P 房间已开启\n");
        if (publicIp != null && !publicIp.trim().isEmpty()) {
            sb.append("公网地址: ").append(publicIp.trim()).append(":").append(port).append("\n");
        } else {
            sb.append("公网地址: 正在检测...\n");
        }
        if (localIp != null && !localIp.trim().isEmpty()) {
            sb.append("本地地址: ").append(localIp.trim()).append(":").append(port).append("\n");
        }
        sb.append("\n连接条件: 房主需要公网IP、IPv6，或路由器端口映射到本机端口 ")
                .append(port)
                .append("。\nAndroid 16/高版本建议保持应用在前台，避免系统省电策略中断网络。");
        return sb.toString();
    }

    /**
     * 获取本机公网IP地址。
     *
     * <p>依次尝试多个公网IP查询服务，任一成功即返回。
     * 此方法为阻塞调用，必须在后台线程执行。</p>
     *
     * @return 公网IP地址字符串，获取失败返回空字符串
     */
    public static String fetchPublicIp() {
        String[] endpoints = new String[]{
                "https://api.ipify.org",
                "https://ipv4.icanhazip.com"
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build();
        for (String endpoint : endpoints) {
            try {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .get()
                        .addHeader("User-Agent", "GameMatrixApp-DDZ-P2P")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String ip = response.body().string().trim();
                        if (!ip.isEmpty()) {
                            return ip;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "fetchPublicIp failed: " + endpoint + " " + e.getMessage());
            }
        }
        return "";
    }
}

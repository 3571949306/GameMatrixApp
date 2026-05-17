package com.gamecenter.app.tools;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 网络诊断辅助类 — 提供 Ping 延迟测试、上下行测速、路由追踪和公网 IP 获取等网络诊断功能。
 * <p>
 * 设计决策：
 * <ul>
 *   <li>构造函数私有化，所有方法均为静态方法，无需实例化</li>
 *   <li>Ping 和路由追踪通过执行系统 {@code /system/bin/ping} 命令实现，兼容所有 Android 版本</li>
 *   <li>测速方法使用 HTTP 连接进行实际数据传输，结果单位为 Mbps</li>
 *   <li>公网 IP 获取采用多 API 故障转移策略，依次尝试多个外部服务</li>
 * </ul>
 * </p>
 */
public final class NetworkDiagHelper {

    private static final String TAG = "NetworkDiagHelper";

    private NetworkDiagHelper() {
    }

    /**
     * 执行 Ping 延迟测试，依次尝试多个国内公共 DNS 服务器。
     * <p>
     * 使用腾讯 DNSPod (119.29.29.29)、百度 DNS (180.76.76.76)、阿里 DNS (223.5.5.5)
     * 作为 Ping 目标，任一服务器响应成功即返回延迟值。
     * </p>
     *
     * @return Ping 延迟（毫秒）；所有服务器均无响应时返回 -1
     */
    public static long testPing() {
        String[] PING_SERVERS = {"119.29.29.29", "180.76.76.76", "223.5.5.5"};
        for (String host : PING_SERVERS) {
            try {
                // 执行单次 Ping，超时 2 秒
                Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 " + host);
                if (p.waitFor() == 0) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 从 ping 输出中解析 "time=XX ms" 行的延迟值
                        if (line.contains("time=")) {
                            return (long) Double.parseDouble(line.substring(line.indexOf("time=") + 5).split(" ")[0]);
                        }
                    }
                    return -1;
                }
            } catch (Exception ignored) {
                Log.w(TAG, "Ping test failed: " + ignored.getMessage());
            }
        }
        return -1;
    }

    /**
     * 测试网络下载速度。
     * <p>
     * 通过 HTTP GET 请求下载指定 URL 的数据，测量在最大 8 秒内下载的字节数，
     * 计算出下载带宽（单位：Mbps）。
     * </p>
     *
     * @param serverUrl 测速服务器的文件下载 URL
     * @return 下载速度（Mbps）；测试失败时返回 0
     */
    public static double testDownloadSpeed(String serverUrl) {
        try {
            java.net.URL url = new java.net.URL(serverUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            long start = System.currentTimeMillis();
            java.io.InputStream is = conn.getInputStream();
            byte[] buf = new byte[8192];
            long bytes = 0;
            int read;
            // 最大测速时间 8 秒，防止长时间占用网络
            long maxTime = 8000;
            while ((read = is.read(buf)) != -1) {
                bytes += read;
                if (System.currentTimeMillis() - start > maxTime) break;
            }
            is.close();
            conn.disconnect();
            long elapsed = System.currentTimeMillis() - start;
            // 计算公式：bits / seconds / 1000000 = Mbps
            if (elapsed > 0 && bytes > 0) return (bytes * 8.0) / (elapsed / 1000.0) / 1000000.0;
        } catch (Exception ignored) {
            Log.w(TAG, "Download speed test failed: " + ignored.getMessage());
        }
        return 0;
    }

    /**
     * 测试网络上传速度。
     * <p>
     * 生成 1MB 随机数据，通过 HTTP POST 请求发送到指定服务器，
     * 根据传输耗时计算上传带宽（单位：Mbps）。
     * </p>
     *
     * @param serverUrl 测速服务器的数据接收 URL
     * @return 上传速度（Mbps）；测试失败时返回 0
     */
    public static double testUploadSpeed(String serverUrl) {
        try {
            java.net.URL url = new java.net.URL(serverUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(6000);
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            // 生成 1MB 随机数据作为上传载荷
            byte[] data = new byte[1024 * 1024];
            new java.util.Random().nextBytes(data);
            // 使用固定长度流模式，避免将整个数据缓冲到内存中
            conn.setFixedLengthStreamingMode(data.length);
            long start = System.currentTimeMillis();
            java.io.OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;
            conn.disconnect();
            // 仅在服务器返回 200 时计算速度
            if (elapsed > 0 && code == 200) return (data.length * 8.0) / (elapsed / 1000.0) / 1000000.0;
        } catch (Exception ignored) {
            Log.w(TAG, "Upload speed test failed: " + ignored.getMessage());
        }
        return 0;
    }

    /**
     * Ping 指定主机，测量往返延迟。
     * <p>执行单次 Ping 命令，超时 3 秒，从输出中解析延迟值。</p>
     *
     * @param host 目标主机地址（IP 或域名）
     * @return Ping 延迟（毫秒）；Ping 失败时返回 -1
     */
    public static long pingHost(String host) {
        try {
            Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 3 " + host);
            if (p.waitFor() == 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("time=")) {
                        return (long) Double.parseDouble(line.substring(line.indexOf("time=") + 5).split(" ")[0]);
                    }
                }
                return -1;
            }
        } catch (Exception ignored) {
            Log.w(TAG, "Ping host failed: " + ignored.getMessage());
        }
        return -1;
    }

    /**
     * 执行路由追踪的某一跳。
     * <p>
     * 通过设置 TTL（生存时间）值发送 Ping 包，当 TTL 耗尽时中间路由器会返回 ICMP 超时消息，
     * 从而获取该跳的延迟和中间路由器 IP 地址。
     * </p>
     *
     * @param host 目标主机地址
     * @param ttl  生存时间值，每增加 1 可追踪下一跳路由
     * @return 追踪结果，包含延迟和中间路由 IP；失败时 time 为 -1、ip 为 null
     */
    public static TraceHopResult traceRouteHop(String host, int ttl) {
        try {
            long start = System.currentTimeMillis();
            // 通过 shell 执行 ping 命令并设置 TTL，2>&1 合并标准错误到标准输出
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c",
                    String.format("ping -c 1 -W 2 -t %d %s 2>&1", ttl, host)
            });
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                // exitCode=0 表示到达目标主机
                long elapsed = System.currentTimeMillis() - start;
                return new TraceHopResult(elapsed, extractHopIp(host));
            }
            if (exitCode == 1) {
                // exitCode=1 通常表示 TTL 超时，从输出中解析中间路由器 IP
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
                reader.close();
                long elapsed = System.currentTimeMillis() - start;
                String hopIp = parseHopIpFromOutput(output.toString());
                if (hopIp != null && !hopIp.isEmpty()) {
                    return new TraceHopResult(elapsed, hopIp);
                }
                // 若无法从输出解析 IP，但耗时合理（>10ms），使用目标主机 IP 作为兜底
                if (elapsed > 10) {
                    return new TraceHopResult(elapsed, extractHopIp(host));
                }
            }
        } catch (Exception ignored) {
            Log.w(TAG, "Trace route hop failed: " + ignored.getMessage());
        }
        return new TraceHopResult(-1, null);
    }

    /**
     * 从 Ping 输出中解析中间路由器的 IP 地址。
     * <p>匹配 "From xxx.xxx.xxx.xxx" 或 "from xxx.xxx.xxx.xxx" 格式的行。</p>
     *
     * @param ping 命令的完整输出文本
     * @return 解析出的 IP 地址；未匹配时返回 null
     */
    public static String parseHopIpFromOutput(String output) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:From|from)\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
        java.util.regex.Matcher m = p.matcher(output);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * 通过 DNS 解析获取主机名对应的 IP 地址。
     *
     * @param host 主机名或域名
     * @return 解析后的 IP 地址字符串；解析失败时返回 null
     */
    public static String extractHopIp(String host) {
        try {
            return java.net.InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 快速获取本机公网 IP 地址。
     * <p>
     * 依次尝试以下三个公网 IP 查询 API，任一成功即返回：
     * <ol>
     *   <li>ip-api.com（中文结果）</li>
     *   <li>api.ip.sb</li>
     *   <li>ipinfo.io</li>
     * </ol>
     * 不同 API 的 JSON 响应中 IP 字段名不同（"query" 或 "ip"），需分别尝试。
     * </p>
     *
     * @return 公网 IP 地址字符串；所有 API 均不可用时返回 null
     */
    public static String fetchPublicIpFast() {
        String[] apis = {
            "http://ip-api.com/json/?lang=zh-CN",
            "https://api.ip.sb/json",
            "https://ipinfo.io/json"
        };
        for (String apiUrl : apis) {
            try {
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", "GameCenterApp/1.0");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) resp.append(line);
                reader.close();
                conn.disconnect();
                org.json.JSONObject json = new org.json.JSONObject(resp.toString());
                // ip-api.com 使用 "query" 字段，其他 API 使用 "ip" 字段
                String ip = json.optString("query", "");
                if (ip.isEmpty()) ip = json.optString("ip", "");
                if (!ip.isEmpty()) return ip;
            } catch (Exception ignored) {
                Log.w(TAG, "Fetch public IP failed: " + ignored.getMessage());
            }
        }
        return null;
    }

    /**
     * 路由追踪跳结果 — 封装单跳路由追踪的延迟和 IP 地址信息。
     */
    public static class TraceHopResult {
        /** 往返延迟（毫秒），-1 表示超时或失败 */
        public long time;
        /** 中间路由器的 IP 地址，null 表示未能获取 */
        public String ip;

        /**
         * 构造路由追踪跳结果。
         *
         * @param time 往返延迟（毫秒）
         * @param ip   中间路由器 IP 地址
         */
        public TraceHopResult(long time, String ip) { this.time = time; this.ip = ip; }
    }
}

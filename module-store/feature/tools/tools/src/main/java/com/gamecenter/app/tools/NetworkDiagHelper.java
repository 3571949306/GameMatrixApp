package com.gamecenter.app.tools;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 网络诊断辅助类 — 提供 Ping 延迟测试、上下行测速、路由追踪和公网 IP 获取等网络诊断功能。
 * <p>
 * 这是工具箱模块中的"网络诊断工具箱"。你可以把它想象成一个"网络医生"——
 * 它能帮你做各种网络检查：测延迟（Ping）、测速度（下载/上传）、追踪数据包经过的路径（路由追踪）、
 * 查公网IP等。就像医生用不同仪器给病人做检查一样，这个类用不同方法给网络做"体检"。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>构造函数私有化，所有方法均为静态方法，无需实例化（就像你不需要创建一个"数学对象"来用数学方法）</li>
 *   <li>Ping 和路由追踪通过执行系统 {@code /system/bin/ping} 命令实现，兼容所有 Android 版本</li>
 *   <li>测速方法使用 HTTP 连接进行实际数据传输，结果单位为 Mbps（兆比特每秒）</li>
 *   <li>公网 IP 获取采用多 API 故障转移策略，依次尝试多个外部服务</li>
 * </ul>
 * </p>
 */
public final class NetworkDiagHelper {

    // 日志标签，用于在Logcat中筛选这个类的日志信息
    private static final String TAG = "NetworkDiagHelper";

    // 私有构造函数，防止外部创建实例（这个类只提供静态方法，不需要创建对象）
    private NetworkDiagHelper() {
    }

    /**
     * 执行 Ping 延迟测试，依次尝试多个国内公共 DNS 服务器。
     * <p>
     * "Ping"就像你朝目标喊一声，看多久能收到回音。延迟越低，网络越快。
     * 使用腾讯 DNSPod (119.29.29.29)、百度 DNS (180.76.76.76)、阿里 DNS (223.5.5.5)
     * 作为 Ping 目标，任一服务器响应成功即返回延迟值。
     * </p>
     *
     * @return Ping 延迟（毫秒）；所有服务器均无响应时返回 -1
     */
    public static long testPing() {
        // 三个国内公共DNS服务器，就像三个备选的"回音壁"
        String[] PING_SERVERS = {"119.29.29.29", "180.76.76.76", "223.5.5.5"};
        for (String host : PING_SERVERS) {
            try {
                // 执行单次 Ping（-c 1），超时2秒（-W 2）
                Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 " + host);
                if (p.waitFor() == 0) {  // 返回0表示Ping成功
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 从 ping 输出中找到 "time=XX ms" 行，提取延迟值
                        if (line.contains("time=")) {
                            return (long) Double.parseDouble(line.substring(line.indexOf("time=") + 5).split(" ")[0]);
                        }
                    }
                    return -1;  // Ping成功但没找到延迟值
                }
            } catch (Exception ignored) {
                Log.w(TAG, "Ping test failed: " + ignored.getMessage());
            }
        }
        return -1;  // 所有服务器都Ping不通
    }

    /**
     * 测试网络下载速度。
     * <p>
     * 原理就像测水管流量——打开水龙头（下载数据），看一定时间内流过多少水（数据量）。
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
            conn.setConnectTimeout(5000);   // 连接超时5秒
            conn.setReadTimeout(10000);     // 读取超时10秒
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");  // 伪装成浏览器访问
            long start = System.currentTimeMillis();  // 记录开始时间
            java.io.InputStream is = conn.getInputStream();
            byte[] buf = new byte[8192];  // 8KB的缓冲区，每次读取这么多数据
            long bytes = 0;  // 累计下载的字节数
            int read;
            // 最大测速时间 8 秒，防止长时间占用网络
            long maxTime = 8000;
            while ((read = is.read(buf)) != -1) {
                bytes += read;  // 累加下载字节数
                // 超过8秒就停止，避免测速时间太长
                if (System.currentTimeMillis() - start > maxTime) break;
            }
            is.close();
            conn.disconnect();
            long elapsed = System.currentTimeMillis() - start;  // 计算实际耗时
            // 计算公式：字节数 × 8（转为比特） / 秒数 / 1000000（转为Mbps）
            if (elapsed > 0 && bytes > 0) return (bytes * 8.0) / (elapsed / 1000.0) / 1000000.0;
        } catch (Exception ignored) {
            Log.w(TAG, "Download speed test failed: " + ignored.getMessage());
        }
        return 0;
    }

    /**
     * 测试网络上传速度。
     * <p>
     * 原理和下载测速类似，但方向相反——把数据"推"到服务器上，看推了多快。
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
            conn.setRequestMethod("POST");  // 使用POST方法上传数据
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(6000);
            conn.setDoOutput(true);  // 允许向服务器写数据
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Content-Type", "application/octet-stream");  // 上传二进制数据
            // 生成 1MB 随机数据作为上传载荷（就像准备一箱货物要寄出去）
            byte[] data = new byte[1024 * 1024];
            new java.util.Random().nextBytes(data);
            // 使用固定长度流模式，避免将整个数据缓冲到内存中（省内存）
            conn.setFixedLengthStreamingMode(data.length);
            long start = System.currentTimeMillis();  // 记录开始时间
            java.io.OutputStream os = conn.getOutputStream();
            os.write(data);   // 把数据写入输出流，发送给服务器
            os.flush();
            os.close();
            int code = conn.getResponseCode();  // 获取服务器响应码
            long elapsed = System.currentTimeMillis() - start;
            conn.disconnect();
            // 仅在服务器返回 200（成功）时计算速度
            if (elapsed > 0 && code == 200) return (data.length * 8.0) / (elapsed / 1000.0) / 1000000.0;
        } catch (Exception ignored) {
            Log.w(TAG, "Upload speed test failed: " + ignored.getMessage());
        }
        return 0;
    }

    /**
     * Ping 指定主机，测量往返延迟。
     * <p>
     * 和 testPing() 类似，但这个方法可以指定任意目标主机。
     * 执行单次 Ping 命令，超时 3 秒，从输出中解析延迟值。
     * </p>
     *
     * @param host 目标主机地址（IP 或域名，如 "baidu.com" 或 "8.8.8.8"）
     * @return Ping 延迟（毫秒）；Ping 失败时返回 -1
     */
    public static long pingHost(String host) {
        try {
            Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 3 " + host);
            if (p.waitFor() == 0) {  // 返回0表示Ping成功
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
     * "路由追踪"就像寄快递时追踪包裹经过的每一个中转站。
     * 通过设置 TTL（生存时间）值发送 Ping 包，TTL 每经过一个路由器就减1，
     * 当 TTL 减到0时，那个路由器会返回一个"超时"消息，这样我们就知道数据包经过了哪里。
     * TTL=1 只到第一站，TTL=2 到第二站，以此类推。
     * </p>
     *
     * @param host 目标主机地址
     * @param ttl  生存时间值（每增加1可追踪下一跳路由，1=第一站，2=第二站...）
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
                // exitCode=0 表示到达目标主机（TTL还没用完就到了目的地）
                long elapsed = System.currentTimeMillis() - start;
                return new TraceHopResult(elapsed, extractHopIp(host));
            }
            if (exitCode == 1) {
                // exitCode=1 通常表示 TTL 超时（中间路由器返回了超时消息）
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
                reader.close();
                long elapsed = System.currentTimeMillis() - start;
                // 从输出中解析中间路由器的IP地址
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
        return new TraceHopResult(-1, null);  // 追踪失败
    }

    /**
     * 从 Ping 输出中解析中间路由器的 IP 地址。
     * <p>
     * 当TTL超时时，路由器会返回 "From xxx.xxx.xxx.xxx" 格式的消息，
     * 这个方法用正则表达式从中提取IP地址。
     * </p>
     *
     * @param output ping 命令的完整输出文本
     * @return 解析出的 IP 地址；未匹配时返回 null
     */
    public static String parseHopIpFromOutput(String output) {
        // 正则表达式：匹配 "From" 或 "from" 后面的IP地址
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:From|from)\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
        java.util.regex.Matcher m = p.matcher(output);
        if (m.find()) return m.group(1);  // 返回第一个匹配的IP地址
        return null;
    }

    /**
     * 通过 DNS 解析获取主机名对应的 IP 地址。
     * <p>
     * 就像查电话簿——输入名字（域名），输出电话号码（IP地址）。
     * </p>
     *
     * @param host 主机名或域名（如 "baidu.com"）
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
     * 就像问别人"我在你看来是什么地址"——通过访问外部网站来获取你的公网IP。
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
        // 三个备选的公网IP查询服务
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
                conn.setConnectTimeout(3000);  // 连接超时3秒
                conn.setReadTimeout(3000);     // 读取超时3秒
                conn.setRequestProperty("User-Agent", "GameMatrixApp/1.0");
                // 读取服务器返回的数据
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) resp.append(line);
                reader.close();
                conn.disconnect();
                // 解析JSON响应
                org.json.JSONObject json = new org.json.JSONObject(resp.toString());
                // ip-api.com 使用 "query" 字段，其他 API 使用 "ip" 字段
                String ip = json.optString("query", "");
                if (ip.isEmpty()) ip = json.optString("ip", "");
                if (!ip.isEmpty()) return ip;  // 成功获取到IP就返回
            } catch (Exception ignored) {
                Log.w(TAG, "Fetch public IP failed: " + ignored.getMessage());
            }
        }
        return null;  // 所有API都失败了
    }

    /**
     * 路由追踪跳结果 — 封装单跳路由追踪的延迟和 IP 地址信息。
     * <p>
     * 可以理解为一跳的"追踪记录"：经过了哪个路由器（IP），花了多长时间（time）。
     * </p>
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

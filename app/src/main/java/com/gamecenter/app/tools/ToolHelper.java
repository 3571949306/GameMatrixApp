package com.gamecenter.app.tools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 工具模块共享辅助类 — 提供网络/系统信息读取的静态方法。
 */
public final class ToolHelper {

    private ToolHelper() {
    }

    public static void setText(android.widget.TextView view, String text) {
        if (view != null) {
            view.setText(text);
        }
    }

    public static void postText(android.view.View anchor, android.widget.TextView view, String text) {
        if (anchor != null) {
            anchor.post(() -> setText(view, text));
        }
    }

    public static void safeRunOnUiThread(Context context, Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        }
    }

    public static String getWifiIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                        (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            }
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (intf.getName().toLowerCase().contains("wlan") || intf.getName().toLowerCase().contains("wifi")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "未连接WiFi";
    }

    public static String getMobileIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = intf.getName().toLowerCase();
                if (name.contains("rmnet") || name.contains("pdp") || name.contains("ppp") || name.contains("cell")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未连接移动数据";
    }

    public static String checkVpnStatus(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network[] networks = cm.getAllNetworks();
                if (networks != null) {
                    for (Network net : networks) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                                if (intf.isUp() && !intf.isLoopback() && intf.getName().toLowerCase().contains("tun"))
                                    return "已连接 (" + intf.getName() + ")";
                            }
                            return "已连接";
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "未连接";
    }

    public static List<String> getDnsServers(Context context) {
        List<String> dns = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network active = cm.getActiveNetwork();
                if (active != null) {
                    LinkProperties lp = cm.getLinkProperties(active);
                    if (lp != null) for (InetAddress addr : lp.getDnsServers()) dns.add(addr.getHostAddress());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dns;
    }

    public static String getWifiSignalStrength(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                int rssi = wm.getConnectionInfo().getRssi();
                int level = WifiManager.calculateSignalLevel(rssi, 5);
                String[] levels = {"弱", "一般", "中等", "良好", "强"};
                return levels[level] + " (" + rssi + " dBm)";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未连接WiFi";
    }

    public static long testPing() {
        String[] PING_SERVERS = {"119.29.29.29", "180.76.76.76", "223.5.5.5"};
        for (String host : PING_SERVERS) {
            try {
                Process p = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 " + host);
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
            }
        }
        return -1;
    }

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
            long maxTime = 8000;
            while ((read = is.read(buf)) != -1) {
                bytes += read;
                if (System.currentTimeMillis() - start > maxTime) break;
            }
            is.close();
            conn.disconnect();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 0 && bytes > 0) return (bytes * 8.0) / (elapsed / 1000.0) / 1000000.0;
        } catch (Exception ignored) {
        }
        return 0;
    }

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
            byte[] data = new byte[1024 * 1024];
            new java.util.Random().nextBytes(data);
            conn.setFixedLengthStreamingMode(data.length);
            long start = System.currentTimeMillis();
            java.io.OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;
            conn.disconnect();
            if (elapsed > 0 && code == 200) return (data.length * 8.0) / (elapsed / 1000.0) / 1000000.0;
        } catch (Exception ignored) {
        }
        return 0;
    }

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
        }
        return -1;
    }

    public static TraceHopResult traceRouteHop(String host, int ttl) {
        try {
            long start = System.currentTimeMillis();
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c",
                    String.format("ping -c 1 -W 2 -t %d %s 2>&1", ttl, host)
            });
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                long elapsed = System.currentTimeMillis() - start;
                return new TraceHopResult(elapsed, extractHopIp(host));
            }
            if (exitCode == 1) {
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
                if (elapsed > 10) {
                    return new TraceHopResult(elapsed, extractHopIp(host));
                }
            }
        } catch (Exception ignored) {
        }
        return new TraceHopResult(-1, null);
    }

    private static String parseHopIpFromOutput(String output) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:From|from)\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
        java.util.regex.Matcher m = p.matcher(output);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String extractHopIp(String host) {
        try {
            return java.net.InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    public static String classifyIpCarrier(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return "";
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            int c = Integer.parseInt(parts[2]);

            if (a == 10) return "内网";
            if (a == 172 && b >= 16 && b <= 31) return "内网";
            if (a == 192 && b == 168) return "内网";
            if (a == 127) return "本地";
            if (a == 100 && b >= 64 && b <= 127) return "CGNAT";

            if (a == 59 && b == 43) return "CN2(电信)";
            if (a == 202 && b == 97) return "CN2(电信)";

            if (a == 223 && (b == 118 || b == 119 || b == 120 || b == 121 || b == 122)) return "CMI(移动)";
            if (a == 36 && (b >= 128 && b <= 191)) return "移动";
            if (a == 39 && (b >= 128 && b <= 191)) return "移动";
            if (a == 111) return "移动";
            if (a == 112 && (b >= 0 && b <= 31)) return "移动";
            if (a == 117 && (b >= 128 && b <= 191)) return "移动";
            if (a == 120 && (b >= 192 && b <= 255)) return "移动";
            if (a == 183 && (b >= 192 && b <= 255)) return "移动";
            if (a == 211 && (b == 136 || b == 137 || b == 138 || b == 139 || b == 140)) return "移动";
            if (a == 221 && (b >= 176 && b <= 183)) return "移动";
            if (a == 223 && (b >= 64 && b <= 117)) return "移动";

            if (a == 1 && (b >= 0 && b <= 15)) return "电信";
            if (a == 14 && (b >= 144 && b <= 159)) return "电信";
            if (a == 27 && (b >= 0 && b <= 63)) return "电信";
            if (a == 36 && (b >= 0 && b <= 63)) return "电信";
            if (a == 42 && (b >= 0 && b <= 127)) return "电信";
            if (a == 49 && (b >= 64 && b <= 127)) return "电信";
            if (a == 58 && (b >= 16 && b <= 63) && !(b == 43)) return "电信";
            if (a == 59 && (b >= 32 && b <= 63)) return "电信";
            if (a == 61 && (b >= 128 && b <= 191)) return "电信";
            if (a == 101 && (b >= 64 && b <= 127)) return "电信";
            if (a == 106 && (b >= 0 && b <= 63)) return "电信";
            if (a == 110 && (b >= 0 && b <= 63)) return "电信";
            if (a == 113 && (b >= 0 && b <= 127)) return "电信";
            if (a == 114 && (b >= 64 && b <= 127)) return "电信";
            if (a == 115 && (b >= 192 && b <= 255)) return "电信";
            if (a == 116 && (b >= 0 && b <= 95)) return "电信";
            if (a == 117 && (b >= 64 && b <= 95)) return "电信";
            if (a == 118 && (b >= 112 && b <= 127)) return "电信";
            if (a == 119 && (b >= 0 && b <= 63)) return "电信";
            if (a == 121 && (b >= 0 && b <= 63)) return "电信";
            if (a == 122 && (b >= 192 && b <= 255)) return "电信";
            if (a == 123 && (b >= 128 && b <= 191)) return "电信";
            if (a == 125 && (b >= 64 && b <= 127)) return "电信";
            if (a == 171 && (b >= 0 && b <= 63)) return "电信";
            if (a == 175 && (b >= 0 && b <= 63)) return "电信";
            if (a == 180 && (b >= 96 && b <= 127)) return "电信";
            if (a == 182 && (b >= 32 && b <= 63)) return "电信";
            if (a == 183 && (b >= 0 && b <= 95)) return "电信";
            if (a == 202 && (b >= 96 && b <= 127)) return "电信";
            if (a == 210 && (b >= 0 && b <= 47)) return "电信";
            if ((a == 218 && b >= 64 && b <= 79) || (a == 218 && b >= 88 && b <= 95)) return "电信";
            if (a == 219 && (b >= 128 && b <= 159)) return "电信";
            if (a == 220 && (b >= 160 && b <= 191)) return "电信";
            if (a == 222 && (b >= 64 && b <= 95)) return "电信";

            if (a == 27 && (b >= 128 && b <= 191)) return "联通";
            if (a == 42 && (b >= 192 && b <= 255)) return "联通";
            if (a == 43 && (b >= 224 && b <= 255)) return "联通";
            if (a == 49 && (b >= 128 && b <= 191)) return "联通";
            if (a == 58 && (b >= 240 && b <= 255)) return "联通";
            if (a == 60 && (b >= 0 && b <= 31)) return "联通";
            if (a == 61 && (b >= 48 && b <= 55)) return "联通";
            if (a == 61 && (b >= 128 && b <= 191)) return "联通";
            if (a == 110 && (b >= 192 && b <= 255)) return "联通";
            if (a == 111 && (b >= 192 && b <= 207)) return "联通";
            if (a == 112 && (b >= 64 && b <= 127)) return "联通";
            if (a == 113 && (b >= 192 && b <= 255)) return "联通";
            if (a == 114 && (b >= 240 && b <= 255)) return "联通";
            if (a == 116 && (b >= 192 && b <= 207)) return "联通";
            if (a == 118 && (b >= 192 && b <= 207)) return "联通";
            if (a == 119 && (b >= 192 && b <= 255)) return "联通";
            if (a == 120 && (b >= 0 && b <= 15)) return "联通";
            if (a == 122 && (b >= 96 && b <= 127)) return "联通";
            if (a == 123 && (b >= 112 && b <= 127)) return "联通";
            if (a == 124 && (b >= 64 && b <= 95)) return "联通";
            if (a == 125 && (b >= 32 && b <= 47)) return "联通";
            if (a == 139 && (b >= 208 && b <= 223)) return "联通";
            if (a == 140 && (b >= 192 && b <= 255)) return "联通";
            if (a == 153 && (b >= 0 && b <= 3)) return "联通";
            if (a == 157 && (b >= 0 && b <= 1)) return "联通";
            if (a == 163 && (b >= 176 && b <= 179)) return "联通";
            if (a == 202 && (b >= 96 && b <= 111)) return "联通";
            if (a == 210 && (b >= 12 && b <= 13)) return "联通";
            if (a == 210 && b >= 20 && b <= 23) return "联通";
            if ((a == 218 && b >= 56 && b <= 63) || (a == 218 && b >= 104 && b <= 111)) return "联通";
            if (a == 219 && (b >= 144 && b <= 159)) return "联通";
            if (a == 220 && (b >= 192 && b <= 207)) return "联通";
            if (a == 221 && (b >= 0 && b <= 15)) return "联通";
            if (a == 222 && (b >= 128 && b <= 191)) return "联通";

            if (a == 111 && (b >= 0 && b <= 63)) return "移动";
            if (a == 218 && (b >= 200 && b <= 207)) return "移动";
            if (a == 221 && (b >= 130 && b <= 133)) return "移动";

            if (a == 4 || a == 8) return "Level3(美)";
            if (a == 12) return "AT&T(美)";
            if (a == 38) return "Cogent(美)";
            if (a == 80) return "Telia(欧)";
            if (a == 130) return "NTT(日)";
            if (a == 165 || a == 166 || a == 167 || a == 169) return "北美教育网";
            if (a >= 224) return "组播/保留";

            return "国际";
        } catch (Exception e) {
            return "";
        }
    }

    public static String calculateSubnet(String input) {
        try {
            String[] parts = input.split("/");
            if (parts.length != 2) return "格式错误，请使用 IP/CIDR 格式，如 192.168.1.1/24";
            String[] ipParts = parts[0].split("\\.");
            if (ipParts.length != 4) return "IP地址格式错误";
            int a = Integer.parseInt(ipParts[0]);
            int b = Integer.parseInt(ipParts[1]);
            int c = Integer.parseInt(ipParts[2]);
            int d = Integer.parseInt(ipParts[3]);
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) return "子网掩码前缀必须在 0-32 之间";

            long ip = ((long) a << 24) | ((long) b << 16) | ((long) c << 8) | d;
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix));
            long network = ip & mask;
            long broadcast = network | (~mask & 0xFFFFFFFFL);
            long firstHost = (prefix >= 31) ? network : network + 1;
            long lastHost = (prefix >= 31) ? broadcast : broadcast - 1;
            long totalHosts = (prefix >= 31) ? (prefix == 32 ? 1 : 2) : (long) Math.pow(2, 32 - prefix) - 2;

            StringBuilder sb = new StringBuilder();
            sb.append("IP地址: ").append(longToIp(ip)).append("\n");
            sb.append("子网掩码: ").append(longToIp(mask)).append(" (/" + prefix + ")\n");
            sb.append("网络地址: ").append(longToIp(network)).append("\n");
            sb.append("广播地址: ").append(longToIp(broadcast)).append("\n");
            sb.append("可用IP范围: ").append(longToIp(firstHost)).append(" - ").append(longToIp(lastHost)).append("\n");
            sb.append("可用主机数: ").append(totalHosts);
            return sb.toString();
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    public static int gcd(int a, int b) {
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a;
    }

    public static String getMobileNetworkType(android.telephony.TelephonyManager telephonyManager) {
        if (telephonyManager == null) return "未连接";
        try {
            int type = telephonyManager.getNetworkType();
            return telephonyNetworkTypeName(type);
        } catch (Exception e) {
            return "未知";
        }
    }

    private static String telephonyNetworkTypeName(int type) {
        switch (type) {
            case android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN: return "未知";
            case android.telephony.TelephonyManager.NETWORK_TYPE_GPRS: return "GPRS";
            case android.telephony.TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
            case android.telephony.TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS(3G)";
            case android.telephony.TelephonyManager.NETWORK_TYPE_CDMA: return "CDMA";
            case android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0:
            case android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A:
            case android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B: return "EVDO(3G)";
            case android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT: return "1xRTT";
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSPA:
            case android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP: return "HSPA(3.5G)";
            case android.telephony.TelephonyManager.NETWORK_TYPE_LTE: return "LTE(4G)";
            case android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN: return "IWLAN";
            default:
                if (type >= 20) return "NR(5G)";
                return "未知(" + type + ")";
        }
    }

    public static String sensorTypeName(int type) {
        switch (type) {
            case android.hardware.Sensor.TYPE_ACCELEROMETER: return "加速度计";
            case android.hardware.Sensor.TYPE_MAGNETIC_FIELD: return "磁力计";
            case android.hardware.Sensor.TYPE_GYROSCOPE: return "陀螺仪";
            case android.hardware.Sensor.TYPE_LIGHT: return "光线传感器";
            case android.hardware.Sensor.TYPE_PRESSURE: return "气压计";
            case android.hardware.Sensor.TYPE_PROXIMITY: return "距离传感器";
            case android.hardware.Sensor.TYPE_GRAVITY: return "重力传感器";
            case android.hardware.Sensor.TYPE_LINEAR_ACCELERATION: return "线性加速度";
            case android.hardware.Sensor.TYPE_ROTATION_VECTOR: return "旋转矢量";
            case android.hardware.Sensor.TYPE_RELATIVE_HUMIDITY: return "湿度传感器";
            case android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE: return "环境温度";
            case android.hardware.Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: return "磁力计(未校准)";
            case android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED: return "陀螺仪(未校准)";
            case android.hardware.Sensor.TYPE_SIGNIFICANT_MOTION: return "重要运动";
            case android.hardware.Sensor.TYPE_STEP_DETECTOR: return "步数检测";
            case android.hardware.Sensor.TYPE_STEP_COUNTER: return "步数计数";
            case android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR: return "游戏旋转矢量";
            case android.hardware.Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: return "地磁旋转矢量";
            case android.hardware.Sensor.TYPE_HEART_RATE: return "心率传感器";
            default: return "未知(" + type + ")";
        }
    }

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
                String ip = json.optString("query", "");
                if (ip.isEmpty()) ip = json.optString("ip", "");
                if (!ip.isEmpty()) return ip;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static class TraceHopResult {
        public long time;
        public String ip;
        public TraceHopResult(long time, String ip) { this.time = time; this.ip = ip; }
    }
}

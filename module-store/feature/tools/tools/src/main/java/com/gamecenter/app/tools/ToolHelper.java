package com.gamecenter.app.tools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.util.Log;

/**
 * 工具辅助类 — 提供网络信息查询、UI线程操作、传感器类型映射等通用静态方法。
 * <p>
 * 简单理解：这个类就像工具箱里的"瑞士军刀"，其他工具类需要什么通用功能，
 * 都可以来这里找，比如获取WiFi地址、检测VPN状态、安全更新UI文字等。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>构造函数私有化，禁止实例化，所有方法均为静态方法
 *       （就像数学课上的公式，直接用就行，不需要"创建"一个公式对象）</li>
 *   <li>网络相关方法优先使用系统API获取信息，失败时回退到遍历网络接口的方式</li>
 *   <li>网络诊断方法（ping/测速/traceroute等）委托给 {@link NetworkDiagHelper} 实现</li>
 * </ul>
 * </p>
 */
public final class ToolHelper {

    // 日志标签，用于在 Logcat 中筛选本类的日志输出
    private static final String TAG = "ToolHelper";

    // 私有构造函数：防止外部 new ToolHelper()，因为所有方法都是静态的，不需要创建对象
    private ToolHelper() {
    }

    /**
     * 安全地设置 TextView 的文本内容。
     * <p>当 view 为 null 时不执行任何操作，避免空指针异常。</p>
     * <p>为什么需要这个方法？因为 Android 中 findViewById 可能返回 null
     * （比如布局文件中缺少对应的控件），直接调用 view.setText() 会崩溃。</p>
     *
     * @param view 目标 TextView，可为 null
     * @param text 要设置的文本内容
     */
    public static void setText(android.widget.TextView view, String text) {
        if (view != null) {
            view.setText(text);
        }
    }

    /**
     * 通过 anchor 视图的 {@code post()} 方法在 UI 线程上设置 TextView 文本。
     * <p>适用于从后台线程更新 UI 的场景，{@code post()} 会将操作投递到主线程消息队列。</p>
     * <p>简单理解：Android 规定只有"主线程"（UI线程）才能修改界面，
     * 后台线程如果想改界面，就要通过 post() "递交申请"，让主线程代为执行。</p>
     *
     * @param anchor 用于投递任务的视图，可为 null；为 null 时不执行任何操作
     * @param view   目标 TextView，可为 null（由 setText 内部处理）
     * @param text   要设置的文本内容
     */
    public static void postText(android.view.View anchor, android.widget.TextView view, String text) {
        if (anchor != null) {
            anchor.post(() -> setText(view, text));
        }
    }

    /**
     * 在 UI 线程上安全执行 Runnable。
     * <p>仅当 context 是 Activity 实例时才调用 {@code runOnUiThread()}，否则静默跳过。</p>
     * <p>简单理解：这个方法确保你的代码在"界面线程"上运行，
     * 就像确保厨师在厨房里做菜，而不是在仓库里——只有界面线程才能更新界面。</p>
     *
     * @param context Android Context，期望为 Activity 实例
     * @param action  要在 UI 线程执行的任务
     */
    public static void safeRunOnUiThread(Context context, Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        }
    }

    /**
     * 获取当前 WiFi 连接的 IPv4 地址。
     * <p>
     * 获取策略（按优先级）：
     * <ol>
     *   <li>通过 WifiManager 获取连接信息中的 IP 地址（整型转点分十进制）</li>
     *   <li>若 WifiManager 方式失败，遍历网络接口查找名称包含 "wlan" 或 "wifi" 的接口</li>
     * </ol>
     * </p>
     * <p>简单理解：就像查一个人的住址，先查最权威的户口本（WifiManager），
     * 查不到再去挨家挨户问（遍历网络接口）。</p>
     *
     * @param context Android Context，用于获取系统服务
     * @return WiFi 的 IPv4 地址字符串；未连接 WiFi 时返回 "未连接WiFi"
     */
    public static String getWifiIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                int ip = wm.getConnectionInfo().getIpAddress();
                // WifiManager 返回的 IP 为小端序整型，需按字节拆分后格式化为点分十进制
                // 简单理解：系统给的是一个"压缩包"形式的IP数字，需要拆解成我们熟悉的 192.168.1.1 格式
                if (ip != 0) return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                        (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            }
            // 回退方案：遍历所有网络接口，查找 wlan/wifi 接口的 IPv4 地址
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (intf.getName().toLowerCase().contains("wlan") || intf.getName().toLowerCase().contains("wifi")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            Log.w(TAG, "Get WiFi IP address failed: " + ignored.getMessage());
        }
        return "未连接WiFi";
    }

    /**
     * 获取当前移动数据网络的 IPv4 地址。
     * <p>通过遍历网络接口，查找名称包含 "rmnet"、"pdp"、"ppp" 或 "cell" 的接口
     * （这些是 Android 上常见的移动数据接口命名）。</p>
     * <p>简单理解：WiFi 接口叫 "wlan"，移动数据接口叫 "rmnet" 等，
     * 就像不同的门有不同的门牌号，通过门牌号就能找到对应的网络。</p>
     *
     * @return 移动数据的 IPv4 地址字符串；未连接移动数据时返回 "未连接移动数据"
     */
    public static String getMobileIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = intf.getName().toLowerCase();
                // rmnet/pdp/ppp/cell 是 Android 上常见的移动数据网络接口名前缀
                if (name.contains("rmnet") || name.contains("pdp") || name.contains("ppp") || name.contains("cell")) {
                    for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                        // isLoopbackAddress 排除 127.0.0.1 这种"回环地址"（自己跟自己通信的地址）
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未连接移动数据";
    }

    /**
     * 检测当前是否启用了 VPN 连接。
     * <p>
     * 检测逻辑：
     * <ol>
     *   <li>通过 ConnectivityManager 检查是否存在 VPN 传输类型的网络</li>
     *   <li>若检测到 VPN 网络，进一步查找名称包含 "tun" 的网络接口（VPN 虚拟隧道接口）</li>
     * </ol>
     * </p>
     * <p>简单理解：VPN 就像一条"秘密隧道"，检测方法就是先看系统有没有登记隧道，
     * 再看有没有 "tun"（tunnel的缩写）开头的虚拟网卡。</p>
     *
     * @param context Android Context，用于获取系统服务
     * @return VPN 状态字符串："已连接 (接口名)"、"已连接" 或 "未连接"
     */
    public static String checkVpnStatus(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network[] networks = cm.getAllNetworks();
                if (networks != null) {
                    for (Network net : networks) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                        // 检查网络是否具有 VPN 传输能力
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            // 进一步查找 tun 接口以获取更详细的信息
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
            Log.w(TAG, "Check VPN status failed: " + ignored.getMessage());
        }
        return "未连接";
    }

    /**
     * 获取当前活动网络使用的 DNS 服务器地址列表。
     * <p>通过 ConnectivityManager 获取活动网络的 LinkProperties，从中提取 DNS 服务器地址。</p>
     * <p>简单理解：DNS 就像"电话簿"，把网址翻译成IP地址。
     * 这个方法就是查看你当前用的是哪本"电话簿"。</p>
     *
     * @param context Android Context，用于获取系统服务
     * @return DNS 服务器地址列表；获取失败时返回空列表
     */
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

    /**
     * 获取当前 WiFi 信号强度描述。
     * <p>使用 WifiManager 获取 RSSI 值，并通过 {@code calculateSignalLevel()} 将其映射为5级信号等级。</p>
     * <p>简单理解：RSSI 是信号强度的原始数值（负数，越接近0越强），
     * 这个方法把它翻译成"弱/一般/中等/良好/强"这样人能看懂的等级。</p>
     *
     * @param context Android Context，用于获取系统服务
     * @return 信号强度描述，格式为 "等级 (RSSI dBm)"；未连接 WiFi 时返回 "未连接WiFi"
     */
    public static String getWifiSignalStrength(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                int rssi = wm.getConnectionInfo().getRssi();
                // 将 RSSI 映射为 0-4 的5级信号等级
                int level = WifiManager.calculateSignalLevel(rssi, 5);
                String[] levels = {"弱", "一般", "中等", "良好", "强"};
                return levels[level] + " (" + rssi + " dBm)";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未连接WiFi";
    }

    /**
     * 测试网络 Ping 延迟。
     * <p>简单理解：Ping 就像在网络世界里"喊一声"，看对方多久回应，
     * 回应越快说明网络越通畅。</p>
     *
     * @return Ping 延迟（毫秒）；失败时返回 -1
     * @see NetworkDiagHelper#testPing()
     */
    public static long testPing() {
        return NetworkDiagHelper.testPing();
    }

    /**
     * 测试网络下载速度。
     * <p>简单理解：就像测你从网上"搬东西"的速度，速度越快下载文件越快。</p>
     *
     * @param serverUrl 测速服务器 URL
     * @return 下载速度（Mbps）；失败时返回 0
     * @see NetworkDiagHelper#testDownloadSpeed(String)
     */
    public static double testDownloadSpeed(String serverUrl) {
        return NetworkDiagHelper.testDownloadSpeed(serverUrl);
    }

    /**
     * 测试网络上传速度。
     * <p>简单理解：就像测你往网上"送东西"的速度，速度越快发图片发视频越快。</p>
     *
     * @param serverUrl 测速服务器 URL
     * @return 上传速度（Mbps）；失败时返回 0
     * @see NetworkDiagHelper#testUploadSpeed(String)
     */
    public static double testUploadSpeed(String serverUrl) {
        return NetworkDiagHelper.testUploadSpeed(serverUrl);
    }

    /**
     * Ping 指定主机，测量延迟。
     *
     * @param host 目标主机地址
     * @return Ping 延迟（毫秒）；失败时返回 -1
     * @see NetworkDiagHelper#pingHost(String)
     */
    public static long pingHost(String host) {
        return NetworkDiagHelper.pingHost(host);
    }

    /**
     * 执行路由追踪的某一跳。
     * <p>简单理解：路由追踪就像寄信时记录信件经过的每一个中转站，
     * TTL（生存时间）控制信件最多能经过几个中转站就"过期"。</p>
     *
     * @param host 目标主机地址
     * @param ttl  生存时间（Time To Live），控制追踪跳数
     * @return 该跳的追踪结果，包含延迟和中间路由 IP
     * @see NetworkDiagHelper#traceRouteHop(String, int)
     */
    public static NetworkDiagHelper.TraceHopResult traceRouteHop(String host, int ttl) {
        return NetworkDiagHelper.traceRouteHop(host, ttl);
    }

    /**
     * 快速获取本机公网 IP 地址。
     * <p>简单理解：公网IP就像你在互联网上的"门牌号"，别人通过这个号码才能找到你。</p>
     *
     * @return 公网 IP 地址字符串；获取失败时返回 null
     * @see NetworkDiagHelper#fetchPublicIpFast()
     */
    public static String fetchPublicIpFast() {
        return NetworkDiagHelper.fetchPublicIpFast();
    }

    /**
     * 根据 IP 地址分类运营商归属。
     * <p>简单理解：就像根据手机号前三位判断是移动还是联通，
     * 这个方法根据IP地址判断是电信、联通还是移动的网络。</p>
     *
     * @param ip IPv4 地址字符串
     * @return 运营商名称（如 "电信"、"联通"、"移动"、"内网" 等）
     * @see IpClassifier#classifyIpCarrier(String)
     */
    public static String classifyIpCarrier(String ip) {
        return IpClassifier.classifyIpCarrier(ip);
    }

    /**
     * 计算子网信息。
     * <p>简单理解：子网就像把一个大办公室隔成几个小隔间，
     * 输入类似 "192.168.1.1/24" 的地址，就能算出这个"隔间"里有哪些地址、
     * 隔间有多大等信息。</p>
     *
     * @param input CIDR 格式的输入，如 "192.168.1.1/24"
     * @return 子网计算结果的多行文本；格式错误时返回错误提示
     * @see SubnetCalculator#calculateSubnet(String)
     */
    public static String calculateSubnet(String input) {
        return SubnetCalculator.calculateSubnet(input);
    }

    /**
     * 使用辗转相除法（欧几里得算法）计算两个整数的最大公约数。
     * <p>简单理解：最大公约数就是两个数"最大的共同因数"，
     * 比如 12 和 8 的最大公约数是 4。辗转相除法就是反复用大数除以小数取余数，
     * 直到余数为0，最后的除数就是答案。</p>
     *
     * @param a 第一个整数
     * @param b 第二个整数
     * @return a 和 b 的最大公约数
     */
    public static int gcd(int a, int b) {
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a;
    }

    /**
     * 获取当前移动网络类型的中文名称。
     * <p>通过 TelephonyManager 获取网络类型常量，并映射为可读的名称。</p>
     *
     * @param context          上下文，用于检查电话状态权限，可为 null
     * @param telephonyManager 电话管理器实例，可为 null
     * @return 网络类型名称（如 "LTE(4G)"、"NR(5G)"）；未连接时返回 "未连接"
     */
    public static String getMobileNetworkType(Context context,
            android.telephony.TelephonyManager telephonyManager) {
        if (context == null || telephonyManager == null) return "未连接";
        if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "未知";
        }
        try {
            int type = telephonyManager.getNetworkType();
            return telephonyNetworkTypeName(type);
        } catch (SecurityException e) {
            // READ_PHONE_STATE may be denied; the diagnostic tool can still report other data.
            return "未知";
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 将 TelephonyManager 网络类型常量映射为可读的中文名称。
     * <p>对于未在 switch 中显式处理的类型，若类型值 >= 20 则判定为 5G 网络。</p>
     * <p>简单理解：系统给每种网络类型编了号，这个方法就是"翻译官"，
     * 把编号翻译成人能看懂的名字，比如编号13翻译成"LTE(4G)"。</p>
     *
     * @param type TelephonyManager 的网络类型常量
     * @return 对应的网络类型名称字符串
     */
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
                // Android 9+ 的 NETWORK_TYPE_NR 常量值为 20，此处用 >= 20 兼容低版本 SDK
                if (type >= 20) return "NR(5G)";
                return "未知(" + type + ")";
        }
    }

    /**
     * 将 Android Sensor 类型常量映射为中文传感器名称。
     * <p>覆盖了常见的传感器类型，未匹配的类型返回 "未知(类型值)"。</p>
     * <p>简单理解：手机里有很多传感器（加速度计、陀螺仪等），
     * 系统给每种传感器编了号，这个方法把编号翻译成中文名。</p>
     *
     * @param type Sensor 类型常量（如 {@link android.hardware.Sensor#TYPE_ACCELEROMETER}）
     * @return 对应的中文传感器名称
     */
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
}

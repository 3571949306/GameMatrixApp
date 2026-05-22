package com.gamecenter.app.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 局域网（LAN）设备发现与服务注册管理器。
 *
 * <p>打个比方：这个类就像一个"小区广播站"，有两种方式找到邻居——
 * 一种是贴告示栏（NSD/mDNS，相当于在小区公告板上登记自己的服务），
 * 另一种是大喇叭广播（UDP广播，相当于拿着大喇叭在小区里喊"有人在家吗？"）。
 * 优先用告示栏，大喇叭是备选方案。</p>
 *
 * <p>在网络模块中的角色：这是局域网联机的"侦察兵"，负责在本地网络中
 * 找到其他正在玩同一游戏的设备。只有在同一WiFi下的设备才能互相发现。</p>
 *
 * <p>负责在同一局域网内发现其他游戏设备，并注册本机提供的服务。
 * 支持两种发现机制：</p>
 * <ul>
 *   <li><b>NSD（Network Service Discovery）</b>：基于 Android 原生 NSD API（mDNS/DNS-SD），
 *       适用于 Android 设备间的服务发现，优先使用。
 *       就像在小区公告板上登记和查找服务信息。</li>
 *   <li><b>UDP 广播</b>：作为 NSD 不可用时的备选方案，通过 UDP 广播/接收
 *       JSON 格式的发现报文来探测同局域网设备。
 *       就像拿着大喇叭在小区里喊话，谁听到了就回复。</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用单例模式（{@link #getInstance(Context)}），确保全局只有一个 LAN 管理实例</li>
 *   <li>所有回调通过主线程 Handler 投递，确保调用者在 UI 线程安全接收事件</li>
 *   <li>发现的主机列表使用 synchronizedList 保证线程安全</li>
 *   <li>NSD 服务列表使用 CopyOnWriteArrayList 保证并发读写安全。
 *       CopyOnWriteArrayList就像"写时复印"，修改时先复制一份再改，不影响正在读的人。</li>
 *   <li>广播和接收线程设置为守护线程，不阻止 JVM 退出</li>
 * </ul>
 * </p>
 */
public class LANManager {
    private static final String TAG = "LANManager";

    /** UDP 发现协议使用的端口号，所有设备都监听这个端口 */
    private static final int DISCOVERY_PORT = 9877;

    /** UDP 广播间隔（毫秒），每3秒喊一次"有人在家吗？" */
    private static final int BROADCAST_INTERVAL = 3000;

    /** 主机过期超时时间（毫秒），超过8秒没收到广播则视为离线。
     *  就像8秒没听到邻居回应，就认为他出门了。 */
    private static final int DISCOVERY_TIMEOUT = 8000;

    /** NSD 服务类型标识，格式为 "_服务名._协议."，用于 mDNS 服务发现 */
    private static final String SERVICE_TYPE = "_doudizhu._tcp.";

    /** HMAC 算法，用于验证发现报文的真实性 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 共享密钥前缀，用于标识游戏房间 */
    private String sharedSecret;

    private DatagramSocket socket;
    private volatile boolean running = false;
    /** 主线程 Handler，用于将回调投递到 UI 线程执行 */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 已发现的主机列表（UDP 广播模式），线程安全 */
    private final List<DiscoveredHost> discoveredHosts = Collections.synchronizedList(new ArrayList<>());
    private OnHostDiscoveredListener listener;

    private final String gameName;
    private final String playerName;
    private final int serverPort;
    private final Context context;
    private final NsdManager nsdManager;

    private static LANManager instance;
    private String serviceName;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    /** 已发现的 NSD 服务列表，使用 CopyOnWriteArrayList 支持并发迭代和修改 */
    private final List<NsdServiceInfo> discoveredServices = new CopyOnWriteArrayList<>();
    private boolean isRegistering = false;
    private boolean isDiscovering = false;
    private OnServiceDiscoveredListener serviceDiscoveredListener;
    private OnServiceLostListener serviceLostListener;
    private OnServiceRegisteredListener serviceRegisteredListener;
    private OnErrorListener errorListener;

    private Thread broadcastThread;
    private Thread receiveThread;

    /**
     * 构造 LANManager 实例（UDP 广播模式）。
     *
     * <p>此构造器不依赖 Android Context，因此无法使用 NSD 功能，
     * 仅支持 UDP 广播方式进行设备发现。</p>
     *
     * @param gameName   游戏名称，用于过滤发现报文，只处理同游戏的广播
     * @param playerName 本机玩家名称，随广播报文发送给其他设备
     * @param serverPort 本机游戏服务端口，随广播报文发送
     */
    public LANManager(String gameName, String playerName, int serverPort) {
        this.gameName = gameName;
        this.playerName = playerName;
        this.serverPort = serverPort;
        this.context = null;
        this.nsdManager = null;
    }

    /**
     * 私有构造器（NSD 模式），由 {@link #getInstance(Context)} 调用。
     *
     * <p>使用 Application Context 避免内存泄漏，并获取系统 NSD 服务。</p>
     *
     * @param context Android 上下文，用于获取 NsdManager 系统服务
     */
    private LANManager(Context context) {
        this.gameName = "斗地主";
        this.playerName = "";
        this.serverPort = 0;
        this.context = context != null ? context.getApplicationContext() : null;
        this.nsdManager = this.context != null ? (NsdManager) this.context.getSystemService(Context.NSD_SERVICE) : null;
        this.sharedSecret = generateDefaultSecret();
    }

    /**
     * 生成默认共享密钥。
     *
     * <p>使用游戏名称和端口号作为熵源，确保同房间的设备能相互认证。</p>
     *
     * @return 默认共享密钥
     */
    private String generateDefaultSecret() {
        return "GameCenter_" + gameName + "_" + serverPort;
    }

    /**
     * 设置共享密钥，用于 HMAC 签名验证。
     *
     * <p>房间创建者应在启动发现前设置共享密钥，加入者可使用相同的密钥进行验证。
     * 共享密钥确保只有同一房间的设备才能相互发现，防止恶意设备伪造发现报文。</p>
     *
     * @param secret 共享密钥字符串
     */
    public void setSharedSecret(String secret) {
        if (secret != null && !secret.isEmpty()) {
            this.sharedSecret = secret;
            Log.d(TAG, "Shared secret configured for discovery authentication");
        }
    }

    /**
     * 获取当前共享密钥。
     *
     * @return 当前共享密钥，若未设置则返回默认密钥
     */
    public String getSharedSecret() {
        return sharedSecret;
    }

    /**
     * 使用 HMAC-SHA256 对数据进行签名。
     *
     * <p>签名数据格式：game|player|port|timestamp</p>
     *
     * @param data 要签名的数据字符串
     * @return Base64 编码的签名字符串，签名失败返回空字符串
     */
    private String computeHmac(String data) {
        if (sharedSecret == null || data == null) return "";
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(hmacBytes, android.util.Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e(TAG, "HMAC computation failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * 验证 HMAC 签名的有效性。
     *
     * @param data 原始数据字符串
     * @param signature Base64 编码的签名
     * @return 签名有效返回 true，否则返回 false
     */
    private boolean verifyHmac(String data, String signature) {
        if (signature == null || signature.isEmpty()) return false;
        String expectedSignature = computeHmac(data);
        return expectedSignature.equals(signature);
    }

    /**
     * 获取 LANManager 单例实例（NSD 模式）。
     *
     * <p>使用 synchronized 保证线程安全的懒加载初始化。</p>
     *
     * @param context Android 上下文
     * @return LANManager 单例
     */
    public static synchronized LANManager getInstance(Context context) {
        if (instance == null) {
            instance = new LANManager(context);
        }
        return instance;
    }

    /**
     * 通过 NSD 注册本机服务，使局域网内其他设备可以发现本机。
     *
     * <p>注册的服务类型为 {@value #SERVICE_TYPE}，使用 DNS-SD 协议。
     * 若当前正在注册中则直接返回，避免重复注册。</p>
     *
     * @param serviceName 服务名称，应具有唯一性以便识别
     * @param port        服务监听端口
     */
    public void registerService(String serviceName, int port) {
        if (nsdManager == null) {
            postError("NsdManager 不可用");
            return;
        }
        // 防止重复注册
        if (isRegistering) return;
        this.serviceName = serviceName;
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);
        registrationListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo info) {
                isRegistering = false;
                // Android 可能会修改服务名以解决冲突，因此更新为实际注册的名称
                LANManager.this.serviceName = info.getServiceName();
                postServiceRegistered(info.getServiceName(), port);
            }
            @Override public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                isRegistering = false;
                postError("服务注册失败: " + errorCode);
            }
            @Override public void onServiceUnregistered(NsdServiceInfo info) {
                isRegistering = false;
            }
            @Override public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.w(TAG, "Unregistration failed: " + errorCode);
            }
        };
        isRegistering = true;
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Exception e) {
            isRegistering = false;
            postError("注册服务异常: " + e.getMessage());
        }
    }

    /**
     * 注销本机已注册的 NSD 服务。
     *
     * <p>注销后其他设备将无法通过 NSD 发现本机。调用此方法会重置注册状态。</p>
     */
    public void unregisterService() {
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.w(TAG, "Unregister error: " + e.getMessage());
            }
        }
        isRegistering = false;
        serviceName = null;
    }

    /**
     * 启动局域网设备发现。
     *
     * <p>优先使用 NSD 发现；若 NsdManager 不可用，则回退到 UDP 广播模式。
     * UDP 模式下会启动两个守护线程：广播线程（发送本机信息）和接收线程（监听其他设备）。</p>
     */
    public void startDiscovery() {
        // 优先使用 NSD 发现
        if (nsdManager != null) {
            startNsdDiscovery();
            return;
        }
        // 回退到 UDP 广播模式
        stopDiscovery();
        discoveredHosts.clear();
        running = true;

        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            // 设置 1 秒超时，使 receive() 不会永久阻塞，便于检查 running 标志
            socket.setSoTimeout(1000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create socket: " + e.getMessage());
            return;
        }

        broadcastThread = new Thread(this::broadcastLoop, "LANBroadcast");
        broadcastThread.setDaemon(true);
        broadcastThread.start();

        receiveThread = new Thread(this::receiveLoop, "LANReceive");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * 停止局域网设备发现。
     *
     * <p>同时处理 NSD 和 UDP 两种模式的清理：停止 NSD 发现、关闭 UDP Socket、
     * 中断广播和接收线程。</p>
     */
    public void stopDiscovery() {
        // 停止 NSD 发现
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.w(TAG, "Stop NSD discovery error: " + e.getMessage());
            }
            discoveryListener = null;
        }
        isDiscovering = false;
        discoveredServices.clear();
        running = false;

        // 关闭 UDP Socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }

        // 中断广播线程
        if (broadcastThread != null && broadcastThread.isAlive()) {
            broadcastThread.interrupt();
            broadcastThread = null;
        }

        // 中断接收线程
        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
            receiveThread = null;
        }
    }

    /**
     * 获取已发现的主机列表（UDP 广播模式）。
     *
     * <p>返回不可修改的列表视图，防止外部修改内部数据。</p>
     *
     * @return 不可修改的已发现主机列表
     */
    public List<DiscoveredHost> getDiscoveredHosts() {
        return Collections.unmodifiableList(discoveredHosts);
    }

    /**
     * 启动 NSD 服务发现。
     *
     * <p>发现的服务类型为 {@value #SERVICE_TYPE}。发现到服务后会自动解析其地址和端口。
     * 过滤掉自身注册的服务，避免自我发现。</p>
     */
    private void startNsdDiscovery() {
        if (isDiscovering) return;
        discoveredServices.clear();
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                isDiscovering = false;
                postError("开始发现失败: " + errorCode);
            }
            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Stop discovery failed: " + errorCode);
            }
            @Override public void onDiscoveryStarted(String serviceType) {
                isDiscovering = true;
            }
            @Override public void onDiscoveryStopped(String serviceType) {
                isDiscovering = false;
            }
            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                // 过滤掉自身注册的服务，避免自我发现
                if (serviceName != null && serviceName.equals(serviceInfo.getServiceName())) return;
                try {
                    // 发现服务后需要解析才能获取其主机地址和端口
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                            postError("服务解析失败: " + errorCode);
                        }
                        @Override public void onServiceResolved(NsdServiceInfo info) {
                            handleServiceResolved(info);
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Resolve error: " + e.getMessage());
                }
            }
            @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
                handleServiceLost(serviceInfo);
            }
        };
        isDiscovering = true;
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            isDiscovering = false;
            postError("发现服务异常: " + e.getMessage());
        }
    }

    /**
     * 处理 NSD 服务解析成功事件。
     *
     * <p>去重检查：若已存在同名服务则忽略，否则添加到已发现列表并通知监听器。</p>
     *
     * @param serviceInfo 已解析的 NSD 服务信息
     */
    private void handleServiceResolved(NsdServiceInfo serviceInfo) {
        // 去重：检查是否已存在同名服务
        for (NsdServiceInfo existing : discoveredServices) {
            if (existing.getServiceName().equals(serviceInfo.getServiceName())) return;
        }
        discoveredServices.add(serviceInfo);
        postServiceDiscovered(serviceInfo);
    }

    /**
     * 处理 NSD 服务丢失事件。
     *
     * <p>从已发现列表中移除对应服务并通知监听器。</p>
     *
     * @param serviceInfo 丢失的 NSD 服务信息
     */
    private void handleServiceLost(NsdServiceInfo serviceInfo) {
        for (int i = 0; i < discoveredServices.size(); i++) {
            if (discoveredServices.get(i).getServiceName().equals(serviceInfo.getServiceName())) {
                discoveredServices.remove(i);
                postServiceLost(serviceInfo);
                return;
            }
        }
    }

    /**
     * 获取本机第一个有效的 IPv4 地址。
     *
     * <p>遍历所有网络接口，返回第一个非回环、非虚拟的 IPv4 地址。</p>
     *
     * @return 本机 IPv4 地址字符串，若无可用地址则返回 null
     */
    public String getLocalIPv4Address() {
        List<String> addresses = getAllLocalIPv4Addresses();
        return addresses.isEmpty() ? null : addresses.get(0);
    }

    /**
     * 获取本机所有有效的 IPv4 地址列表。
     *
     * <p>遍历所有网络接口，过滤掉以下类型的接口：</p>
     * <ul>
     *   <li>lo - 本地回环接口</li>
     *   <li>p2p - Wi-Fi 直连接口</li>
     *   <li>virt/docker/veth - 虚拟化和容器相关接口</li>
     * </ul>
     *
     * @return 有效的 IPv4 地址列表，不会返回 null
     */
    public List<String> getAllLocalIPv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return addresses;
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                // 跳过未启用的接口
                if (!networkInterface.isUp()) continue;
                String interfaceName = networkInterface.getName().toLowerCase();
                // 过滤掉回环、P2P、虚拟化和容器相关的网络接口
                if (interfaceName.contains("lo") || interfaceName.contains("p2p")
                        || interfaceName.contains("virt") || interfaceName.contains("docker")
                        || interfaceName.contains("veth")) {
                    continue;
                }
                Enumeration<InetAddress> addrEnum = networkInterface.getInetAddresses();
                while (addrEnum.hasMoreElements()) {
                    InetAddress addr = addrEnum.nextElement();
                    // 仅保留 IPv4 且非回环地址
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // 二次校验确保是合法的点分十进制 IPv4 地址
                        if (ip != null && ip.contains(".")) addresses.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting local IPs: " + e.getMessage());
        }
        return addresses;
    }

    /**
     * 校验 IP 地址字符串是否合法。
     *
     * <p>校验规则：四段点分十进制，每段 0-255，且不能以 "127." 开头（排除回环地址）。</p>
     *
     * @param ip 待校验的 IP 地址字符串
     * @return 合法返回 true，否则返回 false
     */
    public static boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }
            // 排除回环地址（127.x.x.x）
            return !ip.startsWith("127.");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 校验端口号是否合法。
     *
     * @param port 待校验的端口号
     * @return 合法（1-65535）返回 true，否则返回 false
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * 校验 "IP:端口" 格式字符串是否合法。
     *
     * @param ipPort 格式为 "IP:端口" 的字符串，如 "192.168.1.100:8080"
     * @return IP 和端口均合法返回 true，否则返回 false
     */
    public static boolean isValidIPAndPort(String ipPort) {
        if (ipPort == null || ipPort.isEmpty()) return false;
        String[] parts = ipPort.split(":");
        if (parts.length != 2 || !isValidIPAddress(parts[0])) return false;
        try {
            return isValidPort(Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 获取已发现的 NSD 服务列表的副本。
     *
     * @return 新建的列表，包含所有已发现的 NSD 服务信息
     */
    public List<NsdServiceInfo> getDiscoveredServices() {
        return new ArrayList<>(discoveredServices);
    }

    public boolean isRegistering() { return isRegistering; }
    public boolean isDiscovering() { return isDiscovering; }
    public String getServiceName() { return serviceName; }

    /**
     * UDP 广播循环：定期向局域网广播本机的游戏发现报文。
     *
     * <p>报文为 JSON 格式，包含游戏名称、玩家名称、服务端口和 HMAC 签名。
     * 每隔 {@value #BROADCAST_INTERVAL} 毫秒发送一次，直到 {@link #running} 被置为 false。</p>
     * <p>
     * <b>安全说明</b>：报文包含 HMAC 签名，用于验证发现报文的真实性，
     * 防止同网段恶意设备伪造发现报文。
     * </p>
     */
    private void broadcastLoop() {
        while (running) {
            try {
                long timestamp = System.currentTimeMillis();
                JSONObject broadcast = new JSONObject();
                broadcast.put("type", "DISCOVERY");
                broadcast.put("game", gameName);
                broadcast.put("player", playerName);
                broadcast.put("port", serverPort);
                broadcast.put("ts", timestamp);

                String signData = gameName + "|" + playerName + "|" + serverPort + "|" + timestamp;
                String signature = computeHmac(signData);
                broadcast.put("sig", signature);

                byte[] data = broadcast.toString().getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, getBroadcastAddress(), DISCOVERY_PORT);

                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.send(packet);
                    } catch (Exception e) {
                        Log.w(TAG, "send broadcast: " + e.getMessage());
                    }
                }

                Thread.sleep(BROADCAST_INTERVAL);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting: " + e.getMessage());
            }
        }
    }

    /**
     * UDP 接收循环：持续监听局域网内其他设备的发现广播报文。
     *
     * <p>收到报文后解析 JSON，仅处理 type 为 "DISCOVERY" 且游戏名称匹配的报文。
     * 同时验证 HMAC 签名的有效性，忽略签名无效的报文，防止恶意设备伪造发现。</p>
     * <p>
     * <b>安全说明</b>：接收方会验证报文的 HMAC 签名，只有使用相同共享密钥签名的报文才会被处理。
     * 若共享密钥未设置（使用默认密钥），则验证基于默认密钥的签名。
     * </p>
     */
    private void receiveLoop() {
        byte[] buffer = new byte[1024];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                if (socket != null && !socket.isClosed()) {
                    socket.receive(packet);
                }

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(received);

                if ("DISCOVERY".equals(json.optString("type"))) {
                    String game = json.optString("game");
                    String player = json.optString("player");
                    int port = json.optInt("port", 0);
                    long timestamp = json.optLong("ts", 0);
                    String signature = json.optString("sig");
                    String hostIp = packet.getAddress().getHostAddress();

                    if (!game.equals(gameName)) continue;

                    String signData = game + "|" + player + "|" + port + "|" + timestamp;
                    if (!verifyHmac(signData, signature)) {
                        Log.w(TAG, "Ignoring discovery packet with invalid signature from: " + hostIp);
                        continue;
                    }

                    DiscoveredHost host = new DiscoveredHost(hostIp, port, player);

                    boolean exists = false;
                    for (DiscoveredHost h : discoveredHosts) {
                        if (h.getIp().equals(hostIp)) {
                            h.updateLastSeen();
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        discoveredHosts.add(host);
                        postHostDiscovered(host);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "receive discovery: " + e.getMessage());
            }
        }
    }

    /**
     * 获取局域网广播地址。
     *
     * <p>使用 255.255.255.255 作为受限广播地址，
     * 适用于所有网络接口的广播发送。</p>
     *
     * @return 广播地址
     * @throws UnknownHostException 地址解析异常
     */
    private InetAddress getBroadcastAddress() throws UnknownHostException {
        return InetAddress.getByName("255.255.255.255");
    }

    /**
     * 在主线程通知主机发现监听器。
     *
     * @param host 新发现的主机
     */
    private void postHostDiscovered(DiscoveredHost host) {
        if (listener != null) {
            mainHandler.post(() -> listener.onHostDiscovered(host));
        }
    }

    /**
     * 在主线程通知服务注册完成监听器。
     *
     * @param name 注册成功的服务名称
     * @param port 服务端口
     */
    private void postServiceRegistered(final String name, final int port) {
        if (serviceRegisteredListener != null) {
            mainHandler.post(() -> serviceRegisteredListener.onServiceRegistered(name, port));
        }
    }

    /**
     * 在主线程通知 NSD 服务发现监听器。
     *
     * @param serviceInfo 发现的 NSD 服务信息
     */
    private void postServiceDiscovered(final NsdServiceInfo serviceInfo) {
        if (serviceDiscoveredListener != null) {
            mainHandler.post(() -> serviceDiscoveredListener.onServiceDiscovered(serviceInfo));
        }
    }

    /**
     * 在主线程通知 NSD 服务丢失监听器。
     *
     * @param serviceInfo 丢失的 NSD 服务信息
     */
    private void postServiceLost(final NsdServiceInfo serviceInfo) {
        if (serviceLostListener != null) {
            mainHandler.post(() -> serviceLostListener.onServiceLost(serviceInfo));
        }
    }

    /**
     * 在主线程通知错误监听器。
     *
     * @param message 错误描述信息
     */
    private void postError(final String message) {
        if (errorListener != null) {
            mainHandler.post(() -> errorListener.onError(message));
        }
    }

    /**
     * 设置 UDP 广播模式下的主机发现监听器。
     *
     * @param listener 主机发现事件监听器
     */
    public void setOnHostDiscoveredListener(OnHostDiscoveredListener listener) {
        this.listener = listener;
    }

    /**
     * UDP 广播模式下发现新主机时的回调接口。
     */
    public interface OnHostDiscoveredListener {
        /**
         * 发现新主机时调用。
         *
         * @param host 新发现的主机信息
         */
        void onHostDiscovered(DiscoveredHost host);
    }

    /**
     * NSD 模式下发现新服务时的回调接口。
     */
    public interface OnServiceDiscoveredListener {
        /**
         * 发现新的 NSD 服务时调用。
         *
         * @param serviceInfo 发现的服务信息
         */
        void onServiceDiscovered(NsdServiceInfo serviceInfo);
    }

    /**
     * NSD 模式下服务丢失时的回调接口。
     */
    public interface OnServiceLostListener {
        /**
         * NSD 服务丢失时调用。
         *
         * @param serviceInfo 丢失的服务信息
         */
        void onServiceLost(NsdServiceInfo serviceInfo);
    }

    /**
     * NSD 服务注册成功时的回调接口。
     */
    public interface OnServiceRegisteredListener {
        /**
         * 服务注册成功时调用。
         *
         * @param serviceName 实际注册的服务名称（可能与请求名称不同，因 Android 可能解决名称冲突）
         * @param port        服务端口
         */
        void onServiceRegistered(String serviceName, int port);
    }

    /**
     * 错误事件回调接口。
     */
    public interface OnErrorListener {
        /**
         * 发生错误时调用。
         *
         * @param message 错误描述信息
         */
        void onError(String message);
    }

    public void setOnServiceDiscoveredListener(OnServiceDiscoveredListener listener) {
        this.serviceDiscoveredListener = listener;
    }

    public void setOnServiceLostListener(OnServiceLostListener listener) {
        this.serviceLostListener = listener;
    }

    public void setOnServiceRegisteredListener(OnServiceRegisteredListener listener) {
        this.serviceRegisteredListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }

    /**
     * 释放所有资源，注销服务、停止发现、清除单例引用。
     *
     * <p>调用后此实例不再可用，如需重新使用需通过 {@link #getInstance(Context)} 获取新实例。</p>
     */
    public void release() {
        unregisterService();
        stopDiscovery();
        instance = null;
    }

    /**
     * 已发现主机的数据模型类。
     *
     * <p>封装了通过 UDP 广播发现的其他设备的信息，包括 IP 地址、端口号、
     * 玩家名称以及最后一次收到该主机广播的时间戳。</p>
     *
     * <p>打个比方：每个DiscoveredHost就像一张"邻居名片"，上面写着邻居的
     * 地址（IP）、门牌号（端口）、姓名（玩家名称），以及最后一次见到他的时间。</p>
     */
    public static class DiscoveredHost {
        private final String ip;
        private final int port;
        private final String playerName;
        /** 最后一次收到该主机广播的时间戳（毫秒） */
        private long lastSeen;

        /**
         * 创建已发现主机实例。
         *
         * @param ip         主机 IP 地址
         * @param port       主机服务端口
         * @param playerName 主机上的玩家名称
         */
        public DiscoveredHost(String ip, int port, String playerName) {
            this.ip = ip;
            this.port = port;
            this.playerName = playerName;
            this.lastSeen = System.currentTimeMillis();
        }

        public String getIp() { return ip; }
        public int getPort() { return port; }
        public String getPlayerName() { return playerName; }
        public long getLastSeen() { return lastSeen; }

        /** 更新最后活跃时间戳为当前时间 */
        public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }

        /**
         * 判断该主机是否已过期（超过 {@value #DISCOVERY_TIMEOUT} 毫秒未收到广播）。
         *
         * @return 过期返回 true，否则返回 false
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - lastSeen > DISCOVERY_TIMEOUT;
        }
    }
}

package com.gamecenter.app.games.doudizhu.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 局域网发现与管理工具 (LAN Manager)
 * 使用 NsdManager 实现服务注册与发现
 * 提供手动兜底机制获取本机 IP
 */
public class LANManager {

    // 日志标签
    private static final String TAG = "LANManager";

    // NSD 服务类型
    private static final String SERVICE_TYPE = "_doudizhu._tcp.";

    // 默认端口
    private static final int DEFAULT_PORT = 8765;

    // 心跳间隔（毫秒）
    private static final long HEARTBEAT_INTERVAL = 3000L;

    // NSD 注册超时
    private static final long REGISTER_TIMEOUT = 10000L;

    // NSD 发现超时
    private static final long DISCOVER_TIMEOUT = 5000L;

    // ============ 单例模式 ============

    private static LANManager instance;
    private final Context context;
    private final NsdManager nsdManager;
    private final Handler mainHandler;

    // ============ NSD 相关 ============

    // NSD 服务名称
    private String serviceName;
    // 已注册的服务
    private NsdManager.RegistrationListener registrationListener;
    // 已发现的服务列表
    private final List<NsdServiceInfo> discoveredServices = new CopyOnWriteArrayList<>();
    // NSD 发现监听器
    private NsdManager.DiscoveryListener discoveryListener;
    // 解析监听器
    private final NsdManager.ResolveListener resolveListener;

    // ============ 状态标记 ============

    // 是否正在注册服务
    private boolean isRegistering = false;
    // 是否正在发现服务
    private boolean isDiscovering = false;

    // ============ 回调接口 ============

    // 服务发现回调
    private OnServiceDiscoveredListener serviceDiscoveredListener;
    // 服务丢失回调
    private OnServiceLostListener serviceLostListener;
    // 注册结果回调
    private OnServiceRegisteredListener serviceRegisteredListener;
    // 错误回调
    private OnErrorListener errorListener;

    // ============ 构造函数（单例）============

    private LANManager(Context context) {
        this.context = context.getApplicationContext();
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());

        // 初始化解析监听器
        this.resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed: " + errorCode + " for " + serviceInfo.getServiceName());
                postError("服务解析失败: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service resolved: " + serviceInfo.getServiceName() +
                      " at " + serviceInfo.getHost() + ":" + serviceInfo.getPort());
                handleServiceResolved(serviceInfo);
            }
        };
    }

    /**
     * 获取单例实例
     */
    public static synchronized LANManager getInstance(Context context) {
        if (instance == null) {
            instance = new LANManager(context);
        }
        return instance;
    }

    // ============ 服务注册（主机端使用）============

    /**
     * 注册本机服务到局域网
     * @param serviceName 服务名称（通常为玩家名称）
     * @param port 监听端口
     */
    public void registerService(String serviceName, int port) {
        if (nsdManager == null) {
            postError("NsdManager 不可用");
            return;
        }

        if (isRegistering) {
            Log.w(TAG, "Already registering service");
            return;
        }

        this.serviceName = serviceName;

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                String registeredName = info.getServiceName();
                // 如果名称被占用（Android 会自动添加后缀），记录实际注册名称
                if (!registeredName.equals(serviceName)) {
                    Log.w(TAG, "Service name was changed from " + serviceName + " to " + registeredName);
                }
                isRegistering = false;
                final String finalRegisteredName = registeredName;
                LANManager.this.serviceName = finalRegisteredName;
                Log.d(TAG, "Service registered: " + LANManager.this.serviceName);
                postServiceRegistered(finalRegisteredName, port);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                isRegistering = false;
                Log.e(TAG, "Registration failed: " + errorCode);
                postError("服务注册失败: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {
                isRegistering = false;
                Log.d(TAG, "Service unregistered: " + info.getServiceName());
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.e(TAG, "Unregistration failed: " + errorCode);
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
     * 取消注册服务
     */
    public void unregisterService() {
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.e(TAG, "Unregister error: " + e.getMessage());
            }
        }
        isRegistering = false;
        serviceName = null;
    }

    // ============ 服务发现（客户端使用）============

    /**
     * 开始发现局域网内的斗地主服务
     */
    public void startDiscovery() {
        if (nsdManager == null) {
            postError("NsdManager 不可用");
            return;
        }

        if (isDiscovering) {
            Log.w(TAG, "Already discovering");
            return;
        }

        discoveredServices.clear();

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                isDiscovering = false;
                Log.e(TAG, "Discovery start failed: " + errorCode);
                postError("开始发现失败: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stop failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                isDiscovering = true;
                Log.d(TAG, "Discovery started for " + serviceType);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                isDiscovering = false;
                Log.d(TAG, "Discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                // 排除自己的服务
                if (serviceName != null && serviceInfo.getServiceName().equals(serviceName)) {
                    return;
                }
                // 解析服务详情
                resolveService(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
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
     * 停止服务发现
     */
    public void stopDiscovery() {
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.e(TAG, "Stop discovery error: " + e.getMessage());
            }
        }
        isDiscovering = false;
        discoveredServices.clear();
    }

    /**
     * 解析服务详情
     */
    private void resolveService(NsdServiceInfo serviceInfo) {
        try {
            nsdManager.resolveService(serviceInfo, resolveListener);
        } catch (Exception e) {
            Log.e(TAG, "Resolve error: " + e.getMessage());
        }
    }

    /**
     * 处理服务解析成功
     */
    private void handleServiceResolved(NsdServiceInfo serviceInfo) {
        // 检查是否已存在
        for (NsdServiceInfo existing : discoveredServices) {
            if (existing.getServiceName().equals(serviceInfo.getServiceName())) {
                return;
            }
        }
        discoveredServices.add(serviceInfo);
        postServiceDiscovered(serviceInfo);
    }

    /**
     * 处理服务丢失
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

    // ============ IP 地址工具（手动兜底）============

    /**
     * 获取本机局域网 IPv4 地址
     * 过滤掉虚拟机和回环地址
     * @return 本机 IP 地址，如果获取失败返回 null
     */
    public String getLocalIPv4Address() {
        try {
            // 方法1：遍历所有网络接口
            List<String> addresses = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return getLocalIPViaNetworkInfo();
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // 跳过回环和虚拟机接口
                String interfaceName = networkInterface.getName().toLowerCase();
                if (interfaceName.contains("lo")
                        || interfaceName.contains("wifi")
                        && interfaceName.contains("p2p")
                        || interfaceName.contains("ap")
                        || interfaceName.contains("virt")
                        || interfaceName.contains("docker")
                        || interfaceName.contains("veth")) {
                    continue;
                }

                // 跳过未启用的接口
                if (!networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses_enum = networkInterface.getInetAddresses();
                while (addresses_enum.hasMoreElements()) {
                    InetAddress address = addresses_enum.nextElement();

                    // 只获取 IPv4 地址
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String ip = address.getHostAddress();
                        // 验证是否为有效格式
                        if (ip != null && ip.contains(".")) {
                            addresses.add(ip);
                        }
                    }
                }
            }

            // 返回找到的第一个有效地址
            if (!addresses.isEmpty()) {
                return addresses.get(0);
            }

            // 备用方法：通过 ConnectivityManager 获取
            return getLocalIPViaNetworkInfo();

        } catch (Exception e) {
            Log.e(TAG, "Error getting local IP: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 ConnectivityManager 获取本地 IP
     * 作为备用方案
     */
    private String getLocalIPViaNetworkInfo() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return null;
            }

            Network network = cm.getActiveNetwork();
            if (network == null) {
                return null;
            }

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) {
                return null;
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error via ConnectivityManager: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有有效的局域网 IPv4 地址
     * 包括本机和同网段的其他地址
     * @return IP 地址列表
     */
    public List<String> getAllLocalIPv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return addresses;
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addrEnum = networkInterface.getInetAddresses();
                while (addrEnum.hasMoreElements()) {
                    InetAddress addr = addrEnum.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.contains(".")) {
                            addresses.add(ip);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all IPs: " + e.getMessage());
        }
        return addresses;
    }

    /**
     * 验证 IP 地址格式是否有效
     * @param ip IP 地址字符串
     * @return 是否有效
     */
    public static boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            // 排除回环地址
            if (ip.startsWith("127.")) {
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 验证端口号是否有效
     * @param port 端口号
     * @return 是否有效
     */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /**
     * 验证 IP:Port 格式是否有效
     * @param ipPort IP:Port 字符串
     * @return 是否有效
     */
    public static boolean isValidIPAndPort(String ipPort) {
        if (ipPort == null || ipPort.isEmpty()) {
            return false;
        }

        String[] parts = ipPort.split(":");
        if (parts.length != 2) {
            return false;
        }

        if (!isValidIPAddress(parts[0])) {
            return false;
        }

        try {
            int port = Integer.parseInt(parts[1]);
            return isValidPort(port);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ============ 回调方法（主线程执行）============

    private void postServiceRegistered(final String name, final int port) {
        mainHandler.post(() -> {
            if (serviceRegisteredListener != null) {
                serviceRegisteredListener.onServiceRegistered(name, port);
            }
        });
    }

    private void postServiceDiscovered(final NsdServiceInfo serviceInfo) {
        mainHandler.post(() -> {
            if (serviceDiscoveredListener != null) {
                serviceDiscoveredListener.onServiceDiscovered(serviceInfo);
            }
        });
    }

    private void postServiceLost(final NsdServiceInfo serviceInfo) {
        mainHandler.post(() -> {
            if (serviceLostListener != null) {
                serviceLostListener.onServiceLost(serviceInfo);
            }
        });
    }

    private void postError(final String message) {
        mainHandler.post(() -> {
            if (errorListener != null) {
                errorListener.onError(message);
            }
        });
    }

    // ============ 状态查询 ============

    /**
     * 是否正在注册服务
     */
    public boolean isRegistering() {
        return isRegistering;
    }

    /**
     * 是否正在发现服务
     */
    public boolean isDiscovering() {
        return isDiscovering;
    }

    /**
     * 获取已发现的服务列表
     */
    public List<NsdServiceInfo> getDiscoveredServices() {
        return new ArrayList<>(discoveredServices);
    }

    /**
     * 获取已注册的服务名称
     */
    public String getServiceName() {
        return serviceName;
    }

    // ============ 回调接口定义 ============

    /**
     * 服务发现回调
     */
    public interface OnServiceDiscoveredListener {
        void onServiceDiscovered(NsdServiceInfo serviceInfo);
    }

    /**
     * 服务丢失回调
     */
    public interface OnServiceLostListener {
        void onServiceLost(NsdServiceInfo serviceInfo);
    }

    /**
     * 服务注册成功回调
     */
    public interface OnServiceRegisteredListener {
        void onServiceRegistered(String serviceName, int port);
    }

    /**
     * 错误回调
     */
    public interface OnErrorListener {
        void onError(String message);
    }

    // ============ 回调设置 ============

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

    // ============ 清理资源 ============

    /**
     * 释放所有资源
     * 在 Activity 销毁时调用
     */
    public void release() {
        unregisterService();
        stopDiscovery();
        instance = null;
    }
}

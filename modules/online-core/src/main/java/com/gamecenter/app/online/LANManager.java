package com.gamecenter.app.online;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 局域网管理器。
 * 
 * 负责局域网联机功能：
 * - 广播发现局域网内的游戏房间
 * -  advertise 自己创建的房间
 * - 连接到局域网内的其他玩家
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class LANManager {
    
    private static final String TAG = "LANManager";
    
    /** 广播端口：8888 */
    private static final int DISCOVERY_PORT = 8888;
    
    /** 广播间隔：3 秒 */
    private static final long DISCOVERY_INTERVAL_MS = 3000L;
    
    /** 上下文 */
    private final Context context;
    
    /** 线程池 */
    private final ExecutorService executorService;
    
    /** 是否正在广播 */
    private volatile boolean isAdvertising;
    
    /** 是否正在发现 */
    private volatile boolean isDiscovering;
    
    /** 广播线程 */
    private Thread advertiseThread;
    
    /** 发现线程 */
    private Thread discoverThread;
    
    /** 局域网房间发现监听器 */
    private DiscoveryListener discoveryListener;
    
    /**
     * 构造函数。
     * 
     * @param context Android Context
     */
    public LANManager(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.executorService = Executors.newCachedThreadPool();
        this.isAdvertising = false;
        this.isDiscovering = false;
    }
    
    /**
     * 开始广播房间（advertise）。
     * 
     * @param roomName 房间名称
     * @param port      房间端口
     * @return 启动成功返回 true，否则返回 false
     */
    public boolean startAdvertising(@NonNull String roomName, int port) {
        if (isAdvertising) {
            Log.w(TAG, "已经在广播中");
            return false;
        }
        
        isAdvertising = true;
        
        advertiseThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                
                while (isAdvertising) {
                    // 构建广播消息
                    String message = buildAdvertiseMessage(roomName, port);
                    byte[] data = message.getBytes();
                    
                    // 发送到广播地址
                    DatagramPacket packet = new DatagramPacket(
                            data, 
                            data.length,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT
                    );
                    
                    socket.send(packet);
                    Log.d(TAG, "广播房间: " + roomName);
                    
                    // 等待下次广播
                    Thread.sleep(DISCOVERY_INTERVAL_MS);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "广播失败", e);
            }
        });
        
        advertiseThread.setName("LAN-Advertise-Thread");
        advertiseThread.setDaemon(true);
        advertiseThread.start();
        
        Log.i(TAG, "开始广播房间: " + roomName);
        return true;
    }
    
    /**
     * 停止广播。
     */
    public void stopAdvertising() {
        if (!isAdvertising) {
            return;
        }
        
        isAdvertising = false;
        
        if (advertiseThread != null) {
            advertiseThread.interrupt();
            advertiseThread = null;
        }
        
        Log.i(TAG, "停止广播");
    }
    
    /**
     * 开始发现局域网房间。
     * 
     * @param listener 发现监听器
     * @return 启动成功返回 true，否则返回 false
     */
    public boolean startDiscovery(@NonNull DiscoveryListener listener) {
        if (isDiscovering) {
            Log.w(TAG, "已经在发现中");
            return false;
        }
        
        this.discoveryListener = listener;
        isDiscovering = true;
        
        discoverThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                socket.setBroadcast(true);
                
                byte[] buffer = new byte[1024];
                
                while (isDiscovering) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, packet.getLength());
                    Log.d(TAG, "发现房间: " + message);
                    
                    // 解析房间信息
                    RoomInfo roomInfo = parseAdvertiseMessage(message, packet.getAddress().getHostAddress());
                    
                    if (roomInfo != null && discoveryListener != null) {
                        discoveryListener.onRoomDiscovered(roomInfo);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "发现失败", e);
            }
        });
        
        discoverThread.setName("LAN-Discovery-Thread");
        discoverThread.setDaemon(true);
        discoverThread.start();
        
        Log.i(TAG, "开始发现局域网房间");
        return true;
    }
    
    /**
     * 停止发现。
     */
    public void stopDiscovery() {
        if (!isDiscovering) {
            return;
        }
        
        isDiscovering = false;
        
        if (discoverThread != null) {
            discoverThread.interrupt();
            discoverThread = null;
        }
        
        Log.i(TAG, "停止发现");
    }
    
    /**
     * 构建广播消息。
     * 
     * @param roomName 房间名称
     * @param port      房间端口
     * @return 广播消息
     */
    @NonNull
    private String buildAdvertiseMessage(@NonNull String roomName, int port) {
        return "ROOM:" + roomName + "|PORT:" + port + "|IP:" + getLocalIpAddress();
    }
    
    /**
     * 解析广播消息。
     * 
     * @param message   广播消息
     * @param ipAddress 发送方 IP 地址
     * @return 房间信息，解析失败返回 null
     */
    @NonNull
    private RoomInfo parseAdvertiseMessage(@NonNull String message, @NonNull String ipAddress) {
        try {
            String roomName = "";
            int port = 0;
            
            String[] parts = message.split("\\|");
            for (String part : parts) {
                if (part.startsWith("ROOM:")) {
                    roomName = part.substring(5);
                } else if (part.startsWith("PORT:")) {
                    port = Integer.parseInt(part.substring(5));
                }
            }
            
            if (!roomName.isEmpty() && port > 0) {
                return new RoomInfo(roomName, ipAddress, port);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析广播消息失败: " + message, e);
        }
        
        return null;
    }
    
    /**
     * 获取本地 IP 地址。
     * 
     * @return 本地 IP 地址
     */
    @NonNull
    private String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                int ip = wifiManager.getConnectionInfo().getIpAddress();
                return (ip & 0xFF) + "." + 
                       ((ip >> 8) & 0xFF) + "." + 
                       ((ip >> 16) & 0xFF) + "." + 
                       ((ip >> 24) & 0xFF);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取本地 IP 失败", e);
        }
        
        return "127.0.0.1";
    }
    
    /**
     * 释放所有资源。
     */
    public void release() {
        stopAdvertising();
        stopDiscovery();
        
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        Log.d(TAG, "LANManager 资源已释放");
    }
    
    // ========= 内部类 =========
    
    /**
     * 局域网房间信息。
     */
    public static class RoomInfo {
        @NonNull
        public final String roomName;
        @NonNull
        public final String ipAddress;
        public final int port;
        
        public RoomInfo(@NonNull String roomName, @NonNull String ipAddress, int port) {
            this.roomName = roomName;
            this.ipAddress = ipAddress;
            this.port = port;
        }
        
        @Override
        @NonNull
        public String toString() {
            return "RoomInfo{name='" + roomName + '\'' + 
                   ", ip='" + ipAddress + '\'' + 
                   ", port=" + port + '}';
        }
    }
    
    // ========= 监听器接口 =========
    
    /**
     * 局域网房间发现监听器。
     */
    public interface DiscoveryListener {
        /**
         * 发现房间。
         * 
         * @param roomInfo 房间信息
         */
        void onRoomDiscovered(@NonNull RoomInfo roomInfo);
        
        /**
         * 发现失败。
         * 
         * @param errorMessage 错误信息
         */
        void onDiscoveryFailed(@NonNull String errorMessage);
    }
}

package com.gamecenter.app.online;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏 WebSocket 服务器（单机模式）。
 * 
 * <p>在本地开启 WebSocket 服务器，用于局域网单机对战。
 * 房主端运行服务器，客户端通过局域网 IP 连接。</p>
 * 
 * <p>使用 {@link WebSocketHandler} 处理 WebSocket 协议握手和消息帧。</p>
 * 
 * @author Software Engineer (Alex)
 * @version 2.0
 * @since 2026-05-26
 */
public class GameSocketServer {
    
    private static final String TAG = "GameSocketServer";
    
    /** 默认端口：8080 */
    private static final int DEFAULT_PORT = 8080;
    
    /** 服务器 Socket */
    private ServerSocket serverSocket;
    
    /** WebSocket 协议处理器 */
    private WebSocketHandler webSocketHandler;
    
    /** 线程池（处理客户端连接） */
    private ExecutorService threadPool;
    
    /** 服务器端口 */
    private int port;
    
    /** 是否正在运行 */
    private volatile boolean running;
    
    /** 上下文 */
    private final Context context;
    
    /**
     * 构造函数。
     * 
     * @param context Android Context
     */
    public GameSocketServer(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.port = DEFAULT_PORT;
        this.running = false;
        this.webSocketHandler = new WebSocketHandler();
        this.threadPool = Executors.newCachedThreadPool();
        
        // 设置消息监听器
        this.webSocketHandler.setMessageListener(new WebSocketHandler.MessageListener() {
            @Override
            public void onMessage(@NonNull Socket client, @NonNull String message) {
                // 收到消息后广播给所有其他客户端（简化实现）
                Log.d(TAG, "收到消息 from " + client.getInetAddress().getHostAddress() + ": " + message);
                broadcast(message, client);
            }
            
            @Override
            public void onDisconnected(@NonNull Socket client) {
                Log.d(TAG, "客户端断开: " + client.getInetAddress().getHostAddress());
            }
        });
    }
    
    /**
     * 启动服务器。
     * 
     * @param port 监听端口
     * @return 启动成功返回 true，否则返回 false
     */
    public boolean start(int port) {
        if (running) {
            Log.w(TAG, "服务器已运行");
            return false;
        }
        
        try {
            this.port = port;
            serverSocket = new ServerSocket(port);
            running = true;
            
            Log.i(TAG, "WebSocket 服务器已启动，端口: " + port);
            
            // 启动接受连接的线程
            threadPool.execute(this::acceptConnections);
            
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "启动服务器失败: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 启动服务器（使用默认端口）。
     * 
     * @return 启动成功返回 true，否则返回 false
     */
    public boolean start() {
        return start(DEFAULT_PORT);
    }
    
    /**
     * 停止服务器。
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        try {
            // 释放 WebSocket 处理器（会关闭所有客户端连接）
            if (webSocketHandler != null) {
                webSocketHandler.release();
            }
            
            // 关闭服务器 Socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            Log.i(TAG, "WebSocket 服务器已停止");
            
        } catch (IOException e) {
            Log.e(TAG, "停止服务器失败", e);
        }
    }
    
    /**
     * 接受客户端连接（后台线程）。
     */
    private void acceptConnections() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                
                Log.d(TAG, "新客户端连接: " + client.getInetAddress().getHostAddress());
                
                // 处理客户端握手和消息（在新线程中处理）
                threadPool.execute(() -> handleClient(client));
                
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "接受连接失败", e);
                }
            }
        }
    }
    
    /**
     * 处理客户端连接（握手 + 消息循环）。
     * 
     * @param client 客户端 Socket
     */
    private void handleClient(Socket client) {
        try {
            // 1. 处理 WebSocket 握手
            boolean handshakeSuccess = webSocketHandler.handleHandshake(client);
            if (!handshakeSuccess) {
                Log.w(TAG, "WebSocket 握手失败: " + client.getInetAddress().getHostAddress());
                client.close();
                return;
            }
            
            Log.i(TAG, "WebSocket 连接已建立: " + client.getInetAddress().getHostAddress());
            
            // 2. 消息循环（读取客户端发送的消息）
            while (running && !client.isClosed()) {
                String message = webSocketHandler.readMessage(client);
                if (message == null) {
                    // 连接已关闭或发生错误
                    break;
                }
                // readMessage 内部会回调 MessageListener，无需在此处理
            }
            
        } catch (Exception e) {
            Log.e(TAG, "处理客户端连接失败: " + client.getInetAddress().getHostAddress(), e);
        } finally {
            try {
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "关闭客户端 Socket 失败", e);
            }
        }
    }
    
    /**
     * 广播消息给所有连接的客户端（排除发送者）。
     * 
     * @param message 消息内容
     * @param excludeClient 要排除的客户端（发送者）
     */
    public void broadcast(@NonNull String message, @Nullable Socket excludeClient) {
        for (Socket client : new ArrayList<>(webSocketHandler.getConnectedClientCount())) {
            if (client.equals(excludeClient)) {
                continue; // 不发送给发送者
            }
            webSocketHandler.sendMessage(client, message);
        }
    }
    
    /**
     * 广播消息给所有连接的客户端。
     * 
     * @param message 消息内容
     */
    public void broadcast(@NonNull String message) {
        webSocketHandler.broadcastMessage(message);
    }
    
    /**
     * 获取服务器端口。
     * 
     * @return 端口号
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 检查服务器是否正在运行。
     * 
     * @return 运行中返回 true，否则返回 false
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取当前连接的客户端数量。
     * 
     * @return 客户端数量
     */
    public int getClientCount() {
        return webSocketHandler != null ? webSocketHandler.getConnectedClientCount() : 0;
    }
    
    /**
     * 释放资源。
     */
    public void release() {
        stop();
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        if (webSocketHandler != null) {
            webSocketHandler.release();
        }
        Log.d(TAG, "WebSocket 服务器资源已释放");
    }
}

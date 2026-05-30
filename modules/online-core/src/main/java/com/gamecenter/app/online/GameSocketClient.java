package com.gamecenter.app.online;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import java.util.concurrent.TimeUnit;

/**
 * 游戏 WebSocket 客户端（联机模式）。
 * 
 * 连接到远程 WebSocket 服务器（房主或中继服务器）。
 * 用于联机对战时与对手通信。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class GameSocketClient {
    
    private static final String TAG = "GameSocketClient";
    
    /** 连接超时：10 秒 */
    private static final int CONNECT_TIMEOUT_MS = 10000;
    
    /** 读取超时：30 秒 */
    private static final int READ_TIMEOUT_MS = 30000;
    
    /** 上下文 */
    private final Context context;
    
    /** OkHttp 客户端 */
    private OkHttpClient httpClient;
    
    /** WebSocket 连接 */
    private WebSocket webSocket;
    
    /** 连接状态监听器 */
    private ConnectionListener listener;
    
    /** 是否已连接 */
    private volatile boolean connected;
    
    /** 服务器 URL */
    private String serverUrl;
    
    /**
     * WebSocket 监听器。
     */
    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            Log.i(TAG, "WebSocket 连接已打开");
            connected = true;
            if (listener != null) {
                listener.onConnected();
            }
        }
        
        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            Log.d(TAG, "收到文本消息: " + text);
            if (listener != null) {
                listener.onMessage(text);
            }
        }
        
        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
            Log.d(TAG, "收到二进制消息");
            if (listener != null) {
                listener.onMessage(bytes.base64());
            }
        }
        
        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket 正在关闭: " + reason);
            webSocket.close(code, reason);
        }
        
        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.i(TAG, "WebSocket 连接已关闭");
            connected = false;
            if (listener != null) {
                listener.onDisconnected();
            }
        }
        
        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
            Log.e(TAG, "WebSocket 连接失败", t);
            connected = false;
            if (listener != null) {
                listener.onError(t.getMessage() != null ? t.getMessage() : "连接失败");
            }
        }
    };
    
    /**
     * 构造函数。
     * 
     * @param context Android Context
     */
    public GameSocketClient(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.connected = false;
        this.serverUrl = "";
        
        // 创建 OkHttp 客户端
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }
    
    /**
     * 连接到 WebSocket 服务器。
     * 
     * @param url 服务器 URL（ws:// 或 wss://）
     * @param listener 连接状态监听器
     * @return 连接请求已发送返回 true，否则返回 false
     */
    public boolean connect(@NonNull String url, @NonNull ConnectionListener listener) {
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "URL 为空");
            return false;
        }
        
        if (connected) {
            Log.w(TAG, "已连接到服务器");
            return false;
        }
        
        this.serverUrl = url;
        this.listener = listener;
        
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            
            webSocket = httpClient.newWebSocket(request, webSocketListener);
            
            Log.d(TAG, "正在连接 WebSocket: " + url);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "连接 WebSocket 失败: " + url, e);
            return false;
        }
    }
    
    /**
     * 断开连接。
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "客户端主动断开");
            webSocket = null;
        }
        connected = false;
        Log.d(TAG, "WebSocket 已断开连接");
    }
    
    /**
     * 发送文本消息。
     * 
     * @param message 消息内容
     * @return 发送成功返回 true，否则返回 false
     */
    public boolean sendMessage(@NonNull String message) {
        if (!connected || webSocket == null) {
            Log.e(TAG, "未连接到服务器");
            return false;
        }
        
        boolean success = webSocket.send(message);
        if (success) {
            Log.d(TAG, "消息已发送: " + message);
        } else {
            Log.e(TAG, "消息发送失败: " + message);
        }
        return success;
    }
    
    /**
     * 发送二进制消息。
     * 
     * @param data 二进制数据
     * @return 发送成功返回 true，否则返回 false
     */
    public boolean sendBytes(@NonNull byte[] data) {
        if (!connected || webSocket == null) {
            Log.e(TAG, "未连接到服务器");
            return false;
        }
        
        boolean success = webSocket.send(ByteString.of(data));
        if (success) {
            Log.d(TAG, "二进制消息已发送，长度: " + data.length);
        } else {
            Log.e(TAG, "二进制消息发送失败");
        }
        return success;
    }
    
    /**
     * 检查是否已连接。
     * 
     * @return 已连接返回 true，否则返回 false
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * 获取当前连接的服务器 URL。
     * 
     * @return 服务器 URL
     */
    @NonNull
    public String getServerUrl() {
        return serverUrl != null ? serverUrl : "";
    }
    
    /**
     * 释放资源。
     */
    public void release() {
        disconnect();
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdownNow();
        }
        Log.d(TAG, "WebSocket 客户端资源已释放");
    }
    
    // ========== 连接状态监听器接口 ==========
    
    /**
     * WebSocket 连接状态监听器。
     */
    public interface ConnectionListener {
        /**
         * 连接成功。
         */
        void onConnected();
        
        /**
         * 收到消息。
         * 
         * @param message 消息内容
         */
        void onMessage(@NonNull String message);
        
        /**
         * 连接断开。
         */
        void onDisconnected();
        
        /**
         * 发生错误。
         * 
         * @param errorMessage 错误信息
         */
        void onError(@NonNull String errorMessage);
    }
}

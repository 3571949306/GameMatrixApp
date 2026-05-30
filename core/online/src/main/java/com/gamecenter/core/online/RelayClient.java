package com.gamecenter.core.online;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket Relay 客户端。
 *
 * <p>负责与 Relay 服务器建立 WebSocket 连接，
 * 实现消息的发送和接收。
 *
 * <p>连接流程：
 * <ol>
 *   <li>创建 WebSocket 连接</li>
 *   <li>发送认证/握手消息</li>
 *   <li>收发游戏消息</li>
 *   <li>断开连接</li>
 * </ol>
 *
 * <p>支持断线重连（最多 3 次重试）。
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
public class RelayClient {

    private static final String TAG = "RelayClient";

    /** 最大重连次数 */
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    /** 重连间隔（毫秒） */
    private static final long RECONNECT_INTERVAL_MS = 3000;

    /** WebSocket 服务器 URL */
    private final String serverUrl;

    /** OkHttp 客户端 */
    private final OkHttpClient httpClient;

    /** WebSocket 连接实例 */
    @Nullable
    private WebSocket webSocket;

    /** 消息监听器 */
    @Nullable
    private OnMessageListener messageListener;

    /** 连接状态监听器 */
    @Nullable
    private OnConnectionStateListener connectionStateListener;

    /** 当前重连次数 */
    private int reconnectAttempts = 0;

    /** 是否已手动断开（不自动重连） */
    private boolean manuallyDisconnected = false;

    /**
     * 消息监听器接口。
     */
    public interface OnMessageListener {
        /**
         * 收到消息时回调。
         *
         * @param message 消息内容（JSON 字符串）
         */
        void onMessage(@NonNull String message);
    }

    /**
     * 连接状态监听器接口。
     */
    public interface OnConnectionStateListener {
        /** 连接成功 */
        void onConnected();

        /** 连接断开 */
        void onDisconnected(@Nullable String reason);

        /** 连接错误 */
        void onError(@NonNull String errorMessage);
    }

    /**
     * 构造函数。
     *
     * @param serverUrl WebSocket 服务器 URL
     */
    public RelayClient(@NonNull String serverUrl) {
        this.serverUrl = serverUrl != null ? serverUrl : "";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 连接到 Relay 服务器。
     *
     * @return 发起连接成功返回 true，否则返回 false
     */
    public boolean connect() {
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "服务器 URL 为空");
            return false;
        }

        manuallyDisconnected = false;
        reconnectAttempts = 0;

        return doConnect();
    }

    /**
     * 实际执行连接。
     */
    private boolean doConnect() {
        try {
            Request request = new Request.Builder()
                    .url(serverUrl)
                    .build();

            webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    Log.i(TAG, "WebSocket 连接成功: " + serverUrl);
                    reconnectAttempts = 0;

                    if (connectionStateListener != null) {
                        connectionStateListener.onConnected();
                    }
                }

                @Override
                public void onMessage(WebSocket ws, String text) {
                    Log.d(TAG, "收到消息: " + (text.length() > 100
                            ? text.substring(0, 100) + "..." : text));

                    if (messageListener != null) {
                        messageListener.onMessage(text);
                    }
                }

                @Override
                public void onClosing(WebSocket ws, int code, String reason) {
                    Log.d(TAG, "WebSocket 正在关闭: code=" + code + ", reason=" + reason);
                    ws.close(1000, "客户端确认关闭");
                }

                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    Log.i(TAG, "WebSocket 已关闭: code=" + code + ", reason=" + reason);

                    if (!manuallyDisconnected) {
                        attemptReconnect();
                    }
                }

                @Override
                public void onFailure(WebSocket ws, Throwable t, @Nullable Response response) {
                    Log.e(TAG, "WebSocket 连接失败", t);

                    if (connectionStateListener != null) {
                        String errorMsg = t.getMessage() != null ? t.getMessage() : "未知错误";
                        connectionStateListener.onError(errorMsg);
                    }

                    if (!manuallyDisconnected) {
                        attemptReconnect();
                    }
                }
            });

            Log.d(TAG, "WebSocket 连接请求已发送: " + serverUrl);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "连接异常", e);
            return false;
        }
    }

    /**
     * 尝试重连。
     */
    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "重连次数已达上限 (" + MAX_RECONNECT_ATTEMPTS + ")，停止重连");

            if (connectionStateListener != null) {
                connectionStateListener.onDisconnected("重连失败");
            }
            return;
        }

        reconnectAttempts++;
        Log.d(TAG, "尝试重连 (" + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")...");

        if (connectionStateListener != null) {
            connectionStateListener.onDisconnected("正在重连 (" + reconnectAttempts + ")");
        }

        // 异步延迟重连
        new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_INTERVAL_MS);
                doConnect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 发送消息。
     *
     * @param message 消息内容（JSON 字符串）
     * @return 发送成功返回 true，否则返回 false
     */
    public boolean send(@NonNull String message) {
        if (webSocket == null) {
            Log.w(TAG, "WebSocket 未连接，无法发送消息");
            return false;
        }

        try {
            boolean success = webSocket.send(message);
            if (!success) {
                Log.w(TAG, "消息发送失败（WebSocket 可能已关闭）");
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "发送消息异常", e);
            return false;
        }
    }

    /**
     * 断开连接。
     */
    public void disconnect() {
        manuallyDisconnected = true;

        if (webSocket != null) {
            webSocket.close(1000, "客户端主动断开");
            webSocket = null;
        }

        Log.i(TAG, "WebSocket 已断开");
    }

    // ========== Setter ==========

    public void setMessageListener(@Nullable OnMessageListener listener) {
        this.messageListener = listener;
    }

    public void setConnectionStateListener(@Nullable OnConnectionStateListener listener) {
        this.connectionStateListener = listener;
    }
}

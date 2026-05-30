package com.gamecenter.app.online;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Relay 通信工具类 + WebSocket URL 生成器。
 * 
 * <p>提供与中继服务器（Relay Server）交互所需的工具方法，主要包括：</p>
 * <ul>
 *   <li>生成 WebSocket 连接 URL（区分房主和客户端角色）</li>
 *   <li>发送 HTTP POST 请求到中继服务器</li>
 *   <li>HTTP/HTTPS 到 WS/WSS 协议转换</li>
 *   <li>URL 参数编码</li>
 * </ul>
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class RelayHttpClient {
    
    private static final String TAG = "RelayHttpClient";
    
    /** 默认中继服务器基础 URL */
    private static final String DEFAULT_BASE_URL = "https://your-vps-server.com";
    
    /** WebSocket 服务路径 */
    private static final String WS_PATH = "/ddz-ws";
    
    /** 连接超时：10 秒 */
    private static final int CONNECT_TIMEOUT_MS = 10000;
    
    /** 读取超时：30 秒 */
    private static final int READ_TIMEOUT_MS = 30000;
    
    /** 上下文 */
    private final Context context;
    
    /** OkHttp 客户端 */
    private OkHttpClient httpClient;
    
    /**
     * 私有构造函数。
     * 
     * @param context Android Context
     */
    public RelayHttpClient(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }
    
    /**
     * 生成房主角色的 WebSocket 连接 URL。
     * 
     * @param baseUrl   中继服务器基础 URL
     * @param roomCode  房间代码
     * @param hostToken 房主认证令牌
     * @return 完整的 WebSocket 连接 URL
     */
    @NonNull
    public String getWebSocketUrl(@NonNull String baseUrl, 
                                  @NonNull String roomCode, 
                                  @NonNull String hostToken) {
        return buildWebSocketUrl(baseUrl, roomCode, "host", hostToken);
    }
    
    /**
     * 生成客户端角色的 WebSocket 连接 URL。
     * 
     * @param baseUrl  中继服务器基础 URL
     * @param roomCode 房间代码
     * @return 完整的 WebSocket 连接 URL
     */
    @NonNull
    public String getWebSocketClientUrl(@NonNull String baseUrl, 
                                        @NonNull String roomCode) {
        return buildWebSocketUrl(baseUrl, roomCode, "client", null);
    }
    
    /**
     * 构建 WebSocket 连接 URL 的核心方法。
     * 
     * @param baseUrl  中继服务器基础 URL
     * @param roomCode 房间代码
     * @param role     连接角色
     * @param token    认证令牌
     * @return 完整的 WebSocket 连接 URL
     */
    @NonNull
    private String buildWebSocketUrl(@NonNull String baseUrl, 
                                     @NonNull String roomCode, 
                                     @NonNull String role, 
                                     String token) {
        // 如果未提供 baseUrl，使用默认
        String actualBaseUrl = (baseUrl == null || baseUrl.isEmpty()) 
                ? DEFAULT_BASE_URL : baseUrl;
        
        // 将 HTTP/HTTPS 转换为 WS/WSS
        String wsBaseUrl = convertToWebSocketUrl(actualBaseUrl);
        
        // 构建 URL
        StringBuilder sb = new StringBuilder();
        sb.append(wsBaseUrl);
        sb.append(WS_PATH);
        sb.append("?room=").append(urlEncode(roomCode != null ? roomCode : ""));
        sb.append("&role=").append(urlEncode(role));
        
        if (token != null && !token.isEmpty()) {
            sb.append("&token=").append(urlEncode(token));
        }
        
        String result = sb.toString();
        Log.d(TAG, "生成 WebSocket URL: " + result);
        return result;
    }
    
    /**
     * 将 HTTP/HTTPS URL 转换为 WS/WSS URL。
     * 
     * @param url HTTP/HTTPS URL
     * @return WS/WSS URL
     */
    @NonNull
    private String convertToWebSocketUrl(@NonNull String url) {
        if (url.startsWith("https://")) {
            return "wss://" + url.substring(8);
        } else if (url.startsWith("http://")) {
            return "ws://" + url.substring(7);
        } else if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            // 默认添加 https://
            return "wss://" + url;
        }
        return url;
    }
    
    /**
     * URL 编码（手写实现，符合 RFC 3986）。
     * 
     * @param str 待编码字符串
     * @return 编码后字符串
     */
    @NonNull
    private String urlEncode(@NonNull String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "URL 编码失败", e);
            return str;
        }
    }
    
    /**
     * 发送 HTTP POST 请求到中继服务器。
     * 
     * @param url  请求 URL
     * @param jsonBody JSON 请求体
     * @return 响应字符串，失败返回 null
     */
    @NonNull
    public String sendPostRequest(@NonNull String url, 
                                   @NonNull String jsonBody) {
        if (httpClient == null) {
            Log.e(TAG, "HttpClient 未初始化");
            return null;
        }
        
        try {
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonBody, JSON);
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string();
                    Log.d(TAG, "POST 请求成功: " + url);
                    return result;
                } else {
                    Log.e(TAG, "POST 请求失败: " + response.code() + " - " + url);
                    return null;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "POST 请求异常: " + url, e);
            return null;
        }
    }
    
    /**
     * 获取中继服务器基础 URL（从配置读取）。
     * 
     * @return 基础 URL
     */
    @NonNull
    public String getBaseUrl() {
        // 实际应从 BuildConfig 或 SharedPreferences 读取
        return DEFAULT_BASE_URL;
    }
    
    /**
     * 设置中继服务器基础 URL。
     * 
     * @param baseUrl 基础 URL
     */
    public void setBaseUrl(@NonNull String baseUrl) {
        Log.d(TAG, "设置中继服务器 URL: " + baseUrl);
        // 实际应保存到 SharedPreferences
    }
    
    /**
     * 释放资源。
     */
    public void release() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdownNow();
            httpClient = null;
        }
        Log.d(TAG, "RelayHttpClient 资源已释放");
    }
}

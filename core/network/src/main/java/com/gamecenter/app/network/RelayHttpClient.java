package com.gamecenter.app.network;

import android.content.Context;

import org.json.JSONObject;

import com.gamecenter.app.network.BuildConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
 * <p>关键设计决策：
 * <ul>
 *   <li>纯静态工具类，final 类 + 私有构造器，不可实例化</li>
 *   <li>WebSocket URL 优先使用 BuildConfig.WS_URL 配置，若未配置则从 HTTP 基础 URL 自动推导</li>
 *   <li>URL 编码采用手写实现而非 URLEncoder，以符合 RFC 3986 规范
 *       （空格编码为 "+" 而非 "%20"，保留字符集与 URI 规范一致）</li>
 *   <li>HTTP POST 方法每次创建独立的 OkHttpClient 实例，支持自定义超时，
 *       适用于低频但可能需要不同超时配置的请求场景</li>
 * </ul>
 * </p>
 */
public final class RelayHttpClient {

    /** 默认中继服务器基础 URL，从 BuildConfig 读取 */
    public static final String DEFAULT_BASE_URL = BuildConfig.RELAY_URL;

    /** WebSocket 服务路径 */
    private static final String WS_PATH = "/ddz-ws";

    private RelayHttpClient() {
    }

    /**
     * 生成房主角色的 WebSocket 连接 URL。
     *
     * <p>房主 URL 包含认证 token，用于在中继服务器上验证房主身份。</p>
     *
     * @param baseUrl   中继服务器基础 URL
     * @param roomCode  房间代码
     * @param hostToken 房主认证令牌
     * @return 完整的 WebSocket 连接 URL
     */
    public static String getWebSocketUrl(String baseUrl, String roomCode, String hostToken) {
        return buildWebSocketUrl(baseUrl, roomCode, "host", hostToken);
    }

    /**
     * 生成客户端角色的 WebSocket 连接 URL。
     *
     * <p>客户端 URL 不包含 token，角色标识为 "client"。</p>
     *
     * @param baseUrl  中继服务器基础 URL
     * @param roomCode 房间代码
     * @return 完整的 WebSocket 连接 URL
     */
    public static String getWebSocketClientUrl(String baseUrl, String roomCode) {
        return buildWebSocketUrl(baseUrl, roomCode, "client", null);
    }

    /**
     * 构建 WebSocket 连接 URL 的核心方法。
     *
     * <p>URL 构建逻辑：</p>
     * <ol>
     *   <li>若 BuildConfig.WS_URL 已配置，直接使用该地址作为 WebSocket 基础 URL</li>
     *   <li>否则从 baseUrl 推导：将 HTTP/HTTPS 协议转换为 WS/WSS，并追加 {@value #WS_PATH} 路径</li>
     *   <li>拼接查询参数：room（房间号）、role（角色：host/client）、token（仅房主）</li>
     * </ol>
     *
     * @param baseUrl  中继服务器基础 URL，为 null 或空时使用 DEFAULT_BASE_URL
     * @param roomCode 房间代码
     * @param role     连接角色，"host" 或 "client"
     * @param token    认证令牌，仅房主角色需要，客户端传 null
     * @return 完整的 WebSocket 连接 URL
     */
    private static String buildWebSocketUrl(String baseUrl, String roomCode, String role, String token) {
        // 优先使用 BuildConfig 中配置的专用 WebSocket URL
        String configuredWsUrl = BuildConfig.WS_URL;
        if (configuredWsUrl != null && !configuredWsUrl.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(configuredWsUrl);
            sb.append("?room=").append(urlEncode(roomCode != null ? roomCode : ""));
            sb.append("&role=").append(urlEncode(role != null ? role : "client"));
            if (token != null && !token.isEmpty()) {
                sb.append("&token=").append(urlEncode(token));
            }
            return sb.toString();
        }

        // 未配置专用 WS URL，从 HTTP 基础 URL 推导
        String root = baseUrl != null ? baseUrl.trim() : "";
        if (root.isEmpty()) {
            root = DEFAULT_BASE_URL;
        }

        // 将 HTTP/HTTPS 协议转换为 WS/WSS，并去除路径部分
        String wsRoot = convertHttpToWs(root);

        StringBuilder sb = new StringBuilder();
        sb.append(wsRoot).append(WS_PATH);
        sb.append("?room=").append(urlEncode(roomCode != null ? roomCode : ""));
        sb.append("&role=").append(urlEncode(role != null ? role : "client"));
        if (token != null && !token.isEmpty()) {
            sb.append("&token=").append(urlEncode(token));
        }

        return sb.toString();
    }

    /**
     * 将 HTTP/HTTPS URL 转换为 WS/WSS URL，并去除路径部分仅保留协议+主机+端口。
     *
     * <p>转换规则：</p>
     * <ul>
     *   <li>https:// → wss://</li>
     *   <li>http:// → ws://</li>
     *   <li>去除路径部分，例如 https://example.com/api → wss://example.com</li>
     * </ul>
     *
     * @param httpUrl HTTP/HTTPS 格式的 URL
     * @return WS/WSS 格式的 URL（仅含协议+主机+端口）
     */
    private static String convertHttpToWs(String httpUrl) {
        String url = httpUrl;

        // 协议转换：https → wss, http → ws
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring(8);
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring(7);
        }

        // 去除路径部分，仅保留协议+主机+端口
        int protocolEnd = url.indexOf("://");
        if (protocolEnd > 0) {
            int hostStart = protocolEnd + 3;
            int pathStart = url.indexOf('/', hostStart);
            if (pathStart > 0) {
                url = url.substring(0, pathStart);
            }
        }

        return url;
    }

    /**
     * 对字符串进行 URL 编码（符合 RFC 3986 规范）。
     *
     * <p>编码规则：</p>
     * <ul>
     *   <li>字母（a-z, A-Z）、数字（0-9）和未保留字符（- _ . ~）保持不变</li>
     *   <li>空格编码为 "+"（application/x-www-form-urlencoded 风格）</li>
     *   <li>其他字符编码为 %XX 形式</li>
     * </ul>
     *
     * <p>注意：此处未使用 Java 标准库的 URLEncoder，因为 URLEncoder 会将空格编码为 "+"，
     * 但对其他字符的编码规则与 RFC 3986 不完全一致。此手写实现确保编码行为可控且一致。</p>
     *
     * @param value 待编码的字符串
     * @return 编码后的字符串，输入为 null 或空时返回空字符串
     */
    private static String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            // 未保留字符直接保留
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                result.append(c);
            } else if (c == ' ') {
                // 空格编码为 "+"
                result.append('+');
            } else {
                // 其他字符编码为 %XX
                result.append(String.format("%%%02X", (int) c));
            }
        }
        return result.toString();
    }

    /**
     * 向中继服务器发送 HTTP POST 请求。
     *
     * <p>请求和响应均为 JSON 格式。响应体需包含 "ok": true 字段表示成功，
     * 否则视为业务逻辑失败并抛出 IllegalStateException。</p>
     *
     * <p>注意：此方法每次调用都创建新的 OkHttpClient 实例，
     * 因为不同的调用可能需要不同的超时配置。对于高频请求场景，
     * 建议使用 {@link OkHttpClientProvider} 提供的共享客户端实例。</p>
     *
     * @param baseUrl   中继服务器基础 URL
     * @param path      API 路径，如 "/api/create-room"
     * @param body      请求体 JSON 对象，为 null 时发送空 JSON "{}"
     * @param timeoutMs 超时时间（毫秒），同时应用于连接和读取超时
     * @return 服务器返回的 JSON 响应对象
     * @throws IllegalStateException 当 HTTP 状态码非 2xx 或响应中 ok 字段为 false 时
     * @throws Exception             网络异常或 JSON 解析异常
     */
    public static JSONObject post(String baseUrl, String path, JSONObject body, int timeoutMs) throws Exception {
        String root = baseUrl != null ? baseUrl.trim() : "";
        // 去除末尾的斜杠，避免路径中出现双斜杠
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }

        // 为每次请求创建独立客户端，支持自定义超时
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();

        RequestBody requestBody = RequestBody.create(
                body != null ? body.toString() : "{}",
                MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(root + path)
                .post(requestBody)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "GameCenterApp-Relay")
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String responseStr = response.body() != null ? response.body().string() : "";

            JSONObject json = !responseStr.isEmpty() ? new JSONObject(responseStr) : new JSONObject();
            // 业务逻辑校验：HTTP 状态码非 2xx 或响应 ok 字段为 false 均视为失败
            if (code < 200 || code >= 300 || !json.optBoolean("ok", false)) {
                String error = json.optString("error", "HTTP " + code);
                throw new IllegalStateException(error);
            }
            return json;
        }
    }
}

package com.gamecenter.app.games.doudizhu.network;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import com.gamecenter.app.BuildConfig;

/**
 * 类名：RelayHttpClient
 * 职责：HTTP Relay 通信工具类 + WebSocket URL 生成器
 *        1. 提供 HTTP POST 方法向 Relay 服务器发送请求（创建房间、加入房间、轮询消息等）
 *        2. 将 HTTP baseUrl 转换为 WebSocket URL，供 GameSocketClient/GameSocketServer 连接 Relay 使用
 * 关联类：
 *   - 被 GameSocketClient 调用：getWebSocketClientUrl()、post()
 *   - 被 GameSocketServer 调用：getWebSocketUrl()、post()
 *   - 依赖 BuildConfig.RELAY_URL 作为默认服务器地址
 * 生命周期：无状态工具类，所有方法均为 static，无需实例化
 * 注意事项：
 *   - 修改 WS_PATH 时需要同步修改 nginx 配置和 Node.js Relay 服务的路径映射
 *   - urlEncode() 是简易实现，仅覆盖斗地主房间码常用字符集
 */
public final class RelayHttpClient {

    /**
     * 默认 Relay 服务器地址，从 BuildConfig 读取（由 local.properties 的 relay.url 生成）
     */
    public static final String DEFAULT_BASE_URL = BuildConfig.RELAY_URL;

    /**
     * WebSocket 路径固定为 /ddz-ws，与 nginx location /ddz-ws 和 Node.js Relay 服务保持一致
     */
    private static final String WS_PATH = "/ddz-ws";

    private RelayHttpClient() {
        // 工具类禁止实例化
    }

    /**
     * 方法作用：生成房主端 WebSocket 连接 URL
     * @param baseUrl  HTTP Relay baseUrl，例如 https://hk-relay.<YOUR_DOMAIN>/api/ddz-relay
     * @param roomCode 6 位房间码
     * @param hostToken 房主 Token
     * @return 完整的 WebSocket URL，例如 wss://hk-ws.<YOUR_DOMAIN>/ddz-ws?room=ABC123&role=host&token=xxx
     * 调用时机：GameSocketServer.startWebSocket() 中房主创建房间后调用
     * 副作用：无
     */
    public static String getWebSocketUrl(String baseUrl, String roomCode, String hostToken) {
        return buildWebSocketUrl(baseUrl, roomCode, "host", hostToken);
    }

    /**
     * 方法作用：生成客户端 WebSocket 连接 URL
     * @param baseUrl  HTTP Relay baseUrl，例如 https://hk-relay.<YOUR_DOMAIN>/api/ddz-relay
     * @param roomCode 房间码（6位字母数字）
     * @return 完整的 WebSocket URL，例如 wss://hk-ws.<YOUR_DOMAIN>/ddz-ws?room=ABC123&role=client
     * 调用时机：GameSocketClient.connectWebSocket() 中客户端加入房间时调用
     * 副作用：无
     */
    public static String getWebSocketClientUrl(String baseUrl, String roomCode) {
        return buildWebSocketUrl(baseUrl, roomCode, "client", null);
    }

    /**
     * 方法作用：构建 WebSocket URL 的通用方法
     * @param baseUrl  HTTP Relay baseUrl
     * @param roomCode 房间码
     * @param role     角色："host" 或 "client"
     * @param token    房主令牌（客户端传 null）
     * @return 完整的 WebSocket URL
     * 调用时机：被 getWebSocketUrl() 和 getWebSocketClientUrl() 内部调用
     * 副作用：无
     */
    private static String buildWebSocketUrl(String baseUrl, String roomCode, String role, String token) {
        // 优先使用 BuildConfig.WS_URL 作为 WebSocket URL
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

        String root = baseUrl != null ? baseUrl.trim() : "";
        if (root.isEmpty()) {
            root = DEFAULT_BASE_URL;
        }

        // 提取协议 + 主机部分，去掉 /api/ddz-relay 路径后缀
        String wsRoot = convertHttpToWs(root);

        // 拼接查询参数
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
     * 方法作用：将 HTTP URL 转换为 WebSocket URL，并去掉 API 路径后缀
     * @param httpUrl 原始 HTTP URL，例如 https://hk-relay.<YOUR_DOMAIN>/api/ddz-relay
     * @return 转换后的 WebSocket 根 URL，例如 wss://hk-ws.<YOUR_DOMAIN>
     * 调用时机：被 buildWebSocketUrl() 内部调用
     * 副作用：无
     * 注意事项：
     *   - http://  → ws://
     *   - https:// → wss://
     *   - 同时去掉 /api/ddz-relay 等路径后缀，只保留根路径
     */
    private static String convertHttpToWs(String httpUrl) {
        String url = httpUrl;

        // 协议转换
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring(8);
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring(7);
        }

        // 去掉路径后缀：/api/ddz-relay → 空
        // 找到第三个斜杠之后的内容，去掉 /api/... 部分
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
     * 方法作用：简易 URL 编码（只处理需要编码的字符）
     * @param value 原始字符串
     * @return URL 编码后的字符串
     * 调用时机：被 buildWebSocketUrl() 内部调用，用于编码 roomCode、role、token 等参数
     * 副作用：无
     * 注意事项：
     *   - 仅覆盖 RFC 3986 未保留字符集（字母数字 + -_.~）
     *   - 空格转换为 +
     *   - 其他字符使用 %XX 十六进制编码
     *   - 对于复杂场景建议使用 java.net.URLEncoder
     */
    private static String urlEncode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                result.append(c);
            } else if (c == ' ') {
                result.append('+');
            } else {
                result.append(String.format("%%%02X", (int) c));
            }
        }
        return result.toString();
    }

    /**
     * 方法作用：向 Relay 服务器发送 HTTP POST 请求
     * @param baseUrl   Relay 服务器基础 URL
     * @param path      API 路径，例如 "/create"、"/join"、"/poll"、"/send"
     * @param body      JSON 请求体
     * @param timeoutMs 超时时间（毫秒）
     * @return 服务器返回的 JSON 响应
     * @throws Exception 当 HTTP 状态码非 2xx 或响应中 ok=false 时抛出
     * 调用时机：
     *   - GameSocketServer：创建房间、关闭房间、轮询消息、发送消息
     *   - GameSocketClient：加入房间、断开连接、轮询消息、发送消息
     * 副作用：发送网络请求，可能阻塞调用线程
     * 注意事项：
     *   - 此方法在后台线程中调用，不要在主线程直接调用
     *   - 超时时间根据操作类型不同：创建房间 10s、轮询 35s、发送消息 10s
     */
    static JSONObject post(String baseUrl, String path, JSONObject body, int timeoutMs) throws Exception {
        String root = baseUrl != null ? baseUrl.trim() : "";
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        URL url = new URL(root + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "GameCenterApp-DDZ-Relay");

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(connection.getOutputStream(), "UTF-8"))) {
            writer.write(body != null ? body.toString() : "{}");
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        StringBuilder response = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
        }
        connection.disconnect();

        JSONObject json = response.length() > 0 ? new JSONObject(response.toString()) : new JSONObject();
        if (code < 200 || code >= 300 || !json.optBoolean("ok", false)) {
            String error = json.optString("error", "HTTP " + code);
            throw new IllegalStateException(error);
        }
        return json;
    }
}

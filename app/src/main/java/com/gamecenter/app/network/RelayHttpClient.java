package com.gamecenter.app.network;

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
 * HTTP Relay 通信工具类 + WebSocket URL 生成器
 */
public final class RelayHttpClient {

    public static final String DEFAULT_BASE_URL = BuildConfig.RELAY_URL;

    private static final String WS_PATH = "/ddz-ws";

    private RelayHttpClient() {
    }

    public static String getWebSocketUrl(String baseUrl, String roomCode, String hostToken) {
        return buildWebSocketUrl(baseUrl, roomCode, "host", hostToken);
    }

    public static String getWebSocketClientUrl(String baseUrl, String roomCode) {
        return buildWebSocketUrl(baseUrl, roomCode, "client", null);
    }

    private static String buildWebSocketUrl(String baseUrl, String roomCode, String role, String token) {
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

    private static String convertHttpToWs(String httpUrl) {
        String url = httpUrl;

        if (url.startsWith("https://")) {
            url = "wss://" + url.substring(8);
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring(7);
        }

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
        connection.setRequestProperty("User-Agent", "GameCenterApp-Relay");

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

package com.gamecenter.app.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class RemoteP2PUtil {

    private static final Pattern VALID_CODE = Pattern.compile("^[0-9]{6}$");

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static String normalizeRoomCode(String code) {
        if (code == null) return "";
        return code.trim();
    }

    public static boolean isValidRoomCode(String code) {
        if (code == null || code.isEmpty()) return false;
        return VALID_CODE.matcher(code).matches();
    }

    public static void verifyRoomCode(Context context, String code, RoomCodeCallback callback) {
        String normalized = normalizeRoomCode(code);
        if (!isValidRoomCode(normalized)) {
            if (callback != null) mainHandler.post(() -> callback.onResult(false, "请输入6位房间码"));
            return;
        }
        if (callback != null) mainHandler.post(() -> callback.onResult(true, null));
    }

    public static void savePeerToken(Context context, String prefsName, String token) {
        if (context == null || token == null) return;
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit();
            editor.putString("last_peer_token", token);
            editor.putString("peer_token_" + token, token);
            editor.putLong("peer_token_time", System.currentTimeMillis());
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    public static String getLastPeerToken(Context context, String prefsName) {
        if (context == null) return null;
        try {
            SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            String token = prefs.getString("last_peer_token", null);
            if (token != null && !token.isEmpty()) return token;
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void clearPeerToken(Context context, String prefsName) {
        if (context == null) return;
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit();
            editor.remove("last_peer_token");
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    public static String buildWebSocketUrl(String wsServer, String gameProtocol, String roomCode, String peerToken) {
        if (!wsServer.startsWith("ws://") && !wsServer.startsWith("wss://")) {
            wsServer = "wss://" + wsServer;
        }
        StringBuilder url = new StringBuilder(wsServer);
        if (!wsServer.endsWith("/")) url.append("/");
        url.append("ws?");
        if (gameProtocol != null && !gameProtocol.isEmpty()) url.append("game=").append(gameProtocol).append("&");
        if (roomCode != null && !roomCode.isEmpty()) url.append("room=").append(roomCode).append("&");
        if (peerToken != null && !peerToken.isEmpty()) url.append("token=").append(peerToken);
        return url.toString();
    }

    public static String buildWebSocketUrl(String wsServer, String gameProtocol, String roomCode, String peerToken, String playerName) {
        String base = buildWebSocketUrl(wsServer, gameProtocol, roomCode, peerToken);
        if (playerName != null && !playerName.isEmpty()) {
            if (!base.endsWith("&")) base += "&";
            base += "name=" + playerName;
        }
        return base;
    }

    public static void showToast(Context context, String message) {
        if (context == null || message == null) return;
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    public interface RoomCodeCallback {
        void onResult(boolean valid, String errorMessage);
    }
}

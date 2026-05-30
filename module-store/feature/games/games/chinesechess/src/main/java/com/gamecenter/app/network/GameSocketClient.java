package com.gamecenter.app.network;

import android.content.Context;
import org.json.JSONObject;

/** 联机对战 WebSocket 客户端存根（实际运行时由宿主提供） */
public class GameSocketClient {
    public interface OnConnectedListener { void onConnected(int clientId); }
    public interface OnDisconnectedListener { void onDisconnected(String reason); }
    public interface OnMessageReceivedListener { void onMessageReceived(JSONObject message); }
    public interface OnErrorListener { void onError(String message); }

    private static GameSocketClient instance;
    public static GameSocketClient getInstance(Context context) {
        if (instance == null) instance = new GameSocketClient();
        return instance;
    }
    public void setPlayerName(String name) {}
    public void setOnConnectedListener(OnConnectedListener l) {}
    public void setOnDisconnectedListener(OnDisconnectedListener l) {}
    public void setOnMessageReceivedListener(OnMessageReceivedListener l) {}
    public void setOnErrorListener(OnErrorListener l) {}
    public void setPeerToken(String token) {}
    public String getPeerToken() { return ""; }
    public void connectWebSocket(String wsUrl) {}
    public void send(JSONObject json) {}
    public boolean isConnected() { return false; }
    public void disconnect() {}
    public void release() {}
}

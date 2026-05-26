package com.gamecenter.app.network;

import android.content.Context;
import org.json.JSONObject;

/** 联机对战 WebSocket 服务端存根（实际运行时由宿主提供） */
public class GameSocketServer {
    public interface OnClientConnectedListener { void onClientConnected(int clientId, String ip); }
    public interface OnClientDisconnectedListener { void onClientDisconnected(int clientId, String reason); }
    public interface OnMessageReceivedListener { void onMessageReceived(int clientId, JSONObject message); }
    public interface OnErrorListener { void onError(String message); }

    public GameSocketServer(Context context) {}
    public void setOnClientConnectedListener(OnClientConnectedListener l) {}
    public void setOnClientDisconnectedListener(OnClientDisconnectedListener l) {}
    public void setOnMessageReceivedListener(OnMessageReceivedListener l) {}
    public void setOnErrorListener(OnErrorListener l) {}
    public boolean startWebSocket(String wsUrl) { return false; }
    public void broadcast(JSONObject json) {}
    public void stop() {}
}

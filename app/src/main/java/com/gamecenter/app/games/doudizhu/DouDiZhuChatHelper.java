package com.gamecenter.app.games.doudizhu;

import android.os.Handler;
import android.util.Log;

import com.gamecenter.app.network.GameSocketServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DouDiZhuChatHelper {

    private static final String TAG = "DDZ-ChatHelper";
    static final int TOTAL_SEATS = 3;
    static final int SEAT_TYPE_REMOTE = 1;

    private final List<JSONObject> hostChatHistory = new ArrayList<>();

    public DouDiZhuChatHelper() {}

    public List<JSONObject> getHostChatHistory() {
        return hostChatHistory;
    }

    public void rememberHostChat(int seatIndex, String message) {
        if (message == null) return;
        JSONObject item = new JSONObject();
        try {
            item.put("seatIndex", seatIndex);
            item.put("message", message);
            item.put("time", System.currentTimeMillis());
            hostChatHistory.add(item);
        } catch (JSONException e) { Log.w(TAG, "JSON error: " + e.getMessage()); }
    }

    public void sendChatHistoryToClient(GameSocketServer server, int clientId) {
        if (server == null) return;
        JSONObject msg = new JSONObject();
        JSONArray messages = new JSONArray();
        try {
            for (JSONObject item : hostChatHistory) {
                messages.put(item);
            }
            msg.put("type", "CHAT_HISTORY");
            msg.put("messages", messages);
            server.sendTo(clientId, msg);
        } catch (JSONException e) {
            Log.e(TAG, "sendChatHistoryToClient error: " + e.getMessage());
        }
    }

    public void broadcastChatHistoryToAll(GameSocketServer server, Handler handler,
                                           int[] seatTypes, int[] seatClientIds) {
        if (server == null) return;
        sendChatHistoryToAllNow(server, seatTypes, seatClientIds);
        handler.postDelayed(() -> sendChatHistoryToAllNow(server, seatTypes, seatClientIds), 200);
    }

    public void sendChatHistoryToAllNow(GameSocketServer server,
                                         int[] seatTypes, int[] seatClientIds) {
        if (server == null) return;
        for (int i = 0; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] >= 0) {
                sendChatHistoryToClient(server, seatClientIds[i]);
            }
        }
    }
}

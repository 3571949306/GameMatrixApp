package com.gamecenter.app.games.doudizhu;

import android.util.Log;

import com.gamecenter.app.BuildConfig;

public final class DouDiZhuLogHelper {

    private static final boolean DEBUG_NETWORK = BuildConfig.DEBUG;
    private static final String LOG_PREFIX = "[DDZ-WSS]";

    private DouDiZhuLogHelper() {}

    public static void logEvent(String tag, String event, String roomCode, int playerId, String messageType) {
        if (!DEBUG_NETWORK) return;
        Log.d(tag, LOG_PREFIX + " [" + event + "] room=" + (roomCode != null ? roomCode : "-")
                + " player=" + playerId + " type=" + (messageType != null ? messageType : "-")
                + " t=" + System.currentTimeMillis());
    }

    public static void logGame(String tag, String event, int seatIndex, String detail) {
        if (!DEBUG_NETWORK) return;
        Log.d(tag, LOG_PREFIX + " [GAME_" + event + "] seat=" + seatIndex + " " + (detail != null ? detail : ""));
    }

    public static void logSeatState(String tag, String context, int mode, int gameState,
                                     int[] seatTypes, int[] seatClientIds) {
        if (!DEBUG_NETWORK) return;
        StringBuilder sb = new StringBuilder();
        sb.append(context).append(" | mode=").append(mode);
        sb.append(" gameState=").append(gameState);
        sb.append(" seats=[");
        for (int i = 0; i < seatTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(i).append(":type=").append(seatTypes[i])
              .append(",cid=").append(seatClientIds[i]);
        }
        sb.append("]");
        Log.d(tag, LOG_PREFIX + " [SEAT_STATE] " + sb.toString());
    }
}

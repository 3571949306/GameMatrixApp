package com.gamecenter.app.network;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.gamecenter.app.R;

public final class RoomCodeHelper {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private RoomCodeHelper() {}

    public static String generateRoomCode() {
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public static boolean isValidRoomCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) return false;
        for (char c : code.toCharArray()) {
            if (CHARS.indexOf(c) < 0) return false;
        }
        return true;
    }

    public static void copyRoomCode(Context context, String roomCode) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("room_code", roomCode);
            cm.setPrimaryClip(clip);
        }
        Toast.makeText(context, context.getString(R.string.online_room_code_copied), Toast.LENGTH_SHORT).show();
    }
}

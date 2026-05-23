package com.gamecenter.app.games.doudizhu;

import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import com.gamecenter.app.network.LANManager;

import java.util.List;

public final class DouDiZhuNetUtil {

    private static final String TAG = "DDZ-NetUtil";

    private DouDiZhuNetUtil() {}

    public static String getClipboardText(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
                    || clipboard.getPrimaryClip().getItemCount() == 0) {
                return "";
            }
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
            return text != null ? text.toString() : "";
        } catch (Exception e) {
            Log.w(TAG, "getClipboardText failed: " + e.getMessage());
            return "";
        }
    }

    public static String getSuggestedIpPrefix(LANManager lanManager) {
        if (lanManager == null) return "192.168.1.";
        List<String> addresses = lanManager.getAllLocalIPv4Addresses();
        for (String ip : addresses) {
            if (ip == null || !ip.contains(".")) continue;
            int lastDot = ip.lastIndexOf('.');
            if (lastDot > 0) {
                return ip.substring(0, lastDot + 1);
            }
        }
        return "192.168.1.";
    }
}

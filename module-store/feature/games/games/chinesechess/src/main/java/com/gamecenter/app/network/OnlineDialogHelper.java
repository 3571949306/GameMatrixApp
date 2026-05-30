package com.gamecenter.app.network;

import androidx.fragment.app.FragmentActivity;

/** 联机对话框助手存根（实际运行时由宿主提供） */
public class OnlineDialogHelper {
    public interface OnRoomCodeEnteredListener { void onRoomCodeEntered(String roomCode); }
    public interface OnCancelListener { void onCancel(); }

    public static android.app.AlertDialog showWaitingDialog(FragmentActivity activity, String roomCode, Runnable onCancel) {
        return null;
    }
    public static void showJoinDialog(FragmentActivity activity, OnRoomCodeEnteredListener listener) {}
}

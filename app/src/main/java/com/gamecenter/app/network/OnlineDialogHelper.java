package com.gamecenter.app.network;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

public final class OnlineDialogHelper {

    private OnlineDialogHelper() {}

    public interface JoinCallback {
        void onJoin(String roomCode);
    }

    public interface LeaveCallback {
        void onLeave();
    }

    public interface ReconnectCallback {
        void onReconnect(String roomCode);
    }

    public static AlertDialog showWaitingDialog(Context context, String roomCode,
                                                 LeaveCallback leaveCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.online_waiting_opponent));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 48, 48, 48);
        content.setGravity(View.TEXT_ALIGNMENT_CENTER);

        TextView roomCodeView = new TextView(context);
        roomCodeView.setTextSize(28);
        roomCodeView.setText(roomCode);
        roomCodeView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        roomCodeView.setPadding(0, 16, 0, 16);
        roomCodeView.setTextColor(0xFF2196F3);
        roomCodeView.setTypeface(null, Typeface.BOLD);

        Button copyBtn = new Button(context);
        copyBtn.setText(context.getString(R.string.online_copy_room_code));
        copyBtn.setTextSize(14);
        copyBtn.setBackgroundColor(0xFF4CAF50);
        copyBtn.setTextColor(0xFFFFFFFF);
        copyBtn.setPadding(24, 8, 24, 8);
        copyBtn.setOnClickListener(v -> RoomCodeHelper.copyRoomCode(context, roomCode));

        TextView hintView = new TextView(context);
        hintView.setTextSize(14);
        hintView.setText(context.getString(R.string.online_share_room_code_hint));
        hintView.setGravity(View.TEXT_ALIGNMENT_CENTER);
        hintView.setPadding(0, 8, 0, 8);

        content.addView(roomCodeView);
        content.addView(copyBtn);
        content.addView(hintView);

        builder.setView(content);
        builder.setCancelable(false);
        builder.setNegativeButton(context.getString(R.string.online_cancel), (d, w) -> {
            if (leaveCallback != null) leaveCallback.onLeave();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    public static void showJoinDialog(Context context, JoinCallback joinCallback) {
        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.online_input_room_code_hint));
        input.setMaxLines(1);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.online_join_room_title))
                .setView(input)
                .setPositiveButton(context.getString(R.string.online_join), (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (RoomCodeHelper.isValidRoomCode(code)) {
                        if (joinCallback != null) joinCallback.onJoin(code);
                    } else {
                        Toast.makeText(context, context.getString(R.string.online_input_room_code_toast), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(context.getString(R.string.online_cancel), null)
                .show();
    }

    public static void showDisconnectDialog(Context context, String message,
                                             boolean isHostSide,
                                             ReconnectCallback reconnectCallback,
                                             LeaveCallback leaveCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.online_disconnected_title))
                .setMessage(message)
                .setCancelable(false);

        if (isHostSide) {
            builder.setPositiveButton(context.getString(R.string.online_waiting_reconnect), (d, w) ->
                    Toast.makeText(context, context.getString(R.string.online_waiting_opponent_reconnect), Toast.LENGTH_SHORT).show());
        } else {
            builder.setPositiveButton(context.getString(R.string.online_reconnect), (d, w) -> {
                if (reconnectCallback != null) reconnectCallback.onReconnect(null);
            });
        }

        builder.setNegativeButton(context.getString(R.string.online_leave_room), (d, w) -> {
            if (leaveCallback != null) leaveCallback.onLeave();
        });
        builder.show();
    }
}

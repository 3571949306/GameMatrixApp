package com.gamecenter.app.network;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OnlineChatHelper {

    private static final int MAX_MESSAGES = 50;

    private final Context context;
    private final Handler mainHandler;
    private final List<ChatMessage> messages = new ArrayList<>();
    private TextView chatDisplay;
    private ScrollView chatScroll;
    private AlertDialog chatDialog;
    private OnChatMessageSendListener sendListener;
    private boolean inlineMode = false;

    public interface OnChatMessageSendListener {
        void onSend(String message);
    }

    public static class ChatMessage {
        public final String sender;
        public final String text;
        public final boolean isMine;

        public ChatMessage(String sender, String text, boolean isMine) {
            this.sender = sender;
            this.text = text;
            this.isMine = isMine;
        }
    }

    public OnlineChatHelper(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setOnChatMessageSendListener(OnChatMessageSendListener listener) {
        this.sendListener = listener;
    }

    public void setInlineDisplay(TextView display, ScrollView scroll) {
        this.chatDisplay = display;
        this.chatScroll = scroll;
        this.inlineMode = true;
        for (ChatMessage msg : messages) {
            appendToDisplay(msg.sender, msg.text, msg.isMine);
        }
    }

    public JSONObject createChatMessage(String text) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "CHAT");
            msg.put("text", text);
            return msg;
        } catch (JSONException e) {
            return null;
        }
    }

    public boolean isChatMessage(JSONObject message) {
        return "CHAT".equals(message.optString("type", ""));
    }

    public void handleIncomingChat(JSONObject message) {
        String text = message.optString("text", "");
        if (text.isEmpty()) return;
        addMessage("对手", text, false);
    }

    public void sendChat(String text) {
        if (text == null || text.trim().isEmpty()) return;
        addMessage("我", text.trim(), true);
        if (sendListener != null) {
            sendListener.onSend(text.trim());
        }
    }

    private void addMessage(String sender, String text, boolean isMine) {
        mainHandler.post(() -> {
            messages.add(new ChatMessage(sender, text, isMine));
            if (messages.size() > MAX_MESSAGES) {
                messages.remove(0);
            }
            if (chatDisplay != null) {
                appendToDisplay(sender, text, isMine);
            }
        });
    }

    private void appendToDisplay(String sender, String text, boolean isMine) {
        if (chatDisplay == null) return;
        String prefix = isMine ? "我: " : "对手: ";
        chatDisplay.append(prefix + text + "\n");
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    public void showChatDialog() {
        if (inlineMode) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        chatScroll = new ScrollView(context);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        chatDisplay = new TextView(context);
        chatDisplay.setTextSize(14);
        chatDisplay.setPadding(8, 8, 8, 8);
        chatScroll.addView(chatDisplay);

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, 8, 0, 0);

        EditText input = new EditText(context);
        input.setHint("输入消息...");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        android.widget.Button sendBtn = new android.widget.Button(context);
        sendBtn.setText("发送");
        sendBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                sendChat(text);
                input.setText("");
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    sendChat(text);
                    input.setText("");
                }
                return true;
            }
            return false;
        });

        inputRow.addView(input);
        inputRow.addView(sendBtn);

        layout.addView(chatScroll);
        layout.addView(inputRow);

        chatDialog = new AlertDialog.Builder(context)
                .setTitle("聊天")
                .setView(layout)
                .setNegativeButton("关闭", null)
                .create();
        chatDialog.show();

        for (ChatMessage msg : messages) {
            appendToDisplay(msg.sender, msg.text, msg.isMine);
        }
    }

    public void cleanup() {
        if (chatDialog != null && chatDialog.isShowing()) {
            chatDialog.dismiss();
        }
        chatDialog = null;
        if (!inlineMode) {
            chatDisplay = null;
            chatScroll = null;
        }
    }
}

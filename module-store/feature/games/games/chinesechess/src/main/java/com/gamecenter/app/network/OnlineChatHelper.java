package com.gamecenter.app.network;

import android.content.Context;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

/** 联机聊天助手存根（实际运行时由宿主提供） */
public class OnlineChatHelper {
    public interface OnChatMessageSendListener { void onSendChat(String text); }

    public OnlineChatHelper(Context context) {}
    public void setOnChatMessageSendListener(OnChatMessageSendListener l) {}
    public void setInlineDisplay(TextView textView, ScrollView scrollView) {}
    public JSONObject createChatMessage(String text) { return new JSONObject(); }
    public boolean isChatMessage(JSONObject json) { return false; }
    public void handleIncomingChat(JSONObject json) {}
    public void sendChat(String text) {}
    public void cleanup() {}
}

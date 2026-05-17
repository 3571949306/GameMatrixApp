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

/**
 * 在线聊天辅助类 — 封装联机对战中玩家之间的实时聊天功能。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理聊天消息的收发与本地存储（内存列表，上限 {@link #MAX_MESSAGES} 条）</li>
 *   <li>支持两种展示模式：弹窗模式（Dialog）和内嵌模式（Inline），由 {@link #inlineMode} 控制</li>
 *   <li>将聊天消息序列化为 JSON 协议格式，与 {@link GameSocketServer}/{@link GameSocketClient} 配合传输</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>所有 UI 操作通过 {@link Handler}({@link Looper#getMainLooper()}) 投递到主线程，确保线程安全</li>
 *   <li>消息列表采用滑动窗口策略，超过上限后移除最早的消息，避免内存无限增长</li>
 *   <li>聊天 UI 的构建完全通过代码动态创建，不依赖 XML 布局，便于复用</li>
 * </ul>
 */
public class OnlineChatHelper {

    /** 聊天消息列表的最大容量，超出后移除最早的消息 */
    private static final int MAX_MESSAGES = 50;

    /** 应用级 Context，避免持有 Activity 导致内存泄漏 */
    private final Context context;
    /** 聊天消息列表，按时间顺序存储 */
    private final List<ChatMessage> messages = new ArrayList<>();
    /** 聊天内容显示区域 */
    private TextView chatDisplay;
    /** 聊天内容滚动容器 */
    private ScrollView chatScroll;
    /** 弹窗模式下的对话框实例 */
    private AlertDialog chatDialog;
    /** 消息发送监听器，由外部设置，用于将聊天消息通过网络发送给对手 */
    private OnChatMessageSendListener sendListener;
    /** 是否使用内嵌模式展示聊天（true=嵌入游戏界面，false=弹窗模式） */
    private boolean inlineMode = false;

    /**
     * 聊天消息发送监听接口。
     * 当用户发送一条聊天消息时，通过此回调通知外部（通常是网络层）将消息传输给对手。
     */
    public interface OnChatMessageSendListener {
        /**
         * 用户发送消息时回调。
         *
         * @param message 用户输入的消息文本（已去除首尾空白）
         */
        void onSend(String message);
    }

    /**
     * 聊天消息数据类，封装一条聊天消息的所有信息。
     * <p>
     * 使用 final 字段确保消息不可变，避免在多线程环境下被意外修改。
     */
    public static class ChatMessage {
        /** 发送者名称（"我" 或 "对手"） */
        public final String sender;
        /** 消息文本内容 */
        public final String text;
        /** 是否为本方发送的消息，用于 UI 区分显示样式 */
        public final boolean isMine;

        /**
         * 构造一条聊天消息。
         *
         * @param sender 发送者名称
         * @param text   消息内容
         * @param isMine 是否为本方发送
         */
        public ChatMessage(String sender, String text, boolean isMine) {
            this.sender = sender;
            this.text = text;
            this.isMine = isMine;
        }
    }

    /**
     * 构造聊天辅助类实例。
     * <p>
     * 使用 {@code context.getApplicationContext()} 获取应用级 Context，
     * 防止持有 Activity Context 导致内存泄漏。
     *
     * @param context 上下文，通常为 Activity 实例
     */
    public OnlineChatHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 设置聊天消息发送监听器。
     *
     * @param listener 消息发送监听器，当用户发送消息时回调
     */
    public void setOnChatMessageSendListener(OnChatMessageSendListener listener) {
        this.sendListener = listener;
    }

    /**
     * 设置内嵌模式的聊天显示区域。
     * <p>
     * 调用此方法后进入内嵌模式（{@code inlineMode = true}），聊天内容将直接显示在
     * 指定的 TextView 中，而非弹窗。同时会将历史消息回放到显示区域。
     *
     * @param display 聊天内容显示的 TextView
     * @param scroll  聊天区域的滚动容器
     */
    public void setInlineDisplay(TextView display, ScrollView scroll) {
        this.chatDisplay = display;
        this.chatScroll = scroll;
        this.inlineMode = true;
        // 将已有的历史消息回放到新设置的显示区域
        for (ChatMessage msg : messages) {
            appendToDisplay(msg.sender, msg.text, msg.isMine);
        }
    }

    /**
     * 创建聊天消息的 JSON 协议对象。
     * <p>
     * 协议格式：{@code {"type": "CHAT", "text": "消息内容"}}
     * 此格式与 {@link #isChatMessage(JSONObject)} 配合使用，
     * 用于在游戏消息流中区分聊天消息和游戏逻辑消息。
     *
     * @param text 消息文本内容
     * @return 构造好的 JSON 对象；若 JSON 构造异常则返回 null
     */
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

    /**
     * 判断一个 JSON 消息是否为聊天消息。
     *
     * @param message 待判断的 JSON 消息对象
     * @return 如果 type 字段为 "CHAT" 则返回 true
     */
    public boolean isChatMessage(JSONObject message) {
        return "CHAT".equals(message.optString("type", ""));
    }

    /**
     * 处理收到的对手聊天消息。
     * <p>
     * 从 JSON 中提取文本内容，若文本为空则忽略；否则添加到本地消息列表并显示。
     *
     * @param message 收到的 JSON 聊天消息
     */
    public void handleIncomingChat(JSONObject message) {
        String text = message.optString("text", "");
        // 空消息不处理，避免显示无意义内容
        if (text.isEmpty()) return;
        addMessage("对手", text, false);
    }

    /**
     * 发送本方聊天消息。
     * <p>
     * 将消息添加到本地显示列表，并通过 {@link OnChatMessageSendListener} 通知
     * 外部网络层将消息发送给对手。空消息和纯空白消息会被忽略。
     *
     * @param text 用户输入的消息文本
     */
    public void sendChat(String text) {
        // 过滤空消息和纯空白消息
        if (text == null || text.trim().isEmpty()) return;
        addMessage("我", text.trim(), true);
        // 通知外部网络层发送消息
        if (sendListener != null) {
            sendListener.onSend(text.trim());
        }
    }

    /**
     * 添加一条消息到本地列表并更新显示。
     * <p>
     * 通过主线程 Handler 投递操作，确保 UI 更新在主线程执行。
     * 当消息数量超过 {@link #MAX_MESSAGES} 时，移除最早的一条消息（滑动窗口策略）。
     *
     * @param sender 发送者名称
     * @param text   消息内容
     * @param isMine 是否为本方发送
     */
    private void addMessage(String sender, String text, boolean isMine) {
        new Handler(Looper.getMainLooper()).post(() -> {
            messages.add(new ChatMessage(sender, text, isMine));
            // 滑动窗口：超出上限时移除最早的消息
            if (messages.size() > MAX_MESSAGES) {
                messages.remove(0);
            }
            // 更新聊天显示区域
            if (chatDisplay != null) {
                appendToDisplay(sender, text, isMine);
            }
        });
    }

    /**
     * 将一条消息追加到聊天显示区域。
     * <p>
     * 根据消息发送方添加不同的前缀（"我: " 或 "对手: "），
     * 并自动滚动到底部以显示最新消息。
     *
     * @param sender 发送者名称
     * @param text   消息内容
     * @param isMine 是否为本方发送
     */
    private void appendToDisplay(String sender, String text, boolean isMine) {
        if (chatDisplay == null) return;
        // 根据发送方选择不同的前缀标识
        String prefix = isMine ? "我: " : "对手: ";
        chatDisplay.append(prefix + text + "\n");
        // 自动滚动到底部，确保用户看到最新消息
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /**
     * 以弹窗模式显示聊天界面。
     * <p>
     * 如果当前处于内嵌模式（{@code inlineMode = true}），则不创建弹窗，
     * 因为聊天内容已经嵌入到游戏界面中。
     * <p>
     * 弹窗包含：消息滚动区域、输入框和发送按钮。
     * 支持键盘发送键（IME_ACTION_SEND）和按钮点击两种发送方式。
     * 弹窗打开后会回放所有历史消息。
     */
    public void showChatDialog() {
        // 内嵌模式下不使用弹窗
        if (inlineMode) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        // 消息滚动区域，占据弹窗大部分空间（weight=1.0）
        chatScroll = new ScrollView(context);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        chatDisplay = new TextView(context);
        chatDisplay.setTextSize(14);
        chatDisplay.setPadding(8, 8, 8, 8);
        chatScroll.addView(chatDisplay);

        // 输入行：输入框 + 发送按钮
        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, 8, 0, 0);

        EditText input = new EditText(context);
        input.setHint("输入消息...");
        input.setSingleLine(true);
        // 设置键盘回车键为"发送"动作
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        android.widget.Button sendBtn = new android.widget.Button(context);
        sendBtn.setText("发送");
        // 点击发送按钮的处理逻辑
        sendBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                sendChat(text);
                input.setText("");
            }
        });

        // 键盘发送键的处理逻辑
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

        // 回放历史消息到弹窗的显示区域
        for (ChatMessage msg : messages) {
            appendToDisplay(msg.sender, msg.text, msg.isMine);
        }
    }

    /**
     * 清理聊天相关资源。
     * <p>
     * 关闭弹窗模式的对话框，释放 chatDisplay 和 chatScroll 引用。
     * 内嵌模式下仅关闭弹窗，不释放显示区域引用（由 Activity 管理生命周期）。
     * 应在 Activity 的 onDestroy 中调用此方法。
     */
    public void cleanup() {
        // 关闭正在显示的聊天弹窗
        if (chatDialog != null && chatDialog.isShowing()) {
            chatDialog.dismiss();
        }
        chatDialog = null;
        // 非内嵌模式下释放 UI 引用，避免内存泄漏
        if (!inlineMode) {
            chatDisplay = null;
            chatScroll = null;
        }
    }
}

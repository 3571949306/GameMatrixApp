package com.gamecenter.app.ai.history;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.ai.AiPreferences;
import com.gamecenter.app.ai.data.AiMessage;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 消息历史持久化存储 — 管理聊天记录的保存、加载、收藏与清除。
 * <p>
 * 你可以把这个类想象成一个"日记本管理员"：
 * 它负责把聊天记录写到日记本里（保存）、从日记本里读出来（加载）、
 * 给重要的记录贴上星标（收藏）、以及清空整本日记（清除）。
 * <p>
 * 使用 SharedPreferences 作为底层存储，将消息列表序列化为 JSON 字符串持久化。
 * 收藏 ID 集合使用 SharedPreferences 的 StringSet 原生支持存储。
 * <p>
 * 设计决策：
 * <ul>
 *   <li>选择 SharedPreferences 而非数据库，因为消息量级较小（受 historyMax 上限控制），
 *       JSON 序列化/反序列化的性能开销可接受，且实现简单；</li>
 *   <li>使用 ApplicationContext 获取 SharedPreferences，避免 Activity 级别的内存泄漏；</li>
 *   <li>保存时跳过系统消息（role="system"），仅持久化用户和 AI 的有效对话；</li>
 *   <li>收藏集合独立于消息列表存储，即使消息被清除，收藏标记也会一并清除。</li>
 * </ul>
 */
public final class AiHistoryStore {

    private static final String TAG = "AiHistoryStore";

    // SharedPreferences 文件名
    private static final String PREFS_NAME = "ai_history";

    // 消息列表的存储键，值为 JSON 数组字符串
    private static final String KEY_MESSAGES = "messages";

    // 收藏 ID 集合的存储键，值为 StringSet
    private static final String KEY_FAVORITES = "favorites";

    // 消息持久化存储
    private final SharedPreferences prefs;

    // AI 偏好设置，用于获取历史记录上限等配置
    private final AiPreferences aiPreferences;

    /**
     * 构造历史存储实例。
     * <p>
     * 使用 ApplicationContext 获取 SharedPreferences，确保不会持有 Activity 引用导致泄漏。
     *
     * @param context 上下文（内部会转换为 ApplicationContext）
     */
    public AiHistoryStore(Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.aiPreferences = new AiPreferences(appContext);
    }

    /**
     * 从持久化存储加载消息列表。
     * <p>
     * 将 SharedPreferences 中存储的 JSON 数组字符串反序列化为 AiMessage 列表。
     * 若 JSON 解析失败（数据损坏），则清除损坏数据并返回空列表，避免反复解析报错。
     *
     * @return 加载的消息列表；无数据或解析失败时返回空列表
     */
    public List<AiMessage> loadMessages() {
        List<AiMessage> messages = new ArrayList<>();
        // 从存储中读取 JSON 字符串，默认为空数组 "[]"
        String raw = prefs.getString(KEY_MESSAGES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                // 将 JSON 对象逐个转换为 AiMessage
                messages.add(new AiMessage(
                        item.optString("id"),
                        item.optString("role"),
                        item.optString("content"),
                        item.optLong("timestamp"),
                        item.optString("taskType"),
                        item.optString("source")));
            }
        } catch (Exception ignored) {
            // JSON 解析失败时清除损坏数据，防止下次加载继续报错
            prefs.edit().remove(KEY_MESSAGES).apply();
        }
        return messages;
    }

    /**
     * 将消息列表持久化到存储。
     * <p>
     * 序列化规则：
     * <ul>
     *   <li>跳过系统消息（role="system"），不持久化无实际意义的系统提示；</li>
     *   <li>受 historyMax 上限控制，超出部分的消息会被截断（保留最新的）；</li>
     *   <li>单条消息序列化失败时跳过该条并记录警告日志，不影响其余消息保存。</li>
     * </ul>
     *
     * @param messages 待保存的消息列表（按时间倒序，新消息在前）
     */
    public void saveMessages(List<AiMessage> messages) {
        JSONArray array = new JSONArray();
        // 从偏好设置获取历史记录上限，确保至少保留 1 条
        int max = Math.max(1, aiPreferences.getHistoryMax());
        int count = 0;
        for (AiMessage message : messages) {
            // 跳过系统消息，不持久化
            if ("system".equals(message.role)) {
                continue;
            }
            // 达到上限后停止保存，超出部分自然丢弃
            if (count >= max) {
                break;
            }
            try {
                // 将消息对象转换为 JSON 并添加到数组
                JSONObject item = new JSONObject();
                item.put("id", message.id);
                item.put("role", message.role);
                item.put("content", message.content);
                item.put("timestamp", message.timestamp);
                item.put("taskType", message.taskType);
                item.put("source", message.source);
                array.put(item);
                count++;
            } catch (Exception ignored) {
                Log.w(TAG, "Save message: " + ignored.getMessage());
            }
        }
        // 将 JSON 数组转为字符串存入 SharedPreferences
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply();
    }

    /**
     * 获取所有已收藏消息的 ID 集合。
     * <p>
     * 返回的是新创建的 HashSet 实例，修改返回值不会影响持久化存储中的数据。
     * 这是因为 SharedPreferences.getStringSet() 返回的集合不应直接修改
     * （修改可能导致存储一致性问题和 ClassCastException）。
     *
     * @return 收藏消息 ID 的副本集合
     */
    public Set<String> getFavoriteIds() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    /**
     * 判断指定消息是否已被收藏。
     *
     * @param id 消息 ID
     * @return true 表示已收藏
     */
    public boolean isFavorite(String id) {
        return getFavoriteIds().contains(id);
    }

    /**
     * 切换消息的收藏状态（收藏 ↔ 取消收藏）。
     * <p>
     * 先读取当前收藏集合，若消息 ID 已存在则移除（取消收藏），
     * 若不存在则添加（收藏），然后将修改后的集合写回持久化存储。
     * 就像给消息贴星标：有星标就摘掉，没星标就贴上。
     *
     * @param id 要切换收藏状态的消息 ID
     * @return 切换后的收藏状态（true=已收藏，false=已取消收藏）
     */
    public boolean toggleFavorite(String id) {
        Set<String> favorites = getFavoriteIds();
        boolean favorite;
        if (favorites.contains(id)) {
            favorites.remove(id);
            favorite = false;
        } else {
            favorites.add(id);
            favorite = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
        return favorite;
    }

    /**
     * 清除所有历史数据，包括消息列表和收藏集合。
     */
    public void clear() {
        prefs.edit().remove(KEY_MESSAGES).remove(KEY_FAVORITES).apply();
    }
}

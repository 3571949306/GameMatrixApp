package com.gamecenter.app.ai.history;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.ai.AiPreferences;
import com.gamecenter.app.ai.data.AiMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AiHistoryStore {

    private static final String PREFS_NAME = "ai_history";
    private static final String KEY_MESSAGES = "messages";
    private static final String KEY_FAVORITES = "favorites";

    private final SharedPreferences prefs;
    private final AiPreferences aiPreferences;

    public AiHistoryStore(Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.aiPreferences = new AiPreferences(appContext);
    }

    public List<AiMessage> loadMessages() {
        List<AiMessage> messages = new ArrayList<>();
        String raw = prefs.getString(KEY_MESSAGES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                messages.add(new AiMessage(
                        item.optString("id"),
                        item.optString("role"),
                        item.optString("content"),
                        item.optLong("timestamp"),
                        item.optString("taskType"),
                        item.optString("source")));
            }
        } catch (Exception ignored) {
            prefs.edit().remove(KEY_MESSAGES).apply();
        }
        return messages;
    }

    public void saveMessages(List<AiMessage> messages) {
        JSONArray array = new JSONArray();
        int max = Math.max(1, aiPreferences.getHistoryMax());
        int count = 0;
        for (AiMessage message : messages) {
            if ("system".equals(message.role)) {
                continue;
            }
            if (count >= max) {
                break;
            }
            try {
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
            }
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply();
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public boolean isFavorite(String id) {
        return getFavoriteIds().contains(id);
    }

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

    public void clear() {
        prefs.edit().remove(KEY_MESSAGES).remove(KEY_FAVORITES).apply();
    }
}

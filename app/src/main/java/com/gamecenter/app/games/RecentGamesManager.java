package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 最近游玩记录管理器（Feature C / HOME_REVAMP）。
 *
 * <p>记录用户最近玩过的游戏 ID 列表（最多 {@link #MAX_RECENT} 个），
 * 供游戏大厅首页"最近游玩"横向滚动条展示。使用 LIFO + 去重策略：
 * 最新玩过的游戏排到列表最前，已存在的会先移除再插入到队首。</p>
 *
 * <p>底层数据以 JSON 数组存储在 SharedPreferences 中，进程重启后仍保留。</p>
 */
public class RecentGamesManager {

    /** 最多保留的最近游玩条目数。 */
    public static final int MAX_RECENT = 8;

    private static final String PREF_NAME = "recent_games";
    private static final String KEY_LIST = "recent_game_ids";

    private static volatile RecentGamesManager instance;

    private final SharedPreferences prefs;

    private RecentGamesManager(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 获取单例。 */
    @NonNull
    public static RecentGamesManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (RecentGamesManager.class) {
                if (instance == null) {
                    instance = new RecentGamesManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 记录一次游戏游玩（插入到队首，去重，超过上限截断）。
     *
     * @param gameId 游戏 ID
     */
    public void recordPlay(@NonNull String gameId) {
        if (gameId.isEmpty()) return;
        List<String> current = getRecentIds();
        // 使用 LinkedHashSet 去重并保持插入顺序
        Set<String> set = new LinkedHashSet<>();
        set.add(gameId);
        set.addAll(current);
        List<String> result = new ArrayList<>(set);
        if (result.size() > MAX_RECENT) {
            result = new ArrayList<>(result.subList(0, MAX_RECENT));
        }
        saveList(result);
    }

    /**
     * 获取最近游玩的 gameId 列表（按时间倒序，最新在前）。
     */
    @NonNull
    public List<String> getRecentIds() {
        String json = prefs.getString(KEY_LIST, "");
        if (json.isEmpty()) return new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            List<String> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i, ""));
            }
            return list;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    /** 清空最近游玩记录。 */
    public void clear() {
        prefs.edit().remove(KEY_LIST).apply();
    }

    private void saveList(@NonNull List<String> list) {
        JSONArray arr = new JSONArray();
        for (String id : list) {
            if (id != null && !id.isEmpty()) {
                arr.put(id);
            }
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply();
    }
}

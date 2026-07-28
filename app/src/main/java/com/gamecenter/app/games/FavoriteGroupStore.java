package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏分组存储（P0-2）。
 * <p>
 * 复用 {@link GameUsageStore} 的 SharedPreferences（"game_usage"），保持数据一致性。
 * </p>
 * <p>
 * 存储模型：
 * <ul>
 *   <li>{@code favorite_groups_str} — JSON 列表，元素为 {@link Group}（id + name）</li>
 *   <li>{@code favorite_group_map_str} — JSON Map，key=gameId, value=groupId</li>
 * </ul>
 * </p>
 * <p>智能分组（按分类）由调用方通过 {@link GameRegistry} 现算，不持久化。</p>
 */
public final class FavoriteGroupStore {

    private static final String PREFS_NAME = "game_usage";
    private static final String KEY_GROUPS = "favorite_groups_str";
    private static final String KEY_GROUP_MAP = "favorite_group_map_str";

    public static final String DEFAULT_GROUP_ID = "default";

    private final SharedPreferences prefs;
    private final Gson gson;

    public FavoriteGroupStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /** 分组定义。 */
    public static final class Group {
        public final String id;
        public final String name;

        public Group(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** 获取所有用户自定义分组（不含默认分组与智能分组）。 */
    @NonNull
    public List<Group> getGroups() {
        List<Group> groups = readGroups();
        if (groups.isEmpty()) {
            // 首次使用时创建一个默认分组，便于用户上手
            groups = new ArrayList<>();
            groups.add(new Group(DEFAULT_GROUP_ID, "默认分组"));
            saveGroups(groups);
        }
        return groups;
    }

    /** 新增分组。返回新分组 id；name 为空或重复返回 null。 */
    @Nullable
    public String addGroup(@NonNull String name) {
        if (TextUtils.isEmpty(name)) return null;
        List<Group> groups = readGroups();
        for (Group g : groups) {
            if (g.name.equals(name)) return null;
        }
        String id = "g_" + System.currentTimeMillis();
        groups.add(new Group(id, name));
        saveGroups(groups);
        return id;
    }

    /** 重命名分组。 */
    public boolean renameGroup(@NonNull String groupId, @NonNull String newName) {
        if (TextUtils.isEmpty(newName)) return false;
        List<Group> groups = readGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id.equals(groupId)) {
                groups.set(i, new Group(groupId, newName));
                saveGroups(groups);
                return true;
            }
        }
        return false;
    }

    /** 删除分组：分组下的游戏自动迁移到默认分组。 */
    public boolean deleteGroup(@NonNull String groupId) {
        if (DEFAULT_GROUP_ID.equals(groupId)) return false;
        List<Group> groups = readGroups();
        boolean removed = groups.removeIf(g -> g.id.equals(groupId));
        if (!removed) return false;
        saveGroups(groups);
        // 迁移映射
        Map<String, String> map = readMap();
        for (Map.Entry<String, String> e : new LinkedHashMap<>(map).entrySet()) {
            if (groupId.equals(e.getValue())) {
                map.put(e.getKey(), DEFAULT_GROUP_ID);
            }
        }
        saveMap(map);
        return true;
    }

    /** 设置游戏所属分组。 */
    public void setGameGroup(@NonNull String gameId, @NonNull String groupId) {
        Map<String, String> map = readMap();
        map.put(gameId, groupId);
        saveMap(map);
    }

    /** 获取游戏所属分组 id；未设置返回 {@link #DEFAULT_GROUP_ID}。 */
    @NonNull
    public String getGameGroup(@NonNull String gameId) {
        Map<String, String> map = readMap();
        String gid = map.get(gameId);
        return gid == null ? DEFAULT_GROUP_ID : gid;
    }

    /** 获取分组下所有游戏 id（需配合 {@link GameUsageStore#getFavoriteIds()} 过滤已收藏的）。 */
    @NonNull
    public List<String> getGameIdsInGroup(@NonNull String groupId) {
        Map<String, String> map = readMap();
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (groupId.equals(e.getValue())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /** 移除某游戏的分组映射（取消收藏时调用）。 */
    public void removeGame(@NonNull String gameId) {
        Map<String, String> map = readMap();
        if (map.remove(gameId) != null) {
            saveMap(map);
        }
    }

    // ==================== 内部持久化 ====================

    @NonNull
    private List<Group> readGroups() {
        String json = prefs.getString(KEY_GROUPS, null);
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<Group>>() {}.getType();
            List<Group> list = gson.fromJson(json, t);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveGroups(@NonNull List<Group> groups) {
        try {
            prefs.edit().putString(KEY_GROUPS, gson.toJson(groups)).apply();
        } catch (Exception ignored) {}
    }

    @NonNull
    private Map<String, String> readMap() {
        String json = prefs.getString(KEY_GROUP_MAP, null);
        if (TextUtils.isEmpty(json)) return new LinkedHashMap<>();
        try {
            Type t = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> m = gson.fromJson(json, t);
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void saveMap(@NonNull Map<String, String> map) {
        try {
            prefs.edit().putString(KEY_GROUP_MAP, gson.toJson(map)).apply();
        } catch (Exception ignored) {}
    }
}

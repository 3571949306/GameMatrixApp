package com.gamecenter.app.games;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.modulestore.ModuleVersionChecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏注册中心。
 *
 * <p>管理所有游戏条目（内置 + 动态模块），支持：
 * <ul>
 *   <li>内置游戏的静态注册</li>
 *   <li>动态模块的注册/卸载</li>
 *   <li>内置游戏版本更新检查（版本号判断）</li>
 * </ul>
 *
 * <p>版本比较策略：
 * <ol>
 *   <li>主逻辑：版本号比较（内置 vs 商店），商店版本更高则加载外置版本</li>
 * </ol>
 *
 * @author Software Engineer (Alex)
 * @version 2.1
 * @since 2026-05-26
 */
public final class GameRegistry {
    private static final String TAG = "GameRegistry";

    public static final String CATEGORY_CLASSICS = "classics";
    public static final String CATEGORY_PUZZLE = "puzzle";
    public static final String CATEGORY_CASUAL = "casual";

    /** 内置游戏默认版本号 */
    private static final int BUILT_IN_VERSION_CODE = 1;

    /** 内置游戏默认版本名 */
    private static final String BUILT_IN_VERSION_NAME = "1.0.0";

    private static final Map<String, List<Entry>> dynamicEntries = new LinkedHashMap<>();

    /** 内置模块版本缓存（模块 ID -> 版本号） */
    private static final Map<String, Integer> builtInVersionCache = new LinkedHashMap<>();

    private GameRegistry() {
    }

    /**
     * 获取指定游戏 ID 对应的 Activity 类。
     *
     * <p>查找顺序：
     * <ol>
     *   <li>内置静态游戏</li>
     *   <li>动态注册的游戏</li>
     * </ol>
     *
     * @param context Android Context
     * @param gameId  游戏 ID
     * @return Activity 类，未找到返回 null
     */
    public static Class<?> getActivityClassById(Context context, String gameId) {
        for (Category category : getCategories(context)) {
            for (Entry entry : category.games) {
                if (entry.id.equals(gameId)) {
                    return entry.activityClass;
                }
            }
        }
        return null;
    }

    /**
     * 获取指定游戏 ID 对应的 Fragment 类。
     *
     * @param context Android Context
     * @param gameId  游戏 ID
     * @return Fragment 类，当前固定返回 null（暂未使用 Fragment 模式）
     */
    public static Class<? extends androidx.fragment.app.Fragment> getFragmentClassById(
            Context context,
            String gameId
    ) {
        return null;
    }

    /**
     * 获取指定游戏 ID 对应的显示名称。
     *
     * @param context Android Context
     * @param gameId  游戏 ID
     * @return 游戏名称，未找到返回 null
     */
    @Nullable
    public static String getGameNameById(@NonNull Context context, @NonNull String gameId) {
        for (Category category : getCategories(context)) {
            for (Entry entry : category.games) {
                if (entry.id.equals(gameId)) {
                    return entry.name;
                }
            }
        }
        return null;
    }

    /**
     * 注册动态游戏条目。
     *
     * @param entry 游戏条目
     * @return 注册成功返回 true，ID 冲突返回 false
     */
    public static boolean register(Entry entry) {
        synchronized (dynamicEntries) {
            for (List<Entry> entries : dynamicEntries.values()) {
                for (Entry existing : entries) {
                    if (existing.id.equals(entry.id)) {
                        return false;
                    }
                }
            }

            List<Entry> list = dynamicEntries.get(entry.categoryKey);
            if (list == null) {
                list = new ArrayList<>();
                dynamicEntries.put(entry.categoryKey, list);
            }
            list.add(entry);
            return true;
        }
    }

    /**
     * 注销动态游戏条目。
     *
     * @param gameId 游戏 ID
     * @return 注销成功返回 true，未找到返回 false
     */
    public static boolean unregister(String gameId) {
        synchronized (dynamicEntries) {
            boolean removed = false;
            for (List<Entry> list : dynamicEntries.values()) {
                removed |= list.removeIf(entry -> entry.id.equals(gameId));
            }
            return removed;
        }
    }

    /**
     * 清除所有动态注册的游戏条目。
     */
    public static void clearDynamicEntries() {
        synchronized (dynamicEntries) {
            dynamicEntries.clear();
        }
    }

    /**
     * 启动游戏（内置游戏）。
     *
     * <p>启动逻辑：通过游戏 ID 查找对应的 Activity 类并启动。
     *
     * @param context Android Context
     * @param gameId  游戏 ID
     * @return 启动成功返回 true，否则返回 false
     */
    public static boolean launchGame(@NonNull Context context, @NonNull String gameId) {
        if (context == null || gameId == null) {
            return false;
        }

        // 内置游戏启动
        Class<?> activityClass = getActivityClassById(context, gameId);
        if (activityClass != null) {
            try {
                Intent intent = new Intent(context, activityClass);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // 将 gameId 传递给目标 Activity（DynamicGameActivity 需要此参数定位模块）
                intent.putExtra("gameId", gameId);
                context.startActivity(intent);
                Log.i(TAG, "启动内置游戏: " + gameId);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "启动内置游戏失败: " + gameId, e);
                return false;
            }
        }

        Log.w(TAG, "未找到游戏: " + gameId);
        return false;
    }

    // ========== 内置游戏更新机制 ==========

    /**
     * 注册内置游戏的版本信息。
     *
     * <p>在应用启动时调用，为每个内置游戏注册基线版本号。
     * 后续版本比较将基于此基线版本号。
     *
     * @param gameId      游戏 ID
     * @param versionCode 内置版本号
     */
    public static void registerBuiltInVersion(@NonNull String gameId, int versionCode) {
        if (gameId == null || gameId.isEmpty()) {
            return;
        }
        builtInVersionCache.put(gameId, versionCode);
        Log.d(TAG, "注册内置游戏版本: " + gameId + " v" + versionCode);
    }

    /**
     * 检查内置游戏是否有更新。
     *
     * <p>版本比较策略（架构决策2）：
     * <ol>
     *   <li>主逻辑：版本号比较（内置 vs 外置），外置更高则提示更新</li>
     *   <li>兜底机制：如果内置版本 ClassLoader 加载失败，自动使用外置版本</li>
     * </ol>
     *
     * @param context Android Context
     * @param gameId  游戏 ID
     * @return 有更新返回最新的 ModuleInfo，否则返回 null
     */
    @Nullable
    public static ModuleInfo checkBuiltInGameUpdate(@NonNull Context context,
                                                     @NonNull String gameId) {
        if (context == null || gameId == null || gameId.isEmpty()) {
            return null;
        }

        Integer builtInVersion = builtInVersionCache.get(gameId);
        if (builtInVersion == null) {
            builtInVersion = BUILT_IN_VERSION_CODE;
        }

        // 使用 ModuleVersionChecker 进行版本比较
        ModuleVersionChecker checker = new ModuleVersionChecker(context);

        // 构造内置模块信息
        ModuleInfo builtInInfo = new ModuleInfo();
        builtInInfo.setModuleId(gameId);
        builtInInfo.setVersionCode(builtInVersion);
        builtInInfo.setBuiltIn(true);

        // 检查是否有外置版本更新
        ModuleInfo updateInfo = checker.checkForUpdates(builtInInfo);
        if (updateInfo != null) {
            boolean shouldLoad = checker.shouldLoadExternal(builtInVersion, updateInfo);
            if (shouldLoad) {
                Log.i(TAG, "内置游戏有更新: " + gameId
                        + " (内置 v" + builtInVersion
                        + " -> 外置 v" + updateInfo.getVersionCode() + ")");
                return updateInfo;
            }
        }

        return null;
    }

    /**
     * 检查所有内置游戏的更新。
     *
     * @param context Android Context
     * @return 有更新的游戏列表（ModuleInfo），无更新返回空列表
     */
    @NonNull
    public static List<ModuleInfo> checkAllBuiltInGameUpdates(@NonNull Context context) {
        List<ModuleInfo> updates = new ArrayList<>();

        for (String gameId : builtInVersionCache.keySet()) {
            try {
                ModuleInfo update = checkBuiltInGameUpdate(context, gameId);
                if (update != null) {
                    updates.add(update);
                }
            } catch (Exception e) {
                Log.e(TAG, "检查游戏更新失败: " + gameId, e);
            }
        }

        Log.d(TAG, "内置游戏更新检查完成: " + updates.size() + " 个有更新");
        return updates;
    }

    /**
     * 获取内置游戏的版本号。
     *
     * @param gameId 游戏 ID
     * @return 版本号，未注册返回默认值 1
     */
    public static int getBuiltInVersionCode(@NonNull String gameId) {
        Integer version = builtInVersionCache.get(gameId);
        return version != null ? version : BUILT_IN_VERSION_CODE;
    }

    /**
     * 判断指定游戏是否为 RePlugin 插件。
     *
     * @param gameId 游戏 ID
    // ========== 分类获取 ==========

    /**
     * 获取所有游戏分类（合并静态 + 动态 + 插件）。
     *
     * @param context Android Context
     * @return 分类列表
     */
    public static List<Category> getCategories(Context context) {
        List<Category> staticCategories = buildStaticCategories(context);
        Map<String, List<Entry>> merged = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();

        for (Category category : staticCategories) {
            merged.put(category.categoryKey, new ArrayList<>(category.games));
            names.put(category.categoryKey, category.name);
        }

        synchronized (dynamicEntries) {
            for (Map.Entry<String, List<Entry>> entry : dynamicEntries.entrySet()) {
                String key = entry.getKey();
                List<Entry> target = merged.get(key);
                if (target == null) {
                    target = new ArrayList<>();
                    merged.put(key, target);
                    names.put(key, categoryName(context, key));
                }
                target.addAll(entry.getValue());
            }
        }

        List<Category> result = new ArrayList<>();
        for (Map.Entry<String, String> name : names.entrySet()) {
            List<Entry> games = merged.get(name.getKey());
            if (games != null && !games.isEmpty()) {
                result.add(new Category(name.getValue(), games, name.getKey()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 扁平化分类列表为游戏条目列表。
     *
     * @param categories 分类列表
     * @return 游戏条目列表
     */
    public static List<Entry> flatten(List<Category> categories) {
        List<Entry> games = new ArrayList<>();
        for (Category category : categories) {
            games.addAll(category.games);
        }
        return Collections.unmodifiableList(games);
    }

    private static List<Category> buildStaticCategories(Context context) {
        // 所有游戏已通过模块商店动态加载，不再内置硬编码游戏。
        // 用户可在模块商店中浏览、下载并安装游戏。
        return new ArrayList<>();
    }

    private static String categoryName(Context context, String key) {
        if (CATEGORY_PUZZLE.equals(key)) return context.getString(R.string.category_puzzle);
        if (CATEGORY_CASUAL.equals(key)) return context.getString(R.string.category_casual);
        return context.getString(R.string.category_classics);
    }

    // ========== 内部数据类 ==========

    /**
     * 游戏分类。
     */
    public static final class Category {
        public final String name;
        public final List<Entry> games;
        public final String categoryKey;

        private Category(String name, List<Entry> games, String categoryKey) {
            this.name = name;
            this.games = Collections.unmodifiableList(games);
            this.categoryKey = categoryKey;
        }
    }

    /**
     * 游戏条目。
     */
    public static final class Entry {
        public final String id;
        public final int iconRes;
        public final String name;
        public final String desc;
        public final Class<?> activityClass;
        public final String category;
        public final String categoryKey;

        public Entry(String id, int iconRes, String name, String desc,
                     Class<?> activityClass, String category, String categoryKey) {
            this.id = id;
            this.iconRes = iconRes;
            this.name = name;
            this.desc = desc;
            this.activityClass = activityClass;
            this.category = category;
            this.categoryKey = categoryKey;
        }

        /**
         * 检查此游戏是否有可用的更新。
         *
         * @param context Android Context
         * @return 有更新返回 ModuleInfo，否则返回 null
         */
        @Nullable
        public ModuleInfo checkForUpdate(@NonNull Context context) {
            return checkBuiltInGameUpdate(context, this.id);
        }
    }
}

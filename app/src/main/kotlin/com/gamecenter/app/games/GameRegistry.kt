package com.gamecenter.app.games

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.gamecenter.app.R
import com.gamecenter.app.models.ModuleInfo
import com.gamecenter.app.modulestore.ModuleVersionChecker
import java.util.Collections

/**
 * 游戏注册中心。
 */
object GameRegistry {
    private const val TAG = "GameRegistry"

    const val CATEGORY_CLASSICS = "classics"
    const val CATEGORY_PUZZLE = "puzzle"
    const val CATEGORY_CASUAL = "casual"

    /** 内置游戏默认版本号 */
    private const val BUILT_IN_VERSION_CODE = 1

    /** 内置游戏默认版本名 */
    private const val BUILT_IN_VERSION_NAME = "1.0.0"

    private val dynamicEntries = LinkedHashMap<String, MutableList<Entry>>()

    /** 内置模块版本缓存（模块 ID -> 版本号） */
    private val builtInVersionCache = LinkedHashMap<String, Int>()

    /**
     * 获取指定游戏 ID 对应的 Activity 类。
     */
    @JvmStatic
    fun getActivityClassById(context: Context?, gameId: String?): Class<*>? {
        if (context == null || gameId == null) return null
        for (category in getCategories(context)) {
            for (entry in category.games) {
                if (entry.id == gameId) {
                    return entry.activityClass
                }
            }
        }
        return null
    }

    /**
     * 获取指定游戏 ID 对应的 Fragment 类。
     */
    @JvmStatic
    fun getFragmentClassById(context: Context, gameId: String): Class<out androidx.fragment.app.Fragment>? {
        return null
    }

    /**
     * 获取指定游戏 ID 对应的显示名称。
     */
    @JvmStatic
    @Nullable
    fun getGameNameById(context: Context, gameId: String): String? {
        for (category in getCategories(context)) {
            for (entry in category.games) {
                if (entry.id == gameId) {
                    return entry.name
                }
            }
        }
        return null
    }

    /**
     * 注册动态游戏条目。
     */
    @JvmStatic
    fun register(entry: Entry): Boolean {
        synchronized(dynamicEntries) {
            for (entries in dynamicEntries.values) {
                for (existing in entries) {
                    if (existing.id == entry.id) {
                        return false
                    }
                }
            }

            var list = dynamicEntries[entry.categoryKey]
            if (list == null) {
                list = ArrayList()
                dynamicEntries[entry.categoryKey] = list
            }
            list.add(entry)
            return true
        }
    }

    /**
     * 注销动态游戏条目。
     */
    @JvmStatic
    fun unregister(gameId: String): Boolean {
        synchronized(dynamicEntries) {
            var removed = false
            for (list in dynamicEntries.values) {
                removed = removed or list.removeIf { it.id == gameId }
            }
            return removed
        }
    }

    /**
     * 清除所有动态注册的游戏条目。
     */
    @JvmStatic
    fun clearDynamicEntries() {
        synchronized(dynamicEntries) {
            dynamicEntries.clear()
        }
    }

    /**
     * 启动游戏（内置游戏）。
     */
    @JvmStatic
    fun launchGame(context: Context?, gameId: String?): Boolean {
        if (context == null || gameId == null) {
            Log.w(TAG, "launchGame called with null context or gameId")
            return false
        }
        val activityClass = getActivityClassById(context, gameId)
        if (activityClass != null) {
            return try {
                val intent = Intent(context, activityClass).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("gameId", gameId)
                }
                context.startActivity(intent)
                Log.i(TAG, "启动内置游戏: $gameId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "启动内置游戏失败: $gameId", e)
                false
            }
        }
        Log.w(TAG, "未找到游戏: $gameId")
        return false
    }

    /**
     * 注册内置游戏的版本信息。
     */
    @JvmStatic
    fun registerBuiltInVersion(gameId: String, versionCode: Int) {
        if (gameId.isEmpty()) return
        builtInVersionCache[gameId] = versionCode
        Log.d(TAG, "注册内置游戏版本: $gameId v$versionCode")
    }

    /**
     * 检查内置游戏是否有更新。
     */
    @JvmStatic
    @Nullable
    fun checkBuiltInGameUpdate(context: Context, gameId: String): ModuleInfo? {
        if (gameId.isEmpty()) return null

        val builtInVersion = builtInVersionCache[gameId] ?: BUILT_IN_VERSION_CODE

        val checker = ModuleVersionChecker(context)
        val builtInInfo = ModuleInfo().apply {
            moduleId = gameId
            versionCode = builtInVersion
            isBuiltIn = true
        }

        val updateInfo = checker.checkForUpdates(builtInInfo)
        if (updateInfo != null) {
            val shouldLoad = checker.shouldLoadExternal(builtInVersion, updateInfo)
            if (shouldLoad) {
                Log.i(TAG, "内置游戏有更新: $gameId (内置 v$builtInVersion -> 外置 v${updateInfo.versionCode})")
                return updateInfo
            }
        }
        return null
    }

    /**
     * 检查所有内置游戏的更新。
     */
    @JvmStatic
    fun checkAllBuiltInGameUpdates(context: Context): List<ModuleInfo> {
        val updates = ArrayList<ModuleInfo>()
        for (gameId in builtInVersionCache.keys) {
            try {
                val update = checkBuiltInGameUpdate(context, gameId)
                if (update != null) {
                    updates.add(update)
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查游戏更新失败: $gameId", e)
            }
        }
        Log.d(TAG, "内置游戏更新检查完成: ${updates.size} 个有更新")
        return updates
    }

    /**
     * 获取内置游戏的版本号。
     */
    @JvmStatic
    fun getBuiltInVersionCode(gameId: String): Int {
        return builtInVersionCache[gameId] ?: BUILT_IN_VERSION_CODE
    }

    /**
     * 获取所有游戏分类（合并静态 + 动态 + 插件）。
     */
    @JvmStatic
    fun getCategories(context: Context): List<Category> {
        val staticCategories = buildStaticCategories(context)
        val merged = LinkedHashMap<String, MutableList<Entry>>()
        val names = LinkedHashMap<String, String>()
        val allStaticGames = ArrayList<Entry>()

        for (category in staticCategories) {
            allStaticGames.addAll(category.games)
            merged[category.categoryKey] = ArrayList(category.games)
            names[category.categoryKey] = category.name
        }

        synchronized(dynamicEntries) {
            for ((key, value) in dynamicEntries) {
                var target = merged[key]
                if (target == null) {
                    target = ArrayList()
                    merged[key] = target
                    names[key] = categoryName(context, key)
                }
                for (dynamicEntry in value) {
                    var exists = false
                    for (staticEntry in allStaticGames) {
                        if (staticEntry.id == dynamicEntry.id) {
                            exists = true
                            break
                        }
                    }
                    if (!exists) {
                        target.add(dynamicEntry)
                    }
                }
            }
        }

        val result = ArrayList<Category>()
        for ((key, value) in names) {
            val games = merged[key]
            if (games != null && games.isNotEmpty()) {
                result.add(Category(value, games, key))
            }
        }
        return Collections.unmodifiableList(result)
    }

    /**
     * 扁平化分类列表为游戏条目列表。
     */
    @JvmStatic
    fun flatten(categories: List<Category>): List<Entry> {
        val games = ArrayList<Entry>()
        for (category in categories) {
            games.addAll(category.games)
        }
        return Collections.unmodifiableList(games)
    }

    private fun buildStaticCategories(context: Context): List<Category> {
        val categories = ArrayList<Category>()

        // 2026-08-29 模块热更改造：全部游戏已模块化为独立 APK，由
        // ModuleManager.registerInstalledGameModules 按出厂清单动态注册（DynamicGameActivity
        // + 模块加载器运行，支持经服务器单独热更）。静态注册表仅保留 breakout——
        // 唯一没有模块 APK 的宿主内置游戏。
        val classics = ArrayList<Entry>()
        categories.add(Category(categoryName(context, CATEGORY_CLASSICS), classics, CATEGORY_CLASSICS))

        val puzzle = ArrayList<Entry>().apply {
            add(Entry("breakout", R.drawable.ic_breakout, "打砖块", "经典打砖块游戏",
                com.gamecenter.app.games.breakout.BreakoutActivity::class.java,
                CATEGORY_PUZZLE, CATEGORY_PUZZLE))
        }
        categories.add(Category(categoryName(context, CATEGORY_PUZZLE), puzzle, CATEGORY_PUZZLE))

        val casual = ArrayList<Entry>()
        categories.add(Category(categoryName(context, CATEGORY_CASUAL), casual, CATEGORY_CASUAL))

        return categories
    }

    private fun categoryName(context: Context, key: String): String {
        return try {
            if (CATEGORY_PUZZLE == key) context.getString(R.string.category_puzzle)
            else if (CATEGORY_CASUAL == key) context.getString(R.string.category_casual)
            else context.getString(R.string.category_classics)
        } catch (e: android.content.res.Resources.NotFoundException) {
            if (CATEGORY_PUZZLE == key) "益智"
            else if (CATEGORY_CASUAL == key) "休闲"
            else "经典"
        }
    }

    /**
     * 游戏分类。
     */
    class Category internal constructor(
        @JvmField val name: String,
        games: List<Entry>,
        @JvmField val categoryKey: String
    ) {
        @JvmField val games: List<Entry> = Collections.unmodifiableList(games)
    }

    /**
     * 游戏条目。
     */
    class Entry {
        @JvmField val id: String
        @JvmField val iconRes: Int
        @JvmField val name: String
        @JvmField val desc: String
        @JvmField val activityClass: Class<*>?
        @JvmField val category: String
        @JvmField val categoryKey: String

        constructor(
            id: String, iconRes: Int, name: String, desc: String,
            activityClass: Class<*>?, category: String, categoryKey: String
        ) {
            this.id = id
            this.iconRes = iconRes
            this.name = name
            this.desc = desc
            this.activityClass = activityClass
            this.category = category
            this.categoryKey = categoryKey
        }

        constructor(
            id: String, name: String, desc: String,
            activityClass: Class<*>?, category: String, categoryKey: String
        ) : this(id, 0, name, desc, activityClass, category, categoryKey)

        @Nullable
        fun checkForUpdate(context: Context): ModuleInfo? {
            return checkBuiltInGameUpdate(context, this.id)
        }
    }
}

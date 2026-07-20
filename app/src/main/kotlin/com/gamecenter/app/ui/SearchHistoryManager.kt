package com.gamecenter.app.ui

import android.content.Context
import org.json.JSONArray

/**
 * Batch 8-1 (SEARCH_HISTORY_CHIPS): 搜索历史管理器
 *
 * 持久化用户在游戏大厅搜索框输入过的关键词，最多保留 [MAX_HISTORY] 条。
 * - 越新的关键词越靠前
 * - 同一关键词重复输入时上移到最前，不重复添加
 * - 超过上限时丢弃最旧的
 *
 * 持久化使用 SharedPreferences，key=`search_history`，value 为 JSON 数组字符串。
 */
class SearchHistoryManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取历史列表，最新的在最前。 */
    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 追加一条搜索词。重复时上移；超过上限丢弃最旧。 */
    fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val current = getHistory().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > MAX_HISTORY) {
            current.subList(MAX_HISTORY, current.size).clear()
        }
        persist(current)
    }

    /** 清空全部历史。 */
    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun persist(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "game_search_history"
        private const val KEY_HISTORY = "search_history"
        private const val MAX_HISTORY = 5

        @Volatile private var instance: SearchHistoryManager? = null

        @JvmStatic
        fun getInstance(context: Context): SearchHistoryManager =
            instance ?: synchronized(this) {
                instance ?: SearchHistoryManager(context).also { instance = it }
            }
    }
}

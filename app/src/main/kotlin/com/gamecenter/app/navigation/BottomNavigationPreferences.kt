package com.gamecenter.app.navigation

import android.content.Context
import org.json.JSONArray

/** 用户本机的底部导航排序与隐藏偏好。 */
class BottomNavigationPreferences(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun orderedItems(items: List<BottomNavigationCatalog.Item>): List<BottomNavigationCatalog.Item> {
        val defaults = items.sortedWith(
            compareBy<BottomNavigationCatalog.Item> { it.defaultOrder }.thenBy { it.id }
        )
        val byId = defaults.associateBy { it.id }
        val savedIds = readOrder()
        if (savedIds.isEmpty()) return defaults
        return buildList {
            savedIds.mapNotNullTo(this) { byId[it] }
            val known = savedIds.toSet()
            defaults.filterNotTo(this) { it.id in known }
        }.distinctBy { it.id }
    }

    fun visibleItems(items: List<BottomNavigationCatalog.Item>): List<BottomNavigationCatalog.Item> {
        val ordered = orderedItems(items)
        val hidden = hiddenIds()
        val requested = ordered.filter { it.requiredVisible || it.id !in hidden }
        val selected = enforceLimit(ordered, requested)
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        return ordered.filter { it.id in selectedIds }.take(BottomNavigationCatalog.MAX_VISIBLE_ITEMS)
    }

    fun hiddenIds(): Set<String> = preferences.getStringSet(KEY_HIDDEN, emptySet())?.toSet().orEmpty()

    /** 保存当前页面的完整状态，并保留尚未出现在本次目录中的历史隐藏记录。 */
    fun save(items: List<BottomNavigationCatalog.Item>, visibleIds: Set<String>) {
        val orderedIds = items.map { it.id }.distinct()
        val currentIds = orderedIds.toSet()
        val preservedHidden = hiddenIds().filterNotTo(mutableSetOf()) { it in currentIds }
        val requiredIds = items.filter { it.requiredVisible }.mapTo(mutableSetOf()) { it.id }
        val requested = items.filter { it.id in visibleIds || it.requiredVisible }
        val allowedVisible = enforceLimit(items, requested).mapTo(mutableSetOf()) { it.id }
        val hidden = preservedHidden + currentIds.filterNot { it in allowedVisible }
        preferences.edit()
            .putString(KEY_ORDER, JSONArray(orderedIds).toString())
            .putStringSet(KEY_HIDDEN, hidden)
            .apply()
    }

    fun reset() {
        preferences.edit().remove(KEY_ORDER).remove(KEY_HIDDEN).apply()
    }

    private fun readOrder(): List<String> {
        val raw = preferences.getString(KEY_ORDER, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun enforceLimit(
        ordered: List<BottomNavigationCatalog.Item>,
        requested: List<BottomNavigationCatalog.Item>
    ): List<BottomNavigationCatalog.Item> {
        val selected = requested.take(BottomNavigationCatalog.MAX_VISIBLE_ITEMS).toMutableList()
        // 必要入口即使被旧偏好或溢出挤掉，也必须保留。
        for (required in ordered.filter { it.requiredVisible && it !in selected }) {
            val replaceIndex = selected.indexOfLast { !it.requiredVisible }
            if (replaceIndex >= 0) selected[replaceIndex] = required else selected.add(required)
        }
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        return ordered.filter { it.id in selectedIds }.take(BottomNavigationCatalog.MAX_VISIBLE_ITEMS)
    }

    companion object {
        private const val PREFS_NAME = "bottom_navigation_preferences"
        private const val KEY_ORDER = "item_order"
        private const val KEY_HIDDEN = "hidden_items"
    }
}

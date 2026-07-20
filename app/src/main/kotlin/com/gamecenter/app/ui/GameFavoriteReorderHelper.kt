package com.gamecenter.app.ui

import android.content.Context
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore

/**
 * 收藏置顶排序辅助器（Batch 11-4 / GAME_FAVORITE_REORDER）。
 *
 * 当用户在设置中开启"收藏置顶"后，[sortEntries] 会把已收藏游戏排在前面，
 * 未收藏游戏保持原相对顺序（稳定排序）。
 *
 * 偏好存储在 {@code app_settings} SharedPreferences，key = {@link #KEY_FAVORITE_REORDER}。
 */
object GameFavoriteReorderHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_FAVORITE_REORDER = "favorite_reorder_enabled"

    /** 读取是否开启收藏置顶。 */
    fun isEnabled(context: Context): Boolean {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FAVORITE_REORDER, false)
    }

    /** 设置是否开启收藏置顶。 */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FAVORITE_REORDER, enabled)
            .apply()
    }

    /**
     * 对游戏列表进行排序：已收藏在前，未收藏在后。
     * 不改变同组内的相对顺序（稳定排序）。
     */
    fun sortEntries(
        context: Context,
        entries: List<GameRegistry.Entry>
    ): List<GameRegistry.Entry> {
        if (!isEnabled(context) || entries.isEmpty()) return entries
        val store = GameUsageStore(context)
        // 用两个列表分别收集，再合并，保证稳定
        val favorited = ArrayList<GameRegistry.Entry>()
        val others = ArrayList<GameRegistry.Entry>()
        for (entry in entries) {
            if (store.isFavorite(entry.id)) {
                favorited.add(entry)
            } else {
                others.add(entry)
            }
        }
        favorited.addAll(others)
        return favorited
    }
}

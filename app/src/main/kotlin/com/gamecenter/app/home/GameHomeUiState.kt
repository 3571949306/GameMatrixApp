package com.gamecenter.app.home

import com.gamecenter.app.games.GameRegistry

/** 由 Fragment 从资源构建、传入纯函数 builder 的文案包（保证状态逻辑可纯 JVM 测试）。 */
data class GameHomeStrings(
    val continueTitle: String,
    val recentTitle: String,
    val allGamesTitle: String,
    val emptyLibrary: String,
    val emptyLibraryAction: String,
    val emptyFavorites: String,
    val emptyFavoritesAction: String,
    val noSearchResults: String,
    val clearSearch: String,
    val viewAll: String,
    val collapse: String,
    val allFilter: String,
)

/** 页面筛选状态（SavedStateHandle 持久化）。 */
data class GameHomeFilters(
    val query: String = "",
    val categoryKey: String? = null,   // null = 全部
    val favoritesOnly: Boolean = false,
    val recentExpanded: Boolean = false,
)

/** 不可变页面状态。 */
data class GameHomeUiState(
    val items: List<GameHomeItem>,
    val filters: GameHomeFilters,
    val isEmptyLibrary: Boolean,
    val visibleGameCount: Int,
    val categories: List<CategoryUi> = emptyList(),
) {
    /** 分类筛选 chip（含“全部”）。 */
    data class CategoryUi(val key: String?, val name: String, val selected: Boolean)
}

/**
 * 纯函数状态构建器（计划 §6.5）：
 * 输入真源快照与筛选，输出确定性的行列表；不含 Android 依赖，可直接单测。
 *
 * 行为契约（§4.2）：
 * - 库空 → 单个空状态（浏览模块商店）；
 * - 无最近记录 → 不渲染继续/最近，直接全部游戏；
 * - 继续玩 = 最近第一条可恢复；最近行最多 3 条（展开 8 条），不含继续那条；
 * - 搜索中隐藏继续/最近；分类与收藏筛选继续生效；
 * - 搜索无结果 → “未找到相关游戏 + 清除搜索”；收藏空 → 专属空状态。
 */
object GameHomeStateBuilder {

    const val RECENT_COLLAPSED_COUNT = 3
    const val RECENT_EXPANDED_COUNT = 8

    fun build(
        allEntries: List<GameRegistry.Entry>,
        recentIds: List<String>,
        lastPlayedTextById: Map<String, String>,
        favoriteIds: Set<String>,
        categories: List<Pair<String, String>>,
        filters: GameHomeFilters,
        strings: GameHomeStrings,
        healthReminderText: String? = null,
    ): GameHomeUiState {
        val byId = allEntries.associateBy { it.id }
        if (allEntries.isEmpty()) {
            return GameHomeUiState(
                items = listOf(GameHomeItem.EmptyState(strings.emptyLibrary, strings.emptyLibraryAction)),
                filters = filters,
                isEmptyLibrary = true,
                visibleGameCount = 0,
                categories = listOf(
                    GameHomeUiState.CategoryUi(null, strings.allFilter, true)
                ) + categories.map { GameHomeUiState.CategoryUi(it.first, it.second, false) },
            )
        }

        val query = filters.query.trim()
        val searching = query.isNotEmpty()

        // 1. 先按分类与收藏过滤游戏库（搜索期间二者继续生效）
        val inCategory = allEntries.filter { e ->
            filters.categoryKey == null || e.categoryKey == filters.categoryKey
        }
        val favFiltered = inCategory.filter { e ->
            !filters.favoritesOnly || favoriteIds.contains(e.id)
        }

        // 2. 搜索（名称 + 描述，固定 Locale 避免大小写边界）
        val queryNeedle = query.lowercase(java.util.Locale.ROOT)
        val matched = favFiltered.filter { e ->
            queryNeedle.isEmpty() ||
                e.name.lowercase(java.util.Locale.ROOT).contains(queryNeedle) ||
                e.desc.lowercase(java.util.Locale.ROOT).contains(queryNeedle)
        }

        val items = mutableListOf<GameHomeItem>()

        // 3. 搜索中：隐藏继续/最近，只给结果（或搜索空状态）
        if (searching) {
            if (matched.isEmpty()) {
                items.add(GameHomeItem.EmptyState(strings.noSearchResults, strings.clearSearch))
            } else {
                items.add(GameHomeItem.SectionHeader(strings.allGamesTitle))
                items.addAll(matched.map { GameHomeItem.GameTile(it, favoriteIds.contains(it.id)) })
            }
            return GameHomeUiState(
                items, filters, isEmptyLibrary = false, visibleGameCount = matched.size,
                categories = listOf(
                    GameHomeUiState.CategoryUi(null, strings.allFilter, filters.categoryKey == null)
                ) + categories.map {
                    GameHomeUiState.CategoryUi(it.first, it.second, it.first == filters.categoryKey)
                },)
        }

        // 4. 非搜索态：健康提醒 → 继续 → 最近 → 全部游戏
        healthReminderText?.let { items.add(GameHomeItem.HealthReminder(it)) }

        val validRecent = recentIds.mapNotNull(byId::get).filter { e ->
            // 失效 ID 已被 byId 过滤；收藏筛选/搜索不影响“继续与最近”真源展示
            filters.categoryKey == null || e.categoryKey == filters.categoryKey
        }
        if (validRecent.isNotEmpty()) {
            val resume = validRecent.first()
            items.add(
                GameHomeItem.ContinueRow(
                    resume,
                    lastPlayedTextById[resume.id] ?: ""
                )
            )
            val rest = validRecent.drop(1)
            if (rest.isNotEmpty()) {
                val limit = if (filters.recentExpanded) RECENT_EXPANDED_COUNT else RECENT_COLLAPSED_COUNT
                val visibleRest = rest.take(limit)
                items.add(
                    GameHomeItem.SectionHeader(
                        strings.recentTitle,
                        expandable = true,
                        expanded = filters.recentExpanded
                    )
                )
                visibleRest.forEach { e ->
                    items.add(GameHomeItem.RecentRow(e, lastPlayedTextById[e.id] ?: ""))
                }
            }
        }

        // 5. 全部游戏（含收藏空状态）
        items.add(GameHomeItem.SectionHeader(strings.allGamesTitle))
        if (favFiltered.isEmpty() && filters.favoritesOnly) {
            items.add(GameHomeItem.EmptyState(strings.emptyFavorites, strings.emptyFavoritesAction))
        } else if (favFiltered.isEmpty()) {
            items.add(GameHomeItem.EmptyState(strings.emptyLibrary, strings.emptyLibraryAction))
        } else {
            items.addAll(favFiltered.map { GameHomeItem.GameTile(it, favoriteIds.contains(it.id)) })
        }

        return GameHomeUiState(
            items = items,
            filters = filters,
            isEmptyLibrary = false,
            visibleGameCount = matched.size,
                categories = listOf(
                    GameHomeUiState.CategoryUi(null, strings.allFilter, filters.categoryKey == null)
                ) + categories.map {
                    GameHomeUiState.CategoryUi(it.first, it.second, it.first == filters.categoryKey)
                },)
    }
}

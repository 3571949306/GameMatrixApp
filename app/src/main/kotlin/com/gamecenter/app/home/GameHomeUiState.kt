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
    /** 定位分区叙事（可选，空则不渲染）：经典游戏是入口，工具与学习是留存 */
    val positioningTitle: String = "",
    /** 继续行时间模板（可选，空则直接显示相对时间）：“上次游玩：%1$s” */
    val resumeTimeFormat: String = "",
)

/**
 * 留存卡片纯值包（批次 2a + 2b 增量）：数据获取与文案格式化都在 ViewModel/Repository 完成，
 * StateBuilder 只收纯值做行编排；成员为 null = 数据不可得，静默降级不渲染对应卡片。
 */
data class GameHomeRetentionCards(
    /** 今日时长提醒卡（批次 2b 起为分档卡片；NONE/已 dismiss 由 ViewModel 判定后置 null）。 */
    val playtimeReminder: GameHomeItem.HealthReminder? = null,
    val quickStats: GameHomeItem.QuickStatsRow? = null,
    val dailyChallenge: GameHomeItem.DailyChallengeCard? = null,
    val streakSummary: GameHomeItem.StreakSummaryCard? = null,
    /** 最近解锁成就横幅（批次 2b）：当日已 dismiss 或无数据时为 null。 */
    val recentAchievement: GameHomeItem.RecentAchievementBanner? = null,
    /** 今日精选推荐（批次 2b，HeroBanner 决策 b 方案）：无推荐结果时为 null。 */
    val todayFeatured: GameHomeItem.TodayFeaturedCard? = null,
)

/** 顶栏时段问候（批次 2a）：口径与 legacy GamesFragment.updateGreeting 一致。 */
object GameHomeGreeting {
    enum class Period { MORNING, AFTERNOON, EVENING }

    /** 5-11 早上好，12-17 下午好，其余（18-4）晚上好。 */
    fun periodFor(hourOfDay: Int): Period = when (hourOfDay) {
        in MORNING_START..MORNING_END -> Period.MORNING
        in AFTERNOON_START..AFTERNOON_END -> Period.AFTERNOON
        else -> Period.EVENING
    }

    private const val MORNING_START = 5
    private const val MORNING_END = 11
    private const val AFTERNOON_START = 12
    private const val AFTERNOON_END = 17
}

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
 * - 搜索无结果 → “未找到相关游戏 + 清除搜索”；收藏空 → 专属空状态；
 * - 留存卡片（批次 2a：快览统计/每日挑战/连胜概览；批次 2b：时长提醒分档卡/
 *   最近成就横幅/今日精选）只出现在非搜索态，顺序为继续之前；成员为 null 时静默降级不渲染。
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
        retention: GameHomeRetentionCards = GameHomeRetentionCards(),
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

        // 4. 非搜索态：时长提醒 → 快览统计 → 每日挑战 → 连胜概览 → 最近成就 → 今日精选
        //    → 继续 → 最近 → 全部游戏（批次 2b 顺序）
        retention.playtimeReminder?.let { items.add(it) }
        retention.quickStats?.let { items.add(it) }
        retention.dailyChallenge?.let { items.add(it) }
        retention.streakSummary?.let { items.add(it) }
        retention.recentAchievement?.let { items.add(it) }
        retention.todayFeatured?.let { items.add(it) }
        addContinueAndRecent(items, byId, recentIds, filters, strings, lastPlayedTextById)

        // 5. 全部游戏（含收藏空状态）；前置定位分区叙事（可选）
        if (strings.positioningTitle.isNotBlank()) {
            items.add(GameHomeItem.SectionHeader(strings.positioningTitle))
        }
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

    /**
     * 继续与最近装配（批次 2a 自 build 抽出）：继续玩 = 最近第一条可恢复；
     * 最近行最多 3 条（展开 8 条），不含继续那条。
     */
    private fun addContinueAndRecent(
        items: MutableList<GameHomeItem>,
        byId: Map<String, GameRegistry.Entry>,
        recentIds: List<String>,
        filters: GameHomeFilters,
        strings: GameHomeStrings,
        lastPlayedTextById: Map<String, String>,
    ) {
        val validRecent = recentIds.mapNotNull(byId::get).filter { e ->
            // 失效 ID 已被 byId 过滤；收藏筛选/搜索不影响“继续与最近”真源展示
            filters.categoryKey == null || e.categoryKey == filters.categoryKey
        }
        if (validRecent.isEmpty()) return
        val resume = validRecent.first()
        items.add(
            GameHomeItem.ContinueRow(
                resume,
                continueMetaText(strings.resumeTimeFormat, lastPlayedTextById[resume.id] ?: "")
            )
        )
        val rest = validRecent.drop(1)
        if (rest.isEmpty()) return
        val limit = if (filters.recentExpanded) RECENT_EXPANDED_COUNT else RECENT_COLLAPSED_COUNT
        items.add(
            GameHomeItem.SectionHeader(
                strings.recentTitle,
                expandable = true,
                expanded = filters.recentExpanded,
                collapseText = strings.collapse
            )
        )
        rest.take(limit).forEach { e ->
            items.add(GameHomeItem.RecentRow(e, lastPlayedTextById[e.id] ?: ""))
        }
    }

    /**
     * 继续行时间文案（批次 2a）：有模板（“上次游玩：%1$s”）且相对时间非空时套模板，
     * 否则维持原样（相对时间直接展示），保证旧调用方零破坏。
     */
    private fun continueMetaText(format: String, relativeSpan: String): String =
        if (format.isNotBlank() && relativeSpan.isNotBlank()) format.format(relativeSpan) else relativeSpan
}

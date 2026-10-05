package com.gamecenter.app.home

import com.gamecenter.app.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 状态构建纯函数测试（计划 §9.1 GameHomeStateTest）。不依赖 Android 与随机数。 */
class GameHomeStateTest {

    private val strings = GameHomeStrings(
        continueTitle = "继续玩",
        recentTitle = "最近玩过",
        allGamesTitle = "全部游戏",
        emptyLibrary = "还没有可用游戏",
        emptyLibraryAction = "浏览模块商店",
        emptyFavorites = "还没有收藏",
        emptyFavoritesAction = "查看全部游戏",
        noSearchResults = "未找到相关游戏",
        clearSearch = "清除搜索",
        viewAll = "查看全部",
        collapse = "收起",
        allFilter = "全部",
    )

    private fun entry(
        id: String,
        name: String = id,
        categoryKey: String = GameRegistry.CATEGORY_CASUAL,
        desc: String = "$id 的描述",
    ): GameRegistry.Entry = GameRegistry.Entry(
        id, 0, name, desc, null, "分类", categoryKey
    )

    private fun game(id: String) = GameHomeItem.GameTile(entry(id), isFavorite = false)

    @Test
    fun `首次安装空库_只显示浏览模块商店空状态`() {
        val state = GameHomeStateBuilder.build(
            allEntries = emptyList(), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
        )
        assertTrue(state.isEmptyLibrary)
        assertEquals(1, state.items.size)
        val empty = state.items[0] as GameHomeItem.EmptyState
        assertEquals("还没有可用游戏", empty.message)
        assertEquals("浏览模块商店", empty.action)
    }

    @Test
    fun `有最近记录_继续加最多3条且继续条目不重复出现在最近`() {
        val entries = listOf(entry("a"), entry("b"), entry("c"), entry("d"), entry("e"))
        val state = GameHomeStateBuilder.build(
            allEntries = entries,
            recentIds = listOf("a", "b", "c", "d", "e"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
        )
        val kinds = state.items.map { it::class.simpleName }
        assertEquals("HealthReminder 不存在时首行为继续", "ContinueRow", kinds.first())
        assertEquals(1, state.items.count { it is GameHomeItem.ContinueRow })
        val recentRows = state.items.filterIsInstance<GameHomeItem.RecentRow>()
        assertEquals(3, recentRows.size)
        // 继续条目（最近第一条）不重复出现在最近行
        assertTrue(recentRows.none { it.entry.id == "a" })
        // 展开箭头存在（还有更多）
        val header = state.items.filterIsInstance<GameHomeItem.SectionHeader>().first()
        assertTrue(header.expandable)
    }

    @Test
    fun `失效最近ID被过滤_不进入继续与最近`() {
        val entries = listOf(entry("live1"), entry("live2"))
        val state = GameHomeStateBuilder.build(
            allEntries = entries,
            recentIds = listOf("dead", "live1", "live2"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
        )
        // 失效 ID 不出现；最近第一条按契约成为继续行，live2 进最近行
        val continueId = state.items.filterIsInstance<GameHomeItem.ContinueRow>()
            .single().entry.id
        assertEquals("live1", continueId)
        val recentIdsInUi = state.items.filterIsInstance<GameHomeItem.RecentRow>().map { it.entry.id }
        assertEquals(listOf("live2"), recentIdsInUi)
        assertFalse(recentIdsInUi.contains("dead"))
        assertTrue(state.items.none { (it as? GameHomeItem.RecentRow)?.entry?.id == "dead" })
    }

    @Test
    fun `最近展开至8条上限`() {
        val ids = (1..10).map { "g$it" }
        val entries = ids.map { entry(it) }
        val state = GameHomeStateBuilder.build(
            allEntries = entries,
            recentIds = ids,
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(recentExpanded = true), strings = strings,
        )
        // 继续 1 条 + 最近最多 8 条（RecentGamesManager.MAX_RECENT=8 上游已裁）
        assertEquals(8, state.items.count { it is GameHomeItem.RecentRow })
    }

    @Test
    fun `搜索命中名称与描述_并隐藏继续最近`() {
        val entries = listOf(entry("g1", name = "打砖块"), entry("g2", name = "围棋", desc = "经典策略"))
        val state = GameHomeStateBuilder.build(
            allEntries = entries, recentIds = listOf("g1", "g2"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(query = "砖"), strings = strings,
        )
        assertTrue(state.items.none { it is GameHomeItem.ContinueRow || it is GameHomeItem.RecentRow })
        val tiles = state.items.filterIsInstance<GameHomeItem.GameTile>()
        assertEquals(listOf("g1"), tiles.map { it.entry.id })
    }

    @Test
    fun `搜索无结果_显示清除搜索空状态`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("g1")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(query = "不存在"), strings = strings,
        )
        val empty = state.items.filterIsInstance<GameHomeItem.EmptyState>().single()
        assertEquals("未找到相关游戏", empty.message)
        assertEquals("清除搜索", empty.action)
    }

    @Test
    fun `分类筛选只保留对应categoryKey`() {
        val entries = listOf(
            entry("c1", categoryKey = GameRegistry.CATEGORY_CLASSICS),
            entry("p1", categoryKey = GameRegistry.CATEGORY_PUZZLE),
        )
        val state = GameHomeStateBuilder.build(
            allEntries = entries, recentIds = listOf("p1"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(categoryKey = GameRegistry.CATEGORY_CLASSICS), strings = strings,
        )
        val tiles = state.items.filterIsInstance<GameHomeItem.GameTile>()
        assertEquals(listOf("c1"), tiles.map { it.entry.id })
        // 分类筛选同样作用于最近行（契约：筛选继续生效）
        assertTrue(state.items.none { it is GameHomeItem.RecentRow && it.entry.id == "p1" })
    }

    @Test
    fun `收藏筛选空状态_含查看全部操作`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("g1")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(favoritesOnly = true), strings = strings,
        )
        val empty = state.items.filterIsInstance<GameHomeItem.EmptyState>().single()
        assertEquals("还没有收藏", empty.message)
        assertEquals("查看全部游戏", empty.action)
    }

    @Test
    fun `收藏筛选_只显示收藏项`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("g1"), entry("g2")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = setOf("g2"),
            categories = emptyList(),
            filters = GameHomeFilters(favoritesOnly = true), strings = strings,
        )
        val tiles = state.items.filterIsInstance<GameHomeItem.GameTile>()
        assertEquals(listOf("g2"), tiles.map { it.entry.id })
        assertTrue(tiles.single().isFavorite)
    }

    @Test
    fun `组合筛选_搜索加分类加收藏`() {
        val entries = listOf(
            entry("hit", name = "俄罗斯方块", categoryKey = GameRegistry.CATEGORY_PUZZLE),
            entry("miss1", name = "俄罗斯方块2", categoryKey = GameRegistry.CATEGORY_CASUAL),
            entry("miss2", name = "俄罗斯方块3", categoryKey = GameRegistry.CATEGORY_PUZZLE),
        )
        val state = GameHomeStateBuilder.build(
            allEntries = entries, recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = setOf("hit"),
            categories = emptyList(),
            filters = GameHomeFilters(
                query = "方块", categoryKey = GameRegistry.CATEGORY_PUZZLE, favoritesOnly = true
            ), strings = strings,
        )
        val tiles = state.items.filterIsInstance<GameHomeItem.GameTile>()
        assertEquals(listOf("hit"), tiles.map { it.entry.id })
    }

    @Test
    fun `健康提醒出现在继续之前`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = listOf("a"),
            lastPlayedTextById = mapOf("a" to "5 分钟前"), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(
                playtimeReminder = GameHomeItem.HealthReminder("已连续游玩 60 分钟，注意休息"),
            ),
        )
        val first = state.items.first()
        assertTrue(first is GameHomeItem.HealthReminder)
        assertTrue(state.items[1] is GameHomeItem.ContinueRow)
    }

    // ===== 批次 2a：留存卡片 =====

    private fun quickStats() = GameHomeItem.QuickStatsRow("12 分钟", "连3天", "5")

    private fun dailyChallenge() = GameHomeItem.DailyChallengeCard(
        entry = entry("a"), title = "每日挑战", gameName = "a",
        progressText = "进度：1 / 3", progressPercent = 33,
    )

    private fun streakSummary() = GameHomeItem.StreakSummaryCard("3 天", "7 天")

    // ===== 批次 2b：最近成就横幅 / 今日精选 =====

    private fun recentAchievement() = GameHomeItem.RecentAchievementBanner("连胜大师", "连续胜利 10 次")

    private fun todayFeatured() = GameHomeItem.TodayFeaturedCard(entry("f"), "f 的描述")

    @Test
    fun `留存卡片按顺序渲染在健康提醒之后继续之前`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = listOf("a"),
            lastPlayedTextById = mapOf("a" to "5 分钟前"), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(
                playtimeReminder = GameHomeItem.HealthReminder("今日已游玩 73 分钟"),
                quickStats = quickStats(),
                dailyChallenge = dailyChallenge(),
                streakSummary = streakSummary(),
                recentAchievement = recentAchievement(),
                todayFeatured = todayFeatured(),
            ),
        )
        val kinds = state.items.map { it::class.simpleName }
        assertEquals(
            listOf(
                "HealthReminder", "QuickStatsRow", "DailyChallengeCard", "StreakSummaryCard",
                "RecentAchievementBanner", "TodayFeaturedCard",
                "ContinueRow", "SectionHeader", "GameTile"
            ),
            kinds
        )
    }

    @Test
    fun `留存卡片缺省时静默降级_不渲染占位`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = listOf("a"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
        )
        assertTrue(state.items.none {
            it is GameHomeItem.QuickStatsRow ||
                it is GameHomeItem.DailyChallengeCard ||
                it is GameHomeItem.StreakSummaryCard ||
                it is GameHomeItem.RecentAchievementBanner ||
                it is GameHomeItem.TodayFeaturedCard
        })
    }

    @Test
    fun `部分卡片缺数据_只渲染可用的卡片`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(
                dailyChallenge = dailyChallenge() // 快览/连胜不可得 → 静默降级
            ),
        )
        assertEquals(1, state.items.count { it is GameHomeItem.DailyChallengeCard })
        assertTrue(state.items.none { it is GameHomeItem.QuickStatsRow })
        assertTrue(state.items.none { it is GameHomeItem.StreakSummaryCard })
        // 无最近记录：卡片之后直接进全部游戏
        assertTrue(state.items.none { it is GameHomeItem.ContinueRow })
    }

    @Test
    fun `搜索时隐藏留存卡片`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(query = "a"), strings = strings,
            retention = GameHomeRetentionCards(
                quickStats = quickStats(),
                dailyChallenge = dailyChallenge(),
                streakSummary = streakSummary(),
                recentAchievement = recentAchievement(),
                todayFeatured = todayFeatured(),
            ),
        )
        assertTrue(state.items.none {
            it is GameHomeItem.QuickStatsRow ||
                it is GameHomeItem.DailyChallengeCard ||
                it is GameHomeItem.StreakSummaryCard ||
                it is GameHomeItem.RecentAchievementBanner ||
                it is GameHomeItem.TodayFeaturedCard
        })
    }

    @Test
    fun `空库时不渲染留存卡片`() {
        val state = GameHomeStateBuilder.build(
            allEntries = emptyList(), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(
                quickStats = quickStats(),
                dailyChallenge = dailyChallenge(),
                streakSummary = streakSummary(),
            ),
        )
        assertEquals(1, state.items.size)
        assertTrue(state.items[0] is GameHomeItem.EmptyState)
    }

    @Test
    fun `定位叙事标题渲染在全部游戏之前`() {
        val localized = strings.copy(positioningTitle = "经典游戏是入口，工具与学习是留存")
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = localized,
        )
        val headers = state.items.filterIsInstance<GameHomeItem.SectionHeader>()
        assertEquals("经典游戏是入口，工具与学习是留存", headers[0].title)
        assertEquals("全部游戏", headers[1].title)
    }

    @Test
    fun `定位叙事为空时不渲染`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
        )
        assertTrue(state.items.none {
            it is GameHomeItem.SectionHeader && it.title == "经典游戏是入口，工具与学习是留存"
        })
    }

    @Test
    fun `继续行时间文案套用上次游玩模板`() {
        val localized = strings.copy(resumeTimeFormat = "上次游玩：%1\$s")
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = listOf("a"),
            lastPlayedTextById = mapOf("a" to "5 分钟前"), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = localized,
        )
        val continueRow = state.items.filterIsInstance<GameHomeItem.ContinueRow>().single()
        assertEquals("上次游玩：5 分钟前", continueRow.lastPlayedText)
    }

    @Test
    fun `继续行模板存在但相对时间为空时不套模板`() {
        val localized = strings.copy(resumeTimeFormat = "上次游玩：%1\$s")
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = listOf("a"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = localized,
        )
        val continueRow = state.items.filterIsInstance<GameHomeItem.ContinueRow>().single()
        assertEquals("", continueRow.lastPlayedText)
    }

    @Test
    fun `最近行时间文案不套用继续模板`() {
        val localized = strings.copy(resumeTimeFormat = "上次游玩：%1\$s")
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a"), entry("b")), recentIds = listOf("a", "b"),
            lastPlayedTextById = mapOf("b" to "3 小时前"), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = localized,
        )
        val recentRow = state.items.filterIsInstance<GameHomeItem.RecentRow>().single()
        assertEquals("3 小时前", recentRow.lastPlayedText)
    }

    // ===== 批次 2b：最近成就横幅 / 今日精选 / 收起文案 =====

    @Test
    fun `最近成就横幅与今日精选缺数据时静默降级_其余卡片照常渲染`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(
                quickStats = quickStats(),
                dailyChallenge = dailyChallenge(),
                streakSummary = streakSummary(),
            ),
        )
        assertTrue(state.items.none { it is GameHomeItem.RecentAchievementBanner })
        assertTrue(state.items.none { it is GameHomeItem.TodayFeaturedCard })
        assertEquals(1, state.items.count { it is GameHomeItem.QuickStatsRow })
    }

    @Test
    fun `最近成就横幅当日dismiss后置空则不渲染`() {
        // dismiss 由 Repository 读取 RecentAchievementHelper 的 SP 日期后置 null，
        // StateBuilder 只保证 null 时不出现横幅
        val dismissed = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(recentAchievement = null),
        )
        assertTrue(dismissed.items.none { it is GameHomeItem.RecentAchievementBanner })
        val shown = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(recentAchievement = recentAchievement()),
        )
        assertEquals("连胜大师", shown.items.filterIsInstance<GameHomeItem.RecentAchievementBanner>()
            .single().name)
    }

    @Test
    fun `今日精选点击直达的数据随行透传`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(todayFeatured = todayFeatured()),
        )
        val featured = state.items.filterIsInstance<GameHomeItem.TodayFeaturedCard>().single()
        assertEquals("f", featured.entry.id)
        assertEquals("f 的描述", featured.desc)
    }

    @Test
    fun `时长提醒SEVERE档纯值随行透传`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
            retention = GameHomeRetentionCards(
                playtimeReminder = GameHomeItem.HealthReminder(
                    text = "今日已游玩 130 分钟", desc = "长时间游戏可能影响视力", severe = true
                ),
            ),
        )
        val reminder = state.items.filterIsInstance<GameHomeItem.HealthReminder>().single()
        assertTrue(reminder.severe)
        assertEquals("今日已游玩 130 分钟", reminder.text)
        assertEquals("长时间游戏可能影响视力", reminder.desc)
    }

    @Test
    fun `可展开标题携带收起文案`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a"), entry("b")), recentIds = listOf("a", "b"),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(recentExpanded = true), strings = strings,
        )
        val header = state.items.filterIsInstance<GameHomeItem.SectionHeader>()
            .first { it.expandable }
        assertTrue(header.expanded)
        assertEquals("收起", header.collapseText)
    }

    @Test
    fun `不可展开标题不带收起文案`() {
        val state = GameHomeStateBuilder.build(
            allEntries = listOf(entry("a")), recentIds = emptyList(),
            lastPlayedTextById = emptyMap(), favoriteIds = emptySet(),
            categories = emptyList(),
            filters = GameHomeFilters(), strings = strings,
        )
        val headers = state.items.filterIsInstance<GameHomeItem.SectionHeader>()
        assertTrue(headers.none { it.expandable })
        assertTrue(headers.all { it.collapseText.isEmpty() })
    }
}

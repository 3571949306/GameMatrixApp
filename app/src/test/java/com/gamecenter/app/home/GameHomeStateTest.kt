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
            filters = GameHomeFilters(), strings = strings,
            healthReminderText = "已连续游玩 60 分钟，注意休息",
        )
        val first = state.items.first()
        assertTrue(first is GameHomeItem.HealthReminder)
        assertTrue(state.items[1] is GameHomeItem.ContinueRow)
    }
}

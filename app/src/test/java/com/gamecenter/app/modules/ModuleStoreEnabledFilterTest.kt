package com.gamecenter.app.modules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批次 3 回归：清单级 enabled=false 的模块不得进入商店网格/搜索/hero 直达位。
 *
 * applyCategoryFilter 的最终可见列表与 hero banner 候选均经
 * [filterHiddenStoreModules] 过滤；本测试直接验证该纯函数的契约。
 */
class ModuleStoreEnabledFilterTest {

    @Test
    fun `disabled modules are dropped from the visible list`() {
        val visible = manifest("visible_game", enabled = true)
        val hidden = manifest("hidden_game", enabled = false)

        val result = filterHiddenStoreModules(listOf(visible, hidden))

        assertEquals(listOf(visible), result)
    }

    @Test
    fun `modules default to visible and keep their order`() {
        val first = manifest("a_game", enabled = true)
        val second = manifest("b_tool", enabled = true)
        val hidden = manifest("c_hidden", enabled = false)
        val third = manifest("d_game", enabled = true)

        val result = filterHiddenStoreModules(listOf(first, second, hidden, third))

        assertEquals(listOf(first, second, third), result)
    }

    @Test
    fun `all hidden input yields empty visible list`() {
        val hidden = manifest("hidden_game", enabled = false)

        assertEquals(emptyList<ModuleManifest>(), filterHiddenStoreModules(listOf(hidden)))
        assertEquals(emptyList<ModuleManifest>(), filterHiddenStoreModules(emptyList()))
    }

    private fun manifest(id: String, enabled: Boolean) = ModuleManifest(
        id = id,
        name = "Module $id",
        versionCode = 100,
        type = "game",
        enabled = enabled
    )
}

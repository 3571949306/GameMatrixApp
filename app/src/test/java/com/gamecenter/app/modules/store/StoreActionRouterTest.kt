package com.gamecenter.app.modules.store

import com.gamecenter.app.modules.ModuleManifest
import org.junit.Assert.*
import org.junit.Test

/**
 * P2.8: 商店动作路由白名单测试。
 *
 * 覆盖：
 * - 6 个白名单动作派发
 * - 未知动作拒绝
 * - 必需参数缺失拒绝
 * - 参数值黑名单字符拒绝（Intent / 类名 / Shell / JavaScript）
 * - 安全参数值通过
 */
class StoreActionRouterTest {

    /**
     * 测试用 Host：记录所有派发的动作，便于断言。
     */
    private class FakeRendererHost : StoreRendererHost {
        override val hostContext: android.content.Context
            get() = throw UnsupportedOperationException("Test context not available")

        override fun currentModules(): List<ModuleManifest> = emptyList()
        override fun installedModuleIds(): Set<String> = emptySet()

        val dispatchedActions = mutableListOf<Pair<String, Map<String, String>>>()

        override fun dispatchAction(action: String, params: Map<String, String>) {
            dispatchedActions.add(action to params)
        }

        override fun triggerRefresh() {}
        override fun switchCategory(categoryId: String) {}
    }

    @Test
    fun `whitelist contains exactly 6 actions`() {
        assertEquals(6, StoreActionRouter.ALLOWED_ACTIONS.size)
        assertTrue(StoreActionRouter.ALLOWED_ACTIONS.contains("open_module"))
        assertTrue(StoreActionRouter.ALLOWED_ACTIONS.contains("open_module_detail"))
        assertTrue(StoreActionRouter.ALLOWED_ACTIONS.contains("open_installed_modules"))
        assertTrue(StoreActionRouter.ALLOWED_ACTIONS.contains("refresh_catalog"))
        assertTrue(StoreActionRouter.ALLOWED_ACTIONS.contains("switch_category"))
        assertTrue(StoreActionRouter.ALLOWED_ACTIONS.contains("open_update_list"))
    }

    @Test
    fun `open_module dispatches with valid moduleId`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "wrongbook"),
            host
        )
        assertTrue(result)
        assertEquals(1, host.dispatchedActions.size)
        assertEquals("open_module", host.dispatchedActions[0].first)
        assertEquals("wrongbook", host.dispatchedActions[0].second["moduleId"])
    }

    @Test
    fun `open_module_detail dispatches with valid moduleId`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module_detail",
            mapOf("moduleId" to "browser"),
            host
        )
        assertTrue(result)
        assertEquals(1, host.dispatchedActions.size)
    }

    @Test
    fun `switch_category dispatches with valid categoryId`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "switch_category",
            mapOf("categoryId" to "game"),
            host
        )
        assertTrue(result)
        assertEquals("game", host.dispatchedActions[0].second["categoryId"])
    }

    @Test
    fun `open_installed_modules dispatches without params`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_installed_modules",
            emptyMap(),
            host
        )
        assertTrue(result)
    }

    @Test
    fun `refresh_catalog dispatches without params`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "refresh_catalog",
            emptyMap(),
            host
        )
        assertTrue(result)
    }

    @Test
    fun `open_update_list dispatches without params`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_update_list",
            emptyMap(),
            host
        )
        assertTrue(result)
    }

    // ====== 未知动作拒绝 ======

    @Test
    fun `unknown action is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "start_activity",
            mapOf("class" to "com.evil.Hack"),
            host
        )
        assertFalse(result)
        assertEquals(0, host.dispatchedActions.size)
    }

    @Test
    fun `exec action is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch("exec", emptyMap(), host)
        assertFalse(result)
    }

    @Test
    fun `empty action is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch("", emptyMap(), host)
        assertFalse(result)
    }

    // ====== 必需参数缺失拒绝 ======

    @Test
    fun `open_module without moduleId is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch("open_module", emptyMap(), host)
        assertFalse(result)
    }

    @Test
    fun `open_module with empty moduleId is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch("open_module", mapOf("moduleId" to ""), host)
        assertFalse(result)
    }

    @Test
    fun `switch_category without categoryId is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch("switch_category", emptyMap(), host)
        assertFalse(result)
    }

    // ====== 参数值黑名单校验 ======

    @Test
    fun `moduleId containing Intent keyword is rejected`() {
        val host = FakeRendererHost()
        // \b 词边界要求 Intent 前后是非单词字符，所以用 "Intent;" 触发
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "Intent;"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing Activity keyword is rejected`() {
        val host = FakeRendererHost()
        // "com.evil.Activity" 末尾的 Activity 后面是字符串结尾，触发 \b
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "com.evil.Activity"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing Runtime keyword is rejected`() {
        val host = FakeRendererHost()
        // "Runtime;" 末尾分号触发词边界
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "Runtime;exec"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing exec keyword is rejected`() {
        val host = FakeRendererHost()
        // "exec " 末尾空格触发词边界
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "exec cmd"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing shell keyword is rejected`() {
        val host = FakeRendererHost()
        // "shell|" 中 | 触发词边界
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "shell|cmd"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing javascript keyword is rejected`() {
        val host = FakeRendererHost()
        // "javascript:" 中 : 触发词边界
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "javascript:alert(1)"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing semicolon is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "wrongbook;rm -rf /"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing backtick is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "wrongbook`whoami`"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing dollar sign is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "\${HOME}"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing pipe is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "a|b"),
            host
        )
        assertFalse(result)
    }

    @Test
    fun `moduleId containing angle bracket is rejected`() {
        val host = FakeRendererHost()
        val result = StoreActionRouter.dispatch(
            "open_module",
            mapOf("moduleId" to "<script>"),
            host
        )
        assertFalse(result)
    }

    // ====== 安全参数值通过 ======

    @Test
    fun `safe moduleIds pass through`() {
        val host = FakeRendererHost()
        val safeIds = listOf("wrongbook", "browser", "games_hall", "gomoku", "tts_voice", "vpn", "ai", "tools")
        for (id in safeIds) {
            assertTrue("Safe moduleId 应通过: $id", StoreActionRouter.isParamValueSafe(id))
            val result = StoreActionRouter.dispatch("open_module", mapOf("moduleId" to id), host)
            assertTrue("Safe moduleId 派发应成功: $id", result)
        }
        assertEquals(safeIds.size, host.dispatchedActions.size)
    }

    @Test
    fun `safe categoryIds pass through`() {
        val host = FakeRendererHost()
        val safeIds = listOf("game", "browser", "tools", "ai", "vpn", "installed", "wrongbook")
        for (id in safeIds) {
            assertTrue(StoreActionRouter.isParamValueSafe(id))
            val result = StoreActionRouter.dispatch("switch_category", mapOf("categoryId" to id), host)
            assertTrue(result)
        }
    }

    // ====== 辅助方法测试 ======

    @Test
    fun `isAllowed returns true for whitelist actions`() {
        for (action in StoreActionRouter.ALLOWED_ACTIONS) {
            assertTrue(StoreActionRouter.isAllowed(action))
        }
    }

    @Test
    fun `isAllowed returns false for unknown action`() {
        assertFalse(StoreActionRouter.isAllowed("unknown"))
        assertFalse(StoreActionRouter.isAllowed(""))
    }

    @Test
    fun `hasRequiredParams returns true when all required present`() {
        assertTrue(StoreActionRouter.hasRequiredParams("open_module", mapOf("moduleId" to "x")))
        assertTrue(StoreActionRouter.hasRequiredParams("switch_category", mapOf("categoryId" to "x")))
    }

    @Test
    fun `hasRequiredParams returns false when required missing`() {
        assertFalse(StoreActionRouter.hasRequiredParams("open_module", emptyMap()))
        assertFalse(StoreActionRouter.hasRequiredParams("switch_category", emptyMap()))
    }

    @Test
    fun `hasRequiredParams returns true for actions without required params`() {
        assertTrue(StoreActionRouter.hasRequiredParams("open_installed_modules", emptyMap()))
        assertTrue(StoreActionRouter.hasRequiredParams("refresh_catalog", emptyMap()))
        assertTrue(StoreActionRouter.hasRequiredParams("open_update_list", emptyMap()))
    }

    @Test
    fun `hasRequiredParams returns false for unknown action`() {
        assertFalse(StoreActionRouter.hasRequiredParams("unknown", mapOf("x" to "y")))
    }
}

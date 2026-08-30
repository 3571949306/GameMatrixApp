package com.gamecenter.app.modules.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分发架构 v2：测速记录修剪策略与平均选优的单元测试。 */
class SourceTestStoreTest {

    private fun session(ts: Long, net: String, winner: String, edges: List<Triple<String, Long, Boolean>>) =
        SourceTestSession(ts, net, winner, edges.map { EdgeTestResult(it.first, it.second, it.third) })

    private val hosts = listOf("jp", "hk", "us")

    @Test
    fun `修剪保留最新5条且硬保2条移动记录`() {
        val sessions = (1..7).map { i ->
            session(i * 1000L, if (i >= 6) "mobile" else "wifi", "jp", listOf(Triple("jp", 100L, true)))
        }
        val kept = SourceTestStore.prune(sessions)
        assertEquals(5, kept.size)
        // 最新的 2 条移动记录（i=7,6）必须保留
        assertTrue(kept.any { it.timestampMs == 7000L && it.network == "mobile" })
        assertTrue(kept.any { it.timestampMs == 6000L && it.network == "mobile" })
        assertFalse(kept.any { it.timestampMs == 5000L && it.network == "mobile" })
        // 其余为最新的 wifi 记录
        assertTrue(kept.any { it.timestampMs == 5000L })
    }

    @Test
    fun `平均选优_总耗时最小的边缘胜出`() {
        val sessions = listOf(
            session(1L, "mobile", "hk", listOf(
                Triple("jp", 2000L, true), Triple("hk", 1000L, true), Triple("us", 3000L, true))),
            session(2L, "mobile", "hk", listOf(
                Triple("jp", 2400L, true), Triple("hk", 1200L, true), Triple("us", 2800L, true)))
        )
        // jp 平均 2200 / hk 平均 1100 / us 平均 2900 → hk
        assertEquals("hk", SourceTestStore.bestHostFromSessions(sessions, hosts))
    }

    @Test
    fun `失败样本不计入平均`() {
        val sessions = listOf(
            session(1L, "mobile", "us", listOf(
                Triple("jp", -1L, false), Triple("us", 1500L, true)))
        )
        assertEquals("us", SourceTestStore.bestHostFromSessions(sessions, hosts))
    }

    @Test
    fun `主机不在白名单则忽略`() {
        val sessions = listOf(
            session(1L, "mobile", "xx", listOf(Triple("xx", 100L, true), Triple("jp", 900L, true)))
        )
        assertEquals("jp", SourceTestStore.bestHostFromSessions(sessions, hosts))
    }

    @Test
    fun `无有效数据返回空`() {
        assertEquals(null, SourceTestStore.bestHostFromSessions(emptyList(), hosts))
        val allFailed = listOf(session(1L, "mobile", "", listOf(
            Triple("jp", -1L, false), Triple("hk", -1L, false))))
        assertEquals(null, SourceTestStore.bestHostFromSessions(allFailed, hosts))
    }
}

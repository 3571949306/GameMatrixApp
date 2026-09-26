package com.gamecenter.app.modules.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 分发架构 v2：测速记录修剪策略与平均选优的单元测试。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceTestStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        clearPersistedFiles()
        SourceTestStore.beforeTargetReplaceHook = null
    }

    @After
    fun tearDown() {
        SourceTestStore.beforeTargetReplaceHook = null
        clearPersistedFiles()
    }

    private fun clearPersistedFiles() {
        SourceTestStore.file(context).delete()
        SourceTestStore.tempFile(context).delete()
    }

    private fun writePersistedSessions(sessions: List<SourceTestSession>) {
        SourceTestStore.file(context).writeText(
            JSONObject().put("sessions", JSONArray().apply {
                sessions.forEach { put(it.toJson()) }
            }).toString()
        )
    }

    private fun session(ts: Long, net: String, winner: String, edges: List<Triple<String, Long, Boolean>>) =
        SourceTestSession(ts, net, winner, edges.map { EdgeTestResult(it.first, it.second, it.third) })

    private val hosts = listOf("jp", "hk", "us")

    @Test
    fun `append interrupted after temp write leaves old target readable`() {
        val oldSessions = listOf(
            session(1_000L, "wifi", "jp", listOf(Triple("jp", 100L, true))),
            session(2_000L, "mobile", "hk", listOf(Triple("hk", 200L, true)))
        )
        val appended = session(3_000L, "wifi", "us", listOf(Triple("us", 300L, true)))
        writePersistedSessions(oldSessions)
        val target = SourceTestStore.file(context)
        val oldTargetText = target.readText()
        var hookSawWrittenTemp = false

        SourceTestStore.beforeTargetReplaceHook = { tmp, destination ->
            assertEquals(target, destination)
            assertTrue("append must write the complete candidate to tmp first", tmp.exists())
            assertEquals(3, JSONObject(tmp.readText()).getJSONArray("sessions").length())
            hookSawWrittenTemp = true

            // Model an abrupt stop after the tmp write and before rename, without kill/sleep/locks.
            tmp.writeText("{\"sessions\":[{\"ts\":")
            throw SimulatedProcessInterruption()
        }

        assertThrowsSimulatedInterruption { SourceTestStore.append(context, appended) }

        assertTrue("the seam must run after tmp was written", hookSawWrittenTemp)
        assertEquals("{\"sessions\":[{\"ts\":", SourceTestStore.tempFile(context).readText())
        assertEquals("interruption before rename must not change the old target", oldTargetText, target.readText())
        assertEquals(oldSessions, SourceTestStore.load(context))

        // Also exercise the real post-seam append protocol: a completed append replaces the target
        // and does not leave the tmp sidecar behind.
        // Scope: this test stops before rename; renameTo failure followed by direct-write fallback
        // remains a separate, intentionally uncovered interruption window.
        SourceTestStore.beforeTargetReplaceHook = null
        SourceTestStore.append(context, appended)
        assertEquals(listOf(appended) + oldSessions.asReversed(), SourceTestStore.load(context))
        assertEquals(3, JSONObject(target.readText()).getJSONArray("sessions").length())
        assertFalse("completed append must consume tmp", SourceTestStore.tempFile(context).exists())
    }

    private fun assertThrowsSimulatedInterruption(block: () -> Unit) {
        try {
            block()
        } catch (_: SimulatedProcessInterruption) {
            return
        }
        throw AssertionError("expected simulated process interruption")
    }

    private class SimulatedProcessInterruption : RuntimeException()

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

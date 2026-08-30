package com.gamecenter.app.modules.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 单次测速中一个边缘的结果。elapsedMs 为下载满 5MB 的总耗时；失败时 ok=false。 */
data class EdgeTestResult(val host: String, val elapsedMs: Long, val ok: Boolean)

/** 一次完整的进 App 测速会话记录。 */
data class SourceTestSession(
    val timestampMs: Long,
    val network: String,              // "wifi" | "mobile"
    val winner: String,               // 胜者主机名；全失败时为空串
    val edges: List<EdgeTestResult>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestampMs)
        put("net", network)
        put("winner", winner)
        put("edges", JSONArray().apply {
            edges.forEach { e ->
                put(JSONObject().apply {
                    put("host", e.host); put("ms", e.elapsedMs); put("ok", e.ok)
                })
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): SourceTestSession {
            val edges = mutableListOf<EdgeTestResult>()
            val arr = o.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                edges.add(EdgeTestResult(e.optString("host"), e.optLong("ms"), e.optBoolean("ok")))
            }
            return SourceTestSession(o.optLong("ts"), o.optString("net"), o.optString("winner"), edges)
        }
    }
}

/**
 * 测速记录持久化：保留最近 [MAX_SESSIONS] 条；
 * 修剪时硬性保护最新 [PROTECT_MOBILE] 条移动网络记录（用户要求：移动测速关闭后
 * 用保留的移动记录做平均选优，因此这两条是数据底座，永不因窗口滚动被淘汰）。
 */
object SourceTestStore {
    private const val MAX_SESSIONS = 5
    private const val PROTECT_MOBILE = 2
    private const val FILE_NAME = "source_tests.json"
    private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun load(context: Context): List<SourceTestSession> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(f.readText()).optJSONArray("sessions") ?: JSONArray()
            (0 until arr.length()).map { SourceTestSession.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun append(context: Context, session: SourceTestSession) {
        val kept = prune(load(context) + session)
        file(context).writeText(JSONObject().put("sessions", JSONArray().apply {
            kept.forEach { put(it.toJson()) }
        }).toString())
    }

    /**
     * 修剪策略：按时间倒序取最新 [MAX_SESSIONS] 条，但最新 [PROTECT_MOBILE] 条
     * 移动记录强制保留（可能使总数略超窗口，属预期行为）。
     */
    fun prune(sessions: List<SourceTestSession>): List<SourceTestSession> {
        val sorted = sessions.sortedByDescending { it.timestampMs }
        val protectedMobile = sorted.filter { it.network == "mobile" }.take(PROTECT_MOBILE).toSet()
        val result = sorted.take(MAX_SESSIONS).toMutableSet()
        result.addAll(protectedMobile)
        return result.toList().sortedByDescending { it.timestampMs }
    }

    /** 对保留的移动记录按边缘求平均耗时，返回平均耗时最小的主机；无数据返回 null。 */
    fun bestMobileHost(context: Context, hosts: List<String>): String? {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val mobile = load(context).filter { it.network == "mobile" && it.timestampMs >= cutoff }
        return bestHostFromSessions(mobile, hosts)
    }

    /** 纯函数核心：对会话集合按边缘求平均耗时，平均最小者胜出。 */
    fun bestHostFromSessions(sessions: List<SourceTestSession>, hosts: List<String>): String? {
        val sums = HashMap<String, Pair<Long, Int>>() // host -> (总耗时, 次数)
        sessions.forEach { s ->
            s.edges.filter { it.ok }.forEach { e ->
                val old = sums[e.host] ?: (0L to 0)
                sums[e.host] = old.first + e.elapsedMs to old.second + 1
            }
        }
        return sums.entries
            .filter { it.value.second > 0 && hosts.contains(it.key) }
            .minByOrNull { it.value.first.toDouble() / it.value.second }
            ?.key
    }

    fun latestMobileWinner(context: Context): String? =
        load(context).filter { it.network == "mobile" && it.winner.isNotEmpty() }
            .maxByOrNull { it.timestampMs }?.winner
}

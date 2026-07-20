package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 模块下载指标收集器（Batch 21）。
 *
 * 收集每次模块下载的成功/失败/耗时/重试次数等指标，写入本地文件。
 * - 内存缓存最多 50 条，超出自动 flush 到磁盘
 * - 提供 dump() 方法用于排查问题（adb pull）
 * - 仅在本地存储，不主动上报服务器（后续可扩展）
 *
 * 数据格式（JSON Lines，每行一个 JSON 对象）：
 * {"moduleId":"vpn","success":true,"durationMs":1234,"errorCode":0,"urlIndex":0,"attemptCount":0,"timestamp":1784511230000}
 */
object DownloadMetricsCollector {

    private const val TAG = "DownloadMetrics"
    private const val MAX_BUFFER_SIZE = 50
    private const val METRICS_DIR = "module_metrics"
    private const val METRICS_FILE = "downloads.jsonl"

    private val buffer = mutableListOf<DownloadMetric>()
    private var appContext: Context? = null

    /** 初始化（在 App.onCreate 中调用，传入 applicationContext） */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 记录一次下载结果 */
    @Synchronized
    fun record(metric: DownloadMetric) {
        buffer.add(metric)
        Log.d(TAG, "record: ${metric.moduleId} success=${metric.success} " +
            "duration=${metric.durationMs}ms attempt=${metric.attemptCount}")
        if (buffer.size >= MAX_BUFFER_SIZE) {
            flush()
        }
    }

    /** 将内存中的指标写入磁盘文件（JSON Lines 格式，每行一个 JSON 对象） */
    @Synchronized
    fun flush() {
        val ctx = appContext ?: return
        if (buffer.isEmpty()) return
        try {
            val dir = File(ctx.filesDir, METRICS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, METRICS_FILE)
            val jsonLines = buffer.joinToString("\n", postfix = "\n") { metric ->
                JSONObject().apply {
                    put("moduleId", metric.moduleId)
                    put("success", metric.success)
                    put("durationMs", metric.durationMs)
                    put("errorCode", metric.errorCode)
                    put("urlIndex", metric.urlIndex)
                    put("attemptCount", metric.attemptCount)
                    put("timestamp", metric.timestamp)
                }.toString()
            }
            file.appendText(jsonLines)
            buffer.clear()
        } catch (e: Exception) {
            Log.w(TAG, "flush 失败: ${e.message}")
        }
    }

    /** 读取所有已记录的指标（用于排查问题） */
    @Synchronized
    fun dump(): List<DownloadMetric> {
        val ctx = appContext ?: return emptyList()
        val result = mutableListOf<DownloadMetric>()
        result.addAll(buffer)
        try {
            val file = File(ctx.filesDir, "$METRICS_DIR/$METRICS_FILE")
            if (file.exists()) {
                file.readLines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = JSONObject(line)
                        result.add(DownloadMetric(
                            moduleId = json.getString("moduleId"),
                            success = json.getBoolean("success"),
                            durationMs = json.getLong("durationMs"),
                            errorCode = json.getInt("errorCode"),
                            urlIndex = json.getInt("urlIndex"),
                            attemptCount = json.getInt("attemptCount"),
                            timestamp = json.getLong("timestamp")
                        ))
                    } catch (_: Exception) { /* skip malformed line */ }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "dump 失败: ${e.message}")
        }
        return result
    }

    /** 清空所有指标（仅用于测试 / 用户主动清除） */
    @Synchronized
    fun clear() {
        buffer.clear()
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, "$METRICS_DIR/$METRICS_FILE")
            if (file.exists()) file.delete()
        } catch (_: Exception) { /* ignore */ }
    }

    /** 汇总统计：成功率 / 平均耗时 / 失败原因分布 */
    @Synchronized
    fun summary(): String {
        val all = dump()
        if (all.isEmpty()) return "暂无下载指标"
        val success = all.count { it.success }
        val failed = all.count { !it.success }
        val successRate = success * 100.0 / all.size
        val avgDuration = all.filter { it.success }.map { it.durationMs }.average().toLong()
        val failureByCode = all.filter { !it.success }.groupBy { it.errorCode }
            .mapValues { it.value.size }
        return "总数=${all.size}, 成功=$success, 失败=$failed, " +
            "成功率=${"%.1f".format(successRate)}%, 平均耗时=${avgDuration}ms, " +
            "失败分布=$failureByCode"
    }
}

/** 单次下载指标数据类 */
data class DownloadMetric(
    val moduleId: String,
    val success: Boolean,
    val durationMs: Long,
    val errorCode: Int,
    val urlIndex: Int,
    val attemptCount: Int,
    val timestamp: Long
)

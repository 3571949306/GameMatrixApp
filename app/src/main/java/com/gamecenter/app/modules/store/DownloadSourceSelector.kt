package com.gamecenter.app.modules.store

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.SettingsManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 分发架构 v2：下载源自动选择。
 *
 * - 基准队列恒为 JP → HK → US（jp.dl 默认首选，超时由下载框架级联）。
 * - 进 App 后台顺序测速（服务端限速 5MB/s，客户端单线程各下 5MB 比总耗时），
 *   胜者提升到队首；移动网络是否测速由用户设置与样本数决定。
 * - 全部失败/关闭时回基准队列。
 */
object DownloadSourceSelector {

    const val PROBE_PATH = "probe.bin"
    const val PROBE_BYTES = 5L * 1024 * 1024      // 每边缘下载 5MB
    const val PROBE_TIMEOUT_MS = 15_000           // 单边缘上限
    const val MIRROR_CONNECT_TIMEOUT_MS = 6_000   // 下载级联快速失败

    private val hostList: List<String> by lazy {
        BuildConfig.DL_MIRROR_HOSTS.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val testedThisProcess = AtomicBoolean(false)
    @Volatile private var cachedWinner: String? = null

    fun mirrorHosts(): List<String> = hostList

    fun mirrorBaseFor(host: String): String? =
        BuildConfig.DL_MIRROR_BASES.split(",").map { it.trim() }
            .firstOrNull { it.contains(host) }

    fun isMobileNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * 进 App 入口：满足条件时后台执行一次测速（阻塞调用，须在工作线程调用）。
     * 决策表：
     *   主开关关 → 不测；
     *   WiFi/非计费 → 测；
     *   移动 + mobileAutoSelect 开 + （自动关闭关 或 移动样本 < N）→ 测；
     *   其余 → 不测。
     */
    fun runEntryProbeIfNeeded(context: Context) {
        val settings = SettingsManager.getInstance(context)
        if (!settings.isDlAutoSelect()) return
        if (!testedThisProcess.compareAndSet(false, true)) return
        val mobile = isMobileNetwork(context)
        var shouldTest = false
        if (!mobile) {
            shouldTest = true
        } else if (settings.isDlMobileAutoSelect()) {
            val gateReached = settings.isDlMobileAutoDisable() &&
                    reachedMobileSampleTarget(context)
            if (gateReached) {
                // 用户要求：采集满 N 次样本后自动关闭移动网络测速（设置项可见地翻转）
                settings.setDlMobileAutoSelect(false)
                android.util.Log.i("DLSelector", "移动测速样本已满，自动关闭移动网络自动选择")
            } else {
                shouldTest = true
            }
        }
        if (!shouldTest) return
        runCatching {
            val (winner, session) = probeAll(context, mobile)
            SourceTestStore.append(context, session)
            if (winner.isNotEmpty()) cachedWinner = winner
        }
    }

    private fun reachedMobileSampleTarget(context: Context): Boolean {
        val mobileCount = SourceTestStore.load(context).count { it.network == "mobile" }
        return SettingsManager.getInstance(context).isDlMobileAutoDisable() &&
                mobileCount >= SettingsManager.DL_MOBILE_SAMPLE_TARGET
    }

    /** 单线程顺序测三台：各下载 [PROBE_BYTES] 字节 /probe.bin，比总耗时。 */
    fun probeAll(context: Context, mobile: Boolean): Pair<String, SourceTestSession> {
        val results = mutableListOf<EdgeTestResult>()
        var winner = ""
        var bestMs = Long.MAX_VALUE
        for (host in hostList) {
            val elapsed = probeHost(host)
            val ok = elapsed != null
            results.add(EdgeTestResult(host, elapsed ?: -1L, ok))
            if (ok && elapsed != null && elapsed < bestMs) {
                bestMs = elapsed; winner = host
            }
        }
        val net = if (mobile) "mobile" else "wifi"
        return winner to SourceTestSession(System.currentTimeMillis(), net, winner, results)
    }

    /** 下载前 [PROBE_BYTES] 字节，返回总耗时毫秒；失败返回 null。 */
    private fun probeHost(host: String): Long? {
        val base = mirrorBaseFor(host) ?: return null
        return runCatching {
            val conn = URL("$base/$PROBE_PATH").openConnection() as HttpURLConnection
            conn.connectTimeout = PROBE_TIMEOUT_MS
            conn.readTimeout = PROBE_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            val start = System.currentTimeMillis()
            try {
                if (conn.responseCode !in 200..299) return null
                val input = conn.inputStream
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (total < PROBE_BYTES) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), PROBE_BYTES - total).toInt())
                    if (n < 0) break
                    total += n
                }
                if (total < PROBE_BYTES) return null
                System.currentTimeMillis() - start
            } finally {
                runCatching { conn.inputStream.close() }
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * 返回镜像访问基础地址（https://host:2088）的推荐顺序：
     * 胜者（本次/历史）置首，其余按 JP→HK→US 基准；全无数据即基准顺序。
     */
    fun preferredMirrorBases(context: Context): List<String> {
        if (!SettingsManager.getInstance(context).isDlAutoSelect()) return mirrorHosts().mapNotNull { mirrorBaseFor(it) }
        val winner = cachedWinner
            ?: SourceTestStore.latestMobileWinner(context)
            ?: SourceTestStore.bestMobileHost(context, hostList)
            ?: SourceTestStore.load(context)
                .filter { it.network == "wifi" && it.winner.isNotEmpty() }
                .maxByOrNull { it.timestampMs }
                ?.takeIf { it.timestampMs >= System.currentTimeMillis() - 30L * 24 * 3600 * 1000 }
                ?.winner
        val hosts = if (winner != null && hostList.contains(winner)) {
            listOf(winner) + hostList.filter { it != winner }
        } else hostList
        return hosts.mapNotNull { mirrorBaseFor(it) }
    }

    /** 供宿主自更新/反馈复用的同一决策：首选镜像 base（含 /app 后缀由调用方拼接）。 */
    fun preferredUpdateMirrorBase(context: Context): String? =
        preferredMirrorBases(context).firstOrNull()
}

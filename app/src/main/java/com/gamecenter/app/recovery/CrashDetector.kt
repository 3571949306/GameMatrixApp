package com.gamecenter.app.recovery

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log

object CrashDetector {

    private const val TAG = "CrashDetector"
    private const val PREFS_NAME = "recovery_prefs"

    // —— 启发式信号：冷启动后未能存活 3s（兜底，捕捉 <3s 即死的进程，如启动期 OOM 被杀）——
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val CRASH_THRESHOLD = 3
    private const val CRASH_WINDOW_MS = 120_000L // 放宽至 2 分钟，覆盖较慢复现的连续冷启动死亡

    // —— 真实崩溃信号：由 CrashHandler(UncaughtExceptionHandler) 转发，是恢复模式的主信号（无 3s 门限）——
    private const val KEY_REAL_CRASH_COUNT = "real_crash_count"
    private const val KEY_LAST_REAL_CRASH_TIME = "last_real_crash_time"
    private const val REAL_CRASH_THRESHOLD = 2
    private const val REAL_CRASH_WINDOW_MS = 10 * 60_000L // 10 分钟滚动窗口，覆盖间歇/慢速崩溃

    // —— R4: 优雅退出哨兵，用于消除"连续快速正常打开"对启发式信号的误触发 ——
    // 上一会话曾存活满 3s（markAppRunning 置位）或用户主动离开（onUserLeaveHint 置位），
    // 均说明上一次会话并非"启动即死"，本次冷启动不应计入崩溃。
    private const val KEY_PREV_SURVIVED = "prev_survived"
    private const val KEY_PREV_GRACEFUL = "prev_graceful"

    private const val KEY_RECOVERY_TRIGGERED = "recovery_triggered"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 冷启动计数：进程每次冷启动 +1。配合 [markAppRunning] 的 3s 存活清零，
     * 用于捕捉"启动后 3 秒内即死"的进程（不含被真正捕获的崩溃，见 [recordCrash]）。
     * 使用 commit() 同步落盘，避免极早期崩溃丢失计数。
     */
    fun markAppStart(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
        val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0)

        // R4: 上一会话正常存活或优雅退出 → 本次为正常冷启动，不计入崩溃计数
        val prevSurvived = prefs.getBoolean(KEY_PREV_SURVIVED, false)
        val prevGraceful = prefs.getBoolean(KEY_PREV_GRACEFUL, false)

        val newCount = if (prevSurvived || prevGraceful) {
            Log.d(TAG, "Previous session ended normally (survived=$prevSurvived, graceful=$prevGraceful); not counting as crash")
            0
        } else {
            if (now - lastCrashTime > CRASH_WINDOW_MS) 1 else crashCount + 1
        }

        prefs.edit()
            .putInt(KEY_CRASH_COUNT, newCount)
            .putLong(KEY_LAST_CRASH_TIME, now)
            .putBoolean(KEY_PREV_SURVIVED, false)
            .putBoolean(KEY_PREV_GRACEFUL, false)
            .apply() // P0 流畅度优化：冷启动主线程非崩溃路径，异步落盘足够；保留 recordCrash 的 commit 以维持崩溃自愈信号
        Log.d(TAG, "Heuristic crash count incremented to $newCount")
    }

    /**
     * 记录一次真实未捕获崩溃（由 CrashHandler 的崩溃回调转发）。
     * 与存活门限无关——任何线程、任何时机的崩溃都会被记录，是恢复模式的主触发信号。
     * 使用 commit() 同步落盘，确保进程在崩溃后立即死亡也不会丢计数。
     */
    fun recordCrash(context: Context) {
        val now = System.currentTimeMillis()
        val last = prefs(context).getLong(KEY_LAST_REAL_CRASH_TIME, 0L)
        val count = prefs(context).getInt(KEY_REAL_CRASH_COUNT, 0)
        val newCount = if (now - last > REAL_CRASH_WINDOW_MS) 1 else count + 1
        prefs(context).edit()
            .putInt(KEY_REAL_CRASH_COUNT, newCount)
            .putLong(KEY_LAST_REAL_CRASH_TIME, now)
            .commit()
        Log.w(TAG, "Real crash recorded (count=$newCount) -> recovery may trigger")
    }

    /**
     * App 正常存活 3s：仅清除启发式计数。
     * 注意：真实崩溃计数（real_crash_*）不在此清除，靠 [REAL_CRASH_WINDOW_MS] 时间窗口自然过期，
     * 以免"3s 后存活"把一次真实崩溃的记录抹掉（这正是旧逻辑的关键盲区）。
     */
    fun markAppRunning(context: Context) {
        mainHandler.postDelayed({
            prefs(context).edit()
                .putInt(KEY_CRASH_COUNT, 0)
                .putLong(KEY_LAST_CRASH_TIME, 0L)
                .putBoolean(KEY_PREV_SURVIVED, true)
                .putBoolean(KEY_RECOVERY_TRIGGERED, false)
                .apply()
            Log.d(TAG, "App running normally, heuristic crash count cleared")
        }, 3000L)
    }

    /**
     * R4: 标记上一次会话为用户主动离开（非崩溃）。
     * 由 [MainActivity.onUserLeaveHint] 调用：只要应用走到该生命周期，
     * 即说明它仍存活、并非"启动即死"，本次冷启动不应计入启发式崩溃计数。
     */
    fun markGracefulExit(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_PREV_GRACEFUL, true)
            .apply()
    }

    fun shouldLaunchRecovery(context: Context): Boolean {
        if (prefs(context).getBoolean(KEY_RECOVERY_TRIGGERED, false)) {
            return true
        }
        val now = System.currentTimeMillis()

        // 信号一：真实崩溃（主信号，无 3s 门限，10 分钟滚动窗口）
        val realCount = prefs(context).getInt(KEY_REAL_CRASH_COUNT, 0)
        val lastReal = prefs(context).getLong(KEY_LAST_REAL_CRASH_TIME, 0L)
        if (realCount >= REAL_CRASH_THRESHOLD && (now - lastReal) < REAL_CRASH_WINDOW_MS) {
            Log.w(TAG, "Real-crash threshold reached ($realCount), launching recovery")
            prefs(context).edit().putBoolean(KEY_RECOVERY_TRIGGERED, true).apply()
            return true
        }

        // 信号二：启发式（冷启动 <3s 即死，兜底）
        val crashCount = prefs(context).getInt(KEY_CRASH_COUNT, 0)
        val lastCrashTime = prefs(context).getLong(KEY_LAST_CRASH_TIME, 0L)
        if (crashCount >= CRASH_THRESHOLD && (now - lastCrashTime) < CRASH_WINDOW_MS) {
            Log.w(TAG, "Heuristic crash threshold reached ($crashCount), launching recovery")
            prefs(context).edit().putBoolean(KEY_RECOVERY_TRIGGERED, true).apply()
            return true
        }
        return false
    }

    fun clearRecoveryFlag(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RECOVERY_TRIGGERED, false)
            .putInt(KEY_CRASH_COUNT, 0)
            .putLong(KEY_LAST_CRASH_TIME, 0L)
            .putInt(KEY_REAL_CRASH_COUNT, 0)
            .putLong(KEY_LAST_REAL_CRASH_TIME, 0L)
            .apply()
    }

    fun getCrashCount(context: Context): Int {
        return prefs(context).getInt(KEY_CRASH_COUNT, 0) +
            prefs(context).getInt(KEY_REAL_CRASH_COUNT, 0)
    }
}

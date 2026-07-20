package com.gamecenter.app.ui

import android.os.SystemClock

/**
 * Batch 12-4 (APP_LAUNCH_TIME_DISPLAY): 应用启动耗时追踪。
 *
 * 由 [com.gamecenter.app.SplashActivity] 在 onCreate 起点记录开始时间，
 * 在首页 Fragment 渲染完成时调用 [elapsedMs] 读取耗时。
 *
 * 使用 [SystemClock.elapsedRealtime] 避免系统时间被人为修改导致负值。
 * 仅保留单次启动的计时，不跨进程。
 */
object LaunchTimeTracker {

    @Volatile
    private var startTimeMs: Long = 0L

    /**
     * 标记启动开始（应在 SplashActivity.onCreate 第一行调用）。
     */
    @JvmStatic
    fun markStart() {
        startTimeMs = SystemClock.elapsedRealtime()
    }

    /**
     * 获取从 markStart 到现在的耗时（毫秒）。
     * 若未调用过 markStart，返回 -1。
     */
    @JvmStatic
    fun elapsedMs(): Long {
        if (startTimeMs <= 0L) return -1L
        return SystemClock.elapsedRealtime() - startTimeMs
    }
}

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
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val CRASH_THRESHOLD = 3
    private const val CRASH_WINDOW_MS = 60_000L
    private const val KEY_RECOVERY_TRIGGERED = "recovery_triggered"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun markAppStart(context: Context) {
        val now = System.currentTimeMillis()
        val lastCrashTime = prefs(context).getLong(KEY_LAST_CRASH_TIME, 0L)
        val crashCount = prefs(context).getInt(KEY_CRASH_COUNT, 0)

        if (now - lastCrashTime > CRASH_WINDOW_MS) {
            prefs(context).edit()
                .putInt(KEY_CRASH_COUNT, 1)
                .putLong(KEY_LAST_CRASH_TIME, now)
                .apply()
            Log.d(TAG, "Crash count reset to 1 (window expired)")
        } else {
            val newCount = crashCount + 1
            prefs(context).edit()
                .putInt(KEY_CRASH_COUNT, newCount)
                .putLong(KEY_LAST_CRASH_TIME, now)
                .apply()
            Log.d(TAG, "Crash count incremented to $newCount")
        }
    }

    fun markAppRunning(context: Context) {
        mainHandler.postDelayed({
            prefs(context).edit()
                .putInt(KEY_CRASH_COUNT, 0)
                .putLong(KEY_LAST_CRASH_TIME, 0L)
                .putBoolean(KEY_RECOVERY_TRIGGERED, false)
                .apply()
            Log.d(TAG, "App running normally, crash count cleared")
        }, 3000L)
    }

    fun shouldLaunchRecovery(context: Context): Boolean {
        if (prefs(context).getBoolean(KEY_RECOVERY_TRIGGERED, false)) {
            return true
        }
        val crashCount = prefs(context).getInt(KEY_CRASH_COUNT, 0)
        val lastCrashTime = prefs(context).getLong(KEY_LAST_CRASH_TIME, 0L)
        val now = System.currentTimeMillis()
        if (crashCount >= CRASH_THRESHOLD && (now - lastCrashTime) < CRASH_WINDOW_MS) {
            Log.w(TAG, "Crash threshold reached ($crashCount), launching recovery")
            prefs(context).edit()
                .putBoolean(KEY_RECOVERY_TRIGGERED, true)
                .apply()
            return true
        }
        return false
    }

    fun clearRecoveryFlag(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RECOVERY_TRIGGERED, false)
            .putInt(KEY_CRASH_COUNT, 0)
            .putLong(KEY_LAST_CRASH_TIME, 0L)
            .apply()
    }

    fun getCrashCount(context: Context): Int {
        return prefs(context).getInt(KEY_CRASH_COUNT, 0)
    }
}

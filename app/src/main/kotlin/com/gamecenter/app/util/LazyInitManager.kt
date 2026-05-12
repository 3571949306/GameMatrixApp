package com.gamecenter.app.util

import android.os.Handler
import android.os.Looper

object LazyInitManager {
    private val handler = Handler(Looper.getMainLooper())
    private val initialized = mutableSetOf<String>()
    private val pendingTasks = mutableMapOf<String, MutableList<() -> Unit>>()
    
    @JvmStatic
    fun initWhenIdle(name: String, task: () -> Unit) {
        if (initialized.contains(name)) {
            task()
            return
        }
        
        pendingTasks.getOrPut(name) { mutableListOf() }.add(task)
        
        handler.postDelayed({
            if (!initialized.contains(name)) {
                initialized.add(name)
                pendingTasks[name]?.forEach { it() }
                pendingTasks.remove(name)
            }
        }, 1000)
    }
    
    @JvmStatic
    fun initDelayed(name: String, delayMs: Long, task: () -> Unit) {
        if (initialized.contains(name)) {
            task()
            return
        }
        
        handler.postDelayed({
            if (!initialized.contains(name)) {
                initialized.add(name)
                task()
            }
        }, delayMs)
    }
    
    @JvmStatic
    fun isInitialized(name: String): Boolean = initialized.contains(name)
    
    @JvmStatic
    fun markInitialized(name: String) {
        initialized.add(name)
        pendingTasks[name]?.forEach { it() }
        pendingTasks.remove(name)
    }
}

object PerformanceMonitor {
    private val startTimes = mutableMapOf<String, Long>()
    
    @JvmStatic
    fun startTrace(name: String) {
        startTimes[name] = System.currentTimeMillis()
    }
    
    @JvmStatic
    fun endTrace(name: String): Long {
        val startTime = startTimes.remove(name) ?: return 0
        val duration = System.currentTimeMillis() - startTime
        if (duration > 100) {
            AppLog.w("Performance: $name took ${duration}ms")
        }
        return duration
    }
    
    @JvmStatic
    inline fun <T> trace(name: String, block: () -> T): T {
        startTrace(name)
        return try {
            block()
        } finally {
            endTrace(name)
        }
    }
}

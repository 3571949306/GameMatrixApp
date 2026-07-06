package com.gamecenter.app.util

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler : Thread.UncaughtExceptionHandler {
    
    private const val TAG = "CrashHandler"
    private const val MAX_STACK_TRACE_SIZE = 8192
    
    private var context: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var crashListener: ((Thread, Throwable) -> Unit)? = null
    
    @JvmStatic
    fun init(context: Context, listener: ((Thread, Throwable) -> Unit)? = null) {
        this.context = context.applicationContext
        this.crashListener = listener
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        AppLog.e("Uncaught exception in thread: ${thread.name}", throwable)
        
        try {
            context?.let { ctx ->
                com.gamecenter.app.utils.ErrorReporter.getInstance(ctx).report(throwable, "thread=${thread.name}")
            }
        } catch (e: Exception) { Log.w("CrashHandler", "Failed to write crash log", e) }
        
        crashListener?.invoke(thread, throwable)
        
        defaultHandler?.uncaughtException(thread, throwable) ?: run {
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }
    
    @JvmStatic
    fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()
        return if (trace.length > MAX_STACK_TRACE_SIZE) {
            trace.substring(0, MAX_STACK_TRACE_SIZE) + "...[truncated]"
        } else {
            trace
        }
    }
}

inline fun <T> runCatchingResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Exception) {
    AppLog.e("Error executing block", e)
    AppResult.Error(e.message ?: "Unknown error", e)
}

inline fun <T> AppResult<T>.getOrElse(default: () -> T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Error -> default()
    is AppResult.Loading -> default()
}

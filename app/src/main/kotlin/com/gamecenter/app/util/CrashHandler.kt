package com.gamecenter.app.util

import android.content.Context
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ThreadFactory

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

class NamedThreadFactory(private val name: String) : ThreadFactory {
    private var counter = 0
    
    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r, "$name-${counter++}")
        thread.isDaemon = false
        thread.priority = Thread.NORM_PRIORITY
        return thread
    }
}

object ThreadPools {
    private val ioThreads = NamedThreadFactory("IO")
    private val networkThreads = NamedThreadFactory("Network")
    private val gameThreads = NamedThreadFactory("Game")
    
    @JvmStatic
    fun ioThread(block: () -> Unit) {
        Thread(ioThreads.newThread(block)).start()
    }
    
    @JvmStatic
    fun networkThread(block: () -> Unit) {
        Thread(networkThreads.newThread(block)).start()
    }
    
    @JvmStatic
    fun gameThread(block: () -> Unit) {
        Thread(gameThreads.newThread(block)).start()
    }
}

inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    AppLog.e("Error executing block", e)
    Result.Error(e.message ?: "Unknown error", e)
}

inline fun <T> Result<T>.getOrElse(default: () -> T): T = when (this) {
    is Result.Success -> data
    is Result.Error -> default()
    is Result.Loading -> default()
}

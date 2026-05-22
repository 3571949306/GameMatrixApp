package com.gamecenter.app.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLog {
    private const val TAG = "GameMatrix"
    
    fun d(message: String) = android.util.Log.d(TAG, message)
    fun i(message: String) = android.util.Log.i(TAG, message)
    fun w(message: String) = android.util.Log.w(TAG, message)
    fun e(message: String, throwable: Throwable? = null) = 
        if (throwable != null) android.util.Log.e(TAG, message, throwable)
        else android.util.Log.e(TAG, message)
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

suspend fun <T> withIO(block: suspend CoroutineScope.() -> T): T = 
    withContext(Dispatchers.IO, block)

suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T = 
    withContext(Dispatchers.Main, block)

inline fun <T> tryOrNull(block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    null
}

inline fun <T> tryOrDefault(default: T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    default
}

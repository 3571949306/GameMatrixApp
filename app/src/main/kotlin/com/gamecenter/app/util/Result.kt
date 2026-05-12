package com.gamecenter.app.util

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
    
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (String) -> Unit): Result<T> {
        if (this is Error) action(message)
        return this
    }
    
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
    
    fun getOrNull(): T? = (this as? Success)?.data
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw cause ?: IllegalStateException(message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }
    
    companion object {
        inline fun <T> of(action: () -> T): Result<T> = try {
            Success(action())
        } catch (e: Exception) {
            Error(e.message ?: "Unknown error", e)
        }
        
        inline fun <T> ofNullable(action: () -> T?): Result<T> = try {
            action()?.let { Success(it) } ?: Error("Result is null")
        } catch (e: Exception) {
            Error(e.message ?: "Unknown error", e)
        }
    }
}

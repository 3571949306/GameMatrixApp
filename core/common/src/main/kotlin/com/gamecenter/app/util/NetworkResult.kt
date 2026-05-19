package com.gamecenter.app.util

import com.gamecenter.app.util.AppError

sealed class NetworkResult<out T> {

    data class Success<out T>(val data: T) : NetworkResult<T>()

    data class Failure(val error: AppError) : NetworkResult<Nothing>()

    data object Loading : NetworkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error.cause ?: IllegalStateException(error.message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    fun getErrorMessageOrNull(): String? = (this as? Failure)?.error?.message

    inline fun <R> map(transform: (T) -> R): NetworkResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        is Loading -> this
    }

    inline fun onSuccess(action: (T) -> Unit): NetworkResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (AppError) -> Unit): NetworkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun onLoading(action: () -> Unit): NetworkResult<T> {
        if (this is Loading) action()
        return this
    }

    companion object {
        @JvmStatic
        inline fun <T> of(action: () -> T): NetworkResult<T> = try {
            Success(action())
        } catch (e: Exception) {
            Failure(AppError.fromException(e))
        }

        @JvmStatic
        inline fun <T> ofNullable(action: () -> T?): NetworkResult<T> = try {
            action()?.let { Success(it) } ?: Failure(AppError.BusinessError("结果为空"))
        } catch (e: Exception) {
            Failure(AppError.fromException(e))
        }
    }
}

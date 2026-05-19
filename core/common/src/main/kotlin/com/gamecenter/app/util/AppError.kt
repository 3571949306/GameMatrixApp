package com.gamecenter.app.util

sealed class AppError(
    val code: Int,
    val message: String,
    val cause: Throwable? = null
) {

    class NetworkDisconnected(
        message: String = "网络未连接",
        cause: Throwable? = null
    ) : AppError(CODE_NETWORK_DISCONNECTED, message, cause)

    class Timeout(
        message: String = "请求超时",
        cause: Throwable? = null
    ) : AppError(CODE_TIMEOUT, message, cause)

    class DnsResolution(
        message: String = "DNS解析失败",
        cause: Throwable? = null
    ) : AppError(CODE_DNS_RESOLUTION, message, cause)

    class ServerError(
        val httpCode: Int,
        message: String = "服务器错误",
        cause: Throwable? = null
    ) : AppError(CODE_SERVER_ERROR, message, cause)

    class ClientError(
        val httpCode: Int,
        message: String = "请求错误",
        cause: Throwable? = null
    ) : AppError(CODE_CLIENT_ERROR, message, cause)

    class SslError(
        message: String = "SSL/TLS握手失败",
        cause: Throwable? = null
    ) : AppError(CODE_SSL, message, cause)

    class IoError(
        message: String = "网络IO异常",
        cause: Throwable? = null
    ) : AppError(CODE_IO, message, cause)

    class Cancelled(
        message: String = "请求已取消",
        cause: Throwable? = null
    ) : AppError(CODE_CANCELLED, message, cause)

    class BusinessError(
        message: String,
        cause: Throwable? = null
    ) : AppError(CODE_BUSINESS, message, cause)

    class Unknown(
        message: String = "未知错误",
        cause: Throwable? = null
    ) : AppError(CODE_UNKNOWN, message, cause)

    companion object {
        const val CODE_NETWORK_DISCONNECTED = -1
        const val CODE_TIMEOUT = -2
        const val CODE_DNS_RESOLUTION = -3
        const val CODE_SERVER_ERROR = -4
        const val CODE_CLIENT_ERROR = -5
        const val CODE_UNKNOWN = -6
        const val CODE_IO = -7
        const val CODE_SSL = -8
        const val CODE_CANCELLED = -9
        const val CODE_BUSINESS = -10

        @JvmStatic
        fun fromException(e: Throwable): AppError = when (e) {
            is java.net.SocketTimeoutException -> Timeout(cause = e)
            is java.net.UnknownHostException -> DnsResolution(cause = e)
            is javax.net.ssl.SSLException -> SslError(cause = e)
            is java.io.InterruptedIOException -> Cancelled(cause = e)
            is java.io.IOException -> IoError(cause = e)
            else -> Unknown(message = e.message ?: "未知错误", cause = e)
        }

        @JvmStatic
        fun fromHttpCode(httpCode: Int, responseBody: String? = null): AppError = when {
            httpCode >= 500 -> ServerError(
                httpCode = httpCode,
                message = "服务器错误 ($httpCode)",
                cause = null
            )
            httpCode >= 400 -> ClientError(
                httpCode = httpCode,
                message = responseBody ?: "请求错误 ($httpCode)",
                cause = null
            )
            else -> Unknown(message = "未处理的HTTP状态码: $httpCode")
        }
    }

    fun isNetworkError(): Boolean = code in listOf(
        CODE_NETWORK_DISCONNECTED, CODE_TIMEOUT, CODE_DNS_RESOLUTION,
        CODE_IO, CODE_SSL, CODE_CANCELLED
    )

    fun isServerError(): Boolean = code == CODE_SERVER_ERROR

    fun isRecoverable(): Boolean = code in listOf(
        CODE_TIMEOUT, CODE_IO, CODE_SERVER_ERROR, CODE_NETWORK_DISCONNECTED
    )
}

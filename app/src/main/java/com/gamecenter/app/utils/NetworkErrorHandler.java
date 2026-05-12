package com.gamecenter.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.ResponseBody;

/**
 * 统一网络请求错误处理器。
 * 集中管理所有网络模块的错误处理逻辑，提供统一的错误码和用户友好提示。
 */
public class NetworkErrorHandler {

    // ============ 错误码定义 ============

    public static final int ERROR_NETWORK_DISCONNECTED = -1;
    public static final int ERROR_TIMEOUT = -2;
    public static final int ERROR_DNS_RESOLUTION = -3;
    public static final int ERROR_SERVER_5XX = -4;
    public static final int ERROR_SERVER_4XX = -5;
    public static final int ERROR_UNKNOWN = -6;
    public static final int ERROR_IO = -7;
    public static final int ERROR_SSL = -8;
    public static final int ERROR_CANCELLED = -9;

    // ============ 错误码转用户消息 ============

    public static String getErrorMessage(Context context, int errorCode) {
        if (context == null) return "网络错误";
        
        switch (errorCode) {
            case ERROR_NETWORK_DISCONNECTED:
                return context.getString(R.string.error_network_disconnected);
            case ERROR_TIMEOUT:
                return context.getString(R.string.error_timeout);
            case ERROR_DNS_RESOLUTION:
                return context.getString(R.string.error_dns_resolution);
            case ERROR_SERVER_5XX:
                return context.getString(R.string.error_server_5xx);
            case ERROR_SERVER_4XX:
                return context.getString(R.string.error_server_4xx);
            case ERROR_IO:
                return context.getString(R.string.error_io);
            case ERROR_SSL:
                return context.getString(R.string.error_ssl);
            case ERROR_CANCELLED:
                return context.getString(R.string.error_cancelled);
            case ERROR_UNKNOWN:
            default:
                return context.getString(R.string.error_unknown);
        }
    }

    // ============ 异常转错误码 ============

    public static int getErrorCodeFromException(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return ERROR_TIMEOUT;
        }
        if (e instanceof UnknownHostException) {
            return ERROR_DNS_RESOLUTION;
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return ERROR_SSL;
        }
        if (e instanceof java.io.InterruptedIOException) {
            return ERROR_CANCELLED;
        }
        if (e instanceof IOException) {
            return ERROR_IO;
        }
        return ERROR_UNKNOWN;
    }

    // ============ HTTP响应码转错误码 ============

    public static int getErrorCodeFromHttpCode(int httpCode) {
        if (httpCode >= 500) {
            return ERROR_SERVER_5XX;
        }
        if (httpCode >= 400) {
            return ERROR_SERVER_4XX;
        }
        return ERROR_UNKNOWN;
    }

    // ============ 统一错误处理方法 ============

    /**
     * 处理网络异常，显示 Toast 提示并返回错误码。
     * @return 错误码
     */
    public static int handleNetworkException(Context context, Exception e) {
        return handleNetworkException(context, e, true);
    }

    /**
     * 处理网络异常，可选择是否显示 Toast 提示。
     * @param showToast 是否显示 Toast
     * @return 错误码
     */
    public static int handleNetworkException(Context context, Exception e, boolean showToast) {
        int errorCode = getErrorCodeFromException(e);
        
        if (showToast && context != null) {
            String message = getErrorMessage(context, errorCode);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
        
        return errorCode;
    }

    /**
     * 处理 HTTP 响应错误。
     * @param response 响应体
     * @param showToast 是否显示 Toast
     * @return 错误码
     */
    public static int handleHttpResponse(Context context, okhttp3.Response response, boolean showToast) {
        int httpCode = response.code();
        int errorCode = getErrorCodeFromHttpCode(httpCode);
        
        if (showToast && context != null) {
            String message = getErrorMessage(context, errorCode);
            Toast.makeText(context, message + " (" + httpCode + ")", Toast.LENGTH_SHORT).show();
        }
        
        return errorCode;
    }

    // ============ 网络状态检查 ============

    /**
     * 检查当前网络是否可用。
     * @return true 如果网络可用
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查网络是否可用，不可用时显示 Toast 提示。
     * @return true 如果网络可用
     */
    public static boolean checkNetworkAndShowToast(Context context) {
        if (!isNetworkAvailable(context)) {
            if (context != null) {
                Toast.makeText(context, R.string.error_network_disconnected, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    // ============ 重试逻辑 ============

    /**
     * 智能重试策略。
     * @param maxRetries 最大重试次数
     * @param baseDelayMs 基础延迟（毫秒）
     * @param callback 重试回调
     * @return true 如果成功
     */
    public static boolean retryWithBackoff(int maxRetries, long baseDelayMs, RetryCallback callback) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (callback.execute(attempt)) {
                    return true;
                }
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    return false;
                }
            }
            
            try {
                // 指数退避延迟
                long delay = baseDelayMs * (long) Math.pow(2, attempt - 1);
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ============ 回调接口 ============

    public interface RetryCallback {
        /**
         * 执行重试逻辑。
         * @param attempt 当前尝试次数（从1开始）
         * @return true 如果成功
         */
        boolean execute(int attempt) throws Exception;
    }
}

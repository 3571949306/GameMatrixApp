package com.gamecenter.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.widget.Toast;

import com.gamecenter.app.network.R;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.ResponseBody;

/**
 * 统一网络请求错误处理器。
 *
 * <p>集中管理所有网络模块的错误处理逻辑，提供统一的错误码和用户友好提示。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义标准化的网络错误码常量，覆盖断网、超时、DNS、SSL、服务端错误等场景</li>
 *   <li>将 Java 异常类型映射为对应的错误码（{@link #getErrorCodeFromException(Exception)}）</li>
 *   <li>将 HTTP 状态码映射为对应的错误码（{@link #getErrorCodeFromHttpCode(int)}）</li>
 *   <li>将错误码转换为本地化的用户提示消息</li>
 *   <li>提供网络可用性检查及自动 Toast 提示</li>
 *   <li>内置指数退避重试策略（{@link #retryWithBackoff}）</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>全部方法为 static，无状态，可直接调用，降低使用门槛</li>
 *   <li>错误码使用负数，避免与 HTTP 状态码（正数）混淆</li>
 *   <li>网络状态检查兼容 Android M+ 的新 API 和旧版 API</li>
 * </ul>
 */
public class NetworkErrorHandler {

    // ============ 错误码定义 ============

    /** 网络断开（无可用网络连接） */
    public static final int ERROR_NETWORK_DISCONNECTED = -1;
    /** 请求超时（SocketTimeoutException） */
    public static final int ERROR_TIMEOUT = -2;
    /** DNS解析失败（UnknownHostException） */
    public static final int ERROR_DNS_RESOLUTION = -3;
    /** 服务端5xx错误 */
    public static final int ERROR_SERVER_5XX = -4;
    /** 客户端4xx错误 */
    public static final int ERROR_SERVER_4XX = -5;
    /** 未知错误（无法归类的异常） */
    public static final int ERROR_UNKNOWN = -6;
    /** 通用IO异常 */
    public static final int ERROR_IO = -7;
    /** SSL/TLS握手错误 */
    public static final int ERROR_SSL = -8;
    /** 请求被取消（InterruptedIOException） */
    public static final int ERROR_CANCELLED = -9;

    // ============ 错误码转用户消息 ============

    /**
     * 将错误码转换为用户可读的本地化提示消息。
     *
     * @param context   上下文，用于获取字符串资源；为 null 时返回默认中文提示
     * @param errorCode 错误码常量（如 {@link #ERROR_TIMEOUT}）
     * @return 对应的用户友好提示文本
     */
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

    /**
     * 将 Java 异常映射为标准化的网络错误码。
     *
     * <p>匹配优先级（从高到低）：
     * SocketTimeoutException → UnknownHostException → SSLException →
     * InterruptedIOException → IOException → 未知
     *
     * <p>注意：IOException 的判断放在最后，因为上述更具体的异常都是 IOException 的子类，
     * 必须先匹配子类型再匹配父类型，否则会被提前截获。
     *
     * @param e 网络请求过程中抛出的异常
     * @return 对应的错误码常量
     */
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

    /**
     * 将 HTTP 响应状态码映射为标准化的网络错误码。
     *
     * <p>映射规则：
     * <ul>
     *   <li>5xx → {@link #ERROR_SERVER_5XX}（服务端错误）</li>
     *   <li>4xx → {@link #ERROR_SERVER_4XX}（客户端错误）</li>
     *   <li>其他 → {@link #ERROR_UNKNOWN}</li>
     * </ul>
     *
     * @param httpCode HTTP 响应状态码（如 404、500）
     * @return 对应的错误码常量
     */
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
     *
     * @param context 上下文，用于显示 Toast 和获取字符串资源
     * @param e       网络异常
     * @return 错误码
     */
    public static int handleNetworkException(Context context, Exception e) {
        return handleNetworkException(context, e, true);
    }

    /**
     * 处理网络异常，可选择是否显示 Toast 提示。
     *
     * @param context   上下文，用于显示 Toast 和获取字符串资源
     * @param e         网络异常
     * @param showToast 是否显示 Toast 提示用户
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
     *
     * <p>根据 HTTP 状态码映射为错误码，并可选地显示包含状态码的 Toast 提示。
     * 提示格式为 "{错误消息} ({httpCode})"，方便用户反馈具体错误。
     *
     * @param context   上下文
     * @param response  OkHttp 响应对象
     * @param showToast 是否显示 Toast 提示
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
     *
     * <p>兼容性处理：
     * <ul>
     *   <li>Android M（API 23）及以上：使用 {@link ConnectivityManager#getActiveNetwork()}
     *       和 {@link ConnectivityManager#getNetworkCapabilities(Network)} 新 API</li>
     *   <li>Android M 以下：使用已废弃的 {@link ConnectivityManager#getActiveNetworkInfo()} 旧 API</li>
     * </ul>
     *
     * @param context 上下文，为 null 时返回 false
     * @return true 如果网络可用
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                // 检查网络是否具备互联网能力（NET_CAPABILITY_INTERNET）
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            // 权限不足或系统服务异常时，保守返回 false
            return false;
        }
    }

    /**
     * 检查网络是否可用，不可用时显示 Toast 提示。
     *
     * <p>常用于网络请求前的预检查，避免在无网络时发起无效请求。
     *
     * @param context 上下文
     * @return true 如果网络可用；false 表示不可用（已自动显示 Toast）
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
     * 智能重试策略 —— 指数退避（Exponential Backoff）。
     *
     * <p>每次重试的延迟时间为 baseDelayMs × 2^(attempt-1)，
     * 即第1次延迟 baseDelayMs，第2次 2×baseDelayMs，第3次 4×baseDelayMs，以此类推。
     * 这种策略能有效避免在服务端压力较大时雪崩式重试。
     *
     * <p>重试终止条件：
     * <ul>
     *   <li>回调返回 true → 成功，立即返回 true</li>
     *   <li>达到最大重试次数 → 返回 false</li>
     *   <li>等待期间线程被中断 → 恢复中断标志并返回 false</li>
     * </ul>
     *
     * @param maxRetries  最大重试次数（含首次尝试）
     * @param baseDelayMs 基础延迟（毫秒），后续每次翻倍
     * @param callback    重试回调，返回 true 表示操作成功
     * @return true 如果在某次尝试中成功；false 如果所有尝试均失败
     */
    public static boolean retryWithBackoff(int maxRetries, long baseDelayMs, RetryCallback callback) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (callback.execute(attempt)) {
                    return true;
                }
            } catch (Exception e) {
                // 最后一次尝试失败后不再等待，直接返回
                if (attempt == maxRetries) {
                    return false;
                }
            }
            
            try {
                // 指数退避延迟：baseDelayMs * 2^(attempt-1)
                long delay = baseDelayMs * (long) Math.pow(2, attempt - 1);
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                // 恢复中断状态，遵循 Java 中断协议
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ============ 回调接口 ============

    /**
     * 重试回调接口，由调用方实现具体的重试逻辑。
     */
    public interface RetryCallback {
        /**
         * 执行重试逻辑。
         *
         * @param attempt 当前尝试次数（从1开始）
         * @return true 如果操作成功，不再需要重试
         * @throws Exception 操作过程中可能抛出的异常
         */
        boolean execute(int attempt) throws Exception;
    }
}

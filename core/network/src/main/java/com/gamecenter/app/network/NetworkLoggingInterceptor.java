package com.gamecenter.app.network;

import android.util.Log;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;

/**
 * 网络日志拦截器 (Phase 2.3)
 *
 * <p>统一 OkHttp 请求/响应的日志输出, 方便调试和性能分析.</p>
 *
 * <p>输出格式:
 * <pre>
 *   --> POST https://api.example.com/v1/chat
 *   Content-Type: application/json
 *   Content-Length: 256
 *   <-- 200 OK (412ms, 1024 bytes)
 * </pre>
 * </p>
 *
 * <p>使用方式: 在 OkHttpClientProvider 里 addInterceptor().</p>
 */
public final class NetworkLoggingInterceptor implements Interceptor {
    private static final String TAG = "NetLog";

    private final boolean logHeaders;
    private final boolean logBody;

    public NetworkLoggingInterceptor() {
        this(false, false);
    }

    public NetworkLoggingInterceptor(boolean logHeaders, boolean logBody) {
        this.logHeaders = logHeaders;
        this.logBody = logBody;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        long startNs = System.nanoTime();

        // 记录请求
        Log.d(TAG, String.format("--> %s %s",
                request.method(),
                request.url()));

        if (logHeaders) {
            for (String name : request.headers().names()) {
                Log.d(TAG, "    " + name + ": " + request.header(name));
            }
        }

        // 实际请求
        Response response = chain.proceed(request);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        long bodySize = response.body() != null ? response.body().contentLength() : -1;

        // 记录响应
        Log.d(TAG, String.format("<-- %d %s (%dms, %d bytes)",
                response.code(),
                response.message(),
                durationMs,
                bodySize));

        return response;
    }
}

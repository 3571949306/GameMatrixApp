package com.gamecenter.app.network;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttpClient 统一管理类
 * 提供带缓存的 HTTP 客户端和 WebSocket 客户端
 */
public final class OkHttpClientProvider {

    private static final int CACHE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int HTTP_CONNECT_TIMEOUT = 15;
    private static final int HTTP_READ_TIMEOUT = 30;
    private static final int HTTP_WRITE_TIMEOUT = 30;
    private static final int WS_CONNECT_TIMEOUT = 10;
    private static final int WS_READ_TIMEOUT = 0; // WebSocket 无读取超时
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private static volatile OkHttpClientProvider instance;
    private final OkHttpClient httpClient;
    private final OkHttpClient webSocketClient;

    private OkHttpClientProvider(Context context) {
        File cacheDir = new File(context.getCacheDir(), "http_cache");
        Cache cache = new Cache(cacheDir, CACHE_SIZE);

        httpClient = new OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(HTTP_WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(new RetryInterceptor(MAX_RETRIES, RETRY_DELAY_MS))
                .build();

        webSocketClient = new OkHttpClient.Builder()
                .connectTimeout(WS_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(WS_READ_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static OkHttpClientProvider getInstance(Context context) {
        if (instance == null) {
            synchronized (OkHttpClientProvider.class) {
                if (instance == null) {
                    instance = new OkHttpClientProvider(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public OkHttpClient getWebSocketClient() {
        return webSocketClient;
    }

    /**
     * 重试拦截器
     */
    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;
        private final long retryDelayMs;

        RetryInterceptor(int maxRetries, long retryDelayMs) {
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastException = null;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (response != null) {
                        response.close();
                    }
                    response = chain.proceed(request);
                    if (response.isSuccessful() || attempt == maxRetries) {
                        return response;
                    }
                    response.close();
                } catch (IOException e) {
                    lastException = e;
                    if (attempt == maxRetries) {
                        throw e;
                    }
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        throw lastException != null ? lastException : new IOException("Interrupted");
                    }
                }
            }

            if (lastException != null) {
                throw lastException;
            }
            return response;
        }
    }
}

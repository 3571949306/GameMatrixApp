package com.gamecenter.app.network;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

import okhttp3.Cache;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttpClient 提供者（单例），负责创建和管理应用中使用的 HTTP 和 WebSocket 客户端实例。
 *
 * <p>为不同通信场景提供定制化的 OkHttpClient：</p>
 * <ul>
 *   <li><b>httpClient</b>：用于普通 HTTP 请求，配置了磁盘缓存（50MB）、连接/读写超时、
 *       自动重试（最多 3 次，指数退避）和请求去重拦截器。</li>
 *   <li><b>webSocketClient</b>：用于 WebSocket 长连接，无读取超时（保持连接持久性），
 *       较短的连接超时（10 秒），启用连接失败自动重试。</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用双重检查锁定（DCL）实现懒加载单例，兼顾线程安全与性能</li>
 *   <li>instance 字段使用 volatile 修饰，保证多线程间的可见性</li>
 *   <li>WebSocket 客户端不配置缓存和去重拦截器，因为 WebSocket 是长连接协议，不需要这些特性</li>
 *   <li>重试拦截器采用线性递增延迟（attempt+1 倍基础延迟），避免在服务端压力较大时雪崩</li>
 * </ul>
 * </p>
 */
@Singleton
public final class OkHttpClientProvider {

    private static final String TAG = "OkHttpClientProvider";

    /** HTTP 缓存大小：50MB */
    private static final int CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    /** HTTP 连接超时：15 秒 */
    private static final int HTTP_CONNECT_TIMEOUT = 15;

    /** HTTP 读取超时：30 秒 */
    private static final int HTTP_READ_TIMEOUT = 30;

    /** HTTP 写入超时：30 秒 */
    private static final int HTTP_WRITE_TIMEOUT = 30;

    /** WebSocket 连接超时：10 秒 */
    private static final int WS_CONNECT_TIMEOUT = 10;

    /** WebSocket 读取超时：0（无超时，保持长连接） */
    private static final int WS_READ_TIMEOUT = 0; // WebSocket 无读取超时

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 重试基础延迟（毫秒），实际延迟为 retryDelayMs × (attempt+1) */
    private static final long RETRY_DELAY_MS = 1000;

    /** 单例实例，volatile 保证多线程可见性 */
    private static volatile OkHttpClientProvider instance;

    /** 普通 HTTP 请求客户端 */
    private final OkHttpClient httpClient;

    /** WebSocket 长连接客户端 */
    private final OkHttpClient webSocketClient;

    /** 请求去重拦截器，用于防止短时间内重复发送相同请求 */
    private final RequestDeduplicationInterceptor deduplicationInterceptor;

    /**
     * 构造 OkHttpClientProvider，初始化 HTTP 和 WebSocket 客户端。
     *
     * <p>HTTP 客户端配置了缓存、超时、重试和去重拦截器；
     * WebSocket 客户端仅配置超时和连接失败重试。</p>
     *
     * <p>标注 {@code @Inject} 使 Hilt 可直接通过构造函数注入创建实例，
     * 配合类级 {@code @Singleton} 注解确保全局唯一。</p>
     *
     * @param context Android 上下文，用于创建 HTTP 缓存目录
     */
    @Inject
    public OkHttpClientProvider(@ApplicationContext Context context) {
        File cacheDir = new File(context.getCacheDir(), "http_cache");
        Cache cache = new Cache(cacheDir, CACHE_SIZE);

        deduplicationInterceptor = new RequestDeduplicationInterceptor();

        // 优化线程池配置：限制 OkHttp 并发数，避免线程爆炸
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(8);           // 全局最大并发请求数（默认64→8）
        dispatcher.setMaxRequestsPerHost(4);    // 单主机最大并发请求数（默认5→4）

        // Phase 2.3: 集中所有常用 interceptor
        // - HeaderInterceptor: 统一 User-Agent / X-Client 等公共 header
        // - NetworkLoggingInterceptor: 统一日志 (Debug build 用 verbose, Release 用普通)
        // - RetryInterceptor: 网络抖动重试 (线性退避)
        // - RequestDeduplicationInterceptor: 短时间重复请求去重
        HeaderInterceptor headers = new HeaderInterceptor.Builder()
                .userAgent("GameMatrixApp/1.4.0 (Android)")
                .addStaticHeader("X-Client-Platform", "android")
                .addStaticHeader("X-Client-Version", "1.4.0")
                .build();

        NetworkLoggingInterceptor logging = new NetworkLoggingInterceptor(false, false);

        httpClient = new OkHttpClient.Builder()
                .cache(cache)
                .dispatcher(dispatcher)  // 使用优化后的线程池配置
                .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(HTTP_WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(headers)
                .addInterceptor(logging)
                .addInterceptor(new RetryInterceptor(MAX_RETRIES, RETRY_DELAY_MS))
                .addInterceptor(deduplicationInterceptor)
                .build();

        webSocketClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)  // 使用优化后的线程池配置
                .connectTimeout(WS_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(WS_READ_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(headers)
                .addInterceptor(logging)
                .build();

        instance = this;
    }

    /**
     * 获取 OkHttpClientProvider 单例实例（双重检查锁定）。
     *
     * <p>使用 DCL（Double-Checked Locking）模式，避免每次调用都加锁，
     * 同时保证线程安全。使用 Application Context 防止内存泄漏。</p>
     *
     * @param context Android 上下文，仅首次创建时使用
     * @return OkHttpClientProvider 单例
     * @deprecated 推荐通过 Hilt 依赖注入获取实例，避免手动管理单例
     */
    @Deprecated
    public static OkHttpClientProvider getInstance(Context context) {
        if (instance == null) {
            if (context == null) {
                return null;
            }
            synchronized (OkHttpClientProvider.class) {
                if (instance == null) {
                    instance = new OkHttpClientProvider(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 预加载方法（用于 App Startup）
     * 仅在应用启动时预加载，实际连接在首次使用时创建
     */
    public static void preload(Context context) {
        // 延迟初始化，不阻塞启动
        new Thread(() -> {
            try {
                Thread.sleep(500); // 延迟 500ms，让 UI 先完成加载
                getInstance(context);
            } catch (Exception e) {
                // 忽略预加载错误，会在实际使用时重新初始化
                Log.w(TAG, "预加载错误，会在实际使用时重新初始化", e);
            }
        }).start();
    }

    /**
     * 获取普通 HTTP 请求客户端。
     *
     * <p>配置了缓存、超时、重试和去重拦截器，适用于常规 REST API 调用。</p>
     *
     * @return OkHttpClient 实例
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 获取 WebSocket 长连接客户端。
     *
     * <p>无读取超时，适用于需要保持持久连接的 WebSocket 通信场景。</p>
     *
     * @return OkHttpClient 实例
     */
    public OkHttpClient getWebSocketClient() {
        return webSocketClient;
    }

    /**
     * 获取请求去重拦截器（用于动态控制）
     */
    public RequestDeduplicationInterceptor getDeduplicationInterceptor() {
        return deduplicationInterceptor;
    }

    /**
     * 重试拦截器。
     *
     * <p>在请求失败（IOException）或响应码非成功时自动重试，
     * 重试延迟采用线性递增策略（基础延迟 × 重试次数），避免在服务端压力大时造成请求雪崩。</p>
     *
     * <p>重试逻辑：
     * <ul>
     *   <li>最多重试 {@code maxRetries} 次（加上首次请求共 maxRetries+1 次尝试）</li>
     *   <li>响应成功（2xx）或已达到最大重试次数时返回当前响应</li>
     *   <li>发生 IOException 时记录异常，达到最大重试次数后抛出最后一次异常</li>
     *   <li>每次重试前关闭上一次的响应体，防止连接泄漏</li>
     *   <li>重试延迟 = retryDelayMs × (attempt + 1)，实现线性退避</li>
     * </ul>
     * </p>
     */
    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;
        private final long retryDelayMs;

        /**
         * 创建重试拦截器。
         *
         * @param maxRetries   最大重试次数
         * @param retryDelayMs 基础重试延迟（毫秒）
         */
        RetryInterceptor(int maxRetries, long retryDelayMs) {
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        /**
         * 执行带重试逻辑的拦截处理。
         *
         * @param chain OkHttp 拦截链
         * @return HTTP 响应
         * @throws IOException 重试耗尽后抛出最后一次 IO 异常
         */
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastException = null;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    // 关闭上一次响应体，防止连接泄漏
                    if (response != null) {
                        response.close();
                    }
                    response = chain.proceed(request);
                    // 响应成功或已达到最大重试次数，直接返回
                    if (response.isSuccessful() || attempt == maxRetries) {
                        return response;
                    }
                    // 响应非成功，关闭响应体后进入重试
                    response.close();
                } catch (IOException e) {
                    lastException = e;
                    // 达到最大重试次数，抛出异常
                    if (attempt == maxRetries) {
                        throw e;
                    }
                }

                // 未达到最大重试次数时，等待递增延迟后重试
                if (attempt < maxRetries) {
                    try {
                        // 线性退避：延迟 = retryDelayMs × (attempt + 1)
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        throw lastException != null ? lastException : new IOException("Interrupted");
                    }
                }
            }

            // 理论上不会到达此处，作为安全兜底
            if (lastException != null) {
                throw lastException;
            }
            return response;
        }
    }
}

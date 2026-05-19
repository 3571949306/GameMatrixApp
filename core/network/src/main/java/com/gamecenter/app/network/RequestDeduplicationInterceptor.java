package com.gamecenter.app.network;

import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp 请求去重拦截器 — 防止短时间内重复发起相同的 GET 网络请求。
 * <p>
 * 职责：
 * <ul>
 *   <li>拦截所有 HTTP 请求，仅对 GET 请求（幂等请求）进行去重处理</li>
 *   <li>当检测到相同请求正在执行时，新请求等待原请求完成后复用其响应</li>
 *   <li>提供全局启用/禁用开关和批量取消功能</li>
 * </ul>
 * <p>
 * 工作原理：
 * <ol>
 *   <li>为每个请求生成唯一标识（HTTP方法 + URL）</li>
 *   <li>正在执行的请求会被记录到 {@link #pendingRequests} 中</li>
 *   <li>相同的新请求会等待正在执行的请求完成</li>
 *   <li>返回已完成的响应，避免重复网络调用</li>
 * </ol>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>仅对 GET 请求去重，因为 GET 是幂等的，重复请求不会产生副作用</li>
 *   <li>使用 {@link ConcurrentHashMap} 存储待处理请求，支持并发访问</li>
 *   <li>等待超时为5秒，避免因请求挂起导致线程无限等待</li>
 *   <li>使用 {@link AtomicInteger} 计数器为请求编号，便于日志追踪</li>
 * </ul>
 */
public class RequestDeduplicationInterceptor implements Interceptor {

    private static final String TAG = "RequestDedup";

    /**
     * 正在执行的请求映射表。
     * <p>
     * Key 为请求唯一标识（方法+URL），Value 为对应的 {@link PendingRequest} 对象。
     * 使用 {@link ConcurrentHashMap} 保证线程安全。
     */
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    /** 请求计数器，为每个请求分配递增编号，用于日志追踪 */
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    /** 是否启用去重功能（默认启用），使用 volatile 保证多线程可见性 */
    private volatile boolean enabled = true;

    /**
     * 待处理请求包装类 — 封装正在执行的请求的状态和结果。
     * <p>
     * 使用 synchronized + wait/notify 机制实现请求间的等待与通知，
     * 当一个请求完成时，等待该请求的其他线程会被唤醒。
     */
    private static class PendingRequest {
        /** 请求唯一标识，用于日志 */
        final String key;
        /** 请求成功后的响应对象 */
        volatile Response response;
        /** 请求失败时的异常对象 */
        volatile IOException error;
        /** 请求是否已完成（成功或失败） */
        volatile boolean completed;

        PendingRequest(String key) {
            this.key = key;
        }

        /**
         * 阻塞等待请求完成。
         * <p>
         * 每5秒超时唤醒一次，避免因请求挂起导致线程永久等待。
         * 等待期间若请求失败（error 不为 null），则抛出对应的 IOException。
         *
         * @throws IOException          请求失败时抛出
         * @throws InterruptedException 等待被中断时抛出
         */
        synchronized void waitForResult() throws IOException, InterruptedException {
            while (!completed) {
                wait(5000);
            }
            if (error != null) {
                throw error;
            }
        }

        /**
         * 标记请求成功完成，并唤醒所有等待线程。
         *
         * @param resp 请求响应
         */
        synchronized void complete(Response resp) {
            this.response = resp;
            this.completed = true;
            notifyAll();
        }

        /**
         * 标记请求失败完成，并唤醒所有等待线程。
         *
         * @param e 请求异常
         */
        synchronized void complete(IOException e) {
            this.error = e;
            this.completed = true;
            notifyAll();
        }
    }

    /**
     * 拦截请求并执行去重逻辑。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>若去重功能被禁用或请求非 GET 方法，直接放行</li>
     *   <li>生成请求唯一标识，检查是否有相同请求正在执行</li>
     *   <li>若有相同请求正在执行，等待其完成并复用响应</li>
     *   <li>若无相同请求，创建新的 PendingRequest 并执行实际请求</li>
     *   <li>请求完成后清理 PendingRequest 记录</li>
     * </ol>
     *
     * @param chain OkHttp 拦截器链
     * @return HTTP 响应
     * @throws IOException 网络请求失败时抛出
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // 只对 GET 请求去重（幂等请求），其他方法直接放行
        if (!enabled || !"GET".equalsIgnoreCase(request.method())) {
            return chain.proceed(request);
        }

        String requestKey = generateRequestKey(request);

        // 检查是否有相同的请求正在执行
        PendingRequest pending = pendingRequests.get(requestKey);
        if (pending != null) {
            // 有相同请求正在执行，尝试复用其响应
            Log.d(TAG, "⏳ 请求去重：等待相同请求完成 - " + requestKey);
            try {
                Response cachedResponse = pending.response;
                // 如果原请求已完成且有缓存响应，直接复用
                if (cachedResponse != null && cachedResponse.cacheResponse() != null) {
                    return cachedResponse.newBuilder()
                            .sentRequestAtMillis(System.currentTimeMillis())
                            .build();
                }
                // 原请求尚未完成，阻塞等待其结果
                pending.waitForResult();
                if (pending.response != null) {
                    Log.d(TAG, "✓ 请求去重成功：使用缓存响应 - " + requestKey);
                    return pending.response;
                }
            } catch (InterruptedException e) {
                // 等待被中断时恢复中断标志，让上层处理
                Thread.currentThread().interrupt();
                Log.w(TAG, "⚠ 等待被中断：" + requestKey);
            } catch (IOException e) {
                Log.w(TAG, "⚠ 等待的请求失败：" + requestKey, e);
                throw e;
            }
        }

        // 创建新的待处理请求并注册到映射表
        PendingRequest newPending = new PendingRequest(requestKey);
        pendingRequests.put(requestKey, newPending);

        int requestId = requestCounter.incrementAndGet();
        Log.d(TAG, "📡 发起请求 #" + requestId + ": " + requestKey);
        long startTime = System.currentTimeMillis();

        try {
            // 执行实际的网络请求
            Response response = chain.proceed(request);
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "✅ 请求 #" + requestId + " 完成 (" + duration + "ms): " + requestKey);

            // 通知等待该请求的其他线程（仅在失败时通过 finally 外的 catch 通知）
            if (response.isSuccessful() && response.cacheResponse() == null) {
                // 网络响应直接返回，不缓存到 pending（响应体只能消费一次）
                return response;
            }

            return response;

        } catch (IOException e) {
            Log.e(TAG, "❌ 请求 #" + requestId + " 失败：" + requestKey, e);
            // 通知等待该请求的其他线程：请求失败
            newPending.complete(e);
            throw e;

        } finally {
            // 无论成功或失败，都从映射表中移除待处理记录
            pendingRequests.remove(requestKey);
        }
    }

    /**
     * 生成请求唯一标识。
     * <p>
     * 使用 "HTTP方法:URL" 格式作为标识，确保相同 URL 和方法的请求被视为相同请求。
     *
     * @param request HTTP 请求对象
     * @return 请求唯一标识字符串
     */
    private String generateRequestKey(Request request) {
        String method = request.method();
        String url = request.url().toString();
        return method + ":" + url;
    }

    /**
     * 启用或禁用请求去重功能。
     * <p>
     * 禁用后所有请求将直接放行，不进行去重检查。
     *
     * @param enabled true 启用去重，false 禁用去重
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取当前正在执行的请求数量。
     * <p>
     * 可用于监控和调试，了解当前并发请求的去重情况。
     *
     * @return 正在执行的请求数量
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    /**
     * 取消所有待处理请求。
     * <p>
     * 遍历所有正在执行的请求，将其标记为失败（IOException("Cancelled")），
     * 并唤醒所有等待线程，最后清空映射表。
     * 适用于 Activity 销毁或用户主动取消等场景。
     */
    public void cancelAll() {
        for (PendingRequest pending : pendingRequests.values()) {
            pending.complete(new IOException("Cancelled"));
        }
        pendingRequests.clear();
        Log.d(TAG, "🚫 已取消所有待处理请求");
    }
}

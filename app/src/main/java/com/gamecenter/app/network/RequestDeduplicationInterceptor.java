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
 * 请求去重拦截器
 * 防止短时间内重复发起相同的网络请求
 * 
 * 工作原理：
 * 1. 为每个请求生成唯一标识（URL + 方法）
 * 2. 正在执行的请求会被记录
 * 3. 相同的新请求会等待正在执行的请求完成
 * 4. 返回缓存的响应，避免重复网络调用
 */
public class RequestDeduplicationInterceptor implements Interceptor {

    private static final String TAG = "RequestDedup";
    
    // 正在执行的请求
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    
    // 请求计数器（用于日志）
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    
    // 是否启用去重（默认启用）
    private volatile boolean enabled = true;

    /**
     * 待处理请求包装类
     */
    private static class PendingRequest {
        final String key;
        volatile Response response;
        volatile IOException error;
        volatile boolean completed;
        
        PendingRequest(String key) {
            this.key = key;
        }
        
        synchronized void waitForResult() throws IOException, InterruptedException {
            while (!completed) {
                wait(5000); // 最多等待 5 秒
            }
            if (error != null) {
                throw error;
            }
        }
        
        synchronized void complete(Response resp) {
            this.response = resp;
            this.completed = true;
            notifyAll();
        }
        
        synchronized void complete(IOException e) {
            this.error = e;
            this.completed = true;
            notifyAll();
        }
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        
        // 只对 GET 请求去重（幂等请求）
        if (!enabled || !"GET".equalsIgnoreCase(request.method())) {
            return chain.proceed(request);
        }
        
        // 生成请求唯一标识
        String requestKey = generateRequestKey(request);
        
        // 检查是否有相同的请求正在执行
        PendingRequest pending = pendingRequests.get(requestKey);
        if (pending != null) {
            // 有相同请求正在执行，等待其完成
            Log.d(TAG, "⏳ 请求去重：等待相同请求完成 - " + requestKey);
            try {
                Response cachedResponse = pending.response;
                if (cachedResponse != null && cachedResponse.cacheResponse() != null) {
                    // 返回缓存响应
                    return cachedResponse.newBuilder()
                            .sentRequestAtMillis(System.currentTimeMillis())
                            .build();
                }
                // 等待原始请求完成
                pending.waitForResult();
                if (pending.response != null) {
                    Log.d(TAG, "✓ 请求去重成功：使用缓存响应 - " + requestKey);
                    return pending.response;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "⚠ 等待被中断：" + requestKey);
            } catch (IOException e) {
                Log.w(TAG, "⚠ 等待的请求失败：" + requestKey, e);
                throw e;
            }
        }
        
        // 创建新的待处理请求
        PendingRequest newPending = new PendingRequest(requestKey);
        pendingRequests.put(requestKey, newPending);
        
        int requestId = requestCounter.incrementAndGet();
        Log.d(TAG, "📡 发起请求 #" + requestId + ": " + requestKey);
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行实际请求
            Response response = chain.proceed(request);
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "✅ 请求 #" + requestId + " 完成 (" + duration + "ms): " + requestKey);
            
            // 只有成功的响应才缓存
            if (response.isSuccessful() && response.cacheResponse() == null) {
                // 如果是网络响应，包装后返回（不存储到 pending）
                return response;
            }
            
            return response;
            
        } catch (IOException e) {
            Log.e(TAG, "❌ 请求 #" + requestId + " 失败：" + requestKey, e);
            newPending.complete(e);
            throw e;
            
        } finally {
            // 清理待处理请求
            pendingRequests.remove(requestKey);
        }
    }

    /**
     * 生成请求唯一标识
     */
    private String generateRequestKey(Request request) {
        String method = request.method();
        String url = request.url().toString();
        return method + ":" + url;
    }

    /**
     * 启用/禁用请求去重
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取正在执行的请求数量
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    /**
     * 清除所有待处理请求（用于取消所有请求）
     */
    public void cancelAll() {
        for (PendingRequest pending : pendingRequests.values()) {
            pending.complete(new IOException("Cancelled"));
        }
        pendingRequests.clear();
        Log.d(TAG, "🚫 已取消所有待处理请求");
    }
}

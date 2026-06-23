package com.gamecenter.app.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 通用 HTTP header 注入拦截器 (Phase 2.3)
 *
 * <p>用于统一加 User-Agent / Accept-Language / X-Client-Version 等公共 header,
 * 避免每个调用方重复设置.</p>
 *
 * <p>设计原则: 不修改请求 method / body / URL, 只追加 header.</p>
 *
 * <p>用法:
 * <pre>
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new HeaderInterceptor.Builder()
 *           .userAgent("GameMatrix/1.4.0")
 *           .addStaticHeader("X-Client", "android")
 *           .build())
 *       .build();
 * </pre>
 * </p>
 */
public final class HeaderInterceptor implements Interceptor {

    private final String userAgent;
    private final java.util.Map<String, String> staticHeaders;

    private HeaderInterceptor(Builder b) {
        this.userAgent = b.userAgent;
        this.staticHeaders = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(b.staticHeaders));
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder b = original.newBuilder();
        if (userAgent != null && original.header("User-Agent") == null) {
            b.header("User-Agent", userAgent);
        }
        for (java.util.Map.Entry<String, String> e : staticHeaders.entrySet()) {
            if (original.header(e.getKey()) == null) {
                b.header(e.getKey(), e.getValue());
            }
        }
        return chain.proceed(b.build());
    }

    public static final class Builder {
        @Nullable private String userAgent;
        @NonNull private final java.util.Map<String, String> staticHeaders = new java.util.HashMap<>();

        public Builder userAgent(@Nullable String ua) {
            this.userAgent = ua;
            return this;
        }

        public Builder addStaticHeader(@NonNull String name, @NonNull String value) {
            this.staticHeaders.put(name, value);
            return this;
        }

        public HeaderInterceptor build() {
            return new HeaderInterceptor(this);
        }
    }
}

package com.gamecenter.app.initializers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.gamecenter.app.network.OkHttpClientProvider;
import com.gamecenter.app.utils.NetworkErrorHandler;

/**
 * 网络组件初始化器
 * 使用 App Startup 在应用启动时自动初始化网络相关组件
 */
public class NetworkInitializer implements Initializer<Void> {

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        // 初始化 OkHttpClient（单例模式，按需加载）
        // 注意：这里只是预加载，实际连接在首次使用时创建
        OkHttpClientProvider.preload(context);
        
        return null;
    }

    @NonNull
    @Override
    public java.util.List<Class<? extends Initializer<?>>> dependencies() {
        // 不依赖其他初始化器
        return java.util.Collections.emptyList();
    }
}

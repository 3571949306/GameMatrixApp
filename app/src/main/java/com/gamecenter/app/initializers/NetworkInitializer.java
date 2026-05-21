package com.gamecenter.app.initializers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.gamecenter.app.network.OkHttpClientProvider;
import com.gamecenter.app.utils.NetworkErrorHandler;

/**
 * 网络组件初始化器。
 *
 * <p>打个比方：这个类就像应用启动时的"后勤保障部门"，在应用正式开张之前，
 * 先把网络通信需要的"基础设施"（OkHttpClient）准备好。这样当用户真正开始联机游戏时，
 * 网络工具已经就绪，不需要临时搭建，体验更流畅。</p>
 *
 * <p>在网络模块中的角色：这是网络模块的"启动器"，基于 Jetpack App Startup 机制，
 * 在应用启动阶段自动完成网络相关组件的初始化工作，
 * 避免在 ContentProvider 中进行耗时操作导致启动延迟。</p>
 * <p>
 * 职责：
 * <ul>
 *   <li>预加载 {@link OkHttpClientProvider} 中的 OkHttpClient 单例，使首次网络请求时无需再等待客户端初始化</li>
 *   <li>通过声明无依赖（{@link #dependencies()} 返回空列表），确保网络组件可在其他初始化器之前尽早就绪</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>泛型参数为 {@code Void}，因为本初始化器不需要向下游初始化器暴露任何产出数据</li>
 *   <li>采用 App Startup 而非手动在 Application.onCreate() 中初始化，
 *       可实现懒加载和依赖排序，同时避免各库各自声明 ContentProvider 带来的启动开销。
 *       就像用"统一调度中心"代替"各自为政"，更高效有序。</li>
 * </ul>
 */
public class NetworkInitializer implements Initializer<Void> {

    /**
     * 在应用启动时由 App Startup 框架调用，执行网络组件的初始化。
     *
     * <p>打个比方：就像餐厅开门营业前，厨师先把灶台预热好（预加载OkHttpClient），
     * 这样第一桌客人点菜时就能立刻开炒，不用等灶台加热。</p>
     *
     * <p>调用 {@link OkHttpClientProvider#preload(Context)} 预加载 OkHttpClient 单例。
     * 此处仅为预加载阶段，OkHttpClient 的实际网络连接会在首次请求时才建立，
     * 不会在此方法中发起任何网络 I/O。</p>
     *
     * @param context 应用上下文，由 App Startup 框架自动注入
     * @return 固定返回 null，因为本初始化器无需产出数据供下游使用
     */
    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        OkHttpClientProvider.preload(context);

        return null;
    }

    /**
     * 声明本初始化器所依赖的其他初始化器。
     *
     * <p>返回空列表表示无前置依赖，网络组件可以在App Startup调度中尽早执行。
     * 就像排队办事，网络组件不需要等别人先办完，可以直接排到最前面。</p>
     *
     * @return 空列表，表示无依赖
     */
    @NonNull
    @Override
    public java.util.List<Class<? extends Initializer<?>>> dependencies() {
        return java.util.Collections.emptyList();
    }
}

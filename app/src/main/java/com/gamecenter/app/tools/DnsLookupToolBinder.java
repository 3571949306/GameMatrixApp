package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * DNS 查找工具绑定器。
 * <p>
 * 职责：作为 DNS 查找功能与 UI 之间的桥梁，将绑定请求委托给
 * {@link AdvancedToolBinders#bindDnsLookup} 方法执行实际的视图绑定逻辑。
 * </p>
 * <p>
 * 设计决策：采用委托模式，将复杂绑定逻辑集中到 AdvancedToolBinders 中，
 * 本类仅作为 ToolBinder 接口的轻量级实现，便于工具的注册与管理。
 * </p>
 */
public class DnsLookupToolBinder implements ToolBinder {

    /**
     * 将 DNS 查找工具的 UI 逻辑绑定到指定的内容视图上。
     *
     * @param context     应用上下文，用于获取系统服务和资源
     * @param contentView 工具的根视图容器，包含 DNS 查找相关的 UI 控件
     * @param executor    线程池执行器，用于执行耗时的网络 DNS 查询操作，避免阻塞主线程
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindDnsLookup(context, contentView, executor);
    }
}

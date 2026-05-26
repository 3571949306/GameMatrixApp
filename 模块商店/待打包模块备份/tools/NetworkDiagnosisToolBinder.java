package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 网络诊断工具绑定器。
 * <p>
 * 这是工具箱模块中"网络诊断"功能的入口类。它的作用就像一个"接线员"——
 * 当用户打开网络诊断工具页面时，它负责把页面的请求转接给真正干活的
 * AdvancedToolBinders 去处理。
 * </p>
 * <p>
 * 设计决策：采用委托模式（就像经理把任务分配给专业员工），
 * 将复杂的网络诊断逻辑（如 Ping、Traceroute、DNS 解析检测等）
 * 集中到 AdvancedToolBinders 中，本类仅作为 ToolBinder 接口的轻量级实现。
 * </p>
 */
public class NetworkDiagnosisToolBinder implements ToolBinder {

    /**
     * 将网络诊断工具的 UI 逻辑绑定到指定的内容视图上。
     * <p>
     * 这个方法不做实际工作，只是把任务"转交"给 AdvancedToolBinders.bindNetworkDiagnosis 处理。
     * </p>
     *
     * @param context     应用上下文，用于获取系统服务和 UI 资源
     * @param contentView 工具的根视图容器，包含诊断按钮、结果展示区域等 UI 控件
     * @param executor    线程池执行器，用于执行耗时的网络诊断操作（Ping、DNS 查询等）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        // 委托给 AdvancedToolBinders 执行实际的绑定逻辑
        AdvancedToolBinders.bindNetworkDiagnosis(context, contentView, executor);
    }
}

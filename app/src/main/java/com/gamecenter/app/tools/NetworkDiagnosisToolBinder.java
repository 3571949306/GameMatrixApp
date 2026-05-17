package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 网络诊断工具绑定器。
 * <p>
 * 职责：作为网络诊断功能与 UI 之间的桥梁，将绑定请求委托给
 * {@link AdvancedToolBinders#bindNetworkDiagnosis} 方法执行实际的视图绑定逻辑。
 * </p>
 * <p>
 * 设计决策：采用委托模式，将复杂的网络诊断逻辑（如 Ping、Traceroute、
 * DNS 解析检测等）集中到 AdvancedToolBinders 中，本类仅作为
 * ToolBinder 接口的轻量级实现。
 * </p>
 */
public class NetworkDiagnosisToolBinder implements ToolBinder {

    /**
     * 将网络诊断工具的 UI 逻辑绑定到指定的内容视图上。
     *
     * @param context     应用上下文，用于获取系统服务和 UI 资源
     * @param contentView 工具的根视图容器，包含诊断按钮、结果展示区域等 UI 控件
     * @param executor    线程池执行器，用于执行耗时的网络诊断操作（Ping、DNS 查询等）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindNetworkDiagnosis(context, contentView, executor);
    }
}

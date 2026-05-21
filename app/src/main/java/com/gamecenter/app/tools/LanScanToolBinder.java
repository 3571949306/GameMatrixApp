package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 局域网扫描工具绑定器。
 * <p>
 * 这是工具箱模块中"局域网扫描"功能的入口类。它的作用就像一个"接线员"——
 * 当用户打开局域网扫描工具页面时，它负责把页面的请求转接给真正干活的
 * AdvancedToolBinders 去处理。
 * </p>
 * <p>
 * 设计决策：采用委托模式（就像经理把任务分配给专业员工），
 * 将复杂的局域网扫描逻辑（如并发 Ping、ARP 表解析等）
 * 集中到 AdvancedToolBinders 中，本类仅作为 ToolBinder 接口的轻量级实现。
 * 这样做的好处是：代码更整洁，职责更清晰。
 * </p>
 */
public class LanScanToolBinder implements ToolBinder {

    /**
     * 将局域网扫描工具的 UI 逻辑绑定到指定的内容视图上。
     * <p>
     * 这个方法不做实际工作，只是把任务"转交"给 AdvancedToolBinders.bindLanScan 处理。
     * </p>
     *
     * @param context     应用上下文，用于获取网络信息和 UI 资源
     * @param contentView 工具的根视图容器，包含扫描按钮、进度条、设备列表等 UI 控件
     * @param executor    线程池执行器，用于并发执行局域网内多设备的 Ping 探测
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        // 委托给 AdvancedToolBinders 执行实际的绑定逻辑
        AdvancedToolBinders.bindLanScan(context, contentView, executor);
    }
}

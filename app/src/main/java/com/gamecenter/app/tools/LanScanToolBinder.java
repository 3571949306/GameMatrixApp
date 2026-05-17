package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 局域网扫描工具绑定器。
 * <p>
 * 职责：作为局域网扫描功能与 UI 之间的桥梁，将绑定请求委托给
 * {@link AdvancedToolBinders#bindLanScan} 方法执行实际的视图绑定逻辑。
 * </p>
 * <p>
 * 设计决策：采用委托模式，将复杂的局域网扫描逻辑（如并发 Ping、
 * ARP 表解析等）集中到 AdvancedToolBinders 中，本类仅作为
 * ToolBinder 接口的轻量级实现。
 * </p>
 */
public class LanScanToolBinder implements ToolBinder {

    /**
     * 将局域网扫描工具的 UI 逻辑绑定到指定的内容视图上。
     *
     * @param context     应用上下文，用于获取网络信息和 UI 资源
     * @param contentView 工具的根视图容器，包含扫描按钮、进度条、设备列表等 UI 控件
     * @param executor    线程池执行器，用于并发执行局域网内多设备的 Ping 探测
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindLanScan(context, contentView, executor);
    }
}

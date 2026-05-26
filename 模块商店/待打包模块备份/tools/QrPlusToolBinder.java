package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 二维码增强工具绑定器。
 * <p>
 * 这是工具箱模块中"二维码增强"功能的入口类。相比基础的二维码生成工具（QrToolBinder），
 * 增强版提供了更多功能（如扫描识别、历史记录等）。
 * </p>
 * <p>
 * 该工具的完整绑定逻辑委托给 AdvancedToolBinders.bindQrPlus 实现，
 * 本类仅作为 ToolBinder 接口的薄包装（就像一个门牌，指引用户找到真正的办公室），
 * 保持工具绑定框架的一致性。第三个参数传 null 表示无额外的配置选项。
 * </p>
 */
public class QrPlusToolBinder implements ToolBinder {

    /**
     * 绑定二维码增强工具的视图和逻辑。
     * <p>
     * 这个方法不做实际工作，只是把任务"转交"给 AdvancedToolBinders.bindQrPlus 处理。
     * 第三个参数传 null 表示没有额外的配置选项。
     * </p>
     *
     * @param context     上下文环境，用于获取系统服务和资源
     * @param contentView 工具页面的根视图，包含需要绑定的 UI 控件
     * @param executor    线程池执行器（本工具未使用，因绑定逻辑由 AdvancedToolBinders 内部管理）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        // 委托给 AdvancedToolBinders 执行实际的绑定逻辑，null 表示无额外配置
        AdvancedToolBinders.bindQrPlus(context, contentView, null);
    }
}

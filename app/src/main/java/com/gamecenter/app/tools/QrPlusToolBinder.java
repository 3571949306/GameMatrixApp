package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 二维码增强工具绑定器。
 * <p>
 * 负责将"二维码增强"工具的 UI 视图与业务逻辑进行绑定。
 * 该工具的完整绑定逻辑委托给 {@link AdvancedToolBinders#bindQrPlus} 实现，
 * 本类仅作为 {@link ToolBinder} 接口的薄包装，保持工具绑定框架的一致性。
 * 第三个参数传 {@code null} 表示无额外的配置选项。
 * </p>
 */
public class QrPlusToolBinder implements ToolBinder {

    /**
     * 绑定二维码增强工具的视图和逻辑。
     *
     * @param context     上下文环境，用于获取系统服务和资源
     * @param contentView 工具页面的根视图，包含需要绑定的 UI 控件
     * @param executor    线程池执行器（本工具未使用，因绑定逻辑由 AdvancedToolBinders 内部管理）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindQrPlus(context, contentView, null);
    }
}

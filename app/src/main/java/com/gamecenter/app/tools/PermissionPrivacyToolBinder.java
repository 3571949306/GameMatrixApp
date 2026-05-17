package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 权限与隐私工具绑定器。
 * <p>
 * 负责将"权限与隐私"工具的 UI 视图与实际业务逻辑进行绑定。
 * 该工具的完整绑定逻辑委托给 {@link AdvancedToolBinders#bindPermissionPrivacy} 实现，
 * 本类仅作为 {@link ToolBinder} 接口的薄包装，保持工具绑定框架的一致性。
 * </p>
 */
public class PermissionPrivacyToolBinder implements ToolBinder {

    /**
     * 绑定权限与隐私工具的视图和逻辑。
     *
     * @param context     上下文环境，用于获取系统服务和资源
     * @param contentView 工具页面的根视图，包含需要绑定的 UI 控件
     * @param executor    线程池执行器（本工具未使用，因绑定逻辑无需异步操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindPermissionPrivacy(context, contentView);
    }
}

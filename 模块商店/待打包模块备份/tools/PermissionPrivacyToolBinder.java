package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 权限与隐私工具绑定器。
 * <p>
 * 这是工具箱模块中"权限与隐私"功能的入口类。它负责将"权限与隐私"工具的
 * UI 视图与实际业务逻辑进行绑定。你可以把它理解为"权限检查员"——
 * 帮用户查看手机上各个应用获取了哪些权限，以及隐私安全状态。
 * </p>
 * <p>
 * 该工具的完整绑定逻辑委托给 AdvancedToolBinders.bindPermissionPrivacy 实现，
 * 本类仅作为 ToolBinder 接口的薄包装（就像一个门牌，指引用户找到真正的办公室），
 * 保持工具绑定框架的一致性。
 * </p>
 */
public class PermissionPrivacyToolBinder implements ToolBinder {

    /**
     * 绑定权限与隐私工具的视图和逻辑。
     * <p>
     * 这个方法不做实际工作，只是把任务"转交"给 AdvancedToolBinders.bindPermissionPrivacy 处理。
     * </p>
     *
     * @param context     上下文环境，用于获取系统服务和资源
     * @param contentView 工具页面的根视图，包含需要绑定的 UI 控件
     * @param executor    线程池执行器（本工具未使用，因绑定逻辑无需异步操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        // 委托给 AdvancedToolBinders 执行实际的绑定逻辑
        AdvancedToolBinders.bindPermissionPrivacy(context, contentView);
    }
}

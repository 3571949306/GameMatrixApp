package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 诊断报告工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 采用委托模式，将实际的 UI 绑定逻辑委托给 {@link AdvancedToolBinders#bindDiagnosticReport} 方法。
 * <p>
 * 诊断报告工具提供报告生成、复制和分享功能，具体实现位于
 * {@link AdvancedToolBinders} 中，本类仅作为 ToolBinder 接口的适配器，
 * 使诊断报告工具可以与其他工具统一管理。
 */
public class DiagnosticReportToolBinder implements ToolBinder {

    /**
     * 绑定诊断报告工具的 UI 交互。
     * <p>
     * 委托给 {@link AdvancedToolBinders#bindDiagnosticReport}，
     * 提供诊断报告的生成、复制到剪贴板和分享功能。
     *
     * @param context     上下文，用于获取系统服务和启动分享 Intent
     * @param contentView 工具页面的根视图
     * @param executor    线程池，用于在后台执行报告生成
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindDiagnosticReport(context, contentView, executor);
    }
}

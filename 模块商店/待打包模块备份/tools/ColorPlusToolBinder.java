package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 颜色增强工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 采用委托模式，将实际的 UI 绑定逻辑委托给 {@link AdvancedToolBinders#bindColorPlus} 方法。
 * <p>
 * 颜色增强工具提供 WCAG 对比度计算和图片取色功能，具体实现位于
 * {@link AdvancedToolBinders} 中，本类仅作为 ToolBinder 接口的适配器，
 * 使颜色增强工具可以与其他工具统一管理。
 * <p>
 * 注意：pickImageListener 参数传入 null，表示此绑定器不直接处理图片选择，
 * 图片选择逻辑需要由调用方通过 {@link AdvancedToolBinders#handleColorImageResult} 单独处理。
 */
public class ColorPlusToolBinder implements ToolBinder {

    /**
     * 绑定颜色增强工具的 UI 交互。
     * <p>
     * 委托给 {@link AdvancedToolBinders#bindColorPlus}，第三个参数（pickImageListener）
     * 传入 null，图片选择功能需由外部调用方单独配置。
     *
     * @param context     上下文
     * @param contentView 工具页面的根视图
     * @param executor    线程池（由 AdvancedToolBinders 内部按需使用）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindColorPlus(context, contentView, null);
    }
}

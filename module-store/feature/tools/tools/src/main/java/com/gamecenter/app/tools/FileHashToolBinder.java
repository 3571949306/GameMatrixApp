package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 文件哈希计算工具绑定器。
 * <p>
 * 职责：作为文件哈希计算功能与 UI 之间的桥梁，将绑定请求委托给
 * {@link AdvancedToolBinders#bindFileHash} 方法执行实际的视图绑定逻辑。
 * </p>
 * <p>
 * 设计决策：采用委托模式，与 DnsLookupToolBinder 等同类绑定器保持一致的架构风格。
 * 第三个参数传 null 表示不使用外部传入的文件路径，由 AdvancedToolBinders
 * 内部自行处理文件选择逻辑。
 * </p>
 */
public class FileHashToolBinder implements ToolBinder {

    /**
     * 将文件哈希计算工具的 UI 逻辑绑定到指定的内容视图上。
     *
     * @param context     应用上下文，用于访问文件系统和 UI 资源
     * @param contentView 工具的根视图容器，包含文件选择和哈希结果显示相关的 UI 控件
     * @param executor    线程池执行器，用于执行耗时的文件哈希计算操作（本方法未直接使用，
     *                    由 AdvancedToolBinders 内部管理异步执行）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindFileHash(context, contentView, null);
    }
}

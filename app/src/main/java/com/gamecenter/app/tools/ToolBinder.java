package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 工具绑定器接口 — 定义工具模块的 UI 与业务逻辑绑定契约。
 * <p>
 * 每个工具卡片（如 Ping 工具、子网计算器等）都需要提供一个实现此接口的类，
 * 在 {@link #bind} 方法中完成 UI 控件的初始化、事件监听器的注册以及业务逻辑的绑定。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>实现类应为无状态或通过参数传入状态，避免持有 Fragment/Activity 引用，防止内存泄漏</li>
 *   <li>耗时操作必须使用 {@code executor} 参数在后台线程执行，不得阻塞 UI 线程</li>
 * </ul>
 * </p>
 */
public interface ToolBinder {

    /**
     * 将工具的 UI 控件和业务逻辑绑定到指定的 contentView 上。
     * <p>
     * 此方法在工具卡片被创建或复用时调用，实现类应在此方法中：
     * <ol>
     *   <li>通过 {@code contentView.findViewById()} 获取 UI 控件引用</li>
     *   <li>注册按钮点击、文本变化等事件监听器</li>
     *   <li>使用 {@code executor} 提交后台任务（如网络请求、耗时计算）</li>
     *   <li>后台任务完成后通过 {@link com.gamecenter.app.tools.ToolHelper#safeRunOnUiThread} 更新 UI</li>
     * </ol>
     * </p>
     *
     * @param context    Android Context，用于访问系统服务和资源
     * @param contentView 工具卡片的内容视图，包含该工具的所有 UI 控件
     * @param executor   后台线程执行器，用于执行网络请求、文件IO等耗时操作，避免阻塞 UI 线程
     */
    void bind(Context context, View contentView, ExecutorService executor);
}

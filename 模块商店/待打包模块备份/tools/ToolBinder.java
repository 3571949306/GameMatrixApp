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
 * 简单理解：这个接口就像一个"插头标准"，所有工具都必须按照这个标准来制造，
 * 这样工具箱页面就能统一地把各种工具"插"进去使用。
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
     * <p>
     * 打个比方：bind 就像是"给遥控器装电池"——把按钮（UI控件）和对应的功能（业务逻辑）连接起来，
     * 这样按下按钮就能执行相应的操作了。
     * </p>
     *
     * @param context    Android Context，用于访问系统服务和资源
     *                   （可以理解为应用的"身份证"，有了它才能调用系统功能）
     * @param contentView 工具卡片的内容视图，包含该工具的所有 UI 控件
     *                    （可以理解为工具的"画布"，上面画着各种按钮和文字）
     * @param executor   后台线程执行器，用于执行网络请求、文件IO等耗时操作，避免阻塞 UI 线程
     *                   （可以理解为"后台工人"，让耗时任务在后台默默执行，不影响界面流畅度）
     */
    void bind(Context context, View contentView, ExecutorService executor);
}

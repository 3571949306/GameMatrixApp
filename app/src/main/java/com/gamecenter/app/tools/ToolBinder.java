package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 工具绑定器接口 — 每个工具实现此接口，负责将 UI 和业务逻辑绑定到 contentView。
 * 实现类应为无状态或使用参数传入状态，避免持有 Fragment/Activity 引用。
 */
public interface ToolBinder {

    /**
     * 绑定工具 UI 和业务逻辑到指定 contentView。
     * @param context Android Context
     * @param contentView 工具卡片的内容视图
     * @param executor 后台线程执行器（用于网络/耗时操作）
     */
    void bind(Context context, View contentView, ExecutorService executor);
}

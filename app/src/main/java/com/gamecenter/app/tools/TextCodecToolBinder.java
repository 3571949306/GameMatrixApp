package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 文本编解码工具绑定器。
 * <p>
 * 负责将文本编解码功能（Base64 / URL 编码等）绑定到工具页面的 UI 控件上。
 * 实际的绑定逻辑委托给 AdvancedToolBinders.bindTextCodec 方法处理，
 * 本类仅作为 ToolBinder 接口的适配层。
 * </p>
 * <p>
 * 设计决策：将复杂编解码逻辑抽取到 AdvancedToolBinders 中统一管理，
 * 避免单个绑定器类过于臃肿，同时便于多种编解码方式共享通用 UI 逻辑。
 * </p>
 */
public class TextCodecToolBinder implements ToolBinder {

    /**
     * 将文本编解码功能绑定到视图。
     * <p>
     * 委托给 AdvancedToolBinders.bindTextCodec 完成实际的 UI 绑定。
     * </p>
     *
     * @param context     上下文
     * @param contentView 工具卡片的根视图
     * @param executor    线程池（传递给底层绑定器，用于可能的异步编解码操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        AdvancedToolBinders.bindTextCodec(context, contentView);
    }
}

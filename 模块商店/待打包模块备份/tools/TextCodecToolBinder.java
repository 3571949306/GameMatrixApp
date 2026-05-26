package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import java.util.concurrent.ExecutorService;

/**
 * 文本编解码工具绑定器。
 * <p>
 * 这是工具箱模块中"文本编解码"功能的入口类。它可以帮用户把文本进行各种编码和解码，
 * 比如 Base64 编码（把文字变成一串看似乱码的字符）和 URL 编码（把特殊字符转换成%XX格式）。
 * 你可以把它想象成一个"翻译器"——把文字从一种格式"翻译"成另一种格式。
 * </p>
 * <p>
 * 实际的绑定逻辑委托给 AdvancedToolBinders.bindTextCodec 方法处理，
 * 本类仅作为 ToolBinder 接口的适配层（就像一个转接头）。
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
     * 这个方法不做实际工作，只是把任务"转交"给 AdvancedToolBinders.bindTextCodec 处理。
     * </p>
     *
     * @param context     上下文
     * @param contentView 工具卡片的根视图
     * @param executor    线程池（传递给底层绑定器，用于可能的异步编解码操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        // 委托给 AdvancedToolBinders 执行实际的绑定逻辑
        AdvancedToolBinders.bindTextCodec(context, contentView);
    }
}

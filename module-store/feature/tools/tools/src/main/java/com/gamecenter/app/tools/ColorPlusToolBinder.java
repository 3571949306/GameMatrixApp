package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.gamecenter.app.fragments.ToolsFragment;

import java.util.concurrent.ExecutorService;

/**
 * 颜色增强工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 颜色增强工具提供 WCAG 对比度计算和图片取色功能，具体实现位于
 * {@link AdvancedToolBinders#bindColorPlus} 中。
 * </p>
 * <p>
 * 修复（2026-07-25）：之前传入 null 导致"图片取色"按钮无响应。
 * 直接从 contentView 的 tag_tools_fragment 获取 ToolsFragment 实例，
 * 通过共享 ActivityResultLauncher 接入图片选择器，
 * 结果回调 {@link AdvancedToolBinders#handleColorImageResult}。
 * </p>
 */
public class ColorPlusToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        ToolsFragment fragment = (ToolsFragment) contentView.getTag(R.id.tag_tools_fragment);
        if (fragment == null) {
            AdvancedToolBinders.bindColorPlus(context, contentView, v ->
                    Toast.makeText(context, R.string.tool_file_pick_unavailable, Toast.LENGTH_SHORT).show());
            return;
        }
        AdvancedToolBinders.bindColorPlus(context, contentView, v ->
                fragment.requestPickFile(uri -> {
                    ExecutorService used = (executor != null && !executor.isShutdown())
                            ? executor
                            : java.util.concurrent.Executors.newSingleThreadExecutor();
                    used.execute(() -> AdvancedToolBinders.handleColorImageResult(context, contentView, uri, used));
                }, new String[]{"image/*"}));
    }
}

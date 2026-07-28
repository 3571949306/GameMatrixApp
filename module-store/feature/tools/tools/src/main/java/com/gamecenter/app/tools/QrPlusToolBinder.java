package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.gamecenter.app.fragments.ToolsFragment;

import java.util.concurrent.ExecutorService;

/**
 * 二维码增强工具绑定器。
 * <p>
 * 增强版二维码工具，提供生成、扫描识别、历史记录等功能。
 * 完整绑定逻辑委托给 {@link AdvancedToolBinders#bindQrPlus}，
 * 图片选择通过 ToolsFragment 的共享 ActivityResultLauncher 完成，
 * 结果回调 {@link AdvancedToolBinders#handleQrImageResult} 识别二维码。
 * </p>
 * <p>
 * 修复（2026-07-25）：之前传入 null 导致"识别图片"按钮无响应。
 * 直接从 contentView 的 tag_tools_fragment 获取 ToolsFragment 实例。
 * </p>
 */
public class QrPlusToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        ToolsFragment fragment = (ToolsFragment) contentView.getTag(R.id.tag_tools_fragment);
        if (fragment == null) {
            AdvancedToolBinders.bindQrPlus(context, contentView, v ->
                    Toast.makeText(context, R.string.tool_file_pick_unavailable, Toast.LENGTH_SHORT).show());
            return;
        }
        AdvancedToolBinders.bindQrPlus(context, contentView, v ->
                fragment.requestPickFile(uri -> {
                    ExecutorService used = (executor != null && !executor.isShutdown())
                            ? executor
                            : java.util.concurrent.Executors.newSingleThreadExecutor();
                    used.execute(() -> AdvancedToolBinders.handleQrImageResult(context, contentView, uri, used));
                }, new String[]{"image/*"}));
    }
}

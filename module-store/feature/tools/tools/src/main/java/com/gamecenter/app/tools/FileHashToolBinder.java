package com.gamecenter.app.tools;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.gamecenter.app.fragments.ToolsFragment;

import java.util.concurrent.ExecutorService;

/**
 * 文件哈希计算工具绑定器。
 * <p>
 * 职责：作为文件哈希计算功能与 UI 之间的桥梁，将绑定请求委托给
 * {@link AdvancedToolBinders#bindFileHash} 方法执行实际的视图绑定逻辑。
 * </p>
 * <p>
 * 修复（2026-07-25）：之前传入 null 导致"选择文件"按钮无响应。
 * 现在通过 ToolsFragment 的共享 ActivityResultLauncher 接入系统文件选择器，
 * 文件选择结果回调 {@link AdvancedToolBinders#handleFileHashResult} 计算哈希。
 * </p>
 * <p>
 * ToolsFragment.bindContent 会在 contentView 的 tag_tools_fragment 中
 * 存储 Fragment 引用，本类直接从 tag 获取，避免遍历 FragmentManager。
 * </p>
 */
public class FileHashToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        ToolsFragment fragment = (ToolsFragment) contentView.getTag(R.id.tag_tools_fragment);
        if (fragment == null) {
            // 降级：无法获取 Fragment 时仍委托绑定（按钮点击会提示）
            AdvancedToolBinders.bindFileHash(context, contentView, v ->
                    Toast.makeText(context, R.string.tool_file_pick_unavailable, Toast.LENGTH_SHORT).show());
            return;
        }
        AdvancedToolBinders.bindFileHash(context, contentView, v ->
                fragment.requestPickFile(uri -> {
                    ExecutorService used = (executor != null && !executor.isShutdown())
                            ? executor
                            : java.util.concurrent.Executors.newSingleThreadExecutor();
                    used.execute(() -> AdvancedToolBinders.handleFileHashResult(context, contentView, uri, used));
                }, new String[]{"*/*"}));
    }
}

package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.gamecenter.app.utils.SystemInfoCollector;
import java.util.concurrent.ExecutorService;

/**
 * 系统信息工具绑定器。
 * <p>
 * 负责将系统信息展示与复制功能绑定到工具页面的 UI 控件上。
 * 页面加载时自动显示快速摘要信息（通过 SystemInfoCollector.getQuickSummary），
 * 点击"详情"按钮时收集完整系统信息并复制到剪贴板，同时弹出 Toast 提示。
 * </p>
 * <p>
 * 设计决策：详情信息直接复制到剪贴板而非弹窗展示，因为完整系统信息内容较长，
 * 剪贴板方式更便于用户粘贴到其他应用中分享或排查问题。
 * </p>
 */
public final class SystemInfoToolBinder implements ToolBinder {

    /**
     * 将系统信息展示功能绑定到视图。
     * <p>
     * 绑定时立即加载并显示快速摘要；同时为详情按钮注册点击事件。
     * </p>
     *
     * @param context     上下文，用于获取系统服务和显示 Toast
     * @param contentView 工具卡片的根视图，包含摘要文本和详情按钮
     * @param executor    线程池（本工具未使用，信息收集在主线程完成）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvQuick = contentView.findViewById(R.id.tv_sys_quick);
        View btnDetail = contentView.findViewById(R.id.btn_sys_detail);

        // 页面加载时立即显示快速系统摘要
        String quickInfo = SystemInfoCollector.getQuickSummary(context);
        if (tvQuick != null) tvQuick.setText(quickInfo);

        if (btnDetail != null) {
            btnDetail.setOnClickListener(v -> {
                // 收集完整系统信息并复制到剪贴板
                String fullInfo = buildFullInfo(context);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("系统信息", fullInfo);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "已复制完整系统信息到剪贴板", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 构建完整的系统信息字符串。
     *
     * @param context 上下文
     * @return 包含所有系统诊断信息的完整文本
     */
    private String buildFullInfo(Context context) {
        SystemInfoCollector collector = new SystemInfoCollector(context);
        return collector.collectAll();
    }
}

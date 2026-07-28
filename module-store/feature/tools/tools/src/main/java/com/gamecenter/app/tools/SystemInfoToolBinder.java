package com.gamecenter.app.tools;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.gamecenter.app.R;
import com.gamecenter.app.utils.SystemInfoCollector;

import java.util.concurrent.ExecutorService;

/**
 * 系统信息工具绑定器（2026-07-25 改进版）。
 * <p>
 * 改进点：
 * <ul>
 *   <li>合并原 DeviceToolBinder 的功能 —— 同时显示设备品牌/型号/系统版本</li>
 *   <li>"查看完整详情"按钮改为弹窗展示（AlertDialog），替代原来的直接复制到剪贴板</li>
 *   <li>新增独立的"复制"按钮，方便用户将完整信息复制到剪贴板</li>
 *   <li>使用本地化字符串</li>
 * </ul>
 * </p>
 */
public final class SystemInfoToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        // 设备信息（原 DeviceToolBinder 逻辑）
        TextView tvBrand = contentView.findViewById(R.id.tv_device_brand);
        TextView tvModel = contentView.findViewById(R.id.tv_device_model);
        TextView tvOs = contentView.findViewById(R.id.tv_device_os);
        if (tvBrand != null) tvBrand.setText(Build.BRAND);
        if (tvModel != null) tvModel.setText(Build.MODEL);
        if (tvOs != null) tvOs.setText(Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        // 系统快速摘要
        TextView tvQuick = contentView.findViewById(R.id.tv_sys_quick);
        if (tvQuick != null) {
            tvQuick.setText(SystemInfoCollector.getQuickSummary(context));
        }

        // "查看完整详情" —— 弹窗展示
        View btnDetail = contentView.findViewById(R.id.btn_sys_detail);
        if (btnDetail != null) {
            btnDetail.setOnClickListener(v -> showDetailDialog(context, executor));
        }

        // "复制" —— 完整信息复制到剪贴板
        View btnCopy = contentView.findViewById(R.id.btn_sys_copy);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                String fullInfo = buildFullInfo(context);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("系统信息", fullInfo);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, R.string.tool_sysinfo_copied, Toast.LENGTH_SHORT).show();
            });
        }
    }

    /** 在后台线程收集完整信息，然后弹出 AlertDialog 展示 */
    private void showDetailDialog(Context context, ExecutorService executor) {
        // 立即弹出"加载中"弹窗，避免用户感觉无响应
        TextView loadingMsg = new TextView(context);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        loadingMsg.setPadding(padding, padding, padding, padding);
        loadingMsg.setText(R.string.tool_diag_generating);
        loadingMsg.setTextColor(0xFF888888);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.tool_name_sysinfo)
                .setView(loadingMsg)
                .setPositiveButton(R.string.tool_sysinfo_close, (d, w) -> d.dismiss())
                .create();
        dialog.show();

        ExecutorService used = (executor != null && !executor.isShutdown())
                ? executor
                : java.util.concurrent.Executors.newSingleThreadExecutor();
        used.execute(() -> {
            String fullInfo = buildFullInfo(context);
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(() -> {
                if (!dialog.isShowing()) return;
                TextView detailView = new TextView(context);
                detailView.setPadding(padding, padding, padding, padding);
                detailView.setText(fullInfo);
                detailView.setTextIsSelectable(true);
                detailView.setTextSize(12f);
                detailView.setTypeface(android.graphics.Typeface.MONOSPACE);
                detailView.setTextColor(0xFF222222);

                android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
                scrollView.addView(detailView);

                dialog.setContentView(scrollView);
            });
        });
    }

    private String buildFullInfo(Context context) {
        SystemInfoCollector collector = new SystemInfoCollector(context);
        return collector.collectAll();
    }
}

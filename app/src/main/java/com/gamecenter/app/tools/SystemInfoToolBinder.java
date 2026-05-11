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

public final class SystemInfoToolBinder implements ToolBinder {

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvQuick = contentView.findViewById(R.id.tv_sys_quick);
        View btnDetail = contentView.findViewById(R.id.btn_sys_detail);

        String quickInfo = SystemInfoCollector.getQuickSummary(context);
        if (tvQuick != null) tvQuick.setText(quickInfo);

        if (btnDetail != null) {
            btnDetail.setOnClickListener(v -> {
                String fullInfo = buildFullInfo(context);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("系统信息", fullInfo);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "已复制完整系统信息到剪贴板", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private String buildFullInfo(Context context) {
        SystemInfoCollector collector = new SystemInfoCollector(context);
        return collector.collectAll();
    }
}

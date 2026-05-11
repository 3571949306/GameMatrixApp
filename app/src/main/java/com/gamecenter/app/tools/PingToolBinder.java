package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;

public final class PingToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_ping);
        if (btn != null) btn.setOnClickListener(v -> runPing(context, btn, contentView, executor));
    }

    private void runPing(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText("Ping中...");
        EditText etHost = contentView.findViewById(R.id.et_ping_host);
        TextView tvResult = contentView.findViewById(R.id.tv_ping_result);
        String host = etHost != null ? etHost.getText().toString().trim() : "119.29.29.29";
        if (host.isEmpty()) host = "119.29.29.29";

        final String fHost = host;
        executor.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                long min = Long.MAX_VALUE, max = Long.MIN_VALUE, sum = 0, successCount = 0;
                for (int i = 0; i < 4; i++) {
                    long time = ToolHelper.pingHost(fHost);
                    sb.append("  #").append(i + 1).append(" 响应时间: ");
                    if (time > 0) {
                        sb.append(time).append(" ms\n");
                        if (time < min) min = time;
                        if (time > max) max = time;
                        sum += time;
                        successCount++;
                    } else {
                        sb.append("超时\n");
                    }
                }
                sb.append("\n--- ").append(fHost).append(" Ping 统计 ---\n");
                sb.append("  已发送 = 4, 已接收 = ").append(successCount).append(", 丢包率 = ").append((4 - successCount) * 25).append("%\n");
                if (successCount > 0) sb.append("  最短/最长/平均 = ").append(min).append(" ms / ").append(max).append(" ms / ").append(sum / successCount).append(" ms\n");

                final String res = sb.toString();
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvResult != null) tvResult.setText(res);
                    btnStart.setEnabled(true);
                    btnStart.setText("Ping");
                });
            } catch (Exception e) {
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvResult != null) tvResult.setText("失败: " + e.getMessage());
                    btnStart.setEnabled(true);
                    btnStart.setText("Ping");
                });
            }
        });
    }
}

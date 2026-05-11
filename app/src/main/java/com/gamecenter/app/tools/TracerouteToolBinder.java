package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 路由追踪工具绑定器
 */
public class TracerouteToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_trace);
        if (btn != null) btn.setOnClickListener(v -> runTraceroute(context, btn, contentView, executor));
    }

    private void runTraceroute(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText("追踪中...");
        EditText etHost = contentView.findViewById(R.id.et_trace_host);
        TextView tvResult = contentView.findViewById(R.id.tv_trace_result);
        String host = etHost != null ? etHost.getText().toString().trim() : "119.29.29.29";
        if (host.isEmpty()) host = "119.29.29.29";

        final String fHost = host;
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            boolean resolved = false;
            int maxHops = 15;
            for (int ttl = 1; ttl <= maxHops; ttl++) {
                final int hop = ttl;
                final String partial = "追踪中... TTL " + ttl + "/" + maxHops;
                ToolHelper.safeRunOnUiThread(context, () -> { if (tvResult != null) tvResult.setText(partial); });

                ToolHelper.TraceHopResult result = ToolHelper.traceRouteHop(fHost, ttl);
                if (!resolved && result.ip != null && !result.ip.equals(fHost)) resolved = true;
                sb.append(String.format(Locale.getDefault(), "  %2d: %s %d ms\n", hop, result.ip != null ? result.ip : "* * *", result.time));
                if (result.ip != null && (result.ip.equals(fHost) || fHost.equals(result.ip))) break;
            }
            final String res = sb.toString().trim();
            ToolHelper.safeRunOnUiThread(context, () -> {
                if (tvResult != null) tvResult.setText(res);
                btnStart.setEnabled(true);
                btnStart.setText("追踪");
            });
        });
    }
}

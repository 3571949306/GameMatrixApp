package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;

public final class PortScanToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_port_scan);
        if (btn != null) btn.setOnClickListener(v -> scanPorts(context, btn, contentView, executor));
    }

    private void scanPorts(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText("扫描中...");
        EditText etHost = contentView.findViewById(R.id.et_port_scan_ip);
        EditText etPortRange = contentView.findViewById(R.id.et_port_scan_ports);
        TextView tvResult = contentView.findViewById(R.id.tv_port_scan_result);

        String host = etHost != null ? etHost.getText().toString().trim() : "127.0.0.1";
        String range = etPortRange != null ? etPortRange.getText().toString().trim() : "";
        if (host.isEmpty()) host = "127.0.0.1";
        int startPort = 1, endPort = 1024;
        if (range.contains("-")) {
            String[] parts = range.split("-");
            try {
                startPort = Integer.parseInt(parts[0].trim());
                endPort = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {
            }
        }

        final String fHost = host;
        final int fStart = startPort;
        final int fEnd = endPort;
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            for (int p = fStart; p <= fEnd && p <= 65535; p++) {
                if (isPortOpen(fHost, p, 200)) {
                    sb.append("  ✅ 端口 ").append(p).append(" 开放\n");
                }
                if (p % 10 == 0) {
                    final String partial = "扫描中... " + p + "/" + fEnd;
                    ToolHelper.safeRunOnUiThread(context, () -> {
                        if (tvResult != null) tvResult.setText(partial);
                    });
                }
            }
            final String res = sb.length() == 0 ? "未发现开放端口" : sb.toString().trim();
            ToolHelper.safeRunOnUiThread(context, () -> {
                if (tvResult != null) tvResult.setText(res);
                btnStart.setEnabled(true);
                btnStart.setText(context.getString(R.string.scan));
            });
        });
    }

    private boolean isPortOpen(String host, int port, int timeout) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), timeout);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 端口扫描工具绑定器（2026-07-25 改进版）。
 * <p>
 * 改进点：
 * <ul>
 *   <li>从单线程顺序扫描改为 ThreadPoolExecutor 并发扫描，可配置并发数（默认 16）</li>
 *   <li>支持自定义单端口连接超时（默认 300ms）</li>
 *   <li>支持多种端口范围格式：1-1024 / 80,443,8080 / 1-1024,3306</li>
 *   <li>结果按端口号排序，分别显示开放/关闭端口</li>
 *   <li>显示扫描进度和汇总统计</li>
 *   <li>使用本地化字符串，无硬编码中文</li>
 * </ul>
 * </p>
 */
public final class PortScanToolBinder implements ToolBinder {
    private static final String TAG = "PortScanToolBinder";
    /** 并发数上限，防止用户输入过大导致资源耗尽 */
    private static final int MAX_CONCURRENCY = 64;
    /** 单次扫描端口上限 */
    private static final int MAX_TOTAL_PORTS = 65535;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_port_scan);
        if (btn != null) btn.setOnClickListener(v -> scanPorts(context, btn, contentView, executor));
    }

    private void scanPorts(Context context, MaterialButton btnStart, View contentView, ExecutorService callerExecutor) {
        EditText etHost = contentView.findViewById(R.id.et_port_scan_ip);
        EditText etPortRange = contentView.findViewById(R.id.et_port_scan_ports);
        EditText etTimeout = contentView.findViewById(R.id.et_port_scan_timeout);
        EditText etConcurrency = contentView.findViewById(R.id.et_port_scan_concurrency);
        TextView tvResult = contentView.findViewById(R.id.tv_port_scan_result);
        TextView tvProgress = contentView.findViewById(R.id.tv_port_scan_progress);

        String host = etHost != null && etHost.getText() != null ? etHost.getText().toString().trim() : "";
        if (host.isEmpty()) host = "127.0.0.1";
        String rangeStr = etPortRange != null && etPortRange.getText() != null ? etPortRange.getText().toString().trim() : "";
        if (rangeStr.isEmpty()) rangeStr = context.getString(R.string.tool_portscan_default_ports);

        int timeout = parsePositive(etTimeout, 300);
        int concurrency = Math.min(MAX_CONCURRENCY, Math.max(1, parsePositive(etConcurrency, 16)));

        List<Integer> ports = parsePortRange(rangeStr);
        if (ports.isEmpty() || ports.size() > MAX_TOTAL_PORTS) {
            Toast.makeText(context, R.string.tool_portscan_invalid_input, Toast.LENGTH_SHORT).show();
            return;
        }

        btnStart.setEnabled(false);
        btnStart.setText(context.getString(R.string.tool_port_scan_running));
        if (tvProgress != null) {
            tvProgress.setVisibility(View.VISIBLE);
            tvProgress.setText(context.getString(R.string.tool_port_scan_running));
        }

        final String fHost = host;
        final int fTimeout = timeout;
        final int fConcurrency = concurrency;
        final List<Integer> fPorts = ports;

        // 使用独立的线程池，避免占用调用方 executor
        ThreadPoolExecutor scanPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(fConcurrency);
        // 用于汇总结果（线程安全）
        List<Integer> openPorts = Collections.synchronizedList(new ArrayList<>());
        List<Integer> closedPorts = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger done = new AtomicInteger(0);
        int total = fPorts.size();

        // 提交所有扫描任务
        List<Future<?>> futures = new ArrayList<>();
        for (Integer port : fPorts) {
            futures.add(scanPool.submit(() -> {
                boolean open = isPortOpen(fHost, port, fTimeout);
                if (open) openPorts.add(port);
                else closedPorts.add(port);
                int finished = done.incrementAndGet();
                // 每 10 个或完成时刷新进度
                if (finished % 10 == 0 || finished == total) {
                    ToolHelper.safeRunOnUiThread(context, () -> {
                        if (tvProgress != null) {
                            tvProgress.setText(finished + "/" + total);
                        }
                    });
                }
            }));
        }

        // 关闭线程池并在所有任务完成后汇总结果
        callerExecutor.execute(() -> {
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    Log.w(TAG, "scan future failed: " + e.getMessage());
                }
            }
            scanPool.shutdown();

            Collections.sort(openPorts);
            Collections.sort(closedPorts);

            StringBuilder sb = new StringBuilder();
            for (Integer p : openPorts) {
                sb.append(context.getString(R.string.tool_portscan_open_format, String.valueOf(p))).append("\n");
            }
            for (Integer p : closedPorts) {
                sb.append(context.getString(R.string.tool_portscan_closed_format, String.valueOf(p))).append("\n");
            }
            sb.append("\n").append(context.getString(R.string.tool_portscan_summary_format,
                    openPorts.size(), closedPorts.size(), total));

            final String resultText = sb.toString();
            ToolHelper.safeRunOnUiThread(context, () -> {
                if (tvResult != null) tvResult.setText(resultText);
                if (tvProgress != null) {
                    tvProgress.setText(context.getString(R.string.tool_portscan_summary_format,
                            openPorts.size(), closedPorts.size(), total));
                }
                btnStart.setEnabled(true);
                btnStart.setText(context.getString(R.string.scan));
            });
        });
    }

    /** 解析 EditText 中的正整数值，失败时返回默认值 */
    private int parsePositive(EditText et, int defaultVal) {
        if (et == null || et.getText() == null) return defaultVal;
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            return v > 0 ? v : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * 解析端口范围字符串，支持以下格式：
     * <ul>
     *   <li>"80" — 单个端口</li>
     *   <li>"1-1024" — 范围</li>
     *   <li>"80,443,8080" — 列表</li>
     *   <li>"1-100,3306,8080-8090" — 混合</li>
     * </ul>
     */
    private List<Integer> parsePortRange(String rangeStr) {
        List<Integer> ports = new ArrayList<>();
        if (rangeStr == null || rangeStr.isEmpty()) return ports;
        String[] parts = rangeStr.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length != 2) continue;
                try {
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    if (start < 1 || end > 65535 || start > end) continue;
                    for (int p = start; p <= end; p++) {
                        if (!ports.contains(p)) ports.add(p);
                    }
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    int p = Integer.parseInt(part);
                    if (p >= 1 && p <= 65535 && !ports.contains(p)) ports.add(p);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ports;
    }

    private boolean isPortOpen(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;

/**
 * 端口扫描工具绑定器。
 * <p>
 * 负责将端口扫描工具的 UI 视图与端口连通性检测逻辑进行绑定。
 * 用户输入目标 IP 和端口范围后，逐个尝试 TCP 连接以判断端口是否开放。
 * 关键设计决策：
 * <ul>
 *   <li>扫描操作在后台线程执行，每扫描 10 个端口刷新一次进度</li>
 *   <li>端口范围上限硬限制为 65535，防止越界</li>
 *   <li>使用短超时（200ms）以加快扫描速度，但可能在网络延迟较高时产生误判</li>
 * </ul>
 * </p>
 */
public final class PortScanToolBinder implements ToolBinder {
    private static final String TAG = "PortScanToolBinder";

    /**
     * 绑定端口扫描工具的视图和交互逻辑。
     * <p>
     * 查找"开始扫描"按钮并设置点击监听器，点击后触发端口扫描流程。
     * </p>
     *
     * @param context     上下文环境，用于 UI 线程切换
     * @param contentView 工具页面的根视图，包含 IP 输入框、端口范围输入框、结果文本和操作按钮
     * @param executor    线程池执行器，用于在后台线程执行扫描操作
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_port_scan);
        if (btn != null) btn.setOnClickListener(v -> scanPorts(context, btn, contentView, executor));
    }

    /**
     * 执行端口扫描流程。
     * <p>
     * 流程如下：
     * <ol>
     *   <li>禁用按钮，显示"扫描中..."状态</li>
     *   <li>读取用户输入的 IP 地址和端口范围（格式：起始端口-结束端口）</li>
     *   <li>在后台线程逐个尝试 TCP 连接，判断端口是否开放</li>
     *   <li>每扫描 10 个端口刷新一次进度显示</li>
     *   <li>扫描完成后显示结果并恢复按钮状态</li>
     * </ol>
     * </p>
     *
     * @param context     上下文环境，用于 UI 线程切换
     * @param btnStart    "开始扫描"按钮，用于控制启用/禁用和文本状态
     * @param contentView 根视图，用于查找输入框和结果文本控件
     * @param executor    线程池执行器，扫描操作在此执行以避免阻塞主线程
     */
    private void scanPorts(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText("扫描中...");
        EditText etHost = contentView.findViewById(R.id.et_port_scan_ip);
        EditText etPortRange = contentView.findViewById(R.id.et_port_scan_ports);
        TextView tvResult = contentView.findViewById(R.id.tv_port_scan_result);

        String host = etHost != null ? etHost.getText().toString().trim() : "127.0.0.1";
        String range = etPortRange != null ? etPortRange.getText().toString().trim() : "";
        // IP 为空时默认扫描本机
        if (host.isEmpty()) host = "127.0.0.1";
        // 默认扫描 1-1024 端口范围（知名端口）
        int startPort = 1, endPort = 1024;
        // 解析端口范围格式："起始端口-结束端口"
        if (range.contains("-")) {
            String[] parts = range.split("-");
            try {
                startPort = Integer.parseInt(parts[0].trim());
                endPort = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {
                Log.w(TAG, "Parse port range failed: " + ignored.getMessage());
            }
        }

        final String fHost = host;
        final int fStart = startPort;
        final int fEnd = endPort;
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            // 逐个端口扫描，上限硬限制为 65535
            for (int p = fStart; p <= fEnd && p <= 65535; p++) {
                if (isPortOpen(fHost, p, 200)) {
                    sb.append("  ✅ 端口 ").append(p).append(" 开放\n");
                }
                // 每扫描 10 个端口更新一次进度，避免频繁刷新 UI
                if (p % 10 == 0) {
                    final String partial = "扫描中... " + p + "/" + fEnd;
                    ToolHelper.safeRunOnUiThread(context, () -> {
                        if (tvResult != null) tvResult.setText(partial);
                    });
                }
            }
            // 无开放端口时给出提示
            final String res = sb.length() == 0 ? "未发现开放端口" : sb.toString().trim();
            ToolHelper.safeRunOnUiThread(context, () -> {
                if (tvResult != null) tvResult.setText(res);
                btnStart.setEnabled(true);
                btnStart.setText(context.getString(R.string.scan));
            });
        });
    }

    /**
     * 检测指定主机的指定端口是否开放。
     * <p>
     * 通过尝试建立 TCP 连接来判断端口状态。如果连接成功则端口开放，否则视为关闭或不可达。
     * 使用短超时以加快扫描速度，但可能在网络延迟较高时产生假阴性（误判为关闭）。
     * </p>
     *
     * @param host    目标主机地址
     * @param port    目标端口号
     * @param timeout 连接超时时间（毫秒）
     * @return 如果端口开放返回 {@code true}，否则返回 {@code false}
     */
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

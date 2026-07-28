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
 * 路由追踪工具绑定器。
 * <p>
 * 负责将 Traceroute 功能绑定到工具页面的 UI 控件上。
 * 通过逐跳递增 TTL 值（1~maxHops）发送探测包，记录每一跳的 IP 地址和延迟，
 * 直到到达目标主机或超过最大跳数为止。所有网络操作在 ExecutorService 中执行。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>最大跳数设为 15，兼顾追踪深度与耗时</li>
 *   <li>输入为空时默认追踪 119.29.29.29（腾讯公共 DNS），提供开箱即用的体验</li>
 *   <li>追踪过程中实时更新 UI 显示当前 TTL 进度</li>
 * </ul>
 * </p>
 */
public class TracerouteToolBinder implements ToolBinder {

    /**
     * 将路由追踪功能绑定到视图。
     *
     * @param context     上下文
     * @param contentView 工具卡片的根视图，包含输入框、按钮和结果文本
     * @param executor    线程池，用于执行耗时的网络追踪操作
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_trace);
        if (btn != null) btn.setOnClickListener(v -> runTraceroute(context, btn, contentView, executor));
    }

    /**
     * 执行路由追踪。
     * <p>
     * 从 TTL=1 开始逐跳递增，每跳调用 ToolHelper.traceRouteHop 探测，
     * 记录 IP 和延迟。当探测到的 IP 与目标主机一致时提前终止。
     * 追踪期间按钮禁用，完成后恢复。
     * </p>
     *
     * @param context     上下文
     * @param btnStart    追踪启动按钮
     * @param contentView 根视图
     * @param executor    线程池
     */
    private void runTraceroute(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText(context.getString(R.string.tool_traceroute_running));
        EditText etHost = contentView.findViewById(R.id.et_trace_host);
        TextView tvResult = contentView.findViewById(R.id.tv_trace_result);
        String host = etHost != null ? etHost.getText().toString().trim() : "119.29.29.29";
        // 输入为空时使用默认目标地址
        if (host.isEmpty()) host = "119.29.29.29";

        final String fHost = host;
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            boolean resolved = false;
            int maxHops = 15;
            for (int ttl = 1; ttl <= maxHops; ttl++) {
                final int hop = ttl;
                // 实时更新 UI 显示当前追踪进度
                final String partial = "追踪中... TTL " + ttl + "/" + maxHops;
                ToolHelper.safeRunOnUiThread(context, () -> { if (tvResult != null) tvResult.setText(partial); });

                NetworkDiagHelper.TraceHopResult result = ToolHelper.traceRouteHop(fHost, ttl);
                // 标记是否至少经过了一跳中间路由
                if (!resolved && result.ip != null && !result.ip.equals(fHost)) resolved = true;
                // 格式化输出：跳数、IP（超时显示 * * *）、延迟
                sb.append(String.format(Locale.getDefault(), "  %2d: %s %d ms\n", hop, result.ip != null ? result.ip : "* * *", result.time));
                // 到达目标主机则提前终止追踪
                if (result.ip != null && (result.ip.equals(fHost) || fHost.equals(result.ip))) break;
            }
            final String res = sb.toString().trim();
            ToolHelper.safeRunOnUiThread(context, () -> {
                if (tvResult != null) tvResult.setText(res);
                btnStart.setEnabled(true);
                btnStart.setText(context.getString(R.string.tool_traceroute_start));
            });
        });
    }
}

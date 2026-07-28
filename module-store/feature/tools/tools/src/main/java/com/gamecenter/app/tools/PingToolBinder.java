package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.concurrent.ExecutorService;

/**
 * Ping 网络诊断工具绑定器。
 * <p>
 * 这是工具箱模块中"Ping"功能的核心类。Ping就像你朝远处喊一声，看多久能收到回音——
 * 用来检测你的设备和目标服务器之间的网络是否通畅，以及延迟有多高。
 * </p>
 * <p>
 * 用户输入目标主机地址后，执行 4 次 ICMP Ping 并统计结果（最短/最长/平均延迟、丢包率）。
 * 关键设计决策：Ping 操作在后台线程执行以避免阻塞 UI（不能在主线程做网络操作），
 * 结果通过 ToolHelper.safeRunOnUiThread 回传到主线程更新界面。
 * </p>
 */
public final class PingToolBinder implements ToolBinder {

    /** 默认 Ping 目标（腾讯公共 DNS） */
    private static final String DEFAULT_PING_HOST = "119.29.29.29";

    /**
     * 绑定 Ping 工具的视图和交互逻辑。
     * <p>
     * 找到"开始Ping"按钮，设置点击事件：点击后开始执行Ping。
     * </p>
     *
     * @param context     上下文环境，用于 UI 线程切换
     * @param contentView 工具页面的根视图，包含主机输入框、结果文本和操作按钮
     * @param executor    线程池执行器，用于在后台线程执行 Ping 操作
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        // 找到"开始Ping"按钮
        MaterialButton btn = contentView.findViewById(R.id.btn_start_ping);
        if (btn != null) btn.setOnClickListener(v -> runPing(context, btn, contentView, executor));
    }

    /**
     * 执行 Ping 诊断流程。
     * <p>
     * 整个流程就像去医院做检查：
     * <ol>
     *   <li>禁用按钮，显示"Ping中..."状态（类似"检查中，请等待"）</li>
     *   <li>读取用户输入的主机地址，若为空则使用默认地址 119.29.29.29</li>
     *   <li>在后台线程执行 4 次 Ping，统计最短/最长/平均延迟和丢包率</li>
     *   <li>将统计结果回显到 UI，并恢复按钮状态</li>
     * </ol>
     * </p>
     *
     * @param context     上下文环境，用于 UI 线程切换
     * @param btnStart    "开始 Ping"按钮，用于控制启用/禁用和文本状态
     * @param contentView 根视图，用于查找输入框和结果文本控件
     * @param executor    线程池执行器，Ping 操作在此执行以避免阻塞主线程
     */
    private void runPing(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        // 禁用按钮防止重复点击，并显示"进行中"状态
        btnStart.setEnabled(false);
        btnStart.setText(context.getString(R.string.tool_ping_button_running));

        // 找到输入框和结果显示区域
        EditText etHost = contentView.findViewById(R.id.et_ping_host);
        TextView tvResult = contentView.findViewById(R.id.tv_ping_result);
        String host = etHost != null ? etHost.getText().toString().trim() : DEFAULT_PING_HOST;
        // 输入为空时使用默认 DNS 地址（腾讯公共DNS）
        if (host.isEmpty()) host = DEFAULT_PING_HOST;

        final String fHost = host;
        // 在后台线程执行Ping操作
        executor.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                // 初始化统计变量
                long min = Long.MAX_VALUE, max = Long.MIN_VALUE, sum = 0, successCount = 0;
                // 执行 4 次 Ping，统计延迟数据
                for (int i = 0; i < 4; i++) {
                    long time = ToolHelper.pingHost(fHost);
                    sb.append("  #").append(i + 1).append(" 响应时间: ");
                    if (time > 0) {
                        // Ping成功，记录延迟
                        sb.append(time).append(" ms\n");
                        if (time < min) min = time;   // 更新最短延迟
                        if (time > max) max = time;   // 更新最长延迟
                        sum += time;                    // 累加延迟用于计算平均值
                        successCount++;                 // 成功次数+1
                    } else {
                        // time <= 0 表示 Ping 超时或失败（喊了没回音）
                        sb.append("超时\n");
                    }
                }
                // 汇总统计信息
                sb.append("\n--- ").append(fHost).append(" Ping 统计 ---\n");
                // 丢包率：每丢一个包为 25%（总共 4 次）
                sb.append("  已发送 = 4, 已接收 = ").append(successCount).append(", 丢包率 = ").append((4 - successCount) * 25).append("%\n");
                // 仅在有成功响应时才输出延迟统计，避免除零错误
                if (successCount > 0) sb.append("  最短/最长/平均 = ").append(min).append(" ms / ").append(max).append(" ms / ").append(sum / successCount).append(" ms\n");

                final String res = sb.toString();
                // 回到主线程更新UI
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvResult != null) tvResult.setText(res);
                    btnStart.setEnabled(true);   // 恢复按钮可用
                    btnStart.setText(context.getString(R.string.tool_ping_button_idle));     // 恢复按钮文字
                });
            } catch (Exception e) {
                // 出现异常时也要恢复按钮状态，并显示错误信息
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvResult != null) tvResult.setText(context.getString(R.string.tool_ping_failed, e.getMessage()));
                    btnStart.setEnabled(true);
                    btnStart.setText(context.getString(R.string.tool_ping_button_idle));
                });
            }
        });
    }
}

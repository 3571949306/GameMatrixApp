package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 网络测速工具绑定器。
 * <p>
 * 负责将测速功能（Ping / 下载 / 上传）绑定到工具页面的 UI 控件上。
 * 采用"依次尝试多服务器"策略：下载测速依次尝试 SPEED_TEST_SERVERS 中的服务器，
 * 首个成功即返回；上传测速同理。所有网络操作在 ExecutorService 线程池中执行，
 * UI 更新通过 ToolHelper.safeRunOnUiThread 回到主线程。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>下载与上传使用不同的服务器列表，因为并非所有服务器都支持 POST 上传</li>
 *   <li>测速期间禁用按钮防止重复触发，测速完成后恢复</li>
 * </ul>
 * </p>
 */
public class SpeedTestToolBinder implements ToolBinder {

    private static final String TAG = "SpeedTestToolBinder";

    /** 下载测速服务器列表，依次尝试直到成功 */
    private static final String[] SPEED_TEST_SERVERS = {
        "http://speedtest.tele2.net/100MB.zip",
        "https://proof.ovh.net/files/100Mb.dat",
        "http://speedtest.ftp.otenet.gr/files/test100Mb.db"
    };

    /** 上传测速服务器列表，需要支持 POST 请求 */
    private static final String[] UPLOAD_TEST_SERVERS = {
        "https://httpbin.org/post",
        "https://postman-echo.com/post"
    };

    /**
     * 将测速功能绑定到视图。
     *
     * @param context     上下文，用于 UI 线程回调
     * @param contentView 工具卡片的根视图，包含按钮和结果显示控件
     * @param executor    线程池，用于执行耗时的网络测速操作
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_speed_test);
        if (btn != null) btn.setOnClickListener(v -> startSpeedTest(context, btn, contentView, executor));
    }

    /**
     * 启动完整的网络测速流程：Ping → 下载 → 上传。
     * <p>
     * 整个流程在后台线程执行，依次测试 Ping 延迟、下载速度和上传速度，
     * 每个阶段完成后即时更新对应的 UI 控件。测速期间按钮被禁用，
     * 全部完成或异常后恢复按钮状态。
     * </p>
     *
     * @param context     上下文
     * @param btnStart    测速启动按钮，测速期间禁用
     * @param contentView 根视图，用于查找结果显示控件
     * @param executor    线程池
     */
    private void startSpeedTest(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText("测试中...");
        TextView tvPing = contentView.findViewById(R.id.tv_ping);
        TextView tvDownload = contentView.findViewById(R.id.tv_download_speed);
        TextView tvUpload = contentView.findViewById(R.id.tv_upload_speed);
        TextView tvServer = contentView.findViewById(R.id.tv_speed_test_server);

        executor.execute(() -> {
            try {
                // 第一阶段：Ping 延迟测试
                long ping = ToolHelper.testPing();
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPing != null) tvPing.setText(ping > 0 ? ping + " ms" : "超时");
                });

                // 第二阶段：下载速度测试，依次尝试多个服务器
                final double[] dlSpeed = {0};
                String selected = "";
                for (String server : SPEED_TEST_SERVERS) {
                    try {
                        final String cur = server;
                        // 截取域名部分用于 UI 展示，最长 28 字符避免溢出
                        String shortNameRaw = cur.replace("https://", "").replace("http://", "");
                        final String shortName = shortNameRaw.length() > 28 ? shortNameRaw.substring(0, 28) : shortNameRaw;
                        ToolHelper.safeRunOnUiThread(context, () -> {
                            if (tvServer != null) tvServer.setText("下载测试: " + shortName);
                        });
                        double speed = ToolHelper.testDownloadSpeed(server);
                        // 首个成功的服务器即采用，不再继续尝试
                        if (speed > 0) { dlSpeed[0] = speed; selected = server; break; }
                    } catch (Exception ignored) { Log.w(TAG, "Download speed test failed: " + ignored.getMessage()); }
                }

                // 更新下载测速结果到 UI
                final String fServer = selected;
                final double fSpeed = dlSpeed[0];
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvServer != null) tvServer.setText(fServer.isEmpty() ? "下载失败" : "下载: " + fServer.replace("https://", "").replace("http://", ""));
                    if (tvDownload != null) tvDownload.setText(fSpeed > 0 ? String.format(Locale.getDefault(), "%.1f Mbps", fSpeed) : "测试失败");
                });

                // 第三阶段：上传速度测试，仅在上传控件存在时执行
                double ulSpeed = 0;
                if (tvUpload != null) {
                    ToolHelper.safeRunOnUiThread(context, () -> { if (tvUpload != null) tvUpload.setText("测试中..."); });
                    for (String uploadUrl : UPLOAD_TEST_SERVERS) {
                        try {
                            ulSpeed = ToolHelper.testUploadSpeed(uploadUrl);
                            if (ulSpeed > 0) break;
                        } catch (Exception ignored) { Log.w(TAG, "Upload speed test failed: " + ignored.getMessage()); }
                    }
                }

                // 更新上传测速结果到 UI
                final double fUlSpeed = ulSpeed;
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvUpload != null) tvUpload.setText(fUlSpeed > 0 ? String.format(Locale.getDefault(), "%.1f Mbps", fUlSpeed) : "测试失败");
                });
            } catch (Exception e) {
                // 任何未捕获的异常均将所有结果显示为"失败"
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPing != null) tvPing.setText("失败");
                    if (tvDownload != null) tvDownload.setText("失败");
                    if (tvUpload != null) tvUpload.setText("失败");
                });
            }
            // 无论成功或失败，恢复按钮可用状态
            ToolHelper.safeRunOnUiThread(context, () -> { btnStart.setEnabled(true); btnStart.setText(R.string.start_test); });
        });
    }
}

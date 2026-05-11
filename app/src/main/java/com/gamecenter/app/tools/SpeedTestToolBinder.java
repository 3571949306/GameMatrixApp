package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 网络测速工具绑定器
 */
public class SpeedTestToolBinder implements ToolBinder {

    private static final String[] SPEED_TEST_SERVERS = {
        "http://speedtest.tele2.net/100MB.zip",
        "https://proof.ovh.net/files/100Mb.dat",
        "http://speedtest.ftp.otenet.gr/files/test100Mb.db"
    };
    private static final String[] UPLOAD_TEST_SERVERS = {
        "https://httpbin.org/post",
        "https://postman-echo.com/post"
    };

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        MaterialButton btn = contentView.findViewById(R.id.btn_start_speed_test);
        if (btn != null) btn.setOnClickListener(v -> startSpeedTest(context, btn, contentView, executor));
    }

    private void startSpeedTest(Context context, MaterialButton btnStart, View contentView, ExecutorService executor) {
        btnStart.setEnabled(false);
        btnStart.setText("测试中...");
        TextView tvPing = contentView.findViewById(R.id.tv_ping);
        TextView tvDownload = contentView.findViewById(R.id.tv_download_speed);
        TextView tvUpload = contentView.findViewById(R.id.tv_upload_speed);
        TextView tvServer = contentView.findViewById(R.id.tv_speed_test_server);

        executor.execute(() -> {
            try {
                long ping = ToolHelper.testPing();
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPing != null) tvPing.setText(ping > 0 ? ping + " ms" : "超时");
                });

                final double[] dlSpeed = {0};
                String selected = "";
                for (String server : SPEED_TEST_SERVERS) {
                    try {
                        final String cur = server;
                        String shortNameRaw = cur.replace("https://", "").replace("http://", "");
                        final String shortName = shortNameRaw.length() > 28 ? shortNameRaw.substring(0, 28) : shortNameRaw;
                        ToolHelper.safeRunOnUiThread(context, () -> {
                            if (tvServer != null) tvServer.setText("下载测试: " + shortName);
                        });
                        double speed = ToolHelper.testDownloadSpeed(server);
                        if (speed > 0) { dlSpeed[0] = speed; selected = server; break; }
                    } catch (Exception ignored) {}
                }

                final String fServer = selected;
                final double fSpeed = dlSpeed[0];
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvServer != null) tvServer.setText(fServer.isEmpty() ? "下载失败" : "下载: " + fServer.replace("https://", "").replace("http://", ""));
                    if (tvDownload != null) tvDownload.setText(fSpeed > 0 ? String.format(Locale.getDefault(), "%.1f Mbps", fSpeed) : "测试失败");
                });

                double ulSpeed = 0;
                if (tvUpload != null) {
                    ToolHelper.safeRunOnUiThread(context, () -> { if (tvUpload != null) tvUpload.setText("测试中..."); });
                    for (String uploadUrl : UPLOAD_TEST_SERVERS) {
                        try {
                            ulSpeed = ToolHelper.testUploadSpeed(uploadUrl);
                            if (ulSpeed > 0) break;
                        } catch (Exception ignored) {}
                    }
                }

                final double fUlSpeed = ulSpeed;
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvUpload != null) tvUpload.setText(fUlSpeed > 0 ? String.format(Locale.getDefault(), "%.1f Mbps", fUlSpeed) : "测试失败");
                });
            } catch (Exception e) {
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPing != null) tvPing.setText("失败");
                    if (tvDownload != null) tvDownload.setText("失败");
                    if (tvUpload != null) tvUpload.setText("失败");
                });
            }
            ToolHelper.safeRunOnUiThread(context, () -> { btnStart.setEnabled(true); btnStart.setText(R.string.start_test); });
        });
    }
}

package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;

/**
 * IP 地址查询工具绑定器
 */
public class IpToolBinder implements ToolBinder {

    private static final String[][] PUBLIC_IP_APIS = {
        {"http://ip-api.com/json/?lang=zh-CN", "query", "country", "regionName", "city", "isp"},
        {"https://api.ip.sb/json", "ip", "country", "region", "city", "organization"},
        {"https://ipinfo.io/json", "ip", "country", "region", "city", "org"},
    };

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvWifiIp = contentView.findViewById(R.id.tv_wifi_ip);
        TextView tvMobileIp = contentView.findViewById(R.id.tv_mobile_ip);
        TextView tvPublicIp = contentView.findViewById(R.id.tv_public_ip);
        TextView tvIpLocation = contentView.findViewById(R.id.tv_ip_location);
        TextView tvVpnStatus = contentView.findViewById(R.id.tv_vpn_status);
        if (tvWifiIp != null) tvWifiIp.setText(ToolHelper.getWifiIpAddress(context));
        if (tvMobileIp != null) tvMobileIp.setText(ToolHelper.getMobileIpAddress());
        if (tvVpnStatus != null) tvVpnStatus.setText(ToolHelper.checkVpnStatus(context));
        fetchPublicIpInfo(context, tvPublicIp, tvIpLocation, executor);
    }

    private void fetchPublicIpInfo(Context context, TextView tvPublicIp, TextView tvIpLocation, ExecutorService executor) {
        if (tvPublicIp == null && tvIpLocation == null) return;
        executor.execute(() -> {
            boolean success = false;
            for (String[] api : PUBLIC_IP_APIS) {
                if (success) break;
                try {
                    String urlStr = api[0];
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "GameCenterApp/1.0");
                    if (urlStr.startsWith("https")) {
                        conn.setRequestProperty("Accept", "application/json");
                    }
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) resp.append(line);
                    reader.close();
                    conn.disconnect();

                    org.json.JSONObject json = new org.json.JSONObject(resp.toString());
                    String publicIp = json.optString(api[1], "");
                    if (publicIp.isEmpty()) continue;

                    String loc = json.optString(api[2], "") + " " +
                            json.optString(api[3], "") + " " +
                            json.optString(api[4], "") +
                            "\n运营商: " + json.optString(api[5], "未知");
                    final String fIp = publicIp;
                    final String fLoc = loc.trim().replaceAll("\\s+", " ").replace("\n运营商: ", "\n运营商: ");
                    ToolHelper.safeRunOnUiThread(context, () -> {
                        if (tvPublicIp != null) tvPublicIp.setText(fIp);
                        if (tvIpLocation != null) tvIpLocation.setText(fLoc);
                    });
                    success = true;
                } catch (Exception ignored) {
                }
            }
            if (!success) {
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPublicIp != null) tvPublicIp.setText("获取失败");
                    if (tvIpLocation != null) tvIpLocation.setText("请检查网络连接后重试");
                });
            }
        });
    }
}

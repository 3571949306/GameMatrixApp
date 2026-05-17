package com.gamecenter.app.tools;

import android.content.Context;
import android.util.Log;
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
 * IP 地址查询工具绑定器。
 * <p>
 * 职责：查询并展示设备的各类 IP 地址信息，包括：
 * <ul>
 *   <li>Wi-Fi 本地 IP 地址</li>
 *   <li>移动网络 IP 地址</li>
 *   <li>公网 IP 地址及归属地信息</li>
 *   <li>VPN 连接状态</li>
 * </ul>
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>公网 IP 查询采用多 API 降级策略，依次尝试三个公开 API，任一成功即停止</li>
 *   <li>网络请求在后台线程执行，通过 ToolHelper.safeRunOnUiThread 安全地更新 UI</li>
 *   <li>本地 IP 和 VPN 状态为轻量查询，直接在主线程执行</li>
 * </ul>
 * </p>
 */
public class IpToolBinder implements ToolBinder {

    private static final String TAG = "IpToolBinder";

    /**
     * 公网 IP 查询 API 配置。
     * <p>
     * 每个子数组格式为：{API URL, IP字段名, 国家字段名, 地区字段名, 城市字段名, 运营商字段名}。
     * 不同 API 返回的 JSON 字段名不同，需分别映射。
     * </p>
     */
    private static final String[][] PUBLIC_IP_APIS = {
        {"http://ip-api.com/json/?lang=zh-CN", "query", "country", "regionName", "city", "isp"},
        {"https://api.ip.sb/json", "ip", "country", "region", "city", "organization"},
        {"https://ipinfo.io/json", "ip", "country", "region", "city", "org"},
    };

    /**
     * 将 IP 查询工具的 UI 逻辑绑定到指定的内容视图上。
     * <p>
     * 本地 IP 和 VPN 状态即时获取并显示，公网 IP 信息异步获取。
     * </p>
     *
     * @param context     应用上下文，用于获取系统服务和资源
     * @param contentView 工具的根视图容器，需包含 tv_wifi_ip、tv_mobile_ip、
     *                    tv_public_ip、tv_ip_location、tv_vpn_status 等 TextView
     * @param executor    线程池执行器，用于异步执行公网 IP 查询的网络请求
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvWifiIp = contentView.findViewById(R.id.tv_wifi_ip);
        TextView tvMobileIp = contentView.findViewById(R.id.tv_mobile_ip);
        TextView tvPublicIp = contentView.findViewById(R.id.tv_public_ip);
        TextView tvIpLocation = contentView.findViewById(R.id.tv_ip_location);
        TextView tvVpnStatus = contentView.findViewById(R.id.tv_vpn_status);
        // 本地 IP 和 VPN 状态查询为轻量操作，直接在当前线程执行
        if (tvWifiIp != null) tvWifiIp.setText(ToolHelper.getWifiIpAddress(context));
        if (tvMobileIp != null) tvMobileIp.setText(ToolHelper.getMobileIpAddress());
        if (tvVpnStatus != null) tvVpnStatus.setText(ToolHelper.checkVpnStatus(context));
        // 公网 IP 查询涉及网络 I/O，需异步执行
        fetchPublicIpInfo(context, tvPublicIp, tvIpLocation, executor);
    }

    /**
     * 异步获取公网 IP 地址及归属地信息。
     * <p>
     * 依次尝试 PUBLIC_IP_APIS 中配置的多个 API，任一成功即停止。
     * 所有 API 均失败时，显示"获取失败"提示。
     * </p>
     *
     * @param context      应用上下文，用于通过 safeRunOnUiThread 回到主线程更新 UI
     * @param tvPublicIp   显示公网 IP 地址的 TextView，可为 null
     * @param tvIpLocation 显示 IP 归属地信息的 TextView，可为 null
     * @param executor     线程池执行器，用于执行网络请求
     */
    private void fetchPublicIpInfo(Context context, TextView tvPublicIp, TextView tvIpLocation, ExecutorService executor) {
        // 两个目标 TextView 都为 null 时无需查询
        if (tvPublicIp == null && tvIpLocation == null) return;
        executor.execute(() -> {
            boolean success = false;
            // 降级策略：依次尝试多个 API，任一成功即停止
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
                    // HTTPS API 需设置 Accept 头以获取 JSON 响应
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
                    // api[1] 为当前 API 返回 JSON 中 IP 地址对应的字段名
                    String publicIp = json.optString(api[1], "");
                    if (publicIp.isEmpty()) continue;

                    // api[2]-api[5] 分别对应国家、地区、城市、运营商字段名
                    String loc = json.optString(api[2], "") + " " +
                            json.optString(api[3], "") + " " +
                            json.optString(api[4], "") +
                            "\n运营商: " + json.optString(api[5], "未知");
                    final String fIp = publicIp;
                    // 清理多余空白：合并连续空格，保留"运营商:"前的换行
                    final String fLoc = loc.trim().replaceAll("\\s+", " ").replace("\n运营商: ", "\n运营商: ");
                    ToolHelper.safeRunOnUiThread(context, () -> {
                        if (tvPublicIp != null) tvPublicIp.setText(fIp);
                        if (tvIpLocation != null) tvIpLocation.setText(fLoc);
                    });
                    success = true;
                } catch (Exception ignored) {
                    Log.w(TAG, "Fetch public IP info failed: " + ignored.getMessage());
                }
            }
            // 所有 API 均失败时的兜底处理
            if (!success) {
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPublicIp != null) tvPublicIp.setText("获取失败");
                    if (tvIpLocation != null) tvIpLocation.setText("请检查网络连接后重试");
                });
            }
        });
    }
}

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
 * 这是工具箱模块中的"IP查询"功能的核心类。你可以把它想象成一个"IP信息查询员"——
 * 当用户打开IP查询工具页面时，这个类负责把页面上的各个文字区域填上对应的IP信息。
 * </p>
 * <p>
 * 它能查询并展示设备的各类 IP 地址信息，包括：
 * <ul>
 *   <li>Wi-Fi 本地 IP 地址 — 连接Wi-Fi时路由器分配给你的地址，就像你家的门牌号</li>
 *   <li>移动网络 IP 地址 — 使用手机流量时运营商分配的地址</li>
 *   <li>公网 IP 地址及归属地信息 — 你在互联网上的"真实地址"和所在位置</li>
 *   <li>VPN 连接状态 — 是否正在使用虚拟专用网络</li>
 * </ul>
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>公网 IP 查询采用多 API 降级策略（就像打电话，第一个打不通就打第二个），
 *       依次尝试三个公开 API，任一成功即停止</li>
 *   <li>网络请求在后台线程执行（不能在主线程做网络操作，否则会卡住界面），
 *       通过 ToolHelper.safeRunOnUiThread 安全地更新 UI</li>
 *   <li>本地 IP 和 VPN 状态为轻量查询，直接在主线程执行（读取速度快，不会卡界面）</li>
 * </ul>
 * </p>
 */
public class IpToolBinder implements ToolBinder {

    // 日志标签，用于在Logcat中筛选这个类的日志信息
    private static final String TAG = "IpToolBinder";

    /**
     * 公网 IP 查询 API 配置。
     * <p>
     * 这里配置了三个不同的公网IP查询服务，就像备了三把钥匙，一把打不开就试下一把。
     * 每个子数组格式为：{API URL, IP字段名, 国家字段名, 地区字段名, 城市字段名, 运营商字段名}。
     * 不同 API 返回的 JSON 字段名不同，所以需要分别映射。
     * </p>
     */
    private static final String[][] PUBLIC_IP_APIS = {
        // ip-api.com：返回的JSON中IP字段叫"query"
        {"http://ip-api.com/json/?lang=zh-CN", "query", "country", "regionName", "city", "isp"},
        // api.ip.sb：返回的JSON中IP字段叫"ip"
        {"https://api.ip.sb/json", "ip", "country", "region", "city", "organization"},
        // ipinfo.io：返回的JSON中IP字段也叫"ip"
        {"https://ipinfo.io/json", "ip", "country", "region", "city", "org"},
    };

    /**
     * 将 IP 查询工具的 UI 逻辑绑定到指定的内容视图上。
     * <p>
     * 可以把这个方法理解为"给页面装上功能"——找到页面上的各个文字区域，
     * 然后把对应的IP信息填进去。本地IP和VPN状态立刻就能显示，
     * 公网IP需要联网查询，所以稍后才会出现。
     * </p>
     *
     * @param context     应用上下文，用于获取系统服务和资源（相当于"环境信息"）
     * @param contentView 工具的根视图容器，里面包含了各种文字显示区域（TextView）
     * @param executor    线程池执行器，相当于一个"工人团队"，用来在后台执行网络请求
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        // 如果没有传入视图，说明页面还没准备好，直接返回
        if (contentView == null) return;

        // 通过ID找到页面上的各个文字显示区域
        TextView tvWifiIp = contentView.findViewById(R.id.tv_wifi_ip);       // Wi-Fi IP 显示区
        TextView tvMobileIp = contentView.findViewById(R.id.tv_mobile_ip);   // 移动网络 IP 显示区
        TextView tvPublicIp = contentView.findViewById(R.id.tv_public_ip);   // 公网 IP 显示区
        TextView tvIpLocation = contentView.findViewById(R.id.tv_ip_location); // IP归属地显示区
        TextView tvVpnStatus = contentView.findViewById(R.id.tv_vpn_status); // VPN状态显示区

        // 本地 IP 和 VPN 状态查询为轻量操作，直接在当前线程执行（速度很快，不会卡界面）
        if (tvWifiIp != null) tvWifiIp.setText(ToolHelper.getWifiIpAddress(context));
        if (tvMobileIp != null) tvMobileIp.setText(ToolHelper.getMobileIpAddress());
        if (tvVpnStatus != null) tvVpnStatus.setText(ToolHelper.checkVpnStatus(context));

        // 公网 IP 查询涉及网络 I/O（需要联网），必须在后台线程执行，否则会崩溃
        fetchPublicIpInfo(context, tvPublicIp, tvIpLocation, executor);
    }

    /**
     * 异步获取公网 IP 地址及归属地信息。
     * <p>
     * "异步"的意思是：这个方法启动查询后不会等待结果，而是让后台线程去查询，
     * 查到结果后再回到主线程更新界面。就像你让朋友帮你查快递，查到了再告诉你。
     * </p>
     * <p>
     * 依次尝试 PUBLIC_IP_APIS 中配置的多个 API，任一成功即停止（降级策略）。
     * 所有 API 均失败时，显示"获取失败"提示。
     * </p>
     *
     * @param context      应用上下文，用于通过 safeRunOnUiThread 回到主线程更新 UI
     * @param tvPublicIp   显示公网 IP 地址的 TextView，可为 null
     * @param tvIpLocation 显示 IP 归属地信息的 TextView，可为 null
     * @param executor     线程池执行器，用于执行网络请求
     */
    private void fetchPublicIpInfo(Context context, TextView tvPublicIp, TextView tvIpLocation, ExecutorService executor) {
        // 两个目标 TextView 都为 null 时无需查询（页面上没有显示区域就不查了）
        if (tvPublicIp == null && tvIpLocation == null) return;

        // 把查询任务交给后台线程执行
        executor.execute(() -> {
            boolean success = false;
            // 降级策略：依次尝试多个 API，就像依次拨打不同的客服电话，打通一个就行
            for (String[] api : PUBLIC_IP_APIS) {
                if (success) break;
                try {
                    String urlStr = api[0];
                    // 创建HTTP连接，准备向API服务器发起请求
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");  // 使用GET请求方式
                    conn.setConnectTimeout(5000);   // 连接超时5秒（5秒内连不上就放弃）
                    conn.setReadTimeout(5000);      // 读取超时5秒（5秒内没收到响应就放弃）
                    conn.setRequestProperty("User-Agent", "GameMatrixApp/1.0");  // 告诉服务器我们是谁

                    // HTTPS API 需设置 Accept 头，告诉服务器我们想要JSON格式的响应
                    if (urlStr.startsWith("https")) {
                        conn.setRequestProperty("Accept", "application/json");
                    }

                    // 读取服务器返回的数据
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) resp.append(line);
                    reader.close();
                    conn.disconnect();  // 用完连接记得关闭，释放资源

                    // 把服务器返回的JSON字符串解析为JSON对象
                    org.json.JSONObject json = new org.json.JSONObject(resp.toString());

                    // api[1] 是当前 API 返回 JSON 中 IP 地址对应的字段名
                    // 例如 ip-api.com 的JSON里IP字段叫"query"，其他API叫"ip"
                    String publicIp = json.optString(api[1], "");
                    if (publicIp.isEmpty()) continue;  // 没取到IP就试下一个API

                    // api[2]-api[5] 分别对应国家、地区、城市、运营商字段名
                    // 把这些信息拼接成一段归属地描述文字
                    String loc = json.optString(api[2], "") + " " +
                            json.optString(api[3], "") + " " +
                            json.optString(api[4], "") +
                            "\n运营商: " + json.optString(api[5], "未知");
                    final String fIp = publicIp;
                    // 清理多余空白：合并连续空格，保留"运营商:"前的换行
                    final String fLoc = loc.trim().replaceAll("\\s+", " ").replace("\n运营商: ", "\n运营商: ");

                    // 回到主线程更新UI（Android不允许在后台线程直接修改界面）
                    ToolHelper.safeRunOnUiThread(context, () -> {
                        if (tvPublicIp != null) tvPublicIp.setText(fIp);
                        if (tvIpLocation != null) tvIpLocation.setText(fLoc);
                    });
                    success = true;  // 标记查询成功，不再尝试下一个API
                } catch (Exception ignored) {
                    // 某个API查询失败，记录日志后继续尝试下一个
                    Log.w(TAG, "Fetch public IP info failed: " + ignored.getMessage());
                }
            }
            // 所有 API 均失败时的兜底处理（三个客服电话都打不通）
            if (!success) {
                ToolHelper.safeRunOnUiThread(context, () -> {
                    if (tvPublicIp != null) tvPublicIp.setText(context.getString(R.string.tool_ip_fetch_failed));
                    if (tvIpLocation != null) tvIpLocation.setText(context.getString(R.string.tool_ip_check_network));
                });
            }
        });
    }
}

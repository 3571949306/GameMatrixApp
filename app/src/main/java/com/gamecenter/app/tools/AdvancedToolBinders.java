package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AdvancedToolBinders {

    private static final int QR_SIZE = 720;
    private static final String REPORT_LABEL = "GameCenter diagnostics";

    private AdvancedToolBinders() {
    }

    public static void bindNetworkDiagnosis(Context context, View contentView, ExecutorService executor) {
        TextView result = contentView.findViewById(R.id.tv_network_diagnosis_result);
        MaterialButton button = contentView.findViewById(R.id.btn_network_diagnose);
        if (button == null) return;
        button.setOnClickListener(v -> {
            setText(result, "正在体检网络...");
            executor.execute(() -> {
                String text = buildNetworkDiagnosis(context);
                postText(contentView, result, text);
            });
        });
    }

    public static void bindDiagnosticReport(Context context, View contentView, ExecutorService executor) {
        TextView result = contentView.findViewById(R.id.tv_report_result);
        MaterialButton generate = contentView.findViewById(R.id.btn_generate_report);
        MaterialButton copy = contentView.findViewById(R.id.btn_copy_report);
        MaterialButton share = contentView.findViewById(R.id.btn_share_report);
        final String[] latest = new String[]{""};

        if (generate != null) {
            generate.setOnClickListener(v -> {
                setText(result, "正在生成诊断报告...");
                executor.execute(() -> {
                    String report = buildDiagnosticReport(context);
                    latest[0] = report;
                    postText(contentView, result, report);
                });
            });
        }
        if (copy != null) {
            copy.setOnClickListener(v -> {
                if (latest[0].isEmpty()) latest[0] = buildDiagnosticReport(context);
                copyText(context, REPORT_LABEL, latest[0]);
                Toast.makeText(context, "诊断报告已复制", Toast.LENGTH_SHORT).show();
            });
        }
        if (share != null) {
            share.setOnClickListener(v -> {
                if (latest[0].isEmpty()) latest[0] = buildDiagnosticReport(context);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, REPORT_LABEL);
                intent.putExtra(Intent.EXTRA_TEXT, latest[0]);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(intent, "分享诊断报告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
        }
    }

    public static void bindDnsLookup(Context context, View contentView, ExecutorService executor) {
        EditText domainInput = contentView.findViewById(R.id.et_dns_domain);
        TextView result = contentView.findViewById(R.id.tv_dns_lookup_result);
        MaterialButton button = contentView.findViewById(R.id.btn_dns_lookup);
        if (button == null) return;
        if (domainInput != null && TextUtils.isEmpty(domainInput.getText())) {
            domainInput.setText("example.com");
        }
        button.setOnClickListener(v -> {
            String domain = domainInput != null ? domainInput.getText().toString().trim() : "";
            if (domain.isEmpty()) {
                Toast.makeText(context, "请输入域名", Toast.LENGTH_SHORT).show();
                return;
            }
            setText(result, "正在查询 DNS...");
            executor.execute(() -> {
                String lookup = lookupDns(domain);
                postText(contentView, result, lookup);
            });
        });
    }

    public static void bindLanScan(Context context, View contentView, ExecutorService executor) {
        EditText prefixInput = contentView.findViewById(R.id.et_lan_prefix);
        TextView result = contentView.findViewById(R.id.tv_lan_scan_result);
        MaterialButton button = contentView.findViewById(R.id.btn_lan_scan);
        if (prefixInput != null && TextUtils.isEmpty(prefixInput.getText())) {
            prefixInput.setText(suggestLanPrefix(context));
        }
        if (button == null) return;
        button.setOnClickListener(v -> {
            String prefix = prefixInput != null ? prefixInput.getText().toString().trim() : "";
            if (!prefix.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                Toast.makeText(context, "请输入前三段网段，例如 192.168.1", Toast.LENGTH_SHORT).show();
                return;
            }
            setText(result, "正在扫描 " + prefix + ".1-254...");
            executor.execute(() -> {
                String scanResult = scanLan(prefix);
                postText(contentView, result, scanResult);
            });
        });
    }

    public static void bindTextCodec(Context context, View contentView) {
        EditText input = contentView.findViewById(R.id.et_text_codec_input);
        TextView result = contentView.findViewById(R.id.tv_text_codec_result);

        bindTransform(contentView, R.id.btn_text_url_encode, input, result, text ->
                URLEncoder.encode(text, StandardCharsets.UTF_8.name()));
        bindTransform(contentView, R.id.btn_text_url_decode, input, result, text ->
                java.net.URLDecoder.decode(text, StandardCharsets.UTF_8.name()));
        bindTransform(contentView, R.id.btn_text_base64_encode, input, result, text ->
                Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        bindTransform(contentView, R.id.btn_text_base64_decode, input, result, text ->
                new String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8));
        bindTransform(contentView, R.id.btn_text_json_format, input, result, AdvancedToolBinders::formatJson);

        View timestampNow = contentView.findViewById(R.id.btn_text_timestamp_now);
        if (timestampNow != null) {
            timestampNow.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                setText(result, "秒: " + (now / 1000) + "\n毫秒: " + now + "\n本地时间: " + formatDate(now));
            });
        }

        View timestampConvert = contentView.findViewById(R.id.btn_text_timestamp_convert);
        if (timestampConvert != null) {
            timestampConvert.setOnClickListener(v -> {
                String text = readInput(input);
                try {
                    long value = Long.parseLong(text.trim());
                    long millis = text.trim().length() <= 10 ? value * 1000L : value;
                    setText(result, "本地时间: " + formatDate(millis) + "\nUTC: " + formatUtc(millis));
                } catch (Exception e) {
                    setText(result, "时间戳转换失败: " + e.getMessage());
                }
            });
        }

        View copy = contentView.findViewById(R.id.btn_text_copy_result);
        if (copy != null) {
            copy.setOnClickListener(v -> {
                String text = result != null ? result.getText().toString() : "";
                copyText(context, "tool-result", text);
                Toast.makeText(context, "结果已复制", Toast.LENGTH_SHORT).show();
            });
        }
    }

    public static void bindFileHash(Context context, View contentView, View.OnClickListener pickFileListener) {
        MaterialButton button = contentView.findViewById(R.id.btn_pick_file_hash);
        if (button != null) {
            button.setOnClickListener(pickFileListener);
        }
    }

    public static void handleFileHashResult(Context context, View contentView, Uri uri, ExecutorService executor) {
        if (contentView == null || uri == null) return;
        TextView nameView = contentView.findViewById(R.id.tv_file_hash_name);
        TextView resultView = contentView.findViewById(R.id.tv_file_hash_result);
        setText(nameView, "文件: " + getDisplayName(context, uri));
        setText(resultView, "正在计算文件哈希...");
        executor.execute(() -> {
            String text = hashFile(context, uri);
            postText(contentView, resultView, text);
        });
    }

    public static void bindQrPlus(Context context, View contentView, View.OnClickListener pickImageListener) {
        EditText input = contentView.findViewById(R.id.et_qr_plus_input);
        TextView result = contentView.findViewById(R.id.tv_qr_plus_result);
        ImageView preview = contentView.findViewById(R.id.iv_qr_plus);

        View generate = contentView.findViewById(R.id.btn_qr_plus_generate);
        if (generate != null) {
            generate.setOnClickListener(v -> renderQr(context, preview, result, readInput(input)));
        }

        View decode = contentView.findViewById(R.id.btn_qr_plus_decode_image);
        if (decode != null) {
            decode.setOnClickListener(pickImageListener);
        }

        View clipboard = contentView.findViewById(R.id.btn_qr_plus_clipboard);
        if (clipboard != null) {
            clipboard.setOnClickListener(v -> {
                String text = readClipboard(context);
                if (input != null) input.setText(text);
                renderQr(context, preview, result, text);
            });
        }

        View wifi = contentView.findViewById(R.id.btn_qr_plus_wifi);
        if (wifi != null) {
            wifi.setOnClickListener(v -> {
                String[] parts = readInput(input).split(",", -1);
                if (parts.length < 2) {
                    setText(result, "WiFi码格式: SSID,密码,加密方式，可省略加密方式默认 WPA");
                    return;
                }
                String auth = parts.length >= 3 && !parts[2].trim().isEmpty() ? parts[2].trim() : "WPA";
                String qr = "WIFI:T:" + escapeWifi(auth) + ";S:" + escapeWifi(parts[0].trim())
                        + ";P:" + escapeWifi(parts[1].trim()) + ";;";
                renderQr(context, preview, result, qr);
            });
        }

        View vcard = contentView.findViewById(R.id.btn_qr_plus_vcard);
        if (vcard != null) {
            vcard.setOnClickListener(v -> {
                String[] parts = readInput(input).split(",", -1);
                if (parts.length < 2) {
                    setText(result, "名片码格式: 姓名,电话,邮箱，可省略邮箱");
                    return;
                }
                String email = parts.length >= 3 ? parts[2].trim() : "";
                String qr = "BEGIN:VCARD\nVERSION:3.0\nFN:" + parts[0].trim()
                        + "\nTEL:" + parts[1].trim()
                        + (email.isEmpty() ? "" : "\nEMAIL:" + email)
                        + "\nEND:VCARD";
                renderQr(context, preview, result, qr);
            });
        }
    }

    public static void handleQrImageResult(Context context, View contentView, Uri uri, ExecutorService executor) {
        if (contentView == null || uri == null) return;
        TextView resultView = contentView.findViewById(R.id.tv_qr_plus_result);
        setText(resultView, "正在识别二维码图片...");
        executor.execute(() -> {
            String text;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                text = decodeQr(bitmap);
            } catch (Exception e) {
                text = "识别失败: " + e.getMessage();
            }
            postText(contentView, resultView, text);
        });
    }

    public static void bindColorPlus(Context context, View contentView, View.OnClickListener pickImageListener) {
        EditText fgInput = contentView.findViewById(R.id.et_color_plus_fg);
        EditText bgInput = contentView.findViewById(R.id.et_color_plus_bg);
        View fgPreview = contentView.findViewById(R.id.v_color_plus_fg);
        View bgPreview = contentView.findViewById(R.id.v_color_plus_bg);
        TextView result = contentView.findViewById(R.id.tv_color_plus_result);

        View contrast = contentView.findViewById(R.id.btn_color_plus_contrast);
        if (contrast != null) {
            contrast.setOnClickListener(v -> {
                try {
                    int fg = Color.parseColor(readInput(fgInput));
                    int bg = Color.parseColor(readInput(bgInput));
                    if (fgPreview != null) fgPreview.setBackgroundColor(fg);
                    if (bgPreview != null) bgPreview.setBackgroundColor(bg);
                    double ratio = contrastRatio(fg, bg);
                    String aaSmall = ratio >= 4.5 ? "通过" : "不通过";
                    String aaLarge = ratio >= 3.0 ? "通过" : "不通过";
                    setText(result, String.format(Locale.CHINA,
                            "对比度: %.2f:1\nWCAG AA 正文: %s\nWCAG AA 大字号: %s\n前景: %s\n背景: %s",
                            ratio, aaSmall, aaLarge, toHex(fg), toHex(bg)));
                } catch (Exception e) {
                    setText(result, "颜色格式错误，请输入 #RRGGBB 或 #AARRGGBB");
                }
            });
        }

        View pickImage = contentView.findViewById(R.id.btn_color_plus_pick_image);
        if (pickImage != null) {
            pickImage.setOnClickListener(pickImageListener);
        }
    }

    public static void handleColorImageResult(Context context, View contentView, Uri uri, ExecutorService executor) {
        if (contentView == null || uri == null) return;
        TextView resultView = contentView.findViewById(R.id.tv_color_plus_result);
        View preview = contentView.findViewById(R.id.v_color_plus_image);
        setText(resultView, "正在分析图片主色...");
        executor.execute(() -> {
            String text;
            int color = Color.TRANSPARENT;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                color = averageColor(bitmap);
                text = "图片平均主色: " + toHex(color)
                        + "\nRGB: " + Color.red(color) + ", " + Color.green(color) + ", " + Color.blue(color);
            } catch (Exception e) {
                text = "图片取色失败: " + e.getMessage();
            }
            final int finalColor = color;
            final String finalText = text;
            contentView.post(() -> {
                if (preview != null && finalColor != Color.TRANSPARENT) {
                    preview.setBackgroundColor(finalColor);
                }
                setText(resultView, finalText);
            });
        });
    }

    public static void bindPermissionPrivacy(Context context, View contentView) {
        TextView desc = contentView.findViewById(R.id.tv_permission_privacy_desc);
        TextView result = contentView.findViewById(R.id.tv_permission_privacy_result);
        View settings = contentView.findViewById(R.id.btn_permission_app_settings);
        if (settings != null) {
            settings.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            });
        }
        View copy = contentView.findViewById(R.id.btn_permission_copy_privacy);
        if (copy != null) {
            copy.setOnClickListener(v -> {
                String text = desc != null ? desc.getText().toString() : "";
                copyText(context, "privacy-note", text);
                setText(result, "权限与隐私说明已复制");
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static String buildNetworkDiagnosis(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("网络体检时间: ").append(formatDate(System.currentTimeMillis())).append("\n\n");
        sb.append(getNetworkSummary(context)).append("\n");
        sb.append("WiFi IP: ").append(getWifiIp(context)).append("\n");
        sb.append("DNS: ").append(TextUtils.join(", ", getDnsServers(context))).append("\n\n");

        long dnsTcp = tcpPing("223.5.5.5", 53, 2500);
        long webTcp = tcpPing("www.baidu.com", 443, 3500);
        sb.append("DNS连通性: ").append(dnsTcp >= 0 ? dnsTcp + " ms" : "失败").append("\n");
        sb.append("HTTPS连通性: ").append(webTcp >= 0 ? webTcp + " ms" : "失败").append("\n");
        sb.append("公网IP: ").append(fetchPublicIp()).append("\n\n");

        if (dnsTcp >= 0 && webTcp >= 0) {
            sb.append("结论: 网络基础连通性正常。");
        } else if (dnsTcp >= 0) {
            sb.append("结论: DNS 可达，但 HTTPS 连接异常，建议检查代理、VPN 或防火墙。");
        } else {
            sb.append("结论: DNS 和外网连接均异常，建议检查 WiFi/移动网络或路由器。");
        }
        return sb.toString();
    }

    private static String buildDiagnosticReport(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("GameCenterApp 诊断报告\n");
        sb.append("生成时间: ").append(formatDate(System.currentTimeMillis())).append("\n");
        sb.append("应用版本: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("系统版本: Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("设备型号: ").append(Build.BRAND).append(" ").append(Build.MODEL).append("\n");
        sb.append("网络: ").append(getNetworkSummary(context)).append("\n");
        sb.append("WiFi IP: ").append(getWifiIp(context)).append("\n");
        sb.append("DNS: ").append(TextUtils.join(", ", getDnsServers(context))).append("\n");
        sb.append("电池: ").append(getBatterySummary(context)).append("\n");
        sb.append("\n");
        sb.append(buildNetworkDiagnosis(context));
        return sb.toString();
    }

    private static String getNetworkSummary(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "不可用";
            Network network = cm.getActiveNetwork();
            if (network == null) return "未连接";
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) return "未知网络";
            List<String> parts = new ArrayList<>();
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) parts.add("WiFi");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) parts.add("移动数据");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) parts.add("以太网");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) parts.add("VPN");
            if (parts.isEmpty()) parts.add("其他");
            return TextUtils.join("+", parts);
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
    }

    private static String getBatterySummary(Context context) {
        Intent battery = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return "未知";
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int percent = scale > 0 ? Math.round(level * 100f / scale) : -1;
        String charging = status == BatteryManager.BATTERY_STATUS_CHARGING ? "充电中"
                : status == BatteryManager.BATTERY_STATUS_FULL ? "已充满" : "未充电";
        return percent + "% / " + charging;
    }

    private static List<String> getDnsServers(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return Collections.singletonList("未知");
            Network network = cm.getActiveNetwork();
            LinkProperties props = network != null ? cm.getLinkProperties(network) : null;
            if (props == null || props.getDnsServers().isEmpty()) return Collections.singletonList("未知");
            List<String> dns = new ArrayList<>();
            for (InetAddress address : props.getDnsServers()) {
                dns.add(address.getHostAddress());
            }
            return dns;
        } catch (Exception e) {
            return Collections.singletonList("读取失败");
        }
    }

    private static String getWifiIp(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return "未知";
            int ip = wifi.getConnectionInfo().getIpAddress();
            if (ip == 0) return "未知";
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) {
            return "未知";
        }
    }

    private static long tcpPing(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String fetchPublicIp() {
        String[] apis = {"https://api.ipify.org", "https://ifconfig.me/ip"};
        for (String api : apis) {
            try {
                String text = httpGet(api);
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return "获取失败";
    }

    private static String lookupDns(String domain) {
        StringBuilder sb = new StringBuilder("DNS 查询: ").append(domain).append("\n");
        String[] types = {"A", "AAAA", "CNAME", "MX", "TXT"};
        for (String type : types) {
            sb.append("\n[").append(type).append("]\n");
            try {
                String url = "https://dns.google/resolve?name="
                        + URLEncoder.encode(domain, StandardCharsets.UTF_8.name())
                        + "&type=" + type;
                JSONObject json = new JSONObject(httpGet(url));
                JSONArray answers = json.optJSONArray("Answer");
                if (answers == null || answers.length() == 0) {
                    sb.append("无记录，状态码: ").append(json.optInt("Status", -1)).append("\n");
                    continue;
                }
                for (int i = 0; i < answers.length(); i++) {
                    JSONObject answer = answers.getJSONObject(i);
                    sb.append(answer.optString("data", ""))
                            .append("  TTL=").append(answer.optInt("TTL", 0)).append("\n");
                }
            } catch (Exception e) {
                sb.append("查询失败: ").append(e.getMessage()).append("\n");
            }
        }
        return sb.toString();
    }

    private static String scanLan(String prefix) {
        List<String> found = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(254);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        long start = System.currentTimeMillis();
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + "." + i;
            pool.execute(() -> {
                try {
                    boolean up = false;
                    try {
                        up = InetAddress.getByName(ip).isReachable(220);
                    } catch (Exception ignored) {
                    }
                    if (!up) {
                        up = tcpPing(ip, 80, 180) >= 0 || tcpPing(ip, 443, 180) >= 0;
                    }
                    if (up) {
                        found.add(ip);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(12, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        Collections.sort(found);
        StringBuilder sb = new StringBuilder();
        sb.append("网段: ").append(prefix).append(".0/24\n");
        sb.append("耗时: ").append(System.currentTimeMillis() - start).append(" ms\n");
        sb.append("发现设备: ").append(found.size()).append("\n");
        for (String ip : found) {
            sb.append("  ").append(ip).append("\n");
        }
        if (found.isEmpty()) {
            sb.append("未发现可达设备，可能被系统或路由器限制。");
        }
        return sb.toString();
    }

    private static String suggestLanPrefix(Context context) {
        String ip = getWifiIp(context);
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return "192.168.1";
    }

    private static void bindTransform(View contentView, int buttonId, EditText input, TextView result, TextTransform transform) {
        View button = contentView.findViewById(buttonId);
        if (button == null) return;
        button.setOnClickListener(v -> {
            try {
                setText(result, transform.apply(readInput(input)));
            } catch (Exception e) {
                setText(result, "处理失败: " + e.getMessage());
            }
        });
    }

    private static String formatJson(String text) throws Exception {
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            return new JSONObject(trimmed).toString(2);
        }
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed).toString(2);
        }
        return "请输入 JSON 对象或数组";
    }

    private static String hashFile(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return "无法读取文件";
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                md5.update(buffer, 0, read);
                sha1.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
                total += read;
            }
            return "大小: " + total + " bytes\n"
                    + "MD5: " + hex(md5.digest()) + "\n"
                    + "SHA-1: " + hex(sha1.digest()) + "\n"
                    + "SHA-256: " + hex(sha256.digest());
        } catch (Exception e) {
            return "文件哈希计算失败: " + e.getMessage();
        }
    }

    private static void renderQr(Context context, ImageView preview, TextView result, String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(context, "请输入二维码内容", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap bitmap = createQr(text);
            if (preview != null) {
                preview.setImageBitmap(bitmap);
                preview.setVisibility(View.VISIBLE);
            }
            setText(result, text);
        } catch (Exception e) {
            setText(result, "二维码生成失败: " + e.getMessage());
        }
    }

    private static Bitmap createQr(String text) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
        int[] pixels = new int[QR_SIZE * QR_SIZE];
        for (int y = 0; y < QR_SIZE; y++) {
            int offset = y * QR_SIZE;
            for (int x = 0; x < QR_SIZE; x++) {
                pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888);
    }

    private static String decodeQr(Bitmap bitmap) throws Exception {
        if (bitmap == null) return "图片读取失败";
        Bitmap working = scaleBitmap(bitmap, 1200);
        int width = working.getWidth();
        int height = working.getHeight();
        int[] pixels = new int[width * height];
        working.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(binaryBitmap, hints);
        return result != null ? result.getText() : "未识别到二维码";
    }

    private static Bitmap scaleBitmap(Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max <= maxSide) return bitmap;
        float scale = maxSide / (float) max;
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
    }

    private static String escapeWifi(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:");
    }

    private static int averageColor(Bitmap bitmap) {
        if (bitmap == null) return Color.TRANSPARENT;
        Bitmap working = scaleBitmap(bitmap, 600);
        long r = 0;
        long g = 0;
        long b = 0;
        long count = 0;
        int step = Math.max(1, (working.getWidth() * working.getHeight()) / 30000);
        for (int y = 0; y < working.getHeight(); y += step) {
            for (int x = 0; x < working.getWidth(); x += step) {
                int color = working.getPixel(x, y);
                if (Color.alpha(color) < 32) continue;
                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
                count++;
            }
        }
        if (count == 0) return Color.TRANSPARENT;
        return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
    }

    private static double contrastRatio(int fg, int bg) {
        double l1 = relativeLuminance(fg) + 0.05;
        double l2 = relativeLuminance(bg) + 0.05;
        return Math.max(l1, l2) / Math.min(l1, l2);
    }

    private static double relativeLuminance(int color) {
        double r = linear(Color.red(color) / 255.0);
        double g = linear(Color.green(color) / 255.0);
        double b = linear(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "GameCenterApp/" + BuildConfig.VERSION_NAME);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String getDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "已选择文件";
    }

    private static String readInput(EditText input) {
        return input != null && input.getText() != null ? input.getText().toString() : "";
    }

    private static String readClipboard(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
            CharSequence text = item.coerceToText(context);
            return text != null ? text.toString() : "";
        }
        return "";
    }

    private static void copyText(Context context, String label, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    private static void setText(TextView view, String text) {
        if (view != null) {
            view.setText(text);
        }
    }

    private static void postText(View anchor, TextView view, String text) {
        if (anchor != null) {
            anchor.post(() -> setText(view, text));
        }
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(millis));
    }

    private static String formatUtc(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String toHex(int color) {
        if (Color.alpha(color) == 255) {
            return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
        }
        return String.format(Locale.US, "#%02X%02X%02X%02X",
                Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
    }

    private interface TextTransform {
        String apply(String text) throws Exception;
    }
}

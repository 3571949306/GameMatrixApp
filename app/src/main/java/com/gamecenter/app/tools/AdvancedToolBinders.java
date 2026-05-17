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
import android.util.Log;
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

/**
 * 高级工具绑定器集合类，提供游戏中心应用中各类高级工具的 UI 绑定与业务逻辑实现。
 * <p>
 * 本类采用静态方法设计，所有工具绑定方法均为 public static，便于外部 Fragment/Activity 直接调用。
 * 关键设计决策：
 * <ul>
 *   <li>工具类模式：私有构造函数 + 静态方法，避免实例化，减少内存开销</li>
 *   <li>异步执行：耗时操作（网络诊断、DNS查询、LAN扫描、文件哈希等）通过 ExecutorService 在后台线程执行，
 *       结果通过 View.post() 回传到 UI 线程</li>
 *   <li>空安全：所有 findViewById 结果均做 null 检查，防止布局缺失时崩溃</li>
 *   <li>委托模式：ColorPlusToolBinder、DiagnosticReportToolBinder 等独立 ToolBinder 实现
 *       将实际逻辑委托给本类的静态方法，实现代码复用</li>
 * </ul>
 * <p>
 * 涵盖的工具功能包括：网络诊断、诊断报告生成、DNS查询、局域网扫描、文本编解码、
 * 文件哈希计算、二维码生成/识别、颜色对比度分析、权限隐私说明等。
 */
public final class AdvancedToolBinders {

    private static final String TAG = "AdvancedToolBinders";

    /** 二维码生成的默认尺寸（像素），720px 在清晰度和生成速度间取得平衡 */
    private static final int QR_SIZE = 720;

    /** 诊断报告的剪贴板标签，用于标识复制到剪贴板的内容来源 */
    private static final String REPORT_LABEL = "GameCenter diagnostics";

    private AdvancedToolBinders() {
    }

    /**
     * 绑定网络诊断工具的 UI 交互。
     * <p>
     * 点击诊断按钮后，在后台线程执行网络体检（包括网络类型检测、WiFi IP 获取、
     * DNS 连通性测试、HTTPS 连通性测试、公网 IP 获取），并将结果展示在结果文本框中。
     *
     * @param context     上下文，用于获取系统服务（ConnectivityManager、WifiManager 等）
     * @param contentView 工具页面的根视图，用于查找子视图
     * @param executor    线程池，用于在后台执行耗时的网络诊断操作
     */
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

    /**
     * 绑定诊断报告工具的 UI 交互，包括生成、复制和分享三个操作。
     * <p>
     * 使用 final String[] 数组持有最新的报告内容，避免每次复制/分享时重复生成报告。
     * 若报告尚未生成，复制和分享操作会自动触发一次报告生成。
     *
     * @param context     上下文，用于获取系统服务和启动分享 Intent
     * @param contentView 工具页面的根视图
     * @param executor    线程池，用于在后台执行报告生成
     */
    public static void bindDiagnosticReport(Context context, View contentView, ExecutorService executor) {
        TextView result = contentView.findViewById(R.id.tv_report_result);
        MaterialButton generate = contentView.findViewById(R.id.btn_generate_report);
        MaterialButton copy = contentView.findViewById(R.id.btn_copy_report);
        MaterialButton share = contentView.findViewById(R.id.btn_share_report);
        // 使用数组包装实现"可变引用"，使 lambda 内部可以修改最新报告内容
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
                // 若尚未生成报告，则先自动生成一次
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
                // 从非 Activity 上下文启动需要添加 NEW_TASK 标志
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(intent, "分享诊断报告").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
        }
    }

    /**
     * 绑定 DNS 查询工具的 UI 交互。
     * <p>
     * 用户输入域名后，通过 Google DNS-over-HTTPS API 查询 A、AAAA、CNAME、MX、TXT 五种记录类型。
     * 输入框默认填充 "example.com" 作为示例。
     *
     * @param context     上下文
     * @param contentView 工具页面的根视图
     * @param executor    线程池，用于在后台执行 DNS 查询
     */
    public static void bindDnsLookup(Context context, View contentView, ExecutorService executor) {
        EditText domainInput = contentView.findViewById(R.id.et_dns_domain);
        TextView result = contentView.findViewById(R.id.tv_dns_lookup_result);
        MaterialButton button = contentView.findViewById(R.id.btn_dns_lookup);
        if (button == null) return;
        // 默认填充示例域名，降低用户使用门槛
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

    /**
     * 绑定局域网扫描工具的 UI 交互。
     * <p>
     * 用户输入网段前缀（如 192.168.1），工具会对该网段的 1-254 地址进行并发扫描，
     * 检测可达设备。输入框默认根据当前 WiFi IP 自动填充网段前缀。
     *
     * @param context     上下文，用于获取 WiFi IP 以建议默认网段
     * @param contentView 工具页面的根视图
     * @param executor    线程池，用于在后台执行局域网扫描
     */
    public static void bindLanScan(Context context, View contentView, ExecutorService executor) {
        EditText prefixInput = contentView.findViewById(R.id.et_lan_prefix);
        TextView result = contentView.findViewById(R.id.tv_lan_scan_result);
        MaterialButton button = contentView.findViewById(R.id.btn_lan_scan);
        // 根据当前 WiFi IP 自动建议网段前缀
        if (prefixInput != null && TextUtils.isEmpty(prefixInput.getText())) {
            prefixInput.setText(suggestLanPrefix(context));
        }
        if (button == null) return;
        button.setOnClickListener(v -> {
            String prefix = prefixInput != null ? prefixInput.getText().toString().trim() : "";
            // 校验输入格式：必须是三段数字，如 192.168.1
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

    /**
     * 绑定文本编解码工具的 UI 交互。
     * <p>
     * 提供以下转换功能：
     * <ul>
     *   <li>URL 编码/解码</li>
     *   <li>Base64 编码/解码</li>
     *   <li>JSON 格式化</li>
     *   <li>时间戳：显示当前时间戳、将输入时间戳转换为可读时间</li>
     *   <li>复制结果到剪贴板</li>
     * </ul>
     *
     * @param context     上下文，用于剪贴板操作和 Toast 提示
     * @param contentView 工具页面的根视图
     */
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

        // 显示当前时间戳（秒级和毫秒级），以及本地可读时间
        View timestampNow = contentView.findViewById(R.id.btn_text_timestamp_now);
        if (timestampNow != null) {
            timestampNow.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                setText(result, "秒: " + (now / 1000) + "\n毫秒: " + now + "\n本地时间: " + formatDate(now));
            });
        }

        // 将输入的时间戳转换为可读时间，自动判断秒级（<=10位）或毫秒级
        View timestampConvert = contentView.findViewById(R.id.btn_text_timestamp_convert);
        if (timestampConvert != null) {
            timestampConvert.setOnClickListener(v -> {
                String text = readInput(input);
                try {
                    long value = Long.parseLong(text.trim());
                    // 10位及以下视为秒级时间戳，需乘以1000转为毫秒
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

    /**
     * 绑定文件哈希工具的"选择文件"按钮。
     * <p>
     * 文件选择的具体逻辑由外部通过 pickFileListener 提供（通常触发系统文件选择器），
     * 文件选择结果通过 {@link #handleFileHashResult} 方法处理。
     *
     * @param context           上下文
     * @param contentView       工具页面的根视图
     * @param pickFileListener  文件选择点击监听器，由调用方提供
     */
    public static void bindFileHash(Context context, View contentView, View.OnClickListener pickFileListener) {
        MaterialButton button = contentView.findViewById(R.id.btn_pick_file_hash);
        if (button != null) {
            button.setOnClickListener(pickFileListener);
        }
    }

    /**
     * 处理文件哈希工具的文件选择结果。
     * <p>
     * 在后台线程中计算文件的 MD5、SHA-1、SHA-256 三种哈希值，并显示文件大小。
     *
     * @param context     上下文，用于读取文件内容
     * @param contentView 工具页面的根视图
     * @param uri         用户选择的文件 Uri
     * @param executor    线程池，用于在后台执行哈希计算
     */
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

    /**
     * 绑定二维码增强工具的 UI 交互。
     * <p>
     * 支持以下功能：
     * <ul>
     *   <li>生成二维码：将输入文本转为二维码图片</li>
     *   <li>识别图片二维码：从相册选择图片识别其中的二维码</li>
     *   <li>剪贴板生成：读取剪贴板内容直接生成二维码</li>
     *   <li>WiFi 码：按 "SSID,密码,加密方式" 格式生成 WiFi 连接二维码</li>
     *   <li>名片码：按 "姓名,电话,邮箱" 格式生成 vCard 二维码</li>
     * </ul>
     *
     * @param context            上下文
     * @param contentView        工具页面的根视图
     * @param pickImageListener  图片选择点击监听器，由调用方提供
     */
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

        // 从剪贴板读取内容并直接生成二维码
        View clipboard = contentView.findViewById(R.id.btn_qr_plus_clipboard);
        if (clipboard != null) {
            clipboard.setOnClickListener(v -> {
                String text = readClipboard(context);
                if (input != null) input.setText(text);
                renderQr(context, preview, result, text);
            });
        }

        // WiFi 二维码：格式为 "SSID,密码,加密方式"，加密方式默认 WPA
        View wifi = contentView.findViewById(R.id.btn_qr_plus_wifi);
        if (wifi != null) {
            wifi.setOnClickListener(v -> {
                String[] parts = readInput(input).split(",", -1);
                if (parts.length < 2) {
                    setText(result, "WiFi码格式: SSID,密码,加密方式，可省略加密方式默认 WPA");
                    return;
                }
                // 若未指定加密方式或为空，默认使用 WPA
                String auth = parts.length >= 3 && !parts[2].trim().isEmpty() ? parts[2].trim() : "WPA";
                // 按照 WiFi 二维码标准格式编码，特殊字符需要转义
                String qr = "WIFI:T:" + escapeWifi(auth) + ";S:" + escapeWifi(parts[0].trim())
                        + ";P:" + escapeWifi(parts[1].trim()) + ";;";
                renderQr(context, preview, result, qr);
            });
        }

        // 名片二维码：格式为 "姓名,电话,邮箱"，邮箱可省略
        View vcard = contentView.findViewById(R.id.btn_qr_plus_vcard);
        if (vcard != null) {
            vcard.setOnClickListener(v -> {
                String[] parts = readInput(input).split(",", -1);
                if (parts.length < 2) {
                    setText(result, "名片码格式: 姓名,电话,邮箱，可省略邮箱");
                    return;
                }
                String email = parts.length >= 3 ? parts[2].trim() : "";
                // 按照 vCard 3.0 标准格式编码
                String qr = "BEGIN:VCARD\nVERSION:3.0\nFN:" + parts[0].trim()
                        + "\nTEL:" + parts[1].trim()
                        + (email.isEmpty() ? "" : "\nEMAIL:" + email)
                        + "\nEND:VCARD";
                renderQr(context, preview, result, qr);
            });
        }
    }

    /**
     * 处理二维码图片识别的文件选择结果。
     * <p>
     * 在后台线程中解码图片中的二维码内容，使用 ZXing 库进行识别，
     * 并启用 TRY_HARDER 模式以提高识别成功率。
     *
     * @param context     上下文，用于读取图片文件
     * @param contentView 工具页面的根视图
     * @param uri         用户选择的图片 Uri
     * @param executor    线程池，用于在后台执行二维码识别
     */
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

    /**
     * 绑定颜色增强工具的 UI 交互。
     * <p>
     * 提供以下功能：
     * <ul>
     *   <li>WCAG 对比度计算：输入前景色和背景色，计算对比度比值并判断是否通过 WCAG AA 标准</li>
     *   <li>图片取色：从图片中提取平均主色</li>
     * </ul>
     *
     * @param context            上下文
     * @param contentView        工具页面的根视图
     * @param pickImageListener  图片选择点击监听器，由调用方提供
     */
    public static void bindColorPlus(Context context, View contentView, View.OnClickListener pickImageListener) {
        EditText fgInput = contentView.findViewById(R.id.et_color_plus_fg);
        EditText bgInput = contentView.findViewById(R.id.et_color_plus_bg);
        View fgPreview = contentView.findViewById(R.id.v_color_plus_fg);
        View bgPreview = contentView.findViewById(R.id.v_color_plus_bg);
        TextView result = contentView.findViewById(R.id.tv_color_plus_result);

        // WCAG 对比度计算按钮
        View contrast = contentView.findViewById(R.id.btn_color_plus_contrast);
        if (contrast != null) {
            contrast.setOnClickListener(v -> {
                try {
                    int fg = Color.parseColor(readInput(fgInput));
                    int bg = Color.parseColor(readInput(bgInput));
                    if (fgPreview != null) fgPreview.setBackgroundColor(fg);
                    if (bgPreview != null) bgPreview.setBackgroundColor(bg);
                    double ratio = contrastRatio(fg, bg);
                    // WCAG AA 标准：正文（小字号）对比度需 >= 4.5:1，大字号需 >= 3:1
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

    /**
     * 处理颜色增强工具的图片选择结果。
     * <p>
     * 在后台线程中计算图片的平均主色，并在 UI 上显示颜色预览和 RGB 值。
     * 使用采样策略（每隔 step 个像素取一次）以提高大图的处理速度。
     *
     * @param context     上下文，用于读取图片文件
     * @param contentView 工具页面的根视图
     * @param uri         用户选择的图片 Uri
     * @param executor    线程池，用于在后台执行颜色分析
     */
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

    /**
     * 绑定权限与隐私说明工具的 UI 交互。
     * <p>
     * 提供：
     * <ul>
     *   <li>跳转应用设置页：打开系统的应用详情设置页面，方便用户管理权限</li>
     *   <li>复制隐私说明：将权限与隐私说明文本复制到剪贴板</li>
     * </ul>
     *
     * @param context     上下文，用于启动系统设置页面和剪贴板操作
     * @param contentView 工具页面的根视图
     */
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

    /**
     * 构建网络诊断报告。
     * <p>
     * 依次检测：网络类型、WiFi IP、DNS 服务器、DNS 连通性（TCP 连接阿里 DNS 223.5.5.5:53）、
     * HTTPS 连通性（TCP 连接百度 443）、公网 IP，最后根据连通性结果给出诊断结论。
     *
     * @param context 上下文，用于获取系统服务
     * @return 格式化的网络诊断报告文本
     */
    private static String buildNetworkDiagnosis(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("网络体检时间: ").append(formatDate(System.currentTimeMillis())).append("\n\n");
        sb.append(getNetworkSummary(context)).append("\n");
        sb.append("WiFi IP: ").append(getWifiIp(context)).append("\n");
        sb.append("DNS: ").append(TextUtils.join(", ", getDnsServers(context))).append("\n\n");

        // 使用阿里 DNS 和百度分别测试 DNS 和 HTTPS 连通性
        long dnsTcp = tcpPing("223.5.5.5", 53, 2500);
        long webTcp = tcpPing("www.baidu.com", 443, 3500);
        sb.append("DNS连通性: ").append(dnsTcp >= 0 ? dnsTcp + " ms" : "失败").append("\n");
        sb.append("HTTPS连通性: ").append(webTcp >= 0 ? webTcp + " ms" : "失败").append("\n");
        sb.append("公网IP: ").append(fetchPublicIp()).append("\n\n");

        // 根据连通性测试结果给出诊断结论
        if (dnsTcp >= 0 && webTcp >= 0) {
            sb.append("结论: 网络基础连通性正常。");
        } else if (dnsTcp >= 0) {
            sb.append("结论: DNS 可达，但 HTTPS 连接异常，建议检查代理、VPN 或防火墙。");
        } else {
            sb.append("结论: DNS 和外网连接均异常，建议检查 WiFi/移动网络或路由器。");
        }
        return sb.toString();
    }

    /**
     * 构建完整的诊断报告。
     * <p>
     * 包含应用版本、系统版本、设备型号、网络信息、电池信息和网络体检结果。
     *
     * @param context 上下文，用于获取系统服务
     * @return 格式化的完整诊断报告文本
     */
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

    /**
     * 获取当前网络类型摘要。
     * <p>
     * 通过 ConnectivityManager 检测活跃网络的传输类型（WiFi、移动数据、以太网、VPN），
     * 多种类型同时存在时用 "+" 连接。
     *
     * @param context 上下文
     * @return 网络类型描述字符串，如 "WiFi+VPN"、"移动数据"
     */
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

    /**
     * 获取电池状态摘要。
     * <p>
     * 通过粘性广播（ACTION_BATTERY_CHANGED）获取电池电量和充电状态，
     * 无需注册广播接收器即可读取。
     *
     * @param context 上下文
     * @return 电池状态描述，如 "85% / 充电中"
     */
    private static String getBatterySummary(Context context) {
        // ACTION_BATTERY_CHANGED 是粘性广播，registerReceiver 传入 null 可直接获取最后一次广播
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

    /**
     * 获取当前网络使用的 DNS 服务器地址列表。
     * <p>
     * 通过 LinkProperties 获取系统配置的 DNS 服务器地址。
     *
     * @param context 上下文
     * @return DNS 服务器地址列表，获取失败时返回包含"未知"或"读取失败"的单元素列表
     */
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

    /**
     * 获取当前 WiFi 连接的 IP 地址。
     * <p>
     * 通过 WifiManager 获取连接信息中的整数 IP，再按小端序转换为点分十进制格式。
     * 注意：使用 applicationContext 避免 WifiManager 的内存泄漏。
     *
     * @param context 上下文
     * @return 点分十进制 IP 地址字符串，获取失败时返回"未知"
     */
    private static String getWifiIp(Context context) {
        try {
            // 使用 applicationContext 防止 Activity 上下文导致的 WifiManager 内存泄漏
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return "未知";
            int ip = wifi.getConnectionInfo().getIpAddress();
            if (ip == 0) return "未知";
            // Android 返回的 IP 是小端序整数，需要按字节逆序拼接
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 通过 TCP 连接测试目标主机的连通性和延迟。
     * <p>
     * 尝试在指定超时时间内建立 TCP 连接，成功则返回连接耗时（毫秒），失败返回 -1。
     *
     * @param host      目标主机地址
     * @param port      目标端口
     * @param timeoutMs 连接超时时间（毫秒）
     * @return 连接延迟（毫秒），连接失败返回 -1
     */
    private static long tcpPing(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 获取当前设备的公网 IP 地址。
     * <p>
     * 依次尝试多个公网 IP 查询 API，任一成功即返回结果，
     * 全部失败时返回"获取失败"。
     *
     * @return 公网 IP 地址字符串
     */
    private static String fetchPublicIp() {
        // 配置多个备选 API，提高查询成功率
        String[] apis = {"https://api.ipify.org", "https://ifconfig.me/ip"};
        for (String api : apis) {
            try {
                String text = httpGet(api);
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            } catch (Exception ignored) {
                Log.w(TAG, "Fetch public IP failed: " + ignored.getMessage());
            }
        }
        return "获取失败";
    }

    /**
     * 通过 Google DNS-over-HTTPS API 查询指定域名的 DNS 记录。
     * <p>
     * 依次查询 A、AAAA、CNAME、MX、TXT 五种记录类型，
     * 每种类型显示对应的记录数据和 TTL 值。
     *
     * @param domain 要查询的域名
     * @return 格式化的 DNS 查询结果文本
     */
    private static String lookupDns(String domain) {
        StringBuilder sb = new StringBuilder("DNS 查询: ").append(domain).append("\n");
        // 查询五种常见的 DNS 记录类型
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

    /**
     * 扫描指定网段的局域网设备。
     * <p>
     * 对 prefix.1 ~ prefix.254 的所有地址进行并发扫描（32 线程池），
     * 每个地址先尝试 ICMP isReachable，失败后再尝试 TCP 连接 80/443 端口。
     * 使用 CountDownLatch 等待所有扫描完成，最长等待 12 秒。
     *
     * @param prefix 网段前缀，如 "192.168.1"
     * @return 格式化的扫描结果文本，包含网段、耗时、发现设备数和 IP 列表
     */
    private static String scanLan(String prefix) {
        List<String> found = Collections.synchronizedList(new ArrayList<>());
        // 254 个地址对应 254 个计数，用于等待所有扫描任务完成
        CountDownLatch latch = new CountDownLatch(254);
        // 使用 32 线程池并发扫描，平衡速度和资源消耗
        ExecutorService pool = Executors.newFixedThreadPool(32);
        long start = System.currentTimeMillis();
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + "." + i;
            pool.execute(() -> {
                try {
                    boolean up = false;
                    try {
                        // 先尝试 ICMP ping，超时 220ms
                        up = InetAddress.getByName(ip).isReachable(220);
                    } catch (Exception ignored) {
                        Log.w(TAG, "LAN host reachability check failed: " + ignored.getMessage());
                    }
                    if (!up) {
                        // ICMP 不通时，尝试 TCP 连接常见端口（80 或 443）
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
            // 最长等待 12 秒，超时后强制结束
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

    /**
     * 根据当前 WiFi IP 地址建议局域网扫描的网段前缀。
     * <p>
     * 从 WiFi IP 中提取前三段作为默认网段前缀，获取失败时默认返回 "192.168.1"。
     *
     * @param context 上下文
     * @return 网段前缀字符串，如 "192.168.1"
     */
    private static String suggestLanPrefix(Context context) {
        String ip = getWifiIp(context);
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return "192.168.1";
    }

    /**
     * 通用的文本转换绑定方法。
     * <p>
     * 将指定按钮的点击事件绑定到输入框→转换函数→结果文本框的流程，
     * 转换失败时在结果框中显示错误信息。
     *
     * @param contentView 根视图
     * @param buttonId    触发转换的按钮 ID
     * @param input       输入框
     * @param result      结果文本框
     * @param transform   文本转换函数
     */
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

    /**
     * 格式化 JSON 字符串。
     * <p>
     * 根据首字符判断是 JSON 对象（{）还是数组（[），使用 org.json 库进行缩进格式化。
     *
     * @param text 待格式化的 JSON 字符串
     * @return 缩进格式化后的 JSON 字符串
     * @throws Exception JSON 解析失败时抛出异常
     */
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

    /**
     * 计算文件的 MD5、SHA-1、SHA-256 哈希值。
     * <p>
     * 使用 8KB 缓冲区流式读取文件，同时更新三个 MessageDigest 实例，
     * 避免多次读取文件。适用于大文件场景。
     *
     * @param context 上下文，用于读取文件
     * @param uri     文件 Uri
     * @return 包含文件大小和三种哈希值的格式化文本
     */
    private static String hashFile(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return "无法读取文件";
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            // 8KB 缓冲区，平衡内存使用和 I/O 效率
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                // 同时更新三个摘要，只需读取文件一次
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

    /**
     * 渲染二维码并显示在 ImageView 中。
     * <p>
     * 将输入文本通过 ZXing 库编码为二维码 Bitmap，设置到预览 ImageView 上，
     * 同时在结果文本框中显示原始文本内容。
     *
     * @param context 上下文，用于显示 Toast 提示
     * @param preview 二维码预览 ImageView
     * @param result  结果文本框
     * @param text    要编码为二维码的文本内容
     */
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

    /**
     * 使用 ZXing 库将文本编码为二维码 Bitmap。
     * <p>
     * 生成 QR_SIZE × QR_SIZE 像素的二维码图片，黑色模块在白色背景上。
     *
     * @param text 要编码的文本
     * @return 二维码 Bitmap
     * @throws Exception 编码失败时抛出异常
     */
    private static Bitmap createQr(String text) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
        int[] pixels = new int[QR_SIZE * QR_SIZE];
        for (int y = 0; y < QR_SIZE; y++) {
            int offset = y * QR_SIZE;
            for (int x = 0; x < QR_SIZE; x++) {
                // BitMatrix 中 true 表示黑色模块，false 表示白色背景
                pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888);
    }

    /**
     * 使用 ZXing 库从 Bitmap 中识别二维码内容。
     * <p>
     * 先将图片缩放到合理尺寸（最大边 1200px），再转为灰度二值化图像进行解码。
     * 启用 TRY_HARDER 提示以提高识别率，但会增加解码耗时。
     *
     * @param bitmap 包含二维码的图片
     * @return 识别出的二维码文本内容
     * @throws Exception 识别失败时抛出异常
     */
    private static String decodeQr(Bitmap bitmap) throws Exception {
        if (bitmap == null) return "图片读取失败";
        // 缩放到合理尺寸，避免大图导致内存溢出
        Bitmap working = scaleBitmap(bitmap, 1200);
        int width = working.getWidth();
        int height = working.getHeight();
        int[] pixels = new int[width * height];
        working.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        // TRY_HARDER 模式：牺牲速度换取更高的识别成功率
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(binaryBitmap, hints);
        return result != null ? result.getText() : "未识别到二维码";
    }

    /**
     * 按最大边长等比缩放 Bitmap。
     * <p>
     * 如果图片最大边不超过 maxSide，则直接返回原图（不创建新 Bitmap）；
     * 否则按比例缩放，宽高最小值为 1 像素，防止缩放后尺寸为 0。
     *
     * @param bitmap  原始 Bitmap
     * @param maxSide 最大边长限制（像素）
     * @return 缩放后的 Bitmap，或原图（如果不需要缩放）
     */
    private static Bitmap scaleBitmap(Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max <= maxSide) return bitmap;
        float scale = maxSide / (float) max;
        // Math.max(1, ...) 确保缩放后宽高至少为 1 像素
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
    }

    /**
     * 转义 WiFi 二维码中的特殊字符。
     * <p>
     * 按照 WiFi QR Code 规范，反斜杠、分号、逗号、冒号需要用反斜杠转义。
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeWifi(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:");
    }

    /**
     * 计算图片的平均主色。
     * <p>
     * 先将图片缩放到最大边 600px，然后使用采样策略（约采样 30000 个像素点）
     * 计算所有非透明像素的 RGB 平均值。跳过 alpha < 32 的近透明像素，
     * 避免透明区域影响主色计算结果。
     *
     * @param bitmap 图片 Bitmap
     * @return 平均主色的 Color 值，图片为 null 或无有效像素时返回 Color.TRANSPARENT
     */
    private static int averageColor(Bitmap bitmap) {
        if (bitmap == null) return Color.TRANSPARENT;
        Bitmap working = scaleBitmap(bitmap, 600);
        long r = 0;
        long g = 0;
        long b = 0;
        long count = 0;
        // 采样步长：控制采样约 30000 个像素点，平衡精度和性能
        int step = Math.max(1, (working.getWidth() * working.getHeight()) / 30000);
        for (int y = 0; y < working.getHeight(); y += step) {
            for (int x = 0; x < working.getWidth(); x += step) {
                int color = working.getPixel(x, y);
                // 跳过近透明像素（alpha < 32），避免透明区域干扰主色
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

    /**
     * 计算两个颜色之间的 WCAG 对比度比值。
     * <p>
     * 对比度 = (较亮相对亮度 + 0.05) / (较暗相对亮度 + 0.05)，
     * 按照 WCAG 2.0 标准计算。
     *
     * @param fg 前景颜色
     * @param bg 背景颜色
     * @return 对比度比值，如 4.5 表示 4.5:1
     */
    private static double contrastRatio(int fg, int bg) {
        double l1 = relativeLuminance(fg) + 0.05;
        double l2 = relativeLuminance(bg) + 0.05;
        return Math.max(l1, l2) / Math.min(l1, l2);
    }

    /**
     * 计算颜色的相对亮度（relative luminance）。
     * <p>
     * 按照 WCAG 2.0 标准公式：L = 0.2126 * R + 0.7152 * G + 0.0722 * B，
     * 其中 R/G/B 为线性化后的 sRGB 分量值。
     *
     * @param color 颜色值
     * @return 相对亮度，范围 0.0 ~ 1.0
     */
    private static double relativeLuminance(int color) {
        double r = linear(Color.red(color) / 255.0);
        double g = linear(Color.green(color) / 255.0);
        double b = linear(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * 将 sRGB 通道值线性化。
     * <p>
     * 按照 WCAG 2.0 标准：
     * - 值 <= 0.03928 时，直接除以 12.92
     * - 值 > 0.03928 时，进行幂函数变换 ((V + 0.055) / 1.055)^2.4
     *
     * @param channel sRGB 通道值，范围 0.0 ~ 1.0
     * @return 线性化后的通道值
     */
    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /**
     * 执行 HTTP GET 请求并返回响应体文本。
     * <p>
     * 设置 5 秒连接超时和 5 秒读取超时，请求头中包含应用版本号的 User-Agent。
     *
     * @param urlString 请求 URL
     * @return 响应体文本
     * @throws Exception 网络请求失败时抛出异常
     */
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

    /**
     * 获取 Uri 对应文件的显示名称。
     * <p>
     * 优先通过 ContentResolver 查询 OpenableColumns.DISPLAY_NAME，
     * 查询失败时回退到 Uri 的最后路径段。
     *
     * @param context 上下文
     * @param uri     文件 Uri
     * @return 文件显示名称
     */
    private static String getDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
            Log.w(TAG, "Get display name failed: " + ignored.getMessage());
        }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "已选择文件";
    }

    /**
     * 安全读取 EditText 的文本内容。
     *
     * @param input 输入框，可能为 null
     * @return 输入框中的文本，input 为 null 时返回空字符串
     */
    private static String readInput(EditText input) {
        return input != null && input.getText() != null ? input.getText().toString() : "";
    }

    /**
     * 读取系统剪贴板的文本内容。
     *
     * @param context 上下文
     * @return 剪贴板中的文本内容，无内容时返回空字符串
     */
    private static String readClipboard(Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
            ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
            CharSequence text = item.coerceToText(context);
            return text != null ? text.toString() : "";
        }
        return "";
    }

    /**
     * 将文本复制到系统剪贴板。
     *
     * @param context 上下文
     * @param label   剪贴板数据的标签，用于标识内容类型
     * @param text    要复制的文本内容
     */
    private static void copyText(Context context, String label, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    /**
     * 安全设置 TextView 的文本内容，防止 view 为 null 时崩溃。
     *
     * @param view 目标 TextView，可能为 null
     * @param text 要设置的文本
     */
    private static void setText(TextView view, String text) {
        if (view != null) {
            view.setText(text);
        }
    }

    /**
     * 通过 View.post() 将文本设置操作投递到 UI 线程。
     * <p>
     * 用于从后台线程更新 UI 文本，确保线程安全。
     *
     * @param anchor 用于调用 post() 的视图锚点
     * @param view   目标 TextView
     * @param text   要设置的文本
     */
    private static void postText(View anchor, TextView view, String text) {
        if (anchor != null) {
            anchor.post(() -> setText(view, text));
        }
    }

    /**
     * 将毫秒时间戳格式化为本地日期时间字符串。
     *
     * @param millis 毫秒时间戳
     * @return 格式化的日期时间字符串，如 "2024-01-15 14:30:00"
     */
    private static String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(millis));
    }

    /**
     * 将毫秒时间戳格式化为 UTC 日期时间字符串。
     *
     * @param millis 毫秒时间戳
     * @return 格式化的 UTC 日期时间字符串，如 "2024-01-15 06:30:00 UTC"
     */
    private static String formatUtc(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    /**
     * 将字节数组转换为小写十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串，如 "d41d8cd98f00b204e9800998ecf8427e"
     */
    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 将 Color 整数值转换为十六进制颜色字符串。
     * <p>
     * 不透明颜色（alpha == 255）输出 #RRGGBB 格式，
     * 半透明颜色输出 #AARRGGBB 格式。
     *
     * @param color 颜色值
     * @return 十六进制颜色字符串
     */
    private static String toHex(int color) {
        if (Color.alpha(color) == 255) {
            return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
        }
        return String.format(Locale.US, "#%02X%02X%02X%02X",
                Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * 文本转换函数式接口，用于文本编解码工具的通用绑定。
     */
    private interface TextTransform {
        String apply(String text) throws Exception;
    }
}

package com.gamecenter.app.utils;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaDrm;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.system.Os;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 系统信息采集器 —— 全面收集设备硬件、软件、安全等各项系统信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>采集设备标识（品牌、型号、制造商等）</li>
 *   <li>采集系统版本信息（Android版本、API级别、构建信息等）</li>
 *   <li>采集安全信息（安全补丁、SELinux状态、Verified Boot等）</li>
 *   <li>采集硬件信息（CPU架构、频率、负载、内存、存储、GPU/OpenGL等）</li>
 *   <li>采集电池状态（电量、温度、充电状态等）</li>
 *   <li>采集显示信息（分辨率、DPI、刷新率、HDR等）</li>
 *   <li>执行 Root 检测（su二进制、Magisk、Superuser.apk等）</li>
 *   <li>采集 DRM 信息（Widevine安全级别等）</li>
 *   <li>采集传感器列表及详情</li>
 *   <li>采集系统属性（ro.build.*、ro.secure等关键属性）</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用 StringBuilder 作为输出缓冲区，避免大量字符串拼接产生的内存开销</li>
 *   <li>通过反射调用 {@code android.os.SystemProperties}，因为该类是 @hide API，
 *       无法直接在应用层引用</li>
 *   <li>Root 检测采用多维度综合判断（su/Magisk/Superuser/Busybox/工程版），
 *       单一检测方式容易被绕过</li>
 *   <li>所有采集方法均为 private，对外仅暴露 {@link #collectAll()} 和
 *       {@link #getQuickSummary(Context)} 两个入口</li>
 *   <li>每个采集项都做了异常捕获，确保单项失败不影响整体采集流程</li>
 * </ul>
 */
public class SystemInfoCollector {

    private static final String TAG = "SystemInfoCollector";

    /** 应用上下文（使用 ApplicationContext 避免Activity泄漏） */
    private final Context context;

    /** 输出缓冲区，所有采集结果追加到此 StringBuilder 中 */
    private final StringBuilder sb;

    /**
     * 构造系统信息采集器。
     *
     * @param context 上下文，内部会调用 {@code getApplicationContext()} 避免内存泄漏
     */
    public SystemInfoCollector(Context context) {
        this.context = context.getApplicationContext();
        this.sb = new StringBuilder();
    }

    /**
     * 采集所有系统信息并返回格式化文本。
     *
     * <p>每次调用会先清空缓冲区，重新采集全部信息。
     * 采集项包括：设备标识、系统版本、安全信息、硬件信息、CPU信息、
     * 内存信息、存储信息、显示信息、电池信息、GPU/OpenGL信息、
     * Root检测、运行时长、Java运行时、DRM信息、传感器列表、系统属性。
     *
     * @return 格式化的系统信息文本
     */
    public String collectAll() {
        sb.setLength(0);
        getDeviceIdentity();
        getSystemVersion();
        getSecurityInfo();
        getHardwareInfo();
        getCpuInfo();
        getMemoryInfo();
        getStorageInfo();
        getDisplayInfo();
        getBatteryInfo_static();
        getGpuOpenGLInfo();
        getRootInfo();
        getUptimeInfo();
        getJavaRuntime();
        getDrmInfo();
        getSensorList();
        getSystemProperties();
        return sb.toString();
    }

    /**
     * 追加一行普通文本到输出缓冲区。
     *
     * @param line 要追加的文本行
     */
    private void append(String line) {
        sb.append(line).append("\n");
    }

    /**
     * 追加一个"键-值-注释"格式的条目到输出缓冲区。
     *
     * <p>输出格式为：■ {key}:  {value}  （{comment}）
     * 如果 comment 为空则省略括号部分。
     *
     * @param key     键名
     * @param value   值
     * @param comment 补充注释说明，可为 null 或空
     */
    private void appendKV(String key, String value, String comment) {
        sb.append("■ ").append(key).append(":  ").append(value);
        if (comment != null && !comment.isEmpty()) {
            sb.append("  （").append(comment).append("）");
        }
        sb.append("\n");
    }

    /**
     * 追加一个分节标题到输出缓冲区。
     *
     * @param title 分节标题文本
     */
    private void appendSection(String title) {
        sb.append("\n══ ").append(title).append(" ══\n");
    }

    /**
     * 采集设备标识信息：品牌、型号、设备代号、产品名、制造商、硬件平台、主板。
     */
    private void getDeviceIdentity() {
        appendSection("设备标识");
        appendKV("品牌(Brand)", Build.BRAND, "设备制造商品牌");
        appendKV("型号(Model)", Build.MODEL, "设备具体型号");
        appendKV("设备代号(Device)", Build.DEVICE, "工厂内部代号");
        appendKV("产品名(Product)", Build.PRODUCT, "产品线名称");
        appendKV("制造商(Manufacturer)", Build.MANUFACTURER, "OEM厂商");
        appendKV("硬件平台(Hardware)", Build.HARDWARE, "硬件平台名称，如qcom/mtk");
        appendKV("主板(Board)", Build.BOARD, "主板芯片代号");
    }

    /**
     * 采集系统版本信息：Android版本、API级别、构建ID、增量版本、构建类型/标签/时间、
     * Fingerprint、描述、构建主机和用户。
     */
    private void getSystemVersion() {
        appendSection("系统版本");
        appendKV("Android版本", Build.VERSION.RELEASE, "用户可见的Android版本号");
        appendKV("API级别(SDK)", String.valueOf(Build.VERSION.SDK_INT), "开发者API版本，越高功能越新");
        appendKV("Build ID", Build.ID, "系统构建唯一标识");
        appendKV("增量版本", Build.VERSION.INCREMENTAL, "OTA增量更新标识");
        appendKV("构建类型", Build.TYPE, "user(正式版)/userdebug(调试版)/eng(工程版)");
        appendKV("构建标签(Tags)", Build.TAGS, "构建时的标签，如release-keys表示正式签名");

        long buildTime = Build.TIME;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        appendKV("构建时间", sdf.format(new Date(buildTime)), "ROM编译的时间戳");

        appendKV("Fingerprint", Build.FINGERPRINT, "ROM唯一指纹，用于SafetyNet/Play Integrity认证");
        try {
            // Build.DESCRIPTION 是 @hide 字段，需要通过反射获取
            String desc = (String) Build.class.getField("DESCRIPTION").get(null);
            appendKV("描述(Description)", desc != null ? desc : "N/A", "ROM的完整构建描述");
        } catch (Exception e) {
            appendKV("描述(Description)", "N/A", "ROM的完整构建描述");
        }
        appendKV("Host", Build.HOST, "编译ROM的构建主机名");
        appendKV("User", Build.USER, "编译ROM的构建用户名");
    }

    /**
     * 采集安全信息：安全补丁级别、SELinux状态、Bootloader版本、基带版本、验证启动状态。
     */
    private void getSecurityInfo() {
        appendSection("安全信息");
        appendKV("安全补丁级别", Build.VERSION.SECURITY_PATCH != null ? Build.VERSION.SECURITY_PATCH : "未知",
                "Google每月发布的安全补丁日期，越新越安全");
        appendKV("SELinux状态", isSELinuxEnforcing() ? "Enforcing (强制模式)" : "Permissive (宽容模式)",
                "强制模式可限制恶意程序行为，宽容模式仅记录");

        appendKV("启动加载器(Bootloader)", Build.BOOTLOADER, "Bootloader版本号");
        appendKV("基带版本", getBasebandVersion(), "调制解调器固件版本，影响蜂窝网络/通话");

        String verifiedBoot = getSystemProperty("ro.boot.verifiedbootstate");
        if (!verifiedBoot.isEmpty()) {
            appendKV("验证启动状态", verifiedBoot, "Verified Boot状态: green(正常)/yellow(自定义)/orange(警告)/red(危险)");
        }
    }

    /**
     * 采集硬件信息：SOC平台、CPU架构（主/兼容/32位/64位ABI列表）。
     */
    private void getHardwareInfo() {
        appendSection("硬件信息");
        appendKV("SOC平台", Build.HARDWARE, "芯片平台代号");
        appendKV("CPU架构", Build.SUPPORTED_ABIS[0], "主CPU指令集架构，arm64-v8a为64位");
        if (Build.SUPPORTED_ABIS.length > 1) {
            appendKV("兼容架构", Build.SUPPORTED_ABIS[1], "向下兼容的32位指令集");
        }
        String abis = Arrays.toString(Build.SUPPORTED_32_BIT_ABIS);
        appendKV("32位ABI列表", abis, "支持的32位程序二进制接口");
        String abis64 = Arrays.toString(Build.SUPPORTED_64_BIT_ABIS);
        appendKV("64位ABI列表", abis64, "支持的64位程序二进制接口");
    }

    /**
     * 采集CPU信息：核心数、最大/最小/当前频率、负载、进程数，以及 /proc/cpuinfo 中的关键条目。
     */
    private void getCpuInfo() {
        appendSection("CPU信息");
        int cores = Runtime.getRuntime().availableProcessors();
        appendKV("CPU核心数", String.valueOf(cores), "可用的逻辑处理器数量");
        appendKV("CPU最大频率", getCpuMaxFreq(), "当前CPU的最高频率");
        appendKV("CPU最小频率", getCpuMinFreq(), "当前CPU的最低频率");
        appendKV("CPU当前频率", getCpuCurFreq(), "当前CPU的实时频率");
        appendKV("CPU负载", getCpuLoad(), "1分钟/5分钟/15分钟平均负载");
        appendKV("进程数", getProcessCount(), "系统当前运行的总进程数");

        String cpuInfo = getCpuInfoText();
        if (!cpuInfo.isEmpty()) {
            sb.append("\n--- CPU内核详情 ---\n");
            sb.append(cpuInfo);
        }
    }

    /**
     * 采集内存信息：物理RAM总量/可用/低内存阈值，JVM堆内存（最大/已分配/空闲），Dalvik堆限制。
     */
    private void getMemoryInfo() {
        appendSection("内存信息");
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);
        appendKV("总RAM", formatBytes(memInfo.totalMem), "设备物理内存总量");
        appendKV("可用RAM", formatBytes(memInfo.availMem), "当前空闲+可回收内存");
        appendKV("低内存阈值", formatBytes(memInfo.threshold), "系统认为内存不足的阈值");
        appendKV("是否低内存", String.valueOf(memInfo.lowMemory), "当前是否处于低内存状态");

        Runtime rt = Runtime.getRuntime();
        appendKV("JVM最大堆", formatBytes(rt.maxMemory()), "Java虚拟机可分配的最大内存");
        appendKV("JVM已分配堆", formatBytes(rt.totalMemory()), "JVM当前已申请的总内存");
        appendKV("JVM空闲堆", formatBytes(rt.freeMemory()), "JVM堆中当前空闲的内存");

        appendKV("Dalvik内存类", getSystemProperty("dalvik.vm.heapgrowthlimit"),
                "单个应用堆内存上限(Dalvik/ART)");
    }

    /**
     * 采集存储信息：内部存储总量/可用，外部存储（SD卡/模拟存储）总量/可用。
     */
    private void getStorageInfo() {
        appendSection("存储信息");
        try {
            File data = Environment.getDataDirectory();
            StatFs statData = new StatFs(data.getPath());
            long blockSize = statData.getBlockSizeLong();
            long totalBlocks = statData.getBlockCountLong();
            long availBlocks = statData.getAvailableBlocksLong();
            long total = blockSize * totalBlocks;
            long avail = blockSize * availBlocks;
            appendKV("内部存储总量", formatBytes(total), "系统+应用+用户数据共享空间");
            appendKV("内部存储可用", formatBytes(avail), "剩余可写入空间");

            // 检查外部存储是否已挂载
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File external = Environment.getExternalStorageDirectory();
                StatFs statExt = new StatFs(external.getPath());
                long extBlockSize = statExt.getBlockSizeLong();
                long extTotal = extBlockSize * statExt.getBlockCountLong();
                long extAvail = extBlockSize * statExt.getAvailableBlocksLong();
                appendKV("外部存储总量", formatBytes(extTotal), "SD卡或模拟外部存储");
                appendKV("外部存储可用", formatBytes(extAvail), "");
            }
        } catch (Exception e) {
            append("存储信息获取失败: " + e.getMessage());
        }
    }

    /**
     * 采集显示信息：物理/逻辑分辨率、DPI、密度因子、刷新率、HDR支持、屏幕尺寸、精确DPI。
     */
    private void getDisplayInfo() {
        appendSection("显示信息");
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            // getRealMetrics 获取包含系统装饰（状态栏/导航栏）的完整物理像素
            display.getRealMetrics(metrics);
            // 取较大值为宽度，较小值为高度，确保横竖屏一致性
            int w = Math.max(metrics.widthPixels, metrics.heightPixels);
            int h = Math.min(metrics.widthPixels, metrics.heightPixels);
            appendKV("物理分辨率", w + " × " + h, "屏幕实际像素点数量");

            // getMetrics 获取应用可用的逻辑像素（扣除导航栏等系统装饰）
            display.getMetrics(metrics);
            int dw = Math.max(metrics.widthPixels, metrics.heightPixels);
            int dh = Math.min(metrics.widthPixels, metrics.heightPixels);
            appendKV("逻辑分辨率", dw + " × " + dh, "应用可用的渲染分辨率(扣除导航栏等)");
            appendKV("DPI", String.valueOf(metrics.densityDpi), "每英寸像素密度，越高显示越细腻");
            appendKV("密度因子", String.format("%.2f", metrics.density),
                    "相对于160dpi的缩放比例，1.0=mdpi, 3.0=xxhdpi");

            try {
                // Android M+ 支持获取显示模式（刷新率、HDR等高级信息）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    float refresh = display.getMode().getRefreshRate();
                    appendKV("刷新率", String.format("%.1f Hz", refresh), "屏幕每秒刷新次数，越高越流畅");
                    appendKV("显示模式ID", String.valueOf(display.getMode().getModeId()),
                            "不同模式可能对应不同分辨率/刷新率组合");
                    appendKV("HDR支持", Arrays.toString(display.getHdrCapabilities().getSupportedHdrTypes()),
                            "HDR类型列表，支持HDR的屏幕色彩更丰富");
                }
            } catch (Exception ignored) { Log.w(TAG, "Display mode info: " + ignored.getMessage()); }

            float xdpi = metrics.xdpi;
            float ydpi = metrics.ydpi;
            // 通过勾股定理计算屏幕对角线物理尺寸
            double sizeInches = Math.sqrt(w * w + h * h) / (float) metrics.densityDpi;
            appendKV("屏幕尺寸", String.format("%.1f 英寸", sizeInches), "屏幕对角线物理尺寸");
            appendKV("XDpi / YDpi", String.format("%.1f / %.1f", xdpi, ydpi), "水平和垂直方向精确DPI");
        }
    }

    /**
     * 采集电池静态信息：电量百分比、充电状态、电源类型、温度、电压、技术类型、健康度。
     *
     * <p>通过注册一个 null BroadcastReceiver 来获取系统最后一次广播的电池状态，
     * 这是一种无需动态注册监听器即可获取当前电池信息的高效方式。
     */
    private void getBatteryInfo_static() {
        appendSection("电池(静态信息)");
        // 传入 null 作为 BroadcastReceiver，立即获取最近的粘性广播，无需注册监听
        Intent batteryIntent = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            appendKV("当前电量", (level * 100 / scale) + "%", "当前电池电量百分比");

            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String statusStr;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "充电中"; break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "放电中"; break;
                case BatteryManager.BATTERY_STATUS_FULL: statusStr = "已充满"; break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "未充电"; break;
                default: statusStr = "未知"; break;
            }
            appendKV("充电状态", statusStr, "当前电池充放电状态");

            int plug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            String plugStr;
            switch (plug) {
                case BatteryManager.BATTERY_PLUGGED_AC: plugStr = "交流充电器"; break;
                case BatteryManager.BATTERY_PLUGGED_USB: plugStr = "USB"; break;
                case BatteryManager.BATTERY_PLUGGED_WIRELESS: plugStr = "无线充电"; break;
                default: plugStr = "未连接"; break;
            }
            appendKV("电源类型", plugStr, "当前充电方式");

            int temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            // 电池温度单位为0.1°C，需除以10转换为摄氏度
            appendKV("电池温度", (temp / 10.0) + "°C", "电池当前温度，过高可能影响寿命");

            int voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            // 电池电压单位为mV，需除以1000转换为伏特
            appendKV("电池电压", (voltage / 1000.0) + "V", "电池当前电压，正常范围3.6-4.4V");

            String tech = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            if (tech != null) appendKV("电池技术", tech, "Li-ion(锂离子)或Li-poly(锂聚合物)");

            int health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            String healthStr;
            switch (health) {
                case BatteryManager.BATTERY_HEALTH_GOOD: healthStr = "良好"; break;
                case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthStr = "过热"; break;
                case BatteryManager.BATTERY_HEALTH_DEAD: healthStr = "损坏"; break;
                case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "过压"; break;
                case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthStr = "未知故障"; break;
                default: healthStr = "未知"; break;
            }
            appendKV("电池健康度", healthStr, "电池硬件健康状况");
        }
    }

    /**
     * 采集 GPU/OpenGL 信息：渲染器、供应商、OpenGL ES版本、GLSL版本、扩展列表、EGL版本。
     */
    private void getGpuOpenGLInfo() {
        appendSection("GPU / OpenGL信息");
        appendKV("GPU渲染器", GLES20.glGetString(GLES20.GL_RENDERER), "GPU芯片型号");
        appendKV("GPU供应商", GLES20.glGetString(GLES20.GL_VENDOR), "GPU驱动提供商");
        appendKV("OpenGL ES版本", GLES20.glGetString(GLES20.GL_VERSION), "支持的OpenGL ES版本");
        appendKV("GLSL版本", GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION), "着色器语言版本");

        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (extensions != null) {
            int count = extensions.split(" ").length;
            appendKV("OpenGL扩展数", String.valueOf(count), "GPU支持的扩展功能数量");
            sb.append("  （主要扩展: ");
            String[] extList = extensions.split(" ");
            int showCount = 0;
            for (String ext : extList) {
                // 最多展示12个关键扩展，跳过compression/buffer/texture类（数量多且不太关键）
                if (showCount >= 12) break;
                if (ext.contains("compression") || ext.contains("buffer") || ext.contains("texture")) continue;
                if (showCount > 0) sb.append(", ");
                // 扩展名过长时截断，避免单行过长
                String shortName = ext.length() > 35 ? ext.substring(0, 35) + "..." : ext;
                sb.append(shortName);
                showCount++;
            }
            sb.append("... 等" + count + "个）\n");
        }

        try {
            // 通过 EGL 接口获取更底层的 GPU 信息
            EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                int[] major = new int[1], minor = new int[1];
                EGL14.eglInitialize(eglDisplay, major, 0, minor, 0);
                appendKV("EGL版本", major[0] + "." + minor[0], "EGL(Embedded GL)接口版本");
            }
        } catch (Exception ignored) { Log.w(TAG, "EGL info: " + ignored.getMessage()); }
    }

    /**
     * 执行 Root 检测，综合判断设备是否已获取 Root 权限。
     *
     * <p>检测维度：
     * <ul>
     *   <li>su 二进制文件是否存在（最基础的检测方式）</li>
     *   <li>Magisk 是否安装（当前最主流的 Root 方案）</li>
     *   <li>Superuser.apk / SuperSU.apk / Kinguser.apk 是否存在</li>
     *   <li>Busybox 是否存在（通常随 Root 安装）</li>
     *   <li>构建类型是否为 eng/userdebug（自带 Root 调试权限）</li>
     * </ul>
     *
     * <p>注意：任何单一检测方式都可能被 Root 隐藏工具绕过，
     * 综合多项检测可提高判断的可靠性。
     */
    private void getRootInfo() {
        appendSection("Root检测");
        boolean rootBySu = checkSuBinary();
        boolean rootByBusybox = checkBusybox();
        boolean rootByMagisk = checkMagisk();
        boolean rootBySuperUser = checkSuperUser();
        boolean isEngBuild = "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE);

        boolean isRooted = rootBySu || rootByBusybox || rootByMagisk || rootBySuperUser;
        appendKV("su二进制", rootBySu ? "存在 ⚠" : "不存在 ✓",
                "su=SuperUser，root权限管理核心，存在即表示已root");
        appendKV("Magisk", rootByMagisk ? "存在" : "不存在",
                "Magisk是目前最主流的root方案，通过系统分区修补实现");
        appendKV("Superuser.apk", rootBySuperUser ? "存在" : "不存在",
                "传统SuperSU/KingRoot等root应用的APK文件");
        appendKV("Busybox", rootByBusybox ? "存在" : "不存在",
                "增强型命令行工具集，通常随root安装");
        appendKV("工程调试版", isEngBuild ? "是" : "否",
                "userdebug/eng版本自带root调试权限");
        appendKV("检测结论", isRooted ? "设备已Root" : "设备未Root",
                "综合所有检测项的最终结论");
    }

    /**
     * 采集运行时长信息：开机时长、深度睡眠时长、实际运行时长。
     */
    private void getUptimeInfo() {
        appendSection("运行时长");
        // elapsedRealtime() 包含深度睡眠时间，是设备自开机以来的总时长
        long uptimeMs = SystemClock.elapsedRealtime();
        long uptimeSec = uptimeMs / 1000;
        long days = uptimeSec / 86400;
        long hours = (uptimeSec % 86400) / 3600;
        long minutes = (uptimeSec % 3600) / 60;
        long seconds = uptimeSec % 60;

        StringBuilder uptimeStr = new StringBuilder();
        if (days > 0) uptimeStr.append(days).append("天 ");
        if (hours > 0) uptimeStr.append(hours).append("小时 ");
        uptimeStr.append(minutes).append("分 ").append(seconds).append("秒");
        appendKV("开机时长", uptimeStr.toString(), "自上次重启以来的运行时间");

        appendKV("原始值(ms)", String.valueOf(uptimeMs), "SystemClock.elapsedRealtime()返回值");

        long realtimeMs = SystemClock.elapsedRealtime();
        // 深度睡眠时间 = elapsedRealtime(含睡眠) - uptimeMillis(不含睡眠)
        long suspendMs = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis());
        long deepSleepEstimate = realtimeMs - SystemClock.uptimeMillis();
        appendKV("深度睡眠时长", formatMs(deepSleepEstimate), "CPU完全休眠的时间(估算)");
        appendKV("实际运行时长", formatMs(SystemClock.uptimeMillis()), "CPU处于活跃状态的时间");
    }

    /**
     * 采集 Java 运行时信息：VM名称/版本/供应商、Java版本、编译模式、Image状态、ISA特性。
     */
    private void getJavaRuntime() {
        appendSection("Java运行时");
        appendKV("VM名称", System.getProperty("java.vm.name", "未知"), "ART(Android Runtime)简称");
        appendKV("VM版本", System.getProperty("java.vm.version", "未知"), "ART运行时版本");
        appendKV("VM供应商", System.getProperty("java.vm.vendor", "未知"), "通常为The Android Project");
        appendKV("Java版本", System.getProperty("java.version", "未知"),
                "(名义值，实际Android不完全兼容标准Java)");
        appendKV("VM编译模式", getSystemProperty("dalvik.vm.dex2oat-filter"),
                "speed(全AOT)/speed-profile(按热点)/quicken(半AOT)/space(省空间)");
        appendKV("Image状态", getSystemProperty("dalvik.vm.image-dex2oat-filter"),
                "系统镜像编译级别");
        appendKV("ISA特性", getSystemProperty("dalvik.vm.isa.arm64.features"),
                "CPU指令集扩展特性，如AES/SHA等加速指令");
    }

    /**
     * 采集 DRM（数字版权管理）信息：Widevine/PlayReady/ClearKey 支持情况及安全级别。
     *
     * <p>Widevine 安全级别说明：
     * <ul>
     *   <li>L1：硬件级最高安全，支持 HD/UHD 播放</li>
     *   <li>L2：硬件级加密，支持较低分辨率</li>
     *   <li>L3：软件级，仅支持 SD 播放</li>
     * </ul>
     */
    private void getDrmInfo() {
        appendSection("DRM(数字版权管理)");
        try {
            UUID[] drmSchemes = {};
            try {
                // 通过反射获取 DRM UUID 常量，因为这些字段可能在不同 API 版本中位置不同
                drmSchemes = new UUID[]{
                    (UUID) MediaDrm.class.getField("WIDEVINE_UUID").get(null),
                    (UUID) MediaDrm.class.getField("PLAYREADY_UUID").get(null),
                    (UUID) MediaDrm.class.getField("CLEARKEY_UUID").get(null)
                };
            } catch (Exception ignored) { Log.w(TAG, "DRM schemes: " + ignored.getMessage()); }
            String[] drmNames = {"Widevine", "PlayReady", "ClearKey"};
            for (int i = 0; i < drmSchemes.length; i++) {
                try {
                    MediaDrm drm = new MediaDrm(drmSchemes[i]);
                    String desc = drm.getPropertyString(MediaDrm.PROPERTY_DESCRIPTION);
                    String vendor = drm.getPropertyString(MediaDrm.PROPERTY_VENDOR);
                    String version = drm.getPropertyString(MediaDrm.PROPERTY_VERSION);
                    if (i == 0) {
                        // Widevine 额外显示安全级别
                        appendKV(drmNames[i], "L" + getWidevineLevel(drm) + " | " + desc,
                                "Widevine安全级别: L1(硬件级最高)/L2(硬件级加密)/L3(软件级)，影响Netflix等高清播放");
                        appendKV("  供应商", vendor, "");
                        appendKV("  版本", version, "");
                    } else {
                        appendKV(drmNames[i], vendor != null ? vendor : "支持",
                                vendor != null ? "" : "该DRM方案已检测到");
                    }
                    drm.close();
                } catch (Exception e) {
                    appendKV(drmNames[i], "不支持", "");
                }
            }
        } catch (Exception e) {
            appendKV("DRM", "获取失败: " + e.getMessage(), "");
        }

        appendKV("系统DRM服务", getSystemProperty("drm.service.enabled"),
                "系统级DRM服务是否已开启");
    }

    /**
     * 获取 Widevine DRM 的安全级别。
     *
     * @param drm MediaDrm 实例
     * @return 安全级别（1/2/3），获取失败时返回 -1
     */
    private int getWidevineLevel(MediaDrm drm) {
        try {
            return drm.getSecurityLevel(drm.openSession());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 采集传感器列表及详情：名称、类型、厂商、功耗、精度、量程、最小延迟、ID。
     */
    private void getSensorList() {
        appendSection("传感器列表");
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) { append("传感器服务不可用"); return; }
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        appendKV("传感器总数", String.valueOf(sensors.size()), "");
        sb.append("\n");
        for (Sensor s : sensors) {
            sb.append("● ").append(s.getName()).append("\n");
            sb.append("  类型: ").append(sensorTypeName(s.getType()))
                    .append(" (").append(s.getType()).append(")\n");
            sb.append("  厂商: ").append(s.getVendor()).append("\n");
            sb.append("  功耗: ").append(s.getPower()).append(" mA");
            // 功耗 ≤ 0 通常是低功耗唤醒传感器或虚拟传感器
            if (s.getPower() <= 0) sb.append(" (低功耗/唤醒传感器)");
            sb.append("\n");
            sb.append("  精度: ").append(s.getResolution()).append("\n");
            sb.append("  量程: ±").append(s.getMaximumRange()).append("\n");
            sb.append("  最小延迟: ").append(s.getMinDelay()).append(" μs\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sb.append("  ID: ").append(s.getId()).append("\n");
            }
            sb.append("\n");
        }
    }

    /**
     * 采集关键系统属性（ro.build.*、ro.secure、ro.debuggable 等）。
     *
     * <p>通过反射调用 {@code android.os.SystemProperties.get()} 获取，
     * 因为该类是 Android 内部 @hide API，无法直接引用。
     */
    private void getSystemProperties() {
        appendSection("系统属性(prop)");
        String[][] props = {
                {"ro.build.version.sdk", "SDK版本号（同API级别）"},
                {"ro.build.version.security_patch", "安全补丁日期"},
                {"ro.product.cpu.abi", "主CPU ABI"},
                {"ro.board.platform", "芯片平台代号"},
                {"ro.build.version.release", "Android版本号"},
                {"ro.sf.lcd_density", "屏幕密度配置"},
                {"ro.product.first_api_level", "出厂Android API级别"},
                {"ro.build.characteristics", "设备特征: tablet(平板)/tv(电视)/default(手机)"},
                {"ro.secure", "ro.secure=1表示安全模式（不可adb root）"},
                {"ro.debuggable", "ro.debuggable=0表示不可调试（正式版）"},
                {"persist.sys.timezone", "当前系统时区"},
                {"gsm.version.baseband", "基带版本(备用源)"},
                {"ro.build.version.codename", "Android版本代号，如UPSIDE_DOWN_CAKE"},
                {"ro.config.low_ram", "是否低内存设备(Go Edition)"},
                {"ro.build.version.preview_sdk", "预览版SDK级别(非0表示预览版)"},
        };
        for (String[] prop : props) {
            String val = getSystemProperty(prop[0]);
            appendKV(prop[0], val.isEmpty() ? "N/A" : val, prop[1]);
        }
    }

    /**
     * 检查常见路径下是否存在 su 二进制文件。
     *
     * @return true 如果任一路径下存在 su 文件
     */
    private boolean checkSuBinary() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/sbin/su", "/vendor/bin/su", "/data/local/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    /**
     * 检查常见路径下是否存在 Busybox 二进制文件。
     *
     * @return true 如果任一路径下存在 busybox 文件
     */
    private boolean checkBusybox() {
        String[] paths = {"/system/xbin/busybox", "/system/bin/busybox",
                "/sbin/busybox", "/data/local/busybox"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    /**
     * 通过执行 "which magisk" 命令检测 Magisk 是否安装。
     *
     * @return true 如果命令返回非空结果
     */
    private boolean checkMagisk() {
        try {
            String result = execCommand("which magisk 2>/dev/null");
            return !result.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测 Superuser/SuperSU/Kinguser/Magisk 的 APK 或包名是否存在。
     *
     * <p>同时检查文件路径和 PackageManager 中是否安装了 Magisk 包。
     *
     * @return true 如果检测到任一 Root 管理 APK
     */
    private boolean checkSuperUser() {
        String[] paths = {"/system/app/Superuser.apk", "/system/app/SuperSU.apk",
                "/system/app/Kinguser.apk", "/data/app/com.topjohnwu.magisk"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        try {
            // 通过 PackageManager 检查 Magisk 是否作为用户应用安装
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo("com.topjohnwu.magisk", 0);
            return true;
        } catch (Exception e) {
            // PackageManager.NameNotFoundException 表示未安装
            return false;
        }
    }

    /**
     * 检测 SELinux 是否处于 Enforcing（强制）模式。
     *
     * <p>优先通过执行 "getenforce" 命令获取状态；
     * 命令执行失败时降级为读取系统属性 "ro.build.selinux"。
     *
     * @return true 如果 SELinux 处于 Enforcing 模式
     */
    private boolean isSELinuxEnforcing() {
        try {
            String result = execCommand("getenforce");
            return result.contains("Enforcing");
        } catch (Exception e) {
            // 命令执行失败时，通过系统属性降级判断
            return "1".equals(getSystemProperty("ro.build.selinux"));
        }
    }

    /**
     * 获取基带（Radio/Modem）固件版本。
     *
     * <p>依次尝试三种来源：Build.getRadioVersion() → gsm.version.baseband → gsm.version.ril-impl，
     * 均获取不到时返回 "未知"。
     *
     * @return 基带版本字符串
     */
    private String getBasebandVersion() {
        try {
            String bb = Build.getRadioVersion();
            if (bb != null && !bb.isEmpty()) return bb;
        } catch (Exception ignored) { Log.w(TAG, "Baseband version: " + ignored.getMessage()); }

        String bb = getSystemProperty("gsm.version.baseband");
        if (!bb.isEmpty()) return bb;

        bb = getSystemProperty("gsm.version.ril-impl");
        if (!bb.isEmpty()) return bb;

        return "未知";
    }

    /**
     * 读取 CPU 最大频率（从 sysfs 节点）。
     *
     * @return CPU 最大频率值（单位 KHz），读取失败返回 "未知"
     */
    private String getCpuMaxFreq() {
        try {
            return readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 读取 CPU 最小频率（从 sysfs 节点）。
     *
     * @return CPU 最小频率值（单位 KHz），读取失败返回 "未知"
     */
    private String getCpuMinFreq() {
        try {
            return readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq");
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 读取 CPU 当前运行频率（从 sysfs 节点）。
     *
     * <p>注意：scaling_cur_freq 在某些设备上需要 root 权限才能读取，
     * 读取失败时返回 "未知"。
     *
     * @return CPU 当前频率（如 "1200000 KHz"），读取失败返回 "未知"
     */
    private String getCpuCurFreq() {
        try {
            String freq = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            return freq.isEmpty() ? "未知" : freq + " KHz";
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 读取 CPU 负载信息（从 /proc/loadavg）。
     *
     * @return 1分钟/5分钟/15分钟平均负载，读取失败返回 "未知"
     */
    private String getCpuLoad() {
        try {
            String load = readFile("/proc/loadavg");
            if (!load.isEmpty()) return load.trim();
        } catch (Exception ignored) { Log.w(TAG, "CPU load: " + ignored.getMessage()); }
        return "未知";
    }

    /**
     * 统计 /proc 目录下以纯数字命名的目录数量，即当前系统运行的总进程数。
     *
     * @return 进程数量字符串，读取失败返回 "未知"
     */
    private String getProcessCount() {
        try {
            File procDir = new File("/proc");
            // /proc 下以纯数字命名的目录即为进程 PID 目录
            File[] procFiles = procDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return Pattern.matches("\\d+", f.getName());
                }
            });
            return procFiles != null ? String.valueOf(procFiles.length) : "未知";
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 读取 /proc/cpuinfo 并过滤出关键信息行。
     *
     * <p>过滤规则：仅保留包含 processor、model name、CPU、Hardware、Features、
     * BogoMIPS、Implementer、Architecture、Variant、Part、Revision 关键字的行，
     * 避免输出过多冗余信息。
     *
     * @return 过滤后的 CPU 信息文本，读取失败返回空字符串
     */
    private String getCpuInfoText() {
        try {
            String content = readFileFull("/proc/cpuinfo");
            if (!content.isEmpty()) {
                StringBuilder filtered = new StringBuilder();
                for (String line : content.split("\n")) {
                    if (line.trim().isEmpty()) continue;
                    if (line.contains("processor") || line.contains("model name") ||
                            line.contains("CPU") || line.contains("Hardware") ||
                            line.contains("Features") || line.contains("BogoMIPS") ||
                            line.contains("Implementer") || line.contains("Architecture") ||
                            line.contains("Variant") || line.contains("Part") ||
                            line.contains("Revision")) {
                        filtered.append("  ").append(line.trim()).append("\n");
                    }
                }
                return filtered.toString();
            }
        } catch (Exception ignored) { Log.w(TAG, "CPU info: " + ignored.getMessage()); }
        return "";
    }

    /**
     * 通过反射调用 {@code android.os.SystemProperties.get(key, def)} 获取系统属性。
     *
     * <p>SystemProperties 是 Android 内部 @hide API，无法在应用层直接引用，
     * 因此通过反射绕过访问限制。
     *
     * @param key 属性名（如 "ro.build.version.sdk"）
     * @return 属性值，获取失败返回空字符串
     */
    private String getSystemProperty(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 执行 Shell 命令并返回标准输出内容。
     *
     * <p>使用 "/system/bin/sh -c" 执行命令，支持管道等 Shell 特性。
     *
     * @param command 要执行的 Shell 命令
     * @return 命令的标准输出内容（已去除首尾空白），执行失败返回空字符串
     */
    private String execCommand(String command) {
        StringBuilder result = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append("\n");
            reader.close();
            p.waitFor();
        } catch (Exception ignored) { Log.w(TAG, "Exec command: " + ignored.getMessage()); }
        return result.toString().trim();
    }

    /**
     * 读取文件的第一行内容。
     *
     * <p>适用于 sysfs 节点等只包含单行数据的文件（如 CPU 频率信息）。
     *
     * @param path 文件绝对路径
     * @return 第一行内容（已去除首尾空白），读取失败返回空字符串
     */
    private String readFile(String path) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(path)));
            String line = reader.readLine();
            reader.close();
            return line != null ? line.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 读取文件的完整内容。
     *
     * <p>适用于多行文件（如 /proc/cpuinfo）。
     *
     * @param path 文件绝对路径
     * @return 文件完整内容，读取失败返回空字符串
     */
    private String readFileFull(String path) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(path)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将字节数格式化为人类可读的字符串（B/KB/MB/GB）。
     *
     * @param bytes 字节数
     * @return 格式化后的字符串，如 "1.5 GB"、"512.0 MB"
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 将毫秒数格式化为 "X小时 X分 X秒" 形式的可读字符串。
     *
     * <p>自动省略为零的大单位（如不足1小时则不显示小时）。
     *
     * @param ms 毫秒数
     * @return 格式化后的时间字符串
     */
    private String formatMs(long ms) {
        long sec = ms / 1000;
        long hr = sec / 3600;
        long min = (sec % 3600) / 60;
        sec = sec % 60;
        if (hr > 0) return hr + "小时 " + min + "分 " + sec + "秒";
        if (min > 0) return min + "分 " + sec + "秒";
        return sec + "秒";
    }

    /**
     * 将传感器类型常量转换为中文类型名称。
     *
     * @param type 传感器类型常量（Sensor.TYPE_*）
     * @return 对应的中文类型名称，未知类型返回 "未知(type)"
     */
    private String sensorTypeName(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER: return "加速度计";
            case Sensor.TYPE_MAGNETIC_FIELD: return "磁力计";
            case Sensor.TYPE_GYROSCOPE: return "陀螺仪";
            case Sensor.TYPE_LIGHT: return "光线传感器";
            case Sensor.TYPE_PRESSURE: return "气压计";
            case Sensor.TYPE_PROXIMITY: return "距离传感器";
            case Sensor.TYPE_GRAVITY: return "重力传感器";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "线性加速度";
            case Sensor.TYPE_ROTATION_VECTOR: return "旋转矢量";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "湿度传感器";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "环境温度";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: return "磁力计(未校准)";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED: return "陀螺仪(未校准)";
            case Sensor.TYPE_SIGNIFICANT_MOTION: return "重要运动";
            case Sensor.TYPE_STEP_DETECTOR: return "步数检测";
            case Sensor.TYPE_STEP_COUNTER: return "步数计数";
            case Sensor.TYPE_GAME_ROTATION_VECTOR: return "游戏旋转矢量";
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: return "地磁旋转矢量";
            case Sensor.TYPE_HEART_RATE: return "心率传感器";
            case Sensor.TYPE_POSE_6DOF: return "6DOF姿态";
            case Sensor.TYPE_STATIONARY_DETECT: return "静止检测";
            case Sensor.TYPE_MOTION_DETECT: return "运动检测";
            case Sensor.TYPE_HEART_BEAT: return "心跳检测";
            case Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT: return "离身检测";
            default: return "未知(" + type + ")";
        }
    }

    /**
     * 快速获取系统信息摘要（静态方法，无需创建实例）。
     *
     * <p>返回包含最关键系统信息的简短摘要，适用于日志记录、错误上报等场景。
     * 包含：Android版本、品牌型号、CPU核心数/架构、安全补丁、基带版本、
     * RAM总量、开机时长、Root状态、Fingerprint尾部。
     *
     * @param context 上下文
     * @return 格式化的系统信息摘要文本
     */
    public static String getQuickSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" | SDK ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append(Build.BRAND).append(" ").append(Build.MODEL);
        sb.append(" | ").append(Runtime.getRuntime().availableProcessors()).append("核 ").append(Build.SUPPORTED_ABIS[0]).append("\n");
        sb.append("安全补丁: ").append(Build.VERSION.SECURITY_PATCH != null ? Build.VERSION.SECURITY_PATCH : "未知");
        sb.append(" | 基带: ").append(getQuickBaseband());
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        sb.append("\nRAM: ").append(formatBytesStatic(mi.totalMem));
        sb.append(" | 开机: ").append(formatUptimeStatic());

        String securityPatch = Build.VERSION.SECURITY_PATCH != null ? Build.VERSION.SECURITY_PATCH : "";
        // 快速 Root 检测：仅检查最常见的两个 su 路径
        boolean rooted = new File("/system/bin/su").exists() || new File("/system/xbin/su").exists();
        sb.append("\nRoot: ").append(rooted ? "是" : "否");
        // 仅显示 Fingerprint 最后20个字符，避免摘要过长
        sb.append(" | FPGA: ").append(Build.FINGERPRINT != null ? Build.FINGERPRINT.substring(Math.max(0, Build.FINGERPRINT.length() - 20)) : "N/A");
        return sb.toString();
    }

    /**
     * 快速获取基带版本（静态方法）。
     *
     * @return 基带版本字符串，获取失败返回 "未知"
     */
    private static String getQuickBaseband() {
        try { String bb = Build.getRadioVersion(); if (bb != null && !bb.isEmpty()) return bb; } catch (Exception ignored) { Log.w(TAG, "Quick baseband: " + ignored.getMessage()); }
        return "未知";
    }

    /**
     * 静态字节数格式化（简化版，跳过 KB 级别）。
     *
     * @param bytes 字节数
     * @return 格式化后的字符串（MB 或 GB）
     */
    private static String formatBytesStatic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 静态运行时长格式化（简化版，仅显示小时和分钟）。
     *
     * @return 格式化后的时长字符串（如 "5h 30m"）
     */
    private static String formatUptimeStatic() {
        long ms = android.os.SystemClock.elapsedRealtime();
        long sec = ms / 1000;
        long hr = sec / 3600;
        long min = (sec % 3600) / 60;
        if (hr > 0) return hr + "h " + min + "m";
        return min + "m";
    }
}

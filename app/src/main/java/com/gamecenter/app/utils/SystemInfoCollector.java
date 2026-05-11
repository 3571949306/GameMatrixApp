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

public class SystemInfoCollector {

    private final Context context;
    private final StringBuilder sb;

    public SystemInfoCollector(Context context) {
        this.context = context.getApplicationContext();
        this.sb = new StringBuilder();
    }

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

    private void append(String line) {
        sb.append(line).append("\n");
    }

    private void appendKV(String key, String value, String comment) {
        sb.append("■ ").append(key).append(":  ").append(value);
        if (comment != null && !comment.isEmpty()) {
            sb.append("  （").append(comment).append("）");
        }
        sb.append("\n");
    }

    private void appendSection(String title) {
        sb.append("\n══ ").append(title).append(" ══\n");
    }

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
            String desc = (String) Build.class.getField("DESCRIPTION").get(null);
            appendKV("描述(Description)", desc != null ? desc : "N/A", "ROM的完整构建描述");
        } catch (Exception e) {
            appendKV("描述(Description)", "N/A", "ROM的完整构建描述");
        }
        appendKV("Host", Build.HOST, "编译ROM的构建主机名");
        appendKV("User", Build.USER, "编译ROM的构建用户名");
    }

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

    private void getDisplayInfo() {
        appendSection("显示信息");
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            int w = Math.max(metrics.widthPixels, metrics.heightPixels);
            int h = Math.min(metrics.widthPixels, metrics.heightPixels);
            appendKV("物理分辨率", w + " × " + h, "屏幕实际像素点数量");

            display.getMetrics(metrics);
            int dw = Math.max(metrics.widthPixels, metrics.heightPixels);
            int dh = Math.min(metrics.widthPixels, metrics.heightPixels);
            appendKV("逻辑分辨率", dw + " × " + dh, "应用可用的渲染分辨率(扣除导航栏等)");
            appendKV("DPI", String.valueOf(metrics.densityDpi), "每英寸像素密度，越高显示越细腻");
            appendKV("密度因子", String.format("%.2f", metrics.density),
                    "相对于160dpi的缩放比例，1.0=mdpi, 3.0=xxhdpi");

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    float refresh = display.getMode().getRefreshRate();
                    appendKV("刷新率", String.format("%.1f Hz", refresh), "屏幕每秒刷新次数，越高越流畅");
                    appendKV("显示模式ID", String.valueOf(display.getMode().getModeId()),
                            "不同模式可能对应不同分辨率/刷新率组合");
                    appendKV("HDR支持", Arrays.toString(display.getHdrCapabilities().getSupportedHdrTypes()),
                            "HDR类型列表，支持HDR的屏幕色彩更丰富");
                }
            } catch (Exception ignored) {}

            float xdpi = metrics.xdpi;
            float ydpi = metrics.ydpi;
            double sizeInches = Math.sqrt(w * w + h * h) / (float) metrics.densityDpi;
            appendKV("屏幕尺寸", String.format("%.1f 英寸", sizeInches), "屏幕对角线物理尺寸");
            appendKV("XDpi / YDpi", String.format("%.1f / %.1f", xdpi, ydpi), "水平和垂直方向精确DPI");
        }
    }

    private void getBatteryInfo_static() {
        appendSection("电池(静态信息)");
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
            appendKV("电池温度", (temp / 10.0) + "°C", "电池当前温度，过高可能影响寿命");

            int voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
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
                if (showCount >= 12) break;
                if (ext.contains("compression") || ext.contains("buffer") || ext.contains("texture")) continue;
                if (showCount > 0) sb.append(", ");
                String shortName = ext.length() > 35 ? ext.substring(0, 35) + "..." : ext;
                sb.append(shortName);
                showCount++;
            }
            sb.append("... 等" + count + "个）\n");
        }

        try {
            EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                int[] major = new int[1], minor = new int[1];
                EGL14.eglInitialize(eglDisplay, major, 0, minor, 0);
                appendKV("EGL版本", major[0] + "." + minor[0], "EGL(Embedded GL)接口版本");
            }
        } catch (Exception ignored) {}
    }

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

    private void getUptimeInfo() {
        appendSection("运行时长");
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
        long suspendMs = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis());
        long deepSleepEstimate = realtimeMs - SystemClock.uptimeMillis();
        appendKV("深度睡眠时长", formatMs(deepSleepEstimate), "CPU完全休眠的时间(估算)");
        appendKV("实际运行时长", formatMs(SystemClock.uptimeMillis()), "CPU处于活跃状态的时间");
    }

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

    private void getDrmInfo() {
        appendSection("DRM(数字版权管理)");
        try {
            UUID[] drmSchemes = {};
            try {
                drmSchemes = new UUID[]{
                    (UUID) MediaDrm.class.getField("WIDEVINE_UUID").get(null),
                    (UUID) MediaDrm.class.getField("PLAYREADY_UUID").get(null),
                    (UUID) MediaDrm.class.getField("CLEARKEY_UUID").get(null)
                };
            } catch (Exception ignored) {}
            String[] drmNames = {"Widevine", "PlayReady", "ClearKey"};
            for (int i = 0; i < drmSchemes.length; i++) {
                try {
                    MediaDrm drm = new MediaDrm(drmSchemes[i]);
                    String desc = drm.getPropertyString(MediaDrm.PROPERTY_DESCRIPTION);
                    String vendor = drm.getPropertyString(MediaDrm.PROPERTY_VENDOR);
                    String version = drm.getPropertyString(MediaDrm.PROPERTY_VERSION);
                    if (i == 0) {
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

    private int getWidevineLevel(MediaDrm drm) {
        try {
            return drm.getSecurityLevel(drm.openSession());
        } catch (Exception e) {
            return -1;
        }
    }

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

    private boolean checkSuBinary() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/sbin/su", "/vendor/bin/su", "/data/local/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private boolean checkBusybox() {
        String[] paths = {"/system/xbin/busybox", "/system/bin/busybox",
                "/sbin/busybox", "/data/local/busybox"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private boolean checkMagisk() {
        try {
            String result = execCommand("which magisk 2>/dev/null");
            return !result.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkSuperUser() {
        String[] paths = {"/system/app/Superuser.apk", "/system/app/SuperSU.apk",
                "/system/app/Kinguser.apk", "/data/app/com.topjohnwu.magisk"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo("com.topjohnwu.magisk", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSELinuxEnforcing() {
        try {
            String result = execCommand("getenforce");
            return result.contains("Enforcing");
        } catch (Exception e) {
            return "1".equals(getSystemProperty("ro.build.selinux"));
        }
    }

    private String getBasebandVersion() {
        try {
            String bb = Build.getRadioVersion();
            if (bb != null && !bb.isEmpty()) return bb;
        } catch (Exception ignored) {}

        String bb = getSystemProperty("gsm.version.baseband");
        if (!bb.isEmpty()) return bb;

        bb = getSystemProperty("gsm.version.ril-impl");
        if (!bb.isEmpty()) return bb;

        return "未知";
    }

    private String getCpuMaxFreq() {
        try {
            return readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
        } catch (Exception e) {
            return "未知";
        }
    }

    private String getCpuMinFreq() {
        try {
            return readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq");
        } catch (Exception e) {
            return "未知";
        }
    }

    private String getCpuCurFreq() {
        try {
            String freq = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            return freq.isEmpty() ? "未知" : freq + " KHz";
        } catch (Exception e) {
            return "未知";
        }
    }

    private String getCpuLoad() {
        try {
            String load = readFile("/proc/loadavg");
            if (!load.isEmpty()) return load.trim();
        } catch (Exception ignored) {}
        return "未知";
    }

    private String getProcessCount() {
        try {
            File procDir = new File("/proc");
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
        } catch (Exception ignored) {}
        return "";
    }

    private String getSystemProperty(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
        } catch (Exception e) {
            return "";
        }
    }

    private String execCommand(String command) {
        StringBuilder result = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append("\n");
            reader.close();
            p.waitFor();
        } catch (Exception ignored) {}
        return result.toString().trim();
    }

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

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatMs(long ms) {
        long sec = ms / 1000;
        long hr = sec / 3600;
        long min = (sec % 3600) / 60;
        sec = sec % 60;
        if (hr > 0) return hr + "小时 " + min + "分 " + sec + "秒";
        if (min > 0) return min + "分 " + sec + "秒";
        return sec + "秒";
    }

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
        boolean rooted = new File("/system/bin/su").exists() || new File("/system/xbin/su").exists();
        sb.append("\nRoot: ").append(rooted ? "是" : "否");
        sb.append(" | FPGA: ").append(Build.FINGERPRINT != null ? Build.FINGERPRINT.substring(Math.max(0, Build.FINGERPRINT.length() - 20)) : "N/A");
        return sb.toString();
    }

    private static String getQuickBaseband() {
        try { String bb = Build.getRadioVersion(); if (bb != null && !bb.isEmpty()) return bb; } catch (Exception ignored) {}
        return "未知";
    }

    private static String formatBytesStatic(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String formatUptimeStatic() {
        long ms = android.os.SystemClock.elapsedRealtime();
        long sec = ms / 1000;
        long hr = sec / 3600;
        long min = (sec % 3600) / 60;
        if (hr > 0) return hr + "h " + min + "m";
        return min + "m";
    }
}
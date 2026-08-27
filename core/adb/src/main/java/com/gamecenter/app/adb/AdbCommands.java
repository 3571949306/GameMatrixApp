package com.gamecenter.app.adb;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Remote POSIX shell arguments and conservative UI operation validation. No local execution. */
public final class AdbCommands {
    private AdbCommands() {}

    public static String quote(String value) {
        if (value == null || value.indexOf('\0') >= 0) throw new IllegalArgumentException("参数不能包含 NUL");
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static String path(String value) {
        if (value == null || !value.startsWith("/") || value.indexOf('\0') >= 0
                || value.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new IllegalArgumentException("需要不超过 1024 字节的远端绝对路径");
        }
        StringBuilder result = new StringBuilder();
        for (String segment : value.split("/", -1)) {
            if (segment.equals("..")) throw new IllegalArgumentException("路径不能包含 ..");
            if (!segment.isEmpty() && !segment.equals(".")) result.append('/').append(segment);
        }
        return result.length() == 0 ? "/" : result.toString();
    }

    public static String child(String parent, String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("文件名不能包含路径分隔符或 ..");
        }
        String root = path(parent);
        return path((root.equals("/") ? root : root + "/") + name);
    }

    public static String packageName(String value) {
        if (value == null || value.length() > 255
                || !value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("无效的 Android 包名");
        }
        return value;
    }

    public static String remove(String value, boolean recursive) {
        String target = path(value);
        if (target.equals("/") || target.indexOf('/', 1) < 0
                || target.equals("/data/local") || target.equals("/data/local/tmp")
                || target.equals("/storage/emulated") || target.equals("/storage/emulated/0")) {
            throw new IllegalArgumentException("禁止删除根目录或设备公共根目录");
        }
        return "rm " + (recursive ? "-rf" : "-f") + " -- " + quote(target);
    }

    public static String move(String source, String destination) {
        String from = path(source);
        remove(from, true); // Apply the same root/traversal protection to destructive moves.
        return "mv -- " + quote(from) + " " + quote(path(destination));
    }

    public static String copy(String source, String destination) {
        return "cp -R -- " + quote(path(source)) + " " + quote(path(destination));
    }

    public static String mkdir(String value) { return "mkdir -- " + quote(path(value)); }

    public static String resolution(int width, int height) {
        if (width < 200 || width > 8192 || height < 200 || height > 8192) {
            throw new IllegalArgumentException("分辨率每边应为 200..8192");
        }
        return "wm size " + width + "x" + height;
    }
    public static String resetResolution() { return "wm size reset"; }
    public static String density(int dpi) {
        if (dpi < 72 || dpi > 1280) throw new IllegalArgumentException("DPI 应为 72..1280");
        return "wm density " + dpi;
    }
    public static String resetDensity() { return "wm density reset"; }
    public static String animationScale(double scale) {
        if (Double.isNaN(scale) || Double.isInfinite(scale) || scale < 0 || scale > 10) {
            throw new IllegalArgumentException("动画倍率应为 0..10");
        }
        return BigDecimal.valueOf(scale).stripTrailingZeros().toPlainString();
    }
    public static String animations(double scale) {
        String value = animationScale(scale);
        return "settings put global window_animation_scale " + value
                + " && settings put global transition_animation_scale " + value
                + " && settings put global animator_duration_scale " + value;
    }

    public static String appAction(String action, String value) {
        String pkg = quote(packageName(value));
        if (action == null) throw new IllegalArgumentException("应用操作不能为空");
        switch (action) {
            case "launch": return "monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1";
            case "force-stop": return "am force-stop --user current " + pkg;
            case "enable": return "pm enable --user current " + pkg;
            case "disable": return "pm disable-user --user current " + pkg;
            case "uninstall": return "pm uninstall --user current " + pkg;
            case "clear": return "pm clear --user current " + pkg;
            default: throw new IllegalArgumentException("不支持的应用操作");
        }
    }
}

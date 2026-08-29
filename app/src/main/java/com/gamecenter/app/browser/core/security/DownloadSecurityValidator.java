package com.gamecenter.app.browser.core.security;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Phase B（下载与文件安全）的框架：危险扩展名处置与落盘策略决策。
 *
 * <p>真实落盘 / 二次确认 UI 属 Phase B 实现，本类只定义<b>决策逻辑</b>（纯逻辑、可单测）：
 * <ul>
 *   <li>危险扩展名（apk/exe/bat/sh/cmd/vbs/js/msi）改落 app 私有目录，并提供"另存公共目录"二次确认；</li>
 *   <li>或强制追加 {@code .gmdownload} 后缀阻断直接安装。</li>
 * </ul>
 *
 * <p>TODO(Phase B 实现)：接 BrowserDownloadDelegate / DownloadManager，落地"私有目录 + 二次确认"。
 */
public final class DownloadSecurityValidator {

    public enum TargetPolicy {
        /** 直接落公共 Downloads（仅安全类型）。 */
        PUBLIC,
        /** 落 app 私有目录，需用户二次确认才移到公共目录。 */
        PRIVATE_APP_DIR,
        /** 追加 .gmdownload 后缀阻断直接安装。 */
        QUARANTINE_SUFFIX
    }

    private static final String[] DANGEROUS_EXT = {
            "apk", "exe", "bat", "sh", "cmd", "vbs", "js", "msi"
    };
    private static final String[] DANGEROUS_MIME_TYPES = {
            "application/vnd.android.package-archive",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-msi",
            "application/x-bat",
            "application/x-sh",
            "text/x-shellscript",
            "application/javascript",
            "application/x-javascript",
            "text/javascript"
    };

    private DownloadSecurityValidator() {}

    public static boolean isDangerousExtension(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String d : DANGEROUS_EXT) {
            if (d.equals(ext)) return true;
        }
        return false;
    }

    /**
     * 判断服务器声明的 MIME 是否代表可执行文件或脚本。
     *
     * <p>MIME 可能带 charset 等参数；参数不参与判定。未知 MIME 不自动视为危险，
     * 避免把普通二进制/压缩文件全部误路由；文件名扩展名仍由调用方同时提供。</p>
     */
    public static boolean isDangerousMimeType(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) return false;
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int parameter = normalized.indexOf(';');
        if (parameter >= 0) normalized = normalized.substring(0, parameter).trim();
        for (String dangerous : DANGEROUS_MIME_TYPES) {
            if (dangerous.equals(normalized)) return true;
        }
        return false;
    }

    @NonNull
    public static TargetPolicy policyFor(@NonNull String fileName) {
        return isDangerousExtension(fileName) ? TargetPolicy.PRIVATE_APP_DIR : TargetPolicy.PUBLIC;
    }

    /** 扩展名和 MIME 任一危险时，统一走 app 私有目录。 */
    @NonNull
    public static TargetPolicy policyFor(@NonNull String fileName, String mimeType) {
        return isDangerousExtension(fileName) || isDangerousMimeType(mimeType)
                ? TargetPolicy.PRIVATE_APP_DIR : TargetPolicy.PUBLIC;
    }

    /** 给危险文件追加隔离后缀，阻断"下载即安装"。 */
    @NonNull
    public static String quarantineName(@NonNull String fileName) {
        return isDangerousExtension(fileName) ? fileName + ".gmdownload" : fileName;
    }
}

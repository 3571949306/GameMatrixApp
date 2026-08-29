package com.gamecenter.app.browser.core.security;

import androidx.annotation.NonNull;

/**
 * Phase B（下载与文件安全）的纯逻辑工具：文件名净化。
 *
 * <p>规则（计划 §Phase B）：清 NUL/控制字符、去除非法字符、去除首尾空白与多余点号。
 * 无 Android 依赖，可纯 JVM 单测（见 {@code SanitizeFileNameTest}）。
 *
 * <p>注意：本类只做<b>字符串净化</b>；同名去重（需查文件系统）见 {@link DownloadSecurityValidator}。
 */
public final class FileNameSanitizer {

    // Windows / POSIX 共有非法字符
    private static final String ILLEGAL_CHARS = "\\/:*?\"<>|";

    private FileNameSanitizer() {}

    @NonNull
    public static String sanitize(@NonNull String raw) {
        if (raw == null) return "download";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 0x20 || c == 0x7f) continue; // NUL / 控制字符
            if (ILLEGAL_CHARS.indexOf(c) >= 0) {
                sb.append('_'); // 非法字符替换为下划线
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        // 修正历史笔误：单独出现的 ">" 视为文件名一部分时转义，避免路径注入
        if (out.isEmpty()) out = "download";
        // 去掉首尾点号（纯点号名在部分 FS 上非法）
        out = out.replaceAll("^[. ]+", "").replaceAll("[. ]+$", "");
        return out.isEmpty() ? "download" : out;
    }
}

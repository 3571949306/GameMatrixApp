package com.gamecenter.app.tools;

/**
 * WiFi / 名片二维码内容拼装（纯 Java，无 Android 依赖）。
 * <p>
 * 从 {@code AdvancedToolBinders} 抽出的纯逻辑，格式与既有生成串保持字节级一致，
 * 以便 {@code scripts/verify_qr.py} 纯 javac 回归。
 * </p>
 */
public final class QrPayloads {

    private QrPayloads() {
    }

    /**
     * 转义 WiFi 二维码字段中的保留字符（\ ; , :）。
     */
    public static String escapeWifi(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:");
    }

    /**
     * 拼装 WIFI: 二维码内容。
     *
     * @param ssid     网络名
     * @param password 密码
     * @param auth     加密方式（WPA/WEP/nopass），空则由调用方默认 WPA
     */
    public static String buildWifi(String ssid, String password, String auth) {
        return "WIFI:T:" + escapeWifi(auth) + ";S:" + escapeWifi(ssid)
                + ";P:" + escapeWifi(password) + ";;";
    }

    /**
     * 拼装 vCard 3.0 二维码内容。
     *
     * @param name  姓名
     * @param phone 电话
     * @param email 邮箱，空则省略 EMAIL 行
     */
    public static String buildVCard(String name, String phone, String email) {
        String emailPart = (email == null || email.isEmpty()) ? "" : "\nEMAIL:" + email;
        return "BEGIN:VCARD\nVERSION:3.0\nFN:" + name
                + "\nTEL:" + phone
                + emailPart
                + "\nEND:VCARD";
    }
}

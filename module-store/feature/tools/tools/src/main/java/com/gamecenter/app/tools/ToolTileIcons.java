package com.gamecenter.app.tools;

import java.util.HashMap;
import java.util.Map;

public final class ToolTileIcons {

    private static final Map<String, String> ICONS = new HashMap<>();
    private static final String FALLBACK = "\uD83E\uDDF0";

    static {
        ICONS.put("adb_workbench", "\uD83D\uDEE0");
        ICONS.put("network_diagnosis", "\uD83E\uDE7A");
        ICONS.put("diagnostic_report", "\uD83D\uDCC4");
        ICONS.put("dns_lookup", "\uD83D\uDD0D");
        ICONS.put("lan_scan", "\uD83D\uDEF0");
        ICONS.put("text_codec", "\uD83D\uDD24");
        ICONS.put("file_hash", "\uD83D\uDDC2");
        ICONS.put("qr_plus", "\uD83D\uDD33");
        ICONS.put("color_plus", "\uD83D\uDD8C");
        ICONS.put("permission_privacy", "\uD83D\uDEE1");
        ICONS.put("ip", "\uD83C\uDF10");
        ICONS.put("dns", "\uD83E\uDDED");
        ICONS.put("wifi", "\uD83D\uDCE1");
        ICONS.put("speedtest", "\uD83D\uDE80");
        ICONS.put("portscan", "\uD83D\uDD0C");
        ICONS.put("battery", "\uD83D\uDD0B");
        ICONS.put("ping", "\uD83C\uDFD3");
        ICONS.put("traceroute", "\uD83D\uDDFA");
        ICONS.put("subnet", "\uD83D\uDD22");
        ICONS.put("screen", "\uD83D\uDCFA");
        ICONS.put("sensor", "\uD83C\uDF00");
        ICONS.put("hash", "#\uFE0F\u20E3");
        ICONS.put("clipboard", "\uD83D\uDCCB");
        ICONS.put("color", "\uD83C\uDFA8");
        ICONS.put("sysinfo", "\u2139");
        ICONS.put("device_overview", "\uD83D\uDCF1");
        ICONS.put("installed_apps", "\uD83D\uDCE6");
        ICONS.put("compass", "\uD83E\uDDED");
        ICONS.put("satellite", "\uD83D\uDEF0");
        ICONS.put("regex_test", "\uD83D\uDD23");
        ICONS.put("unit_converter", "\uD83D\uDCD0");
        ICONS.put("radix_converter", "\uD83D\uDD00");
        ICONS.put("password_generator", "\uD83D\uDD12");
        ICONS.put("uuid_generator", "\uD83C\uDD94");
        ICONS.put("crypto_tool", "\uD83D\uDDDD");
        ICONS.put("jwt_parser", "\uD83C\uDFAB");
        ICONS.put("bubble_level", "\uD83D\uDCA7");
        ICONS.put("sound_meter", "\uD83D\uDD0A");
        ICONS.put("color_test", "\uD83C\uDF08");
        ICONS.put("floating_monitor", "\uD83D\uDCCA");
    }

    private ToolTileIcons() {
    }

    public static String iconFor(String toolId) {
        if (toolId == null) {
            return FALLBACK;
        }
        String icon = ICONS.get(toolId);
        return icon != null ? icon : FALLBACK;
    }
}

package com.gamecenter.app.tools;

import android.content.Context;
import android.content.SharedPreferences;
import com.gamecenter.app.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ToolSectionStore {

    private static final String PREFS_NAME = "tools_settings";
    private static final String KEY_ORDER = "tools_order";
    private static final String KEY_VISIBLE = "tools_visible";
    private static final String KEY_LAYOUT_MODE = "tools_layout_mode";
    private static final String KEY_FAVORITES = "tools_favorites";
    private static final String KEY_RECENT = "tools_recent";
    private static final int MAX_RECENT = 8;

    private final Context appContext;

    public ToolSectionStore(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public List<ToolSection> loadSections() {
        SharedPreferences prefs = getPrefs();
        String orderStr = prefs.getString(KEY_ORDER, null);
        Set<String> visibleSet = prefs.getStringSet(KEY_VISIBLE, null);

        List<ToolSection> allSections = defaultSections();
        if (visibleSet != null && !visibleSet.isEmpty()) {
            for (ToolSection section : allSections) {
                section.visible = visibleSet.contains(section.id);
            }
        }

        if (orderStr == null || orderStr.isEmpty()) {
            return allSections;
        }

        String[] orderIds = orderStr.split(",");
        List<ToolSection> ordered = new ArrayList<>();
        for (String id : orderIds) {
            ToolSection section = findById(allSections, id);
            if (section != null && !ordered.contains(section)) {
                ordered.add(section);
            }
        }
        for (ToolSection section : allSections) {
            if (!ordered.contains(section)) {
                ordered.add(section);
            }
        }
        return ordered;
    }

    public void saveOrder(List<ToolSection> sections) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(sections.get(i).id);
        }
        getPrefs().edit().putString(KEY_ORDER, builder.toString()).apply();
    }

    public void saveVisibility(List<ToolSection> sections) {
        Set<String> visibleSet = new HashSet<>();
        for (ToolSection section : sections) {
            if (section.visible) {
                visibleSet.add(section.id);
            }
        }
        getPrefs().edit().putStringSet(KEY_VISIBLE, visibleSet).apply();
    }

    public int getLayoutMode() {
        return getPrefs().getInt(KEY_LAYOUT_MODE, 0);
    }

    public void saveLayoutMode(int mode) {
        getPrefs().edit().putInt(KEY_LAYOUT_MODE, mode).apply();
    }

    public boolean isFavorite(String id) {
        return getFavoriteIds().contains(id);
    }

    public boolean toggleFavorite(String id) {
        Set<String> favorites = new HashSet<>(getFavoriteIds());
        boolean favorite;
        if (favorites.contains(id)) {
            favorites.remove(id);
            favorite = false;
        } else {
            favorites.add(id);
            favorite = true;
        }
        getPrefs().edit().putStringSet(KEY_FAVORITES, favorites).apply();
        return favorite;
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(getPrefs().getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public void recordRecent(String id) {
        List<String> recent = getRecentIds();
        recent.remove(id);
        recent.add(0, id);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        getPrefs().edit().putString(KEY_RECENT, joinIds(recent)).apply();
    }

    public List<String> getRecentIds() {
        String raw = getPrefs().getString(KEY_RECENT, "");
        List<String> ids = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty() && !ids.contains(trimmed)) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    private SharedPreferences getPrefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String joinIds(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private List<ToolSection> defaultSections() {
        List<ToolSection> sections = new ArrayList<>();
        sections.add(new ToolSection("network_diagnosis", "一键网络体检", R.layout.item_tool_network_diagnosis, true));
        sections.add(new ToolSection("diagnostic_report", "诊断报告导出", R.layout.item_tool_diagnostic_report, true));
        sections.add(new ToolSection("dns_lookup", "DNS查询", R.layout.item_tool_dns_lookup, true));
        sections.add(new ToolSection("lan_scan", "局域网设备扫描", R.layout.item_tool_lan_scan, true));
        sections.add(new ToolSection("text_codec", "编码/时间戳/JSON", R.layout.item_tool_text_codec, true));
        sections.add(new ToolSection("file_hash", "文件哈希", R.layout.item_tool_file_hash, true));
        sections.add(new ToolSection("qr_plus", "二维码增强", R.layout.item_tool_qr_plus, true));
        sections.add(new ToolSection("color_plus", "颜色增强", R.layout.item_tool_color_plus, true));
        sections.add(new ToolSection("permission_privacy", "权限与隐私说明", R.layout.item_tool_permission_privacy, true));
        sections.add(new ToolSection("ip", "IP地址信息", R.layout.item_tool_ip, true));
        sections.add(new ToolSection("dns", "DNS服务器", R.layout.item_tool_dns, true));
        sections.add(new ToolSection("wifi", "WiFi信号", R.layout.item_tool_wifi, true));
        sections.add(new ToolSection("speedtest", "网络测速", R.layout.item_tool_speedtest, true));
        sections.add(new ToolSection("portscan", "端口扫描", R.layout.item_tool_portscan, true));
        sections.add(new ToolSection("qr", "二维码工具", R.layout.item_tool_qr, true));
        sections.add(new ToolSection("battery", "电池信息", R.layout.item_tool_battery, true));
        sections.add(new ToolSection("device", "设备信息", R.layout.item_tool_device, true));
        sections.add(new ToolSection("ping", "Ping工具", R.layout.item_tool_ping, true));
        sections.add(new ToolSection("traceroute", "路由追踪", R.layout.item_tool_traceroute, true));
        sections.add(new ToolSection("subnet", "子网计算器", R.layout.item_tool_subnet, true));
        sections.add(new ToolSection("screen", "屏幕信息", R.layout.item_tool_screen, true));
        sections.add(new ToolSection("sensor", "传感器信息", R.layout.item_tool_sensor, true));
        sections.add(new ToolSection("hash", "哈希计算器", R.layout.item_tool_hash, true));
        sections.add(new ToolSection("clipboard", "剪贴板工具", R.layout.item_tool_clipboard, true));
        sections.add(new ToolSection("color", "颜色取色器", R.layout.item_tool_color, true));
        sections.add(new ToolSection("sysinfo", "手机系统详细信息", R.layout.item_tool_sysinfo, true));
        return sections;
    }

    private ToolSection findById(List<ToolSection> sections, String id) {
        for (ToolSection section : sections) {
            if (section.id.equals(id)) {
                return section;
            }
        }
        return null;
    }
}

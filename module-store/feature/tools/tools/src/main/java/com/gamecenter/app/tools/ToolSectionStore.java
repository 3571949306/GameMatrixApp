package com.gamecenter.app.tools;

import android.content.Context;
import android.content.SharedPreferences;
import com.gamecenter.app.core.common.ModuleScopedPreferences;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具分区持久化存储 — 管理工具箱中各工具卡片的排序、可见性、收藏和最近使用记录。
 * <p>
 * 简单理解：这个类就像工具箱的"记忆管家"，帮你记住：
 * 哪些工具放在前面、哪些工具被隐藏了、哪些工具被收藏了、最近用过哪些工具。
 * 即使关掉应用再打开，这些设置也不会丢失。
 * </p>
 * <p>
 * 使用 {@link SharedPreferences} 作为底层存储，所有数据以键值对形式持久化到本地。
 * （SharedPreferences 就像一个小本子，用"键-值"的方式记东西，比如 "tools_order" → "ping,wifi,dns"）
 * </p>
 * <p>
 * 存储内容包括：
 * <ul>
 *   <li>工具卡片的排列顺序（逗号分隔的 ID 列表）</li>
 *   <li>工具卡片的可见性（可见工具的 ID 集合）</li>
 *   <li>布局模式（0=默认布局）</li>
 *   <li>收藏列表（收藏工具的 ID 集合）</li>
 *   <li>最近使用记录（逗号分隔的 ID 列表，最多 {@link #MAX_RECENT} 条）</li>
 * </ul>
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>构造函数中使用 {@code context.getApplicationContext()} 避免 Activity 级别的内存泄漏</li>
 *   <li>使用 {@code apply()} 而非 {@code commit()} 进行持久化，避免阻塞调用线程
 *       （apply 是"异步保存"，commit 是"同步保存"，异步不会卡住界面）</li>
 *   <li>加载排序时，未在已保存顺序中的工具追加到末尾，确保新增工具不会丢失</li>
 * </ul>
 * </p>
 */
public final class ToolSectionStore {

    /** SharedPreferences 文件名 */
    private static final String PREFS_NAME = "tools_settings";
    /** 模块作用域 ID（必须与 catalog.json 中 tools 模块 id 一致） */
    private static final String MODULE_ID = "tools";
    /** 工具排列顺序的存储键 */
    private static final String KEY_ORDER = "tools_order";
    /** 工具可见性集合的存储键 */
    private static final String KEY_VISIBLE = "tools_visible";
    private static final String KEY_ADB_VISIBILITY_MIGRATED = "adb_visibility_v1";
    private static final String KEY_DEVICE_TOOLS_VISIBILITY_MIGRATED = "device_tools_visibility_v1";
    private static final String KEY_SATELLITE_TOOL_VISIBILITY_MIGRATED = "satellite_tool_visibility_v1";
    /** 布局模式的存储键 */
    private static final String KEY_LAYOUT_MODE = "tools_layout_mode";
    /** 收藏列表的存储键 */
    private static final String KEY_FAVORITES = "tools_favorites";
    /** 最近使用记录的存储键 */
    private static final String KEY_RECENT = "tools_recent";
    /** 最近使用记录的最大保留条数 */
    private static final int MAX_RECENT = 8;

    /** 应用级 Context，避免持有 Activity 引用导致内存泄漏 */
    private final Context appContext;

    /**
     * 构造工具分区存储实例。
     *
     * @param context Android Context，内部会转为 ApplicationContext 以避免内存泄漏
     */
    public ToolSectionStore(Context context) {
        // 使用 ApplicationContext 而不是 Activity 的 context
        // 这样即使 Activity 被销毁了，也不会因为还持有引用而无法回收内存
        this.appContext = context.getApplicationContext();
    }

    /**
     * 加载所有工具分区列表，应用已保存的可见性和排序配置。
     * <p>
     * 加载流程：
     * <ol>
     *   <li>生成默认工具分区列表</li>
     *   <li>若存在已保存的可见性配置，则覆盖默认的可见性状态</li>
     *   <li>若存在已保存的排序配置，则按保存的顺序重排，未在保存顺序中的工具追加到末尾</li>
     * </ol>
     * </p>
     *
     * @return 排序和可见性已配置的工具分区列表
     */
    public List<ToolSection> loadSections() {
        SharedPreferences prefs = getPrefs();
        String orderStr = prefs.getString(KEY_ORDER, null);
        Set<String> visibleSet = prefs.getStringSet(KEY_VISIBLE, null);

        List<ToolSection> allSections = defaultSections();
        // Only migrate when this host can actually display the new card. Preserve explicit
        // hide-all (empty set), old hidden tools, and subsequent user changes to the ADB card.
        if (findById(allSections, AdbWorkbenchToolBinder.TOOL_ID) != null
                && !prefs.getBoolean(KEY_ADB_VISIBILITY_MIGRATED, false)) {
            SharedPreferences.Editor editor = prefs.edit();
            if (visibleSet != null && !visibleSet.isEmpty()) {
                visibleSet = new HashSet<>(visibleSet);
                visibleSet.add(AdbWorkbenchToolBinder.TOOL_ID);
                editor.putStringSet(KEY_VISIBLE, visibleSet);
            }
            editor.putBoolean(KEY_ADB_VISIBILITY_MIGRATED, true).apply();
        }
        // Make newly added local device tools visible for existing installs that already
        // have a non-empty visibility set. An empty set is an intentional hide-all choice.
        Set<String> migratedDeviceTools = migrateDeviceToolsVisibility(
                visibleSet, prefs.getBoolean(KEY_DEVICE_TOOLS_VISIBILITY_MIGRATED, false));
        if (migratedDeviceTools != visibleSet) {
            visibleSet = migratedDeviceTools;
            prefs.edit().putStringSet(KEY_VISIBLE, visibleSet).apply();
        }
        if (!prefs.getBoolean(KEY_DEVICE_TOOLS_VISIBILITY_MIGRATED, false)) {
            prefs.edit().putBoolean(KEY_DEVICE_TOOLS_VISIBILITY_MIGRATED, true).apply();
        }
        // Satellite status arrived after the first device-tools migration. Use a separately
        // versioned migration so existing users see the card without reviving a hide-all choice.
        Set<String> migratedSatelliteTool = migrateSatelliteToolVisibility(
                visibleSet, prefs.getBoolean(KEY_SATELLITE_TOOL_VISIBILITY_MIGRATED, false));
        if (migratedSatelliteTool != visibleSet) {
            visibleSet = migratedSatelliteTool;
            prefs.edit().putStringSet(KEY_VISIBLE, visibleSet).apply();
        }
        if (!prefs.getBoolean(KEY_SATELLITE_TOOL_VISIBILITY_MIGRATED, false)) {
            prefs.edit().putBoolean(KEY_SATELLITE_TOOL_VISIBILITY_MIGRATED, true).apply();
        }
        // 应用已保存的可见性配置
        if (visibleSet != null) {
            for (ToolSection section : allSections) {
                section.visible = visibleSet.contains(section.id);
            }
        }

        // 无已保存的排序配置时直接返回默认顺序
        if (orderStr == null || orderStr.isEmpty()) {
            return allSections;
        }

        // 按已保存的顺序重排工具列表
        String[] orderIds = orderStr.split(",");
        List<ToolSection> ordered = new ArrayList<>();
        for (String id : orderIds) {
            ToolSection section = findById(allSections, id);
            if (section != null && !ordered.contains(section)) {
                ordered.add(section);
            }
        }
        // 将未在保存顺序中的新增工具追加到末尾，确保不丢失
        for (ToolSection section : allSections) {
            if (!ordered.contains(section)) {
                ordered.add(section);
            }
        }
        return ordered;
    }

    /**
     * 保存工具卡片的排列顺序到 SharedPreferences。
     * <p>顺序以逗号分隔的 ID 字符串形式存储。</p>
     *
     * @param sections 当前排列顺序的工具分区列表
     */
    public void saveOrder(List<ToolSection> sections) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(sections.get(i).id);
        }
        // apply() 异步保存，不会阻塞当前线程
        getPrefs().edit().putString(KEY_ORDER, builder.toString()).apply();
    }

    // 2026-06-23: 使用次数统计（"热度"排序支持）
    private static final String KEY_USAGE_COUNT = "tool_usage_count";  // 格式: id1:n1,id2:n2,...

    /**
     * 记录工具使用次数（每次工具被打开 +1）
     */
    public void incrementUsage(String toolId) {
        String existing = getPrefs().getString(KEY_USAGE_COUNT, "");
        java.util.Map<String, Integer> counts = parseUsageCounts(existing);
        counts.put(toolId, counts.getOrDefault(toolId, 0) + 1);
        saveUsageCounts(counts);
    }

    /**
     * 获取工具使用次数
     */
    public int getUsageCount(String toolId) {
        return parseUsageCounts(getPrefs().getString(KEY_USAGE_COUNT, "")).getOrDefault(toolId, 0);
    }

    /**
     * 获取按使用次数降序排列的工具 ID 列表
     */
    public java.util.List<String> getTopUsedTools(int limit) {
        java.util.Map<String, Integer> counts = parseUsageCounts(getPrefs().getString(KEY_USAGE_COUNT, ""));
        java.util.List<java.util.Map.Entry<String, Integer>> entries = new java.util.ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        java.util.List<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }

    private java.util.Map<String, Integer> parseUsageCounts(String raw) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                try {
                    map.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    private void saveUsageCounts(java.util.Map<String, Integer> counts) {
        StringBuilder builder = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (builder.length() > 0) builder.append(",");
            builder.append(e.getKey()).append(":").append(e.getValue());
        }
        getPrefs().edit().putString(KEY_USAGE_COUNT, builder.toString()).apply();
    }

    /**
     * 保存工具卡片的可见性配置到 SharedPreferences。
     * <p>仅保存可见工具的 ID 集合，不可见的工具不在集合中。</p>
     *
     * @param sections 当前工具分区列表
     */
    public void saveVisibility(List<ToolSection> sections) {
        Set<String> visibleSet = new HashSet<>();
        for (ToolSection section : sections) {
            if (section.visible) {
                visibleSet.add(section.id);
            }
        }
        getPrefs().edit().putStringSet(KEY_VISIBLE, visibleSet).apply();
    }

    /**
     * 获取当前布局模式。
     *
     * @return 布局模式值，默认为 0（默认布局）
     */
    public int getLayoutMode() {
        return getPrefs().getInt(KEY_LAYOUT_MODE, 0);
    }

    /**
     * 保存布局模式到 SharedPreferences。
     *
     * @param mode 布局模式值
     */
    public void saveLayoutMode(int mode) {
        getPrefs().edit().putInt(KEY_LAYOUT_MODE, mode).apply();
    }

    /**
     * 判断指定工具是否已被收藏。
     *
     * @param id 工具分区的唯一标识符
     * @return 已收藏返回 true，否则返回 false
     */
    public boolean isFavorite(String id) {
        return getFavoriteIds().contains(id);
    }

    /**
     * 切换指定工具的收藏状态。
     * <p>若已收藏则取消收藏，若未收藏则添加收藏。</p>
     * <p>简单理解：就像给工具"加星/取消星"，点一下切换状态。</p>
     *
     * @param id 工具分区的唯一标识符
     * @return 切换后的收藏状态：true 表示已收藏，false 表示已取消收藏
     */
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

    /**
     * 获取所有已收藏工具的 ID 集合。
     * <p>返回的是新的 HashSet 实例，修改返回值不会影响持久化数据。</p>
     *
     * @return 已收藏工具 ID 的集合；无收藏时返回空集合
     */
    public Set<String> getFavoriteIds() {
        return new HashSet<>(getPrefs().getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    /**
     * 记录一个工具的最近使用。
     * <p>
     * 将指定工具 ID 移至最近使用列表的头部，并保持列表不超过 {@link #MAX_RECENT} 条。
     * 若该 ID 已存在于列表中，先移除旧位置再插入头部。
     * </p>
     * <p>简单理解：就像"最近打开的文件"列表，刚用过的排最前面，
     * 列表满了就把最旧的挤掉。</p>
     *
     * @param id 工具分区的唯一标识符
     */
    public void recordRecent(String id) {
        List<String> recent = getRecentIds();
        // 先移除已存在的记录，避免重复
        recent.remove(id);
        // 插入到列表头部，表示最近使用
        recent.add(0, id);
        // 超出最大条数时移除最旧的记录
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        getPrefs().edit().putString(KEY_RECENT, joinIds(recent)).apply();
    }

    /**
     * 获取最近使用的工具 ID 列表，按使用时间从近到远排列。
     *
     * @return 最近使用的工具 ID 列表；无记录时返回空列表
     */
    public List<String> getRecentIds() {
        String raw = getPrefs().getString(KEY_RECENT, "");
        List<String> ids = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            // 去重：避免重复 ID 出现在列表中
            if (!trimmed.isEmpty() && !ids.contains(trimmed)) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    /**
     * 获取工具设置专用的 SharedPreferences 实例。
     *
     * @return SharedPreferences 实例
     */
    private SharedPreferences getPrefs() {
        // Phase 3 数据隔离：迁移旧扁平 SP 并使用作用域 SP（mod_tools__tools_settings）
        ModuleScopedPreferences.migrateFrom(appContext, MODULE_ID, PREFS_NAME);
        return ModuleScopedPreferences.get(appContext, MODULE_ID, PREFS_NAME);
    }

    /**
     * 将 ID 列表拼接为逗号分隔的字符串。
     *
     * @param ids ID 列表
     * @return 逗号分隔的字符串
     */
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

    /**
     * 生成默认的工具分区列表。
     * <p>包含所有内置工具卡片，默认全部可见。新增工具应在此方法中添加。</p>
     * <p>简单理解：这就是工具箱的"出厂设置"——所有工具默认都显示，
     * 按照这里的顺序排列。如果用户新安装了应用，第一次看到的就是这个列表。</p>
     *
     * @return 默认工具分区列表
     */
    private List<ToolSection> defaultSections() {
        List<ToolSection> sections = new ArrayList<>();
        ToolSection adb = AdbWorkbenchToolBinder.createSection(appContext);
        if (adb != null) sections.add(adb);
        sections.add(new ToolSection("network_diagnosis", appContext.getString(R.string.tool_name_network_diagnosis), R.layout.item_tool_network_diagnosis, true, "network", ""));
        sections.add(new ToolSection("diagnostic_report", appContext.getString(R.string.tool_name_diagnostic_report), R.layout.item_tool_diagnostic_report, true, "network", ""));
        sections.add(new ToolSection("dns_lookup", appContext.getString(R.string.tool_name_dns_lookup), R.layout.item_tool_dns_lookup, true, "network", ""));
        sections.add(new ToolSection("lan_scan", appContext.getString(R.string.tool_name_lan_scan), R.layout.item_tool_lan_scan, true, "network", ""));
        sections.add(new ToolSection("text_codec", appContext.getString(R.string.tool_name_text_codec), R.layout.item_tool_text_codec, true, "tool", ""));
        sections.add(new ToolSection("file_hash", appContext.getString(R.string.tool_name_file_hash), R.layout.item_tool_file_hash, true, "tool", ""));
        sections.add(new ToolSection("qr_plus", appContext.getString(R.string.tool_name_qr_plus), R.layout.item_tool_qr_plus, true, "tool", ""));
        sections.add(new ToolSection("color_plus", appContext.getString(R.string.tool_name_color_plus), R.layout.item_tool_color_plus, true, "tool", ""));
        sections.add(new ToolSection("permission_privacy", appContext.getString(R.string.tool_name_permission_privacy), R.layout.item_tool_permission_privacy, true, "tool", ""));
        sections.add(new ToolSection("ip", appContext.getString(R.string.tool_name_ip), R.layout.item_tool_ip, true, "network", ""));
        sections.add(new ToolSection("dns", appContext.getString(R.string.tool_name_dns_config), R.layout.item_tool_dns, true, "network", ""));
        sections.add(new ToolSection("wifi", appContext.getString(R.string.tool_name_wifi), R.layout.item_tool_wifi, true, "network", ""));
        sections.add(new ToolSection("speedtest", appContext.getString(R.string.tool_name_speedtest), R.layout.item_tool_speedtest, true, "network", ""));
        sections.add(new ToolSection("portscan", appContext.getString(R.string.tool_name_portscan), R.layout.item_tool_portscan, true, "network", ""));
        sections.add(new ToolSection("battery", appContext.getString(R.string.tool_name_battery), R.layout.item_tool_battery, true, "device", ""));
        // 2026-07-25: device 工具已合并到 sysinfo（SystemInfoToolBinder 同时显示设备品牌/型号/系统版本）
        sections.add(new ToolSection("ping", appContext.getString(R.string.tool_name_ping), R.layout.item_tool_ping, true, "network", ""));
        sections.add(new ToolSection("traceroute", appContext.getString(R.string.tool_name_traceroute), R.layout.item_tool_traceroute, true, "network", ""));
        sections.add(new ToolSection("subnet", appContext.getString(R.string.tool_name_subnet), R.layout.item_tool_subnet, true, "network", ""));
        sections.add(new ToolSection("screen", appContext.getString(R.string.tool_name_screen), R.layout.item_tool_screen, true, "device", ""));
        sections.add(new ToolSection("sensor", appContext.getString(R.string.tool_name_sensor), R.layout.item_tool_sensor, true, "device", ""));
        sections.add(new ToolSection("hash", appContext.getString(R.string.tool_name_hash), R.layout.item_tool_hash, true, "tool", ""));
        sections.add(new ToolSection("clipboard", appContext.getString(R.string.tool_name_clipboard), R.layout.item_tool_clipboard, true, "tool", ""));
        sections.add(new ToolSection("color", appContext.getString(R.string.tool_name_color), R.layout.item_tool_color, true, "tool", ""));
        sections.add(new ToolSection("sysinfo", appContext.getString(R.string.tool_name_sysinfo), R.layout.item_tool_sysinfo, true, "device", ""));
        sections.add(new ToolSection("device_overview", appContext.getString(R.string.tool_name_device_overview), R.layout.item_tool_device_overview, true, "device", appContext.getString(R.string.tool_desc_device_overview)));
        sections.add(new ToolSection("installed_apps", appContext.getString(R.string.tool_name_installed_apps), R.layout.item_tool_installed_apps, true, "device", appContext.getString(R.string.tool_desc_installed_apps)));
        sections.add(new ToolSection("compass", appContext.getString(R.string.tool_name_compass), R.layout.item_tool_compass, true, "device", appContext.getString(R.string.tool_desc_compass)));
        sections.add(new ToolSection("satellite", appContext.getString(R.string.tool_name_satellite), R.layout.item_tool_satellite, true, "device", appContext.getString(R.string.tool_desc_satellite)));
        sections.add(new ToolSection("regex_test", appContext.getString(R.string.tool_name_regex_test), R.layout.item_tool_regex_test, true, "tool", ""));
        // 阶段3：新增 6 个工具（受 ENABLE_TOOLS_ENHANCEMENT flag 控制）
        if (BuildConfig.ENABLE_TOOLS_ENHANCEMENT) {
            sections.add(new ToolSection("unit_converter", appContext.getString(R.string.tool_name_unit_converter), R.layout.item_tool_unit_converter, true, "tool", appContext.getString(R.string.tool_desc_unit_converter)));
            sections.add(new ToolSection("radix_converter", appContext.getString(R.string.tool_name_radix_converter), R.layout.item_tool_radix_converter, true, "tool", appContext.getString(R.string.tool_desc_radix_converter)));
            sections.add(new ToolSection("password_generator", appContext.getString(R.string.tool_name_password_generator), R.layout.item_tool_password_generator, true, "tool", appContext.getString(R.string.tool_desc_password_generator)));
            sections.add(new ToolSection("uuid_generator", appContext.getString(R.string.tool_name_uuid_generator), R.layout.item_tool_uuid_generator, true, "tool", appContext.getString(R.string.tool_desc_uuid_generator)));
            sections.add(new ToolSection("crypto_tool", appContext.getString(R.string.tool_name_crypto_tool), R.layout.item_tool_crypto_tool, true, "tool", appContext.getString(R.string.tool_desc_crypto_tool)));
            sections.add(new ToolSection("jwt_parser", appContext.getString(R.string.tool_name_jwt_parser), R.layout.item_tool_jwt_parser, true, "tool", appContext.getString(R.string.tool_desc_jwt_parser)));
        }
        sections.add(new ToolSection("bubble_level", appContext.getString(R.string.tool_bubble_level), R.layout.item_tool_bubble_level, true, "device", appContext.getString(R.string.tool_bubble_level_desc)));
        sections.add(new ToolSection("sound_meter", appContext.getString(R.string.tool_sound_meter), R.layout.item_tool_sound_meter, true, "device", appContext.getString(R.string.tool_sound_meter_desc)));
        sections.add(new ToolSection("color_test", appContext.getString(R.string.tool_color_test), R.layout.item_tool_color_test, true, "device", appContext.getString(R.string.tool_color_test_desc)));
        sections.add(new ToolSection("floating_monitor", appContext.getString(R.string.tool_floating_monitor), R.layout.item_tool_floating_monitor, true, "device", appContext.getString(R.string.tool_floating_monitor_desc)));
        return sections;
    }

    /**
     * 在工具分区列表中按 ID 查找对应的分区。
     *
     * @param sections 工具分区列表
     * @param id       要查找的分区 ID
     * @return 匹配的 ToolSection；未找到时返回 null
     */
    private ToolSection findById(List<ToolSection> sections, String id) {
        for (ToolSection section : sections) {
            if (section.id.equals(id)) {
                return section;
            }
        }
        return null;
    }

    /**
     * Adds the first version of the local device tools to an existing non-empty visibility
     * set without overriding an explicit hide-all choice. Package-private for regression tests.
     */
    static Set<String> migrateDeviceToolsVisibility(Set<String> visibleSet, boolean migrated) {
        if (migrated || visibleSet == null || visibleSet.isEmpty()) return visibleSet;
        Set<String> result = new HashSet<>(visibleSet);
        result.add("device_overview");
        result.add("installed_apps");
        result.add("compass");
        return result;
    }

    /**
     * Adds satellite status to a previously non-empty visible set. Kept separate from the first
     * device-tool migration because that marker has already been persisted on existing devices.
     */
    static Set<String> migrateSatelliteToolVisibility(Set<String> visibleSet, boolean migrated) {
        if (migrated || visibleSet == null || visibleSet.isEmpty()) return visibleSet;
        Set<String> result = new HashSet<>(visibleSet);
        result.add("satellite");
        return result;
    }
}

package com.gamecenter.app.tools;

import android.content.Context;
import android.content.SharedPreferences;
import com.gamecenter.app.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具分区持久化存储 — 管理工具箱中各工具卡片的排序、可见性、收藏和最近使用记录。
 * <p>
 * 使用 {@link SharedPreferences} 作为底层存储，所有数据以键值对形式持久化到本地。
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
 *   <li>使用 {@code apply()} 而非 {@code commit()} 进行持久化，避免阻塞调用线程</li>
 *   <li>加载排序时，未在已保存顺序中的工具追加到末尾，确保新增工具不会丢失</li>
 * </ul>
 * </p>
 */
public final class ToolSectionStore {

    /** SharedPreferences 文件名 */
    private static final String PREFS_NAME = "tools_settings";
    /** 工具排列顺序的存储键 */
    private static final String KEY_ORDER = "tools_order";
    /** 工具可见性集合的存储键 */
    private static final String KEY_VISIBLE = "tools_visible";
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
        // 应用已保存的可见性配置
        if (visibleSet != null && !visibleSet.isEmpty()) {
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
        getPrefs().edit().putString(KEY_ORDER, builder.toString()).apply();
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
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
     *
     * @return 默认工具分区列表
     */
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
}

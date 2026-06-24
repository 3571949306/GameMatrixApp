package com.gamecenter.app.tools;

/**
 * 工具分区数据模型 — 描述工具箱中一个工具卡片的元信息。
 * <p>
 * 每个工具分区对应工具箱页面中的一个功能卡片（如 Ping 工具、WiFi 信号等），
 * 包含唯一标识、显示标题、内容布局资源 ID 以及可见性状态。
 * </p>
 * <p>
 * 简单理解：这个类就像一张"工具卡片档案"，记录了工具叫什么名字、
 * 长什么样（布局ID）、是否显示在工具箱页面上。
 * </p>
 * <p>
 * 设计决策：
 * <ul>
 *   <li>使用 final 修饰类，防止被继承；字段使用 public final，属于不可变数据载体
 *       （就像一张印刷好的名片，名字和标题印上去就不能改了）</li>
 *   <li>{@code visible} 字段为非 final，允许运行时动态调整工具卡片的显示/隐藏状态
 *       （就像名片上可以贴/撕"在岗/离岗"标签）</li>
 *   <li>布局 ID 通过构造函数注入，实现数据与视图的解耦</li>
 * </ul>
 * </p>
 */
public final class ToolSection {

    /** 工具分区的唯一标识符，如 "ping"、"wifi"、"speedtest" 等 */
    public final String id;

    /** 工具卡片的显示标题，如 "Ping工具"、"WiFi信号" 等 */
    public final String title;

    /** 工具卡片内容区域的布局资源 ID，如 R.layout.item_tool_ping */
    public final int contentLayoutId;

    /** 工具卡片是否在界面上可见，可由用户在设置中切换 */
    public boolean visible;

    /** 2026-06-23: 工具分类（network/device/tool/utility），用于分类筛选 chip */
    public final String category;

    /** 2026-06-23: 工具描述（用于搜索匹配和卡片副标题） */
    public final String description;

    /**
     * 构造一个工具分区实例。
     */
    public ToolSection(String id, String title, int contentLayoutId, boolean visible) {
        this(id, title, contentLayoutId, visible, "tool", "");
    }

    /**
     * 2026-06-23: 完整构造函数（带分类和描述）。
     */
    public ToolSection(String id, String title, int contentLayoutId, boolean visible, String category, String description) {
        this.id = id;
        this.title = title;
        this.contentLayoutId = contentLayoutId;
        this.visible = visible;
        this.category = category;
        this.description = description;
    }
}

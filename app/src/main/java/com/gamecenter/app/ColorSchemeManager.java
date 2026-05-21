package com.gamecenter.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * 配色方案管理器。
 * <p>
 * 【初学者理解】这个类就像一个"调色板仓库"，里面存放着多套精心搭配好的颜色方案。
 * 就像装修房子时可以选择"北欧风""中式风""日式风"一样，
 * 每套配色方案包含了所有界面元素需要的颜色——按钮用什么色、背景用什么色、文字用什么色，
 * 全都帮你搭配好了，你只需要选择喜欢的风格就行。
 * <p>
 * 定义并管理应用的所有配色方案，每套方案包含完整的 Material Design 色彩体系，
 * 涵盖浅色模式和深色模式下的主色、表面色、文字色等角色色值，
 * 以及导航栏、Tab 指示器、卡片边框等组件专用色值。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>所有方法均为 {@code static}，无需实例化，作为全局工具类使用
 *       【初学者理解】就像数学工具类 Math 一样，不需要 new 一个对象就能直接调用方法，
 *       比如 ColorSchemeManager.getScheme(0) 直接获取第一套配色方案</li>
 *   <li>配色方案在静态初始化块中一次性创建，运行时不可修改（只读列表）
 *       【初学者理解】就像印刷好的色卡，一旦印好就不能改了，这样更安全，
 *       不会因为意外修改导致界面颜色错乱</li>
 *   <li>每套方案同时包含浅色和深色两套色值，通过 {@code isDark} 参数切换，
 *       避免运行时动态计算颜色
 *       【初学者理解】每套配色方案就像一件"两面穿"的外套，
 *       白天穿浅色那面，晚上穿深色那面，不用临时去染颜色</li>
 *   <li>提供 {@link #applyScheme} 和 {@link #applySchemeToView} 两种应用方式，
 *       分别支持 Activity 级别整体应用和单个 View 级别局部应用
 *       【初学者理解】applyScheme 是给整个页面换装，applySchemeToView 是给单个控件换装</li>
 * </ul>
 * </p>
 */
public class ColorSchemeManager {

    /**
     * 配色方案数据类。
     * <p>
     * 【初学者理解】Scheme 就像一个"色卡"，上面记录了一套配色方案的所有颜色。
     * 每个颜色都有一个名字（字段名）和一个色值（如 0xFF5B4E9A）。
     * 色值用 32 位数字表示，格式是 ARGB（Alpha透明度 + Red红 + Green绿 + Blue蓝），
     * 比如 0xFF5B4E9A 中，FF 表示完全不透明，5B4E9A 是紫色的 RGB 值。
     * <p>
     * 封装一套完整的配色方案，包含浅色模式和深色模式下的所有角色色值。
     * 字段命名遵循 Material Design 3 色彩角色命名规范：
     * <ul>
     *   <li>{@code primary} / {@code onPrimary}：主色 / 主色上的文字色
     *       【初学者理解】primary 是最重要的颜色（比如按钮的底色），onPrimary 是放在主色上面的文字颜色</li>
     *   <li>{@code primaryContainer} / {@code onPrimaryContainer}：主色容器 / 主色容器上的文字色
     *       【初学者理解】Container 就像"浅色版"的主色，用于大面积区域，不会太刺眼</li>
     *   <li>{@code secondary} / {@code onSecondary}：次色 / 次色上的文字色
     *       【初学者理解】secondary 是辅助色，用于次要的按钮或装饰元素</li>
     *   <li>{@code surface} / {@code onSurface}：表面色 / 表面上的文字色
     *       【初学者理解】surface 是卡片、面板等"表面"的底色，onSurface 是上面的文字颜色</li>
     *   <li>{@code background} / {@code onBackground}：背景色 / 背景上的文字色
     *       【初学者理解】background 是整个页面的底色，是最基础的背景</li>
     * </ul>
     * 以 {@code dark} 前缀开头的字段为深色模式专用色值。
     * </p>
     */
    public static class Scheme {
        /** 方案名称（中文），用于设置界面展示 */
        public final String name;
        /** 浅色模式-主色 */
        public final int primary;
        /** 浅色模式-主色上的文字色 */
        public final int onPrimary;
        /** 浅色模式-主色容器色 */
        public final int primaryContainer;
        /** 浅色模式-主色容器上的文字色 */
        public final int onPrimaryContainer;
        /** 浅色模式-次色 */
        public final int secondary;
        /** 浅色模式-次色上的文字色 */
        public final int onSecondary;
        /** 浅色模式-次色容器色 */
        public final int secondaryContainer;
        /** 浅色模式-次色容器上的文字色 */
        public final int onSecondaryContainer;
        /** 浅色模式-表面色 */
        public final int surface;
        /** 浅色模式-表面上的文字色 */
        public final int onSurface;
        /** 浅色模式-表面变体色 */
        public final int surfaceVariant;
        /** 浅色模式-表面变体上的文字色 */
        public final int onSurfaceVariant;
        /** 浅色模式-背景色 */
        public final int background;
        /** 浅色模式-背景上的文字色 */
        public final int onBackground;
        /** Tab 指示器颜色 */
        public final int tabIndicator;
        /** 导航栏选中项颜色 */
        public final int navBarActive;
        /** 卡片边框颜色 */
        public final int cardBorder;
        /** 深色模式-表面色 */
        public final int darkSurface;
        /** 深色模式-背景色 */
        public final int darkBackground;
        /** 深色模式-表面变体色 */
        public final int darkSurfaceVariant;
        /** 深色模式-表面上的文字色 */
        public final int darkOnSurface;
        /** 深色模式-背景上的文字色 */
        public final int darkOnBackground;
        /** 深色模式-表面变体上的文字色 */
        public final int darkOnSurfaceVariant;
        /** 深色模式-导航栏未选中项颜色 */
        public final int darkNavBarInactive;

        /**
         * 构造一套完整的配色方案。
         * <p>
         * 【初学者理解】创建一个 Scheme 对象就像填写一张色卡——
         * 把每种角色对应的颜色值填上去。参数虽然很多，但其实就是两类：
         * 浅色模式的颜色和深色模式的颜色。每种颜色都用 0xFF 开头的数字表示。
         * <p>
         * 所有颜色值使用 ARGB 格式的 32 位整数（如 {@code 0xFF5B4E9A}），
         * 高 8 位为 Alpha 通道（0xFF 表示完全不透明）。
         * </p>
         *
         * @param name                 方案名称
         * @param primary              浅色-主色
         * @param onPrimary            浅色-主色上的文字色
         * @param primaryContainer     浅色-主色容器色
         * @param onPrimaryContainer   浅色-主色容器上的文字色
         * @param secondary            浅色-次色
         * @param onSecondary          浅色-次色上的文字色
         * @param secondaryContainer   浅色-次色容器色
         * @param onSecondaryContainer 浅色-次色容器上的文字色
         * @param surface              浅色-表面色
         * @param onSurface            浅色-表面上的文字色
         * @param surfaceVariant       浅色-表面变体色
         * @param onSurfaceVariant     浅色-表面变体上的文字色
         * @param background           浅色-背景色
         * @param onBackground         浅色-背景上的文字色
         * @param tabIndicator         Tab 指示器颜色
         * @param navBarActive         导航栏选中项颜色
         * @param cardBorder           卡片边框颜色
         * @param darkSurface          深色-表面色
         * @param darkBackground       深色-背景色
         * @param darkSurfaceVariant   深色-表面变体色
         * @param darkOnSurface        深色-表面上的文字色
         * @param darkOnBackground     深色-背景上的文字色
         * @param darkOnSurfaceVariant 深色-表面变体上的文字色
         * @param darkNavBarInactive   深色-导航栏未选中项颜色
         */
        Scheme(String name,
               int primary, int onPrimary, int primaryContainer, int onPrimaryContainer,
               int secondary, int onSecondary, int secondaryContainer, int onSecondaryContainer,
               int surface, int onSurface, int surfaceVariant, int onSurfaceVariant,
               int background, int onBackground,
               int tabIndicator, int navBarActive, int cardBorder,
               int darkSurface, int darkBackground, int darkSurfaceVariant,
               int darkOnSurface, int darkOnBackground, int darkOnSurfaceVariant,
               int darkNavBarInactive) {
            this.name = name;
            this.primary = primary;
            this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
            this.secondary = secondary;
            this.onSecondary = onSecondary;
            this.secondaryContainer = secondaryContainer;
            this.onSecondaryContainer = onSecondaryContainer;
            this.surface = surface;
            this.onSurface = onSurface;
            this.surfaceVariant = surfaceVariant;
            this.onSurfaceVariant = onSurfaceVariant;
            this.background = background;
            this.onBackground = onBackground;
            this.tabIndicator = tabIndicator;
            this.navBarActive = navBarActive;
            this.cardBorder = cardBorder;
            this.darkSurface = darkSurface;
            this.darkBackground = darkBackground;
            this.darkSurfaceVariant = darkSurfaceVariant;
            this.darkOnSurface = darkOnSurface;
            this.darkOnBackground = darkOnBackground;
            this.darkOnSurfaceVariant = darkOnSurfaceVariant;
            this.darkNavBarInactive = darkNavBarInactive;
        }
    }

    /** 配色方案索引：清朗紫（默认方案） */
    public static final int SCHEME_INDEX_PURPLE = 0;
    /** 配色方案索引：深海蓝 */
    public static final int SCHEME_INDEX_BLUE = 1;
    /** 配色方案索引：竹影绿 */
    public static final int SCHEME_INDEX_GREEN = 2;
    /** 配色方案索引：晨曦橙 */
    public static final int SCHEME_INDEX_ORANGE = 3;
    /** 配色方案索引：蔷薇莓 */
    public static final int SCHEME_INDEX_PINK = 4;
    /** 配色方案索引：极光青 */
    public static final int SCHEME_INDEX_CYAN = 5;
    /** 配色方案索引：墨金 */
    public static final int SCHEME_INDEX_GOLD = 6;
    /** 配色方案索引：朱砂红 */
    public static final int SCHEME_INDEX_RED = 7;

    /** 所有配色方案的只读列表，在静态初始化块中填充 */
    // 【初学者理解】这个列表就像一本色卡册，里面按顺序排列着所有配色方案，
    // 通过索引号（0、1、2...）就能找到对应的方案
    private static final List<Scheme> SCHEMES = new ArrayList<>();

    /**
     * 静态初始化块，创建所有预定义配色方案。
     * <p>
     * 【初学者理解】static 块就像"开仓仪式"——在程序启动时自动执行一次，
     * 把所有配色方案预先创建好并放入列表中。之后只需要从列表中取用即可。
     * <p>
     * 每套方案的色值按照 Material Design 3 色彩角色体系设计，
     * 确保浅色/深色模式下均有足够的对比度以满足无障碍可读性要求。
     * </p>
     */
    static {
        // 第1套：清朗紫——默认配色，以紫色为主色调
        SCHEMES.add(new Scheme("清朗紫",
                0xFF5B4E9A, 0xFFFFFFFF, 0xFFE7DEFF, 0xFF1E124F,
                0xFF0F766E, 0xFFFFFFFF, 0xFFCCFBF1, 0xFF052E2B,
                0xFFFFFBFE, 0xFF1F1B24, 0xFFE8E3EF, 0xFF4B4655,
                0xFFFDFBFF, 0xFF1F1B24,
                0xFF5B4E9A, 0xFF5B4E9A, 0xFFD4CBE8,
                0xFF181622, 0xFF121019, 0xFF282438,
                0xFFE9E4EF, 0xFFF4EFF7, 0xFFCCC4D9,
                0xFF958DA5));

        // 第2套：深海蓝——以蓝色为主色调，沉稳大气
        SCHEMES.add(new Scheme("深海蓝",
                0xFF2563EB, 0xFFFFFFFF, 0xFFDBEAFE, 0xFF172554,
                0xFF0E7490, 0xFFFFFFFF, 0xFFCFFAFE, 0xFF083344,
                0xFFF8FAFC, 0xFF0F172A, 0xFFE2E8F0, 0xFF475569,
                0xFFF1F5F9, 0xFF0F172A,
                0xFF2563EB, 0xFF2563EB, 0xFFBFDBFE,
                0xFF111827, 0xFF0B1120, 0xFF1E293B,
                0xFFE5E7EB, 0xFFF8FAFC, 0xFFCBD5E1,
                0xFF94A3B8));

        // 第3套：竹影绿——以绿色为主色调，清新自然
        SCHEMES.add(new Scheme("竹影绿",
                0xFF047857, 0xFFFFFFFF, 0xFFD1FAE5, 0xFF022C22,
                0xFF4D7C0F, 0xFFFFFFFF, 0xFFECFCCB, 0xFF1A2E05,
                0xFFF7FBF7, 0xFF152018, 0xFFDDEBDD, 0xFF405047,
                0xFFF2F8F2, 0xFF152018,
                0xFF047857, 0xFF047857, 0xFFB7DEC3,
                0xFF101A15, 0xFF0B1410, 0xFF1B2A22,
                0xFFE5EEE8, 0xFFF1F8F3, 0xFFB9C8BF,
                0xFF87988E));

        // 第4套：晨曦橙——以橙色为主色调，温暖活力
        SCHEMES.add(new Scheme("晨曦橙",
                0xFFC2410C, 0xFFFFFFFF, 0xFFFFEDD5, 0xFF431407,
                0xFFB45309, 0xFFFFFFFF, 0xFFFEF3C7, 0xFF451A03,
                0xFFFFFBF5, 0xFF211A14, 0xFFF1E5D8, 0xFF5C4B3B,
                0xFFFFF7ED, 0xFF211A14,
                0xFFC2410C, 0xFFC2410C, 0xFFFED7AA,
                0xFF1D1712, 0xFF15100C, 0xFF30251B,
                0xFFEFE5DB, 0xFFFFF4E8, 0xFFD0BFAE,
                0xFFA39485));

        // 第5套：蔷薇莓——以粉红色为主色调，浪漫柔美
        SCHEMES.add(new Scheme("蔷薇莓",
                0xFFBE123C, 0xFFFFFFFF, 0xFFFFE4E6, 0xFF4C0519,
                0xFF7C3AED, 0xFFFFFFFF, 0xFFEDE9FE, 0xFF2E1065,
                0xFFFFF7FA, 0xFF25171D, 0xFFF3E2EA, 0xFF5B4350,
                0xFFFFF1F5, 0xFF25171D,
                0xFFBE123C, 0xFFBE123C, 0xFFFDA4AF,
                0xFF1F1419, 0xFF170F13, 0xFF33242B,
                0xFFF1E3E8, 0xFFFFF1F6, 0xFFD4BCC6,
                0xFF9E8992));

        // 第6套：极光青——以青色为主色调，科技感十足
        SCHEMES.add(new Scheme("极光青",
                0xFF0891B2, 0xFFFFFFFF, 0xFFCFFAFE, 0xFF083344,
                0xFF059669, 0xFFFFFFFF, 0xFFD1FAE5, 0xFF022C22,
                0xFFF6FEFF, 0xFF102024, 0xFFD9F0F3, 0xFF3E5459,
                0xFFECFEFF, 0xFF102024,
                0xFF0891B2, 0xFF0891B2, 0xFFA5F3FC,
                0xFF0D1B1F, 0xFF081316, 0xFF172D33,
                0xFFDFF1F5, 0xFFF0FDFF, 0xFFB4C9CE,
                0xFF87A0A6));

        // 第7套：墨金——以金色为主色调，典雅高贵
        SCHEMES.add(new Scheme("墨金",
                0xFFA16207, 0xFFFFFFFF, 0xFFFEF3C7, 0xFF422006,
                0xFF4B5563, 0xFFFFFFFF, 0xFFE5E7EB, 0xFF111827,
                0xFFFFFCF4, 0xFF1F1B14, 0xFFEDE3CF, 0xFF594E3D,
                0xFFFDF8EC, 0xFF1F1B14,
                0xFFA16207, 0xFFA16207, 0xFFFDE68A,
                0xFF171510, 0xFF100F0B, 0xFF282417,
                0xFFECE5D8, 0xFFFFF7E8, 0xFFCFC3AB,
                0xFF9A8F7A));

        // 第8套：朱砂红——以红色为主色调，热情奔放
        SCHEMES.add(new Scheme("朱砂红",
                0xFFB91C1C, 0xFFFFFFFF, 0xFFFEE2E2, 0xFF450A0A,
                0xFF0F766E, 0xFFFFFFFF, 0xFFCCFBF1, 0xFF042F2E,
                0xFFFFF8F8, 0xFF251717, 0xFFF2E2E2, 0xFF5D4444,
                0xFFFFF5F5, 0xFF251717,
                0xFFB91C1C, 0xFFB91C1C, 0xFFFCA5A5,
                0xFF1F1313, 0xFF160D0D, 0xFF332121,
                0xFFF2DEDE, 0xFFFFF0F0, 0xFFD3BABA,
                0xFF9E8585));
    }

    /**
     * 获取所有配色方案列表。
     * <p>
     * 【初学者理解】返回整个色卡册，供设置页面展示所有可选的配色方案。
     *
     * @return 不可修改的配色方案列表
     */
    public static List<Scheme> getSchemes() { return SCHEMES; }

    /**
     * 规范化配色方案索引，防止越界。
     * <p>
     * 【初学者理解】就像图书馆查书号——如果输入的书号不存在，
     * 就默认给你第0号书。这样即使传入了错误的编号，应用也不会崩溃。
     * <p>
     * 当索引为负数或超出方案数量范围时，回退到默认方案（索引 0），
     * 确保应用不会因无效索引而崩溃。
     * </p>
     *
     * @param index 原始索引值
     * @return 有效的索引值，越界时返回 0
     */
    public static int normalizeSchemeIndex(int index) {
        if (index < 0 || index >= SCHEMES.size()) return 0;
        return index;
    }

    /**
     * 根据索引获取配色方案。
     * <p>
     * 【初学者理解】根据编号从色卡册中取出对应的配色方案。
     * 如果编号不合法（比如负数或超出范围），会自动回退到第0号方案（清朗紫），
     * 所以调用这个方法永远不会出错。
     * <p>
     * 内部调用 {@link #normalizeSchemeIndex(int)} 确保索引有效，
     * 因此传入任何整数均不会抛出异常。
     * </p>
     *
     * @param index 配色方案索引，越界时自动回退到索引 0
     * @return 对应的配色方案实例
     */
    public static Scheme getScheme(int index) {
        return SCHEMES.get(normalizeSchemeIndex(index));
    }

    /**
     * 获取配色方案总数。
     * <p>
     * 【初学者理解】返回色卡册里一共有多少套配色方案，供设置页面显示选项数量。
     *
     * @return 当前已定义的配色方案数量
     */
    public static int getSchemeCount() { return SCHEMES.size(); }

    /**
     * 将配色方案应用到整个 Activity。
     * <p>
     * 【初学者理解】这个方法就像"一键换装"——把选好的配色方案一次性应用到整个页面上，
     * 包括状态栏、导航栏、背景、底部导航、Tab标签等所有可见元素。
     * <p>
     * 依次设置以下 UI 元素的颜色：
     * <ol>
     *   <li>状态栏和导航栏背景色</li>
     *   <li>根视图（{@code android.R.id.content}）背景色</li>
     *   <li>底部导航栏（{@code R.id.nav_view}）背景色和图标/文字的选中/未选中色</li>
     *   <li>Tab 布局（{@code R.id.tab_layout}）的指示器色、文字色和背景色</li>
     * </ol>
     * 所有颜色根据 {@code isDark} 参数从方案中选取浅色或深色变体。
     * </p>
     *
     * @param activity 目标 Activity
     * @param scheme   要应用的配色方案
     * @param isDark   是否为深色模式，{@code true} 使用深色变体色值
     */
    public static void applyScheme(Activity activity, Scheme scheme, boolean isDark) {
        Window window = activity.getWindow();

        // 根据浅色/深色模式选取对应的角色色值
        // 【初学者理解】就像选衣服时看天气——晴天选浅色系列，阴天选深色系列
        int surfaceColor = isDark ? scheme.darkSurface : scheme.surface;
        int bgColor = isDark ? scheme.darkBackground : scheme.background;
        int surfaceVarColor = isDark ? scheme.darkSurfaceVariant : scheme.surfaceVariant;
        int onSurfaceColor = isDark ? scheme.darkOnSurface : scheme.onSurface;
        int onSurfaceVarColor = isDark ? scheme.darkOnSurfaceVariant : scheme.onSurfaceVariant;
        // 深色模式下导航栏未选中项使用专用暗色，浅色模式下复用 onSurfaceVariant
        int navBarInactive = isDark ? scheme.darkNavBarInactive : scheme.onSurfaceVariant;

        // 设置状态栏（手机顶部显示时间、电量的那一栏）和导航栏的背景色
        window.setStatusBarColor(surfaceColor);
        window.setNavigationBarColor(surfaceColor);

        // 设置根视图背景色（整个页面的最底层背景）
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setBackgroundColor(bgColor);
        }

        // 设置底部导航栏的颜色
        // 【初学者理解】底部导航栏就是页面底部那排图标按钮（首页、分类、设置等）
        BottomNavigationView navView = activity.findViewById(R.id.nav_view);
        if (navView != null) {
            navView.setBackgroundColor(surfaceColor);
            // 构建选中/未选中两种状态的 ColorStateList
            // 【初学者理解】ColorStateList 就像一个"变色规则"——
            // 按钮被选中时用一种颜色，没被选中时用另一种颜色
            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {-android.R.attr.state_checked}
            };
            int[] colors = new int[] { scheme.navBarActive, navBarInactive };
            navView.setItemIconTintList(new android.content.res.ColorStateList(states, colors));
            navView.setItemTextColor(new android.content.res.ColorStateList(states, colors));
        }

        // 设置 TabLayout 的指示器、文字和背景颜色
        // 【初学者理解】TabLayout 就是页面顶部的标签栏，可以左右滑动切换分类
        TabLayout tabLayout = activity.findViewById(R.id.tab_layout);
        if (tabLayout != null) {
            tabLayout.setSelectedTabIndicatorColor(scheme.tabIndicator);
            // 未选中文字使用 onSurfaceVariant，选中文字使用 primary
            tabLayout.setTabTextColors(onSurfaceVarColor, scheme.primary);
            tabLayout.setBackgroundColor(surfaceVarColor);
        }
    }

    /**
     * 将配色方案应用到单个 View。
     * <p>
     * 【初学者理解】和 applyScheme 不同，这个方法是给单个控件"换装"的。
     * 比如某个 Fragment 中新创建了一个底部导航栏或标签栏，
     * 需要单独给它应用配色方案，就可以用这个方法。
     * <p>
     * 根据 View 的实际类型（{@link BottomNavigationView} 或 {@link TabLayout}）
     * 应用对应的颜色配置。适用于 Fragment 或动态创建的视图中局部应用配色。
     * </p>
     *
     * @param view   目标 View，必须是 BottomNavigationView 或 TabLayout 实例
     * @param scheme 要应用的配色方案
     * @param isDark 是否为深色模式
     */
    public static void applySchemeToView(View view, Scheme scheme, boolean isDark) {
        // 根据浅色/深色模式选取对应的角色色值
        int surfaceColor = isDark ? scheme.darkSurface : scheme.surface;
        int surfaceVarColor = isDark ? scheme.darkSurfaceVariant : scheme.surfaceVariant;
        int onSurfaceVarColor = isDark ? scheme.darkOnSurfaceVariant : scheme.onSurfaceVariant;
        int navBarInactive = isDark ? scheme.darkNavBarInactive : scheme.onSurfaceVariant;

        if (view instanceof BottomNavigationView) {
            // 如果是底部导航栏控件，设置背景色和图标/文字颜色
            BottomNavigationView navView = (BottomNavigationView) view;
            navView.setBackgroundColor(surfaceColor);
            // 构建选中/未选中两种状态的 ColorStateList
            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {-android.R.attr.state_checked}
            };
            int[] colors = new int[] { scheme.navBarActive, navBarInactive };
            navView.setItemIconTintList(new android.content.res.ColorStateList(states, colors));
            navView.setItemTextColor(new android.content.res.ColorStateList(states, colors));
        } else if (view instanceof TabLayout) {
            // 如果是标签栏控件，设置指示器、文字和背景颜色
            TabLayout tabLayout = (TabLayout) view;
            tabLayout.setSelectedTabIndicatorColor(scheme.tabIndicator);
            // 未选中文字使用 onSurfaceVariant，选中文字使用 primary
            tabLayout.setTabTextColors(onSurfaceVarColor, scheme.primary);
            tabLayout.setBackgroundColor(surfaceVarColor);
        }
    }
}

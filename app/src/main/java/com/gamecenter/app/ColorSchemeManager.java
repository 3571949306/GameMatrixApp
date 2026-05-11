package com.gamecenter.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;

public class ColorSchemeManager {

    public static class Scheme {
        public final String name;
        public final int primary;
        public final int onPrimary;
        public final int primaryContainer;
        public final int onPrimaryContainer;
        public final int secondary;
        public final int onSecondary;
        public final int secondaryContainer;
        public final int onSecondaryContainer;
        public final int surface;
        public final int onSurface;
        public final int surfaceVariant;
        public final int onSurfaceVariant;
        public final int background;
        public final int onBackground;
        public final int tabIndicator;
        public final int navBarActive;
        public final int cardBorder;
        public final int darkSurface;
        public final int darkBackground;
        public final int darkSurfaceVariant;
        public final int darkOnSurface;
        public final int darkOnBackground;
        public final int darkOnSurfaceVariant;
        public final int darkNavBarInactive;

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

    public static final int SCHEME_INDEX_PURPLE = 0;
    public static final int SCHEME_INDEX_BLUE = 1;
    public static final int SCHEME_INDEX_GREEN = 2;
    public static final int SCHEME_INDEX_ORANGE = 3;
    public static final int SCHEME_INDEX_PINK = 4;
    public static final int SCHEME_INDEX_CYAN = 5;
    public static final int SCHEME_INDEX_GOLD = 6;
    public static final int SCHEME_INDEX_RED = 7;

    private static final List<Scheme> SCHEMES = new ArrayList<>();

    static {
        SCHEMES.add(new Scheme("清朗紫",
                0xFF5B4E9A, 0xFFFFFFFF, 0xFFE7DEFF, 0xFF1E124F,
                0xFF0F766E, 0xFFFFFFFF, 0xFFCCFBF1, 0xFF052E2B,
                0xFFFFFBFE, 0xFF1F1B24, 0xFFE8E3EF, 0xFF4B4655,
                0xFFFDFBFF, 0xFF1F1B24,
                0xFF5B4E9A, 0xFF5B4E9A, 0xFFD4CBE8,
                0xFF181622, 0xFF121019, 0xFF282438,
                0xFFE9E4EF, 0xFFF4EFF7, 0xFFCCC4D9,
                0xFF958DA5));

        SCHEMES.add(new Scheme("深海蓝",
                0xFF2563EB, 0xFFFFFFFF, 0xFFDBEAFE, 0xFF172554,
                0xFF0E7490, 0xFFFFFFFF, 0xFFCFFAFE, 0xFF083344,
                0xFFF8FAFC, 0xFF0F172A, 0xFFE2E8F0, 0xFF475569,
                0xFFF1F5F9, 0xFF0F172A,
                0xFF2563EB, 0xFF2563EB, 0xFFBFDBFE,
                0xFF111827, 0xFF0B1120, 0xFF1E293B,
                0xFFE5E7EB, 0xFFF8FAFC, 0xFFCBD5E1,
                0xFF94A3B8));

        SCHEMES.add(new Scheme("竹影绿",
                0xFF047857, 0xFFFFFFFF, 0xFFD1FAE5, 0xFF022C22,
                0xFF4D7C0F, 0xFFFFFFFF, 0xFFECFCCB, 0xFF1A2E05,
                0xFFF7FBF7, 0xFF152018, 0xFFDDEBDD, 0xFF405047,
                0xFFF2F8F2, 0xFF152018,
                0xFF047857, 0xFF047857, 0xFFB7DEC3,
                0xFF101A15, 0xFF0B1410, 0xFF1B2A22,
                0xFFE5EEE8, 0xFFF1F8F3, 0xFFB9C8BF,
                0xFF87988E));

        SCHEMES.add(new Scheme("晨曦橙",
                0xFFC2410C, 0xFFFFFFFF, 0xFFFFEDD5, 0xFF431407,
                0xFFB45309, 0xFFFFFFFF, 0xFFFEF3C7, 0xFF451A03,
                0xFFFFFBF5, 0xFF211A14, 0xFFF1E5D8, 0xFF5C4B3B,
                0xFFFFF7ED, 0xFF211A14,
                0xFFC2410C, 0xFFC2410C, 0xFFFED7AA,
                0xFF1D1712, 0xFF15100C, 0xFF30251B,
                0xFFEFE5DB, 0xFFFFF4E8, 0xFFD0BFAE,
                0xFFA39485));

        SCHEMES.add(new Scheme("蔷薇莓",
                0xFFBE123C, 0xFFFFFFFF, 0xFFFFE4E6, 0xFF4C0519,
                0xFF7C3AED, 0xFFFFFFFF, 0xFFEDE9FE, 0xFF2E1065,
                0xFFFFF7FA, 0xFF25171D, 0xFFF3E2EA, 0xFF5B4350,
                0xFFFFF1F5, 0xFF25171D,
                0xFFBE123C, 0xFFBE123C, 0xFFFDA4AF,
                0xFF1F1419, 0xFF170F13, 0xFF33242B,
                0xFFF1E3E8, 0xFFFFF1F6, 0xFFD4BCC6,
                0xFF9E8992));

        SCHEMES.add(new Scheme("极光青",
                0xFF0891B2, 0xFFFFFFFF, 0xFFCFFAFE, 0xFF083344,
                0xFF059669, 0xFFFFFFFF, 0xFFD1FAE5, 0xFF022C22,
                0xFFF6FEFF, 0xFF102024, 0xFFD9F0F3, 0xFF3E5459,
                0xFFECFEFF, 0xFF102024,
                0xFF0891B2, 0xFF0891B2, 0xFFA5F3FC,
                0xFF0D1B1F, 0xFF081316, 0xFF172D33,
                0xFFDFF1F5, 0xFFF0FDFF, 0xFFB4C9CE,
                0xFF87A0A6));

        SCHEMES.add(new Scheme("墨金",
                0xFFA16207, 0xFFFFFFFF, 0xFFFEF3C7, 0xFF422006,
                0xFF4B5563, 0xFFFFFFFF, 0xFFE5E7EB, 0xFF111827,
                0xFFFFFCF4, 0xFF1F1B14, 0xFFEDE3CF, 0xFF594E3D,
                0xFFFDF8EC, 0xFF1F1B14,
                0xFFA16207, 0xFFA16207, 0xFFFDE68A,
                0xFF171510, 0xFF100F0B, 0xFF282417,
                0xFFECE5D8, 0xFFFFF7E8, 0xFFCFC3AB,
                0xFF9A8F7A));

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

    public static List<Scheme> getSchemes() { return SCHEMES; }
    public static int normalizeSchemeIndex(int index) {
        if (index < 0 || index >= SCHEMES.size()) return 0;
        return index;
    }
    public static Scheme getScheme(int index) {
        return SCHEMES.get(normalizeSchemeIndex(index));
    }
    public static int getSchemeCount() { return SCHEMES.size(); }

    public static void applyScheme(Activity activity, Scheme scheme, boolean isDark) {
        Window window = activity.getWindow();

        int surfaceColor = isDark ? scheme.darkSurface : scheme.surface;
        int bgColor = isDark ? scheme.darkBackground : scheme.background;
        int surfaceVarColor = isDark ? scheme.darkSurfaceVariant : scheme.surfaceVariant;
        int onSurfaceColor = isDark ? scheme.darkOnSurface : scheme.onSurface;
        int onSurfaceVarColor = isDark ? scheme.darkOnSurfaceVariant : scheme.onSurfaceVariant;
        int navBarInactive = isDark ? scheme.darkNavBarInactive : scheme.onSurfaceVariant;

        window.setStatusBarColor(surfaceColor);
        window.setNavigationBarColor(surfaceColor);

        View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setBackgroundColor(bgColor);
        }

        BottomNavigationView navView = activity.findViewById(R.id.nav_view);
        if (navView != null) {
            navView.setBackgroundColor(surfaceColor);
            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {-android.R.attr.state_checked}
            };
            int[] colors = new int[] { scheme.navBarActive, navBarInactive };
            navView.setItemIconTintList(new android.content.res.ColorStateList(states, colors));
            navView.setItemTextColor(new android.content.res.ColorStateList(states, colors));
        }

        TabLayout tabLayout = activity.findViewById(R.id.tab_layout);
        if (tabLayout != null) {
            tabLayout.setSelectedTabIndicatorColor(scheme.tabIndicator);
            tabLayout.setTabTextColors(onSurfaceVarColor, scheme.primary);
            tabLayout.setBackgroundColor(surfaceVarColor);
        }
    }

    public static void applySchemeToView(View view, Scheme scheme, boolean isDark) {
        int surfaceColor = isDark ? scheme.darkSurface : scheme.surface;
        int surfaceVarColor = isDark ? scheme.darkSurfaceVariant : scheme.surfaceVariant;
        int onSurfaceVarColor = isDark ? scheme.darkOnSurfaceVariant : scheme.onSurfaceVariant;
        int navBarInactive = isDark ? scheme.darkNavBarInactive : scheme.onSurfaceVariant;

        if (view instanceof BottomNavigationView) {
            BottomNavigationView navView = (BottomNavigationView) view;
            navView.setBackgroundColor(surfaceColor);
            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_checked},
                    new int[] {-android.R.attr.state_checked}
            };
            int[] colors = new int[] { scheme.navBarActive, navBarInactive };
            navView.setItemIconTintList(new android.content.res.ColorStateList(states, colors));
            navView.setItemTextColor(new android.content.res.ColorStateList(states, colors));
        } else if (view instanceof TabLayout) {
            TabLayout tabLayout = (TabLayout) view;
            tabLayout.setSelectedTabIndicatorColor(scheme.tabIndicator);
            tabLayout.setTabTextColors(onSurfaceVarColor, scheme.primary);
            tabLayout.setBackgroundColor(surfaceVarColor);
        }
    }
}

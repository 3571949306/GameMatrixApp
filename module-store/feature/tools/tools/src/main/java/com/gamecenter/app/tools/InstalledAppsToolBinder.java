package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 本地应用清单工具：展示当前系统可见的应用、版本和包名。
 *
 * <p>Android 11 之后应用可见性由系统控制，因此这里不申请 QUERY_ALL_PACKAGES，
 * 只展示 PackageManager 按当前安装环境允许返回的结果。点击一行即可复制包名。</p>
 */
public final class InstalledAppsToolBinder implements ToolBinder {

    private final List<AppEntry> entries = new ArrayList<>();
    private LinearLayout listContainer;
    private TextView summaryView;
    private EditText searchView;
    private Context appContext;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        appContext = context.getApplicationContext();
        listContainer = contentView.findViewById(R.id.ll_apps_list);
        summaryView = contentView.findViewById(R.id.tv_apps_summary);
        searchView = contentView.findViewById(R.id.et_apps_search);

        if (searchView != null) {
            searchView.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(s == null ? "" : s.toString()); }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        View refresh = contentView.findViewById(R.id.btn_apps_refresh);
        if (refresh != null) refresh.setOnClickListener(v -> loadApps());
        loadApps();
    }

    private void loadApps() {
        if (listContainer == null || appContext == null) return;
        PackageManager pm = appContext.getPackageManager();
        List<ApplicationInfo> installed;
        try {
            installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (RuntimeException error) {
            entries.clear();
            render("");
            if (summaryView != null) summaryView.setText("无法读取应用列表");
            return;
        }

        entries.clear();
        for (ApplicationInfo info : installed) {
            if (info == null || info.packageName == null) continue;
            String label;
            try {
                label = String.valueOf(pm.getApplicationLabel(info));
            } catch (Exception ignored) {
                label = info.packageName;
            }
            String version = "未知版本";
            try {
                PackageInfo packageInfo = pm.getPackageInfo(info.packageName, 0);
                if (packageInfo.versionName != null && !packageInfo.versionName.isEmpty()) {
                    version = packageInfo.versionName;
                }
            } catch (Exception ignored) {
            }
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            entries.add(new AppEntry(label, info.packageName, version, system));
        }
        Collections.sort(entries, Comparator
                .comparing((AppEntry item) -> item.system)
                .thenComparing(item -> item.label.toLowerCase(Locale.getDefault()))
                .thenComparing(item -> item.packageName));
        render(searchView == null ? "" : searchView.getText().toString());
    }

    private void render(String query) {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        int total = entries.size();
        int userCount = 0;
        int shown = 0;
        for (AppEntry entry : entries) {
            if (!entry.system) userCount++;
            if (!normalized.isEmpty()
                    && !entry.label.toLowerCase(Locale.getDefault()).contains(normalized)
                    && !entry.packageName.toLowerCase(Locale.getDefault()).contains(normalized)) {
                continue;
            }
            if (shown >= 120) break;
            listContainer.addView(createRow(entry));
            shown++;
        }
        if (summaryView != null) {
            summaryView.setText(String.format(Locale.getDefault(), "共 %d 个可见应用 · 用户 %d · 系统 %d · 当前显示 %d",
                    total, userCount, Math.max(0, total - userCount), shown));
        }
    }

    private View createRow(AppEntry entry) {
        LinearLayout row = new LinearLayout(listContainer.getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        row.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x12000000);
        background.setCornerRadius(dp(12));
        row.setBackground(background);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);
        row.setClickable(true);
        row.setFocusable(true);

        TextView title = new TextView(row.getContext());
        title.setText(entry.label + (entry.system ? "  · 系统" : ""));
        title.setTextColor(resolveTextColor(row.getContext()));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(title);

        TextView detail = new TextView(row.getContext());
        detail.setText(entry.packageName + "  ·  v" + entry.version);
        int primaryColor = resolveTextColor(row.getContext());
        detail.setTextColor((primaryColor & 0x00FFFFFF) | 0x99000000);
        detail.setTextSize(12f);
        detail.setTextIsSelectable(true);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        row.addView(detail, detailParams);

        row.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("应用包名", entry.packageName));
                Toast.makeText(row.getContext(), R.string.tool_apps_package_copied, Toast.LENGTH_SHORT).show();
            }
        });
        return row;
    }

    private int dp(int value) {
        return Math.round(value * listContainer.getResources().getDisplayMetrics().density);
    }

    private static int resolveTextColor(Context context) {
        android.content.res.TypedArray colors = context.obtainStyledAttributes(
                new int[]{android.R.attr.textColorPrimary});
        try {
            return colors.getColor(0, Color.WHITE);
        } finally {
            colors.recycle();
        }
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final String version;
        final boolean system;

        AppEntry(String label, String packageName, String version, boolean system) {
            this.label = label;
            this.packageName = packageName;
            this.version = version;
            this.system = system;
        }
    }
}

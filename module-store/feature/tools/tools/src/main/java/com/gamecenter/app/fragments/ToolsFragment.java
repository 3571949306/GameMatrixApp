package com.gamecenter.app.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.ColorSchemeManager;
import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.tools.AdbWorkbenchToolBinder;
import com.gamecenter.app.tools.AdvancedToolBinders;
import com.gamecenter.app.tools.BatteryToolBinder;
import com.gamecenter.app.tools.BubbleLevelToolBinder;
import com.gamecenter.app.tools.CompassToolBinder;
import com.gamecenter.app.tools.ClipboardToolBinder;
import com.gamecenter.app.tools.ColorPickerToolBinder;
import com.gamecenter.app.tools.ColorPlusToolBinder;
import com.gamecenter.app.tools.CryptoToolBinder;
import com.gamecenter.app.tools.DeviceOverviewToolBinder;
import com.gamecenter.app.tools.DiagnosticReportToolBinder;
import com.gamecenter.app.tools.DnsLookupToolBinder;
import com.gamecenter.app.tools.DnsToolBinder;
import com.gamecenter.app.tools.FileHashToolBinder;
import com.gamecenter.app.tools.FloatingMonitorToolBinder;
import com.gamecenter.app.tools.HashToolBinder;
import com.gamecenter.app.tools.InstalledAppsToolBinder;
import com.gamecenter.app.tools.IpToolBinder;
import com.gamecenter.app.tools.JwtParserToolBinder;
import com.gamecenter.app.tools.LanScanToolBinder;
import com.gamecenter.app.tools.NetworkDiagnosisToolBinder;
import com.gamecenter.app.tools.PasswordGeneratorToolBinder;
import com.gamecenter.app.tools.PermissionPrivacyToolBinder;
import com.gamecenter.app.tools.PingToolBinder;
import com.gamecenter.app.tools.PortScanToolBinder;
import com.gamecenter.app.tools.QrPlusToolBinder;
import com.gamecenter.app.tools.RadixConverterToolBinder;
import com.gamecenter.app.tools.RegexTestToolBinder;
import com.gamecenter.app.tools.ScreenTestToolBinder;
import com.gamecenter.app.tools.ScreenToolBinder;
import com.gamecenter.app.tools.SensorToolBinder;
import com.gamecenter.app.tools.SatelliteToolBinder;
import com.gamecenter.app.tools.SoundMeterToolBinder;
import com.gamecenter.app.tools.SpeedTestToolBinder;
import com.gamecenter.app.tools.SubnetToolBinder;
import com.gamecenter.app.tools.SystemInfoToolBinder;
import com.gamecenter.app.tools.TextCodecToolBinder;
import com.gamecenter.app.tools.ToolBinder;
import com.gamecenter.app.tools.ToolboxDashboardController;
import com.gamecenter.app.tools.ToolSection;
import com.gamecenter.app.tools.ToolSectionStore;
import com.gamecenter.app.tools.TracerouteToolBinder;
import com.gamecenter.app.tools.UnitConverterToolBinder;
import com.gamecenter.app.tools.UuidGeneratorToolBinder;
import com.gamecenter.app.tools.WifiToolBinder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToolsFragment extends Fragment {

    private static final String TAG = "ToolsFragment";

    private ToolSectionStore store;
    private List<ToolSection> sections;
    private BatteryToolBinder batteryToolBinder;
    private CompassToolBinder compassToolBinder;
    private SatelliteToolBinder satelliteToolBinder;
    private final Map<String, ToolBinder> binders = new HashMap<>();
    private ExecutorService executor;

    private ToolboxDashboardController dashboardController;
    private OnBackPressedCallback backCallback;

    private View hubArea;
    private View workspaceArea;
    private FrameLayout workspaceContent;
    private TextView wsTitle;
    private ImageButton wsFavButton;
    private LinearLayout llFavBlock;
    private LinearLayout llRecentBlock;
    private LinearLayout llSections;
    private TextView tvToolsEmpty;
    private TextInputEditText etSearch;
    private String searchKeywordLower = "";

    private ToolSection currentSection;

    public interface PickFileCallback {
        void onFilePicked(Uri uri);
    }

    /**
     * Narrow, on-demand permission bridge for the GNSS tool. The tool itself receives no
     * Activity reference and cannot request permission while merely rendering its workspace.
     */
    public interface SatellitePermissionCallback {
        void onSatelliteLocationPermissionResult(boolean fineLocationGranted);
    }

    private PickFileCallback pendingPickFileCallback;
    private ActivityResultLauncher<String[]> pickFileLauncher;
    private SatellitePermissionCallback pendingSatellitePermissionCallback;
    private ActivityResultLauncher<String[]> satelliteLocationPermissionLauncher;

    public void requestPickFile(PickFileCallback callback, String[] mimeTypes) {
        this.pendingPickFileCallback = callback;
        pickFileLauncher.launch(mimeTypes);
    }

    /**
     * Requests foreground location only after an explicit satellite-tool action.
     *
     * <p>Android 12+ expects coarse and fine location to be requested together. GNSS callbacks
     * still require fine permission, and the result passed back therefore reflects only the fine
     * grant. This method never requests background location.</p>
     */
    public void requestSatelliteLocationPermission(SatellitePermissionCallback callback) {
        if (callback == null) {
            return;
        }
        if (!isAdded()) {
            callback.onSatelliteLocationPermissionResult(false);
            return;
        }
        if (requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            callback.onSatelliteLocationPermissionResult(true);
            return;
        }
        if (satelliteLocationPermissionLauncher == null || pendingSatellitePermissionCallback != null) {
            callback.onSatelliteLocationPermissionResult(false);
            return;
        }
        pendingSatellitePermissionCallback = callback;
        satelliteLocationPermissionLauncher.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newCachedThreadPool();
        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    PickFileCallback cb = pendingPickFileCallback;
                    pendingPickFileCallback = null;
                    if (cb != null && uri != null) {
                        try {
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {
                        }
                        cb.onFilePicked(uri);
                    }
                });
        satelliteLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    SatellitePermissionCallback callback = pendingSatellitePermissionCallback;
                    pendingSatellitePermissionCallback = null;
                    boolean fineGranted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    if (!fineGranted && isAdded()) {
                        fineGranted = requireContext().checkSelfPermission(
                                Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED;
                    }
                    if (callback != null) {
                        callback.onSatelliteLocationPermissionResult(fineGranted);
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pendingSatellitePermissionCallback = null;
        if (compassToolBinder != null) {
            compassToolBinder.unbind();
        }
        if (satelliteToolBinder != null) {
            satelliteToolBinder.unbind();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void initBinders() {
        binders.put(AdbWorkbenchToolBinder.TOOL_ID, new AdbWorkbenchToolBinder());
        binders.put("network_diagnosis", new NetworkDiagnosisToolBinder());
        binders.put("diagnostic_report", new DiagnosticReportToolBinder());
        binders.put("dns_lookup", new DnsLookupToolBinder());
        binders.put("lan_scan", new LanScanToolBinder());
        binders.put("text_codec", new TextCodecToolBinder());
        binders.put("file_hash", new FileHashToolBinder());
        binders.put("qr_plus", new QrPlusToolBinder());
        binders.put("color_plus", new ColorPlusToolBinder());
        binders.put("permission_privacy", new PermissionPrivacyToolBinder());
        binders.put("ip", new IpToolBinder());
        binders.put("dns", new DnsToolBinder());
        binders.put("wifi", new WifiToolBinder());
        binders.put("speedtest", new SpeedTestToolBinder());
        binders.put("portscan", new PortScanToolBinder());
        batteryToolBinder = new BatteryToolBinder();
        binders.put("battery", batteryToolBinder);
        binders.put("ping", new PingToolBinder());
        binders.put("traceroute", new TracerouteToolBinder());
        binders.put("subnet", new SubnetToolBinder());
        binders.put("screen", new ScreenToolBinder());
        binders.put("sensor", new SensorToolBinder());
        binders.put("hash", new HashToolBinder());
        binders.put("clipboard", new ClipboardToolBinder());
        binders.put("color", new ColorPickerToolBinder());
        binders.put("sysinfo", new SystemInfoToolBinder());
        binders.put("device_overview", new DeviceOverviewToolBinder());
        binders.put("installed_apps", new InstalledAppsToolBinder());
        binders.put("regex_test", new RegexTestToolBinder());
        if (BuildConfig.ENABLE_TOOLS_ENHANCEMENT) {
            binders.put("unit_converter", new UnitConverterToolBinder());
            binders.put("radix_converter", new RadixConverterToolBinder());
            binders.put("password_generator", new PasswordGeneratorToolBinder());
            binders.put("uuid_generator", new UuidGeneratorToolBinder());
            binders.put("crypto_tool", new CryptoToolBinder());
            binders.put("jwt_parser", new JwtParserToolBinder());
        }
        compassToolBinder = new CompassToolBinder();
        binders.put("compass", compassToolBinder);
        satelliteToolBinder = new SatelliteToolBinder();
        binders.put("satellite", satelliteToolBinder);
        binders.put("bubble_level", new BubbleLevelToolBinder());
        binders.put("sound_meter", new SoundMeterToolBinder());
        binders.put("color_test", new ScreenTestToolBinder());
        binders.put("floating_monitor", new FloatingMonitorToolBinder());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        store = new ToolSectionStore(requireContext());
        initBinders();

        hubArea = view.findViewById(R.id.ll_tools_hub);
        workspaceArea = view.findViewById(R.id.fl_tool_workspace);
        workspaceContent = view.findViewById(R.id.fl_ws_content);
        wsTitle = view.findViewById(R.id.tv_ws_title);
        wsFavButton = view.findViewById(R.id.ib_ws_fav);
        llFavBlock = view.findViewById(R.id.ll_fav_block);
        llRecentBlock = view.findViewById(R.id.ll_recent_block);
        llSections = view.findViewById(R.id.ll_sections);
        tvToolsEmpty = view.findViewById(R.id.tv_tools_empty);
        etSearch = view.findViewById(R.id.et_tool_search);
        TextView tvTitle = view.findViewById(R.id.tv_tools_title);
        tvTitle.setText(getString(R.string.app_name));
        TextView tvSubtitle = view.findViewById(R.id.tv_tools_subtitle);
        if (tvSubtitle != null) {
            tvSubtitle.setText("实时仪表盘 · 电池 / 内存 / CPU / 存储");
        }
        View sectionsHeader = view.findViewById(R.id.tv_sections_header);
        if (sectionsHeader != null) {
            sectionsHeader.setVisibility(View.GONE);
        }

        dashboardController = new ToolboxDashboardController();
        View dashboardRoot = view.findViewById(R.id.toolbox_dashboard);
        if (dashboardRoot != null) {
            dashboardController.attach(dashboardRoot);
        }

        ImageButton btnBack = view.findViewById(R.id.ib_ws_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> closeWorkspace());
        }
        if (wsFavButton != null) {
            wsFavButton.setOnClickListener(v -> {
                if (currentSection == null || store == null) return;
                boolean favorite = store.toggleFavorite(currentSection.id);
                updateWorkspaceFavIcon(favorite);
                Toast.makeText(requireContext(), favorite ? "已收藏" : "取消收藏", Toast.LENGTH_SHORT).show();
            });
        }

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeWorkspace();
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String keyword = s == null ? "" : s.toString().trim();
                    searchKeywordLower = keyword.toLowerCase();
                    if (workspaceArea == null || workspaceArea.getVisibility() != View.VISIBLE) {
                        renderSections();
                    }
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        applyColorScheme();
        renderHub();
    }

    private void renderHub() {
        if (store == null) return;
        sections = store.loadSections();
        Set<String> favIds = store.getFavoriteIds();
        List<ToolSection> favorites = new ArrayList<>();
        for (ToolSection s : sections) {
            if (s.visible && favIds.contains(s.id)) {
                favorites.add(s);
            }
        }
        List<ToolSection> recents = new ArrayList<>();
        for (String recentId : store.getRecentIds()) {
            ToolSection s = findById(recentId);
            if (s != null && s.visible) {
                recents.add(s);
            }
        }
        renderShortcutRow(llFavBlock, "收藏", favorites, favIds);
        renderShortcutRow(llRecentBlock, "最近使用", recents, favIds);
        renderSections();
    }

    private void renderShortcutRow(LinearLayout block, String title, List<ToolSection> items, Set<String> favIds) {
        if (block == null) return;
        Context ctx = block.getContext();
        block.removeAllViews();
        if (items.isEmpty()) {
            block.setVisibility(View.GONE);
            return;
        }
        block.setVisibility(View.VISIBLE);
        TextView header = makeHeader(ctx, title);
        header.setPadding(0, dip(14), 0, 0);
        block.addView(header);
        HorizontalScrollView scroller = new HorizontalScrollView(ctx);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dip(8), 0, 0);
        for (ToolSection s : items) {
            View tile = buildTile(ctx, s, favIds.contains(s.id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dip(92), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dip(8), 0);
            row.addView(tile, lp);
        }
        scroller.addView(row);
        block.addView(scroller);
    }

    private void renderSections() {
        if (llSections == null || store == null) return;
        Context ctx = llSections.getContext();
        llSections.removeAllViews();
        Set<String> favIds = store.getFavoriteIds();
        LinkedHashMap<String, List<ToolSection>> groups = new LinkedHashMap<>();
        int matched = 0;
        for (ToolSection s : loadVisibleSections()) {
            if (!matches(s)) continue;
            matched++;
            List<ToolSection> bucket = groups.get(s.category);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(s.category, bucket);
            }
            bucket.add(s);
        }
        for (Map.Entry<String, List<ToolSection>> entry : groups.entrySet()) {
            TextView header = makeHeader(ctx, categoryLabel(entry.getKey()));
            header.setPadding(0, dip(16), 0, 0);
            llSections.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            GridLayout grid = new GridLayout(ctx);
            grid.setColumnCount(4);
            for (ToolSection s : entry.getValue()) {
                View tile = buildTile(ctx, s, favIds.contains(s.id));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                int margin = dip(5);
                lp.setMargins(margin, margin, margin, margin);
                grid.addView(tile, lp);
            }
            llSections.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (tvToolsEmpty != null) {
            tvToolsEmpty.setVisibility(matched == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private boolean matches(ToolSection s) {
        if (searchKeywordLower == null || searchKeywordLower.isEmpty()) return s.visible;
        return s.title.toLowerCase().contains(searchKeywordLower)
                || s.id.toLowerCase().contains(searchKeywordLower)
                || s.description.toLowerCase().contains(searchKeywordLower);
    }

    private List<ToolSection> loadVisibleSections() {
        if (sections == null) {
            sections = store.loadSections();
        }
        return sections;
    }

    private View buildTile(Context ctx, ToolSection section, boolean favorite) {
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View tile = inflater.inflate(R.layout.item_tool_tile, null, false);
        TextView icon = tile.findViewById(R.id.tv_tile_icon);
        TextView label = tile.findViewById(R.id.tv_tile_label);
        TextView star = tile.findViewById(R.id.tv_tile_star);
        if (icon != null) {
            icon.setText(com.gamecenter.app.tools.ToolTileIcons.iconFor(section.id));
        }
        if (label != null) {
            label.setText(section.title);
        }
        if (star != null) {
            star.setVisibility(favorite ? View.VISIBLE : View.GONE);
        }
        tile.setOnClickListener(v -> openWorkspace(section));
        tile.setOnLongClickListener(v -> {
            if (store == null) return true;
            boolean fav = store.toggleFavorite(section.id);
            Toast.makeText(ctx, fav ? "已收藏" : "取消收藏", Toast.LENGTH_SHORT).show();
            renderHub();
            return true;
        });
        return tile;
    }

    private TextView makeHeader(Context ctx, String text) {
        TextView header = new TextView(ctx);
        header.setText(text);
        header.setTextColor(
                resolveAttrColor(ctx, android.R.attr.textColorPrimary, 0xFF1F1B24));
        header.setTextSize(16f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        return header;
    }

    private static int resolveAttrColor(Context ctx, int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (!ctx.getTheme().resolveAttribute(attr, value, true)) return fallback;
        if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        try {
            return androidx.core.content.ContextCompat.getColor(ctx, value.resourceId);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String categoryLabel(String category) {
        if (category == null) return "工具";
        switch (category) {
            case "network": return "网络";
            case "device": return "设备";
            case "tool": return "工具";
            default: return "其他";
        }
    }

    private int dip(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private ToolSection findById(String id) {
        if (sections == null || id == null) return null;
        for (ToolSection s : sections) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private void openWorkspace(ToolSection section) {
        View root = getView();
        if (root == null || section == null || store == null) return;
        currentSection = section;
        if (wsTitle != null) {
            wsTitle.setText(section.title);
        }
        updateWorkspaceFavIcon(store.isFavorite(section.id));

        workspaceContent.removeAllViews();
        Context ctx = requireContext();
        View contentView;
        try {
            contentView = LayoutInflater.from(ctx).inflate(section.contentLayoutId, workspaceContent, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inflate tool workspace: " + section.id, e);
            contentView = makeErrorView(ctx, section.title);
        }
        ToolBinder binder = binders.get(section.id);
        if (binder != null) {
            boolean isAdb = AdbWorkbenchToolBinder.TOOL_ID.equals(section.id);
            if (!isAdb) {
                contentView.setTag(R.id.tag_tools_fragment, ToolsFragment.this);
            }
            try {
                binder.bind(ctx, contentView, executor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind tool workspace: " + section.id, e);
            }
            if (!isAdb) {
                store.incrementUsage(section.id);
                store.recordRecent(section.id);
            }
        }
        // Attach the inflated workspace after binding so every tool (including dynamic
        // module tools) is actually rendered inside the scroll container.
        workspaceContent.addView(contentView);
        hubArea.setVisibility(View.GONE);
        workspaceArea.setVisibility(View.VISIBLE);
        if (backCallback != null) {
            backCallback.setEnabled(true);
        }
    }

    private void closeWorkspace() {
        if (workspaceArea == null || workspaceArea.getVisibility() != View.VISIBLE) return;
        if (currentSection != null && "battery".equals(currentSection.id)
                && batteryToolBinder != null) {
            batteryToolBinder.unbind();
        }
        if (currentSection != null && "compass".equals(currentSection.id)
                && compassToolBinder != null) {
            compassToolBinder.unbind();
        }
        if (currentSection != null && "satellite".equals(currentSection.id)
                && satelliteToolBinder != null) {
            satelliteToolBinder.unbind();
        }
        workspaceContent.removeAllViews();
        workspaceArea.setVisibility(View.GONE);
        hubArea.setVisibility(View.VISIBLE);
        if (backCallback != null) {
            backCallback.setEnabled(false);
        }
        currentSection = null;
        renderHub();
    }

    private void updateWorkspaceFavIcon(boolean favorite) {
        if (wsFavButton != null) {
            wsFavButton.setImageResource(favorite
                    ? R.drawable.ic_star_filled
                    : R.drawable.ic_star_border);
        }
    }

    private View makeErrorView(Context context, String title) {
        TextView errorView = new TextView(context);
        int padding = dip(12);
        errorView.setPadding(padding, padding, padding, padding);
        errorView.setText(context.getString(R.string.tool_load_failed_format, title));
        errorView.setTextColor(0xFFB00020);
        errorView.setTextSize(14);
        return errorView;
    }

    private void applyColorScheme() {
        if (getActivity() == null) return;
        boolean isDark = SettingsManager.isDarkMode(requireContext());
        int schemeIndex = SettingsManager.getInstance(requireContext()).getColorSchemeIndex();
        ColorSchemeManager.Scheme scheme = ColorSchemeManager.getScheme(schemeIndex);
        View root = getView();
        if (root != null) {
            ColorSchemeManager.applySchemeToView(root, scheme, isDark);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dashboardController != null) {
            dashboardController.detach();
            dashboardController = null;
        }
        if (batteryToolBinder != null) {
            batteryToolBinder.unbind();
            batteryToolBinder = null;
        }
        if (compassToolBinder != null) {
            compassToolBinder.unbind();
            compassToolBinder = null;
        }
        if (satelliteToolBinder != null) {
            satelliteToolBinder.unbind();
            satelliteToolBinder = null;
        }
        if (binders != null) {
            for (ToolBinder binder : binders.values()) {
                if (binder instanceof BatteryToolBinder) {
                    ((BatteryToolBinder) binder).unbind();
                } else if (binder instanceof CompassToolBinder) {
                    ((CompassToolBinder) binder).unbind();
                } else if (binder instanceof SatelliteToolBinder) {
                    ((SatelliteToolBinder) binder).unbind();
                }
            }
            binders.clear();
        }
        if (workspaceContent != null) {
            workspaceContent.removeAllViews();
        }
        sections = null;
        currentSection = null;
        hubArea = null;
        workspaceArea = null;
        workspaceContent = null;
        wsTitle = null;
        wsFavButton = null;
        llFavBlock = null;
        llRecentBlock = null;
        llSections = null;
        tvToolsEmpty = null;
        etSearch = null;
        backCallback = null;
    }
}

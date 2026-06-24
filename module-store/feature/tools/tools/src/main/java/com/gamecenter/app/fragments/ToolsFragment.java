package com.gamecenter.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.ColorSchemeManager;
import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.tools.AdvancedToolBinders;
import com.gamecenter.app.tools.BatteryToolBinder;
import com.gamecenter.app.tools.ClipboardToolBinder;
import com.gamecenter.app.tools.ColorPickerToolBinder;
import com.gamecenter.app.tools.ColorPlusToolBinder;
import com.gamecenter.app.tools.DeviceToolBinder;
import com.gamecenter.app.tools.DiagnosticReportToolBinder;
import com.gamecenter.app.tools.DnsLookupToolBinder;
import com.gamecenter.app.tools.DnsToolBinder;
import com.gamecenter.app.tools.FileHashToolBinder;
import com.gamecenter.app.tools.HashToolBinder;
import com.gamecenter.app.tools.IpToolBinder;
import com.gamecenter.app.tools.LanScanToolBinder;
import com.gamecenter.app.tools.NetworkDiagnosisToolBinder;
import com.gamecenter.app.tools.PermissionPrivacyToolBinder;
import com.gamecenter.app.tools.PingToolBinder;
import com.gamecenter.app.tools.PortScanToolBinder;
import com.gamecenter.app.tools.QrPlusToolBinder;
import com.gamecenter.app.tools.QrToolBinder;
import com.gamecenter.app.tools.ScreenToolBinder;
import com.gamecenter.app.tools.SensorToolBinder;
import com.gamecenter.app.tools.SpeedTestToolBinder;
import com.gamecenter.app.tools.SubnetToolBinder;
import com.gamecenter.app.tools.SystemInfoToolBinder;
import com.gamecenter.app.tools.TextCodecToolBinder;
import com.gamecenter.app.tools.UrlEncodeToolBinder;
import com.gamecenter.app.tools.RegexTestToolBinder;
import com.gamecenter.app.tools.JsonFormatToolBinder;
import com.gamecenter.app.tools.Base64ToolBinder;
import com.gamecenter.app.tools.ToolBinder;
import com.gamecenter.app.tools.ToolHelper;
import com.gamecenter.app.tools.ToolSection;
import com.gamecenter.app.tools.ToolSectionStore;
import com.gamecenter.app.tools.TracerouteToolBinder;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.gamecenter.app.tools.WifiToolBinder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工具箱 Fragment — 以可拖拽排序的卡片列表展示所有工具。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>加载并展示所有工具分区（ToolSection），每个分区对应一个可展开的卡片</li>
 *   <li>通过 ToolBinder 机制将各工具的功能逻辑绑定到对应的 UI 布局</li>
 *   <li>支持拖拽排序（ItemTouchHelper）和收藏功能</li>
 *   <li>支持单列/双列布局切换</li>
 * </ul>
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>所有 ToolBinder 实例在 initBinders 中统一注册到 Map，以工具 ID 为键</li>
 *   <li>工具排序和收藏状态持久化到 ToolSectionStore</li>
 *   <li>BatteryToolBinder 需要在视图销毁时调用 unbind() 注销广播接收器</li>
 *   <li>使用 CachedThreadPool 以支持多个工具并发执行网络操作</li>
 * </ul>
 * </p>
 */
public class ToolsFragment extends Fragment {

    private static final String TAG = "ToolsFragment";

    private RecyclerView recyclerView;
    private ToolsAdapter adapter;
    /** 工具分区持久化存储，管理排序和收藏状态 */
    private ToolSectionStore store;
    /** 当前加载的工具分区列表 */
    private List<ToolSection> sections;
    // 2026-06-23: 原始完整列表（用于搜索/分类过滤还原）
    private List<ToolSection> allSections;
    /** 布局模式：0=单列，1=双列 */
    private int layoutMode;

    /** 电池工具绑定器，需在视图销毁时显式 unbind */
    private BatteryToolBinder batteryToolBinder;
    /** 工具 ID 到 ToolBinder 实例的映射表 */
    private final Map<String, ToolBinder> binders = new HashMap<>();

    /** 用于执行工具中耗时操作的线程池 */
    private ExecutorService executor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newCachedThreadPool();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 初始化所有工具绑定器，将工具 ID 映射到对应的 Binder 实例。
     * <p>
     * 新增工具时需要在此方法中注册对应的 Binder。
     * BatteryToolBinder 额外保存引用，因为需要在 onDestroyView 中调用 unbind()。
     * </p>
     */
    private void initBinders() {
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
        binders.put("qr", new QrToolBinder());
        batteryToolBinder = new BatteryToolBinder();
        binders.put("battery", batteryToolBinder);
        binders.put("device", new DeviceToolBinder());
        binders.put("ping", new PingToolBinder());
        binders.put("traceroute", new TracerouteToolBinder());
        binders.put("subnet", new SubnetToolBinder());
        binders.put("screen", new ScreenToolBinder());
        binders.put("sensor", new SensorToolBinder());
        binders.put("hash", new HashToolBinder());
        binders.put("clipboard", new ClipboardToolBinder());
        binders.put("color", new ColorPickerToolBinder());
        binders.put("sysinfo", new SystemInfoToolBinder());
        // 2026-06-23: 新增 4 个工具
        binders.put("url_encode", new UrlEncodeToolBinder());
        binders.put("regex_test", new RegexTestToolBinder());
        binders.put("json_format", new JsonFormatToolBinder());
        binders.put("base64", new Base64ToolBinder());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tools, container, false);
    }

    /**
     * 视图创建完成后的初始化入口。
     * <p>
     * 依次初始化绑定器、布局模式、RecyclerView、拖拽排序和配色方案。
     * </p>
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        store = new ToolSectionStore(requireContext());
        initBinders();
        layoutMode = store.getLayoutMode();

        TextView tvTitle = view.findViewById(R.id.tv_tools_title);
        tvTitle.setText(getString(R.string.app_name));

        ImageView btnLayout = view.findViewById(R.id.btn_tools_layout);
        if (btnLayout != null) {
            btnLayout.setOnClickListener(v -> showLayoutModeMenu(v));
        }

        recyclerView = view.findViewById(R.id.rv_tools);
        sections = store.loadSections();
        allSections = new ArrayList<>(sections);  // 备份原始列表用于过滤
        adapter = new ToolsAdapter();
        applyLayoutManager();
        attachTouchHelper();
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(requireContext(), 16));

        applyColorScheme();

        // 2026-06-23: 搜索框 + Chip 筛选
        setupSearchAndFilter(view);
    }

    /**
     * 2026-06-23: 设置搜索框 + 分类 Chip 筛选逻辑。
     * - 搜索框实时过滤工具标题/描述
     * - Chip 单选切换：全部/收藏/最近/网络/设备/工具/最热
     */
    private void setupSearchAndFilter(View view) {
        // 搜索框
        TextInputEditText etSearch = view.findViewById(R.id.et_tool_search);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String keyword = s == null ? "" : s.toString().trim();
                    applyFilter(keyword, currentChipFilter);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Chip 筛选
        ChipGroup chipGroup = view.findViewById(R.id.tool_filter_chips);
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int checkedId = checkedIds.get(0);
                String chipKey = resolveChipKey(checkedId);
                currentChipFilter = chipKey;
                String keyword = etSearch != null ? etSearch.getText().toString().trim() : "";
                applyFilter(keyword, chipKey);
            });
        }
    }

    private String currentChipFilter = "all";

    private String resolveChipKey(int viewId) {
        if (viewId == R.id.chip_filter_all) return "all";
        if (viewId == R.id.chip_filter_favorites) return "favorites";
        if (viewId == R.id.chip_filter_recent) return "recent";
        if (viewId == R.id.chip_filter_network) return "network";
        if (viewId == R.id.chip_filter_device) return "device";
        if (viewId == R.id.chip_filter_tool) return "tool";
        if (viewId == R.id.chip_filter_hot) return "hot";
        return "all";
    }

    /**
     * 应用搜索 + 分类过滤：更新 sections 并刷新 adapter。
     */
    private void applyFilter(String keyword, String chipKey) {
        List<ToolSection> base;
        switch (chipKey) {
            case "favorites":
                Set<String> favIds = store.getFavoriteIds();
                base = new ArrayList<>();
                for (ToolSection s : allSections) {
                    if (favIds.contains(s.id)) base.add(s);
                }
                break;
            case "recent":
                List<String> recentIds = store.getRecentIds();
                base = new ArrayList<>();
                for (String rid : recentIds) {
                    ToolSection s = findById(allSections, rid);
                    if (s != null && s.visible) base.add(s);
                }
                break;
            case "network":
            case "device":
            case "tool":
                base = new ArrayList<>();
                for (ToolSection s : allSections) {
                    if (chipKey.equals(s.category) && s.visible) base.add(s);
                }
                break;
            case "hot":
                List<String> topIds = store.getTopUsedTools(allSections.size());
                base = new ArrayList<>();
                for (String tid : topIds) {
                    ToolSection s = findById(allSections, tid);
                    if (s != null && s.visible) base.add(s);
                }
                break;
            default:
                base = new ArrayList<>(allSections);
                break;
        }

        // 搜索过滤
        if (keyword != null && !keyword.isEmpty()) {
            List<ToolSection> filtered = new ArrayList<>();
            for (ToolSection s : base) {
                if (s.title.toLowerCase().contains(keyword.toLowerCase())
                        || s.id.toLowerCase().contains(keyword.toLowerCase())
                        || s.description.toLowerCase().contains(keyword.toLowerCase())) {
                    filtered.add(s);
                }
            }
            base = filtered;
        }

        sections.clear();
        sections.addAll(base);
        adapter.notifyDataSetChanged();
    }

    private ToolSection findById(List<ToolSection> list, String id) {
        for (ToolSection s : list) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    /**
     * 显示布局模式切换菜单（单列/双列）。
     *
     * @param anchor 菜单锚点视图
     */
    private void showLayoutModeMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.menu_tools_layout, popup.getMenu());
        // 根据当前布局模式设置选中状态
        if (layoutMode == 0) {
            popup.getMenu().findItem(R.id.action_single_column).setChecked(true);
        } else {
            popup.getMenu().findItem(R.id.action_double_column).setChecked(true);
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_single_column) {
                layoutMode = 0;
                store.saveLayoutMode(0);
                applyLayoutManager();
                attachTouchHelper();
                return true;
            } else if (id == R.id.action_double_column) {
                layoutMode = 1;
                store.saveLayoutMode(1);
                applyLayoutManager();
                attachTouchHelper();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * 根据当前布局模式设置 RecyclerView 的 LayoutManager。
     * <p>
     * 单列模式使用 LinearLayoutManager，双列模式使用 GridLayoutManager(2)。
     * </p>
     */
    private void applyLayoutManager() {
        if (layoutMode == 1) {
            recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    /**
     * 为 RecyclerView 附加拖拽排序的 ItemTouchHelper。
     * <p>
     * 单列模式仅支持上下拖拽，双列模式支持上下左右拖拽。
     * 拖拽开始时降低透明度并震动反馈，拖拽结束后恢复透明度并持久化排序。
     * </p>
     */
    private void attachTouchHelper() {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                // 双列模式额外支持左右拖拽方向
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | (layoutMode == 1 ? ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT : 0),
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                // 交换列表中的位置并通知适配器
                Collections.swap(sections, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                // 拖拽开始时提供视觉和触觉反馈
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof SectionViewHolder) {
                    ((SectionViewHolder) viewHolder).drag();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 拖拽结束后恢复视觉状态并持久化排序
                if (viewHolder instanceof SectionViewHolder) {
                    ((SectionViewHolder) viewHolder).drop();
                }
                store.saveOrder(sections);
            }
        });
        touchHelper.attachToRecyclerView(this.recyclerView);
    }

    /**
     * 根据当前设置应用配色方案到根视图。
     */
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

    /**
     * 视图销毁时注销 BatteryToolBinder 的广播接收器，防止内存泄漏。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (batteryToolBinder != null) {
            batteryToolBinder.unbind();
            batteryToolBinder = null;
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        adapter = null;
        // sections 可能来自不可修改集合，只置空引用不调用 clear()
        sections = null;
        if (binders != null) {
            for (ToolBinder binder : binders.values()) {
                if (binder instanceof BatteryToolBinder) {
                    ((BatteryToolBinder) binder).unbind();
                }
            }
            binders.clear();
        }
    }

    /**
     * 工具分区列表适配器。
     * <p>
     * 每个列表项对应一个工具分区卡片，包含标题、内容和收藏按钮。
     * </p>
     */
    private class ToolsAdapter extends RecyclerView.Adapter<SectionViewHolder> {
        @NonNull
        @Override
        public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            return new SectionViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
            ToolSection section = sections.get(position);
            holder.bind(section);
        }

        @Override
        public int getItemViewType(int position) {
            return R.layout.item_tool_section;
        }

        @Override
        public int getItemCount() {
            return sections.size();
        }
    }

    /**
     * 工具分区卡片 ViewHolder。
     * <p>
     * 负责渲染工具分区标题、动态加载内容布局、绑定 ToolBinder，
     * 以及处理收藏按钮交互。拖拽时提供视觉反馈（透明度变化和震动）。
     * </p>
     */
    class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvToolTitle;
        private final FrameLayout contentRoot;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvToolTitle = itemView.findViewById(R.id.tv_section_title);
            contentRoot = itemView.findViewById(R.id.tool_content_container);
        }

        /**
         * 绑定工具分区数据到视图。
         * <p>
         * 每次绑定时重新加载内容布局并调用对应的 ToolBinder.bind()，
         * 确保内容与分区数据一致。
         * </p>
         *
         * @param section 工具分区数据
         */
        void bind(ToolSection section) {
            if (tvToolTitle != null) {
                tvToolTitle.setText(section.title);
            }
            if (contentRoot != null) {
                // 清除旧的内容视图，避免重复添加
                View existing = contentRoot.getChildAt(0);
                if (existing != null) {
                    contentRoot.removeAllViews();
                }
                try {
                    // 动态加载工具的内容布局并绑定功能
                    View contentView = LayoutInflater.from(itemView.getContext()).inflate(section.contentLayoutId, contentRoot, false);
                    contentRoot.addView(contentView);
                    bindContent(section, contentView);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to bind tool section: " + section.id, e);
                    // 布局加载失败时显示错误提示
                    contentRoot.addView(createToolErrorView(itemView.getContext(), section));
                }
            }
            View btnFavorite = itemView.findViewById(R.id.btn_tool_favorite);
            if (btnFavorite != null) {
                btnFavorite.setOnClickListener(v -> {
                    boolean favorite = store.toggleFavorite(section.id);
                    ((android.widget.ImageView) btnFavorite).setImageResource(favorite
                            ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off);
                    Context ctx = v.getContext();
                    if (ctx != null) Toast.makeText(ctx, favorite ? "已收藏" : "取消收藏", Toast.LENGTH_SHORT).show();
                });
            }
        }

        /**
         * 查找并调用对应工具的 ToolBinder 进行功能绑定。
         *
         * @param section     工具分区数据
         * @param contentView 已加载的内容视图
         */
        void bindContent(ToolSection section, View contentView) {
            ToolBinder binder = binders.get(section.id);
            if (binder != null) {
                Context ctx = getContext();
                if (ctx != null) {
                    binder.bind(ctx, contentView, executor);
                    // 2026-06-23: 记录使用次数（用于按热度排序）
                    if (store != null) store.incrementUsage(section.id);
                }
            }
        }

        /**
         * 创建工具加载失败时的错误提示视图。
         *
         * @param context 上下文
         * @param section 加载失败的分区
         * @return 显示错误信息的 TextView
         */
        private View createToolErrorView(Context context, ToolSection section) {
            TextView errorView = new TextView(context);
            int padding = (int) (12 * context.getResources().getDisplayMetrics().density);
            errorView.setPadding(padding, padding, padding, padding);
            errorView.setText("该工具暂时无法加载：" + section.title);
            errorView.setTextColor(0xFFB00020);
            errorView.setTextSize(14);
            return errorView;
        }

        /**
         * 拖拽开始时的视觉反馈：降低透明度并触发短震动。
         */
        void drag() {
            itemView.setAlpha(0.5f);
            if (getActivity() != null) {
                Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(20);
                }
            }
        }

        /**
         * 拖拽结束时的视觉恢复：还原透明度。
         */
        void drop() {
            itemView.setAlpha(1.0f);
        }
    }

    /**
     * 简单的 RecyclerView 间距装饰，为每个列表项添加统一的四周间距。
     */
    private static class SimpleDividerItemDecoration extends RecyclerView.ItemDecoration {
        private final int space;

        SimpleDividerItemDecoration(Context context, int space) {
            this.space = space;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.top = space;
            outRect.bottom = space;
            outRect.left = space;
            outRect.right = space;
        }
    }
}

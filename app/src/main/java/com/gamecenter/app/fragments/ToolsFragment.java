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
import com.gamecenter.app.tools.ToolBinder;
import com.gamecenter.app.tools.ToolHelper;
import com.gamecenter.app.tools.ToolSection;
import com.gamecenter.app.tools.ToolSectionStore;
import com.gamecenter.app.tools.TracerouteToolBinder;
import com.gamecenter.app.tools.WifiToolBinder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToolsFragment extends Fragment {

    private static final String TAG = "ToolsFragment";

    private RecyclerView recyclerView;
    private ToolsAdapter adapter;
    private ToolSectionStore store;
    private List<ToolSection> sections;
    private int layoutMode;

    private BatteryToolBinder batteryToolBinder;
    private final Map<String, ToolBinder> binders = new HashMap<>();

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
        layoutMode = store.getLayoutMode();

        TextView tvTitle = view.findViewById(R.id.tv_tools_title);
        tvTitle.setText(getString(R.string.app_name));

        ImageView btnLayout = view.findViewById(R.id.btn_tools_layout);
        if (btnLayout != null) {
            btnLayout.setOnClickListener(v -> showLayoutModeMenu(v));
        }

        recyclerView = view.findViewById(R.id.rv_tools);
        sections = store.loadSections();
        adapter = new ToolsAdapter();
        applyLayoutManager();
        attachTouchHelper();
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(requireContext(), 16));

        applyColorScheme();
    }

    private void showLayoutModeMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.menu_tools_layout, popup.getMenu());
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

    private void applyLayoutManager() {
        if (layoutMode == 1) {
            recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    private void attachTouchHelper() {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | (layoutMode == 1 ? ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT : 0),
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
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
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof SectionViewHolder) {
                    ((SectionViewHolder) viewHolder).drag();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (viewHolder instanceof SectionViewHolder) {
                    ((SectionViewHolder) viewHolder).drop();
                }
                store.saveOrder(sections);
            }
        });
        touchHelper.attachToRecyclerView(this.recyclerView);
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
        if (batteryToolBinder != null) {
            batteryToolBinder.unbind();
        }
    }

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

    class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvToolTitle;
        private final FrameLayout contentRoot;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvToolTitle = itemView.findViewById(R.id.tv_section_title);
            contentRoot = itemView.findViewById(R.id.tool_content_container);
        }

        void bind(ToolSection section) {
            if (tvToolTitle != null) {
                tvToolTitle.setText(section.title);
            }
            if (contentRoot != null) {
                View existing = contentRoot.getChildAt(0);
                if (existing != null) {
                    contentRoot.removeAllViews();
                }
                try {
                    View contentView = LayoutInflater.from(itemView.getContext()).inflate(section.contentLayoutId, contentRoot, false);
                    contentRoot.addView(contentView);
                    bindContent(section, contentView);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to bind tool section: " + section.id, e);
                    contentRoot.addView(createToolErrorView(itemView.getContext(), section));
                }
            }
            View btnFavorite = itemView.findViewById(R.id.btn_tool_favorite);
            if (btnFavorite != null) {
                btnFavorite.setOnClickListener(v -> {
                    boolean favorite = store.toggleFavorite(section.id);
                    android.graphics.drawable.Drawable star = favorite
                            ? android.graphics.drawable.Drawable.createFromPath("@android:drawable/btn_star_big_on")
                            : android.graphics.drawable.Drawable.createFromPath("@android:drawable/btn_star_big_off");
                    ((android.widget.ImageView) btnFavorite).setImageResource(favorite
                            ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off);
                    Toast.makeText(requireContext(), favorite ? "已收藏" : "取消收藏", Toast.LENGTH_SHORT).show();
                });
            }
        }

        void bindContent(ToolSection section, View contentView) {
            ToolBinder binder = binders.get(section.id);
            if (binder != null) {
                binder.bind(requireContext(), contentView, executor);
            }
        }

        private View createToolErrorView(Context context, ToolSection section) {
            TextView errorView = new TextView(context);
            int padding = (int) (12 * context.getResources().getDisplayMetrics().density);
            errorView.setPadding(padding, padding, padding, padding);
            errorView.setText("该工具暂时无法加载：" + section.title);
            errorView.setTextColor(0xFFB00020);
            errorView.setTextSize(14);
            return errorView;
        }

        void drag() {
            itemView.setAlpha(0.5f);
            if (getActivity() != null) {
                Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(20);
                }
            }
        }

        void drop() {
            itemView.setAlpha(1.0f);
        }
    }

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

package com.gamecenter.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.games.ui.GameLauncherHelper;
import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.settings.AppSettingsDialog;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 游戏大厅 Fragment。
 * 加载 fragment_games.xml 布局，包含设置按钮、搜索栏、分类 Tab、游戏网格。
 */
public class GamesFragment extends Fragment {

    private static final String TAG = "GamesFragment";

    private View rootView;
    private RecyclerView rvGames;
    private GameCardAdapter adapter;
    private List<GameRegistry.Entry> allEntries = new ArrayList<>();
    private String currentCategoryKey = "all";
    private String currentKeyword = "";

    public GamesFragment() {
        super(R.layout.fragment_games);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.rootView = view;
        initViews(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次可见时同步已安装模块，并刷新游戏列表
        if (requireContext() != null) {
            ModuleManager.INSTANCE.registerInstalledGameModules(requireContext());
        }
        loadGames();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
    }

    private void initViews(View v) {
        // 版本号
        TextView tvVersion = v.findViewById(R.id.tv_version);
        if (tvVersion != null) {
            try {
                String vn = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                int vc = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionCode;
                tvVersion.setText("v" + vn + " (vc=" + vc + ")");
            } catch (Exception e) {
                Log.e(TAG, "版本号读取失败", e);
            }
        }

        // 设置按钮
        View btnSettings = v.findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(btn -> {
                try {
                    // 使用原版 Dialog 代替 SettingsActivity，避免内存泄漏且体验更佳
                    new AppSettingsDialog(GamesFragment.this, null, null).show();
                } catch (Exception e) {
                    Log.e(TAG, "打开设置失败", e);
                    Toast.makeText(requireContext(), "设置页面打开失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 模块市场按钮
        View btnStore = v.findViewById(R.id.btn_module_store);
        if (btnStore != null) {
            btnStore.setOnClickListener(btn -> {
                try {
                    Intent intent = new Intent(requireContext(),
                            com.gamecenter.app.modules.ModuleStoreActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "启动模块商店失败", e);
                    Toast.makeText(requireContext(), "模块商店未找到", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 搜索框
        android.widget.EditText etSearch = v.findViewById(R.id.et_game_search);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    currentKeyword = s.toString();
                    filterAndRefresh();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // TabLayout
        TabLayout tabLayout = v.findViewById(R.id.tab_layout);
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    int pos = tab.getPosition();
                    if (pos == 0) currentCategoryKey = "all";
                    else if (pos == 1) currentCategoryKey = GameRegistry.CATEGORY_CLASSICS;
                    else if (pos == 2) currentCategoryKey = GameRegistry.CATEGORY_PUZZLE;
                    else if (pos == 3) currentCategoryKey = GameRegistry.CATEGORY_CASUAL;
                    filterAndRefresh();
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        // RecyclerView
        rvGames = v.findViewById(R.id.rv_games);
        if (rvGames != null) {
            rvGames.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            adapter = new GameCardAdapter(requireContext(), this::onGameClick);
            rvGames.setAdapter(adapter);
        }

        updateEmptyState(false);
    }

    private void loadGames() {
        allEntries.clear();
        List<GameRegistry.Category> categories = GameRegistry.getCategories(requireContext());
        for (GameRegistry.Category cat : categories) {
            allEntries.addAll(cat.games);
        }
        initTabsIfNeeded();
        filterAndRefresh();
    }

    private void initTabsIfNeeded() {
        TabLayout tabLayout = rootView.findViewById(R.id.tab_layout);
        if (tabLayout == null) return;
        if (tabLayout.getTabCount() > 0) return;

        tabLayout.addTab(tabLayout.newTab().setText("全部"));
        tabLayout.addTab(tabLayout.newTab().setText("经典"));
        tabLayout.addTab(tabLayout.newTab().setText("益智"));
        tabLayout.addTab(tabLayout.newTab().setText("休闲"));
    }

    private void filterAndRefresh() {
        if (adapter == null) return;
        List<GameRegistry.Entry> filtered = new ArrayList<>();
        String kw = currentKeyword == null ? "" : currentKeyword.toLowerCase(Locale.getDefault()).trim();
        for (GameRegistry.Entry e : allEntries) {
            boolean catMatch = currentCategoryKey.equals("all")
                    || e.categoryKey.equals(currentCategoryKey);
            boolean kwMatch = kw.isEmpty()
                    || e.name.toLowerCase(Locale.getDefault()).contains(kw)
                    || e.desc.toLowerCase(Locale.getDefault()).contains(kw);
            if (catMatch && kwMatch) filtered.add(e);
        }
        adapter.setEntries(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        TextView tvEmpty = rootView.findViewById(R.id.tv_empty_state);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (isEmpty) tvEmpty.setText("暂无游戏，请前往模块市场下载");
        }
    }

    private void onGameClick(GameRegistry.Entry entry) {
        Log.i(TAG, "点击游戏: " + entry.id + " / " + entry.name);
        boolean ok = GameLauncherHelper.launchGameWithDialog(requireContext(), entry.id);
        if (!ok) {
            Toast.makeText(requireContext(), "游戏启动失败: " + entry.name, Toast.LENGTH_SHORT).show();
        }
    }

    // ======== Adapter ========

    private static class GameCardAdapter extends RecyclerView.Adapter<GameCardAdapter.VH> {

        interface OnItemClick { void onClick(GameRegistry.Entry entry); }
        private final List<GameRegistry.Entry> data = new ArrayList<>();
        private final OnItemClick listener;
        private final Context context;

        GameCardAdapter(Context ctx, OnItemClick listener) {
            this.context = ctx;
            this.listener = listener;
        }

        void setEntries(List<GameRegistry.Entry> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_game_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            GameRegistry.Entry e = data.get(pos);
            android.widget.ImageView iv = h.itemView.findViewById(R.id.iv_game_icon);
            if (iv != null) iv.setImageResource(e.iconRes);

            TextView tvName = h.itemView.findViewById(R.id.tv_game_name);
            if (tvName != null) tvName.setText(e.name);

            TextView tvDesc = h.itemView.findViewById(R.id.tv_game_desc);
            if (tvDesc != null) tvDesc.setText(e.desc);

            TextView tvCat = h.itemView.findViewById(R.id.tv_category_tag);
            if (tvCat != null) {
                String label = e.categoryKey.equals(GameRegistry.CATEGORY_CLASSICS) ? "经典"
                        : e.categoryKey.equals(GameRegistry.CATEGORY_PUZZLE) ? "益智"
                        : e.categoryKey.equals(GameRegistry.CATEGORY_CASUAL) ? "休闲" : e.categoryKey;
                tvCat.setText(label);
            }
            h.itemView.setOnClickListener(v -> listener.onClick(e));
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }
}

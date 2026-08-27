package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 底部工具栏可定制（P3-3）。
 *
 * <p>允许用户控制底栏按钮的显示/隐藏。当前仅持久化可见性状态，
 * 由 BrowserFragment 启动时读取并应用。</p>
 *
 * <p>持久化：SharedPreferences "browser_bottom_bar_prefs"，key=buttonId，value=visible(boolean)。</p>
 */
public class BottomBarCustomizeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "browser_bottom_bar_prefs";

    public static final String BTN_HOME = "btn_home";
    public static final String BTN_BACK = "btn_back";
    public static final String BTN_FORWARD = "btn_forward";
    public static final String BTN_REFRESH = "btn_refresh";
    public static final String BTN_BOOKMARK = "btn_bookmark";
    public static final String BTN_TABS = "btn_tabs";
    public static final String BTN_MORE = "btn_more";
    public static final String BTN_DOWNLOAD = "btn_download";

    private BottomBarAdapter adapter;
    private OnBackInvokedCallback backInvokedCallback;

    public static void start(Context context) {
        context.startActivity(new Intent(context, BottomBarCustomizeActivity.class));
    }

    /** 读取某按钮的可见性（默认 true） */
    public static boolean isVisible(Context context, String buttonId) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(buttonId, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bottom_bar_customize);
        initViews();
        registerPredictiveBack();
    }

    private void registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = () -> finish();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backInvokedCallback);
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
        }
        super.onDestroy();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        RecyclerView rv = findViewById(R.id.rv_buttons);
        btnBack.setOnClickListener(v -> finish());
        adapter = new BottomBarAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        adapter.submit(buildItems());
    }

    private List<BarItem> buildItems() {
        return new ArrayList<>(Arrays.asList(
                new BarItem(BTN_BACK, getString(R.string.browser_btn_back), true),
                new BarItem(BTN_FORWARD, getString(R.string.browser_btn_forward), true),
                new BarItem(BTN_REFRESH, getString(R.string.browser_btn_refresh), true),
                new BarItem(BTN_HOME, getString(R.string.browser_btn_home), true),
                new BarItem(BTN_BOOKMARK, getString(R.string.browser_btn_bookmark_add), true),
                new BarItem(BTN_TABS, getString(R.string.browser_btn_tabs), true),
                new BarItem(BTN_DOWNLOAD, getString(R.string.browser_menu_downloads), true),
                new BarItem(BTN_MORE, getString(R.string.browser_menu_more), true)
        ));
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void setVisible(String buttonId, boolean visible) {
        prefs().edit().putBoolean(buttonId, visible).apply();
    }

    private static class BarItem {
        final String id;
        final String label;
        final boolean defaultVisible;
        BarItem(String id, String label, boolean defaultVisible) {
            this.id = id;
            this.label = label;
            this.defaultVisible = defaultVisible;
        }
    }

    private class BottomBarAdapter extends RecyclerView.Adapter<BottomBarAdapter.VH> {
        private List<BarItem> data = new ArrayList<>();

        @androidx.annotation.NonNull
        @Override
        public VH onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bottom_bar_customize, parent, false);
            return new VH(v);
        }

        void submit(List<BarItem> items) {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                    new BarItemDiffCallback(data, items));
            data = items;
            diffResult.dispatchUpdatesTo(this);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull VH holder, int position) {
            BarItem item = data.get(position);
            holder.tvLabel.setText(item.label);
            boolean visible = prefs().getBoolean(item.id, item.defaultVisible);
            holder.swVisible.setOnCheckedChangeListener(null);
            holder.swVisible.setChecked(visible);
            holder.swVisible.setOnCheckedChangeListener((b, checked) -> setVisible(item.id, checked));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvLabel;
            final MaterialSwitch swVisible;
            VH(@androidx.annotation.NonNull View v) {
                super(v);
                tvLabel = v.findViewById(R.id.tv_label);
                swVisible = v.findViewById(R.id.sw_visible);
            }
        }
    }

    private static class BarItemDiffCallback extends DiffUtil.Callback {
        private final List<BarItem> oldList;
        private final List<BarItem> newList;

        BarItemDiffCallback(List<BarItem> oldList, List<BarItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).id.equals(newList.get(newItemPosition).id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            BarItem oldItem = oldList.get(oldItemPosition);
            BarItem newItem = newList.get(newItemPosition);
            return oldItem.id.equals(newItem.id)
                    && oldItem.label.equals(newItem.label);
        }
    }
}

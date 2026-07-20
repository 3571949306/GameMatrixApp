package com.gamecenter.app.browser.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserOfflineCache;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 离线缓存管理页（P1-4）。
 *
 * <p>展示最近缓存的 10 个页面；点击重新加载（在线模式）；长按删除单条；顶栏"清空"清空全部。</p>
 */
public class OfflineCacheActivity extends AppCompatActivity {

    private RecyclerView rvList;
    private View emptyView;
    private TextView tvCount;
    private OfflineCacheAdapter adapter;
    private OnBackInvokedCallback backInvokedCallback;

    public static void start(Context context) {
        context.startActivity(new Intent(context, OfflineCacheActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_cache);
        initViews();
        setupRecyclerView();
        registerPredictiveBack();
        loadList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadList();
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
        rvList = findViewById(R.id.rv_offline_list);
        emptyView = findViewById(R.id.empty_view);
        tvCount = findViewById(R.id.tv_offline_count);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnClearAll = findViewById(R.id.btn_clear_all);
        btnBack.setOnClickListener(v -> finish());
        btnClearAll.setOnClickListener(v -> confirmClearAll());
    }

    private void setupRecyclerView() {
        adapter = new OfflineCacheAdapter();
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);
    }

    private void loadList() {
        List<BrowserOfflineCache.CacheEntry> list =
                BrowserOfflineCache.getInstance(this).getAll();
        adapter.submit(list);
        if (list.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rvList.setVisibility(View.GONE);
            tvCount.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvList.setVisibility(View.VISIBLE);
            tvCount.setVisibility(View.VISIBLE);
            tvCount.setText(getString(R.string.browser_offline_cache_count, list.size()));
        }
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_offline_cache_clear_title)
                .setMessage(R.string.browser_offline_cache_clear_message)
                .setPositiveButton(R.string.browser_offline_cache_clear_all,
                        (d, w) -> doClearAll())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doClearAll() {
        BrowserOfflineCache.getInstance(this).clear();
        loadList();
        Toast.makeText(this, R.string.browser_offline_cache_cleared, Toast.LENGTH_SHORT).show();
    }

    private static class OfflineCacheAdapter extends RecyclerView.Adapter<OfflineCacheAdapter.VH> {
        private List<BrowserOfflineCache.CacheEntry> data = java.util.Collections.emptyList();

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<BrowserOfflineCache.CacheEntry> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_offline_cache, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BrowserOfflineCache.CacheEntry e = data.get(position);
            holder.tvTitle.setText(e.title != null && !e.title.isEmpty() ? e.title : e.url);
            holder.tvUrl.setText(e.url);
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            holder.tvTime.setText(sdf.format(new Date(e.savedAt)));
            holder.itemView.setOnLongClickListener(v -> {
                OfflineCacheActivity act = (OfflineCacheActivity) holder.itemView.getContext();
                act.confirmDelete(e.url);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvUrl;
            final TextView tvTime;
            VH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_title);
                tvUrl = v.findViewById(R.id.tv_url);
                tvTime = v.findViewById(R.id.tv_time);
            }
        }
    }

    private void confirmDelete(String url) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_offline_cache_delete_title)
                .setMessage(R.string.browser_offline_cache_delete_message)
                .setPositiveButton(R.string.browser_offline_cache_delete,
                        (d, w) -> {
                            BrowserOfflineCache.getInstance(this).remove(url);
                            loadList();
                            Toast.makeText(this, R.string.browser_offline_cache_deleted,
                                    Toast.LENGTH_SHORT).show();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

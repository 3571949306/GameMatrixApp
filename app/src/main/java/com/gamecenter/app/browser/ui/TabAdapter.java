package com.gamecenter.app.browser.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserTabManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签列表适配器。
 */
public class TabAdapter extends RecyclerView.Adapter<TabAdapter.ViewHolder> {

    private final List<BrowserTabManager.Tab> data = new ArrayList<>();
    private OnItemClickListener itemClickListener;
    private OnCloseClickListener closeClickListener;

    public interface OnItemClickListener {
        void onItemClick(BrowserTabManager.Tab tab);
    }

    public interface OnCloseClickListener {
        void onCloseClick(BrowserTabManager.Tab tab);
    }

    public void setData(@NonNull List<BrowserTabManager.Tab> list) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new TabDiffCallback(data, list));
        data.clear();
        data.addAll(list);
        result.dispatchUpdatesTo(this);
    }

    public void setOnItemClickListener(OnItemClickListener l) { this.itemClickListener = l; }
    public void setOnCloseClickListener(OnCloseClickListener l) { this.closeClickListener = l; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        BrowserTabManager.Tab tab = data.get(position);
        String title = tab.getTitle();
        if (title == null || title.isEmpty()) {
            title = tab.getUrl() != null && !tab.getUrl().isEmpty() ? tab.getUrl()
                    : h.itemView.getContext().getString(R.string.browser_tab_empty);
        }
        if (tab.isIncognito()) {
            h.tvTitle.setText(h.itemView.getContext().getString(R.string.browser_tab_incognito_prefix, title));
        } else {
            h.tvTitle.setText(title);
        }
        h.tvUrl.setText(tab.getUrl() != null ? tab.getUrl() : "");
        h.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(tab);
        });
        h.btnClose.setOnClickListener(v -> {
            if (closeClickListener != null) closeClickListener.onCloseClick(tab);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvUrl;
        final ImageButton btnClose;

        ViewHolder(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_tab_title);
            tvUrl = v.findViewById(R.id.tv_tab_url);
            btnClose = v.findViewById(R.id.btn_tab_close);
        }
    }

    private static class TabDiffCallback extends DiffUtil.Callback {

        private final List<BrowserTabManager.Tab> oldList;
        private final List<BrowserTabManager.Tab> newList;

        TabDiffCallback(@NonNull List<BrowserTabManager.Tab> oldList,
                        @NonNull List<BrowserTabManager.Tab> newList) {
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
            String oldId = oldList.get(oldItemPosition).getId();
            String newId = newList.get(newItemPosition).getId();
            if (oldId != null && newId != null) {
                return oldId.equals(newId);
            }
            String oldFallback = buildFallbackId(oldList.get(oldItemPosition));
            String newFallback = buildFallbackId(newList.get(newItemPosition));
            return oldFallback.equals(newFallback);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            BrowserTabManager.Tab oldTab = oldList.get(oldItemPosition);
            BrowserTabManager.Tab newTab = newList.get(newItemPosition);
            if (oldTab.isIncognito() != newTab.isIncognito()) return false;
            if (!safeEquals(oldTab.getTitle(), newTab.getTitle())) return false;
            return safeEquals(oldTab.getUrl(), newTab.getUrl());
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            return super.getChangePayload(oldItemPosition, newItemPosition);
        }

        private static String buildFallbackId(BrowserTabManager.Tab tab) {
            String url = tab.getUrl() != null ? tab.getUrl() : "";
            String title = tab.getTitle() != null ? tab.getTitle() : "";
            return url + "_" + title;
        }

        private static boolean safeEquals(@Nullable String a, @Nullable String b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.equals(b);
        }
    }
}

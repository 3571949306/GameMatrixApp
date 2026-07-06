package com.gamecenter.app.browser.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
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
}

package com.gamecenter.app.browser.ui;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 下载记录列表适配器。
 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(BrowserDownloadEntity item);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(BrowserDownloadEntity item);
    }

    private final List<BrowserDownloadEntity> data = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnDeleteClickListener deleteListener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public void setData(List<BrowserDownloadEntity> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) { this.clickListener = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { this.deleteListener = l; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        BrowserDownloadEntity item = data.get(position);
        h.tvName.setText(item.getFileName());
        h.tvUrl.setText(item.getUrl());
        h.tvTime.setText(fmt.format(new Date(item.getCreateTime())));
        h.tvStatus.setText(statusToString(item.getStatus()));
        h.tvSize.setText(formatSize(h.itemView.getContext(), item.getDownloadedSize())
                + " / " + formatSize(h.itemView.getContext(), item.getTotalSize()));
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(item);
        });
        h.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(item);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    private String statusToString(int s) {
        switch (s) {
            case BrowserDownloadEntity.STATUS_WAITING: return "等待中";
            case BrowserDownloadEntity.STATUS_DOWNLOADING: return "下载中";
            case BrowserDownloadEntity.STATUS_COMPLETED: return "已完成";
            case BrowserDownloadEntity.STATUS_FAILED: return "失败";
            default: return "";
        }
    }

    private String formatSize(android.content.Context ctx, long bytes) {
        if (bytes <= 0) return "0 B";
        return Formatter.formatFileSize(ctx, bytes);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName, tvUrl, tvTime, tvStatus, tvSize;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_download_icon);
            tvName = v.findViewById(R.id.tv_download_filename);
            tvUrl = v.findViewById(R.id.tv_download_url);
            tvTime = v.findViewById(R.id.tv_download_time);
            tvStatus = v.findViewById(R.id.tv_download_status);
            tvSize = v.findViewById(R.id.tv_download_size);
            btnDelete = v.findViewById(R.id.btn_download_delete);
        }
    }
}

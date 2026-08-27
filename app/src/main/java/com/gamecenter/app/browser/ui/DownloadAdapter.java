package com.gamecenter.app.browser.ui;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        List<BrowserDownloadEntity> oldList = new ArrayList<>(data);
        List<BrowserDownloadEntity> newList = list != null ? new ArrayList<>(list) : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DownloadDiffCallback(oldList, newList));

        data.clear();
        data.addAll(newList);

        diffResult.dispatchUpdatesTo(this);
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

    private static class DownloadDiffCallback extends DiffUtil.Callback {

        private final List<BrowserDownloadEntity> oldList;
        private final List<BrowserDownloadEntity> newList;

        DownloadDiffCallback(List<BrowserDownloadEntity> oldList, List<BrowserDownloadEntity> newList) {
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
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            BrowserDownloadEntity oldItem = oldList.get(oldItemPosition);
            BrowserDownloadEntity newItem = newList.get(newItemPosition);
            return oldItem.getFileName().equals(newItem.getFileName())
                    && oldItem.getUrl().equals(newItem.getUrl())
                    && oldItem.getStatus() == newItem.getStatus()
                    && oldItem.getDownloadedSize() == newItem.getDownloadedSize()
                    && oldItem.getTotalSize() == newItem.getTotalSize()
                    && oldItem.getCreateTime() == newItem.getCreateTime();
        }
    }
}

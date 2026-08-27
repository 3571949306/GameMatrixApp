package com.gamecenter.app.browser.ui;

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
import com.gamecenter.app.browser.data.entity.BrowserReadingListEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 阅读列表适配器（P1-3）。
 */
public class ReadingListAdapter extends RecyclerView.Adapter<ReadingListAdapter.ViewHolder> {

    private List<BrowserReadingListEntity> items = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private OnDeleteClickListener deleteListener;

    public interface OnItemClickListener { void onItemClick(BrowserReadingListEntity item); }
    public interface OnItemLongClickListener { void onItemLongClick(BrowserReadingListEntity item); }
    public interface OnDeleteClickListener { void onItemClick(BrowserReadingListEntity item); }

    public void setOnItemClickListener(OnItemClickListener l) { this.clickListener = l; }
    public void setOnItemLongClickListener(OnItemLongClickListener l) { this.longClickListener = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { this.deleteListener = l; }

    public void setData(List<BrowserReadingListEntity> items) {
        List<BrowserReadingListEntity> newList = items != null ? items : new ArrayList<>();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ReadingListDiffCallback(this.items, newList));
        this.items = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class ReadingListDiffCallback extends DiffUtil.Callback {
        private final List<BrowserReadingListEntity> oldList;
        private final List<BrowserReadingListEntity> newList;

        ReadingListDiffCallback(List<BrowserReadingListEntity> oldList, List<BrowserReadingListEntity> newList) {
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
            BrowserReadingListEntity oldItem = oldList.get(oldItemPosition);
            BrowserReadingListEntity newItem = newList.get(newItemPosition);
            return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                    && Objects.equals(oldItem.getUrl(), newItem.getUrl())
                    && Objects.equals(oldItem.getSummary(), newItem.getSummary())
                    && oldItem.getSavedAt() == newItem.getSavedAt()
                    && oldItem.getRead() == newItem.getRead();
        }
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reading_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BrowserReadingListEntity item = items.get(position);
        String title = item.getTitle() != null && !item.getTitle().isEmpty() ? item.getTitle() : item.getUrl();
        holder.tvTitle.setText(title);
        String summary = item.getSummary();
        if (summary == null || summary.isEmpty()) {
            holder.tvSummary.setVisibility(View.GONE);
        } else {
            holder.tvSummary.setVisibility(View.VISIBLE);
            holder.tvSummary.setText(summary);
        }
        holder.tvUrl.setText(item.getUrl());
        holder.tvTime.setText(formatTime(item.getSavedAt()));
        boolean unread = item.getRead() == 0;
        holder.viewUnreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onItemClick(item); });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onItemLongClick(item);
            return true;
        });
        holder.btnDelete.setOnClickListener(v -> { if (deleteListener != null) deleteListener.onItemClick(item); });
    }

    @Override public int getItemCount() { return items.size(); }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "";
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFmt.format(new Date(timestamp));
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return timeStr;
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "\u6628\u5929 " + timeStr;
        }
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle, tvSummary, tvUrl, tvTime;
        final View viewUnreadDot;
        final ImageButton btnDelete;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_reading_icon);
            tvTitle = itemView.findViewById(R.id.tv_reading_title);
            tvSummary = itemView.findViewById(R.id.tv_reading_summary);
            tvUrl = itemView.findViewById(R.id.tv_reading_url);
            tvTime = itemView.findViewById(R.id.tv_reading_time);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);
            btnDelete = itemView.findViewById(R.id.btn_reading_delete);
        }
    }
}

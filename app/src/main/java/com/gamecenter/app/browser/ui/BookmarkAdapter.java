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
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 收藏夹列表适配器（Room 数据源）。
 */
public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {

    private List<BrowserBookmarkEntity> bookmarkList = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private OnDeleteClickListener deleteListener;

    public interface OnItemClickListener { void onItemClick(BrowserBookmarkEntity item); }
    public interface OnItemLongClickListener { void onItemLongClick(BrowserBookmarkEntity item); }
    public interface OnDeleteClickListener { void onItemClick(BrowserBookmarkEntity item); }

    public void setOnItemClickListener(OnItemClickListener l) { this.clickListener = l; }
    public void setOnItemLongClickListener(OnItemLongClickListener l) { this.longClickListener = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { this.deleteListener = l; }

    public void setData(List<BrowserBookmarkEntity> items) {
        List<BrowserBookmarkEntity> oldList = new ArrayList<>(this.bookmarkList);
        List<BrowserBookmarkEntity> newList = items != null ? items : new ArrayList<>();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new BookmarkDiffCallback(oldList, newList));
        this.bookmarkList = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class BookmarkDiffCallback extends DiffUtil.Callback {
        private final List<BrowserBookmarkEntity> oldList;
        private final List<BrowserBookmarkEntity> newList;

        BookmarkDiffCallback(List<BrowserBookmarkEntity> oldList, List<BrowserBookmarkEntity> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            BrowserBookmarkEntity oldItem = oldList.get(oldItemPosition);
            BrowserBookmarkEntity newItem = newList.get(newItemPosition);
            if (oldItem.getId() != newItem.getId()) return false;
            if (!java.util.Objects.equals(oldItem.getTitle(), newItem.getTitle())) return false;
            if (!java.util.Objects.equals(oldItem.getUrl(), newItem.getUrl())) return false;
            return oldItem.getCreateTime() == newItem.getCreateTime();
        }
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BrowserBookmarkEntity item = bookmarkList.get(position);
        holder.tvTitle.setText(item.getTitle() != null && !item.getTitle().isEmpty() ? item.getTitle() : item.getUrl());
        holder.tvUrl.setText(item.getUrl());
        holder.tvTime.setText(formatTime(item.getCreateTime()));
        holder.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onItemClick(item); });
        holder.itemView.setOnLongClickListener(v -> { if (longClickListener != null) longClickListener.onItemLongClick(item); return true; });
        holder.btnDelete.setOnClickListener(v -> { if (deleteListener != null) deleteListener.onItemClick(item); });
    }

    @Override public int getItemCount() { return bookmarkList.size(); }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "";
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFmt.format(new Date(timestamp));
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) return timeStr;
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) return "\u6628\u5929 " + timeStr;
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon; final TextView tvTitle, tvUrl, tvTime; final ImageButton btnDelete;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_bookmark_icon);
            tvTitle = itemView.findViewById(R.id.tv_bookmark_title);
            tvUrl = itemView.findViewById(R.id.tv_bookmark_url);
            tvTime = itemView.findViewById(R.id.tv_bookmark_time);
            btnDelete = itemView.findViewById(R.id.btn_bookmark_delete);
        }
    }
}

package com.gamecenter.app.browser.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 浏览历史记录列表适配器（Room 数据源）。
 *
 * 功能：按日期分组显示（今天、昨天、更早），显示标题、URL、访问时间、访问次数，
 * 支持点击打开 URL，支持长按和按钮删除。
 */
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> displayList = new ArrayList<>();
    private List<BrowserHistoryEntity> historyItems = new ArrayList<>();

    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private OnDeleteClickListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(BrowserHistoryEntity item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(BrowserHistoryEntity item);
    }

    public interface OnDeleteClickListener {
        void onItemClick(BrowserHistoryEntity item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setData(List<BrowserHistoryEntity> items) {
        this.historyItems = items != null ? items : new ArrayList<>();
        rebuildDisplayList();
        notifyDataSetChanged();
    }

    private void rebuildDisplayList() {
        displayList.clear();

        Calendar now = Calendar.getInstance();
        int todayYear = now.get(Calendar.YEAR);
        int todayDay = now.get(Calendar.DAY_OF_YEAR);

        now.add(Calendar.DAY_OF_YEAR, -1);
        int yesterdayYear = now.get(Calendar.YEAR);
        int yesterdayDay = now.get(Calendar.DAY_OF_YEAR);

        boolean hasToday = false;
        boolean hasYesterday = false;
        boolean hasEarlier = false;

        for (BrowserHistoryEntity item : historyItems) {
            Calendar itemCal = Calendar.getInstance();
            itemCal.setTimeInMillis(item.getLastVisitTime());

            int itemYear = itemCal.get(Calendar.YEAR);
            int itemDay = itemCal.get(Calendar.DAY_OF_YEAR);

            String group;
            if (itemYear == todayYear && itemDay == todayDay) {
                group = "today";
            } else if (itemYear == yesterdayYear && itemDay == yesterdayDay) {
                group = "yesterday";
            } else {
                group = "earlier";
            }

            if ("today".equals(group) && !hasToday) {
                displayList.add(getHeaderText(group));
                hasToday = true;
            } else if ("yesterday".equals(group) && !hasYesterday) {
                displayList.add(getHeaderText(group));
                hasYesterday = true;
            } else if ("earlier".equals(group) && !hasEarlier) {
                displayList.add(getHeaderText(group));
                hasEarlier = true;
            }

            displayList.add(item);
        }
    }

    private String getHeaderText(String group) {
        switch (group) {
            case "today": return "\u4eca\u5929";
            case "yesterday": return "\u6628\u5929";
            default: return "\u66f4\u65e9";
        }
    }

    @Override
    public int getItemViewType(int position) {
        return displayList.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_history_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_history, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.tvHeader.setText((String) displayList.get(position));
        } else if (holder instanceof ItemViewHolder) {
            ItemViewHolder h = (ItemViewHolder) holder;
            BrowserHistoryEntity item = (BrowserHistoryEntity) displayList.get(position);

            String title = item.getTitle();
            h.tvTitle.setText(title != null && !title.isEmpty() ? title : item.getUrl());
            h.tvUrl.setText(item.getUrl());
            h.tvTime.setText(formatTime(item.getLastVisitTime()));

            int count = item.getVisitCount();
            if (count > 1) {
                h.tvCount.setVisibility(View.VISIBLE);
                h.tvCount.setText("\u8bbf\u95ee " + count + " \u6b21");
            } else {
                h.tvCount.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onItemClick(item);
            });

            h.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) longClickListener.onItemLongClick(item);
                return true;
            });

            h.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onItemClick(item);
            });
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    private String formatTime(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = timeFmt.format(new Date(timestamp));

        int todayYear = now.get(Calendar.YEAR);
        int todayDay = now.get(Calendar.DAY_OF_YEAR);
        int targetYear = target.get(Calendar.YEAR);
        int targetDay = target.get(Calendar.DAY_OF_YEAR);

        if (todayYear == targetYear && todayDay == targetDay) {
            return timeStr;
        }

        now.add(Calendar.DAY_OF_YEAR, -1);
        if (now.get(Calendar.YEAR) == targetYear
                && now.get(Calendar.DAY_OF_YEAR) == targetDay) {
            return "\u6628\u5929 " + timeStr;
        }

        SimpleDateFormat dateFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return dateFmt.format(new Date(timestamp));
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvHeader;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_header);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvUrl;
        final TextView tvTime;
        final TextView tvCount;
        final ImageButton btnDelete;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_history_icon);
            tvTitle = itemView.findViewById(R.id.tv_history_title);
            tvUrl = itemView.findViewById(R.id.tv_history_url);
            tvTime = itemView.findViewById(R.id.tv_history_time);
            tvCount = itemView.findViewById(R.id.tv_history_count);
            btnDelete = itemView.findViewById(R.id.btn_history_delete);
        }
    }
}

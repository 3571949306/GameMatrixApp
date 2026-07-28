package com.gamecenter.app.games;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameRegistry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 本地排行榜界面（P0-1）。
 * <p>
 * 顶部为游戏选择 ChipGroup（包含"总榜"+ 各游戏），下方为对应游戏的 Top-N 列表。
 * 总榜展示各游戏最高分排名，单游戏榜展示该游戏 Top-N 记录。
 * </p>
 */
public class LeaderboardActivity extends AppCompatActivity {

    /** Intent extra：指定初始展示的游戏 ID（可选） */
    public static final String EXTRA_GAME_ID = "leaderboard_game_id";

    private static final String GLOBAL_TAB_ID = "__global__";

    private LeaderboardStore store;
    private GameUsageStore usageStore;

    private MaterialToolbar toolbar;
    private ChipGroup cgGameTabs;
    private RecyclerView rvLeaderboard;
    private View layoutEmpty;
    private TextView tvEmptyTitle;

    private final LeaderboardAdapter adapter = new LeaderboardAdapter();
    private String currentTab = GLOBAL_TAB_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        store = new LeaderboardStore(this);
        usageStore = new GameUsageStore(this);

        bindViews();
        setupToolbar();
        setupGameTabs();

        // 解析初始 Tab
        String initialGameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        if (TextUtils.isEmpty(initialGameId)) {
            currentTab = GLOBAL_TAB_ID;
        } else {
            currentTab = initialGameId;
        }
        refreshList();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        cgGameTabs = findViewById(R.id.cg_game_tabs);
        rvLeaderboard = findViewById(R.id.rv_leaderboard);
        layoutEmpty = findViewById(R.id.layout_empty);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);

        rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        rvLeaderboard.setHasFixedSize(true);
        rvLeaderboard.setAdapter(adapter);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear) {
                confirmClear();
                return true;
            }
            return false;
        });
    }

    private void setupToolbar() {
        // 标题随选中 Tab 变化
        updateToolbarTitle();
    }

    private void setupGameTabs() {
        cgGameTabs.removeAllViews();
        // 总榜 Chip
        addChip(GLOBAL_TAB_ID, getString(R.string.leaderboard_tab_global));
        // 各游戏 Chip
        try {
            for (GameRegistry.Category cat : GameRegistry.getCategories(this)) {
                for (GameRegistry.Entry entry : cat.games) {
                    addChip(entry.id, entry.name);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        // 默认选中
        selectChip(currentTab);
        cgGameTabs.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip checked = group.findViewById(checkedIds.get(0));
            if (checked == null) return;
            String newTab = (String) checked.getTag();
            if (newTab == null || newTab.equals(currentTab)) return;
            currentTab = newTab;
            updateToolbarTitle();
            refreshList();
        });
    }

    private void addChip(String id, String name) {
        Chip chip = new Chip(this);
        chip.setText(name);
        chip.setTag(id);
        chip.setCheckable(true);
        chip.setId(View.generateViewId());
        cgGameTabs.addView(chip);
    }

    private void selectChip(String id) {
        for (int i = 0; i < cgGameTabs.getChildCount(); i++) {
            View child = cgGameTabs.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                boolean match = id.equals(chip.getTag());
                chip.setChecked(match);
            }
        }
    }

    private void updateToolbarTitle() {
        if (toolbar == null) return;
        if (GLOBAL_TAB_ID.equals(currentTab)) {
            toolbar.setTitle(R.string.leaderboard_title);
        } else {
            String name = lookupGameName(currentTab);
            toolbar.setTitle(TextUtils.isEmpty(name)
                    ? getString(R.string.leaderboard_title) : name);
        }
    }

    private String lookupGameName(String gameId) {
        try {
            for (GameRegistry.Category cat : GameRegistry.getCategories(this)) {
                for (GameRegistry.Entry entry : cat.games) {
                    if (entry.id.equals(gameId)) return entry.name;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void refreshList() {
        if (GLOBAL_TAB_ID.equals(currentTab)) {
            List<LeaderboardStore.GlobalEntry> global = store.getGlobalLeaderboard();
            adapter.setGlobalData(global, this);
            updateEmpty(global.isEmpty());
        } else {
            List<LeaderboardStore.Entry> board = store.getLeaderboard(currentTab);
            adapter.setData(board, this);
            updateEmpty(board.isEmpty());
        }
    }

    private void updateEmpty(boolean empty) {
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvLeaderboard.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty && tvEmptyTitle != null) {
            tvEmptyTitle.setText(R.string.leaderboard_empty_title);
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.leaderboard_clear)
                .setMessage(R.string.leaderboard_clear_confirm)
                .setPositiveButton(R.string.leaderboard_clear_confirm_positive,
                        (DialogInterface d, int w) -> {
                            if (GLOBAL_TAB_ID.equals(currentTab)) {
                                store.clearAll();
                            } else {
                                store.clearLeaderboard(currentTab);
                            }
                            refreshList();
                            Toast.makeText(this, R.string.leaderboard_clear,
                                    Toast.LENGTH_SHORT).show();
                        })
                .setNegativeButton(R.string.leaderboard_clear_confirm_negative, null)
                .show();
    }

    // ==================== Adapter ====================

    private static final class LeaderboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ENTRY = 1;
        private static final int TYPE_GLOBAL = 2;

        private final List<Object> items = new ArrayList<>();
        private @Nullable LeaderboardActivity host;

        void setData(@NonNull List<LeaderboardStore.Entry> entries,
                     @NonNull LeaderboardActivity activity) {
            items.clear();
            items.addAll(entries);
            host = activity;
            notifyDataSetChanged();
        }

        void setGlobalData(@NonNull List<LeaderboardStore.GlobalEntry> entries,
                           @NonNull LeaderboardActivity activity) {
            items.clear();
            items.addAll(entries);
            host = activity;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object item = items.get(position);
            return (item instanceof LeaderboardStore.GlobalEntry) ? TYPE_GLOBAL : TYPE_ENTRY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_GLOBAL) {
                return new GlobalVH(inf.inflate(R.layout.item_leaderboard_global, parent, false));
            }
            return new EntryVH(inf.inflate(R.layout.item_leaderboard_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int rank = position + 1;
            if (holder instanceof EntryVH) {
                ((EntryVH) holder).bind((LeaderboardStore.Entry) items.get(position), rank);
            } else if (holder instanceof GlobalVH) {
                GlobalVH gh = (GlobalVH) holder;
                LeaderboardStore.GlobalEntry ge = (LeaderboardStore.GlobalEntry) items.get(position);
                String name = (host != null) ? host.lookupGameName(ge.gameId) : null;
                gh.bind(ge, rank, name == null ? ge.gameId : name);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static String formatRank(int rank) {
            switch (rank) {
                case 1: return "🥇";
                case 2: return "🥈";
                case 3: return "🥉";
                default: return String.valueOf(rank);
            }
        }
    }

    private static final class EntryVH extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvScore;
        final TextView tvMeta;
        final TextView tvTime;

        EntryVH(@NonNull View v) {
            super(v);
            tvRank = v.findViewById(R.id.tv_lb_rank);
            tvScore = v.findViewById(R.id.tv_lb_score);
            tvMeta = v.findViewById(R.id.tv_lb_meta);
            tvTime = v.findViewById(R.id.tv_lb_time);
        }

        void bind(@NonNull LeaderboardStore.Entry e, int rank) {
            tvRank.setText(LeaderboardAdapter.formatRank(rank));
            tvScore.setText(itemView.getContext().getString(
                    R.string.leaderboard_score_format, e.score));
            String duration = formatDuration(itemView.getContext(), e.durationMs);
            String diffName = TextUtils.isEmpty(e.difficultyName) ? "默认" : e.difficultyName;
            tvMeta.setText(itemView.getContext().getString(
                    R.string.leaderboard_meta_format, diffName, duration));
            tvTime.setText(formatRelativeTime(e.timestamp));
        }

        private static String formatDuration(@NonNull android.content.Context ctx, long ms) {
            long sec = Math.max(0, ms / 1000L);
            if (sec < 60) {
                return ctx.getString(
                        R.string.leaderboard_duration_seconds_format, (int) sec);
            }
            int min = (int) (sec / 60);
            return ctx.getString(
                    R.string.leaderboard_duration_minutes_format, min);
        }

        private static String formatRelativeTime(long ts) {
            if (ts <= 0) return "";
            long diff = System.currentTimeMillis() - ts;
            long sec = diff / 1000L;
            if (sec < 60) return "刚刚";
            long min = sec / 60L;
            if (min < 60) return min + " 分钟前";
            long hour = min / 60L;
            if (hour < 24) return hour + " 小时前";
            long day = hour / 24L;
            if (day < 30) return day + " 天前";
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ts));
        }
    }

    private static final class GlobalVH extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvGameName;
        final TextView tvMeta;
        final TextView tvScore;

        GlobalVH(@NonNull View v) {
            super(v);
            tvRank = v.findViewById(R.id.tv_lb_rank);
            tvGameName = v.findViewById(R.id.tv_lb_game_name);
            tvMeta = v.findViewById(R.id.tv_lb_meta);
            tvScore = v.findViewById(R.id.tv_lb_score);
        }

        void bind(@NonNull LeaderboardStore.GlobalEntry ge, int rank, @NonNull String displayName) {
            tvRank.setText(LeaderboardAdapter.formatRank(rank));
            tvGameName.setText(displayName);
            tvMeta.setText(itemView.getContext().getString(
                    R.string.leaderboard_global_entries) + ": " + ge.entriesCount);
            tvScore.setText(String.valueOf(ge.highScore));
        }
    }
}

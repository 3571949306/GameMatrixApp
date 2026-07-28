package com.gamecenter.app.games;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 收藏分组管理界面（P0-2）。
 * <p>
 * 顶部为 MaterialToolbar，列表分两段：
 * <ul>
 *   <li>智能分组（按游戏分类：经典/益智/休闲/其他）</li>
 *   <li>自定义分组（用户增删改）</li>
 * </ul>
 * </p>
 * <p>点击分组行展开该分组的游戏列表（以 Toast 简要展示，避免 UI 过重）。</p>
 */
public class FavoriteGroupsActivity extends AppCompatActivity {

    private FavoriteGroupStore groupStore;
    private GameUsageStore usageStore;

    private RecyclerView rvGroups;
    private View layoutEmpty;
    private final GroupsAdapter adapter = new GroupsAdapter();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_groups);

        groupStore = new FavoriteGroupStore(this);
        usageStore = new GameUsageStore(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        rvGroups = findViewById(R.id.rv_groups);
        layoutEmpty = findViewById(R.id.layout_empty);

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add_group) {
                showAddGroupDialog();
                return true;
            }
            return false;
        });

        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvGroups.setHasFixedSize(true);
        rvGroups.setAdapter(adapter);

        refresh();
    }

    private void refresh() {
        adapter.load(buildItems());
        boolean empty = adapter.getItemCount() == 0;
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvGroups.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** 构建列表项：智能分组 Section + 智能分组项 + 自定义分组 Section + 自定义分组项。 */
    private List<ListItem> buildItems() {
        List<ListItem> items = new ArrayList<>();
        Set<String> favoriteIds = new HashSet<>(usageStore.getFavoriteIds());
        if (favoriteIds.isEmpty()) {
            return items;
        }

        // 智能分组（按分类）
        items.add(ListItem.header(getString(R.string.favorite_groups_smart_section)));
        SmartBuckets buckets = bucketFavoritesByCategory(favoriteIds);
        if (buckets.classics > 0) {
            items.add(ListItem.smart(getString(R.string.favorite_groups_smart_classics), buckets.classics));
        }
        if (buckets.puzzle > 0) {
            items.add(ListItem.smart(getString(R.string.favorite_groups_smart_puzzle), buckets.puzzle));
        }
        if (buckets.casual > 0) {
            items.add(ListItem.smart(getString(R.string.favorite_groups_smart_casual), buckets.casual));
        }
        if (buckets.other > 0) {
            items.add(ListItem.smart(getString(R.string.favorite_groups_smart_other), buckets.other));
        }

        // 自定义分组
        items.add(ListItem.header(getString(R.string.favorite_groups_custom_section)));
        for (FavoriteGroupStore.Group g : groupStore.getGroups()) {
            int count = countGamesInGroup(g.id, favoriteIds);
            items.add(ListItem.custom(g, count));
        }
        return items;
    }

    private int countGamesInGroup(@NonNull String groupId, @NonNull Set<String> favoriteIds) {
        int count = 0;
        for (String gid : groupStore.getGameIdsInGroup(groupId)) {
            if (favoriteIds.contains(gid)) count++;
        }
        return count;
    }

    private SmartBuckets bucketFavoritesByCategory(@NonNull Set<String> favoriteIds) {
        SmartBuckets b = new SmartBuckets();
        try {
            for (GameRegistry.Category cat : GameRegistry.getCategories(this)) {
                for (GameRegistry.Entry entry : cat.games) {
                    if (!favoriteIds.contains(entry.id)) continue;
                    switch (cat.categoryKey) {
                        case GameRegistry.CATEGORY_CLASSICS: b.classics++; break;
                        case GameRegistry.CATEGORY_PUZZLE:   b.puzzle++; break;
                        case GameRegistry.CATEGORY_CASUAL:   b.casual++; break;
                        default:                            b.other++; break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return b;
    }

    private void showAddGroupDialog() {
        showTextInputDialog(
                getString(R.string.favorite_groups_add),
                getString(R.string.favorite_groups_add_hint),
                "",
                newName -> {
                    if (TextUtils.isEmpty(newName)) {
                        Toast.makeText(this, R.string.favorite_groups_name_empty,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String id = groupStore.addGroup(newName);
                    if (id == null) {
                        Toast.makeText(this, R.string.favorite_groups_name_duplicate,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, getString(R.string.favorite_groups_add_positive)
                                    + ": " + newName, Toast.LENGTH_SHORT).show();
                    refresh();
                });
    }

    private void showRenameDialog(@NonNull FavoriteGroupStore.Group group) {
        showTextInputDialog(
                getString(R.string.favorite_groups_rename),
                getString(R.string.favorite_groups_add_hint),
                group.name,
                newName -> {
                    if (TextUtils.isEmpty(newName)) {
                        Toast.makeText(this, R.string.favorite_groups_name_empty,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (groupStore.renameGroup(group.id, newName)) {
                        refresh();
                    } else {
                        Toast.makeText(this, R.string.favorite_groups_name_duplicate,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteConfirm(@NonNull FavoriteGroupStore.Group group) {
        if (FavoriteGroupStore.DEFAULT_GROUP_ID.equals(group.id)) {
            Toast.makeText(this, getString(R.string.favorite_groups_default_name)
                            + " 不可删除", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.favorite_groups_delete)
                .setMessage(getString(R.string.favorite_groups_delete_confirm, group.name))
                .setPositiveButton(R.string.favorite_groups_delete,
                        (DialogInterface d, int w) -> {
                            groupStore.deleteGroup(group.id);
                            refresh();
                        })
                .setNegativeButton(R.string.leaderboard_clear_confirm_negative, null)
                .show();
    }

    private void showTextInputDialog(@NonNull String title, @NonNull String hint,
                                     @NonNull String initial,
                                     @NonNull OnTextSubmit callback) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint(hint);
        et.setText(initial);
        if (!TextUtils.isEmpty(initial)) {
            et.setSelection(initial.length());
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        container.addView(et);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(R.string.favorite_groups_add_positive,
                        (d, w) -> callback.onSubmit(et.getText().toString().trim()))
                .setNegativeButton(R.string.leaderboard_clear_confirm_negative, null)
                .show();
    }

    private void showGroupGames(@NonNull FavoriteGroupStore.Group group) {
        Set<String> favs = usageStore.getFavoriteIds();
        List<String> ids = groupStore.getGameIdsInGroup(group.id);
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            if (!favs.contains(id)) continue;
            String name = lookupGameName(id);
            names.add(name == null ? id : name);
        }
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.favorite_groups_empty_in_group,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder(group.name).append(":\n");
        for (int i = 0; i < names.size(); i++) {
            sb.append(i + 1).append(". ").append(names.get(i)).append('\n');
        }
        new AlertDialog.Builder(this)
                .setTitle(group.name)
                .setMessage(sb.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
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

    // ==================== 内部数据模型 ====================

    private interface OnTextSubmit {
        void onSubmit(String text);
    }

    private static final class SmartBuckets {
        int classics;
        int puzzle;
        int casual;
        int other;
    }

    private static final class ListItem {
        static final int TYPE_HEADER = 1;
        static final int TYPE_GROUP = 2;

        final int type;
        String headerText;
        FavoriteGroupStore.Group group;
        int count;

        private ListItem(int type) { this.type = type; }

        static ListItem header(@NonNull String text) {
            ListItem i = new ListItem(TYPE_HEADER);
            i.headerText = text;
            return i;
        }

        static ListItem custom(@NonNull FavoriteGroupStore.Group g, int count) {
            ListItem i = new ListItem(TYPE_GROUP);
            i.group = g;
            i.count = count;
            return i;
        }

        static ListItem smart(@NonNull String name, int count) {
            // 智能分组使用临时 Group（id=smart_name），不可编辑
            ListItem i = new ListItem(TYPE_GROUP);
            i.group = new FavoriteGroupStore.Group("smart_" + name, name);
            i.count = count;
            return i;
        }
    }

    // ==================== Adapter ====================

    private final class GroupsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<ListItem> items = new ArrayList<>();

        void load(@NonNull List<ListItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite_group, parent, false);
            return new GroupVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((GroupVH) holder).bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class GroupVH extends RecyclerView.ViewHolder {
        final TextView tvHeader;
        final View rowGroup;
        final TextView tvName;
        final TextView tvCount;
        final View btnMore;

        GroupVH(@NonNull View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_section_header);
            rowGroup = v.findViewById(R.id.row_group);
            tvName = v.findViewById(R.id.tv_group_name);
            tvCount = v.findViewById(R.id.tv_group_count);
            btnMore = v.findViewById(R.id.btn_more);
        }

        void bind(@NonNull ListItem item) {
            if (item.type == ListItem.TYPE_HEADER) {
                tvHeader.setVisibility(View.VISIBLE);
                tvHeader.setText(item.headerText);
                rowGroup.setVisibility(View.GONE);
                return;
            }
            tvHeader.setVisibility(View.GONE);
            rowGroup.setVisibility(View.VISIBLE);
            tvName.setText(item.group.name);
            tvCount.setText(getString(R.string.favorite_groups_count_format, item.count));
            boolean isCustom = !item.group.id.startsWith("smart_");
            btnMore.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            btnMore.setOnClickListener(v -> showGroupMenu(item.group));
            rowGroup.setOnClickListener(v -> showGroupGames(item.group));
        }

        private void showGroupMenu(@NonNull FavoriteGroupStore.Group group) {
            PopupMenu pm = new PopupMenu(itemView.getContext(), btnMore);
            pm.getMenu().add(0, 1, 0, R.string.favorite_groups_rename);
            pm.getMenu().add(0, 2, 1, R.string.favorite_groups_delete);
            pm.setOnMenuItemClickListener(menuItem -> {
                int id = menuItem.getItemId();
                if (id == 1) {
                    showRenameDialog(group);
                    return true;
                } else if (id == 2) {
                    showDeleteConfirm(group);
                    return true;
                }
                return false;
            });
            pm.show();
        }
    }
}

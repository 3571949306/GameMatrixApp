package com.gamecenter.app.games.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.games.model.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏启动对话框 — 统一的难度选择界面
 * <p>
 * 所有游戏在启动前都必须先经过此对话框选择难度（或联机模式），
 * 防止用户未选择难度就直接进入游戏。
 * </p>
 *
 * 使用方式：
 * <pre>
 * new GameStartDialog(context, "贪吃蛇", difficultyLevels, true,
 *     new GameStartDialog.Listener() {
 *         public void onDifficultySelected(int index) { ... }
 *         public void onOnlineSelected() { ... }
 *         public void onCancelled() { ... }
 *     }
 * ).show();
 * </pre>
 */
public class GameStartDialog extends Dialog {

    /**
     * 对话框回调接口
     */
    public interface Listener {
        /** 用户选择了难度 */
        void onDifficultySelected(int difficultyIndex);

        /** 用户点击了联机对战 */
        default void onOnlineSelected() {}

        /** 用户取消（按返回键或点击外部） */
        default void onCancelled() {}
    }

    private final String gameName;
    private final List<DifficultyLevel> levels;
    private final boolean showOnlineButton;
    private final Listener listener;
    private RecyclerView recyclerView;
    private int selectedIndex = -1;

    public GameStartDialog(@NonNull Context context,
                           @NonNull String gameName,
                           @NonNull List<DifficultyLevel> levels,
                           boolean showOnlineButton,
                           @NonNull Listener listener) {
        super(context);
        this.gameName = gameName;
        this.levels = levels != null ? levels : new ArrayList<>();
        this.showOnlineButton = showOnlineButton;
        this.listener = listener;

        // 默认选中推荐难度
        for (int i = 0; i < this.levels.size(); i++) {
            if (this.levels.get(i).recommended) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0 && !this.levels.isEmpty()) {
            selectedIndex = 0; // 默认选第一个
        }

        setupDialog();
    }

    private void setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setCanceledOnTouchOutside(true);
        setCancelable(true);

        // 设置关闭回调
        setOnDismissListener(d -> {
            if (selectedIndex < 0) {
                listener.onCancelled();
            }
        });

        View root = createContentView();
        setContentView(root);

        // 设置对话框宽度
        Window window = getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.85),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, dp(20), pad, dp(16));
        root.setBackground(createRoundedBackground(0xFF2A2A3E, dp(16)));

        // 标题
        TextView title = new TextView(getContext());
        title.setText(gameName);
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 副标题
        TextView subtitle = new TextView(getContext());
        subtitle.setText("选择难度开始游戏");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(20);
        root.addView(subtitle, subParams);

        // 难度选择列表
        if (!levels.isEmpty()) {
            recyclerView = new RecyclerView(getContext());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(new DifficultyAdapter());
            recyclerView.setNestedScrollingEnabled(false);
            int maxHeight = dp(240);
            LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rvParams.bottomMargin = dp(16);
            root.addView(recyclerView, rvParams);
        }

        // 联机按钮（如果支持）
        if (showOnlineButton) {
            LinearLayout onlineBtn = createButton("🌐 联机对战", 0xFF4CAF50, v -> {
                selectedIndex = -1; // 标记为非取消
                dismiss();
                listener.onOnlineSelected();
            });
            LinearLayout.LayoutParams onlineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            onlineParams.bottomMargin = dp(8);
            root.addView(onlineBtn, onlineParams);
        }

        // 开始游戏按钮
        LinearLayout startBtn = createButton("🎮 开始游戏", 0xFF6C63FF, v -> {
            if (selectedIndex >= 0) {
                int idx = selectedIndex;
                selectedIndex = -1; // 标记为非取消
                dismiss();
                listener.onDifficultySelected(idx);
            }
        });
        root.addView(startBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        return root;
    }

    private LinearLayout createButton(String text, int bgColor, View.OnClickListener clickListener) {
        LinearLayout btn = new LinearLayout(getContext());
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setBackground(createRoundedBackground(bgColor, dp(12)));
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(clickListener);

        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return btn;
    }

    private android.graphics.drawable.Drawable createRoundedBackground(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    /**
     * 难度列表适配器
     */
    private class DifficultyAdapter extends RecyclerView.Adapter<DifficultyAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(16), dp(12), dp(16), dp(12));
            item.setBackground(createRoundedBackground(0xFF3A3A52, dp(12)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            item.setLayoutParams(params);

            TextView nameTv = new TextView(getContext());
            nameTv.setTextSize(16);
            nameTv.setTextColor(0xFFFFFFFF);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            nameTv.setId(android.R.id.text1);
            item.addView(nameTv);

            TextView descTv = new TextView(getContext());
            descTv.setTextSize(12);
            descTv.setTextColor(0xFFBBBBBB);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = dp(2);
            item.addView(descTv, descParams);

            return new VH(item);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DifficultyLevel level = levels.get(position);
            holder.nameTv.setText(level.name + (level.recommended ? "  ⭐推荐" : ""));
            holder.descTv.setText(level.description);

            boolean selected = (position == selectedIndex);
            holder.itemView.setBackground(
                    createRoundedBackground(selected ? 0xFF4A4A6A : 0xFF3A3A52, dp(12)));

            holder.itemView.setOnClickListener(v -> {
                int old = selectedIndex;
                selectedIndex = holder.getAdapterPosition();
                if (old >= 0) notifyItemChanged(old);
                notifyItemChanged(selectedIndex);
            });
        }

        @Override
        public int getItemCount() {
            return levels.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView nameTv;
            final TextView descTv;

            VH(View itemView) {
                super(itemView);
                nameTv = itemView.findViewById(android.R.id.text1);
                descTv = (TextView) ((LinearLayout) itemView).getChildAt(1);
            }
        }
    }
}

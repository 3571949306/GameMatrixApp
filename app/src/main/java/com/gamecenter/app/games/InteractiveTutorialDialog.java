package com.gamecenter.app.games;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.gamecenter.app.R;

import java.util.List;

/**
 * 交互式教程对话框
 * 支持多页滑动查看，带有动画效果
 */
public class InteractiveTutorialDialog extends Dialog {

    private final List<TutorialPage> pages;
    private final String gameName;
    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private Button btnNext;
    private Button btnPrevious;
    private Button btnClose;
    private int currentPage = 0;

    public InteractiveTutorialDialog(@NonNull Context context, String gameName, List<TutorialPage> pages) {
        super(context);
        this.gameName = gameName;
        this.pages = pages;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_interactive_tutorial);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = findViewById(R.id.tv_tutorial_title);
        viewPager = findViewById(R.id.view_pager);
        dotsLayout = findViewById(R.id.dots_layout);
        btnNext = findViewById(R.id.btn_next);
        btnPrevious = findViewById(R.id.btn_previous);
        btnClose = findViewById(R.id.btn_close);

        tvTitle.setText(gameName + " 新手教程");

        TutorialPagerAdapter adapter = new TutorialPagerAdapter(pages);
        viewPager.setAdapter(adapter);

        setupDots();
        updateButtons();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateDots();
                updateButtons();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentPage < pages.size() - 1) {
                viewPager.setCurrentItem(currentPage + 1);
            } else {
                dismiss();
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                viewPager.setCurrentItem(currentPage - 1);
            }
        });

        btnClose.setOnClickListener(v -> dismiss());
    }

    private void setupDots() {
        dotsLayout.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            View dot = new View(getContext());
            int size = (int) (8 * getContext().getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(size / 2, 0, size / 2, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
            dotsLayout.addView(dot);
        }
    }

    private void updateDots() {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            dotsLayout.getChildAt(i).setBackgroundResource(
                    i == currentPage ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void updateButtons() {
        btnPrevious.setVisibility(currentPage > 0 ? View.VISIBLE : View.INVISIBLE);
        btnNext.setText(currentPage < pages.size() - 1 ? "下一步" : "明白了");
    }

    public static class TutorialPage {
        public final int iconRes;
        public final String title;
        public final String description;

        public TutorialPage(int iconRes, String title, String description) {
            this.iconRes = iconRes;
            this.title = title;
            this.description = description;
        }

        public TutorialPage(String title, String description) {
            this.iconRes = 0;
            this.title = title;
            this.description = description;
        }
    }

    private static class TutorialPagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<TutorialPagerAdapter.PageViewHolder> {

        private final List<TutorialPage> pages;

        TutorialPagerAdapter(List<TutorialPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tutorial_page, parent, false);
            return new PageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            TutorialPage page = pages.get(position);
            holder.bind(page);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        static class PageViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            private final ImageView ivIcon;
            private final TextView tvTitle;
            private final TextView tvDescription;

            PageViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_tutorial_icon);
                tvTitle = itemView.findViewById(R.id.tv_tutorial_page_title);
                tvDescription = itemView.findViewById(R.id.tv_tutorial_page_desc);
            }

            void bind(TutorialPage page) {
                if (page.iconRes != 0) {
                    ivIcon.setImageResource(page.iconRes);
                    ivIcon.setVisibility(View.VISIBLE);
                } else {
                    ivIcon.setVisibility(View.GONE);
                }
                tvTitle.setText(page.title);
                tvDescription.setText(page.description);
            }
        }
    }
}

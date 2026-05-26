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

import com.gamecenter.app.games.R;

import java.util.List;

/**
 * 交互式教程对话框
 * <p>
 * 以多页滑动的方式展示游戏教程内容，支持左右翻页浏览、
 * 页面指示器（小圆点）和上一步/下一步导航按钮。
 * 适用于规则较复杂的游戏，需要分步骤讲解的场景。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>基于ViewPager2实现页面滑动，利用RecyclerView的复用机制优化内存</li>
 *   <li>内部定义TutorialPage数据类封装每页内容，支持可选的图标资源</li>
 *   <li>使用RecyclerView.Adapter作为ViewPager2的数据适配器</li>
 *   <li>最后一页的"下一步"按钮自动变为"明白了"，点击关闭对话框</li>
 * </ul>
 * </p>
 */
public class InteractiveTutorialDialog extends Dialog {

    /** 教程页面数据列表 */
    private final List<TutorialPage> pages;
    /** 游戏名称，显示在对话框标题中 */
    private final String gameName;
    /** 页面滑动容器 */
    private ViewPager2 viewPager;
    /** 页面指示器容器（小圆点） */
    private LinearLayout dotsLayout;
    /** 下一步按钮，最后一页变为"明白了" */
    private Button btnNext;
    /** 上一步按钮，第一页时隐藏 */
    private Button btnPrevious;
    /** 关闭按钮 */
    private Button btnClose;
    /** 当前显示的页面索引 */
    private int currentPage = 0;

    /**
     * 构造函数
     *
     * @param context   上下文对象
     * @param gameName  游戏名称，显示在对话框标题中
     * @param pages     教程页面列表，至少包含一页
     */
    public InteractiveTutorialDialog(@NonNull Context context, String gameName, List<TutorialPage> pages) {
        super(context);
        this.gameName = gameName;
        this.pages = pages;
    }

    /**
     * 对话框创建时的初始化
     * <p>
     * 完成以下工作：
     * 1. 设置无标题窗口和布局
     * 2. 初始化ViewPager2和页面适配器
     * 3. 创建页面指示器（小圆点）
     * 4. 配置翻页按钮和页面切换回调
     * </p>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_interactive_tutorial);

        // 设置对话框宽度为全屏，高度自适应内容
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

        // 设置ViewPager2适配器
        TutorialPagerAdapter adapter = new TutorialPagerAdapter(pages);
        viewPager.setAdapter(adapter);

        // 初始化页面指示器和按钮状态
        setupDots();
        updateButtons();

        // 监听页面滑动事件，同步更新指示器和按钮
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateDots();
                updateButtons();
            }
        });

        // 下一步按钮：非最后一页翻到下一页，最后一页关闭对话框
        btnNext.setOnClickListener(v -> {
            if (currentPage < pages.size() - 1) {
                viewPager.setCurrentItem(currentPage + 1);
            } else {
                dismiss();
            }
        });

        // 上一步按钮：翻到前一页
        btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                viewPager.setCurrentItem(currentPage - 1);
            }
        });

        // 关闭按钮：直接关闭对话框
        btnClose.setOnClickListener(v -> dismiss());
    }

    /**
     * 创建页面指示器（小圆点）
     * <p>
     * 根据教程页面数量创建对应数量的小圆点视图，
     * 第一页使用激活状态样式，其余使用未激活样式。
     * 圆点大小为8dp，间距为4dp。
     * </p>
     */
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

    /**
     * 更新页面指示器的激活状态
     * <p>
     * 当前页对应的圆点使用激活样式，其余使用未激活样式。
     * </p>
     */
    private void updateDots() {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            dotsLayout.getChildAt(i).setBackgroundResource(
                    i == currentPage ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    /**
     * 根据当前页面位置更新按钮状态
     * <p>
     * 第一页隐藏"上一步"按钮，其余页面显示。
     * 最后一页的"下一步"按钮文本改为"明白了"。
     * </p>
     */
    private void updateButtons() {
        btnPrevious.setVisibility(currentPage > 0 ? View.VISIBLE : View.INVISIBLE);
        btnNext.setText(currentPage < pages.size() - 1 ? "下一步" : "明白了");
    }

    /**
     * 教程页面数据类
     * <p>
     * 封装单个教程页面的内容，包括可选的图标、标题和描述文本。
     * 提供两个构造函数：带图标和不带图标。
     * </p>
     */
    public static class TutorialPage {
        /** 图标资源ID，0表示不显示图标 */
        public final int iconRes;
        /** 页面标题 */
        public final String title;
        /** 页面描述内容 */
        public final String description;

        /**
         * 创建带图标的教程页面
         *
         * @param iconRes    图标资源ID
         * @param title      页面标题
         * @param description 页面描述内容
         */
        public TutorialPage(int iconRes, String title, String description) {
            this.iconRes = iconRes;
            this.title = title;
            this.description = description;
        }

        /**
         * 创建不带图标的教程页面
         * <p>
         * iconRes设为0，绑定视图时图标视图会被隐藏。
         * </p>
         *
         * @param title       页面标题
         * @param description 页面描述内容
         */
        public TutorialPage(String title, String description) {
            this.iconRes = 0;
            this.title = title;
            this.description = description;
        }
    }

    /**
     * 教程页面ViewPager2适配器
     * <p>
     * 基于RecyclerView.Adapter实现，为ViewPager2提供教程页面视图。
     * 每个页面包含图标、标题和描述三个视图元素。
     * </p>
     */
    private static class TutorialPagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<TutorialPagerAdapter.PageViewHolder> {

        /** 教程页面数据列表 */
        private final List<TutorialPage> pages;

        TutorialPagerAdapter(List<TutorialPage> pages) {
            this.pages = pages;
        }

        /**
         * 创建页面ViewHolder
         *
         * @param parent   父视图组
         * @param viewType 视图类型
         * @return 页面ViewHolder实例
         */
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tutorial_page, parent, false);
            return new PageViewHolder(view);
        }

        /**
         * 绑定页面数据到ViewHolder
         * <p>
         * 根据TutorialPage的数据设置图标、标题和描述。
         * 当iconRes为0时隐藏图标视图。
         * </p>
         *
         * @param holder   页面ViewHolder
         * @param position 页面位置
         */
        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            TutorialPage page = pages.get(position);
            holder.bind(page);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        /**
         * 教程页面ViewHolder
         * <p>
         * 持有页面中的图标、标题和描述视图引用，
         * 负责将TutorialPage数据绑定到视图上。
         * </p>
         */
        static class PageViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            /** 页面图标视图 */
            private final ImageView ivIcon;
            /** 页面标题视图 */
            private final TextView tvTitle;
            /** 页面描述视图 */
            private final TextView tvDescription;

            PageViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_tutorial_icon);
                tvTitle = itemView.findViewById(R.id.tv_tutorial_page_title);
                tvDescription = itemView.findViewById(R.id.tv_tutorial_page_desc);
            }

            /**
             * 绑定教程页面数据到视图
             * <p>
             * 当iconRes不为0时显示图标，否则隐藏图标视图。
             * </p>
             *
             * @param page 教程页面数据
             */
            void bind(TutorialPage page) {
                if (page.iconRes != 0) {
                    ivIcon.setImageResource(page.iconRes);
                    ivIcon.setVisibility(View.VISIBLE);
                } else {
                    // 无图标时隐藏图标视图，避免占位空白
                    ivIcon.setVisibility(View.GONE);
                }
                tvTitle.setText(page.title);
                tvDescription.setText(page.description);
            }
        }
    }
}

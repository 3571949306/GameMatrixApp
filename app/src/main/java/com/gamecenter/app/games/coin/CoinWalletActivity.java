package com.gamecenter.app.games.coin;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * 金币钱包页 —— 余额 / 赚取规则 / 最近流水 / 称号商店
 *
 * <p>功能：
 * <ul>
 *   <li>余额卡片：大号金币余额 + "今日已赚 X / 上限 200" 副行</li>
 *   <li>赚取规则说明区（胜场 10 金币起、连胜加成、每日上限 200、完成对局 3 金币）</li>
 *   <li>最近流水：最近 20 条，"时间 · 原因 ±金币"，最新在上</li>
 *   <li>称号商店：可购买 4 个（兑换/佩戴/已佩戴）+ 成就称号 3 个（未拥有时禁用并提示解锁门槛）</li>
 *   <li>使用护眼主题颜色，样式仿成就中心</li>
 * </ul>
 * </p>
 */
public class CoinWalletActivity extends AppCompatActivity {

    /** 正向流水（赚取）颜色，与成就中心解锁色一致。 */
    private static final String COLOR_POSITIVE = "#5B8A72";

    private TextView tvBalance;
    private TextView tvEarnedToday;
    private LinearLayout layoutEntries;
    private LinearLayout layoutTitles;

    private CoinWallet wallet;

    /**
     * 启动金币钱包的便捷方法
     *
     * @param context 上下文
     */
    public static void launch(@NonNull Context context) {
        Intent intent = new Intent(context, CoinWalletActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coin_wallet);

        wallet = new CoinWallet(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        tvBalance = findViewById(R.id.tv_coin_balance);
        tvEarnedToday = findViewById(R.id.tv_coin_earned_today);
        layoutEntries = findViewById(R.id.layout_coin_entries);
        layoutTitles = findViewById(R.id.layout_coin_titles);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兑换/佩戴操作后返回时也要刷新，统一在 onResume 全量重绘
        refresh();
    }

    /** 全量刷新余额、今日已赚、流水与称号商店。 */
    private void refresh() {
        tvBalance.setText(String.valueOf(wallet.balance()));
        tvEarnedToday.setText(getString(
                R.string.coin_wallet_earned_today_format, wallet.earnedToday()));
        renderEntries();
        renderTitles();
    }

    /** 渲染最近 20 条流水（最新在上）；无流水时显示占位文案。 */
    private void renderEntries() {
        layoutEntries.removeAllViews();
        List<CoinLedger.Entry> entries = wallet.recentEntries(20);
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.coin_wallet_recent_empty);
            empty.setTextSize(12);
            empty.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            layoutEntries.addView(empty);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (CoinLedger.Entry e : entries) {
            String left = fmt.format(e.timestamp) + " · " + e.reason;
            String right = (e.delta > 0 ? "+" : "") + e.delta;
            layoutEntries.addView(createEntryRow(left, right, e.delta > 0));
        }
    }

    /** 渲染称号商店：可购买 4 个 + 成就称号 3 个，按钮状态由拥有/佩戴/类型三态决定。 */
    private void renderTitles() {
        layoutTitles.removeAllViews();
        for (TitleCatalog.Title title : TitleCatalog.all()) {
            layoutTitles.addView(createTitleRow(title));
        }
        for (TitleCatalog.Title title : TitleCatalog.achievementTitles()) {
            layoutTitles.addView(createTitleRow(title));
        }
    }

    /**
     * 单条流水行：左侧"时间 · 原因"，右侧"±金币"（赚取绿色、消费灰色）。
     */
    private View createEntryRow(String left, String right, boolean positive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tvLeft = new TextView(this);
        tvLeft.setText(left);
        tvLeft.setTextSize(12);
        tvLeft.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
        tvLeft.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvRight = new TextView(this);
        tvRight.setText(right);
        tvRight.setTextSize(12);
        tvRight.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRight.setTextColor(Color.parseColor(COLOR_POSITIVE));
        if (!positive) {
            tvRight.setTextColor(themeColor(
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
        }

        row.addView(tvLeft);
        row.addView(tvRight);
        return row;
    }

    /**
     * 单个称号行：名称 + 价格/解锁条件 + 状态按钮（兑换 / 佩戴 / 已佩戴 / 解锁 N 个成就）。
     */
    private View createTitleRow(TitleCatalog.Title title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(title.name);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));

        TextView tvPrice = new TextView(this);
        // 成就称号不显示金币价格，副标题改为获取方式说明
        if (title.requiredAchievements > 0) {
            tvPrice.setText(R.string.coin_wallet_title_achievement_source);
        } else {
            tvPrice.setText(getString(R.string.coin_wallet_title_price_format, title.cost));
        }
        tvPrice.setTextSize(12);
        tvPrice.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));

        info.addView(tvName);
        info.addView(tvPrice);

        // 项目硬性规范：所有 Button 显式关闭 stateListAnimator
        Button btn = new Button(this);
        btn.setStateListAnimator(null);
        btn.setTextSize(12);
        btn.setPadding(dp(16), dp(4), dp(16), dp(4));

        boolean owned = wallet.isOwned(title.id);
        boolean equipped = wallet.equippedTitleId() == title.id;
        if (equipped) {
            btn.setText(R.string.coin_wallet_btn_equipped);
            btn.setEnabled(false);
        } else if (owned) {
            btn.setText(R.string.coin_wallet_btn_equip);
            btn.setOnClickListener(v -> {
                wallet.equipTitle(title.id);
                refresh();
            });
        } else if (title.requiredAchievements > 0) {
            // 成就称号未拥有：不可购买，禁用并提示解锁门槛
            btn.setText(getString(
                    R.string.coin_wallet_btn_locked_format, title.requiredAchievements));
            btn.setEnabled(false);
        } else {
            btn.setText(R.string.coin_wallet_btn_buy);
            btn.setOnClickListener(v -> {
                if (wallet.buyTitle(title.id)) {
                    Toast.makeText(this, getString(
                            R.string.coin_wallet_buy_success_format, title.name),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.coin_wallet_buy_failed,
                            Toast.LENGTH_SHORT).show();
                }
                refresh();
            });
        }

        row.addView(info);
        row.addView(btn);
        return row;
    }

    /** 解析主题属性颜色（护眼主题可随 DayNight 切换）。 */
    private int themeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    /** dp 转 px。 */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

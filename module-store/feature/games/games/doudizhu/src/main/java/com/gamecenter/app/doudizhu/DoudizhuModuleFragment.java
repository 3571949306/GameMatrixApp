package com.gamecenter.app.doudizhu;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

/**
 * 斗地主模块主 Fragment（菜单页面）。
 * 提供单机模式入口，联机模式暂未迁移。
 */
public class DoudizhuModuleFragment extends Fragment {

    private boolean isNightMode(@Nullable Context context) {
        if (context == null) return false;
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode(ctx);

        int bgColor = night ? 0xFF121212 : 0xFFF5F5F5;
        int textColor = night ? 0xFFEEEEEE : 0xFF212121;
        int accentColor = night ? 0xFFBB86FC : 0xFF6200EE;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(0, (int) (48 * dp), 0, 0);

        // 标题
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_doudizhu));
        tvTitle.setTextSize(32);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(accentColor);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 副标题
        TextView tvSubtitle = new TextView(ctx);
        tvSubtitle.setText(getString(R.string.game_title_doudizhu_subtitle));
        tvSubtitle.setTextSize(16);
        tvSubtitle.setTextColor(textColor);
        tvSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (8 * dp);
        tvSubtitle.setLayoutParams(subLp);
        root.addView(tvSubtitle);

        // 单机模式按钮
        Button btnSingle = new Button(ctx);
        btnSingle.setText(getString(R.string.game_doudizhu_single));
        btnSingle.setTextColor(Color.WHITE);
        btnSingle.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (48 * dp);
        btnSingle.setLayoutParams(btnLp);
        btnSingle.setOnClickListener(v -> {
            // 单机模式：直接显示简化版斗地主游戏界面
            Toast.makeText(ctx, R.string.game_doudizhu_single_wip, Toast.LENGTH_SHORT).show();
        });
        root.addView(btnSingle);

        // 规则说明按钮
        Button btnRules = new Button(ctx);
        btnRules.setText(getString(R.string.game_btn_rules));
        btnRules.setTextColor(textColor);
        btnRules.setBackgroundColor(night ? 0xFF2D2D2D : 0xFFE0E0E0);
        LinearLayout.LayoutParams rulesLp = new LinearLayout.LayoutParams(
                (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        rulesLp.topMargin = (int) (16 * dp);
        btnRules.setLayoutParams(rulesLp);
        btnRules.setOnClickListener(v -> showRulesDialog(ctx));
        root.addView(btnRules);

        return root;
    }

    private void showRulesDialog(Context ctx) {
        new android.app.AlertDialog.Builder(ctx)
                .setTitle(getString(R.string.game_doudizhu_rules_title))
                .setMessage(getString(R.string.game_doudizhu_rules_msg)
                        + "地主先出牌，按逆时针顺序轮流出牌，谁先出完牌谁就获胜。\n\n"
                        + "牌型：单张、对子、三张、三带一、三带二、顺子、连对、飞机、炸弹、火箭等。\n"
                        + "大小：火箭 > 炸弹 > 其他牌型，同牌型比大小。")
                .setPositiveButton(R.string.game_doudizhu_got_it, null)
                .show();
    }
}

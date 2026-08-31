package com.gamecenter.app.doudizhu;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 斗地主游戏风格弹窗（深绿圆角面板 + 金色标题 + 胶囊按钮），
 * 替代系统 AlertDialog，与牌桌视觉统一。
 */
public final class GameDialog {

    public interface Action { void run(); }

    private GameDialog() {}

    /**
     * 展示弹窗。
     *
     * @param title 标题（null 则不显示）
     * @param message 正文
     * @param positive 主按钮文案（null 不显示）
     * @param negative 次按钮文案（null 不显示）
     */
    public static void show(Context ctx, String title, String message,
                            String positive, String negative,
                            Action onPositive, Action onNegative) {
        float density = ctx.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1B3A24"));
        bg.setCornerRadius(18 * density);
        bg.setStroke((int) (1.5f * density), Color.parseColor("#FFD700"));
        root.setBackground(bg);
        int pad = (int) (22 * density);
        root.setPadding(pad, pad, pad, (int) (14 * density));

        if (title != null) {
            TextView tv = new TextView(ctx);
            tv.setText(title);
            tv.setTextSize(20);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(Color.parseColor("#FFD700"));
            tv.setGravity(Gravity.CENTER);
            root.addView(tv, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));
        }

        TextView msg = new TextView(ctx);
        msg.setText(message);
        msg.setTextSize(16);
        msg.setTextColor(Color.parseColor("#ECEFF1"));
        msg.setGravity(Gravity.CENTER);
        root.addView(msg, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, title != null ? (int) (14 * density) : 0, 0, 0));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        root.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, (int) (20 * density), 0, 0));

        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (negative != null) {
            Button b = pillButton(ctx, negative, false, density);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegative != null) onNegative.run();
            });
            row.addView(b);
        }
        if (positive != null) {
            Button b = pillButton(ctx, positive, true, density);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPositive != null) onPositive.run();
            });
            row.addView(b);
        }

        dialog.setContentView(root, new ViewGroup.LayoutParams(
                (int) (300 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static Button pillButton(Context ctx, String text, boolean primary, float density) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(primary ? Color.parseColor("#6200EE") : Color.parseColor("#37474F"));
        gd.setCornerRadius(20 * density);
        b.setBackground(gd);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                (int) (124 * density), (int) (44 * density));
        p.setMargins((int) (6 * density), 0, (int) (6 * density), 0);
        b.setLayoutParams(p);
        return b;
    }
}

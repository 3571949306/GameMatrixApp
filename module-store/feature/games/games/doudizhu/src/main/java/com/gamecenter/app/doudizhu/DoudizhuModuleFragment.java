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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

/**
 * 斗地主模块主 Fragment。
 *
 * <p>承载两种内容视图：菜单页（默认）与单机牌桌页（{@link DoudizhuGameScreen}），
 * 通过替换 contentRoot 子视图切换（不使用子 Fragment 事务，避免动态模块类
 * 在 FragmentManager 恢复时经过宿主 classloader 的加载风险）。</p>
 *
 * <p>对局控制器 {@link DoudizhuGameController} 持有于本 Fragment（retained），
 * 旋转屏幕后重建视图并通过 attachUi 重放对局状态。</p>
 */
public class DoudizhuModuleFragment extends Fragment {

    /** 对局控制器：跨配置变更保留（setRetainInstance），无 View 引用 */
    private DoudizhuGameController controller;

    private FrameLayout contentRoot;

    public DoudizhuModuleFragment() {
        setRetainInstance(true);
    }

    @Override
    public void onCreate(@Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 斗地主牌桌为横屏体验：模块自行请求方向，退出时还原为宿主的竖屏默认，
        // 不影响其他动态模块游戏（不改宿主清单）
        requireActivity().setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    public void onDestroy() {
        requireActivity().setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
        super.onDestroy();
    }

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
        contentRoot = new FrameLayout(ctx);
        if (controller != null && controller.isInGame()) {
            showGameScreen();
        } else {
            showMenu();
        }
        return contentRoot;
    }

    // ============ 菜单页 ============

    private void showMenu() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean night = isNightMode(ctx);

        int bgColor = night ? 0xFF121212 : 0xFFF5F5F5;
        int textColor = night ? 0xFFEEEEEE : 0xFF212121;
        int accentColor = night ? 0xFFBB86FC : 0xFF6200EE;

        LinearLayout menu = new LinearLayout(ctx);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(bgColor);
        menu.setGravity(Gravity.CENTER_HORIZONTAL);
        menu.setPadding(0, (int) (48 * dp), 0, 0);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_doudizhu));
        tvTitle.setTextSize(32);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(accentColor);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleLp);
        menu.addView(tvTitle);

        TextView tvSubtitle = new TextView(ctx);
        tvSubtitle.setText(getString(R.string.game_title_doudizhu_subtitle));
        tvSubtitle.setTextSize(16);
        tvSubtitle.setTextColor(textColor);
        tvSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (8 * dp);
        tvSubtitle.setLayoutParams(subLp);
        menu.addView(tvSubtitle);

        Button btnSingle = new Button(ctx);
        btnSingle.setText(getString(R.string.game_doudizhu_single));
        btnSingle.setTextColor(Color.WHITE);
        btnSingle.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (48 * dp);
        btnSingle.setLayoutParams(btnLp);
        btnSingle.setOnClickListener(v -> showDifficultyDialog(ctx));
        menu.addView(btnSingle);

        Button btnRules = new Button(ctx);
        btnRules.setText(getString(R.string.game_btn_rules));
        btnRules.setTextColor(textColor);
        btnRules.setBackgroundColor(night ? 0xFF2D2D2D : 0xFFE0E0E0);
        LinearLayout.LayoutParams rulesLp = new LinearLayout.LayoutParams(
                (int) (240 * dp), LinearLayout.LayoutParams.WRAP_CONTENT);
        rulesLp.topMargin = (int) (16 * dp);
        btnRules.setLayoutParams(rulesLp);
        btnRules.setOnClickListener(v -> showRulesDialog(ctx));
        menu.addView(btnRules);

        swapContent(menu);
    }

    private void showDifficultyDialog(Context ctx) {
        new android.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.game_doudizhu_choose_difficulty)
                .setItems(
                        new CharSequence[]{
                                getString(R.string.doudizhu_diff_easy),
                                getString(R.string.doudizhu_diff_normal),
                                getString(R.string.doudizhu_diff_hard)},
                        (d, which) -> startGame(which))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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

    // ============ 牌桌页 ============

    private void startGame(int difficulty) {
        if (controller == null) {
            controller = new DoudizhuGameController();
        }
        controller.startNewGame(difficulty);
        if (contentRoot != null) {
            showGameScreen();
        }
    }

    private void showGameScreen() {
        Context ctx = requireContext();
        DoudizhuGameScreen screen = new DoudizhuGameScreen(ctx, controller, this::exitToMenu);
        swapContent(screen);
    }

    private void exitToMenu() {
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
        if (contentRoot != null) {
            showMenu();
        }
    }

    private void swapContent(View view) {
        contentRoot.removeAllViews();
        contentRoot.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}

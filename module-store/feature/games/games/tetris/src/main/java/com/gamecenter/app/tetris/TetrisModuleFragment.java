package com.gamecenter.app.tetris;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import com.gamecenter.app.core.common.ModuleScopedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

/**
 * 俄罗斯方块模块 Fragment（现代化：横屏适配 + HUD）。
 *
 * <p>将 game 状态与 UI 用代码搭建（HUD 三栏 + 浮动按钮）。难度选择弹窗在 Fragment
 * 内显示；并通过 SharedPreferences 持久化"上次难度"以便玩家下次继续。</p>
 */
public class TetrisModuleFragment extends Fragment {

    private TetrisView tetrisView;
    private TetrisGame game;

    private int colorBg;
    private int colorTitle;
    private int colorSub;
    private int colorButtonBg;
    private int colorButtonText;
    private int colorButtonActiveBg;
    private int colorButtonActiveText;
    private int colorOverlay = 0xCC000000;

    private boolean paused = false;

    /** 模块作用域 ID（必须与 catalog.json 中 tetris 模块 id 一致） */
    private static final String MODULE_ID = "tetris";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        applyThemeColors();

        game = new TetrisGame();

        // 读取上次难度
        SharedPreferences prefs = getTetrisPrefs(ctx);
        int savedDiff = prefs.getInt("last_difficulty", 2);
        game.setDifficultyLevel(savedDiff);

        // 整个模块 = 横屏 TetrisView（自带 HUD）
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_tetris));
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTitle);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.topMargin = (int) (8 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 难度切换按钮条
        LinearLayout difficultyBar = new LinearLayout(ctx);
        difficultyBar.setOrientation(LinearLayout.HORIZONTAL);
        difficultyBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.setMargins(0, (int) (4 * dp), 0, (int) (4 * dp));
        difficultyBar.setLayoutParams(barLp);
        root.addView(difficultyBar);

        for (int i = 0; i < TetrisGame.DIFFICULTY_NAMES.length; i++) {
            final int level = i + 1;
            android.widget.Button btn = new android.widget.Button(ctx);
            btn.setText(TetrisGame.DIFFICULTY_NAMES[i]);
            btn.setTextSize(11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, (int) (32 * dp), 1f);
            lp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
            btn.setLayoutParams(lp);
            applyDifficultyButtonStyle(btn, level == game.getDifficultyLevel());
            btn.setOnClickListener(v -> {
                setDifficulty(level);
                showDifficultyRestartConfirm(level);
            });
            difficultyBar.addView(btn);
        }

        // 游戏视图容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        tetrisView = new TetrisView(ctx);
        gameContainer.addView(tetrisView);

        // 浮动按钮：暂停/重开/设置/返回
        addFloatingButtons(gameContainer, dp);

        return root;
    }

    private void addFloatingButtons(FrameLayout container, float dp) {
        int btnSize = (int) (40 * dp);

        // 暂停
        android.widget.ImageButton btnPause = iconButton(android.R.drawable.ic_media_pause, btnSize);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(btnSize, btnSize);
        pLp.gravity = Gravity.END | Gravity.TOP;
        pLp.setMargins((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), 0);
        btnPause.setLayoutParams(pLp);
        btnPause.setOnClickListener(v -> {
            if (tetrisView == null || tetrisView.isGameOver()) return;
            if (tetrisView.isPaused()) {
                tetrisView.resumeGame();
                paused = false;
                btnPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                tetrisView.pauseGame();
                paused = true;
                btnPause.setImageResource(android.R.drawable.ic_media_play);
            }
        });
        container.addView(btnPause);

        // 重开
        android.widget.ImageButton btnRestart = iconButton(android.R.drawable.ic_menu_revert, btnSize);
        FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(btnSize, btnSize);
        rLp.gravity = Gravity.END | Gravity.TOP;
        rLp.setMargins(0, (int) (8 * dp + btnSize + 6 * dp), (int) (8 * dp), 0);
        btnRestart.setLayoutParams(rLp);
        btnRestart.setOnClickListener(v -> {
            if (tetrisView != null) showDifficultyRestartConfirm(game.getDifficultyLevel());
        });
        container.addView(btnRestart);

        // 设置
        android.widget.ImageButton btnSettings = iconButton(android.R.drawable.ic_menu_preferences, btnSize);
        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(btnSize, btnSize);
        sLp.gravity = Gravity.END | Gravity.BOTTOM;
        sLp.setMargins(0, 0, (int) (8 * dp), (int) (8 * dp));
        btnSettings.setLayoutParams(sLp);
        btnSettings.setOnClickListener(v -> showModuleSettings());
        container.addView(btnSettings);
    }

    private android.widget.ImageButton iconButton(int drawableRes, int size) {
        android.widget.ImageButton btn = new android.widget.ImageButton(requireContext());
        btn.setImageResource(drawableRes);
        btn.setBackgroundColor(0x66000000);
        btn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(6, 6, 6, 6);
        return btn;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tetrisView.setDifficultyLevel(game.getDifficultyLevel());

        tetrisView.setOnScoreChangeListener(score -> {
            game.setScore(score);
        });
        tetrisView.setOnLinesClearedListener((lines, combo) -> {
            game.setLines(game.getLines() + lines);
            game.setCombo(combo);
        });
        tetrisView.setOnLevelChangeListener(level -> {
            game.setLevel(level);
        });
        tetrisView.setOnGameOverListener(finalScore -> {
            if (finalScore > game.getHighScore()) game.setHighScore(finalScore);
            // 显示 Game Over 提示
            Toast.makeText(requireContext(), getString(R.string.game_tetris_game_over_msg,
                    finalScore, Math.max(finalScore, game.getHighScore())), Toast.LENGTH_LONG).show();
        });

        tetrisView.post(() -> tetrisView.startGame());
    }

    /**
     * 获取俄罗斯方块模块作用域 SharedPreferences（Phase 3 数据隔离）。
     * 文件名形如 mod_tetris__tetris_module，并一次性迁移旧扁平 SP 数据。
     */
    private SharedPreferences getTetrisPrefs(Context ctx) {
        ModuleScopedPreferences.migrateFrom(ctx, MODULE_ID, "tetris_module");
        return ModuleScopedPreferences.get(ctx, MODULE_ID, "tetris_module");
    }

    private void setDifficulty(int level) {
        game.setDifficultyLevel(level);
        if (tetrisView != null) tetrisView.setDifficultyLevel(level);
        // 持久化
        getTetrisPrefs(requireContext())
                .edit().putInt("last_difficulty", level).apply();
        // 更新底部按钮高亮
        View root = getView();
        if (root instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) root;
            LinearLayout bar = (LinearLayout) layout.getChildAt(1);
            for (int i = 0; i < bar.getChildCount(); i++) {
                android.widget.Button b = (android.widget.Button) bar.getChildAt(i);
                applyDifficultyButtonStyle(b, i + 1 == level);
            }
        }
    }

    private void applyDifficultyButtonStyle(android.widget.Button btn, boolean active) {
        if (active) {
            btn.setBackgroundColor(colorButtonActiveBg);
            btn.setTextColor(colorButtonActiveText);
        } else {
            btn.setBackgroundColor(colorButtonBg);
            btn.setTextColor(colorButtonText);
        }
    }

    private void showDifficultyRestartConfirm(int level) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.game_tetris_restart_confirm_title)
                .setMessage(R.string.game_tetris_restart_confirm_msg)
                .setPositiveButton(R.string.game_tetris_restart, (d, w) -> {
                    tetrisView.pauseGame();
                    setDifficulty(level);
                    tetrisView.startGame();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showModuleSettings() {
        final String[] items = {
                getString(R.string.game_tetris_settings_sound),
                getString(R.string.game_tetris_settings_vibrate),
                getString(R.string.tetris_rules_title)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.game_tetris_settings_title)
                .setItems(items, (d, w) -> {
                    if (w == 2) {
                        String body = getString(R.string.tetris_rules_basic)
                                + "\n\n" + getString(R.string.tetris_rules_victory)
                                + "\n\n" + getString(R.string.tetris_rules_scoring)
                                + "\n\n" + getString(R.string.tetris_rules_modern);
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(R.string.tetris_rules_title)
                                .setMessage(body)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    } else {
                        // 模块商店版本提示进入宿主设置
                        Toast.makeText(requireContext(), "请到宿主 App 设置中调整", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (tetrisView != null && !tetrisView.isGameOver() && !tetrisView.isPaused()) {
            tetrisView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tetrisView != null && !tetrisView.isGameOver() && !paused) {
            tetrisView.resumeGame();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tetrisView != null) tetrisView.stopGame();
    }

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        if (isDark) {
            colorBg = 0xFF1B1E22;
            colorTitle = 0xFFE4E6F0;
            colorSub = 0xFF9AA0A6;
            colorButtonBg = 0xFF2A2E3A;
            colorButtonText = 0xFFE4E6F0;
            colorButtonActiveBg = 0xFF5B8A72;
            colorButtonActiveText = 0xFFFFFFFF;
        } else {
            colorBg = 0xFFF5F5F5;
            colorTitle = 0xFF212121;
            colorSub = 0xFF757575;
            colorButtonBg = 0xFFE0E0E0;
            colorButtonText = 0xFF212121;
            colorButtonActiveBg = 0xFF5B8A72;
            colorButtonActiveText = 0xFFFFFFFF;
        }
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}

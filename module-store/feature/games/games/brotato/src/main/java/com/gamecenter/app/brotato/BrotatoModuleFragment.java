package com.gamecenter.app.brotato;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.BrotatoContent;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.modules.ModuleManager;

/**
 * 土豆兄弟游戏 Fragment（五星级现代化架构版）。
 *
 * <p>由宿主 BrotatoActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 采用统一顶部导航栏、即开即玩战场体系、实时战况 HUD 与沉浸式对局结算。</p>
 */
public class BrotatoModuleFragment extends Fragment {

    private static final String GAME_ID = "brotato";
    /** 驱动频率：与引擎固定帧步长一致，保证"一 tick = 16ms 引擎时间"。 */
    private static final long FRAME_INTERVAL_MS = BrotatoArena.TICK_MS;

    private BrotatoView brotatoView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int highScore = 0;
    private int totalGames = 0;
    /** UI 侧保持旧版难度因子口径（0.3/0.5/0.8），只在传给引擎时映射成倍率。 */
    private float difficultyFactor = 0.5f;

    private TextView tvScore;
    private TextView tvWave;
    private TextView tvBest;
    private Button btnEasy;
    private Button btnNormal;
    private Button btnHard;
    private Button btnRestart;
    private GameUsageStore usageStore;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (brotatoView != null && brotatoView.isGameRunning()) {
                brotatoView.update();
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int colorBg = isNightMode() ? 0xFF0F172A : 0xFFF8FAFC;
        int colorTextPrimary = isNightMode() ? 0xFFF1F5F9 : 0xFF0F172A;
        int colorTextSecondary = isNightMode() ? 0xFF94A3B8 : 0xFF64748B;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (10 * dp));

        // 1. 顶部统一导航栏
        LinearLayout topNavBar = new LinearLayout(ctx);
        topNavBar.setOrientation(LinearLayout.HORIZONTAL);
        topNavBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topNavLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * dp));
        topNavLp.bottomMargin = (int) (6 * dp);
        topNavBar.setLayoutParams(topNavLp);

        Button btnBack = new Button(ctx);
        btnBack.setText("❮ 返回");
        btnBack.setTextSize(13f);
        btnBack.setTextColor(colorTextPrimary);
        btnBack.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFE2E8F0, (int) (14 * dp)));
        btnBack.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        btnBack.setMinHeight((int) (32 * dp));
        btnBack.setMinimumHeight((int) (32 * dp));
        btnBack.setOnClickListener(v -> handleBackPress());
        topNavBar.addView(btnBack);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_brotato));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleLp);
        topNavBar.addView(tvTitle);

        Button btnRules = new Button(ctx);
        btnRules.setText("ℹ 规则");
        btnRules.setTextSize(13f);
        btnRules.setTextColor(colorTextPrimary);
        btnRules.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFE2E8F0, (int) (14 * dp)));
        btnRules.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        btnRules.setMinHeight((int) (32 * dp));
        btnRules.setMinimumHeight((int) (32 * dp));
        btnRules.setOnClickListener(v -> showRulesDialog());
        topNavBar.addView(btnRules);

        root.addView(topNavBar);

        // 2. 状态战况 HUD 看板
        LinearLayout statBar = new LinearLayout(ctx);
        statBar.setOrientation(LinearLayout.HORIZONTAL);
        statBar.setGravity(Gravity.CENTER);
        statBar.setBackground(createCapsuleDrawable(isNightMode() ? 0xFF1E293B : 0xFFEDF2F7, (int) (12 * dp)));
        statBar.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
        LinearLayout.LayoutParams statBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statBarLp.bottomMargin = (int) (6 * dp);
        statBar.setLayoutParams(statBarLp);

        tvScore = new TextView(ctx);
        tvScore.setTextSize(14f);
        tvScore.setTypeface(null, android.graphics.Typeface.BOLD);
        tvScore.setTextColor(0xFF2563EB);
        tvScore.setText(getString(R.string.game_score_init));
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvScore.setLayoutParams(scoreLp);
        statBar.addView(tvScore);

        tvWave = new TextView(ctx);
        tvWave.setTextSize(14f);
        tvWave.setTypeface(null, android.graphics.Typeface.BOLD);
        tvWave.setTextColor(0xFF059669);
        tvWave.setGravity(Gravity.CENTER);
        tvWave.setText(getString(R.string.game_wave_init));
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvWave.setLayoutParams(waveLp);
        statBar.addView(tvWave);

        tvBest = new TextView(ctx);
        tvBest.setTextSize(14f);
        tvBest.setTextColor(colorTextSecondary);
        tvBest.setGravity(Gravity.END);
        tvBest.setText(getString(R.string.game_high_score_init));
        LinearLayout.LayoutParams bestLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvBest.setLayoutParams(bestLp);
        statBar.addView(tvBest);

        root.addView(statBar);

        // 3. 难度胶囊选择区
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.bottomMargin = (int) (6 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = createDiffButton(ctx, getString(R.string.game_diff_easy), dp);
        btnEasy.setOnClickListener(v -> setDifficulty(0.3f));
        diffBar.addView(btnEasy);

        btnNormal = createDiffButton(ctx, getString(R.string.game_diff_normal), dp);
        btnNormal.setOnClickListener(v -> setDifficulty(0.5f));
        diffBar.addView(btnNormal);

        btnHard = createDiffButton(ctx, getString(R.string.game_diff_hard), dp);
        btnHard.setOnClickListener(v -> setDifficulty(0.8f));
        diffBar.addView(btnHard);

        root.addView(diffBar);

        // 4. 游戏容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources moduleResources = null;
        try {
            moduleResources = ModuleManager.INSTANCE.getModuleResources("brotato");
        } catch (RuntimeException exception) {
            android.util.Log.e("BrotatoFragment", "module resources unavailable", exception);
        }
        if (moduleResources == null) {
            Toast.makeText(ctx, "土豆兄弟模块资源装载失败，请重新安装该模块", Toast.LENGTH_LONG).show();
            return new FrameLayout(requireContext());
        }
        BrotatoContent content;
        try {
            content = BrotatoAssets.load(moduleResources.getAssetManager());
        } catch (IllegalStateException exception) {
            android.util.Log.e("BrotatoFragment", "brotato content assets invalid", exception);
            Toast.makeText(ctx, "土豆兄弟内容文件校验失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
            return new FrameLayout(requireContext());
        }
        final int totalWavesForHud = content.totalWaves();
        brotatoView = new BrotatoView(ctx, content);
        applyDifficulty();
        brotatoView.setOnGameListener(new BrotatoView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                if (tvScore != null) {
                    tvScore.setText(getString(R.string.game_score_format, score));
                }
            }

            @Override
            public void onGameOver(int score, int wave) {
                finishGame(score, false);
            }

            @Override
            public void onWin(int score) {
                finishGame(score, true);
            }

            @Override
            public void onWaveComplete(int wave) {
                if (tvWave != null) {
                    tvWave.setText(getString(R.string.game_wave_format,
                            Math.min(wave + 1, totalWavesForHud)));
                }
            }
        });
        gameContainer.addView(brotatoView);

        // 5. 底部控制栏
        btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        btnRestart.setTextSize(15f);
        btnRestart.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setBackground(createCapsuleDrawable(0xFF2563EB, (int) (22 * dp)));
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * dp));
        restartLp.topMargin = (int) (6 * dp);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> startGame());
        root.addView(btnRestart);

        updateDifficultyButtons();
        return root;
    }

    private Button createDiffButton(Context ctx, String text, float dp) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setMinHeight((int) (34 * dp));
        btn.setMinimumHeight((int) (34 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private GradientDrawable createCapsuleDrawable(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private void handleBackPress() {
        if (brotatoView != null && brotatoView.isGameRunning()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("当前战斗正处于激烈波次中，确定要退出战场吗？")
                    .setPositiveButton("确定退出", (dialog, which) -> exitModule())
                    .setNegativeButton("继续战斗", null)
                    .show();
        } else {
            exitModule();
        }
    }

    private void exitModule() {
        if (getActivity() != null) {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                getActivity().finish();
            }
        }
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("土豆兄弟 · 战场规则")
                .setMessage("【游戏介绍】\n"
                        + "你将操控全副武装的土豆勇士，在无垠角斗场中迎击四面八方涌来的怪物浪潮！\n\n"
                        + "【核心操作】\n"
                        + "1. 触控滑动屏幕操控土豆灵活走位、躲避攻击；\n"
                        + "2. 武器会自动索敌并持续倾泻火力；\n"
                        + "3. 击败敌人收集战利品，在波次结束后选择强力升级！\n\n"
                        + "【难度分级】\n"
                        + "· 简单 (0.3x)：怪物生命与移速降低，轻松割草；\n"
                        + "· 普通 (0.5x)：标准战斗节奏与怪物强度；\n"
                        + "· 困难 (0.8x)：怪物血量狂飙、移速加快，极限走位生存！")
                .setPositiveButton("开战！", null)
                .show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        highScore = usageStore.getHighScore(GAME_ID);
        if (tvBest != null) {
            tvBest.setText(getString(R.string.game_high_score_format, highScore));
        }
        updateDifficultyButtons();
        startGame();
    }

    private void startGame() {
        if (brotatoView == null) return;
        brotatoView.startGame();
        if (tvScore != null) tvScore.setText(getString(R.string.game_score_init));
        if (tvWave != null) tvWave.setText(getString(R.string.game_wave_init));
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    private void setDifficulty(float factor) {
        difficultyFactor = factor;
        applyDifficulty();
        updateDifficultyButtons();
        if (isAdded()) {
            Toast.makeText(requireContext(),
                    R.string.game_brotato_diff_next_round,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void applyDifficulty() {
        if (brotatoView == null) return;
        brotatoView.setDifficultyScalar(difficultyScalar(difficultyFactor));
    }

    private static float difficultyScalar(float factor) {
        if (factor <= 0.3f) return BrotatoArena.DIFFICULTY_EASY;
        if (factor >= 0.8f) return BrotatoArena.DIFFICULTY_HARD;
        return BrotatoArena.DIFFICULTY_NORMAL;
    }

    private void finishGame(int score, boolean won) {
        handler.removeCallbacks(gameLoop);
        totalGames++;
        boolean isNewRecord = score > highScore;
        if (isNewRecord) {
            highScore = score;
        }
        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, highScore);
            if (won) {
                usageStore.recordWin(GAME_ID);
            } else {
                usageStore.recordLoss(GAME_ID);
            }
        }
        if (tvBest != null) {
            tvBest.setText(getString(R.string.game_high_score_format, highScore));
        }

        if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(won ? "🏆 胜利！波次全清" : "💥 战斗结束")
                    .setMessage((won ? "恭喜勇士成功通关所有波次！" : "你倒在了冲锋的路上！")
                            + "\n\n最终积分：" + score
                            + (isNewRecord ? "\n🎉 恭喜创造全新历史记录！" : "\n历史最佳：" + highScore))
                    .setPositiveButton("再战一局", (dialog, which) -> startGame())
                    .setNegativeButton("返回大厅", (dialog, which) -> exitModule())
                    .show();
        }
    }

    private void updateDifficultyButtons() {
        if (btnEasy == null || btnNormal == null || btnHard == null) return;
        float dp = getResources().getDisplayMetrics().density;
        styleDiffTab(btnEasy, difficultyFactor == 0.3f, dp);
        styleDiffTab(btnNormal, difficultyFactor == 0.5f, dp);
        styleDiffTab(btnHard, difficultyFactor == 0.8f, dp);
    }

    private void styleDiffTab(Button btn, boolean active, float dp) {
        if (active) {
            btn.setBackground(createCapsuleDrawable(0xFF2563EB, (int) (10 * dp)));
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            int inactiveBg = isNightMode() ? 0xFF1E293B : 0xFFE2E8F0;
            int inactiveText = isNightMode() ? 0xFF94A3B8 : 0xFF475569;
            btn.setBackground(createCapsuleDrawable(inactiveBg, (int) (10 * dp)));
            btn.setTextColor(inactiveText);
            btn.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private boolean isNightMode() {
        if (!isAdded()) return false;
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(gameLoop);
        if (brotatoView != null) {
            brotatoView.pauseGame();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (brotatoView != null && brotatoView.isGameRunning()) {
            brotatoView.resumeGame();
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (brotatoView != null) {
            brotatoView.stopGame();
        }
    }
}

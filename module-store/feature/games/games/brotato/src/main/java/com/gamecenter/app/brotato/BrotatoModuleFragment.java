package com.gamecenter.app.brotato;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.BrotatoContent;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.modules.ModuleManager;

/**
 * 土豆兄弟游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 BrotatoActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。难度选择以代码内按钮实现，
 * 不含成就系统，仅保留基本游戏功能。</p>
 *
 * <p>本层只做三件事：装载数据驱动内容（{@link BrotatoAssets}，失败即抛、不回退）、
 * 把三档难度映射成引擎倍率 0.7/1.0/1.45、以及用 16ms 定时器驱动
 * {@link BrotatoView#update()}（与 {@link BrotatoArena#TICK_MS} 同频）。
 * 战场规则全在 {@link BrotatoArena} 内，这里不再有任何游戏逻辑。</p>
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
        int colorBg = isNightMode() ? 0xFF121622 : 0xFFF5F5F5;
        int colorTextPrimary = isNightMode() ? 0xFFE4E6F0 : 0xFF212121;
        int colorTextSecondary = isNightMode() ? 0xFFAAAAAA : 0xFF757575;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_brotato));
        tvTitle.setTextSize(24);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        // 状态栏
        LinearLayout statBar = new LinearLayout(ctx);
        statBar.setOrientation(LinearLayout.HORIZONTAL);
        statBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statBarLp.topMargin = (int) (8 * dp);
        statBar.setLayoutParams(statBarLp);

        tvScore = new TextView(ctx);
        tvScore.setTextSize(16);
        tvScore.setTextColor(colorTextPrimary);
        tvScore.setText(getString(R.string.game_score_init));
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.rightMargin = (int) (16 * dp);
        tvScore.setLayoutParams(scoreLp);
        statBar.addView(tvScore);

        tvWave = new TextView(ctx);
        tvWave.setTextSize(16);
        tvWave.setTextColor(colorTextPrimary);
        tvWave.setText(getString(R.string.game_wave_init));
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        waveLp.rightMargin = (int) (16 * dp);
        tvWave.setLayoutParams(waveLp);
        statBar.addView(tvWave);

        tvBest = new TextView(ctx);
        tvBest.setTextSize(16);
        tvBest.setTextColor(colorTextSecondary);
        tvBest.setText(getString(R.string.game_high_score_init));
        statBar.addView(tvBest);
        root.addView(statBar);

        // 难度按钮
        LinearLayout diffBar = new LinearLayout(ctx);
        diffBar.setOrientation(LinearLayout.HORIZONTAL);
        diffBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams diffBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        diffBarLp.topMargin = (int) (8 * dp);
        diffBarLp.bottomMargin = (int) (4 * dp);
        diffBar.setLayoutParams(diffBarLp);

        btnEasy = new Button(ctx);
        btnEasy.setText(getString(R.string.game_diff_easy));
        btnEasy.setTextSize(12);
        btnEasy.setOnClickListener(v -> setDifficulty(0.3f));
        diffBar.addView(btnEasy);

        btnNormal = new Button(ctx);
        btnNormal.setText(getString(R.string.game_diff_normal));
        btnNormal.setTextSize(12);
        btnNormal.setOnClickListener(v -> setDifficulty(0.5f));
        diffBar.addView(btnNormal);

        btnHard = new Button(ctx);
        btnHard.setText(getString(R.string.game_diff_hard));
        btnHard.setTextSize(12);
        btnHard.setOnClickListener(v -> setDifficulty(0.8f));
        diffBar.addView(btnHard);
        root.addView(diffBar);

        // 游戏容器
        FrameLayout gameContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerLp.topMargin = (int) (8 * dp);
        gameContainer.setLayoutParams(containerLp);
        root.addView(gameContainer);

        // 模块 APK 的资产必须走模块 AssetManager（宿主 getAssets() 看不到 assets/brotato/，
        // 与 td/klotski/chinesechess 同款装载口）；失败给可见提示并返回空视图，不崩宿主。
        com.gamecenter.app.modular.ModuleResourceLoader.ModuleResources moduleResources = null;
        try {
            moduleResources = ModuleManager.INSTANCE.getModuleResources("brotato");
        } catch (RuntimeException exception) {
            android.util.Log.e("BrotatoFragment", "module resources unavailable", exception);
        }
        if (moduleResources == null) {
            android.widget.Toast.makeText(ctx, "土豆兄弟模块资源装载失败，请重新安装该模块",
                    android.widget.Toast.LENGTH_LONG).show();
            return new android.widget.FrameLayout(requireContext());
        }
        BrotatoContent content;
        try {
            content = BrotatoAssets.load(moduleResources.getAssetManager());
        } catch (IllegalStateException exception) {
            android.util.Log.e("BrotatoFragment", "brotato content assets invalid", exception);
            android.widget.Toast.makeText(ctx, "土豆兄弟内容文件校验失败：" + exception.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            return new android.widget.FrameLayout(requireContext());
        }
        final int totalWavesForHud = content.totalWaves();
        brotatoView = new BrotatoView(ctx, content);
        applyDifficulty();
        brotatoView.setOnGameListener(new BrotatoView.OnGameListener() {
            @Override
            public void onScoreChanged(int score) {
                tvScore.setText(getString(R.string.game_score_format, score));
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

        // 重新开始按钮
        btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = (int) (8 * dp);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> startGame());
        root.addView(btnRestart);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        // 复用最高分存储位记录最高分，便于在 Fragment 间持久化
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

    /** UI 难度因子 → 引擎倍率：简单 0.3 → {@code DIFFICULTY_EASY}、普通 0.5 → NORMAL、困难 0.8 → HARD。 */
    private void setDifficulty(float factor) {
        difficultyFactor = factor;
        applyDifficulty();
        updateDifficultyButtons();
        // 引擎标量在构造期固定，难度只对下一局生效——给用户明确反馈，避免"点了没反应"
        if (isAdded()) {
            android.widget.Toast.makeText(requireContext(),
                    R.string.game_brotato_diff_next_round,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 倍率乘在敌人 hp/speed 上、除在出怪间隔上。引擎的难度标量是构造期入参（一整局固定，
     * 以保证同 seed 同标量可复现），所以这里改的值在下一局（"重新开始"）生效。
     */
    private void applyDifficulty() {
        if (brotatoView == null) return;
        brotatoView.setDifficultyScalar(difficultyScalar(difficultyFactor));
    }

    private static float difficultyScalar(float factor) {
        if (factor <= 0.3f) return BrotatoArena.DIFFICULTY_EASY;
        if (factor >= 0.8f) return BrotatoArena.DIFFICULTY_HARD;
        return BrotatoArena.DIFFICULTY_NORMAL;
    }

    /** 一局结束的结算：停循环、记最高分、按胜负分别记胜/负。 */
    private void finishGame(int score, boolean won) {
        handler.removeCallbacks(gameLoop);
        totalGames++;
        if (score > highScore) {
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
    }

    private void updateDifficultyButtons() {
        if (btnEasy == null) return;
        int active = 0xFF3949AB;
        int inactive = isNightMode() ? 0xFF333A4D : 0xFFE0E0E0;
        btnEasy.setBackgroundColor(difficultyFactor == 0.3f ? active : inactive);
        btnNormal.setBackgroundColor(difficultyFactor == 0.5f ? active : inactive);
        btnHard.setBackgroundColor(difficultyFactor == 0.8f ? active : inactive);
        btnEasy.setTextColor(Color.WHITE);
        btnNormal.setTextColor(Color.WHITE);
        btnHard.setTextColor(Color.WHITE);
    }

    private boolean isNightMode() {
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
            // 先摘再挂：防止 onViewCreated/onResume 同帧双投递造成 2× 速自排循环
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
